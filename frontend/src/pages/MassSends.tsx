/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : MassSends.tsx
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
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  ReloadOutlined,
  SearchOutlined,
  SendOutlined,
  EyeOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

const { Text } = Typography;

/** 群发任务实体 */
interface ScrmMassSend {
  id: string;
  taskName: string;
  platformType?: string; // wework / wechat_personal
  targetType: string; // ALL / TAG / LIST / SEGMENT
  targetFilter?: string;
  senderAccountId?: string;
  content?: string;
  messageTemplateId?: string;
  scheduledAt?: string;
  status: string; // DRAFT / RUNNING / COMPLETED / FAILED / CANCELLED
  totalCount?: number;
  sentCount?: number;
  successCount?: number;
  failCount?: number;
  createTime?: string;
}

/** 群发目标明细 */
interface ScrmMassSendTarget {
  id: string;
  taskId: string;
  customerId?: string;
  customerNickname?: string;
  platformCustomerUid?: string;
  status: string; // PENDING / SENT / FAILED
  errorMessage?: string;
  sentAt?: string;
}

/** 群发任务报告 */
interface ScrmMassSendReport {
  taskId: string;
  taskName: string;
  status: string;
  totalCount: number;
  sentCount: number;
  successCount: number;
  failCount: number;
  pendingCount: number;
  successRate: number;
  failureRate: number;
}

/** Spring Page 分页响应 */
interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/** 任务状态映射 */
const statusConfig: Record<string, { color: string; label: string }> = {
  DRAFT: { color: 'default', label: '草稿' },
  RUNNING: { color: 'processing', label: '发送中' },
  COMPLETED: { color: 'green', label: '已完成' },
  FAILED: { color: 'red', label: '失败' },
  CANCELLED: { color: 'orange', label: '已取消' },
};

/** 目标类型映射 */
const targetTypeConfig: Record<string, { label: string }> = {
  ALL: { label: '全部客户' },
  TAG: { label: '按标签' },
  LIST: { label: '按名单' },
  SEGMENT: { label: '按生命周期' },
};

/** 目标发送状态映射 */
const sendStatusConfig: Record<string, { color: string; label: string }> = {
  PENDING: { color: 'orange', label: '待发送' },
  SENT: { color: 'green', label: '已发送' },
  FAILED: { color: 'red', label: '失败' },
};

/**
 * 群发任务管理页
 * <p>
 * 面向企业微信/个人微信的批量群发; 支持任务创建、发布、目标明细(分页) 与发送报告。
 * </p>
 *
 * @author Hsi Chu
 */
