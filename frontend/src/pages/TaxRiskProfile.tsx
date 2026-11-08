/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : TaxRiskProfile.tsx
 * Date : 2026/11/08 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Button,
  Card,
  Col,
  Empty,
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

/** 税负率趋势行（month 为 YYYY-MM；revenue / taxRate 数据不足时为 "-"） */
interface TrendItem {
  month: string;
  revenue: string;
  taxRate: string;
  flag: string;
}

/** 风险项（level = WARN / CRITICAL） */
interface RiskItem {
  level: string;
  message: string;
  suggestion?: string;
}

/** 税负风险画像（GET /scrm/tax-calendar/companies/{companyId}/risk-profile） */
interface TaxRiskProfile {
  companyId: string;
  year: number;
  recordTotal: number;
  overdueCount: number;
  underpaidCount: number;
  trend: TrendItem[];
  risks: RiskItem[];
  generatedAt?: string;
}

/** 风险级别映射 */
const riskLevelConfig: Record<string, { label: string; color: string }> = {
  CRITICAL: { label: '严重', color: 'red' },
  WARN: { label: '警告', color: 'orange' },
};

/** 税负率标记映射（LOW 偏低 / HIGH 偏高 / NORMAL 正常 / NA 数据不足） */
const flagConfig: Record<string, { label: string; color: string }> = {
  LOW: { label: '偏低', color: 'blue' },
  HIGH: { label: '偏高', color: 'red' },
  NORMAL: { label: '正常', color: 'green' },
  NA: { label: '数据不足', color: 'default' },
};

/** 金额/数字展示：接口可能直接给 "-"，此时原样展示 */
const amount = (value?: string) => {
  if (value == null || value === '' || value === '-') return '-';
  const num = Number(value);
  if (Number.isNaN(num)) return value;
  return num.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
};

