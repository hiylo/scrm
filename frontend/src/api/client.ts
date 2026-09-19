/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : client.ts
 * Date : 2026/09/18 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

import axios, { AxiosRequestConfig, AxiosResponse } from 'axios';
import { message } from 'antd';

/**
 * API 基础地址
 * 优先使用环境变量; 否则使用当前页面同源地址, 便于反向代理直连后端
 */
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '';

/** axios 实例 */
const apiClientInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

/** 请求超时 (ms) */
export const DEFAULT_TIMEOUT = 30000;
/** 长耗时操作超时 (ms) */
export const LONG_TIMEOUT = 120000;

/** 请求拦截器: 注入 Authorization、X-User-Id、X-Department-Id 头 */
apiClientInstance.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('scrm_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    const userId = localStorage.getItem('userId');
    if (userId) {
      config.headers['X-User-Id'] = userId;
    }
    const departmentId = localStorage.getItem('departmentId');
    if (departmentId) {
      config.headers['X-Department-Id'] = departmentId;
    }
    return config;
  },
  (error) => Promise.reject(error),
);

/** 响应拦截器: 401 跳转登录, 非 401 错误弹出提示 */
apiClientInstance.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('scrm_token');
      localStorage.removeItem('refreshToken');
      localStorage.removeItem('userId');
      localStorage.removeItem('displayName');
      // 使用 BASE_URL 适配子路径部署 (如 /scrm/)
      const loginPath = `${import.meta.env.BASE_URL}login`;
      if (!window.location.pathname.endsWith('/login')) {
        window.location.href = loginPath;
      }
    } else if (error.response && error.response.status !== 404) {
      const msg = error.response?.data?.message || error.message || '请求失败';
      message.error(msg);
    }
    return Promise.reject(error);
  },
);

/**
 * 解包后端 OperationResponse 响应
 * 标准格式: { status: 'SUCCESS'|'FAILURE', data: T, message: string, code: string }
 */
function unwrap<T>(response: AxiosResponse): T {
  const body = response.data;
  if (body && typeof body === 'object' && 'status' in body && 'data' in body) {
    if (body.status === 'SUCCESS') {
      return body.data as T;
    }
    const err = new Error(body.message || '操作失败');
    (err as unknown as Record<string, unknown>).code = body.code;
    throw err;
  }
  // 兼容 Spring Page 对象
  if (body && typeof body === 'object' && 'content' in body && 'totalElements' in body) {
    return body as T;
  }
  return body as T;
}

/** 类型化的 API 客户端 */
export const apiClient = {
  get: <T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T> =>
    apiClientInstance.get(url, config).then((r: AxiosResponse) => unwrap<T>(r)),
  post: <T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> =>
    apiClientInstance.post(url, data, config).then((r: AxiosResponse) => unwrap<T>(r)),
  put: <T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> =>
    apiClientInstance.put(url, data, config).then((r: AxiosResponse) => unwrap<T>(r)),
  delete: <T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T> =>
    apiClientInstance.delete(url, config).then((r: AxiosResponse) => unwrap<T>(r)),
};

/** 获取 API 基础地址 */
export const getApiBaseUrl = (): string => API_BASE_URL;

/** 导出 axios 实例 (用于文件上传等特殊场景) */
export { apiClientInstance };
