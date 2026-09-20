/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAttributionService.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.dto.ScrmAttributionCalculateDto;
import org.hiylo.scrm.dto.ScrmAttributionConversionDto;
import org.hiylo.scrm.dto.ScrmAttributionModelDto;
import org.hiylo.scrm.dto.ScrmAttributionReportDto;
import org.hiylo.scrm.dto.ScrmAttributionTouchpointDto;
import org.hiylo.scrm.entity.ScrmAttributionConversionEntity;
import org.hiylo.scrm.entity.ScrmAttributionModelEntity;
import org.hiylo.scrm.entity.ScrmAttributionTouchpointEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 营销效果归因服务。
 * <p>
 * 承载多触点营销归因分析的核心能力, 按子域委托给 {@link ScrmAttributionModelService} /
 * {@link ScrmAttributionTouchpointService} / {@link ScrmAttributionConversionService} /
 * {@link ScrmAttributionCalculateService} / {@link ScrmAttributionReportService} /
 * {@link ScrmAttributionStatsService} 实现, 本类为门面, 保持公开 API 契约不变。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
public class ScrmAttributionService {

    /** 模型管理服务 */
    private final ScrmAttributionModelService modelService;

    /** 触点记录服务 */
    private final ScrmAttributionTouchpointService touchpointService;

    /** 转化记录服务 */
    private final ScrmAttributionConversionService conversionService;

    /** 归因计算服务 */
    private final ScrmAttributionCalculateService calculateService;

    /** 归因报告服务 */
    private final ScrmAttributionReportService reportService;

    /** 归因统计服务 */
    private final ScrmAttributionStatsService statsService;

    /**
     * 创建归因模型。
     *
     * @param dto 模型参数
     * @return 创建后的模型
     * @throws ScrmException 参数非法 / modelCode 重复
     */
    public ScrmAttributionModelEntity createModel(ScrmAttributionModelDto dto) throws ScrmException {
        return modelService.createModel(dto);
    }

    /**
     * 更新归因模型（字段非空才覆盖）。
     *
     * @param id  模型 ID
     * @param dto 模型参数
     * @return 更新后的模型
     * @throws ScrmException 模型不存在 / 参数非法 / modelCode 重复
     */
    public ScrmAttributionModelEntity updateModel(Long id, ScrmAttributionModelDto dto) throws ScrmException {
        return modelService.updateModel(id, dto);
    }

    /**
     * 删除归因模型。
     *
     * @param id 模型 ID
     * @throws ScrmException 模型不存在
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
    public ScrmAttributionModelEntity getModel(Long id) throws ScrmException {
        return modelService.getModel(id);
    }

    /**
     * 按模型编码查询模型。
     *
     * @param code 模型编码
     * @return 模型实体
     * @throws ScrmException 模型不存在
     */
    public ScrmAttributionModelEntity getModelByCode(String code) throws ScrmException {
        return modelService.getModelByCode(code);
    }

    /**
     * 分页查询模型, 支持按模型类型 / 发布状态 / 关键字过滤。
     *
     * @param modelType   模型类型过滤（可空）
     * @param isPublished 发布状态过滤（可空）
     * @param keyword     模型名称关键字模糊匹配（可空）
     * @param pageable    分页参数
     * @return 模型分页结果 (按 createTime DESC)
     */
    public Page<ScrmAttributionModelEntity> listModels(String modelType, Boolean isPublished,
                                                        String keyword, Pageable pageable) {
        return modelService.listModels(modelType, isPublished, keyword, pageable);
    }

    /**
     * 发布模型。
     *
     * @param id 模型 ID
     * @return 更新后的模型
     * @throws ScrmException 模型不存在
     */
    public ScrmAttributionModelEntity publishModel(Long id) throws ScrmException {
        return modelService.publishModel(id);
    }

    /**
     * 取消发布模型。
     *
     * @param id 模型 ID
     * @return 更新后的模型
     * @throws ScrmException 模型不存在
     */
    public ScrmAttributionModelEntity unpublishModel(Long id) throws ScrmException {
        return modelService.unpublishModel(id);
    }

    /**
     * 设置为默认模型 (清理旧默认)。
     *
     * @param id 模型 ID
     * @return 更新后的模型
     * @throws ScrmException 模型不存在
     */
    public ScrmAttributionModelEntity setDefault(Long id) throws ScrmException {
        return modelService.setDefault(id);
    }

