/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : InteractionCalendars.test.tsx
 * Description : 互动日历页面的加载 / 新建计划 / 状态流转测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import InteractionCalendars from './InteractionCalendars';
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

/** 计划行 */
function plan(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    planName: `计划${id}`,
    planCode: `IP-${id}`,
    planType: 'PHONE',
    customerId: '1',
    customerName: '张三',
    status: 'PENDING',
    plannedDate: '2026-10-10',
    plannedTime: '10:30',
    ...overrides,
  } as never;
}

function renderPage() {
  return renderWithProviders(<InteractionCalendars />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('InteractionCalendars', () => {
  it('加载互动计划列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/plans/list')) return Promise.resolve(page([plan('1'), plan('2')]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('计划1')).toBeInTheDocument());
    expect(screen.getByText('计划2')).toBeInTheDocument();
  });

  it('新建计划: 打开弹窗填写后 POST', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/plans/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(plan('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('新建计划') }));
    await waitFor(() => expect(screen.getByLabelText('计划名称')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('计划名称'), '回访客户');
    await userEvent.type(screen.getByLabelText('客户 ID'), '1');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/interaction-calendar/plans', expect.objectContaining({ planName: '回访客户', customerId: '1' })));
  });

  it('确认计划: 待确认调用 confirm', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/plans/list')) return Promise.resolve(page([plan('1')]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(plan('1', { status: 'CONFIRMED' }));

    renderPage();
    await waitFor(() => expect(screen.getByText('计划1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('确认') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/interaction-calendar/plans/1/confirm', expect.anything()));
  });
});