/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : TaskScheduler.tsx
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
  PauseCircleOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

const { Text } = Typography;

/** 定时任务实体 */
interface ScrmScheduledTask {
  id: string;
  taskName: string;
  taskCode: string;
  taskCategory?: string;
  taskType: string;
  cronExpression?: string;
  fixedRateMs?: number;
  handlerClass?: string;
  handlerMethod?: string;
  parameters?: string;
  timeoutSeconds?: number;
  status: string; // ACTIVE / INACTIVE / PAUSED
  lastExecutedAt?: string;
  createTime?: string;
}

/** 执行记录 */
interface ScrmTaskExecution {
  id: string;
  taskId?: string;
  taskCode?: string;
  executionNo: string;
  triggerType?: string;
  status: string; // SUCCESS / FAILED / RUNNING / CANCELLED / TIMEOUT
  startedAt?: string;
  completedAt?: string;
  durationMs?: number;
  workerId?: string;
}

/** Spring Page 分页响应 */
interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/** 任务状态 */
const taskStatusConfig: Record<string, { color: string; label: string }> = {
  ACTIVE: { color: 'green', label: '启用' },
  INACTIVE: { color: 'default', label: '停用' },
  PAUSED: { color: 'orange', label: '暂停' },
};

/** 执行状态 */
const execStatusConfig: Record<string, { color: string; label: string }> = {
  SUCCESS: { color: 'green', label: '成功' },
  FAILED: { color: 'red', label: '失败' },
  RUNNING: { color: 'processing', label: '执行中' },
  CANCELLED: { color: 'default', label: '已取消' },
  TIMEOUT: { color: 'orange', label: '超时' },
};

/**
 * 任务调度页
 * <p>
 * 定时任务编排: 定时任务 CRUD (cron/固定频率/一次性) + 启停/暂停/恢复
 * + 手动执行 + 执行记录 (成功/失败/超时/耗时)。
 * </p>
 *
 * @author Hsi Chu
 */
