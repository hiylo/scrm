/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLtvPredictionController.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmCustomerLtvDto;
import org.hiylo.scrm.dto.ScrmLtvCalculateDto;
import org.hiylo.scrm.dto.ScrmLtvCohortDto;
import org.hiylo.scrm.dto.ScrmLtvForecastDto;
import org.hiylo.scrm.dto.ScrmLtvModelDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmLtvPredictionService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 客户终身价值预测 (LTV) 控制器
 * <p>
 * 提供 LTV 模型管理、客户 LTV 计算、价值分层、价值预测、同期群分析与统计接口。
 * 基于 LTV 模型对客户终身价值进行预测、分层与流失预警。
 * 权限由 gateway-server 统一鉴权, 此处通过 {@link RequirePermission} 声明资源与动作元数据。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/ltv")
@RequiredArgsConstructor
public class ScrmLtvPredictionController {

    /** LTV 预测服务 */
    private final ScrmLtvPredictionService ltvPredictionService;

    // ============================================================
    // LTV 模型管理
    // ============================================================

    /**
     * 创建 LTV 模型
     *
     * @param dto 模型参数
     * @return 创建后的模型
     */
    @RequirePermission(resource = "scrm_ltv", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/models")
    public OperationResponse<ScrmLtvModelDto> createModel(@Valid @RequestBody ScrmLtvModelDto dto)
            throws ScrmException {
        return OperationResponse.build(ltvPredictionService.createModel(dto));
    }

    /**
     * 更新 LTV 模型
     *
     * @param id  模型 ID
     * @param dto 模型参数
     * @return 更新后的模型
     */
    @RequirePermission(resource = "scrm_ltv", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/models/{id}")
    public OperationResponse<ScrmLtvModelDto> updateModel(@PathVariable Long id,
                                                            @RequestBody ScrmLtvModelDto dto)
            throws ScrmException {
        return OperationResponse.build(ltvPredictionService.updateModel(id, dto));
    }

    /**
     * 删除 LTV 模型
     *
     * @param id 模型 ID
     * @return 空响应
     * @throws ScrmException 模型不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_ltv", action = "delete")
    @DeleteMapping("/models/{id}")
    public OperationResponse<Void> deleteModel(@PathVariable Long id) throws ScrmException {
        ltvPredictionService.deleteModel(id);
        return OperationResponse.build();
    }

    /**
     * 查询 LTV 模型详情
     *
     * @param id 模型 ID
     * @return 模型详情
     * @throws ScrmException 模型不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_ltv", action = "read")
    @GetMapping("/models/{id}")
    public OperationResponse<ScrmLtvModelDto> getModel(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(ltvPredictionService.getModel(id));
    }

    /**
     * 按模型编码查询模型
     *
     * @param code 模型编码
     * @return 模型详情
     * @throws ScrmException 模型不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_ltv", action = "read")
    @GetMapping("/models/code/{code}")
    public OperationResponse<ScrmLtvModelDto> getModelByCode(@PathVariable String code) throws ScrmException {
        return OperationResponse.build(ltvPredictionService.getModelByCode(code));
    }

    /**
     * 分页查询 LTV 模型, 支持按模型类型、发布状态与关键词过滤
     *
     * @param modelType   模型类型过滤 (可选)
     * @param isPublished 发布状态过滤 (可选)
     * @param keyword     名称/编码关键词 (可选)
     * @param page        页码 (从 0 开始, 默认 0)
     * @param size        每页大小 (默认 20)
     * @return 模型分页结果
     */
    @RequirePermission(resource = "scrm_ltv", action = "read")
    @GetMapping("/models/list")
    public OperationResponse<Page<ScrmLtvModelDto>> listModels(
            @RequestParam(required = false) String modelType,
            @RequestParam(required = false) Boolean isPublished,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(ltvPredictionService.listModels(modelType, isPublished, keyword, pageable));
    }

    /**
     * 发布 LTV 模型
     *
     * @param id 模型 ID
     * @return 更新后的模型
     * @throws ScrmException 模型不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_ltv", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/models/{id}/publish")
    public OperationResponse<ScrmLtvModelDto> publishModel(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(ltvPredictionService.publishModel(id));
    }

    /**
     * 取消发布 LTV 模型
     *
     * @param id 模型 ID
     * @return 更新后的模型
     * @throws ScrmException 模型不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_ltv", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/models/{id}/unpublish")
    public OperationResponse<ScrmLtvModelDto> unpublishModel(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(ltvPredictionService.unpublishModel(id));
    }

    /**
     * 设置默认 LTV 模型
     *
     * @param id 模型 ID
     * @return 更新后的模型
     * @throws ScrmException 模型不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_ltv", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/models/{id}/default")
    public OperationResponse<ScrmLtvModelDto> setDefaultModel(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(ltvPredictionService.setDefault(id));
    }

    /**
     * 复制 LTV 模型
     *
     * @param id 源模型 ID
     * @return 复制后的模型
     * @throws ScrmException 模型不存在 / 复制失败
     */
    @RequirePermission(resource = "scrm_ltv", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/models/{id}/copy")
    public OperationResponse<ScrmLtvModelDto> copyModel(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(ltvPredictionService.copyModel(id));
    }

    // ============================================================
    // LTV 计算
    // ============================================================

    /**
     * 计算单个客户 LTV
     *
     * @param customerId 客户 ID
     * @param modelId    模型 ID
     * @return LTV 计算结果
     */
    @RequirePermission(resource = "scrm_ltv", action = "execute")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/calculate")
    public OperationResponse<ScrmCustomerLtvDto> calculate(@RequestParam Long customerId,
                                                             @RequestParam Long modelId)
            throws ScrmException {
        return OperationResponse.build(ltvPredictionService.calculateLtv(customerId, modelId));
    }

    /**
     * 批量计算客户 LTV
     *
     * @param dto 批量计算请求 (模型 ID + 客户 ID 列表)
     * @return 计算结果列表
     * @throws ScrmException 模型不存在 / 参数非法 / 计算失败
     */
    @RequirePermission(resource = "scrm_ltv", action = "execute")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/batch-calculate")
    public OperationResponse<List<ScrmCustomerLtvDto>> batchCalculate(
            @Valid @RequestBody ScrmLtvCalculateDto dto) throws ScrmException {
        return OperationResponse.build(ltvPredictionService.batchCalculate(dto));
    }

    /**
     * 计算所有客户 LTV
     *
     * @param modelId 模型 ID
     * @return 计算结果列表
     */
    @RequirePermission(resource = "scrm_ltv", action = "execute")
    @RateLimit(capacity = 5, refillTokens = 5, refillPeriodSeconds = 60)
    @PostMapping("/calculate-all/{modelId}")
    public OperationResponse<List<ScrmCustomerLtvDto>> calculateAll(@PathVariable Long modelId)
            throws ScrmException {
        return OperationResponse.build(ltvPredictionService.calculateAll(modelId));
    }

    /**
     * 重新计算客户 LTV
     *
     * @param customerId 客户 ID
     * @param modelId    模型 ID
     * @return 重新计算后的结果
     */
    @RequirePermission(resource = "scrm_ltv", action = "execute")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/{id}/recalculate")
    public OperationResponse<ScrmCustomerLtvDto> recalculate(@PathVariable("id") Long customerId,
                                                               @RequestParam Long modelId)
            throws ScrmException {
        return OperationResponse.build(ltvPredictionService.recalculate(customerId, modelId));
    }

    /**
     * 查询客户 LTV 计算结果详情
     *
     * @param id LTV 结果 ID
     * @return LTV 结果
     * @throws ScrmException 结果不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_ltv", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmCustomerLtvDto> getLtv(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(ltvPredictionService.getLtv(id));
    }

    /**
     * 按客户 ID 查询 LTV 结果
     *
     * @param customerId 客户 ID
     * @param modelId    模型 ID (可选, 为空时取最新一条)
     * @return LTV 结果
     */
    @RequirePermission(resource = "scrm_ltv", action = "read")
    @GetMapping("/customer/{customerId}")
    public OperationResponse<ScrmCustomerLtvDto> getLtvByCustomer(@PathVariable Long customerId,
                                                                    @RequestParam(required = false) Long modelId)
            throws ScrmException {
        return OperationResponse.build(ltvPredictionService.getLtvByCustomer(customerId, modelId));
    }

    /**
     * 分页查询客户 LTV 结果, 支持按价值层级、LTV 范围、增长潜力过滤
     *
     * @param valueTier       价值层级过滤 (可选)
     * @param minLtv          最小预测 LTV 过滤 (可选)
     * @param maxLtv          最大预测 LTV 过滤 (可选)
     * @param growthPotential 增长潜力过滤 (可选)
     * @param sortBy          排序字段 (可选: predictedLtv/historicalLtv/churnProbability/roi)
     * @param page            页码 (从 0 开始, 默认 0)
     * @param size            每页大小 (默认 20)
     * @return LTV 结果分页
     */
    @RequirePermission(resource = "scrm_ltv", action = "read")
    @GetMapping("/list")
    public OperationResponse<Page<ScrmCustomerLtvDto>> listLtv(
            @RequestParam(required = false) String valueTier,
            @RequestParam(required = false) Double minLtv,
            @RequestParam(required = false) Double maxLtv,
            @RequestParam(required = false) String growthPotential,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(ltvPredictionService.listLtv(
                valueTier, minLtv, maxLtv, growthPotential, sortBy, pageable));
    }

    /**
     * 查询高价值客户 (按预测 LTV 降序取 Top N)
     *
     * @param limit 返回条数 (默认 10)
     * @return LTV 结果列表
     */
    @RequirePermission(resource = "scrm_ltv", action = "read")
    @GetMapping("/top")
    public OperationResponse<List<ScrmCustomerLtvDto>> getTopCustomers(
            @RequestParam(defaultValue = "10") int limit) {
        return OperationResponse.build(ltvPredictionService.getTopCustomers(limit));
    }

    // ============================================================
    // 价值层级
    // ============================================================

    /**
     * 确定价值层级
     *
     * @param ltv        预测 LTV
     * @param thresholds 模型层级阈值 JSON (可选)
     * @return 价值层级
     */
    @RequirePermission(resource = "scrm_ltv", action = "read")
    @PostMapping("/tiers/determine")
    public OperationResponse<Map<String, Object>> determineTier(@RequestParam Double ltv,
                                                                  @RequestParam(required = false) String thresholds) {
        String tier = ltvPredictionService.determineTier(ltv, thresholds);
        return OperationResponse.build(Map.of("tier", tier, "ltv", ltv));
    }

    /**
     * 价值层级分布统计
     *
     * @param modelId 模型 ID (可选, 为空时统计全部)
     * @return 层级分布列表
     */
    @RequirePermission(resource = "scrm_ltv", action = "read")
    @GetMapping("/tiers/distribution/{modelId}")
    public OperationResponse<List<Map<String, Object>>> getTierDistribution(@PathVariable Long modelId) {
        return OperationResponse.build(ltvPredictionService.getTierDistribution(modelId));
    }

    /**
     * 各层级统计
     *
     * @param modelId 模型 ID (可选, 为空时统计全部)
     * @param tier    价值层级
     * @return 层级统计
     */
    @RequirePermission(resource = "scrm_ltv", action = "read")
    @GetMapping("/tiers/stats/{modelId}/{tier}")
    public OperationResponse<Map<String, Object>> getTierStats(@PathVariable Long modelId,
                                                                 @PathVariable String tier) {
        return OperationResponse.build(ltvPredictionService.getTierStats(modelId, tier));
    }

    // ============================================================
    // 预测
    // ============================================================

    /**
     * 预测客户未来 LTV (基于历史趋势线性回归)
     *
     * @param dto 预测请求 (客户 ID + 预测天数)
     * @return 预测结果
     */
    @RequirePermission(resource = "scrm_ltv", action = "read")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/forecast")
    public OperationResponse<Map<String, Object>> forecast(@Valid @RequestBody ScrmLtvForecastDto dto)
            throws ScrmException {
        return OperationResponse.build(ltvPredictionService.forecast(dto.getCustomerId(), dto.getForecastDays()));
    }

    /**
     * 预测客户未来收入
     *
     * @param customerId 客户 ID
     * @param months     预测月数 (默认 12)
     * @return 预测结果
     */
    @RequirePermission(resource = "scrm_ltv", action = "read")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/forecast/revenue")
    public OperationResponse<Map<String, Object>> forecastRevenue(@RequestParam Long customerId,
                                                                    @RequestParam(defaultValue = "12") int months)
            throws ScrmException {
        return OperationResponse.build(ltvPredictionService.forecastRevenue(customerId, months));
    }

    /**
     * 预测客户流失
     *
     * @param customerId 客户 ID
     * @return 流失预测结果
     */
    @RequirePermission(resource = "scrm_ltv", action = "read")
    @PostMapping("/forecast/churn/{customerId}")
    public OperationResponse<Map<String, Object>> predictChurn(@PathVariable Long customerId)
            throws ScrmException {
        return OperationResponse.build(ltvPredictionService.predictChurn(customerId));
    }

    /**
     * 查询高流失风险客户
     *
     * @param limit 返回条数 (默认 10)
     * @return LTV 结果列表
     */
    @RequirePermission(resource = "scrm_ltv", action = "read")
    @GetMapping("/forecast/churn-risk")
    public OperationResponse<List<ScrmCustomerLtvDto>> getChurnRiskCustomers(
            @RequestParam(defaultValue = "10") int limit) {
        return OperationResponse.build(ltvPredictionService.getChurnRiskCustomers(limit));
    }

    // ============================================================
    // 同期群 (Cohort)
    // ============================================================

    /**
     * 创建同期群分组
     *
     * @param dto 分组参数
     * @return 创建后的分组
     */
    @RequirePermission(resource = "scrm_ltv", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/cohorts")
    public OperationResponse<ScrmLtvCohortDto> createCohort(@Valid @RequestBody ScrmLtvCohortDto dto)
            throws ScrmException {
        return OperationResponse.build(ltvPredictionService.createCohort(dto));
    }

    /**
     * 更新同期群分组
     *
     * @param id  分组 ID
     * @param dto 分组参数
     * @return 更新后的分组
     */
    @RequirePermission(resource = "scrm_ltv", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/cohorts/{id}")
    public OperationResponse<ScrmLtvCohortDto> updateCohort(@PathVariable Long id,
                                                              @RequestBody ScrmLtvCohortDto dto)
            throws ScrmException {
        return OperationResponse.build(ltvPredictionService.updateCohort(id, dto));
    }

    /**
     * 删除同期群分组
     *
     * @param id 分组 ID
     * @return 空响应
     * @throws ScrmException 分组不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_ltv", action = "delete")
    @DeleteMapping("/cohorts/{id}")
    public OperationResponse<Void> deleteCohort(@PathVariable Long id) throws ScrmException {
        ltvPredictionService.deleteCohort(id);
        return OperationResponse.build();
    }

    /**
     * 查询同期群分组详情
     *
     * @param id 分组 ID
     * @return 分组详情
     * @throws ScrmException 分组不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_ltv", action = "read")
    @GetMapping("/cohorts/{id}")
    public OperationResponse<ScrmLtvCohortDto> getCohort(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(ltvPredictionService.getCohort(id));
    }

    /**
     * 分页查询同期群分组, 支持按分组类型与日期范围过滤
     *
     * @param cohortType 分组类型过滤 (可选)
     * @param startDate  分组开始日期下限 (可选)
     * @param endDate    分组开始日期上限 (可选)
     * @param page       页码 (从 0 开始, 默认 0)
     * @param size       每页大小 (默认 20)
     * @return 分组分页结果
     */
    @RequirePermission(resource = "scrm_ltv", action = "read")
    @GetMapping("/cohorts/list")
    public OperationResponse<Page<ScrmLtvCohortDto>> listCohorts(
            @RequestParam(required = false) String cohortType,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(ltvPredictionService.listCohorts(cohortType, startDate, endDate, pageable));
    }

    /**
     * 生成同期群分析
     *
     * @param cohortType 分组类型
     * @param startDate  起始日期 (可选)
     * @param endDate    截止日期 (可选)
     * @return 生成的分组列表
     * @throws ScrmException 分组类型非法 / 计算失败
     */
    @RequirePermission(resource = "scrm_ltv", action = "execute")
    @RateLimit(capacity = 5, refillTokens = 5, refillPeriodSeconds = 60)
    @PostMapping("/cohorts/generate")
    public OperationResponse<List<ScrmLtvCohortDto>> generateCohort(
            @RequestParam String cohortType,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) throws ScrmException {
        return OperationResponse.build(ltvPredictionService.generateCohort(cohortType, startDate, endDate));
    }

    /**
     * 查询同期群趋势
     *
     * @param cohortType 分组类型
     * @return 分组列表
     */
    @RequirePermission(resource = "scrm_ltv", action = "read")
    @GetMapping("/cohorts/trend")
    public OperationResponse<List<ScrmLtvCohortDto>> getCohortTrend(@RequestParam String cohortType) {
        return OperationResponse.build(ltvPredictionService.getCohortTrend(cohortType));
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * LTV 统计概览
     *
     * @param startTime 起始时间 (可选)
     * @param endTime   截止时间 (可选)
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_ltv", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getLtvStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(ltvPredictionService.getLtvStats(startTime, endTime));
    }

    /**
     * 模型效果统计
     *
     * @param modelId 模型 ID
     * @return 模型效果统计
     */
    @RequirePermission(resource = "scrm_ltv", action = "read")
    @GetMapping("/stats/model/{modelId}/performance")
    public OperationResponse<Map<String, Object>> getModelPerformance(@PathVariable Long modelId)
            throws ScrmException {
        return OperationResponse.build(ltvPredictionService.getModelPerformance(modelId));
    }

    /**
     * LTV 趋势 (近 N 天的平均预测 LTV 按日序列)
     *
     * @param days 天数 (默认 30)
     * @return 趋势序列
     */
    @RequirePermission(resource = "scrm_ltv", action = "read")
    @GetMapping("/stats/trend")
    public OperationResponse<List<Map<String, Object>>> getLtvTrend(
            @RequestParam(defaultValue = "30") int days) {
        return OperationResponse.build(ltvPredictionService.getLtvTrend(days));
    }

    /**
     * ROI 统计
     *
     * @param startTime 起始时间 (可选)
     * @param endTime   截止时间 (可选)
     * @return ROI 统计
     */
    @RequirePermission(resource = "scrm_ltv", action = "read")
    @GetMapping("/stats/roi")
    public OperationResponse<Map<String, Object>> getRoiStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(ltvPredictionService.getRoiStats(startTime, endTime));
    }

    /**
     * 同期群对比
     *
     * @param cohortType 分组类型
     * @param startDate  起始日期 (可选)
     * @param endDate    截止日期 (可选)
     * @return 对比结果
     */
    @RequirePermission(resource = "scrm_ltv", action = "read")
    @GetMapping("/stats/cohort-comparison")
    public OperationResponse<Map<String, Object>> getCohortComparison(
            @RequestParam String cohortType,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        return OperationResponse.build(ltvPredictionService.getCohortComparison(cohortType, startDate, endDate));
    }
}
