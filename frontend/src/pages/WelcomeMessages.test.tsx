/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : WelcomeMessages.test.tsx
 * Description : 欢迎语配置页面的加载 / 创建 / 触发预览 / 统计测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import WelcomeMessages from './WelcomeMessages';
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

/** 欢迎语规则行 */
function rule(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    ruleName: `规则${id}`,
    messageType: 'TEXT',
    content: '您好 ${nickname}, 欢迎关注!',
    platformType: 'wework',
    status: 'ACTIVE',
    triggerCount: 5,
    ...overrides,
  } as never;
}

function renderPage() {
  return renderWithProviders(<WelcomeMessages />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('WelcomeMessages', () => {
  it('加载并展示欢迎语规则列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/welcome-messages?')) return Promise.resolve(page([rule('1'), rule('2')]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('规则1')).toBeInTheDocument());
    expect(screen.getByText('规则2')).toBeInTheDocument();
  });

  it('创建规则: 打开弹窗填写后 POST 并刷新列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/welcome-messages?')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(rule('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('新建规则') }));
    await waitFor(() => expect(screen.getByLabelText('规则名称')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('规则名称'), '新客欢迎');
    await userEvent.type(screen.getByLabelText('文本内容'), '您好, 欢迎关注!');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/welcome-messages', expect.objectContaining({ ruleName: '新客欢迎', content: '您好, 欢迎关注!' })));
  });

  it('触发预览: 调用 trigger 并展示渲染后内容', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/welcome-messages?')) return Promise.resolve(page([rule('1')]));
      return Promise.resolve(page([]));
    });
    post.mockImplementation((url: string) => {
      if (url === '/scrm/welcome-messages/trigger') {
        return Promise.resolve({ ruleId: '1', ruleName: '规则1', renderedContent: '您好 张三, 欢迎关注!', messageType: 'TEXT', delaySeconds: 0 });
      }
      return Promise.resolve(rule('1'));
    });

    renderPage();
    await waitFor(() => expect(screen.getByText('规则1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('预览') }));

    await waitFor(() => expect(screen.getByText('您好 张三, 欢迎关注!')).toBeInTheDocument());
    expect(post).toHaveBeenCalledWith('/scrm/welcome-messages/trigger', expect.anything());
  });

  it('查看统计: 加载触发统计', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/stats')) return Promise.resolve({ triggerCount: 42, todayCount: 3 });
      if (url.startsWith('/scrm/welcome-messages?')) return Promise.resolve(page([rule('1')]));
      return Promise.resolve(page([]));
    });

    renderPage();
    await waitFor(() => expect(screen.getByText('规则1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('统计') }));

    await waitFor(() => expect(screen.getByText('triggerCount')).toBeInTheDocument());
    expect(screen.getByText('42')).toBeInTheDocument();
  });
});