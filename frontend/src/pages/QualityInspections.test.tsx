/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : QualityInspections.test.tsx
 * Description : 会话质检页面的规则 / 任务 / 执行 / 结果测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import QualityInspections from './QualityInspections';
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

/** 规则行 */
function rule(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    ruleName: `规则${id}`,
    category: 'SENSITIVE_WORD',
    ruleType: 'KEYWORD_REGEX',
    ruleConfig: '{"keywords":["辱骂"]}',
    passCondition: 'ALL_PASS',
    scoreWeight: 1,
    enabled: true,
    matchCount: 5,
    passCount: 4,
    ...overrides,
  } as never;
}

/** 任务行 */
function task(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    taskName: `任务${id}`,
    inspectionScope: 'ALL',
    ruleIds: '1,2',
    totalConversations: 100,
    inspectedCount: 0,
    passedCount: 0,
    failedCount: 0,
    status: 'PENDING',
    createTime: '2026-10-08T10:00:00',
    ...overrides,
  } as never;
}

function renderPage() {
  return renderWithProviders(<QualityInspections />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('QualityInspections', () => {
  it('加载质检规则 (默认 Tab)', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/rules/list')) return Promise.resolve(page([rule('1'), rule('2')]));
      if (url.includes('/tasks/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('规则1')).toBeInTheDocument());
    expect(screen.getByText('规则2')).toBeInTheDocument();
  });

  it('新建规则: 打开弹窗填写后 POST', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/rules/list')) return Promise.resolve(page([]));
      if (url.includes('/tasks/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(rule('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('新建规则') }));
    await waitFor(() => expect(screen.getByLabelText('规则名称')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('规则名称'), '禁骂规则');
    // JSON 含花括号, userEvent 会解析键盘描述, 用 fireEvent.change 直接设值
    fireEvent.change(screen.getByLabelText('规则配置 JSON'), { target: { value: '{"keywords":["滚"]}' } });
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/quality-inspections/rules', expect.objectContaining({ ruleName: '禁骂规则' })));
  });

  it('切换到任务 Tab, 执行任务', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/rules/list')) return Promise.resolve(page([]));
      if (url.includes('/tasks/list')) return Promise.resolve(page([task('1')]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(task('1', { status: 'RUNNING' }));

    renderPage();
    fireEvent.click(screen.getByRole('tab', { name: '质检任务' }));
    await waitFor(() => expect(screen.getByText('任务1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('执行') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/quality-inspections/tasks/1/execute'));
  });

  it('查看任务结果: 打开抽屉加载结果', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/rules/list')) return Promise.resolve(page([]));
      if (url.includes('/tasks/list')) return Promise.resolve(page([task('1', { status: 'COMPLETED', inspectedCount: 100, passedCount: 80, failedCount: 20 })]));
      if (url.includes('/tasks/1/results')) {
        return Promise.resolve([{ id: 'r1', taskId: '1', conversationId: 'c1', customerName: '张三', totalScore: 90, passed: true, inspectedAt: '2026-10-08T10:00:00' }]);
      }
      return Promise.resolve(page([]));
    });

    renderPage();
    fireEvent.click(screen.getByRole('tab', { name: '质检任务' }));
    await waitFor(() => expect(screen.getByText('任务1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('结果') }));

    await waitFor(() => expect(screen.getByText('张三')).toBeInTheDocument());
    expect(screen.getByText('90')).toBeInTheDocument();
  });
});