/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFeedbackController.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmFeedbackCategoryDto;
import org.hiylo.scrm.dto.ScrmFeedbackCommentDto;
import org.hiylo.scrm.dto.ScrmFeedbackDto;
import org.hiylo.scrm.dto.ScrmFeedbackProcessDto;
import org.hiylo.scrm.dto.ScrmFeedbackQueryDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmFeedbackService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
 * SCRM 客户反馈管理控制器。
 * <p>
 * 提供反馈增删改查、分配/状态变更/优先级变更/解决/关闭/驳回/标记重复/合并反馈、
 * 评论与内部备注/回复、反馈分类管理、反馈分析 (情感/分类/标签/摘要) 以及反馈统计
 * (总览/情感分布/分类统计/趋势/热点问题/响应时间/SLA 达标率) 接口。权限由 gateway-server
 * 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/feedbacks")
@RequiredArgsConstructor
public class ScrmFeedbackController {

    /** 反馈服务 */
    private final ScrmFeedbackService scrmFeedbackService;

    // ============================================================
    // 反馈 CRUD
    // ============================================================

    /**
     * 创建反馈。
     *
     * @param dto 反馈参数
     * @return 创建后的反馈
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_feedback", action = "create")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60,
            message = "创建反馈过于频繁，请稍后重试")
    @PostMapping
    public OperationResponse<ScrmFeedbackDto> createFeedback(@Valid @RequestBody ScrmFeedbackDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmFeedbackService.createFeedback(dto));
    }

    /**
     * 更新反馈 (字段非空才覆盖)。
     *
     * @param id  反馈 ID
     * @param dto 反馈参数
     * @return 更新后的反馈
     * @throws ScrmException 反馈不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_feedback", action = "update")
    @PutMapping("/{id}")
    public OperationResponse<ScrmFeedbackDto> updateFeedback(@PathVariable Long id,
                                                              @RequestBody ScrmFeedbackDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmFeedbackService.updateFeedback(id, dto));
    }

    /**
     * 删除反馈 (级联清理评论)。
     *
     * @param id 反馈 ID
     * @return 空响应
     * @throws ScrmException 反馈不存在
     */
    @RequirePermission(resource = "scrm_feedback", action = "delete")
    @DeleteMapping("/{id}")
    public OperationResponse<Void> deleteFeedback(@PathVariable Long id) throws ScrmException {
        scrmFeedbackService.deleteFeedback(id);
        return OperationResponse.build();
    }

    /**
     * 查询反馈详情。
     *
     * @param id 反馈 ID
     * @return 反馈详情
     * @throws ScrmException 反馈不存在
     */
    @RequirePermission(resource = "scrm_feedback", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmFeedbackDto> getFeedback(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmFeedbackService.getFeedback(id));
    }

    /**
     * 按反馈编号查询反馈。
     *
     * @param feedbackNo 反馈编号
     * @return 反馈详情
     * @throws ScrmException 反馈不存在
     */
    @RequirePermission(resource = "scrm_feedback", action = "read")
    @GetMapping("/no/{feedbackNo}")
    public OperationResponse<ScrmFeedbackDto> getFeedbackByNo(@PathVariable String feedbackNo)
            throws ScrmException {
        return OperationResponse.build(scrmFeedbackService.getFeedbackByNo(feedbackNo));
    }

