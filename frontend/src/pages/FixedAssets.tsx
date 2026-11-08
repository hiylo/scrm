/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : FixedAssets.tsx
 * Date : 2026/11/09 10:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  App,
  Button,
  Card,
  Col,
  DatePicker,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PlusOutlined, ReloadOutlined, ThunderboltOutlined } from '@ant-design/icons';
import dayjs, { type Dayjs } from 'dayjs';
import { apiClient } from '../api/client';
import { listCompanies } from '../api/finance';

/** 资产类别 */
type AssetCategory = 'MACHINERY' | 'ELECTRONIC' | 'FURNITURE' | 'VEHICLE' | 'OTHER';

/** 下拉选项 */
interface Segment {
  value: string;
  label: string;
}

/** 固定资产卡片（对应 ScrmFixedAssetEntity） */
interface FixedAsset {
  id: string;
  companyId?: string;
  companyName?: string;
  assetName: string;
  assetCategory: AssetCategory;
  originalValue?: number;
  residualRate?: number;
  usefulLifeMonths?: number;
  startDate?: string;
  boughtDate?: string;
  accumulatedDepreciation?: number;
  netValue?: number;
  status?: string;
  remark?: string;
}

/** 企业资产汇总 */
interface AssetSummary {
  assetCount?: number;
  originalTotal?: number;
  accumulatedTotal?: number;
  netTotal?: number;
}

/** 计提折旧结果 */
interface DepreciateResult {
  count?: number;
  totalAmount?: number;
  voucherId?: string | number | null;
  message?: string;
}

/** 新增/修改表单值 */
interface AssetFormValues {
  assetName: string;
  assetCategory: AssetCategory;
  originalValue: number;
  residualRate?: number;
  usefulLifeMonths: number;
  startDate?: Dayjs;
  boughtDate?: Dayjs;
  remark?: string;
}

/** 计提折旧表单值 */
interface DepreciateFormValues {
  periodYear: number;
  periodMonth: number;
  expenseSubjectCode?: string;
}

/** 资产类别映射 */
const categoryConfig: Record<AssetCategory, Segment> = {
  MACHINERY: { value: 'MACHINERY', label: '机器设备' },
  ELECTRONIC: { value: 'ELECTRONIC', label: '电子设备' },
  FURNITURE: { value: 'FURNITURE', label: '办公家具' },
  VEHICLE: { value: 'VEHICLE', label: '车辆' },
  OTHER: { value: 'OTHER', label: '其他' },
};

/** 资产类别可选项 */
const categoryOptions: Segment[] = Object.values(categoryConfig);

/** 卡片状态映射 */
const statusConfig: Record<string, { color: string; label: string }> = {
  ACTIVE: { color: 'green', label: '在用' },
  SCRAPPED: { color: 'default', label: '已报废' },
};

/** 状态可选项 */
const statusOptions: Segment[] = Object.entries(statusConfig).map(([value, cfg]) => ({
  value,
  label: cfg.label,
}));

/** 月份可选项（value 用数字，与表单数值口径一致，避免 Select 回显失配） */
const monthOptions: Array<{ value: number; label: string }> = Array.from(
  { length: 12 },
  (_, i) => ({ value: i + 1, label: `${i + 1} 月` }),
);

/** 金额格式化：空值显示占位符，有值统一千分位 + 两位小数 */
const money = (value?: number | null): string =>
  value == null
    ? '-'
    : Number(value).toLocaleString('zh-CN', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      });

/** 页面固定分页大小 */
const PAGE_SIZE = 10;

/**
 * 固定资产管理页（资产卡片 + 按月计提折旧）。
 * <p>
 * 折旧口径由服务端决定（直线法，月折旧 = 原值 × (1 − 残值率) / 寿命月数，
 * 最后一个月补差），页面只负责把口径讲清楚、把结果反馈回来：
 * </p>
 * <ul>
 *   <li><b>汇总卡只统计在用资产</b>：已报废资产不参与原值/净值合计，避免口径误读。</li>
 *   <li><b>计提结果直说要害</b>：本次计提几项、多少钱、生成了哪张凭证草稿——
 *       凭证号是会计下一步去「记账」页核对的唯一线索，不能省。</li>
 *   <li><b>报废不可撤销</b>：用 Popconfirm 二次确认，并在文案里写明退出计提范围。</li>
 * </ul>
 *
 * @author Hsi Chu
 * @since V1.0
 */
