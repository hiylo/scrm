/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmApprovalFlowRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmApprovalFlowEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * SCRM 审批流程定义数据访问层。
 * <p>
 * 提供按与编码加载流程、按业务类型查找默认流程, 以及流程统计聚合能力, 供
 * {@code ScrmApprovalService} 流程管理与统计引擎使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmApprovalFlowRepository extends JpaRepository<ScrmApprovalFlowEntity, Long>,
        JpaSpecificationExecutor<ScrmApprovalFlowEntity> {

    /**
     * 按与流程编码加载流程。
     *
     * @param flowCode 流程编码
     * @return 流程实体 (可能不存在)
     */
    Optional<ScrmApprovalFlowEntity> findByFlowCode(String flowCode);

    /**
     * 按与流程类型查询已激活流程 (按使用次数倒序)。
     *
     * @param flowType 流程类型
     * @return 流程列表
     */
    @Query(value = "SELECT t FROM ScrmApprovalFlowEntity t WHERE t.flowType = :flowType AND t.status = 'ACTIVE' "
                          + "ORDER BY t.usageCount DESC, t.createTime DESC")
    List<ScrmApprovalFlowEntity> findActiveByFlowType(
                                                      @Param("flowType") String flowType);

    /**
     * 按查询默认流程 (按使用次数倒序)。
     *
     * @return 默认流程列表
     */
    @Query(value = "SELECT t FROM ScrmApprovalFlowEntity t WHERE t.isDefault = TRUE AND t.status = 'ACTIVE' ORDER BY "
                          + "t.usageCount DESC")
    List<ScrmApprovalFlowEntity> findDefaultFlows();

    /**
     * 清除同账号其他流程的默认标记 (设置默认流程时调用)。
     *
     * @param excludeFlowId 排除的流程 ID
     * @return 受影响行数
     */
    @Modifying
    @Query(value = "UPDATE ScrmApprovalFlowEntity t SET t.isDefault = FALSE WHERE t.id <> :excludeFlowId AND "
                          + "t.isDefault = TRUE")
    int clearDefaultFlag(
                        @Param("excludeFlowId") Long excludeFlowId);

    /**
     * 递增流程使用次数并刷新最近使用时间。
     *
     * @param flowId     流程 ID
     * @param lastUsedAt 最近使用时间
     * @return 受影响行数
     */
    @Modifying
    @Query(value = "UPDATE ScrmApprovalFlowEntity t SET t.usageCount = COALESCE(t.usageCount, 0) + 1, t.lastUsedAt ="
                          + ":lastUsedAt WHERE t.id = :flowId")
    int incrementUsage(@Param("flowId") Long flowId,
                       @Param("lastUsedAt") LocalDateTime lastUsedAt);

    /**
     * 按状态聚合流程数 (统计用)。
     *
     * @param startTime 创建时间起始 (含, 可空)
     * @param endTime   创建时间截止 (含, 可空)
     * @return Object[]{status, count}
     */
    @Query(value = "SELECT t.status, COUNT(t.id) FROM ScrmApprovalFlowEntity t WHERE (:startTime IS NULL OR "
                          + "t.createTime >= :startTime) AND (:endTime IS NULL OR t.createTime <= :endTime) GROUP BY t.status")
    List<Object[]> countByStatus(
                                 @Param("startTime") LocalDateTime startTime,
                                 @Param("endTime") LocalDateTime endTime);
}
