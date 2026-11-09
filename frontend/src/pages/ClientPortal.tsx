/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ClientPortal.tsx
 * Date : 2026/11/08 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  App,
  Button,
  Card,
  Col,
  Descriptions,
  Divider,
  Empty,
  List,
  Row,
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ReloadOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';
import { listCompanies } from '../api/finance';

/** 公司概况块（服务端逐块容错，可能为 null） */
interface CompanyInfo {
  companyName: string;
  creditCode?: string;
  legalRepresentative?: string;
  companyType?: string;
  registeredCapital?: string;
  industry?: string;
  businessStatus?: string;
  taxStatus?: string;
  serviceStatus?: string;
  serviceEndDate?: string;
  accountantName?: string;
}

/** 最近申报记录行 */
interface RecentRecord {
  id: string;
  taxType: string;
  periodStart?: string;
  periodEnd?: string;
  dueDate?: string;
  taxAmount?: number;
  paidAmount?: number;
  declarationStatus: string;
  overdueDays?: number;
}

/** 报税进度块 */
interface TaxSummary {
  recordTotal: number;
  pendingCount: number;
  filedCount: number;
  overdueCount: number;
  underpaidCount: number;
  recentRecords: RecentRecord[];
}

/** 利润表（近 3 期，单期为 null 表示该期取数失败） */
interface IncomeStmt {
  period: number | string;
  revenueCurrent?: number;
  revenueYtd?: number;
  netProfitCurrent?: number;
  netProfitYtd?: number;
}

/** 资产负债表摘要 */
interface BalanceSheet {
  period: number | string;
  totalAssetClosing?: number;
  totalLiabilityClosing?: number;
  totalEquityClosing?: number;
  totalLiabilityAndEquityClosing?: number;
  balanced?: boolean;
}

/** 财务报表块 */
interface ReportBlock {
  year: number;
  latestMonth: number;
  incomeStatements: Array<IncomeStmt | null>;
  balanceSheet: BalanceSheet | null;
}

/** 固定资产摘要 */
interface Assets {
  assetCount: number;
  originalTotal?: number;
  accumulatedTotal?: number;
  netTotal?: number;
}

/** 风险项（level = WARN / CRITICAL） */
interface RiskItem {
  level: string;
  message: string;
  suggestion?: string;
}

/** 税负率趋势行（taxRate 为百分比字符串，数据不足时为 "-"） */
interface TrendItem {
  month: string;
  revenue?: string;
  taxRate?: string;
  flag?: string;
}

/** 税负风险块 */
interface TaxRisk {
  recordTotal: number;
  overdueCount: number;
  underpaidCount: number;
  risks: RiskItem[];
  trend: TrendItem[];
  generatedAt?: string;
}

/** 客户查账全景（GET /scrm/client-portal/{companyId}/overview 解包后的 data） */
interface ClientPortalOverview {
  companyId: string;
  year: number;
  company: CompanyInfo | null;
  taxSummary: TaxSummary | null;
  reports: ReportBlock | null;
  assets: Assets | null;
  taxRisk: TaxRisk | null;
}

/** 申报状态 → 颜色与文案（未知取值降级为灰色原文，服务端加状态不会白屏） */
const declarationStatusConfig: Record<string, { color: string; label: string }> = {
  NOT_STARTED: { color: 'default', label: '未开始' },
  PENDING: { color: 'processing', label: '申报中' },
  FILED: { color: 'blue', label: '已申报' },
  PAID: { color: 'green', label: '已缴款' },
  OVERDUE: { color: 'red', label: '已逾期' },
  ARCHIVED: { color: 'default', label: '已归档' },
  CANCELED: { color: 'default', label: '已取消' },
};

/** 经营状态 → 颜色与文案 */
const businessStatusConfig: Record<string, { color: string; label: string }> = {
  NORMAL: { color: 'green', label: '正常' },
  ABNORMAL: { color: 'red', label: '异常' },
  REVOKED: { color: 'red', label: '已吊销' },
  CANCELED: { color: 'default', label: '已注销' },
};

