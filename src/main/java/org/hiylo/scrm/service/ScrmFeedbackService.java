/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFeedbackService.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.dto.ScrmFeedbackCategoryDto;
import org.hiylo.scrm.dto.ScrmFeedbackCommentDto;
import org.hiylo.scrm.dto.ScrmFeedbackDto;
import org.hiylo.scrm.dto.ScrmFeedbackProcessDto;
import org.hiylo.scrm.dto.ScrmFeedbackQueryDto;
import org.hiylo.scrm.exception.ScrmException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 客户反馈管理服务门面。
 * <p>
 * 统一对外暴露客户反馈核心能力, 具体实现按子域委托给兄弟服务:
 * 反馈管理 (增删改查/分配/状态优先级变更/解决关闭驳回/合并/编号生成/情感分析/自动分类/标签提取)、
 * 评论回复、分类管理、情感分析统计。所有 public 方法签名保持不变。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
public class ScrmFeedbackService {

    /** 反馈管理服务 (反馈 CRUD 与动作) */
    private final ScrmFeedbackManagementService managementService;
    /** 评论回复服务 */
    private final ScrmFeedbackCommentService commentService;
    /** 分类管理服务 */
    private final ScrmFeedbackCategoryService categoryService;
    /** 情感分析统计服务 */
    private final ScrmFeedbackAnalysisService analysisService;

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
    public ScrmFeedbackDto createFeedback(ScrmFeedbackDto dto) throws ScrmException {
        return managementService.createFeedback(dto);
    }

    /**
     * 更新反馈（字段非空才覆盖）。
     *
     * @param id  反馈 ID
     * @param dto 反馈参数
     * @return 更新后的反馈
     * @throws ScrmException 反馈不存在 / 参数非法
     */
    public ScrmFeedbackDto updateFeedback(Long id, ScrmFeedbackDto dto) throws ScrmException {
        return managementService.updateFeedback(id, dto);
    }

    /**
     * 删除反馈。
     *
     * @param id 反馈 ID
     * @throws ScrmException 反馈不存在
     */
    public void deleteFeedback(Long id) throws ScrmException {
        managementService.deleteFeedback(id);
    }

    /**
     * 查询反馈详情。
     *
     * @param id 反馈 ID
     * @return 反馈 DTO
     * @throws ScrmException 反馈不存在
     */
    public ScrmFeedbackDto getFeedback(Long id) throws ScrmException {
        return managementService.getFeedback(id);
    }

    /**
     * 按反馈编号查询反馈。
     *
     * @param feedbackNo 反馈编号
     * @return 反馈 DTO
     * @throws ScrmException 反馈不存在
     */
    public ScrmFeedbackDto getFeedbackByNo(String feedbackNo) throws ScrmException {
        return managementService.getFeedbackByNo(feedbackNo);
    }

    /**
     * 分页查询反馈。
     *
     * @param queryDto 查询条件
     * @param pageable 分页参数
     * @return 反馈分页结果
     */
    public Page<ScrmFeedbackDto> listFeedbacks(ScrmFeedbackQueryDto queryDto, Pageable pageable) {
        return managementService.listFeedbacks(queryDto, pageable);
    }

    // ============================================================
    // 反馈动作
    // ============================================================

    /**
     * 分配反馈。
     *
     * @param id         反馈 ID
     * @param assigneeId 处理人 ID (可空)
     * @param teamId     处理团队 ID (可空)
     * @return 更新后的反馈
     * @throws ScrmException 反馈不存在 / 参数非法 / 反馈已关闭
     */
    public ScrmFeedbackDto assignFeedback(Long id, String assigneeId, String teamId) throws ScrmException {
        return managementService.assignFeedback(id, assigneeId, teamId);
    }

    /**
     * 变更反馈状态。
     *
     * @param processDto 处理请求 (feedbackId + status + resolution + assigneeId)
     * @return 更新后的反馈
     * @throws ScrmException 反馈不存在 / 状态非法 / 状态流转非法
     */
    public ScrmFeedbackDto changeStatus(ScrmFeedbackProcessDto processDto) throws ScrmException {
        return managementService.changeStatus(processDto);
    }

    /**
     * 变更反馈优先级。
     *
     * @param id       反馈 ID
     * @param priority 目标优先级
     * @return 更新后的反馈
     * @throws ScrmException 反馈不存在 / 优先级非法 / 反馈已关闭
     */
    public ScrmFeedbackDto changePriority(Long id, String priority) throws ScrmException {
        return managementService.changePriority(id, priority);
    }

