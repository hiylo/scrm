/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ContentMarketing.test.tsx
 * Description : 内容营销页面的加载 / 新建 / 提交审核 / 审核测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ContentMarketing from './ContentMarketing';
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

/** 内容行 */
function content(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    title: `内容${id}`,
    contentType: 'ARTICLE',
    summary: '618 活动预热',
    authorName: '小张',
    viewCount: 100,
    likeCount: 10,
    shareCount: 5,
    status: 'DRAFT',
    createTime: '2026-10-08T10:00:00',
    ...overrides,
  } as never;
}

function renderPage() {
  return renderWithProviders(<ContentMarketing />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('ContentMarketing', () => {
  it('加载内容列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/contents/list')) return Promise.resolve(page([content('1'), content('2')]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('内容1')).toBeInTheDocument());
    expect(screen.getByText('内容2')).toBeInTheDocument();
  });

  it('新建内容: 打开弹窗填写后 POST', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/contents/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(content('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('新建内容') }));
    await waitFor(() => expect(screen.getByLabelText('内容标题')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('内容标题'), '618 预热文章');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/contents', expect.objectContaining({ title: '618 预热文章' })));
  });

  it('提交审核: 草稿调用 submit-review', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/contents/list')) return Promise.resolve(page([content('1')]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(content('1', { status: 'PENDING_REVIEW' }));

    renderPage();
    await waitFor(() => expect(screen.getByText('内容1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('提交审核') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/contents/1/submit-review'));
  });

  it('审核内容: 待审核打开弹窗后 review', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/contents/list')) return Promise.resolve(page([content('1', { status: 'PENDING_REVIEW' })]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(content('1', { status: 'APPROVED' }));

    renderPage();
    await waitFor(() => expect(screen.getByText('内容1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('审核') }));
    await waitFor(() => expect(screen.getByLabelText('审核意见')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/contents/review', expect.objectContaining({ contentId: '1', approved: true })));
  });
});