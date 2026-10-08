/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : OpenApiApps.test.tsx
 * Description : OpenAPI 管理页面的应用加载 / 新建应用 / 暂停 / 密钥测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import OpenApiApps from './OpenApiApps';
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

/** 应用行 */
function app(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    appName: `应用${id}`,
    appType: 'SERVER',
    clientId: `client_${id}`,
    rateLimitPerMinute: 60,
    rateLimitPerDay: 10000,
    status: 'ACTIVE',
    createTime: '2026-10-08T10:00:00',
    ...overrides,
  } as never;
}

/** 密钥行 */
function key(id: string, overrides: Record<string, unknown> = {}) {
  return { id, appId: '1', apiKey: `key_${id}`, keyType: 'APP', status: 'ACTIVE', ...overrides } as never;
}

function renderPage() {
  return renderWithProviders(<OpenApiApps />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('OpenApiApps', () => {
  it('加载应用列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/apps/list')) return Promise.resolve(page([app('1'), app('2')]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('应用1')).toBeInTheDocument());
    expect(screen.getByText('应用2')).toBeInTheDocument();
  });

  it('新建应用: 打开弹窗填写后 POST', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/apps/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(app('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('新建应用') }));
    await waitFor(() => expect(screen.getByLabelText('应用名称')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('应用名称'), '合作伙伴集成');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/open-api/apps', expect.objectContaining({ appName: '合作伙伴集成' })));
  });

  it('暂停应用: 调用 suspend', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/apps/list')) return Promise.resolve(page([app('1')]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(app('1', { status: 'SUSPENDED' }));

    renderPage();
    await waitFor(() => expect(screen.getByText('应用1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('暂停') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/open-api/apps/1/suspend'));
  });

  it('查看密钥: 打开抽屉加载 keys', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/apps/list')) return Promise.resolve(page([app('1')]));
      if (url.includes('/keys?')) return Promise.resolve(page([key('1')]));
      return Promise.resolve(page([]));
    });

    renderPage();
    await waitFor(() => expect(screen.getByText('应用1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('密钥') }));

    await waitFor(() => expect(screen.getByText('key_1')).toBeInTheDocument());
  });
});