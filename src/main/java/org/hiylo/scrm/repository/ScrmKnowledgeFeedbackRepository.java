/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmKnowledgeFeedbackRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmKnowledgeFeedbackEntity;
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
 * SCRM 知识反馈数据访问层。
 * <p>
 * 提供按文章 / 类型 / 用户 / 评论父节点 等维度的查询能力,
 * 供 {@code ScrmKnowledgeBaseService} 使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmKnowledgeFeedbackRepository extends JpaRepository<ScrmKnowledgeFeedbackEntity, Long>,
        JpaSpecificationExecutor<ScrmKnowledgeFeedbackEntity> {

    /**
     * 按文章与用户查询已有反馈 (用于点赞/收藏去重判断)。
     *
     * @param articleId   文章 ID
     * @param userId      用户 ID
     * @param feedbackType 反馈类型
     * @return 反馈实体 (可选)
     */
    Optional<ScrmKnowledgeFeedbackEntity> findByArticleIdAndUserIdAndFeedbackType(Long articleId, String userId, String feedbackType);

    /**
     * 按文章查询评论 (按 createTime ASC)。
     *
     * @param articleId 文章 ID
     * @param status   状态
     * @param pageable 分页参数
     * @return 反馈分页结果
     */
    Page<ScrmKnowledgeFeedbackEntity> findByArticleIdAndFeedbackTypeAndStatusOrderByCreateTimeAsc(Long articleId, String feedbackType, String status, Pageable pageable);

    /**
     * 按文章查询子评论。
     *
     * @param parentCommentId 父评论 ID
     * @return 子评论列表
     */
    List<ScrmKnowledgeFeedbackEntity> findByParentCommentIdOrderByCreateTimeAsc(Long parentCommentId);

    /**
     * 按文章统计评论数。
     *
     * @param articleId 文章 ID
     * @param feedbackType 反馈类型
     * @param status   状态
     * @return 评论数
     */
    long countByArticleIdAndFeedbackTypeAndStatus(Long articleId, String feedbackType, String status);

    /**
     * 按文章汇总评分 (用于计算平均评分)。
     *
     * @param articleId 文章 ID
     * @param feedbackType 反馈类型 (RATING)
     * @param status   状态
     * @return Object[]{ratingSum, count}
     */
    @Query(value = "SELECT COALESCE(SUM(f.rating), 0), COUNT(f.id) FROM ScrmKnowledgeFeedbackEntity f WHERE "
                          + "f.articleId = :articleId AND f.feedbackType = :feedbackType AND f.status = :status AND f.rating "
                          + "IS NOT NULL")
    Object[] sumRating(
                       @Param("articleId") Long articleId,
                       @Param("feedbackType") String feedbackType,
                       @Param("status") String status);

    /**
     * 按统计反馈数 (按类型分组)。
     *
     * @param startTime 创建时间起始 (含, 可空)
     * @param endTime   创建时间截止 (含, 可空)
     * @return Object[]{feedbackType, count}
     */
    @Query(value = "SELECT f.feedbackType, COUNT(f.id) FROM ScrmKnowledgeFeedbackEntity f WHERE (:startTime IS NULL "
                          + "OR f.createTime >= :startTime) AND (:endTime IS NULL OR f.createTime <= :endTime) GROUP BY "
                          + "f.feedbackType")
    List<Object[]> countByFeedbackType(
                                       @Param("startTime") LocalDateTime startTime,
                                       @Param("endTime") LocalDateTime endTime);
}
