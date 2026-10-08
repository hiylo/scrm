/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : QuickReplies.tsx
 * Date : 2026/10/08 12:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  App,
  Button,
  Card,
  Col,
  Empty,
  Form,
  Input,
  Modal,
  Row,
  Select,
  Space,
  Table,
  Tag,
} from 'antd';
import {
  PlusOutlined,
  ReloadOutlined,
  FolderAddOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

/** 回复类型映射 */
const replyTypeConfig: Record<string, { color: string; label: string }> = {
  TEXT: { color: 'blue', label: '文本' },
  IMAGE: { color: 'cyan', label: '图片' },
  CARD: { color: 'purple', label: '卡片' },
  LINK: { color: 'geekblue', label: '链接' },
  FILE: { color: 'default', label: '文件' },
};

/** 状态映射 */
const statusConfig: Record<string, { color: string; label: string }> = {
  ENABLED: { color: 'green', label: '启用' },
  DISABLED: { color: 'default', label: '停用' },
};

/** 快捷回复分类 */
interface ScrmQuickReplyCategory {
  id: string;
  categoryName: string;
  platformType?: string;
  status?: string;
}

/** 快捷回复实体 */
interface ScrmQuickReply {
  id: string;
  title: string;
  categoryId?: string;
  categoryName?: string;
  content: string;
  replyType: string;
  shortcut?: string;
  scenario?: string;
  useCount?: number;
  status: string;
  tags?: string;
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
 * 快捷回复页
 * <p>
 * 分类管理 + 快捷回复 CRUD, 会话中输入 # 快捷码即可快速回复。
 * </p>
 *
 * @author Hsi Chu
 */
export default function QuickReplies() {
  const { message } = App.useApp();
  /** 分类 */
  const [categories, setCategories] = useState<ScrmQuickReplyCategory[]>([]);
  const [categoryId, setCategoryId] = useState<string | undefined>();
  /** 列表 */
  const [list, setList] = useState<ScrmQuickReply[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const pageSize = 10;
  /** 分类弹窗 */
  const [catOpen, setCatOpen] = useState(false);
  const [catSaving, setCatSaving] = useState(false);
  const [catForm] = Form.useForm();
  /** 回复弹窗 */
  const [editing, setEditing] = useState<ScrmQuickReply | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  /** 加载分类 */
  const loadCategories = useCallback(async () => {
    try {
      const data = await apiClient.get<Page<ScrmQuickReplyCategory>>('/scrm/quick-replies/quick-reply-categories/list');
      setCategories(data.content || []);
    } catch {
      setCategories([]);
    }
  }, []);

  /** 加载回复列表 */
  const loadList = useCallback(async (targetPage = page, cat = categoryId) => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(pageSize) });
      if (cat) params.set('categoryId', cat);
      const data = await apiClient.get<Page<ScrmQuickReply>>(`/scrm/quick-replies/list?${params.toString()}`);
      setList(data.content || []);
      setTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, categoryId]);

  useEffect(() => {
    loadCategories();
  }, [loadCategories]);

  useEffect(() => {
    loadList();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, categoryId]);

  /** 新增分类 */
  const handleCreateCategory = async () => {
    const values = await catForm.validateFields();
    setCatSaving(true);
    try {
      await apiClient.post('/scrm/quick-replies/categories', values);
      message.success('分类已创建');
      setCatOpen(false);
      loadCategories();
    } catch {
      // 拦截器已弹出错误
    } finally {
      setCatSaving(false);
    }
  };

  /** 打开新建弹窗 */
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ replyType: 'TEXT', status: 'ENABLED' });
    setOpen(true);
  };

  /** 提交回复 */
  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await apiClient.put(`/scrm/quick-replies/${editing.id}`, values);
        message.success('回复已更新');
      } else {
        await apiClient.post('/scrm/quick-replies', values);
        message.success('回复已创建');
      }
      setOpen(false);
      setPage(0);
      loadList(0, categoryId);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setSaving(false);
    }
  };

  /** 表格列 */
  const columns: ColumnsType<ScrmQuickReply> = useMemo(() => [
    { title: '标题', dataIndex: 'title', render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
    { title: '分类', dataIndex: 'categoryName', width: 110, render: (v?: string) => v || '-' },
    {
      title: '类型',
      dataIndex: 'replyType',
      width: 90,
      render: (v: string) => {
        const cfg = replyTypeConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '快捷码', dataIndex: 'shortcut', width: 90, render: (v?: string) => (v ? `#${v}` : '-') },
    { title: '场景', dataIndex: 'scenario', width: 100, render: (v?: string) => v || '-' },
    { title: '使用次数', dataIndex: 'useCount', width: 90, render: (v?: number) => v ?? 0 },
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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  return (
    <div className="quick-replies-page">
      <Row gutter={16}>
        <Col span={5}>
          <Card
            title="回复分类"
            size="small"
            extra={
              <Button type="text" size="small" icon={<FolderAddOutlined />} onClick={() => { catForm.resetFields(); setCatOpen(true); }}>
                新增分类
              </Button>
            }
          >
            <Space direction="vertical" style={{ width: '100%' }}>
              {categories.map(c => (
                <Button
                  key={c.id}
                  type={categoryId === c.id ? 'primary' : 'text'}
                  size="small"
                  block
                  onClick={() => {
                    setCategoryId(categoryId === c.id ? undefined : c.id);
                    setPage(0);
                  }}
                >
                  {c.categoryName}
                </Button>
              ))}
            </Space>
          </Card>
        </Col>
        <Col span={19}>
          <Card
            title="快捷回复"
            extra={
              <Space>
                <Button icon={<ReloadOutlined />} onClick={() => { loadCategories(); loadList(); }} />
                <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                  新建回复
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
              scroll={{ y: 'calc(100vh - 320px)' }}
              locale={{ emptyText: <Empty description="暂无快捷回复" /> }}
            />
          </Card>
        </Col>
      </Row>

      {/* 分类弹窗 */}
      <Modal
        title="新增分类"
        open={catOpen}
        onCancel={() => setCatOpen(false)}
        onOk={handleCreateCategory}
        confirmLoading={catSaving}
      >
        <Form form={catForm} layout="vertical">
          <Form.Item name="categoryName" label="分类名称" rules={[{ required: true, message: '请输入分类名称' }]}>
            <Input placeholder="如: 售前咨询" maxLength={100} />
          </Form.Item>
          <Form.Item name="platformType" label="平台类型" initialValue="wework">
            <Select
              options={[
                { value: 'wework', label: '企业微信' },
                { value: 'wechat_personal', label: '个人微信' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 回复弹窗 */}
      <Modal
        title={editing ? '编辑快捷回复' : '新建快捷回复'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={640}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入标题' }]}>
            <Input placeholder="如: 产品报价介绍" maxLength={200} />
          </Form.Item>
          <Form.Item name="categoryId" label="分类">
            <Select
              placeholder="选择分类"
              allowClear
              options={categories.map(c => ({ value: c.id, label: c.categoryName }))}
            />
          </Form.Item>
          <Form.Item name="content" label="回复内容" rules={[{ required: true, message: '请输入回复内容' }]}>
            <Input.TextArea rows={4} placeholder="回复内容, 支持 ${nickname} 变量" maxLength={5000} />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="replyType" label="回复类型" rules={[{ required: true, message: '请选择类型' }]}>
              <Select style={{ width: 140 }} options={Object.entries(replyTypeConfig).map(([v, c]) => ({ value: v, label: c.label }))} />
            </Form.Item>
            <Form.Item name="shortcut" label="快捷码 (会话中输入 # 触发)">
              <Input placeholder="如: jg" style={{ width: 160 }} />
            </Form.Item>
            <Form.Item name="scenario" label="场景">
              <Input placeholder="如: 售前 / 售后" style={{ width: 160 }} />
            </Form.Item>
          </Space>
          <Form.Item name="tags" label="标签 (逗号分隔)">
            <Input placeholder="如: 报价,热销" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}