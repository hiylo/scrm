/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : CustomerJourneys.test.tsx
 * Description : 客户旅程页面的加载 / 新建旅程 / 启动 / 步骤测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import CustomerJourneys from './CustomerJourneys';
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

/** 旅程行 */
function journey(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    journeyName: `旅程${id}`,
    journeyCode: `J-${id}`,
    triggerEvent: 'CUSTOMER_CREATED',
    status: 'DRAFT',
    enrollmentCount: 0,
    completedCount: 0,
    createTime: '2026-10-08T10:00:00',
    ...overrides,
  } as never;
}

function renderPage() {
  return renderWithProviders(<CustomerJourneys />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('CustomerJourneys', () => {
  it('加载客户旅程列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/customer-journeys/list')) return Promise.resolve(page([journey('1'), journey('2')]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('旅程1')).toBeInTheDocument());
    expect(screen.getByText('旅程2')).toBeInTheDocument();
  });

  it('新建旅程: 打开弹窗填写后 POST', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/customer-journeys/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(journey('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('新建旅程') }));
    await waitFor(() => expect(screen.getByLabelText('旅程名称')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('旅程名称'), '新客培育');
    await userEvent.type(screen.getByLabelText('旅程编码'), 'ONBOARD');
    await userEvent.type(screen.getByLabelText('触发事件'), 'CUSTOMER_CREATED');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/customer-journeys', expect.objectContaining({ journeyName: '新客培育', journeyCode: 'ONBOARD' })));
  });

  it('查看旅程步骤: 打开抽屉加载 steps', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/customer-journeys/list')) return Promise.resolve(page([journey('1')]));
      if (url.includes('/steps')) {
        return Promise.resolve([{ id: 's1', journeyId: '1', stepName: '欢迎语', stepType: 'ACTION', stepOrder: 1, config: '{"content":"welcome"}' }]);
      }
      return Promise.resolve(page([]));
    });

    renderPage();
    await waitFor(() => expect(screen.getByText('旅程1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('步骤') }));

    await waitFor(() => expect(screen.getByText('欢迎语')).toBeInTheDocument());
  });

  it('启动旅程: 草稿调用 activate', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/customer-journeys/list')) return Promise.resolve(page([journey('1')]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(journey('1', { status: 'ACTIVE' }));

    renderPage();
    await waitFor(() => expect(screen.getByText('旅程1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('启动') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/customer-journeys/1/activate'));
  });
});