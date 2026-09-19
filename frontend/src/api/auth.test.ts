/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : auth.test.ts
 * Description : 登录 / 登出 / 令牌刷新 / 角色与权限推导测试
 */

import { describe, it, expect, vi, afterEach } from 'vitest';
import axios, { type AxiosResponse } from 'axios';
import {
  login,
  logout,
  refreshToken,
  saveAuthData,
  isTokenExpired,
  isAuthenticated,
  getToken,
  getUserId,
  getUsername,
  getDisplayName,
  getUserRoles,
  getUserPermissions,
  getRole,
  getDepartmentId,
  isAdmin,
  hasPermission,
  type LoginResponse,
} from './auth';

import { makeJwt } from '../test/fixtures';

/** 秒级时间戳 (JWT exp 语义) */
const nowSec = (): number => Math.floor(Date.now() / 1000);

/** 包装后端 OperationResponse 为 axios 响应 */
function httpBody(data: unknown): AxiosResponse {
  return {
    data,
    status: 200,
    statusText: 'OK',
    headers: {},
    config: { headers: {} as never } as never,
  };
}

/** 拦截全局 axios.post (auth.ts 使用默认 axios 实例) */
function mockPost() {
  return vi.spyOn(axios, 'post');
}

/** 模拟浏览器地址 */
function stubLocation() {
  const location = { pathname: '/dashboard', href: 'http://localhost/dashboard' };
  vi.stubGlobal('location', location);
  return location;
}

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe('login', () => {
  it('请求登录端点并组装 { username, password } 载荷', async () => {
    const post = mockPost();
    post.mockResolvedValue(
      httpBody({
        status: 'SUCCESS',
        data: { accessToken: 'access-token', userId: 42, displayName: '张三' },
      }),
    );

    await login('zhangsan', 'secret');

    expect(post).toHaveBeenCalledTimes(1);
    expect(post.mock.calls[0][0]).toBe('/scrm/auth/login');
    expect(post.mock.calls[0][1]).toEqual({ username: 'zhangsan', password: 'secret' });
    expect(post.mock.calls[0][2]).toEqual({ headers: { 'Content-Type': 'application/json' } });
  });

  it('解包 OperationResponse 并归一化字段', async () => {
    const post = mockPost();
    post.mockResolvedValue(
      httpBody({
        status: 'SUCCESS',
        data: {
          token: 'plain-token',
          refreshToken: 'refresh-1',
          userId: 7,
          displayName: '李四',
          email: 'li@example.com',
          roles: ['SALES'],
          permissions: ['scrm_customer:read'],
          departmentId: 'dept-9',
        },
      }),
    );

    const result = await login('lisi', 'pw');

    expect(result).toEqual({
      token: 'plain-token',
      refreshToken: 'refresh-1',
      username: 'lisi',
      // 后端下发数字 userId, 前端统一转为字符串存储
      userId: '7',
      displayName: '李四',
      email: 'li@example.com',
      roles: ['SALES'],
      permissions: ['scrm_customer:read'],
      role: 'sales',
      departmentId: 'dept-9',
    });
  });

  it('优先使用 accessToken, 并从 JWT authorities 提取角色 (去掉 ROLE_ 前缀)', async () => {
    const token = makeJwt({ authorities: ['ROLE_ADMIN', 'ROLE_SALES'], departmentId: 'dept-jwt' });
    const post = mockPost();
    post.mockResolvedValue(httpBody({ status: 'SUCCESS', data: { accessToken: token } }));

    const result = await login('admin', 'pw');

    expect(result.token).toBe(token);
    // 角色统一归一为规范形式: 去 ROLE_ 前缀 + 大写
    expect(result.roles).toEqual(['ADMIN', 'SALES']);
    // 主角色取优先级最高者
    expect(result.role).toBe('admin');
    // 响应未携带 departmentId 时回退到 JWT
    expect(result.departmentId).toBe('dept-jwt');
    // 缺省字段有兜底值
    expect(result.userId).toBe('');
    expect(result.refreshToken).toBe('');
    expect(result.email).toBe('');
    expect(result.permissions).toEqual([]);
  });

  it('JWT 与响应均无角色时主角色回退为 viewer', async () => {
    const post = mockPost();
    post.mockResolvedValue(httpBody({ status: 'SUCCESS', data: { accessToken: 'not-a-jwt' } }));

    const result = await login('guest', 'pw');

    expect(result.roles).toEqual([]);
    expect(result.role).toBe('viewer');
    expect(result.displayName).toBe('guest');
    expect(result.departmentId).toBe('');
  });

  it('JWT 同时给小写 role 与大写 ROLE_ authority 时归一为规范形式且只保留一个角色', async () => {
    const token = makeJwt({ role: 'sales', authorities: ['ROLE_SALES', 'scrm_custom', 'scrm_custom'] });
    const post = mockPost();
    post.mockResolvedValue(httpBody({ status: 'SUCCESS', data: { accessToken: token } }));

    const result = await login('u', 'p');

    // 规范形式 = 去 ROLE_ 前缀 + 大写, 因此 role='sales' 与 ROLE_SALES 合并为一项, 重复 authority 同样被去掉
    expect(result.roles).toEqual(['SALES', 'SCRM_CUSTOM']);
    expect(result.role).toBe('sales');

    // 写入 localStorage 后再读出, 仍然是这个规范形式
    saveAuthData(result);
    expect(getUserRoles()).toEqual(['SALES', 'SCRM_CUSTOM']);
    expect(isAdmin()).toBe(false);
  });

  it('后端真实令牌形态: 角色在 roles 声明 (大写数组), 不再依赖 role / authorities', async () => {
    // JwtTokenProvider 只签发 uid / roles / username 三个自定义声明, roles 为大写角色名数组。
    // 只读 role / authorities 的旧实现对本令牌恒解析为空。
    const token = makeJwt({ uid: 7, roles: ['ADMIN', 'OPERATOR'] });
    const post = mockPost();
    post.mockResolvedValue(httpBody({ status: 'SUCCESS', data: { accessToken: token } }));

    const result = await login('u', 'p');

    expect(result.roles).toEqual(['ADMIN', 'OPERATOR']);
    expect(result.role).toBe('admin');
    saveAuthData(result);
    expect(isAdmin()).toBe(true);
  });

  it('roles 声明为逗号分隔字符串时同样拆分归一, 空项被丢弃', async () => {
    const token = makeJwt({ roles: 'ROLE_manager, ,sales' });
    const post = mockPost();
    post.mockResolvedValue(httpBody({ status: 'SUCCESS', data: { accessToken: token } }));

    const result = await login('u', 'p');

    expect(result.roles).toEqual(['MANAGER', 'SALES']);
    expect(result.role).toBe('manager');
  });

  it('role 与 authorities 指向同一管理员时不产生重复项', async () => {
    const token = makeJwt({ role: 'admin', authorities: ['ROLE_ADMIN'] });
    const post = mockPost();
    post.mockResolvedValue(httpBody({ status: 'SUCCESS', data: { accessToken: token } }));

    const result = await login('u', 'p');

    expect(result.roles).toEqual(['ADMIN']);
    expect(result.role).toBe('admin');
    saveAuthData(result);
    expect(isAdmin()).toBe(true);
  });

  it('响应 roles 独立下发时也归一为规范形式 (大小写 / ROLE_ 前缀混用)', async () => {
    const post = mockPost();
    post.mockResolvedValue(
      httpBody({ status: 'SUCCESS', data: { token: 'not-a-jwt', roles: ['manager', 'ROLE_MANAGER'] } }),
    );

    const result = await login('u', 'p');

    expect(result.roles).toEqual(['MANAGER']);
    expect(result.role).toBe('manager');
  });

  it('数字 userId 统一转字符串, 0 不被误判为空值', async () => {
    const post = mockPost();
    post.mockResolvedValue(
      httpBody({ status: 'SUCCESS', data: { accessToken: 't', userId: 123 } }),
    );

    const result = await login('u', 'p');
    expect(result.userId).toBe('123');
    saveAuthData(result);
    expect(getUserId()).toBe('123');

    post.mockResolvedValue(httpBody({ status: 'SUCCESS', data: { accessToken: 't', userId: 0 } }));
    const zero = await login('u', 'p');
    expect(zero.userId).toBe('0');
    saveAuthData(zero);
    expect(getUserId()).toBe('0');

    // 后端未下发 userId 时仍是空串
    post.mockResolvedValue(httpBody({ status: 'SUCCESS', data: { accessToken: 't' } }));
    expect((await login('u', 'p')).userId).toBe('');
  });

  it('status=FAILURE 时抛出后端 message', async () => {
    const post = mockPost();
    post.mockResolvedValue(httpBody({ status: 'FAILURE', message: '用户名或密码错误' }));

    await expect(login('u', 'p')).rejects.toThrow('用户名或密码错误');
  });

  it('status=SUCCESS 但缺少 data 时抛出默认文案', async () => {
    const post = mockPost();
    post.mockResolvedValue(httpBody({ status: 'SUCCESS', data: null }));

    await expect(login('u', 'p')).rejects.toThrow('登录失败');
  });

  it('网络异常时向上抛出, 由调用方处理', async () => {
    const post = mockPost();
    post.mockRejectedValue(new Error('Network Error'));

    await expect(login('u', 'p')).rejects.toThrow('Network Error');
  });
});