export default function TaskScheduler() {
  const { message } = App.useApp();
  /** 任务 */
  const [tasks, setTasks] = useState<ScrmScheduledTask[]>([]);
  const [tasksLoading, setTasksLoading] = useState(false);
  const [taskTotal, setTaskTotal] = useState(0);
  const [taskPage, setTaskPage] = useState(0);
  const taskPageSize = 10;
  /** 执行记录 */
  const [executions, setExecutions] = useState<ScrmTaskExecution[]>([]);
  const [execLoading, setExecLoading] = useState(false);
  const [execTotal, setExecTotal] = useState(0);
  const [execPage, setExecPage] = useState(0);
  const execPageSize = 10;
  /** 弹窗 */
  const [editing, setEditing] = useState<ScrmScheduledTask | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  /** 加载任务 */
  const loadTasks = useCallback(async (targetPage = taskPage) => {
    setTasksLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(taskPageSize) });
      const data = await apiClient.get<Page<ScrmScheduledTask>>(`/scrm/task-scheduler/list?${params.toString()}`);
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

  /** 加载执行记录 */
  const loadExecutions = useCallback(async (targetPage = execPage) => {
    setExecLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(execPageSize) });
      const data = await apiClient.get<Page<ScrmTaskExecution>>(`/scrm/task-scheduler/executions/list?${params.toString()}`);
      setExecutions(data.content || []);
      setExecTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setExecLoading(false);
    }
  }, [execPage]);

  useEffect(() => {
    loadExecutions();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [execPage]);

  /** 打开创建 */
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ taskType: 'CRON', status: 'ACTIVE', timeoutSeconds: 60 });
    setOpen(true);
  };

  /** 打开编辑 */
  const openEdit = (record: ScrmScheduledTask) => {
    setEditing(record);
    form.setFieldsValue(record);
    setOpen(true);
  };

  /** 提交 */
  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await apiClient.put(`/scrm/task-scheduler/${editing.id}`, values);
        message.success('任务已更新');
      } else {
        await apiClient.post('/scrm/task-scheduler', values);
        message.success('任务已创建');
      }
      setOpen(false);
      setTaskPage(0);
      loadTasks(0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setSaving(false);
    }
  };

  /** 删除 */
  const handleDelete = async (record: ScrmScheduledTask) => {
    try {
      await apiClient.delete(`/scrm/task-scheduler/${record.id}`);
      message.success('任务已删除');
      loadTasks();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 任务状态动作 */
  const handleAction = async (record: ScrmScheduledTask, action: string) => {
    const labelMap: Record<string, string> = { enable: '启用', disable: '停用', pause: '暂停', resume: '恢复' };
    try {
      await apiClient.post(`/scrm/task-scheduler/${record.id}/${action}`);
      message.success(`任务已${labelMap[action] || action}`);
      loadTasks();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 手动执行 */
  const handleExecute = async (record: ScrmScheduledTask) => {
    try {
      await apiClient.post('/scrm/task-scheduler/executions/execute', { taskId: Number(record.id) });
      message.success('任务已触发执行');
      loadTasks();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 任务列 */
  const taskColumns: ColumnsType<ScrmScheduledTask> = useMemo(() => [
    {
      title: '任务名称',
      dataIndex: 'taskName',
      render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span>,
    },
    { title: '编码', dataIndex: 'taskCode', width: 110, render: (v: string) => <Text code>{v}</Text> },
    { title: '分类', dataIndex: 'taskCategory', width: 100, render: (v?: string) => v || '-' },
    {
      title: '调度',
      width: 140,
      render: (_, r) => (
        <Text type="secondary" style={{ fontSize: 12 }}>
          {r.cronExpression ? `cron ${r.cronExpression}` : r.fixedRateMs ? `每 ${r.fixedRateMs / 1000}s` : '-'}
        </Text>
      ),
    },
    { title: '最后执行', dataIndex: 'lastExecutedAt', width: 130, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => {
        const cfg = taskStatusConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '操作',
      key: 'actions',
      width: 240,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          <Button type="link" size="small" icon={<PlayCircleOutlined />} onClick={() => handleExecute(r)}>
            执行
          </Button>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(r)}>
            编辑
          </Button>
          {r.status === 'ACTIVE' ? (
            <Button type="link" size="small" danger icon={<PauseCircleOutlined />} onClick={() => handleAction(r, 'pause')}>
              暂停
            </Button>
          ) : r.status === 'PAUSED' ? (
            <Button type="link" size="small" icon={<PlayCircleOutlined />} onClick={() => handleAction(r, 'resume')}>
              恢复
            </Button>
          ) : (
            <Button type="link" size="small" onClick={() => handleAction(r, 'enable')}>
              启用
            </Button>
          )}
          <Popconfirm title="确定删除该任务?" onConfirm={() => handleDelete(r)}>
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  /** 执行记录列 */
  const execColumns: ColumnsType<ScrmTaskExecution> = [
    { title: '执行号', dataIndex: 'executionNo', width: 120, render: (v: string) => <Text code>{v}</Text> },
    { title: '任务', dataIndex: 'taskCode', width: 100, render: (v?: string) => v || '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => {
        const cfg = execStatusConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '触发', dataIndex: 'triggerType', width: 90, render: (v?: string) => v || '-' },
    { title: '耗时 (ms)', dataIndex: 'durationMs', width: 100, render: (v?: number) => v ?? '-' },
    { title: '开始时间', dataIndex: 'startedAt', width: 140, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
    { title: 'Worker', dataIndex: 'workerId', width: 100, render: (v?: string) => v || '-' },
  ];

  return (
    <div className="task-scheduler-page">
      <Tabs
        items={[
          {
            key: 'tasks',
            label: '定时任务',
            children: (
              <Card
                title="定时任务"
                extra={
                  <Space>
                    <Button icon={<ReloadOutlined />} onClick={() => loadTasks()} />
                    <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
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
          {
            key: 'executions',
            label: '执行记录',
            children: (
              <Card
                title="任务执行记录"
                extra={<Button icon={<ReloadOutlined />} onClick={() => loadExecutions()} />}
              >
                <Table
                  rowKey="id"
                  columns={execColumns}
                  dataSource={executions}
                  loading={execLoading}
                  pagination={{
                    current: execPage + 1,
                    pageSize: execPageSize,
                    total: execTotal,
                    showTotal: t => `共 ${t} 条`,
                    onChange: p => setExecPage(p - 1),
                  }}
                  locale={{ emptyText: <Empty description="暂无执行记录" /> }}
                />
              </Card>
            ),
          },
        ]}
      />

      {/* 任务弹窗 */}
      <Modal
        title={editing ? '编辑定时任务' : '新建定时任务'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={560}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="taskName" label="任务名称" rules={[{ required: true, message: '请输入任务名称' }]}>
            <Input placeholder="如: 每日客户同步" maxLength={200} />
          </Form.Item>
          <Form.Item name="taskCode" label="任务编码" rules={[{ required: true, message: '请输入任务编码' }]}>
            <Input placeholder="如: DAILY_SYNC" maxLength={100} />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="taskType" label="调度类型" rules={[{ required: true }]}>
              <Select style={{ width: 140 }} options={[{ value: 'CRON', label: 'Cron' }, { value: 'FIXED_RATE', label: '固定频率' }, { value: 'ONCE', label: '一次性' }]} />
            </Form.Item>
            <Form.Item name="cronExpression" label="Cron 表达式">
              <Input placeholder="如: 0 0 2 * * ?" style={{ width: 170 }} />
            </Form.Item>
          </Space>
          <Space size={16} wrap>
            <Form.Item name="handlerClass" label="处理类" rules={[{ required: true }]}>
              <Input placeholder="如: com.example.Job" style={{ width: 220 }} />
            </Form.Item>
            <Form.Item name="handlerMethod" label="处理方法" rules={[{ required: true }]}>
              <Input placeholder="如: run" style={{ width: 130 }} />
            </Form.Item>
          </Space>
          <Form.Item name="parameters" label="参数 JSON">
            <Input.TextArea rows={2} placeholder='{"key":"value"}' />
          </Form.Item>
          <Form.Item name="timeoutSeconds" label="超时 (秒)">
            <Input type="number" style={{ width: 120 }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}