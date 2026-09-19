/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : WebSocketAuthInterceptor.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手认证拦截器
 * <p>
 * 在 WebSocket 握手阶段从 HTTP 请求中解析 userId, 写入 session attributes,
 * 供后续 {@link ScrmNotificationWebSocketHandler#sendMessageToUser} 进行定向推送。
 * </p>
 *
 * <p><b>鉴权策略 (fail-closed, 与 {@code DataScopeService} 一致):</b></p>
 * <ol>
 *   <li>优先读取网关 (gateway-server) 注入的 {@code X-User-Id} 请求头。
 *       gateway-server 在转发前已校验 JWT 并注入该头, 对下游服务权威可信,
 *       scrm-server 无需自行验签 (与 {@code DataScopeService#getCurrentUserId} 同源)。</li>
 *   <li>{@code X-User-Id} 缺失时视为匿名连接, 直接拒绝握手 (beforeHandshake 返回 false)。
 *       scrm-server 无公开的 WebSocket 健康检查端点, 所有 WS 连接均需网关认证。</li>
 * </ol>
 *
 * <p>鉴权由 gateway-server 统一承担, 本拦截器不做 token 真实性校验,
 * 也不再将 token 字符串本身作为 userId (历史占位实现已移除)。</p>
 *
 * @author Hsi Chu
 */
@Slf4j
@Component
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    /** 网关注入的用户 ID 请求头 (与 DataScopeService.HEADER_USER_ID 一致) */
    private static final String HEADER_USER_ID = "X-User-Id";

    /** URL 参数键: token (仅用于诊断日志, 不作为 userId) */
    private static final String PARAM_TOKEN = "token";

    /** 请求头键: Authorization */
    private static final String HEADER_AUTHORIZATION = "Authorization";

    /** Bearer token 前缀 */
    private static final String BEARER_PREFIX = "Bearer";

    /**
     * 握手前: 解析 userId 并写入 attributes。
     * <p>有 X-User-Id 头 → 已认证 (返回 true); 无 → 拒绝匿名连接 (返回 false, fail-closed)。</p>
     *
     * @param request    HTTP 握手请求
     * @param response   HTTP 握手响应
     * @param wsHandler  WebSocket 处理器
     * @param attributes session attributes (将传递到 WebSocketSession.getAttributes())
     * @return true 允许握手 (已认证); false 拒绝握手 (匿名)
     * @throws Exception 处理异常
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) throws Exception {
        // 1. 优先读取网关注入的 X-User-Id 头 (gateway 解析 JWT 后透传, 权威可信)
        String userId = readHeader(request, HEADER_USER_ID);
        if (userId != null && !userId.isBlank()) {
            attributes.put(ScrmNotificationWebSocketHandler.ATTR_ANONYMOUS, Boolean.FALSE);
            attributes.put(ScrmNotificationWebSocketHandler.ATTR_USER_ID, userId.trim());
            log.debug("WebSocket 网关认证握手: userId={}, uri={}", userId.trim(), request.getURI());
            return true;
        }
        // 2. X-User-Id 缺失: 网关未注入 (直连 / 未鉴权), fail-closed 拒绝匿名连接
        String token = extractToken(request);
        boolean hasToken = token != null && !token.isBlank();
        log.warn("WebSocket 匿名握手被拒绝 (缺少网关认证 X-User-Id 头): uri={}, 携带Token={}",
                request.getURI(), hasToken);
        return false;
    }

    /**
     * 握手后: 空实现 (无需清理)。
     *
     * @param request       HTTP 握手请求
     * @param response      HTTP 握手响应
     * @param wsHandler     WebSocket 处理器
     * @param exception     握手异常 (无异常为 null)
     */
    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // 空实现: 握手后处理由 WebSocketHandler#afterConnectionEstablished 接管
    }

    /**
     * 读取 HTTP 请求头 (兼容 ServletServerHttpRequest 与裸 ServerHttpRequest)。
     *
     * @param request    HTTP 请求
     * @param headerName 头名称
     * @return 头值, 不存在返回 null
     */
    private String readHeader(ServerHttpRequest request, String headerName) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            return servletRequest.getServletRequest().getHeader(headerName);
        }
        return request.getHeaders().getFirst(headerName);
    }

    /**
     * 从 HTTP 请求中提取 token (仅用于诊断日志, 不作为 userId)。
     * <p>优先级: URL 参数 token > Authorization 请求头 (Bearer)。</p>
     *
     * @param request HTTP 请求
     * @return token 字符串, 不存在返回 null
     */
    private String extractToken(ServerHttpRequest request) {
        // 1. URL 参数 token
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String tokenParam = servletRequest.getServletRequest().getParameter(PARAM_TOKEN);
            if (tokenParam != null && !tokenParam.isBlank()) {
                return tokenParam.trim();
            }
        }
        // 2. Authorization 请求头
        String authHeader = request.getHeaders().getFirst(HEADER_AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length()).trim();
            if (!token.isBlank()) {
                return token;
            }
        }
        return null;
    }
}
