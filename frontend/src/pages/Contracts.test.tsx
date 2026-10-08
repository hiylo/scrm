/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Contracts.test.tsx
 * Description : 合同管理页面的加载 / 新建 / 状态流转 / 回款抽屉测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Contracts from './Contracts';
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

/** 合同行 */
function contract(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    contractNo: `HT-2026${id}`,
    contractName: `合同${id}`,
    contractType: 'SALES',
    customerId: '1',
    customerName: '张三',
    contractAmount: 10000,
    currency: 'CNY',
    startDate: '2026-01-01',
    endDate: '2026-12-31',
    status: 'DRAFT',
    totalPaid: 0,
    totalDue: 10000,
    ...overrides,
  } as never;
}

function renderPage() {
  return renderWithProviders(<Contracts />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('Contracts', () => {
  it('加载合同列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/contracts/list')) return Promise.resolve(page([contract('1'), contract('2')]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('合同1')).toBeInTheDocument());
    expect(screen.getByText('合同2')).toBeInTheDocument();
  });

  it('新建合同: 打开弹窗填写后 POST', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/contracts/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(contract('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('新建合同') }));
    await waitFor(() => expect(screen.getByLabelText('合同名称')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('合同名称'), '年度框架');
    await userEvent.type(screen.getByLabelText('客户 ID'), '1');
    await userEvent.type(screen.getByLabelText('合同金额 (¥)'), '10000');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/contracts', expect.objectContaining({ contractName: '年度框架', contractAmount: 10000 })));
  });

  it('提交审批: 草稿调用 submit', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/contracts/list')) return Promise.resolve(page([contract('1')]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(contract('1', { status: 'PENDING_REVIEW' }));

    renderPage();
    await waitFor(() => expect(screen.getByText('合同1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('提交审批') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/contracts/1/submit', undefined));
  });

  it('查看回款记录: 打开抽屉加载 payments', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/contracts/list')) return Promise.resolve(page([contract('1', { status: 'ACTIVE' })]));
      if (url.includes('/contract-payments/contract/')) {
        return Promise.resolve([{ id: 'p1', contractId: '1', paymentNo: 'HK001', amount: 5000, paymentDate: '2026-03-01', paymentMethod: 'BANK' }]);
      }
      return Promise.resolve(page([]));
    });

    renderPage();
    await waitFor(() => expect(screen.getByText('合同1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('回款') }));

    await waitFor(() => expect(screen.getByText('HK001')).toBeInTheDocument());
    expect(screen.getByText('¥5,000')).toBeInTheDocument();
  });
});