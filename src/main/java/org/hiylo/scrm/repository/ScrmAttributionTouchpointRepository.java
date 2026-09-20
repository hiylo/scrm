/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAttributionTouchpointRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmAttributionTouchpointEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 营销效果归因触点数据访问层。
 * <p>
 * 提供客户触点链查询 (按触点时间升序, 供归因计算读取客户旅程), 回溯窗口内触点查询,
 * 已归因触点聚合统计 (按渠道 / 触点类型 / 活动汇总归因价值与权重), 以及触点趋势查询,
 * 供 {@code ScrmAttributionService} 归因计算与报告生成使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmAttributionTouchpointRepository extends JpaRepository<ScrmAttributionTouchpointEntity, Long>,
        JpaSpecificationExecutor<ScrmAttributionTouchpointEntity> {

    /**
     * 客户触点链: 按客户与时间范围查询触点 (按触点时间升序), 供归因计算读取客户旅程。
     *
     * @param customerId 客户 ID
     * @param startTime  触点时间起点 (含, 可空)
     * @param endTime    触点时间终点 (含, 可空)
     * @return 触点列表 (touchpointTime ASC)
     */
    @Query(value = "SELECT t FROM ScrmAttributionTouchpointEntity t WHERE t.customerId = :customerId AND (:startTime "
                          + "IS NULL OR t.touchpointTime >= :startTime) AND (:endTime IS NULL OR t.touchpointTime <="
                          + ":endTime) ORDER BY t.touchpointTime ASC")
    List<ScrmAttributionTouchpointEntity> findCustomerTouchpointChain(
                                                                      @Param("customerId") Long customerId,
                                                                      @Param("startTime") LocalDateTime startTime,
                                                                      @Param("endTime") LocalDateTime endTime);

    /**
     * 按渠道统计归因价值 (已归因触点): channel, totalAttributionValue, totalWeight, touchpointCount。
     *
     * @param startTime 触点时间起点 (含, 可空)
     * @param endTime   触点时间终点 (含, 可空)
     * @return Object[] 列表: [channel, totalAttributionValue, totalWeight, touchpointCount]
     */
    @Query(value = "SELECT t.channel, COALESCE(SUM(t.attributionValue), 0), COALESCE(SUM(t.attributionWeight), 0),"
                          + "COUNT(t) FROM ScrmAttributionTouchpointEntity t WHERE t.isAttributed = true AND (:startTime IS "
                          + "NULL OR t.touchpointTime >= :startTime) AND (:endTime IS NULL OR t.touchpointTime <= :endTime) "
                          + "GROUP BY t.channel ORDER BY SUM(t.attributionValue) DESC")
    List<Object[]> aggregateAttributionByChannel(
                                                  @Param("startTime") LocalDateTime startTime,
                                                  @Param("endTime") LocalDateTime endTime);

    /**
     * 按触点类型统计归因价值 (已归因触点)。
     *
     * @param startTime 触点时间起点 (含, 可空)
     * @param endTime   触点时间终点 (含, 可空)
     * @return Object[] 列表: [touchpointType, totalAttributionValue, totalWeight, touchpointCount]
     */
    @Query(value = "SELECT t.touchpointType, COALESCE(SUM(t.attributionValue), 0),"
                          + "COALESCE(SUM(t.attributionWeight), 0), COUNT(t) FROM ScrmAttributionTouchpointEntity t WHERE "
                          + "t.isAttributed = true AND (:startTime IS NULL OR t.touchpointTime >= :startTime) AND (:endTime "
                          + "IS NULL OR t.touchpointTime <= :endTime) GROUP BY t.touchpointType ORDER BY "
                          + "SUM(t.attributionValue) DESC")
    List<Object[]> aggregateAttributionByTouchpointType(
                                                         @Param("startTime") LocalDateTime startTime,
                                                         @Param("endTime") LocalDateTime endTime);

    /**
     * 按营销活动统计归因价值 (已归因触点)。
     *
     * @param startTime 触点时间起点 (含, 可空)
     * @param endTime   触点时间终点 (含, 可空)
     * @return Object[] 列表: [campaignId, campaignName, totalAttributionValue, totalWeight, touchpointCount]
     */
    @Query(value = "SELECT t.campaignId, MAX(t.campaignName), COALESCE(SUM(t.attributionValue), 0),"
                          + "COALESCE(SUM(t.attributionWeight), 0), COUNT(t) FROM ScrmAttributionTouchpointEntity t WHERE "
                          + "t.isAttributed = true AND t.campaignId IS NOT NULL AND (:startTime IS NULL OR t.touchpointTime"
                          + ">= :startTime) AND (:endTime IS NULL OR t.touchpointTime <= :endTime) GROUP BY t.campaignId "
                          + "ORDER BY SUM(t.attributionValue) DESC")
    List<Object[]> aggregateAttributionByCampaign(
                                                   @Param("startTime") LocalDateTime startTime,
                                                   @Param("endTime") LocalDateTime endTime);

    /**
     * 高价值触点: 已归因触点按归因价值倒序分页 (供 getTopTouchpoints)。
     *
     * @param pageable 分页参数
     * @return 触点分页结果
     */
    Page<ScrmAttributionTouchpointEntity> findByIsAttributedTrueOrderByAttributionValueDesc(Pageable pageable);

    /**
     * 按日期统计已归因触点的归因价值与权重 (归因趋势)。
     *
     * @param start    区间起点 (含)
     * @return Object[] 列表: [date(yyyy-MM-dd), totalValue, totalWeight, count]
     */
    @Query(value = "SELECT to_char(t.touchpoint_time, 'YYYY-MM-DD') AS d, COALESCE(SUM(t.attribution_value), 0) AS "
                          + "v, COALESCE(SUM(t.attribution_weight), 0) AS w, COUNT(*) AS c FROM "
                          + "scrm.scrm_attribution_touchpoint t WHERE t.is_attributed = true AND t.touchpoint_time >= :start "
                          + "GROUP BY d ORDER BY d ASC",
            nativeQuery = true)
    List<Object[]> attributionTrendByDay(
                                          @Param("start") LocalDateTime start);

    /**
     * 按渠道统计全部触点 (含未归因) 的事件数与归因数 (触点效果分析)。
     *
     * @param startTime 触点时间起点 (含, 可空)
     * @param endTime   触点时间终点 (含, 可空)
     * @return Object[] 列表: [channel, totalEvents, attributedEvents, totalValue]
     */
    @Query(value = "SELECT t.channel, COUNT(t), SUM(CASE WHEN t.isAttributed = true THEN 1 ELSE 0 END),"
                          + "COALESCE(SUM(t.attributionValue), 0) FROM ScrmAttributionTouchpointEntity t WHERE (:startTime IS "
                          + "NULL OR t.touchpointTime >= :startTime) AND (:endTime IS NULL OR t.touchpointTime <= :endTime) "
                          + "GROUP BY t.channel ORDER BY COUNT(t) DESC")
    List<Object[]> aggregateTouchpointEffectByChannel(
                                                       @Param("startTime") LocalDateTime startTime,
                                                       @Param("endTime") LocalDateTime endTime);
}
