/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
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

  it('查看回款记录: 按后端字段渲染金额/状态/回款时间', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/contracts/list')) return Promise.resolve(page([contract('1', { status: 'ACTIVE' })]));
      if (url.includes('/contract-payments/stats/contract/')) {
        return Promise.resolve({ totalPaidAmount: 3000, totalUnpaidAmount: 2000 } as never);
      }
      if (url.includes('/contract-payments/contract/')) {
        // 后端回款 DTO 的真实字段名：plannedAmount / actualDate / paymentStatus
        // 用错成 amount / paymentDate / status 时页面会渲染空白却不报错，故必须锁字段
        return Promise.resolve([
          {
            id: 'p1',
            contractId: '1',
            paymentNo: 'HK001',
            plannedAmount: 5000,
            paidAmount: 3000,
            actualDate: '2026-03-01',
            paymentMethod: 'BANK',
            paymentStatus: 'PARTIAL',
          },
        ]);
      }
      return Promise.resolve(page([]));
    });

    renderPage();
    await waitFor(() => expect(screen.getByText('合同1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('回款') }));

    await waitFor(() => expect(screen.getByText('HK001')).toBeInTheDocument());
    // 金额列读 plannedAmount，并带出已收金额
    expect(screen.getByText(/¥5,000（已收 ¥3,000）/)).toBeInTheDocument();
    // 状态列读 paymentStatus
    expect(screen.getByText('PARTIAL')).toBeInTheDocument();
    // 回款时间列读 actualDate
    expect(screen.getByText('2026-03-01')).toBeInTheDocument();
  });

  it('回款抽屉的统计取自 stats 接口，而不是合同对象', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/contracts/list')) return Promise.resolve(page([contract('1', { status: 'ACTIVE' })]));
      if (url.includes('/contract-payments/stats/contract/')) {
        return Promise.resolve({ totalPaidAmount: 4800, totalUnpaidAmount: 5200 } as never);
      }
      if (url.includes('/contract-payments/contract/')) return Promise.resolve([] as never);
      return Promise.resolve(page([]));
    });

    renderPage();
    await waitFor(() => expect(screen.getByText('合同1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('回款') }));

    // 合同列表接口不返回 totalPaid/totalDue，统计必须来自 stats 接口
    await waitFor(() =>
      expect(get).toHaveBeenCalledWith(expect.stringContaining('/scrm/contract-payments/stats/contract/1')),
    );
    // Statistic 会把前缀与数值拆成两个节点，直接读统计卡的整块文本
    const statTexts = () =>
      [...document.querySelectorAll('.ant-statistic')].map((s) => (s.textContent || '').replace(/\s/g, ''));
    await waitFor(() => expect(statTexts().some((t) => t.includes('¥4,800'))).toBe(true));
    expect(statTexts().some((t) => t.includes('¥5,200'))).toBe(true);
  });

  it('合同列表不再展示后端不存在的「已回款」列', async () => {
    get.mockImplementation((url: string) => {
      if (url.startsWith('/scrm/contracts/list')) return Promise.resolve(page([contract('1')]));
      return Promise.resolve(page([]));
    });

    renderPage();
    await waitFor(() => expect(screen.getByText('合同1')).toBeInTheDocument());
    const heads = screen.getAllByRole('columnheader').map((h) => h.textContent?.trim());
    expect(heads).not.toContain('已回款');
  });
});