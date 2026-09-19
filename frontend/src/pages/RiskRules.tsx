/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : RiskRules.tsx
 * Date : 2026/07/26
 * Author : hiylo
 * Contact : hiylo@live.com
 */

import { useEffect, useState, useCallback, useMemo } from 'react';
import type { ReactNode } from 'react';
import {
  Alert,
  App,
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
  ExperimentOutlined,
  FireOutlined,
  WarningOutlined,
  ExclamationOutlined,
  InfoOutlined,
  LoadingOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';
import { useDebounce } from '../hooks/useDebounce';

const { Paragraph, Text } = Typography;

/** 标签配置类型 */
interface TagConfig {
  label: string;
  color: string;
  icon?: ReactNode;
}

/** 风控规则实体 */
interface ScrmRiskRule {
  id: string;
  ruleName: string;
  ruleCode: string;
  conditionExpression: string; // SpEL 表达式
  riskLevel: string; // LOW / MEDIUM / HIGH / CRITICAL
  signalType: string; // frequency_overflow / keyword_match / time_anomaly
  description?: string;
  enabled: boolean;
  priority: number;
  action: string; // ALERT / PAUSE_ACCOUNT / STOP_CAMPAIGN
  createTime?: string;
}

/** 手动评估请求体 */
interface ConversationEventCallbackDto {
  conversationId?: string;
  customerId?: string;
  accountId?: string;
  platformType?: string;
  messageContent?: string;
  messageCountInWindow?: number;
  eventTime?: string;
}

/** 风控信号实体 */
interface ScrmRiskSignal {
  id: string;
  ruleId: string;
  personaId?: string;
  accountId?: string;
  signalType: string;
  riskLevel: string;
  detail?: string;
  triggeredAt: string;
}

/** Spring Page 分页响应 */
interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/** 风险等级映射 (含图标) */
const riskLevelConfig: Record<string, TagConfig> = {
  LOW: { label: '低风险', color: 'blue', icon: <InfoOutlined /> },
  MEDIUM: { label: '中风险', color: 'orange', icon: <ExclamationOutlined /> },
  HIGH: { label: '高风险', color: 'red', icon: <WarningOutlined /> },
  CRITICAL: { label: '严重', color: 'magenta', icon: <FireOutlined /> },
};

/** 触发动作映射 */
const actionConfig: Record<string, TagConfig> = {
  ALERT: { label: '告警', color: 'orange' },
  PAUSE_ACCOUNT: { label: '暂停账号', color: 'red' },
  STOP_CAMPAIGN: { label: '停止任务', color: 'magenta' },
};

/** 信号类型映射 */
const signalTypeConfig: Record<string, TagConfig> = {
  frequency_overflow: { label: '频次溢出', color: 'gold' },
  keyword_match: { label: '关键词命中', color: 'volcano' },
  time_anomaly: { label: '时间异常', color: 'geekblue' },
};

/** 风险等级下拉选项 */
const riskLevelOptions = Object.entries(riskLevelConfig).map(([value, cfg]) => ({
  value,
  label: cfg.label,
}));

/** 信号类型下拉选项 */
const signalTypeOptions = Object.entries(signalTypeConfig).map(([value, cfg]) => ({
  value,
  label: cfg.label,
}));

/** 触发动作下拉选项 */
const actionOptions = Object.entries(actionConfig).map(([value, cfg]) => ({
  value,
  label: cfg.label,
}));

/** 表单值类型 */
interface RuleFormValues {
  ruleName: string;
  ruleCode: string;
  conditionExpression: string;
  riskLevel: string;
  signalType: string;
  action?: string;
  priority?: number;
  description?: string;
}

/** 评估表单值类型 */
interface EvaluateFormValues {
  platformType?: string;
  messageContent?: string;
  messageCountInWindow?: number;
  accountId?: string;
}

/**
 * 渲染标签 (带 fallback, 可选图标)
 */
const renderTag = (value: string, config: Record<string, TagConfig>) => {
  const cfg = config[value] || { label: value, color: 'default' };
  return cfg.icon ? (
    <Tag color={cfg.color} icon={cfg.icon}>
      {cfg.label}
    </Tag>
  ) : (
    <Tag color={cfg.color}>{cfg.label}</Tag>
  );
};

/**
 * 风控规则管理页面
 * 支持创建 / 编辑 / 删除 / 启用禁用 / 手动评估测试
 */
export default function RiskRules() {
  const { message, modal } = App.useApp();
  const [rules, setRules] = useState<ScrmRiskRule[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [total, setTotal] = useState(0);
  // 搜索与筛选 (keyword 为输入框值, debouncedKeyword 为防抖后用于过滤的值)
  const [keyword, setKeyword] = useState('');
  const debouncedKeyword = useDebounce(keyword, 300);
  const [riskLevelFilter, setRiskLevelFilter] = useState<string | undefined>(undefined);
  const [enabledFilter, setEnabledFilter] = useState<boolean | undefined>(undefined);
  // 新建/编辑弹窗
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ScrmRiskRule | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<RuleFormValues>();
  // 手动评估弹窗
  const [evaluateOpen, setEvaluateOpen] = useState(false);
  const [evaluating, setEvaluating] = useState(false);
  const [evaluateForm] = Form.useForm<EvaluateFormValues>();
  const [evaluateResult, setEvaluateResult] = useState<ScrmRiskSignal[]>([]);
  // 评估是否已执行 (用于展示 Alert)
  const [evaluateHasRun, setEvaluateHasRun] = useState(false);
  // 启用/禁用切换中
  const [togglingId, setTogglingId] = useState<string | null>(null);

  /** 拉取规则分页列表 */
  const fetchRules = useCallback(async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      params.set('page', String(page));
      params.set('size', String(size));
      if (enabledFilter !== undefined) params.set('enabled', String(enabledFilter));
      const data = await apiClient.get<Page<ScrmRiskRule>>(
        `/scrm/risk-rules?${params.toString()}`,
      );
      // 客户端按关键词与风险等级过滤 (后端未提供这些查询参数)
      let list = data.content || [];
      if (debouncedKeyword) {
        const kw = debouncedKeyword.toLowerCase();
        list = list.filter(
          (r) =>
            r.ruleName.toLowerCase().includes(kw) ||
            r.ruleCode.toLowerCase().includes(kw),
        );
      }
      if (riskLevelFilter) {
        list = list.filter((r) => r.riskLevel === riskLevelFilter);
      }
      setRules(list);
      setTotal(data.totalElements || 0);
    } catch {
      // 错误已由 axios 拦截器统一提示
      setRules([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [page, size, debouncedKeyword, riskLevelFilter, enabledFilter]);

  useEffect(() => {
    fetchRules();
  }, [fetchRules]);

  // 防抖关键词变化时, 重置到第一页 (避免在高页码搜索时返回空列表)
  useEffect(() => {
    setPage(0);
  }, [debouncedKeyword]);

  /** 规则 ID -> 规则名称 映射, 用于评估结果表格展示规则名称 */
  const ruleNameMap = useMemo(() => {
    const m = new Map<string, string>();
    rules.forEach((r) => m.set(r.id, r.ruleName));
    return m;
  }, [rules]);

  /** 打开新建弹窗 */
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ priority: 100 });
    setModalOpen(true);
  };

  /** 打开编辑弹窗 */
  const openEdit = (record: ScrmRiskRule) => {
    setEditing(record);
    form.setFieldsValue({
      ruleName: record.ruleName,
      ruleCode: record.ruleCode,
      conditionExpression: record.conditionExpression,
      riskLevel: record.riskLevel,
      signalType: record.signalType,
      action: record.action,
      priority: record.priority,
      description: record.description,
    });
    setModalOpen(true);
  };

  /** 提交新建/编辑表单 */
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      if (editing) {
        await apiClient.put(`/scrm/risk-rules/${editing.id}`, values);
        message.success('规则已更新');
      } else {
        await apiClient.post('/scrm/risk-rules', values);
        message.success('规则已创建');
      }
      setModalOpen(false);
      fetchRules();
    } catch {
      // 表单校验失败或请求失败; 请求失败已由 axios 拦截器统一提示
    } finally {
      setSubmitting(false);
    }
  };

  /** 删除规则 (带二次确认) */
  const handleDelete = (record: ScrmRiskRule) => {
    modal.confirm({
      title: '删除规则',
      content: `确认删除规则 "${record.ruleName}" 吗? 此操作不可恢复。`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await apiClient.delete(`/scrm/risk-rules/${record.id}`);
          message.success('规则已删除');
          fetchRules();
        } catch {
          // 错误已由拦截器提示
        }
      },
    });
  };

  /** 切换启用状态 */
  const handleToggleEnabled = async (record: ScrmRiskRule, checked: boolean) => {
    setTogglingId(record.id);
    try {
      const action = checked ? 'enable' : 'disable';
      await apiClient.post(`/scrm/risk-rules/${record.id}/${action}`);
      message.success(checked ? '规则已启用' : '规则已禁用');
      fetchRules();
    } catch {
      // 错误已由拦截器提示
    } finally {
      setTogglingId(null);
    }
  };

  /** 打开手动评估弹窗 */
  const openEvaluate = () => {
    evaluateForm.resetFields();
    setEvaluateResult([]);
    setEvaluateHasRun(false);
    setEvaluateOpen(true);
  };

  /** 提交手动评估 */
  const handleEvaluate = async () => {
    try {
      const values = await evaluateForm.validateFields();
      // 评估请求前确认参数: 消息内容为空时提示用户
      if (!values.messageContent || !values.messageContent.trim()) {
        message.warning('请输入消息内容后再进行评估');
        return;
      }
      setEvaluating(true);
      const payload: ConversationEventCallbackDto = {
        ...values,
        eventTime: dayjs().toISOString(),
      };
      const result = await apiClient.post<ScrmRiskSignal[]>(
        '/scrm/risk-rules/evaluate',
        payload,
      );
      const list = Array.isArray(result) ? result : [];
      setEvaluateResult(list);
      setEvaluateHasRun(true);
      if (list.length === 0) {
        message.success('评估完成, 未触发任何风控信号');
      } else {
        message.success(`评估完成, 触发 ${list.length} 条风控信号`);
      }
    } catch {
      // 错误已由拦截器提示
    } finally {
      setEvaluating(false);
    }
  };

  /** 表格列定义 */
  const columns: ColumnsType<ScrmRiskRule> = [
    {
      title: '规则名称',
      dataIndex: 'ruleName',
      key: 'ruleName',
      ellipsis: true,
      width: 180,
    },
    {
      title: '规则代码',
      dataIndex: 'ruleCode',
      key: 'ruleCode',
      ellipsis: true,
      width: 160,
    },
    {
      title: 'SpEL 表达式',
      dataIndex: 'conditionExpression',
      key: 'conditionExpression',
      width: 280,
      ellipsis: true,
      render: (value: string) => (
        <Tooltip title={value} overlayStyle={{ maxWidth: 600 }}>
          <span>{value || '-'}</span>
        </Tooltip>
      ),
    },
    {
      title: '风险等级',
      dataIndex: 'riskLevel',
      key: 'riskLevel',
      width: 120,
      render: (value: string) => renderTag(value, riskLevelConfig),
    },
    {
      title: '信号类型',
      dataIndex: 'signalType',
      key: 'signalType',
      width: 130,
      render: (value: string) => renderTag(value, signalTypeConfig),
    },
    {
      title: '触发动作',
      dataIndex: 'action',
      key: 'action',
      width: 120,
      render: (value: string) => (value ? renderTag(value, actionConfig) : '-'),
    },
    {
      title: '优先级',
      dataIndex: 'priority',
      key: 'priority',
      width: 90,
      sorter: (a, b) => a.priority - b.priority,
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
      title: '操作',
      key: 'actions',
      width: 220,
      render: (_, record) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            icon={<ExperimentOutlined />}
            onClick={openEvaluate}
          >
            评估
          </Button>
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

  /** 评估结果表格列定义 */
  const signalColumns: ColumnsType<ScrmRiskSignal> = [
    {
      title: '规则名称',
      dataIndex: 'ruleId',
      key: 'ruleName',
      width: 180,
      ellipsis: true,
      render: (ruleId: string) => ruleNameMap.get(ruleId) || `规则 #${ruleId}`,
    },
    {
      title: '风险等级',
      dataIndex: 'riskLevel',
      key: 'riskLevel',
      width: 120,
      render: (value: string) => renderTag(value, riskLevelConfig),
    },
    {
      title: '信号类型',
      dataIndex: 'signalType',
      key: 'signalType',
      width: 130,
      render: (value: string) => renderTag(value, signalTypeConfig),
    },
    {
      title: '详情',
      dataIndex: 'detail',
      key: 'detail',
      ellipsis: true,
      render: (value?: string) => value || '-',
    },
    {
      title: '触发时间',
      dataIndex: 'triggeredAt',
      key: 'triggeredAt',
      width: 180,
      render: (value: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-'),
    },
  ];

  return (
    <div>
      {/* 顶部工具栏 */}
      <Card style={{ marginBottom: 16 }}>
        <Row gutter={[16, 16]} align="middle">
          <Col xs={24} sm={8} md={6}>
            <Input
              placeholder="搜索规则名称 / 代码"
              allowClear
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              suffix={
                keyword && keyword !== debouncedKeyword ? <LoadingOutlined /> : null
              }
            />
          </Col>
          <Col xs={12} sm={6} md={4}>
            <Select
              placeholder="风险等级"
              allowClear
              style={{ width: '100%' }}
              options={riskLevelOptions}
              onChange={(v) => {
                setRiskLevelFilter(v);
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
              <Button icon={<ReloadOutlined />} onClick={fetchRules}>
                刷新
              </Button>
              <Button
                type="primary"
                icon={<ExperimentOutlined />}
                onClick={openEvaluate}
              >
                手动评估
              </Button>
              <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                新建规则
              </Button>
            </Space>
          </Col>
        </Row>
      </Card>

      {/* 规则列表 */}
      <Card>
        <Table<ScrmRiskRule>
          rowKey="id"
          columns={columns}
          dataSource={rules}
          loading={loading}
          scroll={{ x: 1380 }}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="暂无风控规则"
                style={{ padding: 32 }}
              >
                <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                  新建规则
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
        title={editing ? '编辑规则' : '新建规则'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        confirmLoading={submitting}
        destroyOnHidden
        width={680}
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="ruleName"
                label="规则名称"
                rules={[{ required: true, message: '请输入规则名称' }]}
              >
                <Input placeholder="请输入规则名称" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="ruleCode"
                label="规则代码"
                rules={[
                  { required: true, message: '请输入规则代码' },
                  {
                    pattern: /^[A-Za-z0-9_]+$/,
                    message: '仅允许字母、数字和下划线',
                  },
                ]}
              >
                <Input placeholder="唯一代码, 如 RISK_KEYWORD_WECHAT" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item
            name="conditionExpression"
            label="SpEL 条件表达式"
            rules={[{ required: true, message: '请输入 SpEL 条件表达式' }]}
          >
            <Input.TextArea
              rows={3}
              placeholder="例: #message.contains('微信') 或 #messageCountInWindow > 10"
            />
          </Form.Item>
          {/* 常用 SpEL 表达式示例 */}
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
            message="常用 SpEL 表达式示例"
            description={
              <ul style={{ margin: 0, paddingLeft: 20 }}>
                <li>
                  <Text code>#message.contains('微信')</Text> - 关键词命中检测
                </li>
                <li>
                  <Text code>#messageCountInWindow &gt; 10</Text> - 窗口内消息频次检测
                </li>
                <li>
                  <Text code>
                    #message.contains('转账') and #messageCountInWindow &gt; 5
                  </Text>{' '}
                  - 复合条件 (关键词命中且频次超限)
                </li>
              </ul>
            }
          />
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item
                name="riskLevel"
                label="风险等级"
                rules={[{ required: true, message: '请选择风险等级' }]}
              >
                <Select placeholder="请选择风险等级" options={riskLevelOptions} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item
                name="signalType"
                label="信号类型"
                rules={[{ required: true, message: '请选择信号类型' }]}
              >
                <Select placeholder="请选择信号类型" options={signalTypeOptions} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="action" label="触发动作">
                <Select placeholder="请选择触发动作" allowClear options={actionOptions} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="priority" label="优先级" initialValue={100}>
                <InputNumber
                  min={0}
                  max={9999}
                  style={{ width: '100%' }}
                  placeholder="默认 100, 数值越小优先级越高"
                />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="请输入规则描述" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 手动评估弹窗 */}
      <Modal
        title="手动评估"
        open={evaluateOpen}
        onCancel={() => setEvaluateOpen(false)}
        width={760}
        destroyOnHidden
        footer={[
          <Button key="cancel" onClick={() => setEvaluateOpen(false)}>
            关闭
          </Button>,
          <Button
            key="evaluate"
            type="primary"
            loading={evaluating}
            onClick={handleEvaluate}
          >
            执行评估
          </Button>,
        ]}
      >
        <Form form={evaluateForm} layout="vertical" preserve={false}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="platformType" label="平台类型">
                <Input placeholder="如 wework" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="messageCountInWindow" label="窗口内消息数">
                <InputNumber
                  min={0}
                  style={{ width: '100%' }}
                  placeholder="时间窗口内的消息条数"
                />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="accountId" label="账号 ID">
            <Input
              style={{ width: '100%' }}
              placeholder="请输入账号 ID"
            />
          </Form.Item>
          <Form.Item name="messageContent" label="消息内容">
            <Input.TextArea rows={3} placeholder="请输入待评估的消息内容" />
          </Form.Item>
        </Form>

        {/* 评估结果 Alert */}
        {evaluateHasRun && (
          <Alert
            type={evaluateResult.length === 0 ? 'success' : 'error'}
            showIcon
            style={{ marginTop: 16 }}
            message={
              evaluateResult.length === 0
                ? '评估完成, 未触发任何风控信号'
                : `评估完成, 触发 ${evaluateResult.length} 条风控信号`
            }
            description={
              evaluateResult.length > 0
                ? '请查看下方触发的风控信号详情'
                : '当前消息内容未命中任何风控规则'
            }
          />
        )}

        {/* 评估结果表格 */}
        {evaluateResult.length > 0 && (
          <Card size="small" title="触发的风控信号" style={{ marginTop: 16 }}>
            <Table<ScrmRiskSignal>
              rowKey="id"
              columns={signalColumns}
              dataSource={evaluateResult}
              pagination={false}
              size="small"
              scroll={{ x: 700 }}
            />
          </Card>
        )}
        {evaluateOpen && !evaluateHasRun && (
          <Card size="small" style={{ marginTop: 16 }}>
            <Paragraph type="secondary" style={{ marginBottom: 0 }}>
              <Text type="secondary">点击 "执行评估" 查看触发的风控信号。</Text>
            </Paragraph>
          </Card>
        )}
      </Modal>
    </div>
  );
}
