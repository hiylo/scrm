/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Layout.tsx
 * Date : 2026/07/26
 * Author : hiylo
 * Contact : hiylo@live.com
 */

import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Layout as AntLayout, Avatar, Dropdown, Button, Tooltip, Modal, Input, Drawer, Popover, Badge, Tag, App, Empty, AutoComplete } from 'antd';
import {
  UserOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  SunOutlined,
  MoonOutlined,
  SearchOutlined,
  FullscreenOutlined,
  FullscreenExitOutlined,
  BellOutlined,
  MenuOutlined,
  WarningOutlined,
  InfoCircleOutlined,
  AlertOutlined,
  ScheduleOutlined,
  DeleteOutlined,
} from '@ant-design/icons';
import { useState, useMemo, useEffect, useCallback, useRef } from 'react';
import type { ReactNode } from 'react';
import type { ScrmWsNotification } from '../hooks/useScrmWebSocket';
import { isAuthenticated, getDisplayName, logout, hasPermission, getRole } from '../api/auth';
import { apiClient } from '../api/client';
import { useTheme } from '../contexts/ThemeContext';
import { useDebounce } from '../hooks/useDebounce';
import { useScrmWebSocket } from '../hooks/useScrmWebSocket';
import './Layout.css';

const { Sider, Content, Header } = AntLayout;

/** 通知中心最多保留的历史通知条数 (超出后移除最旧的) */
const MAX_NOTIFICATIONS = 50;

/** 通知记录 (在 ScrmWsNotification 基础上增加 id / read 状态) */
interface NotificationRecord extends ScrmWsNotification {
  id: number;
  read: boolean;
}

/** 通知类型 → 图标与颜色映射 */
const notificationTypeConfig: Record<string, { icon: ReactNode; color: string }> = {
  RISK_ALERT: { icon: <AlertOutlined />, color: '#ff4d4f' },
  RISK_SIGNAL: { icon: <WarningOutlined />, color: '#faad14' },
  FOLLOWUP_REMINDER: { icon: <ScheduleOutlined />, color: '#1677ff' },
  NEW_MESSAGE: { icon: <BellOutlined />, color: '#1677ff' },
  ACCOUNT_HEALTH: { icon: <WarningOutlined />, color: '#faad14' },
  TASK_STATUS: { icon: <InfoCircleOutlined />, color: '#1677ff' },
  CAMPAIGN_LOG: { icon: <InfoCircleOutlined />, color: '#8c8c8c' },
  SYSTEM: { icon: <InfoCircleOutlined />, color: '#8c8c8c' },
  DASHBOARD_STAT_UPDATE: { icon: <InfoCircleOutlined />, color: '#8c8c8c' },
};

/** 格式化时间为相对时间 (如 "3 分钟前") */
function formatRelativeTime(timestamp?: string): string {
  if (!timestamp) return '';
  const now = Date.now();
  const ts = new Date(timestamp).getTime();
  if (isNaN(ts)) return '';
  const diff = now - ts;
  if (diff < 60000) return '刚刚';
  if (diff < 3600000) return `${Math.floor(diff / 60000)} 分钟前`;
  if (diff < 86400000) return `${Math.floor(diff / 3600000)} 小时前`;
  return `${Math.floor(diff / 86400000)} 天前`;
}

/** 平台类型 → 中文标签映射 (全局搜索结果显示用) */
const platformLabelMap: Record<string, string> = {
  wework: '企业微信',
};

/** 移动端断点 (px) */
const MOBILE_BREAKPOINT = 768;

/** 全局搜索选项类型 */
interface SearchOption {
  value: string;
  label: React.ReactNode;
  key: string;
}

/** 导航菜单项 */
interface NavItem {
  key: string;
  icon: string;
  label: string;
  adminOnly?: boolean;
  permission?: string;
  /** 可见角色列表 (小写: admin/manager/sales/viewer), 未设置则所有角色可见 */
  roles?: string[];
}

/** 导航菜单分组 */
interface NavGroup {
  group: string;
  items: NavItem[];
}

