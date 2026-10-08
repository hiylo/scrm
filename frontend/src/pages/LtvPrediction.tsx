/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : LtvPrediction.tsx
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
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  ReloadOutlined,
  SearchOutlined,
  SendOutlined,
  EyeOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

const { Text } = Typography;

/** LTV 预测模型实体 */
interface ScrmLtvModel {
  id: string;
  modelName: string;
  modelCode: string;
  modelType: string; // HISTORICAL / PREDICTIVE / MIXED
  timeWindowDays?: number;
  predictionPeriodDays?: number;
  parameters?: string;
  description?: string;
  status: string; // DRAFT / PUBLISHED
  createTime?: string;
}

/** 客户 LTV 预测结果 */
interface ScrmCustomerLtv {
  id: string;
  modelId?: string;
  customerId?: string;
  customerName?: string;
  predictedLtv?: number;
  remainingLtv?: number;
  predictedLifespanMonths?: number;
  churnRisk?: number;
  calcTime?: string;
}

/** 同期群留存数据 */
interface ScrmCohort {
  id: string;
  cohortName: string;
  cohortMonth?: string;
  customerCount?: number;
  retentionRate?: number;
  avgRevenue?: number;
  totalRevenue?: number;
}

/** Spring Page 分页响应 */
interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/** 模型类型映射 */
const modelTypeConfig: Record<string, { color: string; label: string }> = {
  HISTORICAL: { color: 'blue', label: '历史型' },
  PREDICTIVE: { color: 'purple', label: '预测型' },
  MIXED: { color: 'cyan', label: '混合型' },
};

/** 模型状态映射 */
const modelStatusConfig: Record<string, { color: string; label: string }> = {
  DRAFT: { color: 'default', label: '草稿' },
  PUBLISHED: { color: 'green', label: '已发布' },
};

/** 流失风险颜色 */
function riskColor(v: number): string {
  if (v == null) return 'default';
  if (v < 0.2) return 'green';
  if (v < 0.5) return 'orange';
  return 'red';
}

/**
 * LTV 预测管理页
 * <p>
 * 基于客户历史行为构建生命周期价值模型; 支持模型新建/发布、同期群留存分析、客户 LTV 预测明细。
 * </p>
 *
 * @author Hsi Chu
 */
