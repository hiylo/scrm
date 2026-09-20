/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmForecastService.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.dto.ScrmForecastModelDto;
import org.hiylo.scrm.dto.ScrmForecastRunDto;
import org.hiylo.scrm.dto.ScrmForecastScenarioDto;
import org.hiylo.scrm.entity.ScrmForecastModelEntity;
import org.hiylo.scrm.entity.ScrmForecastResultEntity;
import org.hiylo.scrm.entity.ScrmForecastScenarioEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * SCRM 销售预测服务 (门面)。
 * <p>
 * 作为销售预测模块的统一入口, 保持对外 public 方法签名不变, 实际能力按子域委托给
 * {@link ScrmForecastModelService} (模型管理)、{@link ScrmForecastScenarioService} (场景管理)、
 * {@link ScrmForecastQueryService} (结果查询) 与 {@link ScrmForecastStatsService} (统计概览)。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
public class ScrmForecastService {

    /** 预测模型子域服务 */
    private final ScrmForecastModelService modelService;
    /** 预测场景子域服务 */
    private final ScrmForecastScenarioService scenarioService;
    /** 预测结果查询子域服务 */
    private final ScrmForecastQueryService queryService;
    /** 预测统计子域服务 */
    private final ScrmForecastStatsService statsService;

    /**
     * 创建预测模型。
     *
     * @param dto 模型参数
     * @return 创建后的模型
     * @throws ScrmException 参数非法 / 编码重复
     */
    public ScrmForecastModelEntity createModel(ScrmForecastModelDto dto) throws ScrmException {
        return modelService.createModel(dto);
    }

    /**
     * 更新预测模型。
     *
     * @param id  模型 ID
     * @param dto 模型参数
     * @return 更新后的模型
     * @throws ScrmException 模型不存在 / 参数非法 / 编码重复
     */
    public ScrmForecastModelEntity updateModel(Long id, ScrmForecastModelDto dto) throws ScrmException {
        return modelService.updateModel(id, dto);
    }

    /**
     * 删除预测模型。
     *
     * @param id 模型 ID
     * @throws ScrmException 模型不存在 / 存在关联场景
     */
    public void deleteModel(Long id) throws ScrmException {
        modelService.deleteModel(id);
    }

    /**
     * 查询模型详情。
     *
     * @param id 模型 ID
     * @return 模型实体
     * @throws ScrmException 模型不存在
     */
    public ScrmForecastModelEntity getModel(Long id) throws ScrmException {
        return modelService.getModel(id);
    }

    /**
     * 按模型编码查询模型。
     *
     * @param code 模型编码
     * @return 模型实体
     * @throws ScrmException 模型不存在
     */
    public ScrmForecastModelEntity getModelByCode(String code) throws ScrmException {
        return modelService.getModelByCode(code);
    }

    /**
     * 分页查询模型列表。
     *
     * @param modelType     模型类型过滤（可空）
     * @param targetMetric  目标指标过滤（可空）
     * @param enabled       启用状态过滤（可空）
     * @param isTrained     训练状态过滤（可空）
     * @param keyword       名称/编码关键字（可空）
     * @param pageable      分页参数
     * @return 模型分页结果
     */
    public Page<ScrmForecastModelEntity> listModels(String modelType, String targetMetric, Boolean enabled,
                                                     Boolean isTrained, String keyword, Pageable pageable) {
        return modelService.listModels(modelType, targetMetric, enabled, isTrained, keyword, pageable);
    }

    /**
     * 启用模型。
     *
     * @param id 模型 ID
     * @return 更新后的模型
     * @throws ScrmException 模型不存在
     */
    public ScrmForecastModelEntity enableModel(Long id) throws ScrmException {
        return modelService.enableModel(id);
    }

    /**
     * 禁用模型。
     *
     * @param id 模型 ID
     * @return 更新后的模型
     * @throws ScrmException 模型不存在
     */
    public ScrmForecastModelEntity disableModel(Long id) throws ScrmException {
        return modelService.disableModel(id);
    }

    /**
     * 训练模型。
     *
     * @param modelId 模型 ID
     * @return 训练后的模型
     * @throws ScrmException 模型不存在 / 未启用 / 训练数据为空
     */
    public ScrmForecastModelEntity trainModel(Long modelId) throws ScrmException {
        return modelService.trainModel(modelId);
    }

    /**
     * 重新训练模型。
     *
     * @param modelId 模型 ID
     * @return 训练后的模型
     * @throws ScrmException 模型不存在 / 未启用 / 训练数据为空
     */
    public ScrmForecastModelEntity retrainModel(Long modelId) throws ScrmException {
        return modelService.retrainModel(modelId);
    }

    /**
     * 自动重训练。
     *
     * @return 重训练汇总
     */
    public Map<String, Object> autoRetrain() {
        return modelService.autoRetrain();
    }

