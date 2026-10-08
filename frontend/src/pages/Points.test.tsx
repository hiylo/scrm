/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Points.test.tsx
 * Description : 积分管理页面的账户 / 规则 / 调账测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Points from './Points';
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

/** 账户行 */
function account(id: string, overrides: Record<string, unknown> = {}) {
  return { id, customerId: id, customerName: `客户${id}`, currentPoints: 100, frozenPoints: 0, totalEarned: 500, totalRedeemed: 400, level: 'L2', ...overrides } as never;
}

/** 规则行 */
function rule(id: string, name = `规则${id}`) {
  return { id, ruleName: name, ruleType: 'EARN', triggerEvent: 'ORDER_PAID', pointsValue: 10, pointsType: 'FIXED', enabled: true } as never;
}

function renderPage() {
  return renderWithProviders(<Points />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('Points', () => {
  it('加载积分账户 (默认 Tab)', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/accounts/list')) return Promise.resolve(page([account('1'), account('2')]));
      if (url.includes('/rules/list')) return Promise.resolve(page([]));
      if (url.includes('/transactions/list')) return Promise.resolve(page([]));
      if (url.includes('/exchanges/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('客户1')).toBeInTheDocument());
    expect(screen.getByText('客户2')).toBeInTheDocument();
  });

  it('切换到积分规则 Tab 并加载规则', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/accounts/list')) return Promise.resolve(page([]));
      if (url.includes('/rules/list')) return Promise.resolve(page([rule('1'), rule('2')]));
      if (url.includes('/transactions/list')) return Promise.resolve(page([]));
      if (url.includes('/exchanges/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });

    renderPage();
    fireEvent.click(screen.getByRole('tab', { name: '积分规则' }));

    await waitFor(() => expect(screen.getByText('规则1')).toBeInTheDocument());
    expect(screen.getByText('规则2')).toBeInTheDocument();
  });

  it('手动调账: 打开弹窗填写后 adjust', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/accounts/list')) return Promise.resolve(page([account('1')]));
      if (url.includes('/rules/list')) return Promise.resolve(page([]));
      if (url.includes('/transactions/list')) return Promise.resolve(page([]));
      if (url.includes('/exchanges/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(account('1', { currentPoints: 200 }));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('手动调账') }));
    await waitFor(() => expect(screen.getByLabelText('客户 ID')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('客户 ID'), '1');
    await userEvent.type(screen.getByLabelText('积分变动'), '100');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith(
      '/scrm/points/accounts/adjust',
      expect.objectContaining({ customerId: '1', points: 100 }),
    ));
  });
});