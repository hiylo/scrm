/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Webhooks.tsx
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
  InputNumber,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  ReloadOutlined,
  ThunderboltOutlined,
  DeleteOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

const { Text } = Typography;

/** 订阅事件映射 (用于 Tag 展示) */
const eventTypeConfig: Record<string, { color: string; label: string }> = {
  CUSTOMER_CREATED: { color: 'blue', label: '客户创建' },
  ORDER_PAID: { color: 'green', label: '订单支付' },
  CUSTOMER_UPDATED: { color: 'cyan', label: '客户更新' },
  CONVERSATION_CREATED: { color: 'geekblue', label: '会话创建' },
};

/** 事件日志状态映射 */
const logStatusConfig: Record<string, { color: string; label: string }> = {
  SUCCESS: { color: 'green', label: '成功' },
  FAILED: { color: 'red', label: '失败' },
  PENDING: { color: 'orange', label: '待投递' },
  RETRYING: { color: 'processing', label: '重试中' },
};

/** Webhook 配置实体 */
interface ScrmWebhookConfig {
  id: string;
  webhookName: string;
  targetUrl: string;
  subscribedEvents?: string;
  httpMethod?: string;
  timeoutSeconds?: number;
  secret?: string;
  headers?: string;
  enabled?: boolean;
  createTime?: string;
}

/** Webhook 事件日志 */
interface ScrmWebhookLog {
  id: string;
  webhookId?: string;
  webhookName?: string;
  eventType?: string;
  status?: string;
  responseCode?: number;
  errorMessage?: string;
  deliveredAt?: string;
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
 * Webhook 管理页面
 * 双 Tab: Webhook 配置 / 事件日志; 支持配置 CRUD 与手动测试回调。
 *
 * @author Hsi Chu
 */
export default function Webhooks() {
  const { message } = App.useApp();
  /** 配置 */
  const [configs, setConfigs] = useState<ScrmWebhookConfig[]>([]);
  const [configsLoading, setConfigsLoading] = useState(false);
  const [configPage, setConfigPage] = useState(0);
  const configPageSize = 10;
  const [configTotal, setConfigTotal] = useState(0);
  /** 日志 */
  const [logs, setLogs] = useState<ScrmWebhookLog[]>([]);
  const [logsLoading, setLogsLoading] = useState(false);
  const [logPage, setLogPage] = useState(0);
  const logPageSize = 10;
  const [logTotal, setLogTotal] = useState(0);
  /** 配置弹窗 */
  const [editing, setEditing] = useState<ScrmWebhookConfig | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  /** 加载配置列表 */
  const loadConfigs = useCallback(async (targetPage = configPage) => {
    setConfigsLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(configPageSize) });
      const data = await apiClient.get<Page<ScrmWebhookConfig>>(`/scrm/webhooks/configs/list?${params.toString()}`);
      setConfigs(data.content || []);
      setConfigTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setConfigsLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [configPage]);

