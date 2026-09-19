/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Conversations.test.tsx
 * Description : 会话管理页面的加载 / 列表渲染 / 选中会话 / 发送消息 / 空数据与报错降级 / utils 接线测试
 *
 * 选型说明: 与 Customers 测试一致, 使用 vi.mock 替换 apiClient 模块;
 * 同时 mock useScrmWebSocket —— 否则会真实创建 WebSocket 并启动指数退避重连定时器, 造成用例挂起。
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import Conversations from './Conversations';
import { apiClient } from '../api/client';
import { useScrmWebSocket } from '../hooks/useScrmWebSocket';
import { cn, conversation, deferred, message, page } from '../test/fixtures';
import { PathProbe, renderWithProviders } from '../test/render';

vi.mock('../api/client', () => ({
  apiClient: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
  apiClientInstance: { get: vi.fn(), post: vi.fn() },
  getApiBaseUrl: () => '',
  DEFAULT_TIMEOUT: 30000,
  LONG_TIMEOUT: 120000,
}));

vi.mock('../hooks/useScrmWebSocket', () => ({
  useScrmWebSocket: vi.fn(() => ({ connected: false, lastNotification: null })),
}));

/** 按 URL 前缀分发的默认响应 */
function routeRequestsBy(conversations: unknown[], messagesPage: unknown) {
  get.mockImplementation(((url: string) => {
    if (url.startsWith('/scrm/message-templates')) {
      return Promise.resolve(page([]));
    }
    if (url.includes('/messages')) {
      return Promise.resolve(messagesPage);
    }
    if (url.startsWith('/scrm/conversations')) {
      return Promise.resolve(page(conversations, { totalElements: conversations.length }));
    }
    return Promise.resolve(null);
  }) as never);
}

/** 生成"N 秒前"的 ISO 串, 用于验证页面按友好时间展示且不依赖具体时钟 */
function recentIso(secondsAgo: number): string {
  return new Date(Date.now() - secondsAgo * 1000).toISOString();
}

function renderConversations(initialPath = '/conversations') {
  return renderWithProviders(
    <Routes>
      <Route path="/conversations" element={<Conversations />} />
      <Route path="/customers/:id" element={<PathProbe />} />
    </Routes>,
    initialPath,
  );
}

const get = vi.mocked(apiClient.get);
const post = vi.mocked(apiClient.post);
const put = vi.mocked(apiClient.put);
const ws = vi.mocked(useScrmWebSocket);

beforeEach(() => {
  get.mockReset();
  post.mockReset();
  put.mockReset();
  // 页面在选中会话 / 发送 / 标记已读等处会直接 await 或 .catch() 这些调用, 必须返回 Promise
  post.mockResolvedValue(null);
  put.mockResolvedValue(null);
  ws.mockReturnValue({ connected: false, lastNotification: null });
  routeRequestsBy([], page([]));
});

describe('Conversations 加载态', () => {
  it('会话列表请求未返回时展示 loading, 返回后消失', async () => {
    const pending = deferred();
    get.mockImplementation(
      ((url: string) => {
        if (url.startsWith('/scrm/message-templates')) return Promise.resolve(page([]));
        return pending.promise;
      }) as never,
    );

    const { container } = renderConversations();
    expect(screen.getByRole('tab', { name: '会话列表' })).toBeInTheDocument();
    await waitFor(() => expect(container.querySelector('.ant-spin-spinning')).toBeTruthy());

    pending.resolve(page([conversation()], { totalElements: 1 }));
    await screen.findByText('张三');
    await waitFor(() => expect(container.querySelector('.ant-spin-spinning')).toBeNull());
  });

  it('首次请求带分页参数且不传 status 筛选', async () => {
    renderConversations();
    await waitFor(() => expect(get).toHaveBeenCalled());
    const url = String(get.mock.calls.find((c) => String(c[0]).startsWith('/scrm/conversations'))?.[0]);
    expect(url).toContain('/scrm/conversations?page=0&size=10');
    expect(url).not.toContain('status=');
  });
});

