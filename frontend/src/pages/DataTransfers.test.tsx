/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : DataTransfers.test.tsx
 * Description : 数据导入导出页面的任务加载 / 新建任务 / 明细测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import DataTransfers from './DataTransfers';
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

/** 任务行 */
function task(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    taskName: `任务${id}`,
    taskCode: `TASK_${id}`,
    transferType: 'EXPORT',
    dataType: 'CUSTOMER',
    status: 'COMPLETED',
    totalCount: 100,
    successCount: 100,
    failCount: 0,
    fileUrl: 'https://cdn.example.com/export.xlsx',
    completedAt: '2026-10-08T10:00:00',
    ...overrides,
  } as never;
}

function renderPage() {
  return renderWithProviders(<DataTransfers />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('DataTransfers', () => {
  it('加载导入导出任务列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/data-transfer/tasks/list')) return Promise.resolve(page([task('1'), task('2')]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('任务1')).toBeInTheDocument());
    expect(screen.getByText('任务2')).toBeInTheDocument();
  });

  it('新建任务: 打开弹窗填写后 POST', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/data-transfer/tasks/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(task('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('新建任务') }));
    await waitFor(() => expect(screen.getByLabelText('任务名称')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('任务名称'), '10 月客户导出');
    await userEvent.type(screen.getByLabelText('任务编码'), 'EXP_OCT');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/data-transfer/tasks', expect.objectContaining({ taskName: '10 月客户导出', taskCode: 'EXP_OCT' })));
  });

  it('查看任务明细: 打开抽屉加载 records', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/data-transfer/tasks/list')) return Promise.resolve(page([task('1')]));
      if (url.includes('/records')) {
        return Promise.resolve([{ id: 'r1', taskId: '1', rowKey: '2026-001', status: 'SUCCESS', processedAt: '2026-10-08T10:00:00' }]);
      }
      return Promise.resolve(page([]));
    });

    renderPage();
    await waitFor(() => expect(screen.getByText('任务1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('明细') }));

    await waitFor(() => expect(screen.getByText('2026-001')).toBeInTheDocument());
  });
});