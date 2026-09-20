/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAttributionController.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmAttributionCalculateDto;
import org.hiylo.scrm.dto.ScrmAttributionConversionDto;
import org.hiylo.scrm.dto.ScrmAttributionModelDto;
import org.hiylo.scrm.dto.ScrmAttributionReportDto;
import org.hiylo.scrm.dto.ScrmAttributionTouchpointDto;
import org.hiylo.scrm.entity.ScrmAttributionConversionEntity;
import org.hiylo.scrm.entity.ScrmAttributionModelEntity;
import org.hiylo.scrm.entity.ScrmAttributionTouchpointEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmAttributionService;
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
 * SCRM 营销效果归因控制器。
 * <p>
 * 提供归因模型管理、触点管理、转化管理、归因计算与归因报告/统计接口。
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/attribution")
@RequiredArgsConstructor
public class ScrmAttributionController {

    /** 归因服务 */
    private final ScrmAttributionService scrmAttributionService;

    // ============================================================
    // 模型管理 /models
    // ============================================================

    /**
     * 创建归因模型。
     *
     * @param dto 模型参数
     * @return 创建后的模型
     * @throws ScrmException 参数非法 / modelCode 重复
     */
    @RequirePermission(resource = "scrm_attribution", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/models")
    public OperationResponse<ScrmAttributionModelEntity> createModel(@Valid @RequestBody ScrmAttributionModelDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmAttributionService.createModel(dto));
    }

    /**
     * 更新归因模型。
     *
     * @param id  模型 ID
     * @param dto 模型参数
     * @return 更新后的模型
     * @throws ScrmException 模型不存在 / 参数非法 / modelCode 重复
     */
    @RequirePermission(resource = "scrm_attribution", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/models/{id}")
    public OperationResponse<ScrmAttributionModelEntity> updateModel(@PathVariable Long id,
                                                                     @RequestBody ScrmAttributionModelDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmAttributionService.updateModel(id, dto));
    }