describe('Conversations 列表渲染', () => {
  it('渲染会话摘要、状态标签、消息数与分页总数', async () => {
    routeRequestsBy(
      [
        conversation({ id: '5001', customerNickname: '张三', status: 'ACTIVE' }),
        conversation({
          id: '5002',
          customerNickname: '李四',
          status: 'CLOSED',
          lastMessageSummary: undefined,
          messageCount: 3,
        }),
      ],
      page([]),
    );

    renderConversations();

    expect(await screen.findByText('张三')).toBeInTheDocument();
    expect(screen.getByText('李四')).toBeInTheDocument();
    expect(screen.getByText('我想了解一下价格')).toBeInTheDocument();
    // 无摘要的会话回退文案
    expect(screen.getByText('暂无消息')).toBeInTheDocument();
    expect(screen.getByText('进行中')).toBeInTheDocument();
    expect(screen.getByText('已关闭')).toBeInTheDocument();
    expect(screen.getByText('12 条消息')).toBeInTheDocument();
    expect(screen.getByText('共 2 条')).toBeInTheDocument();
    // 未选中会话时右侧为占位空态
    expect(screen.getByText('请选择会话')).toBeInTheDocument();
    expect(screen.getByText('请选择左侧会话查看消息')).toBeInTheDocument();
  });

  it('无 customerNickname 时回退展示客户编号', async () => {
    routeRequestsBy([conversation({ id: '5003', customerNickname: undefined, customerId: '888' })], page([]));
    renderConversations();
    expect(await screen.findByText('客户 888')).toBeInTheDocument();
  });

  it('WebSocket 断开时在卡片头部展示离线状态', async () => {
    ws.mockReturnValue({ connected: false, lastNotification: null } as never);
    routeRequestsBy([conversation()], page([]));
    renderConversations();
    await screen.findByText('张三');
    expect(screen.getByText('离线')).toBeInTheDocument();
  });

  it('WebSocket 已连接时展示在线状态', async () => {
    ws.mockReturnValue({ connected: true, lastNotification: null } as never);
    routeRequestsBy([conversation()], page([]));
    renderConversations();
    await screen.findByText('张三');
    expect(screen.getByText('在线')).toBeInTheDocument();
  });
});

describe('Conversations 选中会话与消息', () => {
  it('点击会话后拉取消息记录并标记已读', async () => {
    const user = userEvent.setup();
    routeRequestsBy(
      [conversation({ id: '5001' })],
      page([
        message({ id: 'm-1', content: '你好, 请问产品怎么收费?' }),
        message({ id: 'm-2', content: '我们有三档方案', direction: 'OUT' }),
      ]),
    );

    renderConversations();
    await screen.findByText('张三');

    await user.click(screen.getByText('张三'));

    expect(await screen.findByText('你好, 请问产品怎么收费?')).toBeInTheDocument();
    expect(screen.getByText('我们有三档方案')).toBeInTheDocument();
    await waitFor(() => expect(put).toHaveBeenCalledWith('/scrm/conversations/5001/read'));
    // 选中后卡片标题展示会话上下文与操作按钮
    expect(screen.getByRole('button', { name: /AI 总结/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /关闭会话/ })).toBeInTheDocument();
  });

  it('消息为空时展示暂无消息', async () => {
    const user = userEvent.setup();
    routeRequestsBy([conversation({ id: '5001' })], page([]));

    renderConversations();
    await screen.findByText('张三');
    await user.click(screen.getByText('张三'));

    expect(await screen.findByText('暂无消息')).toBeInTheDocument();
  });

  it('消息接口报错时降级为空列表, 不影响会话列表', async () => {
    const user = userEvent.setup();
    routeRequestsBy([conversation({ id: '5001' })], page([]));
    get.mockImplementation(((url: string) => {
      if (String(url).includes('/messages')) return Promise.reject(new Error('boom'));
      if (String(url).startsWith('/scrm/message-templates')) return Promise.resolve(page([]));
      return Promise.resolve(page([conversation({ id: '5001' })]));
    }) as never);

    renderConversations();
    await screen.findByText('张三');
    await user.click(screen.getByText('张三'));

    expect(await screen.findByText('暂无消息')).toBeInTheDocument();
    // 选中会话后 "张三" 同时出现在列表项与聊天头部, 说明列表未被消息报错清空
    expect(screen.getAllByText('张三').length).toBeGreaterThanOrEqual(2);
  });

  it('URL 携带 customerId 时自动选中对应会话', async () => {
    routeRequestsBy([conversation({ id: '5001', customerId: '1001' })], page([message()]));
    renderConversations('/conversations?customerId=1001');

    expect(await screen.findByText('你好, 请问产品怎么收费?')).toBeInTheDocument();
    await waitFor(() => expect(put).toHaveBeenCalledWith('/scrm/conversations/5001/read'));
  });

  it('URL 指定的客户当前页无会话时回退查询 by-customer 接口', async () => {
    routeRequestsBy([conversation({ id: '5001', customerId: '9999' })], page([message()]));
    get.mockImplementation(((url: string) => {
      const u = String(url);
      if (u.startsWith('/scrm/conversations/by-customer/')) {
        return Promise.resolve(page([conversation({ id: '7007', customerId: '1001' })]));
      }
      if (u.includes('/messages')) return Promise.resolve(page([message()]));
      if (u.startsWith('/scrm/message-templates')) return Promise.resolve(page([]));
      return Promise.resolve(page([conversation({ id: '5001', customerId: '9999' })]));
    }) as never);

    renderConversations('/conversations?customerId=1001');

    await waitFor(() =>
      expect(
        get.mock.calls.some((c) => String(c[0]).startsWith('/scrm/conversations/by-customer/1001')),
      ).toBe(true),
    );
    await waitFor(() => expect(put).toHaveBeenCalledWith('/scrm/conversations/7007/read'));
  });
});

