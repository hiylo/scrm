/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmKnowledgeStatsService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmKnowledgeArticleEntity;
import org.hiylo.scrm.entity.ScrmKnowledgeCategoryEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmKnowledgeArticleRepository;
import org.hiylo.scrm.repository.ScrmKnowledgeCategoryRepository;
import org.hiylo.scrm.repository.ScrmKnowledgeFeedbackRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRM 知识库统计分析服务。
 * <p>
 * 承载知识库统计子域: 知识库概览 / 分类统计 / 单文章统计 / 搜索统计 / 反馈统计 / 贡献统计 / 趋势。
 * 文章与分类共享取值工具取自 {@link ScrmKnowledgeCategoryService}, 单文章统计校验委托给
 * {@link ScrmKnowledgeArticleService}, 热门搜索词委托给 {@link ScrmKnowledgeSearchFeedbackService}。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmKnowledgeStatsService {

    /** 知识文章数据访问层 */
    private final ScrmKnowledgeArticleRepository articleRepository;

    /** 知识分类数据访问层 */
    private final ScrmKnowledgeCategoryRepository categoryRepository;

    /** 知识反馈数据访问层 */
    private final ScrmKnowledgeFeedbackRepository feedbackRepository;

    /** 知识文章管理服务 (文章存在性校验) */
    private final ScrmKnowledgeArticleService articleService;

    /** 知识搜索与反馈服务 (热门搜索词) */
    private final ScrmKnowledgeSearchFeedbackService searchFeedbackService;

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 知识库统计概览 (文章数 / 各类型 / 各分类 / 总浏览 / 总点赞)。
     *
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getKnowledgeStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        Object[] metrics = articleRepository.sumMetrics(null, null);
        stats.put("totalArticles", articleRepository.count());
        stats.put("publishedCount", ScrmKnowledgeCategoryService.toLong(metrics[6]));
        stats.put("totalViews", ScrmKnowledgeCategoryService.toLong(metrics[0]));
        stats.put("totalLikes", ScrmKnowledgeCategoryService.toLong(metrics[1]));
        stats.put("totalFavorites", ScrmKnowledgeCategoryService.toLong(metrics[2]));
        stats.put("totalShares", ScrmKnowledgeCategoryService.toLong(metrics[3]));
        stats.put("totalComments", ScrmKnowledgeCategoryService.toLong(metrics[4]));
        stats.put("totalHelpful", ScrmKnowledgeCategoryService.toLong(metrics[5]));
        // 按状态统计
        List<Object[]> statusAgg = articleRepository.countByStatus(null, null);
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Object[] row : statusAgg) {
            byStatus.put((String) row[0], ScrmKnowledgeCategoryService.toLong(row[1]));
        }
        stats.put("byStatus", byStatus);
        // 按文章类型统计
        List<Object[]> typeAgg = articleRepository.countByArticleType();
        Map<String, Long> byType = new LinkedHashMap<>();
        for (Object[] row : typeAgg) {
            byType.put((String) row[0], ScrmKnowledgeCategoryService.toLong(row[1]));
        }
        stats.put("byArticleType", byType);
        // 分类数
        stats.put("categoryCount", categoryRepository.count());
        return stats;
    }

    /**
     * 分类统计 (各分类的文章数 / 浏览量 / 点赞数)。
     *
     * @return 分类统计列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCategoryStats() {
        List<ScrmKnowledgeCategoryEntity> categories =
                categoryRepository.findAllByOrderBySortOrderAscCategoryLevelAsc();
        List<Object[]> articleAgg = articleRepository.countByCategory();
        Map<Long, Long> articleCountMap = new LinkedHashMap<>();
        for (Object[] row : articleAgg) {
            Long categoryId = (Long) row[0];
            if (categoryId != null) {
                articleCountMap.put(categoryId, ScrmKnowledgeCategoryService.toLong(row[1]));
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (ScrmKnowledgeCategoryEntity c : categories) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("categoryId", c.getId());
            m.put("categoryName", c.getCategoryName());
            m.put("categoryCode", c.getCategoryCode());
            m.put("categoryLevel", c.getCategoryLevel());
            m.put("articleCount", articleCountMap.getOrDefault(c.getId(), 0L));
            m.put("totalViews", ScrmKnowledgeCategoryService.safeInt(c.getTotalViews()));
            m.put("totalLikes", ScrmKnowledgeCategoryService.safeInt(c.getTotalLikes()));
            m.put("enabled", c.getEnabled());
            result.add(m);
        }
        return result;
    }

    /**
     * 单文章统计详情。
     *
     * @param articleId 文章 ID
     * @return 统计详情 Map
     * @throws ScrmException 文章不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getArticleStats(Long articleId) throws ScrmException {
        ScrmKnowledgeArticleEntity article = articleService.findArticleOrThrow(articleId);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("articleId", article.getId());
        stats.put("title", article.getTitle());
        stats.put("status", article.getStatus());
        stats.put("viewCount", ScrmKnowledgeCategoryService.safeInt(article.getViewCount()));
        stats.put("uniqueViewCount", ScrmKnowledgeCategoryService.safeInt(article.getUniqueViewCount()));
        stats.put("likeCount", ScrmKnowledgeCategoryService.safeInt(article.getLikeCount()));
        stats.put("dislikeCount", ScrmKnowledgeCategoryService.safeInt(article.getDislikeCount()));
        stats.put("favoriteCount", ScrmKnowledgeCategoryService.safeInt(article.getFavoriteCount()));
        stats.put("shareCount", ScrmKnowledgeCategoryService.safeInt(article.getShareCount()));
        stats.put("commentCount", ScrmKnowledgeCategoryService.safeInt(article.getCommentCount()));
        stats.put("helpfulCount", ScrmKnowledgeCategoryService.safeInt(article.getHelpfulCount()));
        stats.put("notHelpfulCount", ScrmKnowledgeCategoryService.safeInt(article.getNotHelpfulCount()));
        stats.put("helpfulRate", article.getHelpfulRate() != null ? article.getHelpfulRate() : 0.0);
        stats.put("avgRating", article.getAvgRating() != null ? article.getAvgRating() : 0.0);
        stats.put("ratingCount", ScrmKnowledgeCategoryService.safeInt(article.getRatingCount()));
        return stats;
    }

    /**
     * 搜索统计 (基于热门标签与浏览量, 简化实现)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSearchStats(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> stats = new LinkedHashMap<>();
        List<String> popularKeywords = searchFeedbackService.getPopularKeywords(20);
        stats.put("popularKeywords", popularKeywords);
        Object[] metrics = articleRepository.sumMetrics(startTime, endTime);
        stats.put("totalViews", ScrmKnowledgeCategoryService.toLong(metrics[0]));
        stats.put("totalLikes", ScrmKnowledgeCategoryService.toLong(metrics[1]));
        stats.put("publishedCount", ScrmKnowledgeCategoryService.toLong(metrics[6]));
        return stats;
    }

    /**
     * 反馈统计 (按反馈类型分组)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getFeedbackStats(LocalDateTime startTime, LocalDateTime endTime) {
        List<Object[]> agg = feedbackRepository.countByFeedbackType(startTime, endTime);
        Map<String, Long> byType = new LinkedHashMap<>();
        long total = 0L;
        for (Object[] row : agg) {
            byType.put((String) row[0], ScrmKnowledgeCategoryService.toLong(row[1]));
            total += ScrmKnowledgeCategoryService.toLong(row[1]);
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalFeedback", total);
        stats.put("byType", byType);
        return stats;
    }

    /**
     * 贡献统计 (按作者分组)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 贡献统计列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getContributionStats(LocalDateTime startTime, LocalDateTime endTime) {
        List<Object[]> agg = articleRepository.aggregateByAuthor(startTime, endTime);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : agg) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("authorId", row[0]);
            m.put("authorName", row[1]);
            m.put("articleCount", ScrmKnowledgeCategoryService.toLong(row[2]));
            m.put("totalViews", ScrmKnowledgeCategoryService.toLong(row[3]));
            m.put("totalLikes", ScrmKnowledgeCategoryService.toLong(row[4]));
            result.add(m);
        }
        return result;
    }

    /**
     * 趋势统计 (每日新增文章数)。
     *
     * @param days 统计天数
     * @return 趋势列表 [{date, count}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTrend(Integer days) {
        int range = days != null && days > 0 ? days : 30;
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusDays(range);
        List<Object[]> agg = articleRepository.dailyCount(startTime, endTime);
        Map<String, Long> countMap = new LinkedHashMap<>();
        for (Object[] row : agg) {
            java.sql.Date date = (java.sql.Date) row[0];
            countMap.put(date.toString(), ScrmKnowledgeCategoryService.toLong(row[1]));
        }
        // 填充空日期
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate start = startTime.toLocalDate();
        LocalDate end = endTime.toLocalDate();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", d.toString());
            m.put("count", countMap.getOrDefault(d.toString(), 0L));
            result.add(m);
        }
        return result;
    }
}