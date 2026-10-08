/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Competitors.test.tsx
 * Description : 竞品监测页面的加载 / 新建竞品 / 监控启停 / 产品抽屉测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Competitors from './Competitors';
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

/** 竞品行 */
function comp(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    competitorName: `竞品${id}`,
    competitorCode: `C-${id}`,
    industry: 'SaaS',
    threatLevel: 'MEDIUM',
    marketShare: 20,
    monitoringEnabled: true,
    status: 'ACTIVE',
    ...overrides,
  } as never;
}

function renderPage() {
  return renderWithProviders(<Competitors />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('Competitors', () => {
  it('加载竞品列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/competitors/list')) return Promise.resolve(page([comp('1'), comp('2')]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('竞品1')).toBeInTheDocument());
    expect(screen.getByText('竞品2')).toBeInTheDocument();
  });

  it('新建竞品: 打开弹窗填写后 POST', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/competitors/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(comp('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('新建竞品') }));
    await waitFor(() => expect(screen.getByLabelText('竞品名称')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('竞品名称'), 'XX 云');
    await userEvent.type(screen.getByLabelText('竞品编码'), 'XX_CLOUD');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/competitors', expect.objectContaining({ competitorName: 'XX 云', competitorCode: 'XX_CLOUD' })));
  });

  it('停止监测: 调用 monitoring/disable', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/competitors/list')) return Promise.resolve(page([comp('1')]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(comp('1', { monitoringEnabled: false }));

    renderPage();
    await waitFor(() => expect(screen.getByText('竞品1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('停测') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/competitors/1/monitoring/disable'));
  });

  it('查看产品: 打开抽屉加载 products', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/competitors/list')) return Promise.resolve(page([comp('1')]));
      if (url.includes('/products/by-competitor/')) {
        return Promise.resolve(page([{ id: 'p1', competitorId: '1', productName: '企业版', price: 9999, currency: 'CNY' }]));
      }
      return Promise.resolve(page([]));
    });

    renderPage();
    await waitFor(() => expect(screen.getByText('竞品1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('产品') }));

    await waitFor(() => expect(screen.getByText('企业版')).toBeInTheDocument());
    expect(screen.getByText('¥9,999')).toBeInTheDocument();
  });
});