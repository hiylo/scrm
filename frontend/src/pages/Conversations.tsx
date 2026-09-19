/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Conversations.tsx
 * Date : 2026/07/26
 * Author : hiylo
 * Contact : hiylo@live.com
 */

import { useEffect, useState, useCallback, useRef, useMemo } from 'react';
import {
  Alert,
  App,
  Avatar,
  Badge,
  Button,
  Card,
  Col,
  Empty,
  Image,
  Input,
  List,
  Pagination,
  Popover,
  Row,
  Select,
  Space,
  Spin,
  Tabs,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import {
  ReloadOutlined,
  ThunderboltOutlined,
  SendOutlined,
  CopyOutlined,
  VideoCameraOutlined,
  FileOutlined,
  AudioOutlined,
  FileImageOutlined,
  CheckCircleOutlined,
  StopOutlined,
  FilterOutlined,
  SnippetsOutlined,
  DownloadOutlined,
  LoadingOutlined,
  CheckOutlined,
  ExclamationCircleOutlined,
  UserOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { apiClient, apiClientInstance } from '../api/client';
import { useDebounce } from '../hooks/useDebounce';
import { useScrmWebSocket } from '../hooks/useScrmWebSocket';
import { formatFriendlyTime } from '../utils/friendlyTime';
import { highlightKeyword } from '../utils/highlight';
import { isImeComposing } from '../utils/imeHelpers';

const { Text, Paragraph } = Typography;

/** 会话实体 */
interface ScrmConversation {
  id: string;
  customerId: string;
  customerNickname?: string;
  customerAvatarUrl?: string;
  accountId: string;
  accountName?: string;
  platformType: string;
  status: string; // ACTIVE / CLOSED / PENDING
  lastMessageSummary?: string;
  lastMessageAt?: string;
  messageCount?: number;
  unreadCount?: number;
  createTime?: string;
}

/** 会话消息实体 */
interface ScrmConversationMessage {
  id: string;
  messageId?: string; // 业务消息 ID (用于获取媒体 URL)
  conversationId: string;
  content: string;
  messageType: string; // TEXT / IMAGE / VIDEO / FILE / VOICE
  direction: string; // IN / OUT 或 INCOMING / OUTGOING
  sentAt: string;
  mediaObjectKey?: string; // 媒体对象 key (媒体类消息)
}

/** AI 总结响应 */
interface ConversationSummaryVo {
  conversationId: string;
  summary: string;
  messageCount: number;
  model: string;
  latencyMs: number;
  summarizedAt: string;
}

/** Spring Page 分页响应 */
interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/** 消息模板 (快捷回复用, 仅取必要字段) */
interface MessageTemplateQuick {
  id: string;
  templateName: string;
  content: string;
  category?: string;
  platformType?: string;
  variables?: string;
}

/** 会话状态映射 */
const statusConfig: Record<string, { label: string; color: string }> = {
  ACTIVE: { label: '进行中', color: 'green' },
  CLOSED: { label: '已关闭', color: 'default' },
  PENDING: { label: '待处理', color: 'orange' },
};

/** 判断消息是否为发出方向 (兼容 OUT / OUTGOING) */
const isOutgoing = (direction: string): boolean => {
  const d = (direction || '').toUpperCase();
  return d === 'OUT' || d === 'OUTGOING';
};

/** 消息时间分组间隔 (分钟), 超过该间隔显示时间分隔线 */
const MESSAGE_GROUP_GAP_MINUTES = 5;

/** 消息发送状态 */
type MessageStatus = 'sending' | 'sent' | 'failed';

/**
 * 会话管理页面
 * 左侧会话列表 + 右侧消息记录 + 消息发送 + AI 总结
 */
export default function Conversations() {
  const { message } = App.useApp();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const urlCustomerId = searchParams.get('customerId');
  // WebSocket 实时推送
  const [wsToken, setWsToken] = useState<string | null>(null);

  useEffect(() => {
    const t = localStorage.getItem('scrm_token');
    setWsToken(t);
  }, []);
  // 会话列表状态
  const [conversations, setConversations] = useState<ScrmConversation[]>([]);
  const [conversationsLoading, setConversationsLoading] = useState(false);
  // 批量标记已读 loading 状态
  const [markingAllRead, setMarkingAllRead] = useState(false);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [keyword, setKeyword] = useState('');
  const debouncedKeyword = useDebounce(keyword, 300);
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  // 当前选中的会话
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const selected = conversations.find((c) => c.id === selectedId) || null;

  /** 选中会话并标记已读 */
  const handleSelectConversation = (id: string) => {
    setSelectedId(id);
    // 切换会话时清空发送状态映射与搜索结果 (新会话从空白开始)
    setMessageStatusMap({});
    setSearchResults([]);
    setSearchKeyword('');
    apiClient.put(`/scrm/conversations/${id}/read`).catch(() => {
      // 标记已读失败不影响主流程
    });
  };

  /** 将所有会话标记为已读 */
  const handleMarkAllRead = async () => {
    setMarkingAllRead(true);
    try {
      // 找出所有有未读消息的会话
      const unreadConversations = conversations.filter((c) => (c.unreadCount || 0) > 0);
      if (unreadConversations.length === 0) {
        message.info('没有未读会话');
        return;
      }
      // 并行标记所有未读会话为已读 (使用 PUT 与单条标记已读保持一致)
      const results = await Promise.allSettled(
        unreadConversations.map((c) => apiClient.put(`/scrm/conversations/${c.id}/read`)),
      );
      const successCount = results.filter((r) => r.status === 'fulfilled').length;
      message.success(`已标记 ${successCount} 个会话为已读`);
      // 更新本地状态: 清零所有未读数
      setConversations((prev) => prev.map((c) => ({ ...c, unreadCount: 0 })));
    } catch {
      message.error('标记已读失败');
    } finally {
      setMarkingAllRead(false);
    }
  };

  /** 标记单个会话为已读 (不触发列表项选中) */
  const handleMarkOneRead = async (conversationId: string) => {
    try {
      await apiClient.put(`/scrm/conversations/${conversationId}/read`);
      // 更新本地状态: 清零该会话的未读数
      setConversations((prev) =>
        prev.map((c) => (c.id === conversationId ? { ...c, unreadCount: 0 } : c)),
      );
      message.success('已标记为已读');
    } catch {
      // 错误已由 axios 拦截器统一提示
    }
  };
  // 消息列表
  const [messages, setMessages] = useState<ScrmConversationMessage[]>([]);
  const [messagesLoading, setMessagesLoading] = useState(false);
  const [msgPage, setMsgPage] = useState(0);
  const [msgSize, setMsgSize] = useState(50);
  const [msgTotal, setMsgTotal] = useState(0);
  // AI 总结
  const [summary, setSummary] = useState<ConversationSummaryVo | null>(null);
  const [summarizing, setSummarizing] = useState(false);
  // 消息导出
  const [exporting, setExporting] = useState(false);
  // 消息发送
  const [messageInput, setMessageInput] = useState('');
  const [sending, setSending] = useState(false);
  /** 消息 ID → 发送状态映射 (仅追踪本次会话内发送的消息) */
  const [messageStatusMap, setMessageStatusMap] = useState<Record<string, MessageStatus>>({});
  // 快捷模板
  const [templates, setTemplates] = useState<MessageTemplateQuick[]>([]);
  const [templatePopoverOpen, setTemplatePopoverOpen] = useState(false);
  const [templatesLoading, setTemplatesLoading] = useState(false);
  // 快捷模板搜索关键词 (按模板名称/内容过滤)
  const [templateSearch, setTemplateSearch] = useState('');
  // 消息搜索
  const [searchResults, setSearchResults] = useState<ScrmConversationMessage[]>([]);
  const [searching, setSearchSearching] = useState(false);
  const [searchKeyword, setSearchKeyword] = useState('');
  // 媒体消息 URL 缓存 (messageId → 预签名 URL)
  const [mediaUrls, setMediaUrls] = useState<Record<string, string>>({});
  const [mediaLoading, setMediaLoading] = useState<Record<string, boolean>>({});
  // 消息列表滚动容器 ref (用于自动滚动到底部)
  const messagesContainerRef = useRef<HTMLDivElement>(null);
  // 记录用户是否手动向上滚动 (避免新消息时强制拉到底部)
  const isNearBottomRef = useRef(true);
  const prevMsgCountRef = useRef(0);

  /** 检测用户是否在底部附近 (底部 80px 范围内) */
  const handleScroll = useCallback(() => {
    const el = messagesContainerRef.current;
    if (!el) return;
    const distFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    isNearBottomRef.current = distFromBottom < 80;
  }, []);

  /** 拉取会话分页列表 */
  const fetchConversations = useCallback(async () => {
    setConversationsLoading(true);
    try {
      const params = new URLSearchParams();
      params.set('page', String(page));
      params.set('size', String(size));
      if (debouncedKeyword) params.set('keyword', debouncedKeyword);
      if (statusFilter && statusFilter !== 'ALL') params.set('status', statusFilter);
      const data = await apiClient.get<Page<ScrmConversation>>(
        `/scrm/conversations?${params.toString()}`,
      );
      setConversations(data.content || []);
      setTotal(data.totalElements || 0);
    } catch {
      // 错误已由 axios 拦截器统一提示
      setConversations([]);
      setTotal(0);
    } finally {
      setConversationsLoading(false);
    }
  }, [page, size, debouncedKeyword, statusFilter]);

  useEffect(() => {
    fetchConversations();
  }, [fetchConversations]);

  // URL 参数指定客户 ID 时，自动选中该客户的会话
  useEffect(() => {
    if (urlCustomerId && conversations.length > 0 && !selectedId) {
      const matched = conversations.find(
        (c) => String(c.customerId) === String(urlCustomerId),
      );
      if (matched) {
        handleSelectConversation(matched.id);
      } else {
        // 当前页未找到，尝试通过 by-customer API 查找
        apiClient
          .get<Page<ScrmConversation>>(`/scrm/conversations/by-customer/${urlCustomerId}?size=1`)
          .then((page) => {
            const conv = page.content?.[0];
            if (conv) handleSelectConversation(String(conv.id));
          })
          .catch(() => {
            // 客户无会话记录
          });
      }
    }
  }, [urlCustomerId, conversations, selectedId]);

  // 防抖关键词变化时, 重置到第一页
  useEffect(() => {
    setPage(0);
  }, [debouncedKeyword]);

  // 状态筛选变化时, 重置到第一页
  useEffect(() => {
    setPage(0);
  }, [statusFilter]);

  /** 拉取指定会话的消息记录 */
  const fetchMessages = useCallback(async () => {
    if (!selectedId) {
      setMessages([]);
      setMsgTotal(0);
      return;
    }
    setMessagesLoading(true);
    try {
      const data = await apiClient.get<Page<ScrmConversationMessage>>(
        `/scrm/conversations/${selectedId}/messages?page=${msgPage}&size=${msgSize}`,
      );
      setMessages(data.content || []);
      setMsgTotal(data.totalElements || 0);
    } catch {
      // 错误已由 axios 拦截器统一提示
      setMessages([]);
      setMsgTotal(0);
    } finally {
      setMessagesLoading(false);
    }
  }, [selectedId, msgPage, msgSize]);

  useEffect(() => {
    // 选中会话变化时重置消息与总结, 然后加载消息
    if (selectedId) {
      setSummary(null);
      setMsgPage(0);
      // 清空媒体 URL 缓存 (避免上一会话的媒体 URL 残留)
      setMediaUrls({});
      setMediaLoading({});
      fetchMessages();
    } else {
      setMessages([]);
      setMsgTotal(0);
      setSummary(null);
      setMediaUrls({});
      setMediaLoading({});
    }
  }, [selectedId, fetchMessages]);

  // 消息列表变化时, 仅在用户位于底部附近或有新消息时自动滚动
  useEffect(() => {
    const el = messagesContainerRef.current;
    if (!el) return;
    const hasNewMessages = messages.length > prevMsgCountRef.current;
    prevMsgCountRef.current = messages.length;
    // 切换会话时总是滚动到底部; 新消息时仅在用户位于底部附近才滚动
    if (isNearBottomRef.current || !hasNewMessages) {
      el.scrollTop = el.scrollHeight;
    }
  }, [messages, selectedId]);

  // WebSocket 回调使用 ref 保存最新函数引用，避免依赖频繁变化导致重连
  const fetchMessagesRef = useRef(fetchMessages);
  fetchMessagesRef.current = fetchMessages;
  const fetchConversationsRef = useRef(fetchConversations);
  fetchConversationsRef.current = fetchConversations;

  const { connected: wsConnected } = useScrmWebSocket({
    token: wsToken,
    onNotification: (notif) => {
      if (notif.type === 'NEW_MESSAGE' || notif.type === 'CONVERSATION_EVENT') {
        const convId = notif.data?.conversationId
          ? String(notif.data.conversationId)
          : null;
        if (selectedId && convId === String(selectedId)) {
          // 增量追加单条消息, 避免全量刷新
          const newMsg = notif.data?.message as ScrmConversationMessage | undefined;
          if (newMsg && newMsg.id) {
            setMessages((prev) => {
              // 避免重复追加 (WebSocket 可能多次推送同一条消息)
              if (prev.some((m) => m.id === newMsg.id)) return prev;
              return [...prev, newMsg];
            });
          } else {
            // 消息体不完整时回退到全量拉取
            fetchMessagesRef.current();
          }
        }
        // 会话列表更新 lastMessageSummary / unreadCount, 仍需刷新
        fetchConversationsRef.current();
      }
    },
  });

  /** 触发 AI 总结 */
  const handleSummarize = async () => {
    if (!selectedId) return;
    setSummarizing(true);
    try {
      const data = await apiClient.post<ConversationSummaryVo>(
        `/scrm/conversations/${selectedId}/summarize`,
      );
      setSummary(data);
      message.success('AI 总结已生成');
    } catch {
      // 错误已由 axios 拦截器统一提示
    } finally {
      setSummarizing(false);
    }
  };

  /** 复制 AI 总结内容到剪贴板 */
  const handleCopySummary = async () => {
    if (!summary) return;
    try {
      await navigator.clipboard.writeText(summary.summary);
      message.success('已复制到剪贴板');
    } catch {
      message.error('复制失败, 请手动选择文本复制');
    }
  };

  /** 复制消息内容到剪贴板 */
  const handleCopyMessage = async (content: string) => {
    if (!content) return;
    try {
      await navigator.clipboard.writeText(content);
      message.success('已复制');
    } catch {
      message.error('复制失败');
    }
  };

  /** 导出当前会话的消息记录 (Excel 文件下载) */
  const handleExport = async () => {
    if (!selectedId) return;
    setExporting(true);
    try {
      const response = await apiClientInstance.get(
        `/scrm/conversations/${selectedId}/export`,
        { params: { format: 'xlsx' }, responseType: 'blob' },
      );
      // 创建下载链接并触发点击
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.download = `conversation_messages_${selectedId}.xlsx`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);
      message.success('导出成功');
    } catch {
      // 错误已由 axios 拦截器统一提示
    } finally {
      setExporting(false);
    }
  };

  /** 加载已启用的消息模板列表 (用于快捷回复) */
  const loadTemplates = useCallback(async () => {
    setTemplatesLoading(true);
    try {
      const data = await apiClient.get<Page<MessageTemplateQuick>>(
        `/scrm/message-templates?enabled=true&size=50`,
      );
      setTemplates(data.content || []);
    } catch {
      setTemplates([]);
    } finally {
      setTemplatesLoading(false);
    }
  }, []);

  // 组件挂载时预加载模板列表, 打开快捷回复 Popover 时即可直接展示
  useEffect(() => {
    loadTemplates();
  }, [loadTemplates]);

  /** 按搜索关键词过滤模板 (匹配模板名称或内容), 最多展示 50 条 */
  const filteredTemplates = useMemo(() => {
    const kw = templateSearch.trim().toLowerCase();
    if (!kw) return templates.slice(0, 50);
    return templates
      .filter(
        (t) =>
          (t.templateName || '').toLowerCase().includes(kw) ||
          (t.content || '').toLowerCase().includes(kw),
      )
      .slice(0, 50);
  }, [templates, templateSearch]);

  /** 选择模板后填入输入框 (替换变量占位符为提示文本) */
  const handleSelectTemplate = (tpl: MessageTemplateQuick) => {
    let content = tpl.content || '';
    // 将 {{variable}} 替换为 [变量名] 提示用户修改
    content = content.replace(/\{\{(\w+)\}\}/g, '[$1]');
    // 输入框已有内容时追加换行分隔, 空则直接替换
    if (messageInput.trim()) {
      setMessageInput((prev) => `${prev}\n${content}`);
    } else {
      setMessageInput(content);
    }
    setTemplatePopoverOpen(false);
    // 聚焦输入框, 方便用户直接修改变量值并发送
    const textarea = document.querySelector('textarea');
    if (textarea) textarea.focus();
  };

  /** 发送消息 */
  const handleSendMessage = async () => {
    if (!selectedId || !messageInput.trim()) return;
    const content = messageInput.trim();
    // 生成本地临时 ID, 用于乐观插入与发送状态追踪
    const tempId = `local_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
    const optimisticMsg: ScrmConversationMessage = {
      id: tempId,
      conversationId: selectedId,
      content,
      messageType: 'TEXT',
      direction: 'OUT',
      sentAt: dayjs().format('YYYY-MM-DDTHH:mm:ss'),
    };
    // 乐观插入消息并标记为发送中
    setMessages((prev) => [...prev, optimisticMsg]);
    setMessageStatusMap((prev) => ({ ...prev, [tempId]: 'sending' }));
    setSending(true);
    try {
      const sentMsg = await apiClient.post<ScrmConversationMessage>(
        `/scrm/conversations/messages`,
        {
          messageId: `msg_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
          conversationId: selectedId,
          messageType: 'TEXT',
          direction: 'OUT',
          content,
          sentAt: dayjs().format('YYYY-MM-DDTHH:mm:ss'),
        },
      );
      // 用服务端返回的真实消息替换临时消息, 避免与 WebSocket 推送重复
      if (sentMsg && sentMsg.id) {
        setMessages((prev) => {
          if (prev.some((m) => m.id === sentMsg.id)) {
            // WebSocket 已推送过该消息, 仅移除临时消息
            return prev.filter((m) => m.id !== tempId);
          }
          return prev.map((m) => (m.id === tempId ? sentMsg : m));
        });
        // 状态转移: 临时 ID → 真实 ID, 标记为已发送
        setMessageStatusMap((prev) => {
          const next = { ...prev };
          delete next[tempId];
          next[sentMsg.id] = 'sent';
          return next;
        });
      } else {
        // 服务端未返回消息, 直接标记临时消息为已发送
        setMessageStatusMap((prev) => ({ ...prev, [tempId]: 'sent' }));
      }
      setMessageInput('');
      // 仅刷新会话列表以更新 lastMessageSummary, 不重新拉取消息
      fetchConversations();
    } catch {
      // 发送失败, 标记临时消息为失败状态
      setMessageStatusMap((prev) => ({ ...prev, [tempId]: 'failed' }));
      // 错误已由 axios 拦截器统一提示
    } finally {
      setSending(false);
    }
  };

  /**
   * 重试发送失败的消息
   * 移除失败消息后用原内容重新发送, 复用 handleSendMessage 的乐观插入逻辑
   */
  const handleRetrySend = async (failedMsgId: string) => {
    const failedMsg = messages.find((m) => m.id === failedMsgId);
    if (!failedMsg || !selectedId) return;
    const content = failedMsg.content;
    if (!content) return;
    // 移除失败消息, 重置状态
    setMessages((prev) => prev.filter((m) => m.id !== failedMsgId));
    setMessageStatusMap((prev) => {
      const next = { ...prev };
      delete next[failedMsgId];
      return next;
    });
    // 生成新临时 ID 重新发送
    const tempId = `local_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
    const optimisticMsg: ScrmConversationMessage = {
      id: tempId,
      conversationId: selectedId,
      content,
      messageType: 'TEXT',
      direction: 'OUT',
      sentAt: dayjs().format('YYYY-MM-DDTHH:mm:ss'),
    };
    setMessages((prev) => [...prev, optimisticMsg]);
    setMessageStatusMap((prev) => ({ ...prev, [tempId]: 'sending' }));
    setSending(true);
    try {
      const sentMsg = await apiClient.post<ScrmConversationMessage>(
        `/scrm/conversations/messages`,
        {
          messageId: `msg_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
          conversationId: selectedId,
          messageType: 'TEXT',
          direction: 'OUT',
          content,
          sentAt: dayjs().format('YYYY-MM-DDTHH:mm:ss'),
        },
      );
      if (sentMsg && sentMsg.id) {
        setMessages((prev) => {
          if (prev.some((m) => m.id === sentMsg.id)) {
            return prev.filter((m) => m.id !== tempId);
          }
          return prev.map((m) => (m.id === tempId ? sentMsg : m));
        });
        setMessageStatusMap((prev) => {
          const next = { ...prev };
          delete next[tempId];
          next[sentMsg.id] = 'sent';
          return next;
        });
      } else {
        setMessageStatusMap((prev) => ({ ...prev, [tempId]: 'sent' }));
      }
      fetchConversations();
    } catch {
      setMessageStatusMap((prev) => ({ ...prev, [tempId]: 'failed' }));
    } finally {
      setSending(false);
    }
  };

  /** 消息列表按时间升序排列 (旧→新), 确保新消息在底部 */
  const sortedMessages = useMemo(() => {
    return [...messages].sort((a, b) => {
      const ta = dayjs(a.sentAt).valueOf();
      const tb = dayjs(b.sentAt).valueOf();
      return ta - tb;
    });
  }, [messages]);

  /** 按时间间隔分组消息 (超过 5 分钟显示时间分隔线) */
  const groupedMessages = useMemo(() => {
    const groups: Array<{ time: string; messages: ScrmConversationMessage[] }> = [];
    let currentGroup: ScrmConversationMessage[] = [];
    let groupStartTime: Dayjs | null = null;
    let lastMsgTime: Dayjs | null = null;

    for (const msg of sortedMessages) {
      const msgTime = dayjs(msg.sentAt);
      if (lastMsgTime === null || msgTime.diff(lastMsgTime, 'minute') > MESSAGE_GROUP_GAP_MINUTES) {
        // 超过间隔, 开启新分组
        if (currentGroup.length > 0 && groupStartTime) {
          groups.push({ time: groupStartTime.format('YYYY-MM-DD HH:mm'), messages: currentGroup });
        }
        currentGroup = [];
        groupStartTime = msgTime;
      }
      currentGroup.push(msg);
      lastMsgTime = msgTime;
    }
    if (currentGroup.length > 0 && groupStartTime) {
      groups.push({ time: groupStartTime.format('YYYY-MM-DD HH:mm'), messages: currentGroup });
    }
    return groups;
  }, [messages]);

  /**
   * 获取媒体消息的预签名 URL
   * 调用 GET /scrm/conversations/messages/{messageId}/media, 结果缓存到 mediaUrls state
   */
  const fetchMediaUrl = useCallback(async (msg: ScrmConversationMessage) => {
    const bizId = msg.messageId || msg.id;
    if (!bizId || mediaUrls[bizId] || mediaLoading[bizId]) return;
    setMediaLoading((prev) => ({ ...prev, [bizId]: true }));
    try {
      const url = await apiClient.get<string>(`/scrm/conversations/messages/${bizId}/media`);
      if (url) {
        setMediaUrls((prev) => ({ ...prev, [bizId]: url }));
      }
    } catch {
      // 媒体 URL 获取失败, 静默处理
    } finally {
      setMediaLoading((prev) => ({ ...prev, [bizId]: false }));
    }
  }, [mediaUrls, mediaLoading]);

  /** 渲染消息内容 (按消息类型展示, 媒体类消息通过预签名 URL 展示) */
  const renderMessageContent = (msg: ScrmConversationMessage) => {
    const bizId = msg.messageId || msg.id;
    const mediaUrl = bizId ? mediaUrls[bizId] : undefined;
    const isLoading = bizId ? mediaLoading[bizId] : false;

    switch (msg.messageType) {
      case 'IMAGE':
        // 有预签名 URL 时展示图片, 否则触发获取并显示加载占位
        if (mediaUrl) {
          return <Image src={mediaUrl} width={200} style={{ borderRadius: 8, maxWidth: '100%' }} />;
        }
        // 延迟触发获取 (避免渲染期间 setState)
        if (bizId && !isLoading) {
          setTimeout(() => fetchMediaUrl(msg), 0);
        }
        return (
          <div style={{ width: 200, height: 120, display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'var(--color-fill-tertiary)', borderRadius: 8 }}>
            <Spin size="small" tip="加载图片..." />
          </div>
        );
      case 'VIDEO':
        if (mediaUrl) {
          return <video src={mediaUrl} controls style={{ maxWidth: 300, borderRadius: 8 }} />;
        }
        if (bizId && !isLoading) {
          setTimeout(() => fetchMediaUrl(msg), 0);
        }
        return <Tag icon={<VideoCameraOutlined />} color="processing">视频加载中...</Tag>;
      case 'VOICE':
        if (mediaUrl) {
          return <audio src={mediaUrl} controls style={{ maxWidth: 240 }} />;
        }
        if (bizId && !isLoading) {
          setTimeout(() => fetchMediaUrl(msg), 0);
        }
        return <Tag icon={<AudioOutlined />} color="processing">语音加载中...</Tag>;
      case 'FILE':
        if (mediaUrl) {
          return (
            <a href={mediaUrl} target="_blank" rel="noopener noreferrer">
              <Tag icon={<FileOutlined />} color="blue" style={{ cursor: 'pointer' }}>下载文件</Tag>
            </a>
          );
        }
        if (bizId && !isLoading) {
          setTimeout(() => fetchMediaUrl(msg), 0);
        }
        return <Tag icon={<FileOutlined />} color="processing">文件加载中...</Tag>;
      case 'TEXT':
      default:
        return <div style={{ whiteSpace: 'pre-wrap' }}>{msg.content}</div>;
    }
  };

  return (
    <Tabs
      defaultActiveKey="scrm"
      items={[
        {
          key: 'scrm',
          label: '会话列表',
          children: (
            <>
    <Row gutter={16}>
      {/* 左侧会话列表 */}
      <Col xs={24} md={8}>
        <Card
          title="会话列表"
          size="small"
          styles={{ body: { padding: 0 } }}
          extra={
            <Space>
              <Badge
                status={wsConnected ? 'success' : 'default'}
                text={wsConnected ? '在线' : '离线'}
                style={{ fontSize: 11 }}
              />
              {/* 批量标记已读按钮 */}
              <Button
                size="small"
                icon={<CheckCircleOutlined />}
                onClick={handleMarkAllRead}
                loading={markingAllRead}
              >
                全部已读
              </Button>
              <Button
                size="small"
                icon={<ReloadOutlined />}
                onClick={fetchConversations}
                loading={conversationsLoading}
              >
                刷新
              </Button>
            </Space>
          }
        >
          <div style={{ padding: 12 }}>
            <Space direction="vertical" style={{ width: '100%' }} size={8}>
              <Input
                placeholder="搜索客户昵称 / 账号"
                allowClear
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
              />
              <Select
                style={{ width: '100%' }}
                value={statusFilter}
                onChange={(value) => setStatusFilter(value)}
                suffixIcon={<FilterOutlined />}
                options={[
                  { value: 'ALL', label: '全部状态' },
                  { value: 'ACTIVE', label: '进行中' },
                  { value: 'PENDING', label: '待处理' },
                  { value: 'CLOSED', label: '已关闭' },
                ]}
              />
            </Space>
          </div>
          <Spin spinning={conversationsLoading}>
            {conversations.length === 0 && !conversationsLoading ? (
              <Empty description="暂无会话" style={{ padding: 48 }} />
            ) : (
              <List<ScrmConversation>
                dataSource={conversations}
                renderItem={(item) => {
                  const cfg = statusConfig[item.status] || { label: item.status, color: 'default' };
                  const active = item.id === selectedId;
                  const hasUnread = (item.unreadCount ?? 0) > 0;
                  return (
                    <List.Item
                      onClick={() => handleSelectConversation(item.id)}
                      style={{
                        cursor: 'pointer',
                        padding: '12px 16px',
                        // 选中态使用主题色浅底背景, 适配暗色模式
                        background: active ? 'var(--color-primary-bg)' : undefined,
                        borderLeft: active ? '3px solid var(--color-primary)' : '3px solid transparent',
                      }}
                    >
                      <List.Item.Meta
                        avatar={
                          <Badge dot={hasUnread} offset={[-4, 4]} status="processing">
                            <Avatar src={item.customerAvatarUrl}>
                              {item.customerNickname?.[0]?.toUpperCase()}
                            </Avatar>
                          </Badge>
                        }
                        title={
                          <div
                            style={{
                              display: 'flex',
                              justifyContent: 'space-between',
                              alignItems: 'center',
                            }}
                          >
                            <Text strong>
                              {item.customerNickname || `客户 ${item.customerId}`}
                            </Text>
                            <Space size={4}>
                              {/* 单条会话标记已读按钮 (仅有未读消息时展示) */}
                              {hasUnread && (
                                <Button
                                  size="small"
                                  type="link"
                                  icon={<CheckOutlined />}
                                  style={{ padding: '0 4px', fontSize: 11, height: 20 }}
                                  onClick={(e) => {
                                    // 阻止事件冒泡, 避免触法列表项的点击选中
                                    e.stopPropagation();
                                    handleMarkOneRead(item.id);
                                  }}
                                >
                                  标记已读
                                </Button>
                              )}
                              <Tag color={cfg.color} style={{ marginInlineStart: 0 }}>
                                {cfg.label}
                              </Tag>
                            </Space>
                          </div>
                        }
                        description={
                          <div>
                            <Paragraph
                              type="secondary"
                              ellipsis={{ rows: 1 }}
                              style={{ marginBottom: 4, fontSize: 12 }}
                            >
                              {item.lastMessageSummary || '暂无消息'}
                            </Paragraph>
                            <div
                              style={{
                                display: 'flex',
                                justifyContent: 'space-between',
                                fontSize: 11,
                                // 次要时间文本使用三级文本颜色, 适配暗色模式
                                color: 'var(--color-text-tertiary)',
                              }}
                            >
                              <span>
                                {formatFriendlyTime(item.lastMessageAt)}
                              </span>
                              <span>{item.messageCount ?? 0} 条消息</span>
                            </div>
                          </div>
                        }
                      />
                    </List.Item>
                  );
                }}
              />
            )}
          </Spin>
          <div style={{ padding: 12, textAlign: 'center' }}>
            <Pagination
              size="small"
              current={page + 1}
              pageSize={size}
              total={total}
              showSizeChanger
              showTotal={(t) => `共 ${t} 条`}
              onChange={(p, s) => {
                setPage(p - 1);
                setSize(s);
              }}
            />
          </div>
        </Card>
      </Col>

      {/* 右侧消息区域 */}
      <Col xs={24} md={16}>
        <Card
          size="small"
          title={
            selected ? (
              <Space wrap>
                <Avatar src={selected.customerAvatarUrl} size="small">
                  {selected.customerNickname?.[0]?.toUpperCase()}
                </Avatar>
                <span>{selected.customerNickname || `客户 ${selected.customerId}`}</span>
                <Tag>{selected.accountName || `账号 ${selected.accountId}`}</Tag>
                <Tag>{selected.platformType}</Tag>
                {(() => {
                  const cfg = statusConfig[selected.status] || {
                    label: selected.status,
                    color: 'default',
                  };
                  return <Tag color={cfg.color}>{cfg.label}</Tag>;
                })()}
              </Space>
            ) : (
              <Text type="secondary">请选择左侧会话查看消息</Text>
            )
          }
          extra={
            selected && (
              <Space>
                <Input.Search
                  placeholder="搜索消息"
                  allowClear
                  size="small"
                  style={{ width: 150 }}
                  onSearch={async (value) => {
                    if (!value.trim() || !selectedId) return;
                    setSearchKeyword(value.trim());
                    setSearchSearching(true);
                    try {
                      const data = await apiClient.get<ScrmConversationMessage[]>(
                        `/scrm/conversations/${selectedId}/messages/search?keyword=${encodeURIComponent(value.trim())}`,
                      );
                      setSearchResults(data || []);
                    } catch {
                      setSearchResults([]);
                    } finally {
                      setSearchSearching(false);
                    }
                  }}
                  loading={searching}
                />
                <Button
                  type="primary"
                  icon={<ThunderboltOutlined />}
                  loading={summarizing}
                  onClick={handleSummarize}
                >
                  AI 总结
                </Button>
                {/* 跳转客户详情页: 快速查看客户档案、标签、备注 */}
                <Button
                  size="small"
                  icon={<UserOutlined />}
                  onClick={() => navigate(`/customers/${selected.customerId}`)}
                >
                  查看客户
                </Button>
                {/* 导出会话消息 (Excel 下载) */}
                <Button
                  icon={<DownloadOutlined />}
                  loading={exporting}
                  onClick={handleExport}
                  size="small"
                >
                  导出
                </Button>
                {selected.status === 'CLOSED' ? (
                  <Button
                    size="small"
                    icon={<CheckCircleOutlined />}
                    onClick={async () => {
                      if (!selectedId) return;
                      try {
                        await apiClient.put(`/scrm/conversations/${selectedId}/status?status=ACTIVE`);
                        message.success('会话已重新开启');
                        fetchConversations();
                      } catch {
                        // 错误已由拦截器处理
                      }
                    }}
                  >
                    重新开启
                  </Button>
                ) : (
                  <Button
                    size="small"
                    danger
                    icon={<StopOutlined />}
                    onClick={async () => {
                      if (!selectedId) return;
                      try {
                        await apiClient.put(`/scrm/conversations/${selectedId}/status?status=CLOSED`);
                        message.success('会话已关闭');
                        fetchConversations();
                      } catch {
                        // 错误已由拦截器处理
                      }
                    }}
                  >
                    关闭会话
                  </Button>
                )}
              </Space>
            )
          }
        >
          {!selected ? (
            <Empty description="请选择会话" style={{ marginTop: 80 }} />
          ) : (
            <>
              {/* 消息搜索结果面板 (有搜索结果时在消息列表上方展示) */}
              {searchResults.length > 0 && (
                <Card
                  size="small"
                  style={{ marginBottom: 8, maxHeight: 240, overflowY: 'auto' }}
                  title={
                    <Space size="small">
                      <SnippetsOutlined />
                      <span>搜索 "{searchKeyword}" - 找到 {searchResults.length} 条结果</span>
                    </Space>
                  }
                  extra={
                    <Button
                      size="small"
                      type="text"
                      onClick={() => {
                        setSearchResults([]);
                        setSearchKeyword('');
                      }}
                    >
                      关闭
                    </Button>
                  }
                >
                  <List
                    size="small"
                    dataSource={searchResults}
                    renderItem={(msg) => {
                      const out = isOutgoing(msg.direction);
                      return (
                        <List.Item style={{ padding: '6px 0' }}>
                          <div style={{ width: '100%', minWidth: 0 }}>
                            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 2 }}>
                              <Tag color={out ? 'blue' : 'default'} style={{ margin: 0 }}>
                                {out ? '发出' : '收到'}
                              </Tag>
                              <span style={{ fontSize: 11, color: 'var(--color-text-tertiary)' }}>
                                {dayjs(msg.sentAt).format('MM-DD HH:mm')}
                              </span>
                            </div>
                            <div
                              style={{
                                fontSize: 13,
                                overflow: 'hidden',
                                textOverflow: 'ellipsis',
                                whiteSpace: 'nowrap',
                                color: 'var(--color-text)',
                              }}
                            >
                              {msg.content
                                ? highlightKeyword(msg.content, searchKeyword)
                                : `[${msg.messageType}消息]`}
                            </div>
                          </div>
                        </List.Item>
                      );
                    }}
                  />
                </Card>
              )}
              {/* 消息列表 (可滚动) */}
              <div
                ref={messagesContainerRef}
                onScroll={handleScroll}
                style={{
                  maxHeight: 'calc(100vh - 360px)',
                  minHeight: 300,
                  overflowY: 'auto',
                  paddingRight: 4,
                }}
              >
                {messagesLoading ? (
                  <div style={{ textAlign: 'center', padding: 48 }}>
                    <Spin />
                  </div>
                ) : messages.length === 0 ? (
                  <Empty description="暂无消息" />
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                    {groupedMessages.map((group, gi) => (
                      <div key={gi}>
                        {/* 时间分隔线 (第一组不显示, 后续组显示该组首条消息的时间) */}
                        {gi > 0 && (
                          <div style={{ textAlign: 'center', margin: '12px 0' }}>
                            <span
                              style={{
                                // 时间分隔线使用填充背景色, 适配暗色模式
                                background: 'var(--color-fill-tertiary)',
                                padding: '2px 8px',
                                borderRadius: 4,
                                fontSize: 11,
                                // 时间分隔线文本使用三级文本颜色, 适配暗色模式
                                color: 'var(--color-text-tertiary)',
                              }}
                            >
                              {group.time}
                            </span>
                          </div>
                        )}
                        {group.messages.map((msg) => {
                          const out = isOutgoing(msg.direction);
                          return (
                            <div
                              key={msg.id}
                              style={{
                                display: 'flex',
                                justifyContent: out ? 'flex-end' : 'flex-start',
                                alignItems: 'flex-end',
                                marginBottom: 8,
                                gap: 8,
                              }}
                            >
                              {!out && (
                                <Avatar size={32} style={{ flexShrink: 0, background: 'var(--color-primary)' }}>
                                  {selected?.customerNickname?.[0]?.toUpperCase() || '客'}
                                </Avatar>
                              )}
                              <div style={{ display: 'flex', flexDirection: 'column', maxWidth: '70%' }}>
                                <div
                                  className="msg-bubble"
                                  style={{
                                    padding: '8px 12px',
                                    borderRadius: out ? '12px 4px 12px 12px' : '4px 12px 12px 12px',
                                    // 消息气泡: 发出使用主题色, 接收使用填充背景色, 适配暗色模式
                                    background: out ? 'var(--color-primary)' : 'var(--color-fill-tertiary)',
                                    color: out ? '#fff' : 'inherit',
                                    wordBreak: 'break-word',
                                  }}
                                >
                                  {msg.messageType && msg.messageType !== 'TEXT' && (
                                    <div style={{ marginBottom: 4 }}>
                                      <Tag
                                        style={{
                                          background: out ? 'rgba(255,255,255,0.2)' : undefined,
                                          color: out ? '#fff' : undefined,
                                          borderColor: 'transparent',
                                        }}
                                        icon={
                                          msg.messageType === 'IMAGE' ? <FileImageOutlined /> : undefined
                                        }
                                      >
                                        {msg.messageType}
                                      </Tag>
                                    </div>
                                  )}
                                  {renderMessageContent(msg)}
                                  {msg.messageType === 'TEXT' && msg.content && (
                                    <Tooltip title="复制消息">
                                      <Button
                                        size="small"
                                        type="text"
                                        icon={<CopyOutlined />}
                                        className="msg-copy-btn"
                                        style={{
                                          position: 'absolute',
                                          top: 4,
                                          right: out ? undefined : 4,
                                          left: out ? 4 : undefined,
                                          fontSize: 12,
                                          color: out ? '#fff' : 'var(--color-text-tertiary)',
                                        }}
                                        onClick={() => handleCopyMessage(msg.content)}
                                      />
                                    </Tooltip>
                                  )}
                                  <div
                                    style={{
                                      fontSize: 11,
                                      marginTop: 4,
                                      opacity: 0.75,
                                      textAlign: out ? 'right' : 'left',
                                    }}
                                  >
                                    {dayjs(msg.sentAt).format(
                                      dayjs(msg.sentAt).isSame(dayjs(), 'day') ? 'HH:mm' : 'MM-DD HH:mm',
                                    )}
                                  </div>
                                </div>
                                {/* 发送状态指示器: 仅展示本次会话内发送的消息 (历史消息不展示) */}
                                {out && messageStatusMap[msg.id] && (
                                  <div style={{ textAlign: 'right', marginTop: 2, fontSize: 11 }}>
                                    {messageStatusMap[msg.id] === 'sending' && (
                                      <span style={{ color: 'var(--ant-color-text-tertiary)' }}>
                                        <LoadingOutlined /> 发送中...
                                      </span>
                                    )}
                                    {messageStatusMap[msg.id] === 'sent' && (
                                      <span style={{ color: 'var(--ant-color-text-tertiary)' }}>
                                        <CheckOutlined /> 已发送
                                      </span>
                                    )}
                                    {messageStatusMap[msg.id] === 'failed' && (
                                      <Space size="small">
                                        <span style={{ color: 'var(--ant-color-error)' }}>
                                          <ExclamationCircleOutlined /> 发送失败
                                        </span>
                                        <Button
                                          size="small"
                                          type="link"
                                          style={{ padding: 0, fontSize: 11 }}
                                          disabled={sending}
                                          onClick={() => handleRetrySend(msg.id)}
                                        >
                                          重试
                                        </Button>
                                      </Space>
                                    )}
                                  </div>
                                )}
                              </div>
                              {out && (
                                <Avatar size={32} style={{ flexShrink: 0, background: 'var(--color-primary)' }}>
                                  我
                                </Avatar>
                              )}
                            </div>
                          );
                        })}
                      </div>
                    ))}
                  </div>
                )}

                {/* AI 总结结果 (用 Alert 组件展示) */}
                {summary && (
                  <Alert
                    type="info"
                    showIcon
                    icon={<ThunderboltOutlined />}
                    style={{ marginTop: 16, textAlign: 'left' }}
                    message={
                      <Space>
                        <span>AI 会话总结</span>
                        <Tag>{summary.model}</Tag>
                        <Button
                          size="small"
                          type="text"
                          icon={<CopyOutlined />}
                          onClick={handleCopySummary}
                        >
                          复制
                        </Button>
                      </Space>
                    }
                    description={
                      <div>
                        <Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 8 }}>
                          {summary.summary}
                        </Paragraph>
                        <Space style={{ fontSize: 12, color: 'var(--color-text-tertiary)' }} split={<span>·</span>}>
                          <span>消息数: {summary.messageCount}</span>
                          <span>耗时: {summary.latencyMs} ms</span>
                          <span>
                            生成时间: {dayjs(summary.summarizedAt).format('YYYY-MM-DD HH:mm')}
                          </span>
                        </Space>
                      </div>
                    }
                  />
                )}

                {/* 消息分页 */}
                {msgTotal > msgSize && (
                  <div style={{ textAlign: 'center', marginTop: 12 }}>
                    <Pagination
                      size="small"
                      current={msgPage + 1}
                      pageSize={msgSize}
                      total={msgTotal}
                      showSizeChanger
                      onChange={(p, s) => {
                        setMsgPage(p - 1);
                        setMsgSize(s);
                      }}
                    />
                  </div>
                )}
              </div>

              {/* 消息发送区 */}
              <div style={{ display: 'flex', gap: 8, marginTop: 12, marginBottom: 20, alignItems: 'flex-end' }}>
                <Popover
                  open={templatePopoverOpen}
                  onOpenChange={(open) => {
                    setTemplatePopoverOpen(open);
                    if (open && templates.length === 0) loadTemplates();
                  }}
                  trigger="click"
                  placement="topLeft"
                  title="快捷回复"
                  content={
                    <div style={{ width: 360 }}>
                      {/* 模板搜索框 (按名称/内容过滤) */}
                      <Input
                        placeholder="搜索模板名称或内容"
                        allowClear
                        size="small"
                        value={templateSearch}
                        onChange={(e) => setTemplateSearch(e.target.value)}
                        style={{ marginBottom: 8 }}
                      />
                      {/* 模板列表 (可滚动, 最多展示 300px 高度) */}
                      <div style={{ maxHeight: 300, overflow: 'auto' }}>
                        {templatesLoading ? (
                          <div style={{ textAlign: 'center', padding: 24 }}>
                            <Spin size="small" />
                          </div>
                        ) : filteredTemplates.length === 0 ? (
                          <Empty
                            image={Empty.PRESENTED_IMAGE_SIMPLE}
                            description={templateSearch ? '没有匹配的模板' : '暂无启用的模板'}
                          />
                        ) : (
                          <List
                            size="small"
                            dataSource={filteredTemplates}
                            renderItem={(tpl) => (
                              <List.Item
                                style={{ cursor: 'pointer', padding: '8px 0' }}
                                onClick={() => handleSelectTemplate(tpl)}
                              >
                                <List.Item.Meta
                                  title={
                                    <Space size={4}>
                                      <Text strong style={{ fontSize: 13 }}>{tpl.templateName}</Text>
                                      {tpl.category && (
                                        <Tag style={{ fontSize: 11 }}>{tpl.category}</Tag>
                                      )}
                                    </Space>
                                  }
                                  description={
                                    <Typography.Text
                                      ellipsis
                                      style={{ fontSize: 12, maxWidth: 300 }}
                                    >
                                      {tpl.content}
                                    </Typography.Text>
                                  }
                                />
                              </List.Item>
                            )}
                          />
                        )}
                      </div>
                    </div>
                  }
                >
                  <Button
                    icon={<SnippetsOutlined />}
                    title="快捷回复"
                  />
                </Popover>
                <div style={{ flex: 1, position: 'relative' }}>
                  <Input.TextArea
                    placeholder="输入消息 (Enter 发送, Shift+Enter 换行)"
                    value={messageInput}
                    onChange={(e) => setMessageInput(e.target.value)}
                    autoSize={{ minRows: 1, maxRows: 4 }}
                    onKeyDown={(e) => {
                      // IME 组合态 (中文输入候选词确认) 时跳过提交, 让浏览器处理候选词选择
                      if (isImeComposing(e)) return;
                      if (e.key === 'Enter' && !e.shiftKey) {
                        e.preventDefault();
                        handleSendMessage();
                      }
                    }}
                  />
                  {/* 字符计数 (超过 450 字变橙色, 超过 500 字变红色) */}
                  {messageInput.length > 0 && (
                    <span
                      style={{
                        position: 'absolute',
                        right: 8,
                        bottom: -18,
                        fontSize: 11,
                        // 字符计数: 超限使用语义色 (红/橙), 正常使用三级文本颜色适配暗色模式
                        color: messageInput.length > 500 ? '#ff4d4f'
                          : messageInput.length > 450 ? '#faad14'
                          : 'var(--color-text-tertiary)',
                        pointerEvents: 'none',
                      }}
                    >
                      {messageInput.length}/500
                    </span>
                  )}
                </div>
                <Button
                  type="primary"
                  icon={<SendOutlined />}
                  loading={sending}
                  onClick={handleSendMessage}
                >
                  发送
                </Button>
                {selected.platformType === 'wework' && (
                  <Tag color="blue" style={{ alignSelf: 'center' }}>企微API直发</Tag>
                )}
              </div>
            </>
          )}
        </Card>
      </Col>
    </Row>
            </>
          ),
        },
      ]}
    />
  );
}