/** 税务状态 → 颜色与文案 */
const taxStatusConfig: Record<string, { color: string; label: string }> = {
  NORMAL: { color: 'green', label: '正常' },
  ABNORMAL: { color: 'red', label: '异常' },
  RISK: { color: 'orange', label: '风险' },
};

/** 服务状态 → 颜色与文案 */
const serviceStatusConfig: Record<string, { color: string; label: string }> = {
  ACTIVE: { color: 'green', label: '服务中' },
  NORMAL: { color: 'green', label: '服务中' },
  EXPIRED: { color: 'red', label: '已到期' },
  SUSPENDED: { color: 'orange', label: '已暂停' },
  TERMINATED: { color: 'default', label: '已终止' },
};

/** 风险级别映射 */
const riskLevelConfig: Record<string, { color: string; label: string }> = {
  CRITICAL: { color: 'red', label: '严重' },
  WARN: { color: 'orange', label: '警告' },
};

/** 税负率标记映射（LOW 偏低 / HIGH 偏高 / NORMAL 正常 / NA 数据不足） */
const flagConfig: Record<string, { color: string; label: string }> = {
  LOW: { color: 'blue', label: '偏低' },
  HIGH: { color: 'red', label: '偏高' },
  NORMAL: { color: 'green', label: '正常' },
  NA: { color: 'default', label: '数据不足' },
};

/** 金额格式化（千分位 2 位；接口给 "-" 等非数值时原样展示） */
const money = (value?: number | string | null) => {
  if (value == null || value === '') return '-';
  const num = Number(value);
  if (Number.isNaN(num)) return String(value);
  return num.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
};

/** 报表期间格式化：服务端给的是 yyyymm 整数（如 202611） */
const formatPeriod = (period?: number | string | null) => {
  if (period == null || period === '') return '-';
  const text = String(period);
  return /^\d{6}$/.test(text) ? `${text.slice(0, 4)}-${text.slice(4)}` : text;
};

/** 日期格式化（脏值不至于抛错） */
const dateText = (value?: string | null) => {
  if (!value) return '-';
  const parsed = dayjs(value);
  return parsed.isValid() ? parsed.format('YYYY-MM-DD') : String(value);
};

/** 状态 Tag（未知取值降级为灰色原文） */
const renderStatusTag = (
  value: string | undefined,
  config: Record<string, { color: string; label: string }>,
) => {
  if (!value) return <Tag>-</Tag>;
  const cfg = config[value];
  return cfg ? <Tag color={cfg.color}>{cfg.label}</Tag> : <Tag>{value}</Tag>;
};

/** 最近申报记录列 */
const recentColumns: ColumnsType<RecentRecord> = [
  { title: '税种', dataIndex: 'taxType', width: 120 },
  {
    title: '所属期',
    key: 'period',
    width: 200,
    render: (_, record) => (
      <span style={{ fontSize: 12 }}>
        {dateText(record.periodStart)} ~ {dateText(record.periodEnd)}
      </span>
    ),
  },
  {
    title: '应缴',
    dataIndex: 'taxAmount',
    width: 130,
    align: 'right',
    render: (value?: number) => money(value),
  },
  {
    title: '实缴',
    dataIndex: 'paidAmount',
    width: 130,
    align: 'right',
    render: (value?: number) => money(value),
  },
  {
    title: '截止日',
    dataIndex: 'dueDate',
    width: 120,
    render: (value?: string) => dateText(value),
  },
  {
    title: '状态',
    dataIndex: 'declarationStatus',
    width: 100,
    render: (value: string) => renderStatusTag(value, declarationStatusConfig),
  },
  {
    title: '逾期天数',
    dataIndex: 'overdueDays',
    width: 100,
    align: 'right',
    render: (value?: number) =>
      value != null && value > 0 ? (
        <Typography.Text type="danger">{value} 天</Typography.Text>
      ) : (
        <span style={{ color: '#bbb' }}>—</span>
      ),
  },
];

