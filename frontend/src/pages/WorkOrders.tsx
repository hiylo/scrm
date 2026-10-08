/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : WorkOrders.tsx
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
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  ReloadOutlined,
  SearchOutlined,
  UserOutlined,
  CheckCircleOutlined,
  StopOutlined,
  EyeOutlined,
  ArrowUpOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

const { Text } = Typography;

/** 工单实体 */
interface ScrmWorkOrder {
  id: string;
  orderNo: string;
  title: string;
  description?: string;
  orderType: string;
  orderCategory?: string;
  priority: string; // URGENT / HIGH / MEDIUM / LOW
  status: string; // PENDING / ASSIGNED / ACCEPTED / IN_PROGRESS / RESOLVED / CLOSED / CANCELLED / REOPENED
  customerId?: string;
  customerName?: string;
  assigneeId?: string;
  assigneeName?: string;
  slaDueAt?: string;
  slaStatus?: string;
  createTime?: string;
}

/** 工单日志 */
interface ScrmWorkOrderLog {
  id: string;
  orderId: string;
  action: string;
  operatorName?: string;
  detail?: string;
  createdAt?: string;
}

/** Spring Page 分页响应 */
interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/** 状态映射 */
const statusConfig: Record<string, { color: string; label: string }> = {
  PENDING: { color: 'orange', label: '待分配' },
  ASSIGNED: { color: 'blue', label: '已分配' },
  ACCEPTED: { color: 'cyan', label: '已接单' },
  IN_PROGRESS: { color: 'processing', label: '处理中' },
  RESOLVED: { color: 'green', label: '已解决' },
  CLOSED: { color: 'default', label: '已关闭' },
  CANCELLED: { color: 'red', label: '已取消' },
  REOPENED: { color: 'volcano', label: '已重开' },
};

/** 优先级映射 */
const priorityConfig: Record<string, { color: string; label: string }> = {
  URGENT: { color: 'red', label: '紧急' },
  HIGH: { color: 'orange', label: '高' },
  MEDIUM: { color: 'blue', label: '中' },
  LOW: { color: 'default', label: '低' },
};

/**
 * 工单管理页
 * <p>
 * 内部工单执行体系: 工单 CRUD + 状态机 (分配→接单→开始→解决→关闭, 可重开/取消/升级)
 * + SLA 状态 + 紧急/逾期视图 + 明细抽屉 (操作日志)。
 * </p>
 *
 * @author Hsi Chu
 */
