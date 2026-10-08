/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : AiAssistant.tsx
 * Date : 2026/10/08 12:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  App,
  Avatar,
  Button,
  Card,
  Col,
  Empty,
  Input,
  List,
  Row,
  Space,
  Spin,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import {
  SendOutlined,
  ReloadOutlined,
  RobotOutlined,
  UserOutlined,
  BulbOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

const { Text, Paragraph } = Typography;

/** 客户实体 (AI 对话客户选择) */
interface ScrmCustomer {
  id: string;
  nickname?: string;
  avatarUrl?: string;
  platformType?: string;
  lifecycle?: string;
}

/** Spring Page 分页响应 */
interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/** AI 对话记录 */
interface AiConversation {
  id: string;
  customerId: string;
  customerName?: string;
  userMessage: string;
  detectedIntent?: string;
  intentConfidence?: number;
  sentiment?: string;
  sentimentScore?: number;
  recommendedReplies?: string;
  aiResponse?: string;
  responseTimeMs?: number;
  createdAt?: string;
}

/** 情感 → 标签颜色 */
const sentimentTagConfig: Record<string, { color: string; label: string }> = {
  POSITIVE: { color: 'green', label: '积极' },
  NEUTRAL: { color: 'default', label: '中性' },
  NEGATIVE: { color: 'orange', label: '消极' },
  ANGRY: { color: 'red', label: '愤怒' },
  HAPPY: { color: 'cyan', label: '开心' },
};

/**
 * AI 助手对话页
 * <p>
 * 左侧选择客户, 右侧发起 AI 对话。对话请求走后端
 * {@code POST /scrm/ai-assistant/chat}, 后端完成意图识别/情感分析/真实 AI 回复
 * (AI 不可用时回退模拟回复), 返回后展示用户的输入与 AI 的回复, 并保留历史对话记录。
 * </p>
 *
 * @author Hsi Chu
 */
interface AiAssistantProps {
  /** 初始客户 ID (可从客户详情页跳转带参) */
  initialCustomerId?: string;
}

export default function AiAssistant({ initialCustomerId }: AiAssistantProps) {
  const { message } = App.useApp();
  /** 客户列表 */
  const [customers, setCustomers] = useState<ScrmCustomer[]>([]);
  const [customersLoading, setCustomersLoading] = useState(false);
  const [selectedCustomerId, setSelectedCustomerId] = useState<string>(initialCustomerId || '');
  /** 对话区 */
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const [currentReply, setCurrentReply] = useState<AiConversation | null>(null);
  /** 历史 */
  const [history, setHistory] = useState<AiConversation[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyPage, setHistoryPage] = useState(0);
  const historySize = 20;
  const [historyTotal, setHistoryTotal] = useState(0);
  /** 输入框 IME 组合态 */
  const imeComposingRef = useRef(false);

  /** 分页查询客户列表 */
  const loadCustomers = useCallback(async () => {
    setCustomersLoading(true);
    try {
      const params = new URLSearchParams({ page: '0', size: '50' });
      const data = await apiClient.get<Page<ScrmCustomer>>(`/scrm/customers?${params.toString()}`);
      setCustomers(data.content || []);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setCustomersLoading(false);
    }
  }, []);

  useEffect(() => {
    loadCustomers();
  }, [loadCustomers]);

  /** 加载 AI 对话历史 (按选中客户) */
  const loadHistory = useCallback(async (customerId: string, page: number) => {
    if (!customerId) {
      setHistory([]);
      return;
    }
    setHistoryLoading(true);
    try {
      const params = new URLSearchParams({
        customerId,
        page: String(page),
        size: String(historySize),
      });
      const data = await apiClient.get<Page<AiConversation>>(
        `/scrm/ai-assistant/conversations/list?${params.toString()}`,
      );
      if (page === 0) {
        setHistory(data.content || []);
      } else {
        setHistory(prev => [...prev, ...(data.content || [])]);
      }
      setHistoryTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setHistoryLoading(false);
    }
  }, []);

  useEffect(() => {
    if (selectedCustomerId) {
      setHistoryPage(0);
      loadHistory(selectedCustomerId, 0);
      setCurrentReply(null);
    } else {
      setHistory([]);
      setCurrentReply(null);
    }
  }, [selectedCustomerId, loadHistory]);

  /** 发送对话 */
  const handleSend = useCallback(async () => {
    const text = input.trim();
    if (!text || sending) return;
    if (!selectedCustomerId) {
      message.warning('请先选择客户');
      return;
    }
    setSending(true);
    try {
      const reply = await apiClient.post<AiConversation>('/scrm/ai-assistant/chat', {
        customerId: Number(selectedCustomerId),
        message: text,
      });
      setCurrentReply(reply);
      setInput('');
      setHistory(prev => [reply, ...prev]);
      setHistoryTotal(prev => prev + 1);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setSending(false);
    }
  }, [input, sending, selectedCustomerId, message]);

  /** 渲染意图标签 */
  const renderIntent = (item?: AiConversation | null) => {
    if (!item?.detectedIntent) return null;
    return <Tag color="blue">{item.detectedIntent}</Tag>;
  };

  /** 渲染情感标签 */
  const renderSentiment = (item?: AiConversation | null) => {
    if (!item?.sentiment) return null;
    const cfg = sentimentTagConfig[item.sentiment] || { color: 'default', label: item.sentiment };
    return <Tag color={cfg.color}>{cfg.label}</Tag>;
  };

  /** 解析推荐回复 JSON */
  const parseRecommended = (json?: string): string[] => {
    if (!json) return [];
    try {
      const arr = JSON.parse(json);
      return Array.isArray(arr) ? arr.map(String) : [];
    } catch {
      return [];
    }
  };

  const selectedCustomer = useMemo(
    () => customers.find(c => c.id === selectedCustomerId),
    [customers, selectedCustomerId],
  );

  return (
    <div className="ai-assistant-page">
      <Row gutter={16} style={{ height: '100%' }}>
        {/* 左侧: 客户列表 */}
        <Col xs={24} md={8} lg={6} style={{ height: '100%' }}>
          <Card
            title="选择客户"
            size="small"
            styles={{ body: { overflow: 'hidden', display: 'flex', flexDirection: 'column', padding: 0 } }}
            extra={
              <Tooltip title="刷新客户列表">
                <Button type="text" size="small" icon={<ReloadOutlined />} onClick={loadCustomers} />
              </Tooltip>
            }
          >
            {customersLoading ? (
              <div style={{ textAlign: 'center', padding: 24 }}>
                <Spin />
              </div>
            ) : (
              <div style={{ flex: 1, minHeight: 0, overflowY: 'auto' }}>
              <List
                dataSource={customers}
                locale={{ emptyText: <Empty description="暂无客户" /> }}
                renderItem={c => (
                  <List.Item
                    onClick={() => setSelectedCustomerId(c.id)}
                    style={{
                      cursor: 'pointer',
                      padding: '8px 12px',
                      borderRadius: 8,
                      background: c.id === selectedCustomerId ? 'var(--color-primary-lightest, #eef2ff)' : undefined,
                    }}
                  >
                    <List.Item.Meta
                      avatar={<Avatar src={c.avatarUrl} icon={<UserOutlined />} />}
                      title={c.nickname || `客户 ${c.id}`}
                      description={c.platformType ? `平台: ${c.platformType}` : undefined}
                    />
                  </List.Item>
                )}
              />
              </div>
            )}
          </Card>
        </Col>

        {/* 右侧: 对话窗 */}
        <Col xs={24} md={16} lg={18} style={{ height: '100%' }}>
          <Card
            title={
              <Space>
                <RobotOutlined style={{ color: 'var(--color-primary, #6366f1)' }} />
                <span>AI 智能对话助手</span>
                {selectedCustomer && (
                  <Tag color="geekblue">{selectedCustomer.nickname || `客户 ${selectedCustomer.id}`}</Tag>
                )}
              </Space>
            }
            size="small"
            styles={{ body: { overflow: 'hidden', display: 'flex', flexDirection: 'column', minHeight: 0 } }}
          >
            {/* 对话区 */}
            <div style={{ flex: 1, minHeight: 0, overflowY: 'auto', marginBottom: 16 }}>
              {!currentReply && history.length === 0 ? (
                <Empty
                  description="选择左侧客户, 输入消息开始 AI 对话"
                  style={{ padding: 48 }}
                />
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                  {/* 用户消息 */}
                  {currentReply && (
                    <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                      <div
                        style={{
                          background: 'var(--color-primary, #6366f1)',
                          color: '#fff',
                          padding: '8px 12px',
                          borderRadius: 12,
                          maxWidth: '70%',
                          whiteSpace: 'pre-wrap',
                        }}
                      >
                        {currentReply.userMessage}
                      </div>
                    </div>
                  )}
                  {/* AI 回复 */}
                  {currentReply && (
                    <div style={{ display: 'flex', justifyContent: 'flex-start', gap: 8 }}>
                      <Avatar icon={<RobotOutlined />} style={{ background: 'var(--color-primary, #6366f1)' }} />
                      <div style={{ maxWidth: '80%' }}>
                        <div
                          style={{
                            background: 'var(--color-bg-elevated, #f5f5f5)',
                            padding: '8px 12px',
                            borderRadius: 12,
                            whiteSpace: 'pre-wrap',
                          }}
                        >
                          {currentReply.aiResponse}
                        </div>
                        <Space size={4} style={{ marginTop: 4 }}>
                          {renderIntent(currentReply)}
                          {renderSentiment(currentReply)}
                          {currentReply.responseTimeMs != null && (
                            <Text type="secondary" style={{ fontSize: 12 }}>
                              耗时 {currentReply.responseTimeMs}ms
                            </Text>
                          )}
                        </Space>
                        {parseRecommended(currentReply.recommendedReplies).length > 0 && (
                          <div style={{ marginTop: 6 }}>
                            <Text type="secondary" style={{ fontSize: 12 }}>推荐回复:</Text>
                            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 4 }}>
                              {parseRecommended(currentReply.recommendedReplies).map((r, idx) => (
                                <Tag
                                  key={idx}
                                  icon={<BulbOutlined />}
                                  style={{ cursor: 'pointer' }}
                                  onClick={() => setInput(r)}
                                >
                                  {r}
                                </Tag>
                              ))}
                            </div>
                          </div>
                        )}
                      </div>
                    </div>
                  )}

                  {/* 历史记录 */}
                  {history.slice(currentReply ? 1 : 0).map(item => (
                    <div key={item.id}>
                      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                        <div
                          style={{
                            background: 'var(--color-bg-elevated, #f5f5f5)',
                            padding: '8px 12px',
                            borderRadius: 12,
                            maxWidth: '70%',
                            whiteSpace: 'pre-wrap',
                          }}
                        >
                          {item.userMessage}
                        </div>
                      </div>
                      <div style={{ display: 'flex', justifyContent: 'flex-start', gap: 8, marginTop: 8 }}>
                        <Avatar icon={<RobotOutlined />} style={{ background: '#8c8c8c' }} />
                        <div style={{ maxWidth: '80%' }}>
                          <div
                            style={{
                              background: 'var(--color-bg-elevated, #f5f5f5)',
                              padding: '8px 12px',
                              borderRadius: 12,
                              whiteSpace: 'pre-wrap',
                            }}
                          >
                            {item.aiResponse}
                          </div>
                          <Space size={4} style={{ marginTop: 4 }}>
                            {renderIntent(item)}
                            {renderSentiment(item)}
                            {item.createdAt && (
                              <Text type="secondary" style={{ fontSize: 12 }}>
                                {dayjs(item.createdAt).format('MM-DD HH:mm')}
                              </Text>
                            )}
                          </Space>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* 输入区 */}
            <div style={{ display: 'flex', gap: 8 }}>
              <Input.TextArea
                value={input}
                onChange={e => setInput(e.target.value)}
                placeholder="输入客户消息, 例如: 我想了解一下你们的产品报价"
                autoSize={{ minRows: 2, maxRows: 4 }}
                onPressEnter={e => {
                  if (!e.shiftKey) {
                    e.preventDefault();
                    handleSend();
                  }
                }}
                onCompositionStart={() => { imeComposingRef.current = true; }}
                onCompositionEnd={() => { imeComposingRef.current = false; }}
              />
              <Button
                type="primary"
                icon={<SendOutlined />}
                loading={sending}
                onClick={handleSend}
                style={{ alignSelf: 'stretch' }}
              >
                发送
              </Button>
            </div>

            {/* 历史分页 */}
            {history.length < historyTotal && (
              <div style={{ textAlign: 'center', marginTop: 12 }}>
                <Button
                  type="link"
                  loading={historyLoading}
                  onClick={() => {
                    const next = historyPage + 1;
                    setHistoryPage(next);
                    loadHistory(selectedCustomerId, next);
                  }}
                >
                  加载更早 ({history.length}/{historyTotal})
                </Button>
              </div>
            )}

            {/* 客户切换提示 */}
            {!selectedCustomerId && (
              <Paragraph type="secondary" style={{ marginTop: 8, fontSize: 12 }}>
                提示: 需选择客户后方可发起对话 (对话记录按客户归档)。
              </Paragraph>
            )}
          </Card>
        </Col>
      </Row>
    </div>
  );
}