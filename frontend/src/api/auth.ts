/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : auth.ts
 * Date : 2026/07/26
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

import axios from 'axios';
import { getApiBaseUrl } from './client';

/** API 基础地址 (与 API_BASE_URL 相同, 同源直连后端) */
const GATEWAY_BASE = getApiBaseUrl();

/** 登录端点 (单体后端统一前缀 /scrm/auth) */
const AUTH_LOGIN_URL = GATEWAY_BASE + (import.meta.env.VITE_AUTH_LOGIN_URL || '/scrm/auth/login');
/** 刷新令牌端点 (单体暂未提供, 保留前端兜底路径) */
const AUTH_REFRESH_URL = GATEWAY_BASE + (import.meta.env.VITE_AUTH_REFRESH_URL || '/scrm/auth/refresh');

/** 登录响应 */
export interface LoginResponse {
  token: string;
  refreshToken?: string;
  username: string;
  /** 用户 ID (后端下发数字, 统一转为字符串; 缺失时为空串) */
  userId: string;
  displayName: string;
  email: string;
  /** 角色列表 (规范形式: 去 ROLE_ 前缀 + 大写, 如 ADMIN / SALES) */
  roles?: string[];
  permissions?: string[];
  /** 主角色 (小写: admin/manager/sales/viewer) */
  role?: string;
  /** 部门 ID */
  departmentId?: string;
}

/** 用户信息 */
export interface User {
  id: string;
  username: string;
  displayName?: string;
  email?: string;
  enabled?: boolean;
}

/**
 * 解码 JWT payload
 * @param token JWT 令牌
 * @returns payload 对象, 解析失败返回 null
 */
function decodeJwtPayload(token: string): Record<string, unknown> | null {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;
    const b64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = b64 + '='.repeat((4 - (b64.length % 4)) % 4);
    const json = atob(padded);
    return JSON.parse(json);
  } catch {
    return null;
  }
}

/**
 * 角色规范形式
 * <p>
 * 后端 {@code JwtTokenProvider} 把角色放在 **`roles` 声明**里 (大写、无 `ROLE_` 前缀的字符串数组,
 * 由 {@code "ADMIN,OPERATOR"} 这样的 CSV 拆分而来)。历史实现只读 `role` / `authorities`
 * 两个从未被签发的声明, 导致从 JWT 解析角色恒为空, 角色完全依赖登录响应体。
 * 这里同时兼容三种写法 (旧令牌 / Spring Security 风格的 `ROLE_X` / 小写), 并统一归一为
 * **去 `ROLE_` 前缀 + 大写** (如 SALES / ADMIN): 两处消费方 normalizeRole 与 isAdmin 都按大写比较。
 * 归一一次性完成, 之后 localStorage 与 LoginResponse.roles 存的都是该形式,
 * 否则大小写敏感的 {@code new Set} 会把同一角色留成 ['sales','SALES'] 两项。
 *
 * @param role 原始角色字符串 (可带 ROLE_ 前缀, 大小写不限)
 * @returns 规范形式的角色名
 */
function canonicalRole(role: string): string {
  return role.replace(/^ROLE_/, '').trim().toUpperCase();
}

/**
 * 归一 + 去重角色列表 (忽略非字符串项)
 * @param roles 原始角色列表
 * @returns 规范形式的角色数组
 */
function normalizeRoles(roles: unknown): string[] {
  if (Array.isArray(roles)) {
    return [...new Set(roles.filter((r): r is string => typeof r === 'string').map(canonicalRole)
      .filter(r => r.length > 0))];
  }
  // 后端 roles 也可能是逗号分隔字符串 ("ADMIN,OPERATOR")
  if (typeof roles === 'string') {
    return normalizeRoles(roles.split(','));
  }
  return [];
}

/**
 * 从 JWT 中提取角色列表
 * @param token JWT 令牌
 * @returns 规范形式的角色数组 (去 ROLE_ 前缀 + 大写, 已去重)
 */
