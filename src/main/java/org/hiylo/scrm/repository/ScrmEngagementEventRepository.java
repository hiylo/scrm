/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmEngagementEventRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmEngagementEventEntity;
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
 * SCRM 互动行为事件数据访问层。
 * <p>
 * 提供按客户加载已计入评分的事件 (供 {@code ScrmEngagementScoreService.calculateScore} 汇总),
 * 按规则 + 客户统计指定时间区间内已计分事件数 (供每日/每周/每月上限校验), 以及按客户分页查询事件。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmEngagementEventRepository extends JpaRepository<ScrmEngagementEventEntity, Long>,
        JpaSpecificationExecutor<ScrmEngagementEventEntity> {

    /**
     * 按客户加载所有已计入评分的事件, 按 eventTime 升序 (供评分汇总计算)。
     *
     * @param customerId 客户 ID
     * @return 事件列表 (eventTime ASC)
     */
    List<ScrmEngagementEventEntity> findByCustomerIdAndProcessedTrueOrderByEventTimeAsc(Long customerId);

    /**
     * 按客户分页查询事件, 按 eventTime 倒序。
     *
     * @param customerId 客户 ID
     * @param pageable   分页参数
     * @return 事件分页结果
     */
    Page<ScrmEngagementEventEntity> findByCustomerIdOrderByEventTimeDesc(Long customerId, Pageable pageable);

    /**
     * 统计客户通过指定规则在 [start, end) 区间内已计分的事件数。
     * <p>用于每日/每周/每月得分上限校验。</p>
     *
     * @param customerId 客户 ID
     * @param ruleId     规则 ID
     * @param start      区间起点 (含)
     * @param end        区间终点 (不含)
     * @return 事件数 (无记录返回 0)
     */
    @Query(value = "SELECT COUNT(e) FROM ScrmEngagementEventEntity e WHERE e.customerId = :customerId AND e.ruleId ="
                          + ":ruleId AND e.processed = true AND e.eventTime >= :start AND e.eventTime < :end")
    long countByRuleAndCustomerInRange(
                                       @Param("customerId") Long customerId,
                                       @Param("ruleId") Long ruleId,
                                       @Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end);

    /**
     * 统计账号下指定时间区间内的事件数 (供互动统计)。
     *
     * @param start    区间起点 (含, 可空)
     * @param end      区间终点 (不含, 可空)
     * @return 事件数
     */
    @Query(value = "SELECT COUNT(e) FROM ScrmEngagementEventEntity e WHERE (:start IS NULL OR e.eventTime >= :start) "
                          + "AND (:end IS NULL OR e.eventTime < :end)")
    long countInRange(
                              @Param("start") LocalDateTime start,
                              @Param("end") LocalDateTime end);

    /**
     * 按行为类型统计事件数 (供行为统计)。
     *
     * @param start    区间起点 (含, 可空)
     * @param end      区间终点 (不含, 可空)
     * @return Object[] 列表: [behaviorType, eventCount]
     */
    @Query(value = "SELECT e.behaviorType, COUNT(e) FROM ScrmEngagementEventEntity e WHERE (:start IS NULL OR "
                          + "e.eventTime >= :start) AND (:end IS NULL OR e.eventTime < :end) GROUP BY e.behaviorType ORDER BY "
                          + "COUNT(e) DESC")
    List<Object[]> countByBehaviorType(
                                       @Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end);

    /**
     * 按渠道统计事件数 (供渠道统计)。
     *
     * @param start    区间起点 (含, 可空)
     * @param end      区间终点 (不含, 可空)
     * @return Object[] 列表: [channel, eventCount]
     */
    @Query(value = "SELECT e.channel, COUNT(e) FROM ScrmEngagementEventEntity e WHERE (:start IS NULL OR e.eventTime"
                          + ">= :start) AND (:end IS NULL OR e.eventTime < :end) GROUP BY e.channel ORDER BY COUNT(e) DESC")
    List<Object[]> countByChannel(
                                  @Param("start") LocalDateTime start,
                                  @Param("end") LocalDateTime end);

    /**
     * 按日期统计事件数 (供互动趋势)。
     *
     * @param start    区间起点 (含)
     * @return Object[] 列表: [date(yyyy-MM-dd), eventCount]
     */
    default List<Object[]> countByDay(LocalDateTime start) {
        return DailyStatsRepository.toRows(DailyStatsRepository.getInstance().countByDay(
                "scrm.scrm_engagement_event e", "e.event_time", "",
                Map.of(), start, null));
    }
}
