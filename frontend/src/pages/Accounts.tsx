/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Accounts.tsx
 * Date : 2026/07/26
 * Author : hiylo
 * Contact : hiylo@live.com
 */

import { useEffect, useState, useCallback, useRef, type ReactNode, type Key } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  App,
  Avatar,
  Button,
  Card,
  Col,
  Descriptions,
  Drawer,
  Dropdown,
  Empty,
  Form,
  Input,
  Modal,
  Progress,
  Row,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Tooltip,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  ReloadOutlined,
  CheckCircleOutlined,
  MinusCircleOutlined,
  StopOutlined,
  ExclamationCircleOutlined,
  TeamOutlined,
  SyncOutlined,
  MoreOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { MenuProps } from 'antd';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';
import { useDebounce } from '../hooks/useDebounce';
import { useRole } from '../hooks/useRole';
import { usePlatforms } from '../hooks/usePlatforms';

/** 社媒账号实体 */
interface ScrmAccount {
  id: string;
  platformType: string; // wework
  accountName: string;
  displayName: string;
  avatarUrl?: string;
  loginState: string; // LOGIN / LOGOUT / FROZEN / UNKNOWN
  deviceId?: string;
  personaId?: string;
  healthScore?: number; // 健康度 (0-100)
  lastLoginAt?: string;
  createTime?: string;
  updateTime?: string;
  version?: number;
}

/** Spring Page 分页响应 */
interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/** 账号登录状态变更日志 */
interface LoginLog {
  id: string;
  accountId: string;
  fromState?: string | null; // LOGIN / LOGOUT / FROZEN / UNKNOWN
  toState: string; // LOGIN / LOGOUT / FROZEN / UNKNOWN
  reason?: string | null;
  operateAt: string; // ISO datetime
}

/** 平台类型映射 (用于 Tag 颜色与显示文案) */
const platformConfig: Record<string, { label: string; color: string; icon: ReactNode }> = {
  wework: { label: '企业微信', color: 'blue', icon: <TeamOutlined /> },
};

/** 账号状态映射 (与后端 loginState 字段对齐: LOGIN / LOGOUT / FROZEN / UNKNOWN) */
const statusConfig: Record<string, { label: string; color: string; icon: ReactNode }> = {
  LOGIN: { label: '已登录', color: 'green', icon: <CheckCircleOutlined /> },
  LOGOUT: { label: '未登录', color: 'default', icon: <MinusCircleOutlined /> },
  FROZEN: { label: '已冻结', color: 'red', icon: <StopOutlined /> },
  UNKNOWN: { label: '未知', color: 'orange', icon: <ExclamationCircleOutlined /> },
};

/** 状态下拉选项 */
const statusOptions = Object.entries(statusConfig).map(([value, cfg]) => ({
  value,
  label: cfg.label,
}));

/** 根据健康度返回颜色 */
const healthColor = (score: number): string => {
  if (score > 80) return '#52c41a';
  if (score >= 50) return '#fa8c16';
  return '#ff4d4f';
};

/** 表单值类型 */
interface AccountFormValues {
  platformType: string;
  accountName: string;
  displayName?: string;
  avatarUrl?: string;
  deviceId?: string;
}

/**
 * 账号管理页面
 * 列表查询 / 新建 / 编辑 / 删除社媒账号
 */
