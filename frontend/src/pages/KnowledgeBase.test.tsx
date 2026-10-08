/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : KnowledgeBase.test.tsx
 * Description : 知识库页面的分类树加载 / 文章列表 / 新建文章 / 发布测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import KnowledgeBase from './KnowledgeBase';
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

/** 分类树 */
function categoryTree() {
  return [{ id: '1', categoryName: '产品资料', categoryCode: 'PRODUCT', status: 'ACTIVE', children: [] }] as never;
}

/** 文章行 */
function article(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    title: `文章${id}`,
    articleCode: `ART-${id}`,
    categoryName: '产品资料',
    contentType: 'TEXT',
    articleType: 'ARTICLE',
    content: '正文内容',
    status: 'DRAFT',
    viewCount: 10,
    helpfulCount: 2,
    createTime: '2026-10-08T10:00:00',
    ...overrides,
  } as never;
}

function renderPage() {
  return renderWithProviders(<KnowledgeBase />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('KnowledgeBase', () => {
  it('加载分类树与文章列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/categories/tree')) return Promise.resolve(categoryTree());
      if (url.includes('/articles/list')) return Promise.resolve(page([article('1'), article('2')]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getAllByText('产品资料').length).toBeGreaterThan(0));
    await waitFor(() => expect(screen.getByText('文章1')).toBeInTheDocument());
    expect(screen.getByText('文章2')).toBeInTheDocument();
  });

  it('新建文章: 打开弹窗填写后 POST', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/categories/tree')) return Promise.resolve(categoryTree());
      if (url.includes('/articles/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(article('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('新建文章') }));
    await waitFor(() => expect(screen.getByLabelText('文章标题')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('文章标题'), 'FAQ 汇总');
    await userEvent.type(screen.getByLabelText('正文内容'), '常见问题解答');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/knowledge-base/articles', expect.objectContaining({ title: 'FAQ 汇总', content: '常见问题解答' })));
  });

  it('发布文章: 草稿调用 publish', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/categories/tree')) return Promise.resolve(categoryTree());
      if (url.includes('/articles/list')) return Promise.resolve(page([article('1')]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(article('1', { status: 'PUBLISHED' }));

    renderPage();
    await waitFor(() => expect(screen.getByText('文章1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('发布') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/knowledge-base/articles/1/publish'));
  });
});