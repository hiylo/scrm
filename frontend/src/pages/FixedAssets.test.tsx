/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : FixedAssets.test.tsx
 * Description : 固定资产管理页的列表加载 / 新增卡片 / 计提折旧冒烟测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import FixedAssets from './FixedAssets';
import { apiClient } from '../api/client';
import { renderWithProviders } from '../test/render';
import { cn } from '../test/fixtures';

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

/** 分页响应 */
function page<T>(rows: T[], total = rows.length) {
  return { content: rows, totalElements: total, totalPages: 1, number: 0, size: 10 } as never;
}

/** 资产卡片行 */
function asset(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    companyId: '1',
    companyName: '测试财税公司',
    assetName: `设备${id}`,
    assetCategory: 'MACHINERY',
    originalValue: 120000,
    residualRate: 0.05,
    usefulLifeMonths: 60,
    startDate: '2026-01-01',
    boughtDate: '2025-12-20',
    accumulatedDepreciation: 1900,
    netValue: 118100,
    status: 'ACTIVE',
    ...overrides,
  } as never;
}

/** 汇总响应 */
function summary() {
  return {
    assetCount: 2,
    originalTotal: 240000,
    accumulatedTotal: 3800,
    netTotal: 236200,
  } as never;
}

/** 取汇总卡片的数值文本（antd Statistic 内容节点，整数与小数分段渲染） */
function statValue(title: string): string {
  const card = screen.getByText(title).closest('.ant-card') as HTMLElement | null;
  return card?.querySelector('.ant-statistic-content')?.textContent ?? '';
}

/** 按 URL 片段分发 mock */
function routeMock(assets: unknown = page([asset('1'), asset('2', { status: 'SCRAPPED' })])) {
  get.mockImplementation((url: string) => {
    if (url.includes('/scrm/companies')) {
      return Promise.resolve(page([{ id: '1', companyName: '测试财税公司', creditCode: '9133' }]) as never);
    }
    if (url.includes('/scrm/fixed-assets/company-summary')) return Promise.resolve(summary());
    if (url.includes('/scrm/fixed-assets')) return Promise.resolve(assets as never);
    return Promise.resolve(page([]) as never);
  });
}

