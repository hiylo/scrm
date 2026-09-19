/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAttributionConversionRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmAttributionConversionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * SCRM 营销效果归因转化数据访问层。
 * <p>
 * 提供按订单号查询转化, 时间范围内已归因转化的渠道/触点类型/活动汇总, 转化统计
 * (总转化 / 总触点 / 平均触点数 / 平均转化时长), 转化趋势等聚合查询,
 * 供 {@code ScrmAttributionService} 归因计算与报告生成使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmAttributionConversionRepository extends JpaRepository<ScrmAttributionConversionEntity, Long>,
        JpaSpecificationExecutor<ScrmAttributionConversionEntity> {

    /**
     * 按与订单号查询转化 (订单号在可重复, 取最近一条)。
     *
     * @param orderId  订单号
     * @return 转化 (可能为空)
     */
    Optional<ScrmAttributionConversionEntity> findFirstByOrderIdOrderByConversionTimeDesc(String orderId);

    /**
     * 区间内已归因转化数与总转化价值。
     *
     * @param startTime 转化时间起点 (含, 可空)
     * @param endTime   转化时间终点 (含, 可空)
     * @return Object[]: [conversionCount, totalValue, totalTouchpoints, totalAttributed]
     */
    @Query(value = "SELECT COUNT(c), COALESCE(SUM(c.conversionValue), 0), COALESCE(SUM(c.totalTouchpoints), 0),"
                          + "COALESCE(SUM(c.attributedTouchpoints), 0) FROM ScrmAttributionConversionEntity c WHERE "
                          + "(:startTime IS NULL OR c.conversionTime >= :startTime) AND (:endTime IS NULL OR c.conversionTime"
                          + "<= :endTime)")
    Object[] aggregateConversionStats(
                                      @Param("startTime") LocalDateTime startTime,
                                      @Param("endTime") LocalDateTime endTime);

    /**
     * 区间内已归因转化的平均转化耗时 (小时) 与平均触点数。
     *
     * @param startTime 转化时间起点 (含, 可空)
     * @param endTime   转化时间终点 (含, 可空)
     * @return Object[]: [avgTimeToConversionHours, avgTotalTouchpoints, avgAttributedTouchpoints]
     */
    @Query(value = "SELECT AVG(c.timeToConversionHours), AVG(c.totalTouchpoints), AVG(c.attributedTouchpoints) FROM "
                          + "ScrmAttributionConversionEntity c WHERE c.attributedAt IS NOT NULL AND (:startTime IS NULL OR "
                          + "c.conversionTime >= :startTime) AND (:endTime IS NULL OR c.conversionTime <= :endTime)")
    Object[] aggregateAttributionAverages(
                                          @Param("startTime") LocalDateTime startTime,
                                          @Param("endTime") LocalDateTime endTime);

    /**
     * 按日期统计转化数与转化价值 (转化趋势)。
     *
     * @param start    区间起点 (含)
     * @return Object[] 列表: [date(yyyy-MM-dd), conversionCount, totalValue]
     */
    @Query(value = "SELECT to_char(c.conversion_time, 'YYYY-MM-DD') AS d, COUNT(*) AS c,"
                          + "COALESCE(SUM(c.conversion_value), 0) AS v FROM scrm.scrm_attribution_conversion c WHERE "
                          + "c.conversion_time >= :start GROUP BY d ORDER BY d ASC",
            nativeQuery = true)
    List<Object[]> conversionTrendByDay(
                                         @Param("start") LocalDateTime start);

    /**
     * 按转化类型统计转化数与转化价值 (区间内)。
     *
     * @param startTime 转化时间起点 (含, 可空)
     * @param endTime   转化时间终点 (含, 可空)
     * @return Object[] 列表: [conversionType, conversionCount, totalValue]
     */
    @Query(value = "SELECT c.conversionType, COUNT(c), COALESCE(SUM(c.conversionValue), 0) FROM "
                          + "ScrmAttributionConversionEntity c WHERE (:startTime IS NULL OR c.conversionTime >= :startTime) "
                          + "AND (:endTime IS NULL OR c.conversionTime <= :endTime) GROUP BY c.conversionType ORDER BY "
                          + "COUNT(c) DESC")
    List<Object[]> aggregateByConversionType(
                                              @Param("startTime") LocalDateTime startTime,
                                              @Param("endTime") LocalDateTime endTime);
}
