/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : RfmAnalysis.tsx
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
  ThunderboltOutlined,
  ProfileOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

const { Text } = Typography;

/** 配置状态映射 */
const statusConfig: Record<string, { color: string; label: string }> = {
  ACTIVE: { color: 'green', label: '启用' },
  INACTIVE: { color: 'default', label: '停用' },
};

/** 客户分群颜色映射 (RFM 八分群) */
const segmentColorConfig: Record<string, string> = {
  CHAMPIONS: 'gold',
  LOYAL: 'green',
  POTENTIAL_LOYAL: 'cyan',
  NEW: 'blue',
  PROMISING: 'geekblue',
  NEED_ATTENTION: 'orange',
  AT_RISK: 'volcano',
  HIBERNATING: 'default',
};

/** 根据分群编码返回 Tag 颜色 */
function segmentColor(segment?: string): string {
  if (!segment) return 'default';
  return segmentColorConfig[segment] || 'purple';
}

/** RFM 配置实体 */
interface ScrmRfmConfig {
  id: string;
  configName: string;
  configCode: string;
  analyzePeriodDays?: number;
  recencyWeight?: number;
  frequencyWeight?: number;
  monetaryWeight?: number;
  status?: string;
  description?: string;
  createTime?: string;
}

/** RFM 客户分群 */
interface ScrmRfmSegment {
  id: string;
  configId?: string;
  segmentCode: string;
  segmentName: string;
  customerCount?: number;
}

/** RFM 客户价值分析结果 */
interface ScrmRfmAnalysis {
  id: string;
  configId?: string;
  customerId?: string;
  customerName?: string;
  recencyScore?: number;
  frequencyScore?: number;
  monetaryScore?: number;
  rfmScore?: string;
  segment?: string;
  segmentName?: string;
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

/**
 * RFM 客户价值分析页
 * <p>
 * RFM 配置 CRUD + 触发计算 + 客户价值明细 (分群概览与客户列表)。
 * </p>
 *
 * @author Hsi Chu
 */
export default function RfmAnalysis() {
  const { message } = App.useApp();
  /** 配置列表 */
  const [configs, setConfigs] = useState<ScrmRfmConfig[]>([]);
  const [configsLoading, setConfigsLoading] = useState(false);
  const [configTotal, setConfigTotal] = useState(0);
  const [configPage, setConfigPage] = useState(0);
  const configPageSize = 10;
  /** 配置弹窗 */
  const [editing, setEditing] = useState<ScrmRfmConfig | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  /** 客户明细抽屉 */
  const [detailConfig, setDetailConfig] = useState<ScrmRfmConfig | null>(null);
  const [segments, setSegments] = useState<ScrmRfmSegment[]>([]);
  const [customers, setCustomers] = useState<ScrmRfmAnalysis[]>([]);
  const [customersLoading, setCustomersLoading] = useState(false);
  const [customersTotal, setCustomersTotal] = useState(0);
  const [customerPage, setCustomerPage] = useState(0);
  const [segmentFilter, setSegmentFilter] = useState<string | undefined>();
  const customerPageSize = 20;

  /** 加载配置列表 */
  const loadConfigs = useCallback(async (targetPage = configPage) => {
    setConfigsLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(configPageSize) });
      const data = await apiClient.get<Page<ScrmRfmConfig>>(`/scrm/rfm/configs/list?${params.toString()}`);
      setConfigs(data.content || []);
      setConfigTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setConfigsLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [configPage]);

