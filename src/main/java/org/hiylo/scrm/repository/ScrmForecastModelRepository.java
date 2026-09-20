/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmForecastModelRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmForecastModelEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 销售预测模型数据访问层。
 * <p>
 * 提供按 + 模型编码定位模型 (供 {@code getModelByCode} 使用),
 * 按与目标指标分页查询模型 (供 {@code getModelsByMetric} 使用),
 * 按与启用状态查询模型列表 (供 {@code autoRetrain} 批量扫描使用),
 * 按与目标指标且已训练的模型按准确度降序分页查询 (供 {@code getBestModel} 使用)。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmForecastModelRepository extends JpaRepository<ScrmForecastModelEntity, Long>,
        JpaSpecificationExecutor<ScrmForecastModelEntity> {

    /**
     * 按与模型编码查询模型 (modelCode 唯一)。
     *
     * @param modelCode 模型编码
     * @return 模型 (可能为空)
     */
    Optional<ScrmForecastModelEntity> findByModelCode(String modelCode);

    /**
     * 按与目标指标分页查询模型。
     *
     * @param targetMetric 目标指标
     * @param pageable    分页参数
     * @return 模型分页结果
     */
    Page<ScrmForecastModelEntity> findByTargetMetric(String targetMetric, Pageable pageable);

    /**
     * 按与启用状态查询模型列表 (自动重训练批量扫描用)。
     *
     * @param enabled  启用状态
     * @return 模型列表
     */
    List<ScrmForecastModelEntity> findByEnabledAndIsAutoRetrainTrue(Boolean enabled);

    /**
     * 按与目标指标, 仅已训练模型, 按准确度评分降序分页查询 (最佳模型选取用)。
     *
     * @param targetMetric 目标指标
     * @param isTrained   是否已训练
     * @param pageable    分页参数
     * @return 模型分页结果
     */
    Page<ScrmForecastModelEntity> findByTargetMetricAndIsTrainedAndEnabledTrue(String targetMetric, Boolean isTrained, Pageable pageable);

    /**
     * 按与目标指标查询所有已训练且启用的模型 (模型对比用)。
     *
     * @param targetMetric 目标指标
     * @param isTrained   是否已训练
     * @return 模型列表
     */
    List<ScrmForecastModelEntity> findByTargetMetricAndIsTrainedAndEnabledTrue(String targetMetric, Boolean isTrained);

    /**
     * 按统计指定模型编码数量 (用于编码唯一性校验)。
     *
     * @param modelCode 模型编码
     * @return 数量
     */
    long countByModelCode(String modelCode);
}