export default function FixedAssets() {
  const { message } = App.useApp();

  // ============ 企业上下文 ============
  const [companyId, setCompanyId] = useState<string | undefined>();
  const [companyOptions, setCompanyOptions] = useState<Segment[]>([]);
  const [companyLoading, setCompanyLoading] = useState(false);

  // ============ 资产列表 ============
  const [assets, setAssets] = useState<FixedAsset[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [statusFilter, setStatusFilter] = useState<string | undefined>();
  const [categoryFilter, setCategoryFilter] = useState<string | undefined>();

  // ============ 汇总 ============
  const [summary, setSummary] = useState<AssetSummary | null>(null);
  const [summaryLoading, setSummaryLoading] = useState(false);

  // ============ 新增/修改弹窗 ============
  const [editorOpen, setEditorOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [assetForm] = Form.useForm<AssetFormValues>();

  // ============ 计提折旧弹窗 ============
  const [depreciateOpen, setDepreciateOpen] = useState(false);
  const [depreciating, setDepreciating] = useState(false);
  const [depreciateForm] = Form.useForm<DepreciateFormValues>();

  /** 加载企业下拉 */
  const loadCompanies = useCallback(async (keyword?: string) => {
    setCompanyLoading(true);
    try {
      const result = await listCompanies({ keyword: keyword || undefined, page: 0, size: 30 });
      setCompanyOptions(
        (result.content || []).map((c) => ({
          value: c.id,
          label: c.creditCode ? `${c.companyName}（${c.creditCode}）` : c.companyName,
        })),
      );
    } catch {
      // client.ts 已统一提示
    } finally {
      setCompanyLoading(false);
    }
  }, []);

  /** 加载资产列表 */
  const loadAssets = useCallback(async () => {
    if (!companyId) {
      setAssets([]);
      setTotal(0);
      return;
    }
    setLoading(true);
    try {
      const result = await apiClient.get<{ content?: FixedAsset[]; totalElements?: number }>(
        '/scrm/fixed-assets',
        {
          params: {
            companyId,
            page,
            size: PAGE_SIZE,
            status: statusFilter || undefined,
            category: categoryFilter || undefined,
          },
        },
      );
      setAssets(result.content || []);
      setTotal(result.totalElements || 0);
    } catch {
      // 已统一提示
    } finally {
      setLoading(false);
    }
  }, [companyId, page, statusFilter, categoryFilter]);

  /** 加载企业资产汇总 */
  const loadSummary = useCallback(async () => {
    if (!companyId) {
      setSummary(null);
      return;
    }
    setSummaryLoading(true);
    try {
      setSummary(await apiClient.get<AssetSummary>('/scrm/fixed-assets/company-summary', { params: { companyId } }));
    } catch {
      // 已统一提示
    } finally {
      setSummaryLoading(false);
    }
  }, [companyId]);

  useEffect(() => {
    void loadCompanies();
  }, [loadCompanies]);

  useEffect(() => {
    void loadAssets();
  }, [loadAssets]);

  useEffect(() => {
    void loadSummary();
  }, [loadSummary]);

  /** 打开新增弹窗（带常用默认值：残值率 5%、寿命 60 个月、日期取今天） */
  const openCreate = () => {
    if (!companyId) {
      message.warning('请先选择企业');
      return;
    }
    setEditingId(null);
    assetForm.resetFields();
    assetForm.setFieldsValue({
      assetCategory: 'OTHER',
      residualRate: 0.05,
      usefulLifeMonths: 60,
      startDate: dayjs(),
      boughtDate: dayjs(),
    } as never);
    setEditorOpen(true);
  };

  /** 打开修改弹窗（已报废卡片不允许改） */
  const openEdit = (record: FixedAsset) => {
    if (!companyId) {
      message.warning('请先选择企业');
      return;
    }
    if (record.status === 'SCRAPPED') {
      message.warning('已报废的资产卡片不可修改');
      return;
    }
    setEditingId(record.id);
    assetForm.resetFields();
    assetForm.setFieldsValue({
      assetName: record.assetName,
      assetCategory: record.assetCategory || 'OTHER',
      originalValue: Number(record.originalValue) || 0,
      residualRate: record.residualRate == null ? 0.05 : Number(record.residualRate),
      usefulLifeMonths: record.usefulLifeMonths ?? 60,
      startDate: record.startDate ? dayjs(record.startDate) : undefined,
      boughtDate: record.boughtDate ? dayjs(record.boughtDate) : undefined,
      remark: record.remark,
    } as never);
    setEditorOpen(true);
  };

  /** 提交新增/修改 */
  const submitAsset = async () => {
    if (!companyId) return;
    let values: AssetFormValues;
    try {
      values = await assetForm.validateFields();
    } catch {
      // 校验失败：antd 已在表单内提示
      return;
    }
    const payload = {
      assetName: values.assetName?.trim(),
      assetCategory: values.assetCategory,
      originalValue: Number(values.originalValue) || 0,
      residualRate: values.residualRate == null ? 0.05 : Number(values.residualRate),
      usefulLifeMonths: Number(values.usefulLifeMonths) || 0,
      startDate: values.startDate ? values.startDate.format('YYYY-MM-DD') : undefined,
      boughtDate: values.boughtDate ? values.boughtDate.format('YYYY-MM-DD') : undefined,
      remark: values.remark,
    };
    setSaving(true);
    try {
      if (editingId) {
        await apiClient.put<FixedAsset>(`/scrm/fixed-assets/${editingId}`, payload, {
          params: { companyId },
        });
        message.success('资产卡片已更新');
      } else {
        await apiClient.post<FixedAsset>('/scrm/fixed-assets', payload, { params: { companyId } });
        message.success('资产卡片已新增');
      }
      setEditorOpen(false);
      await Promise.all([loadAssets(), loadSummary()]);
    } catch {
      // 已统一提示
    } finally {
      setSaving(false);
    }
  };

  /** 资产报废 */
  const scrapAsset = async (record: FixedAsset) => {
    if (!companyId) return;
    try {
      await apiClient.post<FixedAsset>(`/scrm/fixed-assets/${record.id}/scrap`, undefined, {
        params: { companyId },
      });
      message.success(`「${record.assetName}」已报废，下月起退出计提范围`);
      await Promise.all([loadAssets(), loadSummary()]);
    } catch {
      // 已统一提示
    }
  };

  /** 打开计提折旧弹窗（默认取上一个会计期间，符合「月初计提上月」的实务节奏） */
  const openDepreciate = () => {
    if (!companyId) {
      message.warning('请先选择企业');
      return;
    }
    const lastMonth = dayjs().subtract(1, 'month');
    depreciateForm.resetFields();
    depreciateForm.setFieldsValue({
      periodYear: lastMonth.year(),
      periodMonth: lastMonth.month() + 1,
      expenseSubjectCode: '6602',
    } as never);
    setDepreciateOpen(true);
  };

  /** 提交计提折旧 */
  const submitDepreciate = async () => {
    if (!companyId) return;
    let values: DepreciateFormValues;
    try {
      values = await depreciateForm.validateFields();
    } catch {
      return;
    }
    setDepreciating(true);
    try {
      const result = await apiClient.post<DepreciateResult>('/scrm/fixed-assets/depreciate', undefined, {
        params: {
          companyId,
          periodYear: values.periodYear,
          periodMonth: values.periodMonth,
          expenseSubjectCode: values.expenseSubjectCode?.trim() || undefined,
        },
      });
      const count = result.count ?? 0;
      const amount = money(result.totalAmount ?? 0);
      if (count === 0 || !result.voucherId) {
        // 没有可计提资产不是错误：服务端会给出原因文案，原样透出
        message.warning(result.message || '该期间没有需要计提的资产', 6);
      } else {
        message.success(
          `${values.periodYear} 年 ${values.periodMonth} 月计提 ${count} 项，合计 ¥${amount}；凭证草稿 ${result.voucherId}`,
          8,
        );
      }
      setDepreciateOpen(false);
      await Promise.all([loadAssets(), loadSummary()]);
    } catch {
      // 已统一提示
    } finally {
      setDepreciating(false);
    }
  };

  /** 资产列表列 */
  const columns: ColumnsType<FixedAsset> = [
    {
      title: '资产名称',
      dataIndex: 'assetName',
      width: 180,
      ellipsis: true,
      render: (value: string) => <Typography.Text strong>{value || '-'}</Typography.Text>,
    },
    {
      title: '类别',
      dataIndex: 'assetCategory',
      width: 110,
      render: (value: AssetCategory) => {
        const cfg = categoryConfig[value];
        return <Tag color="blue">{cfg ? cfg.label : value || '-'}</Tag>;
      },
    },
    {
      title: '原值',
      dataIndex: 'originalValue',
      width: 130,
      align: 'right',
      render: (value: number) => money(value),
    },
    {
      title: '残值率',
      dataIndex: 'residualRate',
      width: 100,
      align: 'right',
      render: (value: number) =>
        value == null ? '-' : `${(Number(value) * 100).toFixed(2)}%`,
    },
    {
      title: '寿命',
      dataIndex: 'usefulLifeMonths',
      width: 90,
      align: 'right',
      render: (value: number) => (value == null ? '-' : `${value} 月`),
    },
    {
      title: '累计折旧',
      dataIndex: 'accumulatedDepreciation',
      width: 130,
      align: 'right',
      render: (value: number) => money(value),
    },
    {
      title: '净值',
      dataIndex: 'netValue',
      width: 130,
      align: 'right',
      render: (value: number) => <Typography.Text strong>{money(value)}</Typography.Text>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value: string) => {
        const cfg = statusConfig[value] || { color: 'default', label: value || '-' };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '启用日期',
      dataIndex: 'startDate',
      width: 120,
      render: (value: string) => value || '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button
            size="small"
            disabled={record.status === 'SCRAPPED'}
            onClick={() => openEdit(record)}
          >
            修改
          </Button>
          <Popconfirm
            title="确认报废该资产？"
            description="报废后不再参与折旧计提，且卡片不可再修改。"
            okText="确认报废"
            cancelText="取消"
            okButtonProps={{ danger: true }}
            disabled={record.status === 'SCRAPPED'}
            onConfirm={() => void scrapAsset(record)}
          >
            <Button size="small" danger disabled={record.status === 'SCRAPPED'}>
              报废
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      {/* 操作行：企业 + 新增 + 计提 */}
      <Card size="small" style={{ marginBottom: 16 }}>
        <Space wrap size="large">
          <Space>
            <span style={{ color: '#666' }}>企业</span>
            <Select
              showSearch
              allowClear
              style={{ width: 340 }}
              placeholder="搜索企业名称"
              filterOption={false}
              loading={companyLoading}
              onSearch={(value) => void loadCompanies(value)}
              onChange={(value) => {
                setCompanyId(value);
                setPage(0);
              }}
              value={companyId}
              options={companyOptions}
            />
          </Space>
          <Button
            icon={<ReloadOutlined />}
            onClick={() => void Promise.all([loadAssets(), loadSummary()])}
          >
            刷新
          </Button>
          <Tooltip title="录入一张新的固定资产卡片，下月起可计提折旧">
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              新增资产
            </Button>
          </Tooltip>
          <Tooltip title="按直线法对企业全部在用资产计提一个会计期间，生成凭证草稿">
            <Button icon={<ThunderboltOutlined />} onClick={openDepreciate}>
              计提本月折旧
            </Button>
          </Tooltip>
        </Space>
      </Card>

      {!companyId && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="请先选择企业。固定资产按「企业主体」独立建卡与计提，不同主体互不影响。"
        />
      )}

      {/* 汇总卡：只统计在用资产 */}
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={6}>
          <Card size="small" loading={summaryLoading}>
            <Statistic title="资产数（在用）" value={summary?.assetCount ?? 0} suffix="项" />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small" loading={summaryLoading}>
            <Statistic title="原值合计" value={summary?.originalTotal ?? 0} precision={2} prefix="¥" />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small" loading={summaryLoading}>
            <Statistic
              title="累计折旧"
              value={summary?.accumulatedTotal ?? 0}
              precision={2}
              prefix="¥"
              valueStyle={{ color: '#fa8c16' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small" loading={summaryLoading}>
            <Statistic
              title="净值合计"
              value={summary?.netTotal ?? 0}
              precision={2}
              prefix="¥"
              valueStyle={{ color: '#52c41a' }}
            />
          </Card>
        </Col>
      </Row>

      {/* 资产列表 */}
      <Card
        title={
          <Space>
            <span>固定资产卡片</span>
            <Typography.Text type="secondary" style={{ fontSize: 12, fontWeight: 'normal' }}>
              直线法折旧；汇总只统计在用资产
            </Typography.Text>
          </Space>
        }
        extra={
          <Space>
            <Select
              allowClear
              placeholder="状态"
              style={{ width: 120 }}
              value={statusFilter}
              onChange={(value) => {
                setStatusFilter(value);
                setPage(0);
              }}
              options={statusOptions}
            />
            <Select
              allowClear
              placeholder="类别"
              style={{ width: 130 }}
              value={categoryFilter}
              onChange={(value) => {
                setCategoryFilter(value);
                setPage(0);
              }}
              options={categoryOptions}
            />
          </Space>
        }
      >
        <Table<FixedAsset>
          rowKey="id"
          size="small"
          loading={loading}
          columns={columns}
          dataSource={assets}
          scroll={{ x: 1300 }}
          locale={{
            emptyText: (
              <Empty
                description={
                  companyId ? '该企业还没有固定资产卡片，点「新增资产」录入' : '选择企业后查看资产卡片'
                }
              />
            ),
          }}
          pagination={{
            current: page + 1,
            pageSize: PAGE_SIZE,
            total,
            showSizeChanger: false,
            onChange: (p) => setPage(p - 1),
            showTotal: (t) => `共 ${t} 项资产`,
          }}
        />
      </Card>

      {/* 新增/修改弹窗 */}
      <Modal
        title={editingId ? '修改资产卡片' : '新增资产卡片'}
        open={editorOpen}
        confirmLoading={saving}
        onOk={() => void submitAsset()}
        onCancel={() => setEditorOpen(false)}
        width={640}
        destroyOnClose
      >
        <Form form={assetForm} layout="vertical" preserve={false}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="assetName"
                label="资产名称"
                rules={[{ required: true, message: '请输入资产名称' }]}
              >
                <Input placeholder="如：办公电脑 / 生产线设备" maxLength={200} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="assetCategory"
                label="资产类别"
                rules={[{ required: true, message: '请选择资产类别' }]}
              >
                <Select placeholder="请选择类别" options={categoryOptions} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="originalValue"
                label="原值（元）"
                rules={[{ required: true, message: '请输入原值' }]}
              >
                <InputNumber
                  style={{ width: '100%' }}
                  min={0}
                  precision={2}
                  placeholder="不含税入账价值"
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="residualRate"
                label="残值率"
                tooltip="小数口径：0.05 = 5%。月折旧 = 原值 × (1 − 残值率) / 寿命月数"
                rules={[{ required: true, message: '请输入残值率' }]}
              >
                <InputNumber style={{ width: '100%' }} min={0} max={1} step={0.01} precision={4} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="usefulLifeMonths"
                label="使用寿命（月）"
                rules={[{ required: true, message: '请输入使用寿命（月）' }]}
              >
                <InputNumber style={{ width: '100%' }} min={1} max={600} precision={0} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="startDate" label="启用日期" tooltip="自该月起开始计提折旧">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="boughtDate" label="购买日期">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item name="remark" label="备注">
                <Input.TextArea rows={2} maxLength={500} placeholder="存放地点 / 使用部门 / 发票号等" />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>

      {/* 计提折旧弹窗 */}
      <Modal
        title="计提折旧"
        open={depreciateOpen}
        confirmLoading={depreciating}
        okText="开始计提"
        onOk={() => void submitDepreciate()}
        onCancel={() => setDepreciateOpen(false)}
        destroyOnClose
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="按直线法对企业全部在用资产计提，生成「借：费用 贷：1602 累计折旧」凭证草稿"
          description="已计提过的期间会自动跳过（幂等），可重复点击不会重复计提。"
        />
        <Form form={depreciateForm} layout="vertical" preserve={false}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="periodYear"
                label="会计年度"
                rules={[{ required: true, message: '请输入会计年度' }]}
              >
                <InputNumber style={{ width: '100%' }} min={2000} max={2100} precision={0} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="periodMonth"
                label="会计月份"
                rules={[{ required: true, message: '请选择会计月份' }]}
              >
                <Select placeholder="请选择月份" options={monthOptions} />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item
                name="expenseSubjectCode"
                label="费用科目"
                tooltip="留空则使用 6602 管理费用；需为已存在的科目编码"
              >
                <Input allowClear placeholder="默认 6602 管理费用" maxLength={20} />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </div>
  );
}