/**
 * 税负风险画像页（纯只读分析）。
 * <p>
 * 后端从申报记录 + 利润表收入聚合出「增值税税负率趋势」与「逾期/欠缴/税负率异常」风险项，
 * 前端只做呈现，不做任何推断：阈值判断（连续 3 月 &lt; 1.0% 偏低、&gt; 4.5% 偏高、
 * 逾期率 &gt; 20% 严重）全部由服务端给出，避免前后端两套口径互相打架。
 * </p>
 * <p>
 * 因此本页对 {@code flag} / {@code level} 只做颜色映射，遇到未知取值一律降级为灰色 Tag，
 * 未来服务端新增标记时页面不会白屏。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
export default function TaxRiskProfilePage() {
  // ============ 查询条件 ============
  const currentYear = dayjs().year();
  const [companyId, setCompanyId] = useState<string | undefined>();
  const [year, setYear] = useState<number>(currentYear);
  const [companyOptions, setCompanyOptions] = useState<Array<{ value: string; label: string }>>([]);
  const [companyLoading, setCompanyLoading] = useState(false);

  // ============ 画像数据 ============
  const [profile, setProfile] = useState<TaxRiskProfile | null>(null);
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

  /** 加载企业 */
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

  /** 加载风险画像 */
  const loadProfile = useCallback(async () => {
    if (!companyId) {
      setProfile(null);
      return;
    }
    setLoading(true);
    try {
      const data = await apiClient.get<TaxRiskProfile>(
        `/scrm/tax-calendar/companies/${companyId}/risk-profile`,
        { params: { year } },
      );
      setProfile(data);
    } catch {
      // 失败提示由 apiClient 拦截器统一弹出（含 404），这里只清空避免展示上一年度的旧数据
      setProfile(null);
    } finally {
      setLoading(false);
    }
  }, [companyId, year]);

  useEffect(() => {
    void loadCompanies();
  }, [loadCompanies]);

  useEffect(() => {
    void loadProfile();
  }, [loadProfile]);

  /** 风险项列 */
  const riskColumns: ColumnsType<RiskItem> = [
    {
      title: '级别',
      dataIndex: 'level',
      width: 100,
      render: (v: string) => {
        const cfg = riskLevelConfig[v] || { label: v || '未知', color: 'default' };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '风险消息',
      dataIndex: 'message',
      render: (v: string) => <Typography.Text strong>{v}</Typography.Text>,
    },
    {
      title: '建议',
      dataIndex: 'suggestion',
      width: '38%',
      render: (v?: string) => v || <span style={{ color: '#bbb' }}>—</span>,
    },
  ];

  /** 税负率趋势列 */
  const trendColumns: ColumnsType<TrendItem> = [
    { title: '月份', dataIndex: 'month', width: 120 },
    {
      title: '营业收入',
      dataIndex: 'revenue',
      width: 180,
      align: 'right',
      render: (v: string) => amount(v),
    },
    {
      title: '增值税税负率',
      dataIndex: 'taxRate',
      width: 150,
      align: 'right',
      render: (v: string) => (v == null || v === '' ? '-' : v),
    },
    {
      title: '标记',
      dataIndex: 'flag',
      width: 120,
      render: (v: string) => {
        const cfg = flagConfig[v] || { label: v || '未知', color: 'default' };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
  ];

  /** 有画像但该年度没有任何申报记录 */
  const noRecord = !!profile && (profile.recordTotal ?? 0) === 0;

  const risks = profile?.risks ?? [];
  const trend = profile?.trend ?? [];

  return (
    <div>
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
          <Button icon={<ReloadOutlined />} loading={loading} onClick={() => void loadProfile()}>
            刷新
          </Button>
          {profile?.generatedAt && (
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              生成于 {profile.generatedAt}
            </Typography.Text>
          )}
        </Space>
      </Card>

      <Spin spinning={loading}>
        {!companyId ? (
          <Card size="small">
            <Empty description="请先选择企业。税负风险画像按「企业 + 年度」独立分析" />
          </Card>
        ) : !profile || noRecord ? (
          <Card size="small">
            <Empty description={noRecord ? '该年度暂无申报记录' : '暂无画像数据，请确认企业与年度后重试'} />
          </Card>
        ) : (
          <>
            <Row gutter={16} style={{ marginBottom: 16 }}>
              <Col span={6}>
                <Card size="small">
                  <Statistic title="申报记录数" value={profile?.recordTotal ?? 0} suffix="条" />
                </Card>
              </Col>
              <Col span={6}>
                <Card size="small">
                  <Statistic
                    title="逾期数"
                    value={profile?.overdueCount ?? 0}
                    suffix="条"
                    valueStyle={{
                      color: (profile?.overdueCount ?? 0) > 0 ? '#ff4d4f' : undefined,
                    }}
                  />
                </Card>
              </Col>
              <Col span={6}>
                <Card size="small">
                  <Statistic
                    title="欠缴数"
                    value={profile?.underpaidCount ?? 0}
                    suffix="条"
                    valueStyle={{
                      color: (profile?.underpaidCount ?? 0) > 0 ? '#fa8c16' : undefined,
                    }}
                  />
                </Card>
              </Col>
              <Col span={6}>
                <Card size="small">
                  <Statistic
                    title="风险项数"
                    value={risks.length}
                    suffix="项"
                    valueStyle={{ color: risks.length > 0 ? '#ff4d4f' : undefined }}
                  />
                </Card>
              </Col>
            </Row>

            <Card
              size="small"
              title={
                <Space>
                  <span>风险项</span>
                  <Typography.Text type="secondary" style={{ fontSize: 12, fontWeight: 'normal' }}>
                    {year} 年 · 逾期率 &gt; 20% 记严重，税负率连续偏离与欠缴记警告
                  </Typography.Text>
                </Space>
              }
              style={{ marginBottom: 16 }}
            >
              <Table<RiskItem>
                rowKey={(record) => `${record.level}-${record.message}`}
                size="small"
                columns={riskColumns}
                dataSource={risks}
                pagination={false}
                locale={{
                  emptyText: <Empty description="未发现涉税风险，申报与缴款均正常" />,
                }}
              />
            </Card>

            <Card
              size="small"
              title={
                <Space>
                  <span>税负率趋势</span>
                  <Typography.Text type="secondary" style={{ fontSize: 12, fontWeight: 'normal' }}>
                    增值税税负率 = 应纳增值税 / 当月营业收入；数据不足的月份标记为「数据不足」
                  </Typography.Text>
                </Space>
              }
            >
              <Table<TrendItem>
                rowKey="month"
                size="small"
                columns={trendColumns}
                dataSource={trend}
                pagination={false}
                scroll={{ x: 600 }}
                locale={{ emptyText: <Empty description="该年度暂无申报记录" /> }}
              />
            </Card>
          </>
        )}
      </Spin>
    </div>
  );
}
