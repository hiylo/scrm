/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ClientPortal.test.tsx
 * Description : 客户查账页面的企业选择 / 公司概况 / 报税进度 / 报表 / 空态与降级测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent, within } from '@testing-library/react';
import ClientPortal from './ClientPortal';
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

/** 查账全景响应 */
function overview(overrides: Record<string, unknown> = {}) {
  return {
    companyId: '1',
    year: new Date().getFullYear(),
    company: {
      companyName: '测试企业',
      creditCode: '91110000000000000X',
      legalRepresentative: '张三',
      companyType: '有限责任公司',
      registeredCapital: '100.00 万元',
      industry: '软件和信息技术服务业',
      businessStatus: 'NORMAL',
      taxStatus: 'NORMAL',
      serviceStatus: 'ACTIVE',
      serviceEndDate: '2026-12-31',
      accountantName: '李会计',
    },
    taxSummary: {
      recordTotal: 8,
      pendingCount: 3,
      filedCount: 4,
      overdueCount: 2,
      underpaidCount: 1,
      recentRecords: [
        {
          id: 'R1',
          taxType: '增值税',
          periodStart: '2026-10-01',
          periodEnd: '2026-10-31',
          dueDate: '2026-11-15',
          taxAmount: 12345.6,
          paidAmount: 0,
          declarationStatus: 'PENDING',
          overdueDays: 0,
        },
        {
          id: 'R2',
          taxType: '企业所得税',
          periodStart: '2026-09-01',
          periodEnd: '2026-09-30',
          dueDate: '2026-10-20',
          taxAmount: 8000,
          paidAmount: 0,
          declarationStatus: 'OVERDUE',
          overdueDays: 5,
        },
      ],
    },
    reports: {
      year: new Date().getFullYear(),
      latestMonth: 10,
      incomeStatements: [
        { period: 202608, revenueCurrent: 100000, revenueYtd: 800000, netProfitCurrent: 20000 },
        { period: 202609, revenueCurrent: 120000, revenueYtd: 920000, netProfitCurrent: -5000 },
        { period: 202610, revenueCurrent: 150000, revenueYtd: 1070000, netProfitCurrent: 30000 },
      ],
      balanceSheet: {
        period: 202610,
        totalAssetClosing: 500000,
        totalLiabilityClosing: 200000,
        totalEquityClosing: 300000,
        totalLiabilityAndEquityClosing: 500000,
        balanced: true,
      },
    },
    assets: {
      assetCount: 3,
      originalTotal: 60000,
      accumulatedTotal: 12000,
      netTotal: 48000,
    },
    taxRisk: {
      recordTotal: 8,
      overdueCount: 2,
      underpaidCount: 1,
      risks: [
        { level: 'CRITICAL', message: '申报逾期率 25.0%，超过 20% 阈值', suggestion: '补齐逾期申报' },
        { level: 'WARN', message: '增值税税负率连续 3 个月低于 1.0%' },
      ],
      trend: [
        { month: '2026-09', revenue: '120000', taxRate: '0.8%', flag: 'LOW' },
        { month: '2026-10', revenue: '150000', taxRate: '2.1%', flag: 'NORMAL' },
      ],
      generatedAt: '2026-11-08 10:00:00',
    },
    ...overrides,
  } as never;
}

