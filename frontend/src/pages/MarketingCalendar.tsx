/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : MarketingCalendar.tsx
 * Date : 2026/10/08 12:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  App,
  Button,
  Card,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  ReloadOutlined,
  CheckCircleOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

/** 营销日历事件实体 */
interface ScrmMarketingEvent {
  id: string;
  eventTitle: string;
  eventType: string; // CAMPAIGN / PROMOTION / ACTIVITY / OTHER
  priority?: string; // LOW / MEDIUM / HIGH / URGENT
  startDate?: string;
  endDate?: string;
  ownerId?: string;
  ownerName?: string;
  status: string; // PENDING / CONFIRMED
  description?: string;
}

/** 法定节假日实体 */
interface ScrmHoliday {
  id: string;
  holidayName: string;
  holidayType?: string;
  holidayDate?: string;
  description?: string;
  enabled?: boolean;
}

/** Spring Page 分页响应 */
interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/** 事件类型映射 */
const eventTypeConfig: Record<string, { color: string; label: string }> = {
  CAMPAIGN: { color: 'blue', label: '营销活动' },
  PROMOTION: { color: 'orange', label: '促销' },
  ACTIVITY: { color: 'green', label: '线下活动' },
  OTHER: { color: 'default', label: '其他' },
};

/** 事件状态映射 */
const eventStatusConfig: Record<string, { color: string; label: string }> = {
  PENDING: { color: 'orange', label: '待确认' },
  CONFIRMED: { color: 'green', label: '已确认' },
};

/** 优先级映射 */
const priorityConfig: Record<string, { color: string; label: string }> = {
  LOW: { color: 'default', label: '低' },
  MEDIUM: { color: 'blue', label: '中' },
  HIGH: { color: 'orange', label: '高' },
  URGENT: { color: 'red', label: '紧急' },
};

/**
 * 营销日历管理页
 * <p>
 * 统一编排营销事件(Campaign/促销/活动) 与法定节假日; 事件支持新建与确认流转。
 * </p>
 *
 * @author Hsi Chu
 */