describe('saveAuthData / logout', () => {
  it('saveAuthData 写入全部会话键', () => {
    saveAuthData({
      token: 'tk',
      refreshToken: 'rt',
      username: 'wangwu',
      userId: 'u-3',
      displayName: '王五',
      email: 'w@example.com',
      roles: ['manager'],
      permissions: ['scrm_customer:write'],
      role: 'manager',
      departmentId: 'dept-1',
    } satisfies LoginResponse);

    expect(localStorage.getItem('scrm_token')).toBe('tk');
    expect(localStorage.getItem('refreshToken')).toBe('rt');
    expect(localStorage.getItem('username')).toBe('wangwu');
    expect(localStorage.getItem('displayName')).toBe('王五');
    expect(localStorage.getItem('userId')).toBe('u-3');
    expect(localStorage.getItem('userRoles')).toBe(JSON.stringify(['manager']));
    expect(localStorage.getItem('userPermissions')).toBe(
      JSON.stringify(['scrm_customer:write']),
    );
    expect(localStorage.getItem('userRole')).toBe('manager');
    expect(localStorage.getItem('departmentId')).toBe('dept-1');
  });

  it('可选字段为空时不写入, displayName 缺失时回退 username', () => {
    saveAuthData({
      token: 'tk2',
      username: 'solo',
      userId: 'u-1',
      displayName: '',
      email: '',
    } satisfies LoginResponse);

    expect(localStorage.getItem('scrm_token')).toBe('tk2');
    expect(localStorage.getItem('displayName')).toBe('solo');
    expect(localStorage.getItem('refreshToken')).toBeNull();
    expect(localStorage.getItem('userRoles')).toBeNull();
    expect(localStorage.getItem('departmentId')).toBeNull();
  });

  it('logout 清除所有会话键', () => {
    saveAuthData({
      token: 'tk',
      refreshToken: 'rt',
      username: 'u',
      userId: '1',
      displayName: 'U',
      email: 'e',
      roles: ['admin'],
      permissions: ['*'],
      role: 'admin',
      departmentId: 'd',
    } satisfies LoginResponse);

    logout();

    [
      'scrm_token',
      'refreshToken',
      'username',
      'displayName',
      'userId',
      'userRoles',
      'userPermissions',
      'userRole',
      'departmentId',
    ].forEach((key) => expect(localStorage.getItem(key)).toBeNull());
  });
});

