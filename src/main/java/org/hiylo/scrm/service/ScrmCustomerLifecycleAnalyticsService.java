/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerLifecycleAnalyticsService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmCustomerLifecycleEntity;
import org.hiylo.scrm.entity.ScrmLifecycleHistoryEntity;
import org.hiylo.scrm.entity.ScrmLifecycleStageEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCustomerLifecycleRepository;
import org.hiylo.scrm.repository.ScrmLifecycleHistoryRepository;
import org.hiylo.scrm.repository.ScrmLifecycleStageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SCRM 客户生命周期评分与统计分析服务。
 * <p>
 * 承载客户生命周期的派生指标计算与统计分析能力: 流失风险 / LTV / 活跃度评分, 生命周期时间线 /
 * 下一步最佳行动 / 客户旅程, 以及概览 / 分布 / 流失率 / 留存率 / 平均周期 / 转化率 / 获客渠道 /
 * 趋势等统计。价值分层 / 风险等级等派生指标由服务端按需计算。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026/09/19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmCustomerLifecycleAnalyticsService {

    /** 高风险阈值 (流失风险 ≥ 60 视为风险客户) */
    static final double AT_RISK_THRESHOLD = 60.0;
    /** 流失风险上限 */
    private static final double MAX_RISK = 100.0;
    /** 默认趋势月数 */
    private static final int DEFAULT_TREND_MONTHS = 6;

    /** 既有生命周期服务 (复用阶段定义 / 流转规则 / 转换历史能力) */
    private final ScrmLifecycleService scrmLifecycleService;

    /** 阶段数据访问层 */
    private final ScrmLifecycleStageRepository stageRepository;

    /** 客户生命周期数据访问层 */
    private final ScrmCustomerLifecycleRepository customerLifecycleRepository;

    /** 转换历史数据访问层 */
    private final ScrmLifecycleHistoryRepository historyRepository;

    /**
     * 计算并返回客户流失风险 (基于停留天数 / 超期 / 转换频率等)。
     *
     * @param customerId 客户 ID
     * @return 风险评分结果 Map
     * @throws ScrmException 客户生命周期不存在
     */
    @Transactional
    public Map<String, Object> updateChurnRisk(Long customerId) throws ScrmException {
        ScrmCustomerLifecycleEntity lifecycle = scrmLifecycleService.getCustomerLifecycle(customerId);
        double risk = calculateChurnRisk(lifecycle);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customerId", customerId);
        result.put("churnRiskScore", round2(risk));
        result.put("riskLevel", computeRiskLevel(risk));
        result.put("churnProbability", round2(Math.min(risk / MAX_RISK, 1.0)));
        return result;
    }

    /**
     * 计算并返回客户 LTV (基于转换频率与阶段价值的代理估算)。
     *
     * @param customerId 客户 ID
     * @return LTV 估算结果 Map
     * @throws ScrmException 客户生命周期不存在
     */
    @Transactional
    public Map<String, Object> updateLTV(Long customerId) throws ScrmException {
        ScrmCustomerLifecycleEntity lifecycle = scrmLifecycleService.getCustomerLifecycle(customerId);
        double ltv = calculateLTV(lifecycle);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customerId", customerId);
        result.put("ltvValue", round2(ltv));
        result.put("predictedLtv", round2(ltv * 1.2));
        result.put("valueSegment", computeValueSegment(lifecycle));
        return result;
    }

    /**
     * 计算并返回客户活跃度评分 (基于转换频率 / 最近活跃)。
     *
     * @param customerId 客户 ID
     * @return 活跃度评分结果 Map
     * @throws ScrmException 客户生命周期不存在
     */
    @Transactional
    public Map<String, Object> updateEngagementScore(Long customerId) throws ScrmException {
        ScrmCustomerLifecycleEntity lifecycle = scrmLifecycleService.getCustomerLifecycle(customerId);
        double score = calculateEngagementScore(lifecycle);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customerId", customerId);
        result.put("engagementScore", round2(score));
        result.put("satisfactionScore", round2(Math.max(0, 100 - score) / 2.0));
        return result;
    }

    /**
     * 重算所有客户评分 (流失风险 / LTV / 活跃度)。
     *
     * @return 重算汇总 Map
     */
    @Transactional
    public Map<String, Object> recalculateAllScores() {
        List<ScrmCustomerLifecycleEntity> all = loadAllLifecycles();
        double totalRisk = 0;
        double totalLtv = 0;
        double totalEngagement = 0;
        int highRisk = 0;
        int churned = 0;
        Set<Long> churnStageIds = churnStageIds();
        for (ScrmCustomerLifecycleEntity e : all) {
            double risk = calculateChurnRisk(e);
            totalRisk += risk;
            totalLtv += calculateLTV(e);
            totalEngagement += calculateEngagementScore(e);
            if (risk >= AT_RISK_THRESHOLD) {
                highRisk++;
            }
            if (e.getCurrentStageId() != null && churnStageIds.contains(e.getCurrentStageId())) {
                churned++;
            }
        }
        int count = all.size();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCustomers", count);
        result.put("avgChurnRisk", count > 0 ? round2(totalRisk / count) : 0.0);
        result.put("avgLtv", count > 0 ? round2(totalLtv / count) : 0.0);
        result.put("avgEngagement", count > 0 ? round2(totalEngagement / count) : 0.0);
        result.put("highRiskCount", highRisk);
        result.put("churnedCount", churned);
        return result;
    }

    /**
     * 生命周期时间线 (最近 N 月的转换记录)。
     *
     * @param customerId 客户 ID
     * @param months     月数
     * @return 时间线列表
     * @throws ScrmException 客户 ID 非法
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getLifecycleTimeline(Long customerId, int months) throws ScrmException {
        if (customerId == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        if (months <= 0) {
            months = DEFAULT_TREND_MONTHS;
        }
        LocalDateTime start = LocalDateTime.now().minusMonths(months);
        List<ScrmLifecycleHistoryEntity> histories = historyRepository
                .findByCustomerIdOrderByTransitionTimeDesc(customerId);
        List<Map<String, Object>> timeline = new ArrayList<>();
        for (ScrmLifecycleHistoryEntity h : histories) {
            if (h.getTransitionTime() == null || h.getTransitionTime().isBefore(start)) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("transitionId", h.getId());
            m.put("transitionTime", h.getTransitionTime());
            m.put("fromStage", h.getFromStageCode());
            m.put("toStage", h.getToStageCode());
            m.put("transitionType", h.getTransitionType());
            m.put("trigger", h.getTriggerEvent());
            m.put("durationInPreviousStage", h.getDurationInPreviousStage());
            timeline.add(m);
        }
        return timeline;
    }

    /**
     * 下一步最佳行动 (基于当前阶段与流失风险)。
     *
     * @param customerId 客户 ID
     * @return 行动建议 Map
     * @throws ScrmException 客户生命周期不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getNextBestAction(Long customerId) throws ScrmException {
        ScrmCustomerLifecycleEntity lifecycle = scrmLifecycleService.getCustomerLifecycle(customerId);
        double risk = calculateChurnRisk(lifecycle);
        String action;
        String offer;
        if (risk >= 80) {
            action = "立即挽回触达: 专属优惠 + 1对1回访";
            offer = "高价值挽回券";
        } else if (risk >= 60) {
            action = "流失预警干预: 发送关怀消息 + 优惠券";
            offer = "限时优惠券";
        } else if (lifecycle.getStageHistoryCount() != null && lifecycle.getStageHistoryCount() == 0) {
            action = "新客首单转化: 引导首次购买";
            offer = "首单立减";
        } else {
            action = "保持互动: 推荐相关内容 + 会员权益";
            offer = "会员积分加倍";
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customerId", customerId);
        result.put("currentStage", lifecycle.getCurrentStageCode());
        result.put("churnRiskScore", round2(risk));
        result.put("nextBestAction", action);
        result.put("nextBestOffer", offer);
        return result;
    }

    /**
     * 客户旅程 (按时间升序的转换历史)。
     *
     * @param customerId 客户 ID
     * @return 旅程节点列表
     * @throws ScrmException 客户 ID 非法
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCustomerJourney(Long customerId) throws ScrmException {
        if (customerId == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        List<ScrmLifecycleHistoryEntity> histories = historyRepository
                .findByCustomerIdOrderByTransitionTimeDesc(customerId);
        histories.sort(Comparator.comparing(ScrmLifecycleHistoryEntity::getTransitionTime,
                Comparator.nullsLast(Comparator.naturalOrder())));
        List<Map<String, Object>> journey = new ArrayList<>();
        for (ScrmLifecycleHistoryEntity h : histories) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("transitionId", h.getId());
            m.put("time", h.getTransitionTime());
            m.put("fromStage", h.getFromStageCode());
            m.put("toStage", h.getToStageCode());
            m.put("trigger", h.getTriggerEvent());
            m.put("type", h.getTransitionType());
            m.put("durationDays", h.getDurationInPreviousStage());
            m.put("description", h.getTriggerDescription());
            journey.add(m);
        }
        return journey;
    }

    /**
     * 生命周期统计概览。
     *
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getLifecycleOverview() {
        return scrmLifecycleService.getLifecycleStats();
    }

    /**
     * 生命周期统计 (各阶段客户数 / 分布 / 平均停留)。
     *
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getLifecycleStats() {
        return scrmLifecycleService.getLifecycleStats();
    }

    /**
     * 阶段分布统计。
     *
     * @return 阶段分布列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getStageDistribution() {
        Map<String, Object> stats = scrmLifecycleService.getLifecycleStats();
        Object stageStats = stats.get("stageStats");
        return stageStats instanceof List ? (List<Map<String, Object>>) stageStats : new ArrayList<>();
    }

    /**
     * 价值分层分布 (派生指标, 内存计算)。
     *
     * @return 价值分层分布 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getValueSegmentDistribution() {
        List<ScrmCustomerLifecycleEntity> all = loadAllLifecycles();
        Map<String, Long> dist = all.stream()
                .collect(Collectors.groupingBy(this::computeValueSegment, Collectors.counting()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", all.size());
        result.put("distribution", dist);
        return result;
    }

    /**
     * 风险等级分布 (派生指标, 内存计算)。
     *
     * @return 风险等级分布 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getRiskLevelDistribution() {
        List<ScrmCustomerLifecycleEntity> all = loadAllLifecycles();
        Map<String, Long> dist = all.stream()
                .collect(Collectors.groupingBy(e -> computeRiskLevel(calculateChurnRisk(e)), Collectors.counting()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", all.size());
        result.put("distribution", dist);
        return result;
    }

    /**
     * 整体流失率 (流失客户数 / 总客户数)。
     *
     * @return 流失率统计 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getChurnRate() {
        List<ScrmCustomerLifecycleEntity> all = loadAllLifecycles();
        Set<Long> churnStageIds = churnStageIds();
        long churned = all.stream()
                .filter(e -> e.getCurrentStageId() != null && churnStageIds.contains(e.getCurrentStageId())).count();
        int total = all.size();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCustomers", total);
        result.put("churnedCustomers", churned);
        result.put("churnRate", total > 0 ? round2(churned * 1.0 / total) : 0.0);
        return result;
    }

    /**
     * 整体留存率 (1 - 流失率)。
     *
     * @return 留存率统计 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getRetentionRate() {
        Map<String, Object> churn = getChurnRate();
        double churnRate = churn.get("churnRate") instanceof Number
                ? ((Number) churn.get("churnRate")).doubleValue()
                : 0.0;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCustomers", churn.get("totalCustomers"));
        result.put("retainedCustomers",
                ((Number) churn.get("totalCustomers")).longValue()
                        - ((Number) churn.get("churnedCustomers")).longValue());
        result.put("retentionRate", round2(1.0 - churnRate));
        return result;
    }

    /**
     * 平均生命周期时长 (基于转换历史的平均停留天数)。
     *
     * @return 平均周期统计 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getAverageLifecycleDuration() {
        List<ScrmLifecycleHistoryEntity> all = historyRepository.findAll(
                (root, query, cb) -> cb.and());
        double avg = all.stream()
                .mapToInt(h -> h.getDurationInPreviousStage() != null ? h.getDurationInPreviousStage() : 0)
                .average().orElse(0.0);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalTransitions", all.size());
        result.put("averageDurationDays", round2(avg));
        return result;
    }

    /**
     * 各阶段转化率。
     *
     * @return 阶段转化率列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getStageConversionRates() {
        List<ScrmLifecycleStageEntity> stages = stageRepository.findAllByOrderByStageOrderAsc();
        List<Map<String, Object>> rates = new ArrayList<>();
        for (ScrmLifecycleStageEntity s : stages) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("stageId", s.getId());
            m.put("stageName", s.getStageName());
            m.put("stageCode", s.getStageCode());
            m.put("conversionRate", s.getConversionRate() != null ? s.getConversionRate() : 0.0);
            m.put("avgDurationDays", s.getAvgDurationDays() != null ? s.getAvgDurationDays() : 0.0);
            rates.add(m);
        }
        return rates;
    }

    /**
     * 获客渠道统计 (实体未承载渠道字段, 返回空结构占位)。
     *
     * @return 渠道统计 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getAcquisitionChannelStats() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("channels", new ArrayList<>());
        result.put("totalCustomers", customerLifecycleRepository.count());
        result.put("message", "当前生命周期实体未承载获客渠道字段, 暂无渠道分布数据");
        return result;
    }

    /**
     * 生命周期趋势 (最近 N 月每月转换数)。
     *
     * @param months 月数
     * @return 趋势数据列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getLifecycleTrend(int months) {
        if (months <= 0) {
            months = DEFAULT_TREND_MONTHS;
        }
        LocalDateTime start = LocalDateTime.now().minusMonths(months);
        List<ScrmLifecycleHistoryEntity> all = historyRepository.findAll(
                (root, query, cb) -> cb.and(
                        cb.greaterThanOrEqualTo(root.get("transitionTime"), start)));
        Map<String, Long> monthly = new LinkedHashMap<>();
        for (ScrmLifecycleHistoryEntity h : all) {
            if (h.getTransitionTime() == null) {
                continue;
            }
            String key = h.getTransitionTime().getYear() + "-"
                    + String.format("%02d", h.getTransitionTime().getMonthValue());
            monthly.merge(key, 1L, Long::sum);
        }
        List<Map<String, Object>> trend = new ArrayList<>();
        for (Map.Entry<String, Long> e : monthly.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("month", e.getKey());
            m.put("transitionCount", e.getValue());
            trend.add(m);
        }
        return trend;
    }

    /**
     * 计算流失风险 (0-100): 基于停留天数 / 超期 / 转换频率。
     *
     * @param lifecycle 客户生命周期
     * @return 流失风险评分
     */
    double calculateChurnRisk(ScrmCustomerLifecycleEntity lifecycle) {
        if (lifecycle == null) {
            return 0.0;
        }
        double risk = 0.0;
        ScrmLifecycleStageEntity stage = lifecycle.getCurrentStageId() == null ? null
                : stageRepository.findById(lifecycle.getCurrentStageId()).orElse(null);
        int daysInStage = lifecycle.getDurationInStageDays() != null ? lifecycle.getDurationInStageDays() : 0;
        if (lifecycle.getEnteredCurrentStageAt() != null) {
            daysInStage = (int) ChronoUnit.DAYS.between(lifecycle.getEnteredCurrentStageAt(), LocalDateTime.now());
        }
        if (stage != null && stage.getTargetDurationDays() != null && stage.getTargetDurationDays() > 0) {
            int target = stage.getTargetDurationDays();
            double overdueRatio = Math.max(0, daysInStage - target) * 1.0 / target;
            risk += Math.min(overdueRatio * 50, 50);
        } else {
            risk += Math.min(daysInStage * 0.5, 30);
        }
        if (Boolean.TRUE.equals(lifecycle.getIsOverdue())) {
            int overdue = lifecycle.getOverdueDays() != null ? lifecycle.getOverdueDays() : 0;
            risk += Math.min(overdue, 20);
        }
        int historyCount = lifecycle.getStageHistoryCount() != null ? lifecycle.getStageHistoryCount() : 0;
        risk += Math.max(0, 20 - historyCount * 2.0);
        if (stage != null && Boolean.TRUE.equals(stage.getIsChurnStage())) {
            risk += 30;
        }
        return Math.max(0, Math.min(risk, MAX_RISK));
    }

    /**
     * 计算 LTV 代理值: 转换次数 × 阶段价值基数 + 阶段加分。
     *
     * @param lifecycle 客户生命周期
     * @return LTV 估算值
     */
    private double calculateLTV(ScrmCustomerLifecycleEntity lifecycle) {
        if (lifecycle == null) {
            return 0.0;
        }
        int historyCount = lifecycle.getStageHistoryCount() != null ? lifecycle.getStageHistoryCount() : 0;
        double ltv = historyCount * 200.0;
        ScrmLifecycleStageEntity stage = lifecycle.getCurrentStageId() == null ? null
                : stageRepository.findById(lifecycle.getCurrentStageId()).orElse(null);
        if (stage != null) {
            String category = stage.getStageCategory();
            if ("RETENTION".equals(category)) {
                ltv += 500;
            } else if ("ENGAGEMENT".equals(category)) {
                ltv += 300;
            } else if ("ACQUISITION".equals(category)) {
                ltv += 100;
            }
            if (Boolean.TRUE.equals(stage.getIsChurnStage())) {
                ltv *= 0.3;
            }
        }
        return ltv;
    }

    /**
     * 计算活跃度评分 (0-100): 基于转换频率与最近活跃。
     *
     * @param lifecycle 客户生命周期
     * @return 活跃度评分
     */
    private double calculateEngagementScore(ScrmCustomerLifecycleEntity lifecycle) {
        if (lifecycle == null) {
            return 0.0;
        }
        int historyCount = lifecycle.getStageHistoryCount() != null ? lifecycle.getStageHistoryCount() : 0;
        double score = Math.min(historyCount * 10.0, 60);
        if (lifecycle.getEnteredCurrentStageAt() != null) {
            long daysSince = ChronoUnit.DAYS.between(lifecycle.getEnteredCurrentStageAt(), LocalDateTime.now());
            if (daysSince <= 7) {
                score += 40;
            } else if (daysSince <= 30) {
                score += 30 - (daysSince - 7);
            } else if (daysSince <= 90) {
                score += Math.max(0, 10 - (daysSince - 30) / 6.0);
            }
        }
        return Math.max(0, Math.min(score, MAX_RISK));
    }

    /**
     * 计算价值分层 (派生指标)。
     *
     * @param lifecycle 客户生命周期
     * @return 价值分层
     */
    String computeValueSegment(ScrmCustomerLifecycleEntity lifecycle) {
        if (lifecycle == null) {
            return "STANDARD";
        }
        ScrmLifecycleStageEntity stage = lifecycle.getCurrentStageId() == null ? null
                : stageRepository.findById(lifecycle.getCurrentStageId()).orElse(null);
        if (stage != null && Boolean.TRUE.equals(stage.getIsChurnStage())) {
            return "CHURNED";
        }
        double risk = calculateChurnRisk(lifecycle);
        if (risk >= 70) {
            return "AT_RISK";
        }
        int historyCount = lifecycle.getStageHistoryCount() != null ? lifecycle.getStageHistoryCount() : 0;
        double ltv = calculateLTV(lifecycle);
        if (ltv >= 1500 && historyCount >= 3) {
            return "VIP";
        }
        if (ltv >= 800 && historyCount >= 2) {
            return "HIGH_VALUE";
        }
        if (historyCount == 0) {
            return "LOW_VALUE";
        }
        return "STANDARD";
    }

    /**
     * 计算风险等级 (基于流失风险)。
     *
     * @param churnRisk 流失风险
     * @return 风险等级
     */
    String computeRiskLevel(double churnRisk) {
        if (churnRisk >= 80) {
            return "CRITICAL";
        }
        if (churnRisk >= 60) {
            return "HIGH";
        }
        if (churnRisk >= 40) {
            return "MEDIUM";
        }
        return "LOW";
    }

    /**
     * 加载当前账号全部客户生命周期。
     *
     * @return 生命周期列表
     */
    List<ScrmCustomerLifecycleEntity> loadAllLifecycles() {
        return customerLifecycleRepository.findAll(
                (root, query, cb) -> cb.and());
    }

    /**
     * 获取当前账号流失阶段 ID 集合。
     *
     * @return 流失阶段 ID 集合
     */
    Set<Long> churnStageIds() {
        return stageRepository.findAllByOrderByStageOrderAsc().stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsChurnStage()))
                .map(ScrmLifecycleStageEntity::getId)
                .collect(Collectors.toSet());
    }

    /**
     * 保留两位小数。
     *
     * @param v 原始值
     * @return 四舍五入值
     */
    double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
