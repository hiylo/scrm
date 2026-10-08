/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Competitors.tsx
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
  StopOutlined,
  ShoppingOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

/** 威胁等级映射 */
const threatConfig: Record<string, { color: string; label: string }> = {
  HIGH: { color: 'red', label: '高' },
  MEDIUM: { color: 'orange', label: '中' },
  LOW: { color: 'default', label: '低' },
};

/** 状态映射 */
const statusConfig: Record<string, { color: string; label: string }> = {
  ACTIVE: { color: 'green', label: '监测中' },
  SUSPENDED: { color: 'default', label: '已停测' },
};

/** 竞品实体 */
interface ScrmCompetitor {
  id: string;
  competitorName: string;
  competitorCode: string;
  industry?: string;
  threatLevel: string;
  marketShare?: number;
  monitoringEnabled: boolean;
  status: string;
  website?: string;
  createTime?: string;
}

/** 竞品产品 */
interface ScrmCompetitorProduct {
  id: string;
  competitorId: string;
  productName: string;
  productCategory?: string;
  price?: number;
  currency?: string;
  lastPriceChangeAt?: string;
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
 * 竞品监测页
 * <p>
 * 竞品 CRUD + 监控启停 + 产品价格抽屉 (监测竞品产品与调价动态)。
 * </p>
 *
 * @author Hsi Chu
 */
export default function Competitors() {
  const { message } = App.useApp();
  /** 列表 */
  const [list, setList] = useState<ScrmCompetitor[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const pageSize = 10;
  /** 弹窗 */
  const [editing, setEditing] = useState<ScrmCompetitor | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  /** 产品抽屉 */
  const [detailComp, setDetailComp] = useState<ScrmCompetitor | null>(null);
  const [products, setProducts] = useState<ScrmCompetitorProduct[]>([]);
  const [productLoading, setProductLoading] = useState(false);

  /** 加载列表 */
  const loadList = useCallback(async (targetPage = page) => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(pageSize) });
      const data = await apiClient.get<Page<ScrmCompetitor>>(`/scrm/competitors/list?${params.toString()}`);
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

  /** 打开创建 */
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ threatLevel: 'MEDIUM', monitoringFrequency: 'DAILY' });
    setOpen(true);
  };

  /** 提交 */
  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await apiClient.put(`/scrm/competitors/${editing.id}`, values);
        message.success('竞品已更新');
      } else {
        await apiClient.post('/scrm/competitors', values);
        message.success('竞品已创建');
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

  /** 停止监测 */
  const handleDisable = async (record: ScrmCompetitor) => {
    try {
      await apiClient.post(`/scrm/competitors/${record.id}/monitoring/disable`);
      message.success('已停止监测');
      loadList();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 打开产品抽屉 */
  const openProducts = async (record: ScrmCompetitor) => {
    setDetailComp(record);
    setProductLoading(true);
    try {
      const data = await apiClient.get<Page<ScrmCompetitorProduct>>(`/scrm/competitors/products/by-competitor/${record.id}`);
      setProducts(data.content || (Array.isArray(data) ? data : []));
    } catch {
      setProducts([]);
    } finally {
      setProductLoading(false);
    }
  };

  /** 表格列 */
  const columns: ColumnsType<ScrmCompetitor> = useMemo(() => [
    { title: '竞品名称', dataIndex: 'competitorName', width: 160, render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
    { title: '竞品编码', dataIndex: 'competitorCode', width: 120, render: (v: string) => <Tag>{v}</Tag> },
    { title: '行业', dataIndex: 'industry', width: 110, render: (v?: string) => v || '-' },
    {
      title: '威胁等级',
      dataIndex: 'threatLevel',
      width: 100,
      render: (v: string) => {
        const cfg = threatConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '市占率', dataIndex: 'marketShare', width: 90, render: (v?: number) => (v != null ? `${v}%` : '-') },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => {
        const cfg = statusConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '操作',
      key: 'actions',
      width: 160,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          <Button type="link" size="small" icon={<ShoppingOutlined />} onClick={() => openProducts(r)}>
            产品
          </Button>
          {r.monitoringEnabled && (
            <Button type="link" size="small" danger icon={<StopOutlined />} onClick={() => handleDisable(r)}>
              停测
            </Button>
          )}
        </Space>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  return (
    <div className="competitors-page">
      <Card
        title="竞品监测"
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => loadList()} />
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              新建竞品
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
        />
      </Card>

      {/* 竞品弹窗 */}
      <Modal
        title={editing ? '编辑竞品' : '新建竞品'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={560}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="competitorName" label="竞品名称" rules={[{ required: true, message: '请输入竞品名称' }]}>
            <Input placeholder="如: XX 云" maxLength={200} />
          </Form.Item>
          <Form.Item name="competitorCode" label="竞品编码" rules={[{ required: true, message: '请输入竞品编码' }]}>
            <Input placeholder="如: XX_CLOUD" maxLength={50} />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="industry" label="行业">
              <Input placeholder="如: SaaS" style={{ width: 140 }} />
            </Form.Item>
            <Form.Item name="threatLevel" label="威胁等级" rules={[{ required: true }]}>
              <Select style={{ width: 140 }} options={Object.entries(threatConfig).map(([v, c]) => ({ value: v, label: c.label }))} />
            </Form.Item>
            <Form.Item name="marketShare" label="市占率 (%)">
              <Input type="number" style={{ width: 110 }} />
            </Form.Item>
          </Space>
          <Form.Item name="website" label="官网">
            <Input placeholder="https://..." />
          </Form.Item>
          <Form.Item name="monitoringFrequency" label="监测频次">
            <Select options={[{ value: 'DAILY', label: '每日' }, { value: 'WEEKLY', label: '每周' }, { value: 'MONTHLY', label: '每月' }]} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="竞品说明" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 产品抽屉 */}
      <Drawer
        title={detailComp ? `竞品产品 - ${detailComp.competitorName}` : '竞品产品'}
        open={!!detailComp}
        onClose={() => setDetailComp(null)}
        width={560}
      >
        <Table
          rowKey="id"
          size="small"
          dataSource={products}
          loading={productLoading}
          pagination={false}
          locale={{ emptyText: <Empty description="暂无产品数据" /> }}
          columns={[
            { title: '产品名称', dataIndex: 'productName', render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
            { title: '分类', dataIndex: 'productCategory', width: 100, render: (v?: string) => v || '-' },
            { title: '价格', dataIndex: 'price', width: 100, render: (v?: number) => (v != null ? `¥${v.toLocaleString()}` : '-') },
            { title: '币种', dataIndex: 'currency', width: 70, render: (v?: string) => v || '-' },
            { title: '最近调价', dataIndex: 'lastPriceChangeAt', width: 140, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD') : '-') },
          ]}
        />
      </Drawer>
    </div>
  );
}
