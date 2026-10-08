/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : MassSends.test.tsx
 * Description : 群发任务管理页面的加载 / 创建 / 发布 / 目标明细测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import MassSends from './MassSends';
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
    platformType: 'wework',
    content: '您好, 618 活动来了!',
    targetType: 'ALL',
    senderAccountId: '1',
    status: 'DRAFT',
    totalCount: 0,
    sentCount: 0,
    successCount: 0,
    failCount: 0,
    createTime: '2026-10-08T10:00:00',
    ...overrides,
  } as never;
}

function renderPage() {
  return renderWithProviders(<MassSends />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('MassSends', () => {
  it('加载并展示群发任务列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/mass-send?')) return Promise.resolve(page([task('1', { status: 'RUNNING', totalCount: 100, sentCount: 30 }), task('2')]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('任务1')).toBeInTheDocument());
    expect(screen.getByText('任务2')).toBeInTheDocument();
    expect(screen.getByText('已发 30/100 (30%)')).toBeInTheDocument();
  });

  it('创建任务: 打开弹窗填写后 POST 并刷新列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/mass-send?')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(task('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('创建任务') }));
    await waitFor(() => expect(screen.getByLabelText('任务名称')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('任务名称'), '618 关怀');
    await userEvent.type(screen.getByLabelText('群发内容'), '你好, 欢迎参加活动');
    await userEvent.type(screen.getByLabelText('发送账号 ID'), '1');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/mass-send', expect.objectContaining({ taskName: '618 关怀' })));
  });

  it('发布任务: 点击发布调用 publish', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/mass-send?')) return Promise.resolve(page([task('1')]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(task('1', { status: 'RUNNING' }));

    renderPage();
    await waitFor(() => expect(screen.getByText('任务1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('发布') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/mass-send/1/publish'));
  });

  it('查看目标明细: 打开抽屉加载报告与明细', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/report')) {
        return Promise.resolve({ taskId: '1', taskName: '任务1', status: 'COMPLETED', totalCount: 100, sentCount: 100, successCount: 90, failCount: 10, pendingCount: 0, successRate: 90, failureRate: 10 });
      }
      if (url.includes('/targets')) {
        return Promise.resolve(page([{ id: 't1', taskId: '1', customerId: 'c1', customerNickname: '张三', status: 'SENT', sentAt: '2026-10-08T10:00:00' }]));
      }
      if (url.startsWith('/scrm/mass-send?')) return Promise.resolve(page([task('1')]));
      return Promise.resolve(page([]));
    });

    renderPage();
    await waitFor(() => expect(screen.getByText('任务1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('明细') }));

    await waitFor(() => expect(screen.getByText('目标总数')).toBeInTheDocument());
    expect(screen.getByText('张三')).toBeInTheDocument();
  });
});