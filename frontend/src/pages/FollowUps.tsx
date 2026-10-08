/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : FollowUps.tsx
 * Date : 2026/10/08 12:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

import { useCallback, useEffect, useState } from 'react';
import {
  App,
  Button,
  Card,
  Empty,
  Form,
  Input,
  Modal,
  Space,
  Table,
  Tabs,
  Tag,
} from 'antd';
import {
  PlusOutlined,
  ReloadOutlined,
  CheckCircleOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

/** 任务状态映射 */
const taskStatusConfig: Record<string, { color: string; label: string }> = {
  PENDING: { color: 'orange', label: '待跟进' },
  COMPLETED: { color: 'green', label: '已完成' },
  CANCELLED: { color: 'default', label: '已取消' },
};

/** 跟进类型映射 */
const followTypeConfig: Record<string, { color: string; label: string }> = {
  PHONE: { color: 'cyan', label: '电话' },
  WECHAT: { color: 'blue', label: '微信' },
  VISIT: { color: 'purple', label: '拜访' },
  EMAIL: { color: 'gold', label: '邮件' },
};

/** 跟进任务实体 */
interface ScrmFollowUpTask {
  id: string;
  taskName: string;
  customerId?: string;
  customerName?: string;
  followType?: string;
  status: string;
  plannedAt?: string;
  completedAt?: string;
}

/** 跟进记录实体 */
interface ScrmFollowUpRecord {
  id: string;
  taskId?: string;
  customerId?: string;
  customerName?: string;
  content: string;
  channel?: string;
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
 * 客户跟进页
 * <p>
 * 跟进任务 CRUD + 完成流转 + 跟进记录 Tab 与任务明细抽屉。
 * </p>
 *
 * @author Hsi Chu
 */
export default function FollowUps() {
  const { message } = App.useApp();
  /** 任务列表 */
  const [tasks, setTasks] = useState<ScrmFollowUpTask[]>([]);
  const [tasksLoading, setTasksLoading] = useState(false);
  const [taskTotal, setTaskTotal] = useState(0);
  const [taskPage, setTaskPage] = useState(0);
  const taskPageSize = 10;
  /** 跟进记录列表 */
  const [records, setRecords] = useState<ScrmFollowUpRecord[]>([]);
  const [recordsLoading, setRecordsLoading] = useState(false);
  const [recordTotal, setRecordTotal] = useState(0);
  const [recordPage, setRecordPage] = useState(0);
  const recordPageSize = 10;
  /** 任务弹窗 */
  const [editing, setEditing] = useState<ScrmFollowUpTask | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  /** 记录抽屉 */
  const [recordTask, setRecordTask] = useState<ScrmFollowUpTask | null>(null);
  const [taskRecords, setTaskRecords] = useState<ScrmFollowUpRecord[]>([]);
  const [taskRecordLoading, setTaskRecordLoading] = useState(false);

  /** 加载任务 */
  const loadTasks = useCallback(async (targetPage = taskPage) => {
    setTasksLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(taskPageSize) });
      const data = await apiClient.get<Page<ScrmFollowUpTask>>(`/scrm/follow-ups/tasks/list?${params.toString()}`);
      setTasks(data.content || []);
      setTaskTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setTasksLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [taskPage]);

  /** 加载跟进记录 */
  const loadRecords = useCallback(async (targetPage = recordPage) => {
    setRecordsLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(recordPageSize) });
      const data = await apiClient.get<Page<ScrmFollowUpRecord>>(`/scrm/follow-ups/records/list?${params.toString()}`);
      setRecords(data.content || []);
      setRecordTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setRecordsLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [recordPage]);

  useEffect(() => {
    loadTasks();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [taskPage]);

  useEffect(() => {
    loadRecords();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [recordPage]);

  /** 打开新建弹窗 */
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setOpen(true);
  };

  /** 打开编辑弹窗 */
  const openEdit = (record: ScrmFollowUpTask) => {
    setEditing(record);
    form.setFieldsValue(record);
    setOpen(true);
  };

  /** 提交任务 */
  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await apiClient.put(`/scrm/follow-ups/tasks/${editing.id}`, values);
        message.success('任务已更新');
      } else {
        await apiClient.post('/scrm/follow-ups/tasks', values);
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

  /** 完成任务 */
  const handleComplete = async (record: ScrmFollowUpTask) => {
    try {
      await apiClient.post(`/scrm/follow-ups/tasks/${record.id}/complete`, { taskId: record.id });
      message.success('任务已完成');
      loadTasks();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 打开记录抽屉: 加载该任务的跟进记录 */
  const openTaskRecords = async (record: ScrmFollowUpTask) => {
    setRecordTask(record);
    setTaskRecords([]);
    setTaskRecordLoading(true);
    try {
      const data = await apiClient.get<Page<ScrmFollowUpRecord>>(`/scrm/follow-ups/tasks/${record.id}/records`);
      setTaskRecords(data.content || []);
    } catch {
      setTaskRecords([]);
    } finally {
      setTaskRecordLoading(false);
    }
  };

  /** 任务表格列 */
  const taskColumns: ColumnsType<ScrmFollowUpTask> = [
    { title: '任务名称', dataIndex: 'taskName', render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
    { title: '客户', dataIndex: 'customerName', width: 120, render: (v?: string) => v || '-' },
    {
      title: '类型',
      dataIndex: 'followType',
      width: 90,
      render: (v?: string) => {
        if (!v) return '-';
        const cfg = followTypeConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => {
        const cfg = taskStatusConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '计划时间', dataIndex: 'plannedAt', width: 140, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
    {
      title: '操作',
      key: 'actions',
      width: 140,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          <Button type="link" size="small" onClick={() => openTaskRecords(r)}>
            记录
          </Button>
          {r.status === 'PENDING' && (
            <Button type="link" size="small" icon={<CheckCircleOutlined />} onClick={() => handleComplete(r)}>
              完成
            </Button>
          )}
        </Space>
      ),
    },
  ];

  /** 记录表格列 */
  const recordColumns: ColumnsType<ScrmFollowUpRecord> = [
    { title: '客户', dataIndex: 'customerName', width: 120, render: (v?: string) => v || '-' },
    { title: '跟进内容', dataIndex: 'content', ellipsis: true, render: (v?: string) => v || '-' },
    { title: '渠道', dataIndex: 'channel', width: 90, render: (v?: string) => v || '-' },
    { title: '跟进时间', dataIndex: 'createdAt', width: 160, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
  ];

  return (
    <div className="follow-ups-page">
      <Tabs
        items={[
          {
            key: 'tasks',
            label: '跟进任务',
            children: (
              <Card
                title="跟进任务"
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
                  scroll={{ y: 'calc(100vh - 320px)' }}
                />
              </Card>
            ),
          },
          {
            key: 'records',
            label: '跟进记录',
            children: (
              <Card
                title="跟进记录"
                extra={<Button icon={<ReloadOutlined />} onClick={() => loadRecords()} />}
              >
                <Table
                  rowKey="id"
                  columns={recordColumns}
                  dataSource={records}
                  loading={recordsLoading}
                  pagination={{
                    current: recordPage + 1,
                    pageSize: recordPageSize,
                    total: recordTotal,
                    showTotal: t => `共 ${t} 条`,
                    onChange: p => setRecordPage(p - 1),
                  }}
                  locale={{ emptyText: <Empty description="暂无跟进记录" /> }}
                />
              </Card>
            ),
          },
        ]}
      />

      {/* 任务弹窗 */}
      <Modal
        title={editing ? '编辑跟进任务' : '新建跟进任务'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={520}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="taskName" label="任务名称" rules={[{ required: true, message: '请输入任务名称' }]}>
            <Input placeholder="如: 跟进高意向客户" maxLength={200} />
          </Form.Item>
          <Form.Item name="customerId" label="客户 ID" rules={[{ required: true, message: '请输入客户 ID' }]}>
            <Input placeholder="客户 ID" />
          </Form.Item>
          <Form.Item name="followType" label="跟进类型">
            <Input placeholder="如: PHONE / WECHAT / VISIT" maxLength={30} />
          </Form.Item>
          <Form.Item name="plannedAt" label="计划时间">
            <Input placeholder="如 2026-10-10T10:00:00" maxLength={30} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 记录抽屉 */}
      <Modal
        title={recordTask ? `跟进记录 - ${recordTask.taskName}` : '跟进记录'}
        open={!!recordTask}
        onCancel={() => setRecordTask(null)}
        footer={null}
        width={560}
      >
        <Table
          rowKey="id"
          size="small"
          dataSource={taskRecords}
          loading={taskRecordLoading}
          pagination={false}
          locale={{ emptyText: <Empty description="暂无跟进记录" /> }}
          columns={recordColumns}
        />
      </Modal>
    </div>
  );
}