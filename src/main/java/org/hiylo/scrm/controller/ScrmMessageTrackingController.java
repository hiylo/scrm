/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMessageTrackingController.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmMessageReadReportDto;
import org.hiylo.scrm.dto.ScrmMessageRecallActionDto;
import org.hiylo.scrm.dto.ScrmMessageTrackingDto;
import org.hiylo.scrm.entity.ScrmMessageReadLogEntity;
import org.hiylo.scrm.entity.ScrmMessageRecallEntity;
import org.hiylo.scrm.entity.ScrmMessageTrackingEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmMessageTrackingService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 消息跟踪控制器。
 * <p>
 * 提供消息发送后全生命周期跟踪接口: 跟踪记录管理 (创建/查询/列表/状态更新/送达标记)、
 * 阅读管理 (记录/批量记录/日志查询/统计/未读列表)、撤回管理 (撤回/查询/列表/窗口检查/执行)、
 * 转发追踪 (记录/转发链/列表)、回复追踪 (记录/列表) 与统计分析 (概览/已读率/互动/渠道对比/
 * 已读趋势/最佳发送时间)。权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为
 * 端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/message-tracking")
@RequiredArgsConstructor
public class ScrmMessageTrackingController {

    /** 消息跟踪服务 */
    private final ScrmMessageTrackingService scrmMessageTrackingService;

    // ============================================================
    // 跟踪记录 Tracking
    // ============================================================

