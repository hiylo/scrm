/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Notifications.test.tsx
 * Description : 通知中心页面的模板加载 / 新建模板 / 发送 / 已读测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Notifications from './Notifications';
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

/** 模板行 */
function template(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    templateName: `模板${id}`,
    templateCode: `T-${id}`,
    category: 'ORDER',
    channel: 'IN_APP',
    title: '订单通知',
    content: 'content',
    enabled: true,
    ...overrides,
  } as never;
}

/** 消息行 */
function notif(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    templateCode: 'T-1',
    channel: 'IN_APP',
    category: 'ORDER',
    title: `通知${id}`,
    status: 'SENT',
    recipientId: 'u1',
    createTime: '2026-10-08T10:00:00',
    ...overrides,
  } as never;
}

function renderPage() {
  return renderWithProviders(<Notifications />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('Notifications', () => {
  it('加载通知模板列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/templates/list')) return Promise.resolve(page([template('1'), template('2')]));
      if (url.includes('/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('模板1')).toBeInTheDocument());
    expect(screen.getByText('模板2')).toBeInTheDocument();
  });

  it('新建模板: 打开弹窗填写后 POST', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/templates/list')) return Promise.resolve(page([]));
      if (url.includes('/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(template('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('新建模板') }));
    await waitFor(() => expect(screen.getByLabelText('模板名称')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('模板名称'), '支付成功模板');
    await userEvent.type(screen.getByLabelText('模板编码'), 'ORDER_PAID');
    await userEvent.type(screen.getByLabelText('标题'), '支付成功');
    await userEvent.type(screen.getByLabelText('内容'), '您的订单已支付成功');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/notifications/templates', expect.objectContaining({ templateName: '支付成功模板' })));
  });

  it('发送通知: 打开弹窗填写后 POST send', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/templates/list')) return Promise.resolve(page([template('1')]));
      if (url.includes('/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue([notif('1')]);

    renderPage();
    await waitFor(() => expect(screen.getByText('模板1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('发送') }));
    await waitFor(() => expect(screen.getByLabelText('模板编码')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('接收人 ID'), 'u1');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/notifications/send', expect.objectContaining({ recipientId: 'u1' })));
  });

  it('切换消息 Tab, 标记已读', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/templates/list')) return Promise.resolve(page([]));
      if (url.includes('/notifications/list')) return Promise.resolve(page([notif('1', { status: 'PENDING' })]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(notif('1', { status: 'READ' }));

    renderPage();
    fireEvent.click(screen.getByRole('tab', { name: '通知消息' }));
    await waitFor(() => expect(screen.getByText('通知1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('已读') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/notifications/1/read'));
  });
});