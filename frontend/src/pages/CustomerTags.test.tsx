/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : CustomerTags.test.tsx
 * Description : 客户标签页面测试 - 查询条件 / 添加按钮禁用联动 / 提交失败在弹窗内展示原因
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import CustomerTags from './CustomerTags';
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

/** 标签行 */
function tag(tagKey: string, tagValue: string) {
  return { customerId: '1', tagKey, tagValue, createTime: '2026-11-11T10:00:00' } as never;
}

function renderPage() {
  return renderWithProviders(<CustomerTags />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('CustomerTags', () => {
  it('未输入客户 ID 时不查询、且「添加标签」按钮禁用', async () => {
    renderPage();

    await waitFor(() => expect(screen.getByText('请输入客户 ID 查询标签')).toBeInTheDocument());
    // 空态文案说明未查询与查询后无数据是两回事
    expect(screen.getAllByRole('button', { name: cn('添加标签') })[0]).toBeDisabled();
    expect(get).not.toHaveBeenCalledWith(expect.stringContaining('/tags'));
  });

  it('输入客户 ID 查询后解除禁用并展示空态', async () => {
    get.mockResolvedValue([] as never);

    renderPage();
    await waitFor(() => expect(screen.getByPlaceholderText('输入客户 ID 查询标签')).toBeInTheDocument());

    await userEvent.type(screen.getByPlaceholderText('输入客户 ID 查询标签'), '366599420550189056');
    fireEvent.click(screen.getByRole('button', { name: cn('查询') }));

    await waitFor(() => expect(screen.getByText('暂无标签数据')).toBeInTheDocument());
    expect(screen.getAllByRole('button', { name: cn('添加标签') })[0]).not.toBeDisabled();
    expect(get).toHaveBeenCalledWith(expect.stringContaining('/scrm/customers/366599420550189056'));
  });

  it('提交失败时把后端原因展示在弹窗内，弹窗不关闭', async () => {
    get.mockResolvedValue([] as never);
    // 后端对「标签定义不存在」返回 400，拦截器抛出带 message 的错误
    post.mockRejectedValue(new Error('标签定义不存在, 请先创建标签定义: tagCode=消费层级'));

    renderPage();
    await waitFor(() => expect(screen.getByPlaceholderText('输入客户 ID 查询标签')).toBeInTheDocument());
    await userEvent.type(screen.getByPlaceholderText('输入客户 ID 查询标签'), '366599420550189056');
    fireEvent.click(screen.getByRole('button', { name: cn('查询') }));
    await waitFor(() => expect(screen.getByText('暂无标签数据')).toBeInTheDocument());

    fireEvent.click(screen.getAllByRole('button', { name: cn('添加标签') })[0]);
    await waitFor(() => expect(screen.getByLabelText('标签键')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('标签键'), '消费层级');
    await userEvent.type(screen.getByLabelText('标签值'), '高');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));

    // D4 修复点：失败原因必须留在弹窗内，用户改完可直接重试
    await waitFor(() =>
      expect(screen.getByText(/标签定义不存在/)).toBeInTheDocument(),
    );
    expect(screen.getByLabelText('标签键')).toBeInTheDocument();
  });

  it('标签键由 tagId 映射为 tagCode 展示（后端不返回 tagKey）', async () => {
    get.mockImplementation((url: string) => {
      // 标签赋值记录：后端只有 tagId，没有 tagKey
      if (url.includes('/customers/366599420550189056/tags')) {
        return Promise.resolve([
          { id: 'tc1', customerId: '366599420550189056', tagId: '378843255812526080', tagValue: '高' },
        ] as never);
      }
      // 标签定义：id → tagCode（真实路径是 /list 子路径）
      if (url === '/scrm/customer-tags/list') {
        return Promise.resolve({
          content: [{ id: '378843255812526080', tagCode: 'UI_CONS_LEVEL', tagName: '消费层级' }],
        } as never);
      }
      return Promise.resolve([] as never);
    });

    renderPage();
    await waitFor(() => expect(screen.getByPlaceholderText('输入客户 ID 查询标签')).toBeInTheDocument());
    await userEvent.type(screen.getByPlaceholderText('输入客户 ID 查询标签'), '366599420550189056');
    fireEvent.click(screen.getByRole('button', { name: cn('查询') }));

    // 映射成功才能看到 tagCode，否则这一列是空白
    await waitFor(() => expect(screen.getByText('UI_CONS_LEVEL')).toBeInTheDocument());
    expect(screen.getByText('高')).toBeInTheDocument();
  });

  it('提交成功: 按查询参数带上 tagKey/tagValue', async () => {
    get.mockResolvedValue([] as never);
    post.mockResolvedValue(undefined);

    renderPage();
    await waitFor(() => expect(screen.getByPlaceholderText('输入客户 ID 查询标签')).toBeInTheDocument());
    await userEvent.type(screen.getByPlaceholderText('输入客户 ID 查询标签'), '366599420550189056');
    fireEvent.click(screen.getByRole('button', { name: cn('查询') }));
    await waitFor(() => expect(screen.getByText('暂无标签数据')).toBeInTheDocument());

    fireEvent.click(screen.getAllByRole('button', { name: cn('添加标签') })[0]);
    await waitFor(() => expect(screen.getByLabelText('标签键')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('标签键'), '消费层级');
    await userEvent.type(screen.getByLabelText('标签值'), '高');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        expect.stringContaining('tagKey=%E6%B6%88%E8%B4%B9%E5%B1%82%E7%BA%A7'),
      ),
    );
    expect(post).toHaveBeenCalledWith(expect.stringContaining('tagValue=%E9%AB%98'));
  });
});
