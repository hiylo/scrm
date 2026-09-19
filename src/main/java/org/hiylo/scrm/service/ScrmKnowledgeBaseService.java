/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmKnowledgeBaseService.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.dto.ScrmKnowledgeArticleDto;
import org.hiylo.scrm.dto.ScrmKnowledgeCategoryDto;
import org.hiylo.scrm.dto.ScrmKnowledgeFeedbackDto;
import org.hiylo.scrm.dto.ScrmKnowledgeReviewDto;
import org.hiylo.scrm.dto.ScrmKnowledgeSearchDto;
import org.hiylo.scrm.entity.ScrmKnowledgeArticleEntity;
import org.hiylo.scrm.entity.ScrmKnowledgeCategoryEntity;
import org.hiylo.scrm.entity.ScrmKnowledgeFeedbackEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 知识库服务 (门面)。
 * <p>
 * 作为知识库模块的统一入口, 保持对外 public 方法签名不变, 实际能力按子域委托给
 * {@link ScrmKnowledgeCategoryService} (分类管理)、{@link ScrmKnowledgeArticleService} (文章管理)、
 * {@link ScrmKnowledgeVersionService} (版本管理)、{@link ScrmKnowledgeSearchFeedbackService} (搜索与反馈)、
 * {@link ScrmKnowledgeReviewService} (审核) 与 {@link ScrmKnowledgeStatsService} (统计)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
public class ScrmKnowledgeBaseService {

    /** 知识分类管理子域服务 */
    private final ScrmKnowledgeCategoryService categoryService;
    /** 知识文章管理子域服务 */
    private final ScrmKnowledgeArticleService articleService;
    /** 知识版本管理子域服务 */
    private final ScrmKnowledgeVersionService versionService;
    /** 知识搜索与反馈子域服务 */
    private final ScrmKnowledgeSearchFeedbackService searchFeedbackService;
    /** 知识审核子域服务 */
    private final ScrmKnowledgeReviewService reviewService;
    /** 知识统计子域服务 */
    private final ScrmKnowledgeStatsService statsService;

    // ============================================================
    // 分类管理
    // ============================================================

    /**
     * 创建知识分类。
     *
     * @param dto 分类参数
     * @return 创建后的分类
     * @throws ScrmException 参数非法 / 编码重复
     */
    public ScrmKnowledgeCategoryEntity createCategory(ScrmKnowledgeCategoryDto dto) throws ScrmException {
        return categoryService.createCategory(dto);
    }

    /**
     * 更新知识分类。
     *
     * @param id  分类 ID
     * @param dto 分类参数
     * @return 更新后的分类
     * @throws ScrmException 分类不存在 / 参数非法
     */
    public ScrmKnowledgeCategoryEntity updateCategory(Long id, ScrmKnowledgeCategoryDto dto) throws ScrmException {
        return categoryService.updateCategory(id, dto);
    }

    /**
     * 删除知识分类。
     *
     * @param id 分类 ID
     * @throws ScrmException 分类不存在 / 存在子分类 / 存在关联文章
     */
    public void deleteCategory(Long id) throws ScrmException {
        categoryService.deleteCategory(id);
    }

    /**
     * 查询知识分类详情。
     *
     * @param id 分类 ID
     * @return 分类实体
     * @throws ScrmException 分类不存在
     */
    public ScrmKnowledgeCategoryEntity getCategory(Long id) throws ScrmException {
        return categoryService.getCategory(id);
    }

    /**
     * 按编码查询知识分类。
     *
     * @param code 分类编码
     * @return 分类实体
     * @throws ScrmException 分类不存在
     */
    public ScrmKnowledgeCategoryEntity getCategoryByCode(String code) throws ScrmException {
        return categoryService.getCategoryByCode(code);
    }

    /**
     * 分页查询知识分类。
     *
     * @param parentId 父分类 ID 过滤（可空）
     * @param enabled  启用状态过滤（可空）
     * @param keyword  分类名称关键字模糊匹配（可空）
     * @param pageable 分页参数
     * @return 分类分页结果
     */
    public Page<ScrmKnowledgeCategoryEntity> listCategories(Long parentId, Boolean enabled,
                                                             String keyword, Pageable pageable) {
        return categoryService.listCategories(parentId, enabled, keyword, pageable);
    }

    /**
     * 获取分类树。
     *
     * @return 分类树节点列表
     */
    public List<Map<String, Object>> getCategoryTree() {
        return categoryService.getCategoryTree();
    }

