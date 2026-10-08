/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Assets.tsx
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
  Typography,
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

const { Paragraph } = Typography;

/** 素材类型映射 */
const assetTypeConfig: Record<string, { color: string; label: string }> = {
  IMAGE: { color: 'cyan', label: '图片' },
  VIDEO: { color: 'purple', label: '视频' },
  AUDIO: { color: 'gold', label: '音频' },
  DOCUMENT: { color: 'blue', label: '文档' },
  FILE: { color: 'default', label: '文件' },
};

/** 审核状态映射 */
const reviewStatusConfig: Record<string, { color: string; label: string }> = {
  DRAFT: { color: 'default', label: '未送审' },
  PENDING: { color: 'orange', label: '待审' },
  APPROVED: { color: 'green', label: '已过审' },
  REJECTED: { color: 'red', label: '已驳回' },
};

/** 素材状态映射 */
const statusConfig: Record<string, { color: string; label: string }> = {
  DRAFT: { color: 'default', label: '草稿' },
  PUBLISHED: { color: 'green', label: '已发布' },
  DISABLED: { color: 'default', label: '已下架' },
};

/** 素材分类 */
interface ScrmAssetCategory {
  id: string;
  categoryName: string;
  status?: string;
  description?: string;
  children?: ScrmAssetCategory[];
}

/** 素材实体 */
interface ScrmAsset {
  id: string;
  assetName: string;
  assetCode: string;
  assetType: string;
  categoryId?: string;
  categoryName?: string;
  reviewStatus: string;
  status: string;
  fileSizeBytes?: number;
  fileUrl?: string;
  mimeType?: string;
  useCount?: number;
  createTime?: string;
}

