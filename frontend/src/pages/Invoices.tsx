/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Invoices.tsx
 * Date : 2026/10/08 12:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  App,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tag,
} from 'antd';
import {
  PlusOutlined,
  ReloadOutlined,
  EditOutlined,
  FileDoneOutlined,
  StopOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

/** 发票类型映射 */
const invoiceTypeConfig: Record<string, { color: string; label: string }> = {
  GENERAL: { color: 'blue', label: '普通发票' },
  SPECIAL: { color: 'purple', label: '专用发票' },
  ELECTRONIC: { color: 'cyan', label: '电子发票' },
};

/** 发票状态映射 */
const statusConfig: Record<string, { color: string; label: string }> = {
  PENDING: { color: 'orange', label: '待开具' },
  ISSUED: { color: 'green', label: '已开具' },
  VOIDED: { color: 'red', label: '已作废' },
  RED_FLUSHED: { color: 'default', label: '已红冲' },
};

/** 发票行 */
interface ScrmInvoice {
  id: string;
  invoiceNo: string;
  invoiceType: string;
  customerId?: string;
  customerName?: string;
  invoiceTitle?: string;
  amount?: number;
  taxAmount?: number;
  totalAmount?: number;
  taxNumber?: string;
  status: string;
  createTime?: string;
}

/** Spring Page 分页响应 */
interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/**
 * 发票管理页面
 * <p>
 * 发票列表 + 申请开票 (新建) + 开具 + 作废 + 编辑全流程。
 * </p>
 *
 * @author Hsi Chu
 */
export default function Invoices() {
  const { message } = App.useApp();
  /** 列表 */
  const [list, setList] = useState<ScrmInvoice[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const pageSize = 10;
  /** 弹窗 */
  const [editing, setEditing] = useState<ScrmInvoice | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  /** 加载列表 */
  const loadList = useCallback(async (targetPage = page) => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(pageSize) });
      const data = await apiClient.get<Page<ScrmInvoice>>(`/scrm/invoices/list?${params.toString()}`);
      setList(data.content || []);
      setTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page]);

  useEffect(() => {
    loadList();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page]);

  /** 打开新建弹窗 */
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ invoiceType: 'GENERAL' });
    setOpen(true);
  };

  /** 打开编辑弹窗 */
  const openEdit = (record: ScrmInvoice) => {
    setEditing(record);
    form.setFieldsValue({
      customerId: record.customerId,
      customerName: record.customerName,
      invoiceTitle: record.invoiceTitle,
      invoiceType: record.invoiceType,
      amount: record.amount,
      taxAmount: record.taxAmount,
      taxNumber: record.taxNumber,
    });
    setOpen(true);
  };

  /** 提交新建/编辑 */
  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await apiClient.put(`/scrm/invoices/${editing.id}`, values);
        message.success('发票已更新');
      } else {
        await apiClient.post('/scrm/invoices/apply', values);
        message.success('开票申请已提交');
      }
      setOpen(false);
      setPage(0);
      loadList(0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setSaving(false);
    }
  };

  /** 开具发票 */
  const handleIssue = async (record: ScrmInvoice) => {
    try {
      await apiClient.post(`/scrm/invoices/${record.id}/issue`);
      message.success('发票已开具');
      loadList();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 作废发票 */
  const handleVoid = async (record: ScrmInvoice) => {
    try {
      await apiClient.post(`/scrm/invoices/void?id=${record.id}`, {});
      message.success('发票已作废');
      loadList();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 表格列 */
  const columns: ColumnsType<ScrmInvoice> = useMemo(() => [
    { title: '发票号', dataIndex: 'invoiceNo', width: 140, render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
    {
      title: '类型',
      dataIndex: 'invoiceType',
      width: 110,
      render: (v: string) => {
        const cfg = invoiceTypeConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '客户', dataIndex: 'customerName', width: 120, render: (v?: string) => v || '-' },
    { title: '发票抬头', dataIndex: 'invoiceTitle', ellipsis: true, render: (v?: string) => v || '-' },
    { title: '不含税金额', dataIndex: 'amount', width: 100, render: (v?: number) => (v != null ? `¥${v.toLocaleString()}` : '-') },
    { title: '税额', dataIndex: 'taxAmount', width: 90, render: (v?: number) => (v != null ? `¥${v.toLocaleString()}` : '-') },
    { title: '价税合计', dataIndex: 'totalAmount', width: 100, render: (v?: number) => (v != null ? `¥${v.toLocaleString()}` : '-') },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => {
        const cfg = statusConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '创建时间', dataIndex: 'createTime', width: 140, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
    {
      title: '操作',
      key: 'actions',
      width: 160,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          {r.status === 'PENDING' && (
            <Button type="link" size="small" icon={<FileDoneOutlined />} onClick={() => handleIssue(r)}>
              开具
            </Button>
          )}
          {r.status === 'ISSUED' && (
            <Button type="link" size="small" danger icon={<StopOutlined />} onClick={() => handleVoid(r)}>
              作废
            </Button>
          )}
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(r)}>
            编辑
          </Button>
        </Space>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  return (
    <div className="invoices-page">
      <Card
        title="发票管理"
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => loadList()} />
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              新建发票
            </Button>
          </Space>
        }
      >
        <Table
          rowKey="id"
          columns={columns}
          dataSource={list}
          loading={loading}
          pagination={{
            current: page + 1,
            pageSize,
            total,
            showTotal: t => `共 ${t} 条`,
            onChange: p => setPage(p - 1),
          }}
          scroll={{ y: 'calc(100vh - 320px)' }}
        />
      </Card>

      {/* 发票弹窗 */}
      <Modal
        title={editing ? '编辑发票' : '新建发票'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={560}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="customerId" label="客户 ID" rules={[{ required: true, message: '请输入客户 ID' }]}>
            <Input placeholder="客户 ID" />
          </Form.Item>
          <Form.Item name="customerName" label="客户名称">
            <Input placeholder="客户名称" />
          </Form.Item>
          <Form.Item name="invoiceTitle" label="发票抬头" rules={[{ required: true, message: '请输入发票抬头' }]}>
            <Input placeholder="如: 某某科技有限公司" maxLength={200} />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="invoiceType" label="发票类型" rules={[{ required: true }]}>
              <Select style={{ width: 160 }} options={Object.entries(invoiceTypeConfig).map(([v, c]) => ({ value: v, label: c.label }))} />
            </Form.Item>
            <Form.Item name="amount" label="不含税金额 (¥)" rules={[{ required: true }]}>
              <InputNumber min={0} style={{ width: 150 }} />
            </Form.Item>
            <Form.Item name="taxAmount" label="税额 (¥)">
              <InputNumber min={0} style={{ width: 120 }} />
            </Form.Item>
          </Space>
          <Form.Item name="taxNumber" label="纳税人识别号">
            <Input placeholder="纳税人识别号" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}