describe('Conversations 发送消息', () => {
  /** 选中会话并返回输入框 */
  async function prepareConversation() {
    const user = userEvent.setup();
    routeRequestsBy([conversation({ id: '5001' })], page([message({ id: 'm-1' })]));
    post.mockResolvedValue(
      message({ id: 'm-server', content: '收到, 我发报价单给你', direction: 'OUT' }),
    );
    renderConversations();
    await screen.findByText('张三');
    await user.click(screen.getByText('张三'));
    await screen.findByText('你好, 请问产品怎么收费?');
    return {
      user,
      input: screen.getByPlaceholderText('输入消息 (Enter 发送, Shift+Enter 换行)'),
    };
  }

  it('点击发送按钮提交消息并以服务端返回替换乐观占位', async () => {
    const { user, input } = await prepareConversation();

    await user.type(input, '收到, 我发报价单给你');
    await user.click(screen.getByRole('button', { name: /发送/ }));

    await waitFor(() => expect(post).toHaveBeenCalledTimes(1));
    const [url, payload] = post.mock.calls[0] as [string, Record<string, string>];
    expect(url).toBe('/scrm/conversations/messages');
    expect(payload).toMatchObject({
      conversationId: '5001',
      messageType: 'TEXT',
      direction: 'OUT',
      content: '收到, 我发报价单给你',
    });
    expect(payload.messageId).toBeTruthy();
    expect(await screen.findByText('已发送')).toBeInTheDocument();
    // 发送成功后输入框清空
    expect(input).toHaveValue('');
  });

  it('Enter 发送, Shift+Enter 换行', async () => {
    const { user, input } = await prepareConversation();

    await user.type(input, '换行测试{Shift>}{Enter}{/Shift}第二行');
    expect(post).not.toHaveBeenCalled();
    expect(input).toHaveValue('换行测试\n第二行');

    await user.type(input, '{Enter}');
    await waitFor(() => expect(post).toHaveBeenCalledTimes(1));
    const [, payload] = post.mock.calls[0] as [string, Record<string, string>];
    expect(payload.content).toBe('换行测试\n第二行');
  });

  it('IME 组合态下的 Enter 不触发发送', async () => {
    const { input } = await prepareConversation();

    // 候选词确认: nativeEvent.isComposing = true
    fireEvent.keyDown(input, { key: 'Enter', isComposing: true });
    // 老浏览器: keyCode 229
    fireEvent.keyDown(input, { key: 'Enter', keyCode: 229, charCode: 229 });
    await new Promise((r) => setTimeout(r, 50));

    expect(post).not.toHaveBeenCalled();
    expect(input).toHaveValue('');
  });

  it('输入内容为空时不发送', async () => {
    const { user } = await prepareConversation();
    await user.click(screen.getByRole('button', { name: /发送/ }));
    expect(post).not.toHaveBeenCalled();
  });

  it('发送失败时标记消息为失败并提供重试', async () => {
    const { user, input } = await prepareConversation();
    post.mockRejectedValueOnce(new Error('send failed'));

    await user.type(input, '会失败的消息');
    await user.click(screen.getByRole('button', { name: /发送/ }));

    expect(await screen.findByText('发送失败')).toBeInTheDocument();
    const retry = screen.getByRole('button', { name: cn('重试') });

    post.mockResolvedValueOnce(message({ id: 'm-retry', content: '会失败的消息' }));
    await user.click(retry);

    await waitFor(() => expect(post).toHaveBeenCalledTimes(2));
    const [, secondPayload] = post.mock.calls[1] as [string, Record<string, string>];
    expect(secondPayload.content).toBe('会失败的消息');
  });
});

