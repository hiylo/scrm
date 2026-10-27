/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmInspectionRunRepository.java
 * Date : 2026/10/24 16:35:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmInspectionRunEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 巡检/抓取执行历史数据访问层。
 *
 * @author Hsi Chu
 * @since V1.0
 */
public interface ScrmInspectionRunRepository extends JpaRepository<ScrmInspectionRunEntity, Long> {

    /**
     * 按执行类型分页查询（按开始时间倒序）。
     *
     * @param runType  执行类型
     * @param pageable 分页
     * @return 分页结果
     */
    Page<ScrmInspectionRunEntity> findByRunTypeOrderByStartedAtDesc(String runType, Pageable pageable);

    /**
     * 分页查询全部（按开始时间倒序）。
     *
     * @param pageable 分页
     * @return 分页结果
     */
    Page<ScrmInspectionRunEntity> findAllByOrderByStartedAtDesc(Pageable pageable);

    /**
     * 查询某时间点之后的所有执行记录（统计用）。
     *
     * @param since 起始时间
     * @return 记录列表
     */
    List<ScrmInspectionRunEntity> findByStartedAtAfter(LocalDateTime since);

    /**
     * 按类型与状态统计条数。
     *
     * @param runType  执行类型
     * @param status   状态
     * @param since    起始时间
     * @return 条数
     */
    @Query("SELECT COUNT(r) FROM ScrmInspectionRunEntity r "
            + "WHERE r.runType = :runType AND r.status = :status AND r.startedAt >= :since")
    long countByTypeAndStatusSince(@Param("runType") String runType,
                                   @Param("status") String status,
                                   @Param("since") LocalDateTime since);
}
