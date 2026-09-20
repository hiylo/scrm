/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCalendarEventRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCalendarEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 营销日历事件数据访问层。
 * <p>
 * 供 {@code ScrmMarketingCalendarService} 事件管理与统计使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCalendarEventRepository extends JpaRepository<ScrmCalendarEventEntity, Long>,
        JpaSpecificationExecutor<ScrmCalendarEventEntity> {

    /**
     * 按状态聚合事件数 (统计用)。
     *
     * @param startDate 开始日期起始 (含, 可空)
     * @param endDate   结束日期截止 (含, 可空)
     * @return Object[]{status, count}
     */
    @Query(value = "SELECT e.status, COUNT(e.id) FROM ScrmCalendarEventEntity e WHERE (:startDate IS NULL OR "
                          + "e.startDate >= :startDate) AND (:endDate IS NULL OR e.endDate <= :endDate) GROUP BY e.status")
    List<Object[]> countByStatus(
                                 @Param("startDate") LocalDate startDate,
                                 @Param("endDate") LocalDate endDate);

    /**
     * 按事件类型聚合事件数 (统计用)。
     *
     * @param startDate 开始日期起始 (含, 可空)
     * @param endDate   结束日期截止 (含, 可空)
     * @return Object[]{eventType, count}
     */
    @Query(value = "SELECT e.eventType, COUNT(e.id) FROM ScrmCalendarEventEntity e WHERE (:startDate IS NULL OR "
                          + "e.startDate >= :startDate) AND (:endDate IS NULL OR e.endDate <= :endDate) GROUP BY e.eventType")
    List<Object[]> countByEventType(
                                    @Param("startDate") LocalDate startDate,
                                    @Param("endDate") LocalDate endDate);

    /**
     * 按负责人聚合事件数与预算 (负责人工作量)。
     *
     * @param ownerId   负责人 ID
     * @param startDate 开始日期起始 (含, 可空)
     * @param endDate   结束日期截止 (含, 可空)
     * @return Object[]{ownerId, ownerName, eventCount, totalBudget, totalEstimatedReach}
     */
    @Query(value = "SELECT e.ownerId, e.ownerName, COUNT(e.id), COALESCE(SUM(e.budget), 0),"
                          + "COALESCE(SUM(e.estimatedReach), 0) FROM ScrmCalendarEventEntity e WHERE (:ownerId IS NULL OR "
                          + "e.ownerId = :ownerId) AND (:startDate IS NULL OR e.startDate >= :startDate) AND (:endDate IS "
                          + "NULL OR e.endDate <= :endDate) GROUP BY e.ownerId, e.ownerName")
    List<Object[]> aggregateByOwner(
                                    @Param("ownerId") String ownerId,
                                    @Param("startDate") LocalDate startDate,
                                    @Param("endDate") LocalDate endDate);

    /**
     * 计算预算总额 (统计用)。
     *
     * @param startDate 开始日期起始 (含, 可空)
     * @param endDate   结束日期截止 (含, 可空)
     * @return 预算总额 (无数据返回 null)
     */
    @Query(value = "SELECT COALESCE(SUM(e.budget), 0) FROM ScrmCalendarEventEntity e WHERE (:startDate IS NULL OR "
                          + "e.startDate >= :startDate) AND (:endDate IS NULL OR e.endDate <= :endDate)")
    Double sumBudget(
                     @Param("startDate") LocalDate startDate,
                     @Param("endDate") LocalDate endDate);

    /**
     * 按日期范围查询事件 (用于日/周/月视图), 按开始日期升序。
     *
     * @param rangeStart 范围开始日期 (含)
     * @param rangeEnd   范围结束日期 (含)
     * @return 事件列表
     */
    List<ScrmCalendarEventEntity> findByStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateAsc(LocalDate rangeEnd, LocalDate rangeStart);

    /**
     * 按创建时间范围查询事件 (统计用)。
     *
     * @param startTime 创建时间起始 (含, 可空)
     * @param endTime   创建时间截止 (含, 可空)
     * @return 事件列表
     */
    @Query(value = "SELECT e FROM ScrmCalendarEventEntity e WHERE (:startTime IS NULL OR e.createTime >= :startTime) "
                          + "AND (:endTime IS NULL OR e.createTime <= :endTime)")
    List<ScrmCalendarEventEntity> findByCreateTimeRange(
                                                       @Param("startTime") LocalDateTime startTime,
                                                       @Param("endTime") LocalDateTime endTime);
}
