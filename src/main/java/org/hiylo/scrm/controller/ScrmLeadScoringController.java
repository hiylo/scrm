/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLeadScoringController.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmLeadAssignDto;
import org.hiylo.scrm.dto.ScrmLeadDimensionDto;
import org.hiylo.scrm.dto.ScrmLeadScoreResultDto;
import org.hiylo.scrm.dto.ScrmLeadScoringModelDto;
import org.hiylo.scrm.entity.ScrmLeadDimensionEntity;
import org.hiylo.scrm.entity.ScrmLeadScoreEntity;
import org.hiylo.scrm.entity.ScrmLeadScoringModelEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmLeadScoringService;
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
 * SCRM 销售线索评分控制器。
 * <p>
 * 提供评分模型管理、评分维度管理、线索评分计算、线索分级、转化预测、线索分配与统计接口。
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/lead-scoring")
@RequiredArgsConstructor
public class ScrmLeadScoringController {

    /** 线索评分服务 */
    private final ScrmLeadScoringService scrmLeadScoringService;

    // ============================================================
    // 模型管理 /models
    // ============================================================

    /**
     * 创建评分模型。
     *
     * @param dto 模型参数
     * @return 创建后的模型
     * @throws ScrmException 参数非法 / modelCode 重复
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/models")
    public OperationResponse<ScrmLeadScoringModelEntity> createModel(@Valid @RequestBody ScrmLeadScoringModelDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmLeadScoringService.createModel(dto));
    }

    /**
     * 更新评分模型。
     *
     * @param id  模型 ID
     * @param dto 模型参数
     * @return 更新后的模型
     * @throws ScrmException 模型不存在 / 参数非法 / modelCode 重复
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/models/{id}")
    public OperationResponse<ScrmLeadScoringModelEntity> updateModel(@PathVariable Long id,
                                                                      @RequestBody ScrmLeadScoringModelDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmLeadScoringService.updateModel(id, dto));
    }

    /**
     * 删除评分模型。
     *
     * @param id 模型 ID
     * @return 空响应
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "delete")
    @DeleteMapping("/models/{id}")
    public OperationResponse<Void> deleteModel(@PathVariable Long id) throws ScrmException {
        scrmLeadScoringService.deleteModel(id);
        return OperationResponse.build();
    }

    /**
     * 查询模型详情。
     *
     * @param id 模型 ID
     * @return 模型详情
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "read")
    @GetMapping("/models/{id}")
    public OperationResponse<ScrmLeadScoringModelEntity> getModel(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmLeadScoringService.getModel(id));
    }

    /**
     * 按模型编码查询模型。
     *
     * @param code 模型编码
     * @return 模型详情
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "read")
    @GetMapping("/models/code/{code}")
    public OperationResponse<ScrmLeadScoringModelEntity> getModelByCode(@PathVariable String code)
            throws ScrmException {
        return OperationResponse.build(scrmLeadScoringService.getModelByCode(code));
    }

    /**
     * 分页查询模型列表。
     *
     * @param modelType   模型类型过滤（可空）: RULE_BASED / ML_BASED / HYBRID
     * @param isPublished 发布状态过滤（可空）
     * @param keyword     模型名称关键字模糊匹配（可空）
     * @param page        页码（从 0 开始, 默认 0）
     * @param size        每页大小（默认 20）
     * @return 模型分页结果 (按 createTime DESC)
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "read")
    @GetMapping("/models/list")
    public OperationResponse<Page<ScrmLeadScoringModelEntity>> listModels(
            @RequestParam(required = false) String modelType,
            @RequestParam(required = false) Boolean isPublished,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(
                scrmLeadScoringService.listModels(modelType, isPublished, keyword, pageable));
    }

    /**
     * 发布模型。
     *
     * @param id 模型 ID
     * @return 更新后的模型
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "update")
    @PostMapping("/models/{id}/publish")
    public OperationResponse<ScrmLeadScoringModelEntity> publishModel(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmLeadScoringService.publishModel(id));
    }

    /**
     * 取消发布模型。
     *
     * @param id 模型 ID
     * @return 更新后的模型
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "update")
    @PostMapping("/models/{id}/unpublish")
    public OperationResponse<ScrmLeadScoringModelEntity> unpublishModel(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmLeadScoringService.unpublishModel(id));
    }

    /**
     * 设置为默认模型。
     *
     * @param id 模型 ID
     * @return 更新后的模型
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "update")
    @PostMapping("/models/{id}/default")
    public OperationResponse<ScrmLeadScoringModelEntity> setDefaultModel(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmLeadScoringService.setDefaultModel(id));
    }

    /**
     * 复制模型。
     *
     * @param id 源模型 ID
     * @return 复制后的新模型
     * @throws ScrmException 源模型不存在 / modelCode 冲突
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/models/{id}/copy")
    public OperationResponse<ScrmLeadScoringModelEntity> copyModel(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmLeadScoringService.copyModel(id));
    }

    // ============================================================
    // 维度管理 /dimensions
    // ============================================================

    /**
     * 创建评分维度。
     *
     * @param dto 维度参数
     * @return 创建后的维度
     * @throws ScrmException 参数非法 / dimensionCode 重复
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/dimensions")
    public OperationResponse<ScrmLeadDimensionEntity> createDimension(@Valid @RequestBody ScrmLeadDimensionDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmLeadScoringService.createDimension(dto));
    }

    /**
     * 更新评分维度。
     *
     * @param id  维度 ID
     * @param dto 维度参数
     * @return 更新后的维度
     * @throws ScrmException 维度不存在 / 参数非法 / dimensionCode 重复
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/dimensions/{id}")
    public OperationResponse<ScrmLeadDimensionEntity> updateDimension(@PathVariable Long id,
                                                                       @RequestBody ScrmLeadDimensionDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmLeadScoringService.updateDimension(id, dto));
    }

    /**
     * 删除评分维度。
     *
     * @param id 维度 ID
     * @return 空响应
     * @throws ScrmException 维度不存在
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "delete")
    @DeleteMapping("/dimensions/{id}")
    public OperationResponse<Void> deleteDimension(@PathVariable Long id) throws ScrmException {
        scrmLeadScoringService.deleteDimension(id);
        return OperationResponse.build();
    }

    /**
     * 查询维度详情。
     *
     * @param id 维度 ID
     * @return 维度详情
     * @throws ScrmException 维度不存在
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "read")
    @GetMapping("/dimensions/{id}")
    public OperationResponse<ScrmLeadDimensionEntity> getDimension(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmLeadScoringService.getDimension(id));
    }

    /**
     * 按维度编码查询维度。
     *
     * @param code 维度编码
     * @return 维度详情
     * @throws ScrmException 维度不存在
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "read")
    @GetMapping("/dimensions/code/{code}")
    public OperationResponse<ScrmLeadDimensionEntity> getDimensionByCode(@PathVariable String code)
            throws ScrmException {
        return OperationResponse.build(scrmLeadScoringService.getDimensionByCode(code));
    }

    /**
     * 分页查询维度列表。
     *
     * @param dimensionCategory 维度类别过滤（可空）: *
       * DEMOGRAPHIC/BEHAVIORAL/ENGAGEMENT/FIRMOGRAPHIC/TECHNOGRAPHIC/NEED_BASED/TIMING * @param enabled 启用状态过滤（可空）
     * @param keyword           维度名称关键字模糊匹配（可空）
     * @param page              页码（从 0 开始, 默认 0）
     * @param size              每页大小（默认 20）
     * @return 维度分页结果 (按 createTime DESC)
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "read")
    @GetMapping("/dimensions/list")
    public OperationResponse<Page<ScrmLeadDimensionEntity>> listDimensions(
            @RequestParam(required = false) String dimensionCategory,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(
                scrmLeadScoringService.listDimensions(dimensionCategory, enabled, keyword, pageable));
    }

    /**
     * 启用维度。
     *
     * @param id 维度 ID
     * @return 更新后的维度
     * @throws ScrmException 维度不存在
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "update")
    @PostMapping("/dimensions/{id}/enable")
    public OperationResponse<ScrmLeadDimensionEntity> enableDimension(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmLeadScoringService.enableDimension(id));
    }

    /**
     * 禁用维度。
     *
     * @param id 维度 ID
     * @return 更新后的维度
     * @throws ScrmException 维度不存在
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "update")
    @PostMapping("/dimensions/{id}/disable")
    public OperationResponse<ScrmLeadDimensionEntity> disableDimension(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmLeadScoringService.disableDimension(id));
    }

    // ============================================================
    // 评分计算 /scores
    // ============================================================

    /**
     * 计算单客户线索评分。
     *
     * @param body 请求体: {customerId, modelId}
     * @return 评分计算结果
     * @throws ScrmException 客户 / 模型不存在
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/scores/calculate")
    public OperationResponse<ScrmLeadScoreResultDto> calculateScore(@RequestBody Map<String, Object> body)
            throws ScrmException {
        Long customerId = parseLong(body.get("customerId"));
        Long modelId = parseLong(body.get("modelId"));
        return OperationResponse.build(scrmLeadScoringService.calculateScore(customerId, modelId));
    }

    /**
     * 批量计算客户线索评分。
     *
     * @param body 请求体: {modelId, customerIds}
     * @return 计算结果: {total, processed, failed}
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "update")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/scores/batch-calculate")
    public OperationResponse<Map<String, Integer>> batchCalculateScores(@RequestBody Map<String, Object> body)
            throws ScrmException {
        Long modelId = parseLong(body.get("modelId"));
        @SuppressWarnings("unchecked")
        List<Long> customerIds = (List<Long>) body.get("customerIds");
        return OperationResponse.build(scrmLeadScoringService.batchCalculateScores(modelId, customerIds));
    }

    /**
     * 计算所有客户评分。
     *
     * @param modelId 模型 ID
     * @return 计算结果: {total, processed, failed}
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "update")
    @RateLimit(capacity = 5, refillTokens = 5, refillPeriodSeconds = 60)
    @PostMapping("/scores/calculate-all/{modelId}")
    public OperationResponse<Map<String, Integer>> calculateAllScores(@PathVariable Long modelId)
            throws ScrmException {
        return OperationResponse.build(scrmLeadScoringService.calculateAllScores(modelId));
    }

    /**
     * 重新计算指定评分记录。
     *
     * @param id 评分记录 ID
     * @return 评分计算结果
     * @throws ScrmException 评分记录不存在
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/scores/{id}/recalculate")
    public OperationResponse<ScrmLeadScoreResultDto> recalculateScore(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmLeadScoringService.recalculateScore(id));
    }

    /**
     * 查询评分详情。
     *
     * @param id 评分记录 ID
     * @return 评分详情
     * @throws ScrmException 评分记录不存在
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "read")
    @GetMapping("/scores/{id}")
    public OperationResponse<ScrmLeadScoreEntity> getScore(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmLeadScoringService.getScore(id));
    }

    /**
     * 按客户与模型查询评分。
     *
     * @param customerId 客户 ID
     * @param modelId    模型 ID
     * @return 评分详情 (不存在返回 null)
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "read")
    @GetMapping("/scores/customer/{customerId}")
    public OperationResponse<ScrmLeadScoreEntity> getScoreByCustomer(@PathVariable Long customerId,
                                                                      @RequestParam Long modelId) {
        return OperationResponse.build(scrmLeadScoringService.getScoreByCustomer(customerId, modelId));
    }

    /**
     * 分页查询评分列表。
     *
     * @param grade       等级过滤（可空）
     * @param isHotLead   热线索过滤（可空）
     * @param isQualified 合格线索过滤（可空）
     * @param isConverted 已转化过滤（可空）
     * @param modelId     模型 ID 过滤（可空）
     * @param minScore    最低分过滤（可空）
     * @param maxScore    最高分过滤（可空）
     * @param sortBy      排序字段: totalScore / conversionProbability / lastCalculatedAt（可空, 默认 totalScore）
     * @param page        页码（从 0 开始, 默认 0）
     * @param size        每页大小（默认 20）
     * @return 评分分页结果
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "read")
    @GetMapping("/scores/list")
    public OperationResponse<Page<ScrmLeadScoreEntity>> listScores(
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) Boolean isHotLead,
            @RequestParam(required = false) Boolean isQualified,
            @RequestParam(required = false) Boolean isConverted,
            @RequestParam(required = false) Long modelId,
            @RequestParam(required = false) Double minScore,
            @RequestParam(required = false) Double maxScore,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "totalScore"));
        return OperationResponse.build(scrmLeadScoringService.listScores(
                grade, isHotLead, isQualified, isConverted, modelId, minScore, maxScore, sortBy, pageable));
    }

    /**
     * 热线索列表。
     *
     * @param modelId 模型 ID（可空, 缺省取全部）
     * @param limit   返回数量（默认 10）
     * @return 评分列表 (按 totalScore DESC)
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "read")
    @GetMapping("/scores/hot")
    public OperationResponse<List<ScrmLeadScoreEntity>> getHotLeads(
            @RequestParam(required = false) Long modelId,
            @RequestParam(defaultValue = "10") int limit) {
        return OperationResponse.build(scrmLeadScoringService.getHotLeads(modelId, limit));
    }

    /**
     * 合格线索列表。
     *
     * @param modelId 模型 ID（可空, 缺省取全部）
     * @param limit   返回数量（默认 10）
     * @return 评分列表 (按 totalScore DESC)
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "read")
    @GetMapping("/scores/qualified")
    public OperationResponse<List<ScrmLeadScoreEntity>> getQualifiedLeads(
            @RequestParam(required = false) Long modelId,
            @RequestParam(defaultValue = "10") int limit) {
        return OperationResponse.build(scrmLeadScoringService.getQualifiedLeads(modelId, limit));
    }

    // ============================================================
    // 等级 /grades
    // ============================================================

    /**
     * 确定等级 (基于分数与等级阈值)。
     *
     * @param body 请求体: {score, maxScore, thresholds (可空)}
     * @return 等级编码
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "read")
    @PostMapping("/grades/determine")
    public OperationResponse<String> determineGrade(@RequestBody Map<String, Object> body) {
        double score = parseDouble(body.get("score"));
        double maxScore = parseDouble(body.get("maxScore"));
        if (maxScore <= 0) {
            maxScore = 100;
        }
        Object thresholds = body.get("thresholds");
        String thresholdJson = thresholds == null ? null : thresholds.toString();
        return OperationResponse.build(scrmLeadScoringService.determineGrade(score, maxScore, thresholdJson));
    }

    /**
     * 等级分布统计 (按模型)。
     *
     * @param modelId 模型 ID
     * @return 等级分布 Map
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "read")
    @GetMapping("/grades/distribution/{modelId}")
    public OperationResponse<Map<String, Object>> getGradeDistribution(@PathVariable Long modelId)
            throws ScrmException {
        return OperationResponse.build(scrmLeadScoringService.getGradeDistribution(modelId));
    }

    /**
     * 分数分布统计 (按模型)。
     *
     * @param modelId 模型 ID
     * @return 分数分布 Map
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "read")
    @GetMapping("/grades/score-distribution/{modelId}")
    public OperationResponse<Map<String, Object>> getScoreDistribution(@PathVariable Long modelId)
            throws ScrmException {
        return OperationResponse.build(scrmLeadScoringService.getScoreDistribution(modelId));
    }

    // ============================================================
    // 转化预测 /conversion
    // ============================================================

    /**
     * 预测客户转化概率。
     *
     * @param body 请求体: {customerId, modelId}
     * @return 转化概率 (0-1)
     * @throws ScrmException 客户 / 模型不存在
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "read")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/conversion/predict")
    public OperationResponse<Double> predictConversion(@RequestBody Map<String, Object> body)
            throws ScrmException {
        Long customerId = parseLong(body.get("customerId"));
        Long modelId = parseLong(body.get("modelId"));
        return OperationResponse.build(scrmLeadScoringService.predictConversion(customerId, modelId));
    }

    /**
     * 转化统计。
     *
     * @param modelId   模型 ID
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 转化统计 Map
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "read")
    @GetMapping("/conversion/stats")
    public OperationResponse<Map<String, Object>> getConversionStats(
            @RequestParam Long modelId,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) throws ScrmException {
        return OperationResponse.build(
                scrmLeadScoringService.getConversionStats(modelId, startTime, endTime));
    }

    /**
     * 转化漏斗 (按等级)。
     *
     * @param modelId 模型 ID
     * @return 漏斗列表
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "read")
    @GetMapping("/conversion/funnel/{modelId}")
    public OperationResponse<List<Map<String, Object>>> getConversionFunnel(@PathVariable Long modelId)
            throws ScrmException {
        return OperationResponse.build(scrmLeadScoringService.getConversionFunnel(modelId));
    }

    // ============================================================
    // 分配 /assignment
    // ============================================================

    /**
     * 分配线索给负责人。
     *
     * @param assignDto 分配参数
     * @return 更新后的评分记录
     * @throws ScrmException 评分记录不存在
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/assignment/assign")
    public OperationResponse<ScrmLeadScoreEntity> assignLead(@Valid @RequestBody ScrmLeadAssignDto assignDto)
            throws ScrmException {
        return OperationResponse.build(scrmLeadScoringService.assignLead(assignDto));
    }

    /**
     * 批量分配线索。
     *
     * @param body 请求体: {scoreIds, assigneeId}
     * @return 分配结果: {total, assigned, failed}
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/assignment/batch-assign")
    public OperationResponse<Map<String, Integer>> batchAssignLeads(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> rawIds = (List<Object>) body.get("scoreIds");
        List<Long> scoreIds = rawIds == null ? List.of()
                : rawIds.stream().map(this::parseLong).collect(java.util.stream.Collectors.toList());
        String assigneeId = body.get("assigneeId") == null ? null : body.get("assigneeId").toString();
        return OperationResponse.build(scrmLeadScoringService.batchAssignLeads(scoreIds, assigneeId));
    }

    /**
     * 获取分配给负责人的线索。
     *
     * @param assigneeId 负责人用户标识
     * @param page       页码（从 0 开始, 默认 0）
     * @param size       每页大小（默认 20）
     * @return 评分分页结果 (按 assignedAt DESC)
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "read")
    @GetMapping("/assignment/assigned/{assigneeId}")
    public OperationResponse<Page<ScrmLeadScoreEntity>> getAssignedLeads(
            @PathVariable String assigneeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "assignedAt"));
        return OperationResponse.build(scrmLeadScoringService.getAssignedLeads(assigneeId, pageable));
    }

    /**
     * 标记线索为已转化。
     *
     * @param id             评分记录 ID
     * @param conversionValue 转化价值 (可空)
     * @return 更新后的评分记录
     * @throws ScrmException 评分记录不存在 / 已转化
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "update")
    @PostMapping("/assignment/{id}/convert")
    public OperationResponse<ScrmLeadScoreEntity> markConverted(
            @PathVariable Long id,
            @RequestParam(required = false) Double conversionValue) throws ScrmException {
        return OperationResponse.build(scrmLeadScoringService.markConverted(id, conversionValue));
    }

    // ============================================================
    // 统计 /stats
    // ============================================================

    /**
     * 线索统计概览。
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getLeadStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmLeadScoringService.getLeadStats(startTime, endTime));
    }

    /**
     * 模型效果统计。
     *
     * @param modelId 模型 ID
     * @return 模型效果 Map
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "read")
    @GetMapping("/stats/model/{modelId}/performance")
    public OperationResponse<Map<String, Object>> getModelPerformance(@PathVariable Long modelId)
            throws ScrmException {
        return OperationResponse.build(scrmLeadScoringService.getModelPerformance(modelId));
    }

    /**
     * 评分趋势 (按客户与模型)。
     *
     * @param customerId 客户 ID
     * @param modelId    模型 ID
     * @param days       天数（默认 7）
     * @return 趋势列表 (按日期升序)
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "read")
    @GetMapping("/stats/trend")
    public OperationResponse<List<Map<String, Object>>> getScoreTrend(
            @RequestParam Long customerId,
            @RequestParam Long modelId,
            @RequestParam(defaultValue = "7") int days) {
        return OperationResponse.build(
                scrmLeadScoringService.getScoreTrend(customerId, modelId, days));
    }

    /**
     * 排行: 顶级线索。
     *
     * @param limit  返回数量（默认 10）
     * @param sortBy 排序字段: totalScore / conversionProbability / predictedValue（可空, 默认 totalScore）
     * @return 评分列表
     */
    @RequirePermission(resource = "scrm_lead_scoring", action = "read")
    @GetMapping("/stats/top")
    public OperationResponse<List<ScrmLeadScoreEntity>> getTopLeads(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String sortBy) {
        return OperationResponse.build(scrmLeadScoringService.getTopLeads(limit, sortBy));
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
