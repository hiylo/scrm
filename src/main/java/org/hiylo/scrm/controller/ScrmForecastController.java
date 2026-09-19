/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmForecastController.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmForecastModelDto;
import org.hiylo.scrm.dto.ScrmForecastRunDto;
import org.hiylo.scrm.dto.ScrmForecastScenarioDto;
import org.hiylo.scrm.entity.ScrmForecastModelEntity;
import org.hiylo.scrm.entity.ScrmForecastResultEntity;
import org.hiylo.scrm.entity.ScrmForecastScenarioEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmForecastService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * SCRM 销售预测控制器。
 * <p>
 * 提供销售预测模块的完整接口, 分为四大域:
 * <ul>
 *   <li>Models: 预测模型管理 (CRUD / 启停 / 训练 / 自动重训练 / 准确度 / 历史 / 对比 / 最佳模型)。</li>
 *   <li>Scenarios: 预测场景管理 (CRUD / 运行 / 重跑 / 结果 / 审批 / 归档 / What-If / 对比)。</li>
 *   <li>Forecast: 预测结果查询 (按场景 / 日期 / 客群 / 产品 / 趋势 / 准确度 / 导出)。</li>
 *   <li>Stats: 统计与概览 (概览 / 准确度趋势 / 模型表现 / 预测 vs 实际)。</li>
 * </ul>
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@RestController
@RequestMapping("/scrm/forecasts")
@RequiredArgsConstructor
// Tag: SCRM 销售预测 -
public class ScrmForecastController {

    /** 销售预测服务 */
    private final ScrmForecastService scrmForecastService;

    // ============================================================
    // 模型管理 /models
    // ============================================================

    /**
     * 创建预测模型。
     *
     * @param dto 模型参数
     * @return 创建后的模型
     * @throws ScrmException 参数非法 / 编码重复
     */
    @RequirePermission(resource = "scrm_forecast", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/models")
    public OperationResponse<ScrmForecastModelEntity> createModel(@Valid @RequestBody ScrmForecastModelDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmForecastService.createModel(dto));
    }

    /**
     * 更新预测模型。
     *
     * @param id  模型 ID
     * @param dto 模型参数
     * @return 更新后的模型
     * @throws ScrmException 模型不存在 / 参数非法 / 编码重复
     */
    @RequirePermission(resource = "scrm_forecast", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/models/{id}")
    public OperationResponse<ScrmForecastModelEntity> updateModel(@PathVariable Long id,
                                                                    @RequestBody ScrmForecastModelDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmForecastService.updateModel(id, dto));
    }

    /**
     * 删除预测模型。
     *
     * @param id 模型 ID
     * @return 空响应
     * @throws ScrmException 模型不存在 / 存在关联场景
     */
    @RequirePermission(resource = "scrm_forecast", action = "delete")
    @DeleteMapping("/models/{id}")
    public OperationResponse<Void> deleteModel(@PathVariable Long id) throws ScrmException {
        scrmForecastService.deleteModel(id);
        return OperationResponse.build();
    }

    /**
     * 查询模型详情。
     *
     * @param id 模型 ID
     * @return 模型实体
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_forecast", action = "read")
    @GetMapping("/models/{id}")
    public OperationResponse<ScrmForecastModelEntity> getModel(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmForecastService.getModel(id));
    }

    /**
     * 按模型编码查询模型。
     *
     * @param code 模型编码
     * @return 模型实体
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_forecast", action = "read")
    @GetMapping("/models/code/{code}")
    public OperationResponse<ScrmForecastModelEntity> getModelByCode(
            @PathVariable String code) throws ScrmException {
        return OperationResponse.build(scrmForecastService.getModelByCode(code));
    }

    /**
     * 分页查询模型列表。
     *
     * @param modelType    模型类型过滤（可空）
     * @param targetMetric 目标指标过滤（可空）
     * @param enabled      启用状态过滤（可空）
     * @param isTrained    训练状态过滤（可空）
     * @param keyword      名称/编码关键字（可空）
     * @param page         页码（从 0 开始, 默认 0）
     * @param size         每页大小（默认 20）
     * @return 模型分页结果 (按 updateTime DESC)
     */
    @RequirePermission(resource = "scrm_forecast", action = "read")
    @GetMapping("/models/list")
    public OperationResponse<Page<ScrmForecastModelEntity>> listModels(
            @RequestParam(required = false) String modelType,
            @RequestParam(required = false) String targetMetric,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Boolean isTrained,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updateTime"));
        return OperationResponse.build(scrmForecastService.listModels(
                modelType, targetMetric, enabled, isTrained, keyword, pageable));
    }

