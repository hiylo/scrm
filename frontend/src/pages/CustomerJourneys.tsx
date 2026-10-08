/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : CustomerJourneys.tsx
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
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import {
  PlusOutlined,
  ReloadOutlined,
  CaretRightOutlined,
  PauseCircleOutlined,
  ApartmentOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

const { Text } = Typography;

/** 旅程状态映射 */
const journeyStatusConfig: Record<string, { color: string; label: string }> = {
  DRAFT: { color: 'default', label: '草稿' },
  ACTIVE: { color: 'green', label: '运行中' },
  PAUSED: { color: 'orange', label: '已暂停' },
  ARCHIVED: { color: 'default', label: '已归档' },
};

/** 旅程步骤类型映射 */
const stepTypeConfig: Record<string, { color: string; label: string }> = {
  START: { color: 'green', label: '开始' },
  ACTION: { color: 'blue', label: '动作' },
  DELAY: { color: 'orange', label: '等待' },
  CONDITION: { color: 'geekblue', label: '条件' },
  END: { color: 'default', label: '结束' },
};

/** 入组客户状态映射 */
const enrollStatusConfig: Record<string, { color: string; label: string }> = {
  ACTIVE: { color: 'green', label: '进行中' },
  COMPLETED: { color: 'default', label: '已完成' },
  EXITED: { color: 'orange', label: '已退出' },
  FAILED: { color: 'red', label: '失败' },
};

/** 触发事件映射 */
const triggerEventConfig: Record<string, string> = {
  CUSTOMER_CREATED: '创建客户',
  ORDER_PAID: '订单支付',
  CUSTOMER_UPDATE: '更新资料',
  MANUAL: '手动触发',
};

/** 客户旅程实体 */
interface ScrmCustomerJourney {
  id: string;
  journeyName: string;
  journeyCode: string;
  triggerEvent?: string;
  triggerCondition?: string;
  status?: string;
  description?: string;
  enrollmentCount?: number;
  completedCount?: number;
  createTime?: string;
  updateTime?: string;
}

/** 旅程步骤 */
interface ScrmJourneyStep {
  id: string;
  journeyId?: string;
  stepOrder?: number;
  stepName: string;
  stepType?: string;
  config?: string;
}

/** 入组客户 */
interface ScrmJourneyEnrollment {
  id: string;
  journeyId?: string;
  customerId?: string;
  customerName?: string;
  status?: string;
  currentStepId?: string;
  enteredAt?: string;
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
 * 客户旅程页面
 * 列表查询 / 新建/编辑 / 启停 / 步骤抽屉 / 入组客户抽屉。
 *
 * @author Hsi Chu
 */
export default function CustomerJourneys() {
  const { message } = App.useApp();
  /** 列表 */
  const [list, setList] = useState<ScrmCustomerJourney[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const pageSize = 10;
  const [total, setTotal] = useState(0);
  /** 旅程弹窗 */
  const [editing, setEditing] = useState<ScrmCustomerJourney | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  /** 步骤抽屉 */
  const [stepJourney, setStepJourney] = useState<ScrmCustomerJourney | null>(null);
  const [steps, setSteps] = useState<ScrmJourneyStep[]>([]);
  const [stepLoading, setStepLoading] = useState(false);
  /** 入组抽屉 */
  const [enrollJourney, setEnrollJourney] = useState<ScrmCustomerJourney | null>(null);
  const [enrollments, setEnrollments] = useState<ScrmJourneyEnrollment[]>([]);
  const [enrollLoading, setEnrollLoading] = useState(false);

  /** 加载旅程列表 */
  const loadList = useCallback(async (targetPage = page) => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(pageSize) });
      const data = await apiClient.get<Page<ScrmCustomerJourney>>(`/scrm/customer-journeys/list?${params.toString()}`);
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

  useEffect(() => {
    loadList();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page]);

  /** 打开新建弹窗 */
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ triggerEvent: 'CUSTOMER_CREATED' });
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
        await apiClient.put(`/scrm/customer-journeys/${editing.id}`, values);
        message.success('客户旅程已更新');
      } else {
        await apiClient.post('/scrm/customer-journeys', values);
        message.success('客户旅程已创建');
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

  /** 启用/暂停/激活旅程 */
  const handleStatus = async (record: ScrmCustomerJourney, action: 'activate' | 'pause', tip: string) => {
    try {
      await apiClient.post(`/scrm/customer-journeys/${record.id}/${action}`);
      message.success(tip);
      loadList();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 查看旅程步骤 */
  const openSteps = async (record: ScrmCustomerJourney) => {
    setStepJourney(record);
    setSteps([]);
    setStepLoading(true);
    try {
      const rows = await apiClient.get<ScrmJourneyStep[]>(`/scrm/customer-journeys/${record.id}/steps`);
      setSteps(rows || []);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setStepLoading(false);
    }
  };

  /** 查看入组客户 */
  const openEnrollments = async (record: ScrmCustomerJourney) => {
    setEnrollJourney(record);
    setEnrollments([]);
    setEnrollLoading(true);
    try {
      const rows = await apiClient.get<ScrmJourneyEnrollment[]>(`/scrm/customer-journeys/${record.id}/enrollments`);
      setEnrollments(rows || []);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setEnrollLoading(false);
    }
  };

  /** 表格列 */
  const columns: ColumnsType<ScrmCustomerJourney> = useMemo(() => [
    { title: '旅程名称', dataIndex: 'journeyName', render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
    { title: '旅程编码', dataIndex: 'journeyCode', width: 150, render: (v: string) => <Text code>{v}</Text> },
    {
      title: '触发事件',
      dataIndex: 'triggerEvent',
      width: 130,
      render: (v?: string) => (v ? triggerEventConfig[v] || v : '-'),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v?: string) => {
        const cfg = journeyStatusConfig[v || ''] || { color: 'default', label: v || '-' };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '入组数', dataIndex: 'enrollmentCount', width: 90, render: (v?: number) => v ?? 0 },
    { title: '完成数', dataIndex: 'completedCount', width: 90, render: (v?: number) => v ?? 0 },
    { title: '创建时间', dataIndex: 'createTime', width: 140, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
    {
      title: '操作',
      key: 'actions',
      width: 230,
      fixed: 'right',
      render: (_, r) => (
        <Space size={0}>
          {r.status === 'DRAFT' && (
            <Button type="link" size="small" icon={<CaretRightOutlined />} onClick={() => handleStatus(r, 'activate', '旅程已启动')}>
              启动
            </Button>
          )}
          {r.status === 'ACTIVE' && (
            <Button type="link" size="small" icon={<PauseCircleOutlined />} onClick={() => handleStatus(r, 'pause', '旅程已暂停')}>
              暂停
            </Button>
          )}
          <Button type="link" size="small" icon={<ApartmentOutlined />} onClick={() => openSteps(r)}>
            步骤
          </Button>
          <Button type="link" size="small" icon={<TeamOutlined />} onClick={() => openEnrollments(r)}>
            入组
          </Button>
          <Button type="link" size="small" onClick={() => { setEditing(r); form.setFieldsValue(r); setOpen(true); }}>
            编辑
          </Button>
        </Space>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  return (
    <div className="customer-journeys-page">
      <Card
        title="客户旅程"
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => loadList()} />
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              新建旅程
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

      {/* 旅程弹窗 */}
      <Modal
        title={editing ? '编辑客户旅程' : '新建客户旅程'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={560}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="journeyName" label="旅程名称" rules={[{ required: true, message: '请输入旅程名称' }]}>
            <Input placeholder="如: 新客首单培育旅程" maxLength={200} />
          </Form.Item>
          <Form.Item name="journeyCode" label="旅程编码" rules={[{ required: true, message: '请输入旅程编码' }]}>
            <Input placeholder="如: NEW_CUSTOMER_ONBOARD" maxLength={100} />
          </Form.Item>
          <Form.Item name="triggerEvent" label="触发事件" rules={[{ required: true, message: '请输入触发事件' }]}>
            <Input placeholder="如: CUSTOMER_CREATED / ORDER_PAID" />
          </Form.Item>
          <Form.Item name="triggerCondition" label="触发条件 JSON">
            <Input.TextArea rows={2} placeholder='{"lifecycle":"NEW"}' />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="旅程说明" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 步骤抽屉 */}
      <Drawer
        title={stepJourney ? `旅程步骤 - ${stepJourney.journeyName}` : '旅程步骤'}
        open={!!stepJourney}
        onClose={() => setStepJourney(null)}
        width={560}
      >
        <Table
          rowKey="id"
          size="small"
          dataSource={steps}
          loading={stepLoading}
          pagination={false}
          locale={{ emptyText: <Empty description="暂无步骤, 请在旅程编排器中配置" /> }}
          columns={[
            { title: '顺序', dataIndex: 'stepOrder', width: 60, render: (v?: number) => (v != null ? `#${v}` : '-') },
            { title: '步骤名称', dataIndex: 'stepName', render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
            {
              title: '类型',
              dataIndex: 'stepType',
              width: 90,
              render: (v: string) => {
                const cfg = stepTypeConfig[v] || { color: 'default', label: v };
                return <Tag color={cfg.color}>{cfg.label}</Tag>;
              },
            },
            { title: '配置', dataIndex: 'config', ellipsis: true, render: (v?: string) => v || '-' },
          ]}
        />
      </Drawer>

      {/* 入组抽屉 */}
      <Drawer
        title={enrollJourney ? `入组客户 - ${enrollJourney.journeyName}` : '入组客户'}
        open={!!enrollJourney}
        onClose={() => setEnrollJourney(null)}
        width={560}
      >
        <Table
          rowKey="id"
          size="small"
          dataSource={enrollments}
          loading={enrollLoading}
          pagination={false}
          locale={{ emptyText: <Empty description="暂无入组客户" /> }}
          columns={[
            { title: '客户', dataIndex: 'customerName', width: 120, render: (v?: string) => v || '-' },
            {
              title: '状态',
              dataIndex: 'status',
              width: 90,
              render: (v: string) => {
                const cfg = enrollStatusConfig[v] || { color: 'default', label: v };
                return <Tag color={cfg.color}>{cfg.label}</Tag>;
              },
            },
            { title: '当前步骤', dataIndex: 'currentStepId', width: 100, render: (v?: string) => v || '-' },
            { title: '入组时间', dataIndex: 'enteredAt', width: 140, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
          ]}
        />
      </Drawer>
    </div>
  );
}