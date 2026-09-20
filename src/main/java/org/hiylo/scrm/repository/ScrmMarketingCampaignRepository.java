/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMarketingCampaignRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmMarketingCampaignEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 营销活动数据访问层。
 * <p>
 * {@code ScrmMarketingCampaignService} 管理与统计引擎使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmMarketingCampaignRepository extends JpaRepository<ScrmMarketingCampaignEntity, Long>,
        JpaSpecificationExecutor<ScrmMarketingCampaignEntity> {

    /**
     * 按状态聚合活动数 (统计用)。
     *
     * @param startTime 创建时间起始 (含, 可空)
     * @param endTime   创建时间截止 (含, 可空)
     * @return Object[]{status, count}
     */
    @Query(value = "SELECT c.status, COUNT(c.id) FROM ScrmMarketingCampaignEntity c WHERE (:startTime IS NULL OR "
                          + "c.createTime >= :startTime) AND (:endTime IS NULL OR c.createTime <= :endTime) GROUP BY c.status")
    List<Object[]> countByStatus(
                                 @Param("startTime") LocalDateTime startTime,
                                 @Param("endTime") LocalDateTime endTime);

    /**
     * 计算实际花费总额 (统计用)。
     *
     * @param startTime 创建时间起始 (含, 可空)
     * @param endTime   创建时间截止 (含, 可空)
     * @return 实际花费总额 (无数据返回 null)
     */
    @Query(value = "SELECT COALESCE(SUM(c.actualCost), 0) FROM ScrmMarketingCampaignEntity c WHERE (:startTime IS "
                          + "NULL OR c.createTime >= :startTime) AND (:endTime IS NULL OR c.createTime <= :endTime)")
    Double sumActualCost(
                         @Param("startTime") LocalDateTime startTime,
                         @Param("endTime") LocalDateTime endTime);
}
