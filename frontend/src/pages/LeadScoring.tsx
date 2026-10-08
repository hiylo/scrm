/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : LeadScoring.tsx
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
  StarOutlined,
  CalculatorOutlined,
  CloudUploadOutlined,
  CloudDownloadOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

const { Text } = Typography;

/** 打分模型实体 */
interface ScrmLeadScoringModel {
  id: string;
  modelName: string;
  modelCode: string;
  description?: string;
  modelType: string; // RULE / ML / HYBRID
  dimensions: string;
  totalMaxScore: number;
  gradeThresholds?: string;
  isPublished?: boolean;
  isDefault?: boolean;
  createTime?: string;
}

/** 线索分数 */
interface ScrmLeadScore {
  id: string;
  customerId: string;
  customerName?: string;
  modelId: string;
  totalScore: number;
  maxScore?: number;
  scorePercent?: number;
  grade?: string;
  qualified?: boolean;
  calcTime?: string;
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
  RULE: { color: 'blue', label: '规则' },
  ML: { color: 'purple', label: '机器学习' },
  HYBRID: { color: 'cyan', label: '混合' },
};

/** 等级颜色 */
const gradeColor = (grade?: string): string => {
  const map: Record<string, string> = { A: 'green', B: 'cyan', C: 'orange', D: 'red' };
  return map[grade || ''] || 'default';
};

/**
 * 线索打分页
 * <p>
 * 销售线索质量评估: 打分模型 (规则/机器学习/混合) + 打分维度 (权重/满分/逻辑) +
 * 触发计算 (全量) + 分数明细 (总分/百分比/等级/是否合格)。
 * </p>
 *
 * @author Hsi Chu
 */
