/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ChannelCodes.test.tsx
 * Description : 渠道活码页面的加载 / 创建 / 启停 / 统计抽屉测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ChannelCodes from './ChannelCodes';
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

/** 活码行 */
function code(id: string, overrides: Record<string, unknown> = {}) {
  return { id, codeName: `活码${id}`, codeType: 'SINGLE', platformType: 'wework', status: 'ACTIVE', scanCount: 12, addCount: 8, ...overrides } as never;
}

/** 统计 VO */
function stats(codeId: string) {
  return {
    channelCodeId: codeId,
    codeName: `活码${codeId}`,
    codeType: 'SINGLE',
    platformType: 'wework',
    status: 'ACTIVE',
    scanCount: 12,
    addedCount: 8,
    pendingCount: 2,
    rejectedCount: 2,
    conversionRate: 66.7,
  } as never;
}

function renderPage() {
  return renderWithProviders(<ChannelCodes />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('ChannelCodes', () => {
  it('加载并展示活码列表与扫码/添加统计', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/channel-codes?') || url.startsWith('/scrm/channel-codes/')) return Promise.resolve(page([code('1'), code('2', { scanCount: 3, addCount: 1 })]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('活码1')).toBeInTheDocument());
    expect(screen.getByText('活码2')).toBeInTheDocument();
    expect(screen.getByText('扫 12')).toBeInTheDocument();
    expect(screen.getByText('扫 3')).toBeInTheDocument();
  });

  it('创建活码: 打开弹窗填写后 POST 并刷新列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/channel-codes')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(code('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('创建活码') }));
    await waitFor(() => expect(screen.getByLabelText('活码名称')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('活码名称'), '朋友圈扫码');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/channel-codes', expect.objectContaining({ codeName: '朋友圈扫码' })));
  });

  it('停用活码: 点击停用调用 deactivate', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/channel-codes')) return Promise.resolve(page([code('1')]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(code('1', { status: 'INACTIVE' }));

    renderPage();
    await waitFor(() => expect(screen.getByText('活码1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('停用') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/channel-codes/1/deactivate'));
  });

  it('打开统计抽屉: 加载统计与扫码记录', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/stats')) return Promise.resolve(stats('1'));
      if (url.includes('/scans')) return Promise.resolve(page([{ id: 's1', scannerNickname: '扫码者A', added: 'ADDED', scannedAt: '2026-10-08T10:00:00' }]));
      if (url.startsWith('/scrm/channel-codes')) return Promise.resolve(page([code('1')]));
      return Promise.resolve(page([]));
    });

    renderPage();
    await waitFor(() => expect(screen.getByText('活码1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('统计') }));

    await waitFor(() => expect(screen.getByText('累计扫码')).toBeInTheDocument());
    expect(screen.getAllByText('12').length).toBeGreaterThan(0);
    expect(screen.getByText('扫码者A')).toBeInTheDocument();
  });
});