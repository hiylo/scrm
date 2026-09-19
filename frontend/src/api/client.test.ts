/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : client.test.ts
 * Description : API 客户端拦截器与 OperationResponse 解包行为测试
 */

import { describe, it, expect, vi, afterEach } from 'vitest';
import { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import type { AxiosResponse } from 'axios';
import { message } from 'antd';
import {
  apiClient,
  apiClientInstance,
  getApiBaseUrl,
  DEFAULT_TIMEOUT,
  LONG_TIMEOUT,
} from './client';

/** 请求头读取 (兼容 AxiosHeaders 实例与普通对象) */
function readHeader(headers: unknown, name: string): unknown {
  const bag = headers as Record<string, unknown> & {
    get?: (key: string) => unknown;
  };
  if (!bag) return undefined;
  if (typeof bag.get === 'function') {
    const viaGetter = bag.get(name);
    if (viaGetter !== undefined) return viaGetter;
  }
  if (bag[name] !== undefined) return bag[name];
  const lower = name.toLowerCase();
  const key = Object.keys(bag).find((k) => k.toLowerCase() === lower);
  return key ? bag[key] : undefined;
}

/** 适配器返回的响应描述 */
interface MockResult {
  status?: number;
  data?: unknown;
  message?: string;
}

type CapturedConfig = InternalAxiosRequestConfig;

/**
 * 用自定义 adapter 替换真实 HTTP 调用
 * <p>
 * adapter 位于请求拦截器之后、响应拦截器之前, 因此既能观察拦截器注入的请求头,
 * 又能通过返回/抛出值驱动响应拦截器分支。
 */
let lastConfig: CapturedConfig | null = null;

function stubAdapter(result: MockResult | ((config: CapturedConfig) => MockResult)) {
  const original = apiClientInstance.defaults.adapter;
  lastConfig = null;
  apiClientInstance.defaults.adapter = (async (config: CapturedConfig): Promise<AxiosResponse> => {
    lastConfig = config;
    const resolved =
      typeof result === 'function' ? result(config) : result;
    const status = resolved.status ?? 200;
    const response: AxiosResponse = {
      data: resolved.data,
      status,
      statusText: `HTTP ${status}`,
      headers: {},
      config,
    };
    if (status >= 400) {
      throw new AxiosError(
        resolved.message || `Request failed with status code ${status}`,
        'ERR_BAD_REQUEST',
        config,
        {},
        response,
      );
    }
    return response;
  }) as NonNullable<typeof original>;
  return () => {
    apiClientInstance.defaults.adapter = original;
  };
}

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

/** 模拟浏览器地址 (client.ts 会读取 pathname 并写入 href) */
function stubLocation(pathname: string) {
  const location = { pathname, href: `http://localhost${pathname}`, origin: 'http://localhost' };
  // jsdom 中 window === globalThis, 因此替换全局 location 即可同时影响 window.location
  vi.stubGlobal('location', location);
  return location;
}

describe('apiClient 实例配置', () => {
  it('导出超时常量与基础地址', () => {
    expect(DEFAULT_TIMEOUT).toBe(30000);
    expect(LONG_TIMEOUT).toBe(120000);
    expect(apiClientInstance.defaults.timeout).toBe(DEFAULT_TIMEOUT);
    expect(getApiBaseUrl()).toBe(import.meta.env.VITE_API_BASE_URL || '');
    expect(apiClientInstance.defaults.baseURL).toBe(getApiBaseUrl());
  });
});

describe('请求拦截器: 鉴权请求头注入', () => {
  it('localStorage 中存在凭证时注入 Authorization / X-User-Id / X-Department-Id', async () => {
    localStorage.setItem('scrm_token', 'jwt-token');
    localStorage.setItem('userId', 'u-1001');
    localStorage.setItem('departmentId', 'd-2002');
    const restore = stubAdapter({ data: { ok: true } });

    await apiClient.get('/scrm/customers');
    restore();

    expect(readHeader(lastConfig?.headers, 'Authorization')).toBe('Bearer jwt-token');
    expect(readHeader(lastConfig?.headers, 'X-User-Id')).toBe('u-1001');
    expect(readHeader(lastConfig?.headers, 'X-Department-Id')).toBe('d-2002');
  });

  it('无凭证时不注入鉴权头, 但保留 Content-Type', async () => {
    const restore = stubAdapter({ data: {} });

    await apiClient.get('/scrm/auth/login');
    restore();

    expect(readHeader(lastConfig?.headers, 'Authorization')).toBeUndefined();
    expect(readHeader(lastConfig?.headers, 'X-User-Id')).toBeUndefined();
    expect(readHeader(lastConfig?.headers, 'X-Department-Id')).toBeUndefined();
    expect(readHeader(lastConfig?.headers, 'Content-Type')).toContain('application/json');
  });

  it('仅有 token 时只注入 Authorization', async () => {
    localStorage.setItem('scrm_token', 'only-token');
    const restore = stubAdapter({ data: {} });

    await apiClient.post('/scrm/customers', { nickname: '张三' });
    restore();

    expect(readHeader(lastConfig?.headers, 'Authorization')).toBe('Bearer only-token');
    expect(readHeader(lastConfig?.headers, 'X-User-Id')).toBeUndefined();
    expect(readHeader(lastConfig?.headers, 'X-Department-Id')).toBeUndefined();
  });

  it('put / delete 同样经过请求拦截器', async () => {
    localStorage.setItem('scrm_token', 'tk');
    localStorage.setItem('userId', 'u9');
    const restore = stubAdapter({ data: { status: 'SUCCESS', data: null } });

    await apiClient.put('/scrm/customers/1', { nickname: '李四' });
    await apiClient.delete('/scrm/customers/1');
    restore();

    expect(readHeader(lastConfig?.headers, 'Authorization')).toBe('Bearer tk');
    expect(readHeader(lastConfig?.headers, 'X-User-Id')).toBe('u9');
    expect(lastConfig?.method).toBe('delete');
  });
});

describe('响应拦截器: 401 清理会话并跳转登录', () => {
  it('401 时清除本地凭证并跳转到登录页', async () => {
    localStorage.setItem('scrm_token', 'expired');
    localStorage.setItem('refreshToken', 'rt');
    localStorage.setItem('userId', 'u1');
    localStorage.setItem('displayName', '张三');
    const location = stubLocation('/customers');
    const errorSpy = vi.spyOn(message, 'error').mockImplementation(() => ({} as never));
    const restore = stubAdapter({ status: 401, data: { message: '未登录' } });

    await expect(apiClient.get('/scrm/customers')).rejects.toBeInstanceOf(AxiosError);
    restore();

    expect(localStorage.getItem('scrm_token')).toBeNull();
    expect(localStorage.getItem('refreshToken')).toBeNull();
    expect(localStorage.getItem('userId')).toBeNull();
    expect(localStorage.getItem('displayName')).toBeNull();
    expect(location.href).toBe('/login');
    // 401 不应再弹通用错误提示
    expect(errorSpy).not.toHaveBeenCalled();
  });

  it('已位于登录页时不重复跳转, 但仍清理凭证', async () => {
    localStorage.setItem('scrm_token', 'expired');
    const location = stubLocation('/login');
    const restore = stubAdapter({ status: 401, data: {} });

    await expect(apiClient.post('/scrm/auth/login', {})).rejects.toBeInstanceOf(AxiosError);
    restore();

    expect(localStorage.getItem('scrm_token')).toBeNull();
    expect(location.href).toBe('http://localhost/login');
  });
});

describe('响应拦截器: 非 401 错误提示', () => {
  it('500 错误弹出后端返回的 message', async () => {
    const errorSpy = vi.spyOn(message, 'error').mockImplementation(() => ({} as never));
    const restore = stubAdapter({ status: 500, data: { message: '服务器开小差了' } });

    await expect(apiClient.get('/scrm/customers')).rejects.toBeInstanceOf(AxiosError);
    restore();

    expect(errorSpy).toHaveBeenCalledTimes(1);
    expect(errorSpy).toHaveBeenCalledWith('服务器开小差了');
  });

  it('后端未返回 message 时回退到 axios 错误信息', async () => {
    const errorSpy = vi.spyOn(message, 'error').mockImplementation(() => ({} as never));
    const restore = stubAdapter({ status: 502, data: {}, message: 'Bad Gateway' });

    await expect(apiClient.post('/scrm/campaigns', {})).rejects.toBeInstanceOf(AxiosError);
    restore();

    expect(errorSpy).toHaveBeenCalledWith('Bad Gateway');
  });

  it('404 静默处理, 不弹提示也不清理凭证', async () => {
    localStorage.setItem('scrm_token', 'keep-me');
    const errorSpy = vi.spyOn(message, 'error').mockImplementation(() => ({} as never));
    const restore = stubAdapter({ status: 404, data: { message: 'not found' } });

    await expect(apiClient.get('/scrm/unknown')).rejects.toBeInstanceOf(AxiosError);
    restore();

    expect(errorSpy).not.toHaveBeenCalled();
    expect(localStorage.getItem('scrm_token')).toBe('keep-me');
  });
});

describe('unwrap: 后端响应解包', () => {
  it('OperationResponse status=SUCCESS 时返回 data 字段', async () => {
    const restore = stubAdapter({
      data: { status: 'SUCCESS', data: { id: 7, nickname: '张三' }, message: 'ok', code: '0' },
    });

    const result = await apiClient.get<{ id: number; nickname: string }>('/scrm/customers/7');
    restore();

    expect(result).toEqual({ id: 7, nickname: '张三' });
  });

  it('OperationResponse status=FAILURE 时抛出带 code 的 Error', async () => {
    const errorSpy = vi.spyOn(message, 'error').mockImplementation(() => ({} as never));
    // 后端以 HTTP 200 + status=FAILURE 表达业务失败, 此时由 unwrap 负责抛错
    const restore = stubAdapter({
      data: { status: 'FAILURE', data: null, message: '昵称不能为空', code: 'SCRM_VALIDATION' },
    });

    const error = await apiClient.post('/scrm/customers', {}).catch((e: unknown) => e);
    restore();

    expect(error).toBeInstanceOf(Error);
    expect((error as Error).message).toBe('昵称不能为空');
    expect((error as unknown as { code?: string }).code).toBe('SCRM_VALIDATION');
    expect(errorSpy).not.toHaveBeenCalled();
  });

  it('status=FAILURE 且无 message 时使用默认文案', async () => {
    const restore = stubAdapter({ data: { status: 'FAILURE', data: null } });
    const error = await apiClient.delete('/scrm/customers/1').catch((e: unknown) => e);
    restore();
    expect((error as Error).message).toBe('操作失败');
  });

  it('Spring Page 对象原样返回', async () => {
    const page = {
      content: [{ id: 1 }, { id: 2 }],
      totalElements: 2,
      totalPages: 1,
      number: 0,
      size: 10,
    };
    const restore = stubAdapter({ data: page });

    const result = await apiClient.get<typeof page>('/scrm/customers?page=0');
    restore();

    expect(result).toEqual(page);
  });

  it('普通数组 / 字符串响应原样返回', async () => {
    const restore = stubAdapter({ data: ['a', 'b'] });
    const list = await apiClient.get<string[]>('/scrm/tags');
    restore();
    expect(list).toEqual(['a', 'b']);

    const restore2 = stubAdapter({ data: 'https://signed-url' });
    const text = await apiClient.get<string>('/scrm/media/1');
    restore2();
    expect(text).toBe('https://signed-url');
  });
});

describe('apiClientInstance (未解包的原始实例)', () => {
  it('返回完整 AxiosResponse, 供 blob 下载 / multipart 上传场景使用', async () => {
    localStorage.setItem('scrm_token', 'raw-token');
    const restore = stubAdapter({ data: 'binary-ish' });

    const response = await apiClientInstance.get('/scrm/customers/export?format=xlsx', {
      responseType: 'blob',
    });
    restore();

    expect(response.status).toBe(200);
    // 原始实例不做 unwrap, 返回体保持后端原样
    expect(response.data).toBe('binary-ish');
    expect(readHeader(lastConfig?.headers, 'Authorization')).toBe('Bearer raw-token');
    expect(readHeader(lastConfig?.headers, 'X-User-Id')).toBeUndefined();
  });
});
