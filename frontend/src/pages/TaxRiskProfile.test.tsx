/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : TaxRiskProfile.test.tsx
 * Description : 税负风险画像页面的企业选择 / 汇总卡 / 风险项 / 税负率趋势 / 空态与失败降级测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent, within } from '@testing-library/react';
import TaxRiskProfile from './TaxRiskProfile';
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

/** 分页响应 */
function page<T>(rows: T[], total = rows.length) {
  return { content: rows, totalElements: total, totalPages: 1, number: 0, size: 30 } as never;
}

/** 企业行 */
function company(id: string, name: string) {
  return { id, companyName: name, creditCode: `91${id}0000000000000X` } as never;
}

/** 税负风险画像响应 */
function profile(overrides: Record<string, unknown> = {}) {
  return {
    companyId: '1',
    year: new Date().getFullYear(),
    recordTotal: 12,
    overdueCount: 2,
    underpaidCount: 1,
    trend: [
      { month: '2026-01', revenue: '123400.50', taxRate: '0.8%', flag: 'LOW' },
      { month: '2026-02', revenue: '200000', taxRate: '2.1%', flag: 'NORMAL' },
      { month: '2026-03', revenue: '300000', taxRate: '5.2%', flag: 'HIGH' },
      { month: '2026-04', revenue: '-', taxRate: '-', flag: 'NA' },
    ],
    risks: [
      { level: 'CRITICAL', message: '申报逾期率 25.0%，超过 20% 阈值', suggestion: '排查逾期任务，补齐申报' },
      { level: 'WARN', message: '增值税税负率连续 3 个月低于 1.0%', suggestion: '核对收入完整性与进项抵扣' },
    ],
    generatedAt: '2026-11-08 10:00:00',
    ...overrides,
  } as never;
}

/** 按 URL 分派桩数据 */
function mockApi(riskProfile: () => Promise<unknown>) {
  get.mockImplementation((url: string) => {
    if (url === '/scrm/companies') {
      return Promise.resolve(page([company('1', '测试企业')])) as never;
    }
    if (url.includes('/risk-profile')) {
      return riskProfile() as never;
    }
    return Promise.resolve(null) as never;
  });
}

/** 打开企业下拉并选中第一家企业 */
async function selectCompany() {
  const combos = screen.getAllByRole('combobox');
  fireEvent.mouseDown(combos[0]);
  await waitFor(() => expect(screen.getByText(/测试企业/)).toBeInTheDocument());
  fireEvent.click(screen.getByText(/测试企业/));
}

function renderPage() {
  return renderWithProviders(<TaxRiskProfile />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('TaxRiskProfile', () => {
  it('未选择企业时给出空态提示，且不请求画像接口', async () => {
    mockApi(() => Promise.resolve(profile()));
    renderPage();

    await waitFor(() => expect(screen.getByText(/请先选择企业/)).toBeInTheDocument());
    const calledRiskProfile = get.mock.calls.some(([url]) =>
      String(url).includes('/risk-profile'),
    );
    expect(calledRiskProfile).toBe(false);
  });

  it('选择企业后按年度请求画像并渲染汇总卡、风险项与趋势标记', async () => {
    mockApi(() => Promise.resolve(profile()));
    renderPage();

    await waitFor(() => expect(get).toHaveBeenCalledWith('/scrm/companies', expect.anything()));
    await selectCompany();

    await waitFor(() =>
      expect(get).toHaveBeenCalledWith('/scrm/tax-calendar/companies/1/risk-profile', {
        params: { year: new Date().getFullYear() },
      }),
    );

    // 汇总卡：申报记录数 / 逾期数 / 欠缴数 / 风险项数
    const recordCard = screen.getByText(/申报记录数/).closest('.ant-card') as HTMLElement;
    expect(within(recordCard).getByText('12')).toBeInTheDocument();
    const riskCard = screen.getByText(/风险项数/).closest('.ant-card') as HTMLElement;
    expect(within(riskCard).getByText('2')).toBeInTheDocument();

    // 风险项：级别 Tag + 消息 + 建议
    expect(screen.getAllByText(/严重/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/警告/).length).toBeGreaterThan(0);
    expect(screen.getByText('申报逾期率 25.0%，超过 20% 阈值')).toBeInTheDocument();
    expect(screen.getByText('核对收入完整性与进项抵扣')).toBeInTheDocument();

    // 趋势：月份 / 收入千分位 / 税负率原样 / flag 文案
    expect(screen.getByText('2026-01')).toBeInTheDocument();
    expect(screen.getByText('123,400.50')).toBeInTheDocument();
    expect(screen.getByText('0.8%')).toBeInTheDocument();
    expect(screen.getByText(/偏低/)).toBeInTheDocument();
    expect(screen.getByText(/正常/)).toBeInTheDocument();
    expect(screen.getByText(/偏高/)).toBeInTheDocument();
    expect(screen.getAllByText(/数据不足/).length).toBeGreaterThan(0);
  });

  it('该年度无申报记录时展示空态', async () => {
    mockApi(() =>
      Promise.resolve(profile({ recordTotal: 0, overdueCount: 0, underpaidCount: 0, trend: [], risks: [] })),
    );
    renderPage();

    await selectCompany();

    await waitFor(() => expect(screen.getByText(/该年度暂无申报记录/)).toBeInTheDocument());
    // 无记录时不渲染汇总卡
    expect(screen.queryByText(/申报记录数/)).not.toBeInTheDocument();
  });

  it('接口失败时不崩溃，且不残留旧画像', async () => {
    mockApi(() => Promise.reject(new Error('boom')));
    renderPage();

    await selectCompany();

    await waitFor(() =>
      expect(get).toHaveBeenCalledWith(
        '/scrm/tax-calendar/companies/1/risk-profile',
        expect.anything(),
      ),
    );
    // 查询栏仍在，汇总卡未渲染
    expect(screen.getByText(/^年度$/)).toBeInTheDocument();
    expect(screen.getByText(/暂无画像数据/)).toBeInTheDocument();
    expect(screen.queryByText(/申报记录数/)).not.toBeInTheDocument();
  });
});