    /**
     * 启用知识分类。
     *
     * @param id 分类 ID
     * @return 更新后的分类
     * @throws ScrmException 分类不存在
     */
    public ScrmKnowledgeCategoryEntity enableCategory(Long id) throws ScrmException {
        return categoryService.enableCategory(id);
    }

    /**
     * 禁用知识分类。
     *
     * @param id 分类 ID
     * @return 更新后的分类
     * @throws ScrmException 分类不存在
     */
    public ScrmKnowledgeCategoryEntity disableCategory(Long id) throws ScrmException {
        return categoryService.disableCategory(id);
    }

    /**
     * 移动分类到新的父分类下并设置排序序号。
     *
     * @param id            分类 ID
     * @param newParentId   新父分类 ID (可空)
     * @param newSortOrder  新排序序号 (可空)
     * @return 更新后的分类
     * @throws ScrmException 分类不存在 / 父分类不能为自身
     */
    public ScrmKnowledgeCategoryEntity moveCategory(Long id, Long newParentId, Integer newSortOrder)
            throws ScrmException {
        return categoryService.moveCategory(id, newParentId, newSortOrder);
    }

    /**
     * 更新分类统计。
     *
     * @param id 分类 ID
     * @return 更新后的分类
     * @throws ScrmException 分类不存在
     */
    public ScrmKnowledgeCategoryEntity updateCategoryStats(Long id) throws ScrmException {
        return categoryService.updateCategoryStats(id);
    }

    /**
     * 查询分类下的已发布文章 (分页)。
     *
     * @param categoryId 分类 ID
     * @param pageable   分页参数
     * @return 文章分页结果
     * @throws ScrmException 分类不存在
     */
    public Page<ScrmKnowledgeArticleEntity> getArticlesByCategory(Long categoryId, Pageable pageable)
            throws ScrmException {
        return categoryService.getArticlesByCategory(categoryId, pageable);
    }

    // ============================================================
    // 文章管理
    // ============================================================

    /**
     * 创建知识文章。
     *
     * @param dto 文章参数
     * @return 创建后的文章
     * @throws ScrmException 参数非法 / 编码重复
     */
    public ScrmKnowledgeArticleEntity createArticle(ScrmKnowledgeArticleDto dto) throws ScrmException {
        return articleService.createArticle(dto);
    }

    /**
     * 更新知识文章。
     *
     * @param id  文章 ID
     * @param dto 文章参数
     * @return 更新后的文章
     * @throws ScrmException 文章不存在 / 参数非法 / 状态非法
     */
    public ScrmKnowledgeArticleEntity updateArticle(Long id, ScrmKnowledgeArticleDto dto) throws ScrmException {
        return articleService.updateArticle(id, dto);
    }

    /**
     * 删除知识文章。
     *
     * @param id 文章 ID
     * @throws ScrmException 文章不存在
     */
    public void deleteArticle(Long id) throws ScrmException {
        articleService.deleteArticle(id);
    }

    /**
     * 查询文章详情 (同时增加浏览量)。
     *
     * @param id 文章 ID
     * @return 文章实体
     * @throws ScrmException 文章不存在
     */
    public ScrmKnowledgeArticleEntity getArticle(Long id) throws ScrmException {
        return articleService.getArticle(id);
    }

    /**
     * 按编码查询文章。
     *
     * @param code 文章编码
     * @return 文章实体
     * @throws ScrmException 文章不存在
     */
    public ScrmKnowledgeArticleEntity getArticleByCode(String code) throws ScrmException {
        return articleService.getArticleByCode(code);
    }

    /**
     * 分页查询文章列表。
     *
     * @param queryDto 查询参数
     * @param pageable 分页参数
     * @return 文章分页结果
     */
    public Page<ScrmKnowledgeArticleEntity> listArticles(ScrmKnowledgeSearchDto queryDto, Pageable pageable) {
        return articleService.listArticles(queryDto, pageable);
    }

    /**
     * 发布文章。
     *
     * @param id 文章 ID
     * @return 更新后的文章
     * @throws ScrmException 文章不存在 / 状态非法
     */
    public ScrmKnowledgeArticleEntity publishArticle(Long id) throws ScrmException {
        return articleService.publishArticle(id);
    }

    /**
     * 归档文章。
     *
     * @param id 文章 ID
     * @return 更新后的文章
     * @throws ScrmException 文章不存在 / 状态非法
     */
    public ScrmKnowledgeArticleEntity archiveArticle(Long id) throws ScrmException {
        return articleService.archiveArticle(id);
    }