export default function MarketingCalendar() {
  const { message } = App.useApp();
  /** 事件 */
  const [events, setEvents] = useState<ScrmMarketingEvent[]>([]);
  const [eventsLoading, setEventsLoading] = useState(false);
  const [eventPage, setEventPage] = useState(0);
  const eventPageSize = 10;
  const [eventTotal, setEventTotal] = useState(0);
  /** 假日 */
  const [holidays, setHolidays] = useState<ScrmHoliday[]>([]);
  const [holidaysLoading, setHolidaysLoading] = useState(false);
  const [holidayPage, setHolidayPage] = useState(0);
  const holidayPageSize = 10;
  const [holidayTotal, setHolidayTotal] = useState(0);
  /** 事件弹窗 */
  const [editing, setEditing] = useState<ScrmMarketingEvent | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  /** 假日弹窗 */
  const [holidayOpen, setHolidayOpen] = useState(false);
  const [holidaySaving, setHolidaySaving] = useState(false);
  const [holidayForm] = Form.useForm();

  const loadEvents = useCallback(async (targetPage = eventPage) => {
    setEventsLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(eventPageSize) });
      const data = await apiClient.get<Page<ScrmMarketingEvent>>(`/scrm/marketing-calendar/list?${params.toString()}`);
      setEvents(data.content || []);
      setEventTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setEventsLoading(false);
    }
  }, [eventPage]);

  const loadHolidays = useCallback(async (targetPage = holidayPage) => {
    setHolidaysLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(holidayPageSize) });
      const data = await apiClient.get<Page<ScrmHoliday>>(`/scrm/marketing-calendar/holidays/list?${params.toString()}`);
      setHolidays(data.content || []);
      setHolidayTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setHolidaysLoading(false);
    }
  }, [holidayPage]);

  useEffect(() => {
    loadEvents();
  }, [loadEvents]);

  useEffect(() => {
    loadHolidays();
  }, [loadHolidays]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ eventType: 'CAMPAIGN', priority: 'MEDIUM' });
    setOpen(true);
  };

  const openEdit = (record: ScrmMarketingEvent) => {
    setEditing(record);
    form.setFieldsValue(record);
    setOpen(true);
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await apiClient.put(`/scrm/marketing-calendar/${editing.id}`, values);
        message.success('事件已更新');
      } else {
        await apiClient.post('/scrm/marketing-calendar', values);
        message.success('事件已创建');
      }
      setOpen(false);
      setEventPage(0);
      loadEvents(0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setSaving(false);
    }
  };

  /** 确认事件 */
  const handleConfirm = async (record: ScrmMarketingEvent) => {
    try {
      await apiClient.post(`/scrm/marketing-calendar/${record.id}/confirm`, {});
      message.success('事件已确认');
      loadEvents();
    } catch {
      // 拦截器已弹出错误
    }
  };

  const handleSubmitHoliday = async () => {
    const values = await holidayForm.validateFields();
    setHolidaySaving(true);
    try {
      await apiClient.post('/scrm/marketing-calendar/holidays', values);
      message.success('假日已创建');
      setHolidayOpen(false);
      setHolidayPage(0);
      loadHolidays(0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setHolidaySaving(false);
    }
  };

  const handleDeleteHoliday = async (record: ScrmHoliday) => {
    try {
      await apiClient.delete(`/scrm/marketing-calendar/holidays/${record.id}`);
      message.success('假日已删除');
      loadHolidays();
    } catch {
      // 拦截器已弹出错误
    }
  };

  const eventColumns: ColumnsType<ScrmMarketingEvent> = useMemo(() => [
    { title: '事件标题', dataIndex: 'eventTitle', render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
    { title: '类型', dataIndex: 'eventType', width: 100, render: (v: string) => { const c = eventTypeConfig[v] || { color: 'default', label: v }; return <Tag color={c.color}>{c.label}</Tag>; } },
    { title: '优先级', dataIndex: 'priority', width: 80, render: (v?: string) => { const c = priorityConfig[v || ''] || { color: 'default', label: v || '-' }; return <Tag color={c.color}>{c.label}</Tag>; } },
    { title: '开始日期', dataIndex: 'startDate', width: 110, render: (v?: string) => v || '-' },
    { title: '结束日期', dataIndex: 'endDate', width: 110, render: (v?: string) => v || '-' },
    { title: '状态', dataIndex: 'status', width: 90, render: (v: string) => { const c = eventStatusConfig[v] || { color: 'default', label: v }; return <Tag color={c.color}>{c.label}</Tag>; } },
    { title: '负责人', dataIndex: 'ownerName', width: 100, render: (v?: string) => v || '-' },
    { title: '创建时间', dataIndex: 'createTime', width: 150, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
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
    <div className="marketing-calendar-page">
      <Tabs
        items={[
          {
            key: 'events',
            label: '营销事件',
            children: (
              <Card
                title="营销日历"
                extra={
                  <Space>
                    <Button icon={<ReloadOutlined />} onClick={() => loadEvents()} />
                    <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                      新建事件
                    </Button>
                  </Space>
                }
              >
                <Table
                  rowKey="id"
                  columns={eventColumns}
                  dataSource={events}
                  loading={eventsLoading}
                  pagination={{
                    current: eventPage + 1,
                    pageSize: eventPageSize,
                    total: eventTotal,
                    showTotal: t => `共 ${t} 条`,
                    onChange: p => setEventPage(p - 1),
                  }}
                  scroll={{ y: 'calc(100vh - 320px)' }}
                />
              </Card>
            ),
          },
          {
            key: 'holidays',
            label: '假日管理',
            children: (
              <Card
                title="法定节假日"
                extra={
                  <Space>
                    <Button icon={<ReloadOutlined />} onClick={() => loadHolidays()} />
                    <Button type="primary" icon={<PlusOutlined />} onClick={() => { holidayForm.resetFields(); setHolidayOpen(true); }}>
                      新建假日
                    </Button>
                  </Space>
                }
              >
                <Table
                  rowKey="id"
                  columns={[
                    { title: '假日名称', dataIndex: 'holidayName', render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
                    { title: '类型', dataIndex: 'holidayType', width: 110, render: (v?: string) => v || '-' },
                    { title: '日期', dataIndex: 'holidayDate', width: 140, render: (v?: string) => v || '-' },
                    { title: '说明', dataIndex: 'description', ellipsis: true, render: (v?: string) => v || '-' },
                    {
                      title: '启用',
                      dataIndex: 'enabled',
                      width: 80,
                      render: (v?: boolean) => (v ? <Tag color="green">是</Tag> : <Tag>否</Tag>),
                    },
                    {
                      title: '操作',
                      key: 'actions',
                      width: 90,
                      render: (_, r) => (
                        <Popconfirm title="确定删除该假日?" onConfirm={() => handleDeleteHoliday(r)}>
                          <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                            删除
                          </Button>
                        </Popconfirm>
                      ),
                    },
                  ]}
                  dataSource={holidays}
                  loading={holidaysLoading}
                  pagination={{
                    current: holidayPage + 1,
                    pageSize: holidayPageSize,
                    total: holidayTotal,
                    showTotal: t => `共 ${t} 条`,
                    onChange: p => setHolidayPage(p - 1),
                  }}
                />
              </Card>
            ),
          },
        ]}
      />

      {/* 事件弹窗 */}
      <Modal
        title={editing ? '编辑事件' : '新建事件'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={560}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="eventTitle" label="事件标题" rules={[{ required: true, message: '请输入事件标题' }]}>
            <Input placeholder="如: 618 大促预热" maxLength={200} />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="eventType" label="事件类型" rules={[{ required: true }]}>
              <Select style={{ width: 150 }} options={Object.entries(eventTypeConfig).map(([v, c]) => ({ value: v, label: c.label }))} />
            </Form.Item>
            <Form.Item name="priority" label="优先级">
              <Select style={{ width: 120 }} options={[{ value: 'LOW', label: '低' }, { value: 'MEDIUM', label: '中' }, { value: 'HIGH', label: '高' }, { value: 'URGENT', label: '紧急' }]} />
            </Form.Item>
          </Space>
          <Space size={16} wrap>
            <Form.Item name="startDate" label="开始日期" rules={[{ required: true }]}>
              <Input placeholder="如 2026-06-01" style={{ width: 150 }} />
            </Form.Item>
            <Form.Item name="endDate" label="结束日期" rules={[{ required: true }]}>
              <Input placeholder="如 2026-06-03" style={{ width: 150 }} />
            </Form.Item>
          </Space>
          <Form.Item name="ownerId" label="负责人 ID">
            <Input placeholder="负责人用户 ID" />
          </Form.Item>
          <Form.Item name="description" label="说明">
            <Input.TextArea rows={2} placeholder="事件说明" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 假日弹窗 */}
      <Modal
        title="新建假日"
        open={holidayOpen}
        onCancel={() => setHolidayOpen(false)}
        onOk={handleSubmitHoliday}
        confirmLoading={holidaySaving}
      >
        <Form form={holidayForm} layout="vertical">
          <Form.Item name="holidayName" label="假日名称" rules={[{ required: true, message: '请输入假日名称' }]}>
            <Input placeholder="如: 国庆节" maxLength={100} />
          </Form.Item>
          <Form.Item name="holidayType" label="假日类型">
            <Input placeholder="如: NATIONAL" maxLength={30} />
          </Form.Item>
          <Form.Item name="holidayDate" label="日期" rules={[{ required: true, message: '请输入日期' }]}>
            <Input placeholder="如 2026-10-01" maxLength={20} />
          </Form.Item>
          <Form.Item name="description" label="说明">
            <Input placeholder="假日说明" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}