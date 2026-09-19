/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmHealthScoreController.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmHealthAlertActionDto;
import org.hiylo.scrm.dto.ScrmHealthAlertDto;
import org.hiylo.scrm.dto.ScrmHealthCalculateDto;
import org.hiylo.scrm.dto.ScrmHealthScoreModelDto;
import org.hiylo.scrm.entity.ScrmCustomerHealthScoreEntity;
import org.hiylo.scrm.entity.ScrmHealthAlertEntity;
import org.hiylo.scrm.entity.ScrmHealthScoreModelEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmHealthScoreService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 客户健康度评分控制器。
 * <p>
 * 提供健康度模型管理、健康度计算、健康等级、告警管理、趋势分析与统计接口。
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/health-scores")
@RequiredArgsConstructor
public class ScrmHealthScoreController {

    /** 健康度评分服务 */
    private final ScrmHealthScoreService scrmHealthScoreService;

    // ============================================================
    // 模型管理 /models
    // ============================================================

    /**
     * 创建健康度模型。
     *
     * @param dto 模型参数
     * @return 创建后的模型
     * @throws ScrmException 参数非法 / modelCode 重复
     */
    @RequirePermission(resource = "scrm_health_score", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/models")
    public OperationResponse<ScrmHealthScoreModelEntity> createModel(@Valid @RequestBody ScrmHealthScoreModelDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmHealthScoreService.createModel(dto));
    }

    /**
     * 更新健康度模型。
     *
     * @param id  模型 ID
     * @param dto 模型参数
     * @return 更新后的模型
     * @throws ScrmException 模型不存在 / 参数非法 / modelCode 重复
     */
    @RequirePermission(resource = "scrm_health_score", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/models/{id}")
    public OperationResponse<ScrmHealthScoreModelEntity> updateModel(@PathVariable Long id,
                                                                     @RequestBody ScrmHealthScoreModelDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmHealthScoreService.updateModel(id, dto));
    }

