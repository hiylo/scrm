/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : AutoTags.tsx
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
  Tag,
  Typography,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  ReloadOutlined,
  PlayCircleOutlined,
  PauseCircleOutlined,
  ExperimentOutlined,
  FileTextOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

const { Text } = Typography;

/** 触发事件映射 */
const triggerEventConfig: Record<string, { color: string; label: string }> = {
  CUSTOMER_CREATED: { color: 'green', label: '客户创建' },
  ORDER_PAID: { color: 'blue', label: '订单支付' },
  MESSAGE_RECEIVED: { color: 'cyan', label: '收到消息' },
  LIFECYCLE_CHANGED: { color: 'geekblue', label: '生命周期变更' },
  MANUAL: { color: 'default', label: '手动触发' },
};

/** 条件类型映射 */
const conditionTypeConfig: Record<string, { color: string; label: string }> = {
  ALL: { color: 'blue', label: '全部满足' },
  ANY: { color: 'cyan', label: '任一满足' },
  NONE: { color: 'orange', label: '全部不满足' },
};

/** 动作类型映射 */
const actionTypeConfig: Record<string, { color: string; label: string }> = {
  ADD_TAG: { color: 'green', label: '添加标签' },
  REMOVE_TAG: { color: 'red', label: '移除标签' },
  SET_LIFECYCLE: { color: 'blue', label: '设置生命周期' },
  NOTIFY: { color: 'orange', label: '通知员工' },
  SCORE: { color: 'purple', label: '调整积分' },
};

/** 条件字段元数据 */
interface ConditionFieldMeta {
  name: string;
  description?: string;
}

/** 条件操作符元数据 */
interface ConditionOperatorMeta {
  name: string;
  description?: string;
}

/** 自动打标规则实体 */
interface ScrmAutoTagRule {
  id: string;
  ruleName: string;
  description?: string;
  triggerEvent?: string;
  conditionType?: string;
  conditions?: string;
  actionType?: string;
  actionParams?: string;
  priority?: number;
  enabled?: boolean;
  matchCount?: number;
  createTime?: string;
}

/** 规则执行日志 */
interface ScrmAutoTagLog {
  id: string;
  ruleId?: string;
  customerId?: string;
  customerNickname?: string;
  triggerEvent?: string;
  actionType?: string;
  actionResult?: string;
  actionDetail?: string;
  executedAt?: string;
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
 * 自动打标规则页面
 * 规则 CRUD / 启停 / 手动评估 / 执行日志。
 *
 * @author Hsi Chu
 */
export default function AutoTags() {
  const { message } = App.useApp();
  /** 规则列表 */
  const [list, setList] = useState<ScrmAutoTagRule[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const pageSize = 10;
  const [total, setTotal] = useState(0);
  /** 条件元数据 */
  const [fields, setFields] = useState<ConditionFieldMeta[]>([]);
  const [operators, setOperators] = useState<ConditionOperatorMeta[]>([]);
  /** 规则弹窗 */
  const [editing, setEditing] = useState<ScrmAutoTagRule | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  /** 手动评估弹窗 */
  const [evaluateOpen, setEvaluateOpen] = useState(false);
  const [evaluating, setEvaluating] = useState(false);
  const [evaluateResult, setEvaluateResult] = useState<ScrmAutoTagLog[] | null>(null);
  const [evaluateForm] = Form.useForm();
  /** 执行日志弹窗 */
  const [logsRule, setLogsRule] = useState<ScrmAutoTagRule | null>(null);
  const [logs, setLogs] = useState<ScrmAutoTagLog[]>([]);
  const [logsLoading, setLogsLoading] = useState(false);
  const [logsPage, setLogsPage] = useState(0);
  const [logsTotal, setLogsTotal] = useState(0);

  /** 加载规则列表 */
  const loadList = useCallback(async (targetPage = page) => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(pageSize) });
      const data = await apiClient.get<Page<ScrmAutoTagRule>>(`/scrm/auto-tags/rules/list?${params.toString()}`);
      setList(data.content || []);
      setTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
      setList([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page]);

  /** 加载条件字段与操作符元数据 */
  const loadMeta = useCallback(async () => {
    try {
      const [fieldRows, operatorRows] = await Promise.all([
        apiClient.get<ConditionFieldMeta[]>('/scrm/auto-tags/fields'),
        apiClient.get<ConditionOperatorMeta[]>('/scrm/auto-tags/operators'),
      ]);
      setFields(fieldRows || []);
      setOperators(operatorRows || []);
    } catch {
      // 拦截器已弹出错误
    }
  }, []);

  useEffect(() => {
    loadList();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page]);

  useEffect(() => {
    loadMeta();
  }, [loadMeta]);

  /** 打开新建规则弹窗 */
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({
      triggerEvent: 'CUSTOMER_CREATED',
      conditionType: 'ALL',
      actionType: 'ADD_TAG',
      conditions: '[]',
      actionParams: '{}',
      enabled: true,
    });
    setOpen(true);
  };

