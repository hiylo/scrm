/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Inspections.tsx
 * Date : 2026/10/24 18:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  App,
  Badge,
  Button,
  Card,
  Col,
  Descriptions,
  Empty,
  Row,
  Space,
  Statistic,
  Table,
  Tag,
  Tooltip,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PlayCircleOutlined, ReloadOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  getInspectionRunStats,
  getNoticeStatus,
  getQixinStatus,
  listInspectionRuns,
  runBusinessInspection,
  runTaxInspection,
  type InspectionRun,
  type InspectionRunStats,
  type NoticeStatus,
} from '../api/finance';

/** 执行状态映射（SKIPPED 是「按开关跳过」，不是失败） */
const runStatusConfig: Record<string, { color: string; label: string }> = {
  SUCCESS: { color: 'green', label: '成功' },
  PARTIAL: { color: 'orange', label: '部分成功' },
  FAILED: { color: 'red', label: '失败' },
  SKIPPED: { color: 'default', label: '已跳过' },
};

/** 执行类型映射 */
const runTypeConfig: Record<string, { color: string; label: string }> = {
  BUSINESS: { color: 'blue', label: '工商巡检' },
  TAX: { color: 'cyan', label: '税务抓取' },
};

/**
 * 巡检与公告页。
 * <p>
 * 三块：外部链路接入状态（启信宝 / 各公告源）、手动触发抓取、执行历史与统计。
 * 强调「SKIPPED ≠ FAILED」——未启用或限额跳过时状态是 SKIPPED，不是失败。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
export default function Inspections() {
  const { message } = App.useApp();

  const [noticeStatus, setNoticeStatus] = useState<NoticeStatus | null>(null);
  const [qixin, setQixin] = useState<Record<string, unknown> | null>(null);
  const [stats, setStats] = useState<InspectionRunStats | null>(null);
  const [runs, setRuns] = useState<InspectionRun[]>([]);
  const [runTotal, setRunTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [running, setRunning] = useState<'tax' | 'business' | null>(null);

  /** 加载全部状态与历史 */
  const loadAll = useCallback(async () => {
    setLoading(true);
    try {
      const [notice, qixinStatus, statsResult, runPage] = await Promise.all([
        getNoticeStatus(),
        getQixinStatus(),
        getInspectionRunStats(7),
        listInspectionRuns({ page: 0, size: 10 }),
      ]);
      setNoticeStatus(notice);
      setQixin(qixinStatus);
      setStats(statsResult);
      setRuns(runPage.content || []);
      setRunTotal(runPage.totalElements || 0);
    } catch {
      // client.ts 已统一提示
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadAll();
  }, [loadAll]);

  /** 手动触发（税务/工商） */
  const handleRun = async (kind: 'tax' | 'business') => {
    setRunning(kind);
    try {
      if (kind === 'tax') {
        const s = await runTaxInspection();
        message.success(
          `税务抓取完成：扫描 ${s.taskScanned} 个任务，命中 ${s.recordsMatched} 条，验证码阻断 ${s.captchaBlocked}`,
        );
      } else {
        const s = await runBusinessInspection();
        message.success(
          s.skipReason
            ? `工商巡检已跳过：${s.skipReason}`
            : `工商巡检完成：扫描 ${s.taskScanned} 个，成功 ${s.success}，失败 ${s.failed}`,
        );
      }
      await loadAll();
    } catch {
      // 已统一提示
    } finally {
      setRunning(null);
    }
  };

  /** 历史列定义 */
  const columns: ColumnsType<InspectionRun> = [
    {
      title: '类型',
      dataIndex: 'runType',
      width: 110,
      render: (value: string) => {
        const cfg = runTypeConfig[value] || { color: 'default', label: value };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: string) => {
        const cfg = runStatusConfig[value] || { color: 'default', label: value };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '触发',
      dataIndex: 'triggerType',
      width: 90,
      render: (value: string) => (value === 'MANUAL' ? '手动' : '定时'),
    },
    { title: '扫描', dataIndex: 'taskScanned', width: 70, align: 'right' },
    { title: '成功/命中', dataIndex: 'successCount', width: 90, align: 'right' },
    { title: '失败', dataIndex: 'failedCount', width: 70, align: 'right' },
    {
      title: '耗时',
      dataIndex: 'costMs',
      width: 90,
      align: 'right',
      render: (value: number) => (value == null ? '-' : `${value} ms`),
    },
    {
      title: '跳过原因',
      dataIndex: 'skipReason',
      ellipsis: true,
      render: (value: string) => value || '-',
    },
    {
      title: '时间',
      dataIndex: 'startedAt',
      width: 160,
      render: (value: string) => (value ? dayjs(value).format('MM-DD HH:mm:ss') : '-'),
    },
  ];

  const qixinReady = Boolean(qixin?.ready);

  return (
    <div>
      {/* 统计卡 */}
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={6}>
          <Card size="small">
            <Statistic
              title="近 7 天税务抓取"
              value={stats?.byType.find((b) => b.runType === 'TAX')?.totalRuns ?? 0}
              suffix="次"
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic
              title="近 7 天命中记录"
              value={stats?.byType.find((b) => b.runType === 'TAX')?.successCount ?? 0}
              suffix="条"
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic
              title="近 7 天工商巡检"
              value={stats?.byType.find((b) => b.runType === 'BUSINESS')?.totalRuns ?? 0}
              suffix="次"
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic
              title="可用公告源"
              value={noticeStatus?.usableSourceCount ?? 0}
              suffix="个"
            />
          </Card>
        </Col>
      </Row>

      {/* 接入状态 + 触发 */}
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={14}>
          <Card
            title="公告源接入状态"
            extra={<Button size="small" icon={<ReloadOutlined />} onClick={() => void loadAll()}>刷新</Button>}
          >
            {(noticeStatus?.sources || []).map((s) => (
              <div key={s.code} style={{ marginBottom: 10 }}>
                <Space>
                  <Badge status={s.ready ? 'success' : 'default'} />
                  <strong>{s.name}</strong>
                  <Tag>{s.code}</Tag>
                  {s.enabled ? <Tag color="blue">已启用</Tag> : <Tag>未启用</Tag>}
                  {s.ready ? <Tag color="green">可用</Tag> : <Tag color="default">不可用</Tag>}
                </Space>
                <div style={{ marginLeft: 26, color: '#888', fontSize: 12 }}>{s.detail}</div>
                {s.blocker && (
                  <div style={{ marginLeft: 26, color: '#d46b08', fontSize: 12 }}>阻塞：{s.blocker}</div>
                )}
              </div>
            ))}
            {!noticeStatus && <Empty description="暂无数据" />}
          </Card>
        </Col>

        <Col span={10}>
          <Card title="工商 API（启信宝）" style={{ marginBottom: 16 }}>
            <Descriptions column={1} size="small">
              <Descriptions.Item label="开关">
                {qixin?.enabled ? <Tag color="blue">已启用</Tag> : <Tag>未启用</Tag>}
              </Descriptions.Item>
              <Descriptions.Item label="Token">
                {qixinReady ? <Tag color="green">已配置</Tag> : <Tag color="orange">未配置</Tag>}
              </Descriptions.Item>
              <Descriptions.Item label="日预算">
                {String(qixin?.dailyBudget ?? '-')} 次
              </Descriptions.Item>
              <Descriptions.Item label="状态">
                <Badge status={qixinReady ? 'success' : 'warning'} text={qixinReady ? '就绪' : '需要 Token 才能启用'} />
              </Descriptions.Item>
            </Descriptions>
          </Card>

          <Card title="手动触发">
            <Space direction="vertical" style={{ width: '100%' }}>
              <Tooltip title="抓取广东/浙江公告列表，按授权企业过滤后落快照">
                <Button
                  block
                  type="primary"
                  icon={<PlayCircleOutlined />}
                  loading={running === 'tax'}
                  onClick={() => void handleRun('tax')}
                >
                  立即抓取税务公告
                </Button>
              </Tooltip>
              <Tooltip title={qixinReady ? '按启用任务巡检工商状态' : '启信宝未启用时会整体跳过（状态记为 SKIPPED）'}>
                <Button
                  block
                  icon={<PlayCircleOutlined />}
                  loading={running === 'business'}
                  onClick={() => void handleRun('business')}
                >
                  立即执行工商巡检
                </Button>
              </Tooltip>
            </Space>
          </Card>
        </Col>
      </Row>

      {/* 执行历史 */}
      <Card
        title="执行历史"
        loading={loading}
        extra={<span style={{ color: '#888' }}>共 {runTotal} 条（SKIPPED 表示按开关跳过，不是失败）</span>}
      >
        <Table<InspectionRun>
          rowKey="id"
          size="small"
          columns={columns}
          dataSource={runs}
          pagination={false}
          locale={{ emptyText: <Empty description="暂无执行记录" /> }}
        />
      </Card>
    </div>
  );
}
