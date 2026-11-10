/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : AccountingReports.close.test.tsx
 * Description : 结账弹窗的「无期初从零起账」确认口径测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import AccountingReports from './AccountingReports';
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
const post = vi.mocked(apiClient.post);

/** 空报表体：本测试只关心结账交互，报表内容不参与断言 */
const emptySheet = {
  companyId: '372374768728059904',
  periodYear: 2026,
  periodMonth: 11,
  assets: [],
  liabilities: [],
  equities: [],
  totalAssetOpening: 0,
  totalAssetClosing: 0,
  totalLiabilityAndEquityOpening: 0,
  totalLiabilityAndEquityClosing: 0,
  balanced: true,
};

beforeEach(() => {
  vi.clearAllMocks();
  get.mockImplementation(async (url: string) => {
    if (url === '/scrm/companies') {
      return {
        content: [{ id: '372374768728059904', companyName: '测试公司', creditCode: 'X' }],
      } as never;
    }
    if (url === '/scrm/vouchers/periods') {
      return [] as never;
    }
    if (url === '/scrm/vouchers/subjects') {
      return [{ subjectCode: '1002', subjectName: '银行存款' }] as never;
    }
    if (url === '/scrm/vouchers/balance-sheet') {
      return emptySheet as never;
    }
    return {} as never;
  });
  post.mockResolvedValue({
    period: '2026-11',
    status: 'CLOSED',
    profit: 0,
    message: '已结账',
  } as never);
});

/** 选中企业，进入已选状态 */
async function selectCompany(user: ReturnType<typeof userEvent.setup>) {
  const selects = document.querySelectorAll('.ant-select-selector');
  await user.click(selects[selects.length - 1] as HTMLElement);
  const option = await screen.findByText('测试公司（X）');
  await user.click(option);
  await waitFor(() =>
    expect(get).toHaveBeenCalledWith('/scrm/vouchers/periods', expect.anything()),
  );
}

describe('AccountingReports 结账弹窗', () => {
  it('结账弹窗包含「无期初从零起账」勾选框，默认不勾选', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AccountingReports />);
    await selectCompany(user);

    await user.click(screen.getAllByRole('button').find((b) => b.textContent === '结账') as HTMLElement);

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText(/本户无期初（从零起账）/)).toBeInTheDocument();
    expect(within(dialog).getByRole('checkbox')).not.toBeChecked();
  });

  it('未勾选时结账传 confirmNoOpening=false', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AccountingReports />);
    await selectCompany(user);

    await user.click(screen.getAllByRole('button').find((b) => b.textContent === '结账') as HTMLElement);
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: /确认结账/ }));

    await waitFor(() => expect(post).toHaveBeenCalled());
    const [url, , config] = post.mock.calls[0];
    expect(url).toBe('/scrm/vouchers/close-period');
    expect(config).toMatchObject({
      params: expect.objectContaining({ confirmNoOpening: false }),
    });
  });

  it('勾选后结账传 confirmNoOpening=true', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AccountingReports />);
    await selectCompany(user);

    await user.click(screen.getAllByRole('button').find((b) => b.textContent === '结账') as HTMLElement);
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('checkbox'));
    await user.click(within(dialog).getByRole('button', { name: /确认结账/ }));

    await waitFor(() => expect(post).toHaveBeenCalled());
    const [, , config] = post.mock.calls[0];
    expect(config).toMatchObject({
      params: expect.objectContaining({ confirmNoOpening: true }),
    });
  });
});
