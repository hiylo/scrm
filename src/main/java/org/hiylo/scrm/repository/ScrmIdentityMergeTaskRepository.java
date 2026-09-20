/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmIdentityMergeTaskRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmIdentityMergeTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * SCRM 客户身份合并任务数据访问层。
 * <p>
 * 状态与时间维度聚合统计能力, 供 {@code ScrmIdentityMergeService} 使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmIdentityMergeTaskRepository extends JpaRepository<ScrmIdentityMergeTaskEntity, Long>,
        JpaSpecificationExecutor<ScrmIdentityMergeTaskEntity> {

    /**
     * 按与源客户查询合并任务。
     *
     * @param sourceCustomerId 源客户 ID
     * @return 任务列表
     */
    List<ScrmIdentityMergeTaskEntity> findBySourceCustomerId(Long sourceCustomerId);

    /**
     * 按源客户 ID 列表批量查询合并任务 (批量预加载用, 避免逐源客户查询造成 N+1)。
     *
     * @param sourceCustomerIds 源客户 ID 列表
     * @return 任务列表
     */
    List<ScrmIdentityMergeTaskEntity> findBySourceCustomerIdIn(Collection<Long> sourceCustomerIds);

    /**
     * 按与目标客户查询合并任务。
     *
     * @param targetCustomerId 目标客户 ID
     * @return 任务列表
     */
    List<ScrmIdentityMergeTaskEntity> findByTargetCustomerId(Long targetCustomerId);

    /**
     * 按状态聚合任务数 (统计用)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return Object[]{status, count}
     */
    @Query(value = "SELECT t.status, COUNT(t.id) FROM ScrmIdentityMergeTaskEntity t WHERE (:startTime IS NULL OR "
                          + "t.createTime >= :startTime) AND (:endTime IS NULL OR t.createTime <= :endTime) GROUP BY t.status")
    List<Object[]> countByStatus(
                                   @Param("startTime") LocalDateTime startTime,
                                   @Param("endTime") LocalDateTime endTime);

    /**
     * 按与合并类型聚合任务数 (统计用)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return Object[]{mergeType, count}
     */
    @Query(value = "SELECT t.mergeType, COUNT(t.id) FROM ScrmIdentityMergeTaskEntity t WHERE (:startTime IS NULL OR "
                          + "t.createTime >= :startTime) AND (:endTime IS NULL OR t.createTime <= :endTime) GROUP BY "
                          + "t.mergeType")
    List<Object[]> countByMergeType(
                                      @Param("startTime") LocalDateTime startTime,
                                      @Param("endTime") LocalDateTime endTime);

    /**
     * 计算已完成任务的平均合并耗时 (秒, 基于 startedAt 与 completedAt)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 平均合并耗时秒数 (无数据返回 null)
     */
    @Query(value = "SELECT AVG(EXTRACT(EPOCH FROM (t.completed_at - t.started_at))) FROM scrm.scrm_identity_merge_task t "
            + "WHERE t.status = 'COMPLETED' "
            + "AND t.started_at IS NOT NULL AND t.completed_at IS NOT NULL "
            + "AND (:startTime IS NULL OR t.create_time >= :startTime) "
            + "AND (:endTime IS NULL OR t.create_time <= :endTime)", nativeQuery = true)
    Double avgCompletedDurationSeconds(
                                          @Param("startTime") LocalDateTime startTime,
                                          @Param("endTime") LocalDateTime endTime);

    /**
     * 统计指定账号的任务总数。
     *
     * @return 任务总数
     */
}