    /**
     * 记录触点。
     *
     * @param dto 触点参数
     * @return 创建后的触点
     * @throws ScrmException 参数非法
     */
    public ScrmAttributionTouchpointEntity recordTouchpoint(
            ScrmAttributionTouchpointDto dto) throws ScrmException {
        return touchpointService.recordTouchpoint(dto);
    }

    /**
     * 批量记录触点。
     *
     * @param dtos 触点参数列表
     * @return 创建后的触点列表
     * @throws ScrmException 参数非法
     */
    public List<ScrmAttributionTouchpointEntity> batchRecordTouchpoints(List<ScrmAttributionTouchpointDto> dtos)
            throws ScrmException {
        return touchpointService.batchRecordTouchpoints(dtos);
    }

    /**
     * 查询触点详情。
     *
     * @param id 触点 ID
     * @return 触点实体
     * @throws ScrmException 触点不存在
     */
    public ScrmAttributionTouchpointEntity getTouchpoint(Long id) throws ScrmException {
        return touchpointService.getTouchpoint(id);
    }

    /**
     * 分页查询触点, 支持按客户 / 触点类型 / 渠道 / 营销活动 / 时间范围过滤。
     *
     * @param customerId     客户 ID 过滤（可空）
     * @param touchpointType 触点类型过滤（可空）
     * @param channel        渠道过滤（可空）
     * @param campaignId     营销活动 ID 过滤（可空）
     * @param startTime      触点时间起点 (含, 可空)
     * @param endTime        触点时间终点 (含, 可空)
     * @param pageable       分页参数
     * @return 触点分页结果 (按 touchpointTime DESC)
     */
    public Page<ScrmAttributionTouchpointEntity> listTouchpoints(Long customerId, String touchpointType,
                                                                   String channel, Long campaignId,
                                                                   LocalDateTime startTime, LocalDateTime endTime,
                                                                   Pageable pageable) {
        return touchpointService.listTouchpoints(customerId, touchpointType, channel, campaignId,
                startTime, endTime, pageable);
    }

    /**
     * 客户触点链: 按客户与时间范围查询触点 (按触点时间升序)。
     *
     * @param customerId 客户 ID
     * @return 触点列表 (touchpointTime ASC)
     */
    public List<ScrmAttributionTouchpointEntity> getCustomerTouchpoints(Long customerId) {
        return touchpointService.getCustomerTouchpoints(customerId);
    }

    /**
     * 回溯窗口内触点: 查询客户在转化时间前 lookbackDays 天内的触点 (按触点时间升序)。
     *
     * @param customerId     客户 ID
     * @param conversionTime 转化时间 (回溯窗口终点)
     * @param lookbackDays   回溯天数
     * @return 触点列表 (touchpointTime ASC)
     */
    public List<ScrmAttributionTouchpointEntity> getTouchpointsInWindow(Long customerId,
                                                                          LocalDateTime conversionTime,
                                                                          int lookbackDays) {
        return touchpointService.getTouchpointsInWindow(customerId, conversionTime, lookbackDays);
    }

    /**
     * 记录转化。
     *
     * @param dto 转化参数
     * @return 创建后的转化
     * @throws ScrmException 参数非法
     */
    public ScrmAttributionConversionEntity recordConversion(
            ScrmAttributionConversionDto dto) throws ScrmException {
        return conversionService.recordConversion(dto);
    }

    /**
     * 查询转化详情。
     *
     * @param id 转化 ID
     * @return 转化实体
     * @throws ScrmException 转化不存在
     */
    public ScrmAttributionConversionEntity getConversion(Long id) throws ScrmException {
        return conversionService.getConversion(id);
    }

    /**
     * 分页查询转化, 支持按客户 / 转化类型 / 模型 / 时间范围过滤。
     *
     * @param customerId     客户 ID 过滤（可空）
     * @param conversionType 转化类型过滤（可空）
     * @param modelId        归因模型 ID 过滤（可空）
     * @param startTime      转化时间起点 (含, 可空)
     * @param endTime        转化时间终点 (含, 可空)
     * @param pageable       分页参数
     * @return 转化分页结果 (按 conversionTime DESC)
     */
    public Page<ScrmAttributionConversionEntity> listConversions(Long customerId, String conversionType,
                                                                   Long modelId, LocalDateTime startTime,
                                                                   LocalDateTime endTime, Pageable pageable) {
        return conversionService.listConversions(customerId, conversionType, modelId, startTime, endTime, pageable);
    }

