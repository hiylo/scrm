/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : Readiness.tsx
 * Date : 2026/10/24 18:40:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Empty,
  Progress,
  Row,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ReloadOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { getReadiness, type Readiness, type ReadinessModule } from '../api/finance';

/**
 * 财税域就绪看板。
 * <p>
 * 一眼看清五条外部依赖链路各自是「已就绪」「没开」还是「开了但缺凭证」。
 * 仅展示开关与凭证是否齐备，<b>不回显任何凭证明文</b>。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
export default function ReadinessPage() {
  const [data, setData] = useState<Readiness | null>(null);
  const [loading, setLoading] = useState(false);

  /** 加载就绪汇总 */
  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await getReadiness());
    } catch {
      // client.ts 已统一提示
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const columns: ColumnsType<ReadinessModule> = [
    {
      title: '模块',
      dataIndex: 'name',
      width: 220,
      render: (value: string, record) => (
        <Space>
          <Tag color={record.ready ? 'green' : 'default'}>{record.ready ? '就绪' : '未就绪'}</Tag>
          <span>{value}</span>
        </Space>
      ),
    },
    {
      title: '当前状态',
      dataIndex: 'detail',
      render: (value: string) => <span style={{ fontSize: 13 }}>{value}</span>,
    },
    {
      title: '缺什么 / 怎么开',
      dataIndex: 'blocker',
      width: 420,
      render: (value: string | null) =>
        value ? (
          <Typography.Text type="warning" style={{ fontSize: 12 }}>{value}</Typography.Text>
        ) : (
          <Typography.Text type="success" style={{ fontSize: 12 }}>无需处理</Typography.Text>
        ),
    },
  ];

  const total = data?.totalCount ?? 0;
  const ready = data?.readyCount ?? 0;
  const percent = total > 0 ? Math.round((ready / total) * 100) : 0;

  return (
    <div>
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={8}>
          <Card size="small">
            <Space direction="vertical" style={{ width: '100%' }} align="center">
              <Progress type="circle" percent={percent} size={110} />
              <span style={{ color: '#888' }}>
                已就绪 {ready} / {total} 条链路
              </span>
            </Space>
          </Card>
        </Col>
        <Col span={16}>
          <Card
            size="small"
            title="说明"
            extra={<Button size="small" icon={<ReloadOutlined />} onClick={() => void load()}>刷新</Button>}
          >
            <Space direction="vertical" size={8} style={{ width: '100%' }}>
              <Typography.Text>
                「就绪」指开关已开启且凭证/参数齐备，可直接使用；未就绪项会明确写出缺什么。
              </Typography.Text>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                本页只反映依赖与配置状态，不查询业务数据量；也<b>不会回显任何 Token / Secret 明文</b>。
              </Typography.Text>
              {data?.generatedAt && (
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  生成时间：{dayjs(data.generatedAt).format('YYYY-MM-DD HH:mm:ss')}
                </Typography.Text>
              )}
            </Space>
          </Card>
        </Col>
      </Row>

      {data && !data.allReady && (
        <Alert
          style={{ marginBottom: 16 }}
          type="warning"
          showIcon
          message={`还有 ${total - ready} 条链路未就绪`}
          description="未就绪不等于故障：多为外部凭证未提供（启信宝 Token、企微凭证）或功能按计划未启用。站内信通知不依赖这些链路，始终可用。"
        />
      )}

      <Card title="外部依赖链路" loading={loading}>
        <Table<ReadinessModule>
          rowKey="key"
          size="small"
          columns={columns}
          dataSource={data?.modules || []}
          pagination={false}
          locale={{ emptyText: <Empty description="暂无数据" /> }}
        />
      </Card>
    </div>
  );
}
