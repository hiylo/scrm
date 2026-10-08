/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : LeadScoring.test.tsx
 * Description : 线索打分页面的模型加载 / 新建 / 发布 / 计算测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import LeadScoring from './LeadScoring';
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
    modelCode: `LS-${id}`,
    modelType: 'RULE',
    dimensions: '[]',
    totalMaxScore: 100,
    isPublished: false,
    ...overrides,
  } as never;
}

function renderPage() {
  return renderWithProviders(<LeadScoring />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('LeadScoring', () => {
  it('加载打分模型列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/models/list')) return Promise.resolve(page([model('1'), model('2')]));
      if (url.includes('/scores/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('模型1')).toBeInTheDocument());
    expect(screen.getByText('模型2')).toBeInTheDocument();
  });

  it('新建模型: 打开弹窗填写后 POST', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/models/list')) return Promise.resolve(page([]));
      if (url.includes('/scores/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(model('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('新建模型') }));
    await waitFor(() => expect(screen.getByLabelText('模型名称')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('模型名称'), '标准模型');
    await userEvent.type(screen.getByLabelText('模型编码'), 'LEAD_STD');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/lead-scoring/models', expect.objectContaining({ modelName: '标准模型', modelCode: 'LEAD_STD' })));
  });

  it('发布模型: 草稿调用 publish', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/models/list')) return Promise.resolve(page([model('1')]));
      if (url.includes('/scores/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(model('1', { isPublished: true }));

    renderPage();
    await waitFor(() => expect(screen.getByText('模型1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('发布') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/lead-scoring/models/1/publish'));
  });

  it('触发全量计算: 调用 calculate-all', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/models/list')) return Promise.resolve(page([model('1')]));
      if (url.includes('/scores/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(model('1'));

    renderPage();
    await waitFor(() => expect(screen.getByText('模型1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('计算') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/lead-scoring/scores/calculate-all/1', expect.anything()));
  });
});