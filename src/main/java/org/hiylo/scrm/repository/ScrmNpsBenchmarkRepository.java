/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmNpsBenchmarkRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmNpsBenchmarkEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.Optional;

/**
 * SCRM NPS 基准数据访问层。
 * <p>
 * 提供按周期类型查询最新基准 (供 NPS 趋势 / 看板展示), 以及按周期类型 / 周期范围分页查询等能力,
 * 供 {@code ScrmNpsSurveyService.getLatestBenchmark} / {@code listBenchmarks} 使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmNpsBenchmarkRepository extends JpaRepository<ScrmNpsBenchmarkEntity, Long>,
        JpaSpecificationExecutor<ScrmNpsBenchmarkEntity> {

    /**
     * 按与周期类型查询最新基准 (按生成时间倒序取首条)。
     *
     * @param periodType 周期类型: MONTHLY / QUARTERLY / YEARLY
     * @param pageable   分页参数 (取首条)
     * @return 最新基准分页结果 (取第一个)
     */
    Page<ScrmNpsBenchmarkEntity> findByPeriodTypeOrderByGeneratedAtDesc(String periodType, Pageable pageable);

    /**
     * 按、周期类型与周期范围查询基准 (用于去重 / 校验)。
     *
     * @param periodType  周期类型
     * @param periodStart 周期开始日期
     * @param periodEnd   周期结束日期
     * @return 基准实体 (不存在返回 empty)
     */
    Optional<ScrmNpsBenchmarkEntity> findByPeriodTypeAndPeriodStartAndPeriodEnd(String periodType,
            LocalDate periodStart, LocalDate periodEnd);
}
