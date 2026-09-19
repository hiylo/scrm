/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Dashboard.tsx
 * Date : 2026/07/26
 * Author : hiylo
 * Contact : hiylo@live.com
 */

import { useEffect, useState, useCallback, useRef } from 'react';
import { Alert, Card, Col, Row, Statistic, Result, Tabs, Radio, Switch, Tooltip, Skeleton, Badge, Tag, List, Avatar, Empty, Button } from 'antd';
import {
  TeamOutlined,
  MessageOutlined,
  NotificationOutlined,
  WarningOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import ReactECharts from 'echarts-for-react';
import dayjs from 'dayjs';
import type { EChartsOption } from 'echarts';
import { apiClient } from '../api/client';
import { useScrmWebSocket } from '../hooks/useScrmWebSocket';
import { recordToPieData } from '../utils/chart';

/** 账号概览 */
interface AccountOverview {
  totalAccounts: number;
  platformDistribution: Record<string, number>;
  loginStateDistribution: Record<string, number>;
  onlineRate: number;
  unhealthyCount: number;
  totalHealthChecks: number;
}

/** 营销任务概览 */
interface CampaignOverview {
  totalCampaigns: number;
  statusDistribution: Record<string, number>;
  recentTrend: Array<{ date: string; count: number }>;
}

/** 客户概览 */
interface CustomerOverview {
  totalCustomers: number;
  lifecycleDistribution: Record<string, number>;
  recentNewCustomers: Array<{ date: string; count: number }>;
}

/** 会话概览 */
interface ConversationOverview {
  totalConversations: number;
  totalMessages: number;
  recentMessages: Array<{ date: string; count: number }>;
  activeConversations: number;
}

/** 风控概览 */
interface RiskOverview {
  totalRiskSignals: number;
  riskLevelDistribution: Record<string, number>;
  signalTypeDistribution: Record<string, number>;
  recentTrend: Array<{ date: string; count: number }>;
}

/** 看板概览数据 */
interface DashboardOverview {
  accountOverview: AccountOverview;
  campaignOverview: CampaignOverview;
  customerOverview: CustomerOverview;
  conversationOverview: ConversationOverview;
  riskOverview: RiskOverview;
}

/** 趋势数据 */
interface DashboardTrend {
  metric: string;
  days: number;
  startDate: string;
  endDate: string;
  totalCount: number;
  trend: Array<{ date: string; count: number }>;
  dailyAverage: number;
  peakValue: number;
  peakDate: string;
}

/** 趋势指标类型 */
type TrendMetric = 'customers' | 'campaigns' | 'messages' | 'risk';

/** 跟进提醒项 (仪表盘展示) */
interface FollowUpItem {
  id: string;
  nickname?: string;
  platformType: string;
  platformCustomerUid: string;
  lifecycle?: string;
  nextFollowUpAt?: string;
  remark?: string;
}

/** 趋势指标 Tab 配置 */
const metricTabItems: Array<{ key: TrendMetric; label: string }> = [
  { key: 'customers', label: '客户增长' },
  { key: 'campaigns', label: '营销任务' },
  { key: 'messages', label: '消息量' },
  { key: 'risk', label: '风控信号' },
];

/** 趋势指标对应系列名 (用于图表展示) */
const metricSeriesName: Record<TrendMetric, string> = {
  customers: '客户增长',
  campaigns: '营销任务',
  messages: '消息量',
  risk: '风控信号',
};

/** 趋势指标对应主色 */
const metricColor: Record<TrendMetric, string> = {
  customers: '#6366f1',
  campaigns: '#f59e0b',
  messages: '#10b981',
  risk: '#ef4444',
};

/** 自动刷新间隔 (ms) */
const AUTO_REFRESH_INTERVAL = 60_000;

/** 统计卡片高亮动画持续时长 (ms) */
const HIGHLIGHT_DURATION = 1500;

/** 平台类型 → 中文标签 */
const platformLabels: Record<string, string> = {
  wework: '企业微信',
};

/** 登录态 → 中文标签 */
const loginStateLabels: Record<string, string> = {
  LOGIN: '已登录',
  LOGOUT: '已登出',
  FROZEN: '已冻结',
  UNKNOWN: '未知',
};

/** 客户生命周期 → 中文标签 (与后端 ScrmCustomerDto @Pattern 保持一致) */
const lifecycleLabels: Record<string, string> = {
  NEW: '新客户',
  PROSPECT: '意向',
  ACTIVE: '活跃',
  DORMANT: '沉睡',
  CHURNED: '流失',
  CONVERTED: '已转化',
};

/** 风控信号类型 → 中文标签 */
const signalTypeLabels: Record<string, string> = {
  FREQUENCY: '频率异常',
  CONTENT: '内容违规',
  BEHAVIOR: '行为异常',
  TIME: '时间异常',
  CONTACT: '联系人异常',
};

/**
 * 运营概览页面
 * 展示核心指标卡片 + 客户增长趋势 + 风险等级分布
 * 支持指标切换、天数选择、自动刷新
 */
export default function Dashboard() {
  const navigate = useNavigate();
  const [overview, setOverview] = useState<DashboardOverview | null>(null);
  const [trend, setTrend] = useState<DashboardTrend | null>(null);
  const [loading, setLoading] = useState(true);
  const [trendLoading, setTrendLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  /** 跟进提醒客户列表 (逾期 + 24h 内到期) */
  const [followUps, setFollowUps] = useState<FollowUpItem[]>([]);
  /** 跟进提醒接口错误 (与看板主体错误分开, 只在该卡片内提示, 不整页降级) */
  const [followUpError, setFollowUpError] = useState<Error | null>(null);

  // 趋势指标与天数
  const [metric, setMetric] = useState<TrendMetric>('customers');
  const [days, setDays] = useState<number>(7);

  // 自动刷新
  const [autoRefresh, setAutoRefresh] = useState(false);
  const [lastRefreshed, setLastRefreshed] = useState<dayjs.Dayjs | null>(null);

  // WebSocket 实时推送: 看板统计增量更新
  const [wsToken, setWsToken] = useState<string | null>(null);
  // 各统计卡片的高亮状态 (key: statType, value: 是否高亮中)
  const [highlight, setHighlight] = useState<Record<string, boolean>>({});

  useEffect(() => {
    // 从 localStorage 读取 token, 用于 WebSocket 握手认证
    const t = localStorage.getItem('scrm_token');
    setWsToken(t);
  }, []);

  /** 收到看板统计更新通知时, 增量递增对应计数并触发高亮动画 (无需整页刷新) */
  const handleDashboardStatUpdate = useCallback((statType: string) => {
    // 增量递增对应统计计数, 仅修改相关字段, 其余保持引用稳定
    setOverview(prev => {
      if (!prev) return prev;
      if (statType === 'customers') {
        // 新客户: 客户总数 +1 (今日新增明细由后端 recentNewCustomers 提供, 此处仅增量更新总数)
        return {
          ...prev,
          customerOverview: {
            ...prev.customerOverview,
            totalCustomers: prev.customerOverview.totalCustomers + 1,
          },
        };
      }
      if (statType === 'messages') {
        // 新消息: 消息总量 +1
        return {
          ...prev,
          conversationOverview: {
            ...prev.conversationOverview,
            totalMessages: prev.conversationOverview.totalMessages + 1,
          },
        };
      }
      if (statType === 'conversations') {
        // 新会话: 会话总数 +1
        return {
          ...prev,
          conversationOverview: {
            ...prev.conversationOverview,
            totalConversations: prev.conversationOverview.totalConversations + 1,
          },
        };
      }
      return prev;
    });
    // 触发对应卡片的视觉高亮动画
    setHighlight(prev => ({ ...prev, [statType]: true }));
    // 清除上一个高亮定时器, 避免快速连续触发时定时器堆积
    if (highlightTimerRef.current) {
      clearTimeout(highlightTimerRef.current);
    }
    highlightTimerRef.current = setTimeout(() => {
      setHighlight(prev => ({ ...prev, [statType]: false }));
      highlightTimerRef.current = null;
    }, HIGHLIGHT_DURATION);
  }, []);

  // 监听 DASHBOARD_STAT_UPDATE 通知, 实时递增看板统计
  const { connected: wsConnected } = useScrmWebSocket({
    token: wsToken,
    onNotification: (notif) => {
      if (notif.type === 'DASHBOARD_STAT_UPDATE') {
        const statType = notif.data?.statType as string | undefined;
        if (statType) {
          handleDashboardStatUpdate(statType);
        }
      }
    },
  });

  /** 加载概览数据 */
  const loadOverview = useCallback(async () => {
    try {
      const data = await apiClient.get<DashboardOverview>('/scrm/dashboard/overview');
      setOverview(data);
    } catch (err) {
      setError(err instanceof Error ? err : new Error('加载失败'));
    }
  }, []);

  /** 加载趋势数据 (按当前 metric 和 days) */
  const loadTrend = useCallback(async (m: TrendMetric, d: number) => {
    setTrendLoading(true);
    try {
      const data = await apiClient.get<DashboardTrend>(
        `/scrm/dashboard/trend?metric=${m}&days=${d}`,
      );
      setTrend(data);
    } catch {
      // 趋势接口失败时保留旧数据, 不影响概览展示
    } finally {
      setTrendLoading(false);
    }
  }, []);

  /**
   * 加载跟进提醒列表
   * 该接口属于看板的附属数据, 失败时只记录 followUpError 供卡片内提示,
   * 既不整页降级, 也不静默留空 (永远 resolve, 便于并入 Promise.all)
   */
  const loadFollowUps = useCallback(async () => {
    try {
      const data = await apiClient.get<FollowUpItem[]>('/scrm/dashboard/follow-ups?limit=10');
      setFollowUps(data || []);
      setFollowUpError(null);
    } catch (err) {
      setFollowUps([]);
      setFollowUpError(err instanceof Error ? err : new Error('加载失败'));
    }
  }, []);

  /** 一次性加载概览 + 趋势 + 跟进提醒 */
  const loadAll = useCallback(async (m: TrendMetric, d: number) => {
    setLoading(true);
    setError(null);
    try {
      const [overviewData, trendData] = await Promise.all([
        apiClient.get<DashboardOverview>('/scrm/dashboard/overview'),
        apiClient.get<DashboardTrend>(`/scrm/dashboard/trend?metric=${m}&days=${d}`),
        loadFollowUps(),
      ]);
      setOverview(overviewData);
      setTrend(trendData);
      setLastRefreshed(dayjs());
    } catch (err) {
      setError(err instanceof Error ? err : new Error('加载失败'));
    } finally {
      setLoading(false);
    }
  }, [loadFollowUps]);

  // 首次加载
  useEffect(() => {
    loadAll(metric, days);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 切换指标或天数时, 仅重新加载趋势
  useEffect(() => {
    if (loading) return;
    loadTrend(metric, days);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [metric, days]);

  // 自动刷新
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  // 高亮动画定时器 (避免组件卸载后 setState)
  const highlightTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => {
    if (timerRef.current) {
      clearInterval(timerRef.current);
      timerRef.current = null;
    }
    if (autoRefresh) {
      timerRef.current = setInterval(() => {
        loadOverview();
        loadTrend(metric, days);
        loadFollowUps();
        setLastRefreshed(dayjs());
      }, AUTO_REFRESH_INTERVAL);
    }
    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [autoRefresh, metric, days]);

  // 组件卸载时清除高亮定时器, 防止内存泄漏
  useEffect(() => {
    return () => {
      if (highlightTimerRef.current) {
        clearTimeout(highlightTimerRef.current);
      }
    };
  }, []);

  /** 手动刷新 (含跟进提醒, 便于从局部失败中恢复) */
  const handleManualRefresh = useCallback(() => {
    loadOverview();
    loadTrend(metric, days);
    loadFollowUps();
    setLastRefreshed(dayjs());
  }, [loadOverview, loadTrend, loadFollowUps, metric, days]);

  // 加载中 (Skeleton 骨架屏)
  if (loading) {
    return (
      <div>
        <Row gutter={[16, 16]}>
          {[0, 1, 2, 3].map(i => (
            <Col xs={24} sm={12} lg={6} key={i}>
              <Card>
                <Skeleton active paragraph={{ rows: 1 }} />
              </Card>
            </Col>
          ))}
        </Row>
        <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
          <Col xs={24} lg={16}>
            <Card title="客户增长趋势">
              <Skeleton active paragraph={{ rows: 8 }} />
            </Card>
          </Col>
          <Col xs={24} lg={8}>
            <Card title="风险等级分布">
              <Skeleton active paragraph={{ rows: 8 }} />
            </Card>
          </Col>
        </Row>
      </div>
    );
  }

  // 请求真的失败 (概览或趋势接口 reject) → 整页错误页, 并提供重试
  if (error) {
    return (
      <Result
        status="error"
        title="数据加载失败"
        subTitle={error.message || '请稍后重试'}
        extra={
          <Button type="primary" icon={<ReloadOutlined />} onClick={() => loadAll(metric, days)}>
            重试
          </Button>
        }
      />
    );
  }

  // 请求成功但没有任何概览数据 (后端返回 null / 空对象) → 正常空态, 不误报为加载失败
  if (!overview || Object.keys(overview).length === 0) {
    return (
      <Card>
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无看板数据">
          <Button icon={<ReloadOutlined />} onClick={() => loadAll(metric, days)}>
            刷新
          </Button>
        </Empty>
      </Card>
    );
  }

  // 客户增长趋势折线图配置 (含 tooltip 和平均值 markLine)
  const trendOption: EChartsOption = {
    tooltip: {
      trigger: 'axis',
      formatter: (params: unknown) => {
        const arr = params as Array<{ axisValue: string; data: number; seriesName: string }>;
        if (!Array.isArray(arr) || arr.length === 0) return '';
        const item = arr[0];
        return `${item.axisValue}<br/>${item.seriesName}: ${item.data}`;
      },
    },
    grid: { left: 40, right: 20, top: 40, bottom: 40 },
    xAxis: {
      type: 'category',
      data: (trend?.trend || []).map(item => dayjs(item.date).format('MM-DD')),
      axisLine: { lineStyle: { color: '#94a3b8' } },
    },
    yAxis: {
      type: 'value',
      axisLine: { lineStyle: { color: '#94a3b8' } },
      // 分割线使用半透明颜色, 亮色/暗色模式下均可见
      splitLine: { lineStyle: { color: 'rgba(128, 128, 128, 0.15)' } },
    },
    series: [
      {
        name: metricSeriesName[metric],
        type: 'line',
        smooth: true,
        data: (trend?.trend || []).map(item => item.count),
        itemStyle: { color: metricColor[metric] },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: `${metricColor[metric]}59` },
              { offset: 1, color: `${metricColor[metric]}05` },
            ],
          },
        },
        markLine: {
          silent: true,
          symbol: 'none',
          lineStyle: { type: 'dashed', color: '#94a3b8' },
          label: { formatter: '平均: {c}', position: 'insideEndTop' },
          data: [{ type: 'average', name: '平均值' }],
        },
      },
    ],
  };

  // 风险等级分布饼图配置
  const riskOption: EChartsOption = {
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, textStyle: { color: '#64748b' } },
    series: [
      {
        name: '风险等级',
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        label: { show: false, position: 'center' },
        emphasis: {
          label: { show: true, fontSize: 18, fontWeight: 'bold' },
        },
        labelLine: { show: false },
        data: [
          { value: overview?.riskOverview?.riskLevelDistribution?.['CRITICAL'] || 0, name: '严重', itemStyle: { color: '#7f1d1d' } },
          { value: overview?.riskOverview?.riskLevelDistribution?.['HIGH'] || 0, name: '高风险', itemStyle: { color: '#ef4444' } },
          { value: overview?.riskOverview?.riskLevelDistribution?.['MEDIUM'] || 0, name: '中风险', itemStyle: { color: '#f59e0b' } },
          { value: overview?.riskOverview?.riskLevelDistribution?.['LOW'] || 0, name: '低风险', itemStyle: { color: '#10b981' } },
        ],
      },
    ],
  };

  // 平台分布饼图配置 (环形)
  const platformOption: EChartsOption = {
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, textStyle: { color: '#64748b' } },
    color: ['#6366f1', '#10b981', '#f59e0b', '#ef4444', '#3b82f6', '#8b5cf6'],
    series: [
      {
        name: '平台分布',
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        label: { show: false, position: 'center' },
        emphasis: {
          label: { show: true, fontSize: 14, fontWeight: 'bold' },
        },
        labelLine: { show: false },
        data: recordToPieData(
          overview?.accountOverview?.platformDistribution,
          platformLabels,
        ),
      },
    ],
  };

  // 账号登录态分布饼图配置 (环形)
  const loginStateOption: EChartsOption = {
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, textStyle: { color: '#64748b' } },
    color: ['#10b981', '#94a3b8', '#ef4444', '#cbd5e1'],
    series: [
      {
        name: '登录态',
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        label: { show: false, position: 'center' },
        emphasis: {
          label: { show: true, fontSize: 14, fontWeight: 'bold' },
        },
        labelLine: { show: false },
        data: recordToPieData(
          overview?.accountOverview?.loginStateDistribution,
          loginStateLabels,
        ),
      },
    ],
  };

  // 客户生命周期分布条形图配置 (横向)
  const lifecycleOption: EChartsOption = {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 70, right: 20, top: 20, bottom: 20 },
    xAxis: { type: 'value' },
    yAxis: {
      type: 'category',
      data: Object.entries(
        overview?.customerOverview?.lifecycleDistribution || {},
      ).map(([key]) => lifecycleLabels[key] || key),
    },
    series: [
      {
        name: '客户数',
        type: 'bar',
        data: Object.values(
          overview?.customerOverview?.lifecycleDistribution || {},
        ),
        itemStyle: { color: '#6366f1' },
      },
    ],
  };

  // 风控信号类型分布饼图配置 (环形)
  const signalTypeOption: EChartsOption = {
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, textStyle: { color: '#64748b' } },
    color: ['#ef4444', '#f59e0b', '#8b5cf6', '#3b82f6', '#10b981'],
    series: [
      {
        name: '信号类型',
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        label: { show: false, position: 'center' },
        emphasis: {
          label: { show: true, fontSize: 14, fontWeight: 'bold' },
        },
        labelLine: { show: false },
        data: recordToPieData(
          overview?.riskOverview?.signalTypeDistribution,
          signalTypeLabels,
        ),
      },
    ],
  };

  /** 可点击的统计卡片 (highlighted 为真时展示高亮动画) */
  const renderStatCard = (
    title: string,
    value: number,
    icon: React.ReactNode,
    color: string,
    footer: React.ReactNode,
    targetPath: string,
    highlighted?: boolean,
  ) => (
    <Card
      hoverable
      onClick={() => navigate(targetPath)}
      className={`stat-card${highlighted ? ' stat-card-highlight' : ''}`}
    >
      <div className="stat-card-body">
        <div className="stat-card-icon" style={{ background: `${color}1a`, color }}>
          {icon}
        </div>
        <div className="stat-card-content">
          <Statistic
            title={title}
            value={value}
            valueStyle={{ color }}
          />
          <div className="stat-card-footer">{footer}</div>
        </div>
      </div>
    </Card>
  );

  return (
    <div>
      {/* 顶部统计卡片 */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} lg={6}>
          {renderStatCard(
            '客户总数',
            overview?.customerOverview?.totalCustomers,
            <TeamOutlined />,
            '#6366f1',
            <>今日新增 {overview?.customerOverview?.recentNewCustomers?.[(overview?.customerOverview?.recentNewCustomers?.length || 0) - 1]?.count || 0} 人</>,
            '/customers',
            highlight['customers'],
          )}
        </Col>
        <Col xs={24} sm={12} lg={6}>
          {renderStatCard(
            '会话总数',
            overview?.conversationOverview?.totalConversations,
            <MessageOutlined />,
            '#10b981',
            <>
              <span>活跃会话 {overview?.conversationOverview?.activeConversations || 0} 个</span>
              {/* totalMessages 会随 WS 推送自增, 展示出来才有实时反馈 */}
              <span style={{ marginLeft: 8 }}>
                累计消息 {overview?.conversationOverview?.totalMessages || 0} 条
              </span>
            </>,
            '/conversations',
            // 新会话 / 新消息均高亮会话卡片 (消息归属会话语境)
            highlight['conversations'] || highlight['messages'],
          )}
        </Col>
        <Col xs={24} sm={12} lg={6}>
          {renderStatCard(
            '营销任务',
            overview?.campaignOverview?.totalCampaigns,
            <NotificationOutlined />,
            '#f59e0b',
            <>运行中 {overview?.campaignOverview?.statusDistribution?.['RUNNING'] || 0} 个</>,
            '/campaigns',
            // 营销推送兼容 marketing / campaigns 两种 statType 命名
            highlight['marketing'] || highlight['campaigns'],
          )}
        </Col>
        <Col xs={24} sm={12} lg={6}>
          {renderStatCard(
            '风控信号',
            overview?.riskOverview?.totalRiskSignals,
            <WarningOutlined />,
            '#ef4444',
            <>高风险 {overview?.riskOverview?.riskLevelDistribution?.['HIGH'] || 0} 个</>,
            '/risk-rules',
            highlight['risk'],
          )}
        </Col>
      </Row>

      {/* 趋势图与风险分布 (左右 2:1 布局) */}
      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={16}>
          <Card
            title="趋势分析"
            extra={
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
                {/* 时间范围选择器: 控制趋势数据天数, 切换后通过 useEffect 自动重新加载趋势 */}
                <Radio.Group
                  size="small"
                  buttonStyle="solid"
                  value={days}
                  onChange={e => setDays(Number(e.target.value))}
                  optionType="button"
                  options={[
                    { label: '7天', value: 7 },
                    { label: '14天', value: 14 },
                    { label: '30天', value: 30 },
                    { label: '90天', value: 90 },
                  ]}
                />
                <Tooltip title="手动刷新">
                  <ReloadOutlined
                    onClick={handleManualRefresh}
                    style={{ cursor: 'pointer', color: 'var(--color-primary)' }}
                  />
                </Tooltip>
              </div>
            }
          >
            <Tabs
              activeKey={metric}
              onChange={k => setMetric(k as TrendMetric)}
              items={metricTabItems.map(t => ({ key: t.key, label: t.label }))}
              size="small"
            />
            <ReactECharts
              option={trendOption}
              style={{ height: 320 }}
              showLoading={trendLoading}
              notMerge={true}
            />
          </Card>
        </Col>
        <Col xs={24} lg={8}>
          <Card title="风险等级分布">
            <ReactECharts option={riskOption} style={{ height: 320 }} />
          </Card>
        </Col>
      </Row>

      {/* 跟进提醒: 展示逾期和即将到期的跟进客户 */}
      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={12}>
          <Card
            title={
              <span>
                <Badge count={followUps.length} offset={[8, -2]} size="small">
                  待跟进客户
                </Badge>
              </span>
            }
            size="small"
            extra={
              <Button size="small" type="link" onClick={() => navigate('/customers')}>
                查看全部
              </Button>
            }
          >
            {followUpError ? (
              /* 跟进接口失败: 明确告知局部降级, 而不是静默展示空列表 */
              <Alert
                type="warning"
                showIcon
                message="待跟进客户加载失败"
                description={followUpError.message || '暂时无法获取跟进提醒, 请稍后重试'}
              />
            ) : followUps.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无待跟进客户" />
            ) : (
              <List
                size="small"
                dataSource={followUps}
                renderItem={(item) => {
                  const followUpTime = item.nextFollowUpAt ? dayjs(item.nextFollowUpAt) : null;
                  const isOverdue = followUpTime && followUpTime.isBefore(dayjs());
                  const isUrgent = followUpTime && !isOverdue && followUpTime.isBefore(dayjs().add(2, 'hour'));
                  return (
                    <List.Item
                      actions={[
                        <Button
                          key="detail"
                          size="small"
                          type="link"
                          onClick={() => navigate(`/customers/${item.id}`)}
                        >
                          详情
                        </Button>,
                      ]}
                    >
                      <List.Item.Meta
                        avatar={
                          <Avatar size="small">
                            {item.nickname?.[0]?.toUpperCase() || '客'}
                          </Avatar>
                        }
                        title={
                          <span>
                            {item.nickname || `客户 ${item.id}`}
                            {/* 枚举统一走中文映射, 未命中时兜底展示原始值而不是 undefined */}
                            <Tag style={{ marginLeft: 8 }}>
                              {platformLabels[item.platformType] || item.platformType}
                            </Tag>
                            {item.lifecycle && (
                              <Tag color="blue">{lifecycleLabels[item.lifecycle] || item.lifecycle}</Tag>
                            )}
                          </span>
                        }
                        description={
                          <span>
                            {followUpTime && (
                              <span
                                style={{
                                  color: isOverdue
                                    ? 'var(--ant-color-error)'
                                    : isUrgent
                                      ? 'var(--ant-color-warning)'
                                      : 'var(--ant-color-text-secondary)',
                                  fontWeight: isOverdue ? 500 : 400,
                                }}
                              >
                                {isOverdue ? '逾期: ' : '跟进: '}
                                {followUpTime.format('MM-DD HH:mm')}
                              </span>
                            )}
                            {item.remark && (
                              <span style={{ marginLeft: 8, color: 'var(--ant-color-text-tertiary)' }}>
                                | {item.remark.length > 20 ? `${item.remark.slice(0, 20)}...` : item.remark}
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
        <Col xs={24} lg={12}>
          <Card title="近期消息趋势" size="small">
            <ReactECharts
              option={{
                tooltip: { trigger: 'axis' },
                grid: { left: 40, right: 20, top: 20, bottom: 30 },
                xAxis: {
                  type: 'category',
                  data: (overview?.conversationOverview?.recentMessages || []).map((d) => (d.date || '').slice(5)),
                },
                yAxis: { type: 'value', minInterval: 1 },
                series: [
                  {
                    name: '消息数',
                    type: 'line',
                    smooth: true,
                    data: (overview?.conversationOverview?.recentMessages || []).map((d) => d.count),
                    itemStyle: { color: '#6366f1' },
                    areaStyle: { opacity: 0.1 },
                  },
                ],
              }}
              style={{ height: 280 }}
            />
          </Card>
        </Col>
      </Row>

      {/* 数据分布 (4 个图表) */}
      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} sm={12} lg={6}>
          <Card title="平台分布" size="small">
            <ReactECharts option={platformOption} style={{ height: 240 }} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card title="账号登录态" size="small">
            <ReactECharts option={loginStateOption} style={{ height: 240 }} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card title="客户生命周期" size="small">
            <ReactECharts option={lifecycleOption} style={{ height: 240 }} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card title="风控信号类型" size="small">
            <ReactECharts option={signalTypeOption} style={{ height: 240 }} />
          </Card>
        </Col>
      </Row>

      {/* 自动刷新状态条 */}
      <div className="dashboard-footer">
        {/* WebSocket 实时连接状态指示 */}
        <Badge
          status={wsConnected ? 'success' : 'default'}
          text={wsConnected ? '实时' : '实时 (已断开)'}
          className="ws-status-badge"
        />
        <div className="auto-refresh">
          <span>自动刷新 (每 60 秒)</span>
          <Switch
            checked={autoRefresh}
            onChange={setAutoRefresh}
            size="small"
          />
        </div>
        {lastRefreshed && (
          <span className="last-refreshed">
            最后刷新: {lastRefreshed.format('YYYY-MM-DD HH:mm:ss')}
          </span>
        )}
      </div>

      {/* 内联样式 (仅当前组件使用) */}
      <style>{`
        .stat-card {
          cursor: pointer;
          transition: transform 0.2s, box-shadow 0.2s;
        }
        .stat-card:hover {
          transform: translateY(-2px);
          box-shadow: 0 6px 16px rgba(0, 0, 0, 0.08);
        }
        /* 实时更新触发的统计卡片高亮动画 */
        .stat-card-highlight {
          animation: stat-card-flash 1.5s ease-out;
        }
        @keyframes stat-card-flash {
          0% {
            box-shadow: 0 0 0 0 rgba(99, 102, 241, 0.55);
            transform: scale(1);
          }
          25% {
            box-shadow: 0 0 0 6px rgba(99, 102, 241, 0.18);
            transform: scale(1.03);
          }
          100% {
            box-shadow: 0 0 0 0 rgba(99, 102, 241, 0);
            transform: scale(1);
          }
        }
        .stat-card-body {
          display: flex;
          align-items: center;
          gap: 16px;
        }
        .stat-card-icon {
          width: 48px;
          height: 48px;
          border-radius: 12px;
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: 24px;
          flex-shrink: 0;
        }
        .stat-card-content {
          flex: 1;
          min-width: 0;
        }
        .stat-card-footer {
          margin-top: 4px;
          color: var(--color-text-tertiary);
          font-size: 12px;
        }
        .dashboard-footer {
          display: flex;
          align-items: center;
          justify-content: flex-end;
          gap: 16px;
          margin-top: 16px;
          color: var(--color-text-tertiary);
          font-size: 12px;
        }
        .auto-refresh {
          display: flex;
          align-items: center;
          gap: 8px;
        }
        .ws-status-badge {
          margin-right: auto;
        }
      `}</style>
    </div>
  );
}
