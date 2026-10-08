/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : AutoTags.test.tsx
 * Description : 自动打标规则页面的加载 / 创建 / 启停 / 手动评估测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import AutoTags from './AutoTags';
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
    triggerEvent: 'CUSTOMER_CREATED',
    conditionType: 'ALL',
    conditions: '[{"field":"lifecycle","operator":"eq","value":"NEW"}]',
    actionType: 'ADD_TAG',
    actionParams: '{"tagIds":[1]}',
    priority: 0,
    enabled: true,
    matchCount: 3,
    ...overrides,
  } as never;
}

function renderPage() {
  return renderWithProviders(<AutoTags />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('AutoTags', () => {
  it('加载并展示规则列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/fields') || url.includes('/operators')) return Promise.resolve([]);
      if (url.startsWith('/scrm/auto-tags/rules/list')) return Promise.resolve(page([rule('1'), rule('2')]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('规则1')).toBeInTheDocument());
    expect(screen.getByText('规则2')).toBeInTheDocument();
  });

  it('加载条件字段与操作符元数据', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/fields')) return Promise.resolve([{ name: 'lifecycle', description: '生命周期' }]);
      if (url.includes('/operators')) return Promise.resolve([{ name: 'eq', description: '等于' }]);
      if (url.startsWith('/scrm/auto-tags/rules/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(get).toHaveBeenCalledWith('/scrm/auto-tags/fields'));
    expect(get).toHaveBeenCalledWith('/scrm/auto-tags/operators');
  });

  it('创建规则: 打开弹窗填写后 POST 并刷新列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/fields') || url.includes('/operators')) return Promise.resolve([]);
      if (url.startsWith('/scrm/auto-tags/rules/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(rule('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('新建规则') }));
    await waitFor(() => expect(screen.getByLabelText('规则名称')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('规则名称'), '新客打标');
    // 弹窗触发事件 Select: 用 label 精确定位 (页面顶栏过滤 Select 只有 placeholder 无 label)
    fireEvent.mouseDown(screen.getByLabelText('触发事件'));
    await waitFor(() => expect(screen.getAllByText('客户创建').length).toBeGreaterThanOrEqual(1));
    fireEvent.click(screen.getAllByText('客户创建').pop()!);
    // 必填 JSON: conditions / actionParams 已有默认 [] / {} (openCreate 时 setFieldsValue), 无需填
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/auto-tags/rules', expect.objectContaining({ ruleName: '新客打标', triggerEvent: 'CUSTOMER_CREATED' })));
  });

  it('停用规则: 点击停用调用 disable', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/fields') || url.includes('/operators')) return Promise.resolve([]);
      if (url.startsWith('/scrm/auto-tags/rules/list')) return Promise.resolve(page([rule('1')]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(rule('1', { enabled: false }));

    renderPage();
    await waitFor(() => expect(screen.getByText('规则1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('停用') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/auto-tags/rules/1/disable'));
  });

  it('手动评估: 选择触发事件后提交返回命中规则日志', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/fields') || url.includes('/operators')) return Promise.resolve([]);
      if (url.startsWith('/scrm/auto-tags/rules/list')) return Promise.resolve(page([rule('1')]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue([
      { id: 'log1', ruleId: '1', actionType: 'ADD_TAG', actionResult: 'SUCCESS', actionDetail: '已添加标签', executedAt: '2026-10-08T10:00:00' },
    ]);

    renderPage();
    await waitFor(() => expect(screen.getByText('规则1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('手动评估') }));

    await waitFor(() => expect(screen.getByLabelText('客户 ID')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('客户 ID'), '1001');
    // 弹窗中的触发事件 Select (页面顶栏也有一个, 取弹窗里最后一个); 下拉选项与表格 Tag 文本重名, 点 DOM 序靠后的下拉项
    const combos = screen.getAllByRole('combobox');
    fireEvent.mouseDown(combos[combos.length - 1]);
    await waitFor(() => expect(screen.getAllByText('客户创建').length).toBeGreaterThanOrEqual(2));
    fireEvent.click(screen.getAllByText('客户创建').pop()!);

    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/auto-tags/evaluate', expect.objectContaining({ customerId: 1001, triggerEvent: 'CUSTOMER_CREATED' })));
    await waitFor(() => expect(screen.getByText('SUCCESS')).toBeInTheDocument());
  });
});