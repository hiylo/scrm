/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Opportunities.tsx
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
  Spin,
  Statistic,
  Table,
  Tag,
} from 'antd';
import {
  PlusOutlined,
  ReloadOutlined,
  EditOutlined,
  HistoryOutlined,
  RiseOutlined,
  FundOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

/** 商机状态映射 */
const statusConfig: Record<string, { color: string; label: string }> = {
  OPEN: { color: 'processing', label: '进行中' },
  WON: { color: 'green', label: '赢单' },
  LOST: { color: 'red', label: '输单' },
  PAUSED: { color: 'orange', label: '已暂停' },
};

/** 商机实体 */
interface ScrmOpportunity {
  id: string;
  opportunityName: string;
  customerId?: string;
  customerName?: string;
  funnelId?: string;
  currentStageId?: string;
  currentStageName?: string;
  amount?: number;
  probability?: number;
  status?: string;
  source?: string;
  ownerUserId?: string;
  competitor?: string;
  expectedCloseDate?: string;
  note?: string;
  createTime?: string;
}

/** 漏斗阶段 */
interface ScrmFunnelStage {
  id: string;
  funnelId?: string;
  stageName: string;
  stageOrder?: number;
}

/** 阶段变更历史 */
interface ScrmStageHistory {
  id: string;
  fromStageName?: string;
  toStageName?: string;
  changedBy?: string;
  changedAt?: string;
}

/** 销售预测结果 */
interface ScrmForecast {
  summary?: Record<string, number>;
  content?: ScrmOpportunity[];
  totalElements?: number;
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
 * 商机管理页面
 * 列表查询 / 新建/编辑 / 阶段推进 / 阶段历史抽屉 / 销售预测抽屉。
 *
 * @author Hsi Chu
 */
export default function Opportunities() {
  const { message } = App.useApp();
  /** 列表 */
  const [list, setList] = useState<ScrmOpportunity[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const pageSize = 10;
  const [total, setTotal] = useState(0);
  /** 创建/编辑弹窗 */
  const [editing, setEditing] = useState<ScrmOpportunity | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  /** 阶段推进弹窗 */
  const [stageTarget, setStageTarget] = useState<ScrmOpportunity | null>(null);
  const [stageSaving, setStageSaving] = useState(false);
  const [stageForm] = Form.useForm();
  const [stages, setStages] = useState<ScrmFunnelStage[]>([]);
  /** 历史抽屉 */
  const [historyOpp, setHistoryOpp] = useState<ScrmOpportunity | null>(null);
  const [history, setHistory] = useState<ScrmStageHistory[]>([]);
  /** 预测抽屉 */
  const [forecastOpen, setForecastOpen] = useState(false);
  const [forecastLoading, setForecastLoading] = useState(false);
  const [forecast, setForecast] = useState<ScrmForecast | null>(null);

  /** 加载商机列表 */
  const loadList = useCallback(async (targetPage = page) => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(pageSize) });
      const data = await apiClient.get<Page<ScrmOpportunity>>(`/scrm/opportunities?${params.toString()}`);
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
    form.setFieldsValue({ probability: 50 });
    setOpen(true);
  };

  /** 打开编辑弹窗 */
  const openEdit = (record: ScrmOpportunity) => {
    setEditing(record);
    form.setFieldsValue(record);
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
        await apiClient.put(`/scrm/opportunities/${editing.id}`, values);
        message.success('商机已更新');
      } else {
        await apiClient.post('/scrm/opportunities', values);
        message.success('商机已创建');
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

  /** 打开阶段推进弹窗 (先加载漏斗阶段) */
  const openStageChange = async (record: ScrmOpportunity) => {
    stageForm.resetFields();
    setStages([]);
    setStageTarget(record);
    try {
      const rows = await apiClient.get<ScrmFunnelStage[]>(`/scrm/opportunities/${record.id}/stages`);
      setStages(rows || []);
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 提交阶段推进 */
  const handleStageChange = async () => {
    if (!stageTarget) return;
    let values: Record<string, unknown>;
    try {
      values = await stageForm.validateFields();
    } catch {
      return;
    }
    setStageSaving(true);
    try {
      await apiClient.post(`/scrm/opportunities/${stageTarget.id}/change-stage`, values);
      message.success('阶段已推进');
      setStageTarget(null);
      loadList();
    } catch {
      // 拦截器已弹出错误
    } finally {
      setStageSaving(false);
    }
  };

  /** 查看阶段历史 */
  const openHistory = async (record: ScrmOpportunity) => {
    setHistoryOpp(record);
    setHistory([]);
    try {
      const rows = await apiClient.get<Page<ScrmStageHistory> | ScrmStageHistory[]>(`/scrm/opportunities/${record.id}/stage-history`);
      setHistory(Array.isArray(rows) ? rows : rows?.content || []);
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 打开销售预测抽屉 */
  const openForecast = async () => {
    setForecastOpen(true);
    setForecastLoading(true);
    try {
      const data = await apiClient.get<ScrmForecast>('/scrm/opportunities/forecast');
      setForecast(data);
    } catch {
      // 拦截器已弹出错误
      setForecast(null);
    } finally {
      setForecastLoading(false);
    }
  };

  /** 表格列 */
  const columns: ColumnsType<ScrmOpportunity> = useMemo(() => [
    { title: '商机名称', dataIndex: 'opportunityName', render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
    { title: '客户', dataIndex: 'customerName', width: 110, render: (v?: string, r?: ScrmOpportunity) => v || r?.customerId || '-' },
    { title: '当前阶段', dataIndex: 'currentStageName', width: 120, render: (v?: string) => (v ? <Tag color="blue">{v}</Tag> : '-') },
    { title: '商机金额', dataIndex: 'amount', width: 130, render: (v?: number) => (v != null ? `¥${v.toLocaleString()}` : '-') },
    { title: '成交概率', dataIndex: 'probability', width: 100, render: (v?: number) => (v != null ? `${v}%` : '-') },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v?: string) => {
        const cfg = statusConfig[v || ''] || { color: 'default', label: v || '-' };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '负责人', dataIndex: 'ownerUserId', width: 100, render: (v?: string) => v || '-' },
    { title: '预计成交', dataIndex: 'expectedCloseDate', width: 120, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD') : '-') },
    { title: '创建时间', dataIndex: 'createTime', width: 140, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
    {
      title: '操作',
      key: 'actions',
      width: 190,
      fixed: 'right',
      render: (_, r) => (
        <Space size={0}>
          {r.status === 'OPEN' && (
            <Button type="link" size="small" icon={<RiseOutlined />} onClick={() => openStageChange(r)}>
              推进
            </Button>
          )}
          <Button type="link" size="small" icon={<HistoryOutlined />} onClick={() => openHistory(r)}>
            历史
          </Button>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(r)}>
            编辑
          </Button>
        </Space>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  return (
    <div className="opportunities-page">
      <Card
        title="商机管理"
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => loadList()} />
            <Button icon={<FundOutlined />} onClick={openForecast}>
              销售预测
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              新建商机
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

      {/* 创建/编辑弹窗 */}
      <Modal
        title={editing ? '编辑商机' : '新建商机'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={640}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="opportunityName" label="商机名称" rules={[{ required: true, message: '请输入商机名称' }]}>
            <Input placeholder="如: 华东区年度采购" maxLength={200} />
          </Form.Item>
          <Form.Item name="customerId" label="客户 ID" rules={[{ required: true, message: '请输入客户 ID' }]}>
            <Input placeholder="客户 ID" />
          </Form.Item>
          <Form.Item name="funnelId" label="漏斗 ID" rules={[{ required: true, message: '请输入漏斗 ID' }]}>
            <Input placeholder="漏斗 ID" />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="amount" label="商机金额 (¥)">
              <InputNumber min={0} style={{ width: 160 }} />
            </Form.Item>
            <Form.Item name="probability" label="成交概率 (%)">
              <InputNumber min={0} max={100} style={{ width: 120 }} />
            </Form.Item>
          </Space>
          <Space size={16} wrap>
            <Form.Item name="expectedCloseDate" label="预计成交日期">
              <Input placeholder="如 2026-12-31" style={{ width: 180 }} />
            </Form.Item>
            <Form.Item name="source" label="来源">
              <Input placeholder="如: 渠道活码" style={{ width: 180 }} />
            </Form.Item>
          </Space>
          <Form.Item name="ownerUserId" label="负责人用户 ID" rules={[{ required: true, message: '请输入负责人' }]}>
            <Input placeholder="负责人用户 ID" />
          </Form.Item>
          <Form.Item name="competitor" label="竞争对手">
            <Input placeholder="竞争对手" />
          </Form.Item>
          <Form.Item name="note" label="备注">
            <Input.TextArea rows={2} placeholder="备注" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 阶段推进弹窗 */}
      <Modal
        title={stageTarget ? `阶段推进 - ${stageTarget.opportunityName}` : '阶段推进'}
        open={!!stageTarget}
        onCancel={() => setStageTarget(null)}
        onOk={handleStageChange}
        confirmLoading={stageSaving}
      >
        <Form form={stageForm} layout="vertical">
          <Form.Item name="toStageId" label="目标阶段" rules={[{ required: true, message: '请选择目标阶段' }]}>
            <Select placeholder="选择目标阶段" options={stages.map(s => ({ value: s.id, label: s.stageName }))} />
          </Form.Item>
          <Form.Item name="note" label="推进备注">
            <Input.TextArea rows={2} placeholder="推进说明" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 历史抽屉 */}
      <Drawer
        title={historyOpp ? `阶段历史 - ${historyOpp.opportunityName}` : '阶段历史'}
        open={!!historyOpp}
        onClose={() => setHistoryOpp(null)}
        width={520}
      >
        <Table
          rowKey="id"
          size="small"
          dataSource={history}
          pagination={false}
          locale={{ emptyText: <Empty description="暂无阶段历史" /> }}
          columns={[
            { title: '从', dataIndex: 'fromStageName', width: 120, render: (v?: string) => v || '-' },
            { title: '到', dataIndex: 'toStageName', width: 120, render: (v?: string) => v || '-' },
            { title: '操作人', dataIndex: 'changedBy', width: 100, render: (v?: string) => v || '-' },
            { title: '时间', dataIndex: 'changedAt', width: 150, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
          ]}
        />
      </Drawer>

      {/* 预测抽屉 */}
      <Drawer
        title="销售预测"
        open={forecastOpen}
        onClose={() => setForecastOpen(false)}
        width={640}
      >
        {forecastLoading ? (
          <div style={{ textAlign: 'center', padding: 48 }}><Spin /></div>
        ) : forecast?.summary ? (
          <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', marginBottom: 16 }}>
            {Object.entries(forecast.summary).map(([k, v]) => (
              <Statistic key={k} title={k} value={v} />
            ))}
          </div>
        ) : null}
        <Table
          rowKey="id"
          size="small"
          dataSource={forecast?.content || []}
          pagination={false}
          locale={{ emptyText: <Empty description="暂无预测数据" /> }}
          columns={[
            { title: '商机', dataIndex: 'opportunityName', ellipsis: true },
            { title: '金额', dataIndex: 'amount', width: 100, render: (v?: number) => (v != null ? `¥${v.toLocaleString()}` : '-') },
            { title: '概率', dataIndex: 'probability', width: 80, render: (v?: number) => `${v ?? 0}%` },
            { title: '加权金额', dataIndex: 'weightedAmount', width: 110, render: (v?: number) => (v != null ? `¥${v.toLocaleString()}` : '-') },
          ]}
        />
      </Drawer>
    </div>
  );
}