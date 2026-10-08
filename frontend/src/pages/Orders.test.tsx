/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Orders.test.tsx
 * Description : 产品订单页面的加载 / 新建 / 状态流转 / 明细测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Orders from './Orders';
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

/** 订单行 */
function order(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    orderNo: `DD-2026${id}`,
    customerId: '1',
    customerName: '张三',
    orderType: 'PRODUCT',
    productName: '基础套餐',
    orderAmount: 2000,
    orderStatus: 'PENDING',
    createdAt: '2026-10-08T10:00:00',
    ...overrides,
  } as never;
}

function renderPage() {
  return renderWithProviders(<Orders />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('Orders', () => {
  it('加载订单列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/orders/list')) return Promise.resolve(page([order('1'), order('2')]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('DD-20261')).toBeInTheDocument());
    expect(screen.getByText('DD-20262')).toBeInTheDocument();
  });

  it('新建订单: 打开弹窗填写后 POST', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/orders/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(order('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('新建订单') }));
    await waitFor(() => expect(screen.getByLabelText('客户 ID')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('客户 ID'), '1');
    await userEvent.type(screen.getByLabelText('订单金额 (¥)'), '2000');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/orders', expect.objectContaining({ customerId: '1', orderAmount: 2000 })));
  });

  it('确认订单: 待确认调用 confirm', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/orders/list')) return Promise.resolve(page([order('1')]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(order('1', { orderStatus: 'CONFIRMED' }));

    renderPage();
    await waitFor(() => expect(screen.getByText('DD-20261')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('确认') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/orders/1/confirm', undefined));
  });

  it('查看订单明细: 打开抽屉加载 items', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/orders/list')) return Promise.resolve(page([order('1', { orderStatus: 'COMPLETED' })]));
      if (url.includes('/items')) {
        return Promise.resolve([{ id: 'i1', productName: '基础套餐', quantity: 1, unitPrice: 2000, totalPrice: 2000 }]);
      }
      return Promise.resolve(page([]));
    });

    renderPage();
    await waitFor(() => expect(screen.getByText('DD-20261')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('明细') }));

    await waitFor(() => expect(screen.getByText('基础套餐')).toBeInTheDocument());
    expect(screen.getAllByText('¥2,000').length).toBeGreaterThan(0);
  });
});