  useEffect(() => {
    loadConfigs();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [configPage]);

  /** 打开新建弹窗 */
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ analyzePeriodDays: 90, recencyWeight: 1, frequencyWeight: 1, monetaryWeight: 1 });
    setOpen(true);
  };

  /** 打开编辑弹窗 */
  const openEdit = (record: ScrmRfmConfig) => {
    setEditing(record);
    form.setFieldsValue(record);
    setOpen(true);
  };

  /** 提交配置 */
  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await apiClient.put(`/scrm/rfm/configs/${editing.id}`, values);
        message.success('配置已更新');
      } else {
        await apiClient.post('/scrm/rfm/configs', values);
        message.success('配置已创建');
      }
      setOpen(false);
      setConfigPage(0);
      loadConfigs(0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setSaving(false);
    }
  };

  /** 触发 RFM 计算 */
  const handleCalculate = async (record: ScrmRfmConfig) => {
    try {
      await apiClient.post(`/scrm/rfm/calculate/${record.id}`, { configId: record.id });
      message.success('已触发计算');
      loadConfigs();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 打开客户价值明细抽屉 */
  const openDetail = useCallback(async (config: ScrmRfmConfig, targetPage = 0) => {
    setDetailConfig(config);
    setCustomersLoading(true);
    // 首页加载时刷新分群概览
    if (targetPage === 0) {
      try {
        const segs = await apiClient.get<ScrmRfmSegment[]>(`/scrm/rfm/configs/${config.id}/segments`);
        setSegments(Array.isArray(segs) ? segs : ((segs as unknown as Page<ScrmRfmSegment>)?.content || []));
      } catch {
        setSegments([]);
      }
    }
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(customerPageSize) });
      if (segmentFilter) params.set('segment', segmentFilter);
      const data = await apiClient.get<Page<ScrmRfmAnalysis>>(
        `/scrm/rfm/configs/${config.id}/customers/list?${params.toString()}`,
      );
      setCustomers(prev => (targetPage === 0 ? data.content || [] : [...prev, ...(data.content || [])]));
      setCustomersTotal(data.totalElements || 0);
      setCustomerPage(targetPage);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setCustomersLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [segmentFilter]);

  /** 表格列 */
  const columns: ColumnsType<ScrmRfmConfig> = useMemo(() => [
    { title: '配置名称', dataIndex: 'configName', render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
    { title: '配置编码', dataIndex: 'configCode', width: 140, render: (v?: string) => (v ? <Text code>{v}</Text> : '-') },
    { title: '分析周期', dataIndex: 'analyzePeriodDays', width: 100, render: (v?: number) => (v != null ? `${v} 天` : '-') },
    {
      title: '权重 (R/F/M)',
      key: 'weights',
      width: 140,
      render: (_, r) => `${r.recencyWeight ?? 0} / ${r.frequencyWeight ?? 0} / ${r.monetaryWeight ?? 0}`,
    },
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
      width: 180,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          <Button type="link" size="small" icon={<ThunderboltOutlined />} onClick={() => handleCalculate(r)}>
            计算
          </Button>
          <Button type="link" size="small" icon={<ProfileOutlined />} onClick={() => openDetail(r, 0)}>
            明细
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
    <div className="rfm-analysis-page">
      <Card
        title="RFM 客户价值分析"
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => loadConfigs()} />
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              新建配置
            </Button>
          </Space>
        }
      >
        <Table
          rowKey="id"
          columns={columns}
          dataSource={configs}
          loading={configsLoading}
          pagination={{
            current: configPage + 1,
            pageSize: configPageSize,
            total: configTotal,
            showTotal: t => `共 ${t} 条`,
            onChange: p => setConfigPage(p - 1),
          }}
          scroll={{ y: 'calc(100vh - 320px)' }}
        />
      </Card>

      {/* 配置弹窗 */}
      <Modal
        title={editing ? '编辑 RFM 配置' : '新建 RFM 配置'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={560}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="configName" label="配置名称" rules={[{ required: true, message: '请输入配置名称' }]}>
            <Input placeholder="如: 默认 RFM 模型" maxLength={200} />
          </Form.Item>
          <Form.Item name="configCode" label="配置编码" rules={[{ required: true, message: '请输入配置编码' }]}>
            <Input placeholder="如: DEFAULT_RFM" maxLength={50} />
          </Form.Item>
          <Form.Item name="analyzePeriodDays" label="分析周期 (天)">
            <InputNumber min={1} style={{ width: 160 }} />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="recencyWeight" label="R 权重"><InputNumber min={0} step={0.1} style={{ width: 100 }} /></Form.Item>
            <Form.Item name="frequencyWeight" label="F 权重"><InputNumber min={0} step={0.1} style={{ width: 100 }} /></Form.Item>
            <Form.Item name="monetaryWeight" label="M 权重"><InputNumber min={0} step={0.1} style={{ width: 100 }} /></Form.Item>
          </Space>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="配置说明" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 客户明细抽屉 */}
      <Drawer
        title={detailConfig ? `客户价值明细 - ${detailConfig.configName}` : '客户价值明细'}
        open={!!detailConfig}
        onClose={() => setDetailConfig(null)}
        width={720}
      >
        {/* 分群概览 */}
        {segments.length > 0 && (
          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', marginBottom: 16 }}>
            {segments.map(s => (
              <Card key={s.id} size="small" style={{ minWidth: 120 }}>
                <Statistic title={s.segmentName} value={s.customerCount ?? 0} />
              </Card>
            ))}
          </div>
        )}
        <Space style={{ marginBottom: 12 }}>
          <Select
            placeholder="按分群筛选"
            allowClear
            style={{ width: 180 }}
            options={segments.map(s => ({ value: s.segmentCode, label: s.segmentName }))}
            onChange={v => { setSegmentFilter(v); }}
          />
          <Button icon={<SearchOutlined />} onClick={() => detailConfig && openDetail(detailConfig, 0)}>查询</Button>
        </Space>
        <Table
          rowKey="id"
          size="small"
          dataSource={customers}
          loading={customersLoading}
          pagination={false}
          locale={{ emptyText: <Empty description="暂无客户数据, 请先触发计算" /> }}
          columns={[
            { title: '客户', dataIndex: 'customerName', width: 120, render: (v?: string, r?: ScrmRfmAnalysis) => v || r?.customerId || '-' },
            { title: 'R', dataIndex: 'recencyScore', width: 60, render: (v?: number) => v ?? '-' },
            { title: 'F', dataIndex: 'frequencyScore', width: 60, render: (v?: number) => v ?? '-' },
            { title: 'M', dataIndex: 'monetaryScore', width: 60, render: (v?: number) => v ?? '-' },
            { title: 'RFM', dataIndex: 'rfmScore', width: 80, render: (v?: string) => <Text code>{v || '-'}</Text> },
            {
              title: '分群',
              dataIndex: 'segment',
              width: 130,
              render: (v?: string, r?: ScrmRfmAnalysis) => <Tag color={segmentColor(v)}>{r?.segmentName || v || '-'}</Tag>,
            },
            { title: '计算时间', dataIndex: 'calcTime', width: 150, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
          ]}
        />
        {customers.length < customersTotal && (
          <div style={{ textAlign: 'center', marginTop: 8 }}>
            <Button type="link" loading={customersLoading} onClick={() => detailConfig && openDetail(detailConfig, customerPage + 1)}>
              加载更早 ({customers.length}/{customersTotal})
            </Button>
          </div>
        )}
      </Drawer>
    </div>
  );
}