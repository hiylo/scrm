/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : AiAssistant.test.tsx
 * Description : AI 助手页面的客户加载 / 对话发送 / 空态与报错降级测试
 *
 * 选型说明: 同 Customers.test.tsx, 采用 vi.mock 模块级替换 apiClient,
 * 页面只通过 apiClient 抽象层访问后端, 模块 mock 零新增依赖。
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import AiAssistant from './AiAssistant';
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
const post = vi.mocked(apiClient.post);

/** 分页响应 */
function page<T>(rows: T[], total = rows.length) {
  return {
    content: rows,
    totalElements: total,
    totalPages: 1,
    number: 0,
    size: 20,
  } as never;
}

/** 客户行 */
function customer(id: string, nickname = `客户${id}`) {
  return { id, nickname } as never;
}

/** AI 对话回复 */
function aiReply(overrides: Record<string, unknown> = {}) {
  return {
    id: 'c1',
    customerId: '1',
    userMessage: '你好',
    detectedIntent: 'GREETING',
    sentiment: 'NEUTRAL',
    aiResponse: '您好, 请问有什么可以帮您?',
    responseTimeMs: 120,
    recommendedReplies: '["您好, 请问有什么可以帮您?"]',
    createdAt: '2026-10-08T12:00:00',
    ...overrides,
  } as never;
}

/** 渲染 AI 助手页 */
function renderAi() {
  return renderWithProviders(<AiAssistant />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('AiAssistant', () => {
  it('加载客户列表并展示客户选项', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/customers')) return Promise.resolve(page([customer('1', '张三'), customer('2', '李四')]));
      return Promise.resolve(page([]));
    });

    renderAi();

    await waitFor(() => expect(screen.getByText('张三')).toBeInTheDocument());
    expect(screen.getByText('李四')).toBeInTheDocument();
  });

  it('未选择客户时发送消息给出提示', async () => {
    get.mockImplementation(() => Promise.resolve(page([])));
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});

    renderAi();

    const textarea = screen.getByPlaceholderText(/输入客户消息/);
    await userEvent.type(textarea, '你好');
    fireEvent.click(screen.getByRole('button', { name: /发送/ }));

    // AntD message 以 portal 渲染, 断言其文本出现
    await waitFor(() => expect(screen.getByText('请先选择客户')).toBeInTheDocument());
    expect(post).not.toHaveBeenCalled();
    warnSpy.mockRestore();
  });

  it('选择客户并发送对话, 展示 AI 回复与意图/情感标签', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/customers')) return Promise.resolve(page([customer('1', '张三')]));
      if (url.startsWith('/scrm/ai-assistant/conversations/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(aiReply());

    renderAi();

    await waitFor(() => expect(screen.getByText('张三')).toBeInTheDocument());
    fireEvent.click(screen.getByText('张三'));

    const textarea = screen.getByPlaceholderText(/输入客户消息/);
    await userEvent.type(textarea, '你好');
    fireEvent.click(screen.getByRole('button', { name: /发送/ }));

    await waitFor(() => expect(screen.getAllByText('您好, 请问有什么可以帮您?').length).toBeGreaterThan(0));
    expect(screen.getByText('GREETING')).toBeInTheDocument();
    expect(screen.getByText('中性')).toBeInTheDocument();
    // 请求体正确携带 customerId 与 message
    expect(post).toHaveBeenCalledWith(
      '/scrm/ai-assistant/chat',
      expect.objectContaining({ customerId: 1, message: '你好' }),
    );
  });

  it('对话接口报错时提示错误并清空发送态', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/customers')) return Promise.resolve(page([customer('1', '张三')]));
      return Promise.resolve(page([]));
    });
    post.mockRejectedValue(new Error('ai-server 不可达'));
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    renderAi();

    await waitFor(() => expect(screen.getByText('张三')).toBeInTheDocument());
    fireEvent.click(screen.getByText('张三'));

    const textarea = screen.getByPlaceholderText(/输入客户消息/);
    await userEvent.type(textarea, '你好');
    fireEvent.click(screen.getByRole('button', { name: /发送/ }));

    // 错误后发送按钮恢复可点击 (不再 loading)
    await waitFor(() => {
      const btn = screen.getByRole('button', { name: /发送/ });
      expect(btn).not.toHaveClass('ant-btn-loading');
    });
    errorSpy.mockRestore();
  });
});
