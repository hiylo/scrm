/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : VoucherAttachmentDrawer.test.tsx
 * Description : 凭证原始单据抽屉的列表加载 / 上传挂载 / 下载 / 删除 / 作废只读冒烟测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import VoucherAttachmentDrawer from './VoucherAttachmentDrawer';
import { apiClient } from '../api/client';
import { renderWithProviders } from '../test/render';

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
const del = vi.mocked(apiClient.delete);

/** 单据行 */
function attachment(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    voucherId: '9001',
    companyId: '1',
    objectKey: `scrm/voucher/202611/${id}.png`,
    fileName: `单据${id}.png`,
    fileSize: 2048,
    contentType: 'image/png',
    docType: 'INVOICE',
    uploadedAt: '2026-11-10T10:00:00',
    ...overrides,
  } as never;
}

/** 渲染抽屉并等待列表加载完成 */
async function renderDrawer(props: Partial<React.ComponentProps<typeof VoucherAttachmentDrawer>> = {}) {
  const onClose = vi.fn();
  const onChanged = vi.fn();
  const user = userEvent.setup();
  renderWithProviders(
    <VoucherAttachmentDrawer
      open
      voucherId="9001"
      voucherLabel="记-0001"
      voucherStatus="DRAFT"
      onClose={onClose}
      onChanged={onChanged}
      {...props}
    />,
  );
  return { user, onClose, onChanged };
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('VoucherAttachmentDrawer', () => {
  it('打开后加载单据列表，并展示类型/大小/份数', async () => {
    get.mockResolvedValue([attachment('1'), attachment('2', { docType: 'BANK_RECEIPT', fileName: '回单.pdf' })] as never);
    await renderDrawer();

    expect(await screen.findByText('单据1.png')).toBeInTheDocument();
    expect(screen.getByText('回单.pdf')).toBeInTheDocument();
    // 类型中文映射（发票在「单据类型」下拉里也有同名项，这里限定到表格内）
    const table = screen.getByRole('table');
    expect(within(table).getByText('发票')).toBeInTheDocument();
    expect(within(table).getByText('银行回单')).toBeInTheDocument();
    // 大小格式化（2048B → 2.0 KB）
    expect(screen.getAllByText('2.0 KB').length).toBeGreaterThan(0);
    // 份数
    expect(screen.getByText('共 2 份')).toBeInTheDocument();

    expect(get).toHaveBeenCalledWith('/scrm/vouchers/9001/attachments');
  });

  it('无单据时展示空态与上传引导', async () => {
    get.mockResolvedValue([] as never);
    await renderDrawer();

    expect(await screen.findByText('共 0 份')).toBeInTheDocument();
    expect(
      screen.getByText('还没有挂载原始单据。上传发票/回单后，审计与对账时可直接调阅'),
    ).toBeInTheDocument();
  });

  it('下载走预签名 URL 而不是直连后端接口', async () => {
    get.mockImplementation((url: string) => {
      if (url.includes('/download-url')) {
        return Promise.resolve({ url: 'http://minio/presigned' } as never);
      }
      return Promise.resolve([attachment('1')] as never);
    });
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null);
    const { user } = await renderDrawer();

    await screen.findByText('单据1.png');
    await user.click(screen.getByRole('button', { name: /下载/ }));

    await waitFor(() =>
      expect(openSpy).toHaveBeenCalledWith('http://minio/presigned', '_blank', 'noopener,noreferrer'),
    );
    openSpy.mockRestore();
  });

  it('删除先确认再调接口，成功后刷新列表', async () => {
    get.mockResolvedValue([attachment('1')] as never);
    del.mockResolvedValue(undefined as never);
    const { user, onChanged } = await renderDrawer();

    await screen.findByText('单据1.png');
    await user.click(screen.getByRole('button', { name: /删除/ }));

    // 确认弹窗（antd Modal 会把标题重复渲染到 aria 层，这里只断言至少出现一次）
    expect((await screen.findAllByText('确认删除该原始单据？')).length).toBeGreaterThan(0);
    await user.click(screen.getByRole('button', { name: /确\s*定/ }));

    await waitFor(() => expect(del).toHaveBeenCalledWith('/scrm/vouchers/attachments/1'));
    await waitFor(() => expect(onChanged).toHaveBeenCalled());
  });

  it('已作废凭证只读：隐藏上传区与删除按钮，但保留下载', async () => {
    get.mockResolvedValue([attachment('1')] as never);
    await renderDrawer({ voucherStatus: 'VOID' });

    expect(await screen.findByText('单据1.png')).toBeInTheDocument();
    expect(
      screen.getByText('凭证已作废，单据仅供查看与下载，不可新增或删除。'),
    ).toBeInTheDocument();
    // 上传引导不存在
    expect(screen.queryByText('点击或拖拽单据到此处')).not.toBeInTheDocument();
    // 删除按钮不存在，下载仍在
    expect(screen.queryByRole('button', { name: /删除/ })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /下载/ })).toBeInTheDocument();
  });

  it('未选文件时点挂载提示而不是发请求', async () => {
    get.mockResolvedValue([] as never);
    const { user } = await renderDrawer();

    await screen.findByText('共 0 份');
    await user.click(screen.getByRole('button', { name: /挂载单据/ }));

    expect(post).not.toHaveBeenCalled();
  });
});
