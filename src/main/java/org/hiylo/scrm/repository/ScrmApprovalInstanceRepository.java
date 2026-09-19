/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmApprovalInstanceRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmApprovalInstanceEntity;
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
 * SCRM 审批实例数据访问层。
 * <p>
 * 提供按实例编号 / 业务关联 / 申请人 / 审批人查询实例, 以及审批统计聚合能力, 供
 * {@code ScrmApprovalService} 实例管理与统计引擎使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmApprovalInstanceRepository extends JpaRepository<ScrmApprovalInstanceEntity, Long>,
        JpaSpecificationExecutor<ScrmApprovalInstanceEntity> {

    /**
     * 按与实例编号加载实例。
     *
     * @param instanceNo 实例编号
     * @return 实例实体 (可能不存在)
     */
    Optional<ScrmApprovalInstanceEntity> findByInstanceNo(String instanceNo);

    /**
     * 按与业务关联查询实例 (按开始时间倒序)。
     *
     * @param businessType 业务类型
     * @param businessId   业务 ID
     * @return 实例列表
     */
    @Query(value = "SELECT t FROM ScrmApprovalInstanceEntity t WHERE t.businessType = :businessType AND t.businessId"
                          + "= :businessId ORDER BY t.startedAt DESC")
    List<ScrmApprovalInstanceEntity> findByBusiness(
                                                     @Param("businessType") String businessType,
                                                     @Param("businessId") String businessId);

    /**
     * 按申请人分页查询实例 (按开始时间倒序)。
     *
     * @param applicantId 申请人 ID
     * @param pageable    分页参数
     * @return 实例分页结果
     */
    @Query(value = "SELECT t FROM ScrmApprovalInstanceEntity t WHERE t.applicantId = :applicantId ORDER BY "
                          + "t.startedAt DESC")
    Page<ScrmApprovalInstanceEntity> findByApplicant(
                                                      @Param("applicantId") String applicantId,
                                                      Pageable pageable);

    /**
     * 分页查询申请人的实例 (按状态过滤, 按开始时间倒序)。
     *
     * @param applicantId 申请人 ID
     * @param status      状态过滤 (可空)
     * @param pageable    分页参数
     * @return 实例分页结果
     */
    @Query(value = "SELECT t FROM ScrmApprovalInstanceEntity t WHERE t.applicantId = :applicantId AND (:status IS "
                          + "NULL OR t.status = :status) ORDER BY t.startedAt DESC")
    Page<ScrmApprovalInstanceEntity> findByApplicantAndStatus(
                                                              @Param("applicantId") String applicantId,
                                                              @Param("status") String status,
                                                              Pageable pageable);

    /**
     * 按审批人 ID 模糊匹配查询待审批实例 (currentApproverIds 包含 approverId)。
     *
     * @param approverId 审批人 ID
     * @param pageable   分页参数
     * @return 实例分页结果 (按优先级降序、开始时间倒序)
     */
    @Query(value = "SELECT t FROM ScrmApprovalInstanceEntity t WHERE t.status IN ('PENDING', 'APPROVING') AND "
                          + "t.currentApproverIds LIKE CONCAT('%', :approverId, '%') ORDER BY t.isUrgent DESC, t.priority "
                          + "DESC, t.startedAt ASC")
    Page<ScrmApprovalInstanceEntity> findPendingByApprover(
                                                            @Param("approverId") String approverId,
                                                            Pageable pageable);

    /**
     * 按操作人查询其参与过的实例 (基于日志表关联, APPROVE/REJECT/COUNTERSIGN/TRANSFER)。
     *
     * @param approverId 审批人 ID
     * @param pageable   分页参数
     * @return 实例分页结果
     */
    @Query("SELECT DISTINCT t FROM ScrmApprovalInstanceEntity t, ScrmApprovalLogEntity l "
            + "WHERE t.id = l.instanceId AND l.operatorId = :approverId "
            + "AND l.actionType IN ('APPROVE', 'REJECT', 'COUNTERSIGN', 'TRANSFER') "
            + "ORDER BY t.startedAt DESC")
    Page<ScrmApprovalInstanceEntity> findApprovedByApprover(
                                                             @Param("approverId") String approverId,
                                                             Pageable pageable);

    /**
     * 按状态聚合实例数 (统计用)。
     *
     * @param startTime 开始时间起始 (含, 可空)
     * @param endTime   开始时间截止 (含, 可空)
     * @return Object[]{status, count}
     */
    @Query(value = "SELECT t.status, COUNT(t.id) FROM ScrmApprovalInstanceEntity t WHERE (:startTime IS NULL OR "
                          + "t.startedAt >= :startTime) AND (:endTime IS NULL OR t.startedAt <= :endTime) GROUP BY t.status")
    List<Object[]> countByStatus(
                                  @Param("startTime") LocalDateTime startTime,
                                  @Param("endTime") LocalDateTime endTime);

    /**
     * 聚合实例审批时长指标 (统计用, 仅统计已完成且有 durationHours 的实例)。
     *
     * @param startTime 开始时间起始 (含, 可空)
     * @param endTime   开始时间截止 (含, 可空)
     * @return Object[]{avgDuration, maxDuration, count}
     */
    @Query(value = "SELECT COALESCE(AVG(t.durationHours), 0), COALESCE(MAX(t.durationHours), 0), COUNT(t.id) FROM "
                          + "ScrmApprovalInstanceEntity t WHERE t.durationHours IS NOT NULL AND (:startTime IS NULL OR "
                          + "t.startedAt >= :startTime) AND (:endTime IS NULL OR t.startedAt <= :endTime)")
    Object[] aggregateDuration(
                                @Param("startTime") LocalDateTime startTime,
                                @Param("endTime") LocalDateTime endTime);

    /**
     * 统计待审批实例的耗时分布 (按开始时间分段统计老化)。
     *
     * @param cutoffTime 截止时间 (老化阈值)
     * @return 待审批实例数
     */
    @Query(value = "SELECT COUNT(t.id) FROM ScrmApprovalInstanceEntity t WHERE t.status IN ('PENDING', 'APPROVING') "
                          + "AND t.startedAt <= :cutoffTime")
    long countPendingBefore(
                             @Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * 查询所有待审批实例 (PENDING / APPROVING), 用于老化分析。
     *
     * @return 待审批实例列表
     */
    @Query(value = "SELECT t FROM ScrmApprovalInstanceEntity t WHERE t.status IN ('PENDING', 'APPROVING') ORDER BY "
                          + "t.startedAt ASC")
    List<ScrmApprovalInstanceEntity> findPendingInstances();

    /**
     * 统计审批人参与实例数与通过率 (基于日志表)。
     *
     * @param approverId 审批人 ID
     * @return Object[]{totalApproved, totalRejected}
     */
    @Query(value = "SELECT SUM(CASE WHEN l.actionType = 'APPROVE' THEN 1 ELSE 0 END), SUM(CASE WHEN l.actionType ="
                          + "'REJECT' THEN 1 ELSE 0 END) FROM ScrmApprovalLogEntity l WHERE l.operatorId = :approverId AND "
                          + "l.actionType IN ('APPROVE', 'REJECT')")
    Object[] aggregateApproverActions(
                                       @Param("approverId") String approverId);
}