    /**
     * 创建消息跟踪记录。
     *
     * @param dto 跟踪参数
     * @return 创建后的跟踪记录
     * @throws ScrmException 消息 ID 重复 / 参数非法
     */
    @RequirePermission(resource = "scrm_message_tracking", action = "create")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping
    public OperationResponse<ScrmMessageTrackingEntity> createTracking(@Valid @RequestBody ScrmMessageTrackingDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmMessageTrackingService.createTracking(dto));
    }

    /**
     * 查询消息跟踪记录详情。
     *
     * @param id 跟踪记录 ID
     * @return 跟踪记录详情
     * @throws ScrmException 跟踪记录不存在
     */
    @RequirePermission(resource = "scrm_message_tracking", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmMessageTrackingEntity> getTracking(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmMessageTrackingService.getTracking(id));
    }

    /**
     * 按消息 ID 查询跟踪记录。
     *
     * @param messageId 消息 ID
     * @return 跟踪记录详情
     * @throws ScrmException 跟踪记录不存在
     */
    @RequirePermission(resource = "scrm_message_tracking", action = "read")
    @GetMapping("/by-message/{messageId}")
    public OperationResponse<ScrmMessageTrackingEntity> getTrackingByMessageId(@PathVariable String messageId)
            throws ScrmException {
        return OperationResponse.build(scrmMessageTrackingService.getTrackingByMessageId(messageId));
    }

    /**
     * 分页查询消息跟踪记录, 支持按批次 / 发送者 / 接收者 / 渠道 / 发送状态 / 已读 / 已撤回 /
     * 时间范围组合过滤。
     *
     * @param batchId     批次 ID 过滤 (可空)
     * @param senderId    发送者 ID 过滤 (可空)
     * @param recipientId 接收者 ID 过滤 (可空)
     * @param channel     渠道过滤 (可空): WECHAT/WORK_WECHAT/SMS/EMAIL/APP_PUSH/WEB_SOCKET
     * @param sendStatus  发送状态过滤 (可空): PENDING/SENT/DELIVERED/FAILED/CANCELLED
     * @param isRead      已读过滤 (可空)
     * @param isRecalled  已撤回过滤 (可空)
     * @param startTime   发送时间起点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime     发送时间终点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param page        页码 (从 0 开始, 默认 0)
     * @param size        每页大小 (默认 20)
     * @return 跟踪记录分页结果 (按 sentAt DESC)
     */
    @RequirePermission(resource = "scrm_message_tracking", action = "read")
    @GetMapping("/list")
    public OperationResponse<Page<ScrmMessageTrackingEntity>> listTrackings(
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) String senderId,
            @RequestParam(required = false) String recipientId,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String sendStatus,
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(required = false) Boolean isRecalled,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "sentAt"));
        return OperationResponse.build(scrmMessageTrackingService.listTrackings(
                batchId, senderId, recipientId, channel, sendStatus, isRead, isRecalled,
                startTime, endTime, pageable));
    }

    /**
     * 更新消息发送状态。
     *
     * @param messageId 消息 ID
     * @param status    目标发送状态: PENDING/SENT/DELIVERED/FAILED/CANCELLED
     * @return 更新后的跟踪记录
     * @throws ScrmException 跟踪记录不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_message_tracking", action = "update")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/{messageId}/status")
    public OperationResponse<ScrmMessageTrackingEntity> updateSendStatus(
            @PathVariable String messageId, @RequestParam String status) throws ScrmException {
        return OperationResponse.build(scrmMessageTrackingService.updateSendStatus(messageId, status));
    }

    /**
     * 标记消息送达。
     *
     * @param messageId 消息 ID
     * @return 更新后的跟踪记录
     * @throws ScrmException 跟踪记录不存在
     */
    @RequirePermission(resource = "scrm_message_tracking", action = "update")
    @PostMapping("/{messageId}/delivered")
    public OperationResponse<ScrmMessageTrackingEntity> markDelivered(@PathVariable String messageId)
            throws ScrmException {
        return OperationResponse.build(scrmMessageTrackingService.markDelivered(messageId));
    }

    // ============================================================
    // 阅读管理 /reads
    // ============================================================

    /**
     * 记录消息阅读。
     *
     * @param reportDto 阅读上报参数
     * @return 创建后的阅读日志
     * @throws ScrmException 跟踪记录不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_message_tracking", action = "create")
    @RateLimit(capacity = 120, refillTokens = 120, refillPeriodSeconds = 60)
    @PostMapping("/reads/record")
    public OperationResponse<ScrmMessageReadLogEntity> recordRead(
            @Valid @RequestBody ScrmMessageReadReportDto reportDto)
            throws ScrmException {
        return OperationResponse.build(scrmMessageTrackingService.recordRead(reportDto));
    }

    /**
     * 批量记录消息阅读。
     *
     * @param reports 阅读上报列表
     * @return 创建后的阅读日志列表
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_message_tracking", action = "create")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/reads/batch-record")
    public OperationResponse<List<ScrmMessageReadLogEntity>> batchRecordReads(
            @RequestBody List<ScrmMessageReadReportDto> reports) throws ScrmException {
        return OperationResponse.build(scrmMessageTrackingService.batchRecordReads(reports));
    }

    /**
     * 按消息跟踪 ID 查询阅读日志列表 (按阅读时间升序)。
     *
     * @param messageTrackingId 消息跟踪 ID
     * @return 阅读日志列表
     * @throws ScrmException 跟踪记录不存在
     */
    @RequirePermission(resource = "scrm_message_tracking", action = "read")
    @GetMapping("/reads/log/{messageTrackingId}")
    public OperationResponse<List<ScrmMessageReadLogEntity>> getReadLogs(@PathVariable Long messageTrackingId)
            throws ScrmException {
        return OperationResponse.build(scrmMessageTrackingService.getReadLogs(messageTrackingId));
    }

    /**
     * 查询阅读日志详情。
     *
     * @param id 阅读日志 ID
     * @return 阅读日志详情
     * @throws ScrmException 阅读日志不存在
     */
    @RequirePermission(resource = "scrm_message_tracking", action = "read")
    @GetMapping("/reads/logs/{id}")
    public OperationResponse<ScrmMessageReadLogEntity> getReadLog(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmMessageTrackingService.getReadLog(id));
    }

    /**
     * 分页查询阅读日志, 支持按消息跟踪 ID / 阅读者过滤。
     *
     * @param messageTrackingId 消息跟踪 ID 过滤 (可空)
     * @param readerId          阅读者 ID 过滤 (可空)
     * @param page              页码 (从 0 开始, 默认 0)
     * @param size              每页大小 (默认 20)
     * @return 阅读日志分页结果 (按 readAt DESC)
     */
    @RequirePermission(resource = "scrm_message_tracking", action = "read")
    @GetMapping("/reads/list")
    public OperationResponse<Page<ScrmMessageReadLogEntity>> listReadLogs(
            @RequestParam(required = false) Long messageTrackingId,
            @RequestParam(required = false) String readerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "readAt"));
        return OperationResponse.build(scrmMessageTrackingService.listReadLogs(messageTrackingId, readerId, pageable));
    }

    /**
     * 阅读统计: 阅读次数 / 独立阅读者 / 重复阅读数 / 平均阅读时长等。
     *
     * @param messageTrackingId 消息跟踪 ID
     * @return 统计结果 Map
     * @throws ScrmException 跟踪记录不存在
     */
    @RequirePermission(resource = "scrm_message_tracking", action = "read")
    @GetMapping("/reads/stats/{messageTrackingId}")
    public OperationResponse<Map<String, Object>> getReadStats(@PathVariable Long messageTrackingId)
            throws ScrmException {
        return OperationResponse.build(scrmMessageTrackingService.getReadStats(messageTrackingId));
    }

    /**
     * 未读列表: 按批次 ID 分页查询未读消息跟踪记录。
     *
     * @param batchId 批次 ID 过滤 (可空)
     * @param page    页码 (从 0 开始, 默认 0)
     * @param size    每页大小 (默认 20)
     * @return 未读跟踪记录分页结果 (按 sentAt DESC)
     */
    @RequirePermission(resource = "scrm_message_tracking", action = "read")
    @GetMapping("/reads/unread")
    public OperationResponse<Page<ScrmMessageTrackingEntity>> getUnreadList(
            @RequestParam(required = false) Long batchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "sentAt"));
        return OperationResponse.build(scrmMessageTrackingService.getUnreadList(batchId, pageable));
    }

    // ============================================================
    // 撤回管理 /recalls
    // ============================================================

    /**
     * 撤回消息 (模拟实现)。
     *
     * @param recallActionDto 撤回动作参数 (messageId + reason)
     * @return 创建后的撤回记录
     * @throws ScrmException 跟踪记录不存在 / 已撤回 / 超出撤回窗口
     */
    @RequirePermission(resource = "scrm_message_tracking", action = "execute")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/recalls/recall")
    public OperationResponse<ScrmMessageRecallEntity> recallMessage(
            @Valid @RequestBody ScrmMessageRecallActionDto recallActionDto) throws ScrmException {
        return OperationResponse.build(scrmMessageTrackingService.recallMessage(recallActionDto));
    }

    /**
     * 查询撤回记录详情。
     *
     * @param id 撤回记录 ID
     * @return 撤回记录详情
     * @throws ScrmException 撤回记录不存在
     */
    @RequirePermission(resource = "scrm_message_tracking", action = "read")
    @GetMapping("/recalls/{id}")
    public OperationResponse<ScrmMessageRecallEntity> getRecall(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmMessageTrackingService.getRecall(id));
    }

    /**
     * 按消息 ID 查询撤回记录 (取最近一条)。
     *
     * @param messageId 消息 ID
     * @return 撤回记录详情
     * @throws ScrmException 撤回记录不存在
     */
    @RequirePermission(resource = "scrm_message_tracking", action = "read")
    @GetMapping("/recalls/by-message/{messageId}")
    public OperationResponse<ScrmMessageRecallEntity> getRecallByMessageId(@PathVariable String messageId)
            throws ScrmException {
        return OperationResponse.build(scrmMessageTrackingService.getRecallByMessageId(messageId));
    }

    /**
     * 分页查询撤回记录, 支持按发送者 / 撤回状态 / 时间范围过滤。
     *
     * @param senderId     发送者 ID 过滤 (可空)
     * @param recallStatus 撤回状态过滤 (可空): SUCCESS/PARTIAL/FAILED/PENDING
     * @param startTime    撤回时间起点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime      撤回时间终点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param page         页码 (从 0 开始, 默认 0)
     * @param size         每页大小 (默认 20)
     * @return 撤回记录分页结果 (按 recalledAt DESC)
     */
    @RequirePermission(resource = "scrm_message_tracking", action = "read")
    @GetMapping("/recalls/list")
    public OperationResponse<Page<ScrmMessageRecallEntity>> listRecalls(
            @RequestParam(required = false) String senderId,
            @RequestParam(required = false) String recallStatus,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "recalledAt"));
        return OperationResponse.build(scrmMessageTrackingService.listRecalls(
                senderId, recallStatus, startTime, endTime, pageable));
    }

    /**
     * 检查撤回窗口: 消息发送时间至今是否在默认撤回窗口 (2 分钟) 内。
     *
     * @param messageId 消息 ID
     * @return 撤回窗口检查结果 Map
     * @throws ScrmException 跟踪记录不存在
     */
    @RequirePermission(resource = "scrm_message_tracking", action = "read")
    @GetMapping("/recalls/check-window/{messageId}")
    public OperationResponse<Map<String, Object>> checkRecallWindow(@PathVariable String messageId)
            throws ScrmException {
        return OperationResponse.build(scrmMessageTrackingService.checkRecallWindow(messageId));
    }

    /**
     * 执行撤回 (模拟实现): 将 PENDING 状态的撤回记录推进为 SUCCESS。
     *
     * @param id 撤回记录 ID
     * @return 更新后的撤回记录
     * @throws ScrmException 撤回记录不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_message_tracking", action = "execute")
    @PostMapping("/recalls/{id}/process")
    public OperationResponse<ScrmMessageRecallEntity> processRecall(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmMessageTrackingService.processRecall(id));
    }

    // ============================================================
    // 转发追踪 /forwards
    // ============================================================

    /**
     * 记录转发。
     *
     * @param messageId   消息 ID
     * @param forwarderId 转发者 ID
     * @return 更新后的跟踪记录
     * @throws ScrmException 跟踪记录不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_message_tracking", action = "update")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/forwards/record")
    public OperationResponse<ScrmMessageTrackingEntity> recordForward(
            @RequestParam String messageId, @RequestParam String forwarderId) throws ScrmException {
        return OperationResponse.build(scrmMessageTrackingService.recordForward(messageId, forwarderId));
    }

    /**
     * 转发链: 解析跟踪记录 metadata 中的 forwardChain, 返回转发事件列表。
     *
     * @param messageId 消息 ID
     * @return 转发事件列表 [{forwarderId, forwardedAt, sequence}]
     * @throws ScrmException 跟踪记录不存在
     */
    @RequirePermission(resource = "scrm_message_tracking", action = "read")
    @GetMapping("/forwards/chain/{messageId}")
    public OperationResponse<List<Map<String, Object>>> getForwardChain(@PathVariable String messageId)
            throws ScrmException {
        return OperationResponse.build(scrmMessageTrackingService.getForwardChain(messageId));
    }

    /**
     * 分页查询转发事件。
     *
     * @param messageTrackingId 消息跟踪 ID
     * @param page              页码 (从 0 开始, 默认 0)
     * @param size              每页大小 (默认 20)
     * @return 转发事件分页结果
     * @throws ScrmException 跟踪记录不存在
     */
    @RequirePermission(resource = "scrm_message_tracking", action = "read")
    @GetMapping("/forwards/list")
    public OperationResponse<Page<Map<String, Object>>> listForwards(
            @RequestParam Long messageTrackingId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmMessageTrackingService.listForwards(messageTrackingId, pageable));
    }

    // ============================================================
    // 回复追踪 /replies
    // ============================================================

    /**
     * 记录回复。
     *
     * @param messageId    消息 ID
     * @param replyContent 回复内容摘要
     * @param replierId    回复者 ID
     * @return 更新后的跟踪记录
     * @throws ScrmException 跟踪记录不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_message_tracking", action = "update")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/replies/record")
    public OperationResponse<ScrmMessageTrackingEntity> recordReply(
            @RequestParam String messageId,
            @RequestParam String replyContent,
            @RequestParam String replierId) throws ScrmException {
        return OperationResponse.build(scrmMessageTrackingService.recordReply(messageId, replyContent, replierId));
    }

    /**
     * 分页查询回复事件。
     *
     * @param messageTrackingId 消息跟踪 ID
     * @param page              页码 (从 0 开始, 默认 0)
     * @param size              每页大小 (默认 20)
     * @return 回复事件分页结果
     * @throws ScrmException 跟踪记录不存在
     */
    @RequirePermission(resource = "scrm_message_tracking", action = "read")
    @GetMapping("/replies/list")
    public OperationResponse<Page<Map<String, Object>>> listReplies(
            @RequestParam Long messageTrackingId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmMessageTrackingService.listReplies(messageTrackingId, pageable));
    }

    // ============================================================
    // 统计分析 /stats
    // ============================================================

    /**
     * 跟踪统计概览: 发送数 / 送达率 / 已读率 / 撤回率 / 转发率 / 回复率。
     *
     * @param startTime 发送时间起点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   发送时间终点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_message_tracking", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getTrackingStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmMessageTrackingService.getTrackingStats(startTime, endTime));
    }

    /**
     * 已读率统计: 按渠道 (可空) 统计发送数 / 已读数 / 已读率。
     *
     * @param startTime 发送时间起点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   发送时间终点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param channel   渠道过滤 (可空): WECHAT/WORK_WECHAT/SMS/EMAIL/APP_PUSH/WEB_SOCKET
     * @return 已读率统计 Map
     */
    @RequirePermission(resource = "scrm_message_tracking", action = "read")
    @GetMapping("/stats/read-rate")
    public OperationResponse<Map<String, Object>> getReadRateStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(required = false) String channel) {
        return OperationResponse.build(scrmMessageTrackingService.getReadRateStats(startTime, endTime, channel));
    }

    /**
     * 互动统计: 平均互动评分 / 已读数 / 已转发数 / 已回复数。
     *
     * @param startTime 发送时间起点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   发送时间终点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 互动统计 Map
     */
    @RequirePermission(resource = "scrm_message_tracking", action = "read")
    @GetMapping("/stats/engagement")
    public OperationResponse<Map<String, Object>> getEngagementStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmMessageTrackingService.getEngagementStats(startTime, endTime));
    }

    /**
     * 渠道对比: 每个渠道的发送数 / 已读数 / 已读率。
     *
     * @param startTime 发送时间起点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   发送时间终点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 渠道对比列表 [{channel, totalSent, readCount, readRate}]
     */
    @RequirePermission(resource = "scrm_message_tracking", action = "read")
    @GetMapping("/stats/channel-comparison")
    public OperationResponse<List<Map<String, Object>>> getChannelComparison(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmMessageTrackingService.getChannelComparison(startTime, endTime));
    }

    /**
     * 已读趋势: 按日期统计发送数与已读数。
     *
     * @param days 回溯天数 (默认 7)
     * @return 趋势列表 [{date, sentCount, readCount, readRate}]
     */
    @RequirePermission(resource = "scrm_message_tracking", action = "read")
    @GetMapping("/stats/read-trend")
    public OperationResponse<List<Map<String, Object>>> getReadTrend(
            @RequestParam(defaultValue = "7") int days) {
        return OperationResponse.build(scrmMessageTrackingService.getReadTrend(days));
    }

    /**
     * 最佳发送时间分析: 按发送小时统计发送数 / 已读数 / 已读率, 已读率最高的小时段为最佳发送时间。
     *
     * @param channel   渠道过滤 (可空): WECHAT/WORK_WECHAT/SMS/EMAIL/APP_PUSH/WEB_SOCKET
     * @param startTime 发送时间起点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   发送时间终点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 小时统计列表 [{hour, sentCount, readCount, readRate}], 按 readRate DESC 排序
     */
    @RequirePermission(resource = "scrm_message_tracking", action = "read")
    @GetMapping("/stats/best-send-time")
    public OperationResponse<List<Map<String, Object>>> getBestSendTime(
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmMessageTrackingService.getBestSendTime(channel, startTime, endTime));
    }
}
