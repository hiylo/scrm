/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Visits.tsx
 * Date : 2026/10/08 12:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

import { useCallback, useEffect, useState } from 'react';
import {
  App,
  Button,
  Card,
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
  EditOutlined,
  ReloadOutlined,
  CheckCircleOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

/** 计划状态映射 */
const statusConfig: Record<string, { color: string; label: string }> = {
  ACTIVE: { color: 'green', label: '启用' },
  INACTIVE: { color: 'default', label: '停用' },
};

/** 任务状态映射 */
const taskStatusConfig: Record<string, { color: string; label: string }> = {
  PENDING: { color: 'orange', label: '待拜访' },
  COMPLETED: { color: 'green', label: '已完成' },
  CANCELLED: { color: 'default', label: '已取消' },
};

/** 拜访类型映射 */
const visitTypeConfig: Record<string, { color: string; label: string }> = {
  ONSITE: { color: 'blue', label: '上门' },
  PHONE: { color: 'cyan', label: '电话' },
  VIDEO: { color: 'purple', label: '视频' },
};

/** 拜访计划实体 */
interface ScrmVisitPlan {
  id: string;
  planName: string;
  planCode: string;
  frequency?: string;
  templateId?: string;
  status?: string;
  description?: string;
  createTime?: string;
}

/** 拜访任务实体 */
interface ScrmVisitTask {
  id: string;
  taskName: string;
  customerId?: string;
  customerName?: string;
  visitType?: string;
  status: string;
  plannedAt?: string;
  completedAt?: string;
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
 * 客户拜访页
 * <p>
 * 拜访计划 CRUD + 拜访任务 Tab (完成/取消流转)。
 * </p>
 *
 * @author Hsi Chu
 */
export default function Visits() {
  const { message } = App.useApp();
  /** 计划列表 */
  const [plans, setPlans] = useState<ScrmVisitPlan[]>([]);
  const [plansLoading, setPlansLoading] = useState(false);
  const [planTotal, setPlanTotal] = useState(0);
  const [planPage, setPlanPage] = useState(0);
  const planPageSize = 10;
  /** 任务列表 */
  const [tasks, setTasks] = useState<ScrmVisitTask[]>([]);
  const [tasksLoading, setTasksLoading] = useState(false);
  const [taskTotal, setTaskTotal] = useState(0);
  const [taskPage, setTaskPage] = useState(0);
  const taskPageSize = 10;
  /** 计划弹窗 */
  const [editing, setEditing] = useState<ScrmVisitPlan | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  /** 加载计划 */
  const loadPlans = useCallback(async (targetPage = planPage) => {
    setPlansLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(planPageSize) });
      const data = await apiClient.get<Page<ScrmVisitPlan>>(`/scrm/visits/plans/list?${params.toString()}`);
      setPlans(data.content || []);
      setPlanTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setPlansLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [planPage]);

  /** 加载任务 */
  const loadTasks = useCallback(async (targetPage = taskPage) => {
    setTasksLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(taskPageSize) });
      const data = await apiClient.get<Page<ScrmVisitTask>>(`/scrm/visits/tasks/list?${params.toString()}`);
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
    loadPlans();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [planPage]);

  useEffect(() => {
    loadTasks();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [taskPage]);

  /** 打开新建弹窗 */
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setOpen(true);
  };

  /** 打开编辑弹窗 */
  const openEdit = (record: ScrmVisitPlan) => {
    setEditing(record);
    form.setFieldsValue(record);
    setOpen(true);
  };

  /** 提交计划 */
  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await apiClient.put(`/scrm/visits/plans/${editing.id}`, values);
        message.success('计划已更新');
      } else {
        await apiClient.post('/scrm/visits/plans', values);
        message.success('计划已创建');
      }
      setOpen(false);
      setPlanPage(0);
      loadPlans(0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setSaving(false);
    }
  };

  /** 完成任务 */
  const handleComplete = async (record: ScrmVisitTask) => {
    try {
      await apiClient.post(`/scrm/visits/tasks/${record.id}/complete`, { taskId: record.id });
      message.success('任务已完成');
      loadTasks();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 计划表格列 */
  const planColumns: ColumnsType<ScrmVisitPlan> = [
    { title: '计划名称', dataIndex: 'planName', render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
    { title: '计划编码', dataIndex: 'planCode', width: 130, render: (v?: string) => v || '-' },
    { title: '频次', dataIndex: 'frequency', width: 110, render: (v?: string) => v || '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v?: string) => {
        if (!v) return '-';
        const cfg = statusConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '创建时间', dataIndex: 'createTime', width: 140, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
    {
      title: '操作',
      key: 'actions',
      width: 100,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(r)}>
            编辑
          </Button>
        </Space>
      ),
    },
  ];

  /** 任务表格列 */
  const taskColumns: ColumnsType<ScrmVisitTask> = [
    { title: '任务名称', dataIndex: 'taskName', render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
    { title: '客户', dataIndex: 'customerName', width: 120, render: (v?: string) => v || '-' },
    {
      title: '类型',
      dataIndex: 'visitType',
      width: 90,
      render: (v?: string) => {
        if (!v) return '-';
        const cfg = visitTypeConfig[v] || { color: 'default', label: v };
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
      width: 100,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          {r.status === 'PENDING' && (
            <Button type="link" size="small" icon={<CheckCircleOutlined />} onClick={() => handleComplete(r)}>
              完成
            </Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div className="visits-page">
      <Tabs
        items={[
          {
            key: 'plans',
            label: '拜访计划',
            children: (
              <Card
                title="拜访计划"
                extra={
                  <Space>
                    <Button icon={<ReloadOutlined />} onClick={() => loadPlans()} />
                    <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                      新建计划
                    </Button>
                  </Space>
                }
              >
                <Table
                  rowKey="id"
                  columns={planColumns}
                  dataSource={plans}
                  loading={plansLoading}
                  pagination={{
                    current: planPage + 1,
                    pageSize: planPageSize,
                    total: planTotal,
                    showTotal: t => `共 ${t} 条`,
                    onChange: p => setPlanPage(p - 1),
                  }}
                  scroll={{ y: 'calc(100vh - 320px)' }}
                />
              </Card>
            ),
          },
          {
            key: 'tasks',
            label: '拜访任务',
            children: (
              <Card
                title="拜访任务"
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

      {/* 计划弹窗 */}
      <Modal
        title={editing ? '编辑拜访计划' : '新建拜访计划'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={520}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="planName" label="计划名称" rules={[{ required: true, message: '请输入计划名称' }]}>
            <Input placeholder="如: 季度客户拜访" maxLength={200} />
          </Form.Item>
          <Form.Item name="planCode" label="计划编码" rules={[{ required: true, message: '请输入计划编码' }]}>
            <Input placeholder="如: VISIT_Q" maxLength={50} />
          </Form.Item>
          <Form.Item name="frequency" label="频次">
            <Input placeholder="如: QUARTERLY" maxLength={30} />
          </Form.Item>
          <Form.Item name="templateId" label="拜访模板 ID">
            <Input placeholder="模板 ID" />
          </Form.Item>
          <Form.Item name="description" label="说明">
            <Input.TextArea rows={2} placeholder="计划说明" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}