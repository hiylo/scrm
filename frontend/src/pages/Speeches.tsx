/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Speeches.tsx
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
  LikeOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

/** 话术类型映射 */
const speechTypeConfig: Record<string, { color: string; label: string }> = {
  TEXT: { color: 'blue', label: '文本' },
  IMAGE: { color: 'cyan', label: '图片' },
  VIDEO: { color: 'purple', label: '视频' },
  AUDIO: { color: 'gold', label: '音频' },
  FILE: { color: 'default', label: '文件' },
};

/** 话术状态映射 */
const statusConfig: Record<string, { color: string; label: string }> = {
  ENABLED: { color: 'green', label: '上架' },
  DISABLED: { color: 'default', label: '下架' },
};

/** 话术分类 */
interface ScrmSpeechCategory {
  id: string;
  categoryName: string;
  status?: string;
  description?: string;
}

/** 话术实体 */
interface ScrmSpeech {
  id: string;
  title: string;
  categoryId?: string;
  categoryName?: string;
  content: string;
  speechType: string;
  scenario?: string;
  useCount?: number;
  likeCount?: number;
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
 * 话术库页
 * <p>
 * 分类管理 + 话术 CRUD + 点赞, 沉淀可复用的客户沟通话术。
 * </p>
 *
 * @author Hsi Chu
 */
export default function Speeches() {
  const { message } = App.useApp();
  /** 分类 */
  const [categories, setCategories] = useState<ScrmSpeechCategory[]>([]);
  const [categoryId, setCategoryId] = useState<string | undefined>();
  /** 列表 */
  const [list, setList] = useState<ScrmSpeech[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const pageSize = 10;
  /** 分类弹窗 */
  const [catOpen, setCatOpen] = useState(false);
  const [catSaving, setCatSaving] = useState(false);
  const [catForm] = Form.useForm();
  /** 话术弹窗 */
  const [editing, setEditing] = useState<ScrmSpeech | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  /** 加载分类 */
  const loadCategories = useCallback(async () => {
    try {
      const data = await apiClient.get<Page<ScrmSpeechCategory>>('/scrm/speeches/speech-categories/list');
      setCategories(data.content || []);
    } catch {
      setCategories([]);
    }
  }, []);

  /** 加载话术列表 */
  const loadList = useCallback(async (targetPage = page, cat = categoryId) => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(pageSize) });
      if (cat) params.set('categoryId', cat);
      const data = await apiClient.get<Page<ScrmSpeech>>(`/scrm/speeches/list?${params.toString()}`);
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
      await apiClient.post('/scrm/speeches/speech-categories', values);
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
    form.setFieldsValue({ speechType: 'TEXT', status: 'ENABLED' });
    setOpen(true);
  };

  /** 提交话术 */
  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await apiClient.put(`/scrm/speeches/${editing.id}`, values);
        message.success('话术已更新');
      } else {
        await apiClient.post('/scrm/speeches', values);
        message.success('话术已创建');
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

  /** 点赞 */
  const handleLike = async (record: ScrmSpeech) => {
    try {
      await apiClient.post(`/scrm/speeches/${record.id}/like`);
      message.success('已点赞');
      loadList();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 表格列 */
  const columns: ColumnsType<ScrmSpeech> = useMemo(() => [
    { title: '话术标题', dataIndex: 'title', render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
    { title: '分类', dataIndex: 'categoryName', width: 110, render: (v?: string) => v || '-' },
    {
      title: '类型',
      dataIndex: 'speechType',
      width: 90,
      render: (v: string) => {
        const cfg = speechTypeConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '场景', dataIndex: 'scenario', width: 100, render: (v?: string) => v || '-' },
    { title: '使用次数', dataIndex: 'useCount', width: 90, render: (v?: number) => v ?? 0 },
    { title: '点赞', dataIndex: 'likeCount', width: 80, render: (v?: number) => v ?? 0 },
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
      width: 120,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          <Button type="link" size="small" icon={<LikeOutlined />} onClick={() => handleLike(r)}>
            点赞
          </Button>
        </Space>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  return (
    <div className="speeches-page">
      <Row gutter={16}>
        <Col span={5}>
          <Card
            title="话术分类"
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
            title="话术列表"
            extra={
              <Space>
                <Button icon={<ReloadOutlined />} onClick={() => { loadCategories(); loadList(); }} />
                <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                  新建话术
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
              locale={{ emptyText: <Empty description="暂无话术" /> }}
            />
          </Card>
        </Col>
      </Row>

      {/* 分类弹窗 */}
      <Modal
        title="新增话术分类"
        open={catOpen}
        onCancel={() => setCatOpen(false)}
        onOk={handleCreateCategory}
        confirmLoading={catSaving}
      >
        <Form form={catForm} layout="vertical">
          <Form.Item name="categoryName" label="分类名称" rules={[{ required: true, message: '请输入分类名称' }]}>
            <Input placeholder="如: 售前开场" maxLength={100} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input placeholder="分类说明" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 话术弹窗 */}
      <Modal
        title={editing ? '编辑话术' : '新建话术'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={640}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="title" label="话术标题" rules={[{ required: true, message: '请输入标题' }]}>
            <Input placeholder="如: 新品发布开场白" maxLength={200} />
          </Form.Item>
          <Form.Item name="categoryId" label="分类">
            <Select placeholder="选择分类" allowClear options={categories.map(c => ({ value: c.id, label: c.categoryName }))} />
          </Form.Item>
          <Form.Item name="content" label="话术内容" rules={[{ required: true, message: '请输入话术内容' }]}>
            <Input.TextArea rows={4} placeholder="话术内容, 支持 ${nickname} 变量" maxLength={5000} />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="speechType" label="类型" rules={[{ required: true, message: '请选择类型' }]}>
              <Select style={{ width: 140 }} options={Object.entries(speechTypeConfig).map(([v, c]) => ({ value: v, label: c.label }))} />
            </Form.Item>
            <Form.Item name="scenario" label="场景">
              <Input placeholder="如: 售后" style={{ width: 160 }} />
            </Form.Item>
            <Form.Item name="status" label="状态" initialValue="ENABLED">
              <Select
                style={{ width: 120 }}
                options={[
                  { value: 'ENABLED', label: '上架' },
                  { value: 'DISABLED', label: '下架' },
                ]}
              />
            </Form.Item>
          </Space>
          <Form.Item name="tags" label="标签 (逗号分隔)">
            <Input placeholder="如: 热销,新品" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}