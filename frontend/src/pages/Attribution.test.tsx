/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Attribution.test.tsx
 * Description : 归因分析页面的模型加载 / 新建模型 / 发布 / 触点明细测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Attribution from './Attribution';
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

/** 模型行 */
function model(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    modelName: `模型${id}`,
    modelCode: `ATTR-${id}`,
    modelType: 'LAST_TOUCH',
    attributionWindowDays: 30,
    status: 'DRAFT',
    ...overrides,
  } as never;
}

function renderPage() {
  return renderWithProviders(<Attribution />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('Attribution', () => {
  it('加载归因模型列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/models/list')) return Promise.resolve(page([model('1'), model('2')]));
      if (url.includes('/touchpoints/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('模型1')).toBeInTheDocument());
    expect(screen.getByText('模型2')).toBeInTheDocument();
  });

  it('新建模型: 打开弹窗填写后 POST', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/models/list')) return Promise.resolve(page([]));
      if (url.includes('/touchpoints/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(model('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('新建模型') }));
    await waitFor(() => expect(screen.getByLabelText('模型名称')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('模型名称'), '末次点击归因');
    await userEvent.type(screen.getByLabelText('模型编码'), 'LAST_30D');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/attribution/models', expect.objectContaining({ modelName: '末次点击归因', modelCode: 'LAST_30D' })));
  });

  it('发布模型: 草稿调用 publish', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/models/list')) return Promise.resolve(page([model('1')]));
      if (url.includes('/touchpoints/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(model('1', { status: 'PUBLISHED' }));

    renderPage();
    await waitFor(() => expect(screen.getByText('模型1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('发布') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/attribution/models/1/publish'));
  });

  it('查看触点明细: 打开抽屉加载触点', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/models/list')) return Promise.resolve(page([model('1', { status: 'PUBLISHED' })]));
      if (url.includes('/touchpoints/list')) return Promise.resolve(page([]));
      if (url.includes('/touchpoints?')) {
        return Promise.resolve(page([{ id: 't1', customerId: '1', customerName: '张三', channel: '公众号', touchpointType: 'CLICK', attributionWeight: 0.5, attributedRevenue: 1000 }]));
      }
      return Promise.resolve(page([]));
    });

    renderPage();
    await waitFor(() => expect(screen.getByText('模型1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('触点') }));

    await waitFor(() => expect(screen.getByText('张三')).toBeInTheDocument());
    expect(screen.getByText('¥1,000')).toBeInTheDocument();
  });
});