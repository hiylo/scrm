/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : CustomerTags.tsx
 * Date : 2026/07/27
 * Author : hiylo
 * Contact : hiylo@live.com
 */

import { useCallback, useEffect, useState } from 'react';
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
  Space,
  Table,
  Tag,
} from 'antd';
import {
  PlusOutlined,
  DeleteOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';
import { isImeComposing } from '../utils/imeHelpers';

/** 客户标签实体 */
interface ScrmCustomerTag {
  id: string;
  customerId: string;
  tagKey: string;
  tagValue?: string;
  createTime?: string;
}

/** 添加标签表单值 */
interface TagFormValues {
  tagKey: string;
  tagValue?: string;
}

/**
 * 客户标签管理页面
 * 按客户 ID 查询标签 / 添加标签 / 删除标签
 */
export default function CustomerTags() {
  const { message, modal } = App.useApp();
  const [tags, setTags] = useState<ScrmCustomerTag[]>([]);
  const [loading, setLoading] = useState(false);
  // 当前查询的客户 ID
  const [customerId, setCustomerId] = useState<string | undefined>(undefined);
  // 搜索输入框值
  const [customerInput, setCustomerInput] = useState<string>('');
  // 添加标签弹窗
  const [modalOpen, setModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<TagFormValues>();

  /** 拉取客户标签列表 */
  const fetchTags = useCallback(async () => {
    if (customerId == null) {
      setTags([]);
      return;
    }
    setLoading(true);
    try {
      const data = await apiClient.get<ScrmCustomerTag[]>(
        `/scrm/customers/${customerId}/tags`,
      );
      setTags(data || []);
    } catch {
      // 错误已由 axios 拦截器统一提示
      setTags([]);
    } finally {
      setLoading(false);
    }
  }, [customerId]);

  useEffect(() => {
    fetchTags();
  }, [fetchTags]);

  /** 应用客户 ID 查询 */
  const applyCustomerSearch = () => {
    const trimmed = customerInput.trim();
    if (!trimmed) {
      message.warning('请输入有效的客户 ID');
      return;
    }
    setCustomerId(trimmed);
  };

  /** 打开添加标签弹窗 */
  const openCreate = () => {
    form.resetFields();
    setModalOpen(true);
  };

  /** 提交添加标签 (tagKey/tagValue 通过 query 参数传递) */
  const handleSubmit = async () => {
    if (customerId == null) {
      message.warning('请先输入客户 ID');
      return;
    }
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      const params = new URLSearchParams();
      params.set('tagKey', values.tagKey);
      if (values.tagValue) params.set('tagValue', values.tagValue);
      await apiClient.post(`/scrm/customers/${customerId}/tags?${params.toString()}`);
      message.success('标签已添加');
      setModalOpen(false);
      fetchTags();
    } catch {
      // 表单校验失败或请求失败; 请求失败已由 axios 拦截器统一提示
    } finally {
      setSubmitting(false);
    }
  };

  /** 删除标签 (带二次确认) */
  const handleDelete = (record: ScrmCustomerTag) => {
    modal.confirm({
      title: '删除标签',
      content: `确认删除标签 "${record.tagKey}" 吗?`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await apiClient.delete(
            `/scrm/customers/${record.customerId}/tags/${encodeURIComponent(record.tagKey)}`,
          );
          message.success('标签已删除');
          fetchTags();
        } catch {
          // 错误已由拦截器提示
        }
      },
    });
  };

  /** 表格列定义 */
  const columns: ColumnsType<ScrmCustomerTag> = [
    {
      title: '标签键',
      dataIndex: 'tagKey',
      key: 'tagKey',
      width: 200,
      render: (value: string) => <Tag color="blue">{value}</Tag>,
    },
    {
      title: '标签值',
      dataIndex: 'tagValue',
      key: 'tagValue',
      ellipsis: true,
      render: (value?: string) => value || '-',
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 180,
      render: (value?: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-'),
    },
    {
      title: '操作',
      key: 'actions',
      width: 120,
      render: (_, record) => (
        <Button
          type="link"
          size="small"
          danger
          icon={<DeleteOutlined />}
          onClick={() => handleDelete(record)}
        >
          删除
        </Button>
      ),
    },
  ];

  return (
    <div>
      {/* 顶部工具栏 */}
      <Card style={{ marginBottom: 16 }}>
        <Row gutter={[16, 16]} align="middle">
          <Col xs={24} sm={10} md={7}>
            <Input
              placeholder="输入客户 ID 查询标签"
              allowClear
              value={customerInput}
              onChange={(e) => setCustomerInput(e.target.value)}
              onPressEnter={(e) => { if (!isImeComposing(e)) applyCustomerSearch(); }}
              prefix={<SearchOutlined />}
            />
          </Col>
          <Col xs={12} sm={6} md={3}>
            <Button type="primary" icon={<SearchOutlined />} onClick={applyCustomerSearch}>
              查询
            </Button>
          </Col>
          <Col flex="auto">
            <Space style={{ float: 'right' }} wrap>
              <Button
                icon={<ReloadOutlined />}
                onClick={fetchTags}
                disabled={customerId == null}
              >
                刷新
              </Button>
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={openCreate}
                disabled={customerId == null}
              >
                添加标签
              </Button>
            </Space>
          </Col>
        </Row>
      </Card>

      {/* 标签列表 */}
      <Card>
        <Table<ScrmCustomerTag>
          rowKey="id"
          columns={columns}
          dataSource={tags}
          loading={loading}
          scroll={{ x: 'max-content' }}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={customerId == null ? '请输入客户 ID 查询标签' : '暂无标签数据'}
                style={{ padding: 32 }}
              >
                {customerId != null && (
                  <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                    添加标签
                  </Button>
                )}
              </Empty>
            ),
          }}
          pagination={{
            pageSize: 10,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
          }}
        />
      </Card>

      {/* 添加标签弹窗 */}
      <Modal
        title="添加标签"
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        confirmLoading={submitting}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="tagKey"
            label="标签键"
            rules={[
              { required: true, message: '请输入标签键' },
              { max: 100, message: '标签键不超过 100 个字符' },
            ]}
          >
            <Input placeholder="请输入标签键, 如 消费层级" maxLength={100} />
          </Form.Item>
          <Form.Item
            name="tagValue"
            label="标签值"
            rules={[{ max: 200, message: '标签值不超过 200 个字符' }]}
          >
            <Input placeholder="请输入标签值, 如 高" maxLength={200} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
