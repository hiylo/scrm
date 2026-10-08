/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : RfmAnalysis.test.tsx
 * Description : RFM 客户价值分析页面的配置加载 / 新建配置 / 计算 / 明细测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import RfmAnalysis from './RfmAnalysis';
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

/** 配置行 */
function config(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    configName: `配置${id}`,
    configCode: `RFM-${id}`,
    analyzePeriodDays: 90,
    recencyWeight: 1,
    frequencyWeight: 1,
    monetaryWeight: 1,
    status: 'ACTIVE',
    ...overrides,
  } as never;
}

function renderPage() {
  return renderWithProviders(<RfmAnalysis />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('RfmAnalysis', () => {
  it('加载 RFM 配置列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/configs/list')) return Promise.resolve(page([config('1'), config('2')]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('配置1')).toBeInTheDocument());
    expect(screen.getByText('配置2')).toBeInTheDocument();
  });

  it('新建配置: 打开弹窗填写后 POST', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/configs/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(config('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('新建配置') }));
    await waitFor(() => expect(screen.getByLabelText('配置名称')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('配置名称'), '季度 RFM');
    await userEvent.type(screen.getByLabelText('配置编码'), 'Q1_RFM');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/rfm/configs', expect.objectContaining({ configName: '季度 RFM', configCode: 'Q1_RFM' })));
  });

  it('触发计算: 调用 calculate', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/configs/list')) return Promise.resolve(page([config('1')]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(config('1'));

    renderPage();
    await waitFor(() => expect(screen.getByText('配置1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('计算') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/rfm/calculate/1', expect.anything()));
  });

  it('查看客户明细: 打开抽屉加载分群与客户', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/configs/list')) return Promise.resolve(page([config('1')]));
      if (url.includes('/segments')) {
        return Promise.resolve([{ id: 's1', configId: '1', segmentCode: 'CHAMPIONS', segmentName: '忠诚客户', customerCount: 50 }]);
      }
      if (url.includes('/customers/list')) {
        return Promise.resolve(page([{ id: 'a1', configId: '1', customerId: '1', customerName: '张三', rfmScore: '555', segment: 'CHAMPIONS', segmentName: '忠诚客户' }]));
      }
      return Promise.resolve(page([]));
    });

    renderPage();
    await waitFor(() => expect(screen.getByText('配置1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('明细') }));

    await waitFor(() => expect(screen.getByText('忠诚客户')).toBeInTheDocument());
    expect(screen.getByText('张三')).toBeInTheDocument();
  });
});