describe('Conversations 未读与 AI 总结', () => {
  it('未读会话展示标记已读入口, 单条标记后未读数清零', async () => {
    const user = userEvent.setup();
    routeRequestsBy([conversation({ id: '5001', unreadCount: 3 })], page([]));
    put.mockResolvedValue(null);

    renderConversations();
    await screen.findByText('张三');
    // 未读小红点 + 单条标记入口
    expect(document.querySelector('.ant-badge-dot')).toBeTruthy();

    await user.click(screen.getByRole('button', { name: /标记已读/ }));
    await waitFor(() => expect(put).toHaveBeenCalledWith('/scrm/conversations/5001/read'));
    // 未读清零后不再展示单条标记按钮
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: /标记已读/ })).not.toBeInTheDocument(),
    );
    // 点击标记已读不应顺带选中会话
    expect(screen.getByText('请选择会话')).toBeInTheDocument();
  });

  it('全部已读: 存在未读时逐个调用接口, 无未读时给出提示', async () => {
    const user = userEvent.setup();
    routeRequestsBy(
      [
        conversation({ id: '5001', unreadCount: 2 }),
        conversation({ id: '5002', customerNickname: '李四', unreadCount: 0 }),
      ],
      page([]),
    );

    renderConversations();
    await screen.findByText('张三');
    await user.click(screen.getByRole('button', { name: /全部已读/ }));

    await waitFor(() => expect(put).toHaveBeenCalledWith('/scrm/conversations/5001/read'));
    expect(put.mock.calls.length).toBe(1);
    expect(await screen.findByText('已标记 1 个会话为已读')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /全部已读/ }));
    expect(await screen.findByText('没有未读会话')).toBeInTheDocument();
  });

  it('AI 总结成功后以 Alert 展示模型与摘要内容', async () => {
    const user = userEvent.setup();
    routeRequestsBy([conversation({ id: '5001' })], page([message()]));
    post.mockResolvedValue({
      conversationId: '5001',
      summary: '客户咨询价格, 已发送三档方案。',
      messageCount: 12,
      model: 'qwen-plus',
      latencyMs: 820,
      summarizedAt: '2026-09-12T10:05:00',
    });

    renderConversations();
    await screen.findByText('张三');
    await user.click(screen.getByText('张三'));
    await screen.findByText('你好, 请问产品怎么收费?');

    await user.click(screen.getByRole('button', { name: /AI 总结/ }));

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('/scrm/conversations/5001/summarize'),
    );
    expect(await screen.findByText('客户咨询价格, 已发送三档方案。')).toBeInTheDocument();
    expect(screen.getByText('qwen-plus')).toBeInTheDocument();
    expect(screen.getByText('消息数: 12')).toBeInTheDocument();
  });

  it('AI 总结失败时不展示总结卡片', async () => {
    const user = userEvent.setup();
    routeRequestsBy([conversation({ id: '5001' })], page([message()]));
    post.mockRejectedValue(new Error('llm down'));

    renderConversations();
    await screen.findByText('张三');
    await user.click(screen.getByText('张三'));
    await screen.findByText('你好, 请问产品怎么收费?');

    await user.click(screen.getByRole('button', { name: /AI 总结/ }));

    await waitFor(() => expect(post).toHaveBeenCalled());
    expect(screen.queryByText('AI 会话总结')).not.toBeInTheDocument();
  });
});

