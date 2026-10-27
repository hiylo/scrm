/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Renewal.test.tsx
 * Description : 续费页审批入口测试（接入审批流后不得绕过审批引擎）
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import Renewal from './Renewal';
import { apiClient } from '../api/client';
import { PathProbe, renderWithProviders } from '../test/render';

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

/** 审批中状态的续费任务 */
function approvalTask(overrides: Record<string, unknown> = {}) {
  return {
    id: '8801',
    taskNo: 'RN-2026-001',
    contractId: '7701',
    contractNo: 'HT-2026-001',
    contractName: '鼎昊代账合同',
    customerName: '鼎昊环境',
    plannedRenewalDate: '2026-11-01',
    renewalOption: 'DISCOUNT',
    status: 'APPROVAL_PENDING',
    discountPct: 0.85,
    ...overrides,
  } as never;
}

/**
 * 渲染续费页并挂上跳转探针。
 *
 * @param rows 列表数据
 */
function renderPage(rows: unknown[]) {
  get.mockImplementation((url: string) => {
    if (String(url).includes('/scrm/renewals')) return Promise.resolve(page(rows) as never);
    return Promise.resolve([] as never);
  });
  return renderWithProviders(
    <Routes>
      <Route path="/" element={<Renewal />} />
      <Route path="/approvals" element={<PathProbe />} />
    </Routes>,
  );
}

describe('Renewal 续费页审批入口', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('已接入审批流的任务：点击「审批通过」不直接调续费接口，而是引导去审批中心', async () => {
    renderPage([approvalTask({ approvalInstanceId: '9901' })]);

    await waitFor(() => expect(screen.getByText('审批通过')).toBeInTheDocument());
    await userEvent.click(screen.getByText('审批通过'));

    // 关键：不能绕过审批引擎直接通过，否则实例会永远停在「审批中」
    expect(post).not.toHaveBeenCalledWith(
      expect.stringContaining('/approve'),
      expect.anything(),
    );

    // 提示后跳转审批中心
    await waitFor(() => expect(screen.getByText('去审批中心')).toBeInTheDocument());
    await userEvent.click(screen.getByText('去审批中心'));
    await waitFor(() => expect(screen.getByTestId('path')).toHaveTextContent('/approvals'));
  });

  it('无审批实例的历史任务：仍允许直接审批通过（保留旧路径）', async () => {
    post.mockResolvedValue({ id: '8801' } as never);
    renderPage([approvalTask({ approvalInstanceId: undefined })]);

    await waitFor(() => expect(screen.getByText('审批通过')).toBeInTheDocument());
    await userEvent.click(screen.getByText('审批通过'));

    // 二次确认（标题可能在 DOM 中出现多次，断言存在即可）
    await waitFor(() => expect(screen.getAllByText('确认审批通过该续费？').length).toBeGreaterThan(0));
    const confirmBtn = Array.from(document.querySelectorAll('.ant-modal-confirm button'))
      .find(b => /确\s*定/.test(b.textContent || '')) as HTMLElement;
    await userEvent.click(confirmBtn);

    // 契约：post(url, undefined, { params }) —— 参数走 query 而非 body
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        expect.stringContaining('/scrm/renewals/8801/approve'),
        undefined,
        expect.objectContaining({ params: expect.anything() }),
      ),
    );
  });
});