    /**
     * 按订单号查询转化。
     *
     * @param orderId 订单号
     * @return 转化实体
     * @throws ScrmException 转化不存在
     */
    public ScrmAttributionConversionEntity getConversionByOrder(String orderId) throws ScrmException {
        return conversionService.getConversionByOrder(orderId);
    }

    /**
     * 计算归因: 选择模型 → 查询触点 → 应用归因规则 → 分配权重 → 计算归因价值。
     *
     * @param calculateDto 归因计算参数
     * @return 计算结果: {total, processed, failed}
     * @throws ScrmException 模型不存在
     */
    public Map<String, Integer> calculateAttribution(ScrmAttributionCalculateDto calculateDto)
            throws ScrmException {
        return calculateService.calculateAttribution(calculateDto);
    }

    /**
     * 批量计算: 按模型 ID 与时间范围对全部转化执行归因计算。
     *
     * @param modelId   归因模型 ID
     * @param startDate 转化时间起点 (含, 可空)
     * @param endDate   转化时间终点 (含, 可空)
     * @return 计算结果: {total, processed, failed}
     * @throws ScrmException 模型不存在
     */
    public Map<String, Integer> batchCalculate(Long modelId, LocalDateTime startDate, LocalDateTime endDate)
            throws ScrmException {
        return calculateService.batchCalculate(modelId, startDate, endDate);
    }

    /**
     * 首次触点归因: 首触点权重 1.0, 其余 0。
     *
     * @param touchpoints     触点列表 (按触点时间升序)
     * @param conversionValue 转化价值
     * @return 触点 ID → 归因权重 映射
     */
    public Map<Long, Double> applyFirstTouch(List<ScrmAttributionTouchpointEntity> touchpoints,
                                              double conversionValue) {
        return calculateService.applyFirstTouch(touchpoints, conversionValue);
    }

    /**
     * 末次触点归因: 末触点权重 1.0, 其余 0。
     *
     * @param touchpoints     触点列表 (按触点时间升序)
     * @param conversionValue 转化价值
     * @return 触点 ID → 归因权重 映射
     */
    public Map<Long, Double> applyLastTouch(List<ScrmAttributionTouchpointEntity> touchpoints,
                                             double conversionValue) {
        return calculateService.applyLastTouch(touchpoints, conversionValue);
    }

    /**
     * 线性归因: 所有触点均分权重 1/n。
     *
     * @param touchpoints     触点列表 (按触点时间升序)
     * @param conversionValue 转化价值
     * @return 触点 ID → 归因权重 映射
     */
    public Map<Long, Double> applyLinear(List<ScrmAttributionTouchpointEntity> touchpoints,
                                          double conversionValue) {
        return calculateService.applyLinear(touchpoints, conversionValue);
    }

    /**
     * 时间衰减归因: 按触点时间距转化时间的间隔, 以 2^(-days/halfLife) 衰减。
     *
     * @param touchpoints     触点列表 (按触点时间升序)
     * @param conversionValue 转化价值
     * @param halfLifeDays    时间衰减半衰期天数
     * @return 触点 ID → 归因权重 映射
     */
    public Map<Long, Double> applyTimeDecay(List<ScrmAttributionTouchpointEntity> touchpoints,
                                             double conversionValue, int halfLifeDays) {
        return calculateService.applyTimeDecay(touchpoints, conversionValue, halfLifeDays);
    }

    /**
     * 位置归因 (U 型/W 型): 按位置权重 {first, last, middle} 分配。
     *
     * @param touchpoints     触点列表 (按触点时间升序)
     * @param conversionValue 转化价值
     * @param weights         位置权重 {first, last, middle}
     * @return 触点 ID → 归因权重 映射
     */
    public Map<Long, Double> applyPositionBased(List<ScrmAttributionTouchpointEntity> touchpoints,
                                                  double conversionValue, Map<String, Double> weights) {
        return calculateService.applyPositionBased(touchpoints, conversionValue, weights);
    }

    /**
     * 生成归因报告: 按 groupBy 维度 (渠道 / 触点类型 / 活动) 汇总归因价值与权重。
     *
     * @param reportDto 报告参数
     * @return 报告结果 Map
     * @throws ScrmException 模型不存在
     */
    public Map<String, Object> generateReport(ScrmAttributionReportDto reportDto) throws ScrmException {
        return reportService.generateReport(reportDto);
    }

