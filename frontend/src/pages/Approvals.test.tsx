/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Approvals.test.tsx
 * Description : 审批中心页面的审批流加载 / 新建 / 实例审批 / 日志测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Approvals from './Approvals';
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

/** 审批流行 */
function flow(id: string, overrides: Record<string, unknown> = {}) {
  return { id, flowName: `流程${id}`, flowCode: `F-${id}`, flowType: 'SINGLE', applicableModule: 'CONTRACT', status: 'ACTIVE', ...overrides } as never;
}

/** 实例行 */
function instance(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    instanceNo: `SP-2026${id}`,
    flowId: '1',
    flowName: '流程1',
    businessType: 'CONTRACT',
    title: `审批${id}`,
    status: 'PENDING',
    applicantName: '小张',
    submittedAt: '2026-10-08T10:00:00',
    ...overrides,
  } as never;
}

function renderPage() {
  return renderWithProviders(<Approvals />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('Approvals', () => {
  it('加载审批流列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/flows/list')) return Promise.resolve(page([flow('1'), flow('2')]));
      if (url.includes('/approvals/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('流程1')).toBeInTheDocument());
    expect(screen.getByText('流程2')).toBeInTheDocument();
  });

  it('新建审批流: 打开弹窗填写后 POST', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/flows/list')) return Promise.resolve(page([]));
      if (url.includes('/approvals/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(flow('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('新建审批流') }));
    await waitFor(() => expect(screen.getByLabelText('流程名称')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('流程名称'), '合同审批');
    await userEvent.type(screen.getByLabelText('流程编码'), 'CONTRACT_APPR');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/approvals/flows', expect.objectContaining({ flowName: '合同审批', flowCode: 'CONTRACT_APPR' })));
  });

  it('切换到实例 Tab, 审批通过', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/flows/list')) return Promise.resolve(page([]));
      if (url.includes('/approvals/list')) return Promise.resolve(page([instance('1')]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(instance('1', { status: 'APPROVED' }));

    renderPage();
    fireEvent.click(screen.getByRole('tab', { name: '审批实例' }));
    await waitFor(() => expect(screen.getByText('审批1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('通过') }));
    await waitFor(() => expect(screen.getByPlaceholderText('审批意见 (可选)')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/approvals/actions/approve', expect.objectContaining({ instanceId: 1 })));
  });

  it('查看审批日志: 打开抽屉加载 timeline', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/flows/list')) return Promise.resolve(page([]));
      if (url.includes('/approvals/list')) return Promise.resolve(page([instance('1')]));
      if (url.includes('/logs/timeline/')) {
        return Promise.resolve([{ id: 'l1', instanceId: '1', nodeName: '财务审批', action: 'APPROVE', operatorName: '李总', comment: '同意', createdAt: '2026-10-08T10:00:00' }]);
      }
      return Promise.resolve(page([]));
    });

    renderPage();
    fireEvent.click(screen.getByRole('tab', { name: '审批实例' }));
    await waitFor(() => expect(screen.getByText('审批1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('日志') }));

    await waitFor(() => expect(screen.getByText('财务审批')).toBeInTheDocument());
    expect(screen.getByText('同意')).toBeInTheDocument();
  });
});