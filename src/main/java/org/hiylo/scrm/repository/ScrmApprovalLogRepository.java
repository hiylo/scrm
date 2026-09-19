/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmApprovalLogRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmApprovalLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * SCRM 审批操作日志数据访问层。
 * <p>
 * 提供按实例 / 操作人查询日志, 以及审批人统计聚合能力, 供
 * {@code ScrmApprovalService} 操作日志与统计使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmApprovalLogRepository extends JpaRepository<ScrmApprovalLogEntity, Long>,
        JpaSpecificationExecutor<ScrmApprovalLogEntity> {

    /**
     * 按与实例 ID 查询日志 (按操作顺序升序, 即时间线)。
     *
     * @param instanceId 实例 ID
     * @return 日志列表
     */
    @Query(value = "SELECT t FROM ScrmApprovalLogEntity t WHERE t.instanceId = :instanceId ORDER BY t.sequence ASC,"
                          + "t.actedAt ASC")
    List<ScrmApprovalLogEntity> findByInstanceIdOrderBySequence(
                                                                            @Param("instanceId") Long instanceId);

    /**
     * 按与操作人 ID 分页查询日志 (按操作时间倒序)。
     *
     * @param operatorId 操作人 ID
     * @param pageable   分页参数
     * @return 日志分页结果
     */
    @Query("SELECT t FROM ScrmApprovalLogEntity t WHERE t.operatorId = :operatorId ORDER BY t.actedAt DESC")
    Page<ScrmApprovalLogEntity> findByOperator(
                                                @Param("operatorId") String operatorId,
                                                Pageable pageable);

    /**
     * 按操作人聚合审批通过/驳回次数 (统计用)。
     *
     * @param operatorId 操作人 ID
     * @return Object[]{approveCount, rejectCount, totalCount}
     */
    @Query(value = "SELECT SUM(CASE WHEN t.actionType = 'APPROVE' THEN 1 ELSE 0 END), SUM(CASE WHEN t.actionType ="
                          + "'REJECT' THEN 1 ELSE 0 END), COUNT(t.id) FROM ScrmApprovalLogEntity t WHERE t.operatorId ="
                          + ":operatorId AND t.actionType IN ('APPROVE', 'REJECT')")
    Object[] aggregateByOperator(
                                 @Param("operatorId") String operatorId);

    /**
     * 按操作人聚合审批时长指标 (按操作时间范围, 统计审批耗时)。
     *
     * @param operatorId 操作人 ID
     * @param startTime   操作时间起始 (含, 可空)
     * @param endTime     操作时间截止 (含, 可空)
     * @return Object[]{count, avgDurationHours}
     */
    @Query(value = "SELECT COUNT(DISTINCT t.instanceId), COALESCE(AVG(CASE WHEN t.actionType = 'APPROVE' THEN "
                          + "(SELECT i2.durationHours FROM ScrmApprovalInstanceEntity i2 WHERE i2.id = t.instanceId) ELSE "
                          + "NULL END), 0) FROM ScrmApprovalLogEntity t WHERE t.operatorId = :operatorId"
                          + " AND t.actionType IN ('APPROVE', 'REJECT') AND (:startTime IS NULL OR t.actedAt >="
                          + ":startTime) AND (:endTime IS NULL OR t.actedAt <= :endTime)")
    Object[] aggregateApproverDuration(
                                       @Param("operatorId") String operatorId,
                                       @Param("startTime") java.time.LocalDateTime startTime,
                                       @Param("endTime") java.time.LocalDateTime endTime);
}
