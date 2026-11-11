/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : Payroll.test.tsx
 * Description : 社保个税页面测试 - 未选企业时提示先选企业 / 生成按钮前置条件
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import Payroll from './Payroll';
import { renderWithProviders } from '../test/render';

vi.mock('../api/client', () => ({
  apiClient: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
  apiClientInstance: { get: vi.fn(), post: vi.fn() },
  getApiBaseUrl: () => '',
  DEFAULT_TIMEOUT: 30000,
  LONG_TIMEOUT: 120000,
}));

vi.mock('../api/finance', () => ({
  listCompanies: vi.fn().mockResolvedValue({ content: [], totalElements: 0 }),
  listEmployees: vi.fn().mockResolvedValue({ content: [], totalElements: 0 }),
  createEmployee: vi.fn(),
  updateEmployee: vi.fn(),
  deleteEmployee: vi.fn(),
  leaveEmployee: vi.fn(),
  generatePayroll: vi.fn(),
  listSocialRecords: vi.fn().mockResolvedValue([]),
  getSocialSummary: vi.fn().mockResolvedValue(null),
  markSocialDeclared: vi.fn(),
  markSocialPaid: vi.fn(),
  listIitRecords: vi.fn().mockResolvedValue([]),
  getIitSummary: vi.fn().mockResolvedValue(null),
  markIitDeclared: vi.fn(),
  markIitPaid: vi.fn(),
}));

beforeEach(() => {
  vi.clearAllMocks();
});

describe('Payroll', () => {
  it('未选企业时，社保明细空态提示先选择企业（D5）', async () => {
    renderWithProviders(<Payroll />);

    // 切到社保明细 Tab
    const socialTab = await screen.findByRole('tab', { name: '社保明细' });
    fireEvent.click(socialTab);

    // D5 修复点：未选企业时文案必须点明「先在页面顶部选择企业」，
    // 而不是让人直接去点「生成本月社保/个税」
    await waitFor(() => expect(screen.getByText('请先在页面顶部选择企业')).toBeInTheDocument());
    expect(
      screen.getByText(/社保与个税按「企业 \+ 所属期」生成，未选企业时无法批量计算/),
    ).toBeInTheDocument();
  });

  it('未选企业时点击「生成本月社保/个税」给出选择企业提示', async () => {
    renderWithProviders(<Payroll />);

    const genBtn = screen
      .getAllByRole('button')
      .find((b) => b.textContent?.replace(/\s/g, '') === '生成本月社保/个税');
    expect(genBtn).toBeTruthy();
    fireEvent.click(genBtn as HTMLElement);

    await waitFor(() => expect(screen.getByText('请先在页面顶部选择企业')).toBeInTheDocument());
  });
});
