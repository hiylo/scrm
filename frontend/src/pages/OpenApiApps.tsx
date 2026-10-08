/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : OpenApiApps.tsx
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
  EditOutlined,
  ReloadOutlined,
  SearchOutlined,
  StopOutlined,
  KeyOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

const { Text } = Typography;

/** OpenAPI 应用实体 */
interface ScrmOpenApiApp {
  id: string;
  appName: string;
  appType: string; // SERVER / CLIENT / PARTNER
  clientId?: string;
  clientSecret?: string;
  redirectUris?: string;
  scopes?: string;
  rateLimitPerMinute?: number;
  rateLimitPerDay?: number;
  ipWhitelist?: string;
  description?: string;
  status: string; // ACTIVE / SUSPENDED
  createTime?: string;
}

/** API 密钥实体 */
interface ScrmApiKey {
  id: string;
  appId?: string;
  apiKey?: string;
  keyType?: string; // APP / USER / SERVICE
  scopes?: string;
  status?: string;
  expiresAt?: string;
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

/** 应用类型映射 */
const appTypeConfig: Record<string, string> = {
  SERVER: '服务端应用',
  CLIENT: '客户端应用',
  PARTNER: '合作伙伴应用',
};

/** 应用状态映射 */
const appStatusConfig: Record<string, { color: string; label: string }> = {
  ACTIVE: { color: 'green', label: '正常' },
  SUSPENDED: { color: 'orange', label: '已暂停' },
};

/**
 * OpenAPI 应用管理页
 * <p>
 * 管理第三方接入应用: 应用注册/暂停、限流与 IP 白名单配置、API 密钥签发。
 * </p>
 *
 * @author Hsi Chu
 */
export default function OpenApiApps() {
  const { message } = App.useApp();
  /** 应用列表 */
  const [apps, setApps] = useState<ScrmOpenApiApp[]>([]);
  const [appsLoading, setAppsLoading] = useState(false);
  const [appPage, setAppPage] = useState(0);
  const appPageSize = 10;
  const [appTotal, setAppTotal] = useState(0);
  const [keyword, setKeyword] = useState('');
  /** 应用弹窗 */
  const [editing, setEditing] = useState<ScrmOpenApiApp | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  /** 密钥抽屉 */
  const [keysApp, setKeysApp] = useState<ScrmOpenApiApp | null>(null);
  const [keys, setKeys] = useState<ScrmApiKey[]>([]);
  const [keyLoading, setKeyLoading] = useState(false);
  /** 密钥弹窗 */
  const [keyOpen, setKeyOpen] = useState(false);
  const [keySaving, setKeySaving] = useState(false);
  const [keyForm] = Form.useForm();

  const loadApps = useCallback(async (targetPage = appPage, k = keyword) => {
    setAppsLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(appPageSize) });
      if (k) params.set('keyword', k);
      const data = await apiClient.get<Page<ScrmOpenApiApp>>(`/scrm/open-api/apps/list?${params.toString()}`);
      setApps(data.content || []);
      setAppTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setAppsLoading(false);
    }
  }, [appPage, keyword]);

  useEffect(() => {
    loadApps();
  }, [loadApps]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ appType: 'SERVER' });
    setOpen(true);
  };

  const openEdit = (record: ScrmOpenApiApp) => {
    setEditing(record);
    form.setFieldsValue(record);
    setOpen(true);
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await apiClient.put(`/scrm/open-api/apps/${editing.id}`, values);
        message.success('应用已更新');
      } else {
        await apiClient.post('/scrm/open-api/apps', values);
        message.success('应用已创建');
      }
      setOpen(false);
      setAppPage(0);
      loadApps(0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setSaving(false);
    }
  };

  /** 暂停应用 */
  const handleSuspend = async (record: ScrmOpenApiApp) => {
    try {
      await apiClient.post(`/scrm/open-api/apps/${record.id}/suspend`);
      message.success('应用已暂停');
      loadApps();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 加载密钥 */
  const loadKeys = useCallback(async (app: ScrmOpenApiApp) => {
    setKeyLoading(true);
    try {
      const params = new URLSearchParams({ page: '0', size: '50' });
      const data = await apiClient.get<Page<ScrmApiKey>>(`/scrm/open-api/apps/${app.id}/keys?${params.toString()}`);
      setKeys(data.content || []);
    } catch {
      setKeys([]);
    } finally {
      setKeyLoading(false);
    }
  }, []);

  /** 打开密钥抽屉 */
  const openKeys = (record: ScrmOpenApiApp) => {
    setKeysApp(record);
    setKeys([]);
    loadKeys(record);
  };

  /** 新建密钥 */
  const handleCreateKey = async () => {
    if (!keysApp) return;
    const values = await keyForm.validateFields();
    setKeySaving(true);
    try {
      await apiClient.post(`/scrm/open-api/apps/${keysApp.id}/keys`, values);
      message.success('密钥已创建');
      setKeyOpen(false);
      loadKeys(keysApp);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setKeySaving(false);
    }
  };

  const columns: ColumnsType<ScrmOpenApiApp> = useMemo(() => [
    { title: '应用名称', dataIndex: 'appName', render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
    { title: '应用类型', dataIndex: 'appType', width: 130, render: (v: string) => appTypeConfig[v] || v || '-' },
    { title: 'Client ID', dataIndex: 'clientId', width: 150, render: (v?: string) => (v ? <Text code>{v}</Text> : '-') },
    { title: '每分钟限流', dataIndex: 'rateLimitPerMinute', width: 110, render: (v?: number) => v ?? '-' },
    { title: '每日限流', dataIndex: 'rateLimitPerDay', width: 100, render: (v?: number) => v ?? '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => { const c = appStatusConfig[v] || { color: 'default', label: v }; return <Tag color={c.color}>{c.label}</Tag>; },
    },
    { title: '创建时间', dataIndex: 'createTime', width: 140, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
    {
      title: '操作',
      key: 'actions',
      width: 200,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          <Button type="link" size="small" icon={<KeyOutlined />} onClick={() => openKeys(r)}>
            密钥
          </Button>
          {r.status === 'ACTIVE' && (
            <Button type="link" size="small" danger icon={<StopOutlined />} onClick={() => handleSuspend(r)}>
              暂停
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
    <div className="open-api-apps-page">
      <Card
        title="OpenAPI 应用管理"
        extra={
          <Space>
            <Input
              placeholder="搜索应用名称"
              prefix={<SearchOutlined />}
              allowClear
              style={{ width: 200 }}
              onChange={e => { setKeyword(e.target.value); setAppPage(0); }}
            />
            <Button icon={<ReloadOutlined />} onClick={() => loadApps()} />
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              新建应用
            </Button>
          </Space>
        }
      >
        <Table
          rowKey="id"
          columns={columns}
          dataSource={apps}
          loading={appsLoading}
          pagination={{
            current: appPage + 1,
            pageSize: appPageSize,
            total: appTotal,
            showTotal: t => `共 ${t} 条`,
            onChange: p => setAppPage(p - 1),
          }}
          scroll={{ y: 'calc(100vh - 320px)' }}
        />
      </Card>

      {/* 应用弹窗 */}
      <Modal
        title={editing ? '编辑应用' : '新建应用'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={560}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="appName" label="应用名称" rules={[{ required: true, message: '请输入应用名称' }]}>
            <Input placeholder="如: 合作伙伴集成" maxLength={200} />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="appType" label="应用类型" rules={[{ required: true }]}>
              <Select style={{ width: 140 }} options={Object.entries(appTypeConfig).map(([v, l]) => ({ value: v, label: l }))} />
            </Form.Item>
            <Form.Item name="redirectUris" label="回调 URI">
              <Input placeholder="https://..." style={{ width: 220 }} />
            </Form.Item>
          </Space>
          <Form.Item name="scopes" label="权限范围 (逗号分隔)">
            <Input placeholder="如: customer:read,order:write" />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="rateLimitPerMinute" label="每分钟限流">
              <Input type="number" style={{ width: 110 }} />
            </Form.Item>
            <Form.Item name="rateLimitPerDay" label="每日限流">
              <Input type="number" style={{ width: 110 }} />
            </Form.Item>
          </Space>
          <Form.Item name="ipWhitelist" label="IP 白名单">
            <Input placeholder="逗号分隔 IP" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="应用说明" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 密钥抽屉 */}
      <Drawer
        title={keysApp ? `API 密钥 - ${keysApp.appName}` : 'API 密钥'}
        open={!!keysApp}
        onClose={() => setKeysApp(null)}
        width={560}
      >
        <div style={{ marginBottom: 12 }}>
          <Button type="primary" size="small" icon={<PlusOutlined />} onClick={() => { keyForm.resetFields(); keyForm.setFieldsValue({ keyType: 'APP' }); setKeyOpen(true); }}>
            新建密钥
          </Button>
        </div>
        <Table
          rowKey="id"
          size="small"
          dataSource={keys}
          loading={keyLoading}
          pagination={false}
          locale={{ emptyText: <Empty description="暂无密钥" /> }}
          columns={[
            { title: 'API Key', dataIndex: 'apiKey', ellipsis: true, render: (v: string) => <Text code>{v}</Text> },
            { title: '类型', dataIndex: 'keyType', width: 80, render: (v?: string) => v || '-' },
            { title: '状态', dataIndex: 'status', width: 80, render: (v?: string) => v || '-' },
            { title: '过期', dataIndex: 'expiresAt', width: 120, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD') : '永久') },
          ]}
        />
      </Drawer>

      {/* 密钥弹窗 */}
      <Modal
        title="新建 API 密钥"
        open={keyOpen}
        onCancel={() => setKeyOpen(false)}
        onOk={handleCreateKey}
        confirmLoading={keySaving}
      >
        <Form form={keyForm} layout="vertical">
          <Form.Item name="keyType" label="密钥类型">
            <Select options={[{ value: 'APP', label: '应用级' }, { value: 'USER', label: '用户级' }, { value: 'SERVICE', label: '服务级' }]} />
          </Form.Item>
          <Form.Item name="scopes" label="权限范围">
            <Input placeholder="如: customer:read" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}