/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Coupons.tsx
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
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Statistic,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import {
  PlusOutlined,
  ReloadOutlined,
  SendOutlined,
  BarChartOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

const { Text } = Typography;

/** 券类型映射 */
const couponTypeConfig: Record<string, { color: string; label: string }> = {
  CASH: { color: 'red', label: '现金券' },
  DISCOUNT: { color: 'blue', label: '折扣券' },
  FREE_SHIPPING: { color: 'cyan', label: '运费券' },
  EXCHANGE: { color: 'purple', label: '兑换券' },
};

/** 券模板状态映射 */
const templateStatusConfig: Record<string, { color: string; label: string }> = {
  ACTIVE: { color: 'green', label: '启用' },
  INACTIVE: { color: 'default', label: '停用' },
};

/** 券实体状态映射 */
const couponStatusConfig: Record<string, { color: string; label: string }> = {
  UNUSED: { color: 'blue', label: '未使用' },
  USED: { color: 'default', label: '已使用' },
  EXPIRED: { color: 'orange', label: '已过期' },
  FROZEN: { color: 'purple', label: '已冻结' },
};

/** 券模板实体 */
interface ScrmCouponTemplate {
  id: string;
  templateName: string;
  couponType: string;
  faceValue?: number;
  thresholdAmount?: number;
  validType?: string; // FIXED / RELATIVE
  validDays?: number;
  totalQuantity?: number;
  issuedQuantity?: number;
  usedQuantity?: number;
  status: string;
  createTime?: string;
}

/** 券实体 */
interface ScrmCoupon {
  id: string;
  couponCode: string;
  templateId?: string;
  customerId?: string;
  customerName?: string;
  claimSource?: string;
  status: string;
  expiresAt?: string;
  createTime?: string;
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
 * 优惠券管理页
 * <p>
 * 券模板 (CRUD) + 发放记录 (券列表) + 手动发放 + 模板统计抽屉, 覆盖优惠券全生命周期。
 * </p>
 *
 * @author Hsi Chu
 */
export default function Coupons() {
  const { message } = App.useApp();
  /** 模板 */
  const [templates, setTemplates] = useState<ScrmCouponTemplate[]>([]);
  const [templatesLoading, setTemplatesLoading] = useState(false);
  const [templatePage, setTemplatePage] = useState(0);
  const templatePageSize = 10;
  const [templateTotal, setTemplateTotal] = useState(0);
  /** 券 */
  const [coupons, setCoupons] = useState<ScrmCoupon[]>([]);
  const [couponsLoading, setCouponsLoading] = useState(false);
  const [couponPage, setCouponPage] = useState(0);
  const couponPageSize = 10;
  const [couponTotal, setCouponTotal] = useState(0);
  /** 模板弹窗 */
  const [editing, setEditing] = useState<ScrmCouponTemplate | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  /** 发放弹窗 */
  const [issueOpen, setIssueOpen] = useState(false);
  const [issuing, setIssuing] = useState(false);
  const [issueForm] = Form.useForm();
  /** 统计抽屉 */
  const [statsTemplate, setStatsTemplate] = useState<ScrmCouponTemplate | null>(null);
  const [stats, setStats] = useState<Record<string, number> | null>(null);

  /** 加载券模板 */
  const loadTemplates = useCallback(async (targetPage = templatePage) => {
    setTemplatesLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(templatePageSize) });
      const data = await apiClient.get<Page<ScrmCouponTemplate>>(`/scrm/coupons/templates/list?${params.toString()}`);
      setTemplates(data.content || []);
      setTemplateTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setTemplatesLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [templatePage]);

  /** 加载券列表 */
  const loadCoupons = useCallback(async (targetPage = couponPage) => {
    setCouponsLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(couponPageSize) });
      const data = await apiClient.get<Page<ScrmCoupon>>(`/scrm/coupons/coupons/list?${params.toString()}`);
      setCoupons(data.content || []);
      setCouponTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setCouponsLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [couponPage]);

  useEffect(() => {
    loadTemplates();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [templatePage]);

  useEffect(() => {
    loadCoupons();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [couponPage]);

  /** 打开新建 */
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ couponType: 'CASH', validType: 'RELATIVE' });
    setOpen(true);
  };

  /** 提交模板 */
  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await apiClient.put(`/scrm/coupons/templates/${editing.id}`, values);
        message.success('券模板已更新');
      } else {
        await apiClient.post('/scrm/coupons/templates', values);
        message.success('券模板已创建');
      }
      setOpen(false);
      setTemplatePage(0);
      loadTemplates(0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setSaving(false);
    }
  };

  /** 打开发放弹窗 */
  const openIssue = (record: ScrmCouponTemplate) => {
    issueForm.resetFields();
    issueForm.setFieldsValue({ templateId: record.id });
    setIssueOpen(true);
  };

  /** 提交发放 */
  const handleIssue = async () => {
    const values = await issueForm.validateFields();
    setIssuing(true);
    try {
      await apiClient.post('/scrm/coupons/issue', values);
      message.success('优惠券已发放');
      setIssueOpen(false);
      loadCoupons();
    } catch {
      // 拦截器已弹出错误
    } finally {
      setIssuing(false);
    }
  };

  /** 打开统计抽屉 */
  const openStats = async (record: ScrmCouponTemplate) => {
    setStatsTemplate(record);
    setStats(null);
    try {
      const data = await apiClient.get<Record<string, number>>(`/scrm/coupons/templates/${record.id}/stats`);
      setStats(data || {});
    } catch {
      setStats(null);
    }
  };

  /** 模板列 */
  const templateColumns: ColumnsType<ScrmCouponTemplate> = useMemo(() => [
    { title: '模板名称', dataIndex: 'templateName', render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
    {
      title: '券类型',
      dataIndex: 'couponType',
      width: 100,
      render: (v: string) => {
        const cfg = couponTypeConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '面值', dataIndex: 'faceValue', width: 90, render: (v?: number) => (v != null ? `¥${v}` : '-') },
    { title: '使用门槛', dataIndex: 'thresholdAmount', width: 100, render: (v?: number) => (v != null ? `¥${v}` : '无') },
    {
      title: '有效期',
      dataIndex: 'validType',
      width: 110,
      render: (v?: string, r?: ScrmCouponTemplate) =>
        v === 'RELATIVE' ? `领取后 ${r?.validDays ?? '-'} 天` : v === 'FIXED' ? '固定日期' : v || '-',
    },
    { title: '已发放', dataIndex: 'issuedQuantity', width: 90, render: (v?: number) => v ?? 0 },
    { title: '已使用', dataIndex: 'usedQuantity', width: 90, render: (v?: number) => v ?? 0 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v?: string) => {
        const cfg = templateStatusConfig[v || ''] || { color: 'default', label: v || '-' };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '操作',
      key: 'actions',
      width: 130,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          <Button type="link" size="small" icon={<SendOutlined />} onClick={() => openIssue(r)}>
            发放
          </Button>
          <Button type="link" size="small" icon={<BarChartOutlined />} onClick={() => openStats(r)}>
            统计
          </Button>
        </Space>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  /** 券列 */
  const couponColumns: ColumnsType<ScrmCoupon> = useMemo(() => [
    { title: '券码', dataIndex: 'couponCode', width: 130, render: (v: string) => <Text code>{v}</Text> },
    { title: '模板 ID', dataIndex: 'templateId', width: 100, render: (v?: string) => v || '-' },
    { title: '客户', dataIndex: 'customerName', width: 110, render: (v?: string) => v || '-' },
    { title: '领取来源', dataIndex: 'claimSource', width: 100, render: (v?: string) => v || '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v?: string) => {
        const cfg = couponStatusConfig[v || ''] || { color: 'default', label: v || '-' };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '过期时间', dataIndex: 'expiresAt', width: 140, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD') : '永久') },
    { title: '创建时间', dataIndex: 'createTime', width: 140, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
  ], []);

  return (
    <div className="coupons-page">
      <Tabs
        defaultActiveKey="templates"
        items={[
          {
            key: 'templates',
            label: '券模板',
            children: (
              <Card
                title="券模板"
                extra={
                  <Space>
                    <Button icon={<ReloadOutlined />} onClick={() => loadTemplates()} />
                    <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                      新建模板
                    </Button>
                  </Space>
                }
              >
                <Table
                  rowKey="id"
                  columns={templateColumns}
                  dataSource={templates}
                  loading={templatesLoading}
                  pagination={{
                    current: templatePage + 1,
                    pageSize: templatePageSize,
                    total: templateTotal,
                    showTotal: t => `共 ${t} 条`,
                    onChange: p => setTemplatePage(p - 1),
                  }}
                  scroll={{ y: 'calc(100vh - 320px)' }}
                />
              </Card>
            ),
          },
          {
            key: 'coupons',
            label: '券列表',
            children: (
              <Card
                title="发放记录"
                extra={<Button icon={<ReloadOutlined />} onClick={() => loadCoupons()} />}
              >
                <Table
                  rowKey="id"
                  columns={couponColumns}
                  dataSource={coupons}
                  loading={couponsLoading}
                  pagination={{
                    current: couponPage + 1,
                    pageSize: couponPageSize,
                    total: couponTotal,
                    showTotal: t => `共 ${t} 条`,
                    onChange: p => setCouponPage(p - 1),
                  }}
                />
              </Card>
            ),
          },
        ]}
      />

      {/* 模板弹窗 */}
      <Modal
        title={editing ? '编辑券模板' : '新建券模板'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={560}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="templateName" label="模板名称" rules={[{ required: true, message: '请输入模板名称' }]}>
            <Input placeholder="如: 618 满 200 减 30" maxLength={200} />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="couponType" label="券类型" rules={[{ required: true }]}>
              <Select style={{ width: 140 }} options={Object.entries(couponTypeConfig).map(([v, c]) => ({ value: v, label: c.label }))} />
            </Form.Item>
            <Form.Item name="faceValue" label="面值" rules={[{ required: true }]}>
              <InputNumber min={0} style={{ width: 110 }} />
            </Form.Item>
            <Form.Item name="thresholdAmount" label="使用门槛">
              <InputNumber min={0} style={{ width: 110 }} />
            </Form.Item>
          </Space>
          <Space size={16} wrap>
            <Form.Item name="validType" label="有效期类型" rules={[{ required: true }]}>
              <Select
                style={{ width: 140 }}
                options={[
                  { value: 'FIXED', label: '固定日期' },
                  { value: 'RELATIVE', label: '领取后 N 天' },
                ]}
              />
            </Form.Item>
            <Form.Item name="validDays" label="有效天数 (相对)">
              <InputNumber min={1} style={{ width: 110 }} />
            </Form.Item>
            <Form.Item name="totalQuantity" label="发放总量" rules={[{ required: true }]}>
              <InputNumber min={1} style={{ width: 110 }} />
            </Form.Item>
          </Space>
        </Form>
      </Modal>

      {/* 发放弹窗 */}
      <Modal
        title="发放优惠券"
        open={issueOpen}
        onCancel={() => setIssueOpen(false)}
        onOk={handleIssue}
        confirmLoading={issuing}
      >
        <Form form={issueForm} layout="vertical">
          <Form.Item name="templateId" label="模板 ID" rules={[{ required: true, message: '请输入模板 ID' }]}>
            <Input placeholder="券模板 ID" disabled={!!issueForm.getFieldValue('templateId')} />
          </Form.Item>
          <Form.Item name="quantity" label="发放数量" rules={[{ required: true, message: '请输入发放数量' }]}>
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 统计抽屉 */}
      <Drawer
        title={statsTemplate ? `模板统计 - ${statsTemplate.templateName}` : '模板统计'}
        open={!!statsTemplate}
        onClose={() => setStatsTemplate(null)}
        width={420}
      >
        {stats ? (
          <div style={{ display: 'flex', gap: 24, flexWrap: 'wrap' }}>
            {Object.entries(stats).map(([k, v]) => (
              <Statistic key={k} title={k} value={v} />
            ))}
          </div>
        ) : (
          <Text type="secondary">加载中...</Text>
        )}
      </Drawer>
    </div>
  );
}