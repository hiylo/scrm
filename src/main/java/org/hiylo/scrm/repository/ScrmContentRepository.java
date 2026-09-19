/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContentRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmContentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 内容营销数据访问层。
 * <p>
 * 提供按内容状态聚合、按渠道汇总互动指标等聚合查询能力,
 * 供 {@code ScrmContentMarketingService} 统计与效果分析使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmContentRepository extends JpaRepository<ScrmContentEntity, Long>,
        JpaSpecificationExecutor<ScrmContentEntity> {

    /**
     * 按状态聚合内容数 (统计用)。
     *
     * @param startTime 创建时间起始 (含, 可空)
     * @param endTime   创建时间截止 (含, 可空)
     * @return Object[]{status, count}
     */
    @Query(value = "SELECT c.status, COUNT(c.id) FROM ScrmContentEntity c WHERE (:startTime IS NULL OR c.createTime"
                          + ">= :startTime) AND (:endTime IS NULL OR c.createTime <= :endTime) GROUP BY c.status")
    List<Object[]> countByStatus(
                                 @Param("startTime") LocalDateTime startTime,
                                 @Param("endTime") LocalDateTime endTime);

    /**
     * 汇总内容互动指标 (浏览/点赞/分享/评论/收藏/转化, 统计用)。
     *
     * @param startTime 发布时间起始 (含, 可空)
     * @param endTime   发布时间截止 (含, 可空)
     * @return Object[]{viewSum, likeSum, shareSum, commentSum, collectSum, conversionSum, publishedCount}
     */
    @Query(value = "SELECT COALESCE(SUM(c.viewCount), 0), COALESCE(SUM(c.likeCount), 0), COALESCE(SUM(c.shareCount),"
                          + "0), COALESCE(SUM(c.commentCount), 0), COALESCE(SUM(c.collectCount), 0),"
                          + "COALESCE(SUM(c.conversionCount), 0), SUM(CASE WHEN c.status = 'PUBLISHED' THEN 1 ELSE 0 END) "
                          + "FROM ScrmContentEntity c WHERE (:startTime IS NULL OR c.publishedAt >= :startTime) AND (:endTime "
                          + "IS NULL OR c.publishedAt <= :endTime)")
    Object[] sumMetrics(
                        @Param("startTime") LocalDateTime startTime,
                        @Param("endTime") LocalDateTime endTime);
}
