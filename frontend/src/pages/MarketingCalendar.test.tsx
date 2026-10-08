/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : MarketingCalendar.test.tsx
 * Description : 营销日历页面的事件加载 / 新建事件 / 状态流转 / 假日测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import MarketingCalendar from './MarketingCalendar';
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

/** 事件行 */
function event(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    eventTitle: `事件${id}`,
    eventType: 'CAMPAIGN',
    startDate: '2026-06-01',
    endDate: '2026-06-03',
    priority: 'MEDIUM',
    status: 'PENDING',
    ownerName: '小张',
    ...overrides,
  } as never;
}

function renderPage() {
  return renderWithProviders(<MarketingCalendar />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('MarketingCalendar', () => {
  it('加载营销日历事件列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('marketing-calendar/list')) return Promise.resolve(page([event('1'), event('2')]));
      if (url.includes('/holidays/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(get).toHaveBeenCalledWith(expect.stringContaining('marketing-calendar/list')));
    expect(screen.getAllByText(/06-01/).length).toBeGreaterThan(0);
  });

  it('新建事件: 打开弹窗填写后 POST', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('marketing-calendar/list')) return Promise.resolve(page([]));
      if (url.includes('/holidays/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(event('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('新建事件') }));
    await waitFor(() => expect(screen.getByLabelText('事件标题')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('事件标题'), '618 预热');
    await userEvent.type(screen.getByLabelText('开始日期'), '2026-06-01');
    await userEvent.type(screen.getByLabelText('结束日期'), '2026-06-03');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/marketing-calendar', expect.objectContaining({ eventTitle: '618 预热' })));
  });

  it('确认事件: 待确认调用 confirm', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('marketing-calendar/list')) return Promise.resolve(page([event('1')]));
      if (url.includes('/holidays/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(event('1', { status: 'CONFIRMED' }));

    renderPage();
    await waitFor(() => expect(screen.getAllByText(/06-01/).length).toBeGreaterThan(0));
    fireEvent.click(screen.getByRole('button', { name: cn('确认') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/marketing-calendar/1/confirm', expect.anything()));
  });

  it('切换到假日 Tab 加载假日', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('marketing-calendar/list')) return Promise.resolve(page([]));
      if (url.includes('/holidays/list')) return Promise.resolve(page([{ id: 'h1', holidayName: '国庆节', holidayType: 'NATIONAL', holidayDate: '2026-10-01', enabled: true }]));
      return Promise.resolve(page([]));
    });

    renderPage();
    fireEvent.click(screen.getByRole('tab', { name: '假日管理' }));

    await waitFor(() => expect(screen.getByText('国庆节')).toBeInTheDocument());
  });
});
