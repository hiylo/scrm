/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Users.test.tsx
 * Description : 用户管理页面的加载 / 列表 / 创建 / 启用禁用 / 重置密码测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Users from './Users';
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
const post = vi.mocked(apiClient.post);
const put = vi.mocked(apiClient.put);

/** 构造用户分页响应 */
function userPage(...items: object[]) {
  return {
    content: items,
    totalElements: items.length,
    totalPages: 1,
    number: 0,
    size: 10,
  };
}

const OPERATOR = {
  id: '1001',
  username: 'zhangsan',
  displayName: '张三',
  email: 'zhangsan@example.com',
  roles: 'OPERATOR',
  status: 1,
  createTime: '2026-09-01T10:00:00',
};
const ADMIN = {
  id: '1000',
  username: 'admin',
  displayName: '系统管理员',
  roles: 'ADMIN',
  status: 1,
  createTime: '2026-08-01T10:00:00',
};

beforeEach(() => {
  vi.clearAllMocks();
});

describe('Users 页面', () => {
  it('加载并渲染用户列表', async () => {
    get.mockResolvedValueOnce(userPage(ADMIN, OPERATOR));
    renderWithProviders(<Users />);

    await waitFor(() => expect(screen.getByText('zhangsan')).toBeTruthy());
    expect(screen.getByText('admin')).toBeTruthy();
    expect(screen.getByText('张三')).toBeTruthy();
    expect(screen.getAllByText('下级用户').length).toBeGreaterThan(0);
    expect(screen.getAllByText('系统管理员').length).toBeGreaterThan(0);
    expect(get).toHaveBeenCalledWith('/scrm/users/list', expect.objectContaining({ params: expect.anything() }));
  });

  it('列表为空时展示空态', async () => {
    get.mockResolvedValueOnce(userPage());
    renderWithProviders(<Users />);

    await waitFor(() => expect(screen.getAllByText('暂无数据').length).toBeGreaterThan(0));
  });

  it('点击创建用户提交创建请求', async () => {
    get.mockResolvedValueOnce(userPage());
    post.mockResolvedValueOnce(OPERATOR);

    renderWithProviders(<Users />);
    await waitFor(() => expect(screen.getByText('创建用户')).toBeTruthy());
    await userEvent.click(screen.getByText('创建用户'));

    // 填写表单
    await userEvent.type(screen.getByLabelText('用户名'), 'lisi');
    await userEvent.type(screen.getByLabelText('初始密码'), 'Passw0rd123');
    await userEvent.type(screen.getByLabelText('昵称'), '李四');
    await userEvent.click(screen.getByRole('button', { name: /确\s*定/ }));

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        '/scrm/users',
        expect.objectContaining({ username: 'lisi', password: 'Passw0rd123', displayName: '李四' }),
      ),
    );
  });

  it('点击禁用触发状态更新请求', async () => {
    get.mockResolvedValueOnce(userPage(OPERATOR));
    // 禁用后刷新会再次拉取
    get.mockResolvedValue(userPage({ ...OPERATOR, status: 0 }));
    put.mockResolvedValueOnce({ ...OPERATOR, status: 0 });

    renderWithProviders(<Users />);
    await waitFor(() => expect(screen.getByText('zhangsan')).toBeTruthy());
    // 点击禁用按钮弹出二次确认
    await userEvent.click(screen.getByRole('button', { name: /禁\s*用/ }));
    // 点击确认
    await userEvent.click(await screen.findByRole('button', { name: /确\s*定/ }));

    await waitFor(() =>
      expect(put).toHaveBeenCalledWith('/scrm/users/1001/status', null, expect.objectContaining({ params: { status: 0 } })),
    );
  });

  it('重置密码提交请求', async () => {
    get.mockResolvedValueOnce(userPage(OPERATOR));
    post.mockResolvedValueOnce(undefined);

    renderWithProviders(<Users />);
    await waitFor(() => expect(screen.getByText('zhangsan')).toBeTruthy());
    await userEvent.click(screen.getByRole('button', { name: /重\s*置密码/ }));
    await userEvent.type(screen.getByLabelText('新密码'), 'NewPass123');
    await userEvent.click(screen.getByRole('button', { name: /确\s*定/ }));

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('/scrm/users/1001/reset-password', {
        newPassword: 'NewPass123',
      }),
    );
  });
});