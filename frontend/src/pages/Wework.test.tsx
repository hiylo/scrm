/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : Wework.test.tsx
 * Description : 企业微信外部联系人测试 - 未输入 userid 不发请求 / 空态提示输入引导
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Wework from './Wework';
import { apiClient } from '../api/client';
import { renderWithProviders } from '../test/render';

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

beforeEach(() => {
  vi.clearAllMocks();
});

describe('Wework 外部联系人（单视图）', () => {
  it('未输入 userid 时不发起请求，空态给出输入引导', async () => {
    renderWithProviders(<Wework singleView defaultTab="contacts" />);

    // D6 修复点：空态必须说明「先输入 userid 再查询」，而不是笼统的「暂无数据」
    await waitFor(() => expect(screen.getByText(/先在上方输入成员 userid 再点/)).toBeInTheDocument());
    expect(get).not.toHaveBeenCalled();
  });

  it('输入 userid 后点击查询会带上 userId 请求外部联系人', async () => {
    get.mockResolvedValue([] as never);

    renderWithProviders(<Wework singleView defaultTab="contacts" />);
    await waitFor(() =>
      expect(screen.getByPlaceholderText('成员 userid (如: ZhangSan)')).toBeInTheDocument(),
    );

    await userEvent.type(screen.getByPlaceholderText('成员 userid (如: ZhangSan)'), 'ZhangSan');
    const queryBtn = screen
      .getAllByRole('button')
      .find((b) => b.textContent?.replace(/\s/g, '') === '查询');
    expect(queryBtn).toBeTruthy();
    fireEvent.click(queryBtn as HTMLElement);

    await waitFor(() =>
      expect(get).toHaveBeenCalledWith('/scrm/wework/external-contacts', { params: { userId: 'ZhangSan' } }),
    );
  });

  it('查询后无数据时，空态切换为「该成员名下没有外部联系人」', async () => {
    get.mockResolvedValue([] as never);

    renderWithProviders(<Wework singleView defaultTab="contacts" />);
    await waitFor(() =>
      expect(screen.getByPlaceholderText('成员 userid (如: ZhangSan)')).toBeInTheDocument(),
    );
    await userEvent.type(screen.getByPlaceholderText('成员 userid (如: ZhangSan)'), 'ZhangSan');
    const queryBtn = screen
      .getAllByRole('button')
      .find((b) => b.textContent?.replace(/\s/g, '') === '查询');
    fireEvent.click(queryBtn as HTMLElement);

    await waitFor(() => expect(screen.getByText('该成员名下没有外部联系人')).toBeInTheDocument());
  });
});
