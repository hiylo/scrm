/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmForecastResultRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmForecastResultEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;

/**
 * SCRM 销售预测结果数据访问层。
 * <p>
 * 提供按 + 场景 ID 分页查询结果 (供 {@code getScenarioResults} 使用),
 * 按与场景 ID 查询全部结果 (供 {@code runScenario} 重算前清理与摘要统计使用),
 * 按与预测日期查询结果 (供 {@code getForecastByDate} 使用),
 * 按与场景 ID + 客群/产品/渠道/地区维度查询结果。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmForecastResultRepository extends JpaRepository<ScrmForecastResultEntity, Long>,
        JpaSpecificationExecutor<ScrmForecastResultEntity> {

    /**
     * 按与场景 ID 分页查询结果 (按预测日期升序)。
     *
     * @param scenarioId 场景 ID
     * @param pageable   分页参数
     * @return 结果分页
     */
    Page<ScrmForecastResultEntity> findByScenarioIdOrderByForecastDateAsc(Long scenarioId, Pageable pageable);

    /**
     * 按与场景 ID 查询全部结果 (按预测日期升序)。
     *
     * @param scenarioId 场景 ID
     * @return 结果列表
     */
    List<ScrmForecastResultEntity> findByScenarioIdOrderByForecastDateAsc(Long scenarioId);

    /**
     * 按与预测日期查询结果 (按预测日期升序)。
     *
     * @param forecastDate 预测日期
     * @return 结果列表
     */
    List<ScrmForecastResultEntity> findByForecastDate(LocalDate forecastDate);

    /**
     * 按与场景 ID 删除结果 (重算前清理用)。
     *
     * @param scenarioId 场景 ID
     */
    void deleteByScenarioId(Long scenarioId);
}
