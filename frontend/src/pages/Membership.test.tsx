/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Membership.test.tsx
 * Description : 会员体系页面的等级加载 / 会员列表 / 会员入会测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Membership from './Membership';
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

/** 等级行 */
function tier(id: string, name = `等级${id}`) {
  return { id, tierName: name, tierCode: `T${id}`, tierLevel: 1, upgradeThreshold: 1000, status: 'ACTIVE', benefits: '专属客服' } as never;
}

/** 会员行 */
function member(id: string, name = `会员${id}`) {
  return {
    id,
    customerId: '1',
    customerName: name,
    tierId: '1',
    tierName: '黄金',
    memberCardNo: `CARD${id}`,
    membershipStatus: 'ACTIVE',
    joinDate: '2026-10-01',
    totalSpend: 5000,
    totalPoints: 100,
    availablePoints: 80,
  } as never;
}

function renderPage() {
  return renderWithProviders(<Membership />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('Membership', () => {
  it('加载等级与会员列表', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/tiers/list')) return Promise.resolve(page([tier('1'), tier('2', '铂金')]));
      if (url.includes('/memberships/list')) return Promise.resolve(page([member('1'), member('2')]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('等级1')).toBeInTheDocument());
    expect(screen.getByText('铂金')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText('会员1')).toBeInTheDocument());
    expect(screen.getByText('会员2')).toBeInTheDocument();
  });

  it('新建等级: 打开弹窗填写后 POST', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/tiers/list')) return Promise.resolve(page([]));
      if (url.includes('/memberships/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(tier('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('新建等级') }));
    await waitFor(() => expect(screen.getByLabelText('等级名称')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('等级名称'), '钻石会员');
    await userEvent.type(screen.getByLabelText('等级编码'), 'DIAMOND');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/memberships/tiers', expect.objectContaining({ tierName: '钻石会员', tierCode: 'DIAMOND' })));
  });

  it('会员入会: 打开弹窗填写后 enroll', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/tiers/list')) return Promise.resolve(page([tier('1')]));
      if (url.includes('/memberships/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(member('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('会员入会') }));
    await waitFor(() => expect(screen.getByLabelText('客户 ID')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('客户 ID'), '1001');
    await userEvent.type(screen.getByLabelText('等级 ID'), '1');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/memberships/enroll', expect.objectContaining({ customerId: '1001', tierId: '1' })));
  });
});