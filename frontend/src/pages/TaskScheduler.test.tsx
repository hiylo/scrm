/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : TaskScheduler.test.tsx
 * Description : 任务调度页面的任务加载 / 新建 / 执行 / 记录测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import TaskScheduler from './TaskScheduler';
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
  return {
    id,
    taskName: `任务${id}`,
    taskCode: `T-${id}`,
    taskCategory: 'SYNC',
    taskType: 'CRON',
    cronExpression: '0 0 2 * * ?',
    handlerClass: 'com.example.Job',
    handlerMethod: 'run',
    status: 'ACTIVE',
    timeoutSeconds: 60,
    ...overrides,
  } as never;
}

/** 执行行 */
function exec(id: string, overrides: Record<string, unknown> = {}) {
  return { id, taskCode: 'T-1', executionNo: `EX-${id}`, triggerType: 'MANUAL', status: 'SUCCESS', durationMs: 100, startedAt: '2026-10-08T10:00:00', ...overrides } as never;
}

function renderPage() {
  return renderWithProviders(<TaskScheduler />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('TaskScheduler', () => {
  it('加载定时任务列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/task-scheduler/list')) return Promise.resolve(page([task('1'), task('2')]));
      if (url.includes('/executions/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('任务1')).toBeInTheDocument());
    expect(screen.getByText('任务2')).toBeInTheDocument();
  });

  it('新建任务: 打开弹窗填写后 POST', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/task-scheduler/list')) return Promise.resolve(page([]));
      if (url.includes('/executions/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(task('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('新建任务') }));
    await waitFor(() => expect(screen.getByLabelText('任务名称')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('任务名称'), '每日同步');
    await userEvent.type(screen.getByLabelText('任务编码'), 'DAILY_SYNC');
    await userEvent.type(screen.getByLabelText('Cron 表达式'), '0 0 2 * * ?');
    await userEvent.type(screen.getByLabelText('处理类'), 'com.example.Job');
    await userEvent.type(screen.getByLabelText('处理方法'), 'run');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/task-scheduler', expect.objectContaining({ taskName: '每日同步', taskCode: 'DAILY_SYNC' })));
  });

  it('手动执行: 调用 executions/execute', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/task-scheduler/list')) return Promise.resolve(page([task('1')]));
      if (url.includes('/executions/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(exec('1'));

    renderPage();
    await waitFor(() => expect(screen.getByText('任务1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('执行') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/task-scheduler/executions/execute', expect.objectContaining({ taskId: 1 })));
  });

  it('切换到执行记录 Tab 加载', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/task-scheduler/list')) return Promise.resolve(page([]));
      if (url.includes('/executions/list')) return Promise.resolve(page([exec('1')]));
      return Promise.resolve(page([]));
    });

    renderPage();
    fireEvent.click(screen.getByRole('tab', { name: '执行记录' }));

    await waitFor(() => expect(screen.getByText('EX-1')).toBeInTheDocument());
  });
});