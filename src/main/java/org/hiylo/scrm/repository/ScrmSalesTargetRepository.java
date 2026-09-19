/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSalesTargetRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmSalesTargetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * SCRM 销售目标数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmSalesTargetRepository extends JpaRepository<ScrmSalesTargetEntity, Long>,
        JpaSpecificationExecutor<ScrmSalesTargetEntity> {

    /**
     * 按 ID、目标对象类型、目标对象 ID 与周期类型查询某对象某周期目标。
     *
     * @param targetType 目标对象类型
     * @param targetId   目标对象 ID
     * @param periodType 周期类型
     * @return 目标列表
     */
    List<ScrmSalesTargetEntity> findByTargetTypeAndTargetIdAndPeriodType(String targetType, String targetId, String periodType);

    /**
     * 按周期范围查询目标列表 (用于概览统计)。
     *
     * @param periodStart  周期开始 (含)
     * @param periodEnd    周期结束 (含)
     * @return 目标列表
     */
    List<ScrmSalesTargetEntity> findByPeriodStartGreaterThanEqualAndPeriodEndLessThanEqual(LocalDate periodStart, LocalDate periodEnd);

    /**
     * 按周期类型统计达成率分布 (用于达成率分布统计)。
     *
     * @param periodStart  周期开始 (含)
     * @param periodEnd    周期结束 (含)
     * @return Object[]{achievementRateBucket, count}
     */
    @Query(value = "SELECT CASE WHEN achievement_rate >= 100 THEN 'EXCELLENT' WHEN achievement_rate >= 80 THEN"
                          + "'GOOD' WHEN achievement_rate >= 60 THEN 'NORMAL' WHEN achievement_rate >= 30 THEN 'BELOW' ELSE"
                          + "'POOR' END AS bucket, COUNT(*) FROM scrm.scrm_sales_target WHERE period_start >= :periodStart "
                          + "AND period_end <= :periodEnd AND status <> 'ARCHIVED' GROUP BY bucket",
            nativeQuery = true)
    List<Object[]> achievementRateDistribution(
                                                 @Param("periodStart") LocalDate periodStart,
                                                 @Param("periodEnd") LocalDate periodEnd);
}