/** 利润表列 */
const incomeColumns: ColumnsType<IncomeStmt> = [
  {
    title: '期间',
    dataIndex: 'period',
    width: 110,
    render: (value: number | string) => formatPeriod(value),
  },
  {
    title: '营业收入',
    dataIndex: 'revenueCurrent',
    align: 'right',
    render: (value?: number) => money(value),
  },
  {
    title: '净利润',
    dataIndex: 'netProfitCurrent',
    align: 'right',
    render: (value?: number) => (
      <Typography.Text type={(value ?? 0) < 0 ? 'danger' : undefined}>{money(value)}</Typography.Text>
    ),
  },
];

/** 税负率趋势列 */
const trendColumns: ColumnsType<TrendItem> = [
  { title: '月份', dataIndex: 'month', width: 110 },
  {
    title: '营业收入',
    dataIndex: 'revenue',
    width: 140,
    align: 'right',
    render: (value?: string) => money(value),
  },
  {
    title: '税负率',
    dataIndex: 'taxRate',
    width: 110,
    align: 'right',
    render: (value?: string) => (value == null || value === '' ? '-' : value),
  },
  {
    title: '标记',
    dataIndex: 'flag',
    width: 110,
    render: (value?: string) => renderStatusTag(value, flagConfig),
  },
];

/**
 * 客户查账（老板查账 · 只读 H5 面板）。
 * <p>
 * 面向代账客户的<b>只读</b>查账视图：一个企业 + 一个年度 → 公司概况、报税进度、
 * 近 3 期报表、固定资产、税负风险五块聚合展示，减少会计反复回答
 * 「这个月报了吗 / 报表给我看一眼」的重复劳动。
 * </p>
 * <p>
 * 三个刻意的选择：
 * </p>
 * <ul>
 *   <li><b>不选企业不发请求</b>：接口按 companyId 聚合，空 companyId 直接给空态，
 *       避免无意义的 404 与后端数据范围校验报错；</li>
 *   <li><b>逐块容错</b>：服务端每块独立 try/catch，某块为 null 时该卡退化成 Empty，
 *       不让整页崩成错误页（对齐后端 {@code overview} 的容错口径）；</li>
 *   <li><b>状态/标记只做颜色映射</b>：遇未知取值降级为灰色原文，
 *       服务端将来加状态时不至于白屏。</li>
 * </ul>
 *
 * @author Hsi Chu
 * @since V1.0
 */
