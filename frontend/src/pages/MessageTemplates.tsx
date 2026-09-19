/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : MessageTemplates.tsx
 * Date : 2026/07/26
 * Author : hiylo
 * Contact : hiylo@live.com
 */

import { useEffect, useState, useCallback, useMemo } from 'react';
import {
  App,
  Alert,
  Button,
  Card,
  Col,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Space,
  Statistic,
  Switch,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  ReloadOutlined,
  EyeOutlined,
  CopyOutlined,
  InfoCircleOutlined,
  MessageOutlined,
  TagsOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { apiClient } from '../api/client';
import { useDebounce } from '../hooks/useDebounce';

const { Paragraph, Text } = Typography;

/** 消息模板实体 */
interface ScrmMessageTemplate {
  id: string;
  templateName: string;
  category: string; // greeting / promotion / service / follow_up / apology
  content: string;
  platformType?: string;
  variables?: string; // JSON 数组字符串
  enabled: boolean;
  sortOrder: number;
  createdBy?: string;
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

/** 分类映射 */
const categoryConfig: Record<string, { label: string; color: string }> = {
  greeting: { label: '问候', color: 'blue' },
  promotion: { label: '促销', color: 'red' },
  service: { label: '服务', color: 'green' },
  follow_up: { label: '跟进', color: 'orange' },
  apology: { label: '致歉', color: 'purple' },
};

/** 平台类型映射 (与 Customers / Accounts 一致) */
const platformConfig: Record<string, { label: string; color: string }> = {
  wework: { label: '企业微信', color: 'blue' },
};

/** 支持的变量列表 */
const supportedVariables: Array<{ name: string; desc: string }> = [
  { name: 'nickname', desc: '客户昵称' },
  { name: 'platformType', desc: '平台类型' },
  { name: 'customerName', desc: '客户姓名' },
  { name: 'ownerName', desc: '归属人姓名' },
];

/** 分类下拉选项 */
const categoryOptions = Object.entries(categoryConfig).map(([value, cfg]) => ({
  value,
  label: cfg.label,
}));

/** 平台下拉选项 */
const platformOptions = Object.entries(platformConfig).map(([value, cfg]) => ({
  value,
  label: cfg.label,
}));

/** 表单值类型 */
interface TemplateFormValues {
  templateName: string;
  category: string;
  content: string;
  platformType?: string;
  sortOrder?: number;
}

/**
 * 解析模板的 variables 字段为变量名数组
 * variables 是 JSON 数组字符串, 例如 '["nickname","platformType"]'
 */
const parseVariables = (variables?: string): string[] => {
  if (!variables) return [];
  try {
    const parsed = JSON.parse(variables);
    return Array.isArray(parsed) ? parsed.map((v) => String(v)) : [];
  } catch {
    return [];
  }
};

/**
 * 从模板内容中实时提取 {{variable}} 形式的变量名
 */
const extractVariablesFromContent = (content?: string): string[] => {
  if (!content) return [];
  const matches = content.matchAll(/\{\{(\w+)\}\}/g);
  const set = new Set<string>();
  for (const match of matches) {
    if (match[1]) set.add(match[1]);
  }
  return Array.from(set);
};

/**
 * 消息模板管理页面
 * 支持创建 / 编辑 / 删除 / 启用禁用 / 渲染预览
 */
export default function MessageTemplates() {
  const { message, modal } = App.useApp();
  const [templates, setTemplates] = useState<ScrmMessageTemplate[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [total, setTotal] = useState(0);
  // 搜索与筛选
  const [keyword, setKeyword] = useState('');
  const debouncedKeyword = useDebounce(keyword, 300);
  const [categoryFilter, setCategoryFilter] = useState<string | undefined>(undefined);
  const [enabledFilter, setEnabledFilter] = useState<boolean | undefined>(undefined);
  // 分类统计
  const [categoryStats, setCategoryStats] = useState<Record<string, number>>({});
  // 新建/编辑弹窗
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ScrmMessageTemplate | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<TemplateFormValues>();
  // 渲染预览弹窗
  const [renderOpen, setRenderOpen] = useState(false);
  const [renderingTemplate, setRenderingTemplate] = useState<ScrmMessageTemplate | null>(null);
  const [variableValues, setVariableValues] = useState<Record<string, string>>({});
  const [renderResult, setRenderResult] = useState<string>('');
  const [rendering, setRendering] = useState(false);
  // 启用/禁用切换中
  const [togglingId, setTogglingId] = useState<string | null>(null);

  /** 拉取模板分页列表 */
  const fetchTemplates = useCallback(async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      params.set('page', String(page));
      params.set('size', String(size));
      if (debouncedKeyword) params.set('keyword', debouncedKeyword);
      if (categoryFilter) params.set('category', categoryFilter);
      if (enabledFilter !== undefined) params.set('enabled', String(enabledFilter));
      const data = await apiClient.get<Page<ScrmMessageTemplate>>(
        `/scrm/message-templates?${params.toString()}`,
      );
      setTemplates(data.content || []);
      setTotal(data.totalElements || 0);
    } catch {
      // 错误已由 axios 拦截器统一提示
      setTemplates([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [page, size, debouncedKeyword, categoryFilter, enabledFilter]);

  /** 拉取全量模板用于分类统计 */
  const fetchCategoryStats = useCallback(async () => {
    try {
      const params = new URLSearchParams();
      params.set('page', '0');
      params.set('size', '10000');
      const data = await apiClient.get<Page<ScrmMessageTemplate>>(
        `/scrm/message-templates?${params.toString()}`,
      );
      const stats: Record<string, number> = {};
      (data.content || []).forEach((t) => {
        stats[t.category] = (stats[t.category] || 0) + 1;
      });
      setCategoryStats(stats);
    } catch {
      // 统计获取失败不影响主流程
    }
  }, []);

  useEffect(() => {
    fetchTemplates();
  }, [fetchTemplates]);

  useEffect(() => {
    fetchCategoryStats();
  }, [fetchCategoryStats]);

  /** 打开新建弹窗 */
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ sortOrder: 0 });
    setModalOpen(true);
  };

  /** 打开编辑弹窗 */
  const openEdit = (record: ScrmMessageTemplate) => {
    setEditing(record);
    form.setFieldsValue({
      templateName: record.templateName,
      category: record.category,
      content: record.content,
      platformType: record.platformType,
      sortOrder: record.sortOrder,
    });
    setModalOpen(true);
  };

  /** 提交新建/编辑表单 */
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      if (editing) {
        await apiClient.put(`/scrm/message-templates/${editing.id}`, values);
        message.success('模板已更新');
      } else {
        await apiClient.post('/scrm/message-templates', values);
        message.success('模板已创建');
      }
      setModalOpen(false);
      fetchTemplates();
      fetchCategoryStats();
    } catch {
      // 表单校验失败或请求失败; 请求失败已由 axios 拦截器统一提示
    } finally {
      setSubmitting(false);
    }
  };

  /** 删除模板 (带二次确认) */
  const handleDelete = (record: ScrmMessageTemplate) => {
    modal.confirm({
      title: '删除模板',
      content: `确认删除模板 "${record.templateName}" 吗? 此操作不可恢复。`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await apiClient.delete(`/scrm/message-templates/${record.id}`);
          message.success('模板已删除');
          fetchTemplates();
          fetchCategoryStats();
        } catch {
          // 错误已由拦截器提示
        }
      },
    });
  };

  /** 切换启用状态 */
  const handleToggleEnabled = async (record: ScrmMessageTemplate, checked: boolean) => {
    setTogglingId(record.id);
    try {
      const action = checked ? 'enable' : 'disable';
      await apiClient.post(`/scrm/message-templates/${record.id}/${action}`);
      message.success(checked ? '模板已启用' : '模板已禁用');
      fetchTemplates();
    } catch {
      // 错误已由拦截器提示
    } finally {
      setTogglingId(null);
    }
  };

  /** 打开渲染预览弹窗 */
  const openRender = (record: ScrmMessageTemplate) => {
    setRenderingTemplate(record);
    setVariableValues({});
    setRenderResult('');
    setRenderOpen(true);
  };

  /** 调用 API 渲染模板 */
  const handleRender = async () => {
    if (!renderingTemplate) return;
    setRendering(true);
    try {
      const result = await apiClient.post<string>(
        `/scrm/message-templates/${renderingTemplate.id}/render`,
        variableValues,
      );
      setRenderResult(result || '');
      message.success('渲染成功');
    } catch {
      // 错误已由拦截器提示
    } finally {
      setRendering(false);
    }
  };

  /** 复制渲染结果到剪贴板 */
  const handleCopyResult = () => {
    if (!renderResult) return;
    navigator.clipboard
      .writeText(renderResult)
      .then(() => message.success('已复制到剪贴板'))
      .catch(() => message.error('复制失败'));
  };

  /** 当前渲染模板的变量列表 (解析 variables JSON 字符串) */
  const variableNames = useMemo(
    () => parseVariables(renderingTemplate?.variables),
    [renderingTemplate],
  );

  /** 实时监听表单内容字段, 提取变量 */
  const contentValue = Form.useWatch('content', form);
  const extractedVariables = useMemo(
    () => extractVariablesFromContent(contentValue),
    [contentValue],
  );

  /** 表格列定义 */
  const columns: ColumnsType<ScrmMessageTemplate> = [
    {
      title: '模板名称',
      dataIndex: 'templateName',
      key: 'templateName',
      ellipsis: true,
      width: 180,
    },
    {
      title: '分类',
      dataIndex: 'category',
      key: 'category',
      width: 100,
      render: (value: string) => {
        const cfg = categoryConfig[value] || { label: value, color: 'default' };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '内容预览',
      dataIndex: 'content',
      key: 'content',
      ellipsis: true,
      width: 240,
      render: (value: string) => (value ? value.slice(0, 60) : '-'),
    },
    {
      title: '平台类型',
      dataIndex: 'platformType',
      key: 'platformType',
      width: 110,
      render: (value?: string) => {
        if (!value) return <Tag>通用</Tag>;
        const cfg = platformConfig[value] || { label: value, color: 'default' };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '启用状态',
      dataIndex: 'enabled',
      key: 'enabled',
      width: 100,
      render: (_: boolean, record) => (
        <Switch
          checked={record.enabled}
          loading={togglingId === record.id}
          onChange={(checked) => handleToggleEnabled(record, checked)}
        />
      ),
    },
    {
      title: '排序',
      dataIndex: 'sortOrder',
      key: 'sortOrder',
      width: 80,
    },
    {
      title: '操作',
      key: 'actions',
      width: 200,
      render: (_, record) => (
        <Space size="small">
          <Tooltip title="渲染预览">
            <Button
              type="link"
              size="small"
              icon={<EyeOutlined />}
              onClick={() => openRender(record)}
            >
              渲染
            </Button>
          </Tooltip>
          <Tooltip title="编辑模板">
            <Button
              type="link"
              size="small"
              icon={<EditOutlined />}
              onClick={() => openEdit(record)}
            >
              编辑
            </Button>
          </Tooltip>
          <Tooltip title="删除模板">
            <Button
              type="link"
              size="small"
              danger
              icon={<DeleteOutlined />}
              onClick={() => handleDelete(record)}
            >
              删除
            </Button>
          </Tooltip>
        </Space>
      ),
    },
  ];

  return (
    <div>
      {/* 分类统计卡片 */}
      <Card style={{ marginBottom: 16 }}>
        <Row gutter={16}>
          <Col xs={12} sm={8} md={4}>
            <Statistic
              title="模板总数"
              value={Object.values(categoryStats).reduce((a, b) => a + b, 0)}
              prefix={<MessageOutlined style={{ color: '#1677ff' }} />}
            />
          </Col>
          {Object.entries(categoryConfig).map(([key, cfg]) => (
            <Col xs={12} sm={8} md={4} key={key}>
              <Statistic
                title={cfg.label}
                value={categoryStats[key] || 0}
                prefix={<TagsOutlined style={{ color: tagColorHex(cfg.color) }} />}
              />
            </Col>
          ))}
        </Row>
      </Card>

      {/* 顶部工具栏 */}
      <Card style={{ marginBottom: 16 }}>
        <Row gutter={[16, 16]} align="middle">
          <Col xs={24} sm={8} md={6}>
            <Input.Search
              placeholder="搜索模板名称"
              allowClear
              onSearch={(v) => {
                setKeyword(v);
                setPage(0);
              }}
              onChange={(e) => setKeyword(e.target.value)}
            />
          </Col>
          <Col xs={12} sm={6} md={4}>
            <Select
              placeholder="分类"
              allowClear
              style={{ width: '100%' }}
              options={categoryOptions}
              onChange={(v) => {
                setCategoryFilter(v);
                setPage(0);
              }}
            />
          </Col>
          <Col xs={12} sm={6} md={4}>
            <Select
              placeholder="启用状态"
              allowClear
              style={{ width: '100%' }}
              options={[
                { value: true, label: '已启用' },
                { value: false, label: '已禁用' },
              ]}
              onChange={(v) => {
                setEnabledFilter(v);
                setPage(0);
              }}
            />
          </Col>
          <Col flex="auto">
            <Space style={{ float: 'right' }}>
              <Button icon={<ReloadOutlined />} onClick={fetchTemplates}>
                刷新
              </Button>
              <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                新建模板
              </Button>
            </Space>
          </Col>
        </Row>
      </Card>

      {/* 模板列表 */}
      <Card>
        <Table<ScrmMessageTemplate>
          rowKey="id"
          columns={columns}
          dataSource={templates}
          loading={loading}
          scroll={{ x: 1100 }}
          locale={{
            // 空状态展示新建模板 CTA 按钮, 复用工具栏的 openCreate 处理函数
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="暂无模板数据"
                style={{ padding: 32 }}
              >
                <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                  新建模板
                </Button>
              </Empty>
            ),
          }}
          expandable={{
            expandedRowRender: (record) => (
              <Paragraph style={{ whiteSpace: 'pre-wrap', margin: 0 }}>
                {record.content}
              </Paragraph>
            ),
            rowExpandable: (record) => !!record.content,
          }}
          pagination={{
            current: page + 1,
            pageSize: size,
            total,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p, s) => {
              setPage(p - 1);
              setSize(s);
            },
          }}
        />
      </Card>

      {/* 新建/编辑弹窗 */}
      <Modal
        title={editing ? '编辑模板' : '新建模板'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        confirmLoading={submitting}
        destroyOnHidden
        width={640}
      >
        <Form form={form} layout="vertical" preserve={false}>
          {/* 变量说明 */}
          <Alert
            type="info"
            showIcon
            icon={<InfoCircleOutlined />}
            message="支持的模板变量"
            description={
              <Space size={[16, 4]} wrap>
                {supportedVariables.map((v) => (
                  <Text key={v.name} code>
                    {`{{${v.name}}}`}
                  </Text>
                ))}
                <Text type="secondary">{supportedVariables.map((v) => v.desc).join(' / ')}</Text>
              </Space>
            }
            style={{ marginBottom: 16 }}
          />
          <Form.Item
            name="templateName"
            label="模板名称"
            rules={[{ required: true, message: '请输入模板名称' }]}
          >
            <Input placeholder="请输入模板名称" maxLength={100} />
          </Form.Item>
          <Form.Item
            name="category"
            label="分类"
            rules={[{ required: true, message: '请选择分类' }]}
          >
            <Select placeholder="请选择分类" options={categoryOptions} />
          </Form.Item>
          <Form.Item
            name="content"
            label="内容"
            rules={[{ required: true, message: '请输入模板内容' }]}
            extra="使用 {{变量名}} 进行变量插值, 如 {{nickname}}"
          >
            <Input.TextArea
              rows={4}
              placeholder="支持变量插值: {{nickname}}, {{platformType}}, {{customerName}}, {{ownerName}}"
            />
          </Form.Item>
          {/* 实时提取到的变量列表 */}
          {extractedVariables.length > 0 && (
            <div style={{ marginBottom: 16 }}>
              <Text type="secondary" style={{ fontSize: 12 }}>
                已识别变量:
              </Text>
              <Space size={[8, 4]} wrap style={{ marginLeft: 8 }}>
                {extractedVariables.map((v) => (
                  <Tag key={v} color="blue">
                    {`{{${v}}}`}
                  </Tag>
                ))}
              </Space>
            </div>
          )}
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="platformType" label="平台类型">
                <Select placeholder="留空表示通用" allowClear options={platformOptions} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="sortOrder" label="排序值" initialValue={0}>
                <InputNumber min={0} style={{ width: '100%' }} placeholder="默认 0" />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>

      {/* 渲染预览弹窗 */}
      <Modal
        title="渲染预览"
        open={renderOpen}
        onCancel={() => setRenderOpen(false)}
        width={680}
        destroyOnHidden
        footer={[
          <Button key="cancel" onClick={() => setRenderOpen(false)}>
            关闭
          </Button>,
          <Button key="render" type="primary" loading={rendering} onClick={handleRender}>
            渲染
          </Button>,
        ]}
      >
        {renderingTemplate && (
          <div>
            {/* 模板原始内容 */}
            <Card size="small" title="模板原始内容" style={{ marginBottom: 16 }}>
              <Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>
                {renderingTemplate.content}
              </Paragraph>
            </Card>
            {/* 变量输入区 (根据 variables 字段动态生成) */}
            <Card size="small" title="变量输入" style={{ marginBottom: 16 }}>
              {variableNames.length > 0 ? (
                <Row gutter={[16, 12]}>
                  {variableNames.map((name) => (
                    <Col span={12} key={name}>
                      <div style={{ marginBottom: 4, fontSize: 12, color: '#666' }}>
                        {`{{${name}}}`}
                      </div>
                      <Input
                        placeholder={`请输入 ${name} 的示例值`}
                        value={variableValues[name] ?? ''}
                        onChange={(e) =>
                          setVariableValues((prev) => ({
                            ...prev,
                            [name]: e.target.value,
                          }))
                        }
                      />
                    </Col>
                  ))}
                </Row>
              ) : (
                <Paragraph type="secondary" style={{ marginBottom: 0 }}>
                  该模板未声明变量, 可直接点击渲染。
                </Paragraph>
              )}
            </Card>
            {/* 渲染结果 (带复制按钮) */}
            <Card
              size="small"
              title="渲染结果"
              extra={
                renderResult ? (
                  <Tooltip title="复制结果">
                    <Button
                      size="small"
                      type="text"
                      icon={<CopyOutlined />}
                      onClick={handleCopyResult}
                    >
                      复制
                    </Button>
                  </Tooltip>
                ) : null
              }
            >
              {renderResult ? (
                <Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>
                  {renderResult}
                </Paragraph>
              ) : (
                <Paragraph type="secondary" style={{ marginBottom: 0 }}>
                  点击 "渲染" 按钮查看结果
                </Paragraph>
              )}
            </Card>
          </div>
        )}
      </Modal>
    </div>
  );
}

/** 将 antd Tag 颜色名映射为十六进制色值, 用于统计卡片图标着色 */
function tagColorHex(color: string): string {
  const map: Record<string, string> = {
    blue: '#1677ff',
    green: '#52c41a',
    red: '#ff4d4f',
    orange: '#fa8c16',
    purple: '#722ed1',
    cyan: '#13c2c2',
    black: '#262626',
    default: '#8c8c8c',
  };
  return map[color] || '#8c8c8c';
}
