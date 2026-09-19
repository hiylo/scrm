/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCampaignAnalysisStatsService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.entity.ScrmCampaignAnalysisEntity;
import org.hiylo.scrm.entity.ScrmCampaignChannelEntity;
import org.hiylo.scrm.entity.ScrmCampaignFunnelEntity;
import org.hiylo.scrm.repository.ScrmCampaignAnalysisRepository;
import org.hiylo.scrm.repository.ScrmCampaignChannelRepository;
import org.hiylo.scrm.repository.ScrmCampaignFunnelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SCRM 营销活动效果分析服务 - 统计子域。
 * <p>
 * 承载活动 / 渠道 / 漏斗统计, ROI / 转化 / 成本 / 收入趋势, Top 活动 / 渠道,
 * 性能分布与活动概览。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
public class ScrmCampaignAnalysisStatsService {

    /** 默认排行条数 */
    private static final int DEFAULT_LIMIT = 10;

    /** 默认趋势月数 */
    private static final int DEFAULT_TREND_MONTHS = 6;

    /** 活动分析数据访问层 */
    private final ScrmCampaignAnalysisRepository analysisRepository;

    /** 渠道效果数据访问层 */
    private final ScrmCampaignChannelRepository channelRepository;

    /** 转化漏斗数据访问层 */
    private final ScrmCampaignFunnelRepository funnelRepository;