  /** 打开编辑规则弹窗 */
  const openEdit = (record: ScrmAutoTagRule) => {
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
        await apiClient.put(`/scrm/auto-tags/rules/${editing.id}`, values);
        message.success('规则已更新');
      } else {
        await apiClient.post('/scrm/auto-tags/rules', values);
        message.success('规则已创建');
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

  /** 启用/停用规则 */
  const handleToggle = async (record: ScrmAutoTagRule, enabled: boolean) => {
    try {
      await apiClient.post(`/scrm/auto-tags/rules/${record.id}/${enabled ? 'enable' : 'disable'}`);
      message.success(enabled ? '规则已启用' : '规则已停用');
      loadList();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 手动评估规则 */
  const handleEvaluate = async () => {
    let values: Record<string, unknown>;
    try {
      values = await evaluateForm.validateFields();
    } catch {
      return;
    }
    setEvaluating(true);
    try {
      const rows = await apiClient.post<ScrmAutoTagLog[]>('/scrm/auto-tags/evaluate', {
        ...values,
        customerId: Number(values.customerId),
      });
      setEvaluateResult(Array.isArray(rows) ? rows : []);
      message.success('评估完成');
    } catch {
      // 拦截器已弹出错误
    } finally {
      setEvaluating(false);
    }
  };

  /** 加载规则执行日志 */
  const loadLogs = useCallback(async (rule: ScrmAutoTagRule, targetPage = 0) => {
    setLogsLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(pageSize) });
      const data = await apiClient.get<Page<ScrmAutoTagLog>>(`/scrm/auto-tags/rules/${rule.id}/logs?${params.toString()}`);
      const rows = data.content || [];
      setLogs(prev => (targetPage > 0 ? [...prev, ...rows] : rows));
      setLogsTotal(data.totalElements || rows.length);
      setLogsPage(targetPage);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setLogsLoading(false);
    }
  }, []);

  /** 打开执行日志弹窗 */
  const openLogs = (record: ScrmAutoTagRule) => {
    setLogsRule(record);
    setLogs([]);
    loadLogs(record, 0);
  };

