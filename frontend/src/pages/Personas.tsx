/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Personas.tsx
 * Date : 2026/07/29
 * Author : hiylo
 * Contact : hiylo@live.com
 */

import { useCallback, useEffect, useState, type ReactNode } from 'react';
import {
  App,
  Avatar,
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
  Tooltip,
} from 'antd';
import {
  PlusOutlined,
  DeleteOutlined,
  EditOutlined,
  ReloadOutlined,
  SearchOutlined,
  UserOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';
import { isImeComposing } from '../utils/imeHelpers';

/** 人设 DTO (对齐后端 ScrmPersonaDto) */
interface ScrmPersona {
  id: string;
  personaId: string;
  accountId?: string;
  nickname?: string;
  avatarUrl?: string;
  gender?: string;
  ageRange?: string;
  region?: string;
  signature?: string;
  styleTags?: string;
  tags?: string;
  createTime?: string;
  updateTime?: string;
}

/** Spring Page 分页响应 */
interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/** 创建/编辑表单值 */
interface PersonaFormValues {
  personaId: string;
  nickname?: string;
  accountId?: string;
  gender?: string;
  ageRange?: string;
  region?: string;
  signature?: string;
  tags?: string;
}

/** 性别选项 */
const genderOptions = [
  { value: 'UNKNOWN', label: '未知' },
  { value: 'MALE', label: '男' },
  { value: 'FEMALE', label: '女' },
];

/** 性别 → Tag 颜色映射 */
const genderTagConfig: Record<string, { label: string; color: string }> = {
  MALE: { label: '男', color: 'blue' },
  FEMALE: { label: '女', color: 'pink' },
  UNKNOWN: { label: '未知', color: 'default' },
};

/**
 * 渲染性别 Tag
 */
const renderGenderTag = (value?: string) => {
  if (!value) return '-';
  const cfg = genderTagConfig[value] || { label: value, color: 'default' };
  return <Tag color={cfg.color}>{cfg.label}</Tag>;
};

/**
 * 将 JSON 数组字符串解析为 Tag 列表展示
 */
const renderTagsFromString = (value?: string): ReactNode => {
  if (!value) return '-';
  try {
    const arr = JSON.parse(value);
    if (Array.isArray(arr) && arr.length > 0) {
      return (
        <Space size={[0, 4]} wrap>
          {arr.map((tag, i) => (
            <Tag key={i} color="geekblue">{String(tag)}</Tag>
          ))}
        </Space>
      );
    }
  } catch {
    // 非 JSON, 直接展示原始字符串
    return <Tag color="geekblue">{value}</Tag>;
  }
  return '-';
};

/**
 * 人设管理页面
 * 提供人设的增删改查功能, 支持按昵称搜索。
 * 人设是社媒账号的虚拟身份, 用于自动化任务执行时的人格化呈现。
 */
export default function Personas() {
  const { message, modal } = App.useApp();
  const [personas, setPersonas] = useState<ScrmPersona[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [total, setTotal] = useState(0);
  // 搜索
  const [keyword, setKeyword] = useState('');
  // 弹窗
  const [modalOpen, setModalOpen] = useState(false);
  const [modalMode, setModalMode] = useState<'create' | 'edit'>('create');
  const [editingPersona, setEditingPersona] = useState<ScrmPersona | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<PersonaFormValues>();

  /** 拉取人设分页列表 */
  const fetchPersonas = useCallback(async () => {
    setLoading(true);
    try {
      const data = await apiClient.get<Page<ScrmPersona>>(
        `/scrm/personas/list?page=${page}&size=${size}`,
      );
      // 前端按昵称过滤 (后端 list 接口不支持关键字搜索)
      let list = data.content || [];
      if (keyword.trim()) {
        const kw = keyword.trim().toLowerCase();
        list = list.filter(
          p => p.nickname?.toLowerCase().includes(kw) || p.personaId.toLowerCase().includes(kw),
        );
      }
      setPersonas(list);
      setTotal(data.totalElements || 0);
    } catch {
      setPersonas([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [page, size, keyword]);

  useEffect(() => {
    fetchPersonas();
  }, [fetchPersonas]);

  /** 打开创建弹窗 */
  const openCreate = () => {
    setModalMode('create');
    setEditingPersona(null);
    form.resetFields();
    form.setFieldValue('gender', 'UNKNOWN');
    setModalOpen(true);
  };

  /** 打开编辑弹窗 */
  const openEdit = (record: ScrmPersona) => {
    setModalMode('edit');
    setEditingPersona(record);
    form.setFieldsValue({
      personaId: record.personaId,
      nickname: record.nickname,
      accountId: record.accountId,
      gender: record.gender || 'UNKNOWN',
      ageRange: record.ageRange,
      region: record.region,
      signature: record.signature,
      tags: record.tags,
    });
    setModalOpen(true);
  };

  /** 提交创建/编辑 */
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      if (modalMode === 'create') {
        await apiClient.post('/scrm/personas', values);
        message.success('人设创建成功');
      } else if (editingPersona) {
        await apiClient.put(`/scrm/personas/${editingPersona.personaId}`, values);
        message.success('人设更新成功');
      }
      setModalOpen(false);
      fetchPersonas();
    } catch {
      // 表单校验失败或请求失败
    } finally {
      setSubmitting(false);
    }
  };

  /** 删除人设 (带二次确认) */
  const handleDelete = (record: ScrmPersona) => {
    modal.confirm({
      title: '删除人设',
      content: `确认删除人设 "${record.nickname || record.personaId}" 吗?`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await apiClient.delete(`/scrm/personas/${record.personaId}`);
          message.success('人设已删除');
          fetchPersonas();
        } catch {
          // 错误已由拦截器提示
        }
      },
    });
  };

  /** 表格列定义 */
  const columns: ColumnsType<ScrmPersona> = [
    {
      title: '头像',
      dataIndex: 'avatarUrl',
      key: 'avatarUrl',
      width: 60,
      render: (value?: string, record?: ScrmPersona) => (
        <Avatar
          size={36}
          src={value}
          icon={<UserOutlined />}
        >
          {record?.nickname?.[0]?.toUpperCase()}
        </Avatar>
      ),
    },
    {
      title: '昵称',
      dataIndex: 'nickname',
      key: 'nickname',
      width: 140,
      render: (value?: string) => value || '-',
    },
    {
      title: '人设 ID',
      dataIndex: 'personaId',
      key: 'personaId',
      width: 160,
      ellipsis: true,
      render: (value?: string) => (
        <Tooltip title={value}>
          <code style={{ fontSize: 12 }}>{value || '-'}</code>
        </Tooltip>
      ),
    },
    {
      title: '归属账号',
      dataIndex: 'accountId',
      key: 'accountId',
      width: 100,
      render: (value?: string) => (value ? `#${value}` : '-'),
    },
    {
      title: '性别',
      dataIndex: 'gender',
      key: 'gender',
      width: 80,
      render: (value?: string) => renderGenderTag(value),
    },
    {
      title: '年龄段',
      dataIndex: 'ageRange',
      key: 'ageRange',
      width: 100,
      render: (value?: string) => value || '-',
    },
    {
      title: '地区',
      dataIndex: 'region',
      key: 'region',
      width: 120,
      render: (value?: string) => value || '-',
    },
    {
      title: '个性签名',
      dataIndex: 'signature',
      key: 'signature',
      ellipsis: { showTitle: false },
      render: (value?: string) =>
        value ? (
          <Tooltip title={value} overlayStyle={{ maxWidth: 400 }}>
            <span>{value}</span>
          </Tooltip>
        ) : (
          '-'
        ),
    },
    {
      title: '标签',
      dataIndex: 'tags',
      key: 'tags',
      width: 200,
      render: (value?: string) => renderTagsFromString(value),
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 170,
      render: (value?: string) =>
        value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-',
    },
    {
      title: '操作',
      key: 'actions',
      width: 140,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => openEdit(record)}
          >
            编辑
          </Button>
          <Button
            type="link"
            size="small"
            danger
            icon={<DeleteOutlined />}
            onClick={() => handleDelete(record)}
          >
            删除
          </Button>
        </Space>
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
              placeholder="搜索昵称或人设 ID"
              allowClear
              prefix={<SearchOutlined />}
              value={keyword}
              onChange={e => setKeyword(e.target.value)}
              onPressEnter={(e) => { if (!isImeComposing(e)) { setPage(0); fetchPersonas(); } }}
            />
          </Col>
          <Col flex="auto">
            <Space style={{ float: 'right' }} wrap>
              <Button
                icon={<ReloadOutlined />}
                onClick={() => { setPage(0); fetchPersonas(); }}
              >
                刷新
              </Button>
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={openCreate}
              >
                创建人设
              </Button>
            </Space>
          </Col>
        </Row>
      </Card>

      {/* 人设列表 */}
      <Card>
        <Table<ScrmPersona>
          rowKey="id"
          columns={columns}
          dataSource={personas}
          loading={loading}
          scroll={{ x: 1340 }}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="暂无人设数据"
                style={{ padding: 32 }}
              >
                <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                  创建人设
                </Button>
              </Empty>
            ),
          }}
          pagination={{
            current: page + 1,
            pageSize: size,
            total,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: t => `共 ${t} 条`,
            onChange: (p, s) => {
              setPage(p - 1);
              setSize(s);
            },
          }}
        />
      </Card>

      {/* 创建/编辑弹窗 */}
      <Modal
        title={modalMode === 'create' ? '创建人设' : '编辑人设'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        confirmLoading={submitting}
        destroyOnHidden
        width={560}
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="personaId"
            label="人设 ID"
            rules={[
              { required: true, message: '请输入人设 ID' },
              { max: 100, message: '人设 ID 不超过 100 个字符' },
            ]}
            tooltip="人设唯一标识, 创建后不可修改"
          >
            <Input
              placeholder="请输入人设 ID, 如 persona_001"
              maxLength={100}
              disabled={modalMode === 'edit'}
            />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="nickname"
                label="昵称"
                rules={[{ max: 200, message: '昵称不超过 200 个字符' }]}
              >
                <Input placeholder="请输入昵称" maxLength={200} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="accountId" label="归属账号 ID">
                <Input placeholder="请输入账号 ID" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="gender" label="性别">
                <Select options={genderOptions} placeholder="选择性别" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="ageRange"
                label="年龄段"
                rules={[{ max: 30, message: '年龄段不超过 30 个字符' }]}
              >
                <Input placeholder="如 25-30" maxLength={30} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item
            name="region"
            label="地区"
            rules={[{ max: 100, message: '地区不超过 100 个字符' }]}
          >
            <Input placeholder="如 广东深圳" maxLength={100} />
          </Form.Item>
          <Form.Item
            name="signature"
            label="个性签名"
            rules={[{ max: 500, message: '个性签名不超过 500 个字符' }]}
          >
            <Input.TextArea
              placeholder="请输入个性签名"
              maxLength={500}
              rows={2}
              showCount
            />
          </Form.Item>
          <Form.Item
            name="tags"
            label="自定义标签"
            tooltip='JSON 数组格式, 如 ["高消费","活跃用户"]'
          >
            <Input.TextArea
              placeholder='JSON 数组格式, 如 ["高消费","活跃用户"]'
              rows={2}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
