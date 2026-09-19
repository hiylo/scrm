/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContentChannelRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmContentChannelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * SCRM 内容分发渠道数据访问层。
 * <p>
 * 提供按内容/渠道/状态查询渠道记录, 以及渠道效果聚合 (浏览/点赞/分享/评论/转化) 能力,
 * 供 {@code ScrmContentMarketingService} 渠道管理与效果对比使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmContentChannelRepository extends JpaRepository<ScrmContentChannelEntity, Long>,
        JpaSpecificationExecutor<ScrmContentChannelEntity> {

    /**
     * 按内容加载全部渠道记录 (按渠道名升序)。
     *
     * @param contentId 内容 ID
     * @return 渠道记录列表
     */
    List<ScrmContentChannelEntity> findByContentIdOrderByChannelAsc(Long contentId);

    /**
     * 按内容与渠道查找渠道记录 (校验唯一渠道)。
     *
     * @param contentId 内容 ID
     * @param channel   渠道
     * @return 渠道记录 (存在返回)
     */
    Optional<ScrmContentChannelEntity> findByContentIdAndChannel(Long contentId, String channel);

    /**
     * 渠道效果聚合: 按渠道汇总各类互动计数 (统计用)。
     *
     * @param startTime 发布时间起始 (含, 可空)
     * @param endTime   发布时间截止 (含, 可空)
     * @return Object[]{channel, viewSum, likeSum, shareSum, commentSum, conversionSum, publishedCount}
     */
    @Query(value = "SELECT ch.channel, COALESCE(SUM(ch.viewCount), 0), COALESCE(SUM(ch.likeCount), 0),"
                          + "COALESCE(SUM(ch.shareCount), 0), COALESCE(SUM(ch.commentCount), 0),"
                          + "COALESCE(SUM(ch.conversionCount), 0), SUM(CASE WHEN ch.status = 'PUBLISHED' THEN 1 ELSE 0 END) "
                          + "FROM ScrmContentChannelEntity ch WHERE (:startTime IS NULL OR ch.publishedAt >= :startTime) AND "
                          + "(:endTime IS NULL OR ch.publishedAt <= :endTime) GROUP BY ch.channel")
    List<Object[]> aggregateByChannel(
                                      @Param("startTime") LocalDateTime startTime,
                                      @Param("endTime") LocalDateTime endTime);
}
