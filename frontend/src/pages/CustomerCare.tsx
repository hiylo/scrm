/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
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
  Switch,
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

/** 关怀规则 (字段名与后端 ScrmCareRuleDto 对齐) */
interface ScrmCareRule {
  id: string;
  ruleName: string;
  careType: string;
  description?: string;
  triggerCondition?: string;
  actionType?: string;
  actionContent?: string;
  priority?: number;
  applicableSegments?: string;
  applicableLevels?: string;
  enabled?: boolean;
  executionCount?: number;
  lastExecutedAt?: string;
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
  /** 执行详情（后端字段名 actionDetail，无 careContent） */
  actionDetail?: string;
  /** 关怀类型（后端字段名 careType） */
  careType?: string;
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
/** 关怀类型 (与后端 ScrmCareRuleDto.careType 枚举保持一致) */
const careTypeConfig: Record<string, { color: string; label: string }> = {
  BIRTHDAY: { color: 'magenta', label: '生日' },
  FESTIVAL: { color: 'orange', label: '节日' },
  ANNIVERSARY: { color: 'gold', label: '纪念日' },
  MEMBERSHIP_EXPIRY: { color: 'volcano', label: '会员到期' },
  INACTIVITY_REMINDER: { color: 'blue', label: '久未互动提醒' },
  CUSTOM: { color: 'cyan', label: '自定义' },
};

/** 关怀动作 (与后端 ScrmCareRuleDto.actionType 枚举保持一致) */
const actionTypeConfig: Record<string, { color: string; label: string }> = {
  SEND_MESSAGE: { color: 'cyan', label: '发送消息' },
  SEND_COUPON: { color: 'gold', label: '发送优惠券' },
  SEND_GIFT: { color: 'magenta', label: '发送礼品' },
  CALL: { color: 'geekblue', label: '电话回访' },
  CREATE_TASK: { color: 'purple', label: '创建任务' },
  NOTIFY_ASSIGNEE: { color: 'blue', label: '通知负责人' },
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
    form.setFieldsValue({ careType: 'BIRTHDAY', actionType: 'SEND_MESSAGE', enabled: true });
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
    { title: '适用分群', dataIndex: 'applicableSegments', width: 110, render: (v?: string) => v || '-' },
    {
      title: '关怀动作',
      dataIndex: 'actionType',
      width: 110,
      render: (v?: string) => {
        if (!v) return '-';
        const cfg = actionTypeConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 90,
      render: (v?: boolean) => (
        <Tag color={v ? 'green' : 'default'}>{v ? '启用' : '停用'}</Tag>
      ),
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
          {r.enabled ? (
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
            <Form.Item name="careType" label="关怀类型" rules={[{ required: true, message: '请选择关怀类型' }]}>
              <Select style={{ width: 170 }} options={Object.entries(careTypeConfig).map(([v, c]) => ({ value: v, label: c.label }))} />
            </Form.Item>
            <Form.Item name="actionType" label="关怀动作" rules={[{ required: true, message: '请选择关怀动作' }]}>
              <Select style={{ width: 170 }} options={Object.entries(actionTypeConfig).map(([v, c]) => ({ value: v, label: c.label }))} />
            </Form.Item>
          </Space>
          <Form.Item
            name="triggerCondition"
            label="触发条件"
            rules={[{ required: true, message: '请填写触发条件' }]}
            extra='JSON 格式, 如 {"daysBefore":7} 表示提前 7 天触发'
          >
            <Input.TextArea rows={2} placeholder='{"daysBefore":7}' />
          </Form.Item>
          <Form.Item
            name="actionContent"
            label="动作内容"
            rules={[
              { required: true, message: '请填写动作内容' },
              {
                validator: (_, value) => {
                  if (!value) return Promise.resolve();
                  try {
                    JSON.parse(value);
                    return Promise.resolve();
                  } catch {
                    return Promise.reject(new Error('动作内容必须是合法的 JSON, 如 {"template":"尊敬的用户, 祝您生日快乐"}'));
                  }
                },
              },
            ]}
            extra='JSON 格式, 消息文案写在 template 字段, 如 {"template":"尊敬的 ${nickname}, 祝您生日快乐!"}'
          >
            <Input.TextArea rows={3} placeholder='{"template":"尊敬的 ${nickname}, 祝您生日快乐!"}' />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="applicableSegments" label="适用分群">
              <Input placeholder="如: VIP" style={{ width: 170 }} />
            </Form.Item>
            <Form.Item name="priority" label="优先级">
              <Input type="number" placeholder="数字, 越小越优先" style={{ width: 170 }} />
            </Form.Item>
          </Space>
          <Form.Item name="enabled" label="启用状态" initialValue={true} valuePropName="checked">
            <Switch checkedChildren="启用" unCheckedChildren="停用" />
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
            { title: '内容', dataIndex: 'actionDetail', ellipsis: true, render: (v?: string) => v || '-' },
            { title: '类型', dataIndex: 'careType', width: 100, render: (v?: string) => v || '-' },
            { title: '时间', dataIndex: 'createTime', width: 140, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
          ]}
        />
      </Modal>
    </div>
  );
}