  /** 加载事件日志 */
  const loadLogs = useCallback(async (targetPage = logPage) => {
    setLogsLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(logPageSize) });
      const data = await apiClient.get<Page<ScrmWebhookLog>>(`/scrm/webhooks/logs/list?${params.toString()}`);
      setLogs(data.content || []);
      setLogTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setLogsLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [logPage]);

  useEffect(() => {
    loadConfigs();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [configPage]);

  useEffect(() => {
    loadLogs();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [logPage]);

  /** 打开新建弹窗 */
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ httpMethod: 'POST', timeoutSeconds: 10, enabled: true });
    setOpen(true);
  };

  /** 打开编辑弹窗 */
  const openEdit = (record: ScrmWebhookConfig) => {
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
        await apiClient.put(`/scrm/webhooks/configs/${editing.id}`, values);
        message.success('Webhook 配置已更新');
      } else {
        await apiClient.post('/scrm/webhooks/configs', values);
        message.success('Webhook 配置已创建');
      }
      setOpen(false);
      setConfigPage(0);
      loadConfigs(0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setSaving(false);
    }
  };

  /** 测试回调 */
  const handleTest = async (record: ScrmWebhookConfig) => {
    try {
      await apiClient.post(`/scrm/webhooks/configs/${record.id}/test`);
      message.success('测试回调已触发');
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 启停配置 */
  const handleToggle = async (record: ScrmWebhookConfig, checked: boolean) => {
    try {
      await apiClient.post(`/scrm/webhooks/configs/${record.id}/${checked ? 'enable' : 'disable'}`);
      message.success(checked ? 'Webhook 已启用' : 'Webhook 已停用');
      loadConfigs();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 配置表格列 */
  const configColumns: ColumnsType<ScrmWebhookConfig> = useMemo(() => [
    { title: '配置名称', dataIndex: 'webhookName', width: 180, render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
    { title: '目标 URL', dataIndex: 'targetUrl', ellipsis: true, render: (v?: string) => v || '-' },
    {
      title: '订阅事件',
      dataIndex: 'subscribedEvents',
      width: 220,
      render: (v?: string) => {
        if (!v) return '-';
        return v.split(',').map((e) => {
          const ev = e.trim();
          const cfg = eventTypeConfig[ev] || { color: 'default', label: ev };
          return <Tag key={ev} color={cfg.color} style={{ marginBottom: 2 }}>{cfg.label}</Tag>;
        });
      },
    },
    { title: '方法', dataIndex: 'httpMethod', width: 80, render: (v?: string) => (v ? <Text code>{v}</Text> : '-') },
    { title: '超时 (秒)', dataIndex: 'timeoutSeconds', width: 100, render: (v?: number) => v ?? '-' },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 90,
      render: (v?: boolean) => (v === false ? <Tag>停用</Tag> : <Tag color="green">启用</Tag>),
    },
    { title: '创建时间', dataIndex: 'createTime', width: 140, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
    {
      title: '操作',
      key: 'actions',
      width: 200,
      fixed: 'right',
      render: (_, r) => (
        <Space size={0}>
          <Switch size="small" checked={r.enabled} onChange={(checked) => handleToggle(r, checked)} style={{ marginRight: 8 }} />
          <Button type="link" size="small" icon={<ThunderboltOutlined />} onClick={() => handleTest(r)}>
            测试
          </Button>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(r)}>
            编辑
          </Button>
          <Button type="link" size="small" icon={<DeleteOutlined />} danger onClick={() => handleDelete(r)}>
            删除
          </Button>
        </Space>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  /** 删除配置 */
  const handleDelete = async (record: ScrmWebhookConfig) => {
    try {
      await apiClient.delete(`/scrm/webhooks/configs/${record.id}`);
      message.success('Webhook 配置已删除');
      loadConfigs();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 日志表格列 */
  const logColumns: ColumnsType<ScrmWebhookLog> = useMemo(() => [
    { title: '配置', dataIndex: 'webhookName', width: 140, render: (v?: string) => v || '-' },
    {
      title: '事件类型',
      dataIndex: 'eventType',
      width: 160,
      render: (v?: string) => {
        if (!v) return '-';
        const cfg = eventTypeConfig[v];
        return <Tag color={cfg?.color || 'default'}>{v}</Tag>;
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v?: string) => {
        const cfg = logStatusConfig[v || ''] || { color: 'default', label: v || '-' };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '响应码', dataIndex: 'responseCode', width: 90, render: (v?: number) => v ?? '-' },
    { title: '错误信息', dataIndex: 'errorMessage', ellipsis: true, render: (v?: string) => v || '-' },
    { title: '投递时间', dataIndex: 'deliveredAt', width: 150, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-') },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  return (
    <div className="webhooks-page">
      <Tabs
        items={[
          {
            key: 'configs',
            label: 'Webhook 配置',
            children: (
              <Card
                title="Webhook 配置"
                extra={
                  <Space>
                    <Button icon={<ReloadOutlined />} onClick={() => loadConfigs()} />
                    <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                      新建配置
                    </Button>
                  </Space>
                }
              >
                <Table
                  rowKey="id"
                  columns={configColumns}
                  dataSource={configs}
                  loading={configsLoading}
                  pagination={{
                    current: configPage + 1,
                    pageSize: configPageSize,
                    total: configTotal,
                    showTotal: t => `共 ${t} 条`,
                    onChange: p => setConfigPage(p - 1),
                  }} scroll={{ y: 'calc(100vh - 320px)' }}
                />
              </Card>
            ),
          },
          {
            key: 'logs',
            label: '事件日志',
            children: (
              <Card
                title="Webhook 事件日志"
                extra={<Button icon={<ReloadOutlined />} onClick={() => loadLogs()} />}
              >
                <Table
                  rowKey="id"
                  columns={logColumns}
                  dataSource={logs}
                  loading={logsLoading}
                  pagination={{
                    current: logPage + 1,
                    pageSize: logPageSize,
                    total: logTotal,
                    showTotal: t => `共 ${t} 条`,
                    onChange: p => setLogPage(p - 1),
                  }}
                  locale={{ emptyText: <Empty description="暂无事件日志" /> }}
                />
              </Card>
            ),
          },
        ]}
      />

      {/* 配置弹窗 */}
      <Modal
        title={editing ? '编辑 Webhook 配置' : '新建 Webhook 配置'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={560}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="webhookName" label="配置名称" rules={[{ required: true, message: '请输入配置名称' }]}>
            <Input placeholder="如: 客户创建回调" maxLength={200} />
          </Form.Item>
          <Form.Item name="targetUrl" label="目标 URL" rules={[{ required: true, message: '请输入目标 URL' }]}>
            <Input placeholder="https://your-server.com/webhook" maxLength={500} />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="httpMethod" label="HTTP 方法" rules={[{ required: true }]}>
              <Select style={{ width: 120 }} options={['POST', 'PUT', 'PATCH'].map(m => ({ value: m, label: m }))} />
            </Form.Item>
            <Form.Item name="timeoutSeconds" label="超时 (秒)">
              <InputNumber min={1} style={{ width: 110 }} />
            </Form.Item>
          </Space>
          <Form.Item name="subscribedEvents" label="订阅事件 (逗号分隔)" rules={[{ required: true, message: '请输入订阅事件' }]}>
            <Input placeholder="如: CUSTOMER_CREATED,ORDER_PAID" />
          </Form.Item>
          <Form.Item name="secret" label="签名密钥">
            <Input placeholder="HMAC 签名密钥" />
          </Form.Item>
          <Form.Item name="headers" label="自定义头 JSON">
            <Input.TextArea rows={2} placeholder='{"Authorization":"Bearer xxx"}' />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
