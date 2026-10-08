/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Membership.tsx
 * Date : 2026/10/08 12:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  App,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tag,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  ReloadOutlined,
  SearchOutlined,
  CrownOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

/** 会员等级实体 */
interface ScrmMembershipTier {
  id: string;
  tierName: string;
  tierCode: string;
  tierLevel?: number;
  upgradeThreshold?: number;
  tierColor?: string;
  benefits?: string;
  status?: string;
}

/** 会员实体 */
interface ScrmMember {
  id: string;
  customerId?: string;
  customerName?: string;
  tierId?: string;
  tierName?: string;
  memberCardNo?: string;
  membershipStatus?: string; // ACTIVE / EXPIRED / FROZEN
  joinDate?: string;
  totalSpend?: number;
  totalPoints?: number;
  availablePoints?: number;
}

/** Spring Page 分页响应 */
interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/** 会员状态映射 */
const memberStatusConfig: Record<string, { color: string; label: string }> = {
  ACTIVE: { color: 'green', label: '正常' },
  EXPIRED: { color: 'default', label: '已过期' },
  FROZEN: { color: 'orange', label: '已冻结' },
};

/**
 * 会员体系管理页
 * <p>
 * 双卡布局: 上为会员等级(Tier) 配置, 下为会员(Member) 列表; 支持等级新建与会员入会。
 * </p>
 *
 * @author Hsi Chu
 */
