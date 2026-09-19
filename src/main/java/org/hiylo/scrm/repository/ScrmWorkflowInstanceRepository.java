/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWorkflowInstanceRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmWorkflowInstanceEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 营销自动化工作流执行实例数据访问层。
 * <p>
 * 提供按工作流 / 客户 / 状态查询实例, 延迟到期实例扫描与抢占式条件更新, 以及实例统计聚合能力, 供
 * {@code ScrmWorkflowService} 执行引擎、延迟恢复调度器与统计使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmWorkflowInstanceRepository extends JpaRepository<ScrmWorkflowInstanceEntity, Long>,
        JpaSpecificationExecutor<ScrmWorkflowInstanceEntity> {

    /**
     * 按与工作流 ID 查询活跃实例 (RUNNING / WAITING / PAUSED)。
     *
     * @param workflowId 工作流 ID
     * @return 活跃实例列表
     */
    @Query(value = "SELECT t FROM ScrmWorkflowInstanceEntity t WHERE t.workflowId = :workflowId AND t.status IN "
                          + "('RUNNING', 'WAITING', 'PAUSED') ORDER BY t.startedAt DESC")
    List<ScrmWorkflowInstanceEntity> findActiveByWorkflowId(
                                                            @Param("workflowId") Long workflowId);

    /**
     * 按客户 ID 查询工作流实例 (按开始时间倒序)。
     *
     * @param customerId 客户 ID
     * @return 实例列表
     */
    @Query(value = "SELECT t FROM ScrmWorkflowInstanceEntity t WHERE t.customerId = :customerId ORDER BY t.startedAt "
                          + "DESC")
    List<ScrmWorkflowInstanceEntity> findByCustomerId(
                                                                 @Param("customerId") Long customerId);

    /**
     * 按工作流聚合实例各状态数 (统计用)。
     *
     * @param workflowId 工作流 ID
     * @return Object[]{status, count}
     */
    @Query(value = "SELECT t.status, COUNT(t.id) FROM ScrmWorkflowInstanceEntity t WHERE t.workflowId = :workflowId "
                          + "GROUP BY t.status")
    List<Object[]> countByStatus(
                                 @Param("workflowId") Long workflowId);

    /**
     * 按工作流聚合实例执行耗时指标 (统计用)。
     *
     * @param workflowId 工作流 ID
     * @return Object[]{avgDuration, maxDuration, count}
     */
    @Query(value = "SELECT COALESCE(AVG(t.durationMs), 0), COALESCE(MAX(t.durationMs), 0), COUNT(t.id) FROM "
                          + "ScrmWorkflowInstanceEntity t WHERE t.workflowId = :workflowId AND t.durationMs IS NOT NULL")
    Object[] aggregateDuration(
                               @Param("workflowId") Long workflowId);

    /**
     * 统计工作流活跃实例数 (RUNNING / WAITING / PAUSED)。
     *
     * @param workflowId 工作流 ID
     * @return 活跃实例数
     */
    @Query(value = "SELECT COUNT(t.id) FROM ScrmWorkflowInstanceEntity t WHERE t.workflowId = :workflowId AND "
                          + "t.status IN ('RUNNING', 'WAITING', 'PAUSED')")
    long countActiveByWorkflowId(
                                 @Param("workflowId") Long workflowId);

    /**
     * 查询延迟已到期的等待中实例 (供延迟恢复调度器扫描, 按到期时间升序限量取)。
     *
     * @param now      当前时间
     * @param pageable 分页参数 (限制单批扫描数量)
     * @return 到期实例列表
     */
    @Query(value = "SELECT t FROM ScrmWorkflowInstanceEntity t WHERE t.status = 'WAITING' "
                          + "AND t.nextExecutionAt IS NOT NULL AND t.nextExecutionAt <= :now "
                          + "ORDER BY t.nextExecutionAt ASC")
    List<ScrmWorkflowInstanceEntity> findDueWaitingInstances(@Param("now") LocalDateTime now,
                                                            Pageable pageable);

    /**
     * 抢占延迟到期的等待中实例: 以 "状态仍为 WAITING 且 version 未被改动" 为条件将其置为 RUNNING。
     * <p>
     * 多实例部署下同一到期实例可能被多个节点同时扫到, 只有本更新返回 1 的节点获得执行权;
     * 手写递增 version 以符合实体乐观锁约定。
     * </p>
     *
     * @param id              实例 ID
     * @param expectedVersion 扫描时读到的乐观锁版本号
     * @param now             当前时间 (用于刷新 update_time)
     * @return 受影响行数 (1 表示抢占成功, 0 表示已被其他节点处理)
     */
    @Modifying
    @Query(value = "UPDATE ScrmWorkflowInstanceEntity t SET t.status = 'RUNNING', t.nextExecutionAt = NULL, "
                          + "t.updateTime = :now, t.version = t.version + 1 "
                          + "WHERE t.id = :id AND t.status = 'WAITING' AND t.version = :expectedVersion")
    int claimWaitingInstance(@Param("id") Long id,
                             @Param("expectedVersion") Long expectedVersion,
                             @Param("now") LocalDateTime now);
}
