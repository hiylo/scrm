/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : PublicSea.test.tsx
 * Description : 公海客户页面的加载 / 领取 / 分配 / 批量分配测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import PublicSea from './PublicSea';
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

/** 公海客户行 */
function sea(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    platformType: 'wework',
    platformCustomerUid: `wxid_${id}`,
    nickname: `客户${id}`,
    sourceChannel: '渠道活码',
    lifecycle: 'NEW',
    status: 'AVAILABLE',
    recallCount: 0,
    createTime: '2026-10-08T10:00:00',
    ...overrides,
  } as never;
}

function renderPage() {
  return renderWithProviders(<PublicSea />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('PublicSea', () => {
  it('加载并展示公海客户列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/public-sea/list')) return Promise.resolve(page([sea('1'), sea('2', { status: 'ASSIGNED', assignedTo: 'u1' })]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('客户1')).toBeInTheDocument());
    expect(screen.getByText('客户2')).toBeInTheDocument();
    expect(screen.getAllByText('可领取').length).toBeGreaterThan(0);
  });

  it('领取公海客户: 调用 claim', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/public-sea/list')) return Promise.resolve(page([sea('1')]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(sea('1', { status: 'ASSIGNED', assignedTo: 'me' }));

    renderPage();
    await waitFor(() => expect(screen.getByText('客户1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('领取') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/public-sea/1/claim', expect.anything()));
  });

  it('分配公海客户: 打开弹窗填写后 assign', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/public-sea/list')) return Promise.resolve(page([sea('1')]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(sea('1', { status: 'ASSIGNED', assignedTo: 'u9' }));

    renderPage();
    await waitFor(() => expect(screen.getByText('客户1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('分配') }));
    await waitFor(() => expect(screen.getByLabelText('接收人用户 ID')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('接收人用户 ID'), 'u9');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith(
      '/scrm/public-sea/1/assign',
      expect.objectContaining({ assigneeUserId: 'u9' }),
    ));
  });

  it('批量分配: 勾选后提交 batch-assign', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/public-sea/list')) return Promise.resolve(page([sea('1'), sea('2')]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue({ successCount: 2, failedIds: [] });

    renderPage();
    await waitFor(() => expect(screen.getByText('客户1')).toBeInTheDocument());
    const checkboxes = screen.getAllByRole('checkbox');
    fireEvent.click(checkboxes[1]);
    fireEvent.click(checkboxes[2]);
    fireEvent.click(screen.getByRole('button', { name: /批量分配 \(2\)/ }));

    await waitFor(() => expect(screen.getByLabelText('接收人用户 ID')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('接收人用户 ID'), 'u9');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith(
      '/scrm/public-sea/batch-assign',
      expect.objectContaining({ customerIds: [1, 2], assigneeUserId: 'u9' }),
    ));
  });
});