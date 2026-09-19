// Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
import { useEffect, useRef, useCallback, useState } from 'react';

/** WebSocket 通知类型 */
export interface ScrmWsNotification {
  type: string;
  title?: string;
  content?: string;
  level?: string;
  data?: Record<string, unknown>;
  timestamp?: string;
}

interface UseScrmWebSocketOptions {
  /** WebSocket 端点 URL，默认直连 scrm-server */
  url?: string;
  /** JWT token，用于握手认证 */
  token: string | null;
  /** 收到通知时的回调 */
  onNotification?: (notification: ScrmWsNotification) => void;
  /** 连接状态变更回调 */
  onConnectionChange?: (connected: boolean) => void;
  /** 自动重连间隔(ms)，默认 5000 */
  reconnectInterval?: number;
}

/**
 * SCRM WebSocket hook
 * - 自动连接 /ws/scrm/notifications
 * - token 变化时重连
 * - 断线自动重连（指数退避）
 * - 返回最新通知和连接状态
 *
 * 使用 ref 持有 onNotification / onConnectionChange 回调，
 * 避免 useCallback 依赖回调引用导致反复重连。
 */
export function useScrmWebSocket(options: UseScrmWebSocketOptions) {
  const {
    url,
    token,
    onNotification,
    onConnectionChange,
    reconnectInterval = 5000,
  } = options;

  const wsRef = useRef<WebSocket | null>(null);
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [connected, setConnected] = useState(false);
  const [lastNotification, setLastNotification] = useState<ScrmWsNotification | null>(null);

  // 用 ref 持有回调，避免作为 useCallback 依赖
  const onNotificationRef = useRef(onNotification);
  onNotificationRef.current = onNotification;
  const onConnectionChangeRef = useRef(onConnectionChange);
  onConnectionChangeRef.current = onConnectionChange;
  const reconnectIntervalRef = useRef(reconnectInterval);
  reconnectIntervalRef.current = reconnectInterval;

  // 标记是否已主动关闭（组件卸载或 token 变化时），防止关闭后触发自动重连
  const intentionalCloseRef = useRef(false);
  // 心跳定时器
  const heartbeatTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  // 重连次数 (用于指数退避)
  const reconnectAttemptsRef = useRef(0);

  const connect = useCallback(() => {
    // 清除重连定时器
    if (reconnectTimerRef.current) {
      clearTimeout(reconnectTimerRef.current);
      reconnectTimerRef.current = null;
    }
    // 清除心跳定时器
    if (heartbeatTimerRef.current) {
      clearInterval(heartbeatTimerRef.current);
      heartbeatTimerRef.current = null;
    }

    // 关闭已有连接
    if (wsRef.current) {
      wsRef.current.close();
      wsRef.current = null;
    }

    if (!token) {
      setConnected(false);
      return;
    }

    intentionalCloseRef.current = false;

    // 构建 WebSocket URL: 优先使用环境变量指定的主机; 否则使用当前页面同源地址
    const scheme = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsPath = '/ws/scrm/notifications';
    const wsBase = import.meta.env.VITE_SCRM_WS_HOST
      ? `${scheme}//${import.meta.env.VITE_SCRM_WS_HOST}${wsPath}`
      : `${scheme}//${window.location.host}${wsPath}`;
    const wsUrl = url || `${wsBase}?token=${encodeURIComponent(token)}`;

    const ws = new WebSocket(wsUrl);

    ws.onopen = () => {
      setConnected(true);
      reconnectAttemptsRef.current = 0;
      onConnectionChangeRef.current?.(true);
      // 启动心跳: 每 30 秒发送 ping, 保持连接活跃
      heartbeatTimerRef.current = setInterval(() => {
        if (wsRef.current?.readyState === WebSocket.OPEN) {
          wsRef.current.send(JSON.stringify({ type: 'ping' }));
        }
      }, 30000);
    };

    ws.onclose = () => {
      setConnected(false);
      onConnectionChangeRef.current?.(false);
      // 清除心跳
      if (heartbeatTimerRef.current) {
        clearInterval(heartbeatTimerRef.current);
        heartbeatTimerRef.current = null;
      }

      // 仅在非主动关闭时自动重连 (指数退避: 5s, 10s, 20s, 40s, 最大 60s)
      if (!intentionalCloseRef.current) {
        const attempt = reconnectAttemptsRef.current++;
        const delay = Math.min(
          reconnectIntervalRef.current * Math.pow(2, attempt),
          60000,
        );
        reconnectTimerRef.current = setTimeout(() => {
          connect();
        }, delay);
      }
    };

    ws.onerror = () => {
      // onclose 会紧随触发
    };

    ws.onmessage = (event) => {
      try {
        const notification: ScrmWsNotification = JSON.parse(event.data);
        // 忽略 pong 心跳响应
        if (notification.type === 'pong') return;
        setLastNotification(notification);
        onNotificationRef.current?.(notification);
      } catch {
        // 非 JSON 消息忽略
      }
    };

    wsRef.current = ws;
  }, [url, token]); // 仅依赖 url 和 token，回调通过 ref 访问

  // token/url 变化时重连
  useEffect(() => {
    connect();
    return () => {
      intentionalCloseRef.current = true;
      if (reconnectTimerRef.current) {
        clearTimeout(reconnectTimerRef.current);
        reconnectTimerRef.current = null;
      }
      if (wsRef.current) {
        wsRef.current.close();
        wsRef.current = null;
      }
    };
  }, [connect]);

  return { connected, lastNotification };
}
