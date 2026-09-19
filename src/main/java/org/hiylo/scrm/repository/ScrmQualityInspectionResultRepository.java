/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmQualityInspectionResultRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmQualityInspectionResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 质检结果数据访问层。
 * <p>
 * 提供按任务/被质检人/时间范围等维度的查询能力, 供质检统计与排名使用。
 * 复合过滤通过 {@link JpaSpecificationExecutor} 实现动态查询。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmQualityInspectionResultRepository
        extends JpaRepository<ScrmQualityInspectionResultEntity, Long>,
                JpaSpecificationExecutor<ScrmQualityInspectionResultEntity> {

    /**
     * 按与质检时间范围查询结果列表 (质检统计/排名用)。
     *
     * @param from     起始时间（含）
     * @param to       截止时间（含）
     * @return 结果列表
     */
    List<ScrmQualityInspectionResultEntity> findByInspectedAtBetween(LocalDateTime from, LocalDateTime to);

    /**
     * 按与被质检人查询质检时间范围内的结果 (某销售质检结果用)。
     *
     * @param assigneeId 被质检人 ID
     * @param from       起始时间（含）
     * @param to         截止时间（含）
     * @return 结果列表
     */
    List<ScrmQualityInspectionResultEntity> findByAssigneeIdAndInspectedAtBetween(String assigneeId, LocalDateTime from, LocalDateTime to);
}
