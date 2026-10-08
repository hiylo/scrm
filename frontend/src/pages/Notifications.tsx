/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Notifications.tsx
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
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  ReloadOutlined,
  SendOutlined,
  CheckCircleOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

const { Text } = Typography;

/** 通知模板实体 */
interface ScrmNotificationTemplate {
  id: string;
  templateName: string;
  templateCode: string;
  channel: string; // IN_APP / SMS / EMAIL / WEWORK
  category: string;
  title: string;
  content: string;
  variables?: string;
  enabled?: boolean;
  createTime?: string;
}

/** 通知消息实体 */
interface ScrmNotification {
  id: string;
  templateCode?: string;
  channel?: string;
  category?: string;
  title?: string;
  content?: string;
  status: string; // PENDING / SENT / READ / FAILED
  recipientId?: string;
  createTime?: string;
  readAt?: string;
}

/** Spring Page 分页响应 */
interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/** 渠道映射 */
const channelConfig: Record<string, { color: string; label: string }> = {
  IN_APP: { color: 'blue', label: '站内信' },
  SMS: { color: 'orange', label: '短信' },
  EMAIL: { color: 'purple', label: '邮件' },
  WEWORK: { color: 'green', label: '企业微信' },
};

/** 消息状态映射 */
const notifStatusConfig: Record<string, { color: string; label: string }> = {
  PENDING: { color: 'orange', label: '待发送' },
  SENT: { color: 'blue', label: '已发送' },
  READ: { color: 'green', label: '已读' },
  FAILED: { color: 'red', label: '失败' },
};

/**
 * 通知中心管理页
 * <p>
 * 双 Tab: 通知模板(渠道/分类/变量) 与 通知消息(发送/已读); 支持新建模板与手动发送通知。
 * </p>
 *
 * @author Hsi Chu
 */