    /**
     * 设置精选标识。
     *
     * @param id 文章 ID
     * @return 更新后的文章
     * @throws ScrmException 文章不存在
     */
    public ScrmKnowledgeArticleEntity featureArticle(Long id) throws ScrmException {
        return articleService.featureArticle(id);
    }

    /**
     * 设置置顶标识。
     *
     * @param id 文章 ID
     * @return 更新后的文章
     * @throws ScrmException 文章不存在
     */
    public ScrmKnowledgeArticleEntity pinArticle(Long id) throws ScrmException {
        return articleService.pinArticle(id);
    }

    /**
     * 复制文章。
     *
     * @param id      源文章 ID
     * @param newCode 新文章编码
     * @return 复制后的文章
     * @throws ScrmException 源文章不存在 / 新编码已存在
     */
    public ScrmKnowledgeArticleEntity duplicateArticle(Long id, String newCode) throws ScrmException {
        return articleService.duplicateArticle(id, newCode);
    }

    /**
     * 获取精选文章。
     *
     * @param limit 返回条数
     * @return 精选文章列表
     */
    public List<ScrmKnowledgeArticleEntity> getFeaturedArticles(Integer limit) {
        return articleService.getFeaturedArticles(limit);
    }

    /**
     * 获取置顶文章。
     *
     * @return 置顶文章列表
     */
    public List<ScrmKnowledgeArticleEntity> getPinnedArticles() {
        return articleService.getPinnedArticles();
    }

    /**
     * 获取热门文章。
     *
     * @param limit 返回条数
     * @return 热门文章列表
     */
    public List<ScrmKnowledgeArticleEntity> getPopularArticles(Integer limit) {
        return articleService.getPopularArticles(limit);
    }

    /**
     * 获取最新文章。
     *
     * @param limit 返回条数
     * @return 最新文章列表
     */
    public List<ScrmKnowledgeArticleEntity> getRecentArticles(Integer limit) {
        return articleService.getRecentArticles(limit);
    }

    /**
     * 按标签查询已发布文章 (分页)。
     *
     * @param tag      标签
     * @param pageable 分页参数
     * @return 文章分页结果
     */
    public Page<ScrmKnowledgeArticleEntity> getArticlesByTag(String tag, Pageable pageable) {
        return articleService.getArticlesByTag(tag, pageable);
    }

    /**
     * 增加文章浏览量。
     *
     * @param id     文章 ID
     * @param userId 用户 ID
     * @return 更新后的文章
     * @throws ScrmException 文章不存在
     */
    public ScrmKnowledgeArticleEntity incrementViewCount(Long id, String userId) throws ScrmException {
        return articleService.incrementViewCount(id, userId);
    }

    /**
     * 根据反馈类型更新文章统计计数。
     *
     * @param id           文章 ID
     * @param feedbackType 反馈类型
     * @return 更新后的文章
     * @throws ScrmException 文章不存在
     */
    public ScrmKnowledgeArticleEntity updateArticleStats(Long id, String feedbackType) throws ScrmException {
        return articleService.updateArticleStats(id, feedbackType);
    }

    // ============================================================
    // 版本管理
    // ============================================================

    /**
     * 创建文章版本快照。
     *
     * @param articleId 文章 ID
     * @param changeLog 变更日志 (可空)
     * @return 创建的版本快照
     * @throws ScrmException 文章不存在
     */
    public ScrmKnowledgeArticleEntity createVersion(Long articleId, String changeLog) throws ScrmException {
        return versionService.createVersion(articleId, changeLog);
    }

    /**
     * 查询文章的全部版本。
     *
     * @param articleId 文章 ID
     * @param pageable  分页参数
     * @return 版本分页结果
     * @throws ScrmException 文章不存在
     */
    public Page<ScrmKnowledgeArticleEntity> getVersions(Long articleId, Pageable pageable) throws ScrmException {
        return versionService.getVersions(articleId, pageable);
    }

    /**
     * 查询版本详情。
     *
     * @param id 版本 ID
     * @return 版本实体
     * @throws ScrmException 版本不存在
     */
    public ScrmKnowledgeArticleEntity getVersion(Long id) throws ScrmException {
        return versionService.getVersion(id);
    }

    /**
     * 激活指定版本。
     *
     * @param versionId 版本 ID
     * @return 主文章
     * @throws ScrmException 版本不存在
     */
    public ScrmKnowledgeArticleEntity activateVersion(Long versionId) throws ScrmException {
        return versionService.activateVersion(versionId);
    }

