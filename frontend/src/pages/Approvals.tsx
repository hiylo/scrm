/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Approvals.tsx
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
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import {
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  HistoryOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

const { Text } = Typography;

/** 审批流状态映射 */
const flowStatusConfig: Record<string, { color: string; label: string }> = {
  ACTIVE: { color: 'green', label: '启用' },
  INACTIVE: { color: 'default', label: '停用' },
  DRAFT: { color: 'orange', label: '草稿' },
};

/** 审批实例状态映射 */
const instanceStatusConfig: Record<string, { color: string; label: string }> = {
  PENDING: { color: 'orange', label: '待审批' },
  APPROVING: { color: 'processing', label: '审批中' },
  APPROVED: { color: 'green', label: '已通过' },
  REJECTED: { color: 'red', label: '已驳回' },
  CANCELLED: { color: 'default', label: '已撤销' },
};

/** 审批动作映射 */
const actionConfig: Record<string, { color: string; label: string }> = {
  SUBMIT: { color: 'blue', label: '提交' },
  APPROVE: { color: 'green', label: '通过' },
  REJECT: { color: 'red', label: '驳回' },
  WITHDRAW: { color: 'default', label: '撤回' },
};

/** 审批流实体 */
interface ScrmApprovalFlow {
  id: string;
  flowName: string;
  flowCode: string;
  flowType?: string;
  applicableModule?: string;
  status: string;
  description?: string;
  createTime?: string;
}

/** 审批实例实体 */
interface ScrmApprovalInstance {
  id: string;
  instanceNo: string;
  flowId?: string;
  flowName?: string;
  businessType?: string;
  title: string;
  status: string;
  applicantName?: string;
  submittedAt?: string;
}

/** 审批日志 */
interface ScrmApprovalLog {
  id: string;
  instanceId: string;
  nodeName?: string;
  action?: string;
  operatorName?: string;
  comment?: string;
  createdAt?: string;
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
 * 审批中心页
 * <p>
 * 审批流配置 (CRUD) + 审批实例 (通过/驳回) + 审批日志时间线抽屉。
 * </p>
 *
 * @author Hsi Chu
 */
export default function Approvals() {
  const { message } = App.useApp();
  /** 审批流列表 */
  const [flows, setFlows] = useState<ScrmApprovalFlow[]>([]);
  const [flowsLoading, setFlowsLoading] = useState(false);
  const [flowTotal, setFlowTotal] = useState(0);
  const [flowPage, setFlowPage] = useState(0);
  const flowPageSize = 10;
  /** 审批实例列表 */
  const [instances, setInstances] = useState<ScrmApprovalInstance[]>([]);
  const [instancesLoading, setInstancesLoading] = useState(false);
  const [instanceTotal, setInstanceTotal] = useState(0);
  const [instancePage, setInstancePage] = useState(0);
  const instancePageSize = 10;
  const [instanceKeyword, setInstanceKeyword] = useState('');
  /** 审批流弹窗 */
  const [editing, setEditing] = useState<ScrmApprovalFlow | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  /** 审批操作弹窗 */
  const [actionTarget, setActionTarget] = useState<ScrmApprovalInstance | null>(null);
  const [actionType, setActionType] = useState<'APPROVE' | 'REJECT'>('APPROVE');
  const [actionComment, setActionComment] = useState('');
  const [actionSaving, setActionSaving] = useState(false);
  /** 日志抽屉 */
  const [logInstance, setLogInstance] = useState<ScrmApprovalInstance | null>(null);
  const [logs, setLogs] = useState<ScrmApprovalLog[]>([]);
  const [logsLoading, setLogsLoading] = useState(false);

  /** 加载审批流 */
  const loadFlows = useCallback(async (targetPage = flowPage) => {
    setFlowsLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(flowPageSize) });
      const data = await apiClient.get<Page<ScrmApprovalFlow>>(`/scrm/approvals/flows/list?${params.toString()}`);
      setFlows(data.content || []);
      setFlowTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setFlowsLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [flowPage]);

  /** 加载审批实例 */
  const loadInstances = useCallback(async (targetPage = instancePage, keyword = instanceKeyword) => {
    setInstancesLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(instancePageSize) });
      if (keyword) params.set('keyword', keyword);
      const data = await apiClient.get<Page<ScrmApprovalInstance>>(`/scrm/approvals/approvals/list?${params.toString()}`);
      setInstances(data.content || []);
      setInstanceTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setInstancesLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [instancePage]);

  useEffect(() => {
    loadFlows();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [flowPage]);

  useEffect(() => {
    loadInstances();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [instancePage]);

  /** 打开创建 */
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ flowType: 'SINGLE' });
    setOpen(true);
  };

  /** 提交审批流 */
  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await apiClient.put(`/scrm/approvals/flows/${editing.id}`, values);
        message.success('审批流已更新');
      } else {
        await apiClient.post('/scrm/approvals/flows', values);
        message.success('审批流已创建');
      }
      setOpen(false);
      setFlowPage(0);
      loadFlows(0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setSaving(false);
    }
  };

  /** 打开审批操作弹窗 */
  const openAction = (record: ScrmApprovalInstance, type: 'APPROVE' | 'REJECT') => {
    setActionTarget(record);
    setActionType(type);
    setActionComment('');
  };

  /** 提交审批动作 */
  const handleAction = async () => {
    if (!actionTarget) return;
    setActionSaving(true);
    try {
      await apiClient.post('/scrm/approvals/actions/approve', {
        instanceId: Number(actionTarget.id),
        comment: actionComment,
      });
      message.success(actionType === 'APPROVE' ? '审批已通过' : '审批已驳回');
      setActionTarget(null);
      loadInstances();
    } catch {
      // 拦截器已弹出错误
    } finally {
      setActionSaving(false);
    }
  };

  /** 打开日志抽屉 */
  const openLogs = async (record: ScrmApprovalInstance) => {
    setLogInstance(record);
    setLogsLoading(true);
    try {
      const data = await apiClient.get<ScrmApprovalLog[]>(`/scrm/approvals/logs/timeline/${record.id}`);
      setLogs(data || []);
    } catch {
      setLogs([]);
    } finally {
      setLogsLoading(false);
    }
  };

  /** 审批流列 */
  const flowColumns: ColumnsType<ScrmApprovalFlow> = useMemo(() => [
    { title: '流程名称', dataIndex: 'flowName', render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
    { title: '流程编码', dataIndex: 'flowCode', width: 160, render: (v: string) => <Text code>{v}</Text> },
    { title: '流程类型', dataIndex: 'flowType', width: 110, render: (v?: string) => v || '-' },
    { title: '适用模块', dataIndex: 'applicableModule', width: 120, render: (v?: string) => v || '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => {
        const cfg = flowStatusConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '创建时间', dataIndex: 'createTime', width: 140, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
  ], []);

  /** 审批实例列 */
  const instanceColumns: ColumnsType<ScrmApprovalInstance> = useMemo(() => [
    { title: '单号', dataIndex: 'instanceNo', width: 150, render: (v: string) => <Text code>{v}</Text> },
    { title: '标题', dataIndex: 'title', render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
    { title: '审批流', dataIndex: 'flowName', width: 130, render: (v?: string) => v || '-' },
    { title: '业务类型', dataIndex: 'businessType', width: 110, render: (v?: string) => v || '-' },
    { title: '申请人', dataIndex: 'applicantName', width: 100, render: (v?: string) => v || '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => {
        const cfg = instanceStatusConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '提交时间', dataIndex: 'submittedAt', width: 140, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
    {
      title: '操作',
      key: 'actions',
      width: 190,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          {r.status === 'PENDING' && (
            <Button type="link" size="small" icon={<CheckCircleOutlined />} onClick={() => openAction(r, 'APPROVE')}>
              通过
            </Button>
          )}
          {r.status === 'PENDING' && (
            <Button type="link" size="small" danger icon={<CloseCircleOutlined />} onClick={() => openAction(r, 'REJECT')}>
              驳回
            </Button>
          )}
          <Button type="link" size="small" icon={<HistoryOutlined />} onClick={() => openLogs(r)}>
            日志
          </Button>
        </Space>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  return (
    <div className="approvals-page">
      <Card title="审批中心" bodyStyle={{ paddingTop: 8 }}>
        <Tabs
          defaultActiveKey="flows"
          items={[
            {
              key: 'flows',
              label: '审批流',
              children: (
                <Card
                  title="审批流配置"
                  extra={
                    <Space>
                      <Button icon={<ReloadOutlined />} onClick={() => loadFlows()} />
                      <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                        新建审批流
                      </Button>
                    </Space>
                  }
                >
                  <Table
                    rowKey="id"
                    columns={flowColumns}
                    dataSource={flows}
                    loading={flowsLoading}
                    pagination={{
                      current: flowPage + 1,
                      pageSize: flowPageSize,
                      total: flowTotal,
                      showTotal: t => `共 ${t} 条`,
                      onChange: p => setFlowPage(p - 1),
                    }}
                  />
                </Card>
              ),
            },
            {
              key: 'instances',
              label: '审批实例',
              children: (
                <Card
                  title="审批实例"
                  extra={
                    <Space>
                      <Input
                        placeholder="搜索单号/标题"
                        prefix={<SearchOutlined />}
                        allowClear
                        style={{ width: 200 }}
                        onChange={e => {
                          setInstanceKeyword(e.target.value);
                          setInstancePage(0);
                        }}
                      />
                      <Button icon={<ReloadOutlined />} onClick={() => loadInstances()} />
                    </Space>
                  }
                >
                  <Table
                    rowKey="id"
                    columns={instanceColumns}
                    dataSource={instances}
                    loading={instancesLoading}
                    pagination={{
                      current: instancePage + 1,
                      pageSize: instancePageSize,
                      total: instanceTotal,
                      showTotal: t => `共 ${t} 条`,
                      onChange: p => setInstancePage(p - 1),
                    }}
                  />
                </Card>
              ),
            },
          ]}
        />
      </Card>

      {/* 审批流弹窗 */}
      <Modal
        title={editing ? '编辑审批流' : '新建审批流'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={560}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="flowName" label="流程名称" rules={[{ required: true, message: '请输入流程名称' }]}>
            <Input placeholder="如: 合同审批流" maxLength={200} />
          </Form.Item>
          <Form.Item name="flowCode" label="流程编码" rules={[{ required: true, message: '请输入流程编码' }]}>
            <Input placeholder="如: CONTRACT_APPROVAL" maxLength={50} />
          </Form.Item>
          <Form.Item name="applicableModule" label="适用模块">
            <Input placeholder="如: CONTRACT" maxLength={100} />
          </Form.Item>
          <Form.Item name="flowType" label="流程类型">
            <Input placeholder="如: SINGLE / MULTI" maxLength={30} />
          </Form.Item>
          <Form.Item name="approverRules" label="审批人规则 JSON">
            <Input.TextArea rows={2} placeholder='[{"node":"财务审批","approver":"u1"}]' />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="流程说明" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 审批操作弹窗 */}
      <Modal
        title={actionTarget ? `${actionType === 'APPROVE' ? '通过' : '驳回'} - ${actionTarget.title}` : '审批操作'}
        open={!!actionTarget}
        onCancel={() => setActionTarget(null)}
        onOk={handleAction}
        confirmLoading={actionSaving}
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Text type="secondary">审批意见</Text>
          <Input.TextArea rows={3} placeholder="审批意见 (可选)" value={actionComment} onChange={e => setActionComment(e.target.value)} />
        </Space>
      </Modal>

      {/* 日志抽屉 */}
      <Drawer
        title={logInstance ? `审批日志 - ${logInstance.instanceNo}` : '审批日志'}
        open={!!logInstance}
        onClose={() => setLogInstance(null)}
        width={560}
      >
        <Table
          rowKey="id"
          size="small"
          dataSource={logs}
          loading={logsLoading}
          pagination={false}
          locale={{ emptyText: <Empty description="暂无日志" /> }}
          columns={[
            { title: '节点', dataIndex: 'nodeName', width: 110, render: (v?: string) => v || '-' },
            {
              title: '动作',
              dataIndex: 'action',
              width: 80,
              render: (v?: string) => {
                const cfg = actionConfig[v || ''] || { color: 'default', label: v || '-' };
                return <Tag color={cfg.color}>{cfg.label}</Tag>;
              },
            },
            { title: '操作人', dataIndex: 'operatorName', width: 100, render: (v?: string) => v || '-' },
            { title: '意见', dataIndex: 'comment', ellipsis: true, render: (v?: string) => v || '-' },
            { title: '时间', dataIndex: 'createdAt', width: 140, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
          ]}
        />
      </Drawer>
    </div>
  );
}
