/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : MessageTemplateCenter.tsx
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
  Tabs,
  Tag,
  Typography,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  ReloadOutlined,
  SearchOutlined,
  CloudUploadOutlined,
  CopyOutlined,
  InboxOutlined,
  StopOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { apiClient } from '../api/client';

const { Text } = Typography;

/** 模板分组 */
interface ScrmTemplateGroup {
  id: string;
  groupName: string;
  groupCode: string;
  description?: string;
  groupType: string;
  parentGroupId?: string;
  sortOrder?: number;
  templateCount?: number;
  enabled?: boolean;
  color?: string;
  icon?: string;
}

/** 消息模板中心 */
interface ScrmTemplateCenter {
  id: string;
  templateName: string;
  templateCode: string;
  groupId?: string;
  groupName?: string;
  description?: string;
  templateType: string;
  channels?: string;
  subject?: string;
  content: string;
  category?: string;
  tags?: string;
  applicableScenarios?: string;
  language?: string;
  status: string; // DRAFT / PENDING_REVIEW / PUBLISHED / ARCHIVED / REJECTED
  versionNumber?: number;
  reviewStatus?: string;
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

/** 分组类型 */
const groupTypeConfig: Record<string, string> = {
  CATEGORY: '分类',
  SCENARIO: '场景',
  CHANNEL: '渠道',
  CUSTOM: '自定义',
};

/** 模板类型 */
const templateTypeConfig: Record<string, { color: string; label: string }> = {
  MARKETING: { color: 'green', label: '营销' },
  NOTIFICATION: { color: 'blue', label: '通知' },
  SERVICE: { color: 'cyan', label: '服务' },
  SYSTEM: { color: 'default', label: '系统' },
  GREETING: { color: 'purple', label: '问候' },
};

/** 状态映射 */
const statusConfig: Record<string, { color: string; label: string }> = {
  DRAFT: { color: 'default', label: '草稿' },
  PENDING_REVIEW: { color: 'orange', label: '待审核' },
  PUBLISHED: { color: 'green', label: '已发布' },
  ARCHIVED: { color: 'default', label: '已归档' },
  REJECTED: { color: 'red', label: '已驳回' },
};

/**
 * 消息模板中心页
 * <p>
 * 统一消息模板资产库: 模板分组 (分类/场景/渠道, 树形) + 消息模板
 * (营销/通知/服务/系统, 多渠道, 版本与审核状态) + 发布/归档/复制。
 * </p>
 *
 * @author Hsi Chu
 */
export default function MessageTemplateCenter() {
  const { message } = App.useApp();
  /** 分组 */
  const [groups, setGroups] = useState<ScrmTemplateGroup[]>([]);
  const [groupsLoading, setGroupsLoading] = useState(false);
  const [groupTotal, setGroupTotal] = useState(0);
  const [groupPage, setGroupPage] = useState(0);
  const groupPageSize = 10;
  /** 模板 */
  const [templates, setTemplates] = useState<ScrmTemplateCenter[]>([]);
  const [templatesLoading, setTemplatesLoading] = useState(false);
  const [templateTotal, setTemplateTotal] = useState(0);
  const [templatePage, setTemplatePage] = useState(0);
  const templatePageSize = 10;
  const [keyword, setKeyword] = useState('');
  const [statusFilter, setStatusFilter] = useState<string | undefined>();
  /** 分组弹窗 */
  const [groupEditing, setGroupEditing] = useState<ScrmTemplateGroup | null>(null);
  const [groupOpen, setGroupOpen] = useState(false);
  const [groupSaving, setGroupSaving] = useState(false);
  const [groupForm] = Form.useForm();
  /** 模板弹窗 */
  const [editing, setEditing] = useState<ScrmTemplateCenter | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  /** 加载分组 */
  const loadGroups = useCallback(async (targetPage = groupPage) => {
    setGroupsLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(groupPageSize) });
      const data = await apiClient.get<Page<ScrmTemplateGroup>>(`/scrm/message-template-center/groups/list?${params.toString()}`);
      setGroups(data.content || []);
      setGroupTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setGroupsLoading(false);
    }
  }, [groupPage]);

  useEffect(() => {
    loadGroups();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [groupPage]);

  /** 加载模板 */
  const loadTemplates = useCallback(async (targetPage = templatePage, k = keyword) => {
    setTemplatesLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(templatePageSize) });
      if (k) params.set('keyword', k);
      if (statusFilter) params.set('status', statusFilter);
      const data = await apiClient.get<Page<ScrmTemplateCenter>>(`/scrm/message-template-center/list?${params.toString()}`);
      setTemplates(data.content || []);
      setTemplateTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setTemplatesLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [templatePage, statusFilter]);

  useEffect(() => {
    loadTemplates();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [templatePage, statusFilter]);

  /** 打开创建分组 */
  const openCreateGroup = () => {
    setGroupEditing(null);
    groupForm.resetFields();
    groupForm.setFieldsValue({ groupType: 'CATEGORY', enabled: true });
    setGroupOpen(true);
  };

  /** 打开编辑分组 */
  const openEditGroup = (record: ScrmTemplateGroup) => {
    setGroupEditing(record);
    groupForm.setFieldsValue(record);
    setGroupOpen(true);
  };

  /** 提交分组 */
  const handleSubmitGroup = async () => {
    const values = await groupForm.validateFields();
    setGroupSaving(true);
    try {
      if (groupEditing) {
        await apiClient.put(`/scrm/message-template-center/groups/${groupEditing.id}`, values);
        message.success('分组已更新');
      } else {
        await apiClient.post('/scrm/message-template-center/groups', values);
        message.success('分组已创建');
      }
      setGroupOpen(false);
      setGroupPage(0);
      loadGroups(0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setGroupSaving(false);
    }
  };

  /** 删除分组 */
  const handleDeleteGroup = async (record: ScrmTemplateGroup) => {
    try {
      await apiClient.delete(`/scrm/message-template-center/groups/${record.id}`);
      message.success('分组已删除');
      loadGroups();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 启停分组 */
  const handleToggleGroup = async (record: ScrmTemplateGroup, toEnable: boolean) => {
    try {
      await apiClient.post(`/scrm/message-template-center/groups/${record.id}/${toEnable ? 'enable' : 'disable'}`);
      message.success(toEnable ? '分组已启用' : '分组已停用');
      loadGroups();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 打开创建模板 */
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ templateType: 'MARKETING', channels: 'IN_APP', status: 'DRAFT' });
    setOpen(true);
  };

  /** 打开编辑模板 */
  const openEdit = (record: ScrmTemplateCenter) => {
    setEditing(record);
    form.setFieldsValue(record);
    setOpen(true);
  };

  /** 提交模板 */
  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await apiClient.put(`/scrm/message-template-center/${editing.id}`, values);
        message.success('模板已更新');
      } else {
        await apiClient.post('/scrm/message-template-center', values);
        message.success('模板已创建');
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

  /** 删除模板 */
  const handleDelete = async (record: ScrmTemplateCenter) => {
    try {
      await apiClient.delete(`/scrm/message-template-center/${record.id}`);
      message.success('模板已删除');
      loadTemplates();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 模板动作 */
  const handleAction = async (record: ScrmTemplateCenter, action: string) => {
    const labelMap: Record<string, string> = { publish: '发布', archive: '归档', duplicate: '复制' };
    try {
      await apiClient.post(`/scrm/message-template-center/${record.id}/${action}`);
      message.success(`模板已${labelMap[action] || action}`);
      loadTemplates();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 分组列 */
  const groupColumns: ColumnsType<ScrmTemplateGroup> = useMemo(() => [
    {
      title: '分组名称',
      dataIndex: 'groupName',
      render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span>,
    },
    { title: '编码', dataIndex: 'groupCode', width: 120, render: (v: string) => <Text code>{v}</Text> },
    { title: '类型', dataIndex: 'groupType', width: 100, render: (v: string) => groupTypeConfig[v] || v },
    { title: '排序', dataIndex: 'sortOrder', width: 70, render: (v?: number) => v ?? 0 },
    { title: '模板数', dataIndex: 'templateCount', width: 80, render: (v?: number) => v ?? 0 },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 80,
      render: (v?: boolean) => (v ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>),
    },
    {
      title: '操作',
      key: 'actions',
      width: 180,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEditGroup(r)}>
            编辑
          </Button>
          {r.enabled ? (
            <Button type="link" size="small" danger icon={<StopOutlined />} onClick={() => handleToggleGroup(r, false)}>
              停用
            </Button>
          ) : (
            <Button type="link" size="small" onClick={() => handleToggleGroup(r, true)}>
              启用
            </Button>
          )}
          <Popconfirm title="确定删除该分组?" onConfirm={() => handleDeleteGroup(r)}>
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  /** 模板列 */
  const columns: ColumnsType<ScrmTemplateCenter> = useMemo(() => [
    {
      title: '模板名称',
      dataIndex: 'templateName',
      render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span>,
    },
    { title: '编码', dataIndex: 'templateCode', width: 110, render: (v: string) => <Text code>{v}</Text> },
    {
      title: '类型',
      dataIndex: 'templateType',
      width: 90,
      render: (v: string) => {
        const cfg = templateTypeConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '分组', dataIndex: 'groupName', width: 100, render: (v?: string) => v || '-' },
    { title: '渠道', dataIndex: 'channels', width: 100, render: (v?: string) => v || '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => {
        const cfg = statusConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '版本', dataIndex: 'versionNumber', width: 70, render: (v?: number) => `v${v ?? 1}` },
    {
      title: '操作',
      key: 'actions',
      width: 230,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          {r.status === 'DRAFT' && (
            <Button type="link" size="small" icon={<CloudUploadOutlined />} onClick={() => handleAction(r, 'publish')}>
              发布
            </Button>
          )}
          {r.status === 'PUBLISHED' && (
            <Button type="link" size="small" icon={<InboxOutlined />} onClick={() => handleAction(r, 'archive')}>
              归档
            </Button>
          )}
          <Button type="link" size="small" icon={<CopyOutlined />} onClick={() => handleAction(r, 'duplicate')}>
            复制
          </Button>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(r)}>
            编辑
          </Button>
          <Popconfirm title="确定删除该模板?" onConfirm={() => handleDelete(r)}>
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  return (
    <div className="message-template-center-page">
      <Tabs
        items={[
          {
            key: 'templates',
            label: '消息模板',
            children: (
              <Card
                title="消息模板中心"
                extra={
                  <Space>
                    <Input
                      placeholder="搜索模板名称/编码"
                      prefix={<SearchOutlined />}
                      allowClear
                      style={{ width: 200 }}
                      onChange={e => {
                        setKeyword(e.target.value);
                        setTemplatePage(0);
                      }}
                    />
                    <Select
                      placeholder="状态"
                      allowClear
                      style={{ width: 110 }}
                      options={Object.entries(statusConfig).map(([v, c]) => ({ value: v, label: c.label }))}
                      onChange={v => { setStatusFilter(v); setTemplatePage(0); }}
                    />
                    <Button icon={<ReloadOutlined />} onClick={() => loadTemplates()} />
                    <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                      新建模板
                    </Button>
                  </Space>
                }
              >
                <Table
                  rowKey="id"
                  columns={columns}
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
            key: 'groups',
            label: '模板分组',
            children: (
              <Card
                title="模板分组"
                extra={
                  <Space>
                    <Button icon={<ReloadOutlined />} onClick={() => loadGroups()} />
                    <Button type="primary" icon={<PlusOutlined />} onClick={openCreateGroup}>
                      新建分组
                    </Button>
                  </Space>
                }
              >
                <Table
                  rowKey="id"
                  columns={groupColumns}
                  dataSource={groups}
                  loading={groupsLoading}
                  pagination={{
                    current: groupPage + 1,
                    pageSize: groupPageSize,
                    total: groupTotal,
                    showTotal: t => `共 ${t} 条`,
                    onChange: p => setGroupPage(p - 1),
                  }}
                />
              </Card>
            ),
          },
        ]}
      />

      {/* 模板弹窗 */}
      <Modal
        title={editing ? '编辑模板' : '新建模板'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={600}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="templateName" label="模板名称" rules={[{ required: true, message: '请输入模板名称' }]}>
            <Input placeholder="如: 订单支付成功通知" maxLength={200} />
          </Form.Item>
          <Form.Item name="templateCode" label="模板编码" rules={[{ required: true, message: '请输入模板编码' }]}>
            <Input placeholder="如: ORDER_PAID" maxLength={100} />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="templateType" label="模板类型" rules={[{ required: true }]}>
              <Select style={{ width: 150 }} options={Object.entries(templateTypeConfig).map(([v, c]) => ({ value: v, label: c.label }))} />
            </Form.Item>
            <Form.Item name="channels" label="适用渠道" rules={[{ required: true }]}>
              <Input placeholder="如: IN_APP,SMS" style={{ width: 180 }} />
            </Form.Item>
          </Space>
          <Form.Item name="content" label="模板内容" rules={[{ required: true, message: '请输入模板内容' }]}>
            <Input.TextArea rows={4} placeholder="支持 ${nickname} 变量" />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="category" label="分类">
              <Input placeholder="如: 订单" style={{ width: 150 }} />
            </Form.Item>
            <Form.Item name="language" label="语言">
              <Select style={{ width: 120 }} options={[{ value: 'zh-CN', label: '中文' }, { value: 'en-US', label: '英文' }]} />
            </Form.Item>
          </Space>
          <Form.Item name="applicableScenarios" label="适用场景">
            <Input placeholder="如: 支付完成" />
          </Form.Item>
          <Form.Item name="tags" label="标签 (逗号分隔)">
            <Input placeholder="如: 订单,通知" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 分组弹窗 */}
      <Modal
        title={groupEditing ? '编辑分组' : '新建分组'}
        open={groupOpen}
        onCancel={() => setGroupOpen(false)}
        onOk={handleSubmitGroup}
        confirmLoading={groupSaving}
      >
        <Form form={groupForm} layout="vertical">
          <Form.Item name="groupName" label="分组名称" rules={[{ required: true, message: '请输入分组名称' }]}>
            <Input placeholder="如: 订单类通知" maxLength={200} />
          </Form.Item>
          <Form.Item name="groupCode" label="分组编码" rules={[{ required: true, message: '请输入分组编码' }]}>
            <Input placeholder="如: ORDER_NOTIFY" maxLength={50} />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="groupType" label="分组类型" rules={[{ required: true }]}>
              <Select style={{ width: 140 }} options={Object.entries(groupTypeConfig).map(([v, l]) => ({ value: v, label: l }))} />
            </Form.Item>
            <Form.Item name="sortOrder" label="排序">
              <Input type="number" style={{ width: 110 }} />
            </Form.Item>
          </Space>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="分组说明" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}