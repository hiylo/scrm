/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Budgets.tsx
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
  Modal,
  Progress,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import {
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  EditOutlined,
  SwapOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

const { Text } = Typography;

/** 计划状态映射 */
const planStatusConfig: Record<string, { color: string; label: string }> = {
  DRAFT: { color: 'default', label: '草稿' },
  PENDING_APPROVAL: { color: 'orange', label: '待审批' },
  APPROVED: { color: 'green', label: '已通过' },
  REJECTED: { color: 'red', label: '已驳回' },
  EXECUTING: { color: 'processing', label: '执行中' },
  CLOSED: { color: 'default', label: '已结束' },
};

/** 分配状态映射 */
const allocStatusConfig: Record<string, { color: string; label: string }> = {
  ACTIVE: { color: 'green', label: '生效' },
  FROZEN: { color: 'orange', label: '冻结' },
  CLOSED: { color: 'default', label: '已关闭' },
};

/** 预算计划实体 */
interface ScrmBudgetPlan {
  id: string;
  planName: string;
  planCode: string;
  budgetPeriod?: string;
  totalBudget?: number;
  usedBudget?: number;
  remainingBudget?: number;
  status?: string;
  alertThreshold?: number;
  description?: string;
  createTime?: string;
}

/** 预算分配实体 */
interface ScrmBudgetAllocation {
  id: string;
  planId?: string;
  planName?: string;
  channel?: string;
  campaignId?: string;
  allocatedAmount?: number;
  usedAmount?: number;
  status?: string;
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
 * 预算管理页面
 * 双 Tab: 预算计划 / 预算分配; 支持计划 CRUD、审批、预算转移与分配抽屉。
 *
 * @author Hsi Chu
 */
export default function Budgets() {
  const { message } = App.useApp();
  /** 计划 */
  const [plans, setPlans] = useState<ScrmBudgetPlan[]>([]);
  const [plansLoading, setPlansLoading] = useState(false);
  const [planPage, setPlanPage] = useState(0);
  const planPageSize = 10;
  const [planTotal, setPlanTotal] = useState(0);
  /** 分配 */
  const [allocations, setAllocations] = useState<ScrmBudgetAllocation[]>([]);
  const [allocLoading, setAllocLoading] = useState(false);
  const [allocPage, setAllocPage] = useState(0);
  const allocPageSize = 10;
  const [allocTotal, setAllocTotal] = useState(0);
  /** 计划弹窗 */
  const [editing, setEditing] = useState<ScrmBudgetPlan | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  /** 转移弹窗 */
  const [transferOpen, setTransferOpen] = useState(false);
  const [transferSaving, setTransferSaving] = useState(false);
  const [transferForm] = Form.useForm();
  /** 分配抽屉 */
  const [allocPlan, setAllocPlan] = useState<ScrmBudgetPlan | null>(null);
  const [planAllocs, setPlanAllocs] = useState<ScrmBudgetAllocation[]>([]);
  const [planAllocLoading, setPlanAllocLoading] = useState(false);

  /** 加载预算计划 */
  const loadPlans = useCallback(async (targetPage = planPage) => {
    setPlansLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(planPageSize) });
      const data = await apiClient.get<Page<ScrmBudgetPlan>>(`/scrm/budgets/plans/list?${params.toString()}`);
      setPlans(data.content || []);
      setPlanTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setPlansLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [planPage]);

  /** 加载预算分配 */
  const loadAllocations = useCallback(async (targetPage = allocPage) => {
    setAllocLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(allocPageSize) });
      const data = await apiClient.get<Page<ScrmBudgetAllocation>>(`/scrm/budgets/allocations/list?${params.toString()}`);
      setAllocations(data.content || []);
      setAllocTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setAllocLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [allocPage]);

  useEffect(() => {
    loadPlans();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [planPage]);

  useEffect(() => {
    loadAllocations();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [allocPage]);

  /** 打开新建计划弹窗 */
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ alertThreshold: 80, totalBudget: 0 });
    setOpen(true);
  };

  /** 打开编辑计划弹窗 */
  const openEdit = (record: ScrmBudgetPlan) => {
    setEditing(record);
    form.setFieldsValue(record);
    setOpen(true);
  };

  /** 提交新建/编辑 */
  const handleSubmit = async () => {
    let values: Record<string, unknown>;
    try {
      values = await form.validateFields();
    } catch {
      return;
    }
    setSaving(true);
    try {
      if (editing) {
        await apiClient.put(`/scrm/budgets/plans/${editing.id}`, values);
        message.success('预算计划已更新');
      } else {
        await apiClient.post('/scrm/budgets/plans', values);
        message.success('预算计划已创建');
      }
      setOpen(false);
      setPlanPage(0);
      loadPlans(0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setSaving(false);
    }
  };

  /** 提交审批 (草稿 -> 待审批) */
  const handleApprove = async (record: ScrmBudgetPlan) => {
    try {
      await apiClient.post(`/scrm/budgets/plans/${record.id}/approve`, {});
      message.success('已提交审批');
      loadPlans();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 提交预算转移 */
  const handleTransfer = async () => {
    let values: Record<string, unknown>;
    try {
      values = await transferForm.validateFields();
    } catch {
      return;
    }
    setTransferSaving(true);
    try {
      await apiClient.post('/scrm/budgets/transfer', values);
      message.success('预算转移成功');
      setTransferOpen(false);
      loadAllocations();
      loadPlans();
    } catch {
      // 拦截器已弹出错误
    } finally {
      setTransferSaving(false);
    }
  };

  /** 查看计划下的分配明细 */
  const openPlanAllocs = async (record: ScrmBudgetPlan) => {
    setAllocPlan(record);
    setPlanAllocs([]);
    setPlanAllocLoading(true);
    try {
      const rows = await apiClient.get<ScrmBudgetAllocation[]>(`/scrm/budgets/plans/${record.id}/allocations`);
      setPlanAllocs(rows || []);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setPlanAllocLoading(false);
    }
  };

  /** 计划表格列 */
  const planColumns: ColumnsType<ScrmBudgetPlan> = useMemo(() => [
    { title: '计划名称', dataIndex: 'planName', width: 180, render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
    { title: '计划编码', dataIndex: 'planCode', width: 140, render: (v: string) => <Text code>{v}</Text> },
    { title: '周期', dataIndex: 'budgetPeriod', width: 100, render: (v?: string) => v || '-' },
    { title: '总预算', dataIndex: 'totalBudget', width: 120, render: (v?: number) => (v != null ? `¥${v.toLocaleString()}` : '-') },
    { title: '已用', dataIndex: 'usedBudget', width: 110, render: (v?: number) => (v != null ? `¥${v.toLocaleString()}` : '-') },
    { title: '剩余', dataIndex: 'remainingBudget', width: 110, render: (v?: number) => (v != null ? `¥${v.toLocaleString()}` : '-') },
    {
      title: '使用率',
      key: 'usage',
      width: 180,
      render: (_, r) => {
        const percent = r.totalBudget ? Math.round(((r.usedBudget || 0) / r.totalBudget) * 100) : 0;
        return (
          <Progress
            percent={percent}
            size="small"
            strokeColor={percent >= (r.alertThreshold || 80) ? '#fa541c' : '#52c41a'}
          />
        );
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v?: string) => {
        const cfg = planStatusConfig[v || ''] || { color: 'default', label: v || '-' };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '操作',
      key: 'actions',
      width: 180,
      fixed: 'right',
      render: (_, r) => (
        <Space size={0}>
          {r.status === 'DRAFT' && (
            <Button type="link" size="small" onClick={() => handleApprove(r)}>
              审批
            </Button>
          )}
          <Button type="link" size="small" onClick={() => openPlanAllocs(r)}>
            分配
          </Button>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(r)}>
            编辑
          </Button>
        </Space>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  /** 分配表格列 */
  const allocColumns: ColumnsType<ScrmBudgetAllocation> = useMemo(() => [
    { title: '渠道', dataIndex: 'channel', width: 120, render: (v?: string) => (v ? <Tag color="blue">{v}</Tag> : '-') },
    { title: '活动', dataIndex: 'campaignId', width: 110, render: (v?: string) => v || '-' },
    { title: '分配金额', dataIndex: 'allocatedAmount', width: 120, render: (v?: number) => (v != null ? `¥${v.toLocaleString()}` : '-') },
    { title: '已用金额', dataIndex: 'usedAmount', width: 110, render: (v?: number) => (v != null ? `¥${v.toLocaleString()}` : '-') },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (v?: string) => {
        const cfg = allocStatusConfig[v || ''] || { color: 'default', label: v || '-' };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '创建时间', dataIndex: 'createTime', width: 140, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  return (
    <div className="budgets-page">
      <Tabs
        items={[
          {
            key: 'plans',
            label: '预算计划',
            children: (
              <Card
                title="预算计划"
                extra={
                  <Space>
                    <Button icon={<ReloadOutlined />} onClick={() => loadPlans()} />
                    <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                      新建计划
                    </Button>
                  </Space>
                }
              >
                <Table
                  rowKey="id"
                  columns={planColumns}
                  dataSource={plans}
                  loading={plansLoading}
                  pagination={{
                    current: planPage + 1,
                    pageSize: planPageSize,
                    total: planTotal,
                    showTotal: t => `共 ${t} 条`,
                    onChange: p => setPlanPage(p - 1),
                  }} scroll={{ y: 'calc(100vh - 320px)' }}
                />
              </Card>
            ),
          },
          {
            key: 'allocations',
            label: '预算分配',
            children: (
              <Card
                title="预算分配"
                extra={
                  <Space>
                    <Button icon={<ReloadOutlined />} onClick={() => loadAllocations()} />
                    <Button type="primary" icon={<SearchOutlined />} onClick={() => { transferForm.resetFields(); setTransferOpen(true); }}>
                      预算转移
                    </Button>
                  </Space>
                }
              >
                <Table
                  rowKey="id"
                  columns={allocColumns}
                  dataSource={allocations}
                  loading={allocLoading}
                  pagination={{
                    current: allocPage + 1,
                    pageSize: allocPageSize,
                    total: allocTotal,
                    showTotal: t => `共 ${t} 条`,
                    onChange: p => setAllocPage(p - 1),
                  }}
                  locale={{ emptyText: <Empty description="暂无预算分配" /> }}
                />
              </Card>
            ),
          },
        ]}
      />

      {/* 计划弹窗 */}
      <Modal
        title={editing ? '编辑预算计划' : '新建预算计划'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={560}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="planName" label="计划名称" rules={[{ required: true, message: '请输入计划名称' }]}>
            <Input placeholder="如: 2026 年度营销预算" maxLength={200} />
          </Form.Item>
          <Form.Item name="planCode" label="计划编码" rules={[{ required: true, message: '请输入计划编码' }]}>
            <Input placeholder="如: BUDGET_2026" maxLength={50} />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="budgetPeriod" label="周期">
              <Input placeholder="如: FY2026" style={{ width: 140 }} />
            </Form.Item>
            <Form.Item name="totalBudget" label="总预算 (¥)" rules={[{ required: true }]}>
              <Input type="number" style={{ width: 160 }} />
            </Form.Item>
            <Form.Item name="alertThreshold" label="预警阈值 (%)">
              <Input type="number" style={{ width: 110 }} />
            </Form.Item>
          </Space>
          <Form.Item name="description" label="说明">
            <Input.TextArea rows={2} placeholder="预算说明" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 转移弹窗 */}
      <Modal
        title="预算转移"
        open={transferOpen}
        onCancel={() => setTransferOpen(false)}
        onOk={handleTransfer}
        confirmLoading={transferSaving}
      >
        <Form form={transferForm} layout="vertical">
          <Form.Item name="fromId" label="来源分配 ID" rules={[{ required: true, message: '请输入来源分配 ID' }]}>
            <Input placeholder="来源分配 ID" />
          </Form.Item>
          <Form.Item name="toId" label="目标分配 ID" rules={[{ required: true, message: '请输入目标分配 ID' }]}>
            <Input placeholder="目标分配 ID" />
          </Form.Item>
          <Form.Item name="amount" label="转移金额 (¥)" rules={[{ required: true, message: '请输入转移金额' }]}>
            <Input type="number" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 分配抽屉 */}
      <Drawer
        title={allocPlan ? `预算分配 - ${allocPlan.planName}` : '预算分配'}
        open={!!allocPlan}
        onClose={() => setAllocPlan(null)}
        width={560}
      >
        <Table
          rowKey="id"
          size="small"
          dataSource={planAllocs}
          loading={planAllocLoading}
          pagination={false}
          locale={{ emptyText: <Empty description="暂无分配记录" /> }}
          columns={allocColumns}
        />
      </Drawer>
    </div>
  );
}