/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmKnowledgeSearchFeedbackService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.dto.ScrmKnowledgeFeedbackDto;
import org.hiylo.scrm.dto.ScrmKnowledgeSearchDto;
import org.hiylo.scrm.entity.ScrmKnowledgeArticleEntity;
import org.hiylo.scrm.entity.ScrmKnowledgeFeedbackEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmKnowledgeArticleRepository;
import org.hiylo.scrm.repository.ScrmKnowledgeFeedbackRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SCRM 知识搜索与反馈服务。
 * <p>
 * 承载知识库搜索与反馈子域: 搜索 (相关度排序 / 自动补全 / 搜索建议 / 热门词 / 相关文章推荐)
 * 与反馈 (点赞/收藏/评分/评论/举报 / 解决 / 忽略 / 回复 / 评分)。同时托管反馈共享常量
 * (反馈类型 / 反馈状态) 与反馈去重 / 平均评分计算 / 标签解析等辅助方法, 供文章兄弟类
 * 以 package 级访问复用。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmKnowledgeSearchFeedbackService {

    // ==================== 反馈类型 (共享) ====================

    /** 反馈类型: 有用 */
    static final String FEEDBACK_HELPFUL = "HELPFUL";
    /** 反馈类型: 无用 */
    static final String FEEDBACK_NOT_HELPFUL = "NOT_HELPFUL";
    /** 反馈类型: 点赞 */
    static final String FEEDBACK_LIKE = "LIKE";
    /** 反馈类型: 踩 */
    static final String FEEDBACK_DISLIKE = "DISLIKE";
    /** 反馈类型: 收藏 */
    static final String FEEDBACK_FAVORITE = "FAVORITE";
    /** 反馈类型: 分享 */
    static final String FEEDBACK_SHARE = "SHARE";
    /** 反馈类型: 评分 */
    static final String FEEDBACK_RATING = "RATING";
    /** 反馈类型: 评论 */
    static final String FEEDBACK_COMMENT = "COMMENT";
    /** 反馈类型: 举报 */
    static final String FEEDBACK_REPORT = "REPORT";

    /** 反馈状态: 活跃 */
    private static final String FEEDBACK_STATUS_ACTIVE = "ACTIVE";
    /** 反馈状态: 已解决 */
    private static final String FEEDBACK_STATUS_RESOLVED = "RESOLVED";
    /** 反馈状态: 已忽略 */
    private static final String FEEDBACK_STATUS_DISMISSED = "DISMISSED";

    /** 知识文章数据访问层 */
    private final ScrmKnowledgeArticleRepository articleRepository;

    /** 知识反馈数据访问层 */
    private final ScrmKnowledgeFeedbackRepository feedbackRepository;

    /** 知识文章管理服务 (文章查找 / 检索内部实现 / 文章互动计数更新) */
    private final ScrmKnowledgeArticleService articleService;

    // ============================================================
    // 搜索
    // ============================================================

    /**
     * 搜索知识文章 (关键词匹配标题/摘要/内容/标签, 排序按相关度+浏览量+点赞数)。
     *
     * @param queryDto 查询参数
     * @return 搜索结果分页
     */
    @Transactional(readOnly = true)
    public Page<ScrmKnowledgeArticleEntity> search(ScrmKnowledgeSearchDto queryDto) {
        Pageable pageable = PageRequest.of(0, 20);
        return articleService.searchInternal(queryDto, pageable);
    }

    /**
     * 自动补全建议 (按标题前缀匹配, 返回标题列表)。
     *
     * @param prefix 标题前缀
     * @return 建议标题列表
     */
    @Transactional(readOnly = true)
    public List<String> autoComplete(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return List.of();
        }
        PageRequest pageable = PageRequest.of(0, 10);
        List<ScrmKnowledgeArticleEntity> articles =
                articleRepository.findByStatusAndTitleStartingWithOrderByViewCountDesc(
                         ScrmKnowledgeCategoryService.STATUS_PUBLISHED, prefix, pageable);
        return articles.stream().map(ScrmKnowledgeArticleEntity::getTitle).collect(Collectors.toList());
    }

    /**
     * 搜索建议 (关键词匹配标题/标签, 返回文章简要信息)。
     *
     * @param keyword 关键词
     * @return 建议列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSearchSuggestions(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        ScrmKnowledgeSearchDto dto = new ScrmKnowledgeSearchDto();
        dto.setKeyword(keyword);
        dto.setStatus(ScrmKnowledgeCategoryService.STATUS_PUBLISHED);
        Page<ScrmKnowledgeArticleEntity> page = articleService.searchInternal(dto, PageRequest.of(0, 10));
        return page.getContent().stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("title", a.getTitle());
            m.put("articleType", a.getArticleType());
            m.put("summary", a.getSummary());
            m.put("viewCount", ScrmKnowledgeCategoryService.safeInt(a.getViewCount()));
            return m;
        }).collect(Collectors.toList());
    }

    /**
     * 热门搜索词 (基于文章标签统计, 简化实现)。
     *
     * @param limit 返回条数
     * @return 热门标签列表
     */
    @Transactional(readOnly = true)
    public List<String> getPopularKeywords(Integer limit) {
        int size = limit != null && limit > 0 ? limit : 10;
        PageRequest pageable = PageRequest.of(0, 100);
        Page<ScrmKnowledgeArticleEntity> page =
                articleRepository.findByStatusOrderByViewCountDesc(ScrmKnowledgeCategoryService.STATUS_PUBLISHED, pageable);
        Map<String, Long> tagCount = new LinkedHashMap<>();
        for (ScrmKnowledgeArticleEntity a : page.getContent()) {
            if (a.getTags() == null || a.getTags().isBlank()) {
                continue;
            }
            for (String tag : a.getTags().split(",")) {
                String trimmed = tag.trim();
                if (!trimmed.isEmpty()) {
                    tagCount.merge(trimmed, 1L, Long::sum);
                }
            }
        }
        return tagCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(size)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * 相关文章推荐 (基于同分类 / 共同标签, 排除自身)。
     *
     * @param articleId 文章 ID
     * @param limit     返回条数
     * @return 相关文章列表
     * @throws ScrmException 文章不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmKnowledgeArticleEntity> getRelatedArticles(
            Long articleId, Integer limit) throws ScrmException {
        ScrmKnowledgeArticleEntity source = articleService.findArticleOrThrow(articleId);
        int size = limit != null && limit > 0 ? limit : 5;
        List<String> sourceTags = parseTags(source.getTags());
        Specification<ScrmKnowledgeArticleEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), ScrmKnowledgeCategoryService.STATUS_PUBLISHED));
            predicates.add(cb.notEqual(root.get("id"), articleId));
            List<Predicate> orPredicates = new ArrayList<>();
            if (source.getCategoryId() != null) {
                orPredicates.add(cb.equal(root.get("categoryId"), source.getCategoryId()));
            }
            for (String tag : sourceTags) {
                orPredicates.add(cb.like(root.get("tags"), "%" + tag + "%"));
            }
            if (orPredicates.isEmpty()) {
                orPredicates.add(cb.conjunction());
            }
            predicates.add(cb.or(orPredicates.toArray(new Predicate[0])));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        PageRequest pageable = PageRequest.of(0, size * 2, Sort.by(Sort.Direction.DESC, "viewCount"));
        List<ScrmKnowledgeArticleEntity> candidates = articleRepository.findAll(spec, pageable).getContent();
        // 按标签重合度排序
        candidates.sort(Comparator.comparingInt(
                (ScrmKnowledgeArticleEntity a) -> countCommonTags(sourceTags, parseTags(a.getTags()))).reversed());
        return candidates.stream().limit(size).collect(Collectors.toList());
    }

    // ============================================================
    // 反馈
    // ============================================================

    /**
     * 添加知识反馈 (同时更新文章统计, 点赞/收藏/评分做去重判断)。
     *
     * @param dto 反馈参数
     * @return 创建后的反馈
     * @throws ScrmException 参数非法 / 文章不存在
     */
    @Transactional
    public ScrmKnowledgeFeedbackEntity addFeedback(ScrmKnowledgeFeedbackDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("反馈参数不能为空");
        }
        if (dto.getArticleId() == null) {
            throw ScrmException.badRequest("文章 ID 不能为空");
        }
        if (dto.getUserId() == null || dto.getUserId().isBlank()) {
            throw ScrmException.badRequest("用户 ID 不能为空");
        }
        if (dto.getFeedbackType() == null || dto.getFeedbackType().isBlank()) {
            throw ScrmException.badRequest("反馈类型不能为空");
        }
        ScrmKnowledgeArticleEntity article = articleService.findArticleOrThrow(dto.getArticleId());
        // 点赞 / 收藏 / 评分 做去重 (已有则不重复创建)
        if (isUniqueFeedbackType(dto.getFeedbackType())) {
            if (feedbackRepository.findByArticleIdAndUserIdAndFeedbackType(
                     dto.getArticleId(), dto.getUserId(), dto.getFeedbackType()).isPresent()) {
                throw ScrmException.conflict("已存在相同反馈: articleId=" + dto.getArticleId()
                        + ", userId=" + dto.getUserId() + ", type=" + dto.getFeedbackType());
            }
        }
        ScrmKnowledgeFeedbackEntity entity = new ScrmKnowledgeFeedbackEntity();
        entity.setArticleId(dto.getArticleId());
        entity.setArticleTitle(article.getTitle());
        entity.setFeedbackType(dto.getFeedbackType());
        entity.setUserId(dto.getUserId());
        entity.setUserName(dto.getUserName());
        entity.setUserRole(dto.getUserRole());
        entity.setRating(dto.getRating());
        entity.setComment(dto.getComment());
        entity.setCommentType(dto.getCommentType());
        entity.setParentCommentId(dto.getParentCommentId());
        entity.setIsInternal(dto.getIsInternal() != null ? dto.getIsInternal() : Boolean.FALSE);
        entity.setReportReason(dto.getReportReason());
        entity.setStatus(FEEDBACK_STATUS_ACTIVE);
        entity.setUpvoteCount(0);
        entity.setMetadata(dto.getMetadata());
        entity = feedbackRepository.save(entity);
        // 更新文章统计 (评分需单独计算平均分)
        if (FEEDBACK_RATING.equals(dto.getFeedbackType())) {
            recalcArticleRating(article);
            articleRepository.save(article);
        } else {
            articleService.updateArticleStats(dto.getArticleId(), dto.getFeedbackType());
        }
        log.info("添加知识反馈: id={}, articleId={}, type={}, userId={}",
                entity.getId(), dto.getArticleId(), dto.getFeedbackType(), dto.getUserId());
        return entity;
    }

    /**
     * 查询反馈详情。
     *
     * @param id 反馈 ID
     * @return 反馈实体
     * @throws ScrmException 反馈不存在
     */
    @Transactional(readOnly = true)
    public ScrmKnowledgeFeedbackEntity getFeedback(Long id) throws ScrmException {
        return findFeedbackOrThrow(id);
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
    @Transactional(readOnly = true)
    public Page<ScrmKnowledgeFeedbackEntity> listFeedback(Long articleId, String feedbackType,
                                                           String status, Pageable pageable) {
        Specification<ScrmKnowledgeFeedbackEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (articleId != null) {
                predicates.add(cb.equal(root.get("articleId"), articleId));
            }
            if (feedbackType != null && !feedbackType.isBlank()) {
                predicates.add(cb.equal(root.get("feedbackType"), feedbackType));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return feedbackRepository.findAll(spec, pageable);
    }

    /**
     * 解决反馈 (状态 → RESOLVED)。
     *
     * @param id   反馈 ID
     * @param note 解决备注
     * @return 更新后的反馈
     * @throws ScrmException 反馈不存在
     */
    @Transactional
    public ScrmKnowledgeFeedbackEntity resolveFeedback(Long id, String note) throws ScrmException {
        ScrmKnowledgeFeedbackEntity entity = findFeedbackOrThrow(id);
        entity.setStatus(FEEDBACK_STATUS_RESOLVED);
        entity.setResolvedBy(ScrmKnowledgeCategoryService.currentOperator());
        entity.setResolvedAt(LocalDateTime.now());
        entity.setResolutionNote(note);
        return feedbackRepository.save(entity);
    }

    /**
     * 忽略反馈 (状态 → DISMISSED)。
     *
     * @param id   反馈 ID
     * @param note 忽略备注
     * @return 更新后的反馈
     * @throws ScrmException 反馈不存在
     */
    @Transactional
    public ScrmKnowledgeFeedbackEntity dismissFeedback(Long id, String note) throws ScrmException {
        ScrmKnowledgeFeedbackEntity entity = findFeedbackOrThrow(id);
        entity.setStatus(FEEDBACK_STATUS_DISMISSED);
        entity.setResolvedBy(ScrmKnowledgeCategoryService.currentOperator());
        entity.setResolvedAt(LocalDateTime.now());
        entity.setResolutionNote(note);
        return feedbackRepository.save(entity);
    }

    /**
     * 获取文章评论列表 (feedbackType=COMMENT, 按 createTime ASC)。
     *
     * @param articleId 文章 ID
     * @param pageable  分页参数
     * @return 评论分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmKnowledgeFeedbackEntity> getComments(Long articleId, Pageable pageable) {
        return feedbackRepository.findByArticleIdAndFeedbackTypeAndStatusOrderByCreateTimeAsc(
                 articleId, FEEDBACK_COMMENT, FEEDBACK_STATUS_ACTIVE, pageable);
    }

    /**
     * 回复评论 (创建 COMMENT 类型反馈, parentCommentId 指向父评论)。
     *
     * @param feedbackId 父评论 ID
     * @param content    回复内容
     * @return 创建后的回复
     * @throws ScrmException 父评论不存在
     */
    @Transactional
    public ScrmKnowledgeFeedbackEntity replyToComment(Long feedbackId, String content) throws ScrmException {
        ScrmKnowledgeFeedbackEntity parent = findFeedbackOrThrow(feedbackId);
        if (!FEEDBACK_COMMENT.equals(parent.getFeedbackType())) {
            throw ScrmException.badRequest("仅评论类型可回复: id=" + feedbackId + ", type=" + parent.getFeedbackType());
        }
        if (content == null || content.isBlank()) {
            throw ScrmException.badRequest("回复内容不能为空");
        }
        ScrmKnowledgeFeedbackEntity reply = new ScrmKnowledgeFeedbackEntity();
        reply.setArticleId(parent.getArticleId());
        reply.setArticleTitle(parent.getArticleTitle());
        reply.setFeedbackType(FEEDBACK_COMMENT);
        reply.setUserId(ScrmKnowledgeCategoryService.currentOperator());
        reply.setUserName(UserContext.getUsername());
        reply.setComment(content);
        reply.setCommentType(parent.getCommentType());
        reply.setParentCommentId(feedbackId);
        reply.setIsInternal(Boolean.FALSE);
        reply.setStatus(FEEDBACK_STATUS_ACTIVE);
        reply.setUpvoteCount(0);
        reply = feedbackRepository.save(reply);
        // 更新文章评论数
        articleService.updateArticleStats(parent.getArticleId(), FEEDBACK_COMMENT);
        return reply;
    }

    /**
     * 评分 (创建 RATING 类型反馈并更新平均评分)。
     *
     * @param articleId 文章 ID
     * @param userId    用户 ID
     * @param rating    评分 1-5
     * @return 创建后的评分反馈
     * @throws ScrmException 文章不存在 / 评分非法
     */
    @Transactional
    public ScrmKnowledgeFeedbackEntity rateArticle(Long articleId, String userId, Integer rating)
            throws ScrmException {
        if (userId == null || userId.isBlank()) {
            throw ScrmException.badRequest("用户 ID 不能为空");
        }
        if (rating == null || rating < 1 || rating > 5) {
            throw ScrmException.badRequest("评分必须为 1-5");
        }
        ScrmKnowledgeArticleEntity article = articleService.findArticleOrThrow(articleId);
        // 已评分则更新
        ScrmKnowledgeFeedbackEntity entity = feedbackRepository
                .findByArticleIdAndUserIdAndFeedbackType(articleId, userId, FEEDBACK_RATING)
                .orElseGet(ScrmKnowledgeFeedbackEntity::new);
        boolean isNew = entity.getId() == null;
        if (isNew) {
            entity.setArticleId(articleId);
            entity.setArticleTitle(article.getTitle());
            entity.setFeedbackType(FEEDBACK_RATING);
            entity.setUserId(userId);
            entity.setStatus(FEEDBACK_STATUS_ACTIVE);
            entity.setUpvoteCount(0);
        }
        entity.setRating(rating);
        entity = feedbackRepository.save(entity);
        recalcArticleRating(article);
        articleRepository.save(article);
        return entity;
    }

    /**
     * 获取用户对文章的评分。
     *
     * @param articleId 文章 ID
     * @param userId    用户 ID
     * @return 评分反馈实体 (可选, 不存在返回 null)
     * @throws ScrmException 文章不存在
     */
    @Transactional(readOnly = true)
    public ScrmKnowledgeFeedbackEntity getArticleRating(Long articleId, String userId) throws ScrmException {
        articleService.findArticleOrThrow(articleId);
        return feedbackRepository.findByArticleIdAndUserIdAndFeedbackType(
                 articleId, userId, FEEDBACK_RATING).orElse(null);
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 重新计算文章平均评分。
     *
     * @param article 文章实体
     */
    private void recalcArticleRating(ScrmKnowledgeArticleEntity article) {
        Object[] ratingAgg = feedbackRepository.sumRating(
                 article.getId(), FEEDBACK_RATING, FEEDBACK_STATUS_ACTIVE);
        long ratingSum = ScrmKnowledgeCategoryService.toLong(ratingAgg[0]);
        long ratingCount = ScrmKnowledgeCategoryService.toLong(ratingAgg[1]);
        article.setRatingCount((int) ratingCount);
        article.setAvgRating(ratingCount > 0 ? (double) ratingSum / ratingCount : 0.0);
    }

    /**
     * 判断反馈类型是否需要去重 (每用户对每文章仅允许一条)。
     *
     * @param feedbackType 反馈类型
     * @return 是否去重
     */
    private boolean isUniqueFeedbackType(String feedbackType) {
        return FEEDBACK_LIKE.equals(feedbackType)
                || FEEDBACK_FAVORITE.equals(feedbackType)
                || FEEDBACK_RATING.equals(feedbackType)
                || FEEDBACK_HELPFUL.equals(feedbackType)
                || FEEDBACK_NOT_HELPFUL.equals(feedbackType);
    }

    /**
     * 按主键查询反馈, 不存在则抛异常。
     *
     * @param id 反馈 ID
     * @return 反馈实体
     * @throws ScrmException 反馈不存在
     */
    private ScrmKnowledgeFeedbackEntity findFeedbackOrThrow(Long id) throws ScrmException {
        ScrmKnowledgeFeedbackEntity entity = feedbackRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "知识反馈不存在: id=" + id));
        return entity;
    }

    /**
     * 解析标签字符串为列表。
     *
     * @param tags 标签字符串 (逗号分隔)
     * @return 标签列表
     */
    private List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String tag : tags.split(",")) {
            String trimmed = tag.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * 计算两个标签列表的共同标签数。
     *
     * @param list1 标签列表 1
     * @param list2 标签列表 2
     * @return 共同标签数
     */
    private int countCommonTags(List<String> list1, List<String> list2) {
        if (list1.isEmpty() || list2.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String tag : list1) {
            if (list2.contains(tag)) {
                count++;
            }
        }
        return count;
    }
}