export default function Notifications() {
  const { message } = App.useApp();
  /** 模板 */
  const [templates, setTemplates] = useState<ScrmNotificationTemplate[]>([]);
  const [templatesLoading, setTemplatesLoading] = useState(false);
  const [templatePage, setTemplatePage] = useState(0);
  const templatePageSize = 10;
  const [templateTotal, setTemplateTotal] = useState(0);
  /** 消息 */
  const [notifs, setNotifs] = useState<ScrmNotification[]>([]);
  const [notifsLoading, setNotifsLoading] = useState(false);
  const [notifPage, setNotifPage] = useState(0);
  const notifPageSize = 10;
  const [notifTotal, setNotifTotal] = useState(0);
  /** 模板弹窗 */
  const [editing, setEditing] = useState<ScrmNotificationTemplate | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  /** 发送弹窗 */
  const [sendOpen, setSendOpen] = useState(false);
  const [sending, setSending] = useState(false);
  const [sendForm] = Form.useForm();

  const loadTemplates = useCallback(async (targetPage = templatePage) => {
    setTemplatesLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(templatePageSize) });
      const data = await apiClient.get<Page<ScrmNotificationTemplate>>(`/scrm/notifications/templates/list?${params.toString()}`);
      setTemplates(data.content || []);
      setTemplateTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setTemplatesLoading(false);
    }
  }, [templatePage]);

  const loadNotifs = useCallback(async (targetPage = notifPage) => {
    setNotifsLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(notifPageSize) });
      const data = await apiClient.get<Page<ScrmNotification>>(`/scrm/notifications/list?${params.toString()}`);
      setNotifs(data.content || []);
      setNotifTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setNotifsLoading(false);
    }
  }, [notifPage]);

  useEffect(() => {
    loadTemplates();
  }, [loadTemplates]);

  useEffect(() => {
    loadNotifs();
  }, [loadNotifs]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ channel: 'IN_APP', category: 'ORDER' });
    setOpen(true);
  };

  const openEdit = (record: ScrmNotificationTemplate) => {
    setEditing(record);
    form.setFieldsValue(record);
    setOpen(true);
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await apiClient.put(`/scrm/notifications/templates/${editing.id}`, values);
        message.success('通知模板已更新');
      } else {
        await apiClient.post('/scrm/notifications/templates', values);
        message.success('通知模板已创建');
      }
      setOpen(false);
      setTemplatePage(0);
      loadTemplates(0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setSaving(false);
    }
  };

  const handleSend = async () => {
    const values = await sendForm.validateFields();
    setSending(true);
    try {
      // variables 为 JSON 字符串, 后端需要对象
      const payload: Record<string, unknown> = {
        templateCode: values.templateCode,
        recipientId: values.recipientId,
      };
      if (values.variables) {
        try {
          payload.variables = JSON.parse(values.variables);
        } catch {
          message.error('变量 JSON 格式不正确');
          setSending(false);
          return;
        }
      }
      await apiClient.post('/scrm/notifications/send', payload);
      message.success('通知已发送');
      setSendOpen(false);
      setNotifPage(0);
      loadNotifs(0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setSending(false);
    }
  };

  const handleRead = async (record: ScrmNotification) => {
    try {
      await apiClient.post(`/scrm/notifications/${record.id}/read`);
      message.success('已标记为已读');
      loadNotifs();
    } catch {
      // 拦截器已弹出错误
    }
  };

  const templateColumns: ColumnsType<ScrmNotificationTemplate> = useMemo(() => [
    { title: '模板名称', dataIndex: 'templateName', render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
    { title: '模板编码', dataIndex: 'templateCode', width: 140, render: (v: string) => <Text code>{v}</Text> },
    { title: '渠道', dataIndex: 'channel', width: 100, render: (v: string) => { const c = channelConfig[v] || { color: 'default', label: v }; return <Tag color={c.color}>{c.label}</Tag>; } },
    { title: '分类', dataIndex: 'category', width: 100, render: (v?: string) => v || '-' },
    { title: '标题', dataIndex: 'title', ellipsis: true, render: (v?: string) => v || '-' },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 80,
      render: (v?: boolean) => (v === false ? <Tag>停用</Tag> : <Tag color="green">启用</Tag>),
    },
    {
      title: '操作',
      key: 'actions',
      width: 130,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          <Button type="link" size="small" icon={<SendOutlined />} onClick={() => { sendForm.resetFields(); sendForm.setFieldsValue({ templateCode: r.templateCode }); setSendOpen(true); }}>
            发送
          </Button>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(r)}>
            编辑
          </Button>
        </Space>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  const notifColumns: ColumnsType<ScrmNotification> = useMemo(() => [
    { title: '标题', dataIndex: 'title', ellipsis: true, render: (v?: string) => <span style={{ fontWeight: 600 }}>{v || '-'}</span> },
    { title: '模板编码', dataIndex: 'templateCode', width: 120, render: (v?: string) => v || '-' },
    { title: '渠道', dataIndex: 'channel', width: 100, render: (v?: string) => { const c = channelConfig[v || ''] || { color: 'default', label: v || '-' }; return <Tag color={c.color}>{c.label}</Tag>; } },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => { const c = notifStatusConfig[v] || { color: 'default', label: v }; return <Tag color={c.color}>{c.label}</Tag>; },
    },
    { title: '接收人', dataIndex: 'recipientId', width: 110, render: (v?: string) => v || '-' },
    { title: '创建时间', dataIndex: 'createTime', width: 150, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
    {
      title: '操作',
      key: 'actions',
      width: 90,
      fixed: 'right',
      render: (_, r) => (
        r.status !== 'READ' ? (
          <Button type="link" size="small" icon={<CheckCircleOutlined />} onClick={() => handleRead(r)}>
            已读
          </Button>
        ) : null
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  return (
    <div className="notifications-page">
      <Tabs
        items={[
          {
            key: 'templates',
            label: '通知模板',
            children: (
              <Card
                title="通知模板"
                extra={
                  <Space>
                    <Button icon={<ReloadOutlined />} onClick={() => loadTemplates()} />
                    <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                      新建模板
                    </Button>
                  </Space>
                }
              >
                <Table
                  rowKey="id"
                  columns={templateColumns}
                  dataSource={templates}
                  loading={templatesLoading}
                  pagination={{
                    current: templatePage + 1,
                    pageSize: templatePageSize,
                    total: templateTotal,
                    showTotal: t => `共 ${t} 条`,
                    onChange: p => setTemplatePage(p - 1),
                  }}
                />
              </Card>
            ),
          },
          {
            key: 'notifs',
            label: '通知消息',
            children: (
              <Card
                title="通知消息"
                extra={
                  <Space>
                    <Button icon={<SendOutlined />} onClick={() => { sendForm.resetFields(); setSendOpen(true); }}>
                      发送通知
                    </Button>
                    <Button icon={<ReloadOutlined />} onClick={() => loadNotifs()} />
                  </Space>
                }
              >
                <Table
                  rowKey="id"
                  columns={notifColumns}
                  dataSource={notifs}
                  loading={notifsLoading}
                  pagination={{
                    current: notifPage + 1,
                    pageSize: notifPageSize,
                    total: notifTotal,
                    showTotal: t => `共 ${t} 条`,
                    onChange: p => setNotifPage(p - 1),
                  }}
                  locale={{ emptyText: <Empty description="暂无通知消息" /> }}
                />
              </Card>
            ),
          },
        ]}
      />

      {/* 模板弹窗 */}
      <Modal
        title={editing ? '编辑通知模板' : '新建通知模板'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={560}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="templateName" label="模板名称" rules={[{ required: true, message: '请输入模板名称' }]}>
            <Input placeholder="如: 订单支付成功通知" maxLength={200} />
          </Form.Item>
          <Form.Item name="templateCode" label="模板编码" rules={[{ required: true, message: '请输入模板编码' }]}>
            <Input placeholder="如: ORDER_PAID" maxLength={100} />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="channel" label="渠道" rules={[{ required: true }]}>
              <Select style={{ width: 140 }} options={Object.entries(channelConfig).map(([v, c]) => ({ value: v, label: c.label }))} />
            </Form.Item>
            <Form.Item name="category" label="分类" rules={[{ required: true }]}>
              <Input placeholder="如: ORDER" style={{ width: 140 }} />
            </Form.Item>
          </Space>
          <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入标题' }]}>
            <Input placeholder="如: 您的订单已支付成功" />
          </Form.Item>
          <Form.Item name="content" label="内容" rules={[{ required: true, message: '请输入内容' }]}>
            <Input.TextArea rows={3} placeholder="支持 ${nickname} ${orderNo} 变量" />
          </Form.Item>
          <Form.Item name="variables" label="变量说明">
            <Input placeholder="如: nickname,orderNo" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 发送弹窗 */}
      <Modal
        title="发送通知"
        open={sendOpen}
        onCancel={() => setSendOpen(false)}
        onOk={handleSend}
        confirmLoading={sending}
      >
        <Form form={sendForm} layout="vertical">
          <Form.Item name="templateCode" label="模板编码" rules={[{ required: true, message: '请输入模板编码' }]}>
            <Input placeholder="模板编码" />
          </Form.Item>
          <Form.Item name="recipientId" label="接收人 ID" rules={[{ required: true, message: '请输入接收人 ID' }]}>
            <Input placeholder="接收人用户/客户 ID" />
          </Form.Item>
          <Form.Item name="variables" label="变量 JSON">
            <Input.TextArea rows={2} placeholder='{"nickname":"张三","orderNo":"DD-1"}' />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}