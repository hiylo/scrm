/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : KnowledgeBase.tsx
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
  Tree,
} from 'antd';
import type { TreeDataNode } from 'antd';
import {
  PlusOutlined,
  ReloadOutlined,
  FolderAddOutlined,
  SendOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

/** 文章类型映射 */
const articleTypeConfig: Record<string, { color: string; label: string }> = {
  ARTICLE: { color: 'blue', label: '文章' },
  FAQ: { color: 'cyan', label: 'FAQ' },
  GUIDE: { color: 'purple', label: '指南' },
};

/** 内容格式映射 */
const contentTypeConfig: Record<string, { color: string; label: string }> = {
  TEXT: { color: 'default', label: '文本' },
  MARKDOWN: { color: 'blue', label: 'Markdown' },
  HTML: { color: 'geekblue', label: 'HTML' },
};

/** 文章状态映射 */
const statusConfig: Record<string, { color: string; label: string }> = {
  DRAFT: { color: 'default', label: '草稿' },
  PUBLISHED: { color: 'green', label: '已发布' },
  DISABLED: { color: 'default', label: '已下架' },
};

/** 知识分类 */
interface ScrmKnowledgeCategory {
  id: string;
  categoryName: string;
  categoryCode: string;
  parentId?: string;
  status?: string;
  children?: ScrmKnowledgeCategory[];
}

/** 知识文章实体 */
interface ScrmKnowledgeArticle {
  id: string;
  title: string;
  articleCode?: string;
  categoryId?: string;
  categoryName?: string;
  articleType?: string;
  contentType?: string;
  summary?: string;
  content?: string;
  status: string;
  viewCount?: number;
  helpfulCount?: number;
  tags?: string;
  keywords?: string;
  createTime?: string;
}

/** 分类树节点 (antd TreeDataNode) */
function toTreeNodes(categories: ScrmKnowledgeCategory[]): TreeDataNode[] {
  return (categories || []).map(c => ({
    key: c.id,
    title: c.categoryName,
    children: c.children && c.children.length ? toTreeNodes(c.children) : undefined,
  }));
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
 * 知识库页
 * <p>
 * 分类树 + 文章列表 (按分类筛选) + 新建文章 + 发布, 覆盖知识内容管理全流程。
 * </p>
 *
 * @author Hsi Chu
 */
export default function KnowledgeBase() {
  const { message } = App.useApp();
  /** 分类 */
  const [categories, setCategories] = useState<ScrmKnowledgeCategory[]>([]);
  const [categoryId, setCategoryId] = useState<string | undefined>();
  /** 列表 */
  const [list, setList] = useState<ScrmKnowledgeArticle[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const pageSize = 10;
  /** 分类弹窗 */
  const [catOpen, setCatOpen] = useState(false);
  const [catSaving, setCatSaving] = useState(false);
  const [catForm] = Form.useForm();
  /** 文章弹窗 */
  const [editing, setEditing] = useState<ScrmKnowledgeArticle | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  /** 分类下拉选项 (顶层分类 + 子分类扁平化) */
  const categoryOptions = useMemo(() => {
    const flatten = (nodes: ScrmKnowledgeCategory[]): ScrmKnowledgeCategory[] =>
      nodes.flatMap(n => [n, ...(n.children ? flatten(n.children) : [])]);
    return flatten(categories).map(c => ({ value: c.id, label: c.categoryName }));
  }, [categories]);

  /** 加载分类树 */
  const loadCategories = useCallback(async () => {
    try {
      const data = await apiClient.get<ScrmKnowledgeCategory[]>('/scrm/knowledge-base/categories/tree');
      setCategories(data || []);
    } catch {
      setCategories([]);
    }
  }, []);

  /** 加载文章列表 */
  const loadList = useCallback(async (targetPage = page, cat = categoryId) => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(pageSize) });
      if (cat) params.set('categoryId', cat);
      const data = await apiClient.get<Page<ScrmKnowledgeArticle>>(`/scrm/knowledge-base/articles/list?${params.toString()}`);
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
      await apiClient.post('/scrm/knowledge-base/categories', values);
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
    form.setFieldsValue({ articleType: 'ARTICLE', contentType: 'TEXT' });
    setOpen(true);
  };

  /** 提交文章 */
  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await apiClient.put(`/scrm/knowledge-base/articles/${editing.id}`, values);
        message.success('文章已更新');
      } else {
        await apiClient.post('/scrm/knowledge-base/articles', values);
        message.success('文章已创建');
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

  /** 发布文章 */
  const handlePublish = async (record: ScrmKnowledgeArticle) => {
    try {
      await apiClient.post(`/scrm/knowledge-base/articles/${record.id}/publish`);
      message.success('文章已发布');
      loadList(page, categoryId);
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 表格列 */
  const columns: ColumnsType<ScrmKnowledgeArticle> = useMemo(() => [
    { title: '文章标题', dataIndex: 'title', render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
    { title: '文章编码', dataIndex: 'articleCode', width: 130, render: (v?: string) => v || '-' },
    { title: '分类', dataIndex: 'categoryName', width: 110, render: (v?: string) => v || '-' },
    {
      title: '类型',
      dataIndex: 'articleType',
      width: 90,
      render: (v?: string) => {
        if (!v) return '-';
        const cfg = articleTypeConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '浏览量', dataIndex: 'viewCount', width: 90, render: (v?: number) => v ?? 0 },
    { title: '有帮助', dataIndex: 'helpfulCount', width: 90, render: (v?: number) => v ?? 0 },
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
          {r.status !== 'PUBLISHED' && (
            <Button type="link" size="small" icon={<SendOutlined />} onClick={() => handlePublish(r)}>
              发布
            </Button>
          )}
        </Space>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  return (
    <div className="knowledge-base-page">
      <Row gutter={16}>
        <Col span={5}>
          <Card
            title="知识分类"
            size="small"
            extra={
              <Button type="text" size="small" icon={<FolderAddOutlined />} onClick={() => { catForm.resetFields(); setCatOpen(true); }}>
                新增分类
              </Button>
            }
          >
            <Tree
              treeData={toTreeNodes(categories)}
              defaultExpandAll
              selectedKeys={categoryId ? [categoryId] : []}
              onSelect={keys => {
                setCategoryId((keys[0] as string) || undefined);
                setPage(0);
              }}
            />
          </Card>
        </Col>
        <Col span={19}>
          <Card
            title="知识文章"
            extra={
              <Space>
                <Button icon={<ReloadOutlined />} onClick={() => { loadCategories(); loadList(); }} />
                <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                  新建文章
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
              locale={{ emptyText: <Empty description="暂无文章" /> }}
            />
          </Card>
        </Col>
      </Row>

      {/* 分类弹窗 */}
      <Modal
        title="新增知识分类"
        open={catOpen}
        onCancel={() => setCatOpen(false)}
        onOk={handleCreateCategory}
        confirmLoading={catSaving}
      >
        <Form form={catForm} layout="vertical">
          <Form.Item name="categoryName" label="分类名称" rules={[{ required: true, message: '请输入分类名称' }]}>
            <Input placeholder="如: 产品资料" maxLength={200} />
          </Form.Item>
          <Form.Item name="categoryCode" label="分类编码" rules={[{ required: true, message: '请输入分类编码' }]}>
            <Input placeholder="如: PRODUCT" maxLength={50} />
          </Form.Item>
          <Form.Item name="parentId" label="父分类 ID">
            <Select
              placeholder="顶层分类留空"
              allowClear
              options={categoryOptions}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 文章弹窗 */}
      <Modal
        title={editing ? '编辑文章' : '新建文章'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={680}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="title" label="文章标题" rules={[{ required: true, message: '请输入文章标题' }]}>
            <Input placeholder="如: 产品使用常见问题" maxLength={500} />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="categoryId" label="分类">
              <Select style={{ width: 180 }} placeholder="选择分类" allowClear options={categoryOptions} />
            </Form.Item>
            <Form.Item name="articleType" label="文章类型" rules={[{ required: true }]}>
              <Select style={{ width: 140 }} options={Object.entries(articleTypeConfig).map(([v, c]) => ({ value: v, label: c.label }))} />
            </Form.Item>
            <Form.Item name="contentType" label="内容格式">
              <Select style={{ width: 140 }} options={Object.entries(contentTypeConfig).map(([v, c]) => ({ value: v, label: c.label }))} />
            </Form.Item>
          </Space>
          <Form.Item name="summary" label="摘要">
            <Input.TextArea rows={2} placeholder="文章摘要" maxLength={1000} />
          </Form.Item>
          <Form.Item name="content" label="正文内容" rules={[{ required: true, message: '请输入正文内容' }]}>
            <Input.TextArea rows={6} placeholder="正文内容 (支持 Markdown/HTML)" />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="tags" label="标签 (逗号分隔)">
              <Input placeholder="如: 产品,FAQ" style={{ width: 200 }} />
            </Form.Item>
            <Form.Item name="keywords" label="关键词">
              <Input placeholder="SEO 关键词" style={{ width: 200 }} />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </div>
  );
}