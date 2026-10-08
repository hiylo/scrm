/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : PublicSea.tsx
 * Date : 2026/10/08 12:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

import { useCallback, useEffect, useMemo, useState, type Key } from 'react';
import {
  App,
  Button,
  Card,
  Drawer,
  Empty,
  Form,
  Input,
  Modal,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import {
  ReloadOutlined,
  UserAddOutlined,
  CarryOutOutlined,
  ShareAltOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

const { Text } = Typography;

/** 公海状态映射 */
const statusConfig: Record<string, { color: string; label: string }> = {
  AVAILABLE: { color: 'green', label: '可领取' },
  ASSIGNED: { color: 'blue', label: '已分配' },
  LOCKED: { color: 'orange', label: '锁定' },
};

/** 生命周期映射 */
const lifecycleConfig: Record<string, { color: string; label: string }> = {
  NEW: { color: 'blue', label: '新客户' },
  ACTIVE: { color: 'green', label: '活跃' },
  CHURNED: { color: 'default', label: '流失' },
};

/** 公海客户行 */
interface ScrmPublicSeaCustomer {
  id: string;
  platformType: string;
  platformCustomerUid: string;
  nickname?: string;
  sourceChannel?: string;
  lifecycle?: string;
  status: string;
  tags?: string;
  remark?: string;
  recallCount?: number;
  assignmentExpireAt?: string;
  assignedTo?: string;
  createTime?: string;
}

/** 分配记录行 */
interface ScrmLeadAssignment {
  id: string;
  assigneeName?: string;
  assignType?: string;
  assignedAt?: string;
}

/** Spring Page 分页响应 */
interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/** 分配目标: 单条客户或批量哨兵 (id = 'batch') */
type AssignTarget = ScrmPublicSeaCustomer;

/** 批量分配哨兵: 复用实体结构, 仅用 id='batch' 标识批量模式 */
const BATCH_TARGET: AssignTarget = {
  id: 'batch',
  platformType: '',
  platformCustomerUid: '',
  status: 'AVAILABLE',
};

/**
 * 公海客户页面
 * <p>
 * 公海客户列表 + 领取 + 分配 (单条/批量) + 详情抽屉 (含分配记录)。
 * </p>
 *
 * @author Hsi Chu
 */
export default function PublicSea() {
  const { message } = App.useApp();
  /** 列表 */
  const [list, setList] = useState<ScrmPublicSeaCustomer[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const pageSize = 10;
  /** 批量勾选 */
  const [selectedRowKeys, setSelectedRowKeys] = useState<Key[]>([]);
  /** 详情抽屉 */
  const [detail, setDetail] = useState<ScrmPublicSeaCustomer | null>(null);
  const [assignments, setAssignments] = useState<ScrmLeadAssignment[]>([]);
  /** 分配弹窗 */
  const [assignTarget, setAssignTarget] = useState<AssignTarget | null>(null);
  const [assigning, setAssigning] = useState(false);
  const [assignForm] = Form.useForm();
  /** 是否批量分配模式 (以哨兵 id='batch' 判定) */
  const batchMode = assignTarget?.id === 'batch';

  /** 加载列表 */
  const loadList = useCallback(async (targetPage = page) => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(pageSize) });
      const data = await apiClient.get<Page<ScrmPublicSeaCustomer>>(`/scrm/public-sea/list?${params.toString()}`);
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

  /** 领取公海客户 */
  const handleClaim = async (record: ScrmPublicSeaCustomer) => {
    try {
      await apiClient.post(`/scrm/public-sea/${record.id}/claim`, {});
      message.success('已领取该客户');
      loadList();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 打开分配弹窗 (单条) */
  const openAssign = (record: ScrmPublicSeaCustomer) => {
    assignForm.resetFields();
    setAssignTarget(record);
  };

  /** 提交单条分配 */
  const handleAssign = async () => {
    if (!assignTarget || batchMode) return;
    const values = await assignForm.validateFields();
    setAssigning(true);
    try {
      await apiClient.post(`/scrm/public-sea/${assignTarget.id}/assign`, {
        assigneeUserId: values.assigneeUserId,
        assignReason: values.assignReason,
      });
      message.success('分配成功');
      setAssignTarget(null);
      loadList();
    } catch {
      // 拦截器已弹出错误
    } finally {
      setAssigning(false);
    }
  };

  /** 打开批量分配弹窗 */
  const openBatchAssign = () => {
    assignForm.resetFields();
    setAssignTarget(BATCH_TARGET);
  };

  /** 提交批量分配 */
  const handleBatchAssignSubmit = async () => {
    if (!assignTarget || !batchMode) return;
    const values = await assignForm.validateFields();
    setAssigning(true);
    try {
      await apiClient.post('/scrm/public-sea/batch-assign', {
        customerIds: selectedRowKeys.map(Number),
        assigneeUserId: values.assigneeUserId,
      });
      message.success('批量分配成功');
      setAssignTarget(null);
      setSelectedRowKeys([]);
      loadList();
    } catch {
      // 拦截器已弹出错误
    } finally {
      setAssigning(false);
    }
  };

  /** 查看明细抽屉 */
  const openDetail = async (record: ScrmPublicSeaCustomer) => {
    setDetail(record);
    setAssignments([]);
    try {
      const data = await apiClient.get<ScrmLeadAssignment[]>(`/scrm/public-sea/${record.id}/assignments`);
      setAssignments(Array.isArray(data) ? data : []);
    } catch {
      // 拦截器已弹出错误
      setAssignments([]);
    }
  };

  /** 表格列 */
  const columns: ColumnsType<ScrmPublicSeaCustomer> = useMemo(() => [
    {
      title: '客户',
      dataIndex: 'nickname',
      width: 160,
      render: (v: string | undefined, r) => (
        <span style={{ fontWeight: 600 }}>{r.nickname || r.platformCustomerUid}</span>
      ),
    },
    { title: '平台 UID', dataIndex: 'platformCustomerUid', width: 150, ellipsis: true, render: (v: string) => <Tag>{v}</Tag> },
    { title: '来源渠道', dataIndex: 'sourceChannel', width: 120, render: (v?: string) => v || '-' },
    {
      title: '生命周期',
      dataIndex: 'lifecycle',
      width: 100,
      render: (v?: string) => {
        const cfg = lifecycleConfig[v || ''] || { color: 'default', label: v || '-' };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => {
        const cfg = statusConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '回收次数', dataIndex: 'recallCount', width: 90, render: (v?: number) => v ?? 0 },
    { title: '创建时间', dataIndex: 'createTime', width: 140, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
    {
      title: '操作',
      key: 'actions',
      width: 150,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          <Button type="link" size="small" icon={<CarryOutOutlined />} onClick={() => handleClaim(r)}>
            领取
          </Button>
          <Button type="link" size="small" icon={<UserAddOutlined />} onClick={() => openAssign(r)}>
            分配
          </Button>
          <Button type="link" size="small" onClick={() => openDetail(r)}>
            详情
          </Button>
        </Space>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  return (
    <div className="public-sea-page">
      <Card
        title="公海客户"
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => loadList()} />
            <Button
              type="primary"
              icon={<ShareAltOutlined />}
              disabled={selectedRowKeys.length === 0}
              onClick={openBatchAssign}
            >
              批量分配{selectedRowKeys.length > 0 ? ` (${selectedRowKeys.length})` : ''}
            </Button>
          </Space>
        }
      >
        <Table
          rowKey="id"
          columns={columns}
          dataSource={list}
          loading={loading}
          rowSelection={{
            selectedRowKeys,
            onChange: keys => setSelectedRowKeys(keys),
          }}
          pagination={{
            current: page + 1,
            pageSize,
            total,
            showTotal: t => `共 ${t} 条`,
            onChange: p => setPage(p - 1),
          }}
          scroll={{ y: 'calc(100vh - 320px)' }}
        />
      </Card>

      {/* 详情抽屉 */}
      <Drawer
        title={detail ? `公海客户 - ${detail.nickname || detail.platformCustomerUid}` : '公海客户'}
        open={!!detail}
        onClose={() => setDetail(null)}
        width={520}
      >
        {detail && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <Text type="secondary">平台 UID: {detail.platformCustomerUid}</Text>
            <Text type="secondary">来源渠道: {detail.sourceChannel || '-'}</Text>
            <Text type="secondary">标签: {detail.tags || '-'}</Text>
            <Text type="secondary">备注: {detail.remark || '-'}</Text>
            <Text type="secondary">回收次数: {detail.recallCount ?? 0}</Text>
            {detail.assignmentExpireAt && (
              <Text type="secondary">分配过期: {dayjs(detail.assignmentExpireAt).format('YYYY-MM-DD HH:mm')}</Text>
            )}
            <div style={{ marginTop: 8 }}>
              <Text strong>分配记录</Text>
              <Table
                rowKey="id"
                size="small"
                dataSource={assignments}
                pagination={false}
                locale={{ emptyText: <Empty description="暂无分配记录" /> }}
                columns={[
                  { title: '处理人', dataIndex: 'assigneeName', width: 100, render: (v?: string) => v || '-' },
                  { title: '类型', dataIndex: 'assignType', width: 100, render: (v?: string) => v || '-' },
                  { title: '时间', dataIndex: 'assignedAt', width: 150, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
                ]}
              />
            </div>
          </div>
        )}
      </Drawer>

      {/* 分配弹窗 */}
      <Modal
        title={batchMode ? '批量分配客户' : (assignTarget ? `分配客户 - ${assignTarget.nickname || ''}` : '分配客户')}
        open={!!assignTarget}
        onCancel={() => setAssignTarget(null)}
        onOk={batchMode ? handleBatchAssignSubmit : handleAssign}
        confirmLoading={assigning}
      >
        <Form form={assignForm} layout="vertical">
          <Form.Item name="assigneeUserId" label="接收人用户 ID" rules={[{ required: true, message: '请输入接收人用户 ID' }]}>
            <Input placeholder="用户 ID" />
          </Form.Item>
          <Form.Item name="assignReason" label="分配原因">
            <Input.TextArea rows={2} placeholder="分配原因/备注" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}