    /**
     * 查询模型准确度指标。
     *
     * @param id 模型 ID
     * @return 准确度指标 Map
     * @throws ScrmException 模型不存在
     */
    public Map<String, Object> getModelAccuracy(Long id) throws ScrmException {
        return modelService.getModelAccuracy(id);
    }

    /**
     * 查询模型预测历史。
     *
     * @param id       模型 ID
     * @param pageable 分页参数
     * @return 预测结果分页
     * @throws ScrmException 模型不存在
     */
    public Page<ScrmForecastResultEntity> getModelHistory(Long id, Pageable pageable) throws ScrmException {
        return modelService.getModelHistory(id, pageable);
    }

    /**
     * 对比多个模型的准确度指标。
     *
     * @param modelIds 模型 ID 列表
     * @return 对比结果
     * @throws ScrmException 模型不存在 / 列表为空
     */
    public Map<String, Object> compareModels(List<Long> modelIds) throws ScrmException {
        return modelService.compareModels(modelIds);
    }

    /**
     * 获取最佳模型。
     *
     * @param targetMetric 目标指标 (可空)
     * @return 最佳模型
     * @throws ScrmException 无符合条件的模型
     */
    public ScrmForecastModelEntity getBestModel(String targetMetric) throws ScrmException {
        return modelService.getBestModel(targetMetric);
    }

    /**
     * 更新模型统计。
     *
     * @param id 模型 ID
     * @return 更新后的模型
     * @throws ScrmException 模型不存在
     */
    public ScrmForecastModelEntity updateModelStats(Long id) throws ScrmException {
        return modelService.updateModelStats(id);
    }

    /**
     * 创建预测场景。
     *
     * @param dto 场景参数
     * @return 创建后的场景
     * @throws ScrmException 参数非法 / 模型不存在 / 编码重复
     */
    public ScrmForecastScenarioEntity createScenario(ScrmForecastScenarioDto dto) throws ScrmException {
        return scenarioService.createScenario(dto);
    }

    /**
     * 更新预测场景。
     *
     * @param id  场景 ID
     * @param dto 场景参数
     * @return 更新后的场景
     * @throws ScrmException 场景不存在 / 参数非法 / 编码重复
     */
    public ScrmForecastScenarioEntity updateScenario(Long id, ScrmForecastScenarioDto dto) throws ScrmException {
        return scenarioService.updateScenario(id, dto);
    }

    /**
     * 删除预测场景。
     *
     * @param id 场景 ID
     * @throws ScrmException 场景不存在
     */
    public void deleteScenario(Long id) throws ScrmException {
        scenarioService.deleteScenario(id);
    }

    /**
     * 查询场景详情。
     *
     * @param id 场景 ID
     * @return 场景实体
     * @throws ScrmException 场景不存在
     */
    public ScrmForecastScenarioEntity getScenario(Long id) throws ScrmException {
        return scenarioService.getScenario(id);
    }

    /**
     * 按场景编码查询场景。
     *
     * @param code 场景编码
     * @return 场景实体
     * @throws ScrmException 场景不存在
     */
    public ScrmForecastScenarioEntity getScenarioByCode(String code) throws ScrmException {
        return scenarioService.getScenarioByCode(code);
    }

    /**
     * 分页查询场景列表。
     *
     * @param modelId       模型 ID 过滤（可空）
     * @param scenarioType  场景类型过滤（可空）
     * @param status        状态过滤（可空）
     * @param targetPeriod  目标周期过滤（可空）
     * @param keyword       名称/编码关键字（可空）
     * @param pageable      分页参数
     * @return 场景分页结果
     */
    public Page<ScrmForecastScenarioEntity> listScenarios(Long modelId, String scenarioType, String status,
                                                            String targetPeriod, String keyword, Pageable pageable) {
        return scenarioService.listScenarios(modelId, scenarioType, status, targetPeriod, keyword, pageable);
    }

    /**
     * 运行预测场景。
     *
     * @param runDto 运行参数
     * @return 更新后的场景
     * @throws ScrmException 场景/模型不存在 / 模型未训练 / 运行失败
     */
    public ScrmForecastScenarioEntity runScenario(ScrmForecastRunDto runDto) throws ScrmException {
        return scenarioService.runScenario(runDto);
    }

    /**
     * 重跑场景。
     *
     * @param scenarioId 场景 ID
     * @return 更新后的场景
     * @throws ScrmException 场景不存在 / 模型未训练 / 运行失败
     */
    public ScrmForecastScenarioEntity rerunScenario(Long scenarioId) throws ScrmException {
        return scenarioService.rerunScenario(scenarioId);
    }

    /**
     * 查询场景预测结果。
     *
     * @param id       场景 ID
     * @param pageable 分页参数
     * @return 预测结果分页
     * @throws ScrmException 场景不存在
     */
    public Page<ScrmForecastResultEntity> getScenarioResults(Long id, Pageable pageable) throws ScrmException {
        return scenarioService.getScenarioResults(id, pageable);
    }

