/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Assets.test.tsx
 * Description : 素材库页面的分类加载 / 素材列表 / 上传 / 发布测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Assets from './Assets';
import { apiClient, apiClientInstance } from '../api/client';
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
const instancePost = vi.mocked(apiClientInstance.post);

/** 分页响应 */
function page<T>(rows: T[], total = rows.length) {
  return { content: rows, totalElements: total, totalPages: 1, number: 0, size: 10 } as never;
}

/** 分类树 */
function categoryTree() {
  return [{ id: '1', categoryName: '产品图', status: 'ACTIVE', children: [] }] as never;
}

/** 素材行 */
function asset(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    assetName: `素材${id}`,
    assetCode: `AST-${id}`,
    assetType: 'IMAGE',
    categoryName: '产品图',
    reviewStatus: 'APPROVED',
    status: 'DRAFT',
    fileSizeBytes: 2048,
    fileUrl: 'https://cdn.example.com/a.png',
    createTime: '2026-10-08T10:00:00',
    ...overrides,
  } as never;
}

function renderPage() {
  return renderWithProviders(<Assets />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('Assets', () => {
  it('加载分类树与素材列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/categories/tree')) return Promise.resolve(categoryTree());
      if (url.startsWith('/scrm/assets/list')) return Promise.resolve(page([asset('1'), asset('2', { fileSizeBytes: 10485760 })]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getAllByText('产品图').length).toBeGreaterThan(0));
    await waitFor(() => expect(screen.getByText('素材1')).toBeInTheDocument());
    expect(screen.getByText('素材2')).toBeInTheDocument();
    expect(screen.getByText('2.0 KB')).toBeInTheDocument();
    expect(screen.getByText('10.0 MB')).toBeInTheDocument();
  });

  it('上传素材: 打开弹窗填写后 POST 并刷新列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/categories/tree')) return Promise.resolve(categoryTree());
      if (url.startsWith('/scrm/assets/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(asset('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('上传素材') }));
    await waitFor(() => expect(screen.getByLabelText('素材名称')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('素材名称'), '618 主图');
    await userEvent.type(screen.getByLabelText('文件 URL'), 'https://cdn.example.com/b.png');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/assets/upload', expect.objectContaining({ assetName: '618 主图', fileUrl: 'https://cdn.example.com/b.png' })));
  });

  it('上传素材(文件): 选择文件后 multipart POST /upload-file 并刷新列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/categories/tree')) return Promise.resolve(categoryTree());
      if (url.startsWith('/scrm/assets/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    instancePost.mockResolvedValue(asset('2'));
    const file = new File(['img-bytes'], 'hero.png', { type: 'image/png' });

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('上传素材') }));
    await waitFor(() => expect(screen.getByLabelText('素材名称')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('素材名称'), '618 主图');

    // 触发上传文件选择: 通过 Upload 组件的 beforeUpload (input file change)
    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
    // jsdom 不支持 fireEvent.change 直接赋 File 列表, 用 Object.defineProperty + change
    Object.defineProperty(fileInput, 'files', { value: [file], configurable: true });
    fireEvent.change(fileInput);

    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(instancePost).toHaveBeenCalledWith(
      '/scrm/assets/upload-file',
      expect.any(FormData),
      expect.objectContaining({ headers: { 'Content-Type': 'multipart/form-data' } }),
    ));
    // 校验 FormData 内容
    const called = instancePost.mock.calls.find((c) => c[0] === '/scrm/assets/upload-file');
    const fd = called?.[1] as FormData | undefined;
    expect(fd?.get('assetName')).toBe('618 主图');
    expect(fd?.get('assetType')).toBe('IMAGE');
    expect(fd?.get('file')).toBe(file);
    expect(instancePost.mock.calls.length).toBe(1);
  });

  it('发布素材: 调用 publish', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/categories/tree')) return Promise.resolve(categoryTree());
      if (url.startsWith('/scrm/assets/list')) return Promise.resolve(page([asset('1')]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(asset('1', { status: 'PUBLISHED' }));

    renderPage();
    await waitFor(() => expect(screen.getByText('素材1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('发布') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/assets/1/publish'));
  });
});