    /** 分析管理子域服务 (共享安全取值 / 精度处理 / 状态常量) */
    private final ScrmCampaignAnalysisManageService manageService;

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 活动统计 (按活动类型汇总数量/收入/成本/ROI)。
     *
     * @return 活动统计
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCampaignStats() {
        List<ScrmCampaignAnalysisEntity> all = analysisRepository.findAll((root, query, cb) ->
                cb.and());
        double totalRevenue = all.stream().mapToDouble(e -> manageService.safe(e.getRevenue())).sum();
        double totalCost = all.stream().mapToDouble(e -> manageService.safe(e.getActualCost())).sum();
        double totalProfit = totalRevenue - totalCost;
        int totalReach = all.stream().mapToInt(e -> manageService.safe(e.getReachCount())).sum();
        int totalConversion = all.stream().mapToInt(e -> manageService.safe(e.getConversionCount())).sum();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("campaignCount", all.size());
        result.put("totalRevenue", manageService.round2(totalRevenue));
        result.put("totalCost", manageService.round2(totalCost));
        result.put("totalProfit", manageService.round2(totalProfit));
        result.put("overallRoi", totalCost > 0 ? manageService.round2(totalProfit / totalCost) : 0d);
        result.put("totalReach", totalReach);
        result.put("totalConversion", totalConversion);
        result.put("overallConversionRate", totalReach > 0
                ? manageService.round2((double) totalConversion / totalReach) : 0d);
        Map<String, List<ScrmCampaignAnalysisEntity>> byType = all.stream()
                .filter(e -> e.getCampaignType() != null)
                .collect(Collectors.groupingBy(ScrmCampaignAnalysisEntity::getCampaignType));
        result.put("byType", byType.entrySet().stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("campaignType", e.getKey());
            m.put("count", e.getValue().size());
            m.put("revenue", manageService.round2(e.getValue().stream()
                    .mapToDouble(x -> manageService.safe(x.getRevenue())).sum()));
            m.put("cost", manageService.round2(e.getValue().stream()
                    .mapToDouble(x -> manageService.safe(x.getActualCost())).sum()));
            return m;
        }).collect(Collectors.toList()));
        return result;
    }

    /**
     * 渠道统计 (按渠道类型汇总数量/收入/成本/ROI)。
     *
     * @return 渠道统计
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getChannelStats() {
        List<ScrmCampaignChannelEntity> all = channelRepository.findAll((root, query, cb) ->
                cb.and());
        double totalCost = all.stream().mapToDouble(c -> manageService.safe(c.getChannelCost())).sum();
        double totalRevenue = all.stream().mapToDouble(c -> manageService.safe(c.getRevenue())).sum();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("channelCount", all.size());
        result.put("totalCost", manageService.round2(totalCost));
        result.put("totalRevenue", manageService.round2(totalRevenue));
        result.put("overallRoi", totalCost > 0 ? manageService.round2((totalRevenue - totalCost) / totalCost) : 0d);
        Map<String, List<ScrmCampaignChannelEntity>> byType = all.stream()
                .filter(c -> c.getChannelType() != null)
                .collect(Collectors.groupingBy(ScrmCampaignChannelEntity::getChannelType));
        result.put("byType", byType.entrySet().stream().map(e -> {
            double cost = e.getValue().stream().mapToDouble(c -> manageService.safe(c.getChannelCost())).sum();
            double revenue = e.getValue().stream().mapToDouble(c -> manageService.safe(c.getRevenue())).sum();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("channelType", e.getKey());
            m.put("count", e.getValue().size());
            m.put("cost", manageService.round2(cost));
            m.put("revenue", manageService.round2(revenue));
            m.put("roi", cost > 0 ? manageService.round2((revenue - cost) / cost) : 0d);
            return m;
        }).collect(Collectors.toList()));
        return result;
    }

    /**
     * 漏斗统计 (按漏斗类型汇总阶段数/转化率/瓶颈数)。
     *
     * @return 漏斗统计
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getFunnelStats() {
        List<ScrmCampaignFunnelEntity> all = funnelRepository.findAll((root, query, cb) ->
                cb.and());
        long bottleneckCount = all.stream()
                .filter(f -> Boolean.TRUE.equals(f.getIsBottleneck())).count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("funnelStageCount", all.size());
        result.put("bottleneckCount", bottleneckCount);
        result.put("avgConversionRate", all.isEmpty() ? 0d
                : manageService.round2(all.stream().mapToDouble(f -> manageService.safe(f.getConversionRate()))
                        .average().orElse(0d)));
        result.put("avgDropoffRate", all.isEmpty() ? 0d
                : manageService.round2(all.stream().mapToDouble(f -> manageService.safe(f.getDropoffRate()))
                        .average().orElse(0d)));
        Map<String, List<ScrmCampaignFunnelEntity>> byType = all.stream()
                .filter(f -> f.getFunnelType() != null)
                .collect(Collectors.groupingBy(ScrmCampaignFunnelEntity::getFunnelType));
        result.put("byType", byType.entrySet().stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("funnelType", e.getKey());
            m.put("stageCount", e.getValue().size());
            m.put("bottleneckCount", e.getValue().stream()
                    .filter(f -> Boolean.TRUE.equals(f.getIsBottleneck())).count());
            m.put("avgConversionRate", manageService.round2(e.getValue().stream()
                    .mapToDouble(f -> manageService.safe(f.getConversionRate())).average().orElse(0d)));
            return m;
        }).collect(Collectors.toList()));
        return result;
    }

    /**
     * ROI 趋势 (最近 months 个月)。
     *
     * @param months 月数 (默认 6)
     * @return 趋势数据列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getROITrend(Integer months) {
        int n = months != null && months > 0 ? months : DEFAULT_TREND_MONTHS;
        List<ScrmCampaignAnalysisEntity> all = analysisRepository.findAll((root, query, cb) ->
                cb.and());
        List<Map<String, Object>> result = new ArrayList<>();
        YearMonth current = YearMonth.now();
        for (int i = n - 1; i >= 0; i--) {
            YearMonth ym = current.minusMonths(i);
            LocalDate periodStart = ym.atDay(1);
            LocalDate periodEnd = ym.atEndOfMonth();
            List<ScrmCampaignAnalysisEntity> monthAnalyses = all.stream()
                    .filter(e -> e.getStartDate() != null && e.getEndDate() != null
                            && !e.getStartDate().isAfter(periodEnd) && !e.getEndDate().isBefore(periodStart))
                    .collect(Collectors.toList());
            double revenue = monthAnalyses.stream().mapToDouble(e -> manageService.safe(e.getRevenue())).sum();
            double cost = monthAnalyses.stream().mapToDouble(e -> manageService.safe(e.getActualCost())).sum();
            double profit = revenue - cost;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("period", ym.format(DateTimeFormatter.ofPattern("yyyy-MM")));
            m.put("campaignCount", monthAnalyses.size());
            m.put("revenue", manageService.round2(revenue));
            m.put("cost", manageService.round2(cost));
            m.put("profit", manageService.round2(profit));
            m.put("roi", cost > 0 ? manageService.round2(profit / cost) : 0d);
            result.add(m);
        }
        return result;
    }

    /**
     * 转化趋势 (最近 months 个月)。
     *
     * @param months 月数 (默认 6)
     * @return 趋势数据列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getConversionTrend(Integer months) {
        int n = months != null && months > 0 ? months : DEFAULT_TREND_MONTHS;
        List<ScrmCampaignAnalysisEntity> all = analysisRepository.findAll((root, query, cb) ->
                cb.and());
        List<Map<String, Object>> result = new ArrayList<>();
        YearMonth current = YearMonth.now();
        for (int i = n - 1; i >= 0; i--) {
            YearMonth ym = current.minusMonths(i);
            LocalDate periodStart = ym.atDay(1);
            LocalDate periodEnd = ym.atEndOfMonth();
            List<ScrmCampaignAnalysisEntity> monthAnalyses = all.stream()
                    .filter(e -> e.getStartDate() != null && e.getEndDate() != null
                            && !e.getStartDate().isAfter(periodEnd) && !e.getEndDate().isBefore(periodStart))
                    .collect(Collectors.toList());
            int reach = monthAnalyses.stream().mapToInt(e -> manageService.safe(e.getReachCount())).sum();
            int conversion = monthAnalyses.stream().mapToInt(e -> manageService.safe(e.getConversionCount())).sum();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("period", ym.format(DateTimeFormatter.ofPattern("yyyy-MM")));
            m.put("reachCount", reach);
            m.put("conversionCount", conversion);
            m.put("conversionRate", reach > 0 ? manageService.round2((double) conversion / reach) : 0d);
            result.add(m);
        }
        return result;
    }

    /**
     * 成本趋势 (最近 months 个月)。
     *
     * @param months 月数 (默认 6)
     * @return 趋势数据列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCostTrend(Integer months) {
        int n = months != null && months > 0 ? months : DEFAULT_TREND_MONTHS;
        List<ScrmCampaignAnalysisEntity> all = analysisRepository.findAll((root, query, cb) ->
                cb.and());
        List<Map<String, Object>> result = new ArrayList<>();
        YearMonth current = YearMonth.now();
        for (int i = n - 1; i >= 0; i--) {
            YearMonth ym = current.minusMonths(i);
            LocalDate periodStart = ym.atDay(1);
            LocalDate periodEnd = ym.atEndOfMonth();
            List<ScrmCampaignAnalysisEntity> monthAnalyses = all.stream()
                    .filter(e -> e.getStartDate() != null && e.getEndDate() != null
                            && !e.getStartDate().isAfter(periodEnd) && !e.getEndDate().isBefore(periodStart))
                    .collect(Collectors.toList());
            double budget = monthAnalyses.stream().mapToDouble(e -> manageService.safe(e.getBudget())).sum();
            double actualCost = monthAnalyses.stream().mapToDouble(e -> manageService.safe(e.getActualCost())).sum();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("period", ym.format(DateTimeFormatter.ofPattern("yyyy-MM")));
            m.put("campaignCount", monthAnalyses.size());
            m.put("budget", manageService.round2(budget));
            m.put("actualCost", manageService.round2(actualCost));
            m.put("budgetUtilization", budget > 0 ? manageService.round2(actualCost / budget) : 0d);
            result.add(m);
        }
        return result;
    }

    /**
     * 收入趋势 (最近 months 个月)。
     *
     * @param months 月数 (默认 6)
     * @return 趋势数据列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRevenueTrend(Integer months) {
        int n = months != null && months > 0 ? months : DEFAULT_TREND_MONTHS;
        List<ScrmCampaignAnalysisEntity> all = analysisRepository.findAll((root, query, cb) ->
                cb.and());
        List<Map<String, Object>> result = new ArrayList<>();
        YearMonth current = YearMonth.now();
        for (int i = n - 1; i >= 0; i--) {
            YearMonth ym = current.minusMonths(i);
            LocalDate periodStart = ym.atDay(1);
            LocalDate periodEnd = ym.atEndOfMonth();
            List<ScrmCampaignAnalysisEntity> monthAnalyses = all.stream()
                    .filter(e -> e.getStartDate() != null && e.getEndDate() != null
                            && !e.getStartDate().isAfter(periodEnd) && !e.getEndDate().isBefore(periodStart))
                    .collect(Collectors.toList());
            double revenue = monthAnalyses.stream().mapToDouble(e -> manageService.safe(e.getRevenue())).sum();
            double profit = monthAnalyses.stream().mapToDouble(e -> manageService.safe(e.getProfit())).sum();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("period", ym.format(DateTimeFormatter.ofPattern("yyyy-MM")));
            m.put("campaignCount", monthAnalyses.size());
            m.put("revenue", manageService.round2(revenue));
            m.put("profit", manageService.round2(profit));
            m.put("profitMargin", revenue > 0 ? manageService.round2(profit / revenue) : 0d);
            result.add(m);
        }
        return result;
    }

    /**
     * Top 活动 (按收入倒序)。
     *
     * @param limit 返回条数 (默认 10)
     * @return 活动排行列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTopCampaigns(Integer limit) {
        int topN = limit != null && limit > 0 ? limit : DEFAULT_LIMIT;
        List<ScrmCampaignAnalysisEntity> all = analysisRepository.findAll((root, query, cb) ->
                cb.and());
        return all.stream()
                .sorted(Comparator.comparingDouble(
                        (ScrmCampaignAnalysisEntity e) -> manageService.safe(e.getRevenue())).reversed())
                .limit(topN)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("analysisId", e.getId());
                    m.put("campaignId", e.getCampaignId());
                    m.put("campaignName", e.getCampaignName());
                    m.put("campaignType", e.getCampaignType());
                    m.put("status", e.getStatus());
                    m.put("revenue", manageService.round2(manageService.safe(e.getRevenue())));
                    m.put("profit", manageService.round2(manageService.safe(e.getProfit())));
                    m.put("roi", e.getRoi());
                    m.put("conversionCount", e.getConversionCount());
                    return m;
                })
                .collect(Collectors.toList());
    }

    /**
     * Top 渠道 (按效率评分倒序)。
     *
     * @param limit 返回条数 (默认 10)
     * @return 渠道排行列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTopChannels(Integer limit) {
        int topN = limit != null && limit > 0 ? limit : DEFAULT_LIMIT;
        List<ScrmCampaignChannelEntity> all = channelRepository.findAll((root, query, cb) ->
                cb.and());
        return all.stream()
                .sorted(Comparator.comparingDouble(
                        (ScrmCampaignChannelEntity c) -> manageService.safe(c.getEfficiencyScore())).reversed())
                .limit(topN)
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("channelId", c.getId());
                    m.put("channelName", c.getChannelName());
                    m.put("channelType", c.getChannelType());
                    m.put("channelCost", manageService.round2(manageService.safe(c.getChannelCost())));
                    m.put("revenue", manageService.round2(manageService.safe(c.getRevenue())));
                    m.put("roi", c.getRoi());
                    m.put("efficiencyScore", c.getEfficiencyScore());
                    return m;
                })
                .collect(Collectors.toList());
    }

    /**
     * 活动性能分布 (按状态分组统计数量/收入/转化)。
     *
     * @return 性能分布
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCampaignPerformanceDistribution() {
        List<ScrmCampaignAnalysisEntity> all = analysisRepository.findAll((root, query, cb) ->
                cb.and());
        Map<String, List<ScrmCampaignAnalysisEntity>> byStatus = all.stream()
                .filter(e -> e.getStatus() != null)
                .collect(Collectors.groupingBy(ScrmCampaignAnalysisEntity::getStatus));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", all.size());
        result.put("byStatus", byStatus.entrySet().stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("status", e.getKey());
            m.put("count", e.getValue().size());
            m.put("revenue", manageService.round2(e.getValue().stream()
                    .mapToDouble(x -> manageService.safe(x.getRevenue())).sum()));
            m.put("conversion", e.getValue().stream()
                    .mapToInt(x -> manageService.safe(x.getConversionCount())).sum());
            m.put("percentage", all.size() > 0 ? manageService.round2((double) e.getValue().size() / all.size()) : 0d);
            return m;
        }).collect(Collectors.toList()));
        return result;
    }

    /**
     * 活动概览 (核心 KPI 汇总)。
     *
     * @return 概览数据
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCampaignOverview() {
        List<ScrmCampaignAnalysisEntity> all = analysisRepository.findAll((root, query, cb) ->
                cb.and());
        long runningCount = all.stream()
                .filter(e -> ScrmCampaignAnalysisManageService.STATUS_RUNNING.equals(e.getStatus())).count();
        long completedCount = all.stream()
                .filter(e -> ScrmCampaignAnalysisManageService.STATUS_COMPLETED.equals(e.getStatus())).count();
        long plannedCount = all.stream()
                .filter(e -> ScrmCampaignAnalysisManageService.STATUS_PLANNED.equals(e.getStatus())).count();
        double totalRevenue = all.stream().mapToDouble(e -> manageService.safe(e.getRevenue())).sum();
        double totalCost = all.stream().mapToDouble(e -> manageService.safe(e.getActualCost())).sum();
        double totalProfit = totalRevenue - totalCost;
        int totalReach = all.stream().mapToInt(e -> manageService.safe(e.getReachCount())).sum();
        int totalConversion = all.stream().mapToInt(e -> manageService.safe(e.getConversionCount())).sum();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCampaigns", all.size());
        result.put("runningCampaigns", runningCount);
        result.put("completedCampaigns", completedCount);
        result.put("plannedCampaigns", plannedCount);
        result.put("totalRevenue", manageService.round2(totalRevenue));
        result.put("totalCost", manageService.round2(totalCost));
        result.put("totalProfit", manageService.round2(totalProfit));
        result.put("overallRoi", totalCost > 0 ? manageService.round2(totalProfit / totalCost) : 0d);
        result.put("overallRoas", totalCost > 0 ? manageService.round2(totalRevenue / totalCost) : 0d);
        result.put("totalReach", totalReach);
        result.put("totalConversion", totalConversion);
        result.put("overallConversionRate", totalReach > 0
                ? manageService.round2((double) totalConversion / totalReach) : 0d);
        return result;
    }
}