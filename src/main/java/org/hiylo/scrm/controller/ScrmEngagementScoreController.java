/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmEngagementScoreController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmEngagementEventDto;
import org.hiylo.scrm.dto.ScrmEngagementLevelDto;
import org.hiylo.scrm.dto.ScrmEngagementRecordDto;
import org.hiylo.scrm.dto.ScrmEngagementRuleDto;
import org.hiylo.scrm.dto.ScrmEngagementScoreDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmEngagementScoreService;
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
 * SCRM 客户互动评分引擎控制器
 * <p>
 * 提供互动评分规则、互动事件、客户评分、活跃度等级及互动统计等接口。
 * 权限由 gateway-server 统一鉴权, 此处通过 {@link RequirePermission} 声明资源与动作元数据。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/engagement")
@RequiredArgsConstructor
public class ScrmEngagementScoreController {

    /** 互动评分服务 */
    private final ScrmEngagementScoreService engagementScoreService;

    // ============================================================
    // 互动评分规则
    // ============================================================

    /**
     * 创建互动评分规则
     *
     * @param dto 规则参数
     * @return 创建后的规则
     */
    @RequirePermission(resource = "scrm_engagement_rule", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/rules")
    public OperationResponse<ScrmEngagementRuleDto> createRule(@Valid @RequestBody ScrmEngagementRuleDto dto)
            throws ScrmException {
        return OperationResponse.build(engagementScoreService.createRule(dto));
    }

    /**
     * 更新互动评分规则
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     */
    @RequirePermission(resource = "scrm_engagement_rule", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/rules/{id}")
    public OperationResponse<ScrmEngagementRuleDto> updateRule(@PathVariable Long id,
                                                                @RequestBody ScrmEngagementRuleDto dto)
            throws ScrmException {
        return OperationResponse.build(engagementScoreService.updateRule(id, dto));
    }

    /**
     * 删除互动评分规则
     *
     * @param id 规则 ID
     * @return 空响应
     * @throws ScrmException 规则不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_engagement_rule", action = "delete")
    @DeleteMapping("/rules/{id}")
    public OperationResponse<Void> deleteRule(@PathVariable Long id) throws ScrmException {
        engagementScoreService.deleteRule(id);
        return OperationResponse.build();
    }

    /**
     * 查询互动评分规则详情
     *
     * @param id 规则 ID
     * @return 规则详情
     * @throws ScrmException 规则不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_engagement_rule", action = "read")
    @GetMapping("/rules/{id}")
    public OperationResponse<ScrmEngagementRuleDto> getRule(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(engagementScoreService.getRule(id));
    }

    /**
     * 分页查询互动评分规则, 支持按行为类型 / 渠道 / 启用状态 / 关键字过滤
     *
     * @param behaviorType 行为类型过滤 (可选)
     * @param channel      渠道过滤 (可选)
     * @param enabled      启用状态过滤 (可选)
     * @param keyword      规则名称关键字过滤 (可选)
     * @param page         页码 (从 0 开始, 默认 0)
     * @param size         每页大小 (默认 20)
     * @return 规则分页结果
     */
    @RequirePermission(resource = "scrm_engagement_rule", action = "read")
    @GetMapping("/rules/list")
    public OperationResponse<Page<ScrmEngagementRuleDto>> listRules(
            @RequestParam(required = false) String behaviorType,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(
                engagementScoreService.listRules(behaviorType, channel, enabled, keyword, pageable));
    }

    /**
     * 启用互动评分规则
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_engagement_rule", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/rules/{id}/enable")
    public OperationResponse<ScrmEngagementRuleDto> enableRule(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(engagementScoreService.enableRule(id));
    }

    /**
     * 禁用互动评分规则
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_engagement_rule", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/rules/{id}/disable")
    public OperationResponse<ScrmEngagementRuleDto> disableRule(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(engagementScoreService.disableRule(id));
    }

    // ============================================================
    // 互动事件
    // ============================================================

    /**
     * 记录行为事件 (匹配规则→计算得分→更新评分)
     *
     * @param dto 事件记录参数
     * @return 创建后的事件
     */
    @RequirePermission(resource = "scrm_engagement_event", action = "create")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/events/record")
    public OperationResponse<ScrmEngagementEventDto> recordEvent(@Valid @RequestBody ScrmEngagementRecordDto dto)
            throws ScrmException {
        return OperationResponse.build(engagementScoreService.recordEvent(dto));
    }

    /**
     * 批量记录行为事件
     *
     * @param records 事件记录列表
     * @return 创建后的事件列表
     * @throws ScrmException 参数非法 / 规则匹配失败
     */
    @RequirePermission(resource = "scrm_engagement_event", action = "create")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/events/batch-record")
    public OperationResponse<List<ScrmEngagementEventDto>> batchRecordEvents(
            @RequestBody List<ScrmEngagementRecordDto> records) throws ScrmException {
        return OperationResponse.build(engagementScoreService.batchRecordEvents(records));
    }

    /**
     * 查询事件详情
     *
     * @param id 事件 ID
     * @return 事件详情
     * @throws ScrmException 事件不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_engagement_event", action = "read")
    @GetMapping("/events/{id}")
    public OperationResponse<ScrmEngagementEventDto> getEvent(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(engagementScoreService.getEvent(id));
    }

    /**
     * 分页查询事件, 支持按客户 / 行为类型 / 渠道 / 时间区间过滤
     *
     * @param customerId   客户 ID 过滤 (可选)
     * @param behaviorType 行为类型过滤 (可选)
     * @param channel      渠道过滤 (可选)
     * @param startTime    起始时间 (可选, yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime      截止时间 (可选, yyyy-MM-dd'T'HH:mm:ss)
     * @param page         页码 (从 0 开始, 默认 0)
     * @param size         每页大小 (默认 20)
     * @return 事件分页结果
     */
    @RequirePermission(resource = "scrm_engagement_event", action = "read")
    @GetMapping("/events/list")
    public OperationResponse<Page<ScrmEngagementEventDto>> listEvents(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String behaviorType,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(engagementScoreService.listEvents(customerId, behaviorType, channel,
                startTime, endTime, pageable));
    }

    /**
     * 按客户分页查询事件
     *
     * @param customerId 客户 ID
     * @param page       页码 (从 0 开始, 默认 0)
     * @param size       每页大小 (默认 20)
     * @return 事件分页结果
     */
    @RequirePermission(resource = "scrm_engagement_event", action = "read")
    @GetMapping("/events/customer/{customerId}")
    public OperationResponse<Page<ScrmEngagementEventDto>> getEventsByCustomer(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(engagementScoreService.getEventsByCustomer(customerId, pageable));
    }

    // ============================================================
    // 客户评分
    // ============================================================

    /**
     * 计算客户评分 (汇总事件→应用衰减→更新等级→更新趋势)
     *
     * @param customerId 客户 ID
     * @return 更新后的评分
     */
    @RequirePermission(resource = "scrm_engagement_score", action = "calculate")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/scores/calculate/{customerId}")
    public OperationResponse<ScrmEngagementScoreDto> calculateScore(@PathVariable Long customerId)
            throws ScrmException {
        return OperationResponse.build(engagementScoreService.calculateScore(customerId));
    }

    /**
     * 重算所有客户评分
     *
     * @return 处理结果 (total 客户数 / processed 实际处理数)
     */
    @RequirePermission(resource = "scrm_engagement_score", action = "calculate")
    @RateLimit(capacity = 5, refillTokens = 5, refillPeriodSeconds = 60)
    @PostMapping("/scores/recalculate-all")
    public OperationResponse<Map<String, Object>> recalculateAllScores() {
        return OperationResponse.build(engagementScoreService.recalculateAllScores());
    }

    /**
     * 获取客户评分
     *
     * @param customerId 客户 ID
     * @return 评分详情
     */
    @RequirePermission(resource = "scrm_engagement_score", action = "read")
    @GetMapping("/scores/{customerId}")
    public OperationResponse<ScrmEngagementScoreDto> getScore(@PathVariable Long customerId)
            throws ScrmException {
        return OperationResponse.build(engagementScoreService.getScore(customerId));
    }

    /**
     * 分页查询评分, 支持按等级 / 分数区间过滤与排序
     *
     * @param engagementLevel 等级过滤 (可选)
     * @param minScore        最低当前分过滤 (可选)
     * @param maxScore        最高当前分过滤 (可选)
     * @param sortBy          排序字段: currentScore / totalScore / totalEvents / streakDays (可选, 默认 currentScore)
     * @param page            页码 (从 0 开始, 默认 0)
     * @param size            每页大小 (默认 20)
     * @return 评分分页结果
     */
    @RequirePermission(resource = "scrm_engagement_score", action = "read")
    @GetMapping("/scores/list")
    public OperationResponse<Page<ScrmEngagementScoreDto>> listScores(
            @RequestParam(required = false) String engagementLevel,
            @RequestParam(required = false) Double minScore,
            @RequestParam(required = false) Double maxScore,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(engagementScoreService.listScores(engagementLevel, minScore, maxScore,
                sortBy, pageable));
    }

    /**
     * 评分趋势: 返回最近 days 天每日的事件数 / 原始得分 / 衰减后得分
     *
     * @param customerId 客户 ID
     * @param days       天数 (默认 7)
     * @return 趋势列表
     * @throws ScrmException 客户不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_engagement_score", action = "read")
    @GetMapping("/scores/{customerId}/trend")
    public OperationResponse<List<Map<String, Object>>> getScoreTrend(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "7") int days) throws ScrmException {
        return OperationResponse.build(engagementScoreService.getScoreTrend(customerId, days));
    }

    /**
     * 评分分布: 按活跃等级分组统计客户数
     *
     * @return 等级分布
     */
    @RequirePermission(resource = "scrm_engagement_score", action = "read")
    @GetMapping("/scores/distribution")
    public OperationResponse<Map<String, Object>> getScoreDistribution() {
        return OperationResponse.build(engagementScoreService.getScoreDistribution());
    }

    /**
     * 高分客户排行 (按当前分倒序)
     *
     * @param limit 返回数量 (默认 10)
     * @return 评分列表
     */
    @RequirePermission(resource = "scrm_engagement_score", action = "read")
    @GetMapping("/scores/top")
    public OperationResponse<List<ScrmEngagementScoreDto>> getTopCustomers(
            @RequestParam(defaultValue = "10") int limit) {
        return OperationResponse.build(engagementScoreService.getTopCustomers(limit));
    }

    // ============================================================
    // 活跃度等级
    // ============================================================

    /**
     * 创建互动活跃度等级
     *
     * @param dto 等级参数
     * @return 创建后的等级
     */
    @RequirePermission(resource = "scrm_engagement_level", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/levels")
    public OperationResponse<ScrmEngagementLevelDto> createLevel(@Valid @RequestBody ScrmEngagementLevelDto dto)
            throws ScrmException {
        return OperationResponse.build(engagementScoreService.createLevel(dto));
    }

    /**
     * 更新互动活跃度等级
     *
     * @param id  等级 ID
     * @param dto 等级参数
     * @return 更新后的等级
     */
    @RequirePermission(resource = "scrm_engagement_level", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/levels/{id}")
    public OperationResponse<ScrmEngagementLevelDto> updateLevel(@PathVariable Long id,
                                                                   @RequestBody ScrmEngagementLevelDto dto)
            throws ScrmException {
        return OperationResponse.build(engagementScoreService.updateLevel(id, dto));
    }

    /**
     * 删除互动活跃度等级
     *
     * @param id 等级 ID
     * @return 空响应
     * @throws ScrmException 等级不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_engagement_level", action = "delete")
    @DeleteMapping("/levels/{id}")
    public OperationResponse<Void> deleteLevel(@PathVariable Long id) throws ScrmException {
        engagementScoreService.deleteLevel(id);
        return OperationResponse.build();
    }

    /**
     * 查询互动活跃度等级详情
     *
     * @param id 等级 ID
     * @return 等级详情
     * @throws ScrmException 等级不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_engagement_level", action = "read")
    @GetMapping("/levels/{id}")
    public OperationResponse<ScrmEngagementLevelDto> getLevel(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(engagementScoreService.getLevel(id));
    }

    /**
     * 分页查询互动活跃度等级, 支持按启用状态过滤
     *
     * @param enabled 启用状态过滤 (可选)
     * @param page    页码 (从 0 开始, 默认 0)
     * @param size    每页大小 (默认 20)
     * @return 等级分页结果
     */
    @RequirePermission(resource = "scrm_engagement_level", action = "read")
    @GetMapping("/levels/list")
    public OperationResponse<Page<ScrmEngagementLevelDto>> listLevels(
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(engagementScoreService.listLevels(enabled, pageable));
    }

    /**
     * 启用互动活跃度等级
     *
     * @param id 等级 ID
     * @return 更新后的等级
     * @throws ScrmException 等级不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_engagement_level", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/levels/{id}/enable")
    public OperationResponse<ScrmEngagementLevelDto> enableLevel(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(engagementScoreService.enableLevel(id));
    }

    /**
     * 禁用互动活跃度等级
     *
     * @param id 等级 ID
     * @return 更新后的等级
     * @throws ScrmException 等级不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_engagement_level", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/levels/{id}/disable")
    public OperationResponse<ScrmEngagementLevelDto> disableLevel(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(engagementScoreService.disableLevel(id));
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 互动统计: 总事件 / 总客户 / 平均分 / 各等级分布
     *
     * @param startTime 起始时间 (可选, yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可选, yyyy-MM-dd'T'HH:mm:ss)
     * @return 互动统计
     */
    @RequirePermission(resource = "scrm_engagement_stats", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getEngagementStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(engagementScoreService.getEngagementStats(startTime, endTime));
    }

    /**
     * 行为类型统计: 按行为类型分组统计事件数
     *
     * @param startTime 起始时间 (可选, yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可选, yyyy-MM-dd'T'HH:mm:ss)
     * @return 行为统计列表
     */
    @RequirePermission(resource = "scrm_engagement_stats", action = "read")
    @GetMapping("/stats/behaviors")
    public OperationResponse<List<Map<String, Object>>> getBehaviorStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(engagementScoreService.getBehaviorStats(startTime, endTime));
    }

    /**
     * 渠道统计: 按渠道分组统计事件数
     *
     * @param startTime 起始时间 (可选, yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可选, yyyy-MM-dd'T'HH:mm:ss)
     * @return 渠道统计列表
     */
    @RequirePermission(resource = "scrm_engagement_stats", action = "read")
    @GetMapping("/stats/channels")
    public OperationResponse<List<Map<String, Object>>> getChannelStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(engagementScoreService.getChannelStats(startTime, endTime));
    }

    /**
     * 互动趋势: 最近 days 天每日事件数
     *
     * @param days 天数 (默认 7)
     * @return 趋势列表
     */
    @RequirePermission(resource = "scrm_engagement_stats", action = "read")
    @GetMapping("/stats/trend")
    public OperationResponse<List<Map<String, Object>>> getTrend(
            @RequestParam(defaultValue = "7") int days) {
        return OperationResponse.build(engagementScoreService.getTrend(days));
    }
}
