/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmKnowledgeArticleService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmKnowledgeArticleDto;
import org.hiylo.scrm.dto.ScrmKnowledgeSearchDto;
import org.hiylo.scrm.entity.ScrmKnowledgeArticleEntity;
import org.hiylo.scrm.entity.ScrmKnowledgeCategoryEntity;
import org.hiylo.scrm.entity.ScrmKnowledgeFeedbackEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmKnowledgeArticleRepository;
import org.hiylo.scrm.repository.ScrmKnowledgeFeedbackRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * SCRM 知识文章管理服务。
 * <p>
 * 承载知识库文章子域: 文章 CRUD / 发布 / 归档 / 精选 / 置顶 / 复制 / 浏览计数 / 互动计数更新,
 * 以及文章检索内部实现 (关键词匹配 / 相关度排序 / 自动补全依赖的查询)。同时托管文章共享常量
 * (状态 / 审核状态 / 排序方式) 与按主键查找文章, 供版本 / 搜索反馈 / 审核 / 统计兄弟类
 * 以 package 级访问复用。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmKnowledgeArticleService {

    // ==================== 文章状态 (共享) ====================

    /** 文章状态: 草稿 */
    static final String STATUS_DRAFT = "DRAFT";
    /** 文章状态: 待审核 */
    static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    /** 文章状态: 已归档 */
    static final String STATUS_ARCHIVED = "ARCHIVED";
    /** 文章状态: 已驳回 */
    static final String STATUS_REJECTED = "REJECTED";

    // ==================== 审核状态 (共享) ====================

    /** 审核状态: 待审核 */
    static final String REVIEW_PENDING = "PENDING";
    /** 审核状态: 已通过 */
    static final String REVIEW_APPROVED = "APPROVED";
    /** 审核状态: 已驳回 */
    static final String REVIEW_REJECTED = "REJECTED";

    /** 内容类型: Markdown (默认) */
    private static final String CONTENT_TYPE_MARKDOWN = "MARKDOWN";
    /** 文章类型: ARTICLE (默认) */
    private static final String ARTICLE_TYPE_ARTICLE = "ARTICLE";
    /** 难度: 入门 (默认) */
    private static final String DIFFICULTY_BEGINNER = "BEGINNER";

    /** 排序: 相关度 */
    private static final String SORT_RELEVANCE = "RELEVANCE";
    /** 排序: 浏览量 */
    private static final String SORT_VIEW_COUNT = "VIEW_COUNT";
    /** 排序: 点赞数 */
    private static final String SORT_LIKE_COUNT = "LIKE_COUNT";
    /** 排序: 评分 */
    private static final String SORT_RATING = "RATING";
    /** 排序: 创建时间 */
    private static final String SORT_CREATE_TIME = "CREATE_TIME";

    /** 复制文章标题后缀 */
    private static final String COPY_SUFFIX = "-副本";

    /** 搜索相关结果上限 (内存评分时加载的最大条数) */
    private static final int SEARCH_RELEVANCE_LIMIT = 200;

    /** 知识文章数据访问层 */
    private final ScrmKnowledgeArticleRepository articleRepository;

    /** 知识反馈数据访问层 (删除文章时清理关联反馈) */
    private final ScrmKnowledgeFeedbackRepository feedbackRepository;

    /** 知识分类管理服务 (分类查找 / 分类统计刷新) */
    private final ScrmKnowledgeCategoryService categoryService;

    // ============================================================
    // 文章管理
    // ============================================================

    /**
     * 创建知识文章 (默认状态 DRAFT, 互动计数初始化为 0)。
     *
     * @param dto 文章参数
     * @return 创建后的文章
     * @throws ScrmException 参数非法 / 编码重复
     */
    @Transactional
    public ScrmKnowledgeArticleEntity createArticle(ScrmKnowledgeArticleDto dto) throws ScrmException {
        validateArticleDto(dto, false);
        if (articleRepository.existsByArticleCode(dto.getArticleCode())) {
            throw ScrmException.conflict("文章编码已存在: " + dto.getArticleCode());
        }
        ScrmKnowledgeArticleEntity entity = new ScrmKnowledgeArticleEntity();
        entity.setTitle(dto.getTitle());
        entity.setArticleCode(dto.getArticleCode());
        entity.setCategoryId(dto.getCategoryId());
        entity.setSummary(dto.getSummary());
        entity.setContent(dto.getContent());
        entity.setContentType(dto.getContentType() != null ? dto.getContentType() : CONTENT_TYPE_MARKDOWN);
        entity.setArticleType(dto.getArticleType() != null ? dto.getArticleType() : ARTICLE_TYPE_ARTICLE);
        entity.setTags(dto.getTags());
        entity.setKeywords(dto.getKeywords());
        entity.setCoverImage(dto.getCoverImage());
        entity.setAttachments(dto.getAttachments());
        entity.setRelatedArticles(dto.getRelatedArticles());
        entity.setRelatedProducts(dto.getRelatedProducts());
        entity.setApplicableScenarios(dto.getApplicableScenarios());
        entity.setDifficultyLevel(dto.getDifficultyLevel() != null ? dto.getDifficultyLevel() : DIFFICULTY_BEGINNER);
        entity.setReadingTimeMinutes(dto.getReadingTimeMinutes() != null ? dto.getReadingTimeMinutes() : 5);
        entity.setStatus(STATUS_DRAFT);
        entity.setVersionNumber(1);
        entity.setReviewStatus(REVIEW_PENDING);
        entity.setAuthorId(dto.getAuthorId());
        entity.setAuthorName(dto.getAuthorName());
        entity.setIsFeatured(Boolean.FALSE);
        entity.setIsPinned(Boolean.FALSE);
        entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        entity.setCreatedBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : ScrmKnowledgeCategoryService.currentOperator());
        // 回填分类名称
        populateCategoryName(entity, dto.getCategoryId());
        entity = articleRepository.save(entity);
        log.info("创建知识文章: id={}, code={}, title={}", entity.getId(), entity.getArticleCode(), entity.getTitle());
        return entity;
    }

    /**
     * 更新知识文章 (字段非空才覆盖)。已发布 / 已归档文章禁止更新核心字段。
     *
     * @param id  文章 ID
     * @param dto 文章参数
     * @return 更新后的文章
     * @throws ScrmException 文章不存在 / 参数非法 / 状态非法
     */
    @Transactional
    public ScrmKnowledgeArticleEntity updateArticle(Long id, ScrmKnowledgeArticleDto dto) throws ScrmException {
        ScrmKnowledgeArticleEntity entity = findArticleOrThrow(id);
        if (ScrmKnowledgeCategoryService.STATUS_PUBLISHED.equals(entity.getStatus())
                || STATUS_ARCHIVED.equals(entity.getStatus())) {
            throw ScrmException.conflict("已发布 / 已归档文章不可修改: id=" + id + ", status=" + entity.getStatus());
        }
        validateArticleDto(dto, true);
        if (dto.getTitle() != null) entity.setTitle(dto.getTitle());
        if (dto.getCategoryId() != null) {
            entity.setCategoryId(dto.getCategoryId());
            populateCategoryName(entity, dto.getCategoryId());
        }
        if (dto.getSummary() != null) entity.setSummary(dto.getSummary());
        if (dto.getContent() != null) entity.setContent(dto.getContent());
        if (dto.getContentType() != null) entity.setContentType(dto.getContentType());
        if (dto.getArticleType() != null) entity.setArticleType(dto.getArticleType());
        if (dto.getTags() != null) entity.setTags(dto.getTags());
        if (dto.getKeywords() != null) entity.setKeywords(dto.getKeywords());
        if (dto.getCoverImage() != null) entity.setCoverImage(dto.getCoverImage());
        if (dto.getAttachments() != null) entity.setAttachments(dto.getAttachments());
        if (dto.getRelatedArticles() != null) entity.setRelatedArticles(dto.getRelatedArticles());
        if (dto.getRelatedProducts() != null) entity.setRelatedProducts(dto.getRelatedProducts());
        if (dto.getApplicableScenarios() != null) entity.setApplicableScenarios(dto.getApplicableScenarios());
        if (dto.getDifficultyLevel() != null) entity.setDifficultyLevel(dto.getDifficultyLevel());
        if (dto.getReadingTimeMinutes() != null) entity.setReadingTimeMinutes(dto.getReadingTimeMinutes());
        if (dto.getAuthorId() != null) entity.setAuthorId(dto.getAuthorId());
        if (dto.getAuthorName() != null) entity.setAuthorName(dto.getAuthorName());
        if (dto.getSortOrder() != null) entity.setSortOrder(dto.getSortOrder());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity.setLastModifiedAt(LocalDateTime.now());
        entity = articleRepository.save(entity);
        log.info("更新知识文章: id={}, title={}", entity.getId(), entity.getTitle());
        return entity;
    }

    /**
     * 删除知识文章 (同时清理关联反馈)。
     *
     * @param id 文章 ID
     * @throws ScrmException 文章不存在
     */
    @Transactional
    public void deleteArticle(Long id) throws ScrmException {
        ScrmKnowledgeArticleEntity entity = findArticleOrThrow(id);
        Specification<ScrmKnowledgeFeedbackEntity> feedbackSpec = (root, query, cb) -> cb.and(
                cb.equal(root.get("articleId"), id));
        List<ScrmKnowledgeFeedbackEntity> feedbacks = feedbackRepository.findAll(feedbackSpec);
        if (!feedbacks.isEmpty()) {
            feedbackRepository.deleteAll(feedbacks);
        }
        articleRepository.delete(entity);
        log.info("删除知识文章: id={}, title={}, feedbacks={}", id, entity.getTitle(), feedbacks.size());
    }

    /**
     * 查询文章详情 (同时增加浏览量)。
     *
     * @param id 文章 ID
     * @return 文章实体
     * @throws ScrmException 文章不存在
     */
    @Transactional
    public ScrmKnowledgeArticleEntity getArticle(Long id) throws ScrmException {
        ScrmKnowledgeArticleEntity entity = findArticleOrThrow(id);
        entity.setViewCount(ScrmKnowledgeCategoryService.safeInt(entity.getViewCount()) + 1);
        entity = articleRepository.save(entity);
        // 同步分类浏览量 (异步场景下简化为直接更新)
        if (entity.getCategoryId() != null) {
            try {
                categoryService.updateCategoryStats(entity.getCategoryId());
            } catch (ScrmException e) {
                log.warn("同步分类浏览量失败: categoryId={}", entity.getCategoryId());
            }
        }
        return entity;
    }

    /**
     * 按编码查询文章 (不增加浏览量)。
     *
     * @param code 文章编码
     * @return 文章实体
     * @throws ScrmException 文章不存在
     */
    @Transactional(readOnly = true)
    public ScrmKnowledgeArticleEntity getArticleByCode(String code) throws ScrmException {
        return articleRepository.findByArticleCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "知识文章不存在: code=" + code));
    }

    /**
     * 分页查询文章列表, 支持按关键词 / 分类 / 类型 / 标签 / 难度过滤。
     *
     * @param queryDto 查询参数
     * @param pageable 分页参数
     * @return 文章分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmKnowledgeArticleEntity> listArticles(ScrmKnowledgeSearchDto queryDto, Pageable pageable) {
        return searchInternal(queryDto, pageable);
    }

    /**
     * 发布文章 (DRAFT / REJECTED → PUBLISHED, 记录发布时间)。
     *
     * @param id 文章 ID
     * @return 更新后的文章
     * @throws ScrmException 文章不存在 / 状态非法
     */
    @Transactional
    public ScrmKnowledgeArticleEntity publishArticle(Long id) throws ScrmException {
        ScrmKnowledgeArticleEntity entity = findArticleOrThrow(id);
        if (ScrmKnowledgeCategoryService.STATUS_PUBLISHED.equals(entity.getStatus())) {
            return entity;
        }
        if (!STATUS_DRAFT.equals(entity.getStatus()) && !STATUS_REJECTED.equals(entity.getStatus())
                && !STATUS_ARCHIVED.equals(entity.getStatus())) {
            throw ScrmException.conflict("仅 DRAFT / REJECTED / ARCHIVED 状态可发布: id=" + id
                    + ", status=" + entity.getStatus());
        }
        entity.setStatus(ScrmKnowledgeCategoryService.STATUS_PUBLISHED);
        entity.setPublishedAt(LocalDateTime.now());
        entity.setReviewStatus(REVIEW_APPROVED);
        entity = articleRepository.save(entity);
        if (entity.getCategoryId() != null) {
            try {
                categoryService.updateCategoryStats(entity.getCategoryId());
            } catch (ScrmException e) {
                log.warn("发布后同步分类统计失败: categoryId={}", entity.getCategoryId());
            }
        }
        log.info("发布知识文章: id={}, title={}", id, entity.getTitle());
        return entity;
    }

    /**
     * 归档文章 (PUBLISHED → ARCHIVED)。
     *
     * @param id 文章 ID
     * @return 更新后的文章
     * @throws ScrmException 文章不存在 / 状态非法
     */
    @Transactional
    public ScrmKnowledgeArticleEntity archiveArticle(Long id) throws ScrmException {
        ScrmKnowledgeArticleEntity entity = findArticleOrThrow(id);
        if (!ScrmKnowledgeCategoryService.STATUS_PUBLISHED.equals(entity.getStatus())) {
            throw ScrmException.conflict("仅 PUBLISHED 状态可归档: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setStatus(STATUS_ARCHIVED);
        entity = articleRepository.save(entity);
        if (entity.getCategoryId() != null) {
            try {
                categoryService.updateCategoryStats(entity.getCategoryId());
            } catch (ScrmException e) {
                log.warn("归档后同步分类统计失败: categoryId={}", entity.getCategoryId());
            }
        }
        log.info("归档知识文章: id={}, title={}", id, entity.getTitle());
        return entity;
    }

    /**
     * 设置精选标识 (切换为精选)。
     *
     * @param id 文章 ID
     * @return 更新后的文章
     * @throws ScrmException 文章不存在
     */
    @Transactional
    public ScrmKnowledgeArticleEntity featureArticle(Long id) throws ScrmException {
        ScrmKnowledgeArticleEntity entity = findArticleOrThrow(id);
        entity.setIsFeatured(Boolean.TRUE);
        return articleRepository.save(entity);
    }

    /**
     * 设置置顶标识 (切换为置顶)。
     *
     * @param id 文章 ID
     * @return 更新后的文章
     * @throws ScrmException 文章不存在
     */
    @Transactional
    public ScrmKnowledgeArticleEntity pinArticle(Long id) throws ScrmException {
        ScrmKnowledgeArticleEntity entity = findArticleOrThrow(id);
        entity.setIsPinned(Boolean.TRUE);
        return articleRepository.save(entity);
    }

    /**
     * 复制文章 (创建副本, 编码使用 newCode, 状态置 DRAFT, 计数清零)。
     *
     * @param id      源文章 ID
     * @param newCode 新文章编码
     * @return 复制后的文章
     * @throws ScrmException 源文章不存在 / 新编码已存在
     */
    @Transactional
    public ScrmKnowledgeArticleEntity duplicateArticle(Long id, String newCode) throws ScrmException {
        if (newCode == null || newCode.isBlank()) {
            throw ScrmException.badRequest("新文章编码不能为空");
        }
        if (articleRepository.existsByArticleCode(newCode)) {
            throw ScrmException.conflict("文章编码已存在: " + newCode);
        }
        ScrmKnowledgeArticleEntity source = findArticleOrThrow(id);
        ScrmKnowledgeArticleEntity copy = new ScrmKnowledgeArticleEntity();
        copy.setTitle(source.getTitle() + COPY_SUFFIX);
        copy.setArticleCode(newCode);
        copy.setCategoryId(source.getCategoryId());
        copy.setCategoryName(source.getCategoryName());
        copy.setSummary(source.getSummary());
        copy.setContent(source.getContent());
        copy.setContentType(source.getContentType());
        copy.setArticleType(source.getArticleType());
        copy.setTags(source.getTags());
        copy.setKeywords(source.getKeywords());
        copy.setCoverImage(source.getCoverImage());
        copy.setAttachments(source.getAttachments());
        copy.setRelatedArticles(source.getRelatedArticles());
        copy.setRelatedProducts(source.getRelatedProducts());
        copy.setApplicableScenarios(source.getApplicableScenarios());
        copy.setDifficultyLevel(source.getDifficultyLevel());
        copy.setReadingTimeMinutes(source.getReadingTimeMinutes());
        copy.setStatus(STATUS_DRAFT);
        copy.setVersionNumber(1);
        copy.setReviewStatus(REVIEW_PENDING);
        copy.setAuthorId(source.getAuthorId());
        copy.setAuthorName(source.getAuthorName());
        copy.setIsFeatured(Boolean.FALSE);
        copy.setIsPinned(Boolean.FALSE);
        copy.setSortOrder(source.getSortOrder());
        copy.setCreatedBy(ScrmKnowledgeCategoryService.currentOperator());
        copy = articleRepository.save(copy);
        log.info("复制知识文章: sourceId={}, copyId={}, newCode={}", id, copy.getId(), newCode);
        return copy;
    }

    /**
     * 获取精选文章 (按 sortOrder ASC, createTime DESC)。
     *
     * @param limit 返回条数
     * @return 精选文章列表
     */
    @Transactional(readOnly = true)
    public List<ScrmKnowledgeArticleEntity> getFeaturedArticles(Integer limit) {
        int size = limit != null && limit > 0 ? limit : 10;
        PageRequest pageable = PageRequest.of(0, size);
        return articleRepository
                .findByIsFeaturedAndStatusOrderBySortOrderAscCreateTimeDesc(
                         Boolean.TRUE, ScrmKnowledgeCategoryService.STATUS_PUBLISHED, pageable)
                .getContent();
    }

    /**
     * 获取置顶文章。
     *
     * @return 置顶文章列表
     */
    @Transactional(readOnly = true)
    public List<ScrmKnowledgeArticleEntity> getPinnedArticles() {
        return articleRepository.findByIsPinnedAndStatusOrderBySortOrderAscCreateTimeDesc(
                 Boolean.TRUE, ScrmKnowledgeCategoryService.STATUS_PUBLISHED);
    }

    /**
     * 获取热门文章 (按 viewCount DESC)。
     *
     * @param limit 返回条数
     * @return 热门文章列表
     */
    @Transactional(readOnly = true)
    public List<ScrmKnowledgeArticleEntity> getPopularArticles(Integer limit) {
        int size = limit != null && limit > 0 ? limit : 10;
        PageRequest pageable = PageRequest.of(0, size);
        return articleRepository.findByStatusOrderByViewCountDesc(
                ScrmKnowledgeCategoryService.STATUS_PUBLISHED, pageable)
                .getContent();
    }

    /**
     * 获取最新文章 (按 createTime DESC)。
     *
     * @param limit 返回条数
     * @return 最新文章列表
     */
    @Transactional(readOnly = true)
    public List<ScrmKnowledgeArticleEntity> getRecentArticles(Integer limit) {
        int size = limit != null && limit > 0 ? limit : 10;
        PageRequest pageable = PageRequest.of(0, size);
        return articleRepository.findByStatusOrderByCreateTimeDesc(
                ScrmKnowledgeCategoryService.STATUS_PUBLISHED, pageable)
                .getContent();
    }

    /**
     * 按标签查询已发布文章 (分页)。
     *
     * @param tag      标签
     * @param pageable 分页参数
     * @return 文章分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmKnowledgeArticleEntity> getArticlesByTag(String tag, Pageable pageable) {
        String tagPattern = "%" + tag + "%";
        Specification<ScrmKnowledgeArticleEntity> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), ScrmKnowledgeCategoryService.STATUS_PUBLISHED),
                cb.like(root.get("tags"), tagPattern));
        return articleRepository.findAll(spec, pageable);
    }

    /**
     * 增加文章浏览量 (去重, 简化实现: 总浏览量始终 +1, 独立浏览量亦 +1)。
     *
     * @param id     文章 ID
     * @param userId 用户 ID
     * @return 更新后的文章
     * @throws ScrmException 文章不存在
     */
    @Transactional
    public ScrmKnowledgeArticleEntity incrementViewCount(Long id, String userId) throws ScrmException {
        ScrmKnowledgeArticleEntity entity = findArticleOrThrow(id);
        entity.setViewCount(ScrmKnowledgeCategoryService.safeInt(entity.getViewCount()) + 1);
        // 简化去重: 独立浏览量始终 +1 (完整去重需独立浏览日志表, 此处仅做近似统计)
        entity.setUniqueViewCount(ScrmKnowledgeCategoryService.safeInt(entity.getUniqueViewCount()) + 1);
        return articleRepository.save(entity);
    }

    /**
     * 根据反馈类型更新文章统计计数。
     *
     * @param id           文章 ID
     * @param feedbackType 反馈类型
     * @return 更新后的文章
     * @throws ScrmException 文章不存在
     */
    @Transactional
    public ScrmKnowledgeArticleEntity updateArticleStats(Long id, String feedbackType) throws ScrmException {
        ScrmKnowledgeArticleEntity entity = findArticleOrThrow(id);
        if (feedbackType == null) {
            return entity;
        }
        switch (feedbackType) {
            case ScrmKnowledgeSearchFeedbackService.FEEDBACK_LIKE ->
                    entity.setLikeCount(ScrmKnowledgeCategoryService.safeInt(entity.getLikeCount()) + 1);
            case ScrmKnowledgeSearchFeedbackService.FEEDBACK_DISLIKE ->
                    entity.setDislikeCount(ScrmKnowledgeCategoryService.safeInt(entity.getDislikeCount()) + 1);
            case ScrmKnowledgeSearchFeedbackService.FEEDBACK_FAVORITE ->
                    entity.setFavoriteCount(ScrmKnowledgeCategoryService.safeInt(entity.getFavoriteCount()) + 1);
            case ScrmKnowledgeSearchFeedbackService.FEEDBACK_SHARE ->
                    entity.setShareCount(ScrmKnowledgeCategoryService.safeInt(entity.getShareCount()) + 1);
            case ScrmKnowledgeSearchFeedbackService.FEEDBACK_COMMENT ->
                    entity.setCommentCount(ScrmKnowledgeCategoryService.safeInt(entity.getCommentCount()) + 1);
            case ScrmKnowledgeSearchFeedbackService.FEEDBACK_HELPFUL -> {
                entity.setHelpfulCount(ScrmKnowledgeCategoryService.safeInt(entity.getHelpfulCount()) + 1);
                recalcHelpfulRate(entity);
            }
            case ScrmKnowledgeSearchFeedbackService.FEEDBACK_NOT_HELPFUL -> {
                entity.setNotHelpfulCount(ScrmKnowledgeCategoryService.safeInt(entity.getNotHelpfulCount()) + 1);
                recalcHelpfulRate(entity);
            }
            default -> {
                // 其他类型不更新统计
            }
        }
        return articleRepository.save(entity);
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 校验文章参数。
     *
     * @param dto     文章参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateArticleDto(ScrmKnowledgeArticleDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("文章参数不能为空");
        }
        if (dto.getTitle() != null) {
            if (dto.getTitle().isBlank()) {
                throw ScrmException.badRequest("文章标题不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("文章标题不能为空");
        }
        if (dto.getArticleCode() != null) {
            if (dto.getArticleCode().isBlank()) {
                throw ScrmException.badRequest("文章编码不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("文章编码不能为空");
        }
        if (dto.getContent() != null) {
            if (dto.getContent().isBlank()) {
                throw ScrmException.badRequest("文章内容不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("文章内容不能为空");
        }
    }

    /**
     * 回填分类名称到文章实体。
     *
     * @param entity     文章实体
     * @param categoryId 分类 ID
     */
    private void populateCategoryName(ScrmKnowledgeArticleEntity entity, Long categoryId) {
        if (categoryId == null) {
            entity.setCategoryName(null);
            return;
        }
        try {
            ScrmKnowledgeCategoryEntity category = categoryService.findCategoryOrThrow(categoryId);
            entity.setCategoryName(category.getCategoryName());
        } catch (ScrmException e) {
            log.warn("分类不存在, 跳过回填分类名称: categoryId={}", categoryId);
        }
    }

    /**
     * 重新计算文章有用率。
     *
     * @param entity 文章实体
     */
    private void recalcHelpfulRate(ScrmKnowledgeArticleEntity entity) {
        int helpful = ScrmKnowledgeCategoryService.safeInt(entity.getHelpfulCount());
        int notHelpful = ScrmKnowledgeCategoryService.safeInt(entity.getNotHelpfulCount());
        int total = helpful + notHelpful;
        entity.setHelpfulRate(total > 0 ? (double) helpful / total : 0.0);
    }

    /**
     * 按主键查询文章, 不存在则抛异常。
     *
     * @param id 文章 ID
     * @return 文章实体
     * @throws ScrmException 文章不存在
     */
    ScrmKnowledgeArticleEntity findArticleOrThrow(Long id) throws ScrmException {
        ScrmKnowledgeArticleEntity entity = articleRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "知识文章不存在: id=" + id));
        return entity;
    }

    /**
     * 搜索内部实现 (关键词匹配 + 相关度排序)。
     *
     * @param queryDto 查询参数
     * @param pageable 分页参数
     * @return 文章分页结果
     */
    Page<ScrmKnowledgeArticleEntity> searchInternal(ScrmKnowledgeSearchDto queryDto, Pageable pageable) {
        final ScrmKnowledgeSearchDto effectiveQueryDto = queryDto != null ? queryDto : new ScrmKnowledgeSearchDto();
        final String keyword = effectiveQueryDto.getKeyword();
        final String sortBy = effectiveQueryDto.getSortBy() != null ? effectiveQueryDto.getSortBy() : SORT_RELEVANCE;
        final String status = effectiveQueryDto.getStatus() != null && !effectiveQueryDto.getStatus().isBlank()
                ? effectiveQueryDto.getStatus() : ScrmKnowledgeCategoryService.STATUS_PUBLISHED;

        Specification<ScrmKnowledgeArticleEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // 状态过滤 (支持多状态逗号分隔)
            if (status.contains(",")) {
                predicates.add(root.get("status").in((Object[]) status.split(",")));
            } else {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (effectiveQueryDto.getCategoryId() != null) {
                predicates.add(cb.equal(root.get("categoryId"), effectiveQueryDto.getCategoryId()));
            }
            if (effectiveQueryDto.getArticleType() != null && !effectiveQueryDto.getArticleType().isBlank()) {
                predicates.add(cb.equal(root.get("articleType"), effectiveQueryDto.getArticleType()));
            }
            if (effectiveQueryDto.getDifficultyLevel() != null && !effectiveQueryDto.getDifficultyLevel().isBlank()) {
                predicates.add(cb.equal(root.get("difficultyLevel"), effectiveQueryDto.getDifficultyLevel()));
            }
            if (effectiveQueryDto.getAuthorId() != null && !effectiveQueryDto.getAuthorId().isBlank()) {
                predicates.add(cb.equal(root.get("authorId"), effectiveQueryDto.getAuthorId()));
            }
            if (effectiveQueryDto.getTags() != null && !effectiveQueryDto.getTags().isBlank()) {
                predicates.add(cb.like(root.get("tags"), "%" + effectiveQueryDto.getTags() + "%"));
            }
            // 关键词匹配: 标题 / 摘要 / 内容 / 标签
            if (keyword != null && !keyword.isBlank()) {
                String likePattern = "%" + keyword + "%";
                predicates.add(cb.or(
                        cb.like(root.get("title"), likePattern),
                        cb.like(root.get("summary"), likePattern),
                        cb.like(root.get("content"), likePattern),
                        cb.like(root.get("tags"), likePattern),
                        cb.like(root.get("keywords"), likePattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        // 相关度排序需在内存中计算
        boolean useRelevance = SORT_RELEVANCE.equals(sortBy) && keyword != null && !keyword.isBlank();
        if (useRelevance) {
            PageRequest relevancePageable = PageRequest.of(0, SEARCH_RELEVANCE_LIMIT);
            List<ScrmKnowledgeArticleEntity> all = articleRepository.findAll(spec, relevancePageable).getContent();
            final String kw = keyword;
            // 排序: 相关度 DESC → viewCount DESC → likeCount DESC
            all.sort(Comparator
                    .comparingInt((ScrmKnowledgeArticleEntity a) -> computeRelevanceScore(a, kw)).reversed()
                    .thenComparingInt((ScrmKnowledgeArticleEntity a) ->
                            ScrmKnowledgeCategoryService.safeInt(a.getViewCount())).reversed()
                    .thenComparingInt((ScrmKnowledgeArticleEntity a) ->
                            ScrmKnowledgeCategoryService.safeInt(a.getLikeCount())).reversed());
            // 手动分页
            return paginate(all, pageable);
        }

        // 非相关度排序由数据库处理
        Sort sort = buildSort(sortBy);
        Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
        return articleRepository.findAll(spec, sortedPageable);
    }

    /**
     * 计算文章与关键词的相关度评分。
     * <p>
     * 标题命中权重 3, 摘要/标签/关键词命中权重 2, 内容命中权重 1。
     * </p>
     *
     * @param article 文章实体
     * @param keyword 关键词
     * @return 相关度评分
     */
    private int computeRelevanceScore(ScrmKnowledgeArticleEntity article, String keyword) {
        int score = 0;
        String lowerKeyword = keyword.toLowerCase();
        if (article.getTitle() != null && article.getTitle().toLowerCase().contains(lowerKeyword)) {
            score += 3;
        }
        if (article.getSummary() != null && article.getSummary().toLowerCase().contains(lowerKeyword)) {
            score += 2;
        }
        if (article.getTags() != null && article.getTags().toLowerCase().contains(lowerKeyword)) {
            score += 2;
        }
        if (article.getKeywords() != null && article.getKeywords().toLowerCase().contains(lowerKeyword)) {
            score += 2;
        }
        if (article.getContent() != null && article.getContent().toLowerCase().contains(lowerKeyword)) {
            score += 1;
        }
        return score;
    }

    /**
     * 构建排序对象。
     *
     * @param sortBy 排序字段
     * @return Sort 对象
     */
    private Sort buildSort(String sortBy) {
        if (sortBy == null) {
            return Sort.by(Sort.Direction.DESC, "createTime");
        }
        return switch (sortBy) {
            case SORT_VIEW_COUNT -> Sort.by(Sort.Direction.DESC, "viewCount");
            case SORT_LIKE_COUNT -> Sort.by(Sort.Direction.DESC, "likeCount");
            case SORT_RATING -> Sort.by(Sort.Direction.DESC, "avgRating");
            case SORT_CREATE_TIME -> Sort.by(Sort.Direction.DESC, "createTime");
            default -> Sort.by(Sort.Direction.DESC, "createTime");
        };
    }

    /**
     * 内存分页。
     *
     * @param list     全量列表
     * @param pageable 分页参数
     * @return 分页结果
     */
    private Page<ScrmKnowledgeArticleEntity> paginate(List<ScrmKnowledgeArticleEntity> list, Pageable pageable) {
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), list.size());
        List<ScrmKnowledgeArticleEntity> content = start <= list.size()
                ? list.subList(start, end)
                : List.of();
        return new PageImpl<>(content, pageable, list.size());
    }
}