describe('refreshToken', () => {
  it('本地无 refreshToken 时直接返回 null 且不发请求', async () => {
    const post = mockPost();

    await expect(refreshToken()).resolves.toBeNull();
    expect(post).not.toHaveBeenCalled();
  });

  it('刷新成功时写入新 access token 与轮换后的 refresh token', async () => {
    localStorage.setItem('refreshToken', 'old-rt');
    localStorage.setItem('scrm_token', 'old-at');
    const post = mockPost();
    post.mockResolvedValue(
      httpBody({
        status: 'SUCCESS',
        data: { accessToken: 'new-at', refreshToken: 'new-rt' },
      }),
    );

    await expect(refreshToken()).resolves.toBe('new-at');

    expect(post).toHaveBeenCalledTimes(1);
    expect(post.mock.calls[0][0]).toBe('/scrm/auth/refresh');
    expect(post.mock.calls[0][1]).toEqual({ refreshToken: 'old-rt' });
    expect(localStorage.getItem('scrm_token')).toBe('new-at');
    expect(localStorage.getItem('refreshToken')).toBe('new-rt');
  });

  it('响应缺少令牌时返回 null 且不清理会话', async () => {
    localStorage.setItem('refreshToken', 'old-rt');
    const post = mockPost();
    post.mockResolvedValue(httpBody({ status: 'SUCCESS', data: {} }));

    await expect(refreshToken()).resolves.toBeNull();
    expect(localStorage.getItem('refreshToken')).toBe('old-rt');
  });

  it('后端返回 FAILURE 时登出并跳转登录页', async () => {
    localStorage.setItem('scrm_token', 'at');
    localStorage.setItem('refreshToken', 'old-rt');
    localStorage.setItem('userId', 'u1');
    const location = stubLocation();
    const post = mockPost();
    post.mockResolvedValue(httpBody({ status: 'FAILURE', message: 'refresh token 已失效' }));

    await expect(refreshToken()).resolves.toBeNull();

    expect(localStorage.getItem('scrm_token')).toBeNull();
    expect(localStorage.getItem('refreshToken')).toBeNull();
    expect(localStorage.getItem('userId')).toBeNull();
    expect(location.href).toBe('/login');
  });

  it('请求异常时登出并跳转登录页', async () => {
    localStorage.setItem('scrm_token', 'at');
    localStorage.setItem('refreshToken', 'old-rt');
    const location = stubLocation();
    const post = mockPost();
    post.mockRejectedValue(new Error('Network Error'));

    await expect(refreshToken()).resolves.toBeNull();

    expect(localStorage.getItem('scrm_token')).toBeNull();
    expect(location.href).toBe('/login');
  });
});

