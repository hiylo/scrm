/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : GroupBroadcast.test.tsx
 * Description : 群群发页面的加载 / 勾选 / 广播测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import GroupBroadcast from './GroupBroadcast';
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

/** 群行 */
function group(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    communityName: `群${id}`,
    platformType: 'WECHAT',
    communityType: 'CUSTOMER',
    memberCount: 100,
    activeMembers: 60,
    activityScore: 80,
    status: 'ACTIVE',
    ...overrides,
  } as never;
}

function renderPage() {
  return renderWithProviders(<GroupBroadcast />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('GroupBroadcast', () => {
  it('加载并展示群列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/communities/list')) return Promise.resolve(page([group('1'), group('2')]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('群1')).toBeInTheDocument());
    expect(screen.getByText('群2')).toBeInTheDocument();
  });

  it('未勾选群时点群发给出提示', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/communities/list')) return Promise.resolve(page([group('1')]));
      return Promise.resolve(page([]));
    });
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});

    renderPage();
    await waitFor(() => expect(screen.getByText('群1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('群发消息') }));

    await waitFor(() => expect(screen.getByText('请先勾选要广播的群')).toBeInTheDocument());
    expect(post).not.toHaveBeenCalled();
    warnSpy.mockRestore();
  });

  it('勾选群后打开弹窗发送广播', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/communities/list')) return Promise.resolve(page([group('1')]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue({ successCount: 1, failedIds: [] });

    renderPage();
    await waitFor(() => expect(screen.getByText('群1')).toBeInTheDocument());
    // 勾选第一行 checkbox
    const checkboxes = screen.getAllByRole('checkbox');
    fireEvent.click(checkboxes[0]);
    fireEvent.click(screen.getByRole('button', { name: /群发消息 \(1\)/ }));

    await waitFor(() => expect(screen.getByLabelText('消息内容')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('消息内容'), '本周六活动开始啦');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith(
      '/scrm/communities/messages/broadcast',
      expect.objectContaining({ communityIds: [1], messageType: 'TEXT', content: '本周六活动开始啦' }),
    ));
  });
});