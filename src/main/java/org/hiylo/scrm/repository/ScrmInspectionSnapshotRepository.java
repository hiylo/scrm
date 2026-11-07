/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmInspectionSnapshotRepository.java
 * Date : 2026/10/23 09:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmInspectionSnapshotEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 工商税务巡检结果快照数据访问层。
 * <p>
 * 提供按任务+维度查询最新快照（变化比对基线）、置顶最新快照（同企业同维度历史快照
 * 标记为非最新）、以及历史快照分页查询能力。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
public interface ScrmInspectionSnapshotRepository
        extends JpaRepository<ScrmInspectionSnapshotEntity, Long>, JpaSpecificationExecutor<ScrmInspectionSnapshotEntity> {

    /**
     * 查询某企业某维度最新一条快照（变化比对的基线）。
     *
     * @param taskId    巡检任务 ID
     * @param dimension 巡检维度
     * @return 最新快照（不存在时返回空, 表示首次巡检建基线）
     */
    Optional<ScrmInspectionSnapshotEntity> findFirstByTaskIdAndDimensionAndIsLatestTrue(
            Long taskId, String dimension);

    /**
     * 查询某企业某维度最近一次快照（不区分是否最新, 兜底历史比对）。
     *
     * @param taskId    巡检任务 ID
     * @param dimension 巡检维度
     * @param pageable  分页参数
     * @return 快照分页
     */
    Page<ScrmInspectionSnapshotEntity> findByTaskIdAndDimension(Long taskId, String dimension, Pageable pageable);

    /**
     * 批量查询某任务的全部维度最新快照。
     *
     * @param taskId 巡检任务 ID
     * @return 最新快照列表
     */
    List<ScrmInspectionSnapshotEntity> findByTaskIdAndIsLatestTrue(Long taskId);

    /**
     * 将指定任务指定维度的所有快照标记为非最新（新快照入库前调用）。
     *
     * @param taskId    巡检任务 ID
     * @param dimension 巡检维度
     * @return 受影响行数
     */
    @Modifying
    @Query("UPDATE ScrmInspectionSnapshotEntity s SET s.isLatest = false "
            + "WHERE s.taskId = :taskId AND s.dimension = :dimension AND s.isLatest = true")
    int markAllNotLatest(@Param("taskId") Long taskId, @Param("dimension") String dimension);

    /**
     * 统计某任务某维度自某时间后的快照数（调度去重防重入）。
     *
     * @param taskId    巡检任务 ID
     * @param dimension 巡检维度
     * @param since     起始时间（含）
     * @return 快照数
     */
    long countByTaskIdAndDimensionAndInspectAtGreaterThanEqual(Long taskId, String dimension, LocalDateTime since);
}