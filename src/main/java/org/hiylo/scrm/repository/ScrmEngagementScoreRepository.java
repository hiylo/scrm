/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmEngagementScoreRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmEngagementScoreEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 客户互动评分数据访问层。
 * <p>
 * 提供按 + 客户定位唯一评分记录 (供 {@code ScrmEngagementScoreService.getOrCreateScore}
 * 使用), 高分客户排行查询, 评分分布统计与全量评分加载 (供 recalculateAllScores 重算)。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmEngagementScoreRepository extends JpaRepository<ScrmEngagementScoreEntity, Long>,
        JpaSpecificationExecutor<ScrmEngagementScoreEntity> {

    /**
     * 按客户查询评分记录 (customer_id 唯一)。
     *
     * @param customerId 客户 ID
     * @return 评分记录 (可能为空)
     */
    Optional<ScrmEngagementScoreEntity> findByCustomerId(Long customerId);

    /**
     * 高分客户排行: 按当前分倒序分页。
     *
     * @param pageable 分页参数
     * @return 评分分页结果 (按 currentScore DESC)
     */
    Page<ScrmEngagementScoreEntity> findAllByOrderByCurrentScoreDesc(Pageable pageable);

    /**
     * 加载账号下所有评分记录 (供 recalculateAllScores 重算)。
     *
     * @return 评分记录列表
     */

    /**
     * 评分分布统计: 按活跃等级分组计数。
     *
     * @return Object[] 列表: [engagementLevel, customerCount]
     */
    @Query("SELECT s.engagementLevel, COUNT(s) FROM ScrmEngagementScoreEntity s GROUP BY s.engagementLevel")
    List<Object[]> countByLevel();

    /**
     * 互动总览统计: 客户数 / 平均当前分 / 总事件数。
     *
     * @return Object[]: [customerCount, avgCurrentScore, totalEvents]
     */
    @Query("SELECT COUNT(s), COALESCE(AVG(s.currentScore), 0), COALESCE(SUM(s.totalEvents), 0) "
            + "FROM ScrmEngagementScoreEntity s")
    Object[] getOverviewStats();
}
