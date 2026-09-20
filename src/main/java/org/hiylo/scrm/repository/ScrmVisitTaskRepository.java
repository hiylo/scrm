/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmVisitTaskRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmVisitTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * SCRM 客户回访任务数据访问层。
 * <p>
 * 以及回访统计聚合能力, 供 {@code ScrmVisitService} 任务管理与统计使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmVisitTaskRepository extends JpaRepository<ScrmVisitTaskEntity, Long>,
        JpaSpecificationExecutor<ScrmVisitTaskEntity> {

    /**
     * 按任务编号查询 (数据隔离)。
     *
     * @param taskNo   任务编号
     * @return 任务实体 (不存在返回 empty)
     */
    Optional<ScrmVisitTaskEntity> findByTaskNo(String taskNo);

    /**
     * 按状态聚合任务数 (统计用)。
     *
     * @param startTime 开始时间 (含, 可空)
     * @param endTime   结束时间 (含, 可空)
     * @return Object[]{status, count}
     */
    @Query(value = "SELECT t.status, COUNT(t.id) FROM ScrmVisitTaskEntity t WHERE (:startTime IS NULL OR "
                          + "t.createTime >= :startTime) AND (:endTime IS NULL OR t.createTime <= :endTime) GROUP BY t.status")
    List<Object[]> countByStatus(
                                 @Param("startTime") LocalDateTime startTime,
                                 @Param("endTime") LocalDateTime endTime);

    /**
     * 按回访类型聚合任务数 (统计用)。
     *
     * @param startTime 开始时间 (含, 可空)
     * @param endTime   结束时间 (含, 可空)
     * @return Object[]{visitType, count}
     */
    @Query(value = "SELECT t.visitType, COUNT(t.id) FROM ScrmVisitTaskEntity t WHERE (:startTime IS NULL OR "
                          + "t.createTime >= :startTime) AND (:endTime IS NULL OR t.createTime <= :endTime) GROUP BY "
                          + "t.visitType")
    List<Object[]> countByVisitType(
                                    @Param("startTime") LocalDateTime startTime,
                                    @Param("endTime") LocalDateTime endTime);

    /**
     * 按回访结果聚合任务数 (统计用)。
     *
     * @param startTime 开始时间 (含, 可空)
     * @param endTime   结束时间 (含, 可空)
     * @return Object[]{visitOutcome, count}
     */
    @Query(value = "SELECT t.visitOutcome, COUNT(t.id) FROM ScrmVisitTaskEntity t WHERE t.visitOutcome IS NOT NULL "
                          + "AND (:startTime IS NULL OR t.createTime >= :startTime) AND (:endTime IS NULL OR t.createTime <="
                          + ":endTime) GROUP BY t.visitOutcome")
    List<Object[]> countByVisitOutcome(
                                       @Param("startTime") LocalDateTime startTime,
                                       @Param("endTime") LocalDateTime endTime);

    /**
     * 计算已完成任务的平均满意度 (统计用)。
     *
     * @param startTime 开始时间 (含, 可空)
     * @param endTime   结束时间 (含, 可空)
     * @return 平均满意度 (无数据返回 null)
     */
    @Query(value = "SELECT COALESCE(AVG(t.satisfactionScore), 0) FROM ScrmVisitTaskEntity t WHERE "
                          + "t.satisfactionScore IS NOT NULL AND (:startTime IS NULL OR t.createTime >= :startTime) AND "
                          + "(:endTime IS NULL OR t.createTime <= :endTime)")
    Double avgSatisfactionScore(
                                @Param("startTime") LocalDateTime startTime,
                                @Param("endTime") LocalDateTime endTime);

    /**
     * 计算指定计划已完成任务的平均满意度。
     *
     * @param planId   计划 ID
     * @return 平均满意度 (无数据返回 null)
     */
    @Query(value = "SELECT COALESCE(AVG(t.satisfactionScore), 0) FROM ScrmVisitTaskEntity t WHERE t.planId = :planId "
                          + "AND t.satisfactionScore IS NOT NULL")
    Double avgSatisfactionScoreByPlan(
                                      @Param("planId") Long planId);

    /**
     * 按计划 ID 聚合任务状态 (统计用)。
     *
     * @param planId   计划 ID
     * @return Object[]{status, count}
     */
    @Query(value = "SELECT t.status, COUNT(t.id) FROM ScrmVisitTaskEntity t WHERE t.planId = :planId GROUP BY "
                          + "t.status")
    List<Object[]> countByPlanAndStatus(
                                        @Param("planId") Long planId);

    /**
     * 按负责人聚合任务统计 (统计用)。
     *
     * @param assigneeId 负责人 ID
     * @param startTime  开始时间 (含, 可空)
     * @param endTime    结束时间 (含, 可空)
     * @return Object[]{status, count}
     */
    @Query(value = "SELECT t.status, COUNT(t.id) FROM ScrmVisitTaskEntity t WHERE t.assignedTo = :assigneeId AND "
                          + "(:startTime IS NULL OR t.createTime >= :startTime) AND (:endTime IS NULL OR t.createTime <="
                          + ":endTime) GROUP BY t.status")
    List<Object[]> countByAssigneeAndStatus(
                                            @Param("assigneeId") String assigneeId,
                                            @Param("startTime") LocalDateTime startTime,
                                            @Param("endTime") LocalDateTime endTime);

    /**
     * 查询指定负责人在指定日期区间的已完成任务的平均满意度。
     *
     * @param assigneeId 负责人 ID
     * @param startTime  开始时间 (含, 可空)
     * @param endTime    结束时间 (含, 可空)
     * @return 平均满意度 (无数据返回 null)
     */
    @Query(value = "SELECT COALESCE(AVG(t.satisfactionScore), 0) FROM ScrmVisitTaskEntity t WHERE t.assignedTo ="
                          + ":assigneeId AND t.satisfactionScore IS NOT NULL AND (:startTime IS NULL OR t.createTime >="
                          + ":startTime) AND (:endTime IS NULL OR t.createTime <= :endTime)")
    Double avgSatisfactionScoreByAssignee(
                                          @Param("assigneeId") String assigneeId,
                                          @Param("startTime") LocalDateTime startTime,
                                          @Param("endTime") LocalDateTime endTime);

    /**
     * 按日聚合任务数 (趋势统计用)。
     *
     * @param startDate 开始日期 (含)
     * @param endDate   结束日期 (含)
     * @return Object[]{scheduledDate, count}
     */
    @Query(value = "SELECT t.scheduledDate, COUNT(t.id) FROM ScrmVisitTaskEntity t WHERE t.scheduledDate >="
                          + ":startDate AND t.scheduledDate <= :endDate GROUP BY t.scheduledDate ORDER BY t.scheduledDate ASC")
    List<Object[]> countByDay(
                              @Param("startDate") LocalDate startDate,
                              @Param("endDate") LocalDate endDate);

    /**
     * 按日聚合已完成任务的满意度 (趋势统计用)。
     *
     * @param startDate 开始日期 (含)
     * @param endDate   结束日期 (含)
     * @return Object[]{scheduledDate, avgScore}
     */
    @Query(value = "SELECT t.scheduledDate, COALESCE(AVG(t.satisfactionScore), 0) FROM ScrmVisitTaskEntity t WHERE "
                          + "t.scheduledDate >= :startDate AND t.scheduledDate <= :endDate AND t.satisfactionScore IS NOT "
                          + "NULL GROUP BY t.scheduledDate ORDER BY t.scheduledDate ASC")
    List<Object[]> avgSatisfactionByDay(
                                        @Param("startDate") LocalDate startDate,
                                        @Param("endDate") LocalDate endDate);
}