  /** 表格列 */
  const columns: ColumnsType<ScrmAutoTagRule> = useMemo(() => [
    { title: '规则名称', dataIndex: 'ruleName', width: 160, render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
    {
      title: '触发事件',
      dataIndex: 'triggerEvent',
      width: 130,
      render: (v?: string) => {
        const cfg = triggerEventConfig[v || ''] || { color: 'default', label: v || '-' };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '条件类型',
      dataIndex: 'conditionType',
      width: 110,
      render: (v?: string) => {
        const cfg = conditionTypeConfig[v || ''] || { color: 'default', label: v || '-' };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '条件', dataIndex: 'conditions', ellipsis: true, render: (v?: string) => v || '-' },
    {
      title: '动作',
      dataIndex: 'actionType',
      width: 120,
      render: (v?: string) => {
        const cfg = actionTypeConfig[v || ''] || { color: 'default', label: v || '-' };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '优先级', dataIndex: 'priority', width: 80, render: (v?: number) => v ?? 0 },
    { title: '命中次数', dataIndex: 'matchCount', width: 90, render: (v?: number) => v ?? 0 },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 90,
      render: (v?: boolean) => (v === false ? <Tag>停用</Tag> : <Tag color="green">启用</Tag>),
    },
    {
      title: '操作',
      key: 'actions',
      width: 220,
      fixed: 'right',
      render: (_, r) => (
        <Space size={0}>
          <Button
            type="link"
            size="small"
            icon={r.enabled === false ? <PlayCircleOutlined /> : <PauseCircleOutlined />}
            onClick={() => handleToggle(r, r.enabled === false)}
          >
            {r.enabled === false ? '启用' : '停用'}
          </Button>
          <Button type="link" size="small" icon={<FileTextOutlined />} onClick={() => openLogs(r)}>
            日志
          </Button>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(r)}>
            编辑
          </Button>
        </Space>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  return (
    <div className="auto-tags-page">
      <Card
        title="自动打标规则"
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => loadList()} />
            <Button icon={<ExperimentOutlined />} onClick={() => { evaluateForm.resetFields(); setEvaluateResult(null); setEvaluateOpen(true); }}>
              手动评估
            </Button>
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
          }} scroll={{ y: 'calc(100vh - 320px)' }}
        />
      </Card>

      {/* 创建/编辑弹窗 */}
      <Modal
        title={editing ? '编辑规则' : '新建规则'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={680}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="ruleName" label="规则名称" rules={[{ required: true, message: '请输入规则名称' }]}>
            <Input placeholder="如: 新客自动打「新客户」标签" maxLength={200} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="规则说明" maxLength={500} />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="triggerEvent" label="触发事件" rules={[{ required: true, message: '请选择触发事件' }]}>
              <Select style={{ width: 220 }} options={Object.entries(triggerEventConfig).map(([value, cfg]) => ({ value, label: cfg.label }))} />
            </Form.Item>
            <Form.Item name="conditionType" label="条件类型" rules={[{ required: true, message: '请选择条件类型' }]}>
              <Select style={{ width: 140 }} options={Object.entries(conditionTypeConfig).map(([value, cfg]) => ({ value, label: cfg.label }))} />
            </Form.Item>
          </Space>
          <Form.Item
            name="conditions"
            label="条件 JSON"
            rules={[{ required: true, message: '请输入条件 JSON' }]}
            extra={
              (fields.length || operators.length)
                ? `可用字段: ${fields.map(f => f.name).join(', ') || '-'} / 操作符: ${operators.map(o => o.name).join(', ') || '-'}`
                : undefined
            }
          >
            <Input.TextArea rows={3} placeholder='[{"field":"lifecycle","operator":"eq","value":"NEW"}]' />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="actionType" label="动作类型" rules={[{ required: true, message: '请选择动作类型' }]}>
              <Select style={{ width: 200 }} options={Object.entries(actionTypeConfig).map(([value, cfg]) => ({ value, label: cfg.label }))} />
            </Form.Item>
            <Form.Item name="priority" label="优先级">
              <InputNumber min={0} style={{ width: 100 }} />
            </Form.Item>
            <Form.Item name="enabled" label="启用" valuePropName="checked">
              <Switch />
            </Form.Item>
          </Space>
          <Form.Item
            name="actionParams"
            label="动作参数 JSON"
            rules={[{ required: true, message: '请输入动作参数 JSON' }]}
            extra='ADD_TAG: {"tagIds":[1,2]} / SET_LIFECYCLE: {"lifecycle":"ACTIVE"} / NOTIFY: {"notifyUserId":"..."}'
          >
            <Input.TextArea rows={2} placeholder='{"tagIds":[1]}' />
          </Form.Item>
        </Form>
      </Modal>

      {/* 手动评估弹窗 */}
      <Modal
        title="手动评估规则"
        open={evaluateOpen}
        onCancel={() => setEvaluateOpen(false)}
        onOk={handleEvaluate}
        confirmLoading={evaluating}
        width={560}
      >
        <Form form={evaluateForm} layout="vertical">
          <Form.Item name="customerId" label="客户 ID" rules={[{ required: true, message: '请输入客户 ID' }]}>
            <Input placeholder="客户 ID" />
          </Form.Item>
          <Form.Item name="triggerEvent" label="触发事件" rules={[{ required: true, message: '请选择触发事件' }]}>
            <Select options={Object.entries(triggerEventConfig).map(([value, cfg]) => ({ value, label: cfg.label }))} />
          </Form.Item>
        </Form>
        {evaluateResult && (
          <Table
            rowKey="id"
            size="small"
            dataSource={evaluateResult}
            pagination={false}
            locale={{ emptyText: <Empty description="未命中任何规则" /> }}
            columns={[
              { title: '规则 ID', dataIndex: 'ruleId', width: 100 },
              { title: '动作', dataIndex: 'actionType', width: 110 },
              { title: '结果', dataIndex: 'actionResult', ellipsis: true },
              { title: '详情', dataIndex: 'actionDetail', ellipsis: true },
            ]}
          />
        )}
      </Modal>

      {/* 执行日志抽屉 */}
      <Modal
        title={logsRule ? `执行日志 - ${logsRule.ruleName}` : '执行日志'}
        open={!!logsRule}
        onCancel={() => setLogsRule(null)}
        footer={null}
        width={720}
      >
        <Table
          rowKey="id"
          size="small"
          dataSource={logs}
          loading={logsLoading}
          pagination={false}
          locale={{ emptyText: <Empty description="暂无执行日志" /> }}
          columns={[
            { title: '客户', dataIndex: 'customerNickname', width: 120, render: (v?: string) => v || '-' },
            { title: '触发事件', dataIndex: 'triggerEvent', width: 130, render: (v?: string) => v || '-' },
            { title: '动作', dataIndex: 'actionType', width: 110 },
            { title: '结果', dataIndex: 'actionResult', ellipsis: true },
            { title: '执行时间', dataIndex: 'executedAt', width: 160, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
          ]}
        />
        {logs.length < logsTotal && (
          <div style={{ textAlign: 'center', marginTop: 8 }}>
            <Button type="link" loading={logsLoading} onClick={() => loadLogs(logsRule!, logsPage + 1)}>
              加载更早 ({logs.length}/{logsTotal})
            </Button>
          </div>
        )}
      </Modal>
    </div>
  );
}