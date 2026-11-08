/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : AiProvidersPanel.tsx
 * Date : 2026/11/07 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
import { useCallback, useEffect, useState } from 'react';
import {
  App,
  AutoComplete,
  Badge,
  Button,
  Col,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
} from 'antd';
import {
  ApiOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  DeleteOutlined,
  EditOutlined,
  ExclamationCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
  StarOutlined,
} from '@ant-design/icons';
import { apiClient } from '../api/client';

/** 模型选项：从 provider 拉取清单生成（可手输，网关模型时常变化） */
const toModelOptions = (models: AiModelInfo[] | undefined) =>
  (models || []).map(m => ({
    value: m.name,
    label: `${m.name}${m.image ? '（多模态）' : m.text ? '（文本）' : ''}`,
  }));

/** 模型能力信息（从 provider /v1/models 拉取） */
interface AiModelInfo {
  name: string;
  text: boolean;
  image: boolean;
  embedding: boolean;
}

/** AI Provider 配置 */
interface AiProvider {
  id?: string;
  providerName: string;
  providerType: string;
  apiBaseUrl: string;
  chatPath?: string;
  healthPath?: string;
  apiKey?: string;
  hasApiKey?: boolean;
  capability?: string;
  defaultModel?: string;
  temperature?: number;
  maxTokens?: number;
  enabled?: boolean;
  defaultText?: boolean;
  defaultImage?: boolean;
  priority?: number;
  connectionStatus?: string;
  lastTestedAt?: string;
  models?: AiModelInfo[];
  remark?: string;
  createTime?: string;
  updateTime?: string;
  version?: number;
}

/** 表单值 */
interface ProviderFormValues {
  providerName: string;
  providerType: string;
  apiBaseUrl: string;
  chatPath: string;
  healthPath: string;
  apiKey?: string;
  capabilities: string[];
  defaultModel?: string;
  temperature: number;
  maxTokens: number;
  priority: number;
  enabled: boolean;
  defaultText: boolean;
  defaultImage: boolean;
  remark?: string;
}

/** Provider 类型选项 */
const PROVIDER_TYPES = [
  { value: 'OPENAI_LLM', label: 'OpenAI 兼容 LLM' },
  { value: 'OLLAMA', label: 'Ollama' },
  { value: 'MAGPIE', label: 'Magpie 网关' },
  { value: 'OPENAI_VISION', label: 'OpenAI 兼容多模态' },
  { value: 'LOCAL_LLM', label: '本地 llama-server' },
];

/** 能力选项 */
const CAPABILITY_OPTIONS = [
  { value: 'text', label: '文本（对话/客服/文案）' },
  { value: 'image', label: '图像（证照 OCR 多模态）' },
  { value: 'embedding', label: '向量（预留）' },
];

/** 连接状态展示 */
const statusMeta: Record<string, { color: string; text: string; icon?: React.ReactNode }> = {
  CONNECTED: { color: 'success', text: '已连接', icon: <CheckCircleOutlined /> },
  DISCONNECTED: { color: 'error', text: '未连接', icon: <CloseCircleOutlined /> },
  UNKNOWN: { color: 'default', text: '未测试', icon: <ExclamationCircleOutlined /> },
};

/**
 * AI Provider 管理面板（系统管理 → 设置 → AI Providers）。
 * <p>
 * 统一维护模型接入点：配置 base-url / api-key / 能力（text|image）/ 默认模型 /
 * 优先级 / 默认标记，支持新增、编辑、删除、连通测试、拉取模型列表、设默认文本/
 * 多模态、启停。所有配置落库，变更立即刷新后端路由快照——文本任务与证照 OCR
 * 无需重启即可切换模型。
 * </p>
 */