describe('Conversations 空数据与报错降级', () => {
  it('无会话时展示空态且右侧保持未选中', async () => {
    routeRequestsBy([], page([]));
    renderConversations();

    expect(await screen.findByText('暂无会话')).toBeInTheDocument();
    expect(screen.getByText('请选择会话')).toBeInTheDocument();
  });

  it('列表接口报错时降级为空态, 不崩溃', async () => {
    get.mockImplementation(((url: string) => {
      if (String(url).startsWith('/scrm/message-templates')) return Promise.resolve(page([]));
      return Promise.reject(new Error('boom'));
    }) as never);

    renderConversations();

    expect(await screen.findByText('暂无会话')).toBeInTheDocument();
    expect(screen.queryByText('张三')).not.toBeInTheDocument();
  });

  it('状态筛选切换后带 status 参数重新请求', async () => {
    const user = userEvent.setup();
    routeRequestsBy([conversation()], page([]));
    renderConversations();
    await screen.findByText('张三');

    await user.click(screen.getByText('全部状态'));
    const option = await screen.findByText('已关闭', {
      selector: '.ant-select-item-option-content',
    });
    await user.click(option);

    await waitFor(() =>
      expect(
        get.mock.calls.some((c) => String(c[0]).includes('status=CLOSED')),
      ).toBe(true),
    );
  });
});

/**
 * 页面接线测试: formatFriendlyTime / highlightKeyword 已从页面内联实现搬到 utils/* 独立模块,
 * 纯函数行为在 src/utils/{friendlyTime,highlight}.test.* 直测, 这里只锁定页面仍在消费这两个模块。
 */
describe('Conversations 时间展示与搜索高亮接线', () => {
  it('会话列表时间走 formatFriendlyTime: 30 秒前展示 "刚刚", 缺失时间展示 "-"', async () => {
    routeRequestsBy(
      [
        conversation({ id: '5001', customerNickname: '张三', lastMessageAt: recentIso(30) }),
        conversation({
          id: '5002',
          customerNickname: '李四',
          lastMessageAt: undefined,
          lastMessageSummary: '无时间会话',
        }),
      ],
      page([]),
    );

    renderConversations();
    await screen.findByText('张三');

    expect(screen.getByText('刚刚')).toBeInTheDocument();
    expect(screen.getByText('-')).toBeInTheDocument();
  });

  it('搜索结果面板走 highlightKeyword: 关键词片段被 <mark> 包裹且原文完整', async () => {
    const user = userEvent.setup();
    get.mockImplementation(((url: string) => {
      const u = String(url);
      if (u.includes('/messages/search')) {
        return Promise.resolve([message({ id: 's-1', content: '我们有三档方案, 价格按席位计' })]);
      }
      if (u.includes('/messages')) return Promise.resolve(page([message({ id: 'm-1' })]));
      if (u.startsWith('/scrm/message-templates')) return Promise.resolve(page([]));
      return Promise.resolve(page([conversation({ id: '5001' })]));
    }) as never);

    renderConversations();
    await screen.findByText('张三');
    await user.click(screen.getByText('张三'));
    await screen.findByText('你好, 请问产品怎么收费?');

    await user.type(screen.getByPlaceholderText('搜索消息'), '价格{Enter}');

    expect(await screen.findByText(/找到 1 条结果/)).toBeInTheDocument();
    await waitFor(() =>
      expect(
        get.mock.calls.some((c) => String(c[0]).includes('/messages/search?keyword=')),
      ).toBe(true),
    );
    const marks = Array.from(document.querySelectorAll('mark'));
    expect(marks.map((m) => m.textContent)).toEqual(['价格']);
    expect(marks[0].parentElement?.textContent).toBe('我们有三档方案, 价格按席位计');
  });
});
