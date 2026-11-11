/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : OpeningBalancePanel.test.tsx
 * Description : 期初余额录入面板的加载 / 平衡校验拦截 / 提交口径冒烟测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import OpeningBalancePanel from './OpeningBalancePanel';
import { apiClient } from '../api/client';
import { renderWithProviders } from '../test/render';

vi.mock('../api/client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
  apiClientInstance: {
    get: vi.fn(),
    post: vi.fn(),
  },
  getApiBaseUrl: () => '',
  DEFAULT_TIMEOUT: 30000,
  LONG_TIMEOUT: 120000,
}));

const get = vi.mocked(apiClient.get);
const put = vi.mocked(apiClient.put);
const del = vi.mocked(apiClient.delete);

/** 造一行科目（默认零余额） */
function item(code: string, name: string, overrides: Record<string, unknown> = {}) {
  return {
    subjectCode: code,
    subjectName: name,
    category: 'ASSET',
    direction: 'DEBIT',
    debitAmount: 0,
    creditAmount: 0,
    configured: false,
    active: true,
    openingPeriod: null,
    remark: null,
    ...overrides,
  };
}

/** 期初余额视图 */
function view(overrides: Record<string, unknown> = {}) {
  return {
    companyId: '1',
    openingPeriod: null,
    items: [
      item('1002', '银行存款'),
      item('1122', '应收账款'),
      item('4001', '实收资本', { category: 'EQUITY', direction: 'CREDIT' }),
    ],
    totalDebit: 0,
    totalCredit: 0,
    balanced: true,
    configured: false,
    ...overrides,
  } as never;
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('OpeningBalancePanel', () => {
  it('未选企业时提示先选企业，不发请求', async () => {
    renderWithProviders(<OpeningBalancePanel />);
    expect(await screen.findByText('请先选择企业，再录入期初余额')).toBeInTheDocument();
    expect(get).not.toHaveBeenCalled();
  });

  it('加载后默认只显示有余额的科目，可切换为全部科目', async () => {
    get.mockResolvedValue(
      view({
        configured: true,
        openingPeriod: 202601,
        items: [
          item('1002', '银行存款', { debitAmount: 80000, configured: true }),
          item('1122', '应收账款'),
          item('4001', '实收资本', { category: 'EQUITY', creditAmount: 80000, configured: true }),
        ],
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<OpeningBalancePanel companyId="1" />);

    expect(await screen.findByText('共 3 个科目，其中 2 个已录余额（隐藏了 1 个零余额科目）')).toBeInTheDocument();
    expect(screen.getByText('银行存款')).toBeInTheDocument();
    expect(screen.queryByText('应收账款')).not.toBeInTheDocument();

    await user.click(screen.getByRole('switch'));
    expect(await screen.findByText('应收账款')).toBeInTheDocument();
  });

  it('借贷不平衡时禁用保存，并显示差额', async () => {
    get.mockResolvedValue(
      view({
        configured: true,
        openingPeriod: 202601,
        items: [
          item('1002', '银行存款', { debitAmount: 1000, configured: true }),
          item('4001', '实收资本', { category: 'EQUITY', creditAmount: 900, configured: true }),
        ],
      }),
    );
    renderWithProviders(<OpeningBalancePanel companyId="1" />);

    expect(await screen.findByText('差额 100.00')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /保存期初/ })).toBeDisabled();
  });

  it('借贷平衡时保存，只提交有余额的科目', async () => {
    get.mockResolvedValue(
      view({
        configured: true,
        openingPeriod: 202601,
        items: [
          item('1002', '银行存款', { debitAmount: 80000, configured: true }),
          item('1122', '应收账款'),
          item('4001', '实收资本', { category: 'EQUITY', creditAmount: 80000, configured: true }),
        ],
      }),
    );
    put.mockResolvedValue(
      view({ configured: true, openingPeriod: 202601, balanced: true }) as never,
    );
    const onSaved = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(<OpeningBalancePanel companyId="1" onSaved={onSaved} />);

    await screen.findByText('银行存款');
    await user.click(screen.getByRole('button', { name: /保存期初/ }));

    await waitFor(() => expect(put).toHaveBeenCalled());
    const [url, payload, config] = put.mock.calls[0];
    expect(url).toBe('/scrm/vouchers/opening-balance');
    expect(config).toMatchObject({ params: { companyId: '1', openingPeriod: 202601 } });
    // 零余额科目（应收账款）不应出现在提交体里
    expect(payload).toEqual([
      { subjectCode: '1002', debitAmount: 80000, creditAmount: 0, remark: undefined },
      { subjectCode: '4001', debitAmount: 0, creditAmount: 80000, remark: undefined },
    ]);
    await waitFor(() => expect(onSaved).toHaveBeenCalled());
  });

  it('清空前二次确认，确认后调用清空接口并重新加载', async () => {
    get.mockResolvedValue(
      view({ configured: true, openingPeriod: 202601, items: [item('1002', '银行存款', { debitAmount: 1, configured: true })] }),
    );
    del.mockResolvedValue({ deleted: 1 } as never);
    const user = userEvent.setup();
    renderWithProviders(<OpeningBalancePanel companyId="1" />);

    await screen.findByText('银行存款');
    await user.click(screen.getByRole('button', { name: /清空/ }));

    expect((await screen.findAllByText('确认清空该企业的期初余额？')).length).toBeGreaterThan(0);
    await user.click(screen.getByRole('button', { name: /确\s*定/ }));

    await waitFor(() => expect(del).toHaveBeenCalledWith('/scrm/vouchers/opening-balance', {
      params: { companyId: '1' },
    }));
  });

  it('有未录期初的**其他**主体时显示总览条，点击可切换', async () => {
    get.mockImplementation(async (url: string) => {
      if (url === '/scrm/vouchers/opening-balance/summary') {
        return {
          items: [
            {
              companyId: '2',
              companyName: '甲客户',
              configured: false,
              totalDebit: 0,
              totalCredit: 0,
              balanced: false,
              subjectCount: 32,
              configuredSubjectCount: 0,
              suggestOpening: true,
            },
          ],
          pendingCount: 1,
          totalCount: 1,
        } as never;
      }
      return view({ configured: true, openingPeriod: 202601 }) as never;
    });
    const onSwitchCompany = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(<OpeningBalancePanel companyId="1" onSwitchCompany={onSwitchCompany} />);

    expect(await screen.findByText('其他还有 1 户尚未录入期初余额')).toBeInTheDocument();
    await user.click(screen.getByText('甲客户'));
    expect(onSwitchCompany).toHaveBeenCalledWith('2');
  });

  it('没有未录期初的其他主体时不渲染总览条', async () => {
    get.mockImplementation(async (url: string) => {
      if (url === '/scrm/vouchers/opening-balance/summary') {
        return { items: [], pendingCount: 0, totalCount: 0 } as never;
      }
      return view({
        configured: true,
        openingPeriod: 202601,
        items: [item('1002', '银行存款', { debitAmount: 80000, configured: true })],
      }) as never;
    });
    renderWithProviders(<OpeningBalancePanel companyId="1" />);

    await screen.findByText('银行存款');
    expect(screen.queryByText(/尚未录入期初余额/)).not.toBeInTheDocument();
  });

  it('当前户自己在待录名单里时，不把自己列进总览条', async () => {
    get.mockImplementation(async (url: string) => {
      if (url === '/scrm/vouchers/opening-balance/summary') {
        return {
          items: [
            {
              companyId: '1',
              companyName: '当前户',
              configured: false,
              totalDebit: 0,
              totalCredit: 0,
              balanced: false,
              subjectCount: 32,
              configuredSubjectCount: 0,
              suggestOpening: true,
            },
            {
              companyId: '2',
              companyName: '另一户',
              configured: false,
              totalDebit: 0,
              totalCredit: 0,
              balanced: false,
              subjectCount: 32,
              configuredSubjectCount: 0,
              suggestOpening: true,
            },
          ],
          pendingCount: 2,
          totalCount: 2,
        } as never;
      }
      return view({ configured: false, openingPeriod: 202601 }) as never;
    });
    renderWithProviders(<OpeningBalancePanel companyId="1" />);

    // 只提示「其他 1 户」，不把正在录入的当前户算进去
    expect(await screen.findByText('其他还有 1 户尚未录入期初余额')).toBeInTheDocument();
    expect(screen.queryByText('当前户')).not.toBeInTheDocument();
  });

  it('只有当前户自己待录时，整条总览不渲染', async () => {
    get.mockImplementation(async (url: string) => {
      if (url === '/scrm/vouchers/opening-balance/summary') {
        return {
          items: [
            {
              companyId: '1',
              companyName: '当前户',
              configured: false,
              totalDebit: 0,
              totalCredit: 0,
              balanced: false,
              subjectCount: 32,
              configuredSubjectCount: 0,
              suggestOpening: true,
            },
          ],
          pendingCount: 1,
          totalCount: 1,
        } as never;
      }
      return view({ configured: false, openingPeriod: 202601 }) as never;
    });
    renderWithProviders(<OpeningBalancePanel companyId="1" />);

    await screen.findByText(/共 \d+ 个科目/);
    // 面板自身的「尚未录入期初余额」提示仍应存在，只是总览条（带「其他还有」）不该出现
    expect(screen.queryByText(/其他还有 .* 户尚未录入期初余额/)).not.toBeInTheDocument();
  });
});