function extractRolesFromToken(token: string): string[] {
  const payload = decodeJwtPayload(token);
  if (!payload) return [];
  const roles: string[] = [
    ...normalizeRoles(payload.roles),
    ...(typeof payload.role === 'string' ? [payload.role] : []),
    ...normalizeRoles(payload.authorities),
  ];
  return normalizeRoles(roles);
}

/** 角色优先级 (数值越小优先级越高) */
const ROLE_PRIORITY: Record<string, number> = {
  ADMIN: 1,
  MANAGER: 2,
  SALES: 3,
  VIEWER: 4,
};

/**
 * 从角色列表中获取主角色 (优先级最高)
 * @param roles 角色列表 (可能含 ROLE_ 前缀或大小写不一致)
 * @returns 标准化的小写角色名 (admin/manager/sales/viewer), 无匹配返回 'viewer'
 */
function normalizeRole(roles: string[]): string {
  if (roles.length === 0) return 'viewer';
  let bestRole = 'viewer';
  let bestPriority = Infinity;
  for (const r of roles) {
    const normalized = canonicalRole(r);
    const priority = ROLE_PRIORITY[normalized];
    if (priority !== undefined && priority < bestPriority) {
      bestRole = normalized.toLowerCase();
      bestPriority = priority;
    }
  }
  return bestRole;
}

/**
 * 用户登录
 * @param username 用户名
 * @param password 密码
 * @returns 登录响应
 */
export const login = async (username: string, password: string): Promise<LoginResponse> => {
  const response = await axios.post(AUTH_LOGIN_URL, {
    username,
    password,
  }, {
    headers: { 'Content-Type': 'application/json' },
  });

  const body = response.data;
  if (body.status !== 'SUCCESS' || !body.data) {
    throw new Error(body.message || '登录失败');
  }

  const tokenData = body.data;
  const accessToken = tokenData.accessToken || tokenData.token || '';
  const jwtRoles = extractRolesFromToken(accessToken);
  const responseRoles = normalizeRoles(tokenData.roles);
  const roles = jwtRoles.length > 0 ? jwtRoles : responseRoles;
  const primaryRole = normalizeRole(roles);
  // 从响应或 JWT payload 中提取部门 ID
  const jwtPayload = decodeJwtPayload(accessToken);
  const departmentId =
    tokenData.departmentId ||
    (jwtPayload && typeof jwtPayload.departmentId === 'string' ? jwtPayload.departmentId : '') ||
    '';

  return {
    token: accessToken,
    refreshToken: tokenData.refreshToken || '',
    username,
    // 后端下发数字 ID, 这里显式转字符串; 只把 null/undefined 视为缺失, 以免 userId = 0 被误当成空值
    userId: tokenData.userId == null ? '' : String(tokenData.userId),
    displayName: tokenData.displayName || tokenData.username || username,
    email: tokenData.email || '',
    roles,
    permissions: tokenData.permissions || [],
    role: primaryRole,
    departmentId,
  };
};

/**
 * 保存登录数据到 localStorage
 * @param response 登录响应
 */
export const saveAuthData = (response: LoginResponse): void => {
  localStorage.setItem('scrm_token', response.token);
  localStorage.setItem('username', response.username);
  localStorage.setItem('displayName', response.displayName || response.username);
  localStorage.setItem('userId', response.userId);
  if (response.refreshToken) {
    localStorage.setItem('refreshToken', response.refreshToken);
  }
  if (response.roles) {
    localStorage.setItem('userRoles', JSON.stringify(response.roles));
  }
  if (response.permissions) {
    localStorage.setItem('userPermissions', JSON.stringify(response.permissions));
  }
  if (response.role) {
    localStorage.setItem('userRole', response.role);
  }
  if (response.departmentId) {
    localStorage.setItem('departmentId', response.departmentId);
  }
};

/** 退出登录, 清除 localStorage */
export const logout = (): void => {
  localStorage.removeItem('scrm_token');
  localStorage.removeItem('refreshToken');
  localStorage.removeItem('username');
  localStorage.removeItem('displayName');
  localStorage.removeItem('userId');
  localStorage.removeItem('userRoles');
  localStorage.removeItem('userPermissions');
  localStorage.removeItem('userRole');
  localStorage.removeItem('departmentId');
};