    /**
     * 审批场景。
     *
     * @param scenarioId 场景 ID
     * @param approvedBy 审批人 (可空)
     * @return 更新后的场景
     * @throws ScrmException 场景不存在 / 状态非法
     */
    public ScrmForecastScenarioEntity approveScenario(Long scenarioId, String approvedBy) throws ScrmException {
        return scenarioService.approveScenario(scenarioId, approvedBy);
    }

    /**
     * 归档场景。
     *
     * @param scenarioId 场景 ID
     * @return 更新后的场景
     * @throws ScrmException 场景不存在
     */
    public ScrmForecastScenarioEntity archiveScenario(Long scenarioId) throws ScrmException {
        return scenarioService.archiveScenario(scenarioId);
    }

    /**
     * What-If 分析。
     *
     * @param dto 场景参数
     * @return What-If 分析结果
     * @throws ScrmException 参数非法 / 模型不存在 / 模型未训练
     */
    public Map<String, Object> whatIf(ScrmForecastScenarioDto dto) throws ScrmException {
        return scenarioService.whatIf(dto);
    }

    /**
     * 对比多个场景的预测结果。
     *
     * @param scenarioIds 场景 ID 列表
     * @return 对比结果
     * @throws ScrmException 场景不存在 / 列表为空
     */
    public Map<String, Object> compareScenarios(List<Long> scenarioIds) throws ScrmException {
        return scenarioService.compareScenarios(scenarioIds);
    }

    /**
     * 查询场景预测结果。
     *
     * @param scenarioId 场景 ID
     * @param pageable   分页参数
     * @return 预测结果分页
     * @throws ScrmException 场景不存在
     */
    public Page<ScrmForecastResultEntity> getForecast(Long scenarioId, Pageable pageable) throws ScrmException {
        return queryService.getForecast(scenarioId, pageable);
    }

    /**
     * 按预测日期查询结果。
     *
     * @param date 预测日期
     * @return 预测结果列表
     */
    public List<ScrmForecastResultEntity> getForecastByDate(LocalDate date) {
        return queryService.getForecastByDate(date);
    }

    /**
     * 按客群查询预测结果。
     *
     * @param segment 客群
     * @return 预测结果列表
     * @throws ScrmException 客群为空
     */
    public List<ScrmForecastResultEntity> getForecastBySegment(String segment) throws ScrmException {
        return queryService.getForecastBySegment(segment);
    }

    /**
     * 按产品查询预测结果。
     *
     * @param product 产品
     * @return 预测结果列表
     * @throws ScrmException 产品为空
     */
    public List<ScrmForecastResultEntity> getForecastByProduct(String product) throws ScrmException {
        return queryService.getForecastByProduct(product);
    }

    /**
     * 查询预测趋势。
     *
     * @param scenarioId 场景 ID
     * @param periods    返回最近周期数 (可空)
     * @return 趋势结果 Map
     * @throws ScrmException 场景不存在
     */
    public Map<String, Object> getForecastTrend(Long scenarioId, Integer periods) throws ScrmException {
        return queryService.getForecastTrend(scenarioId, periods);
    }

    /**
     * 查询场景预测准确度。
     *
     * @param scenarioId 场景 ID
     * @return 准确度指标 Map
     * @throws ScrmException 场景不存在
     */
    public Map<String, Object> getForecastAccuracy(Long scenarioId) throws ScrmException {
        return queryService.getForecastAccuracy(scenarioId);
    }

    /**
     * 导出场景预测结果。
     *
     * @param scenarioId 场景 ID (可空)
     * @return 预测结果列表
     * @throws ScrmException 场景不存在
     */
    public List<ScrmForecastResultEntity> exportForecast(Long scenarioId) throws ScrmException {
        return queryService.exportForecast(scenarioId);
    }

    /**
     * 预测统计。
     *
     * @return 统计结果 Map
     */
    public Map<String, Object> getForecastStats() {
        return statsService.getForecastStats();
    }

    /**
     * 准确度趋势。
     *
     * @param months 回溯月数 (可空)
     * @return 趋势结果 Map
     */
    public Map<String, Object> getAccuracyTrend(Integer months) {
        return statsService.getAccuracyTrend(months);
    }

    /**
     * 模型表现。
     *
     * @return 模型表现列表
     */
    public Map<String, Object> getModelPerformance() {
        return statsService.getModelPerformance();
    }

    /**
     * 预测 vs 实际。
     *
     * @param scenarioId 场景 ID
     * @return 对比结果 Map
     * @throws ScrmException 场景不存在
     */
    public Map<String, Object> getForecastVsActual(Long scenarioId) throws ScrmException {
        return statsService.getForecastVsActual(scenarioId);
    }

    /**
     * 预测概览。
     *
     * @return 概览 Map
     */
    public Map<String, Object> getForecastOverview() {
        return statsService.getForecastOverview();
    }
}