/** 角色显示配置 (Tag 颜色与中文文案) */
const roleDisplayConfig: Record<string, { label: string; color: string }> = {
  admin: { label: '管理员', color: 'red' },
  manager: { label: '主管', color: 'blue' },
  sales: { label: '业务员', color: 'green' },
  viewer: { label: '访客', color: 'default' },
};

/** SCRM 侧边栏导航分组配置 */
const navGroups: NavGroup[] = [
  {
    group: '概览',
    items: [
      { key: '/', icon: '📊', label: '运营中心' },
    ],
  },
  {
    group: '客户管理',
    items: [
      { key: '/customers', icon: '👥', label: '客户列表', permission: 'scrm_customer:read', roles: ['admin', 'manager', 'sales', 'viewer'] },
      { key: '/customer-groups', icon: '📁', label: '客户分组', permission: 'scrm_customer:read', roles: ['admin', 'manager', 'sales', 'viewer'] },
      { key: '/customer-tags', icon: '🏷️', label: '标签管理', permission: 'scrm_customer:read', roles: ['admin', 'manager', 'sales', 'viewer'] },
    ],
  },
  {
    group: '会话管理',
    items: [
      { key: '/conversations', icon: '💬', label: '会话列表', permission: 'scrm_conversation:read', roles: ['admin', 'manager', 'sales', 'viewer'] },
    ],
  },
  {
    group: '群管理',
    items: [
      { key: '/groups', icon: '👪', label: '客户群', permission: 'scrm_wework:read', roles: ['admin', 'manager', 'sales'] },
    ],
  },
  {
    group: '组织架构',
    items: [
      { key: '/wework-departments', icon: '🏢', label: '部门通讯录', permission: 'scrm_wework:read', roles: ['admin', 'manager', 'sales'] },
    ],
  },
  {
    group: '平台账号',
    items: [
      { key: '/accounts', icon: '🔐', label: '平台账号管理', permission: 'scrm_account:read', roles: ['admin', 'sales'] },
      { key: '/account-health', icon: '💚', label: '健康度', permission: 'scrm_account:read', roles: ['admin'] },
      { key: '/personas', icon: '🎭', label: '人设管理', permission: 'scrm_persona:read', roles: ['admin', 'manager'] },
    ],
  },
  {
    group: '营销任务',
    items: [
      { key: '/campaigns', icon: '🚀', label: '任务管理', permission: 'scrm_campaign:read', roles: ['admin', 'manager', 'sales', 'viewer'] },
      { key: '/message-templates', icon: '📝', label: '消息模板', permission: 'scrm_message_template:read', roles: ['admin', 'manager', 'sales'] },
    ],
  },
  {
    group: '风控管理',
    items: [
      { key: '/risk-rules', icon: '🚨', label: '风险规则', permission: 'scrm_risk_rule:read', roles: ['admin', 'manager'] },
      { key: '/risk-signals', icon: '⚠️', label: '风控信号', permission: 'scrm_risk_signal:read', roles: ['admin', 'manager'] },
    ],
  },
  {
    group: '数据',
    items: [
      { key: '/dashboard', icon: '📈', label: '数据看板', permission: 'scrm_dashboard:read', roles: ['admin', 'manager'] },
      { key: '/audit-logs', icon: '📋', label: '审计日志', adminOnly: true },
    ],
  },
  {
    group: '系统',
    items: [
      { key: '/users', icon: '👤', label: '用户管理', adminOnly: true },
      { key: '/settings', icon: '⚙️', label: '平台配置', permission: 'scrm_platform_config:read', roles: ['admin'] },
    ],
  },
];

const allMenuItems = navGroups.flatMap(g => g.items);

/**
 * 主布局组件
 * 包含侧边栏导航 (分组菜单) 和顶栏 (页面标题、主题切换、用户菜单)
 * 响应式: 移动端使用 Drawer 抽屉, 桌面端使用固定 Sider
 */