    /**
     * 解决反馈。
     *
     * @param id         反馈 ID
     * @param resolution 解决方案
     * @return 更新后的反馈
     * @throws ScrmException 反馈不存在 / 状态非法
     */
    public ScrmFeedbackDto resolveFeedback(Long id, String resolution) throws ScrmException {
        return managementService.resolveFeedback(id, resolution);
    }

    /**
     * 关闭反馈。
     *
     * @param id 反馈 ID
     * @return 更新后的反馈
     * @throws ScrmException 反馈不存在 / 状态非法
     */
    public ScrmFeedbackDto closeFeedback(Long id) throws ScrmException {
        return managementService.closeFeedback(id);
    }

    /**
     * 驳回反馈。
     *
     * @param id     反馈 ID
     * @param reason 驳回原因
     * @return 更新后的反馈
     * @throws ScrmException 反馈不存在 / 状态非法
     */
    public ScrmFeedbackDto rejectFeedback(Long id, String reason) throws ScrmException {
        return managementService.rejectFeedback(id, reason);
    }

    /**
     * 标记反馈为重复。
     *
     * @param id                 反馈 ID
     * @param originalFeedbackId 原始反馈 ID
     * @return 更新后的反馈
     * @throws ScrmException 反馈不存在 / 原始反馈不存在 / 参数非法
     */
    public ScrmFeedbackDto markDuplicate(Long id, Long originalFeedbackId) throws ScrmException {
        return managementService.markDuplicate(id, originalFeedbackId);
    }

