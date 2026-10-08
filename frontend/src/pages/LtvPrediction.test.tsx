/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : LtvPrediction.test.tsx
 * Description : LTV 预测页面的模型加载 / 新建模型 / 发布 / 同期群 / 预测明细测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import LtvPrediction from './LtvPrediction';
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

/** 模型行 */
function model(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    modelName: `模型${id}`,
    modelCode: `LTV-${id}`,
    modelType: 'HISTORICAL',
    timeWindowDays: 365,
    predictionPeriodDays: 365,
    status: 'DRAFT',
    ...overrides,
  } as never;
}

function renderPage() {
  return renderWithProviders(<LtvPrediction />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('LtvPrediction', () => {
  it('加载 LTV 模型列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/models/list')) return Promise.resolve(page([model('1'), model('2')]));
      if (url.includes('/cohorts/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('模型1')).toBeInTheDocument());
    expect(screen.getByText('模型2')).toBeInTheDocument();
  });

  it('新建模型: 打开弹窗填写后 POST', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/models/list')) return Promise.resolve(page([]));
      if (url.includes('/cohorts/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(model('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('新建模型') }));
    await waitFor(() => expect(screen.getByLabelText('模型名称')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('模型名称'), '年度 LTV');
    await userEvent.type(screen.getByLabelText('模型编码'), 'LTV_2026');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/ltv/models', expect.objectContaining({ modelName: '年度 LTV', modelCode: 'LTV_2026' })));
  });

  it('发布模型: 草稿调用 publish', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/models/list')) return Promise.resolve(page([model('1')]));
      if (url.includes('/cohorts/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(model('1', { status: 'PUBLISHED' }));

    renderPage();
    await waitFor(() => expect(screen.getByText('模型1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('发布') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/ltv/models/1/publish'));
  });

  it('切换到同期群 Tab 加载留存数据', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/models/list')) return Promise.resolve(page([]));
      if (url.includes('/cohorts/list')) return Promise.resolve(page([{ id: 'c1', cohortName: '2026-01 群', cohortMonth: '2026-01', customerCount: 100, retentionRate: 0.3, avgRevenue: 1000 }]));
      return Promise.resolve(page([]));
    });

    renderPage();
    fireEvent.click(screen.getByRole('tab', { name: '同期群分析' }));

    await waitFor(() => expect(screen.getByText('2026-01 群')).toBeInTheDocument());
    expect(screen.getByText('30.0%')).toBeInTheDocument();
  });

  it('查看预测明细: 打开抽屉加载客户预测', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/models/list')) return Promise.resolve(page([model('1', { status: 'PUBLISHED' })]));
      if (url.includes('/cohorts/list')) return Promise.resolve(page([]));
      if (url.includes('/customers/list')) {
        return Promise.resolve(page([{ id: 'p1', customerId: '1', customerName: '张三', predictedLtv: 5000, predictedLifespanMonths: 12, churnRisk: 0.2 }]));
      }
      return Promise.resolve(page([]));
    });

    renderPage();
    await waitFor(() => expect(screen.getByText('模型1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('预测') }));

    await waitFor(() => expect(screen.getByText('张三')).toBeInTheDocument());
    expect(screen.getByText('¥5,000')).toBeInTheDocument();
  });
});