/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSegmentHistoryRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmSegmentHistoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;

/**
 * SCRM 客户分群历史快照数据访问层。
 * <p>
 * 提供按分群与时间范围查询历史快照能力, 供
 * {@code ScrmSegmentService.getHistory} / {@code getSegmentTrend} 趋势分析使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmSegmentHistoryRepository extends JpaRepository<ScrmSegmentHistoryEntity, Long>,
        JpaSpecificationExecutor<ScrmSegmentHistoryEntity> {

    /**
     * 按与分群 ID 分页查询历史快照 (按快照日期倒序)。
     *
     * @param segmentId 分群 ID
     * @param pageable  分页参数
     * @return 历史快照分页结果
     */
    Page<ScrmSegmentHistoryEntity> findBySegmentIdOrderBySnapshotDateDesc(Long segmentId, Pageable pageable);

    /**
     * 按、分群 ID 与快照日期范围查询历史快照 (趋势分析用, 按快照日期升序)。
     *
     * @param segmentId   分群 ID
     * @param startDate   起始日期 (含)
     * @param endDate     截止日期 (含)
     * @return 历史快照列表
     */
    List<ScrmSegmentHistoryEntity> findBySegmentIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(Long segmentId, LocalDate startDate, LocalDate endDate);
}