/** 渲染并选中企业 */
async function renderAndPickCompany() {
  const user = userEvent.setup();
  renderWithProviders(<FixedAssets />);

  // 企业下拉：placeholder 渲染在 .ant-select-selection-placeholder 上
  await screen.findByText('搜索企业名称');
  await user.click(screen.getByText('搜索企业名称'));
  const option = await screen.findByText(/测试财税公司/, {
    selector: '.ant-select-item-option-content',
  });
  await user.click(option);
  return user;
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('FixedAssets', () => {
  it('选中企业后加载资产列表与汇总', async () => {
    routeMock();
    await renderAndPickCompany();

    expect(await screen.findByText('设备1')).toBeInTheDocument();
    expect(screen.getByText('设备2')).toBeInTheDocument();
    // 类别与状态中文映射
    expect(screen.getAllByText('机器设备').length).toBeGreaterThan(0);
    expect(screen.getByText('已报废')).toBeInTheDocument();
    // 汇总卡：数值走千分位 + 两位小数
    expect(statValue('原值合计')).toBe('¥240,000.00');
    expect(statValue('净值合计')).toBe('¥236,200.00');
    expect(statValue('资产数（在用）')).toBe('2项');

    const listCall = get.mock.calls.find(([url]) => String(url) === '/scrm/fixed-assets');
    expect(listCall?.[1]).toMatchObject({ params: { companyId: '1', page: 0, size: 10 } });
  });

  it('新增资产：弹窗校验必填，补齐后 POST 到后端并刷新', async () => {
    routeMock();
    const user = await renderAndPickCompany();
    await screen.findByText('设备1');

    await user.click(screen.getByRole('button', { name: /新增资产/ }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('资产类别')).toBeInTheDocument();

    // 资产名称为空 → 拦截提交
    await user.click(within(dialog).getByRole('button', { name: cn('确定') }));
    expect(await within(dialog).findByText('请输入资产名称')).toBeInTheDocument();
    expect(post).not.toHaveBeenCalled();

    await user.type(within(dialog).getByLabelText('资产名称'), '数控机床');
    await user.type(within(dialog).getByLabelText('原值（元）'), '50000');
    await user.click(within(dialog).getByRole('button', { name: cn('确定') }));

    await waitFor(() => expect(post).toHaveBeenCalledTimes(1));
    expect(post.mock.calls[0][0]).toBe('/scrm/fixed-assets');
    expect(post.mock.calls[0][1]).toMatchObject({
      assetName: '数控机床',
      assetCategory: 'OTHER',
      originalValue: 50000,
      residualRate: 0.05,
      usefulLifeMonths: 60,
    });
    expect(post.mock.calls[0][2]).toMatchObject({ params: { companyId: '1' } });
    // 提交成功后刷新列表
    await waitFor(() =>
      expect(
        get.mock.calls.filter(([url]) => String(url) === '/scrm/fixed-assets').length,
      ).toBeGreaterThan(1),
    );
  });

  it('计提本月折旧：提交期间与费用科目，并刷新列表与汇总', async () => {
    routeMock();
    post.mockResolvedValue({ count: 2, totalAmount: 3800, voucherId: '9001', message: '计提完成并生成凭证草稿' } as never);
    const user = await renderAndPickCompany();
    await screen.findByText('设备1');

    await user.click(screen.getByRole('button', { name: /计提本月折旧/ }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('会计年度')).toBeInTheDocument();

    await user.click(within(dialog).getByRole('button', { name: cn('开始计提') }));

    await waitFor(() => expect(post).toHaveBeenCalledTimes(1));
    expect(post.mock.calls[0][0]).toBe('/scrm/fixed-assets/depreciate');
    const params = (post.mock.calls[0][2] as { params: Record<string, unknown> }).params;
    expect(params.companyId).toBe('1');
    expect(typeof params.periodYear).toBe('number');
    expect(typeof params.periodMonth).toBe('number');
    // 费用科目默认留空 → 不发 expenseSubjectCode，由后端按企业科目体系自动探测
    // （新准则 6602 / 旧准则 5602 管理费用）
    expect(params.expenseSubjectCode).toBeUndefined();
    // 计提成功后刷新列表与汇总
    await waitFor(() =>
      expect(
        get.mock.calls.filter(([url]) => String(url).includes('/company-summary')).length,
      ).toBeGreaterThan(1),
    );
  });

  it('批量计提：不选企业也可提交，POST 到 batch-depreciate 并展示汇总', async () => {
    routeMock();
    post.mockResolvedValue({
      successCount: 2,
      failCount: 1,
      skippedCount: 1,
      totalCount: 4,
      totalAmount: 7600,
      details: [
        { companyId: '1', companyName: '测试财税公司', success: true, count: 2, totalAmount: 3800 },
        { companyId: '2', companyName: '另一家公司', success: false, message: '缺少可用科目「1602 累计折旧」' },
      ],
    } as never);
    const user = userEvent.setup();
    renderWithProviders(<FixedAssets />);

    // 不选企业直接点批量计提
    await user.click(await screen.findByRole('button', { name: /批量计提/ }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('批量计提折旧（我负责的全部主体）')).toBeInTheDocument();

    await user.click(within(dialog).getByRole('button', { name: cn('开始批量计提') }));

    await waitFor(() => expect(post).toHaveBeenCalledTimes(1));
    expect(post.mock.calls[0][0]).toBe('/scrm/fixed-assets/batch-depreciate');
    const params = (post.mock.calls[0][2] as { params: Record<string, unknown> }).params;
    expect(typeof params.periodYear).toBe('number');
    expect(typeof params.periodMonth).toBe('number');
    // 不传 companyId（范围由后端按登录会计的数据范围解析）
    expect(params.companyId).toBeUndefined();
    // 失败户原因要在提示里出现
    expect(await screen.findByText(/缺少可用科目/)).toBeInTheDocument();
  });

  it('未选企业时点新增给出提示且不发请求', async () => {
    routeMock();
    const user = userEvent.setup();
    renderWithProviders(<FixedAssets />);

    await user.click(screen.getByRole('button', { name: /新增资产/ }));
    expect(await screen.findByText('请先选择企业')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});