export default function LeadScoring() {
  const { message } = App.useApp();
  /** 模型 */
  const [models, setModels] = useState<ScrmLeadScoringModel[]>([]);
  const [modelsLoading, setModelsLoading] = useState(false);
  const [modelTotal, setModelTotal] = useState(0);
  const [modelPage, setModelPage] = useState(0);
  const modelPageSize = 10;
  /** 分数 */
  const [scores, setScores] = useState<ScrmLeadScore[]>([]);
  const [scoresLoading, setScoresLoading] = useState(false);
  const [scoreTotal, setScoreTotal] = useState(0);
  const [scorePage, setScorePage] = useState(0);
  const scorePageSize = 10;
  /** 模型弹窗 */
  const [editing, setEditing] = useState<ScrmLeadScoringModel | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  /** 分数明细抽屉 */
  const [detailModel, setDetailModel] = useState<ScrmLeadScoringModel | null>(null);
  const [modelScores, setModelScores] = useState<ScrmLeadScore[]>([]);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailTotal, setDetailTotal] = useState(0);
  const [detailPage, setDetailPage] = useState(0);

  /** 加载模型 */
  const loadModels = useCallback(async (targetPage = modelPage) => {
    setModelsLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(modelPageSize) });
      const data = await apiClient.get<Page<ScrmLeadScoringModel>>(`/scrm/lead-scoring/models/list?${params.toString()}`);
      setModels(data.content || []);
      setModelTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setModelsLoading(false);
    }
  }, [modelPage]);

  useEffect(() => {
    loadModels();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [modelPage]);

  /** 加载分数 */
  const loadScores = useCallback(async (targetPage = scorePage) => {
    setScoresLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(scorePageSize) });
      const data = await apiClient.get<Page<ScrmLeadScore>>(`/scrm/lead-scoring/scores/list?${params.toString()}`);
      setScores(data.content || []);
      setScoreTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setScoresLoading(false);
    }
  }, [scorePage]);

  useEffect(() => {
    loadScores();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scorePage]);

  /** 打开创建 */
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ modelType: 'RULE', totalMaxScore: 100, dimensions: '[]' });
    setOpen(true);
  };

  /** 打开编辑 */
  const openEdit = (record: ScrmLeadScoringModel) => {
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
        await apiClient.put(`/scrm/lead-scoring/models/${editing.id}`, values);
        message.success('模型已更新');
      } else {
        await apiClient.post('/scrm/lead-scoring/models', values);
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

  /** 删除 */
  const handleDelete = async (record: ScrmLeadScoringModel) => {
    try {
      await apiClient.delete(`/scrm/lead-scoring/models/${record.id}`);
      message.success('模型已删除');
      loadModels();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 发布/下线 */
  const handleTogglePublish = async (record: ScrmLeadScoringModel, toPublish: boolean) => {
    try {
      await apiClient.post(`/scrm/lead-scoring/models/${record.id}/${toPublish ? 'publish' : 'unpublish'}`);
      message.success(toPublish ? '模型已发布' : '模型已下线');
      loadModels();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 设为默认 */
  const handleSetDefault = async (record: ScrmLeadScoringModel) => {
    try {
      await apiClient.post(`/scrm/lead-scoring/models/${record.id}/default`);
      message.success('已设为默认模型');
      loadModels();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 触发全量计算 */
  const handleCalculateAll = async (record: ScrmLeadScoringModel) => {
    try {
      await apiClient.post(`/scrm/lead-scoring/scores/calculate-all/${record.id}`, {});
      message.success('全量打分已提交');
      loadScores();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 打开分数明细 */
  const openDetail = async (record: ScrmLeadScoringModel, targetPage = 0) => {
    setDetailModel(record);
    setDetailLoading(true);
    if (targetPage === 0) setModelScores([]);
    setDetailPage(targetPage);
    try {
      const params = new URLSearchParams({ modelId: record.id, page: String(targetPage), size: '20' });
      const data = await apiClient.get<Page<ScrmLeadScore>>(`/scrm/lead-scoring/scores/list?${params.toString()}`);
      setModelScores(prev => targetPage === 0 ? (data.content || []) : [...prev, ...(data.content || [])]);
      setDetailTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setDetailLoading(false);
    }
  };

  /** 模型表格列 */
  const modelColumns: ColumnsType<ScrmLeadScoringModel> = useMemo(() => [
    {
      title: '模型名称',
      dataIndex: 'modelName',
      render: (v: string, r) => (
        <Space>
          <span style={{ fontWeight: 600 }}>{v}</span>
          {r.isDefault && <Tag color="gold">默认</Tag>}
        </Space>
      ),
    },
    {
      title: '类型',
      dataIndex: 'modelType',
      width: 100,
      render: (v: string) => {
        const cfg = modelTypeConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '满分', dataIndex: 'totalMaxScore', width: 80, render: (v?: number) => v ?? '-' },
    {
      title: '状态',
      dataIndex: 'isPublished',
      width: 90,
      render: (v?: boolean) => (v ? <Tag color="green">已发布</Tag> : <Tag>草稿</Tag>),
    },
    {
      title: '操作',
      key: 'actions',
      width: 280,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          <Button type="link" size="small" icon={<CalculatorOutlined />} onClick={() => handleCalculateAll(r)}>
            计算
          </Button>
          <Button type="link" size="small" icon={<StarOutlined />} onClick={() => openDetail(r)}>
            明细
          </Button>
          {!r.isDefault && (
            <Button type="link" size="small" icon={<StarOutlined />} onClick={() => handleSetDefault(r)}>
              默认
            </Button>
          )}
          {r.isPublished ? (
            <Button type="link" size="small" icon={<CloudDownloadOutlined />} onClick={() => handleTogglePublish(r, false)}>
              下线
            </Button>
          ) : (
            <Button type="link" size="small" icon={<CloudUploadOutlined />} onClick={() => handleTogglePublish(r, true)}>
              发布
            </Button>
          )}
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(r)}>
            编辑
          </Button>
          <Popconfirm title="确定删除该模型?" onConfirm={() => handleDelete(r)}>
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  /** 分数表格列 */
  const scoreColumns: ColumnsType<ScrmLeadScore> = [
    { title: '客户', dataIndex: 'customerName', width: 120, render: (v?: string, r?: ScrmLeadScore) => v || r?.customerId || '-' },
    { title: '总分', dataIndex: 'totalScore', width: 80, render: (v?: number) => <Text strong>{v ?? '-'}</Text> },
    { title: '百分比', dataIndex: 'scorePercent', width: 90, render: (v?: number) => (v != null ? `${v.toFixed(1)}%` : '-') },
    { title: '等级', dataIndex: 'grade', width: 80, render: (v?: string) => (v ? <Tag color={gradeColor(v)}>{v}</Tag> : '-') },
    { title: '合格', dataIndex: 'qualified', width: 80, render: (v?: boolean) => (v ? <Tag color="green">合格</Tag> : <Tag color="orange">待跟进</Tag>) },
    { title: '计算时间', dataIndex: 'calcTime', width: 150, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
  ];

  return (
    <div className="lead-scoring-page">
      <Tabs
        items={[
          {
            key: 'models',
            label: '打分模型',
            children: (
              <Card
                title="线索打分模型"
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
            key: 'scores',
            label: '线索分数',
            children: (
              <Card
                title="线索打分结果"
                extra={<Button icon={<ReloadOutlined />} onClick={() => loadScores()} />}
              >
                <Table
                  rowKey="id"
                  columns={scoreColumns}
                  dataSource={scores}
                  loading={scoresLoading}
                  pagination={{
                    current: scorePage + 1,
                    pageSize: scorePageSize,
                    total: scoreTotal,
                    showTotal: t => `共 ${t} 条`,
                    onChange: p => setScorePage(p - 1),
                  }}
                  locale={{ emptyText: <Empty description="暂无打分结果" /> }}
                />
              </Card>
            ),
          },
        ]}
      />

      {/* 模型弹窗 */}
      <Modal
        title={editing ? '编辑打分模型' : '新建打分模型'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={560}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="modelName" label="模型名称" rules={[{ required: true, message: '请输入模型名称' }]}>
            <Input placeholder="如: 标准线索打分模型" maxLength={200} />
          </Form.Item>
          <Form.Item name="modelCode" label="模型编码" rules={[{ required: true, message: '请输入模型编码' }]}>
            <Input placeholder="如: LEAD_STD" maxLength={50} />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="modelType" label="模型类型" rules={[{ required: true }]}>
              <Select style={{ width: 160 }} options={Object.entries(modelTypeConfig).map(([v, c]) => ({ value: v, label: c.label }))} />
            </Form.Item>
            <Form.Item name="totalMaxScore" label="满分">
              <InputNumber min={1} style={{ width: 110 }} />
            </Form.Item>
          </Space>
          <Form.Item name="dimensions" label="维度 JSON" rules={[{ required: true, message: '请输入维度 JSON' }]}>
            <Input.TextArea rows={3} placeholder='[{"dimensionCode":"BEHAVIOR","weight":40}]' />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="模型说明" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 分数明细抽屉 */}
      <Drawer
        title={detailModel ? `线索分数 - ${detailModel.modelName}` : '线索分数'}
        open={!!detailModel}
        onClose={() => setDetailModel(null)}
        width={680}
      >
        <Table
          rowKey="id"
          size="small"
          dataSource={modelScores}
          loading={detailLoading}
          pagination={false}
          locale={{ emptyText: <Empty description="暂无打分结果, 请先触发计算" /> }}
          columns={scoreColumns}
        />
        {modelScores.length < detailTotal && (
          <div style={{ textAlign: 'center', marginTop: 8 }}>
            <Button type="link" loading={detailLoading} onClick={() => detailModel && openDetail(detailModel, detailPage + 1)}>
              加载更早 ({modelScores.length}/{detailTotal})
            </Button>
          </div>
        )}
      </Drawer>
    </div>
  );
}