/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : DataTransfers.test.tsx
 * Description : 数据导入模板页面测试 - 列表加载 / 新建模板 / 启停
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

/** 导入模板行（字段与后端 ScrmImportTemplateEntity 对齐） */
function template(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    templateName: `模板${id}`,
    dataType: 'CUSTOMER',
    columns: '["nickname","phone"]',
    deduplicationKey: 'phone',
    updateIfExists: false,
    enabled: true,
    createTime: '2026-10-08T10:00:00',
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
  it('加载导入模板列表', async () => {
    get.mockResolvedValue(page([template('1'), template('2')]));

    renderPage();

    await waitFor(() => expect(screen.getByText('模板1')).toBeInTheDocument());
    expect(screen.getByText('模板2')).toBeInTheDocument();
    // 契约：列表走 templates/list；用 tasks/list 会落到 SPA 兜底返回 HTML
    expect(get).toHaveBeenCalledWith(expect.stringContaining('/scrm/data-transfer/templates/list'));
    expect(get).not.toHaveBeenCalledWith(expect.stringContaining('/data-transfer/tasks'));
  });

  it('新建模板: 填写列定义后 POST templates', async () => {
    get.mockResolvedValue(page([]));
    post.mockResolvedValue(template('1'));

    renderPage();
    await waitFor(() => expect(screen.getByText('还没有导入模板，点右上「新建模板」创建')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: cn('新建模板') }));
    await waitFor(() => expect(screen.getByLabelText('模板名称')).toBeInTheDocument());

    await userEvent.type(screen.getByLabelText('模板名称'), '客户批量导入');
    // 列定义是必填项，后端要求 TEXT；花括号用 fireEvent 直接赋值
    fireEvent.change(screen.getByLabelText('列定义'), {
      target: { value: '[{"source":"姓名","target":"nickname"}]' },
    });

    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        '/scrm/data-transfer/templates',
        expect.objectContaining({ templateName: '客户批量导入', dataType: 'CUSTOMER' }),
      ),
    );
  });

  it('启停模板: 调用 enable/disable 接口', async () => {
    get.mockResolvedValue(page([template('1', { enabled: true })]));
    post.mockResolvedValue(undefined);

    renderPage();
    await waitFor(() => expect(screen.getByText('模板1')).toBeInTheDocument());

    const sw = document.querySelector('.ant-table tbody .ant-switch') as HTMLElement;
    expect(sw).toBeTruthy();
    fireEvent.click(sw);

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(expect.stringContaining('/templates/1/disable'), expect.anything()),
    );
  });
});
