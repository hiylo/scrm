/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWorkflowRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmWorkflowEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * SCRM 营销自动化工作流数据访问层。
 * <p>
 * 提供按与编码加载工作流, 以及工作流统计聚合能力, 供
 * {@code ScrmWorkflowService} 管理与统计引擎使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmWorkflowRepository extends JpaRepository<ScrmWorkflowEntity, Long>,
        JpaSpecificationExecutor<ScrmWorkflowEntity> {

    /**
     * 按与工作流编码加载工作流。
     *
     * @param workflowCode 工作流编码
     * @return 工作流实体 (可能不存在)
     */
    Optional<ScrmWorkflowEntity> findByWorkflowCode(String workflowCode);

    /**
     * 按状态聚合工作流数 (统计用)。
     *
     * @param startTime 创建时间起始 (含, 可空)
     * @param endTime   创建时间截止 (含, 可空)
     * @return Object[]{status, count}
     */
    @Query(value = "SELECT t.status, COUNT(t.id) FROM ScrmWorkflowEntity t WHERE (:startTime IS NULL OR t.createTime"
                          + ">= :startTime) AND (:endTime IS NULL OR t.createTime <= :endTime) GROUP BY t.status")
    List<Object[]> countByStatus(
                                 @Param("startTime") LocalDateTime startTime,
                                 @Param("endTime") LocalDateTime endTime);

    /**
     * 统计指定时间范围内工作流执行指标汇总 (统计用)。
     *
     * @param startTime 创建时间起始 (含, 可空)
     * @param endTime   创建时间截止 (含, 可空)
     * @return Object[]{executionCountSum, successCountSum, failureCountSum, avgExecutionTimeSum, count}
     */
    @Query(value = "SELECT COALESCE(SUM(t.executionCount), 0), COALESCE(SUM(t.successCount), 0),"
                          + "COALESCE(SUM(t.failureCount), 0), COALESCE(SUM(t.avgExecutionTimeMs), 0), COUNT(t.id) FROM "
                          + "ScrmWorkflowEntity t WHERE (:startTime IS NULL OR t.createTime >= :startTime) AND (:endTime IS "
                          + "NULL OR t.createTime <= :endTime)")
    Object[] sumExecutionMetrics(
                                 @Param("startTime") LocalDateTime startTime,
                                 @Param("endTime") LocalDateTime endTime);
}