/** 分类树节点 (antd TreeDataNode) */
function toTreeNodes(categories: ScrmAssetCategory[]): TreeDataNode[] {
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

/** 文件大小格式化: 2048 -> 2.0 KB, 10485760 -> 10.0 MB */
function formatBytes(bytes?: number): string {
  if (bytes == null) return '-';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let value = bytes;
  let i = 0;
  while (value >= 1024 && i < units.length - 1) {
    value /= 1024;
    i += 1;
  }
  return `${value.toFixed(1)} ${units[i]}`;
}

/**
 * 素材库页
 * <p>
 * 分类树 + 素材列表 (按分类筛选) + 上传素材 + 发布, 支持分类管理与素材全流程。
 * </p>
 *
 * @author Hsi Chu
 */
export default function Assets() {
  const { message } = App.useApp();
  /** 列表 */
  const [list, setList] = useState<ScrmAsset[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const pageSize = 10;
  /** 分类 */
  const [categories, setCategories] = useState<ScrmAssetCategory[]>([]);
  const [categoryId, setCategoryId] = useState<string | undefined>();
  /** 分类弹窗 */
  const [catOpen, setCatOpen] = useState(false);
  const [catSaving, setCatSaving] = useState(false);
  const [catForm] = Form.useForm();
  /** 素材弹窗 */
  const [editing, setEditing] = useState<ScrmAsset | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  /** 加载分类树 */
  const loadCategories = useCallback(async () => {
    try {
      const data = await apiClient.get<ScrmAssetCategory[]>('/scrm/assets/categories/tree');
      setCategories(data || []);
    } catch {
      setCategories([]);
    }
  }, []);

  /** 加载素材列表 */
  const loadList = useCallback(async (targetPage = page, cat = categoryId) => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(pageSize) });
      if (cat) params.set('categoryId', cat);
      const data = await apiClient.get<Page<ScrmAsset>>(`/scrm/assets/list?${params.toString()}`);
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
      await apiClient.post('/scrm/assets/categories', values);
      message.success('分类已创建');
      setCatOpen(false);
      loadCategories();
    } catch {
      // 拦截器已弹出错误
    } finally {
      setCatSaving(false);
    }
  };

  /** 打开上传弹窗 */
  const openUpload = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ assetType: 'IMAGE', categoryId });
    setOpen(true);
  };

  /** 提交上传 */
  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await apiClient.put(`/scrm/assets/${editing.id}`, values);
        message.success('素材已更新');
      } else {
        await apiClient.post('/scrm/assets/upload', values);
        message.success('素材已上传');
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

  /** 发布素材 */
  const handlePublish = async (record: ScrmAsset) => {
    try {
      await apiClient.post(`/scrm/assets/${record.id}/publish`);
      message.success('素材已发布');
      loadList(page, categoryId);
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 表格列 */
  const columns: ColumnsType<ScrmAsset> = useMemo(() => [
    { title: '素材名称', dataIndex: 'assetName', render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
    { title: '素材编码', dataIndex: 'assetCode', width: 130, render: (v?: string) => v || '-' },
    {
      title: '类型',
      dataIndex: 'assetType',
      width: 90,
      render: (v: string) => {
        const cfg = assetTypeConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '分类', dataIndex: 'categoryName', width: 110, render: (v?: string) => v || '-' },
    { title: '大小', dataIndex: 'fileSizeBytes', width: 90, render: (v?: number) => formatBytes(v) },
    {
      title: '审核状态',
      dataIndex: 'reviewStatus',
      width: 90,
      render: (v: string) => {
        const cfg = reviewStatusConfig[v] || { color: 'default', label: v };
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
    <div className="assets-page">
      <Row gutter={16}>
        <Col span={5}>
          <Card
            title="素材分类"
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
            title="素材列表"
            extra={
              <Space>
                <Button icon={<ReloadOutlined />} onClick={() => { loadCategories(); loadList(); }} />
                <Button type="primary" icon={<PlusOutlined />} onClick={openUpload}>
                  上传素材
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
              locale={{ emptyText: <Empty description="暂无素材" /> }}
            />
          </Card>
        </Col>
      </Row>

      {/* 分类弹窗 */}
      <Modal
        title="新增素材分类"
        open={catOpen}
        onCancel={() => setCatOpen(false)}
        onOk={handleCreateCategory}
        confirmLoading={catSaving}
      >
        <Form form={catForm} layout="vertical">
          <Form.Item name="categoryName" label="分类名称" rules={[{ required: true, message: '请输入分类名称' }]}>
            <Input placeholder="如: 产品图" maxLength={100} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input placeholder="分类说明" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 素材弹窗 */}
      <Modal
        title={editing ? '编辑素材' : '上传素材'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={640}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="assetName" label="素材名称" rules={[{ required: true, message: '请输入素材名称' }]}>
            <Input placeholder="如: 618 主视觉图" maxLength={500} />
          </Form.Item>
          <Form.Item name="assetType" label="素材类型" rules={[{ required: true, message: '请选择素材类型' }]}>
            <Select options={Object.entries(assetTypeConfig).map(([v, c]) => ({ value: v, label: c.label }))} />
          </Form.Item>
          <Form.Item name="categoryId" label="分类">
            <Select placeholder="选择分类" allowClear options={categories.map(c => ({ value: c.id, label: c.categoryName }))} />
          </Form.Item>
          <Form.Item name="fileUrl" label="文件 URL" rules={[{ required: true, message: '请输入文件 URL' }]}>
            <Input placeholder="https://... (对象存储或 CDN 地址)" maxLength={1000} />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="fileSizeBytes" label="文件大小 (字节)">
              <Input placeholder="可选" type="number" />
            </Form.Item>
            <Form.Item name="mimeType" label="MIME 类型">
              <Input placeholder="如 image/png" style={{ width: 200 }} />
            </Form.Item>
          </Space>
          <Form.Item name="tags" label="标签 (逗号分隔)">
            <Input placeholder="如: 618,主视觉" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="素材描述" maxLength={1000} />
          </Form.Item>
          {editing?.assetCode && (
            <Paragraph type="secondary" style={{ fontSize: 12 }}>
              素材编码: {editing.assetCode} / 使用 {editing.useCount ?? 0} 次
            </Paragraph>
          )}
        </Form>
      </Modal>
    </div>
  );
}