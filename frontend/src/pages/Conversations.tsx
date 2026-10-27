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
  const [size, setSize] = useState(20);
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
  /** 每次加载的消息条数（滚动到底加载更早，不提供切换入口） */
  const msgSize = 50;
  const [msgTotal, setMsgTotal] = useState(0);
  /**
   * 是否正在加载「更早的消息」。
   * <p>
   * 必须与 messagesLoading 分开：messagesLoading 为 true 时列表会被整体替换成
   * 全屏 spinner（首次进入会话用），若追加加载也走这条路，已有消息会被卸载，
   * scrollHeight 骤降、scrollTop 被浏览器钳到 0，锚点补偿随之失效——
   * 表现就是「加载更早之后画面突然跳到别处」。
   * </p>
   */
  const [loadingEarlier, setLoadingEarlier] = useState(false);
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
  // 「加载更早消息」的 ref 桥（handleScroll 定义在前，避免声明顺序互相依赖）
  const loadEarlierRef = useRef<(() => void) | null>(null);
  // 前插更早消息时记录锚点（可见首条消息 id + 其相对容器顶部的偏移），
  // 渲染后按该元素的位移反向补偿 scrollTop —— 比算 scrollHeight 差值可靠，
  // 因为差值补偿会被「前插内容高度 + 布局抖动」的累积误差带偏。
  const prependingRef = useRef(false);
  const preAnchorRef = useRef<{ id: string; top: number } | null>(null);
  // 上一次选中的会话，用于区分「切换会话」与「新消息」
  const prevSelectedIdRef = useRef<string | null>(null);
  const prevMsgCountRef = useRef(0);

  /**
   * 滚动处理：记录是否在底部附近，并在滚到顶部时自动加载更早的消息。
   * <p>
   * 聊天界面的习惯是「往上滚看历史」，所以靠近顶部就预取下一页，
   * 而不是让用户去点分页器（分页器还放在最新消息那一端，方向是反的）。
   * </p>
   */
  const handleScroll = useCallback(() => {
    const el = messagesContainerRef.current;
    if (!el) return;
    const distFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    isNearBottomRef.current = distFromBottom < 80;
    // 距顶部 60px 内触发加载更早的消息（一次性，受 loading 与 total 双重约束）
    if (el.scrollTop < 60) {
      loadEarlierRef.current?.();
    }
  }, []);

  /** 拉取会话分页列表（微信式滚动加载） */
  const fetchConversations = useCallback(
    async (pageArg?: number, append = false) => {
      setConversationsLoading(true);
      try {
        const targetPage = pageArg ?? page;
        const params = new URLSearchParams();
        params.set('page', String(targetPage));
        params.set('size', String(size));
        if (debouncedKeyword) params.set('keyword', debouncedKeyword);
        if (statusFilter && statusFilter !== 'ALL') params.set('status', statusFilter);
        const data = await apiClient.get<Page<ScrmConversation>>(
          `/scrm/conversations?${params.toString()}`,
        );
        const rows = data.content || [];
        if (append) {
          // 微信式滚动加载：下一页累加到现有列表之后，按 id 去重防止重复
          setConversations((prev) => {
            const seen = new Set(prev.map((c) => c.id));
            return [...prev, ...rows.filter((c) => !seen.has(c.id))];
          });
        } else {
          setConversations(rows);
        }
        setTotal(data.totalElements || 0);
      } catch {
        // 错误已由 axios 拦截器统一提示
        setConversations([]);
        setTotal(0);
      } finally {
        setConversationsLoading(false);
      }
    },
    [page, size, debouncedKeyword, statusFilter],
  );

  /** 已加载到的会话页码（供"加载更多"累加，避开 effect 对 page state 的重置） */
  const loadedConversationPageRef = useRef(0);

  /** 加载更多：拉取下一页并累加到现有列表 */
  const handleLoadMore = useCallback(() => {
    const next = loadedConversationPageRef.current + 1;
    loadedConversationPageRef.current = next;
    fetchConversations(next, true);
  }, [fetchConversations]);

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
    loadedConversationPageRef.current = 0;
  }, [debouncedKeyword]);

  // 状态筛选变化时, 重置到第一页
  useEffect(() => {
    setPage(0);
    loadedConversationPageRef.current = 0;
  }, [statusFilter]);

  /**
   * 拉取指定会话的消息记录。
   * <p>
   * 后端按 {@code sentAt DESC} 分页（第 0 页是最新的 N 条）。聊天界面需要
   * 时间正序，且翻页语义是「往上滚看更早」而不是「换一页替换当前内容」——
   * 原实现整页替换会导致加载第 2 页后最新消息从视图里消失。
   * 这里改成累加：加载更早的消息时把结果并入现有列表，由 sortedMessages 统一排序。
   * </p>
   *
   * @param pageArg 目标页（默认当前 msgPage）
   * @param append  true 表示加载更早的消息（累加），false 表示重置加载（仅第 0 页）
   */
  const fetchMessages = useCallback(async (pageArg?: number, append = false) => {
    if (!selectedId) {
      setMessages([]);
      setMsgTotal(0);
      return;
    }
    const targetPage = pageArg ?? msgPage;
    // 追加加载不进入全屏 loading，否则已有消息会被卸载、滚动位置丢失
    if (append) {
      setLoadingEarlier(true);
    } else {
      setMessagesLoading(true);
    }
    try {
      const data = await apiClient.get<Page<ScrmConversationMessage>>(
        `/scrm/conversations/${selectedId}/messages?page=${targetPage}&size=${msgSize}`,
      );
      const rows = data.content || [];
      if (append) {
        // 按 id 去重后并入，避免分页边界重复
        setMessages((prev) => {
          const seen = new Set(prev.map((m) => m.id));
          return [...rows.filter((m) => !seen.has(m.id)), ...prev];
        });
      } else {
        setMessages(rows);
      }
      setMsgTotal(data.totalElements || 0);
    } catch {
      // 错误已由 axios 拦截器统一提示
      if (append) {
        // 前插失败时清掉锚点标记，否则下一次消息变化会被误判成「前插」而跳过贴底
        prependingRef.current = false;
        preAnchorRef.current = null;
      } else {
        setMessages([]);
        setMsgTotal(0);
      }
    } finally {
      if (append) {
        setLoadingEarlier(false);
      } else {
        setMessagesLoading(false);
      }
    }
  }, [selectedId, msgPage, msgSize]);

  /** 加载更早的消息（滚到顶部触发） */
  const handleLoadEarlierMessages = useCallback(() => {
    const loaded = Math.ceil(messages.length / msgSize);
    if (messages.length >= msgTotal || messagesLoading) {
      return;
    }
    // 前插前采集锚点：当前可见的第一条消息及其相对容器顶部的偏移
    const el = messagesContainerRef.current;
    if (el) {
      const boxTop = el.getBoundingClientRect().top;
      const nodes = Array.from(el.querySelectorAll<HTMLElement>('[data-mid]'));
      const firstVisible = nodes.find((n) => n.getBoundingClientRect().bottom > boxTop);
      preAnchorRef.current = firstVisible
          ? { id: firstVisible.dataset.mid || '', top: firstVisible.getBoundingClientRect().top - boxTop }
          : null;
    } else {
      preAnchorRef.current = null;
    }
    prependingRef.current = true;
    const next = loaded;
    setMsgPage(next);
    fetchMessages(next, true);
  }, [messages.length, msgTotal, msgSize, messagesLoading, fetchMessages]);


  /**
   * 刷新会话列表，但<b>保留已加载的条数</b>。
   * <p>
   * 为什么不能用 fetchConversations() 直接刷新：它不带 append 时只会取第 0 页
   * （size=20）并整体替换列表。用户如果已经点过「加载更多」把列表拉到 60 条，
   * 此时收到一条 WebSocket 新消息就会把列表截回 20 条——内容变短，
   * 浏览器把 scrollTop 钳到新的最大值，表现就是「列表突然跳回顶部」。
   * 这里按「当前已加载条数」作为 size 重新拉一次，列表长度不变，滚动位置就不会丢。
   * </p>
   */
  const refreshConversations = useCallback(async () => {
    const alreadyLoaded = Math.max(size, conversations.length);
    setConversationsLoading(true);
    try {
      const params = new URLSearchParams();
      params.set('page', '0');
      params.set('size', String(alreadyLoaded));
      if (debouncedKeyword) params.set('keyword', debouncedKeyword);
      if (statusFilter && statusFilter !== 'ALL') params.set('status', statusFilter);
      const data = await apiClient.get<Page<ScrmConversation>>(
        `/scrm/conversations?${params.toString()}`,
      );
      setConversations(data.content || []);
      setTotal(data.totalElements || 0);
    } catch {
      // 错误已由 axios 拦截器统一提示
    } finally {
      setConversationsLoading(false);
    }
  }, [size, conversations.length, debouncedKeyword, statusFilter]);

  // 把最新的加载函数挂到 ref 上，供 handleScroll 调用
  useEffect(() => {
    loadEarlierRef.current = handleLoadEarlierMessages;
  }, [handleLoadEarlierMessages]);

  /**
   * fetchMessages 的最新引用。
   * <p>
   * 下面的「切换会话重置」effect 必须只用它调用、且依赖只写 selectedId——
   * 因为 fetchMessages 依赖 msgPage，而「加载更早消息」会改 msgPage；
   * 若把 fetchMessages 写进依赖，加载更早会触发这个 effect，
   * 立刻 setMsgPage(0) 并整页替换回最新 50 条，表现为「点了加载更早没反应」。
   * </p>
   */
  const fetchMessagesRef = useRef(fetchMessages);
  fetchMessagesRef.current = fetchMessages;

  useEffect(() => {
    // 选中会话变化时重置消息与总结, 然后加载消息
    if (selectedId) {
      setSummary(null);
      setMsgPage(0);
      // 清空媒体 URL 缓存 (避免上一会话的媒体 URL 残留)
      setMediaUrls({});
      setMediaLoading({});
      // 显式按第 0 页重置，不受上一次 msgPage 残留影响
      fetchMessagesRef.current(0, false);
    } else {
      setMessages([]);
      setMsgTotal(0);
      setSummary(null);
      setMediaUrls({});
      setMediaLoading({});
    }
    // 依赖刻意只写 selectedId：见上方 fetchMessagesRef 的说明
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedId]);

  /**
   * 消息列表变化时的滚动策略，分三种情形：
   * <ol>
   *   <li><b>加载更早消息（前插）</b>：保持当前可见内容不动——
   *       记录前插前的 scrollHeight，渲染后按增量补偿 scrollTop，
   *       否则画面会突然跳到更早的位置；</li>
   *   <li><b>切换会话</b>：直接贴底（进入会话就该看到最新消息）；</li>
   *   <li><b>新消息到达</b>：仅当用户本来就在底部附近才跟随滚动，
   *       避免用户正在翻历史时被强行拽到底部。</li>
   * </ol>
   */
  useEffect(() => {
    const el = messagesContainerRef.current;
    if (!el) return;
    prevMsgCountRef.current = messages.length;

    // 情形 1：前插更早消息，锁住可见位置
    if (prependingRef.current) {
      prependingRef.current = false;
      const anchor = preAnchorRef.current;
      preAnchorRef.current = null;
      if (anchor && anchor.id) {
        // 等两帧再补偿：useEffect 执行时同批 DOM 可能还没完成布局，
        // 此时读到的 rect 是旧值，直接补偿会留下固定偏差。
        const compensate = () => {
          const node = el.querySelector<HTMLElement>(`[data-mid="${anchor.id}"]`);
          if (!node) return;
          const boxTop = el.getBoundingClientRect().top;
          const nowTop = node.getBoundingClientRect().top - boxTop;
          const delta = nowTop - anchor.top;
          if (Math.abs(delta) >= 1) {
            el.scrollTop += delta;
          }
        };
        requestAnimationFrame(() => requestAnimationFrame(compensate));
      }
      return;
    }

    const switched = prevSelectedIdRef.current !== selectedId;
    prevSelectedIdRef.current = selectedId;

    if (switched) {
      el.scrollTop = el.scrollHeight;
      return;
    }
    if (isNearBottomRef.current) {
      el.scrollTop = el.scrollHeight;
    }
  }, [messages, selectedId]);

  // WebSocket 回调使用 ref 保存最新函数引用，避免依赖频繁变化导致重连
  const fetchConversationsRef = useRef(fetchConversations);
  fetchConversationsRef.current = fetchConversations;
  const refreshConversationsRef = useRef(refreshConversations);
  refreshConversationsRef.current = refreshConversations;

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
        // 用保留已加载条数的方式，避免列表被截断导致滚动位置跳回顶部
        refreshConversationsRef.current();
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
      // 保留已加载条数，避免列表被截断导致滚动位置跳回顶部
      void refreshConversations();
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
      void refreshConversations();
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
      className="conv-fill"
      defaultActiveKey="scrm"
      items={[
        {
          key: 'scrm',
          label: '会话列表',
          children: (
            <>
    <Row gutter={16} className="conv-row">
      {/* 左侧会话列表 */}
      <Col xs={24} md={8} className="conv-col">
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
                onClick={() => {
                  loadedConversationPageRef.current = 0;
                  fetchConversations(0);
                }}
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
          {/* 会话列表独立滚动：逐页加载不会再把整页撑长 */}
          <div className="conv-list-scroll">
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
                          <Badge
                            count={hasUnread ? item.unreadCount ?? 0 : 0}
                            size="small"
                            offset={[-2, 2]}
                          >
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
          </div>
          <div style={{ padding: 12, textAlign: 'center', flexShrink: 0 }}>
            {total > 0 && conversations.length >= total ? (
              <span style={{ color: 'var(--color-text-tertiary)', fontSize: 12 }}>
                已加载全部 {conversations.length} 个会话
              </span>
            ) : (
              <Button
                type="link"
                size="small"
                loading={conversationsLoading}
                onClick={handleLoadMore}
              >
                加载更多
              </Button>
            )}
          </div>
        </Card>
      </Col>

      {/* 右侧消息区域 */}
      <Col xs={24} md={16} className="conv-col">
        <Card
          size="small"
          title={
            selected ? (
              <Space size={8} style={{ minWidth: 0 }}>
                <Avatar src={selected.customerAvatarUrl} size="small">
                  {selected.customerNickname?.[0]?.toUpperCase()}
                </Avatar>
                <Tooltip
                  title={`账号 ${selected.accountName || selected.accountId} · ${selected.platformType}`}
                >
                  <span
                    style={{
                      maxWidth: 220,
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {selected.customerNickname || `客户 ${selected.customerId}`}
                  </span>
                </Tooltip>
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
                        void refreshConversations();
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
                        void refreshConversations();
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
                  style={{ marginBottom: 8, maxHeight: 240, overflowY: 'auto', flexShrink: 0 }}
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
                  // 不再用 calc(100vh - 360px) 估算：改为 flex 撑满剩余空间，
                  // 窗口高度变化、卡片头部增减都不会再错位
                  flex: 1,
                  minHeight: 0,
                  overflowY: 'auto',
                  overscrollBehavior: 'contain',
                  // 前插补偿是手动的，关掉浏览器原生 scroll anchoring 避免双重校正
                  overflowAnchor: 'none',
                  paddingRight: 4,
                }}
              >
                {messagesLoading && messages.length === 0 ? (
                  <div style={{ textAlign: 'center', padding: 48 }}>
                    <Spin />
                  </div>
                ) : messages.length === 0 ? (
                  <Empty description="暂无消息" />
                ) : (
                  <>
                  {/* 不在列表顶部插提示条：它会改变 scrollHeight，让锚点补偿多算一个提示条高度，
                      导致加载完成后画面整体位移。加载状态由底部按钮的 loading 表达。 */}
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
                              data-mid={msg.id}
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
                  </>
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

              </div>

              {/* 加载更早的入口：常驻一行占位（加载完用 visibility 隐藏而不是卸载）。
                  高度必须恒定：这一行若随「是否还有更早消息」出现/消失，
                  容器可用高度就变，浏览器会把 scrollTop 钳回去，
                  画面会整体跳动（实测固定 28px）。 */}
              <div
                style={{
                  textAlign: 'center',
                  flexShrink: 0,
                  paddingTop: 4,
                  minHeight: 28,
                  visibility: msgTotal > messages.length ? 'visible' : 'hidden',
                }}
              >
                <Button
                  type="link"
                  size="small"
                  loading={loadingEarlier}
                  disabled={msgTotal <= messages.length}
                  onClick={handleLoadEarlierMessages}
                >
                  {msgTotal > messages.length
                    ? `加载更早的 ${Math.min(msgSize, msgTotal - messages.length)} 条消息`
                    : '已加载全部消息'}
                </Button>
              </div>

              {/* 消息发送区 */}
              <div style={{ display: 'flex', gap: 8, marginTop: 8, marginBottom: 4, alignItems: 'flex-end', flexShrink: 0 }}>
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
