/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Blacklist.tsx
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
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
} from 'antd';
import {
  PlusOutlined,
  ReloadOutlined,
  DeleteOutlined,
  SearchOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

/** 目标类型映射 */
const targetTypeConfig: Record<string, { color: string; label: string }> = {
  CUSTOMER: { color: 'blue', label: '客户' },
  ACCOUNT: { color: 'cyan', label: '员工账号' },
  PHONE: { color: 'gold', label: '手机号' },
  DEVICE: { color: 'purple', label: '设备号' },
  IP: { color: 'geekblue', label: 'IP' },
};

/** 名单类型映射 */
const listTypeConfig: Record<string, { color: string; label: string }> = {
  BLACK: { color: 'red', label: '黑名单' },
  GRAY: { color: 'orange', label: '灰名单' },
};

/** 状态映射 */
const statusConfig: Record<string, { color: string; label: string }> = {
  ACTIVE: { color: 'red', label: '生效中' },
  RELEASED: { color: 'default', label: '已解除' },
  EXPIRED: { color: 'default', label: '已过期' },
};

/** 风险黑名单行 */
interface ScrmBlacklistEntry {
  id: string;
  targetType: string;
  targetValue: string;
  listType: string;
  reason?: string;
  status: string;
  createTime?: string;
  expireAt?: string;
}

/** 校验结果 */
interface ScrmBlacklistCheckResult {
  targetType: string;
  targetValue: string;
  inBlacklist: boolean;
  listType?: string;
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
 * 风险黑名单页
 * <p>
 * 名单 CRUD + 状态流转 (加入/移除) + 命中校验, 覆盖风险名单全生命周期。
 * </p>
 *
 * @author Hsi Chu
 */
export default function Blacklist() {
  const { message } = App.useApp();
  /** 列表 */
  const [list, setList] = useState<ScrmBlacklistEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const pageSize = 10;
  /** 加入弹窗 */
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  /** 校验弹窗 */
  const [checkOpen, setCheckOpen] = useState(false);
  const [checking, setChecking] = useState(false);
  const [checkResult, setCheckResult] = useState<ScrmBlacklistCheckResult | null>(null);
  const [checkForm] = Form.useForm();

  /** 加载列表 */
  const loadList = useCallback(async (targetPage = page) => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(pageSize) });
      const data = await apiClient.get<Page<ScrmBlacklistEntry>>(`/scrm/risk/blacklist/list?${params.toString()}`);
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

  /** 打开加入弹窗 */
  const openCreate = () => {
    form.resetFields();
    form.setFieldsValue({ targetType: 'CUSTOMER', listType: 'BLACK' });
    setOpen(true);
  };

  /** 提交加入 */
  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      await apiClient.post('/scrm/risk/blacklist', values);
      message.success('已加入名单');
      setOpen(false);
      setPage(0);
      loadList(0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setSaving(false);
    }
  };

  /** 移除 */
  const handleDelete = async (record: ScrmBlacklistEntry) => {
    try {
      await apiClient.delete(`/scrm/risk/blacklist/${record.id}`);
      message.success('已移出名单');
      loadList();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 打开校验弹窗 */
  const openCheck = () => {
    checkForm.resetFields();
    checkForm.setFieldsValue({ targetType: 'CUSTOMER' });
    setCheckResult(null);
    setCheckOpen(true);
  };

  /** 校验 */
  const handleCheck = async () => {
    const values = await checkForm.validateFields();
    setChecking(true);
    try {
      const data = await apiClient.post<ScrmBlacklistCheckResult>('/scrm/risk/blacklist/check', values);
      setCheckResult(data);
      message.success(data.inBlacklist ? '命中黑名单' : '未命中黑名单');
    } catch {
      // 拦截器已弹出错误
    } finally {
      setChecking(false);
    }
  };

  /** 表格列 */
  const columns: ColumnsType<ScrmBlacklistEntry> = useMemo(() => [
    {
      title: '目标类型',
      dataIndex: 'targetType',
      width: 100,
      render: (v: string) => {
        const cfg = targetTypeConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '目标值', dataIndex: 'targetValue', width: 200, render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
    {
      title: '名单类型',
      dataIndex: 'listType',
      width: 100,
      render: (v: string) => {
        const cfg = listTypeConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '原因', dataIndex: 'reason', ellipsis: true, render: (v?: string) => v || '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => {
        const cfg = statusConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '加入时间', dataIndex: 'createTime', width: 140, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
    { title: '过期时间', dataIndex: 'expireAt', width: 140, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '永久') },
    {
      title: '操作',
      key: 'actions',
      width: 120,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          <Popconfirm title="确定移出黑名单?" onConfirm={() => handleDelete(r)}>
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              移除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  return (
    <div className="blacklist-page">
      <Card
        title="风险黑名单"
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => loadList()} />
            <Button icon={<SafetyCertificateOutlined />} onClick={openCheck}>
              校验
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              加入名单
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

      {/* 加入弹窗 */}
      <Modal
        title="加入名单"
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
      >
        <Form form={form} layout="vertical">
          <Space size={16} wrap>
            <Form.Item name="targetType" label="目标类型" rules={[{ required: true }]}>
              <Select style={{ width: 160 }} options={Object.entries(targetTypeConfig).map(([v, c]) => ({ value: v, label: c.label }))} />
            </Form.Item>
            <Form.Item name="listType" label="名单类型" rules={[{ required: true }]}>
              <Select style={{ width: 140 }} options={Object.entries(listTypeConfig).map(([v, c]) => ({ value: v, label: c.label }))} />
            </Form.Item>
          </Space>
          <Form.Item name="targetValue" label="目标值" rules={[{ required: true, message: '请输入目标值' }]}>
            <Input placeholder="如: 客户ID / 手机号 / 设备号 / IP" maxLength={200} />
          </Form.Item>
          <Form.Item name="reason" label="原因">
            <Input.TextArea rows={2} placeholder="加入原因" />
          </Form.Item>
          <Form.Item name="expireAt" label="过期时间 (ISO, 留空永久)">
            <Input placeholder="如 2026-12-31T23:59:59" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 校验弹窗 */}
      <Modal
        title="黑名单校验"
        open={checkOpen}
        onCancel={() => setCheckOpen(false)}
        onOk={handleCheck}
        confirmLoading={checking}
      >
        <Form form={checkForm} layout="vertical">
          <Form.Item name="targetType" label="目标类型" rules={[{ required: true }]}>
            <Select options={Object.entries(targetTypeConfig).map(([v, c]) => ({ value: v, label: c.label }))} />
          </Form.Item>
          <Form.Item name="targetValue" label="目标值" rules={[{ required: true, message: '请输入目标值' }]}>
            <Input placeholder="待校验目标值" maxLength={200} />
          </Form.Item>
        </Form>
        {checkResult && (
          <div style={{ marginTop: 12 }}>
            <pre style={{ background: '#f5f5f5', padding: 12, borderRadius: 8, fontSize: 12 }}>
              {JSON.stringify(checkResult, null, 2)}
            </pre>
          </div>
        )}
      </Modal>
    </div>
  );
}
