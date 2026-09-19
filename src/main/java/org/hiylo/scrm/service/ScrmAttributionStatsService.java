/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAttributionStatsService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmAttributionModelEntity;
import org.hiylo.scrm.repository.ScrmAttributionConversionRepository;
import org.hiylo.scrm.repository.ScrmAttributionModelRepository;
import org.hiylo.scrm.repository.ScrmAttributionTouchpointRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRM 营销归因统计服务。
 * <p>
 * 承载归因统计 (总转化/总触点/平均转化时长)、模型对比、触点效果、转化趋势与归因趋势。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026/09/19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmAttributionStatsService {

    /** 默认趋势回溯天数 */
    private static final int DEFAULT_TREND_DAYS = 7;

    /** 归因转化数据访问层 */
    private final ScrmAttributionConversionRepository conversionRepository;

    /** 归因触点数据访问层 */
    private final ScrmAttributionTouchpointRepository touchpointRepository;

    /** 归因模型数据访问层 */
    private final ScrmAttributionModelRepository modelRepository;

    /** 归因报告服务 (模型对比复用渠道归因构建) */
    private final ScrmAttributionReportService reportService;

    /**
     * 归因统计: 总转化 / 总触点 / 平均触点数 / 平均转化时长。
     *
     * @param startTime 转化时间起点 (含, 可空)
     * @param endTime   转化时间终点 (含, 可空)
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getAttributionStats(LocalDateTime startTime, LocalDateTime endTime) {
        Object[] stats = conversionRepository.aggregateConversionStats(startTime, endTime);
        Object[] avgs = conversionRepository.aggregateAttributionAverages(startTime, endTime);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("conversionCount", ScrmAttributionCalculateService.toLong(stats, 0));
        result.put("totalConversionValue", ScrmAttributionCalculateService.round2(
                ScrmAttributionCalculateService.toDouble(stats, 1)));
        result.put("totalTouchpoints", ScrmAttributionCalculateService.toLong(stats, 2));
        result.put("totalAttributedTouchpoints", ScrmAttributionCalculateService.toLong(stats, 3));
        result.put("avgTimeToConversionHours", ScrmAttributionCalculateService.round2(
                ScrmAttributionCalculateService.toDouble(avgs, 0)));
        result.put("avgTotalTouchpoints", ScrmAttributionCalculateService.round2(
                ScrmAttributionCalculateService.toDouble(avgs, 1)));
        result.put("avgAttributedTouchpoints", ScrmAttributionCalculateService.round2(
                ScrmAttributionCalculateService.toDouble(avgs, 2)));
        return result;
    }

    /**
     * 模型对比: 各模型下各渠道权重对比。
     * <p>遍历全部已发布模型, 对每个模型在时间窗口内重算各渠道归因, 输出对比矩阵。</p>
     *
     * @param startTime 转化时间起点 (含, 可空)
     * @param endTime   转化时间终点 (含, 可空)
     * @return 模型对比列表 [{modelId, modelName, modelType, channelAttribution: [{channel, value, weight, count}]}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getModelComparison(LocalDateTime startTime, LocalDateTime endTime) {
        Specification<ScrmAttributionModelEntity> spec = (root, query, cb) ->
                cb.equal(root.get("isPublished"), true);
        List<ScrmAttributionModelEntity> models = modelRepository.findAll(spec);
        List<Map<String, Object>> result = new ArrayList<>(models.size());
        for (ScrmAttributionModelEntity model : models) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("modelId", model.getId());
            entry.put("modelName", model.getModelName());
            entry.put("modelType", model.getModelType());
            entry.put("channelAttribution", reportService.buildChannelReport(startTime, endTime));
            result.add(entry);
        }
        return result;
    }

    /**
     * 触点效果: 各渠道的触点总数 / 归因触点数 / 归因价值 / 归因率。
     *
     * @param startTime 触点时间起点 (含, 可空)
     * @param endTime   触点时间终点 (含, 可空)
     * @return 触点效果列表 [{channel, totalEvents, attributedEvents, attributionValue, attributionRate}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTouchpointEffectiveness(LocalDateTime startTime, LocalDateTime endTime) {
        List<Object[]> rows = touchpointRepository.aggregateTouchpointEffectByChannel(
                 startTime, endTime);
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            long totalEvents = ScrmAttributionCalculateService.toLong(row, 1);
            long attributedEvents = ScrmAttributionCalculateService.toLong(row, 2);
            double value = ScrmAttributionCalculateService.toDouble(row, 3);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("channel", row[0]);
            entry.put("totalEvents", totalEvents);
            entry.put("attributedEvents", attributedEvents);
            entry.put("attributionValue", ScrmAttributionCalculateService.round2(value));
            entry.put("attributionRate", totalEvents == 0 ? 0.0 : (double) attributedEvents / totalEvents);
            result.add(entry);
        }
        return result;
    }

    /**
     * 转化趋势: 按日期统计转化数与转化价值。
     *
     * @param days 回溯天数 (默认 7)
     * @return 趋势列表 [{date, count, totalValue}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getConversionTrend(Integer days) {
        int dayCount = days != null && days > 0 ? days : DEFAULT_TREND_DAYS;
        LocalDateTime start = LocalDateTime.now().minusDays(dayCount);
        List<Object[]> rows = conversionRepository.conversionTrendByDay(start);
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("date", row[0]);
            entry.put("count", ScrmAttributionCalculateService.toLong(row, 1));
            entry.put("totalValue", ScrmAttributionCalculateService.round2(
                    ScrmAttributionCalculateService.toDouble(row, 2)));
            result.add(entry);
        }
        return result;
    }

    /**
     * 归因趋势: 按日期统计已归因触点的归因价值与权重。
     *
     * @param days 回溯天数 (默认 7)
     * @return 趋势列表 [{date, totalValue, totalWeight, count}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAttributionTrend(Integer days) {
        int dayCount = days != null && days > 0 ? days : DEFAULT_TREND_DAYS;
        LocalDateTime start = LocalDateTime.now().minusDays(dayCount);
        List<Object[]> rows = touchpointRepository.attributionTrendByDay(start);
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("date", row[0]);
            entry.put("totalValue", ScrmAttributionCalculateService.round2(
                    ScrmAttributionCalculateService.toDouble(row, 1)));
            entry.put("totalWeight", ScrmAttributionCalculateService.round4(
                    ScrmAttributionCalculateService.toDouble(row, 2)));
            entry.put("count", ScrmAttributionCalculateService.toLong(row, 3));
            result.add(entry);
        }
        return result;
    }
}