/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : AgentHubController.java
 * Date : 2026/10/08 11:35:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.common.OperationResponse;
import org.hiylo.scrm.websocket.AgentHubWebSocketHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 远程 Agent 设备管理端点。
 * <p>
 * 桌面执行端（wx-console）在出站 WSS 连接前需要知道连接地址与令牌。
 * 本控制器提供：
 * <ul>
 *   <li>{@code POST /api/v1/devices/register} - 签发设备令牌（与
 *       {@code SCRM_CALLBACK_AGENT_SECRET} 一致，即 AgentHub 握手令牌；该端点
 *       幂等，返回同一个令牌）。</li>
 *   <li>{@code GET /api/v1/devices/online} - 当前 AgentHub 在线设备。</li>
 * </ul>
 * </p>
 * <p>
 * 说明：当前阶段 AgentHub 握手令牌直接复用回调共享密钥（agent-secret），
 * 因此 register 端点只是让 180 侧能通过 API 拿到/确认令牌，无需维护独立的
 * 设备注册表。若后续要「每设备独立令牌 + 可轮换」，在此增加 agent_device 表即可。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class AgentHubController {

    /** AgentHub WebSocket 处理器（查询在线设备） */
    private final AgentHubWebSocketHandler agentHubWebSocketHandler;

    /**
     * 注册/确认设备令牌。
     * <p>
     * 返回 AgentHub 握手所需令牌（= SCRM_CALLBACK_AGENT_SECRET）与 WebSocket 连接地址。
     * 幂等：任何合法 device_id 都返回同一令牌（密钥由后端统一配置，不随设备变化）。
     * </p>
     *
     * @param deviceId 桌面端设备 ID（auto-gen UUID）
     * @return 注册结果（agentToken / serverUrl / heartbeat）
     */
    @PostMapping("/register")
    public OperationResponse<Map<String, Object>> register(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestParam(value = "device_id", required = false) String deviceIdParam) {
        String deviceId = deviceIdParam;
        if (body != null && body.get("device_id") != null) {
            deviceId = String.valueOf(body.get("device_id"));
        }
        if (deviceId == null || deviceId.isBlank()) {
            return OperationResponse.fail("400", "device_id 不能为空");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        // AgentHub 握手令牌由 SCRM_CALLBACK_AGENT_SECRET 统一承担（fail-closed）。
        // 此处不暴露密钥本身 —— 180 侧的 agent_token 需要由运维注入同一密钥。
        data.put("device_id", deviceId);
        data.put("agent_token_configured", true);
        data.put("server_url", "/agent");
        data.put("heartbeat", 30);
        log.info("AgentHub 设备注册确认: deviceId={}", deviceId);
        return OperationResponse.build(data);
    }

    /**
     * 查询当前 AgentHub 在线设备。
     *
     * @return deviceId → 微信账号 映射
     */
    @GetMapping("/online")
    public OperationResponse<Map<String, String>> onlineDevices() {
        return OperationResponse.build(agentHubWebSocketHandler.onlineDevices());
    }
}
