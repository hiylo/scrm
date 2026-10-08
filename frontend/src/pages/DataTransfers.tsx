/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : DataTransfers.tsx
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
} from 'antd';
import {
  PlusOutlined,
  ReloadOutlined,
  EyeOutlined,
  ImportOutlined,
  ExportOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

/** 任务类型映射 */
const typeConfig: Record<string, { color: string; label: string }> = {
  IMPORT: { color: 'blue', label: '导入' },
  EXPORT: { color: 'purple', label: '导出' },
  SYNC: { color: 'cyan', label: '同步' },
};

/** 数据类型映射 */
const dataTypeConfig: Record<string, { color: string; label: string }> = {
  CUSTOMER: { color: 'green', label: '客户' },
  ORDER: { color: 'blue', label: '订单' },
  MESSAGE: { color: 'orange', label: '消息' },
  CHANNEL_CODE: { color: 'cyan', label: '渠道码' },
};

/** 任务状态映射 */
const statusConfig: Record<string, { color: string; label: string }> = {
  PENDING: { color: 'orange', label: '待执行' },
  RUNNING: { color: 'blue', label: '执行中' },
  COMPLETED: { color: 'green', label: '已完成' },
  FAILED: { color: 'red', label: '失败' },
  CANCELLED: { color: 'default', label: '已取消' },
};

/** 数据传输任务行 */
interface ScrmTransferTask {
  id: string;
  taskName: string;
  taskCode: string;
  transferType: string;
  dataType: string;
  status: string;
  totalCount?: number;
  successCount?: number;
  failCount?: number;
  fileUrl?: string;
  completedAt?: string;
  createTime?: string;
}

/** 任务明细行 */
interface TransferRecord {
  id: string;
  taskId: string;
  rowKey?: string;
  status: string;
  errorMessage?: string;
  processedAt?: string;
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
 * 数据导入导出页面
 * <p>
 * 传输任务列表 + 新建导入/导出任务 + 任务明细抽屉。
 * </p>
 *
 * @author Hsi Chu
 */
export default function DataTransfers() {
  const { message } = App.useApp();
  /** 列表 */
  const [list, setList] = useState<ScrmTransferTask[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const pageSize = 10;
  /** 弹窗 */
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  /** 明细抽屉 */
  const [detail, setDetail] = useState<ScrmTransferTask | null>(null);
  const [records, setRecords] = useState<TransferRecord[]>([]);
  const [detailLoading, setDetailLoading] = useState(false);

  /** 加载列表 */
  const loadList = useCallback(async (targetPage = page) => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(pageSize) });
      const data = await apiClient.get<Page<ScrmTransferTask>>(`/scrm/data-transfer/tasks/list?${params.toString()}`);
      setList(data.content || []);
      setTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page]);

  useEffect(() => {
    loadList();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page]);

  /** 打开新建任务弹窗 */
  const openCreate = () => {
    form.resetFields();
    form.setFieldsValue({ transferType: 'EXPORT', dataType: 'CUSTOMER' });
    setOpen(true);
  };

  /** 提交新建任务 */
  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      await apiClient.post('/scrm/data-transfer/tasks', values);
      message.success('任务已创建');
      setOpen(false);
      setPage(0);
      loadList(0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setSaving(false);
    }
  };

  /** 查看任务明细 */
  const openDetail = async (record: ScrmTransferTask) => {
    setDetail(record);
    setRecords([]);
    setDetailLoading(true);
    try {
      const data = await apiClient.get<Page<TransferRecord> | TransferRecord[]>(`/scrm/data-transfer/tasks/${record.id}/records?page=0&size=50`);
      setRecords(Array.isArray(data) ? data : (data.content || []));
    } catch {
      // 拦截器已弹出错误
      setRecords([]);
    } finally {
      setDetailLoading(false);
    }
  };

  /** 表格列 */
  const columns: ColumnsType<ScrmTransferTask> = useMemo(() => [
    { title: '任务名称', dataIndex: 'taskName', width: 180, render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
    { title: '任务编码', dataIndex: 'taskCode', width: 120, render: (v: string) => <Tag>{v}</Tag> },
    {
      title: '类型',
      dataIndex: 'transferType',
      width: 90,
      render: (v: string) => {
        const cfg = typeConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '数据类型',
      dataIndex: 'dataType',
      width: 100,
      render: (v: string) => {
        const cfg = dataTypeConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => {
        const cfg = statusConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '总数', dataIndex: 'totalCount', width: 70, render: (v?: number) => v ?? '-' },
    { title: '成功', dataIndex: 'successCount', width: 70, render: (v?: number) => v ?? '-' },
    { title: '失败', dataIndex: 'failCount', width: 70, render: (v?: number) => v ?? '-' },
    { title: '完成时间', dataIndex: 'completedAt', width: 140, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
    {
      title: '操作',
      key: 'actions',
      width: 90,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => openDetail(r)}>
            明细
          </Button>
        </Space>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  return (
    <div className="data-transfers-page">
      <Card
        title="数据导入导出"
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => loadList()} />
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              新建任务
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
          scroll={{ y: 'calc(100vh - 320px)' }}
        />
      </Card>

      {/* 创建任务弹窗 */}
      <Modal
        title="新建导入/导出任务"
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="taskName" label="任务名称" rules={[{ required: true, message: '请输入任务名称' }]}>
            <Input placeholder="如: 10 月客户导出" maxLength={200} />
          </Form.Item>
          <Form.Item name="taskCode" label="任务编码" rules={[{ required: true, message: '请输入任务编码' }]}>
            <Input placeholder="如: EXPORT_OCT" maxLength={100} />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="transferType" label="类型" rules={[{ required: true }]}>
              <Select style={{ width: 120 }} options={Object.entries(typeConfig).map(([v, c]) => ({ value: v, label: c.label }))} />
            </Form.Item>
            <Form.Item name="dataType" label="数据类型" rules={[{ required: true }]}>
              <Select style={{ width: 140 }} options={Object.entries(dataTypeConfig).map(([v, c]) => ({ value: v, label: c.label }))} />
            </Form.Item>
          </Space>
        </Form>
      </Modal>

      {/* 明细抽屉 */}
      <Drawer
        title={detail ? `任务明细 - ${detail.taskName}` : '任务明细'}
        open={!!detail}
        onClose={() => setDetail(null)}
        width={560}
      >
        <Table
          rowKey="id"
          size="small"
          dataSource={records}
          loading={detailLoading}
          pagination={false}
          locale={{ emptyText: <Empty description="暂无明细" /> }}
          columns={[
            { title: '行键', dataIndex: 'rowKey', width: 120, render: (v?: string) => v || '-' },
            { title: '结果', dataIndex: 'status', width: 90, render: (v: string) => (v === 'SUCCESS' ? <Tag color="green">成功</Tag> : <Tag color="red">失败</Tag>) },
            { title: '错误', dataIndex: 'errorMessage', ellipsis: true, render: (v?: string) => v || '-' },
            { title: '处理时间', dataIndex: 'processedAt', width: 140, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
          ]}
        />
      </Drawer>
    </div>
  );
}