/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAttributionReportService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmAttributionReportDto;
import org.hiylo.scrm.entity.ScrmAttributionConversionEntity;
import org.hiylo.scrm.entity.ScrmAttributionTouchpointEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmAttributionConversionRepository;
import org.hiylo.scrm.repository.ScrmAttributionModelRepository;
import org.hiylo.scrm.repository.ScrmAttributionTouchpointRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRM 营销归因报告服务。
 * <p>
 * 承载归因报告 (渠道 / 触点类型 / 活动汇总)、渠道/触点类型/活动归因、转化路径与高价值触点查询。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026/09/19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmAttributionReportService {

    /** 默认报告回溯天数 */
    private static final int DEFAULT_REPORT_LOOKBACK_DAYS = 30;
    /** 默认高价值触点返回条数 */
    private static final int DEFAULT_TOP_TOUCHPOINT_LIMIT = 10;

    /** 归因模型数据访问层 */
    private final ScrmAttributionModelRepository modelRepository;

    /** 归因触点数据访问层 */
    private final ScrmAttributionTouchpointRepository touchpointRepository;

    /** 归因转化数据访问层 */
    private final ScrmAttributionConversionRepository conversionRepository;

    /** 模型管理服务 (校验模型归属) */
    private final ScrmAttributionModelService modelService;

    /** 转化记录服务 (查询转化详情) */
    private final ScrmAttributionConversionService conversionService;

    /**
     * 生成归因报告: 按 groupBy 维度 (渠道 / 触点类型 / 活动) 汇总归因价值与权重。
     *
     * @param reportDto 报告参数
     * @return 报告结果 Map
     * @throws ScrmException 模型不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> generateReport(ScrmAttributionReportDto reportDto) throws ScrmException {
        if (reportDto == null || reportDto.getModelId() == null) {
            throw ScrmException.badRequest("报告参数与模型 ID 不能为空");
        }
        // 校验模型存在与账号归属
        modelService.findModelOrThrow(reportDto.getModelId());
        LocalDateTime endTime = reportDto.getEndDate() != null ? reportDto.getEndDate() : LocalDateTime.now();
        LocalDateTime startTime = reportDto.getStartDate() != null
                ? reportDto.getStartDate() : endTime.minusDays(DEFAULT_REPORT_LOOKBACK_DAYS);
        String groupBy = reportDto.getGroupBy() != null && !reportDto.getGroupBy().isBlank()
                ? reportDto.getGroupBy().toUpperCase() : "CHANNEL";
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("modelId", reportDto.getModelId());
        report.put("startDate", startTime);
        report.put("endDate", endTime);
        report.put("groupBy", groupBy);
        switch (groupBy) {
            case "TOUCHPOINT_TYPE":
                report.put("items", buildTouchpointTypeReport(startTime, endTime));
                break;
            case "CAMPAIGN":
                report.put("items", buildCampaignReport(startTime, endTime));
                break;
            case "CHANNEL":
            default:
                report.put("items", buildChannelReport(startTime, endTime));
                break;
        }
        return report;
    }

    /**
     * 渠道归因: 按渠道汇总归因价值与权重。
     *
     * @param modelId   归因模型 ID (用于校验归属)
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   结束时间 (含, 可空)
     * @return 渠道归因列表 [{channel, totalAttributionValue, totalWeight, touchpointCount}]
     * @throws ScrmException 模型不存在
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getChannelAttribution(Long modelId, LocalDateTime startTime,
                                                            LocalDateTime endTime) throws ScrmException {
        modelService.findModelOrThrow(modelId);
        return buildChannelReport(startTime, endTime);
    }

    /**
     * 触点类型归因: 按触点类型汇总归因价值与权重。
     *
     * @param modelId   归因模型 ID (用于校验归属)
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   结束时间 (含, 可空)
     * @return 触点类型归因列表 [{touchpointType, totalAttributionValue, totalWeight, touchpointCount}]
     * @throws ScrmException 模型不存在
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTouchpointTypeAttribution(Long modelId, LocalDateTime startTime,
                                                                   LocalDateTime endTime) throws ScrmException {
        modelService.findModelOrThrow(modelId);
        return buildTouchpointTypeReport(startTime, endTime);
    }

    /**
     * 活动归因: 按营销活动汇总归因价值与权重。
     *
     * @param modelId   归因模型 ID (用于校验归属)
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   结束时间 (含, 可空)
     * @return 活动归因列表 [{campaignId, campaignName, totalAttributionValue, totalWeight, touchpointCount}]
     * @throws ScrmException 模型不存在
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCampaignAttribution(Long modelId, LocalDateTime startTime,
                                                             LocalDateTime endTime) throws ScrmException {
        modelService.findModelOrThrow(modelId);
        return buildCampaignReport(startTime, endTime);
    }

    /**
     * 转化路径: 查询转化详情及其归因触点链。
     *
     * @param modelId      归因模型 ID (用于校验归属)
     * @param conversionId 转化 ID
     * @return 转化路径 Map: {conversion, touchpoints}
     * @throws ScrmException 模型 / 转化不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getConversionPath(Long modelId, Long conversionId) throws ScrmException {
        modelService.findModelOrThrow(modelId);
        ScrmAttributionConversionEntity conversion = conversionService.findConversionOrThrow(conversionId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("conversion", conversion);
        List<ScrmAttributionTouchpointEntity> touchpoints = touchpointRepository.findCustomerTouchpointChain(
                conversion.getCustomerId(), null, conversion.getConversionTime());
        result.put("touchpoints", touchpoints);
        return result;
    }

    /**
     * 高价值触点: 已归因触点按归因价值倒序返回。
     *
     * @param modelId 归因模型 ID (用于校验归属)
     * @param limit   返回条数 (默认 10)
     * @return 触点列表
     * @throws ScrmException 模型不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmAttributionTouchpointEntity> getTopTouchpoints(Long modelId, Integer limit)
            throws ScrmException {
        modelService.findModelOrThrow(modelId);
        int size = limit != null && limit > 0 ? limit : DEFAULT_TOP_TOUCHPOINT_LIMIT;
        return touchpointRepository
                .findByIsAttributedTrueOrderByAttributionValueDesc(
                         PageRequest.of(0, size))
                .getContent();
    }

    /**
     * 构建渠道归因报告。
     * <p>供统计兄弟类共用。</p>
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   结束时间 (含, 可空)
     * @return 渠道归因列表
     */
    List<Map<String, Object>> buildChannelReport(LocalDateTime startTime,
                                                  LocalDateTime endTime) {
        List<Object[]> rows = touchpointRepository.aggregateAttributionByChannel(
                 startTime, endTime);
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("channel", row[0]);
            entry.put("totalAttributionValue", ScrmAttributionCalculateService.round2(
                    ScrmAttributionCalculateService.toDouble(row, 1)));
            entry.put("totalWeight", ScrmAttributionCalculateService.round4(
                    ScrmAttributionCalculateService.toDouble(row, 2)));
            entry.put("touchpointCount", ScrmAttributionCalculateService.toLong(row, 3));
            result.add(entry);
        }
        return result;
    }

    /**
     * 构建触点类型归因报告。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   结束时间 (含, 可空)
     * @return 触点类型归因列表
     */
    private List<Map<String, Object>> buildTouchpointTypeReport(LocalDateTime startTime,
                                                                 LocalDateTime endTime) {
        List<Object[]> rows = touchpointRepository.aggregateAttributionByTouchpointType(
                 startTime, endTime);
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("touchpointType", row[0]);
            entry.put("totalAttributionValue", ScrmAttributionCalculateService.round2(
                    ScrmAttributionCalculateService.toDouble(row, 1)));
            entry.put("totalWeight", ScrmAttributionCalculateService.round4(
                    ScrmAttributionCalculateService.toDouble(row, 2)));
            entry.put("touchpointCount", ScrmAttributionCalculateService.toLong(row, 3));
            result.add(entry);
        }
        return result;
    }

    /**
     * 构建活动归因报告。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   结束时间 (含, 可空)
     * @return 活动归因列表
     */
    private List<Map<String, Object>> buildCampaignReport(LocalDateTime startTime,
                                                           LocalDateTime endTime) {
        List<Object[]> rows = touchpointRepository.aggregateAttributionByCampaign(
                 startTime, endTime);
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("campaignId", row[0] == null ? null : String.valueOf(row[0]));
            entry.put("campaignName", row[1]);
            entry.put("totalAttributionValue", ScrmAttributionCalculateService.round2(
                    ScrmAttributionCalculateService.toDouble(row, 2)));
            entry.put("totalWeight", ScrmAttributionCalculateService.round4(
                    ScrmAttributionCalculateService.toDouble(row, 3)));
            entry.put("touchpointCount", ScrmAttributionCalculateService.toLong(row, 4));
            result.add(entry);
        }
        return result;
    }
}