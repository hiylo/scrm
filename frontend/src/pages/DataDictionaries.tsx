/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : DataDictionaries.tsx
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
  ReloadOutlined,
  DeleteOutlined,
  ProfileOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

const { Text } = Typography;

/** 字典类型映射 */
const dictTypeConfig: Record<string, string> = {
  ENUM: '枚举',
  STRING: '字符串',
  NUMBER: '数值',
  BOOLEAN: '布尔',
  JSON: 'JSON',
};

/** 字典状态映射 */
const statusConfig: Record<string, { color: string; label: string }> = {
  ACTIVE: { color: 'green', label: '启用' },
  INACTIVE: { color: 'default', label: '停用' },
};

/** 数据字典实体 */
interface ScrmDataDictionary {
  id: string;
  dictName: string;
  dictCode: string;
  dictType?: string;
  description?: string;
  status?: string;
  itemCount?: number;
  createTime?: string;
}

/** 字典项实体 */
interface ScrmDictionaryItem {
  id: string;
  dictId?: string;
  itemLabel: string;
  itemValue: string;
  sortOrder?: number;
  itemColor?: string;
  description?: string;
  enabled?: boolean;
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
 * 数据字典页面
 * 字典 CRUD / 字典项抽屉 (增删改) 管理。
 *
 * @author Hsi Chu
 */
export default function DataDictionaries() {
  const { message } = App.useApp();
  /** 字典 */
  const [list, setList] = useState<ScrmDataDictionary[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const pageSize = 10;
  const [total, setTotal] = useState(0);
  /** 字典弹窗 */
  const [editing, setEditing] = useState<ScrmDataDictionary | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  /** 字典项抽屉 */
  const [itemDict, setItemDict] = useState<ScrmDataDictionary | null>(null);
  const [items, setItems] = useState<ScrmDictionaryItem[]>([]);
  const [itemLoading, setItemLoading] = useState(false);
  /** 字典项弹窗 */
  const [itemEditing, setItemEditing] = useState<ScrmDictionaryItem | null>(null);
  const [itemOpen, setItemOpen] = useState(false);
  const [itemSaving, setItemSaving] = useState(false);
  const [itemForm] = Form.useForm();

  /** 加载字典列表 */
  const loadList = useCallback(async (targetPage = page) => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(pageSize) });
      const data = await apiClient.get<Page<ScrmDataDictionary>>(`/scrm/data-dictionaries/list?${params.toString()}`);
      setList(data.content || []);
      setTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
      setList([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page]);

  useEffect(() => {
    loadList();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page]);

  /** 打开新建字典弹窗 */
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ dictType: 'ENUM' });
    setOpen(true);
  };

  /** 提交字典 */
  const handleSubmit = async () => {
    let values: Record<string, unknown>;
    try {
      values = await form.validateFields();
    } catch {
      return;
    }
    setSaving(true);
    try {
      if (editing) {
        await apiClient.put(`/scrm/data-dictionaries/${editing.id}`, values);
        message.success('字典已更新');
      } else {
        await apiClient.post('/scrm/data-dictionaries', values);
        message.success('字典已创建');
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

  /** 打开字典项抽屉 */
  const openItems = async (record: ScrmDataDictionary) => {
    setItemDict(record);
    setItems([]);
    setItemLoading(true);
    try {
      const rows = await apiClient.get<ScrmDictionaryItem[]>(`/scrm/data-dictionaries/${record.id}/items`);
      setItems(rows || []);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setItemLoading(false);
    }
  };

  /** 打开新增字典项弹窗 */
  const openCreateItem = () => {
    setItemEditing(null);
    itemForm.resetFields();
    setItemOpen(true);
  };

  /** 打开编辑字典项弹窗 */
  const openEditItem = (record: ScrmDictionaryItem) => {
    setItemEditing(record);
    itemForm.setFieldsValue(record);
    setItemOpen(true);
  };

  /** 提交字典项 */
  const handleSubmitItem = async () => {
    if (!itemDict) return;
    let values: Record<string, unknown>;
    try {
      values = await itemForm.validateFields();
    } catch {
      return;
    }
    setItemSaving(true);
    try {
      if (itemEditing) {
        await apiClient.put(`/scrm/data-dictionaries/items/${itemEditing.id}`, values);
        message.success('字典项已更新');
      } else {
        await apiClient.post('/scrm/data-dictionaries/items', { ...values, dictId: itemDict.id });
        message.success('字典项已创建');
      }
      setItemOpen(false);
      openItems(itemDict);
      loadList();
    } catch {
      // 拦截器已弹出错误
    } finally {
      setItemSaving(false);
    }
  };

  /** 删除字典项 */
  const handleDeleteItem = async (record: ScrmDictionaryItem) => {
    try {
      await apiClient.delete(`/scrm/data-dictionaries/items/${record.id}`);
      message.success('字典项已删除');
      if (itemDict) openItems(itemDict);
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 表格列 */
  const columns: ColumnsType<ScrmDataDictionary> = useMemo(() => [
    { title: '字典名称', dataIndex: 'dictName', render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
    { title: '字典编码', dataIndex: 'dictCode', width: 190, render: (v: string) => <Text code>{v}</Text> },
    {
      title: '字典类型',
      dataIndex: 'dictType',
      width: 110,
      render: (v?: string) => (v ? <Tag color="blue">{dictTypeConfig[v] || v}</Tag> : '-'),
    },
    { title: '描述', dataIndex: 'description', ellipsis: true, render: (v?: string) => v || '-' },
    { title: '字典项数', dataIndex: 'itemCount', width: 100, render: (v?: number) => v ?? 0 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (v?: string) => {
        const cfg = statusConfig[v || ''] || { color: 'default', label: v || '-' };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '创建时间', dataIndex: 'createTime', width: 140, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
    {
      title: '操作',
      key: 'actions',
      width: 170,
      fixed: 'right',
      render: (_, r) => (
        <Space size={0}>
          <Button type="link" size="small" icon={<ProfileOutlined />} onClick={() => openItems(r)}>
            项
          </Button>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => { setEditing(r); form.setFieldsValue(r); setOpen(true); }}>
            编辑
          </Button>
        </Space>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  return (
    <div className="data-dictionaries-page">
      <Card
        title="数据字典"
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => loadList()} />
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              新建字典
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
          }} scroll={{ y: 'calc(100vh - 320px)' }}
        />
      </Card>

      {/* 字典弹窗 */}
      <Modal
        title={editing ? '编辑字典' : '新建字典'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={520}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="dictName" label="字典名称" rules={[{ required: true, message: '请输入字典名称' }]}>
            <Input placeholder="如: 客户生命周期" maxLength={200} />
          </Form.Item>
          <Form.Item name="dictCode" label="字典编码" rules={[{ required: true, message: '请输入字典编码' }]}>
            <Input placeholder="如: CUSTOMER_LIFECYCLE" maxLength={100} />
          </Form.Item>
          <Form.Item name="dictType" label="字典类型" rules={[{ required: true }]}>
            <Select options={Object.entries(dictTypeConfig).map(([v, l]) => ({ value: v, label: l }))} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="字典说明" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 字典项抽屉 */}
      <Drawer
        title={itemDict ? `字典项 - ${itemDict.dictName}` : '字典项'}
        open={!!itemDict}
        onClose={() => setItemDict(null)}
        width={560}
      >
        <div style={{ marginBottom: 12 }}>
          <Button type="primary" size="small" icon={<PlusOutlined />} onClick={openCreateItem}>
            新增字典项
          </Button>
        </div>
        <Table
          rowKey="id"
          size="small"
          dataSource={items}
          loading={itemLoading}
          pagination={false}
          locale={{ emptyText: <Empty description="暂无字典项" /> }}
          columns={[
            { title: '标签', dataIndex: 'itemLabel', width: 140, render: (v: string) => (v ? <Tag color={itemEditing?.itemColor}>{v}</Tag> : v) },
            { title: '值', dataIndex: 'itemValue', width: 120, render: (v: string) => <Text code>{v}</Text> },
            { title: '排序', dataIndex: 'sortOrder', width: 70, render: (v?: number) => v ?? 0 },
            { title: '说明', dataIndex: 'description', ellipsis: true, render: (v?: string) => v || '-' },
            {
              title: '操作',
              key: 'actions',
              width: 120,
              render: (_, r) => (
                <Space size={4}>
                  <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEditItem(r)}>
                    编辑
                  </Button>
                  <Popconfirm title="确定删除该字典项?" onConfirm={() => handleDeleteItem(r)}>
                    <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                      删除
                    </Button>
                  </Popconfirm>
                </Space>
              ),
            },
          ]}
        />
      </Drawer>

      {/* 字典项弹窗 */}
      <Modal
        title={itemEditing ? '编辑字典项' : '新增字典项'}
        open={itemOpen}
        onCancel={() => setItemOpen(false)}
        onOk={handleSubmitItem}
        confirmLoading={itemSaving}
      >
        <Form form={itemForm} layout="vertical">
          <Form.Item name="itemLabel" label="标签" rules={[{ required: true, message: '请输入标签' }]}>
            <Input placeholder="如: 新客" maxLength={200} />
          </Form.Item>
          <Form.Item name="itemValue" label="值" rules={[{ required: true, message: '请输入值' }]}>
            <Input placeholder="如: NEW" maxLength={100} />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="sortOrder" label="排序">
              <Input type="number" style={{ width: 120 }} />
            </Form.Item>
            <Form.Item name="itemColor" label="颜色">
              <Input placeholder="如: blue" style={{ width: 120 }} />
            </Form.Item>
          </Space>
          <Form.Item name="description" label="说明">
            <Input placeholder="字典项说明" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}