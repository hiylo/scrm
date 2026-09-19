/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmRfmAnalysisController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmRfmAnalysisDto;
import org.hiylo.scrm.dto.ScrmRfmCalculateDto;
import org.hiylo.scrm.dto.ScrmRfmConfigDto;
import org.hiylo.scrm.dto.ScrmRfmSegmentStrategyDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmRfmAnalysisService;
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

import java.util.List;
import java.util.Map;

/**
 * SCRM RFM 客户价值分析控制器
 * <p>
 * 提供 RFM 配置管理、客户价值计算、分析结果查询与分群策略推荐接口。
 * 基于 RFM 模型 (Recency/Frequency/Monetary) 对客户进行价值评分与自动分群。
 * 权限由 gateway-server 统一鉴权, 此处通过 {@link RequirePermission} 声明资源与动作元数据。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/rfm")
@RequiredArgsConstructor
public class ScrmRfmAnalysisController {

    /** RFM 分析服务 */
    private final ScrmRfmAnalysisService rfmAnalysisService;

    // ============================================================
    // RFM 配置管理
    // ============================================================

    /**
     * 创建 RFM 配置
     *
     * @param dto 配置参数
     * @return 创建后的配置
     */
    @RequirePermission(resource = "scrm_rfm", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/configs")
    public OperationResponse<ScrmRfmConfigDto> createConfig(@Valid @RequestBody ScrmRfmConfigDto dto)
            throws ScrmException {
        return OperationResponse.build(rfmAnalysisService.createConfig(dto));
    }

    /**
     * 更新 RFM 配置
     *
     * @param id  配置 ID
     * @param dto 配置参数
     * @return 更新后的配置
     */
    @RequirePermission(resource = "scrm_rfm", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/configs/{id}")
    public OperationResponse<ScrmRfmConfigDto> updateConfig(@PathVariable Long id,
                                                              @RequestBody ScrmRfmConfigDto dto)
            throws ScrmException {
        return OperationResponse.build(rfmAnalysisService.updateConfig(id, dto));
    }

    /**
     * 删除 RFM 配置
     *
     * @param id 配置 ID
     * @return 空响应
     * @throws ScrmException 配置不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_rfm", action = "delete")
    @DeleteMapping("/configs/{id}")
    public OperationResponse<Void> deleteConfig(@PathVariable Long id) throws ScrmException {
        rfmAnalysisService.deleteConfig(id);
        return OperationResponse.build();
    }

    /**
     * 查询 RFM 配置详情
     *
     * @param id 配置 ID
     * @return 配置详情
     * @throws ScrmException 配置不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_rfm", action = "read")
    @GetMapping("/configs/{id}")
    public OperationResponse<ScrmRfmConfigDto> getConfig(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(rfmAnalysisService.getConfig(id));
    }

    /**
     * 分页查询 RFM 配置, 支持按启用状态过滤
     *
     * @param enabled 启用状态过滤 (可选)
     * @param page    页码 (从 0 开始, 默认 0)
     * @param size    每页大小 (默认 20)
     * @return 配置分页结果
     */
    @RequirePermission(resource = "scrm_rfm", action = "read")
    @GetMapping("/configs/list")
    public OperationResponse<Page<ScrmRfmConfigDto>> listConfigs(
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(rfmAnalysisService.listConfigs(enabled, pageable));
    }

    /**
     * 设置默认 RFM 配置
     *
     * @param id 配置 ID
     * @return 更新后的配置
     * @throws ScrmException 配置不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_rfm", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/configs/{id}/default")
    public OperationResponse<ScrmRfmConfigDto> setDefaultConfig(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(rfmAnalysisService.setDefaultConfig(id));
    }

    /**
     * 启用 RFM 配置
     *
     * @param id 配置 ID
     * @return 更新后的配置
     * @throws ScrmException 配置不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_rfm", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/configs/{id}/enable")
    public OperationResponse<ScrmRfmConfigDto> enableConfig(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(rfmAnalysisService.enableConfig(id));
    }

    /**
     * 禁用 RFM 配置
     *
     * @param id 配置 ID
     * @return 更新后的配置
     * @throws ScrmException 配置不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_rfm", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/configs/{id}/disable")
    public OperationResponse<ScrmRfmConfigDto> disableConfig(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(rfmAnalysisService.disableConfig(id));
    }

    // ============================================================
    // RFM 计算
    // ============================================================

    /**
     * 计算单个客户 RFM
     *
     * @param configId   配置 ID
     * @param customerId 客户 ID
     * @return 分析结果
     */
    @RequirePermission(resource = "scrm_rfm", action = "execute")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/calculate/{configId}")
    public OperationResponse<ScrmRfmAnalysisDto> calculate(@PathVariable Long configId,
                                                            @RequestParam Long customerId)
            throws ScrmException {
        return OperationResponse.build(rfmAnalysisService.calculate(configId, customerId));
    }

    /**
     * 批量计算客户 RFM
     *
     * @param dto 批量计算请求 (配置 ID + 客户 ID 列表)
     * @return 计算结果列表
     * @throws ScrmException 配置不存在 / 参数非法 / 计算失败
     */
    @RequirePermission(resource = "scrm_rfm", action = "execute")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/calculate/batch")
    public OperationResponse<List<ScrmRfmAnalysisDto>> calculateBatch(
            @Valid @RequestBody ScrmRfmCalculateDto dto) throws ScrmException {
        return OperationResponse.build(rfmAnalysisService.calculateBatch(dto));
    }

    /**
     * 计算所有客户 RFM
     *
     * @param configId 配置 ID
     * @return 计算结果列表
     */
    @RequirePermission(resource = "scrm_rfm", action = "execute")
    @RateLimit(capacity = 5, refillTokens = 5, refillPeriodSeconds = 60)
    @PostMapping("/calculate/all/{configId}")
    public OperationResponse<List<ScrmRfmAnalysisDto>> calculateAll(@PathVariable Long configId)
            throws ScrmException {
        return OperationResponse.build(rfmAnalysisService.calculateAll(configId));
    }

    // ============================================================
    // 分析结果查询
    // ============================================================

    /**
     * 查询客户 RFM 分析结果
     *
     * @param customerId 客户 ID
     * @return 分析结果
     */
    @RequirePermission(resource = "scrm_rfm", action = "read")
    @GetMapping("/analysis/{customerId}")
    public OperationResponse<ScrmRfmAnalysisDto> getAnalysis(@PathVariable Long customerId)
            throws ScrmException {
        return OperationResponse.build(rfmAnalysisService.getAnalysis(customerId));
    }

    /**
     * 分页查询 RFM 分析结果, 支持按分群大类、编码、价值分范围过滤
     *
     * @param segmentCategory 分群大类过滤 (可选)
     * @param segmentCode     分群编码过滤 (可选)
     * @param minValueScore   最小价值分过滤 (可选)
     * @param maxValueScore   最大价值分过滤 (可选)
     * @param page            页码 (从 0 开始, 默认 0)
     * @param size            每页大小 (默认 20)
     * @return 分析结果分页
     */
    @RequirePermission(resource = "scrm_rfm", action = "read")
    @GetMapping("/analysis/list")
    public OperationResponse<Page<ScrmRfmAnalysisDto>> listAnalysis(
            @RequestParam(required = false) String segmentCategory,
            @RequestParam(required = false) String segmentCode,
            @RequestParam(required = false) Double minValueScore,
            @RequestParam(required = false) Double maxValueScore,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(rfmAnalysisService.listAnalysis(
                segmentCategory, segmentCode, minValueScore, maxValueScore, pageable));
    }

    /**
     * 分群分布统计: 各分群大类的客户数、占比与平均价值分
     *
     * @return 分群分布列表
     */
    @RequirePermission(resource = "scrm_rfm", action = "read")
    @GetMapping("/analysis/distribution")
    public OperationResponse<List<Map<String, Object>>> getSegmentDistribution() {
        return OperationResponse.build(rfmAnalysisService.getSegmentDistribution());
    }

    // ============================================================
    // 分群策略管理
    // ============================================================

    /**
     * 创建分群策略
     *
     * @param dto 策略参数
     * @return 创建后的策略
     * @throws ScrmException 参数非法 / 配置不存在
     */
    @RequirePermission(resource = "scrm_rfm", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/strategies")
    public OperationResponse<ScrmRfmSegmentStrategyDto> createStrategy(
            @Valid @RequestBody ScrmRfmSegmentStrategyDto dto) throws ScrmException {
        return OperationResponse.build(rfmAnalysisService.createStrategy(dto));
    }

    /**
     * 更新分群策略
     *
     * @param id  策略 ID
     * @param dto 策略参数
     * @return 更新后的策略
     */
    @RequirePermission(resource = "scrm_rfm", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/strategies/{id}")
    public OperationResponse<ScrmRfmSegmentStrategyDto> updateStrategy(@PathVariable Long id,
                                                                       @RequestBody ScrmRfmSegmentStrategyDto dto)
            throws ScrmException {
        return OperationResponse.build(rfmAnalysisService.updateStrategy(id, dto));
    }

    /**
     * 删除分群策略
     *
     * @param id 策略 ID
     * @return 空响应
     * @throws ScrmException 策略不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_rfm", action = "delete")
    @DeleteMapping("/strategies/{id}")
    public OperationResponse<Void> deleteStrategy(@PathVariable Long id) throws ScrmException {
        rfmAnalysisService.deleteStrategy(id);
        return OperationResponse.build();
    }

    /**
     * 查询分群策略详情
     *
     * @param id 策略 ID
     * @return 策略详情
     */
    @RequirePermission(resource = "scrm_rfm", action = "read")
    @GetMapping("/strategies/{id}")
    public OperationResponse<ScrmRfmSegmentStrategyDto> getStrategy(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(rfmAnalysisService.getStrategy(id));
    }

    /**
     * 分页查询分群策略, 支持按分群大类与启用状态过滤
     *
     * @param segmentCategory 分群大类过滤 (可选)
     * @param enabled         启用状态过滤 (可选)
     * @param page            页码 (从 0 开始, 默认 0)
     * @param size            每页大小 (默认 20)
     * @return 策略分页结果
     */
    @RequirePermission(resource = "scrm_rfm", action = "read")
    @GetMapping("/strategies/list")
    public OperationResponse<Page<ScrmRfmSegmentStrategyDto>> listStrategies(
            @RequestParam(required = false) String segmentCategory,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(rfmAnalysisService.listStrategies(segmentCategory, enabled, pageable));
    }

    /**
     * 获取分群推荐策略
     *
     * @param segmentCategory 分群大类
     * @param segmentCode     分群编码 (可选)
     * @return 匹配的策略列表
     */
    @RequirePermission(resource = "scrm_rfm", action = "read")
    @GetMapping("/strategies/for-segment")
    public OperationResponse<List<ScrmRfmSegmentStrategyDto>> getStrategiesForSegment(
            @RequestParam String segmentCategory,
            @RequestParam(required = false) String segmentCode) {
        return OperationResponse.build(rfmAnalysisService.getStrategiesForSegment(segmentCategory, segmentCode));
    }
}
