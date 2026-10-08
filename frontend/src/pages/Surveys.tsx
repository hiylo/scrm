/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Surveys.tsx
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
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  ReloadOutlined,
  SearchOutlined,
  PlayCircleOutlined,
  PauseCircleOutlined,
  CopyOutlined,
  InboxOutlined,
  SendOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

const { Text } = Typography;

/** 问卷实体 */
interface ScrmSurvey {
  id: string;
  surveyName: string;
  surveyType: string; // NPS / CSAT / CES / CUSTOM
  description?: string;
  title: string;
  introText?: string;
  outroText?: string;
  questions: string;
  scaleType?: string;
  triggerEvent?: string;
  status: string; // DRAFT / ACTIVE / PAUSED / COMPLETED / ARCHIVED
  responseCount?: number;
  startDate?: string;
  endDate?: string;
  createTime?: string;
}

/** 邀请记录 */
interface ScrmSurveyInvitation {
  id: string;
  surveyId: string;
  customerId?: string;
  customerName?: string;
  invitationCode: string;
  status: string;
  sentAt?: string;
}

/** Spring Page 分页响应 */
interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/** 问卷类型映射 */
const surveyTypeConfig: Record<string, { color: string; label: string }> = {
  NPS: { color: 'geekblue', label: 'NPS 净推荐' },
  CSAT: { color: 'cyan', label: 'CSAT 满意度' },
  CES: { color: 'purple', label: 'CES 费力度' },
  CUSTOM: { color: 'default', label: '自定义' },
};

/** 状态映射 */
const statusConfig: Record<string, { color: string; label: string }> = {
  DRAFT: { color: 'default', label: '草稿' },
  ACTIVE: { color: 'green', label: '进行中' },
  PAUSED: { color: 'orange', label: '已暂停' },
  COMPLETED: { color: 'blue', label: '已完成' },
  ARCHIVED: { color: 'default', label: '已归档' },
};

/** 邀请状态映射 */
const inviteStatusConfig: Record<string, { color: string; label: string }> = {
  PENDING: { color: 'orange', label: '待发送' },
  SENT: { color: 'blue', label: '已发送' },
  RESPONDED: { color: 'green', label: '已回复' },
  REMINDED: { color: 'purple', label: '已提醒' },
  EXPIRED: { color: 'default', label: '已过期' },
};

/**
 * NPS 问卷管理页
 * <p>
 * 客户体验调研 (NPS/CSAT/CES): 问卷 CRUD + 状态流转 (草稿→进行中→暂停→完成→归档)
 * + 邀请码生成与批量邀请 + 问卷复制。
 * </p>
 *
 * @author Hsi Chu
 */
