/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmNotificationWebSocketHandler.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * SCRM 通知 WebSocket 处理器
 * <p>
 * 维护当前所有在线的 WebSocket 连接, 提供:
 * <ul>
 *   <li>广播 {@link #sendMessageToAll(String)} - 推送给全部在线客户端</li>
 *   <li>定向推送 {@link #sendMessageToUser(String, String)} - 按 session attributes 中的 userId 推送</li>
 *   <li>对象通知 {@link #sendNotification(ScrmNotification)} - 将 DTO 序列化为 JSON 后广播</li>
 * </ul>
 * </p>
 * <p>
 * Session 的 attributes 中由 {@link WebSocketAuthInterceptor} 注入 {@code userId} 字段,
 * 缺失时视为匿名 (anonymous) 连接, 仅能接收广播。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScrmNotificationWebSocketHandler extends TextWebSocketHandler {

    /** Session attributes 中存放当前用户 ID 的键名 */
    public static final String ATTR_USER_ID = "userId";

    /** Session attributes 中标记是否为匿名连接的键名 */
    public static final String ATTR_ANONYMOUS = "anonymous";

    /** 在线会话表: sessionId → WebSocketSession, 使用 ConcurrentHashMap 保证并发安全 */
    private final ConcurrentMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /** Jackson ObjectMapper, 由 Spring Boot 自动注入 (Jackson 自动配置) */
    private final ObjectMapper objectMapper;

    /**
     * 连接建立后回调: 将 session 加入会话表。
     *
     * @param session 新建立的 WebSocket 会话
     * @throws Exception 处理异常
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        Object userId = session.getAttributes().get(ATTR_USER_ID);
        boolean anonymous = Boolean.TRUE.equals(session.getAttributes().get(ATTR_ANONYMOUS));
        log.info("WebSocket 连接建立: sessionId={}, userId={}, anonymous={}, 在线总数={}",
                session.getId(), userId, anonymous, sessions.size());
        super.afterConnectionEstablished(session);
    }

    /**
     * 连接关闭后回调: 从会话表移除。
     *
     * @param session 关闭的 WebSocket 会话
     * @param status  关闭状态
     * @throws Exception 处理异常
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
        log.info("WebSocket 连接关闭: sessionId={}, status={}, 剩余在线={}",
                session.getId(), status, sessions.size());
        super.afterConnectionClosed(session, status);
    }

    /**
     * 处理客户端发送的文本消息。
     * <p>当前实现仅记录日志, 后续可扩展为订阅特定事件类型 (如 {"action":"subscribe", "type":"TASK_STATUS"})。</p>
     *
     * @param session 客户端会话
     * @param message 文本消息
     * @throws Exception 处理异常
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("收到 WebSocket 客户端消息: sessionId={}, payload={}", session.getId(), payload);
        // 预留扩展点: 解析 payload 实现按事件类型订阅 / 取消订阅
    }

    /**
     * 广播消息给所有在线客户端。
     * <p>对单个 session 发送失败不影响其他 session, 仅记录警告日志。</p>
     *
     * @param message 文本消息内容 (通常为 JSON 字符串)
     */
    public void sendMessageToAll(String message) {
        if (sessions.isEmpty()) {
            return;
        }
        TextMessage textMessage = new TextMessage(message);
        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            String sessionId = entry.getKey();
            WebSocketSession session = entry.getValue();
            if (!session.isOpen()) {
                sessions.remove(sessionId);
                continue;
            }
            try {
                synchronized (session) {
                    // WebSocketSession.sendMessage 非线程安全, 同一 session 串行发送
                    session.sendMessage(textMessage);
                }
            } catch (IOException e) {
                log.warn("广播消息失败, 移除会话: sessionId={}, err={}", sessionId, e.getMessage());
                sessions.remove(sessionId);
            }
        }
    }

    /**
     * 发送消息给指定用户 (依据 session attributes 中的 userId)。
     * <p>遍历所有会话, 匹配 userId 后推送。匿名会话不接收定向消息。</p>
     *
     * @param userId  目标用户 ID
     * @param message 文本消息内容 (通常为 JSON 字符串)
     */
    public void sendMessageToUser(String userId, String message) {
        if (userId == null || sessions.isEmpty()) {
            return;
        }
        TextMessage textMessage = new TextMessage(message);
        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            WebSocketSession session = entry.getValue();
            Object sessionUserId = session.getAttributes().get(ATTR_USER_ID);
            if (sessionUserId == null || !userId.equals(String.valueOf(sessionUserId))) {
                continue;
            }
            if (!session.isOpen()) {
                sessions.remove(entry.getKey());
                continue;
            }
            try {
                synchronized (session) {
                    session.sendMessage(textMessage);
                }
                log.debug("定向消息已发送: userId={}, sessionId={}", userId, session.getId());
            } catch (IOException e) {
                log.warn("定向消息发送失败: userId={}, sessionId={}, err={}",
                        userId, session.getId(), e.getMessage());
                sessions.remove(entry.getKey());
            }
        }
    }

    /**
     * 将 {@link ScrmNotification} 序列化为 JSON 后广播给所有在线客户端。
     * <p>序列化失败仅记录错误日志, 不抛出异常以避免阻塞调用方。</p>
     *
     * @param notification 通知对象
     */
    public void sendNotification(ScrmNotification notification) {
        if (notification == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(notification);
            sendMessageToAll(json);
        } catch (JsonProcessingException e) {
            log.error("ScrmNotification 序列化失败: type={}, title={}",
                    notification.getType(), notification.getTitle(), e);
        }
    }

    /**
     * 获取当前在线连接数 (供 REST 状态查询使用)。
     *
     * @return 在线会话数
     */
    public int getOnlineCount() {
        return sessions.size();
    }
}
