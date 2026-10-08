/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Blacklist.test.tsx
 * Description : 风险黑名单页面的加载 / 加入名单 / 移除 / 校验测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Blacklist from './Blacklist';
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
const del = vi.mocked(apiClient.delete);

/** 分页响应 */
function page<T>(rows: T[], total = rows.length) {
  return { content: rows, totalElements: total, totalPages: 1, number: 0, size: 10 } as never;
}

/** 名单行 */
function entry(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    targetType: 'CUSTOMER',
    targetValue: `wxid_${id}`,
    listType: 'BLACK',
    reason: '高频骚扰',
    status: 'ACTIVE',
    createTime: '2026-10-08T10:00:00',
    ...overrides,
  } as never;
}

function renderPage() {
  return renderWithProviders(<Blacklist />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('Blacklist', () => {
  it('加载黑名单列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/risk/blacklist/list')) return Promise.resolve(page([entry('1'), entry('2')]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('wxid_1')).toBeInTheDocument());
    expect(screen.getByText('wxid_2')).toBeInTheDocument();
  });

  it('加入名单: 打开弹窗填写后 POST', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/risk/blacklist/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(entry('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('加入名单') }));
    await waitFor(() => expect(screen.getByLabelText('目标值')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('目标值'), 'wxid_bad');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/risk/blacklist', expect.objectContaining({ targetValue: 'wxid_bad' })));
  });

  it('移除名单: 弹窗确认后调用 DELETE', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/risk/blacklist/list')) return Promise.resolve(page([entry('1')]));
      return Promise.resolve(page([]));
    });
    del.mockResolvedValue(entry('1', { status: 'RELEASED' }));

    renderPage();
    await waitFor(() => expect(screen.getByText('wxid_1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('移除') }));
    // Popconfirm 二次确认
    await waitFor(() => expect(screen.getByRole('button', { name: cn('确定') })).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));

    await waitFor(() => expect(del).toHaveBeenCalledWith('/scrm/risk/blacklist/1'));
  });

  it('校验黑名单: 选择类型并填写后 check', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/risk/blacklist/list')) return Promise.resolve(page([entry('1')]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue({ targetType: 'CUSTOMER', targetValue: 'wxid_bad', inBlacklist: true, listType: 'BLACK' });

    renderPage();
    await waitFor(() => expect(screen.getByText('wxid_1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('校验') }));
    await waitFor(() => expect(screen.getByLabelText('目标值')).toBeInTheDocument());
    // 校验弹窗内 Select: label 精确匹配
    fireEvent.mouseDown(screen.getByLabelText('目标类型'));
    await waitFor(() => expect(screen.getAllByText('客户').length).toBeGreaterThan(0));
    fireEvent.click(screen.getAllByText('客户').pop()!);
    await userEvent.type(screen.getByLabelText('目标值'), 'wxid_bad');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/risk/blacklist/check', expect.objectContaining({ targetType: 'CUSTOMER', targetValue: 'wxid_bad' })));
  });
});