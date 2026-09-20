/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmForecastScenarioRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmForecastScenarioEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 销售预测场景数据访问层。
 * <p>
 * 提供按 + 场景编码定位场景 (供 {@code getScenarioByCode} 使用),
 * 按与模型 ID 查询场景列表 (供 {@code getBaselineScenario} 使用),
 * 按与目标周期查询场景列表 (供 {@code getScenarioByPeriod} 使用)。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmForecastScenarioRepository extends JpaRepository<ScrmForecastScenarioEntity, Long>,
        JpaSpecificationExecutor<ScrmForecastScenarioEntity> {

    /**
     * 按与场景编码查询场景 (scenarioCode 唯一)。
     *
     * @param scenarioCode 场景编码
     * @return 场景 (可能为空)
     */
    Optional<ScrmForecastScenarioEntity> findByScenarioCode(String scenarioCode);

    /**
     * 按与模型 ID 查询场景列表。
     *
     * @param modelId  模型 ID
     * @return 场景列表
     */
    List<ScrmForecastScenarioEntity> findByModelId(Long modelId);

    /**
     * 按与目标周期查询场景列表。
     *
     * @param targetPeriod 目标周期
     * @return 场景列表
     */
    List<ScrmForecastScenarioEntity> findByTargetPeriod(String targetPeriod);

    /**
     * 按统计指定场景编码数量 (用于编码唯一性校验)。
     *
     * @param scenarioCode 场景编码
     * @return 数量
     */
    long countByScenarioCode(String scenarioCode);
}
