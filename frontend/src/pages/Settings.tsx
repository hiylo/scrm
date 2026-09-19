/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Settings.tsx
 * Date : 2026/07/28
 * Author : hiylo
 * Contact : hiylo@live.com
 */

import { useEffect, useState } from 'react';
import {
  App,
  Button,
  Card,
  Col,
  Divider,
  Form,
  Input,
  Row,
  Space,
  Switch,
  Tabs,
  Tag,
  TimePicker,
  Alert,
  Spin,
  Skeleton,
} from 'antd';
import {
  SaveOutlined,
  ApiOutlined,
  DeleteOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  QuestionCircleOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';
import { useRole } from '../hooks/useRole';
import { usePlatforms } from '../hooks/usePlatforms';

/** 平台配置实体 */
interface PlatformConfig {
  corpId?: string;
  agentId?: string;
  secret?: string;
  aesKey?: string;
  token?: string;
  baseUrl?: string;
  callbackUrl?: string;
  mockMode?: boolean;
  realApiEnabled?: boolean;
  connectionStatus?: string;
  lastTestedAt?: string;
}

/** 连接状态配置 */
const statusConfig: Record<string, { label: string; color: string; icon: React.ReactNode }> = {
  CONNECTED: { label: '已连接', color: 'success', icon: <CheckCircleOutlined /> },
  DISCONNECTED: { label: '未连接', color: 'error', icon: <CloseCircleOutlined /> },
  UNKNOWN: { label: '未知', color: 'default', icon: <QuestionCircleOutlined /> },
};

/** 各平台配置字段标签 (术语略有不同, appId 为空表示该平台不需要此字段) */
const platformConfigLabels: Record<string, { appKey: string; appSecret: string; appId: string }> = {
  wework: { appKey: '企业CorpID', appSecret: '应用Secret', appId: '应用AgentId' },
};

/** 默认配置标签 (未知平台类型时使用, 防止 undefined 导致页面崩溃) */
const defaultConfigLabels: { appKey: string; appSecret: string; appId: string } = {
  appKey: 'App Key',
  appSecret: 'App Secret',
  appId: '',
};

/** 平台配置表单值 */
interface PlatformFormValues {
  corpId: string;
  agentId: string;
  secret: string;
  aesKey: string;
  token: string;
  baseUrl: string;
  mockMode: boolean;
  realApiEnabled: boolean;
}

/** 通知偏好 (存储于 localStorage, 暂无后端 API) */
interface NotificationPreferences {
  enabledTypes: Record<string, boolean>;
  dndEnabled: boolean;
  dndStart: string; // HH:mm 格式
  dndEnd: string;   // HH:mm 格式
}

/** 通知类型配置 (label/描述/默认开关) */
const notificationTypes = [
  { key: 'RISK_ALERT', label: '风险告警', description: '风险规则触发时通知', defaultEnabled: true },
  { key: 'RISK_SIGNAL', label: '风控信号', description: '风控信号产生时通知', defaultEnabled: true },
  { key: 'FOLLOWUP_REMINDER', label: '跟进提醒', description: '客户跟进时间到达时通知', defaultEnabled: true },
  { key: 'NEW_MESSAGE', label: '新消息', description: '收到客户新消息时通知', defaultEnabled: true },
  { key: 'ACCOUNT_HEALTH', label: '账号健康', description: '账号健康度异常时通知', defaultEnabled: false },
  { key: 'TASK_STATUS', label: '任务状态', description: '营销任务状态变更时通知', defaultEnabled: true },
];

/** 通知偏好 Tab 的 key */
const NOTIFICATION_TAB_KEY = 'notifications';

/**
 * 系统设置页面
 * 平台连接配置与系统参数管理
 */
export default function Settings() {
  const { message, modal } = App.useApp();
  const { isAdmin } = useRole();
  // 平台列表 (从后端 API 动态获取, 失败时使用回退列表)
  const { platforms } = usePlatforms();
  // 平台 Tab 配置 (规范化为小写以匹配现有配置逻辑, 如 renderConfigForm 中的 'wework' 判断)
  const platformTabs = platforms.map(p => ({
    key: p.platformType.toLowerCase(),
    label: p.displayName,
  }));
  const [form] = Form.useForm<PlatformFormValues>();

  const [activeTab, setActiveTab] = useState('wework');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [connectionStatus, setConnectionStatus] = useState<string>('UNKNOWN');
  const [lastTestedAt, setLastTestedAt] = useState<string | undefined>(undefined);

  // 通知偏好 (从 localStorage 读取, 失败时使用默认值)
  const [notificationPrefs, setNotificationPrefs] = useState<NotificationPreferences>(() => {
    // 构造默认通知类型开关
    const defaultEnabledTypes: Record<string, boolean> = {};
    notificationTypes.forEach(t => {
      defaultEnabledTypes[t.key] = t.defaultEnabled;
    });
    const defaultPrefs: NotificationPreferences = {
      enabledTypes: defaultEnabledTypes,
      dndEnabled: false,
      dndStart: '22:00',
      dndEnd: '08:00',
    };
    try {
      const stored = localStorage.getItem('notificationPreferences');
      if (stored) {
        const parsed = JSON.parse(stored) as Partial<NotificationPreferences>;
        // 合并默认值, 确保新增的通知类型有默认开关
        return {
          enabledTypes: { ...defaultEnabledTypes, ...parsed.enabledTypes },
          dndEnabled: parsed.dndEnabled ?? false,
          dndStart: parsed.dndStart || '22:00',
          dndEnd: parsed.dndEnd || '08:00',
        };
      }
    } catch {
      // 解析失败时使用默认值
    }
    return defaultPrefs;
  });

  /** 获取当前 origin 用于生成回调 URL */
  const currentOrigin = typeof window !== 'undefined' ? window.location.origin : '';

  /** 加载平台配置 (所有平台共用同一接口) */
  const fetchConfig = async (platformType: string) => {
    setLoading(true);
    try {
      const data = await apiClient.get<PlatformConfig>(`/scrm/platform-configs/${platformType}`);
      form.setFieldsValue({
        corpId: data.corpId || '',
        agentId: data.agentId || '',
        secret: data.secret || '',
        aesKey: data.aesKey || '',
        token: data.token || '',
        baseUrl: data.baseUrl || '',
        mockMode: data.mockMode ?? false,
        realApiEnabled: data.realApiEnabled ?? false,
      });
      setConnectionStatus(data.connectionStatus || 'UNKNOWN');
      setLastTestedAt(data.lastTestedAt);
    } catch {
      // 配置不存在时使用默认值
      form.resetFields();
      setConnectionStatus('UNKNOWN');
      setLastTestedAt(undefined);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    // 通知偏好 Tab 不需要加载平台配置
    if (activeTab === NOTIFICATION_TAB_KEY) return;
    fetchConfig(activeTab);
  }, [activeTab]);

  /** 保存配置 */
  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      setSaving(true);
      const data = await apiClient.put<PlatformConfig>(`/scrm/platform-configs/${activeTab}`, values);
      message.success('配置已保存');
      setConnectionStatus(data.connectionStatus || 'UNKNOWN');
      setLastTestedAt(data.lastTestedAt);
    } catch {
      // 表单校验失败或请求失败
    } finally {
      setSaving(false);
    }
  };

  /** 测试连接 */
  const handleTestConnection = async () => {
    try {
      setTesting(true);
      const data = await apiClient.post<PlatformConfig>(
        `/scrm/platform-configs/${activeTab}/test`,
      );
      setConnectionStatus(data.connectionStatus || 'UNKNOWN');
      setLastTestedAt(data.lastTestedAt || new Date().toISOString());
      if (data.connectionStatus === 'CONNECTED') {
        message.success('连接测试成功');
      } else {
        message.warning('连接测试失败，请检查配置');
      }
    } catch {
      setConnectionStatus('DISCONNECTED');
    } finally {
      setTesting(false);
    }
  };

  /** 保存并测试连接 */
  const handleSaveAndTest = async () => {
    try {
      const values = await form.validateFields();
      setSaving(true);
      const data = await apiClient.put<PlatformConfig>(`/scrm/platform-configs/${activeTab}`, values);
      setConnectionStatus(data.connectionStatus || 'UNKNOWN');
      setLastTestedAt(data.lastTestedAt);
      setSaving(false);

      // 保存成功后立即测试连接
      setTesting(true);
      const testData = await apiClient.post<PlatformConfig>(
        `/scrm/platform-configs/${activeTab}/test`,
      );
      setConnectionStatus(testData.connectionStatus || 'UNKNOWN');
      setLastTestedAt(testData.lastTestedAt || new Date().toISOString());
      if (testData.connectionStatus === 'CONNECTED') {
        message.success('保存并连接测试成功');
      } else {
        message.warning('配置已保存，但连接测试失败，请检查配置');
      }
    } catch {
      // 表单校验失败或请求失败
    } finally {
      setSaving(false);
      setTesting(false);
    }
  };

  /** 删除配置 */
  const handleDelete = (platformType: string) => {
    const platformName = platformTabs.find(t => t.key === platformType)?.label || '';
    modal.confirm({
      title: '确认删除配置',
      content: `确定要删除${platformName}的配置吗？删除后将无法使用该平台功能。`,
      okText: '确认删除',
      cancelText: '取消',
      okType: 'danger',
      onOk: async () => {
        try {
          await apiClient.delete(`/scrm/platform-configs/${platformType}`);
          message.success('配置已删除');
          form.resetFields();
          setConnectionStatus('UNKNOWN');
          setLastTestedAt(undefined);
        } catch {
          // 错误已由拦截器提示
        }
      },
    });
  };

  /** 渲染连接状态 Tag */
  const renderStatusTag = () => {
    const cfg = statusConfig[connectionStatus] || statusConfig.UNKNOWN;
    return (
      <Tag color={cfg.color} icon={cfg.icon}>
        {cfg.label}
      </Tag>
    );
  };

  /** 渲染平台配置表单 (通用, 适配所有平台, 通过 platformConfigLabels 区分术语) */
  const renderConfigForm = (platformType: string) => {
    const labels = platformConfigLabels[platformType] || defaultConfigLabels;
    const isWeWork = platformType === 'wework';
    const platformName = platformTabs.find(t => t.key === platformType)?.label || '';

    /** 当前平台的回调地址 (wework 保持原有路径, 其他平台按平台类型生成) */
    const platformCallbackUrl = `${currentOrigin}/scrm/${platformType}/callback`;

    if (loading) {
      return (
        <div style={{ padding: '20px 0' }}>
          <Skeleton active paragraph={{ rows: 6 }} />
          <div style={{ display: 'flex', justifyContent: 'center', padding: '16px 0' }}>
            <Spin size="large" />
          </div>
        </div>
      );
    }

    return (
      <>
        {/* 回调 URL 提示 */}
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 24 }}
          message="回调地址配置"
          description={
            <span>
              请在{platformName}管理后台的「接收消息」设置中填写以下回调 URL：
              <br />
              <strong style={{ userSelect: 'all' }}>{platformCallbackUrl}</strong>
            </span>
          }
        />

        <Form form={form} layout="vertical">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="corpId"
                label={labels.appKey}
                rules={
                  isWeWork
                    ? [
                        { required: true, message: '请输入企业 ID' },
                        { pattern: /^ww[a-f0-9]{16}$/i, message: '企业 ID 格式不正确，应以 ww 开头后跟 16 位十六进制字符' },
                      ]
                    : [{ required: true, message: `请输入${labels.appKey}` }]
                }
              >
                <Input placeholder={`请输入${labels.appKey}`} />
              </Form.Item>
            </Col>
            {/* 仅当平台需要应用ID时渲染 (如企业微信), 其他平台 appId 为空则跳过 */}
            {labels.appId ? (
              <Col span={12}>
                <Form.Item
                  name="agentId"
                  label={labels.appId}
                  rules={
                    isWeWork
                      ? [
                          { required: true, message: '请输入应用 ID' },
                          { pattern: /^[1-9]\d*$/, message: '应用 ID 必须为正整数' },
                        ]
                      : []
                  }
                >
                  <Input placeholder={`请输入${labels.appId}`} />
                </Form.Item>
              </Col>
            ) : null}
          </Row>

          <Form.Item
            name="secret"
            label={labels.appSecret}
            rules={[
              { required: true, message: '请输入应用密钥' },
              { min: 1, message: '应用密钥不能为空' },
            ]}
          >
            <Input.Password placeholder={`请输入${labels.appSecret}`} />
          </Form.Item>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="aesKey"
                label="加密密钥 (EncodingAESKey)"
                rules={isWeWork ? [{ required: true, message: '请输入消息加解密密钥' }] : []}
              >
                <Input.Password placeholder={isWeWork ? '请输入 EncodingAESKey' : '请输入加密密钥 (可选)'} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="token"
                label="回调Token (Callback Token)"
                rules={isWeWork ? [{ required: true, message: '请输入回调 Token' }] : []}
              >
                <Input.Password placeholder={isWeWork ? '请输入回调 Token' : '请输入回调Token (可选)'} />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item
            name="baseUrl"
            label="API地址 (Base URL)"
            rules={isWeWork ? [{ required: true, message: '请输入接口基础地址' }] : []}
          >
            <Input placeholder={isWeWork ? '请输入接口基础地址' : '请输入接口基础地址 (可选)'} />
          </Form.Item>

          {/* wework 回调地址只读展示, 其他平台可手动填写 */}
          <Form.Item name="callbackUrl" label="回调地址 (Callback URL)">
            {isWeWork ? (
              <Input value={platformCallbackUrl} readOnly />
            ) : (
              <Input placeholder="请输入回调地址 (可选)" />
            )}
          </Form.Item>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="mockMode" label="Mock 模式" valuePropName="checked">
                <Switch checkedChildren="开启" unCheckedChildren="关闭" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="realApiEnabled" label="启用真实API" valuePropName="checked">
                <Switch checkedChildren="开启" unCheckedChildren="关闭" />
              </Form.Item>
            </Col>
          </Row>
        </Form>

        {/* 连接状态与操作按钮 */}
        <div style={{ borderTop: '1px solid var(--ant-color-border-secondary, #f0f0f0)', paddingTop: 16, marginTop: 8 }}>
          <Row justify="space-between" align="middle">
            <Col>
              <Space size="middle">
                <span>连接状态：{renderStatusTag()}</span>
                {lastTestedAt && (
                  <span style={{ color: 'var(--ant-colorTextSecondary, #999)', marginLeft: 16 }}>
                    最后测试：{dayjs(lastTestedAt).format('YYYY-MM-DD HH:mm:ss')}
                  </span>
                )}
              </Space>
            </Col>
            <Col>
              <Space>
                <Button
                  type="primary"
                  icon={<SaveOutlined />}
                  loading={saving && !testing}
                  disabled={!isAdmin || (saving && testing)}
                  onClick={handleSave}
                >
                  保存
                </Button>
                <Button
                  icon={<ThunderboltOutlined />}
                  loading={saving || testing}
                  disabled={!isAdmin}
                  onClick={handleSaveAndTest}
                >
                  保存并测试
                </Button>
                <Button
                  icon={<ApiOutlined />}
                  loading={testing && !saving}
                  disabled={!isAdmin || (saving && testing)}
                  onClick={handleTestConnection}
                >
                  测试连接
                </Button>
                <Button
                  danger
                  icon={<DeleteOutlined />}
                  disabled={!isAdmin}
                  onClick={() => handleDelete(platformType)}
                >
                  删除配置
                </Button>
              </Space>
            </Col>
          </Row>
        </div>
      </>
    );
  };

  /** 切换某个通知类型的启用状态 */
  const handleToggleNotificationType = (key: string, enabled: boolean) => {
    setNotificationPrefs(prev => ({
      ...prev,
      enabledTypes: { ...prev.enabledTypes, [key]: enabled },
    }));
  };

  /** 保存通知偏好到 localStorage */
  const handleSaveNotificationPrefs = () => {
    try {
      localStorage.setItem('notificationPreferences', JSON.stringify(notificationPrefs));
      message.success('通知偏好已保存');
    } catch {
      message.error('保存失败，请稍后重试');
    }
  };

  /** 渲染通知偏好配置 Tab */
  const renderNotificationPreferences = () => (
    <div style={{ maxWidth: 720 }}>
      {/* 通知类型列表 (每项可独立开关) */}
      <Card title="通知类型" size="small" style={{ marginBottom: 16 }}>
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          {notificationTypes.map(item => (
            <Row key={item.key} align="middle" justify="space-between">
              <Col>
                <div style={{ fontWeight: 500 }}>{item.label}</div>
                <div style={{ color: 'var(--ant-colorTextSecondary, #999)', fontSize: 12 }}>
                  {item.description}
                </div>
              </Col>
              <Col>
                <Switch
                  checked={notificationPrefs.enabledTypes[item.key] ?? item.defaultEnabled}
                  onChange={checked => handleToggleNotificationType(item.key, checked)}
                />
              </Col>
            </Row>
          ))}
        </Space>
      </Card>

      {/* 通知类型与免打扰时段之间的分隔线 */}
      <Divider />

      {/* 免打扰时段: 启用后该时段内暂停所有通知 */}
      <Card title="免打扰时段" size="small" style={{ marginBottom: 16 }}>
        <Row align="middle" gutter={16}>
          <Col>启用免打扰</Col>
          <Col>
            <Switch
              checked={notificationPrefs.dndEnabled}
              onChange={checked =>
                setNotificationPrefs(prev => ({ ...prev, dndEnabled: checked }))
              }
            />
          </Col>
        </Row>
        {notificationPrefs.dndEnabled && (
          <Row align="middle" gutter={8} style={{ marginTop: 16 }}>
            <Col>每日</Col>
            <Col>
              <TimePicker
                value={dayjs(notificationPrefs.dndStart, 'HH:mm')}
                format="HH:mm"
                allowClear={false}
                onChange={time =>
                  setNotificationPrefs(prev => ({
                    ...prev,
                    dndStart: time ? time.format('HH:mm') : '22:00',
                  }))
                }
              />
            </Col>
            <Col>至</Col>
            <Col>
              <TimePicker
                value={dayjs(notificationPrefs.dndEnd, 'HH:mm')}
                format="HH:mm"
                allowClear={false}
                onChange={time =>
                  setNotificationPrefs(prev => ({
                    ...prev,
                    dndEnd: time ? time.format('HH:mm') : '08:00',
                  }))
                }
              />
            </Col>
            <Col style={{ color: 'var(--ant-colorTextSecondary, #999)', fontSize: 12 }}>
              该时段内将暂停所有通知
            </Col>
          </Row>
        )}
      </Card>

      {/* 保存按钮: 将偏好写入 localStorage */}
      <Button
        type="primary"
        icon={<SaveOutlined />}
        onClick={handleSaveNotificationPrefs}
      >
        保存
      </Button>
    </div>
  );

  return (
    <Card>
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        // 切换 Tab 时销毁非活动面板, 避免多个平台表单共用同一 Form 实例导致字段名冲突
        destroyInactiveTabPane
        items={[
          ...platformTabs.map(tab => ({
            key: tab.key,
            label: tab.label,
            children: (
              <div style={{ maxWidth: 720 }}>
                {renderConfigForm(tab.key)}
              </div>
            ),
          })),
          // 通知偏好 Tab (独立于平台配置, 数据持久化于 localStorage)
          {
            key: NOTIFICATION_TAB_KEY,
            label: '通知偏好',
            children: renderNotificationPreferences(),
          },
        ]}
      />
    </Card>
  );
}
