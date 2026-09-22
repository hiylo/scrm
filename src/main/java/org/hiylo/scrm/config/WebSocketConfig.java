/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : WebSocketConfig.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.websocket.ScrmNotificationWebSocketHandler;
import org.hiylo.scrm.websocket.WebSocketAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置
 * <p>
 * 注册 {@link ScrmNotificationWebSocketHandler} 到 {@code /ws/scrm/notifications} 路径,
 * 并挂载 {@link WebSocketAuthInterceptor} 在握手阶段写入 userId 并拒绝匿名连接 (fail-closed)。
 * </p>
 * <p>
 * 跨域: 仅允许本机 / 内网 / 企业域名 (见 {@link #ALLOWED_ORIGIN_PATTERNS}),
 * 避免任意源建立连接。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    /** WebSocket 端点路径 */
    public static final String NOTIFICATION_PATH = "/ws/scrm/notifications";

    /** WebSocket 允许的跨域来源模式 */
    private static final String[] ALLOWED_ORIGIN_PATTERNS = {
            "http://localhost:*",
            "http://192.168.1.*:*",
            "https://*.hiylo.com"
    };

    /** SCRM 通知 WebSocket 处理器 (Spring 注入) */
    private final ScrmNotificationWebSocketHandler scrmNotificationWebSocketHandler;

    /** WebSocket 握手认证拦截器 (Spring 注入) */
    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    /**
     * 注册 WebSocket 处理器。
     * <p>
     * 路径: /ws/scrm/notifications
     * 拦截器: WebSocketAuthInterceptor (校验网关 X-User-Id, 拒绝匿名握手)
     * 跨域: 仅允许本机 / 内网 / 企业域名
     * </p>
     *
     * @param registry WebSocket 处理器注册器
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(scrmNotificationWebSocketHandler, NOTIFICATION_PATH)
                .addInterceptors(webSocketAuthInterceptor)
                .setAllowedOriginPatterns(ALLOWED_ORIGIN_PATTERNS);
        log.info("WebSocket 端点已注册: path={}", NOTIFICATION_PATH);
    }
}