export default function Accounts() {
  const { message, modal } = App.useApp();
  const { canEdit, canDelete } = useRole();
  // 平台列表 (从后端 API 动态获取, 失败时使用回退列表)
  const { platforms } = usePlatforms();
  // 平台下拉选项 (规范化为小写以匹配现有 platformConfig 与数据格式)
  const platformOptions = platforms.map(p => ({
    value: p.platformType.toLowerCase(),
    label: p.displayName,
  }));
  const [accounts, setAccounts] = useState<ScrmAccount[]>([]);
  const [loading, setLoading] = useState(false);
  // 自动刷新 (30 秒轮询)
  const [autoRefresh, setAutoRefresh] = useState(false);
  const autoRefreshRef = useRef<ReturnType<typeof setInterval> | null>(null);
  // 从 URL 查询参数读取筛选状态 (实现筛选状态持久化, 用户导航返回后恢复筛选条件)
  const [searchParams, setSearchParams] = useSearchParams();
  // page 为 0 索引, URL 中存储 1 索引 (page=1 对应第一页)
  const [page, setPage] = useState(() => {
    const p = Number(searchParams.get('page'));
    return Number.isFinite(p) && p > 0 ? p - 1 : 0;
  });
  const [size, setSize] = useState(() => {
    const s = Number(searchParams.get('size'));
    return Number.isFinite(s) && s > 0 ? s : 10;
  });
  const [total, setTotal] = useState(0);
  // 搜索与筛选条件 (初始值从 URL 查询参数读取)
  const [keyword, setKeyword] = useState(searchParams.get('q') || '');
  const debouncedKeyword = useDebounce(keyword, 300);
  const [platformFilter, setPlatformFilter] = useState<string | undefined>(
    searchParams.get('platform') || undefined,
  );
  const [statusFilter, setStatusFilter] = useState<string | undefined>(
    searchParams.get('status') || undefined,
  );
  // 批量选中
  const [selectedRowKeys, setSelectedRowKeys] = useState<Key[]>([]);
  const [batchDeleting, setBatchDeleting] = useState(false);
  // 新建/编辑弹窗
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ScrmAccount | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<AccountFormValues>();
  // 企微同步联系人
  const [syncingAccountId, setSyncingAccountId] = useState<string | null>(null);
  // 账号详情弹窗
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailAccount, setDetailAccount] = useState<ScrmAccount | null>(null);
  // 账号登录日志
  const [loginLogs, setLoginLogs] = useState<LoginLog[]>([]);
  const [loginLogsLoading, setLoginLogsLoading] = useState(false);

  // 设备绑定弹窗
  const [deviceModalOpen, setDeviceModalOpen] = useState(false);
  const [deviceForm] = Form.useForm();
  const [deviceSubmitting, setDeviceSubmitting] = useState(false);
  const [deviceAccountId, setDeviceAccountId] = useState<string | null>(null);

  // 登录态更新弹窗
  const [loginStateModalOpen, setLoginStateModalOpen] = useState(false);
  const [loginStateForm] = Form.useForm();
  const [loginStateSubmitting, setLoginStateSubmitting] = useState(false);
  const [loginStateAccountId, setLoginStateAccountId] = useState<string | null>(null);

  // 人设绑定弹窗
  const [personaModalOpen, setPersonaModalOpen] = useState(false);
  const [personaForm] = Form.useForm();
  const [personaSubmitting, setPersonaSubmitting] = useState(false);
  const [personaAccountId, setPersonaAccountId] = useState<string | null>(null);

  /** 拉取账号分页列表 */
  const fetchAccounts = useCallback(async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      params.set('page', String(page));
      params.set('size', String(size));
      if (debouncedKeyword) params.set('keyword', debouncedKeyword);
      if (platformFilter) params.set('platformType', platformFilter);
      if (statusFilter) params.set('loginState', statusFilter);
      const data = await apiClient.get<Page<ScrmAccount>>(`/scrm/accounts?${params.toString()}`);
      setAccounts(data.content || []);
      setTotal(data.totalElements || 0);
    } catch {
      // 错误已由 axios 拦截器统一提示
      setAccounts([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [page, size, debouncedKeyword, platformFilter, statusFilter]);

  useEffect(() => {
    fetchAccounts();
  }, [fetchAccounts]);

  // 自动刷新: 开启时每 30 秒轮询一次, 关闭或组件卸载时清理定时器
  useEffect(() => {
    if (autoRefresh) {
      autoRefreshRef.current = setInterval(() => {
        fetchAccounts();
      }, 30000);
    }
    return () => {
      if (autoRefreshRef.current) {
        clearInterval(autoRefreshRef.current);
        autoRefreshRef.current = null;
      }
    };
  }, [autoRefresh, fetchAccounts]);

  // 同步筛选状态到 URL 查询参数 (使用 replace 避免污染浏览器历史; keyword 使用防抖值避免频繁更新)
  useEffect(() => {
    const params: Record<string, string> = {};
    if (debouncedKeyword) params.q = debouncedKeyword;
    if (platformFilter) params.platform = platformFilter;
    if (statusFilter) params.status = statusFilter;
    if (page !== 0) params.page = String(page + 1);
    if (size !== 10) params.size = String(size);
    setSearchParams(params, { replace: true });
  }, [debouncedKeyword, platformFilter, statusFilter, page, size, setSearchParams]);

  /** 拉取账号登录日志 (详情抽屉中使用) */
  const fetchLoginLogs = useCallback(async (accountId: string) => {
    setLoginLogsLoading(true);
    try {
      const data = await apiClient.get<Page<LoginLog>>(`/scrm/accounts/${accountId}/login-logs`, {
        params: { page: 0, size: 10 },
      });
      setLoginLogs(data.content || []);
    } catch {
      // 错误已由 axios 拦截器统一提示
      setLoginLogs([]);
    } finally {
      setLoginLogsLoading(false);
    }
  }, []);

  // 打开详情抽屉时拉取登录日志, 关闭时清空
  useEffect(() => {
    if (detailAccount) {
      fetchLoginLogs(detailAccount.id);
    } else {
      setLoginLogs([]);
    }
  }, [detailAccount, fetchLoginLogs]);

  /** 打开新建弹窗 */
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setModalOpen(true);
  };

  /** 打开编辑弹窗 */
  const openEdit = (record: ScrmAccount) => {
    setEditing(record);
    form.setFieldsValue({
      platformType: record.platformType,
      accountName: record.accountName,
      displayName: record.displayName,
      avatarUrl: record.avatarUrl,
      deviceId: record.deviceId,
    });
    setModalOpen(true);
  };

  /** 提交新建/编辑表单 */
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      if (editing) {
        await apiClient.put(`/scrm/accounts/${editing.id}`, values);
        message.success('账号已更新');
      } else {
        await apiClient.post('/scrm/accounts', values);
        message.success('账号已创建');
      }
      setModalOpen(false);
      fetchAccounts();
    } catch {
      // 表单校验失败或请求失败; 请求失败已由 axios 拦截器统一提示
    } finally {
      setSubmitting(false);
    }
  };

  /** 删除账号 (带二次确认) */
  const handleDelete = (record: ScrmAccount) => {
    modal.confirm({
      title: '删除账号',
      content: `确认删除账号 "${record.accountName}" 吗? 此操作不可恢复。`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await apiClient.delete(`/scrm/accounts/${record.id}`);
          message.success('账号已删除');
          fetchAccounts();
        } catch {
          // 错误已由拦截器提示
        }
      },
    });
  };

  /** 批量删除选中账号 */
  const handleBatchDelete = () => {
    if (selectedRowKeys.length === 0) return;
    modal.confirm({
      title: '批量删除',
      content: `确认删除选中的 ${selectedRowKeys.length} 个账号吗? 此操作不可恢复。`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        setBatchDeleting(true);
        try {
          // 使用 allSettled 确保单个删除失败不影响其他项
          const results = await Promise.allSettled(
            selectedRowKeys.map((id) => apiClient.delete(`/scrm/accounts/${id}`)),
          );
          const successCount = results.filter(r => r.status === 'fulfilled').length;
          const failCount = results.length - successCount;
          if (failCount === 0) {
            message.success(`已删除 ${successCount} 个账号`);
          } else {
            message.warning(`删除完成: 成功 ${successCount} 个, 失败 ${failCount} 个`);
          }
          setSelectedRowKeys([]);
          fetchAccounts();
        } finally {
          setBatchDeleting(false);
        }
      },
    });
  };

  /** 同步企微联系人 */
  const handleSyncContacts = async (record: ScrmAccount) => {
    setSyncingAccountId(record.id);
    try {
      const data = await apiClient.post<{ total: number; newCount: number; updatedCount: number }>(
        `/scrm/accounts/${record.id}/sync-contacts`,
      );
      message.success(`同步完成: 共 ${data.total} 个, 新增 ${data.newCount} 个, 更新 ${data.updatedCount} 个`);
    } catch {
      // 错误已由拦截器提示
    } finally {
      setSyncingAccountId(null);
    }
  };

  /** 打开设备绑定弹窗 */
  const openDeviceModal = (record: ScrmAccount) => {
    setDeviceAccountId(record.id);
    deviceForm.resetFields();
    if (record.deviceId) {
      deviceForm.setFieldsValue({ deviceId: record.deviceId });
    }
    setDeviceModalOpen(true);
  };

  /** 提交设备绑定 */
  const handleBindDevice = async () => {
    if (!deviceAccountId) return;
    try {
      const values = await deviceForm.validateFields();
      setDeviceSubmitting(true);
      await apiClient.post(`/scrm/accounts/${deviceAccountId}/bind-device`, undefined, {
        params: { deviceId: values.deviceId },
      });
      message.success('设备绑定成功');
      setDeviceModalOpen(false);
      fetchAccounts();
    } catch {
      // 表单校验失败或请求失败; 请求失败已由 axios 拦截器统一提示
    } finally {
      setDeviceSubmitting(false);
    }
  };

  /** 解绑设备 (带二次确认) */
  const handleUnbindDevice = (record: ScrmAccount) => {
    modal.confirm({
      title: '确认解绑设备',
      content: `确认解绑账号 "${record.accountName}" 当前绑定的设备吗?`,
      okText: '解绑',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await apiClient.post(`/scrm/accounts/${record.id}/unbind-device`);
          message.success('设备已解绑');
          fetchAccounts();
        } catch {
          // 错误已由拦截器提示
        }
      },
    });
  };

  /** 打开登录态更新弹窗 */
  const openLoginStateModal = (record: ScrmAccount) => {
    setLoginStateAccountId(record.id);
    loginStateForm.resetFields();
    if (record.loginState) {
      loginStateForm.setFieldsValue({ state: record.loginState });
    }
    setLoginStateModalOpen(true);
  };

  /** 提交登录态更新 */
  const handleUpdateLoginState = async () => {
    if (!loginStateAccountId) return;
    try {
      const values = await loginStateForm.validateFields();
      setLoginStateSubmitting(true);
      const params: Record<string, string> = { state: values.state };
      if (values.reason && values.reason.trim()) {
        params.reason = values.reason.trim();
      }
      await apiClient.put(`/scrm/accounts/${loginStateAccountId}/login-state`, undefined, {
        params,
      });
      message.success('登录状态已更新');
      setLoginStateModalOpen(false);
      fetchAccounts();
    } catch {
      // 表单校验失败或请求失败; 请求失败已由 axios 拦截器统一提示
    } finally {
      setLoginStateSubmitting(false);
    }
  };

  /** 打开人设绑定弹窗 */
  const openPersonaModal = (record: ScrmAccount) => {
    setPersonaAccountId(record.id);
    personaForm.resetFields();
    if (record.personaId) {
      personaForm.setFieldsValue({ personaId: record.personaId });
    }
    setPersonaModalOpen(true);
  };

  /** 提交人设绑定 */
  const handleBindPersona = async () => {
    if (!personaAccountId) return;
    try {
      const values = await personaForm.validateFields();
      setPersonaSubmitting(true);
      await apiClient.post(`/scrm/accounts/${personaAccountId}/bind-persona`, undefined, {
        params: { personaId: values.personaId },
      });
      message.success('人设绑定成功');
      setPersonaModalOpen(false);
      fetchAccounts();
    } catch {
      // 表单校验失败或请求失败; 请求失败已由 axios 拦截器统一提示
    } finally {
      setPersonaSubmitting(false);
    }
  };

  /** 表格列定义 */
  const columns: ColumnsType<ScrmAccount> = [
    {
      title: '账号',
      dataIndex: 'accountName',
      key: 'accountName',
      width: 200,
      // 支持按账号名称排序 (客户端排序)
      sorter: (a, b) => (a.accountName || '').localeCompare(b.accountName || ''),
      render: (_, record) => {
        // 头像 fallback 优先显示昵称首字母, 其次账号名首字母
        const fallbackText =
          (record.displayName || record.accountName || '?')[0]?.toUpperCase() || '?';
        return (
          <Space>
            <Avatar src={record.avatarUrl} size="small">
              {fallbackText}
            </Avatar>
            <span>{record.accountName}</span>
          </Space>
        );
      },
    },
    {
      title: '平台类型',
      dataIndex: 'platformType',
      key: 'platformType',
      width: 130,
      render: (value: string) => {
        const cfg = platformConfig[value] || { label: value, color: 'default', icon: null };
        return (
          <Tag color={cfg.color} icon={cfg.icon}>
            {cfg.label}
          </Tag>
        );
      },
    },
    {
      title: '显示名称',
      dataIndex: 'displayName',
      key: 'displayName',
      width: 150,
      ellipsis: true,
    },
    {
      title: '健康度',
      dataIndex: 'healthScore',
      key: 'healthScore',
      width: 160,
      // 支持按健康度排序 (客户端排序, 缺失值视为 0)
      sorter: (a, b) => (a.healthScore || 0) - (b.healthScore || 0),
      render: (value?: number) => {
        if (value == null) return '-';
        return (
          <Progress
            percent={value}
            size="small"
            strokeColor={healthColor(value)}
            format={(p) => `${p}`}
          />
        );
      },
    },
    {
      title: '登录状态',
      dataIndex: 'loginState',
      key: 'loginState',
      width: 120,
      render: (value: string) => {
        const cfg = statusConfig[value] || { label: value, color: 'default', icon: null };
        return (
          <Tag color={cfg.color} icon={cfg.icon}>
            {cfg.label}
          </Tag>
        );
      },
    },
    {
      title: '设备ID',
      dataIndex: 'deviceId',
      key: 'deviceId',
      width: 160,
      ellipsis: true,
      render: (value?: string) => value || '-',
    },
    {
      title: '最后登录',
      dataIndex: 'lastLoginAt',
      key: 'lastLoginAt',
      width: 160,
      // 支持按最后登录时间排序 (客户端排序, 缺失值视为 0)
      sorter: (a, b) => new Date(a.lastLoginAt || 0).getTime() - new Date(b.lastLoginAt || 0).getTime(),
      render: (value?: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-'),
    },
    {
      title: '操作',
      key: 'actions',
      width: 240,
      render: (_, record) => {
        // 构建更多操作菜单项 (仅 canEdit 可见)
        const moreItems: NonNullable<MenuProps['items']> = canEdit
          ? [
              {
                key: 'bindDevice',
                label: '绑定设备',
                onClick: () => openDeviceModal(record),
              },
              ...(record.deviceId
                ? [
                    {
                      key: 'unbindDevice',
                      label: '解绑设备',
                      onClick: () => handleUnbindDevice(record),
                    },
                  ]
                : []),
              {
                key: 'updateLoginState',
                label: '更新登录状态',
                onClick: () => openLoginStateModal(record),
              },
              {
                key: 'bindPersona',
                label: '绑定人设',
                onClick: () => openPersonaModal(record),
              },
            ]
          : [];
        return (
          <Space>
            {record.platformType === 'wework' && canEdit && (
              <Tooltip title="同步联系人">
                <Button
                  type="link"
                  size="small"
                  icon={<SyncOutlined />}
                  loading={syncingAccountId === record.id}
                  onClick={() => handleSyncContacts(record)}
                >
                  同步联系人
                </Button>
              </Tooltip>
            )}
            {canEdit && (
              <Tooltip title="编辑账号">
                <Button
                  type="link"
                  size="small"
                  icon={<EditOutlined />}
                  onClick={() => openEdit(record)}
                >
                  编辑
                </Button>
              </Tooltip>
            )}
            {canEdit && moreItems.length > 0 && (
              <Dropdown
                menu={{ items: moreItems }}
                trigger={['click']}
                placement="bottomRight"
              >
                <Button type="link" size="small" icon={<MoreOutlined />}>
                  更多
                </Button>
              </Dropdown>
            )}
            {canDelete && (
              <Tooltip title="删除账号">
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
            )}
          </Space>
        );
      },
    },
  ];

  /** 登录日志表格列定义 */
  const loginLogColumns: ColumnsType<LoginLog> = [
    {
      title: '变更前状态',
      dataIndex: 'fromState',
      key: 'fromState',
      width: 120,
      render: (value: string | null) => {
        if (!value) return '-';
        const cfg = statusConfig[value] || { label: value, color: 'default', icon: null };
        return (
          <Tag color={cfg.color} icon={cfg.icon}>
            {cfg.label}
          </Tag>
        );
      },
    },
    {
      title: '变更后状态',
      dataIndex: 'toState',
      key: 'toState',
      width: 120,
      render: (value: string) => {
        const cfg = statusConfig[value] || { label: value, color: 'default', icon: null };
        return (
          <Tag color={cfg.color} icon={cfg.icon}>
            {cfg.label}
          </Tag>
        );
      },
    },
    {
      title: '原因',
      dataIndex: 'reason',
      key: 'reason',
      ellipsis: true,
      render: (value: string | null) => value || '-',
    },
    {
      title: '操作时间',
      dataIndex: 'operateAt',
      key: 'operateAt',
      width: 180,
      render: (value?: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-'),
    },
  ];

  return (
    <div>
      {/* 顶部工具栏 */}
      <Card style={{ marginBottom: 16 }}>
        <Row gutter={[16, 16]} align="middle">
          <Col xs={24} sm={8} md={6}>
            <Input.Search
              placeholder="搜索账号名称"
              allowClear
              value={keyword}
              onSearch={(v) => {
                setKeyword(v);
                setPage(0);
              }}
              onChange={(e) => setKeyword(e.target.value)}
            />
          </Col>
          <Col xs={12} sm={6} md={4}>
            <Select
              placeholder="平台类型"
              allowClear
              style={{ width: '100%' }}
              options={platformOptions}
              value={platformFilter}
              onChange={(v) => {
                setPlatformFilter(v);
                setPage(0);
              }}
            />
          </Col>
          <Col xs={12} sm={6} md={4}>
            <Select
              placeholder="状态"
              allowClear
              style={{ width: '100%' }}
              options={statusOptions}
              value={statusFilter}
              onChange={(v) => {
                setStatusFilter(v);
                setPage(0);
              }}
            />
          </Col>
          <Col flex="auto">
            <Space style={{ float: 'right' }}>
              {selectedRowKeys.length > 0 && canDelete && (
                <Button
                  danger
                  icon={<DeleteOutlined />}
                  loading={batchDeleting}
                  onClick={handleBatchDelete}
                >
                  批量删除 ({selectedRowKeys.length})
                </Button>
              )}
              <Button icon={<ReloadOutlined />} onClick={fetchAccounts}>
                刷新
              </Button>
              <Space size={4}>
                <Switch
                  size="small"
                  checked={autoRefresh}
                  onChange={setAutoRefresh}
                />
                <span style={{ fontSize: 13, color: 'var(--color-text-secondary)' }}>自动刷新</span>
              </Space>
              {canEdit && (
                <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                  新建账号
                </Button>
              )}
            </Space>
          </Col>
        </Row>
      </Card>

      {/* 账号列表 */}
      <Card>
        <Table<ScrmAccount>
          rowKey="id"
          columns={columns}
          dataSource={accounts}
          loading={loading}
          scroll={{ x: 1200 }}
          locale={{
            // 空状态展示新建账号 CTA 按钮, 复用工具栏的 openCreate 处理函数
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="暂无账号数据"
                style={{ padding: 32 }}
              >
                {canEdit && (
                  <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                    新建账号
                  </Button>
                )}
              </Empty>
            ),
          }}
          rowSelection={{
            selectedRowKeys,
            onChange: (keys) => setSelectedRowKeys(keys),
          }}
          pagination={{
            current: page + 1,
            pageSize: size,
            total,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            showQuickJumper: true,
            onChange: (p, s) => {
              setPage(p - 1);
              setSize(s);
            },
          }}
        />
      </Card>

      {/* 新建/编辑弹窗 */}
      <Modal
        title={editing ? '编辑账号' : '新建账号'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        confirmLoading={submitting}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="platformType"
            label="平台类型"
            rules={[{ required: true, message: '请选择平台类型' }]}
          >
            <Select placeholder="请选择平台" options={platformOptions} />
          </Form.Item>
          <Form.Item
            name="accountName"
            label="账号名称"
            rules={[
              { required: true, message: '请输入账号名称' },
              { max: 100, message: '账号名称不超过 100 个字符' },
            ]}
          >
            <Input placeholder="请输入账号名称" maxLength={100} />
          </Form.Item>
          <Form.Item
            name="displayName"
            label="显示名称"
            rules={[{ max: 100, message: '显示名称不超过 100 个字符' }]}
          >
            <Input placeholder="请输入显示名称" maxLength={100} />
          </Form.Item>
          <Form.Item
            name="avatarUrl"
            label="头像 URL"
            rules={[
              {
                pattern: /^https?:\/\/.+/,
                message: '请输入有效的 URL (以 http:// 或 https:// 开头)',
              },
            ]}
          >
            <Input placeholder="请输入头像 URL" />
          </Form.Item>
          <Form.Item name="deviceId" label="设备 ID">
            <Input placeholder="请输入设备 ID" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 账号详情抽屉 */}
      <Drawer
        title="账号详情"
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
        width={520}
        destroyOnClose
      >
        {detailAccount && (
          <Tabs
            defaultActiveKey="basic"
            items={[
              {
                key: 'basic',
                label: '基本信息',
                children: (
                  <Descriptions column={1} bordered size="small">
                    <Descriptions.Item label="平台类型">
                      {(() => {
                        const cfg = platformConfig[detailAccount.platformType];
                        return cfg ? (
                          <Tag color={cfg.color} icon={cfg.icon}>{cfg.label}</Tag>
                        ) : (
                          detailAccount.platformType
                        );
                      })()}
                    </Descriptions.Item>
                    <Descriptions.Item label="平台账号 UID">
                      {detailAccount.accountName}
                    </Descriptions.Item>
                    <Descriptions.Item label="显示名称">
                      {detailAccount.displayName || '-'}
                    </Descriptions.Item>
                    <Descriptions.Item label="头像">
                      <Avatar src={detailAccount.avatarUrl} size="large">
                        {(detailAccount.displayName || detailAccount.accountName || '?')[0]?.toUpperCase() || '?'}
                      </Avatar>
                    </Descriptions.Item>
                    <Descriptions.Item label="登录状态">
                      {(() => {
                        const cfg = statusConfig[detailAccount.loginState] || { label: detailAccount.loginState, color: 'default', icon: null };
                        return (
                          <Tag color={cfg.color} icon={cfg.icon}>{cfg.label}</Tag>
                        );
                      })()}
                    </Descriptions.Item>
                    <Descriptions.Item label="健康度评分">
                      {detailAccount.healthScore != null ? (
                        <Progress percent={detailAccount.healthScore} size="small" strokeColor={healthColor(detailAccount.healthScore)} format={(p) => `${p}`} />
                      ) : '-'}
                    </Descriptions.Item>
                    <Descriptions.Item label="设备 ID">
                      {detailAccount.deviceId || '-'}
                    </Descriptions.Item>
                    <Descriptions.Item label="最后登录时间">
                      {detailAccount.lastLoginAt ? dayjs(detailAccount.lastLoginAt).format('YYYY-MM-DD HH:mm:ss') : '-'}
                    </Descriptions.Item>
                    <Descriptions.Item label="创建时间">
                      {detailAccount.createTime ? dayjs(detailAccount.createTime).format('YYYY-MM-DD HH:mm:ss') : '-'}
                    </Descriptions.Item>
                    <Descriptions.Item label="更新时间">
                      {detailAccount.updateTime ? dayjs(detailAccount.updateTime).format('YYYY-MM-DD HH:mm:ss') : '-'}
                    </Descriptions.Item>
                    <Descriptions.Item label="版本号">
                      {detailAccount.version != null ? detailAccount.version : '-'}
                    </Descriptions.Item>
                  </Descriptions>
                ),
              },
              {
                key: 'loginLogs',
                label: '登录日志',
                children: (
                  <Table<LoginLog>
                    rowKey="id"
                    columns={loginLogColumns}
                    dataSource={loginLogs}
                    loading={loginLogsLoading}
                    size="small"
                    pagination={false}
                    locale={{ emptyText: <Empty description="暂无登录日志" /> }}
                  />
                ),
              },
            ]}
          />
        )}
      </Drawer>

      {/* 设备绑定弹窗 */}
      <Modal
        title="绑定设备"
        open={deviceModalOpen}
        onOk={handleBindDevice}
        onCancel={() => setDeviceModalOpen(false)}
        confirmLoading={deviceSubmitting}
        destroyOnHidden
      >
        <Form form={deviceForm} layout="vertical" preserve={false}>
          <Form.Item
            name="deviceId"
            label="设备 ID"
            rules={[{ required: true, message: '请输入设备 ID' }]}
          >
            <Input placeholder="请输入设备 ID" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 登录态更新弹窗 */}
      <Modal
        title="更新登录状态"
        open={loginStateModalOpen}
        onOk={handleUpdateLoginState}
        onCancel={() => setLoginStateModalOpen(false)}
        confirmLoading={loginStateSubmitting}
        destroyOnHidden
      >
        <Form form={loginStateForm} layout="vertical" preserve={false}>
          <Form.Item
            name="state"
            label="登录状态"
            rules={[{ required: true, message: '请选择登录状态' }]}
          >
            <Select
              placeholder="请选择登录状态"
              options={[
                { value: 'LOGIN', label: '已登录' },
                { value: 'LOGOUT', label: '已登出' },
                { value: 'FROZEN', label: '已冻结' },
                { value: 'UNKNOWN', label: '未知' },
              ]}
            />
          </Form.Item>
          <Form.Item name="reason" label="变更原因">
            <Input.TextArea placeholder="请输入变更原因" rows={3} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 人设绑定弹窗 */}
      <Modal
        title="绑定人设"
        open={personaModalOpen}
        onOk={handleBindPersona}
        onCancel={() => setPersonaModalOpen(false)}
        confirmLoading={personaSubmitting}
        destroyOnHidden
      >
        <Form form={personaForm} layout="vertical" preserve={false}>
          <Form.Item
            name="personaId"
            label="人设 ID"
            rules={[{ required: true, message: '请输入人设 ID' }]}
          >
            <Input placeholder="请输入人设 ID" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
