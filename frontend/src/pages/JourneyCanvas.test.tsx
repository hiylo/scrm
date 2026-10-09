/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : JourneyCanvas.test.tsx
 * Description : 客户旅程画布编辑器的加载 / 步骤保存 / 调用契约测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import JourneyCanvas from './JourneyCanvas';
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
const put = vi.mocked(apiClient.put);

/** 分页响应 */
function page<T>(rows: T[], total = rows.length) {
  return { content: rows, totalElements: total, totalPages: 1, number: 0, size: 10 } as never;
}

/** 旅程行 */
function journey(id: string, overrides: Record<string, unknown> = {}) {
  return { id, journeyName: `旅程${id}`, journeyCode: `J-${id}`, status: 'DRAFT', ...overrides } as never;
}

/** 步骤 */
function step(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    journeyId: '1',
    stepName: `步骤${id}`,
    stepType: 'SEND_MESSAGE',
    stepOrder: Number(id),
    config: '{}',
    nextStepId: undefined,
    isEntryPoint: Number(id) === 1,
    ...overrides,
  } as never;
}

function renderPage() {
  return renderWithProviders(<JourneyCanvas />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('JourneyCanvas', () => {
  it('加载旅程列表并默认选中第一个加载画布', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/customer-journeys/list')) return Promise.resolve(page([journey('1'), journey('2')]));
      if (/\/customer-journeys\/\d+\/steps/.test(url)) return Promise.resolve([]);
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(get).toHaveBeenCalledWith(expect.stringContaining('/customer-journeys/list')));
    await waitFor(() => expect(get).toHaveBeenCalledWith('/scrm/customer-journeys/1/steps'));
    // 旅程选项渲染
    expect(screen.getByText('旅程1')).toBeInTheDocument();
  });

  it('切换旅程触发加载其步骤', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/customer-journeys/list')) return Promise.resolve(page([journey('1'), journey('2')]));
      if (/\/customer-journeys\/\d+\/steps/.test(url)) return Promise.resolve([]);
      return Promise.resolve(page([]));
    });

    renderPage();
    await waitFor(() => expect(screen.getByText('旅程1')).toBeInTheDocument());

    fireEvent.mouseDown(document.querySelector('.ant-select-selector') as Element);
    await waitFor(() => expect(screen.getByText('旅程2')).toBeInTheDocument());
    fireEvent.click(screen.getByText('旅程2'));

    await waitFor(() => expect(get).toHaveBeenCalledWith('/scrm/customer-journeys/2/steps'));
  });

  it('新增步骤: 打开弹窗 POST /steps', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/customer-journeys/list')) return Promise.resolve(page([journey('1')]));
      if (/\/customer-journeys\/\d+\/steps/.test(url)) return Promise.resolve([]);
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(step('5'));

    renderPage();
    await waitFor(() => expect(screen.getByText('旅程1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('新增步骤') }));
    await waitFor(() => expect(screen.getByLabelText('步骤名称')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('步骤名称'), '发送促销消息');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith(
      '/scrm/customer-journeys/1/steps',
      expect.objectContaining({ stepName: '发送促销消息', stepType: 'SEND_MESSAGE' }),
    ));
  });

  it('保存画布: 同步连线 nextStepId + 重排', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/customer-journeys/list')) return Promise.resolve(page([journey('1')]));
      if (/\/customer-journeys\/\d+\/steps/.test(url)) {
        return Promise.resolve([]);
      }
      return Promise.resolve(page([]));
    });
    put.mockResolvedValue(step('1', { nextStepId: '2' }));
    post.mockResolvedValue([]);

    renderPage();
    await waitFor(() => expect(screen.getByText('旅程1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('保存画布') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith(
      '/scrm/customer-journeys/1/steps/reorder',
      expect.any(Array),
    ));
  });
});