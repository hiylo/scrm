/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAlertEventRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmAlertEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * SCRM 告警事件数据访问层。
 * <p>
 * 提供按与编号加载事件, 以及按规则 / 指标 / 严重度 / 状态 / 时间区间等条件
 * 查询与聚合能力, 供 {@code ScrmSystemMonitorService} 的事件管理与统计使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmAlertEventRepository extends JpaRepository<ScrmAlertEventEntity, Long>,
        JpaSpecificationExecutor<ScrmAlertEventEntity> {

    /**
     * 按与事件编号加载事件。
     *
     * @param eventNo  事件编号
     * @return 事件实体 (可能不存在)
     */
    Optional<ScrmAlertEventEntity> findByEventNo(String eventNo);

    /**
     * 按状态分页查询事件 (按 triggerTime DESC)。
     *
     * @param status   状态
     * @param pageable 分页参数
     * @return 事件分页结果
     */
    Page<ScrmAlertEventEntity> findByStatusOrderByTriggerTimeDesc(String status, Pageable pageable);

    /**
     * 按与严重程度分页查询事件 (按 triggerTime DESC)。
     *
     * @param severity 严重程度
     * @param pageable 分页参数
     * @return 事件分页结果
     */
    Page<ScrmAlertEventEntity> findBySeverityOrderByTriggerTimeDesc(String severity, Pageable pageable);

    /**
     * 按与规则 ID 查询触发中的事件 (用于冷却检查与重复触发判断)。
     *
     * @param ruleId   规则 ID
     * @param status   状态
     * @return 事件列表
     */
    List<ScrmAlertEventEntity> findByRuleIdAndStatus(Long ruleId, String status);

    /**
     * 按状态聚合事件数 (统计用)。
     *
     * @param startTime 触发时间起始 (含, 可空)
     * @param endTime   触发时间截止 (含, 可空)
     * @return Object[]{status, count}
     */
    @Query(value = "SELECT e.status, COUNT(e.id) FROM ScrmAlertEventEntity e WHERE (:startTime IS NULL OR "
                          + "e.triggerTime >= :startTime) AND (:endTime IS NULL OR e.triggerTime <= :endTime) GROUP BY "
                          + "e.status")
    List<Object[]> countByStatus(
                                 @Param("startTime") LocalDateTime startTime,
                                 @Param("endTime") LocalDateTime endTime);

    /**
     * 按严重程度聚合事件数 (统计用)。
     *
     * @param startTime 触发时间起始 (含, 可空)
     * @param endTime   触发时间截止 (含, 可空)
     * @return Object[]{severity, count}
     */
    @Query(value = "SELECT e.severity, COUNT(e.id) FROM ScrmAlertEventEntity e WHERE (:startTime IS NULL OR "
                          + "e.triggerTime >= :startTime) AND (:endTime IS NULL OR e.triggerTime <= :endTime) GROUP BY "
                          + "e.severity")
    List<Object[]> countBySeverity(
                                   @Param("startTime") LocalDateTime startTime,
                                   @Param("endTime") LocalDateTime endTime);

    /**
     * 按规则聚合事件数 (Top 告警规则统计用)。
     *
     * @param startTime 触发时间起始 (含, 可空)
     * @param endTime   触发时间截止 (含, 可空)
     * @return Object[]{ruleId, ruleName, count}
     */
    @Query(value = "SELECT e.ruleId, e.ruleName, COUNT(e.id) FROM ScrmAlertEventEntity e WHERE e.ruleId IS NOT NULL "
                          + "AND (:startTime IS NULL OR e.triggerTime >= :startTime) AND (:endTime IS NULL OR e.triggerTime"
                          + "<= :endTime) GROUP BY e.ruleId, e.ruleName ORDER BY COUNT(e.id) DESC")
    List<Object[]> countByRule(
                               @Param("startTime") LocalDateTime startTime,
                               @Param("endTime") LocalDateTime endTime);

    /**
     * 按天聚合事件数 (告警趋势用)。
     *
     * @param startTime 触发时间起始 (含)
     * @param endTime   触发时间截止 (含)
     * @return Object[]{day, count}
     */
    @Query(value = "SELECT FUNCTION('DATE', e.triggerTime), COUNT(e.id) FROM ScrmAlertEventEntity e WHERE "
                          + "e.triggerTime >= :startTime AND e.triggerTime <= :endTime GROUP BY FUNCTION('DATE',"
                          + "e.triggerTime) ORDER BY FUNCTION('DATE', e.triggerTime)")
    List<Object[]> countByDay(
                              @Param("startTime") LocalDateTime startTime,
                              @Param("endTime") LocalDateTime endTime);

    /**
     * 按与触发时间区间查询已确认事件 (用于计算平均确认时长 MTTA)。
     *
     * @param startTime 触发时间起始 (含, 可空)
     * @param endTime   触发时间截止 (含, 可空)
     * @return 已确认事件列表
     */
    @Query(value = "SELECT e FROM ScrmAlertEventEntity e WHERE e.acknowledgedAt IS NOT NULL AND (:startTime IS NULL "
                          + "OR e.triggerTime >= :startTime) AND (:endTime IS NULL OR e.triggerTime <= :endTime)")
    List<ScrmAlertEventEntity> findAcknowledgedEvents(
                                                      @Param("startTime") LocalDateTime startTime,
                                                      @Param("endTime") LocalDateTime endTime);

    /**
     * 按与触发时间区间查询已恢复事件 (用于计算平均恢复时长 MTTR)。
     *
     * @param status   状态 (RESOLVED)
     * @param startTime 触发时间起始 (含, 可空)
     * @param endTime   触发时间截止 (含, 可空)
     * @return 已恢复事件列表
     */
    @Query(value = "SELECT e FROM ScrmAlertEventEntity e WHERE e.status = :status AND e.durationSeconds IS NOT NULL "
                          + "AND (:startTime IS NULL OR e.triggerTime >= :startTime) AND (:endTime IS NULL OR e.triggerTime"
                          + "<= :endTime)")
    List<ScrmAlertEventEntity> findResolvedEvents(
                                                  @Param("status") String status,
                                                  @Param("startTime") LocalDateTime startTime,
                                                  @Param("endTime") LocalDateTime endTime);
}