export default function Membership() {
  const { message } = App.useApp();
  /** 等级 */
  const [tiers, setTiers] = useState<ScrmMembershipTier[]>([]);
  const [tiersLoading, setTiersLoading] = useState(false);
  const [tierPage, setTierPage] = useState(0);
  const tierPageSize = 10;
  const [tierTotal, setTierTotal] = useState(0);
  /** 会员 */
  const [members, setMembers] = useState<ScrmMember[]>([]);
  const [membersLoading, setMembersLoading] = useState(false);
  const [memberPage, setMemberPage] = useState(0);
  const memberPageSize = 10;
  const [memberTotal, setMemberTotal] = useState(0);
  const [keyword, setKeyword] = useState('');
  const [statusFilter, setStatusFilter] = useState<string | undefined>();
  /** 等级弹窗 */
  const [editing, setEditing] = useState<ScrmMembershipTier | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  /** 入会弹窗 */
  const [enrollOpen, setEnrollOpen] = useState(false);
  const [enrolling, setEnrolling] = useState(false);
  const [enrollForm] = Form.useForm();

  const loadTiers = useCallback(async (targetPage = tierPage) => {
    setTiersLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(tierPageSize) });
      const data = await apiClient.get<Page<ScrmMembershipTier>>(`/scrm/memberships/tiers/list?${params.toString()}`);
      setTiers(data.content || []);
      setTierTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setTiersLoading(false);
    }
  }, [tierPage]);

  const loadMembers = useCallback(async (targetPage = memberPage, k = keyword, status = statusFilter) => {
    setMembersLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(memberPageSize) });
      if (k) params.set('keyword', k);
      if (status) params.set('status', status);
      const data = await apiClient.get<Page<ScrmMember>>(`/scrm/memberships/list?${params.toString()}`);
      setMembers(data.content || []);
      setMemberTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setMembersLoading(false);
    }
  }, [memberPage, keyword, statusFilter]);

  useEffect(() => {
    loadTiers();
  }, [loadTiers]);

  useEffect(() => {
    loadMembers();
  }, [loadMembers]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    // 级别与升级门槛给出合理默认值, 避免必填校验空转
    form.setFieldsValue({ tierLevel: 1, upgradeThreshold: 0 });
    setOpen(true);
  };

  const openEdit = (record: ScrmMembershipTier) => {
    setEditing(record);
    form.setFieldsValue(record);
    setOpen(true);
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await apiClient.put(`/scrm/memberships/tiers/${editing.id}`, values);
        message.success('会员等级已更新');
      } else {
        await apiClient.post('/scrm/memberships/tiers', values);
        message.success('会员等级已创建');
      }
      setOpen(false);
      setTierPage(0);
      loadTiers(0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setSaving(false);
    }
  };

  const handleEnroll = async () => {
    const values = await enrollForm.validateFields();
    setEnrolling(true);
    try {
      await apiClient.post('/scrm/memberships/enroll', values);
      message.success('会员已入会');
      setEnrollOpen(false);
      setMemberPage(0);
      loadMembers(0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setEnrolling(false);
    }
  };

  const tierColumns: ColumnsType<ScrmMembershipTier> = useMemo(() => [
    { title: '等级名称', dataIndex: 'tierName', render: (v: string, r) => <Space><span style={{ fontWeight: 600, color: r.tierColor || undefined }}>{v}</span></Space> },
    { title: '等级编码', dataIndex: 'tierCode', width: 120, render: (v: string) => <Tag>{v}</Tag> },
    { title: '级别', dataIndex: 'tierLevel', width: 80, render: (v?: number) => v ?? '-' },
    { title: '升级门槛', dataIndex: 'upgradeThreshold', width: 120, render: (v?: number) => (v != null ? `¥${v.toLocaleString()}` : '-') },
    { title: '权益', dataIndex: 'benefits', ellipsis: true, render: (v?: string) => v || '-' },
    {
      title: '操作',
      key: 'actions',
      width: 90,
      render: (_, r) => (
        <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(r)}>
          编辑
        </Button>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  const memberColumns: ColumnsType<ScrmMember> = useMemo(() => [
    { title: '客户', dataIndex: 'customerName', render: (v?: string, r?: ScrmMember) => v || r?.customerId || '-' },
    { title: '会员卡号', dataIndex: 'memberCardNo', width: 140, render: (v?: string) => v || '-' },
    { title: '等级', dataIndex: 'tierName', width: 110, render: (v?: string) => (v ? <Tag color="gold">{v}</Tag> : '-') },
    {
      title: '状态',
      dataIndex: 'membershipStatus',
      width: 90,
      render: (v?: string) => { const c = memberStatusConfig[v || ''] || { color: 'default', label: v || '-' }; return <Tag color={c.color}>{c.label}</Tag>; },
    },
    { title: '累计消费', dataIndex: 'totalSpend', width: 110, render: (v?: number) => (v != null ? `¥${v.toLocaleString()}` : '-') },
    { title: '可用积分', dataIndex: 'availablePoints', width: 100, render: (v?: number) => v ?? '-' },
    { title: '入会日期', dataIndex: 'joinDate', width: 120, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD') : '-') },
  ], []);

  return (
    <div className="membership-page">
      <Card
        title={<Space><CrownOutlined />会员等级</Space>}
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => loadTiers()} />
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              新建等级
            </Button>
          </Space>
        }
        style={{ marginBottom: 16 }}
      >
        <Table
          rowKey="id"
          columns={tierColumns}
          dataSource={tiers}
          loading={tiersLoading}
          pagination={{
            current: tierPage + 1,
            pageSize: tierPageSize,
            total: tierTotal,
            showTotal: t => `共 ${t} 条`,
            onChange: p => setTierPage(p - 1),
          }}
        />
      </Card>

      <Card
        title="会员列表"
        extra={
          <Space>
            <Input
              placeholder="搜索客户名/卡号"
              prefix={<SearchOutlined />}
              allowClear
              style={{ width: 200 }}
              onChange={e => {
                setKeyword(e.target.value);
                setMemberPage(0);
              }}
            />
            <Select
              placeholder="状态"
              allowClear
              style={{ width: 110 }}
              options={Object.entries(memberStatusConfig).map(([v, c]) => ({ value: v, label: c.label }))}
              onChange={v => { setStatusFilter(v); setMemberPage(0); }}
            />
            <Button icon={<ReloadOutlined />} onClick={() => loadMembers()} />
            <Button type="primary" icon={<PlusOutlined />} onClick={() => { enrollForm.resetFields(); setEnrollOpen(true); }}>
              会员入会
            </Button>
          </Space>
        }
      >
        <Table
          rowKey="id"
          columns={memberColumns}
          dataSource={members}
          loading={membersLoading}
          pagination={{
            current: memberPage + 1,
            pageSize: memberPageSize,
            total: memberTotal,
            showTotal: t => `共 ${t} 条`,
            onChange: p => setMemberPage(p - 1),
          }}
        />
      </Card>

      {/* 等级弹窗 */}
      <Modal
        title={editing ? '编辑等级' : '新建会员等级'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={560}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="tierName" label="等级名称" rules={[{ required: true, message: '请输入等级名称' }]}>
            <Input placeholder="如: 黄金会员" maxLength={200} />
          </Form.Item>
          <Form.Item name="tierCode" label="等级编码" rules={[{ required: true, message: '请输入等级编码' }]}>
            <Input placeholder="如: GOLD" maxLength={50} />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="tierLevel" label="级别" rules={[{ required: true }]}>
              <InputNumber min={1} style={{ width: 110 }} />
            </Form.Item>
            <Form.Item name="upgradeThreshold" label="升级门槛 (¥)" rules={[{ required: true }]}>
              <InputNumber min={0} style={{ width: 150 }} />
            </Form.Item>
            <Form.Item name="tierColor" label="标识色">
              <Input placeholder="如 #f59e0b" style={{ width: 110 }} />
            </Form.Item>
          </Space>
          <Form.Item name="benefits" label="权益说明">
            <Input.TextArea rows={2} placeholder="如: 专属客服, 生日礼遇" maxLength={1000} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 入会弹窗 */}
      <Modal
        title="会员入会"
        open={enrollOpen}
        onCancel={() => setEnrollOpen(false)}
        onOk={handleEnroll}
        confirmLoading={enrolling}
      >
        <Form form={enrollForm} layout="vertical">
          <Form.Item name="customerId" label="客户 ID" rules={[{ required: true, message: '请输入客户 ID' }]}>
            <Input placeholder="客户 ID" />
          </Form.Item>
          <Form.Item name="tierId" label="等级 ID" rules={[{ required: true, message: '请输入等级 ID' }]}>
            <Input placeholder="等级 ID" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}