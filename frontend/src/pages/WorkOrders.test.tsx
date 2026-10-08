/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : WorkOrders.test.tsx
 * Description : 工单管理页面的加载 / 新建 / 状态流转 / 明细测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import WorkOrders from './WorkOrders';
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

/** 工单行 */
function wo(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    orderNo: `WO-2026${id}`,
    title: `工单${id}`,
    orderType: 'SERVICE',
    priority: 'MEDIUM',
    status: 'PENDING',
    createTime: '2026-10-08T10:00:00',
    ...overrides,
  } as never;
}

function renderPage() {
  return renderWithProviders(<WorkOrders />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('WorkOrders', () => {
  it('加载工单列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/orders/list')) return Promise.resolve(page([wo('1'), wo('2')]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('WO-20261')).toBeInTheDocument());
    expect(screen.getByText('WO-20262')).toBeInTheDocument();
  });

  it('新建工单: 打开弹窗填写后 POST', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/orders/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(wo('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('新建工单') }));
    await waitFor(() => expect(screen.getByLabelText('工单标题')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('工单标题'), '系统故障');
    // 工单类型/优先级已在 openCreate 预填 (orderType=SERVICE, priority=MEDIUM), 无需重复输入
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/work-orders/orders', expect.objectContaining({ title: '系统故障', orderType: 'SERVICE' })));
  });

  it('分配工单: 待分配打开弹窗后 assign', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/orders/list')) return Promise.resolve(page([wo('1')]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(wo('1', { status: 'ASSIGNED' }));

    renderPage();
    await waitFor(() => expect(screen.getByText('WO-20261')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('分配') }));
    await waitFor(() => expect(screen.getByLabelText('处理人用户 ID')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('处理人用户 ID'), 'u1');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/work-orders/orders/assign', expect.objectContaining({ orderId: 1, assigneeId: 'u1' })));
  });

  it('查看明细: 打开抽屉加载日志', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/orders/list')) return Promise.resolve(page([wo('1', { status: 'IN_PROGRESS' })]));
      if (url.includes('/logs')) {
        return Promise.resolve([{ id: 'l1', orderId: '1', action: 'START', operatorName: '小李', detail: '开始处理', createdAt: '2026-10-08T10:00:00' }]);
      }
      return Promise.resolve(page([]));
    });

    renderPage();
    await waitFor(() => expect(screen.getByText('WO-20261')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('明细') }));

    await waitFor(() => expect(screen.getByText('开始处理')).toBeInTheDocument());
  });
});