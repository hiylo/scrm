/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : FollowUps.test.tsx
 * Description : 客户跟进页面的任务加载 / 新建任务 / 完成任务测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import FollowUps from './FollowUps';
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

/** 任务行 */
function task(id: string, overrides: Record<string, unknown> = {}) {
  return { id, taskName: `任务${id}`, customerId: '1', customerName: '张三', followType: 'PHONE', status: 'PENDING', plannedAt: '2026-10-10T10:00:00', ...overrides } as never;
}

/** 记录行 */
function record(id: string, overrides: Record<string, unknown> = {}) {
  return { id, taskId: '1', customerId: '1', customerName: '张三', content: '已电话沟通', channel: 'PHONE', createdAt: '2026-10-10T10:00:00', ...overrides } as never;
}

function renderPage() {
  return renderWithProviders(<FollowUps />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('FollowUps', () => {
  it('加载跟进任务列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/tasks/list')) return Promise.resolve(page([task('1'), task('2')]));
      if (url.includes('/records/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('任务1')).toBeInTheDocument());
    expect(screen.getByText('任务2')).toBeInTheDocument();
  });

  it('新建任务: 打开弹窗填写后 POST', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/tasks/list')) return Promise.resolve(page([]));
      if (url.includes('/records/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(task('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('新建任务') }));
    await waitFor(() => expect(screen.getByLabelText('任务名称')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('任务名称'), '跟进意向客户');
    await userEvent.type(screen.getByLabelText('客户 ID'), '1');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/follow-ups/tasks', expect.objectContaining({ taskName: '跟进意向客户', customerId: '1' })));
  });

  it('完成任务: 调 complete', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/tasks/list')) return Promise.resolve(page([task('1')]));
      if (url.includes('/records/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(task('1', { status: 'COMPLETED' }));

    renderPage();
    await waitFor(() => expect(screen.getByText('任务1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('完成') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/follow-ups/tasks/1/complete', expect.anything()));
  });

  it('查看跟进记录: 切到记录 Tab 加载', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/tasks/list')) return Promise.resolve(page([task('1', { status: 'COMPLETED' })]));
      if (url.includes('/records/list')) return Promise.resolve(page([record('1')]));
      return Promise.resolve(page([]));
    });

    renderPage();
    fireEvent.click(screen.getByRole('tab', { name: '跟进记录' }));

    await waitFor(() => expect(screen.getByText('已电话沟通')).toBeInTheDocument());
  });
});