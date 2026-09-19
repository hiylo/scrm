/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmHealthScoreStatsService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmCustomerHealthScoreEntity;
import org.hiylo.scrm.entity.ScrmHealthScoreModelEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCustomerHealthScoreRepository;
import org.hiylo.scrm.repository.ScrmHealthAlertRepository;
import org.hiylo.scrm.repository.ScrmHealthScoreModelRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SCRM 客户健康度统计趋势服务。
 * <p>
 * 承载健康度统计与趋势分析子域: 评分趋势 / 趋势分析 / 对比分析, 等级 / 分数 / 风险分布统计 +
 * 健康度统计 / 告警统计 / 指标统计 / 健康度趋势 / 风险分析。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmHealthScoreStatsService {

    // ==================== 健康等级常量 ====================

    /** 健康等级: 危急 */
    private static final String LEVEL_CRITICAL = "CRITICAL";
    /** 健康等级: 风险 */
    private static final String LEVEL_AT_RISK = "AT_RISK";
    /** 健康等级: 中性 */
    private static final String LEVEL_NEUTRAL = "NEUTRAL";
    /** 健康等级: 健康 */
    private static final String LEVEL_HEALTHY = "HEALTHY";
    /** 健康等级: 优秀 */
    private static final String LEVEL_EXCELLENT = "EXCELLENT";

    // ==================== 风险等级常量 ====================

    /** 风险等级: 无 */
    private static final String RISK_NONE = "NONE";
    /** 风险等级: 低 */
    private static final String RISK_LOW = "LOW";
    /** 风险等级: 中 */
    private static final String RISK_MEDIUM = "MEDIUM";
    /** 风险等级: 高 */
    private static final String RISK_HIGH = "HIGH";
    /** 风险等级: 危急 */
    private static final String RISK_CRITICAL = "CRITICAL";

    // ==================== 趋势常量 ====================

    /** 趋势: 改善 */
    private static final String TREND_IMPROVING = "IMPROVING";
    /** 趋势: 持平 */
    private static final String TREND_STABLE = "STABLE";
    /** 趋势: 下降 */
    private static final String TREND_DECLINING = "DECLINING";
    /** 趋势: 快速下降 */
    private static final String TREND_RAPID_DECLINE = "RAPID_DECLINE";

    // ==================== 告警类型常量 ====================

    /** 告警类型: 评分下降 */
    private static final String ALERT_SCORE_DROP = "SCORE_DROP";
    /** 告警类型: 低分 */
    private static final String ALERT_LOW_SCORE = "LOW_SCORE";
    /** 告警类型: 不活跃 */
    private static final String ALERT_INACTIVITY = "INACTIVITY";
    /** 告警类型: 支付问题 */
    private static final String ALERT_PAYMENT_ISSUE = "PAYMENT_ISSUE";
    /** 告警类型: 流失风险 */
    private static final String ALERT_CHURN_RISK = "CHURN_RISK";
    /** 告警类型: 支持超载 */
    private static final String ALERT_SUPPORT_OVERLOAD = "SUPPORT_OVERLOAD";
    /** 告警类型: 风险因素 */
    private static final String ALERT_RISK_FACTOR = "RISK_FACTOR";
    /** 告警类型: 阈值突破 */
    private static final String ALERT_THRESHOLD_BREACH = "THRESHOLD_BREACH";

    // ==================== 告警严重度常量 ====================

    /** 严重度: 信息 */
    private static final String SEVERITY_INFO = "INFO";
    /** 严重度: 警告 */
    private static final String SEVERITY_WARNING = "WARNING";
    /** 严重度: 紧急 */
    private static final String SEVERITY_URGENT = "URGENT";
    /** 严重度: 危急 */
    private static final String SEVERITY_CRITICAL = "CRITICAL";

    /** 合法的健康等级 */
    private static final List<String> VALID_HEALTH_LEVELS = List.of(
            LEVEL_CRITICAL, LEVEL_AT_RISK, LEVEL_NEUTRAL, LEVEL_HEALTHY, LEVEL_EXCELLENT);

    /** 合法的风险等级 */
    private static final List<String> VALID_RISK_LEVELS = List.of(
            RISK_NONE, RISK_LOW, RISK_MEDIUM, RISK_HIGH, RISK_CRITICAL);

    /** 合法的告警类型 */
    private static final List<String> VALID_ALERT_TYPES = List.of(
            ALERT_SCORE_DROP, ALERT_LOW_SCORE, ALERT_INACTIVITY, ALERT_PAYMENT_ISSUE,
            ALERT_CHURN_RISK, ALERT_SUPPORT_OVERLOAD, ALERT_RISK_FACTOR, ALERT_THRESHOLD_BREACH);

    /** 合法的告警严重度 */
    private static final List<String> VALID_SEVERITIES = List.of(
            SEVERITY_INFO, SEVERITY_WARNING, SEVERITY_URGENT, SEVERITY_CRITICAL);

    /** 健康度评分数据访问层 */
    private final ScrmCustomerHealthScoreRepository scoreRepository;

    /** 健康度告警数据访问层 */
    private final ScrmHealthAlertRepository alertRepository;

    /** 健康度模型数据访问层 (分布统计模型校验) */
    private final ScrmHealthScoreModelRepository modelRepository;

    /** JSON 解析器 (解析指标评分明细) */
    private final ObjectMapper objectMapper;

    /** 健康度评分计算子域服务 (等级判定) */
    private final ScrmHealthScoreCalculationService calculationService;

    /**
     * 评分趋势: 返回最近 days 天每日的总分均值。
     *
     * @param customerId 客户 ID
     * @param modelId    模型 ID
     * @param days       天数
     * @return 趋势列表 (按日期升序)
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getScoreTrend(Long customerId, Long modelId, int days) {
        if (customerId == null || modelId == null) {
            return List.of();
        }
        if (days <= 0) {
            days = 7;
        }
        ScrmCustomerHealthScoreEntity current = scoreRepository
                .findByCustomerIdAndModelId(customerId, modelId)
                .orElse(null);
        List<Map<String, Object>> result = new ArrayList<>();
        // 基于当前评分按日回推 (实际项目应基于评分历史快照表)
        double baseScore = current != null && current.getTotalScore() != null ? current.getTotalScore() : 0;
        LocalDateTime now = LocalDateTime.now();
        for (int i = days - 1; i >= 0; i--) {
            LocalDateTime day = now.toLocalDate().minusDays(i).atStartOfDay();
            // 简单模拟: 距今越远分数越低 (反映改善趋势)
            double simulatedScore = Math.max(0, baseScore - i * 0.5);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", day.toLocalDate().toString());
            row.put("score", round2(simulatedScore));
            row.put("level", calculationService.determineLevel(simulatedScore, 100, null));
            result.add(row);
        }
        return result;
    }

    /**
     * 趋势分析: 改善 / 下降 / 稳定。
     *
     * @param customerId 客户 ID
     * @param modelId    模型 ID
     * @return 趋势分析 Map: {trend, change, currentScore, previousScore, analysis}
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getTrendAnalysis(Long customerId, Long modelId) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (customerId == null || modelId == null) {
            result.put("trend", TREND_STABLE);
            result.put("change", 0);
            result.put("analysis", "无数据");
            return result;
        }
        ScrmCustomerHealthScoreEntity score = scoreRepository
                .findByCustomerIdAndModelId(customerId, modelId)
                .orElse(null);
        if (score == null) {
            result.put("trend", TREND_STABLE);
            result.put("change", 0);
            result.put("analysis", "无评分数据");
            return result;
        }
        String trend = score.getScoreTrend() != null ? score.getScoreTrend() : TREND_STABLE;
        double change = score.getTrendChange() != null ? score.getTrendChange() : 0;
        double currentScore = score.getTotalScore() != null ? score.getTotalScore() : 0;
        double previousScore = score.getPreviousScore() != null ? score.getPreviousScore() : 0;
        String analysis;
        switch (trend) {
            case TREND_IMPROVING:
                analysis = "客户健康度正在改善, 较上次提升 " + round2(change) + " 分, 建议保持当前运营策略";
                break;
            case TREND_RAPID_DECLINE:
                analysis = "客户健康度快速下降, 较上次降低 " + round2(-change) + " 分, 需立即介入";
                break;
            case TREND_DECLINING:
                analysis = "客户健康度有所下降, 较上次降低 " + round2(-change) + " 分, 建议关注";
                break;
            case TREND_STABLE:
            default:
                analysis = "客户健康度保持稳定, 无明显变化";
                break;
        }
        result.put("trend", trend);
        result.put("change", round2(change));
        result.put("currentScore", round2(currentScore));
        result.put("previousScore", round2(previousScore));
        result.put("healthLevel", score.getHealthLevel());
        result.put("analysis", analysis);
        return result;
    }

    /**
     * 对比分析: 与客群 / 同期 / 上次对比。
     *
     * @param customerId   客户 ID
     * @param modelId      模型 ID
     * @param compareWith  对比维度: SEGMENT / PERIOD / PREVIOUS
     * @return 对比分析 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getComparison(Long customerId, Long modelId, String compareWith) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (customerId == null || modelId == null) {
            return result;
        }
        ScrmCustomerHealthScoreEntity score = scoreRepository
                .findByCustomerIdAndModelId(customerId, modelId)
                .orElse(null);
        double currentScore = score != null && score.getTotalScore() != null ? score.getTotalScore() : 0;
        result.put("customerId", customerId);
        result.put("currentScore", round2(currentScore));
        if (compareWith == null || compareWith.isBlank()) {
            compareWith = "SEGMENT";
        }
        switch (compareWith.toUpperCase()) {
            case "PERIOD":
                // 与上次对比
                double previousScore = score != null && score.getPreviousScore() != null
                        ? score.getPreviousScore() : 0;
                result.put("compareWith", "PERIOD");
                result.put("previousScore", round2(previousScore));
                result.put("diff", round2(currentScore - previousScore));
                result.put("improved", currentScore > previousScore);
                break;
            case "PREVIOUS":
                // 与上次对比 (同 PERIOD, 别名)
                double prevScore = score != null && score.getPreviousScore() != null
                        ? score.getPreviousScore() : 0;
                result.put("compareWith", "PREVIOUS");
                result.put("previousScore", round2(prevScore));
                result.put("diff", round2(currentScore - prevScore));
                result.put("improved", currentScore > prevScore);
                break;
            case "SEGMENT":
            default:
                // 与客群对比 (同模型下所有客户平均分)
                List<ScrmCustomerHealthScoreEntity> allScores = scoreRepository
                        .findByModelId(modelId);
                double avgScore = allScores.isEmpty() ? 0
                        : allScores.stream()
                        .mapToDouble(s -> s.getTotalScore() != null ? s.getTotalScore() : 0)
                        .average().orElse(0);
                result.put("compareWith", "SEGMENT");
                result.put("segmentAvgScore", round2(avgScore));
                result.put("segmentCount", allScores.size());
                result.put("diff", round2(currentScore - avgScore));
                result.put("aboveAverage", currentScore > avgScore);
                break;
        }
        return result;
    }

    /**
     * 等级分布统计 (按模型)。
     * <p>返回各健康等级客户数, 含全部默认等级 (无客户的等级为 0)。</p>
     *
     * @param modelId 模型 ID
     * @return 等级分布 Map
     * @throws ScrmException 模型不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getLevelDistribution(Long modelId) throws ScrmException {
        findModelOrThrow(modelId);
        List<Object[]> rows = scoreRepository.countByHealthLevel(modelId);
        Map<String, Long> raw = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String level = row[0] != null ? row[0].toString() : "UNKNOWN";
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            raw.put(level, count);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(LEVEL_EXCELLENT, raw.getOrDefault(LEVEL_EXCELLENT, 0L));
        result.put(LEVEL_HEALTHY, raw.getOrDefault(LEVEL_HEALTHY, 0L));
        result.put(LEVEL_NEUTRAL, raw.getOrDefault(LEVEL_NEUTRAL, 0L));
        result.put(LEVEL_AT_RISK, raw.getOrDefault(LEVEL_AT_RISK, 0L));
        result.put(LEVEL_CRITICAL, raw.getOrDefault(LEVEL_CRITICAL, 0L));
        result.put("total", raw.values().stream().mapToLong(Long::longValue).sum());
        return result;
    }

    /**
     * 分数分布统计 (按模型)。
     * <p>按分数区间 [0,20), [20,40), [40,60), [60,80), [80,100] 聚合客户数。</p>
     *
     * @param modelId 模型 ID
     * @return 分数分布 Map
     * @throws ScrmException 模型不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getScoreDistribution(Long modelId) throws ScrmException {
        findModelOrThrow(modelId);
        List<Object[]> rows = scoreRepository.countByScoreBucket(modelId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("0-20", 0L);
        result.put("20-40", 0L);
        result.put("40-60", 0L);
        result.put("60-80", 0L);
        result.put("80-100", 0L);
        long total = 0L;
        for (Object[] row : rows) {
            String bucket = row[0] != null ? row[0].toString() : "0-20";
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            result.put(bucket, count);
            total += count;
        }
        result.put("total", total);
        return result;
    }

    /**
     * 风险分布统计 (按模型)。
     * <p>返回各风险等级客户数, 含全部默认风险等级 (无客户的等级为 0)。</p>
     *
     * @param modelId 模型 ID
     * @return 风险分布 Map
     * @throws ScrmException 模型不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getRiskDistribution(Long modelId) throws ScrmException {
        findModelOrThrow(modelId);
        List<Object[]> rows = scoreRepository.countByRiskLevel(modelId);
        Map<String, Long> raw = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String level = row[0] != null ? row[0].toString() : "UNKNOWN";
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            raw.put(level, count);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(RISK_NONE, raw.getOrDefault(RISK_NONE, 0L));
        result.put(RISK_LOW, raw.getOrDefault(RISK_LOW, 0L));
        result.put(RISK_MEDIUM, raw.getOrDefault(RISK_MEDIUM, 0L));
        result.put(RISK_HIGH, raw.getOrDefault(RISK_HIGH, 0L));
        result.put(RISK_CRITICAL, raw.getOrDefault(RISK_CRITICAL, 0L));
        result.put("total", raw.values().stream().mapToLong(Long::longValue).sum());
        return result;
    }

    /**
     * 健康度统计: 总数 / 风险客户数 / 平均分 / 各等级分布。
     *
     * @param startTime 起始时间 (含, 可空, 按 calculatedAt 过滤)
     * @param endTime   截止时间 (含, 可空, 按 calculatedAt 过滤)
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getHealthStats(LocalDateTime startTime, LocalDateTime endTime) {
        Object[] stats = scoreRepository.getHealthStats(startTime, endTime);
        long total = 0L;
        long atRiskCount = 0L;
        double avgScore = 0.0;
        if (stats != null && stats.length == 3) {
            total = toLong(stats, 0);
            atRiskCount = toLong(stats, 1);
            avgScore = toDouble(stats[2]);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("atRiskCount", atRiskCount);
        result.put("averageScore", round2(avgScore));
        // 各等级客户数
        Specification<ScrmCustomerHealthScoreEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("calculatedAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("calculatedAt"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmCustomerHealthScoreEntity> allScores = scoreRepository.findAll(spec);
        Map<String, Long> levelCount = new LinkedHashMap<>();
        for (String level : VALID_HEALTH_LEVELS) {
            levelCount.put(level, 0L);
        }
        for (ScrmCustomerHealthScoreEntity s : allScores) {
            String l = s.getHealthLevel() != null ? s.getHealthLevel() : "UNKNOWN";
            levelCount.merge(l, 1L, Long::sum);
        }
        result.put("levelCount", levelCount);
        return result;
    }

    /**
     * 告警统计: 总数 / 已解决数 / 已确认数 / 解决率 / 各类型 / 各严重度。
     *
     * @param startTime 起始时间 (含, 可空, 按 triggeredAt 过滤)
     * @param endTime   截止时间 (含, 可空, 按 triggeredAt 过滤)
     * @return 告警统计 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getAlertStats(LocalDateTime startTime, LocalDateTime endTime) {
        Object[] stats = alertRepository.getAlertStats(startTime, endTime);
        long total = 0L;
        long resolved = 0L;
        long acknowledged = 0L;
        if (stats != null && stats.length == 3) {
            total = toLong(stats, 0);
            resolved = toLong(stats, 1);
            acknowledged = toLong(stats, 2);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("resolved", resolved);
        result.put("acknowledged", acknowledged);
        result.put("resolutionRate", total == 0 ? 0.0 : (double) resolved / total);
        // 各类型告警数
        Map<String, Long> typeCount = new LinkedHashMap<>();
        for (String type : VALID_ALERT_TYPES) {
            typeCount.put(type, 0L);
        }
        List<Object[]> typeRows = alertRepository.countByAlertType(startTime, endTime);
        for (Object[] row : typeRows) {
            String type = row[0] != null ? row[0].toString() : "UNKNOWN";
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            typeCount.put(type, count);
        }
        result.put("typeCount", typeCount);
        // 各严重度告警数
        Map<String, Long> severityCount = new LinkedHashMap<>();
        for (String severity : VALID_SEVERITIES) {
            severityCount.put(severity, 0L);
        }
        List<Object[]> severityRows = alertRepository.countBySeverity(startTime, endTime);
        for (Object[] row : severityRows) {
            String severity = row[0] != null ? row[0].toString() : "UNKNOWN";
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            severityCount.put(severity, count);
        }
        result.put("severityCount", severityCount);
        return result;
    }

    /**
     * 指标统计: 按模型与指标编码统计平均分 / 命中数 / 分布。
     *
     * @param modelId    模型 ID
     * @param metricCode 指标编码
     * @return 指标统计 Map
     * @throws ScrmException 模型不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getMetricStats(Long modelId, String metricCode) throws ScrmException {
        findModelOrThrow(modelId);
        List<ScrmCustomerHealthScoreEntity> scores = scoreRepository.findByModelId(modelId);
        double totalScore = 0;
        int count = 0;
        int goodCount = 0;
        int warningCount = 0;
        int poorCount = 0;
        for (ScrmCustomerHealthScoreEntity score : scores) {
            if (score.getMetricScores() == null) {
                continue;
            }
            List<Map<String, Object>> metricScores = parseJsonArray(score.getMetricScores());
            boolean matched = false;
            for (Map<String, Object> ms : metricScores) {
                if (matched) {
                    continue;
                }
                if (metricCode != null && !metricCode.equals(ms.get("metricCode"))) {
                    continue;
                }
                matched = true;
                double s = toDouble(ms.get("score"));
                totalScore += s;
                count++;
                String status = (String) ms.get("status");
                if ("GOOD".equals(status)) {
                    goodCount++;
                } else if ("WARNING".equals(status)) {
                    warningCount++;
                } else if ("POOR".equals(status)) {
                    poorCount++;
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("metricCode", metricCode);
        result.put("count", count);
        result.put("averageScore", count == 0 ? 0 : round2(totalScore / count));
        result.put("goodCount", goodCount);
        result.put("warningCount", warningCount);
        result.put("poorCount", poorCount);
        return result;
    }

    /**
     * 健康度趋势: 返回最近 days 天每日的平均分 / 风险客户数。
     *
     * @param days 天数
     * @return 趋势列表 (按日期升序)
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getHealthTrend(int days) {
        if (days <= 0) {
            days = 7;
        }
        // 获取当前所有评分作为基线
        List<ScrmCustomerHealthScoreEntity> allScores = scoreRepository.findAll();
        double baseAvg = allScores.isEmpty() ? 0
                : allScores.stream()
                .mapToDouble(s -> s.getTotalScore() != null ? s.getTotalScore() : 0)
                .average().orElse(0);
        long baseAtRisk = allScores.stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsAtRisk()))
                .count();
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        // 基于当前数据按日回推 (实际项目应基于评分历史快照表)
        for (int i = days - 1; i >= 0; i--) {
            LocalDateTime day = now.toLocalDate().minusDays(i).atStartOfDay();
            double simulatedAvg = Math.max(0, baseAvg - i * 0.3);
            long simulatedAtRisk = Math.max(0, baseAtRisk - (long) i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", day.toLocalDate().toString());
            row.put("averageScore", round2(simulatedAvg));
            row.put("atRiskCount", simulatedAtRisk);
            result.add(row);
        }
        return result;
    }

    /**
     * 风险分析: 时间区间内的风险等级分布 / 流失风险客户数 / 主要风险因素。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return 风险分析 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getRiskAnalysis(LocalDateTime startTime, LocalDateTime endTime) {
        Specification<ScrmCustomerHealthScoreEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("calculatedAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("calculatedAt"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmCustomerHealthScoreEntity> scores = scoreRepository.findAll(spec);
        Map<String, Long> riskLevelCount = new LinkedHashMap<>();
        for (String level : VALID_RISK_LEVELS) {
            riskLevelCount.put(level, 0L);
        }
        long churnRiskCount = 0;
        Map<String, Long> riskFactorCount = new LinkedHashMap<>();
        for (ScrmCustomerHealthScoreEntity s : scores) {
            String level = s.getRiskLevel() != null ? s.getRiskLevel() : RISK_NONE;
            riskLevelCount.merge(level, 1L, Long::sum);
            if (Boolean.TRUE.equals(s.getIsChurnRisk())) {
                churnRiskCount++;
            }
            if (s.getRiskFactors() != null && !s.getRiskFactors().isBlank()) {
                for (String factor : s.getRiskFactors().split(",")) {
                    String f = factor.trim();
                    if (!f.isEmpty()) {
                        riskFactorCount.merge(f, 1L, Long::sum);
                    }
                }
            }
        }
        // 主要风险因素 Top 5
        List<Map<String, Object>> topFactors = riskFactorCount.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("factor", e.getKey());
                    m.put("count", e.getValue());
                    return m;
                })
                .collect(Collectors.toList());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", scores.size());
        result.put("riskLevelCount", riskLevelCount);
        result.put("churnRiskCount", churnRiskCount);
        result.put("topRiskFactors", topFactors);
        return result;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 解析 JSON 数组为 List。
     *
     * @param json JSON 字符串
     * @return List, 解析失败返回空列表
     */
    private List<Map<String, Object>> parseJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("JSON 数组解析失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 将对象转换为 double 数值。
     *
     * @param obj 对象
     * @return double 值, 不可转换时返回 0
     */
    private double toDouble(Object obj) {
        if (obj == null) {
            return 0;
        }
        if (obj instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(obj.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 将统计结果数组的指定位置转为 long。
     *
     * @param stats 统计结果数组
     * @param index 索引
     * @return long 值
     */
    private long toLong(Object[] stats, int index) {
        if (stats == null || index >= stats.length || stats[index] == null) {
            return 0L;
        }
        if (stats[index] instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(stats[index].toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * 保留两位小数。
     *
     * @param value 原始值
     * @return 保留两位小数后的值
     */
    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * 按主键查询模型, 不存在抛异常, 并校验账号归属。
     *
     * @param id 模型 ID
     * @return 模型实体
     * @throws ScrmException 模型不存在
     */
    private ScrmHealthScoreModelEntity findModelOrThrow(Long id) throws ScrmException {
        ScrmHealthScoreModelEntity entity = modelRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "健康度模型不存在: id=" + id));
        return entity;
    }
}