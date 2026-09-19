/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWorkflowNodeLogRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmWorkflowNodeLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * SCRM 营销自动化工作流节点执行日志数据访问层。
 * <p>
 * 提供按实例 / 工作流查询节点日志, 以及节点性能聚合能力, 供
 * {@code ScrmWorkflowService} 执行日志与统计使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmWorkflowNodeLogRepository extends JpaRepository<ScrmWorkflowNodeLogEntity, Long>,
        JpaSpecificationExecutor<ScrmWorkflowNodeLogEntity> {

    /**
     * 按与实例 ID 查询节点日志 (按执行顺序升序, 即时间线)。
     *
     * @param instanceId 实例 ID
     * @return 节点日志列表
     */
    @Query(value = "SELECT t FROM ScrmWorkflowNodeLogEntity t WHERE t.instanceId = :instanceId ORDER BY t.sequence "
                          + "ASC, t.startedAt ASC")
    List<ScrmWorkflowNodeLogEntity> findByInstanceIdOrderBySequence(
                                                                               @Param("instanceId") Long instanceId);

    /**
     * 分页查询指定工作流的失败节点日志 (状态 FAILED)。
     *
     * @param workflowId 工作流 ID
     * @param pageable   分页参数
     * @return 失败节点日志分页结果
     */
    @Query(value = "SELECT t FROM ScrmWorkflowNodeLogEntity t WHERE t.workflowId = :workflowId AND t.status ="
                          + "'FAILED' ORDER BY t.startedAt DESC")
    Page<ScrmWorkflowNodeLogEntity> findFailedByWorkflowId(
                                                           @Param("workflowId") Long workflowId,
                                                           Pageable pageable);

    /**
     * 按工作流聚合各节点执行指标 (统计用)。
     *
     * @param workflowId 工作流 ID
     * @return Object[]{nodeId, nodeName, nodeType, totalCount, successCount, failedCount, avgDuration}
     */
    @Query(value = "SELECT t.nodeId, MAX(t.nodeName), MAX(t.nodeType), COUNT(t.id), SUM(CASE WHEN t.status ="
                          + "'SUCCESS' THEN 1 ELSE 0 END), SUM(CASE WHEN t.status = 'FAILED' THEN 1 ELSE 0 END),"
                          + "COALESCE(AVG(t.durationMs), 0) FROM ScrmWorkflowNodeLogEntity t WHERE t.workflowId = :workflowId "
                          + "GROUP BY t.nodeId")
    List<Object[]> aggregateNodePerformance(
                                            @Param("workflowId") Long workflowId);
}
