/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : DataDictionaries.test.tsx
 * Description : 数据字典页面的字典加载 / 新建 / 字典项管理测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import DataDictionaries from './DataDictionaries';
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

/** 字典行 */
function dict(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    dictName: `字典${id}`,
    dictCode: `DICT_${id}`,
    dictType: 'ENUM',
    status: 'ACTIVE',
    itemCount: 3,
    ...overrides,
  } as never;
}

/** 字典项 */
function item(id: string, overrides: Record<string, unknown> = {}) {
  return { id, dictId: '1', itemLabel: `项${id}`, itemValue: `V${id}`, sortOrder: 1, enabled: true, ...overrides } as never;
}

function renderPage() {
  return renderWithProviders(<DataDictionaries />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('DataDictionaries', () => {
  it('加载字典列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/data-dictionaries/list')) return Promise.resolve(page([dict('1'), dict('2')]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('字典1')).toBeInTheDocument());
    expect(screen.getByText('字典2')).toBeInTheDocument();
  });

  it('新建字典: 打开弹窗填写后 POST', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/data-dictionaries/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(dict('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('新建字典') }));
    await waitFor(() => expect(screen.getByLabelText('字典名称')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('字典名称'), '客户生命周期');
    await userEvent.type(screen.getByLabelText('字典编码'), 'CUSTOMER_LIFECYCLE');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/data-dictionaries', expect.objectContaining({ dictName: '客户生命周期', dictCode: 'CUSTOMER_LIFECYCLE' })));
  });

  it('查看字典项: 打开抽屉加载 items', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/data-dictionaries/list')) return Promise.resolve(page([dict('1')]));
      if (url.includes('/items')) return Promise.resolve([item('1'), item('2')]);
      return Promise.resolve(page([]));
    });

    renderPage();
    await waitFor(() => expect(screen.getByText('字典1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('项') }));

    await waitFor(() => expect(screen.getByText('项1')).toBeInTheDocument());
    expect(screen.getByText('项2')).toBeInTheDocument();
  });

  it('新增字典项: drawTopen 弹窗填写后 POST items', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/data-dictionaries/list')) return Promise.resolve(page([dict('1')]));
      if (url.includes('/items')) return Promise.resolve([item('1')]);
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(item('2'));

    renderPage();
    await waitFor(() => expect(screen.getByText('字典1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('项') }));
    await waitFor(() => expect(screen.getByText('项1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('新增字典项') }));
    await waitFor(() => expect(screen.getByLabelText('标签')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('标签'), '流失客户');
    await userEvent.type(screen.getByLabelText('值'), 'CHURNED');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/data-dictionaries/items', expect.objectContaining({ itemLabel: '流失客户', itemValue: 'CHURNED', dictId: '1' })));
  });
});