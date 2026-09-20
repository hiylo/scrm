/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBehaviorTrackRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmBehaviorTrackEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 客户行为轨迹数据访问层。
 * <p>
 * 提供客户行为时间线查询、按行为类型 / 触点分页查询, 以及行为统计 (总事件 / 独立客户 /
 * 行为类型分布 / 触点分布 / 漏斗阶段 / 转化归因 / 行为趋势 / 热力图) 所需的聚合查询,
 * 供 {@code ScrmBehaviorTrackService} 记录与统计分析引擎使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmBehaviorTrackRepository extends JpaRepository<ScrmBehaviorTrackEntity, Long>,
        JpaSpecificationExecutor<ScrmBehaviorTrackEntity> {

    /**
     * 按客户与会话加载行为事件 (按行为时间升序), 供行为路径构建聚合。
     *
     * @param customerId 客户 ID
     * @param sessionId  会话 ID
     * @return 行为列表 (behaviorTime ASC)
     */
    List<ScrmBehaviorTrackEntity> findByCustomerIdAndSessionIdOrderByBehaviorTimeAsc(Long customerId, String sessionId);

    /**
     * 客户行为时间线: 按客户与时间范围查询行为 (按行为时间升序)。
     *
     * @param customerId 客户 ID
     * @param startTime  行为时间起点 (含, 可空)
     * @param endTime    行为时间终点 (含, 可空)
     * @return 行为列表 (behaviorTime ASC)
     */
    @Query(value = "SELECT t FROM ScrmBehaviorTrackEntity t WHERE t.customerId = :customerId AND (:startTime IS NULL "
                          + "OR t.behaviorTime >= :startTime) AND (:endTime IS NULL OR t.behaviorTime <= :endTime) ORDER BY "
                          + "t.behaviorTime ASC")
    List<ScrmBehaviorTrackEntity> findCustomerTimeline(
                                                        @Param("customerId") Long customerId,
                                                        @Param("startTime") LocalDateTime startTime,
                                                        @Param("endTime") LocalDateTime endTime);

    /**
     * 按行为类型分页查询 (按行为时间倒序)。
     *
     * @param behaviorType 行为类型
     * @param startTime   行为时间起点 (含, 可空)
     * @param endTime     行为时间终点 (含, 可空)
     * @param pageable    分页参数
     * @return 行为分页结果
     */
    @Query(value = "SELECT t FROM ScrmBehaviorTrackEntity t WHERE t.behaviorType = :behaviorType AND (:startTime IS "
                          + "NULL OR t.behaviorTime >= :startTime) AND (:endTime IS NULL OR t.behaviorTime <= :endTime) ORDER "
                          + "BY t.behaviorTime DESC")
    Page<ScrmBehaviorTrackEntity> findByType(
                                              @Param("behaviorType") String behaviorType,
                                              @Param("startTime") LocalDateTime startTime,
                                              @Param("endTime") LocalDateTime endTime,
                                              Pageable pageable);

    /**
     * 按触点分页查询 (按行为时间倒序)。
     *
     * @param touchpoint 触点
     * @param startTime  行为时间起点 (含, 可空)
     * @param endTime    行为时间终点 (含, 可空)
     * @param pageable   分页参数
     * @return 行为分页结果
     */
    @Query(value = "SELECT t FROM ScrmBehaviorTrackEntity t WHERE t.touchpoint = :touchpoint AND (:startTime IS NULL "
                          + "OR t.behaviorTime >= :startTime) AND (:endTime IS NULL OR t.behaviorTime <= :endTime) ORDER BY "
                          + "t.behaviorTime DESC")
    Page<ScrmBehaviorTrackEntity> findByTouchpoint(
                                                    @Param("touchpoint") String touchpoint,
                                                    @Param("startTime") LocalDateTime startTime,
                                                    @Param("endTime") LocalDateTime endTime,
                                                    Pageable pageable);

    /**
     * 统计区间内事件总数。
     *
     * @param start    区间起点 (含, 可空)
     * @param end      区间终点 (含, 可空)
     * @return 事件数
     */
    @Query(value = "SELECT COUNT(t) FROM ScrmBehaviorTrackEntity t WHERE (:start IS NULL OR t.behaviorTime >="
                          + ":start) AND (:end IS NULL OR t.behaviorTime <= :end)")
    long countInRange(
                              @Param("start") LocalDateTime start,
                              @Param("end") LocalDateTime end);

    /**
     * 统计区间内转化事件数。
     *
     * @param start    区间起点 (含, 可空)
     * @param end      区间终点 (含, 可空)
     * @return 转化事件数
     */
    @Query(value = "SELECT COUNT(t) FROM ScrmBehaviorTrackEntity t WHERE t.isConversion = true AND (:start IS NULL "
                          + "OR t.behaviorTime >= :start) AND (:end IS NULL OR t.behaviorTime <= :end)")
    long countConversionsInRange(
                                 @Param("start") LocalDateTime start,
                                 @Param("end") LocalDateTime end);

    /**
     * 统计区间内独立客户数。
     *
     * @param start    区间起点 (含, 可空)
     * @param end      区间终点 (含, 可空)
     * @return 独立客户数
     */
    @Query(value = "SELECT COUNT(DISTINCT t.customerId) FROM ScrmBehaviorTrackEntity t WHERE (:start IS NULL OR "
                          + "t.behaviorTime >= :start) AND (:end IS NULL OR t.behaviorTime <= :end)")
    long countDistinctCustomerInRange(
                                      @Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end);

    /**
     * 单触点统计: 事件总数 / 独立客户数 / 转化数 / 最近事件时间 (供触点统计重算)。
     *
     * @param touchpoint 触点
     * @return Object[]: [totalEvents, uniqueCustomers, conversionCount, lastEventTime]
     */
    @Query(value = "SELECT COUNT(t), COUNT(DISTINCT t.customerId), SUM(CASE WHEN t.isConversion = true THEN 1 ELSE 0 "
                          + "END), MAX(t.behaviorTime) FROM ScrmBehaviorTrackEntity t WHERE t.touchpoint = :touchpoint")
    Object[] aggregateSingleTouchpoint(
                                       @Param("touchpoint") String touchpoint);

    /**
     * 按行为类型统计事件数 (行为类型分布)。
     *
     * @param start    区间起点 (含, 可空)
     * @param end      区间终点 (含, 可空)
     * @return Object[] 列表: [behaviorType, eventCount]
     */
    @Query(value = "SELECT t.behaviorType, COUNT(t) FROM ScrmBehaviorTrackEntity t WHERE (:start IS NULL OR "
                          + "t.behaviorTime >= :start) AND (:end IS NULL OR t.behaviorTime <= :end) GROUP BY t.behaviorType "
                          + "ORDER BY COUNT(t) DESC")
    List<Object[]> countByBehaviorType(
                                       @Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end);

    /**
     * 按触点统计事件数 / 独立客户数 / 转化数 (触点效果与归因)。
     *
     * @param start    区间起点 (含, 可空)
     * @param end      区间终点 (含, 可空)
     * @return Object[] 列表: [touchpoint, totalEvents, uniqueCustomers, conversionCount]
     */
    @Query(value = "SELECT t.touchpoint, COUNT(t), COUNT(DISTINCT t.customerId), SUM(CASE WHEN t.isConversion = true "
                          + "THEN 1 ELSE 0 END) FROM ScrmBehaviorTrackEntity t WHERE (:start IS NULL OR t.behaviorTime >="
                          + ":start) AND (:end IS NULL OR t.behaviorTime <= :end) GROUP BY t.touchpoint ORDER BY COUNT(t) "
                          + "DESC")
    List<Object[]> aggregateByTouchpoint(
                                         @Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end);

    /**
     * 按漏斗阶段统计独立客户数 (漏斗统计)。
     *
     * @param start    区间起点 (含, 可空)
     * @param end      区间终点 (含, 可空)
     * @return Object[] 列表: [funnelStage, distinctCustomerCount]
     */
    @Query(value = "SELECT t.funnelStage, COUNT(DISTINCT t.customerId) FROM ScrmBehaviorTrackEntity t WHERE "
                          + "t.funnelStage IS NOT NULL AND (:start IS NULL OR t.behaviorTime >= :start) AND (:end IS NULL OR "
                          + "t.behaviorTime <= :end) GROUP BY t.funnelStage")
    List<Object[]> countDistinctCustomerByFunnelStage(
                                                       @Param("start") LocalDateTime start,
                                                       @Param("end") LocalDateTime end);

    /**
     * 按日期统计事件数 (行为趋势)。
     *
     * @param start    区间起点 (含)
     * @return Object[] 列表: [date(yyyy-MM-dd), eventCount]
     */
    default List<Object[]> countByDay(LocalDateTime start) {
        return DailyStatsRepository.toRows(DailyStatsRepository.getInstance().countByDay(
                "scrm.scrm_behavior_track t", "t.behavior_time", "",
                Map.of(), start, null));
    }

    /**
     * 按行为类型 + 日期统计事件数 (按类型的行为趋势)。
     *
     * @param behaviorType 行为类型
     * @param start        区间起点 (含)
     * @return Object[] 列表: [date(yyyy-MM-dd), eventCount]
     */
    default List<Object[]> countByDayAndType(String behaviorType, LocalDateTime start) {
        return DailyStatsRepository.toRows(DailyStatsRepository.getInstance().countByDay(
                "scrm.scrm_behavior_track t", "t.behavior_time",
                "",
                Map.of("behaviorType", behaviorType), start, null));
    }

    /**
     * 热力图数据: 按页面 URL + 时段 (YYYY-MM-DD HH24) 聚合事件数。
     *
     * @param touchpoint 触点 (可空, 为空时忽略)
     * @param start      区间起点 (含, 可空)
     * @param end        区间终点 (含, 可空)
     * @return Object[] 列表: [pageUrl, hourBucket, eventCount]
     */
    @Query(value = "SELECT t.page_url AS p, to_char(t.behavior_time, 'YYYY-MM-DD HH24') AS h, COUNT(*) AS c FROM "
                          + "scrm.scrm_behavior_track t WHERE (:touchpoint IS NULL OR t.touchpoint = :touchpoint) AND (:start "
                          + "IS NULL OR t.behavior_time >= :start) AND (:end IS NULL OR t.behavior_time <= :end) AND "
                          + "t.page_url IS NOT NULL GROUP BY p, h ORDER BY c DESC",
            nativeQuery = true)
    List<Object[]> countByPageAndHour(
                                      @Param("touchpoint") String touchpoint,
                                      @Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end);
}
