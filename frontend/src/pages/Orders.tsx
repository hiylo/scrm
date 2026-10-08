/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Orders.tsx
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
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  ReloadOutlined,
  SearchOutlined,
  CheckCircleOutlined,
  EyeOutlined,
  StopOutlined,
  MoneyCollectOutlined,
  CarOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

const { Text } = Typography;

/** 产品订单实体 */
interface ScrmOrder {
  id: string;
  orderNo: string;
  customerId: string;
  customerName?: string;
  orderType?: string;
  productName?: string;
  productQuantity?: number;
  unitPrice?: number;
  orderAmount?: number;
  remark?: string;
  orderStatus: string; // PENDING / CONFIRMED / PAID / SHIPPED / DELIVERED / COMPLETED / CANCELLED
  createdAt?: string;
}

/** 订单明细行 */
interface ScrmOrderItem {
  id: string;
  productName?: string;
  quantity?: number;
  unitPrice?: number;
  totalPrice?: number;
}

/** Spring Page 分页响应 */
interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/** 订单类型映射 */
const orderTypeConfig: Record<string, { label: string; color: string }> = {
  PRODUCT: { label: '产品订单', color: 'blue' },
  SERVICE: { label: '服务订单', color: 'green' },
};

/** 订单状态映射 (状态机: PENDING→CONFIRMED→PAID→SHIPPED→DELIVERED→COMPLETED) */
const orderStatusConfig: Record<string, { color: string; label: string }> = {
  PENDING: { color: 'orange', label: '待确认' },
  CONFIRMED: { color: 'blue', label: '已确认' },
  PAID: { color: 'cyan', label: '已支付' },
  SHIPPED: { color: 'purple', label: '已发货' },
  DELIVERED: { color: 'geekblue', label: '已送达' },
  COMPLETED: { color: 'green', label: '已完成' },
  CANCELLED: { color: 'red', label: '已取消' },
};

/**
 * 产品订单管理页
 * <p>
 * 订单 CRUD + 状态机流转 (确认→支付→发货→送达→完成, 可取消)。
 * </p>
 *
 * @author Hsi Chu
 */
