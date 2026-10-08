/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : CustomerCare.tsx
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
  PlayCircleOutlined,
  StopOutlined,
  EyeOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

const { Text } = Typography;

/** 关怀规则 */
interface ScrmCareRule {
  id: string;
  ruleName: string;
  careType: string; // BIRTHDAY / ANNIVERSARY / HOLIDAY / EVENT / MANUAL
  customerSegment?: string;
  triggerCondition?: string;
  messageTemplate?: string;
  scheduleTime?: string;
  status: string; // ACTIVE / INACTIVE
  createTime?: string;
}

/** 关怀任务 */
interface ScrmCareTask {
  id: string;
  taskName: string;
  careType?: string;
  customerId?: string;
  customerName?: string;
  scheduledAt?: string;
  status: string; // PENDING / COMPLETED / CANCELLED / OVERDUE
  executedAt?: string;
}

/** 关怀记录 */
interface ScrmCareRecord {
  id: string;
  taskId: string;
  customerId: string;
  customerName?: string;
  careContent?: string;
  channel?: string;
  status?: string;
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

/** 规则状态 */
const ruleStatusConfig: Record<string, { color: string; label: string }> = {
  ACTIVE: { color: 'green', label: '启用' },
  INACTIVE: { color: 'default', label: '停用' },
};

/** 关怀类型 */
const careTypeConfig: Record<string, { color: string; label: string }> = {
  BIRTHDAY: { color: 'magenta', label: '生日' },
  ANNIVERSARY: { color: 'gold', label: '纪念日' },
  HOLIDAY: { color: 'orange', label: '节日' },
  EVENT: { color: 'blue', label: '事件' },
  MANUAL: { color: 'cyan', label: '手动' },
};

/** 任务状态 */
const taskStatusConfig: Record<string, { color: string; label: string }> = {
  PENDING: { color: 'orange', label: '待执行' },
  COMPLETED: { color: 'green', label: '已完成' },
  CANCELLED: { color: 'default', label: '已取消' },
  OVERDUE: { color: 'red', label: '已逾期' },
};

/**
 * 客户关怀页
 * <p>
 * 客户关怀体系: 关怀规则 (生日/纪念日/节日/事件/手动) + 关怀任务
 * (定时/状态) + 关怀记录 (发送内容/渠道/结果)。
 * </p>
 *
 * @author Hsi Chu
 */
export default function CustomerCare() {
  const { message } = App.useApp();
  /** 规则 */
  const [rules, setRules] = useState<ScrmCareRule[]>([]);
  const [rulesLoading, setRulesLoading] = useState(false);
  const [ruleTotal, setRuleTotal] = useState(0);
  const [rulePage, setRulePage] = useState(0);
  const rulePageSize = 10;
  /** 任务 */
  const [tasks, setTasks] = useState<ScrmCareTask[]>([]);
  const [tasksLoading, setTasksLoading] = useState(false);
  const [taskTotal, setTaskTotal] = useState(0);
  const [taskPage, setTaskPage] = useState(0);
  const taskPageSize = 10;
  /** 规则弹窗 */
  const [editing, setEditing] = useState<ScrmCareRule | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  /** 记录抽屉 */
  const [recordTask, setRecordTask] = useState<ScrmCareTask | null>(null);
  const [records, setRecords] = useState<ScrmCareRecord[]>([]);
  const [recordLoading, setRecordLoading] = useState(false);

  /** 加载规则 */
  const loadRules = useCallback(async (targetPage = rulePage) => {
    setRulesLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(rulePageSize) });
      const data = await apiClient.get<Page<ScrmCareRule>>(`/scrm/customer-care/rules/list?${params.toString()}`);
      setRules(data.content || []);
      setRuleTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setRulesLoading(false);
    }
  }, [rulePage]);

  useEffect(() => {
    loadRules();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rulePage]);

  /** 加载任务 */
  const loadTasks = useCallback(async (targetPage = taskPage) => {
    setTasksLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(taskPageSize) });
      const data = await apiClient.get<Page<ScrmCareTask>>(`/scrm/customer-care/tasks/list?${params.toString()}`);
      setTasks(data.content || []);
      setTaskTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setTasksLoading(false);
    }
  }, [taskPage]);

  useEffect(() => {
    loadTasks();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [taskPage]);

  /** 打开创建 */
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ careType: 'BIRTHDAY', status: 'ACTIVE' });
    setOpen(true);
  };

  /** 打开编辑 */
  const openEdit = (record: ScrmCareRule) => {
    setEditing(record);
    form.setFieldsValue(record);
    setOpen(true);
  };

  /** 提交规则 */
  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await apiClient.put(`/scrm/customer-care/rules/${editing.id}`, values);
        message.success('规则已更新');
      } else {
        await apiClient.post('/scrm/customer-care/rules', values);
        message.success('规则已创建');
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

  /** 删除规则 */
  const handleDelete = async (record: ScrmCareRule) => {
    try {
      await apiClient.delete(`/scrm/customer-care/rules/${record.id}`);
      message.success('规则已删除');
      loadRules();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 启停规则 */
  const handleToggle = async (record: ScrmCareRule, toEnable: boolean) => {
    try {
      await apiClient.post(`/scrm/customer-care/rules/${record.id}/${toEnable ? 'enable' : 'disable'}`);
      message.success(toEnable ? '规则已启用' : '规则已停用');
      loadRules();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 执行任务 */
  const handleExecute = async (record: ScrmCareTask) => {
    try {
      await apiClient.post(`/scrm/customer-care/tasks/${record.id}/execute`, {});
      message.success('关怀任务已执行');
      loadTasks();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 查看记录 */
  const openRecords = async (record: ScrmCareTask) => {
    setRecordTask(record);
    setRecordLoading(true);
    try {
      const data = await apiClient.get<ScrmCareRecord[]>(`/scrm/customer-care/tasks/${record.id}/records`);
      setRecords(data || []);
    } catch {
      setRecords([]);
    } finally {
      setRecordLoading(false);
    }
  };

  /** 规则列 */
  const ruleColumns: ColumnsType<ScrmCareRule> = useMemo(() => [
    {
      title: '规则名称',
      dataIndex: 'ruleName',
      render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span>,
    },
    {
      title: '关怀类型',
      dataIndex: 'careType',
      width: 100,
      render: (v: string) => {
        const cfg = careTypeConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '客户分群', dataIndex: 'customerSegment', width: 110, render: (v?: string) => v || '-' },
    { title: '计划时间', dataIndex: 'scheduleTime', width: 130, render: (v?: string) => v || '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => {
        const cfg = ruleStatusConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '操作',
      key: 'actions',
      width: 150,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(r)}>
            编辑
          </Button>
          {r.status === 'ACTIVE' ? (
            <Button type="link" size="small" danger icon={<StopOutlined />} onClick={() => handleToggle(r, false)}>
              停用
            </Button>
          ) : (
            <Button type="link" size="small" icon={<PlayCircleOutlined />} onClick={() => handleToggle(r, true)}>
              启用
            </Button>
          )}
          <Popconfirm title="确定删除该规则?" onConfirm={() => handleDelete(r)}>
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  /** 任务列 */
  const taskColumns: ColumnsType<ScrmCareTask> = useMemo(() => [
    {
      title: '任务名称',
      dataIndex: 'taskName',
      render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span>,
    },
    { title: '客户', dataIndex: 'customerName', width: 110, render: (v?: string) => v || '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => {
        const cfg = taskStatusConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '计划时间', dataIndex: 'scheduledAt', width: 130, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
    {
      title: '操作',
      key: 'actions',
      width: 150,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => openRecords(r)}>
            记录
          </Button>
          {r.status === 'PENDING' && (
            <Button type="link" size="small" icon={<PlayCircleOutlined />} onClick={() => handleExecute(r)}>
              执行
            </Button>
          )}
        </Space>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  return (
    <div className="customer-care-page">
      <Tabs
        items={[
          {
            key: 'rules',
            label: '关怀规则',
            children: (
              <Card
                title="客户关怀规则"
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
                />
              </Card>
            ),
          },
          {
            key: 'tasks',
            label: '关怀任务',
            children: (
              <Card
                title="关怀任务"
                extra={<Button icon={<ReloadOutlined />} onClick={() => loadTasks()} />}
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
        title={editing ? '编辑关怀规则' : '新建关怀规则'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={560}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="ruleName" label="规则名称" rules={[{ required: true, message: '请输入规则名称' }]}>
            <Input placeholder="如: 客户生日关怀" maxLength={200} />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="careType" label="关怀类型" rules={[{ required: true }]}>
              <Select style={{ width: 150 }} options={Object.entries(careTypeConfig).map(([v, c]) => ({ value: v, label: c.label }))} />
            </Form.Item>
            <Form.Item name="customerSegment" label="客户分群">
              <Input placeholder="如: VIP" style={{ width: 140 }} />
            </Form.Item>
          </Space>
          <Form.Item name="triggerCondition" label="触发条件 JSON">
            <Input.TextArea rows={2} placeholder='{"daysBefore":7}' />
          </Form.Item>
          <Form.Item name="messageTemplate" label="关怀文案模板">
            <Input.TextArea rows={2} placeholder="支持 ${nickname} 变量" />
          </Form.Item>
          <Form.Item name="scheduleTime" label="计划执行时间">
            <Input placeholder="如: 09:00" maxLength={20} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 记录抽屉 */}
      <Modal
        title={recordTask ? `关怀记录 - ${recordTask.taskName}` : '关怀记录'}
        open={!!recordTask}
        onCancel={() => setRecordTask(null)}
        footer={null}
        width={560}
      >
        <Table
          rowKey="id"
          size="small"
          dataSource={records}
          loading={recordLoading}
          pagination={false}
          locale={{ emptyText: <Empty description="暂无关怀记录" /> }}
          columns={[
            { title: '客户', dataIndex: 'customerName', width: 110, render: (v?: string) => v || '-' },
            { title: '内容', dataIndex: 'careContent', ellipsis: true, render: (v?: string) => v || '-' },
            { title: '渠道', dataIndex: 'channel', width: 90, render: (v?: string) => v || '-' },
            { title: '时间', dataIndex: 'createdAt', width: 140, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
          ]}
        />
      </Modal>
    </div>
  );
}