describe('isTokenExpired / isAuthenticated', () => {
  it('未过期的 JWT 视为有效', () => {
    expect(isTokenExpired(makeJwt({ exp: nowSec() + 3600 }))).toBe(false);
  });

  it('已过期的 JWT 视为失效', () => {
    expect(isTokenExpired(makeJwt({ exp: nowSec() - 10 }))).toBe(true);
  });

  it('缺少 exp 或无法解析的令牌视为失效', () => {
    expect(isTokenExpired(makeJwt({ sub: 'no-exp' }))).toBe(true);
    expect(isTokenExpired('garbage')).toBe(true);
    expect(isTokenExpired('a.b')).toBe(true);
    expect(isTokenExpired('')).toBe(true);
  });

  it('isAuthenticated 综合判断本地令牌', () => {
    expect(isAuthenticated()).toBe(false);

    localStorage.setItem('scrm_token', makeJwt({ exp: nowSec() + 600 }));
    expect(isAuthenticated()).toBe(true);

    localStorage.setItem('scrm_token', makeJwt({ exp: nowSec() - 600 }));
    expect(isAuthenticated()).toBe(false);
  });
});

describe('会话读取器', () => {
  it('从 localStorage 读取令牌与用户信息', () => {
    localStorage.setItem('scrm_token', 'tk');
    localStorage.setItem('userId', 'u-7');
    localStorage.setItem('username', 'zhangsan');
    localStorage.setItem('displayName', '张三');
    localStorage.setItem('departmentId', 'dept-3');

    expect(getToken()).toBe('tk');
    expect(getUserId()).toBe('u-7');
    expect(getUsername()).toBe('zhangsan');
    expect(getDisplayName()).toBe('张三');
    expect(getDepartmentId()).toBe('dept-3');
  });

  it('角色/权限列表缺失或损坏时返回空数组', () => {
    expect(getUserRoles()).toEqual([]);
    expect(getUserPermissions()).toEqual([]);

    localStorage.setItem('userRoles', '["admin","sales"]');
    localStorage.setItem('userPermissions', '[invalid json');

    expect(getUserRoles()).toEqual(['admin', 'sales']);
    expect(getUserPermissions()).toEqual([]);
  });

  it('getRole 优先读取 userRole, 缺失时从角色列表推导', () => {
    expect(getRole()).toBe('viewer');

    localStorage.setItem('userRoles', JSON.stringify(['ROLE_MANAGER', 'ROLE_SALES']));
    expect(getRole()).toBe('manager');

    localStorage.setItem('userRole', 'sales');
    expect(getRole()).toBe('sales');
  });

  it('isAdmin 兼容 ROLE_ 前缀', () => {
    expect(isAdmin()).toBe(false);

    localStorage.setItem('userRoles', JSON.stringify(['sales', 'ROLE_ADMIN']));
    expect(isAdmin()).toBe(true);

    localStorage.setItem('userRoles', JSON.stringify(['ADMIN']));
    expect(isAdmin()).toBe(true);

    localStorage.setItem('userRoles', JSON.stringify(['manager']));
    expect(isAdmin()).toBe(false);
  });
});

