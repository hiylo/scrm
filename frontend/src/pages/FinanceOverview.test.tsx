/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : FinanceOverview.test.tsx
 * Description : 财税概览页的统计口径、待申报清单、续费提醒与降级测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import FinanceOverview from './FinanceOverview';
import { apiClient } from '../api/client';
import { PathProbe, renderWithProviders } from '../test/render';

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
  return { content: rows, totalElements: total, totalPages: 1, number: 0, size: 10 } as never;
}

/** 申报记录行 */
function record(
  id: string,
  declarationStatus: string,
  dueDate: string,
  overrides: Record<string, unknown> = {},
) {
  return {
    id,
    profileId: `P-${id}`,
    customerId: `C-${id}`,
    taxType: '增值税',
    periodStart: '2026-10-01',
    periodEnd: '2026-10-31',
    dueDate,
    declarationStatus,
    ...overrides,
  } as never;
}

/** 续费任务行 */
function renewal(id: string, status: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    taskNo: `R-${id}`,
    contractId: `CT-${id}`,
    contractName: `合同${id}`,
    plannedRenewalDate: '2026-11-01',
    status,
    ...overrides,
  } as never;
}

/**
 * 按 URL 片段分发 mock 响应。
 * <p>未显式给出的接口默认返回空分页，避免用例之间互相影响。</p>
 */
function routeMock(handlers: Record<string, () => unknown>) {
  get.mockImplementation((url: string) => {
    for (const [fragment, fn] of Object.entries(handlers)) {
      if (url.includes(fragment)) return Promise.resolve(fn() as never);
    }
    // 申报记录接口返回数组，其余为分页结构
    if (url.includes('/scrm/tax-calendar/records/month')) return Promise.resolve([] as never);
    return Promise.resolve(page([], 0) as never);
  });
}

/** 取统计卡片的数值文本（antd Statistic 内容节点） */
function statValue(title: string): string {
  const card = screen.getByText(title).closest('.ant-card') as HTMLElement;
  return card.querySelector('.ant-statistic-content-value')?.textContent ?? '';
}

describe('FinanceOverview 财税概览', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('并行拉取四个财税域接口，统计卡片按财税口径渲染', async () => {
    routeMock({
      '/scrm/tax-calendar/records/month': () => [
        record('1', 'NOT_STARTED', '2026-10-20'),
        record('2', 'FILED', '2026-10-25'),
        record('3', 'OVERDUE', '2026-10-10'),
      ],
      '/scrm/compliance/qualifications/summary': () => ({
        valid: 5,
        expiring: 2,
        expired: 1,
        renewed: 0,
        dueSoon90d: 3,
        total: 8,
      }),
      '/scrm/renewals': () => page([renewal('1', 'PENDING'), renewal('2', 'RENEWED')], 2),
      '/scrm/companies': () => page([{ id: '372201427621683200' }], 12),
    });

    renderWithProviders(<FinanceOverview />);

    await waitFor(() => expect(screen.getByText('在管主体')).toBeInTheDocument());

    // 在管主体取 listCompanies.totalElements，而非资质档案数 total=8
    expect(statValue('在管主体')).toBe('12');
    // 本月待申报只算未完成口径（NOT_STARTED + OVERDUE，FILED 不计）
    expect(statValue('本月待申报')).toBe('2');
    // 资质临期取 expiring
    expect(statValue('资质临期')).toBe('2');
    // 待续费只算 PENDING / REMINDED
    expect(statValue('待续费')).toBe('1');
  });

  it('本月待申报清单列出未完成记录，已申报的不展示', async () => {
    routeMock({
      '/scrm/tax-calendar/records/month': () => [
        record('1', 'NOT_STARTED', '2026-10-20', { taxType: '增值税' }),
        record('2', 'FILED', '2026-10-25', { taxType: '企业所得税' }),
      ],
    });

    renderWithProviders(<FinanceOverview />);

    await waitFor(() => expect(screen.getByText('增值税')).toBeInTheDocument());
    expect(screen.queryByText('企业所得税')).not.toBeInTheDocument();
  });

  it('本月申报全部完成时展示空态而非空白列表', async () => {
    routeMock({
      '/scrm/tax-calendar/records/month': () => [record('1', 'PAID', '2026-10-20')],
    });

    renderWithProviders(<FinanceOverview />);

    await waitFor(() =>
      expect(screen.getByText('本月申报已全部完成')).toBeInTheDocument(),
    );
  });

  it('部分接口失败时展示降级提示，其余区块照常渲染', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/scrm/compliance/qualifications/summary')) {
        return Promise.reject(new Error('boom')) as never;
      }
      if (url.includes('/scrm/tax-calendar/records/month')) {
        return Promise.resolve([record('1', 'NOT_STARTED', '2026-10-20')] as never);
      }
      return Promise.resolve(page([], 0) as never);
    });

    renderWithProviders(<FinanceOverview />);

    await waitFor(() => expect(screen.getByText(/合规资质统计/)).toBeInTheDocument());
    // 申报区块不受影响
    expect(screen.getByText('本月待申报')).toBeInTheDocument();
  });

  it('点击「本月待申报」卡片跳转到报税日历', async () => {
    routeMock({
      '/scrm/tax-calendar/records/month': () => [record('1', 'NOT_STARTED', '2026-10-20')],
    });

    renderWithProviders(
      <Routes>
        <Route path="/" element={<FinanceOverview />} />
        <Route path="/tax-calendar" element={<PathProbe />} />
      </Routes>,
    );

    await waitFor(() => expect(screen.getByText('本月待申报')).toBeInTheDocument());
    const pendingCard = screen.getByText('本月待申报').closest('.ant-card') as HTMLElement;
    await userEvent.click(pendingCard);

    await waitFor(() =>
      expect(screen.getByTestId('path')).toHaveTextContent('/tax-calendar'),
    );
  });

  it('点击待续费清单项跳转到服务续费', async () => {
    routeMock({
      '/scrm/renewals': () => page([renewal('1', 'PENDING', { contractName: '鼎昊代账合同' })], 1),
    });

    renderWithProviders(
      <Routes>
        <Route path="/" element={<FinanceOverview />} />
        <Route path="/renewals" element={<PathProbe />} />
      </Routes>,
    );

    await waitFor(() => expect(screen.getByText('鼎昊代账合同')).toBeInTheDocument());
    await userEvent.click(screen.getByText('鼎昊代账合同'));

    await waitFor(() => expect(screen.getByTestId('path')).toHaveTextContent('/renewals'));
  });
});
