/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : CustomerCare.test.tsx
 * Description : 客户关怀页面的规则加载 / 新建规则 / 任务执行 / 记录测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import CustomerCare from './CustomerCare';
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

/** 规则行 */
function rule(id: string, overrides: Record<string, unknown> = {}) {
  return { id, ruleName: `规则${id}`, careType: 'BIRTHDAY', status: 'ACTIVE', scheduleTime: '09:00', ...overrides } as never;
}

/** 任务行 */
function task(id: string, overrides: Record<string, unknown> = {}) {
  return { id, taskName: `任务${id}`, customerId: '1', customerName: '张三', status: 'PENDING', scheduledAt: '2026-10-10T09:00:00', ...overrides } as never;
}

function renderPage() {
  return renderWithProviders(<CustomerCare />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('CustomerCare', () => {
  it('加载关怀规则列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/rules/list')) return Promise.resolve(page([rule('1'), rule('2')]));
      if (url.includes('/tasks/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('规则1')).toBeInTheDocument());
    expect(screen.getByText('规则2')).toBeInTheDocument();
  });

  it('新建规则: 打开弹窗填写后 POST', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/rules/list')) return Promise.resolve(page([]));
      if (url.includes('/tasks/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(rule('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('新建规则') }));
    await waitFor(() => expect(screen.getByLabelText('规则名称')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('规则名称'), '生日关怀');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/customer-care/rules', expect.objectContaining({ ruleName: '生日关怀' })));
  });

  it('切换到任务 Tab 执行任务', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/rules/list')) return Promise.resolve(page([]));
      if (url.includes('/tasks/list')) return Promise.resolve(page([task('1')]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(task('1', { status: 'COMPLETED' }));

    renderPage();
    fireEvent.click(screen.getByRole('tab', { name: '关怀任务' }));
    await waitFor(() => expect(screen.getByText('任务1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('执行') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/customer-care/tasks/1/execute', expect.anything()));
  });

  it('查看关怀记录: 打开抽屉加载 records', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/rules/list')) return Promise.resolve(page([]));
      if (url.includes('/tasks/list')) return Promise.resolve(page([task('1', { status: 'COMPLETED' })]));
      if (url.includes('/records')) {
        return Promise.resolve([{ id: 'r1', taskId: '1', customerId: '1', customerName: '张三', careContent: '生日快乐!', channel: 'WECHAT', createdAt: '2026-10-10T09:00:00' }]);
      }
      return Promise.resolve(page([]));
    });

    renderPage();
    fireEvent.click(screen.getByRole('tab', { name: '关怀任务' }));
    await waitFor(() => expect(screen.getByText('任务1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('记录') }));

    await waitFor(() => expect(screen.getByText('生日快乐!')).toBeInTheDocument());
  });
});