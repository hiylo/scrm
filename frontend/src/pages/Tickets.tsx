/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Tickets.tsx
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
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import {
  PlusOutlined,
  ReloadOutlined,
  DeleteOutlined,
  PlayCircleOutlined,
  EyeOutlined,
  UserSwitchOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

const { Text } = Typography;

/** 工单类别映射 */
const categoryConfig: Record<string, { color: string; label: string }> = {
  PRODUCT_ISSUE: { color: 'volcano', label: '产品问题' },
  ORDER_ISSUE: { color: 'orange', label: '订单问题' },
  LOGISTICS_ISSUE: { color: 'gold', label: '物流问题' },
  SERVICE_ISSUE: { color: 'blue', label: '服务问题' },
  REFUND: { color: 'purple', label: '退款' },
  COMPLAINT: { color: 'red', label: '投诉' },
  OTHER: { color: 'default', label: '其他' },
};

/** 工单优先级映射 */
const priorityConfig: Record<string, { color: string; label: string }> = {
  LOW: { color: 'default', label: '低' },
  MEDIUM: { color: 'blue', label: '中' },
  HIGH: { color: 'orange', label: '高' },
  URGENT: { color: 'red', label: '紧急' },
};

/** 工单状态映射 */
const statusConfig: Record<string, { color: string; label: string }> = {
  OPEN: { color: 'orange', label: '待处理' },
  IN_PROGRESS: { color: 'processing', label: '处理中' },
  PENDING_CUSTOMER: { color: 'gold', label: '待客户反馈' },
  RESOLVED: { color: 'green', label: '已解决' },
  CLOSED: { color: 'default', label: '已关闭' },
};

/** 工单来源映射 */
const sourceConfig: Record<string, { color: string; label: string }> = {
  CUSTOMER: { color: 'cyan', label: '客户提交' },
  STAFF: { color: 'blue', label: '员工创建' },
  SYSTEM: { color: 'geekblue', label: '系统自动' },
  PHONE: { color: 'purple', label: '电话' },
};

/** 客户工单实体 */
interface ScrmTicket {
  id: string;
  ticketNo?: string;
  title: string;
  customerId?: string;
  customerName?: string;
  category?: string;
  priority?: string;
  status?: string;
  source?: string;
  assigneeId?: string;
  assigneeName?: string;
  description?: string;
  tags?: string;
  createTime?: string;
  updateTime?: string;
}

/** 工单评论 */
interface ScrmTicketComment {
  id: string;
  content: string;
  authorName?: string;
  isInternal?: boolean;
  createdAt?: string;
}

/** 工单流转历史 */
interface ScrmTicketHistory {
  id: string;
  action: string;
  fromValue?: string;
  toValue?: string;
  operatorName?: string;
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

/**
 * 客户工单页面
 * 列表查询 / 创建 / 分配 / 状态流转 / 详情抽屉 (评论 + 流转历史)
 *
 * @author Hsi Chu
 */
export default function Tickets() {
  const { message } = App.useApp();
  /** 列表 */
  const [list, setList] = useState<ScrmTicket[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const pageSize = 10;
  const [total, setTotal] = useState(0);
  /** 创建/编辑弹窗 */
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<ScrmTicket | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  /** 分配弹窗 */
  const [assignTarget, setAssignTarget] = useState<ScrmTicket | null>(null);
  const [assigning, setAssigning] = useState(false);
  const [assignForm] = Form.useForm();
  /** 详情抽屉 */
  const [detail, setDetail] = useState<ScrmTicket | null>(null);
  const [comments, setComments] = useState<ScrmTicketComment[]>([]);
  const [history, setHistory] = useState<ScrmTicketHistory[]>([]);

  /** 加载工单列表 */
  const loadList = useCallback(async (targetPage = page) => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(pageSize) });
      const data = await apiClient.get<Page<ScrmTicket>>(`/scrm/tickets/list?${params.toString()}`);
      setList(data.content || []);
      setTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
      setList([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page]);

  useEffect(() => {
    loadList();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page]);

  /** 打开创建弹窗 */
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ category: 'PRODUCT_ISSUE', priority: 'MEDIUM' });
    setOpen(true);
  };

  /** 打开编辑弹窗 */
  const openEdit = (record: ScrmTicket) => {
    setEditing(record);
    form.setFieldsValue(record);
    setOpen(true);
  };

  /** 提交创建/编辑 */
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
        await apiClient.put(`/scrm/tickets/${editing.id}`, values);
        message.success('工单已更新');
      } else {
        await apiClient.post('/scrm/tickets', values);
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

  /** 打开分配弹窗 */
  const openAssign = (record: ScrmTicket) => {
    assignForm.resetFields();
    if (record.assigneeId) {
      assignForm.setFieldsValue({ assigneeId: record.assigneeId, assigneeName: record.assigneeName });
    }
    setAssignTarget(record);
  };

  /** 提交分配 */
  const handleAssign = async () => {
    if (!assignTarget) return;
    let values: Record<string, unknown>;
    try {
      values = await assignForm.validateFields();
    } catch {
      return;
    }
    setAssigning(true);
    try {
      await apiClient.post(`/scrm/tickets/${assignTarget.id}/assign`, values);
      message.success('工单已分配');
      setAssignTarget(null);
      loadList();
    } catch {
      // 拦截器已弹出错误
    } finally {
      setAssigning(false);
    }
  };

  /** 状态流转 */
  const handleStatusChange = async (record: ScrmTicket, status: string, tip: string) => {
    try {
      await apiClient.post(`/scrm/tickets/${record.id}/status`, { status });
      message.success(tip);
      loadList();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 打开详情抽屉, 并行加载评论与流转历史 */
  const openDetail = async (record: ScrmTicket) => {
    setDetail(record);
    setComments([]);
    setHistory([]);
    try {
      const [commentRows, historyRows] = await Promise.all([
        apiClient.get<ScrmTicketComment[] | Page<ScrmTicketComment>>(`/scrm/tickets/${record.id}/comments/list`),
        apiClient.get<ScrmTicketHistory[] | Page<ScrmTicketHistory>>(`/scrm/tickets/${record.id}/history/list`),
      ]);
      setComments(Array.isArray(commentRows) ? commentRows : commentRows?.content || []);
      setHistory(Array.isArray(historyRows) ? historyRows : historyRows?.content || []);
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 表格列 */
  const columns: ColumnsType<ScrmTicket> = useMemo(() => [
    {
      title: '工单号',
      dataIndex: 'ticketNo',
      width: 140,
      render: (v?: string) => <Text code>{v || '-'}</Text>,
    },
    { title: '标题', dataIndex: 'title', ellipsis: true, render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
    { title: '客户', dataIndex: 'customerName', width: 110, render: (v?: string, r?: ScrmTicket) => v || r?.customerId || '-' },
    {
      title: '类别',
      dataIndex: 'category',
      width: 110,
      render: (v?: string) => {
        const cfg = categoryConfig[v || ''] || { color: 'default', label: v || '-' };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '优先级',
      dataIndex: 'priority',
      width: 90,
      render: (v?: string) => {
        const cfg = priorityConfig[v || ''] || { color: 'default', label: v || '-' };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (v?: string) => {
        const cfg = statusConfig[v || ''] || { color: 'default', label: v || '-' };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '来源',
      dataIndex: 'source',
      width: 100,
      render: (v?: string) => {
        if (!v) return '-';
        const cfg = sourceConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '处理人', dataIndex: 'assigneeName', width: 100, render: (v?: string) => v || '-' },
    { title: '创建时间', dataIndex: 'createTime', width: 140, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
    {
      title: '操作',
      key: 'actions',
      width: 220,
      fixed: 'right',
      render: (_, r) => (
        <Space size={0}>
          {r.status === 'OPEN' && (
            <Button type="link" size="small" icon={<PlayCircleOutlined />} onClick={() => handleStatusChange(r, 'IN_PROGRESS', '已开始处理')}>
              开始
            </Button>
          )}
          {r.status === 'IN_PROGRESS' && (
            <Button type="link" size="small" onClick={() => handleStatusChange(r, 'RESOLVED', '工单已解决')}>
              解决
            </Button>
          )}
          <Button type="link" size="small" icon={<UserSwitchOutlined />} onClick={() => openAssign(r)}>
            分配
          </Button>
          <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => openDetail(r)}>
            详情
          </Button>
          <Button type="link" size="small" icon={<DeleteOutlined />} danger onClick={() => openEdit(r)}>
            编辑
          </Button>
        </Space>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  return (
    <div className="tickets-page">
      <Card
        title="客户工单"
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => loadList()} />
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              创建工单
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
          }} scroll={{ y: 'calc(100vh - 320px)' }}
        />
      </Card>

      {/* 创建/编辑弹窗 */}
      <Modal
        title={editing ? '编辑工单' : '创建工单'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={640}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="title" label="工单标题" rules={[{ required: true, message: '请输入工单标题' }]}>
            <Input placeholder="如: 产品到货破损投诉" maxLength={200} />
          </Form.Item>
          <Form.Item name="customerId" label="客户 ID" rules={[{ required: true, message: '请输入客户 ID' }]}>
            <Input placeholder="客户 ID" />
          </Form.Item>
          <Form.Item name="customerName" label="客户名称">
            <Input placeholder="客户名称" />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="category" label="类别" rules={[{ required: true, message: '请选择类别' }]}>
              <Select style={{ width: 220 }} options={Object.entries(categoryConfig).map(([v, c]) => ({ value: v, label: c.label }))} />
            </Form.Item>
            <Form.Item name="priority" label="优先级">
              <Select style={{ width: 140 }} options={Object.entries(priorityConfig).map(([v, c]) => ({ value: v, label: c.label }))} />
            </Form.Item>
          </Space>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} placeholder="问题描述" maxLength={2000} />
          </Form.Item>
          <Form.Item name="tags" label="标签 (逗号分隔)">
            <Input placeholder="如: 投诉,物流" />
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
          <Form.Item name="assigneeName" label="处理人名称">
            <Input placeholder="处理人名称" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 详情抽屉 */}
      <Drawer
        title={detail ? `工单详情 - ${detail.ticketNo}` : '工单详情'}
        open={!!detail}
        onClose={() => setDetail(null)}
        width={620}
      >
        {detail && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <Text strong style={{ fontSize: 16 }}>{detail.title}</Text>
            <Text type="secondary">{detail.description || '无描述'}</Text>
            <div>
              <Text strong>评论与备注</Text>
              <Table
                rowKey="id"
                size="small"
                dataSource={comments}
                pagination={false}
                locale={{ emptyText: <Empty description="暂无评论" /> }}
                columns={[
                  { title: '内容', dataIndex: 'content', ellipsis: true },
                  { title: '作者', dataIndex: 'authorName', width: 90, render: (v?: string) => v || '-' },
                  { title: '内部', dataIndex: 'isInternal', width: 60, render: (v?: boolean) => (v ? <Tag color="orange">内部</Tag> : <Tag>客户</Tag>) },
                  { title: '时间', dataIndex: 'createdAt', width: 140, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
                ]}
              />
            </div>
            <div>
              <Text strong>流转历史</Text>
              <Table
                rowKey="id"
                size="small"
                dataSource={history}
                pagination={false}
                locale={{ emptyText: <Empty description="暂无历史" /> }}
                columns={[
                  { title: '动作', dataIndex: 'action', width: 100 },
                  { title: '变更', dataIndex: 'toValue', ellipsis: true },
                  { title: '操作人', dataIndex: 'operatorName', width: 90, render: (v?: string) => v || '-' },
                  { title: '时间', dataIndex: 'createdAt', width: 140, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
                ]}
              />
            </div>
          </div>
        )}
      </Drawer>
    </div>
  );
}
