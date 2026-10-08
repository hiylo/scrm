/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Segments.tsx
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
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  ReloadOutlined,
  SearchOutlined,
  CalculatorOutlined,
  EyeOutlined,
  StopOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

const { Text } = Typography;

/** 客户分群实体 */
interface ScrmSegment {
  id: string;
  segmentName: string;
  segmentCode: string;
  description?: string;
  segmentType: string; // DYNAMIC / STATIC / HYBRID
  category?: string;
  conditionType?: string;
  conditions?: string;
  status: string; // ACTIVE / INACTIVE / DRAFT
  calculationFrequency?: string;
  autoUpdate?: boolean;
  memberCount?: number;
  lastCalculatedAt?: string;
  createTime?: string;
}

/** 分群成员 */
interface ScrmSegmentMember {
  id: string;
  segmentId: string;
  customerId: string;
  customerName?: string;
  source?: string;
  joinedAt?: string;
}

/** Spring Page 分页响应 */
interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/** 类型映射 */
const segmentTypeConfig: Record<string, { color: string; label: string }> = {
  DYNAMIC: { color: 'blue', label: '动态' },
  STATIC: { color: 'green', label: '静态' },
  HYBRID: { color: 'purple', label: '混合' },
};

/** 状态映射 */
const statusConfig: Record<string, { color: string; label: string }> = {
  ACTIVE: { color: 'green', label: '启用' },
  INACTIVE: { color: 'default', label: '停用' },
  DRAFT: { color: 'orange', label: '草稿' },
};

/** 成员来源映射 */
const sourceConfig: Record<string, { color: string; label: string }> = {
  AUTO: { color: 'blue', label: '自动' },
  MANUAL: { color: 'cyan', label: '手动' },
};

/**
 * 客户分群页
 * <p>
 * 客户细分体系: 分群定义 (动态/静态/混合, 条件 JSON) + 计算/预览 + 成员管理
 * + 分群历史。动态分群按规则自动更新, 静态分群手动维护成员。
 * </p>
 *
 * @author Hsi Chu
 */