    /**
     * 删除归因模型。
     *
     * @param id 模型 ID
     * @return 空响应
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_attribution", action = "delete")
    @DeleteMapping("/models/{id}")
    public OperationResponse<Void> deleteModel(@PathVariable Long id) throws ScrmException {
        scrmAttributionService.deleteModel(id);
        return OperationResponse.build();
    }

    /**
     * 查询模型详情。
     *
     * @param id 模型 ID
     * @return 模型详情
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_attribution", action = "read")
    @GetMapping("/models/{id}")
    public OperationResponse<ScrmAttributionModelEntity> getModel(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmAttributionService.getModel(id));
    }

    /**
     * 按模型编码查询模型。
     *
     * @param code 模型编码
     * @return 模型详情
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_attribution", action = "read")
    @GetMapping("/models/code/{code}")
    public OperationResponse<ScrmAttributionModelEntity> getModelByCode(@PathVariable String code)
            throws ScrmException {
        return OperationResponse.build(scrmAttributionService.getModelByCode(code));
    }

    /**
     * 分页查询模型列表。
     *
     * @param modelType   模型类型过滤（可空）
     * @param isPublished 发布状态过滤（可空）
     * @param keyword     模型名称关键字模糊匹配（可空）
     * @param page        页码（从 0 开始, 默认 0）
     * @param size        每页大小（默认 20）
     * @return 模型分页结果 (按 createTime DESC)
     */
    @RequirePermission(resource = "scrm_attribution", action = "read")
    @GetMapping("/models/list")
    public OperationResponse<Page<ScrmAttributionModelEntity>> listModels(
            @RequestParam(required = false) String modelType,
            @RequestParam(required = false) Boolean isPublished,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmAttributionService.listModels(
                modelType, isPublished, keyword, pageable));
    }

    /**
     * 发布模型。
     *
     * @param id 模型 ID
     * @return 更新后的模型
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_attribution", action = "update")
    @PostMapping("/models/{id}/publish")
    public OperationResponse<ScrmAttributionModelEntity> publishModel(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmAttributionService.publishModel(id));
    }

    /**
     * 取消发布模型。
     *
     * @param id 模型 ID
     * @return 更新后的模型
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_attribution", action = "update")
    @PostMapping("/models/{id}/unpublish")
    public OperationResponse<ScrmAttributionModelEntity> unpublishModel(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmAttributionService.unpublishModel(id));
    }

    /**
     * 设置为默认模型。
     *
     * @param id 模型 ID
     * @return 更新后的模型
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_attribution", action = "update")
    @PostMapping("/models/{id}/default")
    public OperationResponse<ScrmAttributionModelEntity> setDefaultModel(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmAttributionService.setDefault(id));
    }

    // ============================================================
    // 触点管理 /touchpoints
    // ============================================================

    /**
     * 记录触点。
     *
     * @param dto 触点参数
     * @return 创建后的触点
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_attribution", action = "create")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/touchpoints/record")
    public OperationResponse<ScrmAttributionTouchpointEntity> recordTouchpoint(
            @Valid @RequestBody ScrmAttributionTouchpointDto dto) throws ScrmException {
        return OperationResponse.build(scrmAttributionService.recordTouchpoint(dto));
    }

    /**
     * 批量记录触点。
     *
     * @param dtos 触点参数列表
     * @return 创建后的触点列表
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_attribution", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/touchpoints/batch-record")
    public OperationResponse<List<ScrmAttributionTouchpointEntity>> batchRecordTouchpoints(
            @RequestBody List<ScrmAttributionTouchpointDto> dtos) throws ScrmException {
        return OperationResponse.build(scrmAttributionService.batchRecordTouchpoints(dtos));
    }

    /**
     * 查询触点详情。
     *
     * @param id 触点 ID
     * @return 触点详情
     * @throws ScrmException 触点不存在
     */
    @RequirePermission(resource = "scrm_attribution", action = "read")
    @GetMapping("/touchpoints/{id}")
    public OperationResponse<ScrmAttributionTouchpointEntity> getTouchpoint(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmAttributionService.getTouchpoint(id));
    }

    /**
     * 分页查询触点列表。
     *
     * @param customerId     客户 ID 过滤（可空）
     * @param touchpointType 触点类型过滤（可空）
     * @param channel        渠道过滤（可空）
     * @param campaignId     营销活动 ID 过滤（可空）
     * @param startTime      触点时间起点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime        触点时间终点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param page           页码（从 0 开始, 默认 0）
     * @param size           每页大小（默认 20）
     * @return 触点分页结果 (按 touchpointTime DESC)
     */
    @RequirePermission(resource = "scrm_attribution", action = "read")
    @GetMapping("/touchpoints/list")
    public OperationResponse<Page<ScrmAttributionTouchpointEntity>> listTouchpoints(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String touchpointType,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) Long campaignId,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "touchpointTime"));
        return OperationResponse.build(scrmAttributionService.listTouchpoints(
                customerId, touchpointType, channel, campaignId, startTime, endTime, pageable));
    }

    /**
     * 客户触点链。
     *
     * @param customerId 客户 ID
     * @return 触点列表 (touchpointTime ASC)
     */
    @RequirePermission(resource = "scrm_attribution", action = "read")
    @GetMapping("/touchpoints/customer/{customerId}")
    public OperationResponse<List<ScrmAttributionTouchpointEntity>> getCustomerTouchpoints(
            @PathVariable Long customerId) {
        return OperationResponse.build(scrmAttributionService.getCustomerTouchpoints(customerId));
    }

    /**
     * 回溯窗口内触点。
     *
     * @param customerId     客户 ID
     * @param conversionTime 转化时间 (回溯窗口终点, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param lookbackDays   回溯天数（默认 30）
     * @return 触点列表 (touchpointTime ASC)
     */
    @RequirePermission(resource = "scrm_attribution", action = "read")
    @GetMapping("/touchpoints/customer/{customerId}/window")
    public OperationResponse<List<ScrmAttributionTouchpointEntity>> getTouchpointsInWindow(
            @PathVariable Long customerId,
            @RequestParam LocalDateTime conversionTime,
            @RequestParam(defaultValue = "30") int lookbackDays) {
        return OperationResponse.build(scrmAttributionService.getTouchpointsInWindow(
                customerId, conversionTime, lookbackDays));
    }

    // ============================================================
    // 转化管理 /conversions
    // ============================================================

    /**
     * 记录转化。
     *
     * @param dto 转化参数
     * @return 创建后的转化
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_attribution", action = "create")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/conversions/record")
    public OperationResponse<ScrmAttributionConversionEntity> recordConversion(
            @Valid @RequestBody ScrmAttributionConversionDto dto) throws ScrmException {
        return OperationResponse.build(scrmAttributionService.recordConversion(dto));
    }

    /**
     * 查询转化详情。
     *
     * @param id 转化 ID
     * @return 转化详情
     * @throws ScrmException 转化不存在
     */
    @RequirePermission(resource = "scrm_attribution", action = "read")
    @GetMapping("/conversions/{id}")
    public OperationResponse<ScrmAttributionConversionEntity> getConversion(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmAttributionService.getConversion(id));
    }

    /**
     * 分页查询转化列表。
     *
     * @param customerId     客户 ID 过滤（可空）
     * @param conversionType 转化类型过滤（可空）
     * @param modelId        归因模型 ID 过滤（可空）
     * @param startTime      转化时间起点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime        转化时间终点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param page           页码（从 0 开始, 默认 0）
     * @param size           每页大小（默认 20）
     * @return 转化分页结果 (按 conversionTime DESC)
     */
    @RequirePermission(resource = "scrm_attribution", action = "read")
    @GetMapping("/conversions/list")
    public OperationResponse<Page<ScrmAttributionConversionEntity>> listConversions(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String conversionType,
            @RequestParam(required = false) Long modelId,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "conversionTime"));
        return OperationResponse.build(scrmAttributionService.listConversions(
                customerId, conversionType, modelId, startTime, endTime, pageable));
    }

    /**
     * 按订单号查询转化。
     *
     * @param orderId 订单号
     * @return 转化详情
     * @throws ScrmException 转化不存在
     */
    @RequirePermission(resource = "scrm_attribution", action = "read")
    @GetMapping("/conversions/order/{orderId}")
    public OperationResponse<ScrmAttributionConversionEntity> getConversionByOrder(@PathVariable String orderId)
            throws ScrmException {
        return OperationResponse.build(scrmAttributionService.getConversionByOrder(orderId));
    }

    // ============================================================
    // 归因计算
    // ============================================================

    /**
     * 计算归因。
     *
     * @param dto 归因计算参数
     * @return 计算结果: {total, processed, failed}
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_attribution", action = "update")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/calculate")
    public OperationResponse<Map<String, Integer>> calculateAttribution(
            @Valid @RequestBody ScrmAttributionCalculateDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmAttributionService.calculateAttribution(dto));
    }

    /**
     * 批量计算归因。
     *
     * @param modelId   归因模型 ID
     * @param startDate 转化时间起点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endDate   转化时间终点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 计算结果: {total, processed, failed}
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_attribution", action = "update")
    @RateLimit(capacity = 5, refillTokens = 5, refillPeriodSeconds = 60)
    @PostMapping("/batch-calculate")
    public OperationResponse<Map<String, Integer>> batchCalculate(
            @RequestParam Long modelId,
            @RequestParam(required = false) LocalDateTime startDate,
            @RequestParam(required = false) LocalDateTime endDate) throws ScrmException {
        return OperationResponse.build(scrmAttributionService.batchCalculate(modelId, startDate, endDate));
    }

    // ============================================================
    // 报告 /reports
    // ============================================================

    /**
     * 生成归因报告。
     *
     * @param dto 报告参数
     * @return 报告结果 Map
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_attribution", action = "read")
    @PostMapping("/reports/generate")
    public OperationResponse<Map<String, Object>> generateReport(@Valid @RequestBody ScrmAttributionReportDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmAttributionService.generateReport(dto));
    }

    /**
     * 渠道归因报告。
     *
     * @param modelId   归因模型 ID
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   结束时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 渠道归因列表
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_attribution", action = "read")
    @GetMapping("/reports/channels")
    public OperationResponse<List<Map<String, Object>>> getChannelAttribution(
            @RequestParam Long modelId,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) throws ScrmException {
        return OperationResponse.build(scrmAttributionService.getChannelAttribution(
                modelId, startTime, endTime));
    }

    /**
     * 触点类型归因报告。
     *
     * @param modelId   归因模型 ID
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   结束时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 触点类型归因列表
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_attribution", action = "read")
    @GetMapping("/reports/touchpoint-types")
    public OperationResponse<List<Map<String, Object>>> getTouchpointTypeAttribution(
            @RequestParam Long modelId,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) throws ScrmException {
        return OperationResponse.build(scrmAttributionService.getTouchpointTypeAttribution(
                modelId, startTime, endTime));
    }

    /**
     * 活动归因报告。
     *
     * @param modelId   归因模型 ID
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   结束时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 活动归因列表
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_attribution", action = "read")
    @GetMapping("/reports/campaigns")
    public OperationResponse<List<Map<String, Object>>> getCampaignAttribution(
            @RequestParam Long modelId,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) throws ScrmException {
        return OperationResponse.build(scrmAttributionService.getCampaignAttribution(
                modelId, startTime, endTime));
    }

    /**
     * 转化路径。
     *
     * @param conversionId 转化 ID
     * @param modelId      归因模型 ID
     * @return 转化路径 Map: {conversion, touchpoints}
     * @throws ScrmException 模型 / 转化不存在
     */
    @RequirePermission(resource = "scrm_attribution", action = "read")
    @GetMapping("/reports/path/{conversionId}")
    public OperationResponse<Map<String, Object>> getConversionPath(
            @PathVariable Long conversionId,
            @RequestParam Long modelId) throws ScrmException {
        return OperationResponse.build(scrmAttributionService.getConversionPath(modelId, conversionId));
    }

    /**
     * 高价值触点。
     *
     * @param modelId 归因模型 ID
     * @param limit   返回条数（默认 10）
     * @return 触点列表
     * @throws ScrmException 模型不存在
     */
    @RequirePermission(resource = "scrm_attribution", action = "read")
    @GetMapping("/reports/top-touchpoints")
    public OperationResponse<List<ScrmAttributionTouchpointEntity>> getTopTouchpoints(
            @RequestParam Long modelId,
            @RequestParam(defaultValue = "10") int limit) throws ScrmException {
        return OperationResponse.build(scrmAttributionService.getTopTouchpoints(modelId, limit));
    }

    // ============================================================
    // 统计 /stats
    // ============================================================

    /**
     * 归因统计概览: 总转化 / 总触点 / 平均触点数 / 平均转化时长。
     *
     * @param startTime 转化时间起点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   转化时间终点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_attribution", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getAttributionStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmAttributionService.getAttributionStats(startTime, endTime));
    }

    /**
     * 模型对比: 各模型下各渠道权重对比。
     *
     * @param startTime 转化时间起点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   转化时间终点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 模型对比列表
     */
    @RequirePermission(resource = "scrm_attribution", action = "read")
    @GetMapping("/stats/model-comparison")
    public OperationResponse<List<Map<String, Object>>> getModelComparison(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmAttributionService.getModelComparison(startTime, endTime));
    }

    /**
     * 触点效果: 各渠道的触点总数 / 归因触点数 / 归因价值 / 归因率。
     *
     * @param startTime 触点时间起点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   触点时间终点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 触点效果列表
     */
    @RequirePermission(resource = "scrm_attribution", action = "read")
    @GetMapping("/stats/touchpoint-effectiveness")
    public OperationResponse<List<Map<String, Object>>> getTouchpointEffectiveness(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmAttributionService.getTouchpointEffectiveness(startTime, endTime));
    }

    /**
     * 转化趋势: 按日期统计转化数与转化价值。
     *
     * @param days 回溯天数（默认 7）
     * @return 趋势列表 [{date, count, totalValue}]
     */
    @RequirePermission(resource = "scrm_attribution", action = "read")
    @GetMapping("/stats/conversion-trend")
    public OperationResponse<List<Map<String, Object>>> getConversionTrend(
            @RequestParam(defaultValue = "7") int days) {
        return OperationResponse.build(scrmAttributionService.getConversionTrend(days));
    }

    /**
     * 归因趋势: 按日期统计已归因触点的归因价值与权重。
     *
     * @param days 回溯天数（默认 7）
     * @return 趋势列表 [{date, totalValue, totalWeight, count}]
     */
    @RequirePermission(resource = "scrm_attribution", action = "read")
    @GetMapping("/stats/attribution-trend")
    public OperationResponse<List<Map<String, Object>>> getAttributionTrend(
            @RequestParam(defaultValue = "7") int days) {
        return OperationResponse.build(scrmAttributionService.getAttributionTrend(days));
    }
}
