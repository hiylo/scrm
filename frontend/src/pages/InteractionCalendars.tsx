/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : InteractionCalendars.tsx
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
  CheckCircleOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

/** 互动类型映射 */
const planTypeConfig: Record<string, { color: string; label: string }> = {
  PHONE: { color: 'blue', label: '电话' },
  VISIT: { color: 'purple', label: '拜访' },
  WECHAT: { color: 'green', label: '微信' },
  EMAIL: { color: 'cyan', label: '邮件' },
  GIFT: { color: 'gold', label: '礼品' },
  OTHER: { color: 'default', label: '其他' },
};

/** 计划状态映射 */
const statusConfig: Record<string, { color: string; label: string }> = {
  PENDING: { color: 'orange', label: '待确认' },
  CONFIRMED: { color: 'blue', label: '已确认' },
  IN_PROGRESS: { color: 'cyan', label: '进行中' },
  COMPLETED: { color: 'green', label: '已完成' },
  CANCELLED: { color: 'default', label: '已取消' },
};

/** 互动计划行 */
interface ScrmInteractionPlan {
  id: string;
  planName: string;
  planCode?: string;
  planType: string;
  customerId?: string;
  customerName?: string;
  ownerId?: string;
  status: string;
  plannedDate?: string;
  plannedTime?: string;
  description?: string;
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
 * 互动日历页面
 * <p>
 * 互动计划列表 + 新建/编辑计划 + 确认状态流转。
 * </p>
 *
 * @author Hsi Chu
 */
export default function InteractionCalendars() {
  const { message } = App.useApp();
  /** 列表 */
  const [list, setList] = useState<ScrmInteractionPlan[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const pageSize = 10;
  /** 弹窗 */
  const [editing, setEditing] = useState<ScrmInteractionPlan | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  /** 加载列表 */
  const loadList = useCallback(async (targetPage = page) => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(pageSize) });
      const data = await apiClient.get<Page<ScrmInteractionPlan>>(`/scrm/interaction-calendar/plans/list?${params.toString()}`);
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
    form.setFieldsValue({ planType: 'PHONE' });
    setOpen(true);
  };

  /** 打开编辑弹窗 */
  const openEdit = (record: ScrmInteractionPlan) => {
    setEditing(record);
    form.setFieldsValue({
      planName: record.planName,
      customerId: record.customerId,
      planType: record.planType,
      ownerId: record.ownerId,
      plannedDate: record.plannedDate,
      plannedTime: record.plannedTime,
      description: record.description,
    });
    setOpen(true);
  };

  /** 提交新建/编辑 */
  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await apiClient.put(`/scrm/interaction-calendar/plans/${editing.id}`, values);
        message.success('互动计划已更新');
      } else {
        await apiClient.post('/scrm/interaction-calendar/plans', values);
        message.success('互动计划已创建');
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

  /** 确认计划 */
  const handleConfirm = async (record: ScrmInteractionPlan) => {
    try {
      await apiClient.post(`/scrm/interaction-calendar/plans/${record.id}/confirm`, {});
      message.success('互动计划已确认');
      loadList();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 表格列 */
  const columns: ColumnsType<ScrmInteractionPlan> = useMemo(() => [
    { title: '计划名称', dataIndex: 'planName', width: 180, render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
    { title: '计划编码', dataIndex: 'planCode', width: 110, render: (v?: string) => (v ? <Tag>{v}</Tag> : '-') },
    {
      title: '互动类型',
      dataIndex: 'planType',
      width: 100,
      render: (v: string) => {
        const cfg = planTypeConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '客户', dataIndex: 'customerName', width: 120, render: (v?: string) => v || '-' },
    {
      title: '计划时间',
      key: 'planned',
      width: 140,
      render: (_, r) => [r.plannedDate, r.plannedTime].filter(Boolean).join(' ') || '-',
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
    { title: '创建时间', dataIndex: 'createTime', width: 140, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
    {
      title: '操作',
      key: 'actions',
      width: 140,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          {r.status === 'PENDING' && (
            <Button type="link" size="small" icon={<CheckCircleOutlined />} onClick={() => handleConfirm(r)}>
              确认
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
    <div className="interaction-calendars-page">
      <Card
        title="互动日历"
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => loadList()} />
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              新建计划
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

      {/* 计划弹窗 */}
      <Modal
        title={editing ? '编辑互动计划' : '新建互动计划'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={520}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="planName" label="计划名称" rules={[{ required: true, message: '请输入计划名称' }]}>
            <Input placeholder="如: 回访高意向客户" maxLength={200} />
          </Form.Item>
          <Form.Item name="customerId" label="客户 ID" rules={[{ required: true, message: '请输入客户 ID' }]}>
            <Input placeholder="客户 ID" />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="planType" label="互动类型" rules={[{ required: true }]}>
              <Select style={{ width: 150 }} options={Object.entries(planTypeConfig).map(([v, c]) => ({ value: v, label: c.label }))} />
            </Form.Item>
            <Form.Item name="ownerId" label="负责人 ID">
              <Input placeholder="负责人用户 ID" style={{ width: 150 }} />
            </Form.Item>
          </Space>
          <Space size={16} wrap>
            <Form.Item name="plannedDate" label="计划日期">
              <Input placeholder="如 2026-10-10" style={{ width: 140 }} />
            </Form.Item>
            <Form.Item name="plannedTime" label="计划时间">
              <Input placeholder="如 10:30" style={{ width: 120 }} />
            </Form.Item>
          </Space>
          <Form.Item name="description" label="说明">
            <Input.TextArea rows={2} placeholder="互动说明" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}