    /**
     * 启用模型。
     *
     * @param id 模型 ID
     * @return 更新后的模型
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_forecast", action = "update")
    @PostMapping("/models/{id}/enable")
    public OperationResponse<ScrmForecastModelEntity> enableModel(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmForecastService.enableModel(id));
    }

    /**
     * 禁用模型。
     *
     * @param id 模型 ID
     * @return 更新后的模型
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_forecast", action = "update")
    @PostMapping("/models/{id}/disable")
    public OperationResponse<ScrmForecastModelEntity> disableModel(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmForecastService.disableModel(id));
    }

    /**
     * 训练模型 (完整实现移动平均 + 指数平滑)。
     *
     * @param id 模型 ID
     * @return 训练后的模型
     * @throws ScrmException 模型不存在 / 未启用 / 训练数据为空
     */
    @RequirePermission(resource = "scrm_forecast", action = "update")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/models/{id}/train")
    public OperationResponse<ScrmForecastModelEntity> trainModel(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmForecastService.trainModel(id));
    }

    /**
     * 自动重训练: 扫描所有启用且开启自动重训练的模型并逐个重训练。
     *
     * @return 重训练汇总
     */
    @RequirePermission(resource = "scrm_forecast", action = "update")
    @RateLimit(capacity = 5, refillTokens = 5, refillPeriodSeconds = 60)
    @PostMapping("/models/auto-retrain")
    public OperationResponse<Map<String, Object>> autoRetrain() {
        return OperationResponse.build(scrmForecastService.autoRetrain());
    }

    /**
     * 查询模型准确度指标。
     *
     * @param id 模型 ID
     * @return 准确度指标 Map
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_forecast", action = "read")
    @GetMapping("/models/{id}/accuracy")
    public OperationResponse<Map<String, Object>> getModelAccuracy(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmForecastService.getModelAccuracy(id));
    }

    /**
     * 查询模型预测历史。
     *
     * @param id   模型 ID
     * @param page 页码（从 0 开始, 默认 0）
     * @param size 每页大小（默认 20）
     * @return 预测结果分页 (按 forecastDate ASC)
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_forecast", action = "read")
    @GetMapping("/models/{id}/history")
    public OperationResponse<Page<ScrmForecastResultEntity>> getModelHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) throws ScrmException {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "forecastDate"));
        return OperationResponse.build(scrmForecastService.getModelHistory(id, pageable));
    }

    /**
     * 对比多个模型的准确度指标。
     *
     * @param modelIds 模型 ID 列表
     * @return 对比结果
     * @throws ScrmException 模型不存在 / 列表为空
     */
    @RequirePermission(resource = "scrm_forecast", action = "read")
    @PostMapping("/models/compare")
    public OperationResponse<Map<String, Object>> compareModels(
            @RequestBody List<Long> modelIds) throws ScrmException {
        return OperationResponse.build(scrmForecastService.compareModels(modelIds));
    }

    /**
     * 获取指定目标指标下准确度最高的已训练模型。
     *
     * @param targetMetric 目标指标（可空, 为空取所有已训练模型）
     * @return 最佳模型
     * @throws ScrmException 无符合条件的模型
     */
    @RequirePermission(resource = "scrm_forecast", action = "read")
    @GetMapping("/models/best")
    public OperationResponse<ScrmForecastModelEntity> getBestModel(
            @RequestParam(required = false) String targetMetric) throws ScrmException {
        return OperationResponse.build(scrmForecastService.getBestModel(targetMetric));
    }

    // ============================================================
    // 场景管理 /scenarios
    // ============================================================

    /**
     * 创建预测场景。
     *
     * @param dto 场景参数
     * @return 创建后的场景
     * @throws ScrmException 参数非法 / 模型不存在 / 编码重复
     */
    @RequirePermission(resource = "scrm_forecast", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/scenarios")
    public OperationResponse<ScrmForecastScenarioEntity> createScenario(@Valid @RequestBody ScrmForecastScenarioDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmForecastService.createScenario(dto));
    }

    /**
     * 更新预测场景。
     *
     * @param id  场景 ID
     * @param dto 场景参数
     * @return 更新后的场景
     * @throws ScrmException 场景不存在 / 参数非法 / 编码重复
     */
    @RequirePermission(resource = "scrm_forecast", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/scenarios/{id}")
    public OperationResponse<ScrmForecastScenarioEntity> updateScenario(@PathVariable Long id,
                                                                          @RequestBody ScrmForecastScenarioDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmForecastService.updateScenario(id, dto));
    }

    /**
     * 删除预测场景 (连同结果一并删除)。
     *
     * @param id 场景 ID
     * @return 空响应
     * @throws ScrmException 场景不存在
     */
    @RequirePermission(resource = "scrm_forecast", action = "delete")
    @DeleteMapping("/scenarios/{id}")
    public OperationResponse<Void> deleteScenario(@PathVariable Long id) throws ScrmException {
        scrmForecastService.deleteScenario(id);
        return OperationResponse.build();
    }

    /**
     * 查询场景详情。
     *
     * @param id 场景 ID
     * @return 场景实体
     * @throws ScrmException 场景不存在
     */
    @RequirePermission(resource = "scrm_forecast", action = "read")
    @GetMapping("/scenarios/{id}")
    public OperationResponse<ScrmForecastScenarioEntity> getScenario(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmForecastService.getScenario(id));
    }

    /**
     * 按场景编码查询场景。
     *
     * @param code 场景编码
     * @return 场景实体
     * @throws ScrmException 场景不存在
     */
    @RequirePermission(resource = "scrm_forecast", action = "read")
    @GetMapping("/scenarios/code/{code}")
    public OperationResponse<ScrmForecastScenarioEntity> getScenarioByCode(@PathVariable String code)
            throws ScrmException {
        return OperationResponse.build(scrmForecastService.getScenarioByCode(code));
    }

    /**
     * 分页查询场景列表。
     *
     * @param modelId      模型 ID 过滤（可空）
     * @param scenarioType 场景类型过滤（可空）
     * @param status       状态过滤（可空）
     * @param targetPeriod 目标周期过滤（可空）
     * @param keyword      名称/编码关键字（可空）
     * @param page         页码（从 0 开始, 默认 0）
     * @param size         每页大小（默认 20）
     * @return 场景分页结果 (按 updateTime DESC)
     */
    @RequirePermission(resource = "scrm_forecast", action = "read")
    @GetMapping("/scenarios/list")
    public OperationResponse<Page<ScrmForecastScenarioEntity>> listScenarios(
            @RequestParam(required = false) Long modelId,
            @RequestParam(required = false) String scenarioType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String targetPeriod,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updateTime"));
        return OperationResponse.build(scrmForecastService.listScenarios(
                modelId, scenarioType, status, targetPeriod, keyword, pageable));
    }

    /**
     * 运行预测场景: 加载模型 → 获取数据 → 应用参数与调整因子 → 生成预测 → 计算置信区间。
     *
     * @param runDto 运行参数
     * @return 更新后的场景
     * @throws ScrmException 场景/模型不存在 / 模型未训练 / 运行失败
     */
    @RequirePermission(resource = "scrm_forecast", action = "execute")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/scenarios/run")
    public OperationResponse<ScrmForecastScenarioEntity> runScenario(@Valid @RequestBody ScrmForecastRunDto runDto)
            throws ScrmException {
        return OperationResponse.build(scrmForecastService.runScenario(runDto));
    }

    /**
     * 重跑场景 (使用场景已保存的参数与调整因子)。
     *
     * @param scenarioId 场景 ID
     * @return 更新后的场景
     * @throws ScrmException 场景不存在 / 模型未训练 / 运行失败
     */
    @RequirePermission(resource = "scrm_forecast", action = "execute")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/scenarios/rerun")
    public OperationResponse<ScrmForecastScenarioEntity> rerunScenario(@RequestParam Long scenarioId)
            throws ScrmException {
        return OperationResponse.build(scrmForecastService.rerunScenario(scenarioId));
    }

    /**
     * 查询场景预测结果。
     *
     * @param id   场景 ID
     * @param page 页码（从 0 开始, 默认 0）
     * @param size 每页大小（默认 20）
     * @return 预测结果分页 (按 forecastDate ASC)
     * @throws ScrmException 场景不存在
     */
    @RequirePermission(resource = "scrm_forecast", action = "read")
    @GetMapping("/scenarios/{id}/results")
    public OperationResponse<Page<ScrmForecastResultEntity>> getScenarioResults(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) throws ScrmException {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "forecastDate"));
        return OperationResponse.build(scrmForecastService.getScenarioResults(id, pageable));
    }

    /**
     * 审批场景。
     *
     * @param scenarioId 场景 ID
     * @param approvedBy 审批人（可空, 缺省取当前用户）
     * @return 更新后的场景
     * @throws ScrmException 场景不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_forecast", action = "approve")
    @PostMapping("/scenarios/approve")
    public OperationResponse<ScrmForecastScenarioEntity> approveScenario(
            @RequestParam Long scenarioId,
            @RequestParam(required = false) String approvedBy) throws ScrmException {
        return OperationResponse.build(scrmForecastService.approveScenario(scenarioId, approvedBy));
    }

    /**
     * 归档场景。
     *
     * @param scenarioId 场景 ID
     * @return 更新后的场景
     * @throws ScrmException 场景不存在
     */
    @RequirePermission(resource = "scrm_forecast", action = "update")
    @PostMapping("/scenarios/archive")
    public OperationResponse<ScrmForecastScenarioEntity> archiveScenario(@RequestParam Long scenarioId)
            throws ScrmException {
        return OperationResponse.build(scrmForecastService.archiveScenario(scenarioId));
    }

    /**
     * What-If 分析: 基于入参创建一个 WHAT_IF 类型场景并立即运行。
     *
     * @param dto 场景参数 (scenarioType 可空, 强制为 WHAT_IF)
     * @return What-If 分析结果 {scenario, results}
     * @throws ScrmException 参数非法 / 模型不存在 / 模型未训练
     */
    @RequirePermission(resource = "scrm_forecast", action = "execute")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/scenarios/what-if")
    public OperationResponse<Map<String, Object>> whatIf(@Valid @RequestBody ScrmForecastScenarioDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmForecastService.whatIf(dto));
    }

    /**
     * 对比多个场景的预测结果。
     *
     * @param scenarioIds 场景 ID 列表
     * @return 对比结果
     * @throws ScrmException 场景不存在 / 列表为空
     */
    @RequirePermission(resource = "scrm_forecast", action = "read")
    @PostMapping("/scenarios/compare-scenarios")
    public OperationResponse<Map<String, Object>> compareScenarios(@RequestBody List<Long> scenarioIds)
            throws ScrmException {
        return OperationResponse.build(scrmForecastService.compareScenarios(scenarioIds));
    }

    // ============================================================
    // 预测结果查询 /forecast
    // ============================================================

    /**
     * 查询场景预测结果 (按预测日期升序分页)。
     *
     * @param scenarioId 场景 ID
     * @param page       页码（从 0 开始, 默认 0）
     * @param size       每页大小（默认 20）
     * @return 预测结果分页
     * @throws ScrmException 场景不存在
     */
    @RequirePermission(resource = "scrm_forecast", action = "read")
    @GetMapping("/forecast")
    public OperationResponse<Page<ScrmForecastResultEntity>> getForecast(
            @RequestParam Long scenarioId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) throws ScrmException {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "forecastDate"));
        return OperationResponse.build(scrmForecastService.getForecast(scenarioId, pageable));
    }

    /**
     * 按预测日期查询结果 (跨场景)。
     *
     * @param date 预测日期 (ISO 格式: yyyy-MM-dd)
     * @return 预测结果列表
     */
    @RequirePermission(resource = "scrm_forecast", action = "read")
    @GetMapping("/forecast/by-date/{date}")
    public OperationResponse<List<ScrmForecastResultEntity>> getForecastByDate(@PathVariable LocalDate date) {
        return OperationResponse.build(scrmForecastService.getForecastByDate(date));
    }

    /**
     * 按客群查询预测结果。
     *
     * @param segment 客群
     * @return 预测结果列表
     * @throws ScrmException 客群为空
     */
    @RequirePermission(resource = "scrm_forecast", action = "read")
    @GetMapping("/forecast/by-segment")
    public OperationResponse<List<ScrmForecastResultEntity>> getForecastBySegment(@RequestParam String segment)
            throws ScrmException {
        return OperationResponse.build(scrmForecastService.getForecastBySegment(segment));
    }

    /**
     * 按产品查询预测结果。
     *
     * @param product 产品
     * @return 预测结果列表
     * @throws ScrmException 产品为空
     */
    @RequirePermission(resource = "scrm_forecast", action = "read")
    @GetMapping("/forecast/by-product")
    public OperationResponse<List<ScrmForecastResultEntity>> getForecastByProduct(@RequestParam String product)
            throws ScrmException {
        return OperationResponse.build(scrmForecastService.getForecastByProduct(product));
    }

    /**
     * 查询预测趋势 (按场景汇总各周期预测值与置信区间)。
     *
     * @param scenarioId 场景 ID
     * @param periods    返回最近周期数（可空, 缺省全部）
     * @return 趋势结果 Map
     * @throws ScrmException 场景不存在
     */
    @RequirePermission(resource = "scrm_forecast", action = "read")
    @GetMapping("/forecast/trend")
    public OperationResponse<Map<String, Object>> getForecastTrend(
            @RequestParam Long scenarioId,
            @RequestParam(required = false) Integer periods) throws ScrmException {
        return OperationResponse.build(scrmForecastService.getForecastTrend(scenarioId, periods));
    }

    /**
     * 查询场景预测准确度 (基于已回填实际值的结果)。
     *
     * @param scenarioId 场景 ID
     * @return 准确度指标 Map
     * @throws ScrmException 场景不存在
     */
    @RequirePermission(resource = "scrm_forecast", action = "read")
    @GetMapping("/forecast/accuracy")
    public OperationResponse<Map<String, Object>> getForecastAccuracy(@RequestParam Long scenarioId)
            throws ScrmException {
        return OperationResponse.build(scrmForecastService.getForecastAccuracy(scenarioId));
    }

    /**
     * 导出场景预测结果 (scenarioId 为空时导出全部)。
     *
     * @param scenarioId 场景 ID（可空）
     * @return 预测结果列表
     * @throws ScrmException 场景不存在
     */
    @RequirePermission(resource = "scrm_forecast", action = "read")
    @GetMapping("/forecast/export")
    public OperationResponse<List<ScrmForecastResultEntity>> exportForecast(
            @RequestParam(required = false) Long scenarioId) throws ScrmException {
        return OperationResponse.build(scrmForecastService.exportForecast(scenarioId));
    }

    // ============================================================
    // 统计 /stats
    // ============================================================

    /**
     * 预测概览: 顶层仪表盘汇总。
     *
     * @return 概览 Map
     */
    @RequirePermission(resource = "scrm_forecast", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getForecastOverview() {
        return OperationResponse.build(scrmForecastService.getForecastOverview());
    }

    /**
     * 准确度趋势: 按模型最近训练时间聚合平均准确度。
     *
     * @param months 回溯月数（默认 12）
     * @return 趋势结果 Map
     */
    @RequirePermission(resource = "scrm_forecast", action = "read")
    @GetMapping("/stats/accuracy-trend")
    public OperationResponse<Map<String, Object>> getAccuracyTrend(
            @RequestParam(defaultValue = "12") int months) {
        return OperationResponse.build(scrmForecastService.getAccuracyTrend(months));
    }

    /**
     * 模型表现: 列出所有已训练模型的关键准确度指标。
     *
     * @return 模型表现列表
     */
    @RequirePermission(resource = "scrm_forecast", action = "read")
    @GetMapping("/stats/model-performance")
    public OperationResponse<Map<String, Object>> getModelPerformance() {
        return OperationResponse.build(scrmForecastService.getModelPerformance());
    }

    /**
     * 预测 vs 实际: 对比场景中已回填实际值的预测点。
     *
     * @param scenarioId 场景 ID
     * @return 对比结果 Map
     * @throws ScrmException 场景不存在
     */
    @RequirePermission(resource = "scrm_forecast", action = "read")
    @GetMapping("/stats/forecast-vs-actual")
    public OperationResponse<Map<String, Object>> getForecastVsActual(@RequestParam Long scenarioId)
            throws ScrmException {
        return OperationResponse.build(scrmForecastService.getForecastVsActual(scenarioId));
    }
}