    /**
     * 回滚到指定版本。
     *
     * @param articleId 主文章 ID
     * @param versionId 版本 ID
     * @return 回滚后的主文章
     * @throws ScrmException 文章 / 版本不存在
     */
    public ScrmKnowledgeArticleEntity rollbackToVersion(Long articleId, Long versionId) throws ScrmException {
        return versionService.rollbackToVersion(articleId, versionId);
    }

    /**
     * 对比两个版本。
     *
     * @param v1Id 版本 1 ID
     * @param v2Id 版本 2 ID
     * @return 差异 Map
     * @throws ScrmException 版本不存在
     */
    public Map<String, Object> compareVersions(Long v1Id, Long v2Id) throws ScrmException {
        return versionService.compareVersions(v1Id, v2Id);
    }

    // ============================================================
    // 搜索
    // ============================================================

    /**
     * 搜索知识文章。
     *
     * @param queryDto 查询参数
     * @return 搜索结果分页
     */
    public Page<ScrmKnowledgeArticleEntity> search(ScrmKnowledgeSearchDto queryDto) {
        return searchFeedbackService.search(queryDto);
    }

    /**
     * 自动补全建议。
     *
     * @param prefix 标题前缀
     * @return 建议标题列表
     */
    public List<String> autoComplete(String prefix) {
        return searchFeedbackService.autoComplete(prefix);
    }

    /**
     * 搜索建议。
     *
     * @param keyword 关键词
     * @return 建议列表
     */
    public List<Map<String, Object>> getSearchSuggestions(String keyword) {
        return searchFeedbackService.getSearchSuggestions(keyword);
    }

    /**
     * 热门搜索词。
     *
     * @param limit 返回条数
     * @return 热门标签列表
     */
    public List<String> getPopularKeywords(Integer limit) {
        return searchFeedbackService.getPopularKeywords(limit);
    }

    /**
     * 相关文章推荐。
     *
     * @param articleId 文章 ID
     * @param limit     返回条数
     * @return 相关文章列表
     * @throws ScrmException 文章不存在
     */
    public List<ScrmKnowledgeArticleEntity> getRelatedArticles(
            Long articleId, Integer limit) throws ScrmException {
        return searchFeedbackService.getRelatedArticles(articleId, limit);
    }

    // ============================================================
    // 反馈
    // ============================================================

    /**
     * 添加知识反馈。
     *
     * @param dto 反馈参数
     * @return 创建后的反馈
     * @throws ScrmException 参数非法 / 文章不存在
     */
    public ScrmKnowledgeFeedbackEntity addFeedback(ScrmKnowledgeFeedbackDto dto) throws ScrmException {
        return searchFeedbackService.addFeedback(dto);
    }

    /**
     * 查询反馈详情。
     *
     * @param id 反馈 ID
     * @return 反馈实体
     * @throws ScrmException 反馈不存在
     */
    public ScrmKnowledgeFeedbackEntity getFeedback(Long id) throws ScrmException {
        return searchFeedbackService.getFeedback(id);
    }

    /**
     * 分页查询反馈列表。
     *
     * @param articleId    文章 ID 过滤 (可空)
     * @param feedbackType 反馈类型过滤 (可空)
     * @param status       状态过滤 (可空)
     * @param pageable     分页参数
     * @return 反馈分页结果
     */
    public Page<ScrmKnowledgeFeedbackEntity> listFeedback(Long articleId, String feedbackType,
                                                           String status, Pageable pageable) {
        return searchFeedbackService.listFeedback(articleId, feedbackType, status, pageable);
    }

    /**
     * 解决反馈。
     *
     * @param id   反馈 ID
     * @param note 解决备注
     * @return 更新后的反馈
     * @throws ScrmException 反馈不存在
     */
    public ScrmKnowledgeFeedbackEntity resolveFeedback(Long id, String note) throws ScrmException {
        return searchFeedbackService.resolveFeedback(id, note);
    }

    /**
     * 忽略反馈。
     *
     * @param id   反馈 ID
     * @param note 忽略备注
     * @return 更新后的反馈
     * @throws ScrmException 反馈不存在
     */
    public ScrmKnowledgeFeedbackEntity dismissFeedback(Long id, String note) throws ScrmException {
        return searchFeedbackService.dismissFeedback(id, note);
    }

    /**
     * 获取文章评论列表。
     *
     * @param articleId 文章 ID
     * @param pageable  分页参数
     * @return 评论分页结果
     */
    public Page<ScrmKnowledgeFeedbackEntity> getComments(Long articleId, Pageable pageable) {
        return searchFeedbackService.getComments(articleId, pageable);
    }

