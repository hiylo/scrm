/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Contracts.tsx
 * Date : 2026/10/08 12:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  App,
  Button,
  Card,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd';
import {
  PlusOutlined,
  ReloadOutlined,
  CheckCircleOutlined,
  PayCircleOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

const { Text } = Typography;

/** 合同状态映射 */
const statusConfig: Record<string, { color: string; label: string }> = {
  DRAFT: { color: 'default', label: '草稿' },
  PENDING_REVIEW: { color: 'orange', label: '待审批' },
  ACTIVE: { color: 'green', label: '生效中' },
  COMPLETED: { color: 'blue', label: '已完成' },
  TERMINATED: { color: 'red', label: '已终止' },
  REJECTED: { color: 'red', label: '已驳回' },
};

/** 合同类型映射 */
const contractTypeConfig: Record<string, { color: string; label: string }> = {
  SALES: { color: 'blue', label: '销售合同' },
  PURCHASE: { color: 'cyan', label: '采购合同' },
  FRAMEWORK: { color: 'purple', label: '框架合同' },
  OTHER: { color: 'default', label: '其他' },
};

/** 合同实体 */
interface ScrmContract {
  id: string;
  contractNo: string;
  contractName: string;
  contractType: string;
  customerId?: string;
  customerName?: string;
  contractAmount?: number;
  currency?: string;
  startDate?: string;
  endDate?: string;
  status: string;
  totalPaid?: number;
  totalDue?: number;
  createTime?: string;
}

/** 回款记录 */
interface ScrmContractPayment {
  id: string;
  contractId: string;
  paymentNo: string;
  amount?: number;
  paymentDate?: string;
  paymentMethod?: string;
  status?: string;
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
 * 合同管理页
 * <p>
 * 合同 CRUD + 状态流转 (草稿→待审批→生效) + 回款明细抽屉。
 * </p>
 *
 * @author Hsi Chu
 */
export default function Contracts() {
  const { message } = App.useApp();
  /** 列表 */
  const [list, setList] = useState<ScrmContract[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const pageSize = 10;
  /** 弹窗 */
  const [editing, setEditing] = useState<ScrmContract | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  /** 回款抽屉 */
  const [payContract, setPayContract] = useState<ScrmContract | null>(null);
  const [payments, setPayments] = useState<ScrmContractPayment[]>([]);
  const [payLoading, setPayLoading] = useState(false);

  /** 加载列表 */
  const loadList = useCallback(async (targetPage = page) => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(pageSize) });
      const data = await apiClient.get<Page<ScrmContract>>(`/scrm/contracts/list?${params.toString()}`);
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

  /** 打开创建 */
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ contractType: 'SALES' });
    setOpen(true);
  };

  /** 提交 */
  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await apiClient.put(`/scrm/contracts/${editing.id}`, values);
        message.success('合同已更新');
      } else {
        await apiClient.post('/scrm/contracts', values);
        message.success('合同已创建');
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

  /** 提交审批 */
  const handleSubmitReview = async (record: ScrmContract) => {
    try {
      await apiClient.post(`/scrm/contracts/${record.id}/submit`, undefined);
      message.success('合同已提交审批');
      loadList();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 打开回款抽屉 */
  const openPayments = async (record: ScrmContract) => {
    setPayContract(record);
    setPayLoading(true);
    try {
      const data = await apiClient.get<ScrmContractPayment[]>(`/scrm/contract-payments/contract/${record.id}`);
      setPayments(data || []);
    } catch {
      setPayments([]);
    } finally {
      setPayLoading(false);
    }
  };

  /** 表格列 */
  const columns: ColumnsType<ScrmContract> = useMemo(() => [
    { title: '合同编号', dataIndex: 'contractNo', width: 130, render: (v: string) => <Text code>{v}</Text> },
    { title: '合同名称', dataIndex: 'contractName', render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
    {
      title: '类型',
      dataIndex: 'contractType',
      width: 100,
      render: (v: string) => {
        const cfg = contractTypeConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '客户', dataIndex: 'customerName', width: 110, render: (v?: string) => v || '-' },
    { title: '金额', dataIndex: 'contractAmount', width: 110, render: (v?: number) => (v != null ? `¥${v.toLocaleString()}` : '-') },
    { title: '已回款', dataIndex: 'totalPaid', width: 100, render: (v?: number) => (v != null ? `¥${v.toLocaleString()}` : '-') },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => {
        const cfg = statusConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '开始日期', dataIndex: 'startDate', width: 110, render: (v?: string) => v || '-' },
    { title: '结束日期', dataIndex: 'endDate', width: 110, render: (v?: string) => v || '-' },
    {
      title: '操作',
      key: 'actions',
      width: 180,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          {r.status === 'DRAFT' && (
            <Button type="link" size="small" icon={<CheckCircleOutlined />} onClick={() => handleSubmitReview(r)}>
              提交审批
            </Button>
          )}
          <Button type="link" size="small" icon={<PayCircleOutlined />} onClick={() => openPayments(r)}>
            回款
          </Button>
        </Space>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  return (
    <div className="contracts-page">
      <Card
        title="合同管理"
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => loadList()} />
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              新建合同
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
        />
      </Card>

      {/* 合同弹窗 */}
      <Modal
        title={editing ? '编辑合同' : '新建合同'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={680}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="contractName" label="合同名称" rules={[{ required: true, message: '请输入合同名称' }]}>
            <Input placeholder="如: 2026 年度框架合同" maxLength={200} />
          </Form.Item>
          <Form.Item name="customerId" label="客户 ID" rules={[{ required: true, message: '请输入客户 ID' }]}>
            <Input placeholder="客户 ID" />
          </Form.Item>
          <Form.Item name="customerName" label="客户名称">
            <Input placeholder="客户名称" />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="contractType" label="合同类型" rules={[{ required: true }]}>
              <Select style={{ width: 160 }} options={Object.entries(contractTypeConfig).map(([v, c]) => ({ value: v, label: c.label }))} />
            </Form.Item>
            <Form.Item name="contractAmount" label="合同金额 (¥)" rules={[{ required: true }]}>
              <InputNumber min={0} style={{ width: 160 }} />
            </Form.Item>
          </Space>
          <Space size={16} wrap>
            <Form.Item name="startDate" label="开始日期">
              <Input placeholder="如 2026-01-01" style={{ width: 160 }} />
            </Form.Item>
            <Form.Item name="endDate" label="结束日期">
              <Input placeholder="如 2026-12-31" style={{ width: 160 }} />
            </Form.Item>
          </Space>
          <Form.Item name="signUserId" label="签署人 ID">
            <Input placeholder="签署人用户 ID" />
          </Form.Item>
          <Form.Item name="paymentTerms" label="付款条款">
            <Input.TextArea rows={2} placeholder="如: 30% 预付, 70% 验收后" />
          </Form.Item>
          <Form.Item name="description" label="备注">
            <Input.TextArea rows={2} placeholder="合同说明" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 回款抽屉 */}
      <Drawer
        title={payContract ? `回款记录 - ${payContract.contractName}` : '回款记录'}
        open={!!payContract}
        onClose={() => setPayContract(null)}
        width={560}
      >
        {payContract && (
          <div style={{ display: 'flex', gap: 16, marginBottom: 16 }}>
            <Statistic title="合同金额" value={payContract.contractAmount ?? 0} prefix="¥" />
            <Statistic title="累计回款" value={payContract.totalPaid ?? 0} prefix="¥" valueStyle={{ color: '#10b981' }} />
            <Statistic title="待收款" value={payContract.totalDue ?? 0} prefix="¥" valueStyle={{ color: '#f59e0b' }} />
          </div>
        )}
        <Table
          rowKey="id"
          size="small"
          dataSource={payments}
          loading={payLoading}
          pagination={false}
          locale={{ emptyText: <Empty description="暂无回款记录" /> }}
          columns={[
            { title: '回款单号', dataIndex: 'paymentNo', width: 120, render: (v?: string) => (v ? <Text code>{v}</Text> : '-') },
            { title: '金额', dataIndex: 'amount', width: 100, render: (v?: number) => (v != null ? `¥${v.toLocaleString()}` : '-') },
            { title: '方式', dataIndex: 'paymentMethod', width: 100, render: (v?: string) => v || '-' },
            { title: '状态', dataIndex: 'status', width: 80, render: (v?: string) => v || '-' },
            { title: '回款时间', dataIndex: 'paymentDate', width: 130, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD') : '-') },
          ]}
        />
      </Drawer>
    </div>
  );
}
