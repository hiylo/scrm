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
import org.hiylo.scrm.websocket.AgentHubWebSocketHandler;
import org.hiylo.scrm.websocket.ScrmNotificationWebSocketHandler;
import org.hiylo.scrm.websocket.WebSocketAuthInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

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

    /** 远程 Agent 集线器端点路径（桌面执行端出站 WSS, 与 wx-console cloud.server_url 对齐） */
    public static final String AGENT_HUB_PATH = "/agent";

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

    /** 远程 Agent 集线器 WebSocket 处理器 (Spring 注入, 桌面执行端出站 WSS) */
    private final AgentHubWebSocketHandler agentHubWebSocketHandler;

    /**
     * WebSocket 容器参数。
     * <p>
     * AgentHub 会整包上报会话/消息快照（116 会话 + 若干消息约 60KB+），
     * Tomcat 默认文本帧缓冲 8192 字节不够 → 触发 code=1009
     * "text message was too big"，连接被服务端关闭。这里把文本缓冲提到 1MB，
     * 二进制缓冲也同步提升（图片类扩展预留）。
     * </p>
     *
     * @return 配置了缓冲区上限的容器工厂
     */
    @Bean
    @Profile("!test")
    public ServletServerContainerFactoryBean servletServerContainerFactoryBean() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(1024 * 1024);
        container.setMaxBinaryMessageBufferSize(1024 * 1024);
        return container;
    }

    /**
     * 注册 WebSocket 处理器。
     * <p>
     * 路径: /ws/scrm/notifications
     * 拦截器: WebSocketAuthInterceptor (校验网关 X-User-Id, 拒绝匿名握手)
     * 跨域: 仅允许本机 / 内网 / 企业域名
     * </p>
     * <p>
     * AgentHub 路径: /agent (桌面端出站 WSS, 协议见 REMOTE-AGENT §2/§3)。
     * 不挂 WebSocketAuthInterceptor —— AgentHub 的认证是首帧 hello 的
     * token 校验（与 X-Agent-Secret 同源），而非网关 X-User-Id。
     * </p>
     *
     * @param registry WebSocket 处理器注册器
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(scrmNotificationWebSocketHandler, NOTIFICATION_PATH)
                .addInterceptors(webSocketAuthInterceptor)
                .setAllowedOriginPatterns(ALLOWED_ORIGIN_PATTERNS);
        // 桌面执行端（wx-console）出站 WSS：wss://host:port/agent
        registry.addHandler(agentHubWebSocketHandler, AGENT_HUB_PATH)
                .setAllowedOriginPatterns(new String[]{"*"});
        log.info("WebSocket 端点已注册: path={}, agentHubPath={}", NOTIFICATION_PATH, AGENT_HUB_PATH);
    }
}
