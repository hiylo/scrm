/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContentScheduleRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmContentScheduleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 内容排期数据访问层。
 * <p>
 * 提供按内容/状态/时间范围查询排期任务, 以及扫描到期 PENDING 排期能力,
 * 供 {@code ScrmContentMarketingService} 排期管理与定时调度使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmContentScheduleRepository extends JpaRepository<ScrmContentScheduleEntity, Long>,
        JpaSpecificationExecutor<ScrmContentScheduleEntity> {

    /**
     * 扫描已到期但仍处于 PENDING 状态的排期 (调度器轮询用)。
     *
     * @param currentTime 当前时间 (含, 排期时间 ≤ 当前时间视为到期)
     * @return 到期 PENDING 排期列表
     */
    @Query(value = "SELECT s FROM ScrmContentScheduleEntity s WHERE s.status = 'PENDING' AND s.scheduledAt <="
                          + ":currentTime ORDER BY s.scheduledAt ASC")
    List<ScrmContentScheduleEntity> findDueSchedules(
                                                     @Param("currentTime") LocalDateTime currentTime);
}
