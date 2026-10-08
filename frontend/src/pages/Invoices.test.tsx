/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Invoices.test.tsx
 * Description : 发票管理页面的加载 / 申请 / 开具 / 作废测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Invoices from './Invoices';
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

/** 发票行 */
function invoice(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    invoiceNo: `FP-2026${id}`,
    invoiceType: 'GENERAL',
    customerId: '1',
    customerName: '张三',
    invoiceTitle: '某某科技',
    taxAmount: 60,
    totalAmount: 1060,
    status: 'PENDING',
    createTime: '2026-10-08T10:00:00',
    ...overrides,
  } as never;
}

function renderPage() {
  return renderWithProviders(<Invoices />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('Invoices', () => {
  it('加载发票列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/invoices/list')) return Promise.resolve(page([invoice('1'), invoice('2')]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('FP-20261')).toBeInTheDocument());
    expect(screen.getByText('FP-20262')).toBeInTheDocument();
  });

  it('申请发票: 打开弹窗填写后 POST apply', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/invoices/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(invoice('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('新建发票') }));
    await waitFor(() => expect(screen.getByLabelText('客户 ID')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('客户 ID'), '1');
    await userEvent.type(screen.getByLabelText('发票抬头'), '某某科技');
    await userEvent.type(screen.getByLabelText('不含税金额 (¥)'), '1000');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/invoices/apply', expect.objectContaining({ customerId: '1', invoiceTitle: '某某科技', amount: 1000 })));
  });

  it('开具发票: 待开调用 issue', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/invoices/list')) return Promise.resolve(page([invoice('1')]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(invoice('1', { status: 'ISSUED' }));

    renderPage();
    await waitFor(() => expect(screen.getByText('FP-20261')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('开具') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/invoices/1/issue'));
  });

  it('作废发票: 已开调用 void', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/invoices/list')) return Promise.resolve(page([invoice('1', { status: 'ISSUED' })]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(invoice('1', { status: 'VOIDED' }));

    renderPage();
    await waitFor(() => expect(screen.getByText('FP-20261')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('作废') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/invoices/void?id=1', expect.anything()));
  });
});