/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Webhooks.test.tsx
 * Description : Webhook 页面的配置加载 / 新建配置 / 测试 / 日志测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Webhooks from './Webhooks';
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

/** 配置行 */
function config(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    webhookName: `配置${id}`,
    targetUrl: 'https://example.com/webhook',
    subscribedEvents: 'CUSTOMER_CREATED',
    httpMethod: 'POST',
    timeoutSeconds: 10,
    enabled: true,
    ...overrides,
  } as never;
}

/** 日志行 */
function log(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    webhookId: '1',
    webhookName: '配置1',
    eventType: 'CUSTOMER_CREATED',
    status: 'SUCCESS',
    responseCode: 200,
    deliveredAt: '2026-10-08T10:00:00',
    ...overrides,
  } as never;
}

function renderPage() {
  return renderWithProviders(<Webhooks />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('Webhooks', () => {
  it('加载 Webhook 配置列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/configs/list')) return Promise.resolve(page([config('1'), config('2')]));
      if (url.includes('/logs/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('配置1')).toBeInTheDocument());
    expect(screen.getByText('配置2')).toBeInTheDocument();
  });

  it('新建配置: 打开弹窗填写后 POST', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/configs/list')) return Promise.resolve(page([]));
      if (url.includes('/logs/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(config('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('新建配置') }));
    await waitFor(() => expect(screen.getByLabelText('配置名称')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('配置名称'), '客户创建回调');
    await userEvent.type(screen.getByLabelText('目标 URL'), 'https://example.com/wk');
    await userEvent.type(screen.getByLabelText('订阅事件 (逗号分隔)'), 'CUSTOMER_CREATED,ORDER_PAID');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/webhooks/configs', expect.objectContaining({ webhookName: '客户创建回调' })));
  });

  it('测试回调: 调用 test', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/configs/list')) return Promise.resolve(page([config('1')]));
      if (url.includes('/logs/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(log('1'));

    renderPage();
    await waitFor(() => expect(screen.getByText('配置1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('测试') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/webhooks/configs/1/test'));
  });

  it('切换日志 Tab 加载事件日志', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/configs/list')) return Promise.resolve(page([config('1')]));
      if (url.includes('/logs/list')) return Promise.resolve(page([log('1'), log('2', { status: 'FAILED', errorMessage: 'timeout' })]));
      return Promise.resolve(page([]));
    });

    renderPage();
    fireEvent.click(screen.getByRole('tab', { name: '事件日志' }));

    await waitFor(() => expect(screen.getAllByText('CUSTOMER_CREATED').length).toBeGreaterThan(0));
    expect(screen.getByText('timeout')).toBeInTheDocument();
  });
});