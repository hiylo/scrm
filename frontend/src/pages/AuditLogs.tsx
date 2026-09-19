/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : AuditLogs.tsx
 * Date : 2026/07/26
 * Author : hiylo
 * Contact : hiylo@live.com
 */

import { useEffect, useState, useCallback, useMemo } from 'react';
import {
  App,
  Button,
  Card,
  Col,
  DatePicker,
  Descriptions,
  Dropdown,
  Empty,
  Input,
  Modal,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { MenuProps } from 'antd';
import {
  ReloadOutlined,
  DownloadOutlined,
  EyeOutlined,
  DownOutlined,
  CopyOutlined,
  LoadingOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  BarChartOutlined,
  AuditOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { Dayjs } from 'dayjs';
import dayjs from 'dayjs';
import { apiClient, apiClientInstance } from '../api/client';
import { useDebounce } from '../hooks/useDebounce';

const { RangePicker } = DatePicker;
const { Paragraph, Text } = Typography;

/** 审计日志实体 */
interface ScrmAuditLog {
  id: string;
  userId?: string;
  username?: string;
  resource: string; // scrm_customer / scrm_campaign / scrm_account / scrm_risk_rule 等
  action: string; // create / update / delete / read / execute
  method: string; // GET / POST / PUT / DELETE
  requestUri: string;
  requestParams?: string;
  responseBody?: string;
  result: string; // SUCCESS / FAILURE
  errorMessage?: string;
  executionTime?: number | string; // 毫秒（后端 Long 类型被 Jackson ToStringSerializer 序列化为字符串）
  clientIp?: string;
  operatedAt: string;
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

/** 审计统计数据 (后端聚合返回) */
interface AuditStats {
  totalOperations: number; // 总操作数
  successCount: number; // 成功操作数
  failedCount: number; // 失败操作数
  successRate: number; // 成功率 (0~1)
  resourceDistribution: Record<string, number>; // 资源分布 (key=资源标识, value=数量)
}

/** 资源类型映射 */
const resourceConfig: Record<string, { label: string; color: string }> = {
  scrm_customer: { label: '客户', color: 'blue' },
  scrm_campaign: { label: '营销任务', color: 'purple' },
  scrm_account: { label: '账号', color: 'green' },
  scrm_risk_rule: { label: '风险规则', color: 'red' },
  scrm_message_template: { label: '消息模板', color: 'orange' },
  scrm_audit_log: { label: '审计日志', color: 'cyan' },
};

/** 结果映射 */
const resultConfig: Record<string, { label: string; color: string }> = {
  SUCCESS: { label: '成功', color: 'green' },
  FAILURE: { label: '失败', color: 'red' },
};

/** HTTP 方法映射 */
const methodConfig: Record<string, { label: string; color: string }> = {
  GET: { label: 'GET', color: 'blue' },
  POST: { label: 'POST', color: 'green' },
  PUT: { label: 'PUT', color: 'orange' },
  DELETE: { label: 'DELETE', color: 'red' },
};

/** 动作映射 */
const actionConfig: Record<string, { label: string; color: string }> = {
  create: { label: '创建', color: 'green' },
  update: { label: '更新', color: 'blue' },
  delete: { label: '删除', color: 'red' },
  read: { label: '查询', color: 'default' },
  execute: { label: '执行', color: 'purple' },
};

/** 资源类型下拉选项 */
const resourceOptions = Object.entries(resourceConfig).map(([value, cfg]) => ({
  value,
  label: cfg.label,
}));

/** 结果下拉选项 */
const resultOptions = Object.entries(resultConfig).map(([value, cfg]) => ({
  value,
  label: cfg.label,
}));

/**
 * 渲染标签 (带 fallback)
 */
const renderTag = (
  value: string,
  config: Record<string, { label: string; color: string }>,
) => {
  const cfg = config[value] || { label: value, color: 'default' };
  return <Tag color={cfg.color}>{cfg.label}</Tag>;
};

/**
 * 将字符串 JSON 格式化展示
 * 解析失败时返回原始字符串, 空值返回 '-'
 */
function formatJson(str?: string): string {
  if (!str) return '-';
  try {
    return JSON.stringify(JSON.parse(str), null, 2);
  } catch {
    return str;
  }
}

/**
 * 截取 URI 用于表格展示
 */
const truncateUri = (uri: string, max = 50): string => {
  if (!uri) return '-';
  return uri.length > max ? `${uri.slice(0, max)}...` : uri;
};

/**
 * 根据耗时返回颜色 (用于耗时列着色)
 * - > 1000ms 红色
 * - > 500ms 橙色
 * - 其他默认色
 */
function getExecutionTimeColor(value: number): string | undefined {
  if (value > 1000) return '#cf1322';
  if (value > 500) return '#fa8c16';
  return undefined;
}

/** 代码块 `<pre>` 样式 (含背景色 + 等宽字体) */
const codeBlockStyle = {
  margin: 0,
  padding: 12,
  background: 'rgba(0, 0, 0, 0.03)',
  borderRadius: 4,
  fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, "Liberation Mono", monospace',
  fontSize: 13,
  lineHeight: 1.5,
  color: 'rgba(0, 0, 0, 0.85)',
  maxHeight: 200,
  overflow: 'auto',
  whiteSpace: 'pre-wrap',
  wordBreak: 'break-all',
} as const;

/**
 * 审计日志页面
 * 支持分页查询 / 多条件筛选 / 时间范围 / 导出 (Excel/CSV) / 查看详情 / 统计卡片
 */
export default function AuditLogs() {
  const { message } = App.useApp();
  const [logs, setLogs] = useState<ScrmAuditLog[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [total, setTotal] = useState(0);
  // 搜索与筛选 (userId 为输入框值, debouncedUserId 为防抖后用于请求的值)
  const [userId, setUserId] = useState('');
  const debouncedUserId = useDebounce(userId, 300);
  const [resourceFilter, setResourceFilter] = useState<string | undefined>(undefined);
  const [resultFilter, setResultFilter] = useState<string | undefined>(undefined);
  // 时间范围筛选
  const [timeRange, setTimeRange] = useState<[Dayjs, Dayjs] | null>(null);
  // 详情弹窗
  const [detailOpen, setDetailOpen] = useState(false);
  const [currentLog, setCurrentLog] = useState<ScrmAuditLog | null>(null);
  // 导出中
  const [exporting, setExporting] = useState(false);
  // 统计概览 (后端聚合)
  const [stats, setStats] = useState<AuditStats | null>(null);
  const [statsLoading, setStatsLoading] = useState(false);

  /** 拉取审计日志分页列表 */
  const fetchLogs = useCallback(async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      params.set('page', String(page));
      params.set('size', String(size));
      if (debouncedUserId) params.set('userId', debouncedUserId);
      if (resourceFilter) params.set('resource', resourceFilter);
      if (resultFilter) params.set('result', resultFilter);
      if (timeRange && timeRange[0] && timeRange[1]) {
        params.set('startTime', timeRange[0].toISOString());
        params.set('endTime', timeRange[1].toISOString());
      }
      const data = await apiClient.get<Page<ScrmAuditLog>>(
        `/scrm/audit-logs?${params.toString()}`,
      );
      setLogs(data.content || []);
      setTotal(data.totalElements || 0);
    } catch {
      // 错误已由 axios 拦截器统一提示
      setLogs([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [page, size, debouncedUserId, resourceFilter, resultFilter, timeRange]);

  useEffect(() => {
    fetchLogs();
  }, [fetchLogs]);

  /** 拉取审计统计概览 (后端聚合) */
  const fetchStats = useCallback(async () => {
    setStatsLoading(true);
    try {
      const data = await apiClient.get<AuditStats>('/scrm/audit-logs/stats');
      setStats(data);
    } catch {
      // 错误已由 axios 拦截器统一提示
    } finally {
      setStatsLoading(false);
    }
  }, []);

  // 挂载时拉取统计概览
  useEffect(() => {
    fetchStats();
  }, [fetchStats]);

  // 防抖用户 ID 变化时, 重置到第一页
  useEffect(() => {
    setPage(0);
  }, [debouncedUserId]);

  /** 统计数据 (基于当前页日志) */
  const pageStats = useMemo(() => {
    const totalOps = logs.length;
    const success = logs.filter((l) => l.result === 'SUCCESS').length;
    const failure = logs.filter((l) => l.result === 'FAILURE').length;
    const times = logs
      .map((l) => l.executionTime)
      .filter((t): t is number | string => t !== undefined && t !== null)
      .map((t) => Number(t))
      .filter((t) => !isNaN(t));
    const avgTime =
      times.length > 0 ? Math.round(times.reduce((s, t) => s + t, 0) / times.length) : 0;
    return { totalOps, success, failure, avgTime };
  }, [logs]);

  /** 打开详情弹窗 */
  const openDetail = (record: ScrmAuditLog) => {
    setCurrentLog(record);
    setDetailOpen(true);
  };

  /** 复制文本到剪贴板 */
  const handleCopy = async (text?: string) => {
    if (!text) {
      message.warning('内容为空, 无法复制');
      return;
    }
    try {
      await navigator.clipboard.writeText(text);
      message.success('已复制到剪贴板');
    } catch {
      message.error('复制失败, 请手动选择文本复制');
    }
  };

  /** 导出审计日志为 CSV (客户端导出, 基于当前页数据) */
  const handleExportCsv = () => {
    if (!logs || logs.length === 0) {
      message.warning('暂无数据可导出');
      return;
    }
    setExporting(true);
    try {
      // CSV 表头 (包含所有数据字段)
      const headers = [
        '日志ID', '操作时间', '用户ID', '用户名', '资源', '动作', '方法',
        '请求URI', '请求参数', '响应体', '结果', '错误信息', '耗时(ms)',
        '客户端IP', '创建时间',
      ];
      // CSV 数据行
      const rows = logs.map(item => [
        item.id || '',
        item.operatedAt || '',
        item.userId || '',
        item.username || '',
        item.resource || '',
        item.action || '',
        item.method || '',
        item.requestUri || '',
        item.requestParams || '',
        item.responseBody || '',
        item.result || '',
        item.errorMessage || '',
        item.executionTime !== undefined && item.executionTime !== null
          ? String(item.executionTime) : '',
        item.clientIp || '',
        item.createTime || '',
      ]);
      // 组装 CSV 内容 (BOM 头确保 Excel 正确识别 UTF-8)
      const csvContent = '\uFEFF' + [headers, ...rows]
        .map(row => row.map(cell => `"${String(cell).replace(/"/g, '""')}"`).join(','))
        .join('\n');
      // 创建下载链接
      const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
      const link = document.createElement('a');
      link.href = URL.createObjectURL(blob);
      link.download = `audit_logs_${dayjs().format('YYYYMMDD_HHmmss')}.csv`;
      link.click();
      URL.revokeObjectURL(link.href);
      message.success(`已导出 ${logs.length} 条审计日志`);
    } catch {
      message.error('导出失败, 请重试');
    } finally {
      setExporting(false);
    }
  };

  /** 导出审计日志 (xlsx / csv) */
  const handleExport = async (format: 'xlsx' | 'csv') => {
    setExporting(true);
    try {
      // 注意: 导出必须用 apiClientInstance (axios 实例), 不能用 apiClient
      // 因为 apiClient 的 unwrap 会破坏 blob 响应
      const response = await apiClientInstance.get('/scrm/audit-logs/export', {
        params: {
          format,
          userId: debouncedUserId || undefined,
          resource: resourceFilter,
          result: resultFilter,
          startTime: timeRange && timeRange[0] ? timeRange[0].toISOString() : undefined,
          endTime: timeRange && timeRange[1] ? timeRange[1].toISOString() : undefined,
        },
        responseType: 'blob',
      });
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.download = `audit_logs_${new Date().toISOString().slice(0, 10)}.${
        format === 'xlsx' ? 'xlsx' : 'csv'
      }`;
      link.click();
      window.URL.revokeObjectURL(url);
      message.success(`已导出 ${format.toUpperCase()} 文件`);
    } catch {
      // 错误已由拦截器提示
    } finally {
      setExporting(false);
    }
  };

  /** 导出下拉菜单项 */
  const exportMenuItems: MenuProps['items'] = [
    {
      key: 'xlsx',
      label: '导出 Excel',
      onClick: () => handleExport('xlsx'),
    },
    {
      key: 'csv',
      label: '导出 CSV',
      onClick: () => handleExport('csv'),
    },
  ];

  /** 表格列定义 */
  const columns: ColumnsType<ScrmAuditLog> = [
    {
      title: '操作时间',
      dataIndex: 'operatedAt',
      key: 'operatedAt',
      width: 170,
      render: (value: string) =>
        value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: '用户',
      key: 'user',
      width: 150,
      ellipsis: true,
      render: (_, record) => record.username || record.userId || '-',
    },
    {
      title: '资源',
      dataIndex: 'resource',
      key: 'resource',
      width: 120,
      render: (value: string) => renderTag(value, resourceConfig),
    },
    {
      title: '动作',
      dataIndex: 'action',
      key: 'action',
      width: 90,
      render: (value: string) => (value ? renderTag(value, actionConfig) : '-'),
    },
    {
      title: '方法',
      dataIndex: 'method',
      key: 'method',
      width: 90,
      render: (value: string) => (value ? renderTag(value, methodConfig) : '-'),
    },
    {
      title: '请求URI',
      dataIndex: 'requestUri',
      key: 'requestUri',
      width: 220,
      ellipsis: true,
      render: (value: string) => <span title={value}>{truncateUri(value)}</span>,
    },
    {
      title: '结果',
      dataIndex: 'result',
      key: 'result',
      width: 90,
      render: (value: string) => renderTag(value, resultConfig),
    },
    {
      title: '耗时(ms)',
      dataIndex: 'executionTime',
      key: 'executionTime',
      width: 110,
      sorter: (a, b) => Number(a.executionTime || 0) - Number(b.executionTime || 0),
      render: (value?: number | string) => {
        if (value === undefined || value === null) return '-';
        const numVal = Number(value);
        const color = getExecutionTimeColor(numVal);
        return color ? <span style={{ color, fontWeight: 500 }}>{numVal}</span> : numVal;
      },
    },
    {
      title: '客户端IP',
      dataIndex: 'clientIp',
      key: 'clientIp',
      width: 140,
      ellipsis: true,
      render: (value?: string) => value || '-',
    },
    {
      title: '操作',
      key: 'actions',
      width: 100,
      fixed: 'right',
      render: (_, record) => (
        <Button
          type="link"
          size="small"
          icon={<EyeOutlined />}
          onClick={() => openDetail(record)}
        >
          详情
        </Button>
      ),
    },
  ];

  return (
    <div>
      {/* 统计概览 (后端聚合的全局统计) */}
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={6}>
          <Card loading={statsLoading} size="small">
            <Statistic
              title="总操作数"
              value={stats?.totalOperations ?? 0}
              prefix={<AuditOutlined style={{ color: '#1677ff' }} />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card loading={statsLoading} size="small">
            <Statistic
              title="成功操作"
              value={stats?.successCount ?? 0}
              valueStyle={{ color: '#3f8600' }}
              prefix={<CheckCircleOutlined style={{ color: '#3f8600' }} />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card loading={statsLoading} size="small">
            <Statistic
              title="失败操作"
              value={stats?.failedCount ?? 0}
              valueStyle={{ color: '#cf1322' }}
              prefix={<CloseCircleOutlined style={{ color: '#cf1322' }} />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card loading={statsLoading} size="small">
            <Statistic
              title="成功率"
              value={stats ? stats.successRate * 100 : 0}
              precision={1}
              suffix="%"
              prefix={<BarChartOutlined style={{ color: '#1677ff' }} />}
            />
          </Card>
        </Col>
      </Row>

      {/* 资源分布 (按资源类型聚合) */}
      {stats?.resourceDistribution &&
        Object.keys(stats.resourceDistribution).length > 0 && (
          <Card size="small" style={{ marginBottom: 16 }}>
            <Space wrap>
              <Text type="secondary">资源分布:</Text>
              {Object.entries(stats.resourceDistribution).map(
                ([resource, count]) => {
                  const cfg = resourceConfig[resource] || {
                    label: resource,
                    color: 'default',
                  };
                  return (
                    <Tag key={resource} color={cfg.color}>
                      {cfg.label} : {count}
                    </Tag>
                  );
                },
              )}
            </Space>
          </Card>
        )}

      {/* 统计卡片 (基于当前页日志) */}
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col xs={12} sm={6}>
          <Card>
            <Statistic title="总操作数" value={pageStats.totalOps} />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card>
            <Statistic
              title="成功数"
              value={pageStats.success}
              valueStyle={{ color: '#3f8600' }}
            />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card>
            <Statistic
              title="失败数"
              value={pageStats.failure}
              valueStyle={{ color: '#cf1322' }}
            />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card>
            <Statistic title="平均耗时(ms)" value={pageStats.avgTime} />
          </Card>
        </Col>
      </Row>

      {/* 顶部工具栏 */}
      <Card style={{ marginBottom: 16 }}>
        <Row gutter={[16, 16]} align="middle">
          <Col xs={24} sm={8} md={5}>
            <Input
              placeholder="搜索用户 ID"
              allowClear
              value={userId}
              onChange={(e) => setUserId(e.target.value)}
              suffix={
                userId && userId !== debouncedUserId ? <LoadingOutlined /> : null
              }
            />
          </Col>
          <Col xs={12} sm={6} md={4}>
            <Select
              placeholder="资源类型"
              allowClear
              style={{ width: '100%' }}
              options={resourceOptions}
              onChange={(v) => {
                setResourceFilter(v);
                setPage(0);
              }}
            />
          </Col>
          <Col xs={12} sm={6} md={4}>
            <Select
              placeholder="结果"
              allowClear
              style={{ width: '100%' }}
              options={resultOptions}
              onChange={(v) => {
                setResultFilter(v);
                setPage(0);
              }}
            />
          </Col>
          <Col xs={24} sm={12} md={6}>
            <RangePicker
              showTime
              format="YYYY-MM-DD HH:mm:ss"
              style={{ width: '100%' }}
              value={timeRange}
              onChange={(v) => {
                setTimeRange(v as [Dayjs, Dayjs] | null);
                setPage(0);
              }}
            />
          </Col>
          <Col flex="auto">
            <Space style={{ float: 'right' }}>
              <Button icon={<ReloadOutlined />} onClick={fetchLogs}>
                刷新
              </Button>
              <Button
                icon={<DownloadOutlined />}
                onClick={handleExportCsv}
                disabled={!logs || logs.length === 0}
                loading={exporting}
              >
                导出 CSV
              </Button>
              <Dropdown menu={{ items: exportMenuItems }}>
                <Button icon={<DownloadOutlined />} loading={exporting}>
                  导出 <DownOutlined />
                </Button>
              </Dropdown>
            </Space>
          </Col>
        </Row>
      </Card>

      {/* 审计日志列表 */}
      <Card>
        <Table<ScrmAuditLog>
          rowKey="id"
          columns={columns}
          dataSource={logs}
          loading={loading}
          scroll={{ x: 1400 }}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="暂无审计日志"
                style={{ padding: 32 }}
              />
            ),
          }}
          pagination={{
            current: page + 1,
            pageSize: size,
            total,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p, s) => {
              setPage(p - 1);
              setSize(s);
            },
          }}
        />
      </Card>

      {/* 详情弹窗 */}
      <Modal
        title="审计日志详情"
        open={detailOpen}
        onCancel={() => setDetailOpen(false)}
        width={820}
        footer={[
          <Button key="close" onClick={() => setDetailOpen(false)}>
            关闭
          </Button>,
        ]}
      >
        {currentLog && (
          <div>
            {/* 基本信息 */}
            <Descriptions
              size="small"
              column={2}
              bordered
              style={{ marginBottom: 16 }}
            >
              <Descriptions.Item label="日志ID">{currentLog.id}</Descriptions.Item>
              <Descriptions.Item label="操作时间">
                {currentLog.operatedAt
                  ? dayjs(currentLog.operatedAt).format('YYYY-MM-DD HH:mm:ss')
                  : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="用户">
                {currentLog.username || currentLog.userId || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="客户端IP">
                {currentLog.clientIp || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="资源">
                {renderTag(currentLog.resource, resourceConfig)}
              </Descriptions.Item>
              <Descriptions.Item label="动作">
                {currentLog.action ? renderTag(currentLog.action, actionConfig) : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="方法">
                {currentLog.method ? renderTag(currentLog.method, methodConfig) : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="结果">
                {renderTag(currentLog.result, resultConfig)}
              </Descriptions.Item>
              <Descriptions.Item label="耗时(ms)">
                {currentLog.executionTime !== undefined && currentLog.executionTime !== null
                  ? currentLog.executionTime
                  : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="请求URI" span={2}>
                <Text copyable={{ text: currentLog.requestUri }} style={{ wordBreak: 'break-all' }}>
                  {currentLog.requestUri || '-'}
                </Text>
              </Descriptions.Item>
            </Descriptions>

            {/* 请求参数 (JSON 格式化 + 复制按钮) */}
            <Card
              size="small"
              title="请求参数"
              style={{ marginBottom: 12 }}
              extra={
                <Button
                  type="link"
                  size="small"
                  icon={<CopyOutlined />}
                  onClick={() => handleCopy(currentLog.requestParams)}
                >
                  复制
                </Button>
              }
            >
              <pre style={codeBlockStyle}>{formatJson(currentLog.requestParams)}</pre>
            </Card>

            {/* 响应体 (JSON 格式化 + 复制按钮) */}
            <Card
              size="small"
              title="响应体"
              style={{ marginBottom: 12 }}
              extra={
                <Button
                  type="link"
                  size="small"
                  icon={<CopyOutlined />}
                  onClick={() => handleCopy(currentLog.responseBody)}
                >
                  复制
                </Button>
              }
            >
              <pre style={codeBlockStyle}>{formatJson(currentLog.responseBody)}</pre>
            </Card>

            {/* 错误信息 (如有) */}
            {currentLog.errorMessage && (
              <Card size="small" title="错误信息" style={{ marginBottom: 12 }}>
                <Paragraph
                  type="danger"
                  style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }}
                >
                  {currentLog.errorMessage}
                </Paragraph>
              </Card>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
}
