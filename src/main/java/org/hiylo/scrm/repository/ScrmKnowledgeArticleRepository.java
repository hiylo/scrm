/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmKnowledgeArticleRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmKnowledgeArticleEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * SCRM 知识文章数据访问层。
 * <p>
 * 提供按编码 / 分类 / 标签 / 精选 / 置顶 / 热门 等维度的查询能力,
 * 供 {@code ScrmKnowledgeBaseService} 使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmKnowledgeArticleRepository extends JpaRepository<ScrmKnowledgeArticleEntity, Long>,
        JpaSpecificationExecutor<ScrmKnowledgeArticleEntity> {

    /**
     * 按与文章编码查询。
     *
     * @param articleCode 文章编码
     * @return 文章实体 (可选)
     */
    Optional<ScrmKnowledgeArticleEntity> findByArticleCode(String articleCode);

    /**
     * 校验文章编码是否已存在。
     *
     * @param articleCode 文章编码
     * @return 是否存在
     */
    boolean existsByArticleCode(String articleCode);

    /**
     * 按分类查询已发布文章 (分页)。
     *
     * @param categoryId 分类 ID
     * @param status    状态
     * @param pageable  分页参数
     * @return 文章分页结果
     */
    Page<ScrmKnowledgeArticleEntity> findByCategoryIdAndStatus(Long categoryId, String status, Pageable pageable);

    /**
     * 按与精选标识查询 (按 sortOrder ASC, createTime DESC)。
     *
     * @param isFeatured 是否精选
     * @param status    状态
     * @param pageable  分页参数
     * @return 文章分页结果
     */
    Page<ScrmKnowledgeArticleEntity> findByIsFeaturedAndStatusOrderBySortOrderAscCreateTimeDesc(Boolean isFeatured, String status, Pageable pageable);

    /**
     * 按与置顶标识查询 (按 sortOrder ASC, createTime DESC)。
     *
     * @param isPinned 是否置顶
     * @param status   状态
     * @return 文章列表
     */
    List<ScrmKnowledgeArticleEntity> findByIsPinnedAndStatusOrderBySortOrderAscCreateTimeDesc(Boolean isPinned, String status);

    /**
     * 按状态查询热门文章 (按 viewCount DESC)。
     *
     * @param status   状态
     * @param pageable 分页参数 (含 limit)
     * @return 文章分页结果
     */
    Page<ScrmKnowledgeArticleEntity> findByStatusOrderByViewCountDesc(String status, Pageable pageable);

    /**
     * 按状态查询最新文章 (按 createTime DESC)。
     *
     * @param status   状态
     * @param pageable 分页参数 (含 limit)
     * @return 文章分页结果
     */
    Page<ScrmKnowledgeArticleEntity> findByStatusOrderByCreateTimeDesc(String status, Pageable pageable);

    /**
     * 按状态查询文章编码前缀 (用于自动补全)。
     *
     * @param prefix   标题前缀
     * @param pageable 分页参数
     * @return 文章列表
     */
    List<ScrmKnowledgeArticleEntity> findByStatusAndTitleStartingWithOrderByViewCountDesc(String status, String prefix, Pageable pageable);

    /**
     * 按统计文章数 (按状态分组)。
     *
     * @param startTime 创建时间起始 (含, 可空)
     * @param endTime   创建时间截止 (含, 可空)
     * @return Object[]{status, count}
     */
    @Query(value = "SELECT a.status, COUNT(a.id) FROM ScrmKnowledgeArticleEntity a WHERE (:startTime IS NULL OR "
                          + "a.createTime >= :startTime) AND (:endTime IS NULL OR a.createTime <= :endTime) GROUP BY a.status")
    List<Object[]> countByStatus(
                                 @Param("startTime") LocalDateTime startTime,
                                 @Param("endTime") LocalDateTime endTime);

    /**
     * 按统计文章数 (按文章类型分组)。
     *
     * @return Object[]{articleType, count}
     */
    @Query("SELECT a.articleType, COUNT(a.id) FROM ScrmKnowledgeArticleEntity a GROUP BY a.articleType")
    List<Object[]> countByArticleType();

    /**
     * 按统计文章数 (按分类分组)。
     *
     * @return Object[]{categoryId, count}
     */
    @Query(value = "SELECT a.categoryId, COUNT(a.id) FROM ScrmKnowledgeArticleEntity a WHERE a.categoryId IS NOT "
                          + "NULL GROUP BY a.categoryId")
    List<Object[]> countByCategory();

    /**
     * 汇总文章互动指标 (浏览/点赞/收藏/分享/评论/有用, 统计用)。
     *
     * @param startTime 发布时间起始 (含, 可空)
     * @param endTime   发布时间截止 (含, 可空)
     * @return Object[]{viewSum, likeSum, favoriteSum, shareSum, commentSum, helpfulSum, publishedCount}
     */
    @Query(value = "SELECT COALESCE(SUM(a.viewCount), 0), COALESCE(SUM(a.likeCount), 0),"
                          + "COALESCE(SUM(a.favoriteCount), 0), COALESCE(SUM(a.shareCount), 0), COALESCE(SUM(a.commentCount),"
                          + "0), COALESCE(SUM(a.helpfulCount), 0), SUM(CASE WHEN a.status = 'PUBLISHED' THEN 1 ELSE 0 END) "
                          + "FROM ScrmKnowledgeArticleEntity a WHERE (:startTime IS NULL OR a.publishedAt >= :startTime) AND "
                          + "(:endTime IS NULL OR a.publishedAt <= :endTime)")
    Object[] sumMetrics(
                        @Param("startTime") LocalDateTime startTime,
                        @Param("endTime") LocalDateTime endTime);

    /**
     * 按统计贡献 (按作者分组)。
     *
     * @param startTime 创建时间起始 (含, 可空)
     * @param endTime   创建时间截止 (含, 可空)
     * @return Object[]{authorId, authorName, articleCount, viewSum, likeSum}
     */
    @Query(value = "SELECT a.authorId, a.authorName, COUNT(a.id), COALESCE(SUM(a.viewCount), 0),"
                          + "COALESCE(SUM(a.likeCount), 0) FROM ScrmKnowledgeArticleEntity a WHERE (:startTime IS NULL OR "
                          + "a.createTime >= :startTime) AND (:endTime IS NULL OR a.createTime <= :endTime) AND a.authorId IS "
                          + "NOT NULL GROUP BY a.authorId, a.authorName ORDER BY COUNT(a.id) DESC")
    List<Object[]> aggregateByAuthor(
                                     @Param("startTime") LocalDateTime startTime,
                                     @Param("endTime") LocalDateTime endTime);

    /**
     * 按统计每日新增文章数 (趋势)。
     *
     * @param startTime 创建时间起始 (含)
     * @param endTime   创建时间截止 (含)
     * @return Object[]{date, count}
     */
    @Query(value = "SELECT FUNCTION('DATE', a.createTime), COUNT(a.id) FROM ScrmKnowledgeArticleEntity a WHERE "
                          + "a.createTime >= :startTime AND a.createTime <= :endTime GROUP BY FUNCTION('DATE', a.createTime) "
                          + "ORDER BY FUNCTION('DATE', a.createTime) ASC")
    List<Object[]> dailyCount(
                              @Param("startTime") LocalDateTime startTime,
                              @Param("endTime") LocalDateTime endTime);
}