    /**
     * 合并反馈。
     *
     * @param id       当前反馈 ID
     * @param targetId 目标反馈 ID
     * @return 更新后的当前反馈
     * @throws ScrmException 反馈不存在 / 目标反馈不存在 / 参数非法
     */
    public ScrmFeedbackDto mergeFeedback(Long id, Long targetId) throws ScrmException {
        return managementService.mergeFeedback(id, targetId);
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
    public ScrmFeedbackCommentDto addComment(ScrmFeedbackCommentDto dto) throws ScrmException {
        return commentService.addComment(dto);
    }

    /**
     * 查询反馈评论列表 (按评论发生时间升序)。
     *
     * @param feedbackId 反馈 ID
     * @return 评论列表
     * @throws ScrmException 反馈不存在
     */
    public List<ScrmFeedbackCommentDto> listComments(Long feedbackId) throws ScrmException {
        return commentService.listComments(feedbackId);
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
    public ScrmFeedbackCommentDto addInternalNote(Long feedbackId, String content, String authorId)
            throws ScrmException {
        return commentService.addInternalNote(feedbackId, content, authorId);
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
    public ScrmFeedbackCommentDto replyToComment(Long feedbackId, Long parentCommentId,
                                                  String content, String authorId) throws ScrmException {
        return commentService.replyToComment(feedbackId, parentCommentId, content, authorId);
    }

    /**
     * 删除评论。
     *
     * @param id 评论 ID
     * @throws ScrmException 评论不存在
     */
    public void deleteComment(Long id) throws ScrmException {
        commentService.deleteComment(id);
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
    public ScrmFeedbackCategoryDto createCategory(ScrmFeedbackCategoryDto dto) throws ScrmException {
        return categoryService.createCategory(dto);
    }

    /**
     * 更新反馈分类（字段非空才覆盖）。
     *
     * @param id  分类 ID
     * @param dto 分类参数
     * @return 更新后的分类
     * @throws ScrmException 分类不存在 / 参数非法 / 分类编码重复
     */
    public ScrmFeedbackCategoryDto updateCategory(Long id, ScrmFeedbackCategoryDto dto) throws ScrmException {
        return categoryService.updateCategory(id, dto);
    }

    /**
     * 删除反馈分类。
     *
     * @param id 分类 ID
     * @throws ScrmException 分类不存在
     */
    public void deleteCategory(Long id) throws ScrmException {
        categoryService.deleteCategory(id);
    }

    /**
     * 查询反馈分类详情。
     *
     * @param id 分类 ID
     * @return 分类 DTO
     * @throws ScrmException 分类不存在
     */
    public ScrmFeedbackCategoryDto getCategory(Long id) throws ScrmException {
        return categoryService.getCategory(id);
    }

    /**
     * 按分类编码查询反馈分类。
     *
     * @param code 分类编码
     * @return 分类 DTO
     * @throws ScrmException 分类不存在
     */
    public ScrmFeedbackCategoryDto getCategoryByCode(String code) throws ScrmException {
        return categoryService.getCategoryByCode(code);
    }

    /**
     * 分页查询反馈分类。
     *
     * @param applicableType 适用反馈类型过滤 (可空)
     * @param enabled        启用状态过滤 (可空)
     * @param pageable       分页参数
     * @return 分类分页结果
     */
    public Page<ScrmFeedbackCategoryDto> listCategories(String applicableType, Boolean enabled, Pageable pageable) {
        return categoryService.listCategories(applicableType, enabled, pageable);
    }

    /**
     * 启用反馈分类。
     *
     * @param id 分类 ID
     * @return 更新后的分类
     * @throws ScrmException 分类不存在
     */
    public ScrmFeedbackCategoryDto enableCategory(Long id) throws ScrmException {
        return categoryService.enableCategory(id);
    }

    /**
     * 禁用反馈分类。
     *
     * @param id 分类 ID
     * @return 更新后的分类
     * @throws ScrmException 分类不存在
     */
    public ScrmFeedbackCategoryDto disableCategory(Long id) throws ScrmException {
        return categoryService.disableCategory(id);
    }

    /**
     * 更新分类的反馈计数。
     *
     * @param id 分类 ID
     * @return 更新后的分类
     * @throws ScrmException 分类不存在
     */
    public ScrmFeedbackCategoryDto updateCategoryStats(Long id) throws ScrmException {
        return categoryService.updateCategoryStats(id);
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
    public String analyzeSentiment(String text) {
        return analysisService.analyzeSentiment(text);
    }

    /**
     * 自动分类 (关键词匹配)。
     *
     * @param content 反馈内容
     * @return 反馈类型
     */
    public String autoCategorize(String content) {
        return analysisService.autoCategorize(content);
    }

    /**
     * 提取标签 (关键词匹配)。
     *
     * @param content 反馈内容
     * @return 标签字符串 (逗号分隔), 无命中返回 null
     */
    public String extractTags(String content) {
        return analysisService.extractTags(content);
    }

    /**
     * 生成反馈摘要。
     *
     * @param feedbackId 反馈 ID
     * @return 摘要文本
     * @throws ScrmException 反馈不存在
     */
    public String generateSummary(Long feedbackId) throws ScrmException {
        return analysisService.generateSummary(feedbackId);
    }

    // ============================================================
    // 统计 Stats
    // ============================================================

    /**
     * 反馈统计: 总数 / 各类型 / 各状态 / 平均评分 / 平均解决时长 / 满意度。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果
     */
    public Map<String, Object> getFeedbackStats(LocalDateTime startTime, LocalDateTime endTime) {
        return analysisService.getFeedbackStats(startTime, endTime);
    }

    /**
     * 情感分布统计: 各情感数量与占比。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果
     */
    public Map<String, Object> getSentimentDistribution(LocalDateTime startTime, LocalDateTime endTime) {
        return analysisService.getSentimentDistribution(startTime, endTime);
    }

    /**
     * 分类统计: 各分类数量。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果
     */
    public Map<String, Object> getCategoryStats(LocalDateTime startTime, LocalDateTime endTime) {
        return analysisService.getCategoryStats(startTime, endTime);
    }

    /**
     * 反馈趋势: 按天统计最近 N 天的反馈数量, 支持按反馈类型过滤。
     *
     * @param days         天数 (统计最近 N 天, 含今天)
     * @param feedbackType 反馈类型过滤 (可空)
     * @return 趋势数据 (date + count)
     */
    public List<Map<String, Object>> getTrend(int days, String feedbackType) {
        return analysisService.getTrend(days, feedbackType);
    }

    /**
     * 热点问题: 按分类汇总最近反馈, 返回 Top N。
     *
     * @param limit 返回条数 (默认 10)
     * @return 热点问题列表 (category + count + percentage)
     */
    public List<Map<String, Object>> getTopIssues(int limit) {
        return analysisService.getTopIssues(limit);
    }

    /**
     * 响应时间统计: 平均响应时长与平均解决时长。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果
     */
    public Map<String, Object> getResponseTimeStats(LocalDateTime startTime, LocalDateTime endTime) {
        return analysisService.getResponseTimeStats(startTime, endTime);
    }

    /**
     * SLA 达标率统计。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果 (total / compliant / violation / complianceRate)
     */
    public Map<String, Object> getSlaCompliance(LocalDateTime startTime, LocalDateTime endTime) {
        return analysisService.getSlaCompliance(startTime, endTime);
    }

    // ============================================================
    // 反馈编号生成
    // ============================================================

    /**
     * 生成反馈编号 (FB + 年月日 + 4 位序号)。
     *
     * @return 反馈编号
     */
    public String generateFeedbackNo() {
        return managementService.generateFeedbackNo();
    }
}