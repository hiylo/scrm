/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : CustomerGroups.test.tsx
 * Description : 客户分组页面测试 - 列表加载条件 / 新建分组后自动刷新
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import CustomerGroups from './CustomerGroups';
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

/** 账号下拉数据 */
const ACCOUNTS = [{ id: '9001', accountName: '180测试号', platformType: 'wechat_personal' }];

/** 分组行 */
function group(id: string, name: string) {
  return {
    id,
    groupName: name,
    description: undefined,
    ownerAccountId: '9001',
    createTime: '2026-11-11 11:59',
  } as never;
}

/** 统一的接口打桩；可选是否已有分组 */
function stubApi(opts: { groups?: unknown[] }) {
  get.mockImplementation((url: string) => {
    if (url.includes('/accounts')) return Promise.resolve({ content: ACCOUNTS } as never);
    if (url.includes('/customers/groups')) return Promise.resolve((opts.groups ?? []) as never);
    return Promise.resolve([] as never);
  });
}

describe('CustomerGroups', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('未选择归属账号时提示先选择，且不请求分组接口', async () => {
    stubApi({});
    renderWithProviders(<CustomerGroups />);

    await waitFor(() => expect(screen.getByText('请选择归属账号查询分组')).toBeInTheDocument());
    // 未选账号时不应带 ownerAccountId 调分组接口
    expect(get).not.toHaveBeenCalledWith(expect.stringContaining('/customers/groups'));
  });

  it('会加载账号下拉数据，供选择归属账号', async () => {
    stubApi({});
    renderWithProviders(<CustomerGroups />);

    await waitFor(() =>
      expect(get).toHaveBeenCalledWith(expect.stringContaining('/scrm/accounts?page=0&size=100')),
    );
    // 页面至少渲染出一个下拉（归属账号筛选）
    await waitFor(() => expect(document.querySelectorAll('.ant-select').length).toBeGreaterThan(0));
  });

  it('新建分组: 提交后端字段并对新账号立即刷新列表', async () => {
    let created = false;
    get.mockImplementation((url: string) => {
      if (url.includes('/accounts')) return Promise.resolve({ content: ACCOUNTS } as never);
      if (url.includes('/customers/groups')) {
        return Promise.resolve((created ? [group('2', 'UI验收_分组A')] : []) as never);
      }
      return Promise.resolve([] as never);
    });
    post.mockImplementation(async () => {
      created = true;
      return group('2', 'UI验收_分组A');
    });

    renderWithProviders(<CustomerGroups />);
    await waitFor(() => expect(get).toHaveBeenCalledWith(expect.stringContaining('/scrm/accounts')));

    fireEvent.click(screen.getByRole('button', { name: cn('新建分组') }));
    await waitFor(() => expect(screen.getByLabelText('分组名称')).toBeInTheDocument());

    await userEvent.type(screen.getByLabelText('分组名称'), 'UI验收_分组A');

    // 归属账号是 Select（不是输入框）：先打开下拉再点选项
    const ownerSelector = screen.getByLabelText('归属账号 ID').closest('.ant-select') as HTMLElement;
    fireEvent.mouseDown(ownerSelector.querySelector('.ant-select-selector') as HTMLElement);
    const option = await screen.findByTitle('180测试号 (wechat_personal)');
    fireEvent.click(option);

    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));

    // 提交的字段名与后端一致
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        '/scrm/customers/groups',
        expect.objectContaining({ groupName: 'UI验收_分组A', ownerAccountId: '9001' }),
      ),
    );

    // 关键断言：创建成功后必须按新分组所属账号重新拉取列表，无需用户手动点刷新
    await waitFor(() =>
      expect(get).toHaveBeenCalledWith(expect.stringContaining('ownerAccountId=9001')),
    );
  });
});
