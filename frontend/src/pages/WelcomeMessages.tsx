/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : WelcomeMessages.tsx
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
  Spin,
  Statistic,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd';
import {
  PlusOutlined,
  ReloadOutlined,
  EyeOutlined,
  BarChartOutlined,
  SendOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { apiClient } from '../api/client';

const { Text } = Typography;

/** 消息类型映射 */
const messageTypeConfig: Record<string, { color: string; label: string }> = {
  TEXT: { color: 'blue', label: '文本' },
  IMAGE: { color: 'cyan', label: '图片' },
  LINK: { color: 'purple', label: '链接' },
  CARD: { color: 'green', label: '卡片' },
  VIDEO: { color: 'orange', label: '视频' },
};

/** 欢迎语规则状态映射 */
const statusConfig: Record<string, { color: string; label: string }> = {
  ACTIVE: { color: 'green', label: '启用' },
  DISABLED: { color: 'default', label: '停用' },
};

/** 欢迎语规则实体 */
interface ScrmWelcomeRule {
  id: string;
  ruleName: string;
  messageType: string;
  platformType?: string;
  accountId?: string;
  channelCodeId?: string;
  content?: string;
  mediaUrl?: string;
  linkTitle?: string;
  linkUrl?: string;
  linkDesc?: string;
  delaySeconds?: number;
  cooldownMinutes?: number;
  priority?: number;
  weekendEnabled?: boolean;
  secondaryMessages?: string;
  status: string;
  triggerCount?: number;
  createTime?: string;
}

/** 触发预览结果 */
interface ScrmWelcomePreview {
  ruleId?: string;
  ruleName?: string;
  renderedContent?: string;
  messageType?: string;
  delaySeconds?: number;
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
 * 欢迎语配置页
 * <p>
 * 新客欢迎语规则 CRUD + 触发预览 + 触发统计, 覆盖渠道活码/账号级欢迎语全场景。
 * </p>
 *
 * @author Hsi Chu
 */
export default function WelcomeMessages() {
  const { message } = App.useApp();
  /** 列表 */
  const [list, setList] = useState<ScrmWelcomeRule[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const pageSize = 10;
  /** 创建/编辑弹窗 */
  const [editing, setEditing] = useState<ScrmWelcomeRule | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  /** 预览弹窗 */
  const [previewRule, setPreviewRule] = useState<ScrmWelcomeRule | null>(null);
  const [preview, setPreview] = useState<ScrmWelcomePreview | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  /** 统计弹窗 */
  const [statsRule, setStatsRule] = useState<ScrmWelcomeRule | null>(null);
  const [stats, setStats] = useState<Record<string, number> | null>(null);
  const [statsLoading, setStatsLoading] = useState(false);

  /** 加载欢迎语规则列表 */
  const loadList = useCallback(async (targetPage = page) => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(pageSize) });
      const data = await apiClient.get<Page<ScrmWelcomeRule>>(`/scrm/welcome-messages?${params.toString()}`);
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

  /** 打开新建 */
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ messageType: 'TEXT', platformType: 'wework', priority: 0 });
    setOpen(true);
  };

  /** 打开编辑 */
  const openEdit = (record: ScrmWelcomeRule) => {
    setEditing(record);
    form.setFieldsValue(record);
    setOpen(true);
  };

  /** 提交新建/编辑 */
  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await apiClient.put(`/scrm/welcome-messages/${editing.id}`, values);
        message.success('欢迎语规则已更新');
      } else {
        await apiClient.post('/scrm/welcome-messages', values);
        message.success('欢迎语规则已创建');
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

  /** 触发预览 */
  const handlePreview = async (record: ScrmWelcomeRule) => {
    setPreviewRule(record);
    setPreview(null);
    setPreviewLoading(true);
    try {
      const data = await apiClient.post<ScrmWelcomePreview>('/scrm/welcome-messages/trigger', { ruleId: record.id });
      setPreview(data);
    } catch {
      setPreview(null);
    } finally {
      setPreviewLoading(false);
    }
  };

  /** 加载触发统计 */
  const handleStats = async (record: ScrmWelcomeRule) => {
    setStatsRule(record);
    setStats(null);
    setStatsLoading(true);
    try {
      const data = await apiClient.get<Record<string, number>>(`/scrm/welcome-messages/${record.id}/stats`);
      setStats(data || {});
    } catch {
      setStats(null);
    } finally {
      setStatsLoading(false);
    }
  };

  /** 表格列 */
  const columns: ColumnsType<ScrmWelcomeRule> = useMemo(() => [
    { title: '规则名称', dataIndex: 'ruleName', render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
    {
      title: '消息类型',
      dataIndex: 'messageType',
      width: 100,
      render: (v: string) => {
        const cfg = messageTypeConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '平台', dataIndex: 'platformType', width: 110, render: (v?: string) => (v === 'wework' ? '企业微信' : v === 'wechat_personal' ? '个人微信' : v || '-') },
    { title: '平台账号', dataIndex: 'accountId', width: 110, render: (v?: string) => v || '全部' },
    { title: '渠道活码', dataIndex: 'channelCodeId', width: 110, render: (v?: string) => v || '-' },
    { title: '文本内容', dataIndex: 'content', ellipsis: true, render: (v?: string) => v || '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (v?: string) => {
        const cfg = statusConfig[v || ''] || { color: 'default', label: v || '-' };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '触发次数', dataIndex: 'triggerCount', width: 90, render: (v?: number) => v ?? 0 },
    { title: '延迟(秒)', dataIndex: 'delaySeconds', width: 80, render: (v?: number) => v ?? 0 },
    {
      title: '操作',
      key: 'actions',
      width: 140,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => handlePreview(r)}>
            预览
          </Button>
          <Button type="link" size="small" icon={<BarChartOutlined />} onClick={() => handleStats(r)}>
            统计
          </Button>
          <Button type="link" size="small" icon={<SendOutlined />} onClick={() => openEdit(r)}>
            编辑
          </Button>
        </Space>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  return (
    <div className="welcome-messages-page">
      <Card
        title="欢迎语配置"
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => loadList()} />
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              新建规则
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
        title={editing ? '编辑规则' : '新建规则'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={720}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="ruleName" label="规则名称" rules={[{ required: true, message: '请输入规则名称' }]}>
            <Input placeholder="如: 新客欢迎 - 渠道活码" maxLength={200} />
          </Form.Item>
          <Form.Item name="messageType" label="消息类型" rules={[{ required: true, message: '请选择消息类型' }]}>
            <Select options={Object.entries(messageTypeConfig).map(([value, cfg]) => ({ value, label: cfg.label }))} />
          </Form.Item>
          <Form.Item name="platformType" label="平台类型" initialValue="wework">
            <Select
              options={[
                { value: 'wework', label: '企业微信' },
                { value: 'wechat_personal', label: '个人微信' },
              ]}
            />
          </Form.Item>
          <Form.Item name="accountId" label="绑定账号 ID (空=所有账号)">
            <Input placeholder="账号 ID" />
          </Form.Item>
          <Form.Item name="channelCodeId" label="绑定渠道活码 ID (空=非渠道来源)">
            <Input placeholder="渠道活码 ID" />
          </Form.Item>
          <Form.Item name="content" label="文本内容" rules={[{ required: true, message: '请输入文本内容' }]}>
            <Input.TextArea rows={3} placeholder="支持 ${nickname} 变量, 如: 您好 ${nickname}, 欢迎关注我们!" />
          </Form.Item>
          <Form.Item name="mediaUrl" label="图片/文件 URL">
            <Input placeholder="https://..." />
          </Form.Item>
          <Form.Item name="linkTitle" label="链接标题">
            <Input placeholder="链接标题" />
          </Form.Item>
          <Form.Item name="linkUrl" label="链接 URL">
            <Input placeholder="https://..." />
          </Form.Item>
          <Form.Item name="linkDesc" label="链接描述">
            <Input placeholder="链接描述" />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="delaySeconds" label="延迟发送 (秒)">
              <InputNumber min={0} style={{ width: 120 }} />
            </Form.Item>
            <Form.Item name="cooldownMinutes" label="冷却期 (分钟)">
              <InputNumber min={0} style={{ width: 120 }} />
            </Form.Item>
            <Form.Item name="priority" label="优先级">
              <InputNumber min={0} style={{ width: 100 }} />
            </Form.Item>
            <Form.Item name="weekendEnabled" label="周末生效" valuePropName="checked">
              <Switch />
            </Form.Item>
          </Space>
          <Form.Item name="secondaryMessages" label="二次触达消息序列 (JSON)">
            <Input.TextArea rows={2} placeholder='[{"delaySeconds":300,"content":"期待您的回复"}]' />
          </Form.Item>
        </Form>
      </Modal>

      {/* 预览弹窗 */}
      <Modal
        title={previewRule ? `触发预览 - ${previewRule.ruleName}` : '触发预览'}
        open={!!previewRule}
        onCancel={() => setPreviewRule(null)}
        footer={null}
      >
        {previewLoading ? (
          <div style={{ textAlign: 'center', padding: 24 }}>
            <Spin />
          </div>
        ) : preview ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <Statistic title="渲染后内容" value={preview.renderedContent || ''} valueStyle={{ fontSize: 16 }} />
            <Text type="secondary">消息类型: {preview.messageType || '-'} / 延迟: {preview.delaySeconds ?? 0}s</Text>
          </div>
        ) : (
          <Empty description="未匹配到触发内容" />
        )}
      </Modal>

      {/* 统计弹窗 */}
      <Modal
        title={statsRule ? `触发统计 - ${statsRule.ruleName}` : '触发统计'}
        open={!!statsRule}
        onCancel={() => setStatsRule(null)}
        footer={null}
        width={420}
      >
        {statsLoading ? (
          <div style={{ textAlign: 'center', padding: 24 }}>
            <Spin />
          </div>
        ) : stats ? (
          <div style={{ display: 'flex', gap: 24, flexWrap: 'wrap' }}>
            {Object.entries(stats).map(([k, v]) => (
              <Statistic key={k} title={k} value={v} />
            ))}
          </div>
        ) : (
          <Empty description="暂无统计数据" />
        )}
      </Modal>
    </div>
  );
}