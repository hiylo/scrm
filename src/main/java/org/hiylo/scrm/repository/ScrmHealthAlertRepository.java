/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmHealthAlertRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmHealthAlertEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 客户健康度告警数据访问层。
 * <p>
 * 提供按 + 客户查询告警, 活跃告警列表查询, 告警统计 (各类型 / 各严重度 / 解决率) 等。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmHealthAlertRepository extends JpaRepository<ScrmHealthAlertEntity, Long>,
        JpaSpecificationExecutor<ScrmHealthAlertEntity> {

    /**
     * 按 + 客户查询告警列表 (按触发时间倒序)。
     *
     * @param customerId 客户 ID
     * @param pageable   分页参数
     * @return 告警分页结果
     */
    Page<ScrmHealthAlertEntity> findByCustomerIdOrderByTriggeredAtDesc(Long customerId, Pageable pageable);

    /**
     * 按查询活跃告警 (status = ACTIVE, 按触发时间倒序)。
     *
     * @param pageable 分页参数
     * @return 告警分页结果
     */
    Page<ScrmHealthAlertEntity> findByStatusOrderByTriggeredAtDesc(String status, Pageable pageable);

    /**
     * 告警统计: 总数 / 已解决数 / 已确认数。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return Object[]: [total, resolved, acknowledged]
     */
    @Query(value = "SELECT COUNT(a), SUM(CASE WHEN a.status = 'RESOLVED' THEN 1 ELSE 0 END), SUM(CASE WHEN a.status"
                          + "= 'ACKNOWLEDGED' THEN 1 ELSE 0 END) FROM ScrmHealthAlertEntity a WHERE (:startTime IS NULL OR "
                          + "a.triggeredAt >= :startTime) AND (:endTime IS NULL OR a.triggeredAt <= :endTime)")
    Object[] getAlertStats(
                           @Param("startTime") LocalDateTime startTime,
                           @Param("endTime") LocalDateTime endTime);

    /**
     * 按告警类型聚合统计。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return Object[] 列表: [alertType, count]
     */
    @Query(value = "SELECT a.alertType, COUNT(a) FROM ScrmHealthAlertEntity a WHERE (:startTime IS NULL OR "
                          + "a.triggeredAt >= :startTime) AND (:endTime IS NULL OR a.triggeredAt <= :endTime) GROUP BY "
                          + "a.alertType")
    List<Object[]> countByAlertType(
                                    @Param("startTime") LocalDateTime startTime,
                                    @Param("endTime") LocalDateTime endTime);

    /**
     * 按严重程度聚合统计。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return Object[] 列表: [severity, count]
     */
    @Query(value = "SELECT a.severity, COUNT(a) FROM ScrmHealthAlertEntity a WHERE (:startTime IS NULL OR "
                          + "a.triggeredAt >= :startTime) AND (:endTime IS NULL OR a.triggeredAt <= :endTime) GROUP BY "
                          + "a.severity")
    List<Object[]> countBySeverity(
                                   @Param("startTime") LocalDateTime startTime,
                                   @Param("endTime") LocalDateTime endTime);
}
