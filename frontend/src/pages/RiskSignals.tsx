/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : RiskSignals.tsx
 * Date : 2026/07/27
 * Author : hiylo
 * Contact : hiylo@live.com
 */

import { useEffect, useState, useCallback, type ReactNode } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  App,
  Button,
  Card,
  Col,
  DatePicker,
  Descriptions,
  Drawer,
  Empty,
  Input,
  Modal,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
} from 'antd';
import {
  ReloadOutlined,
  SearchOutlined,
  InfoOutlined,
  ExclamationOutlined,
  WarningOutlined,
  FireOutlined,
  CheckCircleOutlined,
  StopOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';
import { isImeComposing } from '../utils/imeHelpers';

const { RangePicker } = DatePicker;

/** 标签配置类型 */
interface TagConfig {
  label: string;
  color: string;
  icon?: ReactNode;
}

/** 风控信号实体 (对齐 ScrmRiskSignalEntity) */
interface ScrmRiskSignal {
  id: string;
  ruleId?: string; // 触发的风险规则 ID
  personaId?: string; // 关联的人设 ID
  accountId?: string; // 关联的账号 ID
  signalType?: string; // 信号类型, 如 login_anomaly / frequency_overflow
  riskLevel?: string; // 风险等级: LOW / MEDIUM / HIGH / CRITICAL
  detail?: string; // 风险详情
  triggeredAt?: string; // 触发时间
  createTime?: string;
  status?: string; // 处理状态: PENDING / RESOLVED / IGNORED
  resolvedAt?: string; // 处理时间
  resolvedBy?: string; // 处理人
  resolveRemark?: string; // 处理备注
}

/** Spring Page 分页响应 */
interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/** 风险等级映射 (含图标与颜色) */
const riskLevelConfig: Record<string, TagConfig> = {
  LOW: { label: '低风险', color: 'blue', icon: <InfoOutlined /> },
  MEDIUM: { label: '中风险', color: 'orange', icon: <ExclamationOutlined /> },
  HIGH: { label: '高风险', color: 'red', icon: <WarningOutlined /> },
  CRITICAL: { label: '严重', color: 'volcano', icon: <FireOutlined /> },
};

/** 风险等级下拉选项 (含"全部"由 allowClear 体现) */
const riskLevelOptions = Object.entries(riskLevelConfig).map(([value, cfg]) => ({
  value,
  label: cfg.label,
}));

/** 处理状态映射 */
const statusConfig: Record<string, { label: string; color: string }> = {
  PENDING: { label: '待处理', color: 'orange' },
  RESOLVED: { label: '已处理', color: 'green' },
  IGNORED: { label: '已忽略', color: 'default' },
};

/** 处理状态下拉选项 */
const statusOptions = Object.entries(statusConfig).map(([value, cfg]) => ({
  value,
  label: cfg.label,
}));

/** 渲染处理状态 Tag */
const renderStatusTag = (value?: string) => {
  if (!value) return <Tag color="orange">待处理</Tag>;
  const cfg = statusConfig[value] || { label: value, color: 'default' };
  return <Tag color={cfg.color}>{cfg.label}</Tag>;
};

/**
 * 渲染风险等级 Tag (带 fallback)
 */
const renderRiskLevelTag = (value?: string) => {
  if (!value) return '-';
  const cfg = riskLevelConfig[value] || { label: value, color: 'default' };
  return cfg.icon ? (
    <Tag color={cfg.color} icon={cfg.icon}>
      {cfg.label}
    </Tag>
  ) : (
    <Tag color={cfg.color}>{cfg.label}</Tag>
  );
};

/**
 * 风控信号页面
 * 只读展示 scrm-server 命中风控规则后回调写入的信号记录,
 * 支持按风险等级 / 信号类型筛选, 按触发时间倒序分页。
 */
export default function RiskSignals() {
  const { message } = App.useApp();
  const [signals, setSignals] = useState<ScrmRiskSignal[]>([]);
  const [loading, setLoading] = useState(false);
  // 从 URL 查询参数读取筛选状态 (实现筛选状态持久化, 用户导航返回后恢复筛选条件)
  const [searchParams, setSearchParams] = useSearchParams();
  // page 为 0 索引, URL 中存储 1 索引 (page=1 对应第一页)
  const [page, setPage] = useState(() => {
    const p = Number(searchParams.get('page'));
    return Number.isFinite(p) && p > 0 ? p - 1 : 0;
  });
  const [size, setSize] = useState(() => {
    const s = Number(searchParams.get('size'));
    return Number.isFinite(s) && s > 0 ? s : 10;
  });
  const [total, setTotal] = useState(0);
  // 筛选条件 (输入框值与已提交值分离, 点击查询按钮才触发请求)
  // 初始值从 URL 查询参数读取, 输入框与已提交值同步恢复
  const [riskLevelFilter, setRiskLevelFilter] = useState<string | undefined>(
    searchParams.get('riskLevel') || undefined,
  );
  const [signalTypeInput, setSignalTypeInput] = useState(searchParams.get('signalType') || '');
  const [signalTypeFilter, setSignalTypeFilter] = useState(searchParams.get('signalType') || '');
  // 账号 ID 筛选 (输入框值与已提交值分离, 初始值从 URL 读取)
  const [accountIdInput, setAccountIdInput] = useState(searchParams.get('accountId') || '');
  const [accountIdFilter, setAccountIdFilter] = useState(searchParams.get('accountId') || '');
  // 触发时间范围筛选 (从 URL 读取 ISO 字符串并解析为 dayjs 对象)
  const [timeRange, setTimeRange] = useState<[dayjs.Dayjs | null, dayjs.Dayjs | null] | null>(() => {
    const start = searchParams.get('startTime');
    const end = searchParams.get('endTime');
    if (start && end) return [dayjs(start), dayjs(end)];
    if (start) return [dayjs(start), null];
    if (end) return [null, dayjs(end)];
    return null;
  });
  // 详情抽屉
  const [detailRecord, setDetailRecord] = useState<ScrmRiskSignal | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailData, setDetailData] = useState<ScrmRiskSignal | null>(null);
  // 处理状态筛选
  const [statusFilter, setStatusFilter] = useState<string | undefined>(
    searchParams.get('status') || undefined,
  );
  // 信号处理弹窗 (resolve=标记已处理 / ignore=标记已忽略)
  const [resolveModal, setResolveModal] = useState<{
    open: boolean;
    action: 'resolve' | 'ignore';
    record: ScrmRiskSignal | null;
  }>({ open: false, action: 'resolve', record: null });
  const [resolveRemark, setResolveRemark] = useState('');
  const [resolving, setResolving] = useState(false);

  /** 拉取风控信号分页列表 */
  const fetchSignals = useCallback(async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      params.set('page', String(page));
      params.set('size', String(size));
      if (riskLevelFilter) params.set('riskLevel', riskLevelFilter);
      if (signalTypeFilter) params.set('signalType', signalTypeFilter);
      if (accountIdFilter) params.set('accountId', accountIdFilter);
      if (statusFilter) params.set('status', statusFilter);
      if (timeRange && timeRange[0]) {
        params.set('startTime', timeRange[0].format('YYYY-MM-DDTHH:mm:ss'));
      }
      if (timeRange && timeRange[1]) {
        params.set('endTime', timeRange[1].format('YYYY-MM-DDTHH:mm:ss'));
      }
      const data = await apiClient.get<Page<ScrmRiskSignal>>(
        `/scrm/risk-signals?${params.toString()}`,
      );
      setSignals(data.content || []);
      setTotal(data.totalElements || 0);
    } catch {
      // 错误已由 axios 拦截器统一提示
      setSignals([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [page, size, riskLevelFilter, signalTypeFilter, accountIdFilter, statusFilter, timeRange]);

  useEffect(() => {
    fetchSignals();
  }, [fetchSignals]);

  // 同步筛选状态到 URL 查询参数 (使用 replace 避免污染浏览器历史; 仅同步已提交的筛选值)
  useEffect(() => {
    const params: Record<string, string> = {};
    if (riskLevelFilter) params.riskLevel = riskLevelFilter;
    if (signalTypeFilter) params.signalType = signalTypeFilter;
    if (accountIdFilter) params.accountId = accountIdFilter;
    if (statusFilter) params.status = statusFilter;
    if (timeRange?.[0]) params.startTime = timeRange[0].format('YYYY-MM-DDTHH:mm:ss');
    if (timeRange?.[1]) params.endTime = timeRange[1].format('YYYY-MM-DDTHH:mm:ss');
    if (page !== 0) params.page = String(page + 1);
    if (size !== 10) params.size = String(size);
    setSearchParams(params, { replace: true });
  }, [riskLevelFilter, signalTypeFilter, accountIdFilter, statusFilter, timeRange, page, size, setSearchParams]);

  /** 点击查询按钮: 提交信号类型 / 账号 ID 筛选并重置到第一页 */
  const handleSearch = () => {
    setSignalTypeFilter(signalTypeInput.trim());
    setAccountIdFilter(accountIdInput.trim());
    setPage(0);
    message.loading({ content: '查询中...', key: 'risk-signal-search', duration: 0.6 });
  };

  /** 查看单条信号详情 (调用详情接口并打开 Drawer) */
  const handleViewDetail = async (record: ScrmRiskSignal) => {
    setDetailRecord(record);
    setDetailData(null);
    setDetailLoading(true);
    try {
      const detail = await apiClient.get<ScrmRiskSignal>(`/scrm/risk-signals/${record.id}`);
      setDetailData(detail);
    } catch {
      // 错误已由拦截器提示, 保留 detailRecord 以展示已知字段
    } finally {
      setDetailLoading(false);
    }
  };

  /** 关闭详情抽屉并清理状态 */
  const handleCloseDetail = () => {
    setDetailRecord(null);
    setDetailData(null);
  };

  /** 打开处理弹窗 */
  const openResolveModal = (record: ScrmRiskSignal, action: 'resolve' | 'ignore') => {
    setResolveModal({ open: true, action, record });
    setResolveRemark('');
  };

  /** 关闭处理弹窗 */
  const closeResolveModal = () => {
    setResolveModal({ open: false, action: 'resolve', record: null });
    setResolveRemark('');
  };

  /** 提交信号处理 (resolve=标记已处理 / ignore=标记已忽略) */
  const handleResolveSignal = async () => {
    if (!resolveModal.record) return;
    setResolving(true);
    try {
      await apiClient.put(
        `/scrm/risk-signals/${resolveModal.record.id}/${resolveModal.action}`,
        resolveRemark.trim() ? { remark: resolveRemark.trim() } : undefined,
      );
      message.success(resolveModal.action === 'resolve' ? '已标记为已处理' : '已标记为已忽略');
      closeResolveModal();
      fetchSignals();
    } catch {
      // 错误已由拦截器提示
    } finally {
      setResolving(false);
    }
  };

  /** 详情展示数据: 优先使用详情接口返回的完整数据, 回退到列表行已知字段 */
  const displayDetail = detailData ?? detailRecord;

  /** 表格列定义 */
  const columns: ColumnsType<ScrmRiskSignal> = [
    {
      title: '信号类型',
      dataIndex: 'signalType',
      key: 'signalType',
      width: 160,
      render: (value?: string) =>
        value ? <Tag color="geekblue">{value}</Tag> : '-',
    },
    {
      title: '风险等级',
      dataIndex: 'riskLevel',
      key: 'riskLevel',
      width: 120,
      render: (value?: string) => renderRiskLevelTag(value),
    },
    {
      title: '关联账号',
      dataIndex: 'accountId',
      key: 'accountId',
      width: 110,
      render: (value?: string) => (value != null ? `#${value}` : '-'),
    },
    {
      title: '关联人设',
      dataIndex: 'personaId',
      key: 'personaId',
      width: 130,
      ellipsis: true,
      render: (value?: string) => value || '-',
    },
    {
      title: '规则 ID',
      dataIndex: 'ruleId',
      key: 'ruleId',
      width: 160,
      ellipsis: true,
      render: (value?: string) => value || '-',
    },
    {
      title: '触发时间',
      dataIndex: 'triggeredAt',
      key: 'triggeredAt',
      width: 180,
      render: (value?: string) =>
        value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: '处理状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value?: string) => renderStatusTag(value),
    },
    {
      title: '风险详情',
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
      width: 200,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            loading={detailLoading && detailRecord?.id === record.id}
            onClick={() => handleViewDetail(record)}
          >
            详情
          </Button>
          {(!record.status || record.status === 'PENDING') && (
            <>
              <Tooltip title="标记为已处理">
                <Button
                  type="link"
                  size="small"
                  icon={<CheckCircleOutlined />}
                  onClick={() => openResolveModal(record, 'resolve')}
                >
                  处理
                </Button>
              </Tooltip>
              <Tooltip title="标记为已忽略">
                <Button
                  type="link"
                  size="small"
                  icon={<StopOutlined />}
                  onClick={() => openResolveModal(record, 'ignore')}
                >
                  忽略
                </Button>
              </Tooltip>
            </>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div>
      {/* 顶部筛选栏 */}
      <Card style={{ marginBottom: 16 }}>
        <Row gutter={[16, 16]} align="middle">
          <Col xs={24} sm={6} md={4}>
            <Select
              placeholder="风险等级 (全部)"
              allowClear
              style={{ width: '100%' }}
              options={riskLevelOptions}
              value={riskLevelFilter}
              onChange={(v) => {
                setRiskLevelFilter(v);
                setPage(0);
              }}
            />
          </Col>
          <Col xs={12} sm={4} md={3}>
            <Select
              placeholder="处理状态 (全部)"
              allowClear
              style={{ width: '100%' }}
              options={statusOptions}
              value={statusFilter}
              onChange={(v) => {
                setStatusFilter(v);
                setPage(0);
              }}
            />
          </Col>
          <Col xs={24} sm={8} md={6}>
            <Input
              placeholder="信号类型, 如 login_anomaly / frequency_overflow"
              allowClear
              value={signalTypeInput}
              onChange={(e) => setSignalTypeInput(e.target.value)}
              onPressEnter={(e) => { if (!isImeComposing(e)) handleSearch(); }}
            />
          </Col>
          <Col xs={12} sm={4} md={3}>
            <Input
              placeholder="账号 ID"
              allowClear
              value={accountIdInput}
              onChange={(e) => setAccountIdInput(e.target.value)}
              onPressEnter={(e) => { if (!isImeComposing(e)) handleSearch(); }}
            />
          </Col>
          <Col xs={24} sm={12} md={8}>
            <RangePicker
              showTime
              format="YYYY-MM-DD HH:mm:ss"
              style={{ width: '100%' }}
              value={timeRange as [dayjs.Dayjs, dayjs.Dayjs] | null}
              onChange={(v) => {
                setTimeRange(v as [dayjs.Dayjs | null, dayjs.Dayjs | null] | null);
                setPage(0);
              }}
            />
          </Col>
          <Col flex="auto">
            <Space style={{ float: 'right' }}>
              <Button icon={<ReloadOutlined />} onClick={fetchSignals}>
                刷新
              </Button>
              <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
                查询
              </Button>
            </Space>
          </Col>
        </Row>
      </Card>

      {/* 信号列表 */}
      <Card>
        <Table<ScrmRiskSignal>
          rowKey="id"
          columns={columns}
          dataSource={signals}
          loading={loading}
          scroll={{ x: 1180 }}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="暂无风控信号"
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

      {/* 风险信号详情抽屉 */}
      <Drawer
        title="风险信号详情"
        open={detailRecord !== null}
        onClose={handleCloseDetail}
        width={560}
        destroyOnClose
      >
        <Descriptions
          column={1}
          bordered
          size="small"
          labelStyle={{ width: 110 }}
        >
          <Descriptions.Item label="信号类型">
            {displayDetail?.signalType ? (
              <Tag color="geekblue">{displayDetail.signalType}</Tag>
            ) : (
              '-'
            )}
          </Descriptions.Item>
          <Descriptions.Item label="风险等级">
            {renderRiskLevelTag(displayDetail?.riskLevel)}
          </Descriptions.Item>
          <Descriptions.Item label="关联账号">
            {displayDetail?.accountId != null ? `#${displayDetail.accountId}` : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="关联人设">
            {displayDetail?.personaId || '-'}
          </Descriptions.Item>
          <Descriptions.Item label="规则 ID">
            {displayDetail?.ruleId || '-'}
          </Descriptions.Item>
          <Descriptions.Item label="触发时间">
            {displayDetail?.triggeredAt
              ? dayjs(displayDetail.triggeredAt).format('YYYY-MM-DD HH:mm:ss')
              : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="风险详情">
            {displayDetail?.detail || '-'}
          </Descriptions.Item>
          <Descriptions.Item label="处理状态">
            {renderStatusTag(displayDetail?.status)}
          </Descriptions.Item>
          {displayDetail?.status && displayDetail.status !== 'PENDING' && (
            <>
              <Descriptions.Item label="处理人">
                {displayDetail?.resolvedBy || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="处理时间">
                {displayDetail?.resolvedAt
                  ? dayjs(displayDetail.resolvedAt).format('YYYY-MM-DD HH:mm:ss')
                  : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="处理备注">
                {displayDetail?.resolveRemark || '-'}
              </Descriptions.Item>
            </>
          )}
          <Descriptions.Item label="创建时间">
            {displayDetail?.createTime
              ? dayjs(displayDetail.createTime).format('YYYY-MM-DD HH:mm:ss')
              : '-'}
          </Descriptions.Item>
        </Descriptions>
        {/* 详情抽屉中的处理按钮 (仅待处理状态显示) */}
        {displayDetail && (!displayDetail.status || displayDetail.status === 'PENDING') && (
          <Space style={{ marginTop: 16 }}>
            <Button
              type="primary"
              icon={<CheckCircleOutlined />}
              onClick={() => openResolveModal(displayDetail, 'resolve')}
            >
              标记为已处理
            </Button>
            <Button
              icon={<StopOutlined />}
              onClick={() => openResolveModal(displayDetail, 'ignore')}
            >
              标记为已忽略
            </Button>
          </Space>
        )}
      </Drawer>

      {/* 信号处理弹窗 */}
      <Modal
        title={resolveModal.action === 'resolve' ? '标记为已处理' : '标记为已忽略'}
        open={resolveModal.open}
        onOk={handleResolveSignal}
        onCancel={closeResolveModal}
        confirmLoading={resolving}
        okText="确认"
        cancelText="取消"
        destroyOnClose
      >
        <div style={{ marginBottom: 8 }}>
          {resolveModal.action === 'resolve'
            ? '确认将此风控信号标记为已处理? 请填写处理措施说明。'
            : '确认将此风控信号标记为已忽略? 请填写忽略原因。'}
        </div>
        <Input.TextArea
          rows={4}
          placeholder={resolveModal.action === 'resolve' ? '请输入处理措施说明 (可选)' : '请输入忽略原因 (可选)'}
          value={resolveRemark}
          onChange={(e) => setResolveRemark(e.target.value)}
          maxLength={500}
          showCount
        />
      </Modal>
    </div>
  );
}
