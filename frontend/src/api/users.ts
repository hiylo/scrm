/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : users.ts
 * Date : 2026/09/22 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

import { apiClient } from './client';

/** 用户视图 (管理端) */
export interface UserView {
  id: string;
  username: string;
  displayName?: string;
  email?: string;
  roles: string;
  status: number;
  lastLoginAt?: string;
  createTime?: string;
  updateTime?: string;
}

/** 创建用户请求 */
export interface CreateUserPayload {
  username: string;
  password: string;
  displayName?: string;
  email?: string;
  roles?: string;
}

/** 更新用户请求 */
export interface UpdateUserPayload {
  displayName?: string;
  email?: string;
  roles?: string;
  status?: number;
}

/** Spring Page 分页响应 */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/** 分页查询用户 */
export const listUsers = (params: { keyword?: string; page?: number; size?: number } = {}) =>
  apiClient.get<Page<UserView>>('/scrm/users/list', { params });

/** 查询用户详情 */
export const getUser = (id: string) => apiClient.get<UserView>(`/scrm/users/${id}`);

/** 创建用户 */
export const createUser = (payload: CreateUserPayload) =>
  apiClient.post<UserView>('/scrm/users', payload);

/** 更新用户 */
export const updateUser = (id: string, payload: UpdateUserPayload) =>
  apiClient.put<UserView>(`/scrm/users/${id}`, payload);

/** 设置用户状态 (1=启用, 0=禁用) */
export const setUserStatus = (id: string, status: number) =>
  apiClient.put<UserView>(`/scrm/users/${id}/status`, null, { params: { status } });

/** 重置用户密码 */
export const resetUserPassword = (id: string, newPassword: string) =>
  apiClient.post<void>(`/scrm/users/${id}/reset-password`, { newPassword });