export default function Orders() {
  const { message } = App.useApp();
  /** 列表 */
  const [list, setList] = useState<ScrmOrder[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const pageSize = 10;
  const [keyword, setKeyword] = useState('');
  const [statusFilter, setStatusFilter] = useState<string | undefined>();
  /** 弹窗 */
  const [editing, setEditing] = useState<ScrmOrder | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  /** 明细抽屉 */
  const [detailOrder, setDetailOrder] = useState<ScrmOrder | null>(null);
  const [items, setItems] = useState<ScrmOrderItem[]>([]);
  const [detailLoading, setDetailLoading] = useState(false);
  /** 状态机动作 map 配置一个文本映射, 用于操作提示 */
  const actionLabel: Record<string, string> = {
    confirm: '确认', pay: '支付', ship: '发货', deliver: '送达', complete: '完成', cancel: '取消',
  };

  const loadList = useCallback(async (targetPage = page, k = keyword, status = statusFilter) => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(pageSize) });
      if (k) params.set('keyword', k);
      if (status) params.set('status', status);
      const data = await apiClient.get<Page<ScrmOrder>>(`/scrm/orders/list?${params.toString()}`);
      setList(data.content || []);
      setTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setLoading(false);
    }
  }, [page, keyword, statusFilter]);

  useEffect(() => {
    loadList();
  }, [loadList]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ orderType: 'PRODUCT', productQuantity: 1 });
    setOpen(true);
  };

  const openEdit = (record: ScrmOrder) => {
    setEditing(record);
    form.setFieldsValue(record);
    setOpen(true);
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await apiClient.put(`/scrm/orders/${editing.id}`, values);
        message.success('订单已更新');
      } else {
        await apiClient.post('/scrm/orders', values);
        message.success('订单已创建');
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

  /** 状态机流转 */
  const changeStatus = async (record: ScrmOrder, action: string) => {
    try {
      await apiClient.post(`/scrm/orders/${record.id}/${action}`, undefined);
      message.success(`订单已${actionLabel[action] || action}`);
      loadList();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 打开明细抽屉 */
  const openDetail = async (record: ScrmOrder) => {
    setDetailOrder(record);
    setDetailLoading(true);
    try {
      const data = await apiClient.get<ScrmOrderItem[]>(`/scrm/orders/${record.id}/items`);
      setItems(data || []);
    } catch {
      setItems([]);
    } finally {
      setDetailLoading(false);
    }
  };

  const columns: ColumnsType<ScrmOrder> = useMemo(() => [
    { title: '订单号', dataIndex: 'orderNo', width: 140, render: (v: string) => <Text code>{v}</Text> },
    { title: '客户', dataIndex: 'customerName', width: 120, render: (v?: string, r?: ScrmOrder) => v || r?.customerId || '-' },
    { title: '类型', dataIndex: 'orderType', width: 100, render: (v?: string) => { const c = orderTypeConfig[v || ''] || { label: v || '-', color: 'default' }; return <Tag color={c.color}>{c.label}</Tag>; } },
    { title: '产品', dataIndex: 'productName', ellipsis: true, render: (v?: string) => v || '-' },
    {
      title: '金额',
      dataIndex: 'orderAmount',
      width: 110,
      render: (v?: number) => (v != null ? <Text strong>¥{v.toLocaleString()}</Text> : '-'),
    },
    {
      title: '状态',
      dataIndex: 'orderStatus',
      width: 90,
      render: (v: string) => { const c = orderStatusConfig[v] || { color: 'default', label: v }; return <Tag color={c.color}>{c.label}</Tag>; },
    },
    { title: '创建时间', dataIndex: 'createdAt', width: 150, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
    {
      title: '操作',
      key: 'actions',
      width: 260,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => openDetail(r)}>
            明细
          </Button>
          {r.orderStatus === 'PENDING' && (
            <Button type="link" size="small" icon={<CheckCircleOutlined />} onClick={() => changeStatus(r, 'confirm')}>
              确认
            </Button>
          )}
          {r.orderStatus === 'CONFIRMED' && (
            <Button type="link" size="small" icon={<MoneyCollectOutlined />} onClick={() => changeStatus(r, 'pay')}>
              支付
            </Button>
          )}
          {r.orderStatus === 'PAID' && (
            <Button type="link" size="small" icon={<CarOutlined />} onClick={() => changeStatus(r, 'ship')}>
              发货
            </Button>
          )}
          {r.orderStatus === 'SHIPPED' && (
            <Button type="link" size="small" onClick={() => changeStatus(r, 'deliver')}>
              送达
            </Button>
          )}
          {r.orderStatus === 'DELIVERED' && (
            <Button type="link" size="small" icon={<CheckCircleOutlined />} onClick={() => changeStatus(r, 'complete')}>
              完成
            </Button>
          )}
          {(r.orderStatus === 'PENDING' || r.orderStatus === 'CONFIRMED') && (
            <Button type="link" size="small" danger icon={<StopOutlined />} onClick={() => changeStatus(r, 'cancel')}>
              取消
            </Button>
          )}
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(r)}>
            编辑
          </Button>
        </Space>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  return (
    <div className="orders-page">
      <Card
        title="产品订单"
        extra={
          <Space>
            <Input
              placeholder="搜索单号/客户"
              prefix={<SearchOutlined />}
              allowClear
              style={{ width: 200 }}
              onChange={e => { setKeyword(e.target.value); setPage(0); }}
            />
            <Select
              placeholder="状态"
              allowClear
              style={{ width: 110 }}
              options={Object.entries(orderStatusConfig).map(([v, c]) => ({ value: v, label: c.label }))}
              onChange={v => { setStatusFilter(v); setPage(0); }}
            />
            <Button icon={<ReloadOutlined />} onClick={() => loadList()} />
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              新建订单
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

      {/* 订单弹窗 */}
      <Modal
        title={editing ? '编辑订单' : '新建订单'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={640}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="customerId" label="客户 ID" rules={[{ required: true, message: '请输入客户 ID' }]}>
            <Input placeholder="客户 ID" />
          </Form.Item>
          <Form.Item name="customerName" label="客户名称">
            <Input placeholder="客户名称" />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="orderType" label="订单类型" rules={[{ required: true }]}>
              <Select style={{ width: 150 }} options={Object.entries(orderTypeConfig).map(([v, c]) => ({ value: v, label: c.label }))} />
            </Form.Item>
            <Form.Item name="orderAmount" label="订单金额 (¥)" rules={[{ required: true }]}>
              <InputNumber min={0} style={{ width: 150 }} />
            </Form.Item>
          </Space>
          <Form.Item name="productName" label="产品名称">
            <Input placeholder="产品名称" />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="productQuantity" label="数量">
              <InputNumber min={1} style={{ width: 120 }} />
            </Form.Item>
            <Form.Item name="unitPrice" label="单价 (¥)">
              <InputNumber min={0} style={{ width: 120 }} />
            </Form.Item>
          </Space>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} placeholder="订单备注" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 明细抽屉 */}
      <Drawer
        title={detailOrder ? `订单明细 - ${detailOrder.orderNo}` : '订单明细'}
        open={!!detailOrder}
        onClose={() => setDetailOrder(null)}
        width={560}
      >
        {detailOrder && (
          <div style={{ display: 'flex', gap: 16, marginBottom: 16 }}>
            <Statistic title="订单金额" value={detailOrder.orderAmount ?? 0} prefix="¥" />
          </div>
        )}
        <Table
          rowKey="id"
          size="small"
          dataSource={items}
          loading={detailLoading}
          pagination={false}
          locale={{ emptyText: <Empty description="暂无明细行" /> }}
          columns={[
            { title: '产品', dataIndex: 'productName', ellipsis: true, render: (v?: string) => v || '-' },
            { title: '数量', dataIndex: 'quantity', width: 80, render: (v?: number) => v ?? '-' },
            { title: '单价', dataIndex: 'unitPrice', width: 100, render: (v?: number) => (v != null ? `¥${v.toLocaleString()}` : '-') },
            { title: '小计', dataIndex: 'totalPrice', width: 100, render: (v?: number) => (v != null ? `¥${v.toLocaleString()}` : '-') },
          ]}
        />
      </Drawer>
    </div>
  );
}