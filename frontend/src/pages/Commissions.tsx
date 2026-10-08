/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Commissions.tsx
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
  Empty,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd';
import {
  CalculatorOutlined,
  ReloadOutlined,
  PayCircleOutlined,
  RollbackOutlined,
  BarChartOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

const { Text } = Typography;

/** 佣金状态映射 */
const statusConfig: Record<string, { color: string; label: string }> = {
  PENDING: { color: 'orange', label: '待计算' },
  CALCULATED: { color: 'blue', label: '已计算' },
  PAID: { color: 'green', label: '已支付' },
  REVERSED: { color: 'red', label: '已冲正' },
  CANCELLED: { color: 'default', label: '已取消' },
};

/** 佣金类型映射 */
const commissionTypeConfig: Record<string, { color: string; label: string }> = {
  SALES: { color: 'blue', label: '销售提成' },
  REFERRAL: { color: 'purple', label: '推荐奖励' },
  TEAM: { color: 'cyan', label: '团队奖励' },
  BONUS: { color: 'gold', label: '奖金' },
};

/** 佣金记录 */
interface ScrmCommissionRecord {
  id: string;
  commissionNo: string;
  salespersonId?: string;
  salespersonName?: string;
  commissionType?: string;
  commissionAmount?: number;
  orderNo?: string;
  status: string;
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
 * 佣金结算页
 * <p>
 * 佣金记录查询 + 计算 (生成提成记录) + 支付 (标记已支付) + 冲正 (误算回退) + 汇总抽屉。
 * </p>
 *
 * @author Hsi Chu
 */
export default function Commissions() {
  const { message } = App.useApp();
  /** 列表 */
  const [list, setList] = useState<ScrmCommissionRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const pageSize = 10;
  /** 汇总抽屉 */
  const [statsOpen, setStatsOpen] = useState(false);
  const [stats, setStats] = useState<Record<string, number> | null>(null);
  const [statsLoading, setStatsLoading] = useState(false);

  /** 加载列表 */
  const loadList = useCallback(async (targetPage = page) => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(pageSize) });
      const data = await apiClient.get<Page<ScrmCommissionRecord>>(`/scrm/commissions/records/list?${params.toString()}`);
      setList(data.content || []);
      setTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page]);

  useEffect(() => {
    loadList();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page]);

  /** 触发计算 */
  const handleCalculate = async () => {
    try {
      await apiClient.post('/scrm/commissions/records/calculate', {});
      message.success('佣金计算完成');
      loadList();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 标记支付 */
  const handlePay = async (record: ScrmCommissionRecord) => {
    try {
      await apiClient.post(`/scrm/commissions/payouts/${record.id}/mark-paid`);
      message.success('佣金已支付');
      loadList();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 冲正 */
  const handleClawback = async (record: ScrmCommissionRecord) => {
    try {
      await apiClient.post('/scrm/commissions/payouts/clawback', { recordIds: [Number(record.id)] });
      message.success('佣金已冲正');
      loadList();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 打开汇总抽屉 */
  const openStats = async () => {
    setStatsOpen(true);
    setStatsLoading(true);
    try {
      const data = await apiClient.get<Record<string, number>>('/scrm/commissions/records/stats');
      setStats(data || null);
    } catch {
      setStats(null);
    } finally {
      setStatsLoading(false);
    }
  };

  /** 表格列 */
  const columns: ColumnsType<ScrmCommissionRecord> = useMemo(() => [
    { title: '佣金单号', dataIndex: 'commissionNo', width: 140, render: (v: string) => <Text code>{v}</Text> },
    { title: '业务员', dataIndex: 'salespersonName', width: 110, render: (v?: string) => v || '-' },
    {
      title: '佣金类型',
      dataIndex: 'commissionType',
      width: 110,
      render: (v?: string) => {
        const cfg = commissionTypeConfig[v || ''] || { color: 'default', label: v || '-' };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '关联订单', dataIndex: 'orderNo', width: 120, render: (v?: string) => v || '-' },
    { title: '佣金金额', dataIndex: 'commissionAmount', width: 110, render: (v?: number) => (v != null ? `¥${v.toLocaleString()}` : '-') },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => {
        const cfg = statusConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '创建时间', dataIndex: 'createTime', width: 140, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
    {
      title: '操作',
      key: 'actions',
      width: 150,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          {r.status === 'CALCULATED' && (
            <Button type="link" size="small" icon={<PayCircleOutlined />} onClick={() => handlePay(r)}>
              支付
            </Button>
          )}
          {r.status === 'PAID' && (
            <Button type="link" size="small" danger icon={<RollbackOutlined />} onClick={() => handleClawback(r)}>
              冲正
            </Button>
          )}
        </Space>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  return (
    <div className="commissions-page">
      <Card
        title="佣金结算"
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => loadList()} />
            <Button icon={<BarChartOutlined />} onClick={openStats}>
              汇总
            </Button>
            <Button type="primary" icon={<CalculatorOutlined />} onClick={handleCalculate}>
              计算
            </Button>
          </Space>
        }
      >
        <Table
          rowKey="id"
          columns={columns}
          dataSource={list}
          loading={loading}
          pagination={{
            current: page + 1,
            pageSize,
            total,
            showTotal: t => `共 ${t} 条`,
            onChange: p => setPage(p - 1),
          }}
        />
      </Card>

      {/* 统计抽屉 */}
      <Drawer
        title="佣金汇总"
        open={statsOpen}
        onClose={() => setStatsOpen(false)}
        width={420}
      >
        {statsLoading ? (
          <div style={{ textAlign: 'center', padding: 48 }}><Spin /></div>
        ) : stats ? (
          <div style={{ display: 'flex', gap: 24, flexWrap: 'wrap' }}>
            {Object.entries(stats).map(([k, v]) => (
              <Statistic key={k} title={k} value={v} prefix="¥" />
            ))}
          </div>
        ) : (
          <Empty description="暂无统计数据" />
        )}
      </Drawer>
    </div>
  );
}