export default function Surveys() {
  const { message } = App.useApp();
  /** 列表 */
  const [list, setList] = useState<ScrmSurvey[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const pageSize = 10;
  const [keyword, setKeyword] = useState('');
  const [statusFilter, setStatusFilter] = useState<string | undefined>();
  /** 编辑弹窗 */
  const [editing, setEditing] = useState<ScrmSurvey | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  /** 邀请弹窗 */
  const [inviteOpen, setInviteOpen] = useState(false);
  const [inviting, setInviting] = useState(false);
  const [inviteForm] = Form.useForm();
  /** 邀请记录弹窗 */
  const [inviteSur, setInviteSur] = useState<ScrmSurvey | null>(null);
  const [invitations, setInvitations] = useState<ScrmSurveyInvitation[]>([]);

  /** 加载列表 */
  const loadList = useCallback(async (targetPage = page, k = keyword) => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(pageSize) });
      if (k) params.set('keyword', k);
      if (statusFilter) params.set('status', statusFilter);
      const data = await apiClient.get<Page<ScrmSurvey>>(`/scrm/surveys/list?${params.toString()}`);
      setList(data.content || []);
      setTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, statusFilter]);

  useEffect(() => {
    loadList();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, statusFilter]);

  /** 打开创建 */
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ surveyType: 'NPS', scaleType: '0-10', status: 'DRAFT', questions: '[]' });
    setOpen(true);
  };

  /** 打开编辑 */
  const openEdit = (record: ScrmSurvey) => {
    setEditing(record);
    form.setFieldsValue(record);
    setOpen(true);
  };

  /** 提交问卷 */
  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await apiClient.put(`/scrm/surveys/${editing.id}`, values);
        message.success('问卷已更新');
      } else {
        await apiClient.post('/scrm/surveys', values);
        message.success('问卷已创建');
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

  /** 删除问卷 */
  const handleDelete = async (record: ScrmSurvey) => {
    try {
      await apiClient.delete(`/scrm/surveys/${record.id}`);
      message.success('问卷已删除');
      loadList();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 状态流转 */
  const changeStatus = async (record: ScrmSurvey, toStatus: string) => {
    try {
      await apiClient.post(`/scrm/surveys/${record.id}/${toStatus}`);
      message.success(`问卷状态已变更为 ${statusConfig[toStatus]?.label || toStatus}`);
      loadList();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 复制问卷 */
  const handleCopy = async (record: ScrmSurvey) => {
    try {
      await apiClient.post(`/scrm/surveys/${record.id}/copy`);
      message.success('问卷已复制');
      loadList();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 生成邀请 */
  const handleInvite = async () => {
    const values = await inviteForm.validateFields();
    setInviting(true);
    try {
      const result = await apiClient.post<{ successCount?: number }>('/scrm/surveys/invitations/batch', values);
      message.success(`已生成 ${result.successCount ?? 0} 个邀请`);
      setInviteOpen(false);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setInviting(false);
    }
  };

  /** 查看邀请记录 */
  const loadInvitations = async (record: ScrmSurvey) => {
    setInviteSur(record);
    try {
      const data = await apiClient.get<ScrmSurveyInvitation[]>(`/scrm/surveys/invitations/survey/${record.id}`);
      setInvitations(data || []);
    } catch {
      setInvitations([]);
    }
  };

  /** 表格列 */
  const columns: ColumnsType<ScrmSurvey> = useMemo(() => [
    {
      title: '问卷名称',
      dataIndex: 'surveyName',
      render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span>,
    },
    {
      title: '类型',
      dataIndex: 'surveyType',
      width: 120,
      render: (v: string) => {
        const cfg = surveyTypeConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '问卷标题',
      dataIndex: 'title',
      ellipsis: true,
      render: (v: string) => <Text type="secondary">{v}</Text>,
    },
    { title: '量表', dataIndex: 'scaleType', width: 90, render: (v?: string) => v || '-' },
    { title: '回收', dataIndex: 'responseCount', width: 80, render: (v?: number) => v ?? 0 },
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
      width: 320,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          <Button type="link" size="small" icon={<InboxOutlined />} onClick={() => loadInvitations(r)}>
            邀请记录
          </Button>
          {r.status === 'DRAFT' && (
            <Button type="link" size="small" icon={<PlayCircleOutlined />} onClick={() => changeStatus(r, 'activate')}>
              发布
            </Button>
          )}
          {r.status === 'ACTIVE' && (
            <Button type="link" size="small" icon={<PauseCircleOutlined />} onClick={() => changeStatus(r, 'pause')}>
              暂停
            </Button>
          )}
          {r.status === 'PAUSED' && (
            <Button type="link" size="small" icon={<PlayCircleOutlined />} onClick={() => changeStatus(r, 'activate')}>
              恢复
            </Button>
          )}
          {r.status !== 'COMPLETED' && r.status !== 'ARCHIVED' && (
            <Button type="link" size="small" onClick={() => changeStatus(r, 'complete')}>
              完成
            </Button>
          )}
          <Button type="link" size="small" icon={<CopyOutlined />} onClick={() => handleCopy(r)}>
            复制
          </Button>
          <Button type="link" size="small" icon={<SendOutlined />} onClick={() => { inviteForm.resetFields(); inviteForm.setFieldsValue({ surveyId: r.id }); setInviteOpen(true); }}>
            邀请
          </Button>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(r)}>
            编辑
          </Button>
          <Popconfirm title="确定删除该问卷?" onConfirm={() => handleDelete(r)}>
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
    <div className="surveys-page">
      <Card
        title="NPS / 满意度问卷"
        extra={
          <Space>
            <Input
              placeholder="搜索问卷名称"
              prefix={<SearchOutlined />}
              allowClear
              style={{ width: 200 }}
              onChange={e => {
                setKeyword(e.target.value);
                setPage(0);
              }}
            />
            <Select
              placeholder="状态"
              allowClear
              style={{ width: 110 }}
              options={Object.entries(statusConfig).map(([v, c]) => ({ value: v, label: c.label }))}
              onChange={v => { setStatusFilter(v); setPage(0); }}
            />
            <Button icon={<ReloadOutlined />} onClick={() => loadList()} />
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              新建问卷
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

      {/* 问卷弹窗 */}
      <Modal
        title={editing ? '编辑问卷' : '新建问卷'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={640}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="surveyName" label="问卷名称" rules={[{ required: true, message: '请输入问卷名称' }]}>
            <Input placeholder="如: 6月 NPS 调研" maxLength={200} />
          </Form.Item>
          <Form.Item name="title" label="问卷标题" rules={[{ required: true, message: '请输入问卷标题' }]}>
            <Input placeholder="如: 您有多大可能向朋友推荐我们?" maxLength={200} />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="surveyType" label="类型" rules={[{ required: true }]}>
              <Select style={{ width: 160 }} options={Object.entries(surveyTypeConfig).map(([v, c]) => ({ value: v, label: c.label }))} />
            </Form.Item>
            <Form.Item name="scaleType" label="量表">
              <Select
                style={{ width: 140 }}
                options={[
                  { value: '0-10', label: '0-10 分' },
                  { value: '1-5', label: '1-5 分' },
                  { value: '1-7', label: '1-7 分' },
                ]}
              />
            </Form.Item>
          </Space>
          <Form.Item name="questions" label="题目 JSON" rules={[{ required: true, message: '请输入题目 JSON' }]}>
            <Input.TextArea rows={3} placeholder='[{"type":"single","title":"推荐意愿","scale":"0-10"}]' />
          </Form.Item>
          <Form.Item name="introText" label="开场文案">
            <Input.TextArea rows={2} placeholder="问卷开场说明" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 邀请弹窗 */}
      <Modal
        title="生成问卷邀请"
        open={inviteOpen}
        onCancel={() => setInviteOpen(false)}
        onOk={handleInvite}
        confirmLoading={inviting}
      >
        <Form form={inviteForm} layout="vertical">
          <Form.Item name="surveyId" label="问卷 ID" rules={[{ required: true, message: '请输入问卷 ID' }]}>
            <Input placeholder="问卷 ID" disabled={!!inviteForm.getFieldValue('surveyId')} />
          </Form.Item>
          <Form.Item name="quantity" label="邀请数量" rules={[{ required: true, message: '请输入邀请数量' }]}>
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 邀请记录弹窗 */}
      <Modal
        title={inviteSur ? `邀请记录 - ${inviteSur.surveyName}` : '邀请记录'}
        open={!!inviteSur}
        onCancel={() => setInviteSur(null)}
        footer={null}
        width={640}
      >
        <Table
          rowKey="id"
          size="small"
          dataSource={invitations}
          pagination={false}
          locale={{ emptyText: <Empty description="暂无邀请记录" /> }}
          columns={[
            { title: '客户', dataIndex: 'customerName', width: 120, render: (v?: string) => v || '-' },
            { title: '邀请码', dataIndex: 'invitationCode', width: 120, render: (v: string) => <Text code>{v}</Text> },
            {
              title: '状态',
              dataIndex: 'status',
              width: 90,
              render: (v: string) => {
                const cfg = inviteStatusConfig[v] || { color: 'default', label: v };
                return <Tag color={cfg.color}>{cfg.label}</Tag>;
              },
            },
            { title: '发送时间', dataIndex: 'sentAt', width: 150, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
          ]}
        />
      </Modal>
    </div>
  );
}