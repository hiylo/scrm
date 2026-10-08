/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Reports.test.tsx
 * Description : 报表中心页面的模板加载 / 新建 / 执行 / 结果测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Reports from './Reports';
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

/** 模板行 */
function template(id: string, overrides: Record<string, unknown> = {}) {
  return { id, templateName: `模板${id}`, reportType: 'CUSTOMER', dataSource: 'customer', status: 'ACTIVE', ...overrides } as never;
}

function renderPage() {
  return renderWithProviders(<Reports />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('Reports', () => {
  it('加载报表模板列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/templates/list')) return Promise.resolve(page([template('1'), template('2')]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('模板1')).toBeInTheDocument());
    expect(screen.getByText('模板2')).toBeInTheDocument();
  });

  it('新建模板: 打开弹窗填写后 POST', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/templates/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(template('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('新建模板') }));
    await waitFor(() => expect(screen.getByLabelText('模板名称')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('模板名称'), '客户月度报表');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/reports/templates', expect.objectContaining({ templateName: '客户月度报表' })));
  });

  it('执行报表: 调用 execute', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/templates/list')) return Promise.resolve(page([template('1')]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue({ id: 'r1', templateId: '1', rowCount: 100, runAt: '2026-10-08T10:00:00' });

    renderPage();
    await waitFor(() => expect(screen.getByText('模板1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('执行') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/reports/templates/1/execute', expect.anything()));
  });

  it('查看报表结果: 打开抽屉加载 results', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/templates/list')) return Promise.resolve(page([template('1')]));
      if (url.includes('/results')) {
        return Promise.resolve(page([{ id: 'r1', templateId: '1', runBy: '管理员', rowCount: 100, resultData: '{"total":100}', runAt: '2026-10-08T10:00:00' }]));
      }
      return Promise.resolve(page([]));
    });

    renderPage();
    await waitFor(() => expect(screen.getByText('模板1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('结果') }));

    await waitFor(() => expect(screen.getByText('管理员')).toBeInTheDocument());
    expect(screen.getByText(/total/)).toBeInTheDocument();
  });
});