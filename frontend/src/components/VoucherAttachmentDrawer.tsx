/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : VoucherAttachmentDrawer.tsx
 * Date : 2026/11/10 17:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

import { useCallback, useEffect, useState } from 'react';
import {
  App,
  Button,
  Drawer,
  Empty,
  Form,
  Input,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  Upload,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { UploadFile } from 'antd/es/upload/interface';
import {
  CloudUploadOutlined,
  DeleteOutlined,
  DownloadOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  VOUCHER_ATTACHMENT_DOC_TYPES,
  deleteVoucherAttachment,
  getVoucherAttachmentDownloadUrl,
  listVoucherAttachments,
  uploadVoucherAttachment,
  type VoucherAttachment,
} from '../api/finance';

/** 单据类型 → 中文与颜色 */
const docTypeConfig: Record<string, { label: string; color: string }> = {
  INVOICE: { label: '发票', color: 'blue' },
  BANK_RECEIPT: { label: '银行回单', color: 'green' },
  CONTRACT: { label: '合同', color: 'purple' },
  PAYROLL: { label: '工资表', color: 'orange' },
  RECEIPT: { label: '收据', color: 'cyan' },
  OTHER: { label: '其他', color: 'default' },
};

/** 文件大小格式化：B / KB / MB */
const formatSize = (size?: number): string => {
  if (size == null || size <= 0) return '-';
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(2)} MB`;
};

/** 抽屉入参 */
export interface VoucherAttachmentDrawerProps {
  /** 是否打开 */
  open: boolean;
  /** 目标凭证 ID（null 表示未选） */
  voucherId?: string | null;
  /** 目标凭证全称（如 记-0001），用于标题 */
  voucherLabel?: string;
  /** 目标凭证状态：已作废凭证只读（不允许再挂新单据） */
  voucherStatus?: string;
  /** 关闭回调 */
  onClose: () => void;
  /** 单据数变化回调（页面据此刷新列表里的「单据」列） */
  onChanged?: () => void;
}

/**
 * 凭证原始单据抽屉（发票 / 银行回单 / 合同 / 工资表）。
 * <p>
 * 原始单据是记账凭证的法定留存物：审计、税务检查、客户对账都要能翻出来。
 * 这里做三件事——上传挂载、预签名直连下载、删除（删记录 + 删存储对象）。
 * </p>
 * <p>
 * 已作废凭证只读：凭证都作废了，再往上挂新单据只会让证据链更混乱，
 * 但仍允许查看与下载已挂单据（历史证据不能因为作废就看不到）。
 * </p>
 *
 * @param props 组件入参
 */
export default function VoucherAttachmentDrawer({
  open,
  voucherId,
  voucherLabel,
  voucherStatus,
  onClose,
  onChanged,
}: VoucherAttachmentDrawerProps) {
  const { message, modal } = App.useApp();
  const [list, setList] = useState<VoucherAttachment[]>([]);
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [form] = Form.useForm<{ docType: string; remark?: string }>();

  /** 已作废凭证只读（可看可下载，不可增删） */
  const readOnly = voucherStatus === 'VOID';

  /** 加载单据列表 */
  const loadList = useCallback(async () => {
    if (!voucherId) {
      setList([]);
      return;
    }
    setLoading(true);
    try {
      setList((await listVoucherAttachments(voucherId)) || []);
    } catch {
      // 拦截器已统一提示
    } finally {
      setLoading(false);
    }
  }, [voucherId]);

  useEffect(() => {
    if (open) {
      void loadList();
      form.setFieldsValue({ docType: 'INVOICE' });
    } else {
      setFileList([]);
      setList([]);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, voucherId]);

  /** 上传挂载 */
  const handleUpload = async () => {
    if (!voucherId) {
      message.warning('请先选择凭证');
      return;
    }
    const file = fileList[0]?.originFileObj;
    if (!file) {
      message.warning('请选择要上传的单据文件');
      return;
    }
    const values = form.getFieldsValue();
    setUploading(true);
    try {
      await uploadVoucherAttachment(voucherId, file, {
        docType: values.docType,
        remark: values.remark,
      });
      message.success('单据已挂载');
      setFileList([]);
      form.setFieldsValue({ remark: undefined });
      await loadList();
      onChanged?.();
    } catch {
      // 拦截器已统一提示
    } finally {
      setUploading(false);
    }
  };

  /** 下载（预签名直连存储） */
  const handleDownload = async (record: VoucherAttachment) => {
    try {
      const result = await getVoucherAttachmentDownloadUrl(record.id);
      if (!result?.url) {
        message.error('未取到下载地址');
        return;
      }
      window.open(result.url, '_blank', 'noopener,noreferrer');
    } catch {
      // 拦截器已统一提示
    }
  };

  /** 删除单据 */
  const handleDelete = (record: VoucherAttachment) => {
    modal.confirm({
      title: '确认删除该原始单据？',
      content: `${record.fileName || '未命名文件'}（记录与存储中的文件一并删除，不可恢复）`,
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteVoucherAttachment(record.id);
          message.success('单据已删除');
          await loadList();
          onChanged?.();
        } catch {
          // 拦截器已统一提示
        }
      },
    });
  };

  /** 单据列 */
  const columns: ColumnsType<VoucherAttachment> = [
    {
      title: '单据类型',
      dataIndex: 'docType',
      width: 110,
      render: (value: string) => {
        const cfg = docTypeConfig[value] ?? { label: value || '其他', color: 'default' };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '文件名',
      dataIndex: 'fileName',
      ellipsis: true,
      render: (value: string) =>
        value || <span style={{ color: '#bbb' }}>—</span>,
    },
    {
      title: '大小',
      dataIndex: 'fileSize',
      width: 100,
      align: 'right',
      render: (value: number) => formatSize(value),
    },
    {
      title: '上传时间',
      dataIndex: 'uploadedAt',
      width: 165,
      render: (value: string) =>
        value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-',
    },
    {
      title: '备注',
      dataIndex: 'remark',
      ellipsis: true,
      render: (value: string) => value || <span style={{ color: '#bbb' }}>—</span>,
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      render: (_, record) => (
        <Space size="small">
          <Tooltip title="从对象存储下载原件">
            <Button
              size="small"
              icon={<DownloadOutlined />}
              onClick={() => void handleDownload(record)}
            >
              下载
            </Button>
          </Tooltip>
          {!readOnly && (
            <Button
              size="small"
              danger
              icon={<DeleteOutlined />}
              onClick={() => handleDelete(record)}
            >
              删除
            </Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Drawer
      title={
        <Space>
          <span>原始单据</span>
          {voucherLabel && (
            <Typography.Text type="secondary" style={{ fontSize: 12, fontWeight: 'normal' }}>
              {voucherLabel}
            </Typography.Text>
          )}
        </Space>
      }
      placement="right"
      width={880}
      open={open}
      onClose={onClose}
      destroyOnClose
    >
      {readOnly && (
        <Typography.Paragraph type="secondary" style={{ marginBottom: 12 }}>
          凭证已作废，单据仅供查看与下载，不可新增或删除。
        </Typography.Paragraph>
      )}

      {!readOnly && (
        <Space
          direction="vertical"
          size="middle"
          style={{ width: '100%', marginBottom: 16 }}
        >
          <Upload.Dragger
            fileList={fileList}
            maxCount={1}
            beforeUpload={() => false}
            onChange={({ fileList: next }) => setFileList(next.slice(-1))}
          >
            <p className="ant-upload-drag-icon" style={{ marginBottom: 4 }}>
              <CloudUploadOutlined />
            </p>
            <p className="ant-upload-text">点击或拖拽单据到此处</p>
            <p className="ant-upload-hint">
              支持图片、PDF、Word、Excel，单份最大 100MB；文件存对象存储，不占数据库
            </p>
          </Upload.Dragger>

          <Form form={form} layout="inline">
            <Form.Item name="docType" label="单据类型" style={{ marginBottom: 8 }}>
              <Select
                style={{ width: 140 }}
                options={VOUCHER_ATTACHMENT_DOC_TYPES}
              />
            </Form.Item>
            <Form.Item name="remark" label="备注" style={{ marginBottom: 8 }}>
              <Input placeholder="如 11月进项发票" maxLength={500} style={{ width: 260 }} />
            </Form.Item>
            <Form.Item style={{ marginBottom: 8 }}>
              <Button
                type="primary"
                loading={uploading}
                disabled={fileList.length === 0}
                onClick={() => void handleUpload()}
              >
                挂载单据
              </Button>
            </Form.Item>
          </Form>
        </Space>
      )}

      <Table<VoucherAttachment>
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={list}
        scroll={{ x: 780 }}
        locale={{
          emptyText: (
            <Empty
              description={
                readOnly
                  ? '该凭证没有挂载原始单据'
                  : '还没有挂载原始单据。上传发票/回单后，审计与对账时可直接调阅'
              }
            />
          ),
        }}
        pagination={false}
        title={() => (
          <Space>
            <span style={{ fontWeight: 600 }}>已挂单据</span>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              共 {list.length} 份
            </Typography.Text>
            <Button
              size="small"
              icon={<ReloadOutlined />}
              onClick={() => void loadList()}
            >
              刷新
            </Button>
          </Space>
        )}
      />
    </Drawer>
  );
}