export default function Segments() {
  const { message } = App.useApp();
  /** 列表 */
  const [list, setList] = useState<ScrmSegment[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const pageSize = 10;
  const [keyword, setKeyword] = useState('');
  /** 弹窗 */
  const [editing, setEditing] = useState<ScrmSegment | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  /** 成员抽屉 */
  const [memberSegment, setMemberSegment] = useState<ScrmSegment | null>(null);
  const [members, setMembers] = useState<ScrmSegmentMember[]>([]);
  const [memberLoading, setMemberLoading] = useState(false);
  const [memberTotal, setMemberTotal] = useState(0);

  /** 加载列表 */
  const loadList = useCallback(async (targetPage = page, k = keyword) => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(pageSize) });
      if (k) params.set('keyword', k);
      const data = await apiClient.get<Page<ScrmSegment>>(`/scrm/segments/list?${params.toString()}`);
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

  /** 打开创建 */
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ segmentType: 'DYNAMIC', status: 'ACTIVE', autoUpdate: true, conditionType: 'ALL', conditions: '[]' });
    setOpen(true);
  };

  /** 打开编辑 */
  const openEdit = (record: ScrmSegment) => {
    setEditing(record);
    form.setFieldsValue(record);
    setOpen(true);
  };

  /** 提交 */
  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await apiClient.put(`/scrm/segments/${editing.id}`, values);
        message.success('分群已更新');
      } else {
        await apiClient.post('/scrm/segments', values);
        message.success('分群已创建');
      }
      setOpen(false);
      setPage(0);
      loadList(0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setSaving(false);
    }
  };

  /** 删除 */
  const handleDelete = async (record: ScrmSegment) => {
    try {
      await apiClient.delete(`/scrm/segments/${record.id}`);
      message.success('分群已删除');
      loadList();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 启停 */
  const handleToggle = async (record: ScrmSegment, toEnable: boolean) => {
    try {
      await apiClient.post(`/scrm/segments/${record.id}/${toEnable ? 'activate' : 'deactivate'}`);
      message.success(toEnable ? '分群已启用' : '分群已停用');
      loadList();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 计算 */
  const handleCalculate = async (record: ScrmSegment) => {
    try {
      const data = await apiClient.post<{ count?: number }>(`/scrm/segments/${record.id}/calculate`, {});
      message.success(`计算完成, 命中 ${data?.count ?? 0} 客户`);
      loadList();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 查看成员 */
  const openMembers = async (record: ScrmSegment) => {
    setMemberSegment(record);
    setMemberLoading(true);
    try {
      const data = await apiClient.get<Page<ScrmSegmentMember>>(`/scrm/segments/${record.id}/members?page=0&size=20`);
      setMembers(data.content || []);
      setMemberTotal(data.totalElements || 0);
    } catch {
      setMembers([]);
      setMemberTotal(0);
    } finally {
      setMemberLoading(false);
    }
  };

  /** 表格列 */
  const columns: ColumnsType<ScrmSegment> = useMemo(() => [
    {
      title: '分群名称',
      dataIndex: 'segmentName',
      render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span>,
    },
    { title: '编码', dataIndex: 'segmentCode', width: 110, render: (v: string) => <Text code>{v}</Text> },
    {
      title: '类型',
      dataIndex: 'segmentType',
      width: 90,
      render: (v: string) => {
        const cfg = segmentTypeConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '分类', dataIndex: 'category', width: 100, render: (v?: string) => v || '-' },
    { title: '成员数', dataIndex: 'memberCount', width: 90, render: (v?: number) => v ?? 0 },
    { title: '最后计算', dataIndex: 'lastCalculatedAt', width: 130, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => {
        const cfg = statusConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '操作',
      key: 'actions',
      width: 230,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          <Button type="link" size="small" icon={<CalculatorOutlined />} onClick={() => handleCalculate(r)}>
            计算
          </Button>
          <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => openMembers(r)}>
            成员
          </Button>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(r)}>
            编辑
          </Button>
          {r.status === 'ACTIVE' ? (
            <Button type="link" size="small" danger icon={<StopOutlined />} onClick={() => handleToggle(r, false)}>
              停用
            </Button>
          ) : (
            <Button type="link" size="small" onClick={() => handleToggle(r, true)}>
              启用
            </Button>
          )}
          <Popconfirm title="确定删除该分群?" onConfirm={() => handleDelete(r)}>
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  return (
    <div className="segments-page">
      <Card
        title="客户分群"
        extra={
          <Space>
            <Input
              placeholder="搜索分群名称/编码"
              prefix={<SearchOutlined />}
              allowClear
              style={{ width: 200 }}
              onChange={e => {
                setKeyword(e.target.value);
                setPage(0);
              }}
            />
            <Button icon={<ReloadOutlined />} onClick={() => loadList()} />
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              新建分群
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

      {/* 分群弹窗 */}
      <Modal
        title={editing ? '编辑分群' : '新建分群'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={560}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="segmentName" label="分群名称" rules={[{ required: true, message: '请输入分群名称' }]}>
            <Input placeholder="如: 高价值客户" maxLength={200} />
          </Form.Item>
          <Form.Item name="segmentCode" label="分群编码" rules={[{ required: true, message: '请输入分群编码' }]}>
            <Input placeholder="如: VIP_CUSTOMER" maxLength={50} />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="segmentType" label="分群类型" rules={[{ required: true }]}>
              <Select style={{ width: 130 }} options={Object.entries(segmentTypeConfig).map(([v, c]) => ({ value: v, label: c.label }))} />
            </Form.Item>
            <Form.Item name="category" label="分类">
              <Input placeholder="如: VALUE" style={{ width: 120 }} />
            </Form.Item>
          </Space>
          <Form.Item name="conditions" label="条件 JSON" rules={[{ required: true, message: '请输入条件 JSON' }]}>
            <Input.TextArea rows={3} placeholder='[{"field":"totalSpend","op":"gt","value":10000}]' />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="分群说明" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 成员抽屉 */}
      <Drawer
        title={memberSegment ? `分群成员 - ${memberSegment.segmentName}` : '分群成员'}
        open={!!memberSegment}
        onClose={() => setMemberSegment(null)}
        width={560}
      >
        <Table
          rowKey="id"
          size="small"
          dataSource={members}
          loading={memberLoading}
          pagination={false}
          locale={{ emptyText: <Empty description="暂无成员, 请先计算分群" /> }}
          columns={[
            { title: '客户', dataIndex: 'customerName', width: 120, render: (v?: string) => v || '-' },
            {
              title: '来源',
              dataIndex: 'source',
              width: 90,
              render: (v?: string) => {
                const cfg = sourceConfig[v || ''] || { color: 'default', label: v || '-' };
                return <Tag color={cfg.color}>{cfg.label}</Tag>;
              },
            },
            { title: '加入时间', dataIndex: 'joinedAt', width: 140, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
          ]}
        />
        {members.length < memberTotal && (
          <div style={{ textAlign: 'center', marginTop: 8 }}>
            <Text type="secondary">{members.length}/{memberTotal}</Text>
          </div>
        )}
      </Drawer>
    </div>
  );
}