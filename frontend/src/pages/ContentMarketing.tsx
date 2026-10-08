/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ContentMarketing.tsx
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
  Modal,
  Select,
  Space,
  Table,
  Tag,
} from 'antd';
import {
  PlusOutlined,
  ReloadOutlined,
  SendOutlined,
  AuditOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

/** 内容类型映射 */
const contentTypeConfig: Record<string, { color: string; label: string }> = {
  ARTICLE: { color: 'blue', label: '文章' },
  VIDEO: { color: 'purple', label: '视频' },
  IMAGE: { color: 'cyan', label: '图片' },
  AUDIO: { color: 'gold', label: '音频' },
  H5: { color: 'geekblue', label: 'H5' },
};

/** 内容状态映射 */
const statusConfig: Record<string, { color: string; label: string }> = {
  DRAFT: { color: 'default', label: '草稿' },
  PENDING_REVIEW: { color: 'orange', label: '待审核' },
  APPROVED: { color: 'green', label: '已通过' },
  REJECTED: { color: 'red', label: '已驳回' },
  PUBLISHED: { color: 'blue', label: '已发布' },
};

/** 内容实体 */
interface ScrmContent {
  id: string;
  title: string;
  contentType: string;
  summary?: string;
  authorName?: string;
  viewCount?: number;
  likeCount?: number;
  shareCount?: number;
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
 * 内容营销页
 * <p>
 * 内容 CRUD + 提交审核 + 审核结论 (通过/驳回), 覆盖内容生产到发布的全流程。
 * </p>
 *
 * @author Hsi Chu
 */
export default function ContentMarketing() {
  const { message } = App.useApp();
  /** 列表 */
  const [list, setList] = useState<ScrmContent[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const pageSize = 10;
  /** 弹窗 */
  const [editing, setEditing] = useState<ScrmContent | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  /** 审核弹窗 */
  const [reviewTarget, setReviewTarget] = useState<ScrmContent | null>(null);
  const [reviewing, setReviewing] = useState(false);
  const [reviewForm] = Form.useForm();

  /** 加载列表 */
  const loadList = useCallback(async (targetPage = page) => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(pageSize) });
      const data = await apiClient.get<Page<ScrmContent>>(`/scrm/contents/list?${params.toString()}`);
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
    form.setFieldsValue({ contentType: 'ARTICLE' });
    setOpen(true);
  };

  /** 提交 */
  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await apiClient.put(`/scrm/contents/${editing.id}`, values);
        message.success('内容已更新');
      } else {
        await apiClient.post('/scrm/contents', values);
        message.success('内容已创建');
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

  /** 提交审核 */
  const handleSubmitReview = async (record: ScrmContent) => {
    try {
      await apiClient.post(`/scrm/contents/${record.id}/submit-review`);
      message.success('内容已提交审核');
      loadList();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 打开审核弹窗 */
  const openReview = (record: ScrmContent) => {
    setReviewTarget(record);
    reviewForm.resetFields();
    reviewForm.setFieldsValue({ approved: true });
    setReviewing(false);
  };

  /** 提交审核结论 */
  const handleReview = async () => {
    if (!reviewTarget) return;
    const values = await reviewForm.validateFields();
    setReviewing(true);
    try {
      await apiClient.post('/scrm/contents/review', {
        contentId: reviewTarget.id,
        approved: values.approved,
        reviewComment: values.reviewComment,
      });
      message.success(values.approved ? '内容已通过' : '内容已驳回');
      setReviewTarget(null);
      loadList();
    } catch {
      // 拦截器已弹出错误
    } finally {
      setReviewing(false);
    }
  };

  /** 表格列 */
  const columns: ColumnsType<ScrmContent> = useMemo(() => [
    { title: '标题', dataIndex: 'title', render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
    {
      title: '类型',
      dataIndex: 'contentType',
      width: 90,
      render: (v: string) => {
        const cfg = contentTypeConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '摘要', dataIndex: 'summary', ellipsis: true, render: (v?: string) => v || '-' },
    { title: '作者', dataIndex: 'authorName', width: 100, render: (v?: string) => v || '-' },
    { title: '浏览量', dataIndex: 'viewCount', width: 90, render: (v?: number) => (v ?? 0).toLocaleString() },
    { title: '点赞', dataIndex: 'likeCount', width: 80, render: (v?: number) => v ?? 0 },
    { title: '分享', dataIndex: 'shareCount', width: 80, render: (v?: number) => v ?? 0 },
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
      width: 160,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          {r.status === 'DRAFT' && (
            <Button type="link" size="small" icon={<SendOutlined />} onClick={() => handleSubmitReview(r)}>
              提交审核
            </Button>
          )}
          {r.status === 'PENDING_REVIEW' && (
            <Button type="link" size="small" icon={<AuditOutlined />} onClick={() => openReview(r)}>
              审核
            </Button>
          )}
        </Space>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  return (
    <div className="content-marketing-page">
      <Card
        title="内容营销"
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => loadList()} />
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              新建内容
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

      {/* 内容弹窗 */}
      <Modal
        title={editing ? '编辑内容' : '新建内容'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={640}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="title" label="内容标题" rules={[{ required: true, message: '请输入内容标题' }]}>
            <Input placeholder="如: 618 活动预热文章" maxLength={200} />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="contentType" label="内容类型" rules={[{ required: true }]}>
              <Select style={{ width: 160 }} options={Object.entries(contentTypeConfig).map(([v, c]) => ({ value: v, label: c.label }))} />
            </Form.Item>
            <Form.Item name="category" label="分类">
              <Input placeholder="如: 活动" style={{ width: 140 }} />
            </Form.Item>
          </Space>
          <Form.Item name="summary" label="摘要">
            <Input.TextArea rows={2} placeholder="内容摘要" maxLength={500} />
          </Form.Item>
          <Form.Item name="bodyContent" label="正文">
            <Input.TextArea rows={5} placeholder="正文/富文本内容" />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="coverImage" label="封面 URL">
              <Input placeholder="https://..." style={{ width: 220 }} />
            </Form.Item>
            <Form.Item name="mediaUrl" label="媒体 URL">
              <Input placeholder="https://..." style={{ width: 220 }} />
            </Form.Item>
          </Space>
          <Space size={16} wrap>
            <Form.Item name="tags" label="标签 (逗号分隔)">
              <Input placeholder="如: 618,活动" style={{ width: 200 }} />
            </Form.Item>
            <Form.Item name="targetAudience" label="目标受众">
              <Input placeholder="如: vip,新客" style={{ width: 200 }} />
            </Form.Item>
          </Space>
        </Form>
      </Modal>

      {/* 审核弹窗 */}
      <Modal
        title={reviewTarget ? `审核内容 - ${reviewTarget.title}` : '审核内容'}
        open={!!reviewTarget}
        onCancel={() => setReviewTarget(null)}
        onOk={handleReview}
        confirmLoading={reviewing}
      >
        <Form form={reviewForm} layout="vertical">
          <Form.Item name="approved" label="审核结论" rules={[{ required: true }]}>
            <Select
              options={[
                { value: true, label: '通过' },
                { value: false, label: '驳回' },
              ]}
            />
          </Form.Item>
          <Form.Item name="reviewComment" label="审核意见">
            <Input.TextArea rows={2} placeholder="审核意见" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
