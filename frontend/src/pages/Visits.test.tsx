/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Visits.test.tsx
 * Description : 客户拜访页面的计划加载 / 新建计划 / 任务完成测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Visits from './Visits';
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
  return { id, planName: `计划${id}`, planCode: `VP-${id}`, frequency: 'QUARTERLY', status: 'ACTIVE', ...overrides } as never;
}

/** 任务行 */
function task(id: string, overrides: Record<string, unknown> = {}) {
  return { id, taskName: `任务${id}`, customerId: '1', customerName: '张三', visitType: 'ONSITE', status: 'PENDING', plannedAt: '2026-10-10T10:00:00', ...overrides } as never;
}

function renderPage() {
  return renderWithProviders(<Visits />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('Visits', () => {
  it('加载拜访计划列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/plans/list')) return Promise.resolve(page([plan('1'), plan('2')]));
      if (url.includes('/tasks/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('计划1')).toBeInTheDocument());
    expect(screen.getByText('计划2')).toBeInTheDocument();
  });

  it('新建计划: 打开弹窗填写后 POST', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/plans/list')) return Promise.resolve(page([]));
      if (url.includes('/tasks/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(plan('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('新建计划') }));
    await waitFor(() => expect(screen.getByLabelText('计划名称')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('计划名称'), '季度拜访');
    await userEvent.type(screen.getByLabelText('计划编码'), 'VISIT_Q');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/visits/plans', expect.objectContaining({ planName: '季度拜访', planCode: 'VISIT_Q' })));
  });

  it('完成任务: 切到任务 Tab 调 complete', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/plans/list')) return Promise.resolve(page([]));
      if (url.includes('/tasks/list')) return Promise.resolve(page([task('1')]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(task('1', { status: 'COMPLETED' }));

    renderPage();
    fireEvent.click(screen.getByRole('tab', { name: '拜访任务' }));
    await waitFor(() => expect(screen.getByText('任务1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('完成') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/visits/tasks/1/complete', expect.anything()));
  });
});