/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmNotificationController.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmNotificationService;
import org.hiylo.scrm.websocket.ScrmNotificationWebSocketHandler;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SCRM 通知 REST 控制器
 * <p>
 * 提供 WebSocket 服务状态查询与手动测试通知接口, 供运维 / 前端联调使用。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/notifications")
@RequiredArgsConstructor
public class ScrmNotificationController {

    /** SCRM 通知 WebSocket 处理器 (查询在线连接数) */
    private final ScrmNotificationWebSocketHandler webSocketHandler;

    /** SCRM 通知服务 (发送测试通知) */
    private final ScrmNotificationService notificationService;

    /**
     * 查询 WebSocket 服务状态 (当前在线连接数)。
     *
     * @return 包含 onlineCount 的状态对象
     */
    @RequirePermission(resource = "scrm_notification", action = "read")
    @GetMapping("/status")
    public OperationResponse<Map<String, Object>> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("onlineCount", webSocketHandler.getOnlineCount());
        status.put("endpoint", "/ws/scrm/notifications");
        return OperationResponse.build(status);
    }

    /**
     * 手动发送测试通知 (广播给所有在线客户端)。
     * <p>用于前端联调 WebSocket 连接是否正常, 默认级别 INFO, 类型 SYSTEM。</p>
     *
     * @param message 自定义消息内容 (可空, 默认 "测试通知")
     * @return 空响应 (通知已异步推送)
     */
    @RequirePermission(resource = "scrm_notification", action = "read")
    @PostMapping("/test")
    public OperationResponse<Void> test(@RequestParam(required = false) String message) {
        String content = (message == null || message.isBlank()) ? "测试通知" : message;
        notificationService.notifySystem("测试通知", content, "INFO");
        log.info("手动触发测试通知: content={}", content);
        return OperationResponse.build();
    }
}