/**
 * 刷新 token
 * @returns 新的 access token, 失败返回 null
 */
export const refreshToken = async (): Promise<string | null> => {
  const refreshTokenValue = localStorage.getItem('refreshToken');
  if (!refreshTokenValue) return null;

  try {
    const response = await axios.post(AUTH_REFRESH_URL, {
      refreshToken: refreshTokenValue,
    }, {
      headers: { 'Content-Type': 'application/json' },
    });

    const body = response.data;
    if (body.status !== 'SUCCESS' || !body.data) {
      logout();
      window.location.href = '/login';
      return null;
    }

    const tokenData = body.data;
    const newAccessToken = tokenData.accessToken || tokenData.token;
    if (newAccessToken) {
      localStorage.setItem('scrm_token', newAccessToken);
      if (tokenData.refreshToken) {
        localStorage.setItem('refreshToken', tokenData.refreshToken);
      }
      return newAccessToken;
    }
    return null;
  } catch {
    logout();
    window.location.href = '/login';
    return null;
  }
};

/**
 * 判断 token 是否过期
 * @param token JWT 令牌
 * @returns 是否已过期
 */
export const isTokenExpired = (token: string): boolean => {
  const payload = decodeJwtPayload(token);
  if (!payload) return true;
  if (typeof payload.exp !== 'number') return true;
  return Date.now() >= payload.exp * 1000;
};

/** 是否已登录 (token 存在且未过期) */
export const isAuthenticated = (): boolean => {
  const token = localStorage.getItem('scrm_token');
  if (!token) return false;
  return !isTokenExpired(token);
};

/** 获取当前 token */
export const getToken = (): string | null => localStorage.getItem('scrm_token');

/** 获取用户 ID */
export const getUserId = (): string | null => localStorage.getItem('userId');

/** 获取用户名 */
export const getUsername = (): string | null => localStorage.getItem('username');

/** 获取显示名称 */
export const getDisplayName = (): string | null => localStorage.getItem('displayName');

/** 获取用户角色列表 */
export const getUserRoles = (): string[] => {
  try {
    const roles = localStorage.getItem('userRoles');
    return roles ? JSON.parse(roles) : [];
  } catch {
    return [];
  }
};

/** 获取用户权限列表 */
export const getUserPermissions = (): string[] => {
  try {
    const permissions = localStorage.getItem('userPermissions');
    return permissions ? JSON.parse(permissions) : [];
  } catch {
    return [];
  }
};

/** 获取当前用户主角色 (小写: admin/manager/sales/viewer) */
export const getRole = (): string => {
  const stored = localStorage.getItem('userRole');
  if (stored) return stored;
  // 回退: 从角色列表推导 (兼容旧会话)
  return normalizeRole(getUserRoles());
};

/** 获取部门 ID */
export const getDepartmentId = (): string | null => localStorage.getItem('departmentId');

/** 是否为管理员 */
export const isAdmin = (): boolean => {
  const roles = getUserRoles();
  return roles.some(r => r === 'ADMIN' || r === 'ROLE_ADMIN');
};

/**
 * 是否拥有指定权限
 * <p>
 * 基于角色判断: admin/manager 拥有所有权限, sales/viewer 根据资源前缀判断。
 * 后续接入细粒度权限服务后, 可改为查询 userPermissions 列表。
 *
 * @param permission 权限标识 (如 'scrm_customer:read')
 * @returns 是否拥有
 */
export const hasPermission = (permission: string): boolean => {
  const role = getRole();
  // admin/manager 拥有所有权限
  if (role === 'admin' || role === 'manager') return true;
  // sales: 客户/会话/营销/消息模板权限
  if (role === 'sales') {
    const salesPermissions = [
      'scrm_customer', 'scrm_conversation', 'scrm_campaign', 'scrm_message_template',
    ];
    return salesPermissions.some(p => permission.startsWith(p));
  }
  // viewer: 所有 read 权限
  if (role === 'viewer') {
    return permission.endsWith(':read');
  }
  // fallback: 检查显式权限列表
  const permissions = getUserPermissions();
  return permissions.some(p => p === permission || p === '*:*');
};