    /**
     * 渠道归因: 按渠道汇总归因价值与权重。
     *
     * @param modelId   归因模型 ID (用于校验归属)
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   结束时间 (含, 可空)
     * @return 渠道归因列表
     * @throws ScrmException 模型不存在
     */
    public List<Map<String, Object>> getChannelAttribution(Long modelId, LocalDateTime startTime,
                                                            LocalDateTime endTime) throws ScrmException {
        return reportService.getChannelAttribution(modelId, startTime, endTime);
    }

    /**
     * 触点类型归因: 按触点类型汇总归因价值与权重。
     *
     * @param modelId   归因模型 ID (用于校验归属)
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   结束时间 (含, 可空)
     * @return 触点类型归因列表
     * @throws ScrmException 模型不存在
     */
    public List<Map<String, Object>> getTouchpointTypeAttribution(Long modelId, LocalDateTime startTime,
                                                                   LocalDateTime endTime) throws ScrmException {
        return reportService.getTouchpointTypeAttribution(modelId, startTime, endTime);
    }

    /**
     * 活动归因: 按营销活动汇总归因价值与权重。
     *
     * @param modelId   归因模型 ID (用于校验归属)
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   结束时间 (含, 可空)
     * @return 活动归因列表
     * @throws ScrmException 模型不存在
     */
    public List<Map<String, Object>> getCampaignAttribution(Long modelId, LocalDateTime startTime,
                                                             LocalDateTime endTime) throws ScrmException {
        return reportService.getCampaignAttribution(modelId, startTime, endTime);
    }

    /**
     * 转化路径: 查询转化详情及其归因触点链。
     *
     * @param modelId      归因模型 ID (用于校验归属)
     * @param conversionId 转化 ID
     * @return 转化路径 Map: {conversion, touchpoints}
     * @throws ScrmException 模型 / 转化不存在
     */
    public Map<String, Object> getConversionPath(Long modelId, Long conversionId) throws ScrmException {
        return reportService.getConversionPath(modelId, conversionId);
    }

    /**
     * 高价值触点: 已归因触点按归因价值倒序返回。
     *
     * @param modelId 归因模型 ID (用于校验归属)
     * @param limit   返回条数 (默认 10)
     * @return 触点列表
     * @throws ScrmException 模型不存在
     */
    public List<ScrmAttributionTouchpointEntity> getTopTouchpoints(Long modelId, Integer limit)
            throws ScrmException {
        return reportService.getTopTouchpoints(modelId, limit);
    }

    /**
     * 归因统计: 总转化 / 总触点 / 平均触点数 / 平均转化时长。
     *
     * @param startTime 转化时间起点 (含, 可空)
     * @param endTime   转化时间终点 (含, 可空)
     * @return 统计结果 Map
     */
    public Map<String, Object> getAttributionStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getAttributionStats(startTime, endTime);
    }

    /**
     * 模型对比: 各模型下各渠道权重对比。
     *
     * @param startTime 转化时间起点 (含, 可空)
     * @param endTime   转化时间终点 (含, 可空)
     * @return 模型对比列表
     */
    public List<Map<String, Object>> getModelComparison(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getModelComparison(startTime, endTime);
    }

    /**
     * 触点效果: 各渠道的触点总数 / 归因触点数 / 归因价值 / 归因率。
     *
     * @param startTime 触点时间起点 (含, 可空)
     * @param endTime   触点时间终点 (含, 可空)
     * @return 触点效果列表
     */
    public List<Map<String, Object>> getTouchpointEffectiveness(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getTouchpointEffectiveness(startTime, endTime);
    }

    /**
     * 转化趋势: 按日期统计转化数与转化价值。
     *
     * @param days 回溯天数 (默认 7)
     * @return 趋势列表 [{date, count, totalValue}]
     */
    public List<Map<String, Object>> getConversionTrend(Integer days) {
        return statsService.getConversionTrend(days);
    }

    /**
     * 归因趋势: 按日期统计已归因触点的归因价值与权重。
     *
     * @param days 回溯天数 (默认 7)
     * @return 趋势列表 [{date, totalValue, totalWeight, count}]
     */
    public List<Map<String, Object>> getAttributionTrend(Integer days) {
        return statsService.getAttributionTrend(days);
    }
}