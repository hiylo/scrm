/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmInteractionPlanRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmInteractionPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * SCRM 客户互动计划数据访问层。
 * <p>
 * 供 {@code ScrmInteractionCalendarService} 计划管理与统计使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmInteractionPlanRepository extends JpaRepository<ScrmInteractionPlanEntity, Long>,
        JpaSpecificationExecutor<ScrmInteractionPlanEntity> {

    /**
     * 按计划编码查询 (数据隔离)。
     *
     * @param planCode 计划编码
     * @return 计划实体 (不存在返回 empty)
     */
    Optional<ScrmInteractionPlanEntity> findByPlanCode(String planCode);

    /**
     * 按状态聚合计划数 (统计用)。
     *
     * @param startTime 开始时间 (含, 可空)
     * @param endTime   结束时间 (含, 可空)
     * @return Object[]{status, count}
     */
    @Query(value = "SELECT p.status, COUNT(p.id) FROM ScrmInteractionPlanEntity p WHERE (:startTime IS NULL OR "
                          + "p.createTime >= :startTime) AND (:endTime IS NULL OR p.createTime <= :endTime) GROUP BY p.status")
    List<Object[]> countByStatus(
                                 @Param("startTime") LocalDateTime startTime,
                                 @Param("endTime") LocalDateTime endTime);

    /**
     * 按互动类型聚合计划数 (统计用)。
     *
     * @param startTime 开始时间 (含, 可空)
     * @param endTime   结束时间 (含, 可空)
     * @return Object[]{interactionType, count}
     */
    @Query(value = "SELECT p.interactionType, COUNT(p.id) FROM ScrmInteractionPlanEntity p WHERE (:startTime IS NULL "
                          + "OR p.createTime >= :startTime) AND (:endTime IS NULL OR p.createTime <= :endTime) GROUP BY "
                          + "p.interactionType")
    List<Object[]> countByInteractionType(
                                          @Param("startTime") LocalDateTime startTime,
                                          @Param("endTime") LocalDateTime endTime);

    /**
     * 按互动方式聚合计划数 (统计用)。
     *
     * @param startTime 开始时间 (含, 可空)
     * @param endTime   结束时间 (含, 可空)
     * @return Object[]{interactionMethod, count}
     */
    @Query(value = "SELECT p.interactionMethod, COUNT(p.id) FROM ScrmInteractionPlanEntity p WHERE (:startTime IS "
                          + "NULL OR p.createTime >= :startTime) AND (:endTime IS NULL OR p.createTime <= :endTime) GROUP BY "
                          + "p.interactionMethod")
    List<Object[]> countByInteractionMethod(
                                            @Param("startTime") LocalDateTime startTime,
                                            @Param("endTime") LocalDateTime endTime);

    /**
     * 按互动结果聚合计划数 (统计用, 仅已完成)。
     *
     * @param startTime 开始时间 (含, 可空)
     * @param endTime   结束时间 (含, 可空)
     * @return Object[]{outcome, count}
     */
    @Query(value = "SELECT p.outcome, COUNT(p.id) FROM ScrmInteractionPlanEntity p WHERE p.outcome IS NOT NULL AND "
                          + "(:startTime IS NULL OR p.createTime >= :startTime) AND (:endTime IS NULL OR p.createTime <="
                          + ":endTime) GROUP BY p.outcome")
    List<Object[]> countByOutcome(
                                  @Param("startTime") LocalDateTime startTime,
                                  @Param("endTime") LocalDateTime endTime);

    /**
     * 按负责人聚合计划状态 (统计用)。
     *
     * @param ownerId   负责人 ID
     * @param startTime 开始时间 (含, 可空)
     * @param endTime   结束时间 (含, 可空)
     * @return Object[]{status, count}
     */
    @Query(value = "SELECT p.status, COUNT(p.id) FROM ScrmInteractionPlanEntity p WHERE p.ownerId = :ownerId AND "
                          + "(:startTime IS NULL OR p.createTime >= :startTime) AND (:endTime IS NULL OR p.createTime <="
                          + ":endTime) GROUP BY p.status")
    List<Object[]> countByOwnerAndStatus(
                                         @Param("ownerId") String ownerId,
                                         @Param("startTime") LocalDateTime startTime,
                                         @Param("endTime") LocalDateTime endTime);

    /**
     * 查询已完成且有实际开始/结束时间的计划 (用于平均时长统计)。
     *
     * @param startTime 开始时间 (含, 可空)
     * @param endTime   结束时间 (含, 可空)
     * @return 已完成计划列表
     */
    @Query(value = "SELECT p FROM ScrmInteractionPlanEntity p WHERE p.actualStart IS NOT NULL AND p.actualEnd IS NOT "
                          + "NULL AND (:startTime IS NULL OR p.createTime >= :startTime) AND (:endTime IS NULL OR "
                          + "p.createTime <= :endTime)")
    List<ScrmInteractionPlanEntity> findCompletedWithActualTime(
                                                                @Param("startTime") LocalDateTime startTime,
                                                                @Param("endTime") LocalDateTime endTime);

    /**
     * 按月聚合计划数 (趋势统计用)。
     *
     * @param startDate 开始日期 (含)
     * @param endDate   结束日期 (含)
     * @return Object[]{yearMonth, count}
     */
    @Query(value = "SELECT CONCAT(YEAR(p.scheduledStart), '-', CASE WHEN MONTH(p.scheduledStart) < 10 THEN "
                          + "CONCAT('0', MONTH(p.scheduledStart)) ELSE CAST(MONTH(p.scheduledStart) AS STRING) END),"
                          + "COUNT(p.id) FROM ScrmInteractionPlanEntity p WHERE p.scheduledStart IS NOT NULL AND "
                          + "CAST(p.scheduledStart AS date) >= :startDate AND CAST(p.scheduledStart AS date) <= :endDate "
                          + "GROUP BY CONCAT(YEAR(p.scheduledStart), '-', CASE WHEN MONTH(p.scheduledStart) < 10 THEN "
                          + "CONCAT('0', MONTH(p.scheduledStart)) ELSE CAST(MONTH(p.scheduledStart) AS STRING) END) ORDER BY "
                          + "1 ASC")
    List<Object[]> countByMonth(
                                @Param("startDate") LocalDate startDate,
                                @Param("endDate") LocalDate endDate);

    /**
     * 按小时聚合计划数 (最佳时间段统计用, 基于 scheduledStart 的小时)。
     *
     * @param ownerId  负责人 ID (可空, 为空则查询全部)
     * @return Object[]{hour, count}
     */
    @Query(value = "SELECT HOUR(p.scheduledStart), COUNT(p.id) FROM ScrmInteractionPlanEntity p WHERE "
                          + "p.scheduledStart IS NOT NULL AND (:ownerId IS NULL OR p.ownerId = :ownerId) GROUP BY "
                          + "HOUR(p.scheduledStart) ORDER BY HOUR(p.scheduledStart) ASC")
    List<Object[]> countByHour(
                               @Param("ownerId") String ownerId);

    /**
     * 按日聚合计划数 (热力图统计用)。
     *
     * @param startDate 开始日期 (含)
     * @param endDate   结束日期 (含)
     * @param ownerId   负责人 ID (可空, 为空则查询全部)
     * @return Object[]{scheduledDate, count}
     */
    @Query(value = "SELECT CAST(p.scheduledStart AS date), COUNT(p.id) FROM ScrmInteractionPlanEntity p WHERE "
                          + "p.scheduledStart IS NOT NULL AND CAST(p.scheduledStart AS date) >= :startDate AND "
                          + "CAST(p.scheduledStart AS date) <= :endDate AND (:ownerId IS NULL OR p.ownerId = :ownerId) GROUP "
                          + "BY CAST(p.scheduledStart AS date) ORDER BY CAST(p.scheduledStart AS date) ASC")
    List<Object[]> countByDayHeatmap(
                                     @Param("startDate") LocalDate startDate,
                                     @Param("endDate") LocalDate endDate,
                                     @Param("ownerId") String ownerId);

    /**
     * 按客户聚合已完成计划数 (客户互动统计用)。
     *
     * @param customerId 客户 ID
     * @return Object[]{status, count}
     */
    @Query(value = "SELECT p.status, COUNT(p.id) FROM ScrmInteractionPlanEntity p WHERE p.customerId = :customerId "
                          + "GROUP BY p.status")
    List<Object[]> countByCustomerAndStatus(
                                            @Param("customerId") Long customerId);
}
