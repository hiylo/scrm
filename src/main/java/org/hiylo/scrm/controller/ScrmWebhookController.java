/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWebhookController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmWebhookConfigDto;
import org.hiylo.scrm.dto.ScrmWebhookEventDto;
import org.hiylo.scrm.dto.ScrmWebhookLogDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmWebhookService;
import org.hiylo.scrm.vo.WebhookStatsVo;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM Webhook 事件通知控制器
 * <p>
 * 提供 Webhook 配置管理、事件发布、推送日志查询与统计接口。
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/webhooks")
@RequiredArgsConstructor
public class ScrmWebhookController {

    /** Webhook 服务 */
    private final ScrmWebhookService webhookService;

    /**
     * 创建 Webhook 配置
     *
     * @param dto Webhook 配置参数
     * @return 创建后的 Webhook 配置
     * @throws ScrmException 参数校验失败
     */
    @RequirePermission(resource = "scrm_webhook", action = "create")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60, message = "创建 Webhook 配置过于频繁，请稍后重试")
    @PostMapping("/configs")
    public OperationResponse<ScrmWebhookConfigDto> createConfig(@Valid @RequestBody ScrmWebhookConfigDto dto)
            throws ScrmException {
        return OperationResponse.build(webhookService.createConfig(dto));
    }

    /**
     * 更新 Webhook 配置
     *
     * @param id  Webhook 配置 ID
     * @param dto Webhook 配置参数
     * @return 更新后的 Webhook 配置
     * @throws ScrmException Webhook 配置不存在
     */
    @RequirePermission(resource = "scrm_webhook", action = "update")
    @PutMapping("/configs/{id}")
    public OperationResponse<ScrmWebhookConfigDto> updateConfig(@PathVariable Long id,
                                                                @RequestBody ScrmWebhookConfigDto dto)
            throws ScrmException {
        return OperationResponse.build(webhookService.updateConfig(id, dto));
    }

    /**
     * 删除 Webhook 配置
     *
     * @param id Webhook 配置 ID
     * @return 空响应
     * @throws ScrmException Webhook 配置不存在
     */
    @RequirePermission(resource = "scrm_webhook", action = "delete")
    @DeleteMapping("/configs/{id}")
    public OperationResponse<Void> deleteConfig(@PathVariable Long id) throws ScrmException {
        webhookService.deleteConfig(id);
        return OperationResponse.build();
    }

    /**
     * 查询 Webhook 配置详情
     *
     * @param id Webhook 配置 ID
     * @return Webhook 配置详情
     * @throws ScrmException Webhook 配置不存在
     */
    @RequirePermission(resource = "scrm_webhook", action = "read")
    @GetMapping("/configs/{id}")
    public OperationResponse<ScrmWebhookConfigDto> getConfig(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(webhookService.getConfig(id));
    }

    /**
     * 分页查询 Webhook 配置, 支持按状态与事件类型过滤
     *
     * @param status    状态过滤 (可选)
     * @param eventType 事件类型过滤 (可选)
     * @param page      页码 (从 0 开始, 默认 0)
     * @param size      每页大小 (默认 20)
     * @return Webhook 配置分页结果
     */
    @RequirePermission(resource = "scrm_webhook", action = "read")
    @GetMapping("/configs/list")
    public OperationResponse<Page<ScrmWebhookConfigDto>> listConfigs(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(webhookService.listConfigs(status, eventType, PageRequest.of(page, size)));
    }

    /**
     * 激活 Webhook 配置
     *
     * @param id Webhook 配置 ID
     * @return 更新后的 Webhook 配置
     * @throws ScrmException Webhook 配置不存在
     */
    @RequirePermission(resource = "scrm_webhook", action = "update")
    @PostMapping("/configs/{id}/activate")
    public OperationResponse<ScrmWebhookConfigDto> activateConfig(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(webhookService.activateConfig(id));
    }

    /**
     * 停用 Webhook 配置
     *
     * @param id Webhook 配置 ID
     * @return 更新后的 Webhook 配置
     * @throws ScrmException Webhook 配置不存在
     */
    @RequirePermission(resource = "scrm_webhook", action = "update")
    @PostMapping("/configs/{id}/deactivate")
    public OperationResponse<ScrmWebhookConfigDto> deactivateConfig(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(webhookService.deactivateConfig(id));
    }

    /**
     * 发送测试事件到目标 URL
     *
     * @param id Webhook 配置 ID
     * @return 推送日志 (含响应信息)
     * @throws ScrmException Webhook 配置不存在
     */
    @RequirePermission(resource = "scrm_webhook", action = "execute")
    @RateLimit(capacity = 5, refillTokens = 5, refillPeriodSeconds = 60, message = "测试 Webhook 过于频繁，请稍后重试")
    @PostMapping("/configs/{id}/test")
    public OperationResponse<ScrmWebhookLogDto> testConfig(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(webhookService.testConfig(id));
    }

    /**
     * 发布事件
     * <p>
     * 查找订阅该事件类型的活跃 Webhook, 创建推送日志并异步发送 HTTP 通知。
     * </p>
     *
     * @param eventDto 事件入参
     * @return 创建的推送日志列表 (未匹配到订阅则返回空列表)
     * @throws ScrmException 参数校验失败
     */
    @RequirePermission(resource = "scrm_webhook", action = "execute")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60, message = "发布事件过于频繁，请稍后重试")
    @PostMapping("/events/publish")
    public OperationResponse<List<ScrmWebhookLogDto>> publishEvent(@Valid @RequestBody ScrmWebhookEventDto eventDto)
            throws ScrmException {
        return OperationResponse.build(webhookService.publishEvent(eventDto));
    }

    /**
     * 批量发布事件
     *
     * @param eventDtos 事件入参列表
     * @return 所有事件创建的推送日志列表
     * @throws ScrmException 参数校验失败
     */
    @RequirePermission(resource = "scrm_webhook", action = "execute")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60, message = "批量发布事件过于频繁，请稍后重试")
    @PostMapping("/events/batch")
    public OperationResponse<List<ScrmWebhookLogDto>> batchPublishEvents(
            @Valid @RequestBody List<ScrmWebhookEventDto> eventDtos) throws ScrmException {
        return OperationResponse.build(webhookService.batchPublishEvents(eventDtos));
    }

    /**
     * 处理待重试的 Webhook (手动触发处理)
     * <p>
     * 捞取已到期且状态为 RETRY 的日志, 逐个重新发送。
     * 通常由定时任务调用, 此接口用于手动补偿。
     * </p>
     *
     * @return 本次处理的日志数
     */
    @RequirePermission(resource = "scrm_webhook", action = "execute")
    @RateLimit(capacity = 5, refillTokens = 5, refillPeriodSeconds = 60, message = "处理重试过于频繁，请稍后重试")
    @PostMapping("/retries/process")
    public OperationResponse<Integer> processRetries() {
        return OperationResponse.build(webhookService.processRetries());
    }

    /**
     * 分页查询推送日志, 支持按 Webhook、事件类型、状态与时间范围过滤
     *
     * @param webhookId Webhook 配置 ID 过滤 (可选)
     * @param eventType 事件类型过滤 (可选)
     * @param status    日志状态过滤 (可选)
     * @param startTime 起始时间过滤 (可选, 格式 yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间过滤 (可选, 格式 yyyy-MM-dd'T'HH:mm:ss)
     * @param page      页码 (从 0 开始, 默认 0)
     * @param size      每页大小 (默认 20)
     * @return 推送日志分页结果
     */
    @RequirePermission(resource = "scrm_webhook", action = "read")
    @GetMapping("/logs/list")
    public OperationResponse<Page<ScrmWebhookLogDto>> listLogs(
            @RequestParam(required = false) Long webhookId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(webhookService.getLogs(webhookId, eventType, status,
                startTime, endTime, PageRequest.of(page, size)));
    }

    /**
     * 查询推送日志详情
     *
     * @param id 日志 ID
     * @return 推送日志详情
     * @throws ScrmException 日志不存在
     */
    @RequirePermission(resource = "scrm_webhook", action = "read")
    @GetMapping("/logs/{id}")
    public OperationResponse<ScrmWebhookLogDto> getLog(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(webhookService.getLog(id));
    }

    /**
     * Webhook 推送统计
     *
     * @param webhookId Webhook 配置 ID
     * @param startTime 起始时间 (可选, 默认最近 30 天)
     * @param endTime   截止时间 (可选, 默认当前时间)
     * @return 统计结果
     * @throws ScrmException Webhook 配置不存在
     */
    @RequirePermission(resource = "scrm_webhook", action = "read")
    @GetMapping("/stats/{webhookId}")
    public OperationResponse<WebhookStatsVo> getStats(
            @PathVariable Long webhookId,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) throws ScrmException {
        return OperationResponse.build(webhookService.getWebhookStats(webhookId, startTime, endTime));
    }

    /**
     * 可订阅事件类型列表
     *
     * @return 事件类型列表
     */
    @RequirePermission(resource = "scrm_webhook", action = "read")
    @GetMapping("/available-events")
    public OperationResponse<List<String>> availableEvents() {
        return OperationResponse.build(webhookService.listAvailableEvents());
    }
}
