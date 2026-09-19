/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmNotificationCenterController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmBatchSendDto;
import org.hiylo.scrm.dto.ScrmNotificationBatchDto;
import org.hiylo.scrm.dto.ScrmNotificationDto;
import org.hiylo.scrm.dto.ScrmNotificationPreferenceDto;
import org.hiylo.scrm.dto.ScrmNotificationSendDto;
import org.hiylo.scrm.dto.ScrmNotificationTemplateDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmNotificationCenterService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
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
import java.util.Map;

/**
 * SCRM 通知中心控制器
 * <p>
 * 提供通知模板管理、通知发送 (单条 / 批量 / 定时 / 取消 / 重试)、消息已读管理、
 * 批次管理、通知偏好管理与通知统计等接口。权限由 gateway-server 统一鉴权,
 * 此处通过 {@link RequirePermission} 声明资源与动作元数据。
 * </p>
 * <p><b>发送 / 批量发送 / 重试端点已启用 (IN_APP 站内信即送达, WEBHOOK 渠道真实 HTTP 分发,
 * PUSH 渠道经个推 {@code PushNotificationService} 下发; EMAIL / SMS 无网关实现, 记录置 FAILED
 * 并标明通道未接入)。</b></p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/notifications")
@RequiredArgsConstructor
public class ScrmNotificationCenterController {

    /** 通知中心服务 */
    private final ScrmNotificationCenterService notificationCenterService;

    // ============================================================
    // 通知模板管理
    // ============================================================

    /**
     * 创建通知模板
     *
     * @param dto 模板参数
     * @return 创建后的模板
     */
    @RequirePermission(resource = "scrm_notification_template", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/templates")
    public OperationResponse<ScrmNotificationTemplateDto> createTemplate(
            @Valid @RequestBody ScrmNotificationTemplateDto dto)
            throws ScrmException {
        return OperationResponse.build(notificationCenterService.createTemplate(dto));
    }

    /**
     * 更新通知模板
     *
     * @param id  模板 ID
     * @param dto 模板参数
     * @return 更新后的模板
     */
    @RequirePermission(resource = "scrm_notification_template", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/templates/{id}")
    public OperationResponse<ScrmNotificationTemplateDto> updateTemplate(@PathVariable Long id,
                                                                          @RequestBody ScrmNotificationTemplateDto dto)
            throws ScrmException {
        return OperationResponse.build(notificationCenterService.updateTemplate(id, dto));
    }

    /**
     * 删除通知模板
     *
     * @param id 模板 ID
     * @return 空响应
     * @throws ScrmException 模板不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_notification_template", action = "delete")
    @DeleteMapping("/templates/{id}")
    public OperationResponse<Void> deleteTemplate(@PathVariable Long id) throws ScrmException {
        notificationCenterService.deleteTemplate(id);
        return OperationResponse.build();
    }

    /**
     * 查询通知模板详情
     *
     * @param id 模板 ID
     * @return 模板详情
     * @throws ScrmException 模板不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_notification_template", action = "read")
    @GetMapping("/templates/{id}")
    public OperationResponse<ScrmNotificationTemplateDto> getTemplate(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(notificationCenterService.getTemplate(id));
    }

    /**
     * 分页查询通知模板, 支持按渠道 / 分类 / 启用状态 / 关键词过滤
     *
     * @param channel  渠道过滤 (可选)
     * @param category 分类过滤 (可选)
     * @param enabled  启用状态过滤 (可选)
     * @param keyword  关键词过滤, 匹配模板名称 / 模板编码 (可选)
     * @param page     页码 (从 0 开始, 默认 0)
     * @param size     每页大小 (默认 20)
     * @return 模板分页结果
     */
    @RequirePermission(resource = "scrm_notification_template", action = "read")
    @GetMapping("/templates/list")
    public OperationResponse<Page<ScrmNotificationTemplateDto>> listTemplates(
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(notificationCenterService.listTemplates(
                channel, category, enabled, keyword, pageable));
    }

    /**
     * 启用通知模板
     *
     * @param id 模板 ID
     * @return 更新后的模板
     * @throws ScrmException 模板不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_notification_template", action = "update")
    @PostMapping("/templates/{id}/enable")
    public OperationResponse<ScrmNotificationTemplateDto> enableTemplate(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(notificationCenterService.enableTemplate(id));
    }

    /**
     * 禁用通知模板
     *
     * @param id 模板 ID
     * @return 更新后的模板
     * @throws ScrmException 模板不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_notification_template", action = "update")
    @PostMapping("/templates/{id}/disable")
    public OperationResponse<ScrmNotificationTemplateDto> disableTemplate(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(notificationCenterService.disableTemplate(id));
    }

    /**
     * 按模板编码查询模板
     *
     * @param code 模板编码
     * @return 模板详情
     * @throws ScrmException 模板不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_notification_template", action = "read")
    @GetMapping("/templates/code/{code}")
    public OperationResponse<ScrmNotificationTemplateDto> getTemplateByCode(@PathVariable String code)
            throws ScrmException {
        return OperationResponse.build(notificationCenterService.getTemplateByCode(code));
    }

    /**
     * 渲染通知模板 (变量替换)
     *
     * @param templateCode 模板编码
     * @param variables    模板变量 (key=变量名, value=变量值)
     * @return 渲染结果 (title / content)
     * @throws ScrmException 模板不存在 / 渲染失败
     */
    @RequirePermission(resource = "scrm_notification_template", action = "read")
    @PostMapping("/templates/render")
    public OperationResponse<Map<String, String>> renderTemplate(@RequestParam String templateCode,
                                                                   @RequestBody(required = false)
                                                                           Map<String, String> variables)
            throws ScrmException {
        return OperationResponse.build(notificationCenterService.renderTemplate(templateCode, variables));
    }

    // ============================================================
    // 通知发送
    // ============================================================

    /**
     * 发送单条通知 (模板渲染 → 偏好检查 → 发送 → 记录, 模拟实现)
     *
     * @param dto 发送参数 (templateCode + recipients + variables)
     * @return 创建的通知列表
     * @throws ScrmException 模板或接收者非法 / 发送失败
     */
    @RequirePermission(resource = "scrm_notification", action = "create")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/send")
    public OperationResponse<List<ScrmNotificationDto>> sendNotification(
            @Valid @RequestBody ScrmNotificationSendDto dto)
            throws ScrmException {
        return OperationResponse.build(notificationCenterService.sendNotification(dto));
    }

    /**
     * 批量发送通知 (创建批次并逐条发送, 模拟实现)
     *
     * @param batchDto 批量发送参数 (batchName + templateCode + channel + recipientIds)
     * @return 更新后的批次
     * @throws ScrmException 参数非法 / 发送失败
     */
    @RequirePermission(resource = "scrm_notification", action = "create")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/batch-send")
    public OperationResponse<ScrmNotificationBatchDto> sendBatch(@Valid @RequestBody ScrmBatchSendDto batchDto)
            throws ScrmException {
        return OperationResponse.build(notificationCenterService.sendBatch(batchDto));
    }

    /**
     * 定时发送通知 (设置计划发送时间)
     *
     * @param id          通知 ID
     * @param scheduledAt 计划发送时间 (ISO 格式)
     * @return 更新后的通知
     * @throws ScrmException 通知不存在 / 时间非法
     */
    @RequirePermission(resource = "scrm_notification", action = "update")
    @PostMapping("/{id}/schedule")
    public OperationResponse<ScrmNotificationDto> scheduleNotification(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime scheduledAt)
            throws ScrmException {
        return OperationResponse.build(notificationCenterService.scheduleNotification(id, scheduledAt));
    }

    /**
     * 取消发送通知
     *
     * @param id 通知 ID
     * @return 更新后的通知
     * @throws ScrmException 通知不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_notification", action = "update")
    @PostMapping("/{id}/cancel")
    public OperationResponse<ScrmNotificationDto> cancelNotification(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(notificationCenterService.cancelNotification(id));
    }

    /**
     * 重试失败的通知
     *
     * @param id 通知 ID
     * @return 更新后的通知
     * @throws ScrmException 通知不存在 / 重试失败
     */
    @RequirePermission(resource = "scrm_notification", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/{id}/retry")
    public OperationResponse<ScrmNotificationDto> retryNotification(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(notificationCenterService.retryNotification(id));
    }

    // ============================================================
    // 通知查询与已读管理
    // ============================================================

    /**
     * 查询通知详情
     *
     * @param id 通知 ID
     * @return 通知详情
     * @throws ScrmException 通知不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_notification", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmNotificationDto> getNotification(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(notificationCenterService.getNotification(id));
    }

    /**
     * 分页查询通知, 支持按渠道 / 分类 / 状态 / 接收者 / 时间区间过滤
     *
     * @param channel     渠道过滤 (可选)
     * @param category    分类过滤 (可选)
     * @param status      状态过滤 (可选)
     * @param recipientId 接收者 ID 过滤 (可选)
     * @param startTime   创建时间起点 (可选, ISO 格式)
     * @param endTime     创建时间终点 (可选, ISO 格式)
     * @param page        页码 (从 0 开始, 默认 0)
     * @param size        每页大小 (默认 20)
     * @return 通知分页结果
     */
    @RequirePermission(resource = "scrm_notification", action = "read")
    @GetMapping("/list")
    public OperationResponse<Page<ScrmNotificationDto>> listNotifications(
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String recipientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(notificationCenterService.listNotifications(
                channel, category, status, recipientId, startTime, endTime, pageable));
    }

    /**
     * 标记通知为已读
     *
     * @param id 通知 ID
     * @return 更新后的通知
     * @throws ScrmException 通知不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_notification", action = "update")
    @PostMapping("/{id}/read")
    public OperationResponse<ScrmNotificationDto> markAsRead(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(notificationCenterService.markAsRead(id));
    }

    /**
     * 批量标记通知为已读
     *
     * @param ids 通知 ID 列表
     * @return 已标记的通知列表
     */
    @RequirePermission(resource = "scrm_notification", action = "update")
    @PostMapping("/read/batch")
    public OperationResponse<List<ScrmNotificationDto>> batchMarkAsRead(@RequestBody List<Long> ids) {
        return OperationResponse.build(notificationCenterService.batchMarkAsRead(ids));
    }

    /**
     * 将用户的全部未读站内信标记为已读
     *
     * @param userId 用户 ID
     * @return 影响行数
     */
    @RequirePermission(resource = "scrm_notification", action = "update")
    @PostMapping("/read/all/{userId}")
    public OperationResponse<Integer> markAllAsRead(@PathVariable String userId) {
        return OperationResponse.build(notificationCenterService.markAllAsRead(userId));
    }

    /**
     * 获取用户未读站内信数
     *
     * @param userId 用户 ID
     * @return 未读数
     */
    @RequirePermission(resource = "scrm_notification", action = "read")
    @GetMapping("/unread-count/{userId}")
    public OperationResponse<Long> getUnreadCount(@PathVariable String userId) {
        return OperationResponse.build(notificationCenterService.getUnreadCount(userId));
    }

    // ============================================================
    // 批次管理
    // ============================================================

    /**
     * 查询批次详情
     *
     * @param id 批次 ID
     * @return 批次详情
     * @throws ScrmException 批次不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_notification_batch", action = "read")
    @GetMapping("/batches/{id}")
    public OperationResponse<ScrmNotificationBatchDto> getBatch(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(notificationCenterService.getBatch(id));
    }

    /**
     * 分页查询批次, 支持按状态 / 时间区间过滤
     *
     * @param status    状态过滤 (可选)
     * @param startTime 创建时间起点 (可选, ISO 格式)
     * @param endTime   创建时间终点 (可选, ISO 格式)
     * @param page      页码 (从 0 开始, 默认 0)
     * @param size      每页大小 (默认 20)
     * @return 批次分页结果
     */
    @RequirePermission(resource = "scrm_notification_batch", action = "read")
    @GetMapping("/batches/list")
    public OperationResponse<Page<ScrmNotificationBatchDto>> listBatches(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(notificationCenterService.listBatches(status, startTime, endTime, pageable));
    }

    /**
     * 取消批次
     *
     * @param id 批次 ID
     * @return 更新后的批次
     * @throws ScrmException 批次不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_notification_batch", action = "update")
    @PostMapping("/batches/{id}/cancel")
    public OperationResponse<ScrmNotificationBatchDto> cancelBatch(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(notificationCenterService.cancelBatch(id));
    }

    /**
     * 执行批次发送 (模拟实现)
     *
     * @param id 批次 ID
     * @return 更新后的批次
     * @throws ScrmException 批次不存在 / 执行失败
     */
    @RequirePermission(resource = "scrm_notification_batch", action = "execute")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/batches/{id}/process")
    public OperationResponse<ScrmNotificationBatchDto> processBatch(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(notificationCenterService.processBatch(id));
    }

    /**
     * 获取批次进度
     *
     * @param id 批次 ID
     * @return 进度信息
     * @throws ScrmException 批次不存在
     */
    @RequirePermission(resource = "scrm_notification_batch", action = "read")
    @GetMapping("/batches/{id}/progress")
    public OperationResponse<Map<String, Object>> getBatchProgress(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(notificationCenterService.getBatchProgress(id));
    }

    // ============================================================
    // 通知偏好
    // ============================================================

    /**
     * 查询用户通知偏好列表
     *
     * @param userId 用户 ID
     * @return 偏好列表
     */
    @RequirePermission(resource = "scrm_notification_preference", action = "read")
    @GetMapping("/preferences/{userId}")
    public OperationResponse<List<ScrmNotificationPreferenceDto>> getPreference(@PathVariable String userId) {
        return OperationResponse.build(notificationCenterService.getPreference(userId));
    }

    /**
     * 更新 (或创建) 用户通知偏好
     *
     * @param userId   用户 ID
     * @param channel  通知渠道
     * @param category 分类
     * @param dto      偏好参数
     * @return 更新后的偏好
     * @throws ScrmException 参数非法 / 渠道不支持
     */
    @RequirePermission(resource = "scrm_notification_preference", action = "update")
    @PutMapping("/preferences/{userId}")
    public OperationResponse<ScrmNotificationPreferenceDto> updatePreference(
            @PathVariable String userId,
            @RequestParam String channel,
            @RequestParam String category,
            @Valid @RequestBody ScrmNotificationPreferenceDto dto) throws ScrmException {
        return OperationResponse.build(notificationCenterService.updatePreference(userId, channel, category, dto));
    }

    /**
     * 查询用户通知偏好列表 (列表入口)
     *
     * @param userId 用户 ID
     * @return 偏好列表
     */
    @RequirePermission(resource = "scrm_notification_preference", action = "read")
    @GetMapping("/preferences/list/{userId}")
    public OperationResponse<List<ScrmNotificationPreferenceDto>> listPreferences(@PathVariable String userId) {
        return OperationResponse.build(notificationCenterService.listPreferences(userId));
    }

    /**
     * 检查偏好 (是否允许发送)
     *
     * @param userId   用户 ID
     * @param channel  通知渠道
     * @param category 分类
     * @param priority  通知优先级 (默认 0)
     * @return true 允许发送, false 拒绝
     */
    @RequirePermission(resource = "scrm_notification_preference", action = "read")
    @PostMapping("/preferences/check")
    public OperationResponse<Boolean> checkPreference(
            @RequestParam String userId,
            @RequestParam String channel,
            @RequestParam String category,
            @RequestParam(defaultValue = "0") int priority) {
        return OperationResponse.build(notificationCenterService.checkPreference(userId, channel, category, priority));
    }

    // ============================================================
    // 通知统计
    // ============================================================

    /**
     * 获取通知总览统计 (发送数 / 成功率 / 已读率 / 各渠道分布)
     *
     * @param startTime 发送时间起点 (可选, ISO 格式)
     * @param endTime   发送时间终点 (可选, ISO 格式)
     * @return 总览统计
     */
    @RequirePermission(resource = "scrm_notification_stats", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getNotificationStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(notificationCenterService.getNotificationStats(startTime, endTime));
    }

    /**
     * 获取渠道统计 (每渠道发送数)
     *
     * @param startTime 发送时间起点 (可选, ISO 格式)
     * @param endTime   发送时间终点 (可选, ISO 格式)
     * @return 渠道统计
     */
    @RequirePermission(resource = "scrm_notification_stats", action = "read")
    @GetMapping("/stats/channels")
    public OperationResponse<Map<String, Long>> getChannelStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(notificationCenterService.getChannelStats(startTime, endTime));
    }

    /**
     * 获取分类统计 (每分类发送数)
     *
     * @param startTime 发送时间起点 (可选, ISO 格式)
     * @param endTime   发送时间终点 (可选, ISO 格式)
     * @return 分类统计
     */
    @RequirePermission(resource = "scrm_notification_stats", action = "read")
    @GetMapping("/stats/categories")
    public OperationResponse<Map<String, Long>> getCategoryStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(notificationCenterService.getCategoryStats(startTime, endTime));
    }

    /**
     * 获取送达率 (已送达 / 已发送)
     *
     * @param channel   渠道过滤 (可选, 为空则统计全部渠道)
     * @param startTime 发送时间起点 (可选, ISO 格式)
     * @param endTime   发送时间终点 (可选, ISO 格式)
     * @return 送达率 (百分比)
     */
    @RequirePermission(resource = "scrm_notification_stats", action = "read")
    @GetMapping("/stats/delivery-rate")
    public OperationResponse<Double> getDeliveryRate(
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(notificationCenterService.getDeliveryRate(channel, startTime, endTime));
    }
}
