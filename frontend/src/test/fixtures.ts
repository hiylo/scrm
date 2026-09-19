/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : fixtures.ts
 * Description : 测试夹具, 提供 JWT 构造与后端响应样例数据
 */

/** base64url 编码 (对应 auth.ts 中的 decodeJwtPayload) */
export function b64url(value: unknown): string {
  return btoa(JSON.stringify(value))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
}

/**
 * 构造按钮可访问名称的容错匹配器
 * <p>
 * 需要容忍两类渲染差异: antd 会给无图标的两汉字按钮插入空格 ("删 除"),
 * 带图标的按钮可访问名称会前置图标 aria-label ("edit 编辑")。
 */
export function cn(text: string): RegExp {
  return new RegExp(`^[^\\u4e00-\\u9fa5]*${text.split('').join('\\s*')}$`);
}

/** 构造一个未签名的测试 JWT */
export function makeJwt(payload: Record<string, unknown>): string {
  return `${b64url({ alg: 'HS256', typ: 'JWT' })}.${b64url(payload)}.test-signature`;
}

/** 生成一个尚未过期的访问令牌 */
export function validToken(ttlSeconds = 3600): string {
  return makeJwt({ sub: 'tester', exp: Math.floor(Date.now() / 1000) + ttlSeconds });
}

/** 生成一个已过期的访问令牌 */
export function expiredToken(): string {
  return makeJwt({ sub: 'tester', exp: Math.floor(Date.now() / 1000) - 60 });
}

/** Spring Page 结构 */
export interface MockPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/** 构造 Spring Page 响应 */
export function page<T>(content: T[], overrides: Partial<MockPage<T>> = {}): MockPage<T> {
  return {
    content,
    totalElements: overrides.totalElements ?? content.length,
    totalPages: overrides.totalPages ?? 1,
    number: overrides.number ?? 0,
    size: overrides.size ?? 10,
  };
}

/** OperationResponse 成功包装 */
export function ok<T>(data: T): { status: string; data: T; message: string; code: string } {
  return { status: 'SUCCESS', data, message: 'ok', code: '0' };
}

/** 可外部结算的 Promise, 用于在测试里精确控制接口何时返回 */
export interface Deferred<T> {
  promise: Promise<T>;
  resolve: (value: T) => void;
  reject: (reason?: unknown) => void;
}

export function deferred<T = unknown>(): Deferred<T> {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

/** 客户列表行 */
export interface CustomerRow {
  id: string;
  platformType: string;
  platformCustomerUid: string;
  nickname: string;
  lifecycle: string;
  ownerAccountId?: string;
  lastInteractionAt?: string;
  nextFollowUpAt?: string;
  createTime?: string;
  remark?: string;
}

/** 一条示例客户记录 */
export function customer(overrides: Partial<CustomerRow> = {}): CustomerRow {
  return {
    id: '1001',
    platformType: 'wework',
    platformCustomerUid: 'wm_userid_001',
    nickname: '张三',
    lifecycle: 'ACTIVE',
    ownerAccountId: '2001',
    lastInteractionAt: '2026-09-10T09:30:00',
    createTime: '2026-09-01T10:00:00',
    ...overrides,
  };
}

/** 会话列表行 */
export interface ConversationRow {
  id: string;
  customerId: string;
  customerNickname?: string;
  accountId: string;
  accountName?: string;
  platformType: string;
  status: string;
  lastMessageSummary?: string;
  lastMessageAt?: string;
  messageCount?: number;
  unreadCount?: number;
}

/** 一条示例会话记录 */
export function conversation(overrides: Partial<ConversationRow> = {}): ConversationRow {
  return {
    id: '5001',
    customerId: '1001',
    customerNickname: '张三',
    accountId: '3001',
    accountName: '企微小张',
    platformType: 'wework',
    status: 'ACTIVE',
    lastMessageSummary: '我想了解一下价格',
    lastMessageAt: '2026-09-12T10:00:00',
    messageCount: 12,
    unreadCount: 0,
    ...overrides,
  };
}

/** 会话消息 */
export interface MessageRow {
  id: string;
  messageId?: string;
  conversationId: string;
  content: string;
  messageType: string;
  direction: string;
  sentAt: string;
}

/** 一条示例消息 */
export function message(overrides: Partial<MessageRow> = {}): MessageRow {
  return {
    id: 'm-1',
    messageId: 'biz-1',
    conversationId: '5001',
    content: '你好, 请问产品怎么收费?',
    messageType: 'TEXT',
    direction: 'IN',
    sentAt: '2026-09-12T10:00:00',
    ...overrides,
  };
}

/** 看板概览数据 */
export function dashboardOverview(overrides: Record<string, unknown> = {}) {
  return {
    accountOverview: {
      totalAccounts: 12,
      platformDistribution: { wework: 12 },
      loginStateDistribution: { LOGIN: 9, FROZEN: 1 },
      onlineRate: 0.75,
      unhealthyCount: 3,
      totalHealthChecks: 48,
    },
    campaignOverview: {
      totalCampaigns: 5,
      statusDistribution: { RUNNING: 2, DRAFT: 3 },
      recentTrend: [],
    },
    customerOverview: {
      totalCustomers: 128,
      lifecycleDistribution: { NEW: 30, ACTIVE: 80, CHURNED: 18 },
      recentNewCustomers: [
        { date: '2026-09-11', count: 4 },
        { date: '2026-09-12', count: 7 },
      ],
    },
    conversationOverview: {
      totalConversations: 64,
      totalMessages: 890,
      activeConversations: 21,
      recentMessages: [
        { date: '2026-09-11', count: 40 },
        { date: '2026-09-12', count: 55 },
      ],
    },
    riskOverview: {
      totalRiskSignals: 9,
      riskLevelDistribution: { HIGH: 2, MEDIUM: 4, LOW: 3 },
      signalTypeDistribution: { FREQUENCY: 5, CONTENT: 4 },
      recentTrend: [],
    },
    ...overrides,
  };
}

/** 看板趋势数据 */
export function dashboardTrend(overrides: Record<string, unknown> = {}) {
  return {
    metric: 'customers',
    days: 7,
    startDate: '2026-09-06',
    endDate: '2026-09-12',
    totalCount: 21,
    trend: [
      { date: '2026-09-06', count: 3 },
      { date: '2026-09-07', count: 4 },
      { date: '2026-09-08', count: 5 },
    ],
    dailyAverage: 4,
    peakValue: 5,
    peakDate: '2026-09-08',
    ...overrides,
  };
}
