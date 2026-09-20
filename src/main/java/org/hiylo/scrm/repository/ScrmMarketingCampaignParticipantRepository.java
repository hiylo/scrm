/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMarketingCampaignParticipantRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmMarketingCampaignParticipantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 营销活动参与者数据访问层。
 * <p>
 * 提供按活动 / 渠道 / 转化状态查询参与者, 以及转化金额与转化数聚合能力,
 * 供 {@code ScrmMarketingCampaignService} 参与记录与 ROI 计算使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmMarketingCampaignParticipantRepository
        extends JpaRepository<ScrmMarketingCampaignParticipantEntity, Long>,
        JpaSpecificationExecutor<ScrmMarketingCampaignParticipantEntity> {

    /**
     * 按活动与转化状态聚合转化金额与转化数 (ROI 计算用)。
     *
     * @param campaignId 活动 ID
     * @return Object[]{conversionValueSum, convertCount}
     */
    @Query(value = "SELECT COALESCE(SUM(p.conversionValue), 0), COUNT(p.id) FROM "
                          + "ScrmMarketingCampaignParticipantEntity p WHERE p.campaignId = :campaignId AND p.converted = TRUE")
    Object[] sumConversionByCampaign(
                                     @Param("campaignId") Long campaignId);

    /**
     * 按渠道聚合转化金额与转化数 (渠道效果分析用)。
     *
     * @param startTime 参与时间起始 (含, 可空)
     * @param endTime   参与时间截止 (含, 可空)
     * @return Object[]{channel, conversionValueSum, convertCount, participantCount}
     */
    @Query(value = "SELECT p.channel, COALESCE(SUM(p.conversionValue), 0), SUM(CASE WHEN p.converted = TRUE THEN 1 "
                          + "ELSE 0 END), COUNT(p.id) FROM ScrmMarketingCampaignParticipantEntity p WHERE (:startTime IS NULL "
                          + "OR p.participatedAt >= :startTime) AND (:endTime IS NULL OR p.participatedAt <= :endTime) GROUP "
                          + "BY p.channel")
    List<Object[]> aggregateByChannel(
                                       @Param("startTime") LocalDateTime startTime,
                                       @Param("endTime") LocalDateTime endTime);

    /**
     * 批量更新参与记录的转化状态与转化金额 (避免乐观锁冲突, 直接 SQL 更新)。
     *
     * @param id              参与记录 ID
     * @param converted       是否转化
     * @param conversionValue 转化金额
     * @param convertedAt     转化时间
     * @return 受影响行数 (1 表示更新成功)
     */
    @Modifying
    @Query(value = "UPDATE ScrmMarketingCampaignParticipantEntity p SET p.converted = :converted, p.conversionValue"
                          + "= :conversionValue, p.convertedAt = :convertedAt WHERE p.id = :id")
    int updateConversion(@Param("id") Long id,
                         @Param("converted") Boolean converted,
                         @Param("conversionValue") Double conversionValue,
                         @Param("convertedAt") LocalDateTime convertedAt);
}
