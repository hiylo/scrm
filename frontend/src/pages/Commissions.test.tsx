/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Commissions.test.tsx
 * Description : 佣金结算页面的加载 / 新建 / 计算 / 支付测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Commissions from './Commissions';
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

/** 佣金行 */
function commission(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    commissionNo: `YJ-2026${id}`,
    salespersonId: 'u1',
    salespersonName: '小张',
    commissionType: 'SALES',
    commissionAmount: 500,
    orderNo: 'DD-100',
    status: 'CALCULATED',
    createTime: '2026-10-08T10:00:00',
    ...overrides,
  } as never;
}

function renderPage() {
  return renderWithProviders(<Commissions />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('Commissions', () => {
  it('加载佣金列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/commissions/records/list')) return Promise.resolve(page([commission('1'), commission('2')]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('YJ-20261')).toBeInTheDocument());
    expect(screen.getByText('YJ-20262')).toBeInTheDocument();
  });

  it('冲正佣金: 已支付调用 clawback', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/commissions/records/list')) return Promise.resolve(page([commission('1', { status: 'PAID' })]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(commission('1', { status: 'REVERSED' }));

    renderPage();
    await waitFor(() => expect(screen.getByText('YJ-20261')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('冲正') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/commissions/payouts/clawback', expect.objectContaining({ recordIds: [1] })));
  });

  it('触发计算: 调用 records/calculate', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/commissions/records/list')) return Promise.resolve(page([commission('1')]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(commission('1'));

    renderPage();
    await waitFor(() => expect(screen.getByText('YJ-20261')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('计算') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/commissions/records/calculate', expect.anything()));
  });

  it('支付佣金: 已计算调用 mark-paid', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/commissions/records/list')) return Promise.resolve(page([commission('1')]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(commission('1', { status: 'PAID' }));

    renderPage();
    await waitFor(() => expect(screen.getByText('YJ-20261')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('支付') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/commissions/payouts/1/mark-paid'));
  });
});