export default function WorkOrders() {
  const { message } = App.useApp();
  /** 列表 */
  const [list, setList] = useState<ScrmWorkOrder[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const pageSize = 10;
  const [keyword, setKeyword] = useState('');
  const [statusFilter, setStatusFilter] = useState<string | undefined>();
  /** 弹窗 */
  const [editing, setEditing] = useState<ScrmWorkOrder | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  /** 分配弹窗 */
  const [assignTarget, setAssignTarget] = useState<ScrmWorkOrder | null>(null);
  const [assignForm] = Form.useForm();
  const [assigning, setAssigning] = useState(false);
  /** 明细抽屉 */
  const [detail, setDetail] = useState<ScrmWorkOrder | null>(null);
  const [logs, setLogs] = useState<ScrmWorkOrderLog[]>([]);
  const [logLoading, setLogLoading] = useState(false);

  /** 加载列表 */
  const loadList = useCallback(async (targetPage = page, k = keyword) => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(pageSize) });
      if (k) params.set('keyword', k);
      if (statusFilter) params.set('status', statusFilter);
      const data = await apiClient.get<Page<ScrmWorkOrder>>(`/scrm/work-orders/orders/list?${params.toString()}`);
      setList(data.content || []);
      setTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, statusFilter]);

  useEffect(() => {
    loadList();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, statusFilter]);

  /** 打开创建 */
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ orderType: 'SERVICE', priority: 'MEDIUM' });
    setOpen(true);
  };

  /** 打开编辑 */
  const openEdit = (record: ScrmWorkOrder) => {
    setEditing(record);
    form.setFieldsValue(record);
    setOpen(true);
  };

  /** 提交 */
  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await apiClient.put(`/scrm/work-orders/orders/${editing.id}`, values);
        message.success('工单已更新');
      } else {
        await apiClient.post('/scrm/work-orders/orders', values);
        message.success('工单已创建');
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

  /** 删除 */
  const handleDelete = async (record: ScrmWorkOrder) => {
    try {
      await apiClient.delete(`/scrm/work-orders/orders/${record.id}`);
      message.success('工单已删除');
      loadList();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 状态流转 (多端点) */
  const changeStatus = async (record: ScrmWorkOrder, action: string) => {
    const labelMap: Record<string, string> = {
      accept: '接单', start: '开始处理', resolve: '解决', close: '关闭',
      cancel: '取消', reopen: '重开', escalate: '升级',
    };
    try {
      await apiClient.post(`/scrm/work-orders/orders/${record.id}/${action}`, {});
      message.success(`工单已${labelMap[action] || action}`);
      loadList();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 分配 */
  const handleAssign = async () => {
    if (!assignTarget) return;
    const values = await assignForm.validateFields();
    setAssigning(true);
    try {
      await apiClient.post('/scrm/work-orders/orders/assign', { orderId: Number(assignTarget.id), assigneeId: values.assigneeId });
      message.success('工单已分配');
      setAssignTarget(null);
      loadList();
    } catch {
      // 拦截器已弹出错误
    } finally {
      setAssigning(false);
    }
  };

  /** 打开明细 */
  const openDetail = async (record: ScrmWorkOrder) => {
    setDetail(record);
    setLogLoading(true);
    try {
      const data = await apiClient.get<ScrmWorkOrderLog[]>(`/scrm/work-orders/orders/${record.id}/logs`);
      setLogs(data || []);
    } catch {
      setLogs([]);
    } finally {
      setLogLoading(false);
    }
  };

  /** 表格列 */
  const columns: ColumnsType<ScrmWorkOrder> = useMemo(() => [
    { title: '单号', dataIndex: 'orderNo', width: 120, render: (v: string) => <Text code>{v}</Text> },
    {
      title: '标题',
      dataIndex: 'title',
      render: (v: string, r) => (
        <Space>
          <span style={{ fontWeight: 600 }}>{v}</span>
          {r.priority === 'URGENT' && <Tag color="red">紧急</Tag>}
        </Space>
      ),
    },
    { title: '类型', dataIndex: 'orderType', width: 100, render: (v?: string) => v || '-' },
    {
      title: '优先级',
      dataIndex: 'priority',
      width: 90,
      render: (v: string) => {
        const cfg = priorityConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => {
        const cfg = statusConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '处理人', dataIndex: 'assigneeName', width: 100, render: (v?: string) => v || '-' },
    { title: '创建时间', dataIndex: 'createTime', width: 130, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
    {
      title: '操作',
      key: 'actions',
      width: 280,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => openDetail(r)}>
            明细
          </Button>
          {r.status === 'PENDING' && (
            <Button type="link" size="small" icon={<UserOutlined />} onClick={() => { setAssignTarget(r); assignForm.resetFields(); }}>
              分配
            </Button>
          )}
          {r.status === 'ASSIGNED' && (
            <Button type="link" size="small" onClick={() => changeStatus(r, 'accept')}>
              接单
            </Button>
          )}
          {r.status === 'ACCEPTED' && (
            <Button type="link" size="small" onClick={() => changeStatus(r, 'start')}>
              开始
            </Button>
          )}
          {r.status === 'IN_PROGRESS' && (
            <Button type="link" size="small" icon={<CheckCircleOutlined />} onClick={() => changeStatus(r, 'resolve')}>
              解决
            </Button>
          )}
          {(r.status === 'RESOLVED' || r.status === 'REOPENED') && (
            <Button type="link" size="small" onClick={() => changeStatus(r, 'close')}>
              关闭
            </Button>
          )}
          {r.status === 'CLOSED' && (
            <Button type="link" size="small" onClick={() => changeStatus(r, 'reopen')}>
              重开
            </Button>
          )}
          {(r.status === 'PENDING' || r.status === 'ASSIGNED' || r.status === 'ACCEPTED') && (
            <Button type="link" size="small" danger icon={<StopOutlined />} onClick={() => changeStatus(r, 'cancel')}>
              取消
            </Button>
          )}
          {r.priority !== 'URGENT' && r.status === 'IN_PROGRESS' && (
            <Button type="link" size="small" icon={<ArrowUpOutlined />} onClick={() => changeStatus(r, 'escalate')}>
              升级
            </Button>
          )}
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(r)}>
            编辑
          </Button>
          <Popconfirm title="确定删除该工单?" onConfirm={() => handleDelete(r)}>
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  return (
    <div className="work-orders-page">
      <Card
        title="工单管理"
        extra={
          <Space>
            <Input
              placeholder="搜索单号/标题"
              prefix={<SearchOutlined />}
              allowClear
              style={{ width: 200 }}
              onChange={e => {
                setKeyword(e.target.value);
                setPage(0);
              }}
            />
            <Select
              placeholder="状态"
              allowClear
              style={{ width: 110 }}
              options={Object.entries(statusConfig).map(([v, c]) => ({ value: v, label: c.label }))}
              onChange={v => { setStatusFilter(v); setPage(0); }}
            />
            <Button icon={<ReloadOutlined />} onClick={() => loadList()} />
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              新建工单
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

      {/* 工单弹窗 */}
      <Modal
        title={editing ? '编辑工单' : '新建工单'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={560}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="title" label="工单标题" rules={[{ required: true, message: '请输入工单标题' }]}>
            <Input placeholder="如: 客户系统故障处理" maxLength={200} />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="orderType" label="工单类型" rules={[{ required: true }]}>
              <Input placeholder="如: SERVICE" style={{ width: 150 }} />
            </Form.Item>
            <Form.Item name="priority" label="优先级" rules={[{ required: true }]}>
              <Select style={{ width: 120 }} options={Object.entries(priorityConfig).map(([v, c]) => ({ value: v, label: c.label }))} />
            </Form.Item>
          </Space>
          <Form.Item name="customerId" label="客户 ID">
            <Input placeholder="客户 ID" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} placeholder="工单描述" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 分配弹窗 */}
      <Modal
        title="分配工单"
        open={!!assignTarget}
        onCancel={() => setAssignTarget(null)}
        onOk={handleAssign}
        confirmLoading={assigning}
      >
        <Form form={assignForm} layout="vertical">
          <Form.Item name="assigneeId" label="处理人用户 ID" rules={[{ required: true, message: '请输入处理人用户 ID' }]}>
            <Input placeholder="处理人用户 ID" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 明细抽屉 */}
      <Drawer
        title={detail ? `工单明细 - ${detail.orderNo}` : '工单明细'}
        open={!!detail}
        onClose={() => setDetail(null)}
        width={560}
      >
        {detail && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <Text strong style={{ fontSize: 16 }}>{detail.title}</Text>
            <Text type="secondary">{detail.description || '无描述'}</Text>
            <Table
              rowKey="id"
              size="small"
              dataSource={logs}
              loading={logLoading}
              pagination={false}
              locale={{ emptyText: <Empty description="暂无操作日志" /> }}
              columns={[
                { title: '动作', dataIndex: 'action', width: 110 },
                { title: '操作人', dataIndex: 'operatorName', width: 100, render: (v?: string) => v || '-' },
                { title: '详情', dataIndex: 'detail', ellipsis: true, render: (v?: string) => v || '-' },
                { title: '时间', dataIndex: 'createdAt', width: 140, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
              ]}
            />
          </div>
        )}
      </Drawer>
    </div>
  );
}