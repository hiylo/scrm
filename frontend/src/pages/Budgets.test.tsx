/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Budgets.test.tsx
 * Description : 预算管理页面的计划加载 / 新建计划 / 状态流转 / 分配测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Budgets from './Budgets';
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

/** 计划行 */
function plan(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    planName: `计划${id}`,
    planCode: `B-${id}`,
    budgetPeriod: 'FY2026',
    totalBudget: 100000,
    usedBudget: 30000,
    remainingBudget: 70000,
    status: 'DRAFT',
    alertThreshold: 80,
    ...overrides,
  } as never;
}

/** 分配行 */
function alloc(id: string, overrides: Record<string, unknown> = {}) {
  return { id, planId: '1', channel: '公众号', campaignId: 'c1', allocatedAmount: 50000, usedAmount: 10000, status: 'ACTIVE', ...overrides } as never;
}

function renderPage() {
  return renderWithProviders(<Budgets />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('Budgets', () => {
  it('加载预算计划列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/plans/list')) return Promise.resolve(page([plan('1'), plan('2')]));
      if (url.includes('/allocations/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('计划1')).toBeInTheDocument());
    expect(screen.getByText('计划2')).toBeInTheDocument();
  });

  it('新建计划: 打开弹窗填写后 POST', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/plans/list')) return Promise.resolve(page([]));
      if (url.includes('/allocations/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(plan('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('新建计划') }));
    await waitFor(() => expect(screen.getByLabelText('计划名称')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('计划名称'), '2026 年度预算');
    await userEvent.type(screen.getByLabelText('计划编码'), 'BUDGET_2026');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/budgets/plans', expect.objectContaining({ planName: '2026 年度预算', planCode: 'BUDGET_2026' })));
  });

  it('审批计划: 草稿调用 approve', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/plans/list')) return Promise.resolve(page([plan('1')]));
      if (url.includes('/allocations/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(plan('1', { status: 'PENDING_APPROVAL' }));

    renderPage();
    await waitFor(() => expect(screen.getByText('计划1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('审批') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/budgets/plans/1/approve', expect.anything()));
  });

  it('切换到分配 Tab 加载分配', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/plans/list')) return Promise.resolve(page([]));
      if (url.includes('/allocations/list')) return Promise.resolve(page([alloc('1')]));
      return Promise.resolve(page([]));
    });

    renderPage();
    fireEvent.click(screen.getByRole('tab', { name: '预算分配' }));

    await waitFor(() => expect(screen.getByText('公众号')).toBeInTheDocument());
  });
});