export default function Layout() {
  const location = useLocation();
  const navigate = useNavigate();
  const { theme, toggleTheme } = useTheme();
  // 通过 App.useApp() 获取 notification 实例 (替代 antd 静态方法, 支持主题上下文)
  const { notification } = App.useApp();
  // 通知中心历史记录 (最多 MAX_NOTIFICATIONS 条, 新通知插入头部)
  const [notifications, setNotifications] = useState<NotificationRecord[]>([]);
  // 通知 ID 自增计数器
  const notifIdRef = useRef(0);

  // 是否移动端 (窗口宽度 < 768)
  const [isMobile, setIsMobile] = useState<boolean>(
    typeof window !== 'undefined' ? window.innerWidth < MOBILE_BREAKPOINT : false,
  );
  // 桌面端侧边栏折叠状态
  const [collapsed, setCollapsed] = useState(false);
  // 移动端 Drawer 抽屉开关
  const [drawerOpen, setDrawerOpen] = useState(false);
  // 菜单搜索词 (输入立即更新, 用于受控输入)
  const [menuFilter, setMenuFilter] = useState('');
  // 防抖后的搜索词, 用于实际过滤
  const debouncedFilter = useDebounce(menuFilter, 250);
  // 全局搜索 (顶栏搜索框)
  const [globalSearchValue, setGlobalSearchValue] = useState('');
  const [globalSearchOptions, setGlobalSearchOptions] = useState<{ value: string; label: React.ReactNode; key: string }[]>([]);
  const [globalSearchLoading, setGlobalSearchLoading] = useState(false);
  const globalSearchTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  // 是否处于全屏
  const [isFullscreen, setIsFullscreen] = useState(false);
  // WebSocket 握手 token (从 localStorage 读取)
  const [wsToken, setWsToken] = useState<string | null>(null);
  // 会话未读消息总数 (侧边栏 Badge 显示)
  const [totalUnread, setTotalUnread] = useState(0);

  // 监听窗口尺寸, 切换移动端 / 桌面端
  useEffect(() => {
    const handleResize = () => {
      const mobile = window.innerWidth < MOBILE_BREAKPOINT;
      setIsMobile(mobile);
      // 进入移动端时折叠桌面侧边栏, 退出移动端时还原
      if (mobile) setCollapsed(false);
    };
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  // 监听全屏状态变化 (包括 ESC 退出)
  useEffect(() => {
    const handleFullscreenChange = () => {
      setIsFullscreen(Boolean(document.fullscreenElement));
    };
    document.addEventListener('fullscreenchange', handleFullscreenChange);
    return () => document.removeEventListener('fullscreenchange', handleFullscreenChange);
  }, []);

  // 读取 token 用于 WebSocket 握手认证
  useEffect(() => {
    const t = localStorage.getItem('scrm_token');
    setWsToken(t);
  }, []);

  // 组件卸载时清理全局搜索防抖定时器
  useEffect(() => {
    return () => {
      if (globalSearchTimerRef.current) {
        clearTimeout(globalSearchTimerRef.current);
      }
    };
  }, []);

  /** 拉取会话未读消息总数 (用于侧边栏 Badge) */
  const fetchTotalUnread = useCallback(async () => {
    try {
      const data = await apiClient.get<{ content: Array<{ unreadCount?: number }>; totalElements: number }>(
        '/scrm/conversations', { params: { page: 0, size: 200 } },
      );
      const total = (data?.content || []).reduce((sum, c) => sum + (c.unreadCount || 0), 0);
      setTotalUnread(total);
    } catch {
      // 静默处理, 不影响页面加载
    }
  }, []);

  // 全局监听 WebSocket 通知: 弹窗提示 + 追加到通知中心历史记录
  useScrmWebSocket({
    token: wsToken,
    onNotification: (notif) => {
      // 将通知追加到历史记录 (插入头部, 超出上限移除最旧)
      const record: NotificationRecord = {
        ...notif,
        id: ++notifIdRef.current,
        read: false,
      };
      setNotifications((prev) => [record, ...prev].slice(0, MAX_NOTIFICATIONS));

      if (notif.type === 'RISK_ALERT') {
        // 优先取通知 content, 其次取 data.description
        const ruleCode = notif.data?.ruleCode as string | undefined;
        const description =
          notif.content ||
          (notif.data?.description as string | undefined) ||
          '检测到风险规则触发';
        notification.error({
          message: ruleCode ? `风险规则告警: ${ruleCode}` : '风险规则告警',
          description,
          placement: 'topRight',
          // duration=0 表示不自动关闭, 突出告警优先级
          duration: 0,
          // 红色告警样式
          style: { background: '#fff2f0', border: '1px solid #ffccc7' },
        });
      } else if (notif.type === 'FOLLOWUP_REMINDER') {
        // 跟进提醒: 蓝色信息样式, 展示客户名称与计划跟进时间
        const customerName = (notif.data?.customerName as string | undefined) || '未知客户';
        const followUpAt = notif.data?.followUpAt as string | undefined;
        const customerId = notif.data?.customerId as string | undefined;
        // 格式化提醒时间 (后端推送 ISO 字符串)
        const formattedTime = followUpAt
          ? new Date(followUpAt).toLocaleString('zh-CN', {
              month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
            })
          : '未知时间';
        notification.info({
          message: '客户跟进提醒',
          description: `客户 ${customerName} 的跟进时间将至: ${formattedTime}`,
          placement: 'topRight',
          duration: 6,
          // 蓝色信息样式
          style: { background: '#e6f4ff', border: '1px solid #91caff' },
          onClick: () => {
            // 点击通知跳转到客户详情页
            if (customerId) {
              navigate(`/customers/${customerId}`);
            }
          },
        });
      } else if (notif.type === 'NEW_MESSAGE') {
        // 新消息到达: 刷新侧边栏会话未读 Badge
        fetchTotalUnread();
      }
    },
  });

  // 挂载时拉取一次未读消息总数, 之后每 60 秒定期刷新 (卸载时清除定时器)
  useEffect(() => {
    fetchTotalUnread();
    const timer = setInterval(() => {
      fetchTotalUnread();
    }, 60000);
    return () => clearInterval(timer);
  }, [fetchTotalUnread]);

  /** 切换全屏 */
  const toggleFullscreen = useCallback(() => {
    if (!document.fullscreenElement) {
      document.documentElement.requestFullscreen?.().catch(() => {
        /* 全屏请求失败时忽略 */
      });
    } else {
      document.exitFullscreen?.();
    }
  }, []);

  /**
   * 全局搜索: 防抖后并行调用客户/账号/会话搜索 API, 汇总为分组下拉选项
   * 每类最多取 5 条, 关键词为空时清空选项
   */
  const handleGlobalSearch = useCallback((keyword: string) => {
    setGlobalSearchValue(keyword);
    // 清除上一次防抖定时器
    if (globalSearchTimerRef.current) {
      clearTimeout(globalSearchTimerRef.current);
    }
    const trimmed = keyword.trim();
    if (!trimmed) {
      setGlobalSearchOptions([]);
      setGlobalSearchLoading(false);
      return;
    }
    setGlobalSearchLoading(true);
    globalSearchTimerRef.current = setTimeout(async () => {
      try {
        // 并行调用三个搜索接口 (取前 5 条)
        const [customerRes, accountRes, conversationRes] = await Promise.allSettled([
          apiClient.get<{ content: Array<{ id: string; nickname?: string; platformType: string; platformCustomerUid: string; lifecycle?: string }>; totalElements: number }>(
            '/scrm/customers/list', { params: { keyword: trimmed, page: 0, size: 5 } },
          ),
          apiClient.get<{ content: Array<{ id: string; displayName?: string; platformType: string; platformAccountUid: string; loginState?: string }>; totalElements: number }>(
            '/scrm/accounts/list', { params: { keyword: trimmed, page: 0, size: 5 } },
          ),
          apiClient.get<{ content: Array<{ id: string; customerNickname?: string; accountName?: string; platformType: string; lastMessageSummary?: string; status?: string }>; totalElements: number }>(
            '/scrm/conversations/list', { params: { keyword: trimmed, page: 0, size: 5 } },
          ),
        ]);

        const options: SearchOption[] = [];

        // 客户结果
        if (customerRes.status === 'fulfilled') {
          const customers = customerRes.value.content || [];
          if (customers.length > 0) {
            // 分组标题
            options.push({
              value: `__group_customer__`,
              label: <div style={{ fontWeight: 600, color: 'var(--color-text-secondary, #8c8c8c)', fontSize: '12px', padding: '4px 0' }}>客户 ({customers.length})</div>,
              key: 'group_customer',
            });
            customers.forEach((c) => {
              const display = c.nickname || c.platformCustomerUid || `客户#${c.id}`;
              const platform = platformLabelMap[c.platformType] || c.platformType;
              options.push({
                value: `customer:${c.id}`,
                label: (
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '2px 0' }}>
                    <span>👥</span>
                    <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{display}</span>
                    <Tag style={{ margin: 0, fontSize: '11px' }}>{platform}</Tag>
                  </div>
                ),
                key: `customer_${c.id}`,
              });
            });
          }
        }

        // 账号结果
        if (accountRes.status === 'fulfilled') {
          const accounts = accountRes.value.content || [];
          if (accounts.length > 0) {
            options.push({
              value: `__group_account__`,
              label: <div style={{ fontWeight: 600, color: 'var(--color-text-secondary, #8c8c8c)', fontSize: '12px', padding: '4px 0' }}>账号 ({accounts.length})</div>,
              key: 'group_account',
            });
            accounts.forEach((a) => {
              const display = a.displayName || a.platformAccountUid || `账号#${a.id}`;
              const platform = platformLabelMap[a.platformType] || a.platformType;
              options.push({
                value: `account:${a.id}`,
                label: (
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '2px 0' }}>
                    <span>🔐</span>
                    <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{display}</span>
                    <Tag style={{ margin: 0, fontSize: '11px' }}>{platform}</Tag>
                  </div>
                ),
                key: `account_${a.id}`,
              });
            });
          }
        }

        // 会话结果
        if (conversationRes.status === 'fulfilled') {
          const conversations = conversationRes.value.content || [];
          if (conversations.length > 0) {
            options.push({
              value: `__group_conversation__`,
              label: <div style={{ fontWeight: 600, color: 'var(--color-text-secondary, #8c8c8c)', fontSize: '12px', padding: '4px 0' }}>会话 ({conversations.length})</div>,
              key: 'group_conversation',
            });
            conversations.forEach((c) => {
              const display = c.customerNickname || c.accountName || `会话#${c.id}`;
              const platform = platformLabelMap[c.platformType] || c.platformType;
              const summary = c.lastMessageSummary || '';
              options.push({
                value: `conversation:${c.id}`,
                label: (
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '2px 0' }}>
                    <span>💬</span>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{display}</div>
                      {summary && (
                        <div style={{ fontSize: '11px', color: 'var(--color-text-tertiary, #bfbfbf)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{summary}</div>
                      )}
                    </div>
                    <Tag style={{ margin: 0, fontSize: '11px' }}>{platform}</Tag>
                  </div>
                ),
                key: `conversation_${c.id}`,
              });
            });
          }
        }

        setGlobalSearchOptions(options);
      } catch {
        // 搜索失败时静默清空选项 (响应拦截器已弹错误提示)
        setGlobalSearchOptions([]);
      } finally {
        setGlobalSearchLoading(false);
      }
    }, 300);
  }, []);

  /** 全局搜索选中: 解析 value 前缀跳转到对应详情页 */
  const handleSearchSelect = useCallback((value: string) => {
    if (!value || value.startsWith('__group_')) return;
    const [type, id] = value.split(':');
    if (!id) return;
    switch (type) {
      case 'customer':
        navigate(`/customers/${id}`);
        break;
      case 'account':
        navigate(`/accounts`);
        break;
      case 'conversation':
        navigate(`/conversations?customerId=${id}`);
        break;
    }
    // 清空搜索框
    setGlobalSearchValue('');
    setGlobalSearchOptions([]);
  }, [navigate]);

  /** 按搜索词过滤菜单 (使用防抖后的关键词) */
  const filteredGroups = useMemo(() => {
    const q = debouncedFilter.trim().toLowerCase();
    if (!q) return navGroups;
    return navGroups.map(g => ({
      ...g,
      items: g.items.filter(item =>
        item.label.toLowerCase().includes(q) || g.group.toLowerCase().includes(q),
      ),
    })).filter(g => g.items.length > 0);
  }, [debouncedFilter]);

  /** 菜单点击导航 */
  const handleMenuClick = (key: string) => {
    navigate(key);
    // 移动端点击后收起抽屉
    if (isMobile) setDrawerOpen(false);
    // 进入会话列表后刷新未读数 (会话页面会标记已读, 延迟以等待状态更新)
    if (key === '/conversations') {
      setTimeout(() => fetchTotalUnread(), 500);
    }
  };

  /** 退出登录确认 */
  const handleLogout = () => {
    Modal.confirm({
      title: '确认退出',
      content: '确定要退出登录吗？',
      okText: '退出',
      cancelText: '取消',
      onOk: () => {
        logout();
        navigate('/login');
      },
    });
  };

  /** 获取当前页面标题 */
  const getPageTitle = (): string => {
    const item = allMenuItems.find(item => item.key === location.pathname);
    if (item) return item.label;
    if (location.pathname.startsWith('/customers/')) return '客户详情';
    if (location.pathname.startsWith('/conversations/')) return '会话详情';
    if (location.pathname.startsWith('/campaigns/')) return '任务详情';
    return '运营中心';
  };

  /** 获取当前页面副标题 */
  const getPageSubtitle = (): string => {
    switch (location.pathname) {
      case '/':
        return 'SCRM 自动化运营概览';
      case '/customers':
        return '管理多平台客户信息和标签';
      case '/customer-groups':
        return '客户分组与人群包管理';
      case '/customer-tags':
        return '客户标签体系管理';
      case '/conversations':
        return '查看和管理客户会话记录';
      case '/campaigns':
        return '创建和管理自动化营销任务';
      case '/message-templates':
        return '快捷回复消息模板管理';
      case '/wework':
        return '企业微信外部联系人与客户群管理';
      case '/risk-rules':
        return '风控规则配置与评估';
      case '/risk-signals':
        return '查看风控触发记录';
      case '/accounts':
        return '管理各平台社媒账号';
      case '/account-health':
        return '账号健康度监控';
      case '/personas':
        return '人设身份管理与维护';
      case '/dashboard':
        return '运营数据分析和趋势看板';
      case '/audit-logs':
        return '操作审计日志查询';
      case '/settings':
        return '平台连接配置与系统参数管理';
      default:
        return 'SCRM 自动化运营引擎';
    }
  };

  const displayName = getDisplayName() || '用户';
  const loggedIn = isAuthenticated();
  const userRole = getRole();

  /**
   * 渲染侧边栏内容 (桌面 Sider 与移动 Drawer 共用)
   * @param compact 紧凑模式 (桌面端折叠), 隐藏搜索框/分组标题/标签/用户卡片, 并启用 Popover
   */
  const renderSiderContent = (compact: boolean) => (
    <>
      {!compact && (
        <div className="menu-search">
          <Input
            placeholder="搜索菜单..."
            prefix={<SearchOutlined />}
            allowClear
            value={menuFilter}
            onChange={e => setMenuFilter(e.target.value)}
            className="menu-search-input"
          />
        </div>
      )}

      <nav className="nav-menu">
        {filteredGroups.map((group) => {
          // 按角色/权限过滤菜单项, 过滤后为空的分组不渲染
          const visibleItems = group.items.filter(item => {
            if (item.adminOnly && userRole !== 'admin') return false;
            if (item.roles && !item.roles.includes(userRole)) return false;
            if (!item.roles && item.permission && !hasPermission(item.permission)) return false;
            return true;
          });
          if (visibleItems.length === 0) return null;
          return (
          <div key={group.group}>
            {!compact && <div className="nav-group-label">{group.group}</div>}
            {visibleItems.map((item) => {
              const isActive = location.pathname === item.key ||
                (item.key !== '/' && location.pathname.startsWith(item.key + '/'));
              const btn = (
                <button
                  key={item.key}
                  className={`nav-button ${isActive ? 'active' : ''}`}
                  onClick={() => handleMenuClick(item.key)}
                  title={item.label}
                >
                  <span className="nav-icon">{item.icon}</span>
                  {!compact && <span className="nav-label">{item.label}</span>}
                </button>
              );
              // 会话列表项: 未读数 > 0 时用 Badge 包裹按钮显示未读数
              const btnWithBadge = item.key === '/conversations' && totalUnread > 0 ? (
                <Badge count={totalUnread} size="small" offset={[6, -2]}>
                  {btn}
                </Badge>
              ) : (
                btn
              );
              // 桌面端折叠状态下, 用 Popover 显示菜单文字 (不改变 collapsed 状态)
              if (compact) {
                return (
                  <Popover
                    key={item.key}
                    content={item.label}
                    placement="right"
                    trigger="hover"
                    mouseEnterDelay={0.2}
                  >
                    {btnWithBadge}
                  </Popover>
                );
              }
              return btnWithBadge;
            })}
          </div>
          );
        })}
      </nav>

      {!compact && (
        <div className="user-card">
          <Avatar
            size={40}
            icon={<UserOutlined />}
            style={{ background: 'linear-gradient(135deg, var(--color-primary-light) 0%, var(--color-primary) 100%)' }}
          />
          <div className="user-info">
            <div className="user-name">{displayName}</div>
            <div className="user-role">
              <Tag color={roleDisplayConfig[userRole]?.color || 'default'} style={{ margin: 0 }}>
                {roleDisplayConfig[userRole]?.label || '用户'}
              </Tag>
            </div>
          </div>
        </div>
      )}
    </>
  );

  return (
    <AntLayout style={{ minHeight: '100vh' }}>
      {/* 桌面端固定侧边栏 */}
      {!isMobile && (
        <Sider
          trigger={null}
          collapsible
          collapsed={collapsed}
          width={260}
          collapsedWidth={72}
          className="app-sider"
          style={{ overflow: 'auto', height: '100vh', position: 'fixed', left: 0, top: 0, bottom: 0 }}
        >
          {renderSiderContent(collapsed)}
        </Sider>
      )}

      {/* 移动端抽屉侧边栏 */}
      {isMobile && (
        <Drawer
          placement="left"
          open={drawerOpen}
          onClose={() => setDrawerOpen(false)}
          width={280}
          className="app-drawer"
          styles={{ body: { padding: 0, display: 'flex', flexDirection: 'column', background: 'var(--color-bg-container)' } }}
        >
          {renderSiderContent(false)}
        </Drawer>
      )}

      <AntLayout
        style={{
          marginLeft: isMobile ? 0 : (collapsed ? 72 : 260),
          transition: 'margin-left 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
          height: '100vh',
          display: 'flex',
          flexDirection: 'column',
        }}
      >
        <Header className="app-header">
          <div className="header-left">
            {isMobile ? (
              <Button
                type="text"
                icon={<MenuOutlined />}
                onClick={() => setDrawerOpen(true)}
                className="collapse-btn"
              />
            ) : (
              <Button
                type="text"
                icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
                onClick={() => setCollapsed(!collapsed)}
                className="collapse-btn"
              />
            )}
            <div className="page-info">
              <span className="page-title">{getPageTitle()}</span>
              <span className="page-subtitle">{getPageSubtitle()}</span>
            </div>
          </div>

          <div className="header-right">
            {/* 全局搜索框: 支持客户/账号/会话关键词搜索, 选中跳转详情页 */}
            <AutoComplete
              className="global-search"
              value={globalSearchValue}
              options={globalSearchOptions}
              style={{ width: 280 }}
              onSearch={handleGlobalSearch}
              onSelect={handleSearchSelect}
              onChange={setGlobalSearchValue}
              placeholder="搜索客户 / 账号 / 会话..."
              allowClear
              filterOption={false}
              notFoundContent={globalSearchLoading ? '搜索中...' : (globalSearchValue ? '无匹配结果' : null)}
              defaultActiveFirstOption={false}
            >
              <Input
                prefix={<SearchOutlined style={{ color: 'var(--color-text-tertiary, #bfbfbf)' }} />}
                allowClear
              />
            </AutoComplete>
            <Tooltip title={isFullscreen ? '退出全屏' : '进入全屏'}>
              <Button
                type="text"
                icon={isFullscreen ? <FullscreenExitOutlined /> : <FullscreenOutlined />}
                onClick={toggleFullscreen}
                className="theme-toggle-btn"
              />
            </Tooltip>
            <Popover
              trigger="click"
              placement="bottomRight"
              overlayStyle={{ width: '380px' }}
              onOpenChange={(open) => {
                // 打开通知面板时将所有通知标记为已读
                if (open) {
                  setNotifications((prev) => prev.map((n) => ({ ...n, read: true })));
                }
              }}
              content={
                <div style={{ maxHeight: '400px', overflowY: 'auto' }}>
                  {notifications.length === 0 ? (
                    <Empty description="暂无通知" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                  ) : (
                    notifications.map((n) => {
                      const config = notificationTypeConfig[n.type] || notificationTypeConfig.SYSTEM;
                      return (
                        <div
                          key={n.id}
                          style={{
                            display: 'flex',
                            gap: '10px',
                            padding: '10px 8px',
                            borderBottom: '1px solid var(--color-border-secondary, #f0f0f0)',
                            background: n.read ? 'transparent' : 'var(--color-primary-bg, #e6f4ff)',
                          }}
                        >
                          <span style={{ color: config.color, fontSize: '16px', marginTop: '2px', flexShrink: 0 }}>
                            {config.icon}
                          </span>
                          <div style={{ flex: 1, minWidth: 0 }}>
                            <div style={{ fontWeight: 600, fontSize: '13px', marginBottom: '2px' }}>
                              {n.title || n.type}
                            </div>
                            {n.content && (
                              <div style={{ fontSize: '12px', color: 'var(--color-text-secondary, #8c8c8c)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                {n.content}
                              </div>
                            )}
                            <div style={{ fontSize: '11px', color: 'var(--color-text-tertiary, #bfbfbf)', marginTop: '3px' }}>
                              {formatRelativeTime(n.timestamp)}
                            </div>
                          </div>
                        </div>
                      );
                    })
                  )}
                </div>
              }
              title={
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span>通知中心</span>
                  {notifications.length > 0 && (
                    <Button
                      type="text"
                      size="small"
                      icon={<DeleteOutlined />}
                      onClick={() => setNotifications([])}
                    >
                      清空
                    </Button>
                  )}
                </div>
              }
            >
              <Badge
                count={notifications.filter((n) => !n.read).length}
                color="#ef4444"
                offset={[-2, 4]}
                overflowCount={99}
              >
                <Button
                  type="text"
                  icon={<BellOutlined />}
                  className="theme-toggle-btn"
                />
              </Badge>
            </Popover>
            <Tooltip title={theme === 'dark' ? '切换亮色模式' : '切换暗色模式'}>
              <Button
                type="text"
                icon={theme === 'dark' ? <SunOutlined /> : <MoonOutlined />}
                onClick={toggleTheme}
                className="theme-toggle-btn"
              />
            </Tooltip>
            {loggedIn ? (
              <Dropdown
                menu={{
                  items: [
                    {
                      key: 'logout',
                      icon: <LogoutOutlined />,
                      label: '退出登录',
                      onClick: handleLogout,
                    },
                  ],
                }}
                placement="bottomRight"
              >
                <div className="user-dropdown">
                  <Avatar
                    size={32}
                    icon={<UserOutlined />}
                    style={{ background: 'linear-gradient(135deg, var(--color-primary-light) 0%, var(--color-primary) 100%)' }}
                  />
                  <span className="user-display-name">{displayName}</span>
                </div>
              </Dropdown>
            ) : (
              <Button
                type="primary"
                className="login-btn"
                onClick={() => navigate('/login')}
              >
                登录
              </Button>
            )}
          </div>
        </Header>

        <Content className="app-content">
          {/* 通过 key 触发页面切换的 CSS 过渡动画 */}
          <div key={location.pathname} className="page-transition">
            <Outlet />
          </div>
        </Content>
      </AntLayout>
    </AntLayout>
  );
}
