/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
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
    enabled: true,
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
      // 真实路径 /scrm/rfm/analysis/list，字段对齐 ScrmRfmAnalysisDto
      if (url.includes('/analysis/list')) {
        return Promise.resolve(page([
          { id: 'a1', configId: '1', customerId: '1', customerName: '张三', rScore: 5, fScore: 5, mScore: 5, rfmSegment: '555', segmentName: '忠诚客户', calculatedAt: '2026-10-08T10:00:00' },
        ]));
      }
      return Promise.resolve(page([]));
    });

    renderPage();
    await waitFor(() => expect(screen.getByText('配置1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('明细') }));

    await waitFor(() => expect(screen.getByText('张三')).toBeInTheDocument());

    // 契约断言：R/F/M/RFM 与计算时间必须来自后端真实字段
    // （recencyScore/calcTime 等错误命名会让这些格子渲染成 '-'）
    const row = screen.getByText('张三').closest('tr') as HTMLElement;
    const cells = [...row.querySelectorAll('td')].map((td) => td.textContent?.trim());
    expect(cells).toContain('5');            // rScore / fScore / mScore
    expect(cells).toContain('555');          // rfmSegment
    expect(cells).toContain('2026-10-08 10:00'); // calculatedAt
    expect(cells).not.toContain('-');
  });

  it('状态列按后端 enabled 渲染（配置实体没有 status 字段）', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/configs/list')) {
        return Promise.resolve(page([config('1', { enabled: true }), config('2', { enabled: false })]));
      }
      return Promise.resolve(page([]));
    });

    renderPage();
    await waitFor(() => expect(screen.getByText('配置1')).toBeInTheDocument());

    const row1 = screen.getByText('配置1').closest('tr') as HTMLElement;
    const row2 = screen.getByText('配置2').closest('tr') as HTMLElement;
    // 字段名写成 status 时这一列会渲染成 '-'
    expect(row1.textContent).toContain('已启用');
    expect(row2.textContent).toContain('未启用');
  });

  it('客户明细走 /rfm/analysis/list，不再请求不存在的 customers/list', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/configs/list')) return Promise.resolve(page([config('1')]));
      if (url.includes('/segments')) return Promise.resolve([] as never);
      if (url.includes('/analysis/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });

    renderPage();
    await waitFor(() => expect(screen.getByText('配置1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('明细') }));

    await waitFor(() =>
      expect(get).toHaveBeenCalledWith(expect.stringContaining('/scrm/rfm/analysis/list?')),
    );
    // /configs/{id}/customers/list 后端不存在，会落到 SPA 兜底返回 HTML
    expect(get).not.toHaveBeenCalledWith(expect.stringContaining('/customers/list'));
  });
});