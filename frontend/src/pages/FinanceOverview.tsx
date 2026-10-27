/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : FinanceOverview.tsx
 * Date : 2026/10/26 12:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Card,
  Col,
  Empty,
  List,
  Row,
  Skeleton,
  Space,
  Statistic,
  Tag,
  Tooltip,
} from 'antd';
import {
  CalendarOutlined,
  FileDoneOutlined,
  FileProtectOutlined,
  ReloadOutlined,
  SyncOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import dayjs from 'dayjs';
import {
  getQualificationSummary,
  listCompanies,
  listRecordsByMonth,
  listRenewalTasks,
  type QualificationSummary,
  type RenewalTask,
  type TaxRecord,
} from '../api/finance';

/** 首页统计卡片配置 */
interface StatCard {
  /** 卡片标题 */
  title: string;
  /** 主数值 */
  value: number;
  /** 图标 */
  icon: React.ReactNode;
  /** 主题色（图标底色与数值同色） */
  color: string;
  /** 底部补充说明 */
  footer: React.ReactNode;
  /** 点击跳转路由 */
  targetPath: string;
}

/** 申报状态 → 颜色与文案 */
const declarationStatusConfig: Record<string, { color: string; label: string }> = {
  NOT_STARTED: { color: 'default', label: '未开始' },
  PENDING: { color: 'processing', label: '申报中' },
  FILED: { color: 'blue', label: '已申报' },
  PAID: { color: 'green', label: '已缴款' },
  OVERDUE: { color: 'red', label: '已逾期' },
  ARCHIVED: { color: 'default', label: '已归档' },
  CANCELED: { color: 'default', label: '已取消' },
};

/** 续费任务状态 → 颜色与文案 */
const renewalStatusConfig: Record<string, { color: string; label: string }> = {
  PENDING: { color: 'orange', label: '待处理' },
  REMINDED: { color: 'blue', label: '已提醒' },
  CONFIRMED: { color: 'green', label: '已确认' },
  RENEWED: { color: 'green', label: '已续费' },
  LOST: { color: 'red', label: '已流失' },
  CANCELED: { color: 'default', label: '已取消' },
};

/**
 * 财税概览（系统首页）。
 * <p>
 * 面向代账公司管理层，聚焦财税作业口径而非 SCRM 营销口径：
 * </p>
 * <ul>
 *   <li><b>核心统计</b>：在管主体数、本月待申报、合规到期、待续费；</li>
 *   <li><b>本月待申报清单</b>：当月未完成的申报记录，按到期日升序；</li>
 *   <li><b>即将到期与续费</b>：临期资质与续费任务，避免漏办。</li>
 * </ul>
 * <p>
 * 数据全部来自既有财税域接口，不新增后端依赖；单个接口失败不影响其他区块。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
export default function FinanceOverview() {
  const navigate = useNavigate();

  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  /** 本月待申报记录（未完成口径） */
  const [records, setRecords] = useState<TaxRecord[]>([]);
  /** 合规资质统计 */
  const [qualification, setQualification] = useState<QualificationSummary | null>(null);
  /** 待续费任务 */
  const [renewals, setRenewals] = useState<RenewalTask[]>([]);
  /** 在管主体总数 */
  const [companyTotal, setCompanyTotal] = useState(0);
  /** 部分接口失败时的提示集合 */
  const [partialErrors, setPartialErrors] = useState<string[]>([]);

  /**
   * 加载首页全部数据。
   * <p>
   * 三个接口相互独立，用 allSettled 并行拉取：任一失败只记录提示，
   * 不让整页退化成错误页。
   * </p>
   *
   * @param silent 为真时不展示整页骨架屏（用于手动刷新）
   */
  const load = useCallback(async (silent = false) => {
    if (silent) {
      setRefreshing(true);
    } else {
      setLoading(true);
    }
    const now = dayjs();
    const errors: string[] = [];
    const [recordResult, qualificationResult, renewalResult, companyResult] =
      await Promise.allSettled([
        listRecordsByMonth(now.year(), now.month() + 1),
        getQualificationSummary(),
        listRenewalTasks({ page: 0, size: 20 }),
        // 只要总数，取 1 条即可
        listCompanies({ page: 0, size: 1 }),
      ]);

    if (recordResult.status === 'fulfilled') {
      // 接口约定返回数组；异常形态（如后端降级返回空对象）时退化为空列表，避免整页崩溃
      setRecords(Array.isArray(recordResult.value) ? recordResult.value : []);
    } else {
      errors.push('本月申报记录');
    }

    if (qualificationResult.status === 'fulfilled') {
      setQualification(qualificationResult.value);
    } else {
      errors.push('合规资质统计');
    }

    if (renewalResult.status === 'fulfilled') {
      const rows = renewalResult.value?.content;
      setRenewals(Array.isArray(rows) ? rows : []);
    } else {
      errors.push('续费任务');
    }

    if (companyResult.status === 'fulfilled') {
      setCompanyTotal(companyResult.value?.totalElements || 0);
    } else {
      errors.push('在管主体');
    }

    setPartialErrors(errors);
    setLoading(false);
    setRefreshing(false);
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  /** 本月尚未完成的申报记录（未开始 / 申报中 / 已逾期） */
  const pendingRecords = records
    .filter(r => ['NOT_STARTED', 'PENDING', 'OVERDUE'].includes(r.declarationStatus))
    .sort((a, b) => dayjs(a.dueDate).valueOf() - dayjs(b.dueDate).valueOf());

  /** 已在申报期内临期的记录（7 天内到期） */
  const dueSoonRecords = pendingRecords.filter(r => {
    const days = dayjs(r.dueDate).startOf('day').diff(dayjs().startOf('day'), 'day');
    return days >= 0 && days <= 7;
  });

  /** 待处理的续费任务 */
  const pendingRenewals = renewals.filter(r =>
    ['PENDING', 'REMINDED'].includes(r.status || ''),
  );

  /** 统计卡片：口径全部来自财税域 */
  const statCards: StatCard[] = [
    {
      title: '在管主体',
      value: companyTotal,
      icon: <TeamOutlined />,
      color: '#6366f1',
      footer: <>公司主体总数</>,
      targetPath: '/companies',
    },
    {
      title: '本月待申报',
      value: pendingRecords.length,
      icon: <CalendarOutlined />,
      color: '#f59e0b',
      footer: (
        <>
          其中 {dueSoonRecords.length} 项 7 天内到期
        </>
      ),
      targetPath: '/tax-calendar',
    },
    {
      title: '资质临期',
      value: qualification?.expiring || 0,
      icon: <FileProtectOutlined />,
      color: '#ef4444',
      footer: <>90 天内到期 {qualification?.dueSoon90d || 0} 项</>,
      targetPath: '/compliance',
    },
    {
      title: '待续费',
      value: pendingRenewals.length,
      icon: <SyncOutlined />,
      color: '#10b981',
      footer: <>待处理 / 已提醒口径</>,
      targetPath: '/renewals',
    },
  ];

  /** 渲染单张统计卡片 */
  const renderStatCard = (card: StatCard) => (
    <Card hoverable onClick={() => navigate(card.targetPath)}>
      <Space align="start" size={16}>
        <div
          style={{
            width: 44,
            height: 44,
            borderRadius: 10,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: 20,
            background: `${card.color}1a`,
            color: card.color,
            flexShrink: 0,
          }}
        >
          {card.icon}
        </div>
        <div style={{ minWidth: 0 }}>
          <Statistic
            title={card.title}
            value={card.value}
            valueStyle={{ color: card.color, fontSize: 26 }}
          />
          <div style={{ fontSize: 12, color: 'var(--color-text-tertiary)', marginTop: 4 }}>
            {card.footer}
          </div>
        </div>
      </Space>
    </Card>
  );

  if (loading) {
    return (
      <Card>
        <Skeleton active paragraph={{ rows: 8 }} />
      </Card>
    );
  }

  return (
    <div>
      {/* 部分接口失败时降级提示，不阻断其余区块 */}
      {partialErrors.length > 0 && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message={`以下数据加载失败：${partialErrors.join('、')}，其余区块正常展示`}
        />
      )}

      {/* 顶部核心统计 */}
      <Row gutter={[16, 16]}>
        {statCards.map(card => (
          <Col key={card.title} xs={24} sm={12} lg={6}>
            {renderStatCard(card)}
          </Col>
        ))}
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        {/* 本月待申报清单 */}
        <Col xs={24} lg={14}>
          <Card
            title="本月待申报清单"
            extra={
              <Tooltip title="刷新全部数据">
                <ReloadOutlined
                  spin={refreshing}
                  style={{ cursor: 'pointer', color: 'var(--color-text-secondary)' }}
                  onClick={() => void load(true)}
                />
              </Tooltip>
            }
            styles={{ body: { padding: 0 } }}
          >
            {pendingRecords.length === 0 ? (
              <Empty description="本月申报已全部完成" style={{ padding: 32 }} />
            ) : (
              <List
                dataSource={pendingRecords.slice(0, 8)}
                renderItem={record => {
                  const cfg = declarationStatusConfig[record.declarationStatus] || {
                    color: 'default',
                    label: record.declarationStatus,
                  };
                  const days = dayjs(record.dueDate)
                    .startOf('day')
                    .diff(dayjs().startOf('day'), 'day');
                  const overdue = days < 0;
                  return (
                    <List.Item
                      style={{ cursor: 'pointer', padding: '10px 16px' }}
                      onClick={() => navigate('/tax-calendar')}
                    >
                      <List.Item.Meta
                        title={
                          <Space size={8}>
                            <span>{record.taxType}</span>
                            <Tag color={cfg.color}>{cfg.label}</Tag>
                          </Space>
                        }
                        description={
                          <span style={{ fontSize: 12 }}>
                            截止 {dayjs(record.dueDate).format('MM-DD')}
                            {overdue ? (
                              <Tag color="red" style={{ marginInlineStart: 8 }}>
                                已逾期 {Math.abs(days)} 天
                              </Tag>
                            ) : (
                              <span style={{ marginInlineStart: 8, color: 'var(--color-text-tertiary)' }}>
                                剩 {days} 天
                              </span>
                            )}
                          </span>
                        }
                      />
                    </List.Item>
                  );
                }}
              />
            )}
          </Card>
        </Col>

        {/* 续费与到期提醒 */}
        <Col xs={24} lg={10}>
          <Card title="待续费服务" styles={{ body: { padding: 0 } }}>
            {pendingRenewals.length === 0 ? (
              <Empty description="暂无待续费任务" style={{ padding: 32 }} />
            ) : (
              <List
                dataSource={pendingRenewals.slice(0, 6)}
                renderItem={task => {
                  const cfg = renewalStatusConfig[task.status || ''] || {
                    color: 'default',
                    label: task.status,
                  };
                  return (
                    <List.Item
                      style={{ cursor: 'pointer', padding: '10px 16px' }}
                      onClick={() => navigate('/renewals')}
                    >
                      <List.Item.Meta
                        title={
                          <Space size={8}>
                            <FileDoneOutlined style={{ color: 'var(--color-text-tertiary)' }} />
                            <span>{task.contractName || task.contractNo || '未命名合同'}</span>
                          </Space>
                        }
                        description={
                          <span style={{ fontSize: 12 }}>
                            计划续费 {dayjs(task.plannedRenewalDate).format('YYYY-MM-DD')}
                            <Tag color={cfg.color} style={{ marginInlineStart: 8 }}>
                              {cfg.label}
                            </Tag>
                          </span>
                        }
                      />
                    </List.Item>
                  );
                }}
              />
            )}
          </Card>
        </Col>
      </Row>
    </div>
  );
}
