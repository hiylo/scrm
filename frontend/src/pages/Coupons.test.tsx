/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Coupons.test.tsx
 * Description : 优惠券页面的模板 / 发放 / 统计测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Coupons from './Coupons';
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

/** 模板行 */
function template(id: string, name = `模板${id}`) {
  return {
    id,
    templateName: name,
    couponType: 'CASH',
    faceValue: 30,
    thresholdAmount: 200,
    validType: 'RELATIVE',
    validDays: 30,
    totalQuantity: 1000,
    issuedQuantity: 100,
    usedQuantity: 20,
    status: 'ACTIVE',
    createTime: '2026-10-08T10:00:00',
  } as never;
}

/** 券行 */
function coupon(id: string) {
  return {
    id,
    couponCode: `CP-${id}`,
    templateId: '1',
    customerName: '张三',
    claimSource: 'CHANNEL',
    status: 'UNUSED',
    expiresAt: '2026-11-08T10:00:00',
  } as never;
}

function renderPage() {
  return renderWithProviders(<Coupons />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('Coupons', () => {
  it('加载券模板 (默认 Tab)', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/templates/list')) return Promise.resolve(page([template('1'), template('2', '模板2')]));
      if (url.includes('/coupons/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('模板1')).toBeInTheDocument());
    expect(screen.getByText('模板2')).toBeInTheDocument();
  });

  it('新建模板: 打开弹窗填写后 POST', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/templates/list')) return Promise.resolve(page([]));
      if (url.includes('/coupons/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue(template('1'));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: cn('新建模板') }));
    await waitFor(() => expect(screen.getByLabelText('模板名称')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('模板名称'), '满减券');
    await userEvent.type(screen.getByLabelText('面值'), '30');
    await userEvent.type(screen.getByLabelText('发放总量'), '1000');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/coupons/templates', expect.objectContaining({ templateName: '满减券', faceValue: 30 })));
  });

  it('发放优惠券: 打开弹窗填写后 issue', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/templates/list')) return Promise.resolve(page([template('1')]));
      if (url.includes('/coupons/list')) return Promise.resolve(page([]));
      return Promise.resolve(page([]));
    });
    post.mockResolvedValue({ successCount: 50, coupons: [] });

    renderPage();
    await waitFor(() => expect(screen.getByText('模板1')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: cn('发放') }));
    await waitFor(() => expect(screen.getByLabelText('发放数量')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('发放数量'), '50');
    fireEvent.click(screen.getByRole('button', { name: cn('确定') }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/coupons/issue', expect.objectContaining({ templateId: '1', quantity: 50 })));
  });
});