    /**
     * 删除健康度模型。
     *
     * @param id 模型 ID
     * @return 空响应
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_health_score", action = "delete")
    @DeleteMapping("/models/{id}")
    public OperationResponse<Void> deleteModel(@PathVariable Long id) throws ScrmException {
        scrmHealthScoreService.deleteModel(id);
        return OperationResponse.build();
    }

    /**
     * 查询模型详情。
     *
     * @param id 模型 ID
     * @return 模型详情
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_health_score", action = "read")
    @GetMapping("/models/{id}")
    public OperationResponse<ScrmHealthScoreModelEntity> getModel(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmHealthScoreService.getModel(id));
    }

    /**
     * 按模型编码查询模型。
     *
     * @param code 模型编码
     * @return 模型详情
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_health_score", action = "read")
    @GetMapping("/models/code/{code}")
    public OperationResponse<ScrmHealthScoreModelEntity> getModelByCode(@PathVariable String code)
            throws ScrmException {
        return OperationResponse.build(scrmHealthScoreService.getModelByCode(code));
    }

    /**
     * 分页查询模型列表。
     *
     * @param isPublished 发布状态过滤（可空）
     * @param keyword     模型名称关键字模糊匹配（可空）
     * @param page        页码（从 0 开始, 默认 0）
     * @param size        每页大小（默认 20）
     * @return 模型分页结果 (按 createTime DESC)
     */
    @RequirePermission(resource = "scrm_health_score", action = "read")
    @GetMapping("/models/list")
    public OperationResponse<Page<ScrmHealthScoreModelEntity>> listModels(
            @RequestParam(required = false) Boolean isPublished,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmHealthScoreService.listModels(isPublished, keyword, pageable));
    }

    /**
     * 发布模型。
     *
     * @param id 模型 ID
     * @return 更新后的模型
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_health_score", action = "update")
    @PostMapping("/models/{id}/publish")
    public OperationResponse<ScrmHealthScoreModelEntity> publishModel(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmHealthScoreService.publishModel(id));
    }

    /**
     * 取消发布模型。
     *
     * @param id 模型 ID
     * @return 更新后的模型
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_health_score", action = "update")
    @PostMapping("/models/{id}/unpublish")
    public OperationResponse<ScrmHealthScoreModelEntity> unpublishModel(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmHealthScoreService.unpublishModel(id));
    }

    /**
     * 设置为默认模型。
     *
     * @param id 模型 ID
     * @return 更新后的模型
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_health_score", action = "update")
    @PostMapping("/models/{id}/default")
    public OperationResponse<ScrmHealthScoreModelEntity> setDefault(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmHealthScoreService.setDefault(id));
    }

    /**
     * 复制模型。
     *
     * @param id 源模型 ID
     * @return 复制后的新模型
     * @throws ScrmException 源模型不存在 / modelCode 冲突
     */
    @RequirePermission(resource = "scrm_health_score", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/models/{id}/copy")
    public OperationResponse<ScrmHealthScoreModelEntity> copyModel(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmHealthScoreService.copyModel(id));
    }

    /**
     * 验证模型配置。
     *
     * @param id 模型 ID
     * @return 验证结果 Map
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_health_score", action = "read")
    @PostMapping("/models/{id}/validate")
    public OperationResponse<Map<String, Object>> validateModel(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmHealthScoreService.validateModel(id));
    }

    // ============================================================
    // 健康度计算
    // ============================================================

    /**
     * 计算单客户健康度评分。
     *
     * @param dto 计算参数 (customerId + modelId + forceRecalculate)
     * @return 健康度评分记录
     * @throws ScrmException 客户 / 模型不存在
     */
    @RequirePermission(resource = "scrm_health_score", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/calculate")
    public OperationResponse<ScrmCustomerHealthScoreEntity> calculateHealthScore(
            @Valid @RequestBody ScrmHealthCalculateDto dto) throws ScrmException {
        return OperationResponse.build(scrmHealthScoreService.calculateHealthScore(dto));
    }

    /**
     * 批量计算客户健康度评分。
     *
     * @param body 请求体: {modelId, customerIds}
     * @return 计算结果: {total, processed, failed}
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_health_score", action = "update")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/batch-calculate")
    public OperationResponse<Map<String, Integer>> batchCalculate(@RequestBody Map<String, Object> body)
            throws ScrmException {
        Long modelId = parseLong(body.get("modelId"));
        @SuppressWarnings("unchecked")
        List<Object> rawIds = (List<Object>) body.get("customerIds");
        List<Long> customerIds = rawIds == null ? List.of()
                : rawIds.stream().map(this::parseLong).collect(java.util.stream.Collectors.toList());
        return OperationResponse.build(scrmHealthScoreService.batchCalculate(customerIds, modelId));
    }

    /**
     * 计算所有客户健康度评分。
     *
     * @param modelId 模型 ID
     * @return 计算结果: {total, processed, failed}
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_health_score", action = "update")
    @RateLimit(capacity = 5, refillTokens = 5, refillPeriodSeconds = 60)
    @PostMapping("/calculate-all/{modelId}")
    public OperationResponse<Map<String, Integer>> calculateAll(@PathVariable Long modelId) throws ScrmException {
        return OperationResponse.build(scrmHealthScoreService.calculateAll(modelId));
    }

    /**
     * 重新计算指定评分记录。
     *
     * @param id 评分记录 ID
     * @return 健康度评分记录
     * @throws ScrmException 评分记录不存在
     */
    @RequirePermission(resource = "scrm_health_score", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/{id}/recalculate")
    public OperationResponse<ScrmCustomerHealthScoreEntity> recalculate(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmHealthScoreService.recalculate(id));
    }

    /**
     * 查询评分详情。
     *
     * @param id 评分记录 ID
     * @return 评分详情
     * @throws ScrmException 评分记录不存在
     */
    @RequirePermission(resource = "scrm_health_score", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmCustomerHealthScoreEntity> getScore(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmHealthScoreService.getScore(id));
    }

    /**
     * 按客户与模型查询评分。
     *
     * @param customerId 客户 ID
     * @param modelId    模型 ID
     * @return 评分详情 (不存在返回 null)
     */
    @RequirePermission(resource = "scrm_health_score", action = "read")
    @GetMapping("/customer/{customerId}")
    public OperationResponse<ScrmCustomerHealthScoreEntity> getScoreByCustomer(@PathVariable Long customerId,
                                                                                @RequestParam Long modelId) {
        return OperationResponse.build(scrmHealthScoreService.getScoreByCustomer(customerId, modelId));
    }

    /**
     * 分页查询评分列表。
     *
     * @param healthLevel 健康等级过滤（可空）: CRITICAL/AT_RISK/NEUTRAL/HEALTHY/EXCELLENT
     * @param isAtRisk    风险客户过滤（可空）
     * @param riskLevel   风险等级过滤（可空）: NONE/LOW/MEDIUM/HIGH/CRITICAL
     * @param minScore    最低分过滤（可空）
     * @param maxScore    最高分过滤（可空）
     * @param sortBy      排序字段: totalScore / calculatedAt / scorePercent（可空, 默认 totalScore）
     * @param page        页码（从 0 开始, 默认 0）
     * @param size        每页大小（默认 20）
     * @return 评分分页结果
     */
    @RequirePermission(resource = "scrm_health_score", action = "read")
    @GetMapping("/list")
    public OperationResponse<Page<ScrmCustomerHealthScoreEntity>> listScores(
            @RequestParam(required = false) String healthLevel,
            @RequestParam(required = false) Boolean isAtRisk,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) Double minScore,
            @RequestParam(required = false) Double maxScore,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "totalScore"));
        return OperationResponse.build(scrmHealthScoreService.listScores(
                healthLevel, isAtRisk, riskLevel, minScore, maxScore, sortBy, pageable));
    }

    /**
     * 风险客户列表。
     *
     * @param limit 返回数量（默认 10）
     * @return 评分列表 (按 totalScore ASC)
     */
    @RequirePermission(resource = "scrm_health_score", action = "read")
    @GetMapping("/at-risk")
    public OperationResponse<List<ScrmCustomerHealthScoreEntity>> getAtRiskCustomers(
            @RequestParam(defaultValue = "10") int limit) {
        return OperationResponse.build(scrmHealthScoreService.getAtRiskCustomers(limit));
    }

    /**
     * 危急客户列表。
     *
     * @param limit 返回数量（默认 10）
     * @return 评分列表 (按 totalScore ASC)
     */
    @RequirePermission(resource = "scrm_health_score", action = "read")
    @GetMapping("/critical")
    public OperationResponse<List<ScrmCustomerHealthScoreEntity>> getCriticalCustomers(
            @RequestParam(defaultValue = "10") int limit) {
        return OperationResponse.build(scrmHealthScoreService.getCriticalCustomers(limit));
    }

    // ============================================================
    // 健康等级 /levels
    // ============================================================

    /**
     * 确定健康等级 (基于分数与健康阈值)。
     *
     * @param body 请求体: {score, maxScore, thresholds (可空)}
     * @return 健康等级
     */
    @RequirePermission(resource = "scrm_health_score", action = "read")
    @PostMapping("/levels/determine")
    public OperationResponse<String> determineLevel(@RequestBody Map<String, Object> body) {
        double score = parseDouble(body.get("score"));
        double maxScore = parseDouble(body.get("maxScore"));
        if (maxScore <= 0) {
            maxScore = 100;
        }
        Object thresholds = body.get("thresholds");
        String thresholdJson = thresholds == null ? null : thresholds.toString();
        return OperationResponse.build(scrmHealthScoreService.determineLevel(score, maxScore, thresholdJson));
    }

    /**
     * 等级分布统计 (按模型)。
     *
     * @param modelId 模型 ID
     * @return 等级分布 Map
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_health_score", action = "read")
    @GetMapping("/levels/distribution/{modelId}")
    public OperationResponse<Map<String, Object>> getLevelDistribution(@PathVariable Long modelId)
            throws ScrmException {
        return OperationResponse.build(scrmHealthScoreService.getLevelDistribution(modelId));
    }

    /**
     * 分数分布统计 (按模型)。
     *
     * @param modelId 模型 ID
     * @return 分数分布 Map
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_health_score", action = "read")
    @GetMapping("/levels/score-distribution/{modelId}")
    public OperationResponse<Map<String, Object>> getScoreDistribution(@PathVariable Long modelId)
            throws ScrmException {
        return OperationResponse.build(scrmHealthScoreService.getScoreDistribution(modelId));
    }

    /**
     * 风险分布统计 (按模型)。
     *
     * @param modelId 模型 ID
     * @return 风险分布 Map
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_health_score", action = "read")
    @GetMapping("/levels/risk-distribution/{modelId}")
    public OperationResponse<Map<String, Object>> getRiskDistribution(@PathVariable Long modelId)
            throws ScrmException {
        return OperationResponse.build(scrmHealthScoreService.getRiskDistribution(modelId));
    }

    // ============================================================
    // 告警管理 /alerts
    // ============================================================

    /**
     * 创建告警。
     *
     * @param dto 告警参数
     * @return 创建后的告警
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_health_score", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/alerts")
    public OperationResponse<ScrmHealthAlertEntity> createAlert(@Valid @RequestBody ScrmHealthAlertDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmHealthScoreService.createAlert(dto));
    }

    /**
     * 查询告警详情。
     *
     * @param id 告警 ID
     * @return 告警详情
     * @throws ScrmException 告警不存在
     */
    @RequirePermission(resource = "scrm_health_score", action = "read")
    @GetMapping("/alerts/{id}")
    public OperationResponse<ScrmHealthAlertEntity> getAlert(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmHealthScoreService.getAlert(id));
    }

    /**
     * 分页查询告警列表。
     *
     * @param customerId 客户 ID 过滤（可空）
     * @param alertType  告警类型过滤（可空）
     * @param severity   严重度过滤（可空）: INFO/WARNING/URGENT/CRITICAL
     * @param status     状态过滤（可空）: ACTIVE/ACKNOWLEDGED/RESOLVED/DISMISSED
     * @param startTime  触发起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime    触发截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param page       页码（从 0 开始, 默认 0）
     * @param size       每页大小（默认 20）
     * @return 告警分页结果 (按 triggeredAt DESC)
     */
    @RequirePermission(resource = "scrm_health_score", action = "read")
    @GetMapping("/alerts/list")
    public OperationResponse<Page<ScrmHealthAlertEntity>> listAlerts(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String alertType,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "triggeredAt"));
        return OperationResponse.build(scrmHealthScoreService.listAlerts(
                customerId, alertType, severity, status, startTime, endTime, pageable));
    }

    /**
     * 活跃告警列表。
     *
     * @param limit 返回数量（默认 10）
     * @return 告警列表 (按 triggeredAt DESC)
     */
    @RequirePermission(resource = "scrm_health_score", action = "read")
    @GetMapping("/alerts/active")
    public OperationResponse<List<ScrmHealthAlertEntity>> getActiveAlerts(
            @RequestParam(defaultValue = "10") int limit) {
        return OperationResponse.build(scrmHealthScoreService.getActiveAlerts(limit));
    }

    /**
     * 处理告警动作 (确认 / 解决 / 忽略)。
     *
     * @param actionDto 动作参数
     * @return 更新后的告警
     * @throws ScrmException 告警不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_health_score", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/alerts/action")
    public OperationResponse<ScrmHealthAlertEntity> alertAction(@Valid @RequestBody ScrmHealthAlertActionDto actionDto)
            throws ScrmException {
        String action = actionDto.getAction();
        switch (action) {
            case "ACKNOWLEDGE":
                return OperationResponse.build(scrmHealthScoreService.acknowledgeAlert(actionDto));
            case "RESOLVE":
                return OperationResponse.build(scrmHealthScoreService.resolveAlert(actionDto));
            case "DISMISS":
                return OperationResponse.build(scrmHealthScoreService.dismissAlert(actionDto));
            default:
                throw ScrmException.badRequest("不支持的动作: " + action);
        }
    }

    /**
     * 分配告警给负责人。
     *
     * @param body 请求体: {alertId, assigneeId}
     * @return 更新后的告警
     * @throws ScrmException 告警不存在
     */
    @RequirePermission(resource = "scrm_health_score", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/alerts/assign")
    public OperationResponse<ScrmHealthAlertEntity> assignAlert(@RequestBody Map<String, Object> body)
            throws ScrmException {
        Long alertId = parseLong(body.get("alertId"));
        String assigneeId = body.get("assigneeId") == null ? null : body.get("assigneeId").toString();
        return OperationResponse.build(scrmHealthScoreService.assignAlert(alertId, assigneeId));
    }

    /**
     * 批量检查生成告警。
     *
     * @return 检查结果: {checked, generated}
     */
    @RequirePermission(resource = "scrm_health_score", action = "update")
    @RateLimit(capacity = 5, refillTokens = 5, refillPeriodSeconds = 60)
    @PostMapping("/alerts/batch-check")
    public OperationResponse<Map<String, Integer>> batchCheckAlerts() {
        return OperationResponse.build(scrmHealthScoreService.batchCheckAlerts());
    }

    // ============================================================
    // 趋势分析 /trends
    // ============================================================

    /**
     * 评分趋势 (按客户与模型)。
     *
     * @param customerId 客户 ID
     * @param modelId    模型 ID
     * @param days       天数（默认 7）
     * @return 趋势列表 (按日期升序)
     */
    @RequirePermission(resource = "scrm_health_score", action = "read")
    @GetMapping("/trends/customer/{customerId}")
    public OperationResponse<List<Map<String, Object>>> getScoreTrend(
            @PathVariable Long customerId,
            @RequestParam Long modelId,
            @RequestParam(defaultValue = "7") int days) {
        return OperationResponse.build(scrmHealthScoreService.getScoreTrend(customerId, modelId, days));
    }

    /**
     * 趋势分析 (改善 / 下降 / 稳定)。
     *
     * @param customerId 客户 ID
     * @param modelId    模型 ID
     * @return 趋势分析 Map
     */
    @RequirePermission(resource = "scrm_health_score", action = "read")
    @GetMapping("/trends/analysis/{customerId}")
    public OperationResponse<Map<String, Object>> getTrendAnalysis(
            @PathVariable Long customerId,
            @RequestParam Long modelId) {
        return OperationResponse.build(scrmHealthScoreService.getTrendAnalysis(customerId, modelId));
    }

    /**
     * 对比分析 (与客群 / 同期 / 上次对比)。
     *
     * @param customerId  客户 ID
     * @param modelId     模型 ID
     * @param compareWith 对比维度: SEGMENT / PERIOD / PREVIOUS（可空, 默认 SEGMENT）
     * @return 对比分析 Map
     */
    @RequirePermission(resource = "scrm_health_score", action = "read")
    @GetMapping("/trends/comparison/{customerId}")
    public OperationResponse<Map<String, Object>> getComparison(
            @PathVariable Long customerId,
            @RequestParam Long modelId,
            @RequestParam(required = false) String compareWith) {
        return OperationResponse.build(scrmHealthScoreService.getComparison(customerId, modelId, compareWith));
    }

    // ============================================================
    // 统计 /stats
    // ============================================================

    /**
     * 健康度统计概览。
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_health_score", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getHealthStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmHealthScoreService.getHealthStats(startTime, endTime));
    }

    /**
     * 告警统计。
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 告警统计 Map
     */
    @RequirePermission(resource = "scrm_health_score", action = "read")
    @GetMapping("/stats/alerts")
    public OperationResponse<Map<String, Object>> getAlertStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmHealthScoreService.getAlertStats(startTime, endTime));
    }

    /**
     * 指标统计。
     *
     * @param modelId    模型 ID
     * @param metricCode 指标编码
     * @return 指标统计 Map
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_health_score", action = "read")
    @GetMapping("/stats/metrics")
    public OperationResponse<Map<String, Object>> getMetricStats(
            @RequestParam Long modelId,
            @RequestParam String metricCode) throws ScrmException {
        return OperationResponse.build(scrmHealthScoreService.getMetricStats(modelId, metricCode));
    }

    /**
     * 健康度趋势。
     *
     * @param days 天数（默认 7）
     * @return 趋势列表 (按日期升序)
     */
    @RequirePermission(resource = "scrm_health_score", action = "read")
    @GetMapping("/stats/health-trend")
    public OperationResponse<List<Map<String, Object>>> getHealthTrend(
            @RequestParam(defaultValue = "7") int days) {
        return OperationResponse.build(scrmHealthScoreService.getHealthTrend(days));
    }

    /**
     * 风险分析。
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 风险分析 Map
     */
    @RequirePermission(resource = "scrm_health_score", action = "read")
    @GetMapping("/stats/risk")
    public OperationResponse<Map<String, Object>> getRiskAnalysis(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmHealthScoreService.getRiskAnalysis(startTime, endTime));
    }

    // ============================================================
    // 内部辅助方法
    // ============================================================

    /**
     * 将请求参数转换为 Long。
     *
     * @param value 参数值
     * @return Long 值, null 或不可转换返回 null
     */
    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 将请求参数转换为 double。
     *
     * @param value 参数值
     * @return double 值, null 或不可转换返回 0
     */
    private double parseDouble(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