export default function MassSends() {
  const { message } = App.useApp();
  /** 列表 */
  const [list, setList] = useState<ScrmMassSend[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const pageSize = 10;
  const [keyword, setKeyword] = useState('');
  /** 弹窗 */
  const [editing, setEditing] = useState<ScrmMassSend | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  /** 目标明细抽屉 */
  const [targetsTask, setTargetsTask] = useState<ScrmMassSend | null>(null);
  const [targets, setTargets] = useState<ScrmMassSendTarget[]>([]);
  const [targetsLoading, setTargetsLoading] = useState(false);
  const [targetsTotal, setTargetsTotal] = useState(0);
  const targetsPageSize = 20;
  const [report, setReport] = useState<ScrmMassSendReport | null>(null);

  const loadList = useCallback(async (targetPage = page, k = keyword) => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(pageSize) });
      if (k) params.set('keyword', k);
      const data = await apiClient.get<Page<ScrmMassSend>>(`/scrm/mass-send?${params.toString()}`);
      setList(data.content || []);
      setTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setLoading(false);
    }
  }, [page, keyword]);

  useEffect(() => {
    loadList();
  }, [loadList]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ platformType: 'wework', targetType: 'ALL' });
    setOpen(true);
  };

  const openEdit = (record: ScrmMassSend) => {
    setEditing(record);
    form.setFieldsValue(record);
    setOpen(true);
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    // ALL 目标类型无需筛选, 清零避免误提交
    if (values.targetType === 'ALL') values.targetFilter = undefined;
    setSaving(true);
    try {
      if (editing) {
        await apiClient.put(`/scrm/mass-send/${editing.id}`, values);
        message.success('任务已更新');
      } else {
        await apiClient.post('/scrm/mass-send', values);
        message.success('任务已创建');
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

  const handlePublish = async (record: ScrmMassSend) => {
    try {
      await apiClient.post(`/scrm/mass-send/${record.id}/publish`);
      message.success('任务已发布');
      loadList();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 打开目标明细抽屉: 并行加载报告与明细 */
  const openTargets = async (record: ScrmMassSend) => {
    setTargetsTask(record);
    setTargets([]);
    setReport(null);
    setTargetsLoading(true);
    try {
      const params = new URLSearchParams({ page: '0', size: String(targetsPageSize) });
      const [rep, tPage] = await Promise.all([
        apiClient.get<ScrmMassSendReport>(`/scrm/mass-send/${record.id}/report`),
        apiClient.get<Page<ScrmMassSendTarget>>(`/scrm/mass-send/${record.id}/targets?${params.toString()}`),
      ]);
      setReport(rep);
      setTargets(tPage.content || []);
      setTargetsTotal(tPage.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setTargetsLoading(false);
    }
  };

  const loadMoreTargets = async () => {
    if (!targetsTask) return;
    setTargetsLoading(true);
    try {
      const params = new URLSearchParams({ page: String(Math.floor(targets.length / targetsPageSize)), size: String(targetsPageSize) });
      const data = await apiClient.get<Page<ScrmMassSendTarget>>(`/scrm/mass-send/${targetsTask.id}/targets?${params.toString()}`);
      setTargets(prev => [...prev, ...(data.content || [])]);
      setTargetsTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setTargetsLoading(false);
    }
  };

  const columns: ColumnsType<ScrmMassSend> = useMemo(() => [
    { title: '任务名称', dataIndex: 'taskName', render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
    { title: '平台', dataIndex: 'platformType', width: 100, render: (v?: string) => (v === 'wework' ? '企业微信' : v === 'wechat_personal' ? '个人微信' : v || '-') },
    { title: '目标类型', dataIndex: 'targetType', width: 100, render: (v: string) => targetTypeConfig[v]?.label || v || '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => { const c = statusConfig[v] || { color: 'default', label: v }; return <Tag color={c.color}>{c.label}</Tag>; },
    },
    {
      title: '发送进度',
      key: 'progress',
      width: 150,
      render: (_, r) => (r.totalCount ? `已发 ${r.sentCount ?? 0}/${r.totalCount} (${Math.round(((r.sentCount ?? 0) / r.totalCount) * 100)}%)` : '-'),
    },
    { title: '创建时间', dataIndex: 'createTime', width: 140, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
    {
      title: '操作',
      key: 'actions',
      width: 160,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          {r.status === 'DRAFT' && (
            <Button type="link" size="small" icon={<SendOutlined />} onClick={() => handlePublish(r)}>
              发布
            </Button>
          )}
          <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => openTargets(r)}>
            明细
          </Button>
          {r.status === 'DRAFT' && (
            <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(r)}>
              编辑
            </Button>
          )}
        </Space>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  return (
    <div className="mass-sends-page">
      <Card
        title="群发任务"
        extra={
          <Space>
            <Input
              placeholder="搜索任务名称"
              prefix={<SearchOutlined />}
              allowClear
              style={{ width: 200 }}
              onChange={e => { setKeyword(e.target.value); setPage(0); }}
            />
            <Button icon={<ReloadOutlined />} onClick={() => loadList()} />
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              创建任务
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

      {/* 创建/编辑弹窗 */}
      <Modal
        title={editing ? '编辑任务' : '创建群发任务'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={640}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="taskName" label="任务名称" rules={[{ required: true, message: '请输入任务名称' }]}>
            <Input placeholder="如: 618 客户关怀群发" maxLength={200} />
          </Form.Item>
          <Form.Item name="platformType" label="平台类型" rules={[{ required: true, message: '请选择平台' }]}>
            <Select
              options={[
                { value: 'wework', label: '企业微信' },
                { value: 'wechat_personal', label: '个人微信' },
              ]}
            />
          </Form.Item>
          <Form.Item name="targetType" label="目标类型" rules={[{ required: true, message: '请选择目标类型' }]}>
            <Select
              options={Object.entries(targetTypeConfig).map(([value, cfg]) => ({ value, label: cfg.label }))}
              onChange={() => form.setFieldsValue({ targetFilter: undefined })}
            />
          </Form.Item>
          <Form.Item
            name="targetFilter"
            label="目标筛选"
            extra={
              <Text type="secondary" style={{ fontSize: 12 }}>
                TAG: 逗号分隔 tagKey / LIST: 逗号分隔 customerId / SEGMENT: lifecycle (如 ACTIVE)
              </Text>
            }
          >
            <Input placeholder="如: vip,618" />
          </Form.Item>
          <Form.Item name="senderAccountId" label="发送账号 ID" rules={[{ required: true, message: '请输入发送账号 ID' }]}>
            <Input placeholder="账号 ID" />
          </Form.Item>
          <Form.Item name="content" label="群发内容" rules={[{ required: true, message: '请输入群发内容' }]}>
            <Input.TextArea rows={4} placeholder="群发文本内容, 支持 ${nickname} 变量" maxLength={5000} />
          </Form.Item>
          <Form.Item name="messageTemplateId" label="关联消息模板 ID (可选)">
            <Input placeholder="消息模板 ID" />
          </Form.Item>
          <Form.Item name="scheduledAt" label="计划发送时间 (可选, ISO 格式)">
            <Input placeholder="如 2026-10-09T10:00:00" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 目标明细抽屉 */}
      <Drawer
        title={targetsTask ? `目标明细 - ${targetsTask.taskName}` : '目标明细'}
        open={!!targetsTask}
        onClose={() => setTargetsTask(null)}
        width={640}
      >
        {report && (
          <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', marginBottom: 16 }}>
            <Statistic title="目标总数" value={report.totalCount} />
            <Statistic title="已发送" value={report.sentCount} />
            <Statistic title="成功" value={report.successCount} valueStyle={{ color: '#10b981' }} />
            <Statistic title="失败" value={report.failCount} valueStyle={{ color: '#ef4444' }} />
            <Statistic title="成功率" value={report.successRate} suffix="%" precision={1} />
          </div>
        )}
        <Table
          rowKey="id"
          size="small"
          dataSource={targets}
          loading={targetsLoading}
          pagination={false}
          locale={{ emptyText: <Empty description="暂无目标明细" /> }}
          columns={[
            { title: '客户', dataIndex: 'customerNickname', width: 120, render: (v?: string) => v || '-' },
            { title: '平台客户 UID', dataIndex: 'platformCustomerUid', ellipsis: true, render: (v?: string) => v || '-' },
            { title: '状态', dataIndex: 'status', width: 90, render: (v: string) => { const cfg = sendStatusConfig[v] || { color: 'default', label: v }; return <Tag color={cfg.color}>{cfg.label}</Tag>; } },
            { title: '失败原因', dataIndex: 'errorMessage', ellipsis: true, render: (v?: string) => v || '-' },
            { title: '发送时间', dataIndex: 'sentAt', width: 160, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
          ]}
        />
        {targets.length < targetsTotal && (
          <div style={{ textAlign: 'center' }}>
            <Button type="link" onClick={loadMoreTargets}>
              加载更早 ({targets.length}/{targetsTotal})
            </Button>
          </div>
        )}
      </Drawer>
    </div>
  );
}