describe('hasPermission', () => {
  it('admin / manager 拥有全部权限', () => {
    localStorage.setItem('userRole', 'admin');
    expect(hasPermission('scrm_customer:write')).toBe(true);
    expect(hasPermission('anything:at:all')).toBe(true);

    localStorage.setItem('userRole', 'manager');
    expect(hasPermission('scrm_risk_rule:delete')).toBe(true);
  });

  it('sales 仅拥有客户 / 会话 / 营销 / 模板前缀权限', () => {
    localStorage.setItem('userRole', 'sales');

    expect(hasPermission('scrm_customer:write')).toBe(true);
    expect(hasPermission('scrm_conversation:read')).toBe(true);
    expect(hasPermission('scrm_campaign:write')).toBe(true);
    expect(hasPermission('scrm_message_template:read')).toBe(true);
    expect(hasPermission('scrm_audit_log:read')).toBe(false);
    expect(hasPermission('scrm_risk_rule:delete')).toBe(false);
  });

  it('viewer 仅拥有 read 权限', () => {
    localStorage.setItem('userRole', 'viewer');

    expect(hasPermission('scrm_customer:read')).toBe(true);
    expect(hasPermission('scrm_customer:write')).toBe(false);
  });

  it('未知角色回退到显式权限列表, 支持通配 *:*', () => {
    localStorage.setItem('userRole', 'auditor');
    localStorage.setItem('userPermissions', JSON.stringify(['scrm_audit_log:read']));

    expect(hasPermission('scrm_audit_log:read')).toBe(true);
    expect(hasPermission('scrm_customer:write')).toBe(false);

    localStorage.setItem('userPermissions', JSON.stringify(['*:*']));
    expect(hasPermission('scrm_customer:write')).toBe(true);
  });
});