export default function LtvPrediction() {
  const { message } = App.useApp();
  /** 模型列表 */
  const [models, setModels] = useState<ScrmLtvModel[]>([]);
  const [modelsLoading, setModelsLoading] = useState(false);
  const [modelPage, setModelPage] = useState(0);
  const modelPageSize = 10;
  const [modelTotal, setModelTotal] = useState(0);
  const [modelKeyword, setModelKeyword] = useState('');
  /** 同期群 */
  const [cohorts, setCohorts] = useState<ScrmCohort[]>([]);
  const [cohortsLoading, setCohortsLoading] = useState(false);
  const [cohortPage, setCohortPage] = useState(0);
  const cohortPageSize = 10;
  /** 弹窗 */
  const [editing, setEditing] = useState<ScrmLtvModel | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  /** 预测明细 */
  const [detailModel, setDetailModel] = useState<ScrmLtvModel | null>(null);
  const [predictions, setPredictions] = useState<ScrmCustomerLtv[]>([]);
  const [predLoading, setPredLoading] = useState(false);
  const [predPage, setPredPage] = useState(0);
  const predPageSize = 20;
  const [predTotal, setPredTotal] = useState(0);

  const loadModels = useCallback(async (targetPage = modelPage, keyword = modelKeyword) => {
    setModelsLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(modelPageSize) });
      if (keyword) params.set('keyword', keyword);
      const data = await apiClient.get<Page<ScrmLtvModel>>(`/scrm/ltv/models/list?${params.toString()}`);
      setModels(data.content || []);
      setModelTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setModelsLoading(false);
    }
  }, [modelPage, modelKeyword]);

  const loadCohorts = useCallback(async (targetPage = 0) => {
    setCohortsLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(cohortPageSize) });
      const data = await apiClient.get<Page<ScrmCohort>>(`/scrm/ltv/cohorts/list?${params.toString()}`);
      setCohorts(data.content || []);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setCohortsLoading(false);
    }
  }, [cohortPageSize]);

  useEffect(() => {
    loadModels();
  }, [loadModels]);

  useEffect(() => {
    loadCohorts();
  }, [loadCohorts]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ modelType: 'HISTORICAL' });
    setOpen(true);
  };

  const openEdit = (record: ScrmLtvModel) => {
    setEditing(record);
    form.setFieldsValue(record);
    setOpen(true);
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await apiClient.put(`/scrm/ltv/models/${editing.id}`, values);
        message.success('LTV 模型已更新');
      } else {
        await apiClient.post('/scrm/ltv/models', values);
        message.success('LTV 模型已创建');
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

  const handlePublish = async (record: ScrmLtvModel) => {
    try {
      await apiClient.post(`/scrm/ltv/models/${record.id}/publish`);
      message.success('LTV 模型已发布');
      loadModels();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 打开预测明细 */
  const openDetail = async (record: ScrmLtvModel, targetPage = 0) => {
    setDetailModel(record);
    setPredLoading(true);
    try {
      const params = new URLSearchParams({
        modelId: String(record.id),
        page: String(targetPage),
        size: String(predPageSize),
      });
      const data = await apiClient.get<Page<ScrmCustomerLtv>>(`/scrm/ltv/customers/list?${params.toString()}`);
      const rows = data.content || [];
      setPredictions(targetPage === 0 ? rows : prev => [...prev, ...rows]);
      setPredTotal(data.totalElements || 0);
      setPredPage(targetPage);
    } catch {
      setPredictions([]);
      setPredTotal(0);
    } finally {
      setPredLoading(false);
    }
  };

  const modelColumns: ColumnsType<ScrmLtvModel> = useMemo(() => [
    { title: '模型名称', dataIndex: 'modelName', render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
    { title: '模型编码', dataIndex: 'modelCode', width: 120, render: (v: string) => <Text code>{v}</Text> },
    { title: '类型', dataIndex: 'modelType', width: 100, render: (v: string) => { const c = modelTypeConfig[v] || { color: 'default', label: v }; return <Tag color={c.color}>{c.label}</Tag>; } },
    { title: '状态', dataIndex: 'status', width: 90, render: (v: string) => { const c = modelStatusConfig[v] || { color: 'default', label: v }; return <Tag color={c.color}>{c.label}</Tag>; } },
    { title: '历史窗口(天)', dataIndex: 'timeWindowDays', width: 110, render: (v?: number) => v ?? '-' },
    { title: '预测期(天)', dataIndex: 'predictionPeriodDays', width: 110, render: (v?: number) => v ?? '-' },
    { title: '创建时间', dataIndex: 'createTime', width: 140, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
    {
      title: '操作',
      key: 'actions',
      width: 150,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          {r.status === 'DRAFT' && (
            <Button type="link" size="small" icon={<SendOutlined />} onClick={() => handlePublish(r)}>
              发布
            </Button>
          )}
          {r.status === 'PUBLISHED' && (
            <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => openDetail(r)}>
              预测
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
    <div className="ltv-prediction-page">
      <Tabs
        items={[
          {
            key: 'models',
            label: '模型管理',
            children: (
              <Card
                title="LTV 预测模型"
                extra={
                  <Space>
                    <Input
                      placeholder="搜索模型名称"
                      prefix={<SearchOutlined />}
                      allowClear
                      style={{ width: 200 }}
                      onChange={e => { setModelKeyword(e.target.value); }}
                    />
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
                  scroll={{ y: 'calc(100vh - 320px)' }}
                />
              </Card>
            ),
          },
          {
            key: 'cohorts',
            label: '同期群分析',
            children: (
              <Card
                title="同期群 (Cohort) 留存"
                extra={<Button icon={<ReloadOutlined />} onClick={() => loadCohorts()} />}
              >
                <Table
                  rowKey="id"
                  dataSource={cohorts}
                  loading={cohortsLoading}
                  pagination={{
                    current: cohortPage + 1,
                    pageSize: cohortPageSize,
                    total: cohorts.length,
                    showTotal: t => `共 ${t} 条`,
                    onChange: p => setCohortPage(p - 1),
                  }}
                  locale={{ emptyText: <Empty description="暂无同期群数据" /> }}
                  columns={[
                    { title: '同期群', dataIndex: 'cohortName', render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
                    { title: '月份', dataIndex: 'cohortMonth', width: 100, render: (v?: string) => v || '-' },
                    { title: '客户数', dataIndex: 'customerCount', width: 90, render: (v?: number) => v ?? '-' },
                    { title: '留存率', dataIndex: 'retentionRate', width: 100, render: (v?: number) => (v != null ? `${(v * 100).toFixed(1)}%` : '-') },
                    { title: '客均收入', dataIndex: 'avgRevenue', width: 110, render: (v?: number) => (v != null ? `¥${v.toLocaleString()}` : '-') },
                    { title: '总收入', dataIndex: 'totalRevenue', width: 120, render: (v?: number) => (v != null ? `¥${v.toLocaleString()}` : '-') },
                  ]}
                />
              </Card>
            ),
          },
        ]}
      />

      {/* 模型弹窗 */}
      <Modal
        title={editing ? '编辑 LTV 模型' : '新建 LTV 模型'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={560}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="modelName" label="模型名称" rules={[{ required: true, message: '请输入模型名称' }]}>
            <Input placeholder="如: 年度 LTV 模型" maxLength={200} />
          </Form.Item>
          <Form.Item name="modelCode" label="模型编码" rules={[{ required: true, message: '请输入模型编码' }]}>
            <Input placeholder="如: LTV_2026" maxLength={50} />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="modelType" label="模型类型" rules={[{ required: true }]}>
              <Select style={{ width: 160 }} options={Object.entries(modelTypeConfig).map(([v, cfg]) => ({ value: v, label: cfg.label }))} />
            </Form.Item>
            <Form.Item name="timeWindowDays" label="历史窗口 (天)">
              <InputNumber min={1} style={{ width: 130 }} />
            </Form.Item>
            <Form.Item name="predictionPeriodDays" label="预测期 (天)">
              <InputNumber min={1} style={{ width: 130 }} />
            </Form.Item>
          </Space>
          <Form.Item name="parameters" label="模型参数 JSON">
            <Input.TextArea rows={3} placeholder='{"alpha":0.5,"beta":0.3}' />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="模型说明" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 预测明细抽屉 */}
      <Drawer
        title={detailModel ? `LTV 预测明细 - ${detailModel.modelName}` : 'LTV 预测明细'}
        open={!!detailModel}
        onClose={() => setDetailModel(null)}
        width={720}
      >
        <Table
          rowKey="id"
          size="small"
          dataSource={predictions}
          loading={predLoading}
          pagination={false}
          locale={{ emptyText: <Empty description="暂无预测数据" /> }}
          columns={[
            { title: '客户', dataIndex: 'customerName', width: 120, render: (v?: string, r?: ScrmCustomerLtv) => v || r?.customerId || '-' },
            { title: '预测 LTV', dataIndex: 'predictedLtv', width: 110, render: (v?: number) => (v != null ? <Text strong>¥{v.toLocaleString()}</Text> : '-') },
            { title: '剩余价值', dataIndex: 'remainingLtv', width: 100, render: (v?: number) => (v != null ? `¥${v.toLocaleString()}` : '-') },
            { title: '预测生命周期', dataIndex: 'predictedLifespanMonths', width: 110, render: (v?: number) => (v != null ? `${v} 月` : '-') },
            {
              title: '流失风险',
              dataIndex: 'churnRisk',
              width: 100,
              render: (v?: number) => (v != null ? <Tag color={riskColor(v)}>{(v * 100).toFixed(0)}%</Tag> : '-'),
            },
            { title: '计算时间', dataIndex: 'calcTime', width: 150, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
          ]}
        />
        {predictions.length < predTotal && (
          <div style={{ textAlign: 'center', marginTop: 8 }}>
            <Button type="link" loading={predLoading} onClick={() => detailModel && openDetail(detailModel, predPage + 1)}>
              加载更早 ({predictions.length}/{predTotal})
            </Button>
          </div>
        )}
      </Drawer>
    </div>
  );
}