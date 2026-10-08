/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Segments.test.tsx
 * Description : 客户分群页面的加载 / 新建分群 / 计算 / 成员测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Segments from './Segments';
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

/** 分群行 */
function segment(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    segmentName: `分群${id}`,
    segmentCode: `SEG-${id}`,
    segmentType: 'DYNAMIC',
    category: 'VALUE',
    status: 'ACTIVE',
    memberCount: 100,
    conditions: '[]',
    ...overrides,
  } as never;
}

function renderPage() {
  return renderWithProviders(<Segments />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('Segments', () => {
  it('加载客户分群列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/segments/list')) return Promise.resolve(page([segment('1'), segment('2')]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('分群1')).toBeInTheDocument());
    expect(screen.getByText('分群2')).toBeInTheDocument();
  });

  it('新建分群: 打开弹窗填写后 POST', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/segments/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(segment('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('新建分群') }));
    await waitFor(() => expect(screen.getByLabelText('分群名称')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('分群名称'), '高价值客户');
    await userEvent.type(screen.getByLabelText('分群编码'), 'VIP_CUSTOMER');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/segments', expect.objectContaining({ segmentName: '高价值客户', segmentCode: 'VIP_CUSTOMER' })));
  });

  it('触发计算: 调用 calculate', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/segments/list')) return Promise.resolve(page([segment('1')]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue({ count: 120 });

    renderPage();
    await waitFor(() => expect(screen.getByText('分群1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('计算') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/segments/1/calculate', expect.anything()));
  });

  it('查看成员: 打开抽屉加载 members', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/segments/list')) return Promise.resolve(page([segment('1')]));
      if (url.includes('/members?')) {
        return Promise.resolve(page([{ id: 'm1', segmentId: '1', customerId: '1', customerName: '张三', source: 'AUTO', joinedAt: '2026-10-08T10:00:00' }]));
      }
      return Promise.resolve(page([]));
    });

    renderPage();
    await waitFor(() => expect(screen.getByText('分群1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('成员') }));

    await waitFor(() => expect(screen.getByText('张三')).toBeInTheDocument());
  });
});