export default function ClientPortal() {
  const { message } = App.useApp();

  const currentYear = dayjs().year();

  // ============ 查询条件 ============
  const [companyId, setCompanyId] = useState<string | undefined>();
  const [year, setYear] = useState<number>(currentYear);
  const [companyOptions, setCompanyOptions] = useState<Array<{ value: string; label: string }>>([]);
  const [companyLoading, setCompanyLoading] = useState(false);

  // ============ 查账数据 ============
  const [overview, setOverview] = useState<ClientPortalOverview | null>(null);
  const [loading, setLoading] = useState(false);

  /** 近 5 年选项（当年往前推 4 年） */
  const yearOptions = useMemo(
    () =>
      Array.from({ length: 5 }, (_, i) => {
        const y = currentYear - i;
        return { value: y, label: `${y} 年` };
      }),
    [currentYear],
  );

  /** 加载企业下拉（可搜索，服务端过滤） */
  const loadCompanies = useCallback(async (keyword?: string) => {
    setCompanyLoading(true);
    try {
      const result = await listCompanies({ keyword: keyword || undefined, page: 0, size: 30 });
      setCompanyOptions(
        (result?.content || []).map((c) => ({
          value: c.id,
          label: c.creditCode ? `${c.companyName}（${c.creditCode}）` : c.companyName,
        })),
      );
    } catch {
      // client.ts 拦截器已统一提示
    } finally {
      setCompanyLoading(false);
    }
  }, []);

  /** 加载查账全景（未选企业直接返回，不发请求） */
  const loadOverview = useCallback(async () => {
    if (!companyId) {
      setOverview(null);
      return;
    }
    setLoading(true);
    try {
      const data = await apiClient.get<ClientPortalOverview>(
        `/scrm/client-portal/${companyId}/overview`,
        { params: { year } },
      );
      setOverview(data ?? null);
    } catch {
      // 后端原文已由拦截器弹出（404 除外），这里补一条面向客户的提示，并清空防止展示旧账
      message.error('查账数据加载失败，请稍后重试');
      setOverview(null);
    } finally {
      setLoading(false);
    }
  }, [companyId, year, message]);

  useEffect(() => {
    void loadCompanies();
  }, [loadCompanies]);

  useEffect(() => {
    void loadOverview();
  }, [loadOverview]);

  /** 公司概况卡（company 为空时不渲染） */
  const renderCompanyCard = (company: CompanyInfo) => (
    <Card
      size="small"
      style={{ marginBottom: 16 }}
      title={
        <Space wrap size={8}>
          <Typography.Title level={5} style={{ margin: 0 }}>
            {company.companyName}
          </Typography.Title>
          {company.companyType && <Tag color="blue">{company.companyType}</Tag>}
        </Space>
      }
      extra={
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          {overview?.year ?? year} 年度查账
        </Typography.Text>
      }
    >
      <Descriptions
        size="small"
        column={{ xs: 1, sm: 2, lg: 3 }}
        items={[
          { key: 'creditCode', label: '纳税人识别号', children: company.creditCode || '-' },
          { key: 'legal', label: '法定代表人', children: company.legalRepresentative || '-' },
          { key: 'industry', label: '所属行业', children: company.industry || '-' },
          { key: 'capital', label: '注册资本', children: company.registeredCapital || '-' },
          {
            key: 'business',
            label: '经营状态',
            children: renderStatusTag(company.businessStatus, businessStatusConfig),
          },
          {
            key: 'tax',
            label: '税务状态',
            children: renderStatusTag(company.taxStatus, taxStatusConfig),
          },
          {
            key: 'service',
            label: '服务状态',
            children: renderStatusTag(company.serviceStatus, serviceStatusConfig),
          },
          { key: 'serviceEnd', label: '服务到期', children: dateText(company.serviceEndDate) },
          { key: 'accountant', label: '负责会计', children: company.accountantName || '-' },
        ]}
      />
    </Card>
  );

  /** 报税进度：四指标 + 最近申报记录 */
  const renderTaxCard = (summary: TaxSummary) => (
    <>
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} sm={12} lg={6}>
          <Card size="small">
            <Statistic title="申报记录" value={summary.recordTotal ?? 0} suffix="条" />
            <div style={{ fontSize: 12, color: '#999', marginTop: 4 }}>
              已申报/已缴款 {summary.filedCount ?? 0} 条
            </div>
          </Card>
        </Col>
        <Col xs={12} sm={12} lg={6}>
          <Card size="small">
            <Statistic
              title="待申报"
              value={summary.pendingCount ?? 0}
              suffix="条"
              valueStyle={{ color: (summary.pendingCount ?? 0) > 0 ? '#fa8c16' : undefined }}
            />
            <div style={{ fontSize: 12, color: '#999', marginTop: 4 }}>未开始 + 申报中</div>
          </Card>
        </Col>
        <Col xs={12} sm={12} lg={6}>
          <Card size="small">
            <Statistic
              title="逾期"
              value={summary.overdueCount ?? 0}
              suffix="条"
              valueStyle={{ color: (summary.overdueCount ?? 0) > 0 ? '#ff4d4f' : undefined }}
            />
            <div style={{ fontSize: 12, color: '#999', marginTop: 4 }}>超出申报截止日</div>
          </Card>
        </Col>
        <Col xs={12} sm={12} lg={6}>
          <Card size="small">
            <Statistic
              title="欠缴"
              value={summary.underpaidCount ?? 0}
              suffix="条"
              valueStyle={{ color: (summary.underpaidCount ?? 0) > 0 ? '#ff4d4f' : undefined }}
            />
            <div style={{ fontSize: 12, color: '#999', marginTop: 4 }}>已到期但未缴足</div>
          </Card>
        </Col>
      </Row>

      <Card
        size="small"
        title="最近申报记录"
        style={{ marginBottom: 16 }}
        extra={
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            最多展示最近 6 条
          </Typography.Text>
        }
      >
        <Table<RecentRecord>
          rowKey="id"
          size="small"
          columns={recentColumns}
          dataSource={summary.recentRecords ?? []}
          pagination={false}
          scroll={{ x: 900 }}
          locale={{ emptyText: <Empty description="该年度暂无申报记录" /> }}
        />
      </Card>
    </>
  );

  /** 财务报表：近 3 期利润表 + 最新资产负债表摘要 */
  const renderReportsCard = (reports: ReportBlock) => {
    const income = (reports.incomeStatements ?? []).filter(
      (row): row is IncomeStmt => row != null,
    );
    const sheet = reports.balanceSheet;

    return (
      <Card
        size="small"
        title="财务报表"
        style={{ marginBottom: 16 }}
        extra={
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {reports.year} 年 · 数据截至 {reports.latestMonth} 月
          </Typography.Text>
        }
      >
        <Table<IncomeStmt>
          rowKey={(row) => String(row.period)}
          size="small"
          columns={incomeColumns}
          dataSource={income}
          pagination={false}
          scroll={{ x: 520 }}
          locale={{ emptyText: <Empty description="近 3 期还没有利润表数据" /> }}
        />

        <Divider style={{ margin: '16px 0 12px' }} />

        <Typography.Text strong style={{ display: 'block', marginBottom: 12 }}>
          资产负债表摘要
          {sheet?.period != null && (
            <Typography.Text type="secondary" style={{ fontSize: 12, marginInlineStart: 8, fontWeight: 'normal' }}>
              {formatPeriod(sheet.period)} 期末
            </Typography.Text>
          )}
        </Typography.Text>

        {!sheet ? (
          <Empty description="还没有资产负债表数据" />
        ) : (
          <Row gutter={[16, 16]}>
            <Col xs={12} sm={12} lg={6}>
              <Statistic title="期末总资产" value={money(sheet.totalAssetClosing)} />
            </Col>
            <Col xs={12} sm={12} lg={6}>
              <Statistic title="期末总负债" value={money(sheet.totalLiabilityClosing)} />
            </Col>
            <Col xs={12} sm={12} lg={6}>
              <Statistic title="期末净资产" value={money(sheet.totalEquityClosing)} />
            </Col>
            <Col xs={12} sm={12} lg={6}>
              <div style={{ paddingTop: 8 }}>
                <div style={{ fontSize: 14, color: 'rgba(0, 0, 0, 0.45)' }}>是否平衡</div>
                <div style={{ marginTop: 8 }}>
                  {sheet.balanced === true ? (
                    <Tag color="green">平衡</Tag>
                  ) : sheet.balanced === false ? (
                    <Tag color="red">不平</Tag>
                  ) : (
                    <Tag>-</Tag>
                  )}
                </div>
                <div style={{ fontSize: 12, color: '#999', marginTop: 4 }}>
                  负债+权益 {money(sheet.totalLiabilityAndEquityClosing)}
                </div>
              </div>
            </Col>
          </Row>
        )}
      </Card>
    );
  };

  /** 固定资产摘要（assets 为空不渲染） */
  const renderAssetsCard = (assets: Assets) => (
    <Card size="small" title="固定资产" style={{ marginBottom: 16 }}>
      <Row gutter={[16, 16]}>
        <Col xs={12} sm={12} lg={6}>
          <Statistic title="资产数量" value={assets.assetCount ?? 0} suffix="项" />
        </Col>
        <Col xs={12} sm={12} lg={6}>
          <Statistic title="原值合计" value={money(assets.originalTotal)} />
        </Col>
        <Col xs={12} sm={12} lg={6}>
          <Statistic title="累计折旧" value={money(assets.accumulatedTotal)} />
        </Col>
        <Col xs={12} sm={12} lg={6}>
          <Statistic title="净值合计" value={money(assets.netTotal)} />
        </Col>
      </Row>
    </Card>
  );

  /** 税负风险：风险项 + 税负率趋势（taxRisk 为空不渲染） */
  const renderRiskCard = (risk: TaxRisk) => {
    const risks = risk.risks ?? [];
    const trend = risk.trend ?? [];
    const criticalCount = risks.filter((item) => item.level === 'CRITICAL').length;

    return (
      <Card
        size="small"
        title="税负风险"
        extra={
          <Space size={8}>
            {criticalCount > 0 && <Tag color="red">严重 {criticalCount} 项</Tag>}
            {risk.generatedAt && (
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                {risk.generatedAt}
              </Typography.Text>
            )}
          </Space>
        }
      >
        <Space size="large" wrap style={{ marginBottom: 12 }}>
          <Typography.Text style={{ fontSize: 12, color: '#666' }}>
            申报记录 {risk.recordTotal ?? 0} 条 · 逾期 {risk.overdueCount ?? 0} 条 · 欠缴{' '}
            {risk.underpaidCount ?? 0} 条
          </Typography.Text>
        </Space>

        <List
          size="small"
          dataSource={risks}
          locale={{ emptyText: <Empty description="未发现涉税风险，申报与缴款均正常" /> }}
          renderItem={(item) => {
            const cfg = riskLevelConfig[item.level] || { color: 'default', label: item.level };
            return (
              <List.Item>
                <List.Item.Meta
                  avatar={<Tag color={cfg.color}>{cfg.label}</Tag>}
                  title={<span>{item.message}</span>}
                  description={
                    item.suggestion ? (
                      <span style={{ fontSize: 12 }}>建议：{item.suggestion}</span>
                    ) : null
                  }
                />
              </List.Item>
            );
          }}
        />

        <Divider style={{ margin: '12px 0' }} />

        <Typography.Text strong style={{ display: 'block', marginBottom: 8 }}>
          税负率趋势
          <Typography.Text type="secondary" style={{ fontSize: 12, marginInlineStart: 8, fontWeight: 'normal' }}>
            增值税税负率 = 应纳增值税 / 当月营业收入
          </Typography.Text>
        </Typography.Text>

        <Table<TrendItem>
          rowKey="month"
          size="small"
          columns={trendColumns}
          dataSource={trend.slice(0, 12)}
          pagination={false}
          scroll={{ x: 480 }}
          locale={{ emptyText: <Empty description="该年度暂无税负率数据" /> }}
        />
      </Card>
    );
  };

  const company = overview?.company ?? null;
  const taxSummary = overview?.taxSummary ?? null;
  const reports = overview?.reports ?? null;
  const assets = overview?.assets ?? null;
  const taxRisk = overview?.taxRisk ?? null;

  return (
    <div>
      {/* 查询条件 */}
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
              onChange={(value) => setCompanyId(value)}
              value={companyId}
              options={companyOptions}
            />
          </Space>
          <Space>
            <span style={{ color: '#666' }}>年度</span>
            <Select
              style={{ width: 120 }}
              value={year}
              onChange={(value) => setYear(value)}
              options={yearOptions}
            />
          </Space>
          <Button
            icon={<ReloadOutlined />}
            loading={loading}
            disabled={!companyId}
            onClick={() => void loadOverview()}
          >
            刷新
          </Button>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            只读面板：不提供任何修改入口
          </Typography.Text>
        </Space>
      </Card>

      <Spin spinning={loading}>
        {!companyId ? (
          <Card size="small">
            <Empty description="请先选择企业。查账面板按「企业 + 年度」只读聚合，未选企业不会发起请求" />
          </Card>
        ) : !overview ? (
          <Card size="small">
            <Empty description="暂无查账数据，请确认企业与年度后点「刷新」重试" />
          </Card>
        ) : (
          <>
            {company && renderCompanyCard(company)}

            {taxSummary ? (
              renderTaxCard(taxSummary)
            ) : (
              <Card size="small" title="报税进度" style={{ marginBottom: 16 }}>
                <Empty description="该企业暂无报税数据" />
              </Card>
            )}

            {reports ? (
              renderReportsCard(reports)
            ) : (
              <Card size="small" title="财务报表" style={{ marginBottom: 16 }}>
                <Empty description="该企业暂无财务报表数据" />
              </Card>
            )}

            {assets && renderAssetsCard(assets)}

            {taxRisk ? (
              renderRiskCard(taxRisk)
            ) : (
              <Card size="small" title="税负风险">
                <Empty description="该企业暂无税负风险数据" />
              </Card>
            )}
          </>
        )}
      </Spin>
    </div>
  );
}