/** 按 URL 分派桩数据 */
function mockApi(portal: () => unknown) {
  get.mockImplementation((url: string) => {
    if (url === '/scrm/companies') return Promise.resolve(page([company('1', '测试企业')])) as never;
    if (url.includes('/scrm/client-portal/')) return Promise.resolve(portal()) as never;
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

/** 取某张卡内的统计数值 */
function statValue(title: string): string {
  const card = screen.getByText(title).closest('.ant-card') as HTMLElement;
  return card.querySelector('.ant-statistic-content-value')?.textContent ?? '';
}

describe('ClientPortal 客户查账', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('未选择企业时给出空态提示，且不请求查账接口', async () => {
    mockApi(() => overview());
    renderWithProviders(<ClientPortal />);

    await waitFor(() => expect(screen.getByText(/请先选择企业/)).toBeInTheDocument());
    const calledPortal = get.mock.calls.some(([url]) => String(url).includes('/client-portal/'));
    expect(calledPortal).toBe(false);
  });

  it('选择企业后按年度请求查账并渲染公司概况与四项统计', async () => {
    mockApi(() => overview());
    renderWithProviders(<ClientPortal />);

    await waitFor(() => expect(get).toHaveBeenCalledWith('/scrm/companies', expect.anything()));
    await selectCompany();

    await waitFor(() =>
      expect(get).toHaveBeenCalledWith('/scrm/client-portal/1/overview', {
        params: { year: new Date().getFullYear() },
      }),
    );

    // 公司概况：名称 + 税号 + 法人 + 会计
    await waitFor(() => expect(screen.getByText('纳税人识别号')).toBeInTheDocument());
    expect(screen.getByText('91110000000000000X')).toBeInTheDocument();
    expect(screen.getByText('张三')).toBeInTheDocument();
    expect(screen.getByText('李会计')).toBeInTheDocument();
    // 税务状态 NORMAL → 正常
    expect(screen.getAllByText('正常').length).toBeGreaterThan(0);

    // 四指标：记录数 / 待申报 / 逾期 / 欠缴
    expect(statValue('申报记录')).toBe('8');
    expect(statValue('待申报')).toBe('3');
    expect(statValue('逾期')).toBe('2');
    expect(statValue('欠缴')).toBe('1');
  });

  it('最近申报记录按状态渲染文案，金额千分位，逾期天数可见', async () => {
    mockApi(() => overview());
    renderWithProviders(<ClientPortal />);

    await waitFor(() => expect(get).toHaveBeenCalledWith('/scrm/companies', expect.anything()));
    await selectCompany();

    await waitFor(() => expect(screen.getByText('最近申报记录')).toBeInTheDocument());
    expect(screen.getByText('增值税')).toBeInTheDocument();
    expect(screen.getByText('企业所得税')).toBeInTheDocument();
    expect(screen.getByText('12,345.60')).toBeInTheDocument();
    expect(screen.getByText('申报中')).toBeInTheDocument();
    expect(screen.getByText('已逾期')).toBeInTheDocument();
    expect(screen.getByText('5 天')).toBeInTheDocument();
  });

  it('报表与固定资产、税负风险按块渲染', async () => {
    mockApi(() => overview());
    renderWithProviders(<ClientPortal />);

    await waitFor(() => expect(get).toHaveBeenCalledWith('/scrm/companies', expect.anything()));
    await selectCompany();

    await waitFor(() => expect(screen.getByText('财务报表')).toBeInTheDocument());
    // 利润表期间 yyyymm → YYYY-MM
    expect(screen.getByText('2026-08')).toBeInTheDocument();
    expect(screen.getByText('100,000.00')).toBeInTheDocument();
    // 资产负债表：平衡 Tag
    expect(screen.getByText('平衡')).toBeInTheDocument();
    // 固定资产
    expect(screen.getByText('固定资产')).toBeInTheDocument();
    expect(screen.getByText('48,000.00')).toBeInTheDocument();
    // 税负风险：严重级别 + 建议 + 趋势标记
    expect(screen.getByText('申报逾期率 25.0%，超过 20% 阈值')).toBeInTheDocument();
    expect(screen.getByText(/补齐逾期申报/)).toBeInTheDocument();
    expect(screen.getByText('0.8%')).toBeInTheDocument();
    expect(screen.getByText('偏低')).toBeInTheDocument();
  });

  it('区块为 null 时退化为空态，不渲染公司卡也不崩溃', async () => {
    mockApi(() =>
      overview({ company: null, taxSummary: null, reports: null, assets: null, taxRisk: null }),
    );
    renderWithProviders(<ClientPortal />);

    await waitFor(() => expect(get).toHaveBeenCalledWith('/scrm/companies', expect.anything()));
    await selectCompany();

    await waitFor(() => expect(screen.getByText('该企业暂无报税数据')).toBeInTheDocument());
    expect(screen.getByText('该企业暂无财务报表数据')).toBeInTheDocument();
    expect(screen.getByText('该企业暂无税负风险数据')).toBeInTheDocument();
    // company 为 null 时不渲染公司概况卡
    expect(screen.queryByText('纳税人识别号')).not.toBeInTheDocument();
    // assets 为 null 时整卡不渲染
    expect(screen.queryByText('固定资产')).not.toBeInTheDocument();
  });

  it('接口失败时给出提示并落到重试空态', async () => {
    mockApi(() => Promise.reject(new Error('boom')));
    renderWithProviders(<ClientPortal />);

    await waitFor(() => expect(get).toHaveBeenCalledWith('/scrm/companies', expect.anything()));
    await selectCompany();

    await waitFor(() =>
      expect(screen.getByText(/查账数据加载失败/)).toBeInTheDocument(),
    );
    expect(screen.getByText(/暂无查账数据/)).toBeInTheDocument();
    // 公司概况卡未渲染
    const card = screen.queryByText('纳税人识别号');
    expect(card).not.toBeInTheDocument();
  });

  it('切换年度后按新年度重新请求', async () => {
    mockApi(() => overview());
    renderWithProviders(<ClientPortal />);

    await waitFor(() => expect(get).toHaveBeenCalledWith('/scrm/companies', expect.anything()));
    await selectCompany();
    await waitFor(() =>
      expect(get).toHaveBeenCalledWith('/scrm/client-portal/1/overview', {
        params: { year: new Date().getFullYear() },
      }),
    );

    const combos = screen.getAllByRole('combobox');
    fireEvent.mouseDown(combos[1]);
    const lastYear = new Date().getFullYear() - 1;
    fireEvent.click(await screen.findByTitle(`${lastYear} 年`));

    await waitFor(() =>
      expect(get).toHaveBeenCalledWith('/scrm/client-portal/1/overview', {
        params: { year: lastYear },
      }),
    );
    // within 引用保持 lint 静默：统计卡断言已在上面用例使用
    const recordCard = screen.getByText('申报记录').closest('.ant-card') as HTMLElement;
    expect(within(recordCard).getByText('条')).toBeInTheDocument();
  });
});