    /**
     * 回复评论。
     *
     * @param feedbackId 父评论 ID
     * @param content    回复内容
     * @return 创建后的回复
     * @throws ScrmException 父评论不存在
     */
    public ScrmKnowledgeFeedbackEntity replyToComment(Long feedbackId, String content) throws ScrmException {
        return searchFeedbackService.replyToComment(feedbackId, content);
    }

    /**
     * 评分。
     *
     * @param articleId 文章 ID
     * @param userId    用户 ID
     * @param rating    评分 1-5
     * @return 创建后的评分反馈
     * @throws ScrmException 文章不存在 / 评分非法
     */
    public ScrmKnowledgeFeedbackEntity rateArticle(Long articleId, String userId, Integer rating)
            throws ScrmException {
        return searchFeedbackService.rateArticle(articleId, userId, rating);
    }

    /**
     * 获取用户对文章的评分。
     *
     * @param articleId 文章 ID
     * @param userId    用户 ID
     * @return 评分反馈实体 (可选, 不存在返回 null)
     * @throws ScrmException 文章不存在
     */
    public ScrmKnowledgeFeedbackEntity getArticleRating(Long articleId, String userId) throws ScrmException {
        return searchFeedbackService.getArticleRating(articleId, userId);
    }

    // ============================================================
    // 审核
    // ============================================================

    /**
     * 提交审核。
     *
     * @param articleId 文章 ID
     * @return 更新后的文章
     * @throws ScrmException 文章不存在 / 状态非法
     */
    public ScrmKnowledgeArticleEntity submitForReview(Long articleId) throws ScrmException {
        return reviewService.submitForReview(articleId);
    }

    /**
     * 审核文章。
     *
     * @param reviewDto 审核参数
     * @return 更新后的文章
     * @throws ScrmException 文章不存在 / 状态非法
     */
    public ScrmKnowledgeArticleEntity review(ScrmKnowledgeReviewDto reviewDto) throws ScrmException {
        return reviewService.review(reviewDto);
    }

    /**
     * 批量审核文章。
     *
     * @param articleIds 文章 ID 列表
     * @param action     审核动作 APPROVE / REJECT
     * @param comment    审核意见
     * @return 各文章审核结果
     */
    public List<Map<String, Object>> batchReview(List<Long> articleIds, String action, String comment) {
        return reviewService.batchReview(articleIds, action, comment);
    }

    /**
     * 获取待审核文章。
     *
     * @param pageable 分页参数
     * @return 待审核文章分页结果
     */
    public Page<ScrmKnowledgeArticleEntity> getPendingReviews(Pageable pageable) {
        return reviewService.getPendingReviews(pageable);
    }

    /**
     * 获取文章的审核历史。
     *
     * @param articleId 文章 ID
     * @param pageable  分页参数
     * @return 版本分页结果
     * @throws ScrmException 文章不存在
     */
    public Page<ScrmKnowledgeArticleEntity> getReviewHistory(
            Long articleId, Pageable pageable) throws ScrmException {
        return reviewService.getReviewHistory(articleId, pageable);
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 知识库统计概览。
     *
     * @return 统计结果 Map
     */
    public Map<String, Object> getKnowledgeStats() {
        return statsService.getKnowledgeStats();
    }

    /**
     * 分类统计。
     *
     * @return 分类统计列表
     */
    public List<Map<String, Object>> getCategoryStats() {
        return statsService.getCategoryStats();
    }

    /**
     * 单文章统计详情。
     *
     * @param articleId 文章 ID
     * @return 统计详情 Map
     * @throws ScrmException 文章不存在
     */
    public Map<String, Object> getArticleStats(Long articleId) throws ScrmException {
        return statsService.getArticleStats(articleId);
    }

    /**
     * 搜索统计。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果 Map
     */
    public Map<String, Object> getSearchStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getSearchStats(startTime, endTime);
    }

    /**
     * 反馈统计。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果 Map
     */
    public Map<String, Object> getFeedbackStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getFeedbackStats(startTime, endTime);
    }

    /**
     * 贡献统计。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 贡献统计列表
     */
    public List<Map<String, Object>> getContributionStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getContributionStats(startTime, endTime);
    }

    /**
     * 趋势统计。
     *
     * @param days 统计天数
     * @return 趋势列表
     */
    public List<Map<String, Object>> getTrend(Integer days) {
        return statsService.getTrend(days);
    }
}