    /**
     * 分页查询反馈, 支持按反馈类型、分类、状态、优先级、情感、客户、时间范围与关键词过滤。
     *
     * @param queryDto 查询条件 (Spring 自动绑定查询参数)
     * @param page     页码 (从 0 开始, 默认 0)
     * @param size     每页大小 (默认 20)
     * @return 反馈分页结果 (按创建时间倒序)
     */
    @RequirePermission(resource = "scrm_feedback", action = "read")
    @GetMapping("/list")
    public OperationResponse<Page<ScrmFeedbackDto>> listFeedbacks(
            ScrmFeedbackQueryDto queryDto,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmFeedbackService.listFeedbacks(queryDto, pageable));
    }

    // ============================================================
    // 反馈动作
    // ============================================================

    /**
     * 分配反馈 (assigneeId 与 teamId 至少传其一)。
     *
     * @param id         反馈 ID
     * @param assigneeId 处理人 ID (可空)
     * @param teamId     处理团队 ID (可空)
     * @return 更新后的反馈
     * @throws ScrmException 反馈不存在 / 参数非法 / 反馈已关闭
     */
    @RequirePermission(resource = "scrm_feedback", action = "execute")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/{id}/assign")
    public OperationResponse<ScrmFeedbackDto> assignFeedback(
            @PathVariable Long id,
            @RequestParam(required = false) String assigneeId,
            @RequestParam(required = false) String teamId) throws ScrmException {
        return OperationResponse.build(scrmFeedbackService.assignFeedback(id, assigneeId, teamId));
    }

    /**
     * 变更反馈状态。
     *
     * @param processDto 处理请求 (feedbackId + status + resolution + assigneeId)
     * @return 更新后的反馈
     * @throws ScrmException 反馈不存在 / 状态非法 / 状态流转非法
     */
    @RequirePermission(resource = "scrm_feedback", action = "execute")
    @PostMapping("/status")
    public OperationResponse<ScrmFeedbackDto> changeStatus(@Valid @RequestBody ScrmFeedbackProcessDto processDto)
            throws ScrmException {
        return OperationResponse.build(scrmFeedbackService.changeStatus(processDto));
    }

    /**
     * 变更反馈优先级。
     *
     * @param id       反馈 ID
     * @param priority 目标优先级: URGENT / HIGH / MEDIUM / LOW
     * @return 更新后的反馈
     * @throws ScrmException 反馈不存在 / 优先级非法 / 反馈已关闭
     */
    @RequirePermission(resource = "scrm_feedback", action = "execute")
    @PostMapping("/{id}/priority")
    public OperationResponse<ScrmFeedbackDto> changePriority(@PathVariable Long id,
                                                              @RequestParam String priority)
            throws ScrmException {
        return OperationResponse.build(scrmFeedbackService.changePriority(id, priority));
    }

    /**
     * 解决反馈。
     *
     * @param id         反馈 ID
     * @param resolution 解决方案 (可空)
     * @return 更新后的反馈
     * @throws ScrmException 反馈不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_feedback", action = "execute")
    @PostMapping("/{id}/resolve")
    public OperationResponse<ScrmFeedbackDto> resolveFeedback(
            @PathVariable Long id,
            @RequestParam(required = false) String resolution) throws ScrmException {
        return OperationResponse.build(scrmFeedbackService.resolveFeedback(id, resolution));
    }

    /**
     * 关闭反馈。
     *
     * @param id 反馈 ID
     * @return 更新后的反馈
     * @throws ScrmException 反馈不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_feedback", action = "execute")
    @PostMapping("/{id}/close")
    public OperationResponse<ScrmFeedbackDto> closeFeedback(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmFeedbackService.closeFeedback(id));
    }

    /**
     * 驳回反馈。
     *
     * @param id     反馈 ID
     * @param reason 驳回原因 (可空)
     * @return 更新后的反馈
     * @throws ScrmException 反馈不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_feedback", action = "execute")
    @PostMapping("/{id}/reject")
    public OperationResponse<ScrmFeedbackDto> rejectFeedback(
            @PathVariable Long id,
            @RequestParam(required = false) String reason) throws ScrmException {
        return OperationResponse.build(scrmFeedbackService.rejectFeedback(id, reason));
    }

    /**
     * 标记反馈为重复。
     *
     * @param id                 反馈 ID
     * @param originalFeedbackId 原始反馈 ID
     * @return 更新后的反馈
     * @throws ScrmException 反馈不存在 / 原始反馈不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_feedback", action = "execute")
    @PostMapping("/{id}/duplicate")
    public OperationResponse<ScrmFeedbackDto> markDuplicate(
            @PathVariable Long id,
            @RequestParam Long originalFeedbackId) throws ScrmException {
        return OperationResponse.build(scrmFeedbackService.markDuplicate(id, originalFeedbackId));
    }

    /**
     * 合并反馈 (将当前反馈合并到目标反馈)。
     *
     * @param id       当前反馈 ID
     * @param targetId 目标反馈 ID
     * @return 更新后的当前反馈
     * @throws ScrmException 反馈不存在 / 目标反馈不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_feedback", action = "execute")
    @PostMapping("/{id}/merge")
    public OperationResponse<ScrmFeedbackDto> mergeFeedback(
            @PathVariable Long id,
            @RequestParam Long targetId) throws ScrmException {
        return OperationResponse.build(scrmFeedbackService.mergeFeedback(id, targetId));
    }

    // ============================================================
    // 评论
    // ============================================================

    /**
     * 添加评论。
     *
     * @param dto 评论参数
     * @return 创建后的评论
     * @throws ScrmException 反馈不存在 / 参数非法 / 反馈已关闭
     */
    @RequirePermission(resource = "scrm_feedback", action = "execute")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/comments")
    public OperationResponse<ScrmFeedbackCommentDto> addComment(@Valid @RequestBody ScrmFeedbackCommentDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmFeedbackService.addComment(dto));
    }

    /**
     * 查询反馈评论列表 (按评论发生时间升序)。
     *
     * @param feedbackId 反馈 ID
     * @return 评论列表
     * @throws ScrmException 反馈不存在
     */
    @RequirePermission(resource = "scrm_feedback", action = "read")
    @GetMapping("/comments/{feedbackId}")
    public OperationResponse<List<ScrmFeedbackCommentDto>> listComments(@PathVariable Long feedbackId)
            throws ScrmException {
        return OperationResponse.build(scrmFeedbackService.listComments(feedbackId));
    }

    /**
     * 添加内部备注。
     *
     * @param feedbackId 反馈 ID
     * @param content    备注内容
     * @param authorId   作者 ID
     * @return 创建后的评论
     * @throws ScrmException 反馈不存在 / 参数非法 / 反馈已关闭
     */
    @RequirePermission(resource = "scrm_feedback", action = "execute")
    @PostMapping("/comments/{feedbackId}/internal")
    public OperationResponse<ScrmFeedbackCommentDto> addInternalNote(
            @PathVariable Long feedbackId,
            @RequestParam String content,
            @RequestParam String authorId) throws ScrmException {
        return OperationResponse.build(scrmFeedbackService.addInternalNote(feedbackId, content, authorId));
    }

    /**
     * 回复评论。
     *
     * @param feedbackId      反馈 ID
     * @param parentCommentId 父评论 ID
     * @param content         回复内容
     * @param authorId        作者 ID
     * @return 创建后的评论
     * @throws ScrmException 反馈不存在 / 父评论不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_feedback", action = "execute")
    @PostMapping("/comments/{feedbackId}/reply")
    public OperationResponse<ScrmFeedbackCommentDto> replyToComment(
            @PathVariable Long feedbackId,
            @RequestParam Long parentCommentId,
            @RequestParam String content,
            @RequestParam String authorId) throws ScrmException {
        return OperationResponse.build(scrmFeedbackService.replyToComment(feedbackId, parentCommentId,
                content, authorId));
    }

    /**
     * 删除评论。
     *
     * @param id 评论 ID
     * @return 空响应
     * @throws ScrmException 评论不存在
     */
    @RequirePermission(resource = "scrm_feedback", action = "delete")
    @DeleteMapping("/comments/{id}")
    public OperationResponse<Void> deleteComment(@PathVariable Long id) throws ScrmException {
        scrmFeedbackService.deleteComment(id);
        return OperationResponse.build();
    }

    // ============================================================
    // 分类 Category
    // ============================================================

    /**
     * 创建反馈分类。
     *
     * @param dto 分类参数
     * @return 创建后的分类
     * @throws ScrmException 参数非法 / 分类编码重复
     */
    @RequirePermission(resource = "scrm_feedback", action = "create")
    @PostMapping("/categories")
    public OperationResponse<ScrmFeedbackCategoryDto> createCategory(@Valid @RequestBody ScrmFeedbackCategoryDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmFeedbackService.createCategory(dto));
    }

    /**
     * 更新反馈分类 (字段非空才覆盖)。
     *
     * @param id  分类 ID
     * @param dto 分类参数
     * @return 更新后的分类
     * @throws ScrmException 分类不存在 / 参数非法 / 分类编码重复
     */
    @RequirePermission(resource = "scrm_feedback", action = "update")
    @PutMapping("/categories/{id}")
    public OperationResponse<ScrmFeedbackCategoryDto> updateCategory(
            @PathVariable Long id,
            @RequestBody ScrmFeedbackCategoryDto dto) throws ScrmException {
        return OperationResponse.build(scrmFeedbackService.updateCategory(id, dto));
    }

    /**
     * 删除反馈分类。
     *
     * @param id 分类 ID
     * @return 空响应
     * @throws ScrmException 分类不存在
     */
    @RequirePermission(resource = "scrm_feedback", action = "delete")
    @DeleteMapping("/categories/{id}")
    public OperationResponse<Void> deleteCategory(@PathVariable Long id) throws ScrmException {
        scrmFeedbackService.deleteCategory(id);
        return OperationResponse.build();
    }

    /**
     * 查询反馈分类详情。
     *
     * @param id 分类 ID
     * @return 分类详情
     * @throws ScrmException 分类不存在
     */
    @RequirePermission(resource = "scrm_feedback", action = "read")
    @GetMapping("/categories/{id}")
    public OperationResponse<ScrmFeedbackCategoryDto> getCategory(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmFeedbackService.getCategory(id));
    }

    /**
     * 按分类编码查询反馈分类。
     *
     * @param code 分类编码
     * @return 分类详情
     * @throws ScrmException 分类不存在
     */
    @RequirePermission(resource = "scrm_feedback", action = "read")
    @GetMapping("/categories/code/{code}")
    public OperationResponse<ScrmFeedbackCategoryDto> getCategoryByCode(@PathVariable String code)
            throws ScrmException {
        return OperationResponse.build(scrmFeedbackService.getCategoryByCode(code));
    }

    /**
     * 分页查询反馈分类, 支持按适用反馈类型与启用状态过滤。
     *
     * @param applicableType 适用反馈类型过滤 (可空)
     * @param enabled        启用状态过滤 (可空)
     * @param page           页码 (从 0 开始, 默认 0)
     * @param size           每页大小 (默认 20)
     * @return 分类分页结果 (按 sortOrder 升序)
     */
    @RequirePermission(resource = "scrm_feedback", action = "read")
    @GetMapping("/categories/list")
    public OperationResponse<Page<ScrmFeedbackCategoryDto>> listCategories(
            @RequestParam(required = false) String applicableType,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmFeedbackService.listCategories(applicableType, enabled, pageable));
    }

    /**
     * 启用反馈分类。
     *
     * @param id 分类 ID
     * @return 更新后的分类
     * @throws ScrmException 分类不存在
     */
    @RequirePermission(resource = "scrm_feedback", action = "update")
    @PostMapping("/categories/{id}/enable")
    public OperationResponse<ScrmFeedbackCategoryDto> enableCategory(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmFeedbackService.enableCategory(id));
    }

    /**
     * 禁用反馈分类。
     *
     * @param id 分类 ID
     * @return 更新后的分类
     * @throws ScrmException 分类不存在
     */
    @RequirePermission(resource = "scrm_feedback", action = "update")
    @PostMapping("/categories/{id}/disable")
    public OperationResponse<ScrmFeedbackCategoryDto> disableCategory(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmFeedbackService.disableCategory(id));
    }

    // ============================================================
    // 分析 Analytics
    // ============================================================

    /**
     * 情感分析 (关键词法)。
     *
     * @param text 待分析文本
     * @return 情感: POSITIVE / NEUTRAL / NEGATIVE
     */
    @RequirePermission(resource = "scrm_feedback", action = "read")
    @GetMapping("/analytics/sentiment")
    public OperationResponse<String> analyzeSentiment(@RequestParam String text) {
        return OperationResponse.build(scrmFeedbackService.analyzeSentiment(text));
    }

    /**
     * 自动分类 (关键词匹配)。
     *
     * @param content 反馈内容
     * @return 反馈类型
     */
    @RequirePermission(resource = "scrm_feedback", action = "read")
    @GetMapping("/analytics/categorize")
    public OperationResponse<String> autoCategorize(@RequestParam String content) {
        return OperationResponse.build(scrmFeedbackService.autoCategorize(content));
    }

    /**
     * 提取标签 (关键词匹配)。
     *
     * @param content 反馈内容
     * @return 标签字符串 (逗号分隔), 无命中返回 null
     */
    @RequirePermission(resource = "scrm_feedback", action = "read")
    @GetMapping("/analytics/tags")
    public OperationResponse<String> extractTags(@RequestParam String content) {
        return OperationResponse.build(scrmFeedbackService.extractTags(content));
    }

    /**
     * 生成反馈摘要。
     *
     * @param feedbackId 反馈 ID
     * @return 摘要文本
     * @throws ScrmException 反馈不存在
     */
    @RequirePermission(resource = "scrm_feedback", action = "read")
    @GetMapping("/analytics/summary/{feedbackId}")
    public OperationResponse<String> generateSummary(@PathVariable Long feedbackId) throws ScrmException {
        return OperationResponse.build(scrmFeedbackService.generateSummary(feedbackId));
    }

    // ============================================================
    // 统计 Stats
    // ============================================================

    /**
     * 反馈统计: 总数 / 各类型 / 各状态 / 平均评分 / 平均解决时长 / 满意度。
     * <p>时间范围按反馈创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_feedback", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getFeedbackStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmFeedbackService.getFeedbackStats(startTime, endTime));
    }

    /**
     * 情感分布统计: 各情感数量与占比。
     * <p>时间范围按反馈创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_feedback", action = "read")
    @GetMapping("/stats/sentiment")
    public OperationResponse<Map<String, Object>> getSentimentDistribution(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmFeedbackService.getSentimentDistribution(startTime, endTime));
    }

    /**
     * 分类统计: 各分类数量。
     * <p>时间范围按反馈创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_feedback", action = "read")
    @GetMapping("/stats/categories")
    public OperationResponse<Map<String, Object>> getCategoryStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmFeedbackService.getCategoryStats(startTime, endTime));
    }

    /**
     * 反馈趋势: 按天统计最近 N 天的反馈数量, 支持按反馈类型过滤。
     *
     * @param days         天数 (默认 7)
     * @param feedbackType 反馈类型过滤 (可空)
     * @return 趋势数据 (date + count)
     */
    @RequirePermission(resource = "scrm_feedback", action = "read")
    @GetMapping("/stats/trend")
    public OperationResponse<List<Map<String, Object>>> getTrend(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false) String feedbackType) {
        return OperationResponse.build(scrmFeedbackService.getTrend(days, feedbackType));
    }

    /**
     * 热点问题: 按分类汇总最近 30 天反馈, 返回 Top N。
     *
     * @param limit 返回条数 (默认 10)
     * @return 热点问题列表 (category + count + percentage)
     */
    @RequirePermission(resource = "scrm_feedback", action = "read")
    @GetMapping("/stats/top-issues")
    public OperationResponse<List<Map<String, Object>>> getTopIssues(
            @RequestParam(defaultValue = "10") int limit) {
        return OperationResponse.build(scrmFeedbackService.getTopIssues(limit));
    }

    /**
     * 响应时间统计: 平均响应时长与平均解决时长。
     * <p>时间范围按反馈创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_feedback", action = "read")
    @GetMapping("/stats/response-time")
    public OperationResponse<Map<String, Object>> getResponseTimeStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmFeedbackService.getResponseTimeStats(startTime, endTime));
    }

    /**
     * SLA 达标率统计。
     * <p>时间范围按反馈创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果 (total / compliant / violation / complianceRate)
     */
    @RequirePermission(resource = "scrm_feedback", action = "read")
    @GetMapping("/stats/sla")
    public OperationResponse<Map<String, Object>> getSlaCompliance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmFeedbackService.getSlaCompliance(startTime, endTime));
    }
}
