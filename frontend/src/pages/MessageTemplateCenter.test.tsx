/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : MessageTemplateCenter.test.tsx
 * Description : 消息模板中心页面的模板/分组加载 / 新建 / 发布 / 分组管理测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import MessageTemplateCenter from './MessageTemplateCenter';
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
    templateType: 'MARKETING',
    channels: 'IN_APP',
    content: 'content',
    status: 'DRAFT',
    versionNumber: 1,
    ...overrides,
  } as never;
}

/** 分组行 */
function group(id: string, overrides: Record<string, unknown> = {}) {
  return { id, groupName: `分组${id}`, groupCode: `G-${id}`, groupType: 'CATEGORY', enabled: true, templateCount: 2, ...overrides } as never;
}

function renderPage() {
  return renderWithProviders(<MessageTemplateCenter />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('MessageTemplateCenter', () => {
  it('加载消息模板列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/message-template-center/list')) return Promise.resolve(page([template('1'), template('2')]));
      if (url.includes('/groups/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('模板1')).toBeInTheDocument());
    expect(screen.getByText('模板2')).toBeInTheDocument();
  });

  it('新建模板: 打开弹窗填写后 POST', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/message-template-center/list')) return Promise.resolve(page([]));
      if (url.includes('/groups/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(template('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('新建模板') }));
    await waitFor(() => expect(screen.getByLabelText('模板名称')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('模板名称'), '支付通知');
    await userEvent.type(screen.getByLabelText('模板编码'), 'ORDER_PAID');
    await userEvent.type(screen.getByLabelText('模板内容'), '您的订单已支付');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/message-template-center', expect.objectContaining({ templateName: '支付通知', templateCode: 'ORDER_PAID' })));
  });

  it('发布模板: 草稿调用 publish', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/message-template-center/list')) return Promise.resolve(page([template('1')]));
      if (url.includes('/groups/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(template('1', { status: 'PUBLISHED' }));

    renderPage();
    await waitFor(() => expect(screen.getByText('模板1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('发布') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/message-template-center/1/publish'));
  });

  it('切换到分组 Tab 并新建分组', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/message-template-center/list')) return Promise.resolve(page([]));
      if (url.includes('/groups/list')) return Promise.resolve(page([group('1')]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(group('1'));

    renderPage();
    fireEvent.click(screen.getByRole('tab', { name: '模板分组' }));
    await waitFor(() => expect(screen.getByText('分组1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('新建分组') }));
    await waitFor(() => expect(screen.getByLabelText('分组名称')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('分组名称'), '订单类通知');
    await userEvent.type(screen.getByLabelText('分组编码'), 'ORDER_NOTIFY');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/message-template-center/groups', expect.objectContaining({ groupName: '订单类通知', groupCode: 'ORDER_NOTIFY' })));
  });
});