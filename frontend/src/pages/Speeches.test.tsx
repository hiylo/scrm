/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Speeches.test.tsx
 * Description : 话术库页面的分类加载 / 话术列表 / 点赞 / 新建话术测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Speeches from './Speeches';
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
function category(id: string, name = `话术分类${id}`) {
  return { id, categoryName: name, status: 'ACTIVE' } as never;
}

/** 话术行 */
function speech(id: string, title = `话术${id}`) {
  return { id, categoryId: '1', title, content: '您好, 请问有什么可以帮您?', speechType: 'TEXT', scenario: '售前', useCount: 5, likeCount: 2, status: 'ENABLED', createTime: '2026-10-08T10:00:00' } as never;
}

function renderPage() {
  return renderWithProviders(<Speeches />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('Speeches', () => {
  it('加载分类与话术列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('speech-categories/list')) return Promise.resolve(page([category('1', '开场白')]));
      if (url.startsWith('/scrm/speeches/list')) return Promise.resolve(page([speech('1'), speech('2')]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('开场白')).toBeInTheDocument());
    await waitFor(() => expect(screen.getByText('话术1')).toBeInTheDocument());
    expect(screen.getByText('话术2')).toBeInTheDocument();
  });

  it('点赞话术: 调用 like 并刷新列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/categories/list')) return Promise.resolve(page([category('1', '开场白')]));
      if (url.startsWith('/scrm/speeches/list')) return Promise.resolve(page([speech('1')]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(speech('1'));

    renderPage();
    await waitFor(() => expect(screen.getByText('话术1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('点赞') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/speeches/1/like'));
  });

  it('新建话术: 打开弹窗填写后 POST 并刷新列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('speech-categories/list')) return Promise.resolve(page([category('1', '开场白')]));
      if (url.startsWith('/scrm/speeches/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(speech('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('新建话术') }));
    await waitFor(() => expect(screen.getByLabelText('话术标题')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('话术标题'), '新品开场');
    await userEvent.type(screen.getByLabelText('话术内容'), '欢迎了解我们新品');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/speeches', expect.objectContaining({ title: '新品开场', content: '欢迎了解我们新品' })));
  });
});