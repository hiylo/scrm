/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : QualityInspections.tsx
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
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import {
  PlusOutlined,
  ReloadOutlined,
  CaretRightOutlined,
  FileSearchOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

const { Text } = Typography;

/** 规则类别映射 */
const categoryConfig: Record<string, { color: string; label: string }> = {
  SENSITIVE_WORD: { color: 'red', label: '敏感词' },
  RESPONSE_TIME: { color: 'blue', label: '响应时效' },
  MANNER: { color: 'orange', label: '服务态度' },
  COMPLIANCE: { color: 'purple', label: '合规' },
  OTHER: { color: 'default', label: '其他' },
};

/** 任务状态映射 */
const taskStatusConfig: Record<string, { color: string; label: string }> = {
  PENDING: { color: 'orange', label: '待执行' },
  RUNNING: { color: 'processing', label: '执行中' },
  COMPLETED: { color: 'green', label: '已完成' },
  FAILED: { color: 'red', label: '失败' },
};

/** 质检规则实体 */
interface ScrmQualityRule {
  id: string;
  ruleName: string;
  category: string;
  ruleType?: string;
  ruleConfig?: string;
  passCondition?: string;
  scoreWeight?: number;
  enabled?: boolean;
  matchCount?: number;
  passCount?: number;
  description?: string;
}

/** 质检任务实体 */
interface ScrmQualityTask {
  id: string;
  taskName: string;
  inspectionScope?: string;
  ruleIds?: string;
  totalConversations?: number;
  inspectedCount?: number;
  passedCount?: number;
  failedCount?: number;
  status: string;
  createTime?: string;
}

/** 质检结果行 */
interface ScrmQualityResult {
  id: string;
  taskId?: string;
  conversationId: string;
  customerName?: string;
  assigneeName?: string;
  totalScore?: number;
  passed: boolean;
  inspectedAt?: string;
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
 * 会话质检页
 * <p>
 * 质检规则配置 (CRUD) + 质检任务 (新建/执行) + 质检结果抽屉, 覆盖会话质检全流程。
 * </p>
 *
 * @author Hsi Chu
 */
export default function QualityInspections() {
  const { message } = App.useApp();
  /** 规则 */
  const [rules, setRules] = useState<ScrmQualityRule[]>([]);
  const [rulesLoading, setRulesLoading] = useState(false);
  const [rulePage, setRulePage] = useState(0);
  const rulePageSize = 10;
  const [ruleTotal, setRuleTotal] = useState(0);
  /** 任务 */
  const [tasks, setTasks] = useState<ScrmQualityTask[]>([]);
  const [tasksLoading, setTasksLoading] = useState(false);
  const [taskPage, setTaskPage] = useState(0);
  const taskPageSize = 10;
  const [taskTotal, setTaskTotal] = useState(0);
  /** 规则弹窗 */
  const [editing, setEditing] = useState<ScrmQualityRule | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  /** 任务弹窗 */
  const [taskOpen, setTaskOpen] = useState(false);
  const [taskSaving, setTaskSaving] = useState(false);
  const [taskForm] = Form.useForm();
  /** 结果抽屉 */
  const [resultTask, setResultTask] = useState<ScrmQualityTask | null>(null);
  const [results, setResults] = useState<ScrmQualityResult[]>([]);
  const [resultsLoading, setResultsLoading] = useState(false);

  /** 加载质检规则 */
  const loadRules = useCallback(async (targetPage = rulePage) => {
    setRulesLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(rulePageSize) });
      const data = await apiClient.get<Page<ScrmQualityRule>>(`/scrm/quality-inspections/rules/list?${params.toString()}`);
      setRules(data.content || []);
      setRuleTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setRulesLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rulePage]);

  /** 加载质检任务 */
  const loadTasks = useCallback(async (targetPage = taskPage) => {
    setTasksLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(taskPageSize) });
      const data = await apiClient.get<Page<ScrmQualityTask>>(`/scrm/quality-inspections/tasks/list?${params.toString()}`);
      setTasks(data.content || []);
      setTaskTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setTasksLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [taskPage]);

  useEffect(() => {
    loadRules();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rulePage]);

  useEffect(() => {
    loadTasks();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [taskPage]);

  /** 打开新建 */
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ category: 'SENSITIVE_WORD', passCondition: 'ALL_PASS' });
    setOpen(true);
  };

  /** 提交规则 */
  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await apiClient.put(`/scrm/quality-inspections/rules/${editing.id}`, values);
        message.success('质检规则已更新');
      } else {
        await apiClient.post('/scrm/quality-inspections/rules', values);
        message.success('质检规则已创建');
      }
      setOpen(false);
      setRulePage(0);
      loadRules(0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setSaving(false);
    }
  };

  /** 新建任务 */
  const handleCreateTask = async () => {
    const values = await taskForm.validateFields();
    setTaskSaving(true);
    try {
      await apiClient.post('/scrm/quality-inspections/tasks', values);
      message.success('质检任务已创建');
      setTaskOpen(false);
      setTaskPage(0);
      loadTasks(0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setTaskSaving(false);
    }
  };

  /** 执行任务 */
  const handleExecute = async (record: ScrmQualityTask) => {
    try {
      await apiClient.post(`/scrm/quality-inspections/tasks/${record.id}/execute`);
      message.success('任务已开始执行');
      loadTasks();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 打开结果抽屉 */
  const openResults = async (record: ScrmQualityTask) => {
    setResultTask(record);
    setResults([]);
    setResultsLoading(true);
    try {
      const data = await apiClient.get<ScrmQualityResult[]>(`/scrm/quality-inspections/tasks/${record.id}/results`);
      setResults(data || []);
    } catch {
      setResults([]);
    } finally {
      setResultsLoading(false);
    }
  };

  /** 规则列 */
  const ruleColumns: ColumnsType<ScrmQualityRule> = useMemo(() => [
    { title: '规则名称', dataIndex: 'ruleName', render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
    {
      title: '类别',
      dataIndex: 'category',
      width: 100,
      render: (v: string) => {
        const cfg = categoryConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '规则类型', dataIndex: 'ruleType', width: 130, render: (v?: string) => v || '-' },
    { title: '通过条件', dataIndex: 'passCondition', width: 120, render: (v?: string) => v || '-' },
    { title: '权重', dataIndex: 'scoreWeight', width: 70, render: (v?: number) => v ?? '-' },
    {
      title: '命中/通过',
      dataIndex: 'matchCount',
      width: 110,
      render: (v?: number, r?: ScrmQualityRule) => `${v ?? 0} / ${r?.passCount ?? 0}`,
    },
    {
      title: '启用',
      dataIndex: 'enabled',
      width: 80,
      render: (v?: boolean) => (v === false ? <Tag>停用</Tag> : <Tag color="green">启用</Tag>),
    },
  ], []);

  /** 任务列 */
  const taskColumns: ColumnsType<ScrmQualityTask> = useMemo(() => [
    { title: '任务名称', dataIndex: 'taskName', render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
    { title: '检查范围', dataIndex: 'inspectionScope', width: 110, render: (v?: string) => v || '-' },
    { title: '会话总数', dataIndex: 'totalConversations', width: 100, render: (v?: number) => v ?? '-' },
    { title: '已检查', dataIndex: 'inspectedCount', width: 90, render: (v?: number) => v ?? 0 },
    { title: '通过/失败', dataIndex: 'passedCount', width: 110, render: (v?: number, r?: ScrmQualityTask) => `${v ?? 0} / ${r?.failedCount ?? 0}` },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => {
        const cfg = taskStatusConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '创建时间', dataIndex: 'createTime', width: 140, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
    {
      title: '操作',
      key: 'actions',
      width: 130,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          {r.status === 'PENDING' && (
            <Button type="link" size="small" icon={<CaretRightOutlined />} onClick={() => handleExecute(r)}>
              执行
            </Button>
          )}
          {r.status === 'COMPLETED' && (
            <Button type="link" size="small" icon={<FileSearchOutlined />} onClick={() => openResults(r)}>
              结果
            </Button>
          )}
        </Space>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  return (
    <div className="quality-inspections-page">
      <Tabs
        defaultActiveKey="rules"
        items={[
          {
            key: 'rules',
            label: '质检规则',
            children: (
              <Card
                title="质检规则"
                extra={
                  <Space>
                    <Button icon={<ReloadOutlined />} onClick={() => loadRules()} />
                    <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                      新建规则
                    </Button>
                  </Space>
                }
              >
                <Table
                  rowKey="id"
                  columns={ruleColumns}
                  dataSource={rules}
                  loading={rulesLoading}
                  pagination={{
                    current: rulePage + 1,
                    pageSize: rulePageSize,
                    total: ruleTotal,
                    showTotal: t => `共 ${t} 条`,
                    onChange: p => setRulePage(p - 1),
                  }}
                  scroll={{ y: 'calc(100vh - 320px)' }}
                />
              </Card>
            ),
          },
          {
            key: 'tasks',
            label: '质检任务',
            children: (
              <Card
                title="质检任务"
                extra={
                  <Space>
                    <Button icon={<ReloadOutlined />} onClick={() => loadTasks()} />
                    <Button type="primary" icon={<PlusOutlined />} onClick={() => { taskForm.resetFields(); taskForm.setFieldsValue({ inspectionScope: 'ALL' }); setTaskOpen(true); }}>
                      新建任务
                    </Button>
                  </Space>
                }
              >
                <Table
                  rowKey="id"
                  columns={taskColumns}
                  dataSource={tasks}
                  loading={tasksLoading}
                  pagination={{
                    current: taskPage + 1,
                    pageSize: taskPageSize,
                    total: taskTotal,
                    showTotal: t => `共 ${t} 条`,
                    onChange: p => setTaskPage(p - 1),
                  }}
                />
              </Card>
            ),
          },
        ]}
      />

      {/* 规则弹窗 */}
      <Modal
        title={editing ? '编辑质检规则' : '新建质检规则'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={560}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="ruleName" label="规则名称" rules={[{ required: true, message: '请输入规则名称' }]}>
            <Input placeholder="如: 禁止辱骂客户" maxLength={200} />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="category" label="类别" rules={[{ required: true }]}>
              <Select style={{ width: 180 }} options={Object.entries(categoryConfig).map(([v, c]) => ({ value: v, label: c.label }))} />
            </Form.Item>
            <Form.Item name="ruleType" label="规则类型">
              <Input placeholder="如: KEYWORD_REGEX" style={{ width: 180 }} />
            </Form.Item>
          </Space>
          <Form.Item name="ruleConfig" label="规则配置 JSON" rules={[{ required: true, message: '请输入规则配置 JSON' }]}>
            <Input.TextArea rows={3} placeholder='{"keywords":["辱骂","投诉"],"regexp":"..."}' />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="passCondition" label="通过条件" rules={[{ required: true }]}>
              <Select
                style={{ width: 160 }}
                options={[
                  { value: 'ALL_PASS', label: '全部通过' },
                  { value: 'SCORE_GE', label: '总分 >= 阈值' },
                  { value: 'NOT_MATCH', label: '未命中' },
                ]}
              />
            </Form.Item>
            <Form.Item name="scoreWeight" label="权重">
              <InputNumber min={0} style={{ width: 100 }} />
            </Form.Item>
          </Space>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="规则说明" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 任务弹窗 */}
      <Modal
        title="新建质检任务"
        open={taskOpen}
        onCancel={() => setTaskOpen(false)}
        onOk={handleCreateTask}
        confirmLoading={taskSaving}
      >
        <Form form={taskForm} layout="vertical">
          <Form.Item name="taskName" label="任务名称" rules={[{ required: true, message: '请输入任务名称' }]}>
            <Input placeholder="如: 9 月会话质检" maxLength={200} />
          </Form.Item>
          <Form.Item name="inspectionScope" label="检查范围" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'ALL', label: '全部会话' },
                { value: 'ACCOUNT', label: '按账号' },
                { value: 'CUSTOMER', label: '按客户' },
                { value: 'DATE_RANGE', label: '按日期' },
              ]}
            />
          </Form.Item>
          <Form.Item name="ruleIds" label="规则 ID (逗号分隔)" rules={[{ required: true, message: '请输入规则 ID' }]}>
            <Input placeholder="如: 1,2,3" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 结果抽屉 */}
      <Drawer
        title={resultTask ? `质检结果 - ${resultTask.taskName}` : '质检结果'}
        open={!!resultTask}
        onClose={() => setResultTask(null)}
        width={680}
      >
        <Table
          rowKey="id"
          size="small"
          dataSource={results}
          loading={resultsLoading}
          pagination={false}
          locale={{ emptyText: <Empty description="暂无结果" /> }}
          columns={[
            { title: '会话', dataIndex: 'conversationId', width: 100, render: (v: string) => <Text code>#{v}</Text> },
            { title: '客户', dataIndex: 'customerName', width: 110, render: (v?: string) => v || '-' },
            { title: '坐席', dataIndex: 'assigneeName', width: 100, render: (v?: string) => v || '-' },
            { title: '得分', dataIndex: 'totalScore', width: 80, render: (v?: number) => <Text strong>{v ?? 0}</Text> },
            {
              title: '结果',
              dataIndex: 'passed',
              width: 80,
              render: (v: boolean) => (v ? <Tag color="green">通过</Tag> : <Tag color="red">不通过</Tag>),
            },
            { title: '检查时间', dataIndex: 'inspectedAt', width: 150, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
          ]}
        />
      </Drawer>
    </div>
  );
}