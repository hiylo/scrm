/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Surveys.test.tsx
 * Description : NPS 问卷页面的加载 / 新建 / 发布 / 邀请测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Surveys from './Surveys';
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

/** 问卷行 */
function survey(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    surveyName: `问卷${id}`,
    surveyType: 'NPS',
    title: '推荐意愿调研',
    questions: '[]',
    scaleType: '0-10',
    status: 'DRAFT',
    responseCount: 0,
    createTime: '2026-10-08T10:00:00',
    ...overrides,
  } as never;
}

function renderPage() {
  return renderWithProviders(<Surveys />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('Surveys', () => {
  it('加载问卷列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/surveys/list')) return Promise.resolve(page([survey('1'), survey('2', { surveyName: '问卷2' })]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('问卷1')).toBeInTheDocument());
    expect(screen.getByText('问卷2')).toBeInTheDocument();
  });

  it('新建问卷: 打开弹窗填写后 POST', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/surveys/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(survey('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('新建问卷') }));
    await waitFor(() => expect(screen.getByLabelText('问卷名称')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('问卷名称'), '6月调研');
    await userEvent.type(screen.getByLabelText('问卷标题'), '推荐意愿');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/surveys', expect.objectContaining({ surveyName: '6月调研', title: '推荐意愿' })));
  });

  it('发布问卷: 草稿调用 activate', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/surveys/list')) return Promise.resolve(page([survey('1')]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(survey('1', { status: 'ACTIVE' }));

    renderPage();
    await waitFor(() => expect(screen.getByText('问卷1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('发布') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/surveys/1/activate'));
  });

  it('生成邀请: 打开弹窗填写后 batch', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/surveys/list')) return Promise.resolve(page([survey('1')]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue({ successCount: 20 });

    renderPage();
    await waitFor(() => expect(screen.getByText('问卷1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('邀请') }));
    await waitFor(() => expect(screen.getByLabelText('邀请数量')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('邀请数量'), '20');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/surveys/invitations/batch', expect.objectContaining({ surveyId: '1', quantity: 20 })));
  });
});