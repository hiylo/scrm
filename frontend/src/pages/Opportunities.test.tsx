/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Opportunities.test.tsx
 * Description : 商机管理页面的加载 / 创建 / 阶段推进 / 预测测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Opportunities from './Opportunities';
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

/** 商机行 */
function opp(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    opportunityName: `商机${id}`,
    customerId: '1',
    customerName: '张三',
    funnelId: 'f1',
    currentStageName: '初步接洽',
    amount: 50000,
    probability: 60,
    status: 'OPEN',
    ownerUserId: 'u1',
    expectedCloseDate: '2026-12-31T00:00:00',
    createTime: '2026-10-08T10:00:00',
    ...overrides,
  } as never;
}

/** 阶段行 */
function stage(id: string, name: string) {
  return { id, funnelId: 'f1', stageName: name, stageOrder: 1 } as never;
}

function renderPage() {
  return renderWithProviders(<Opportunities />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('Opportunities', () => {
  it('加载并展示商机列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/opportunities?')) return Promise.resolve(page([opp('1'), opp('2', { status: 'WON' })]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('商机1')).toBeInTheDocument());
    expect(screen.getByText('商机2')).toBeInTheDocument();
    expect(screen.getAllByText('¥50,000').length).toBeGreaterThan(0);
  });

  it('创建商机: 打开弹窗填写后 POST', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/opportunities?')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(opp('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('新建商机') }));
    await waitFor(() => expect(screen.getByLabelText('商机名称')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('商机名称'), '年度大单');
    await userEvent.type(screen.getByLabelText('客户 ID'), '1');
    await userEvent.type(screen.getByLabelText('漏斗 ID'), 'f1');
    await userEvent.type(screen.getByLabelText('负责人用户 ID'), 'u1');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/opportunities', expect.objectContaining({ opportunityName: '年度大单' })));
  });

  it('阶段推进: 选择目标阶段后 change-stage', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/opportunities?')) return Promise.resolve(page([opp('1')]));
      if (url.includes('/stages')) return Promise.resolve([stage('s1', '方案报价'), stage('s2', '商务谈判')]);
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(opp('1', { currentStageName: '商务谈判' }));

    renderPage();
    await waitFor(() => expect(screen.getByText('商机1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('推进') }));
    await waitFor(() => expect(screen.getByText('目标阶段')).toBeInTheDocument());
    // 阶段 Select: 下拉选项 portal 渲染, 需 mouseDown 打开
    fireEvent.mouseDown(screen.getByLabelText('目标阶段'));
    await waitFor(() => expect(screen.getAllByText('商务谈判').length).toBeGreaterThan(0));
    fireEvent.click(screen.getAllByText('商务谈判').pop()!);
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/opportunities/1/change-stage', expect.objectContaining({ toStageId: 's2' })));
  });

  it('打开销售预测: 加载 summary 与明细', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/opportunities?')) return Promise.resolve(page([opp('1')]));
      if (url.includes('/forecast')) {
        return Promise.resolve({ summary: { 加权总额: 50000, 商机数: 1 }, content: [opp('1', { weightedAmount: 30000 })], totalElements: 1 });
      }
      return Promise.resolve(page([]));
    });

    renderPage();
    await waitFor(() => expect(screen.getByText('商机1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('销售预测') }));

    await waitFor(() => expect(screen.getByText('加权总额')).toBeInTheDocument());
    expect(screen.getByText('50,000')).toBeInTheDocument();
  });
});