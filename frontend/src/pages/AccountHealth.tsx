/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : AccountHealth.tsx
 * Date : 2026/07/27
 * Author : hiylo
 * Contact : hiylo@live.com
 */

import { useEffect, useState, useCallback, type ReactNode } from 'react';
import {
  App,
  Button,
  Card,
  Col,
  Empty,
  Input,
  Modal,
  Progress,
  Row,
  Space,
  Statistic,
  Table,
  Tag,
  Tooltip,
} from 'antd';
import {
  ReloadOutlined,
  SearchOutlined,
  CloudSyncOutlined,
  CheckCircleOutlined,
  WarningOutlined,
  DisconnectOutlined,
  TeamOutlined,
  HeartOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';
import { isImeComposing } from '../utils/imeHelpers';

/** 标签配置类型 */
interface TagConfig {
  label: string;
  color: string;
  icon?: ReactNode;
}

/** 账号健康度检测记录实体 (对齐 ScrmAccountHealthEntity) */
interface ScrmAccountHealth {
  id: string;
  accountId: string; // 账号 ID
  checkResult: string; // 检测结果: HEALTHY / OFFLINE / FROZEN / UNKNOWN / ERROR
  previousState?: string; // 检测前登录态
  currentState?: string; // 检测后登录态
  detail?: string; // 检测详情
  checkedAt?: string; // 检测时间
  createTime?: string;
}

/** 健康度统计 VO (对齐 AccountHealthStatsVo) */
interface AccountHealthStats {
  totalAccounts?: number; // 账号总数
  healthyCount?: number; // 健康账号数
  offlineCount?: number; // 离线账号数
  frozenCount?: number; // 冻结账号数 (视为警告)
  unhealthyCount?: number; // 不健康账号数 (离线 + 冻结)
  onlineRate?: number; // 在线率 0~1
}

/** 全量检测摘要 (对齐 ScrmAccountHealthService.CheckSummary) */
interface CheckSummary {
  checkedCount?: number; // 已检测账号数
  errorCount?: number; // 检测异常账号数
}

/** Spring Page 分页响应 */
interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/** 检测结果映射 (含图标与颜色) */
const checkResultConfig: Record<string, TagConfig> = {
  HEALTHY: { label: '健康', color: 'green', icon: <CheckCircleOutlined /> },
  OFFLINE: { label: '离线', color: 'default', icon: <DisconnectOutlined /> },
  FROZEN: { label: '冻结', color: 'orange', icon: <WarningOutlined /> },
  UNKNOWN: { label: '未知', color: 'blue', icon: <HeartOutlined /> },
  ERROR: { label: '异常', color: 'red', icon: <WarningOutlined /> },
};

/**
 * 渲染检测结果 Tag (带 fallback)
 */
const renderCheckResultTag = (value?: string) => {
  if (!value) return '-';
  const cfg = checkResultConfig[value] || { label: value, color: 'default' };
  return cfg.icon ? (
    <Tag color={cfg.color} icon={cfg.icon}>
      {cfg.label}
    </Tag>
  ) : (
    <Tag color={cfg.color}>{cfg.label}</Tag>
  );
};

/** 根据检测结果评分映射为健康度百分比 (用于 Progress 展示) */
const scoreFromResult = (result?: string): number => {
  switch (result) {
    case 'HEALTHY':
      return 100;
    case 'UNKNOWN':
      return 60;
    case 'FROZEN':
      return 40;
    case 'ERROR':
      return 20;
    case 'OFFLINE':
      return 0;
    default:
      return 0;
  }
};

/** 根据健康度返回颜色 */
const healthColor = (score: number): string => {
  if (score >= 80) return '#52c41a';
  if (score >= 50) return '#fa8c16';
  return '#ff4d4f';
};

/**
 * 账号健康度页面
 * 展示账号池健康度概览统计, 支持手动触发全量检测、
 * 查看离线账号列表与按账号 ID 查询历史检测记录。
 */
export default function AccountHealth() {
  const { message, modal } = App.useApp();
  // 统计数据
  const [stats, setStats] = useState<AccountHealthStats | null>(null);
  const [statsLoading, setStatsLoading] = useState(false);
  // 离线账号列表
  const [offlineList, setOfflineList] = useState<ScrmAccountHealth[]>([]);
  const [offlineLoading, setOfflineLoading] = useState(false);
  const [offlinePage, setOfflinePage] = useState(0);
  const [offlineSize, setOfflineSize] = useState(10);
  const [offlineTotal, setOfflineTotal] = useState(0);
  // 全量检测中
  const [checkingAll, setCheckingAll] = useState(false);
  // 单账号检测中
  const [checkingId, setCheckingId] = useState<string | null>(null);
  // 历史记录查询
  const [historySearchInput, setHistorySearchInput] = useState('');
  const [historyAccountId, setHistoryAccountId] = useState<string | null>(null);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [historyList, setHistoryList] = useState<ScrmAccountHealth[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyPage, setHistoryPage] = useState(0);
  const [historySize, setHistorySize] = useState(10);
  const [historyTotal, setHistoryTotal] = useState(0);
  // 最新健康度 (用于历史弹窗顶部摘要)
  const [latestHealth, setLatestHealth] = useState<ScrmAccountHealth | null>(null);

  /** 拉取健康度统计 */
  const fetchStats = useCallback(async () => {
    setStatsLoading(true);
    try {
      const data = await apiClient.get<AccountHealthStats>(
        '/scrm/accounts/health/stats',
      );
      setStats(data || null);
    } catch {
      // 错误已由 axios 拦截器统一提示
      setStats(null);
    } finally {
      setStatsLoading(false);
    }
  }, []);

  /** 拉取离线账号分页列表 */
  const fetchOffline = useCallback(async () => {
    setOfflineLoading(true);
    try {
      const params = new URLSearchParams();
      params.set('page', String(offlinePage));
      params.set('size', String(offlineSize));
      const data = await apiClient.get<Page<ScrmAccountHealth>>(
        `/scrm/accounts/health/offline?${params.toString()}`,
      );
      setOfflineList(data.content || []);
      setOfflineTotal(data.totalElements || 0);
    } catch {
      // 错误已由拦截器提示
      setOfflineList([]);
      setOfflineTotal(0);
    } finally {
      setOfflineLoading(false);
    }
  }, [offlinePage, offlineSize]);

  useEffect(() => {
    fetchStats();
  }, [fetchStats]);

  useEffect(() => {
    fetchOffline();
  }, [fetchOffline]);

  /** 全量检测所有账号 (带二次确认) */
  const handleCheckAll = () => {
    modal.confirm({
      title: '全量健康检测',
      content: '将对所有账号执行健康检测, 可能需要一些时间, 是否继续?',
      okText: '开始检测',
      cancelText: '取消',
      onOk: async () => {
        setCheckingAll(true);
        try {
          const summary = await apiClient.post<CheckSummary>(
            '/scrm/accounts/health/check-all',
          );
          const checked = summary?.checkedCount ?? 0;
          const error = summary?.errorCount ?? 0;
          message.success(`检测完成: 已检测 ${checked} 个账号, 异常 ${error} 个`);
          // 刷新统计与离线列表
          fetchStats();
          fetchOffline();
        } catch {
          // 错误已由拦截器提示
        } finally {
          setCheckingAll(false);
        }
      },
    });
  };

  /** 单个账号健康检测 */
  const handleCheckOne = async (accountId: string) => {
    setCheckingId(accountId);
    try {
      await apiClient.post<ScrmAccountHealth>(
        `/scrm/accounts/health/check/${accountId}`,
      );
      message.success(`账号 #${accountId} 检测完成`);
      // 若该账号当前在离线列表中, 刷新列表
      fetchOffline();
      fetchStats();
    } catch {
      // 错误已由拦截器提示
    } finally {
      setCheckingId(null);
    }
  };

  /** 拉取指定账号的健康度历史 (并获取最新一条用于摘要) */
  const fetchHistory = useCallback(async () => {
    if (historyAccountId == null) return;
    setHistoryLoading(true);
    try {
      const params = new URLSearchParams();
      params.set('page', String(historyPage));
      params.set('size', String(historySize));
      const [historyData, latestData] = await Promise.all([
        apiClient.get<Page<ScrmAccountHealth>>(
          `/scrm/accounts/health/${historyAccountId}/history?${params.toString()}`,
        ),
        apiClient.get<ScrmAccountHealth>(
          `/scrm/accounts/health/${historyAccountId}/latest`,
        ),
      ]);
      setHistoryList(historyData.content || []);
      setHistoryTotal(historyData.totalElements || 0);
      setLatestHealth(latestData || null);
    } catch {
      // 错误已由拦截器提示
      setHistoryList([]);
      setHistoryTotal(0);
      setLatestHealth(null);
    } finally {
      setHistoryLoading(false);
    }
  }, [historyAccountId, historyPage, historySize]);

  useEffect(() => {
    if (historyOpen && historyAccountId != null) {
      fetchHistory();
    }
  }, [historyOpen, historyAccountId, fetchHistory]);

  /** 点击查询历史: 解析账号 ID 并打开弹窗 */
  const handleSearchHistory = () => {
    const trimmed = historySearchInput.trim();
    if (!trimmed) {
      message.warning('请输入账号 ID');
      return;
    }
    setHistoryAccountId(trimmed);
    setHistoryPage(0);
    setHistoryOpen(true);
  };

  /** 离线账号表格列定义 */
  const offlineColumns: ColumnsType<ScrmAccountHealth> = [
    {
      title: '账号 ID',
      dataIndex: 'accountId',
      key: 'accountId',
      width: 110,
      render: (value: string) => `#${value}`,
    },
    {
      title: '健康度评分',
      key: 'healthScore',
      width: 200,
      render: (_, record) => {
        const score = scoreFromResult(record.checkResult);
        return (
          <Progress
            percent={score}
            size="small"
            strokeColor={healthColor(score)}
            format={() => record.checkResult || '-'}
          />
        );
      },
    },
    {
      title: '状态',
      dataIndex: 'checkResult',
      key: 'checkResult',
      width: 120,
      render: (value: string) => renderCheckResultTag(value),
    },
    {
      title: '最后检查时间',
      dataIndex: 'checkedAt',
      key: 'checkedAt',
      width: 180,
      render: (value?: string) =>
        value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: '检测详情',
      dataIndex: 'detail',
      key: 'detail',
      ellipsis: { showTitle: false },
      render: (value?: string) =>
        value ? (
          <Tooltip title={value} overlayStyle={{ maxWidth: 600 }}>
            <span>{value}</span>
          </Tooltip>
        ) : (
          '-'
        ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 130,
      fixed: 'right',
      render: (_, record) => (
        <Button
          type="link"
          size="small"
          loading={checkingId === record.accountId}
          onClick={() => handleCheckOne(record.accountId)}
        >
          重新检测
        </Button>
      ),
    },
  ];

  /** 历史记录表格列定义 */
  const historyColumns: ColumnsType<ScrmAccountHealth> = [
    {
      title: '检测结果',
      dataIndex: 'checkResult',
      key: 'checkResult',
      width: 120,
      render: (value: string) => renderCheckResultTag(value),
    },
    {
      title: '检测前状态',
      dataIndex: 'previousState',
      key: 'previousState',
      width: 120,
      render: (value?: string) => value || '-',
    },
    {
      title: '检测后状态',
      dataIndex: 'currentState',
      key: 'currentState',
      width: 120,
      render: (value?: string) => value || '-',
    },
    {
      title: '检测时间',
      dataIndex: 'checkedAt',
      key: 'checkedAt',
      width: 180,
      render: (value?: string) =>
        value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: '检测详情',
      dataIndex: 'detail',
      key: 'detail',
      ellipsis: { showTitle: false },
      render: (value?: string) =>
        value ? (
          <Tooltip title={value} overlayStyle={{ maxWidth: 600 }}>
            <span>{value}</span>
          </Tooltip>
        ) : (
          '-'
        ),
    },
  ];

  // 在线率百分比 (0~1 -> 0~100, 保留 1 位小数)
  const onlineRatePercent =
    stats?.onlineRate != null
      ? Math.round(stats.onlineRate * 1000) / 10
      : undefined;

  return (
    <div>
      {/* 顶部统计卡片 */}
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} sm={12} md={6}>
          <Card loading={statsLoading}>
            <Statistic
              title="账号总数"
              value={stats?.totalAccounts ?? 0}
              prefix={<TeamOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card loading={statsLoading}>
            <Statistic
              title="健康账号"
              value={stats?.healthyCount ?? 0}
              valueStyle={{ color: '#52c41a' }}
              prefix={<CheckCircleOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card loading={statsLoading}>
            <Statistic
              title="警告账号 (冻结)"
              value={stats?.frozenCount ?? 0}
              valueStyle={{ color: '#fa8c16' }}
              prefix={<WarningOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card loading={statsLoading}>
            <Statistic
              title="离线账号"
              value={stats?.offlineCount ?? 0}
              valueStyle={{ color: '#ff4d4f' }}
              prefix={<DisconnectOutlined />}
            />
          </Card>
        </Col>
      </Row>

      {/* 在线率提示 + 操作栏 + 历史搜索 */}
      <Card style={{ marginBottom: 16 }}>
        <Row gutter={[16, 16]} align="middle">
          <Col xs={24} sm={12} md={8}>
            {onlineRatePercent != null && (
              <Statistic
                title="当前在线率"
                value={onlineRatePercent}
                precision={1}
                suffix="%"
                valueStyle={{
                  color: onlineRatePercent >= 80 ? '#52c41a' : '#fa8c16',
                }}
              />
            )}
          </Col>
          <Col xs={24} sm={12} md={8}>
            <Input
              placeholder="输入账号 ID 查询健康度历史"
              allowClear
              value={historySearchInput}
              onChange={(e) => setHistorySearchInput(e.target.value)}
              onPressEnter={(e) => { if (!isImeComposing(e)) handleSearchHistory(); }}
            />
          </Col>
          <Col flex="auto">
            <Space style={{ float: 'right' }}>
              <Button icon={<ReloadOutlined />} onClick={() => { fetchStats(); fetchOffline(); }}>
                刷新
              </Button>
              <Button
                type="primary"
                icon={<CloudSyncOutlined />}
                loading={checkingAll}
                onClick={handleCheckAll}
              >
                检查所有账号
              </Button>
              <Button
                icon={<SearchOutlined />}
                onClick={handleSearchHistory}
              >
                查询历史
              </Button>
            </Space>
          </Col>
        </Row>
      </Card>

      {/* 离线账号列表 */}
      <Card title="离线账号列表">
        <Table<ScrmAccountHealth>
          rowKey="id"
          columns={offlineColumns}
          dataSource={offlineList}
          loading={offlineLoading}
          scroll={{ x: 980 }}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="暂无离线账号, 一切正常"
                style={{ padding: 32 }}
              />
            ),
          }}
          pagination={{
            current: offlinePage + 1,
            pageSize: offlineSize,
            total: offlineTotal,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p, s) => {
              setOfflinePage(p - 1);
              setOfflineSize(s);
            },
          }}
        />
      </Card>

      {/* 历史记录弹窗 */}
      <Modal
        title={`账号 #${historyAccountId ?? ''} 健康度历史`}
        open={historyOpen}
        onCancel={() => setHistoryOpen(false)}
        width={820}
        footer={[
          <Button key="close" onClick={() => setHistoryOpen(false)}>
            关闭
          </Button>,
          <Button
            key="recheck"
            type="primary"
            icon={<CloudSyncOutlined />}
            disabled={historyAccountId == null}
            loading={historyAccountId != null && checkingId === historyAccountId}
            onClick={() => historyAccountId != null && handleCheckOne(historyAccountId)}
          >
            重新检测此账号
          </Button>,
        ]}
      >
        {/* 最新健康度摘要 */}
        {latestHealth ? (
          <Card size="small" style={{ marginBottom: 16 }}>
            <Row gutter={16} align="middle">
              <Col span={8}>
                <Statistic
                  title="最新检测结果"
                  formatter={() => renderCheckResultTag(latestHealth.checkResult)}
                />
              </Col>
              <Col span={10}>
                <div style={{ fontSize: 12, color: '#999', marginBottom: 4 }}>
                  最后检查时间
                </div>
                <div>
                  {latestHealth.checkedAt
                    ? dayjs(latestHealth.checkedAt).format('YYYY-MM-DD HH:mm:ss')
                    : '-'}
                </div>
              </Col>
              <Col span={6}>
                <div style={{ fontSize: 12, color: '#999', marginBottom: 4 }}>
                  健康度评分
                </div>
                <Progress
                  percent={scoreFromResult(latestHealth.checkResult)}
                  size="small"
                  strokeColor={healthColor(scoreFromResult(latestHealth.checkResult))}
                />
              </Col>
            </Row>
          </Card>
        ) : null}

        <Table<ScrmAccountHealth>
          rowKey="id"
          columns={historyColumns}
          dataSource={historyList}
          loading={historyLoading}
          scroll={{ x: 720 }}
          size="small"
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="该账号暂无历史检测记录"
              />
            ),
          }}
          pagination={{
            current: historyPage + 1,
            pageSize: historySize,
            total: historyTotal,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p, s) => {
              setHistoryPage(p - 1);
              setHistorySize(s);
            },
          }}
        />
      </Modal>
    </div>
  );
}
