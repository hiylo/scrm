/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : AccountantWorkbench.test.tsx
 * Description : 会计工作台的角色分流测试（会计不展示分派总览，也不请求他人工作量）
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import AccountantWorkbench from './AccountantWorkbench';
import { apiClient } from '../api/client';
import { renderWithProviders } from '../test/render';

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

/** 我的客户行 */
function company(id: string, name: string, accountantName = '张会计') {
  return {
    id,
    companyName: name,
    accountantId: '5001',
    accountantName,
    companyType: 'ENTERPRISE',
    serviceStatus: 'PENDING',
    businessStatus: 'ACTIVE',
    verified: true,
    createTime: '2026-10-24T21:24:23',
  } as never;
}

/** 工作量行 */
function workload(accountantId: string | null, name: string, count: number) {
  return { accountantId, accountantName: name, companyCount: count } as never;
}

/**
 * 按 URL 分发 mock。
 * <p>未命中的接口返回空列表，避免用例间互相影响。</p>
 */
function routeMock(handlers: Record<string, () => unknown>) {
  get.mockImplementation((url: string) => {
    for (const [fragment, fn] of Object.entries(handlers)) {
      if (url.includes(fragment)) return Promise.resolve(fn() as never);
    }
    return Promise.resolve([] as never);
  });
}

/** 设置当前登录角色（localStorage 是前端唯一的角色来源） */
function setRole(role: string, roles: string[]) {
  localStorage.setItem('userRole', role);
  localStorage.setItem('userRoles', JSON.stringify(roles));
}

describe('AccountantWorkbench 会计工作台', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it('管理角色展示「分派总览」并拉取全员工作量', async () => {
    setRole('manager', ['MANAGER']);
    routeMock({
      '/scrm/companies/mine': () => [company('1', '鼎昊环境工程有限公司')],
      '/scrm/companies/accountants/workload': () => [
        workload('5001', '张会计', 3),
        workload('5002', '李会计', 2),
        workload(null, '未指派', 1),
      ],
    });

    renderWithProviders(<AccountantWorkbench />);

    await waitFor(() => expect(screen.getByText('分派总览')).toBeInTheDocument());
    // 该接口不带参数，断言只给 URL
    expect(get).toHaveBeenCalledWith('/scrm/companies/accountants/workload');
  });

  it('会计角色不展示「分派总览」，且不请求他人工作量（避免越权调用）', async () => {
    setRole('accountant', ['ACCOUNTANT']);
    routeMock({
      '/scrm/companies/mine': () => [company('1', '鼎昊环境工程有限公司', '李会计')],
    });

    renderWithProviders(<AccountantWorkbench />);

    await waitFor(() => expect(screen.getByText('鼎昊环境工程有限公司')).toBeInTheDocument());

    // 主管视角的区块不应出现
    expect(screen.queryByText('分派总览')).not.toBeInTheDocument();
    // 也不应发起该接口请求
    const workloadCalls = get.mock.calls.filter(args =>
      String(args[0]).includes('/accountants/workload'),
    );
    expect(workloadCalls).toHaveLength(0);
  });
});
