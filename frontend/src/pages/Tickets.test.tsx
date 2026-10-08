/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Tickets.test.tsx
 * Description : 客户工单页面的加载 / 创建 / 状态流转 / 详情抽屉测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Tickets from './Tickets';
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
function ticket(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    ticketNo: `TK-2026000${id}`,
    title: `工单${id}`,
    customerId: '1',
    customerName: '张三',
    category: 'PRODUCT_ISSUE',
    priority: 'MEDIUM',
    status: 'OPEN',
    source: 'CUSTOMER',
    assigneeName: '客服A',
    createTime: '2026-10-08T10:00:00',
    ...overrides,
  } as never;
}

function renderPage() {
  return renderWithProviders(<Tickets />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('Tickets', () => {
  it('加载并展示工单列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/tickets/list')) return Promise.resolve(page([ticket('1'), ticket('2', { priority: 'URGENT', status: 'IN_PROGRESS' })]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('工单1')).toBeInTheDocument());
    expect(screen.getByText('工单2')).toBeInTheDocument();
    expect(screen.getByText('TK-20260001')).toBeInTheDocument();
  });

  it('创建工单: 打开弹窗填写后 POST', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/tickets/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(ticket('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('创建工单') }));
    await waitFor(() => expect(screen.getByLabelText('工单标题')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('工单标题'), '到货破损');
    await userEvent.type(screen.getByLabelText('客户 ID'), '1');
    // 类别 Select 必填: mouseDown 打开下拉选「产品问题」
    fireEvent.mouseDown(screen.getByLabelText('类别'));
    await waitFor(() => expect(screen.getAllByText('产品问题').length).toBeGreaterThan(0));
    fireEvent.click(screen.getAllByText('产品问题').pop()!);
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/tickets', expect.objectContaining({ title: '到货破损' })));
  });

  it('状态流转: OPEN 开始处理调 IN_PROGRESS', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/tickets/list')) return Promise.resolve(page([ticket('1')]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(ticket('1', { status: 'IN_PROGRESS' }));

    renderPage();
    await waitFor(() => expect(screen.getByText('工单1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('开始') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/tickets/1/status', expect.objectContaining({ status: 'IN_PROGRESS' })));
  });

  it('查看详情: 打开抽屉加载评论与历史', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/tickets/list')) return Promise.resolve(page([ticket('1')]));
      if (url.includes('/comments/')) return Promise.resolve([{ id: 'c1', content: '请尽快处理', authorName: '张三', isInternal: false, createdAt: '2026-10-08T10:00:00' }]);
      if (url.includes('/history/')) return Promise.resolve([{ id: 'h1', action: 'CREATED', toValue: 'OPEN', operatorName: '客服A', createdAt: '2026-10-08T10:00:00' }]);
      return Promise.resolve(page([]));
    });

    renderPage();
    await waitFor(() => expect(screen.getByText('工单1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('详情') }));

    await waitFor(() => expect(screen.getByText('请尽快处理')).toBeInTheDocument());
    expect(screen.getByText('CREATED')).toBeInTheDocument();
  });
});