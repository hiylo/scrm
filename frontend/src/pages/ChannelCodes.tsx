/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ChannelCodes.tsx
 * Date : 2026/10/08 12:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  App,
  Button,
  Card,
  DatePicker,
  Drawer,
  Empty,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
} from 'antd';
import {
  PlusOutlined,
  ReloadOutlined,
  StopOutlined,
  BarChartOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

/** 活码类型映射 */
const codeTypeConfig: Record<string, { color: string; label: string }> = {
  SINGLE: { color: 'blue', label: '单账号' },
  MULTI: { color: 'purple', label: '多账号' },
  ROUND_ROBIN: { color: 'cyan', label: '轮询' },
};

/** 状态映射 */
const statusConfig: Record<string, { color: string; label: string }> = {
  ACTIVE: { color: 'green', label: '启用' },
  INACTIVE: { color: 'default', label: '停用' },
};

/** 扫码添加状态映射 */
const addedConfig: Record<string, { color: string; label: string }> = {
  ADDED: { color: 'green', label: '已添加' },
  PENDING: { color: 'orange', label: '待添加' },
  REJECTED: { color: 'red', label: '已拒绝' },
};

/** 渠道活码实体 */
interface ScrmChannelCode {
  id: string;
  codeName: string;
  codeType: string;
  platformType: string;
  status: string;
  scanCount?: number;
  addCount?: number;
  createTime?: string;
}

/** 统计 VO */
interface ScrmChannelCodeStats {
  channelCodeId: string;
  codeName: string;
  codeType: string;
  platformType: string;
  status: string;
  scanCount?: number;
  addedCount?: number;
  pendingCount?: number;
  rejectedCount?: number;
  conversionRate?: number;
}

/** 扫码记录 */
interface ScrmChannelScan {
  id: string;
  scannerNickname?: string;
  scannerUid?: string;
  assignedAccountId?: string;
  added?: string;
  scannedAt?: string;
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
 * 渠道活码页
 * <p>
 * 活码 CRUD + 启停 + 扫码统计与扫码记录抽屉。
 * </p>
 *
 * @author Hsi Chu
 */
export default function ChannelCodes() {
  const { message } = App.useApp();
  /** 列表 */
  const [list, setList] = useState<ScrmChannelCode[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const pageSize = 10;
  /** 弹窗 */
  const [editing, setEditing] = useState<ScrmChannelCode | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  /** 统计抽屉 */
  const [statsCode, setStatsCode] = useState<ScrmChannelCode | null>(null);
  const [stats, setStats] = useState<ScrmChannelCodeStats | null>(null);
  const [statsLoading, setStatsLoading] = useState(false);
  const [scans, setScans] = useState<ScrmChannelScan[]>([]);
  const [scansTotal, setScansTotal] = useState(0);
  const [scansPage, setScansPage] = useState(0);
  const scansPageSize = 10;

  /** 加载列表 */
  const loadList = useCallback(async (targetPage = page) => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(pageSize) });
      const data = await apiClient.get<Page<ScrmChannelCode>>(`/scrm/channel-codes?${params.toString()}`);
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
    setOpen(true);
  };

  /** 提交 */
  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await apiClient.put(`/scrm/channel-codes/${editing.id}`, values);
        message.success('活码已更新');
      } else {
        await apiClient.post('/scrm/channel-codes', values);
        message.success('活码已创建');
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

  /** 停用 */
  const handleDeactivate = async (record: ScrmChannelCode) => {
    try {
      await apiClient.post(`/scrm/channel-codes/${record.id}/deactivate`);
      message.success('活码已停用');
      loadList();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 打开统计抽屉 */
  const openStats = async (record: ScrmChannelCode) => {
    setStatsCode(record);
    setStatsLoading(true);
    setScansPage(0);
    setScans([]);
    setScansTotal(0);
    try {
      const statsData = await apiClient.get<ScrmChannelCodeStats>(`/scrm/channel-codes/${record.id}/stats`);
      setStats(statsData || null);
    } catch {
      setStats(null);
    } finally {
      setStatsLoading(false);
    }
    loadScans(record.id, 0);
  };

  /** 加载扫码记录 */
  const loadScans = async (codeId: string, targetPage: number) => {
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(scansPageSize) });
      const data = await apiClient.get<Page<ScrmChannelScan>>(`/scrm/channel-codes/${codeId}/scans?${params.toString()}`);
      setScans(prev => (targetPage === 0 ? data.content || [] : [...prev, ...(data.content || [])]));
      setScansTotal(data.totalElements || 0);
      setScansPage(targetPage);
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 加载更早的扫码记录 */
  const loadMoreScans = async () => {
    if (!statsCode) return;
    await loadScans(statsCode.id, scansPage + 1);
  };

  /** 表格列 */
  const columns: ColumnsType<ScrmChannelCode> = useMemo(() => [
    { title: '活码名称', dataIndex: 'codeName', render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
    {
      title: '类型',
      dataIndex: 'codeType',
      width: 100,
      render: (v: string) => {
        const cfg = codeTypeConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '平台', dataIndex: 'platformType', width: 100, render: (v: string) => (v === 'wework' ? '企业微信' : v) },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => {
        const cfg = statusConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '扫码/添加',
      width: 110,
      render: (_, r) => (
        <Space size={8}>
          <span>扫 {r.scanCount ?? 0}</span>
          <span style={{ color: '#10b981' }}>加 {r.addCount ?? 0}</span>
        </Space>
      ),
    },
    { title: '创建时间', dataIndex: 'createTime', width: 140, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
    {
      title: '操作',
      key: 'actions',
      width: 140,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          <Button type="link" size="small" icon={<BarChartOutlined />} onClick={() => openStats(r)}>
            统计
          </Button>
          {r.status === 'ACTIVE' && (
            <Button type="link" size="small" danger icon={<StopOutlined />} onClick={() => handleDeactivate(r)}>
              停用
            </Button>
          )}
        </Space>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  return (
    <div className="channel-codes-page">
      <Card
        title="渠道活码"
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => loadList()} />
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              创建活码
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

      {/* 创建/编辑弹窗 */}
      <Modal
        title={editing ? '编辑活码' : '创建活码'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={640}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="codeName" label="活码名称" rules={[{ required: true, message: '请输入活码名称' }]}>
            <Input placeholder="如: 朋友圈扫码 - 618 活动" maxLength={200} />
          </Form.Item>
          <Form.Item name="platformType" label="平台类型" initialValue="wework" rules={[{ required: true, message: '请选择平台' }]}>
            <Select
              options={[
                { value: 'wework', label: '企业微信' },
                { value: 'wechat_personal', label: '个人微信' },
              ]}
            />
          </Form.Item>
          <Form.Item name="codeType" label="活码类型" initialValue="SINGLE" rules={[{ required: true, message: '请选择类型' }]}>
            <Select
              options={Object.entries(codeTypeConfig).map(([value, cfg]) => ({ value, label: cfg.label }))}
              onChange={() => form.setFieldsValue({ redirectAccountId: undefined })}
            />
          </Form.Item>
          <Form.Item name="redirectAccountId" label="重定向账号 ID (SINGLE 类型)">
            <Input placeholder="账号 ID" />
          </Form.Item>
          <Form.Item name="assignRule" label="分配规则 JSON (MULTI/轮询)">
            <Input.TextArea rows={3} placeholder='[{"accountId":1,"weight":50},{"accountId":2,"weight":50}]' />
          </Form.Item>
          <Form.Item name="welcomeMessage" label="欢迎语">
            <Input.TextArea rows={2} placeholder="扫码后自动发送的欢迎语 (支持 ${nickname} 变量)" />
          </Form.Item>
          <Form.Item name="tags" label="标签 (逗号分隔)">
            <Input placeholder="如: 朋友圈渠道,618活动" />
          </Form.Item>
          <Form.Item name="expireAt" label="过期时间">
            <DatePicker showTime style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 统计抽屉 */}
      <Drawer
        title={statsCode ? `扫码统计 - ${statsCode.codeName}` : '扫码统计'}
        open={!!statsCode}
        onClose={() => setStatsCode(null)}
        width={640}
      >
        {statsLoading ? (
          <div style={{ textAlign: 'center', padding: 48 }}>
            <Spin />
          </div>
        ) : (
          stats && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
                <Statistic title="累计扫码" value={stats.scanCount} />
                <Statistic title="已添加" value={stats.addedCount} valueStyle={{ color: '#10b981' }} />
                <Statistic title="待添加" value={stats.pendingCount} valueStyle={{ color: '#f59e0b' }} />
                <Statistic title="已拒绝" value={stats.rejectedCount} valueStyle={{ color: '#ef4444' }} />
                <Statistic title="转化率" value={stats.conversionRate} suffix="%" precision={1} />
              </div>
              <Table
                rowKey="id"
                size="small"
                dataSource={scans}
                pagination={false}
                locale={{ emptyText: <Empty description="暂无扫码记录" /> }}
                columns={[
                  { title: '扫码者', dataIndex: 'scannerNickname', render: (v?: string) => v || '-' },
                  { title: '扫码者 UID', dataIndex: 'scannerUid', ellipsis: true, render: (v?: string) => v || '-' },
                  { title: '分配账号', dataIndex: 'assignedAccountId', render: (v?: string) => v || '-' },
                  { title: '添加状态', dataIndex: 'added', width: 90, render: (v: string) => { const cfg = addedConfig[v] || { color: 'default', label: v }; return <Tag color={cfg.color}>{cfg.label}</Tag>; } },
                  { title: '扫码时间', dataIndex: 'scannedAt', width: 160, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
                ]}
              />
              {scans.length < scansTotal && (
                <div style={{ textAlign: 'center' }}>
                  <Button type="link" onClick={loadMoreScans}>
                    加载更早 ({scans.length}/{scansTotal})
                  </Button>
                </div>
              )}
            </div>
          )
        )}
      </Drawer>
    </div>
  );
}