export default function AiProvidersPanel() {
  const { message, modal } = App.useApp();
  const [providers, setProviders] = useState<AiProvider[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<AiProvider | null>(null);
  const [saving, setSaving] = useState(false);
  const [testingId, setTestingId] = useState<string | null>(null);
  const [fetchingModelsId, setFetchingModelsId] = useState<string | null>(null);
  const [form] = Form.useForm<ProviderFormValues>();

  /** 加载 provider 列表 */
  const loadProviders = useCallback(async () => {
    setLoading(true);
    try {
      const data = await apiClient.get<AiProvider[]>('/scrm/ai-providers');
      setProviders(data || []);
    } catch {
      // apiClient 已弹出错误提示
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadProviders();
  }, [loadProviders]);

  /** 打开新增面板 */
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({
      providerType: 'OPENAI_LLM',
      chatPath: '/v1/chat/completions',
      healthPath: '/v1/models',
      capabilities: ['text'],
      temperature: 0.7,
      maxTokens: 500,
      priority: 100,
      enabled: true,
      defaultText: false,
      defaultImage: false,
    });
    setModalOpen(true);
  };

  /** 打开编辑面板（apiKey 掩码值不写入表单，避免回传明文） */
  const openEdit = (record: AiProvider) => {
    setEditing(record);
    form.resetFields();
    form.setFieldsValue({
      providerName: record.providerName,
      providerType: record.providerType,
      apiBaseUrl: record.apiBaseUrl,
      chatPath: record.chatPath || '/v1/chat/completions',
      healthPath: record.healthPath || '/v1/models',
      apiKey: record.hasApiKey ? '********' : '',
      capabilities: (record.capability || 'text').split(',').filter(Boolean),
      defaultModel: record.defaultModel || undefined,
      temperature: record.temperature ?? 0.7,
      maxTokens: record.maxTokens ?? 500,
      priority: record.priority ?? 100,
      enabled: record.enabled ?? true,
      defaultText: record.defaultText ?? false,
      defaultImage: record.defaultImage ?? false,
      remark: record.remark,
    });
    setModalOpen(true);
  };

  /** 保存（新增/更新） */
  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      setSaving(true);
      const payload = {
        providerName: values.providerName,
        providerType: values.providerType,
        apiBaseUrl: values.apiBaseUrl,
        chatPath: values.chatPath || '/v1/chat/completions',
        healthPath: values.healthPath || '/v1/models',
        // 掩码值（********）不回传，后端跳过保留原密钥
        apiKey: values.apiKey && values.apiKey !== '********' ? values.apiKey : undefined,
        capability: (values.capabilities || ['text']).join(','),
        defaultModel: values.defaultModel || undefined,
        temperature: values.temperature ?? 0.7,
        maxTokens: values.maxTokens ?? 500,
        priority: values.priority ?? 100,
        enabled: values.enabled ?? true,
        defaultText: values.defaultText ?? false,
        defaultImage: values.defaultImage ?? false,
        remark: values.remark,
      };
      if (editing?.id) {
        await apiClient.put(`/scrm/ai-providers/${editing.id}`, payload);
        message.success('Provider 已更新');
      } else {
        await apiClient.post('/scrm/ai-providers', payload);
        message.success('Provider 已创建');
      }
      setModalOpen(false);
      await loadProviders();
    } catch (err) {
      // apiClient 已弹出错误提示
    } finally {
      setSaving(false);
    }
  };

  /** 删除 provider */
  const handleDelete = (record: AiProvider) => {
    modal.confirm({
      title: `删除 Provider「${record.providerName}」？`,
      content: '删除后该模型接入点将不可用（不影响已生成的数据），确定继续？',
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await apiClient.delete(`/scrm/ai-providers/${record.id}`);
        message.success('已删除');
        await loadProviders();
      },
    });
  };

  /** 测试连通性 */
  const handleTest = async (record: AiProvider) => {
    if (!record.id) return;
    setTestingId(record.id);
    try {
      const updated = await apiClient.post<AiProvider>(`/scrm/ai-providers/${record.id}/test`);
      const status = updated?.connectionStatus;
      if (status === 'CONNECTED') {
        message.success(`「${record.providerName}」连接成功`);
      } else {
        message.error(`「${record.providerName}」连接失败，请检查地址/密钥`);
      }
      await loadProviders();
    } catch {
      await loadProviders();
    } finally {
      setTestingId(null);
    }
  };

  /** 拉取模型列表 */
  const handleFetchModels = async (record: AiProvider) => {
    if (!record.id) return;
    setFetchingModelsId(record.id);
    try {
      const models = await apiClient.post<AiModelInfo[]>(`/scrm/ai-providers/${record.id}/models`);
      const withImage = models?.filter(m => m.image).length || 0;
      message.success(`「${record.providerName}」模型清单已更新：共 ${models?.length || 0} 个（含多模态 ${withImage} 个）`);
      await loadProviders();
    } catch {
      await loadProviders();
    } finally {
      setFetchingModelsId(null);
    }
  };

  /** 设默认文本 provider */
  const handleSetDefaultText = async (record: AiProvider) => {
    await apiClient.post(`/scrm/ai-providers/${record.id}/default-text`);
    message.success(`「${record.providerName}」已设为默认文本模型`);
    await loadProviders();
  };

  /** 设默认多模态 provider */
  const handleSetDefaultImage = async (record: AiProvider) => {
    await apiClient.post(`/scrm/ai-providers/${record.id}/default-image`);
    message.success(`「${record.providerName}」已设为默认多模态模型（证照 OCR 走它）`);
    await loadProviders();
  };

  /** 启停 */
  const handleToggleEnabled = async (record: AiProvider, enabled: boolean) => {
    await apiClient.post(`/scrm/ai-providers/${record.id}/enabled?enabled=${enabled}`);
    message.success(enabled ? '已启用' : '已停用');
    await loadProviders();
  };

  /** 表格列 */
  const columns = [
    {
      title: '名称',
      dataIndex: 'providerName',
      key: 'providerName',
      render: (v: string, r: AiProvider) => (
        <Space>
          <span>{v}</span>
          {r.defaultText && <Tag color="blue">默认文本</Tag>}
          {r.defaultImage && <Tag color="purple">默认多模态</Tag>}
        </Space>
      ),
    },
    {
      title: '类型',
      dataIndex: 'providerType',
      key: 'providerType',
      width: 140,
      render: (v: string) => PROVIDER_TYPES.find(t => t.value === v)?.label || v,
    },
    {
      title: '地址',
      dataIndex: 'apiBaseUrl',
      key: 'apiBaseUrl',
      ellipsis: true,
    },
    {
      title: '模型',
      dataIndex: 'defaultModel',
      key: 'defaultModel',
      width: 200,
      render: (v: string, r: AiProvider) => {
        if (v) return v;
        const models = r.models || [];
        if (models.length > 0) {
          return (
            <Tooltip title={models.map(m => `${m.name}${m.image ? ' (多模态)' : ''}`).join('\n')}>
              <Tag>{models.length} 个可用</Tag>
            </Tooltip>
          );
        }
        return <span style={{ color: '#999' }}>未设置</span>;
      },
    },
    {
      title: '能力',
      dataIndex: 'capability',
      key: 'capability',
      width: 160,
      render: (v: string) => (
        <Space size={4} wrap>
          {(v || 'text').split(',').filter(Boolean).map(c => (
            <Tag key={c} color={c === 'image' ? 'purple' : c === 'text' ? 'blue' : 'default'}>
              {c === 'text' ? '文本' : c === 'image' ? '多模态' : c}
            </Tag>
          ))}
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'connectionStatus',
      key: 'connectionStatus',
      width: 110,
      render: (v: string) => {
        const meta = statusMeta[v] || statusMeta.UNKNOWN;
        return (
          <Tag color={meta.color}>
            {meta.icon} {meta.text}
          </Tag>
        );
      },
    },
    {
      title: '启用',
      dataIndex: 'enabled',
      key: 'enabled',
      width: 80,
      render: (v: boolean, r: AiProvider) => (
        <Switch checked={v} size="small" onChange={checked => handleToggleEnabled(r, checked)} />
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 320,
      render: (_: unknown, r: AiProvider) => (
        <Space size={4} wrap>
          <Button size="small" icon={<ApiOutlined />} loading={testingId === r.id}
            onClick={() => handleTest(r)}>
            测试
          </Button>
          <Button size="small" icon={<ReloadOutlined />} loading={fetchingModelsId === r.id}
            onClick={() => handleFetchModels(r)}>
            模型
          </Button>
          {!r.defaultText && (
            <Tooltip title="文本对话/客服/文案路由目标">
              <Button size="small" icon={<StarOutlined />} onClick={() => handleSetDefaultText(r)}>
                默认文本
              </Button>
            </Tooltip>
          )}
          {!r.defaultImage && (
            <Tooltip title="证照 OCR 多模态路由目标">
              <Button size="small" type="primary" ghost icon={<StarOutlined />}
                onClick={() => handleSetDefaultImage(r)}>
                默认多模态
              </Button>
            </Tooltip>
          )}
          <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(r)} />
          <Popconfirm title="删除该 Provider？" onConfirm={() => handleDelete(r)} okText="删除" cancelText="取消">
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <Col>
          <Space>
            <span style={{ fontWeight: 600 }}>AI Provider 配置</span>
            <Badge status="processing" text="文本/多模态任务按能力自动路由" />
          </Space>
        </Col>
        <Col>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新增 Provider
          </Button>
        </Col>
      </Row>

      <Table
        rowKey="id"
        columns={columns}
        dataSource={providers}
        loading={loading}
        pagination={false}
        size="middle"
      />

      <Modal
        title={editing ? '编辑 AI Provider' : '新增 AI Provider'}
        open={modalOpen}
        onOk={handleSave}
        onCancel={() => setModalOpen(false)}
        confirmLoading={saving}
        width={720}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="providerName" label="名称"
                rules={[{ required: true, message: '请输入名称' }]}>
                <Input placeholder="如：Magpie 网关" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="providerType" label="类型"
                rules={[{ required: true, message: '请选择类型' }]}>
                <Select options={PROVIDER_TYPES} placeholder="选择类型" />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="apiBaseUrl" label="API 基础地址"
            rules={[{ required: true, message: '请输入基础地址' }]}>
            <Input placeholder="http://192.168.1.150:3425" />
          </Form.Item>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="chatPath" label="对话补全路径">
                <Input placeholder="/v1/chat/completions" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="healthPath" label="健康/模型探测路径">
                <Input placeholder="/v1/models" />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="apiKey" label="API 密钥"
                extra={editing ? '留空且保持掩码值则保留原密钥' : '本地无鉴权网关可留空'}>
                <Input.Password placeholder="可留空" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="defaultModel" label="默认模型"
                extra={`可从右侧「拉取模型」获取候选；共 ${(editing?.models || []).length} 个可用`}>
                <AutoComplete
                  options={toModelOptions(editing?.models)}
                  placeholder={editing?.models?.length
                    ? '选择或输入模型名'
                    : '如 group/sensenova-pool；可点表格中「模型」拉取候选'}
                  filterOption={(input, option) =>
                    ((option?.value as string) || '').toLowerCase()
                      .includes(input.toLowerCase())}
                />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="capabilities" label="能力"
            rules={[{ required: true, message: '请至少选择一项能力' }]}>
            <Select mode="multiple" options={CAPABILITY_OPTIONS} placeholder="选择能力" />
          </Form.Item>

          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="temperature" label="温度">
                <InputNumber min={0} max={2} step={0.1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="maxTokens" label="最大 token">
                <InputNumber min={1} max={12000} step={100} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="priority" label="优先级（小优先）">
                <InputNumber min={1} max={999} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="enabled" label="启用" valuePropName="checked">
                <Switch checkedChildren="开启" unCheckedChildren="关闭" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="defaultText" label="默认文本" valuePropName="checked">
                <Switch checkedChildren="是" unCheckedChildren="否" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="defaultImage" label="默认多模态" valuePropName="checked">
                <Switch checkedChildren="是" unCheckedChildren="否" />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} placeholder="用途/接入说明（可选）" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}