/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : QuickReplies.test.tsx
 * Description : 快捷回复页面的分类加载 / 回复列表加载 / 新建回复测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import QuickReplies from './QuickReplies';
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

/** 分类行 */
function category(id: string, name = `分类${id}`) {
  return { id, categoryName: name, status: 'ACTIVE' } as never;
}

/** 回复行 */
function reply(id: string, title = `回复${id}`) {
  return { id, categoryId: '1', title, content: '您好, 这是我们产品的报价', replyType: 'TEXT', shortcut: 'bj', useCount: 3, status: 'ENABLED', createTime: '2026-10-08T10:00:00' } as never;
}

function renderPage() {
  return renderWithProviders(<QuickReplies />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('QuickReplies', () => {
  it('加载分类与回复列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('quick-reply-categories/list')) return Promise.resolve(page([category('1', '售前'), category('2', '售后')]));
      if (url.startsWith('/scrm/quick-replies/list')) return Promise.resolve(page([reply('1'), reply('2')]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('售前')).toBeInTheDocument());
    expect(screen.getByText('售后')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText('回复1')).toBeInTheDocument());
    expect(screen.getByText('回复2')).toBeInTheDocument();
  });

  it('点击分类筛选回复列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('quick-reply-categories/list')) return Promise.resolve(page([category('1', '售前')]));
      if (url.startsWith('/scrm/quick-replies/list')) return Promise.resolve(page([reply('1')]));
      return Promise.resolve(page([]));
    });

    renderPage();
    await waitFor(() => expect(screen.getByText('售前')).toBeInTheDocument());
    fireEvent.click(screen.getByText('售前'));

    await waitFor(() => expect(get).toHaveBeenCalledWith(expect.stringContaining('categoryId=1')));
  });

  it('新建回复: 打开弹窗填写后 POST 并刷新列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('quick-reply-categories/list')) return Promise.resolve(page([category('1', '售前')]));
      if (url.startsWith('/scrm/quick-replies/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(reply('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('新建回复') }));
    await waitFor(() => expect(screen.getByLabelText('标题')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('标题'), '报价回复');
    await userEvent.type(screen.getByLabelText('回复内容'), '报价单见附件');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/quick-replies', expect.objectContaining({ title: '报价回复', content: '报价单见附件' })));
  });
});