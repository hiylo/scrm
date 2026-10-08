/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Attribution.tsx
 * Date : 2026/10/08 12:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  App,
  Button,
  Card,
  Col,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
} from 'antd';
import {
  PlusOutlined,
  ReloadOutlined,
  SendOutlined,
  ApiOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

/** 归因类型映射 */
const modelTypeConfig: Record<string, { color: string; label: string }> = {
  LAST_TOUCH: { color: 'blue', label: '末次触点' },
  FIRST_TOUCH: { color: 'cyan', label: '首次触点' },
  LINEAR: { color: 'green', label: '线性归因' },
  TIME_DECAY: { color: 'orange', label: '时间衰减' },
  POSITION_BASED: { color: 'purple', label: '位置归因' },
};

/** 模型状态映射 */
const statusConfig: Record<string, { color: string; label: string }> = {
  DRAFT: { color: 'default', label: '草稿' },
  PUBLISHED: { color: 'green', label: '已发布' },
  DISABLED: { color: 'red', label: '已停用' },
};

/** 归因模型实体 */
interface ScrmAttributionModel {
  id: string;
  modelName: string;
  modelCode: string;
  modelType: string;
  attributionWindowDays?: number;
  status: string;
  description?: string;
  createTime?: string;
}

/** 触点记录 */
interface ScrmAttributionTouchpoint {
  id: string;
  customerId?: string;
  customerName?: string;
  channel?: string;
  touchpointType?: string;
  attributionWeight?: number;
  attributedRevenue?: number;
  touchpointTime?: string;
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
 * 归因分析页
 * <p>
 * 归因模型配置 (CRUD + 发布) + 触点明细 (全局列表与按模型抽屉)。
 * </p>
 *
 * @author Hsi Chu
 */
export default function Attribution() {
  const { message } = App.useApp();
  /** 模型列表 */
  const [models, setModels] = useState<ScrmAttributionModel[]>([]);
  const [modelsLoading, setModelsLoading] = useState(false);
  const [modelTotal, setModelTotal] = useState(0);
  const [modelPage, setModelPage] = useState(0);
  const modelPageSize = 10;
  /** 触点列表 */
  const [touchpoints, setTouchpoints] = useState<ScrmAttributionTouchpoint[]>([]);
  const [tpLoading, setTpLoading] = useState(false);
  const [tpPage, setTpPage] = useState(0);
  const tpPageSize = 10;
  /** 模型弹窗 */
  const [editing, setEditing] = useState<ScrmAttributionModel | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  /** 触点抽屉 */
  const [detailModel, setDetailModel] = useState<ScrmAttributionModel | null>(null);
  const [modelTouchpoints, setModelTouchpoints] = useState<ScrmAttributionTouchpoint[]>([]);
  const [detailTotal, setDetailTotal] = useState(0);
  const [detailPage, setDetailPage] = useState(0);
  const [detailLoading, setDetailLoading] = useState(false);

  /** 加载模型 */
  const loadModels = useCallback(async (targetPage = modelPage) => {
    setModelsLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(modelPageSize) });
      const data = await apiClient.get<Page<ScrmAttributionModel>>(`/scrm/attribution/models/list?${params.toString()}`);
      setModels(data.content || []);
      setModelTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setModelsLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [modelPage]);

  /** 加载触点 */
  const loadTouchpoints = useCallback(async (targetPage = tpPage) => {
    setTpLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(tpPageSize) });
      const data = await apiClient.get<Page<ScrmAttributionTouchpoint>>(`/scrm/attribution/touchpoints/list?${params.toString()}`);
      setTouchpoints(data.content || []);
    } catch {
      setTouchpoints([]);
    } finally {
      setTpLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tpPage]);

  useEffect(() => {
    loadModels();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [modelPage]);

  useEffect(() => {
    loadTouchpoints();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tpPage]);

  /** 打开创建 */
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ modelType: 'LAST_TOUCH', attributionWindowDays: 30 });
    setOpen(true);
  };

  /** 提交 */
  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await apiClient.put(`/scrm/attribution/models/${editing.id}`, values);
        message.success('模型已更新');
      } else {
        await apiClient.post('/scrm/attribution/models', values);
        message.success('模型已创建');
      }
      setOpen(false);
      setModelPage(0);
      loadModels(0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setSaving(false);
    }
  };

  /** 发布模型 */
  const handlePublish = async (record: ScrmAttributionModel) => {
    try {
      await apiClient.post(`/scrm/attribution/models/${record.id}/publish`);
      message.success('模型已发布');
      loadModels();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 打开触点抽屉 */
  const openDetail = async (record: ScrmAttributionModel, targetPage = 0) => {
    setDetailModel(record);
    setDetailLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: '20' });
      const data = await apiClient.get<Page<ScrmAttributionTouchpoint>>(`/scrm/attribution/touchpoints?${params.toString()}`);
      setModelTouchpoints(prev => (targetPage === 0 ? data.content || [] : [...prev, ...(data.content || [])]));
      setDetailTotal(data.totalElements || 0);
      setDetailPage(targetPage);
    } catch {
      if (targetPage === 0) setModelTouchpoints([]);
    } finally {
      setDetailLoading(false);
    }
  };

  /** 触点列 */
  const touchpointColumns: ColumnsType<ScrmAttributionTouchpoint> = useMemo(() => [
    { title: '客户', dataIndex: 'customerName', width: 120, render: (v: string | undefined, r) => v || r.customerId || '-' },
    { title: '渠道', dataIndex: 'channel', width: 110, render: (v?: string) => v || '-' },
    { title: '触点类型', dataIndex: 'touchpointType', width: 110, render: (v?: string) => v || '-' },
    { title: '归因权重', dataIndex: 'attributionWeight', width: 100, render: (v?: number) => (v != null ? v.toFixed(2) : '-') },
    { title: '归因收入', dataIndex: 'attributedRevenue', width: 110, render: (v?: number) => (v != null ? `¥${v.toLocaleString()}` : '-') },
    { title: '触点时间', dataIndex: 'touchpointTime', width: 150, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
  ], []);

  /** 模型列 */
  const modelColumns: ColumnsType<ScrmAttributionModel> = useMemo(() => [
    { title: '模型名称', dataIndex: 'modelName', render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
    { title: '模型编码', dataIndex: 'modelCode', width: 150, render: (v: string) => <Tag>{v}</Tag> },
    {
      title: '归因类型',
      dataIndex: 'modelType',
      width: 110,
      render: (v: string) => {
        const cfg = modelTypeConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '归因窗口', dataIndex: 'attributionWindowDays', width: 100, render: (v?: number) => (v != null ? `${v} 天` : '-') },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => {
        const cfg = statusConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '创建时间', dataIndex: 'createTime', width: 140, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
    {
      title: '操作',
      key: 'actions',
      width: 140,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          {r.status === 'DRAFT' && (
            <Button type="link" size="small" icon={<SendOutlined />} onClick={() => handlePublish(r)}>
              发布
            </Button>
          )}
          <Button type="link" size="small" icon={<ApiOutlined />} onClick={() => openDetail(r, 0)}>
            触点
          </Button>
        </Space>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  return (
    <div className="attribution-page">
      <Tabs
        defaultActiveKey="models"
        items={[
          {
            key: 'models',
            label: '归因模型',
            children: (
              <Card
                title="归因模型"
                extra={
                  <Space>
                    <Button icon={<ReloadOutlined />} onClick={() => loadModels()} />
                    <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                      新建模型
                    </Button>
                  </Space>
                }
              >
                <Table
                  rowKey="id"
                  columns={modelColumns}
                  dataSource={models}
                  loading={modelsLoading}
                  pagination={{
                    current: modelPage + 1,
                    pageSize: modelPageSize,
                    total: modelTotal,
                    showTotal: t => `共 ${t} 条`,
                    onChange: p => setModelPage(p - 1),
                  }}
                />
              </Card>
            ),
          },
          {
            key: 'touchpoints',
            label: '触点明细',
            children: (
              <Card
                title="客户触点记录"
                extra={<Button icon={<ReloadOutlined />} onClick={() => loadTouchpoints()} />}
              >
                <Table
                  rowKey="id"
                  dataSource={touchpoints}
                  loading={tpLoading}
                  pagination={{
                    current: tpPage + 1,
                    pageSize: tpPageSize,
                    total: touchpoints.length,
                    showTotal: t => `共 ${t} 条`,
                    onChange: p => setTpPage(p - 1),
                  }}
                  locale={{ emptyText: <Empty description="暂无触点数据" /> }}
                  columns={touchpointColumns}
                />
              </Card>
            ),
          },
        ]}
      />

      {/* 模型弹窗 */}
      <Modal
        title={editing ? '编辑归因模型' : '新建归因模型'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={560}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="modelName" label="模型名称" rules={[{ required: true, message: '请输入模型名称' }]}>
            <Input placeholder="如: 末次点击归因" maxLength={200} />
          </Form.Item>
          <Form.Item name="modelCode" label="模型编码" rules={[{ required: true, message: '请输入模型编码' }]}>
            <Input placeholder="如: LAST_TOUCH_30D" maxLength={50} />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="modelType" label="归因类型" rules={[{ required: true }]}>
              <Select style={{ width: 180 }} options={Object.entries(modelTypeConfig).map(([v, c]) => ({ value: v, label: c.label }))} />
            </Form.Item>
            <Form.Item name="attributionWindowDays" label="归因窗口 (天)">
              <InputNumber min={1} style={{ width: 140 }} />
            </Form.Item>
          </Space>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="模型说明" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 触点抽屉 */}
      <Drawer
        title={detailModel ? `触点明细 - ${detailModel.modelName}` : '触点明细'}
        open={!!detailModel}
        onClose={() => setDetailModel(null)}
        width={720}
      >
        <Row gutter={16} style={{ marginBottom: 12 }}>
          <Col>
            <Table
              rowKey="id"
              size="small"
              dataSource={modelTouchpoints}
              loading={detailLoading}
              pagination={false}
              locale={{ emptyText: <Empty description="暂无触点数据" /> }}
              columns={touchpointColumns}
            />
          </Col>
        </Row>
        {modelTouchpoints.length < detailTotal && (
          <div style={{ textAlign: 'center', marginTop: 8 }}>
            <Button type="link" loading={detailLoading} onClick={() => detailModel && openDetail(detailModel, detailPage + 1)}>
              加载更早 ({modelTouchpoints.length}/{detailTotal})
            </Button>
          </div>
        )}
      </Drawer>
    </div>
  );
}
