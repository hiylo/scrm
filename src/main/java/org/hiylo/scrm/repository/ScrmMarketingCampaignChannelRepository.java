/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMarketingCampaignChannelRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmMarketingCampaignChannelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 营销活动渠道数据访问层。
 * <p>
 * 提供按活动 / 渠道查询渠道记录, 以及渠道效果聚合 (花费 / 送达 / 点击 / 转化) 能力,
 * 供 {@code ScrmMarketingCampaignService} 渠道管理与效果分析使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmMarketingCampaignChannelRepository extends JpaRepository<ScrmMarketingCampaignChannelEntity, Long>,
        JpaSpecificationExecutor<ScrmMarketingCampaignChannelEntity> {

    /**
     * 按活动加载全部渠道记录 (按渠道名升序)。
     *
     * @param campaignId 活动 ID
     * @return 渠道记录列表
     */
    List<ScrmMarketingCampaignChannelEntity> findByCampaignIdOrderByChannelAsc(Long campaignId);

    /**
     * 按活动与渠道查找渠道记录 (校验唯一渠道)。
     *
     * @param campaignId 活动 ID
     * @param channel    渠道
     * @return 渠道记录 (存在返回)
     */
    Optional<ScrmMarketingCampaignChannelEntity> findByCampaignIdAndChannel(Long campaignId, String channel);

    /**
     * 按活动加载 PENDING 状态渠道 (启动渠道用)。
     *
     * @param campaignId 活动 ID
     * @return PENDING 渠道列表
     */
    List<ScrmMarketingCampaignChannelEntity> findByCampaignIdAndStatus(Long campaignId, String status);

    /**
     * 渠道效果聚合: 按渠道汇总花费与各类计数 (统计用)。
     *
     * @param startTime 发送时间起始 (含, 可空)
     * @param endTime   发送时间截止 (含, 可空)
     * @return Object[]{channel, totalCost, sentCount, deliveredCount, readCount, clickCount, convertCount}
     */
    @Query(value = "SELECT ch.channel, COALESCE(SUM(ch.cost), 0), COALESCE(SUM(ch.sentCount), 0),"
                          + "COALESCE(SUM(ch.deliveredCount), 0), COALESCE(SUM(ch.readCount), 0),"
                          + "COALESCE(SUM(ch.clickCount), 0), COALESCE(SUM(ch.convertCount), 0) FROM "
                          + "ScrmMarketingCampaignChannelEntity ch WHERE ch.sentAt IS NOT NULL AND (:startTime IS NULL OR "
                          + "ch.sentAt >= :startTime) AND (:endTime IS NULL OR ch.sentAt <= :endTime) GROUP BY ch.channel")
    List<Object[]> aggregateByChannel(
                                       @Param("startTime") java.time.LocalDateTime startTime,
                                       @Param("endTime") java.time.LocalDateTime endTime);
}
