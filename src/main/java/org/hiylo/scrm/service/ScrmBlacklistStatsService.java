/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBlacklistStatsService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmBlacklistCheckDto;
import org.hiylo.scrm.dto.ScrmRiskAssessmentDto;
import org.hiylo.scrm.entity.ScrmBlacklistEntity;
import org.hiylo.scrm.entity.ScrmBlacklistRuleEntity;
import org.hiylo.scrm.entity.ScrmRiskEventEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmBlacklistRepository;
import org.hiylo.scrm.repository.ScrmBlacklistRuleRepository;
import org.hiylo.scrm.repository.ScrmRiskEventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SCRM 黑名单统计与风险评估服务。
 * <p>
 * 承载统计与风险评估子域: 名单 / 风险 / 规则表现等多维统计, 风险概览, 以及综合
 * 风险评估、客户与目标风险评分、风险因子与风险历史。部分能力委托
 * {@link ScrmBlacklistManageService} 与 {@link ScrmBlacklistRuleService} 完成。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmBlacklistStatsService {

    // ==================== 风险计算常量 ====================

    /** 风险因子权重 (用于加权计算风险分) */
    private static final Map<String, Double> RISK_FACTOR_WEIGHTS = Map.of(
            "blacklist_hit", 0.40,
            "rule_triggered", 0.25,
            "risk_level", 0.15,
            "frequency", 0.08,
            "amount", 0.07,
            "history", 0.05);
    /** 默认因子权重 (未知名因子) */
    private static final double DEFAULT_FACTOR_WEIGHT = 0.05;

    // ==================== 依赖注入 ====================

    /** 黑名单数据访问层 */
    private final ScrmBlacklistRepository blacklistRepository;
    /** 风控规则数据访问层 */
    private final ScrmBlacklistRuleRepository ruleRepository;
    /** 风险事件数据访问层 */
    private final ScrmRiskEventRepository eventRepository;
    /** 黑名单管理子域服务 (名单检查与共享常量) */
    private final ScrmBlacklistManageService blacklistService;
    /** 风控规则子域服务 (全量规则评估) */
    private final ScrmBlacklistRuleService ruleService;

    /**
     * 各名单类型活跃数统计。
     *
     * @return 统计 Map {BLACKLIST, GRAYLIST, WHITELIST, WATCHLIST, total}
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getActiveCount() {
        Map<String, Long> counts = new LinkedHashMap<>();
        long total = 0L;
        for (String listType : ScrmBlacklistManageService.VALID_LIST_TYPES) {
            long c = blacklistRepository.countByListTypeAndStatus(listType, "ACTIVE");
            counts.put(listType, c);
            total += c;
        }
        counts.put("total", total);
        return counts;
    }

    /**
     * 名单统计 (各类型 / 各目标 / 各等级)。
     *
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getBlacklistStats() {
        List<ScrmBlacklistEntity> all = blacklistRepository.findAll(
                (root, query, cb) -> cb.and());
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", all.size());
        // 各名单类型
        Map<String, Long> byListType = all.stream()
                .collect(Collectors.groupingBy(ScrmBlacklistEntity::getListType, Collectors.counting()));
        Map<String, Long> listTypeFull = new LinkedHashMap<>();
        for (String lt : ScrmBlacklistManageService.VALID_LIST_TYPES) {
            listTypeFull.put(lt, byListType.getOrDefault(lt, 0L));
        }
        stats.put("listTypeCount", listTypeFull);
        // 各目标类型
        Map<String, Long> byTargetType = all.stream()
                .collect(Collectors.groupingBy(ScrmBlacklistEntity::getTargetType, Collectors.counting()));
        stats.put("targetTypeCount", byTargetType);
        // 各风险等级
        Map<String, Long> byRiskLevel = all.stream()
                .filter(b -> b.getRiskLevel() != null)
                .collect(Collectors.groupingBy(ScrmBlacklistEntity::getRiskLevel, Collectors.counting()));
        Map<String, Long> riskLevelFull = new LinkedHashMap<>();
        for (String lv : ScrmBlacklistManageService.VALID_RISK_LEVELS) {
            riskLevelFull.put(lv, byRiskLevel.getOrDefault(lv, 0L));
        }
        stats.put("riskLevelCount", riskLevelFull);
        // 各状态
        Map<String, Long> byStatus = all.stream()
                .filter(b -> b.getStatus() != null)
                .collect(Collectors.groupingBy(ScrmBlacklistEntity::getStatus, Collectors.counting()));
        stats.put("statusCount", byStatus);
        return stats;
    }

    /**
     * 风险统计 (事件数 / 各类别 / 各等级 / 解决率)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getRiskStats(LocalDateTime startTime, LocalDateTime endTime) {
        List<ScrmRiskEventEntity> all = eventRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("triggerTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("triggerTime"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        });
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", all.size());
        // 各风险类别
        Map<String, Long> byCategory = all.stream()
                .filter(e -> e.getRiskCategory() != null)
                .collect(Collectors.groupingBy(ScrmRiskEventEntity::getRiskCategory, Collectors.counting()));
        stats.put("categoryCount", byCategory);
        // 各风险等级
        Map<String, Long> byLevel = all.stream()
                .filter(e -> e.getRiskLevel() != null)
                .collect(Collectors.groupingBy(ScrmRiskEventEntity::getRiskLevel, Collectors.counting()));
        Map<String, Long> levelFull = new LinkedHashMap<>();
        for (String lv : ScrmBlacklistManageService.VALID_RISK_LEVELS) {
            levelFull.put(lv, byLevel.getOrDefault(lv, 0L));
        }
        stats.put("levelCount", levelFull);
        // 解决率
        long resolved = all.stream().filter(e -> "RESOLVED".equals(e.getStatus())).count();
        double resolutionRate = all.isEmpty() ? 0.0
                : BigDecimal.valueOf(resolved).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(all.size()), 2, RoundingMode.HALF_UP).doubleValue();
        stats.put("resolvedCount", resolved);
        stats.put("resolutionRate", resolutionRate);
        // 误报数
        long falsePositive = all.stream().filter(e -> Boolean.TRUE.equals(e.getIsFalsePositive())).count();
        stats.put("falsePositiveCount", falsePositive);
        return stats;
    }

    /**
     * 规则表现统计 (触发率 / 准确率 / 误报率)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getRulePerformanceStats(LocalDateTime startTime, LocalDateTime endTime) {
        List<ScrmBlacklistRuleEntity> all = ruleRepository.findAll(
                (root, query, cb) -> cb.and());
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalRules", all.size());
        long enabled = all.stream().filter(r -> Boolean.TRUE.equals(r.getEnabled())).count();
        stats.put("enabledRules", enabled);
        long totalTrigger = all.stream()
                .mapToLong(r -> r.getTriggerCount() != null ? r.getTriggerCount() : 0).sum();
        long totalFalsePositive = all.stream()
                .mapToLong(r -> r.getFalsePositiveCount() != null ? r.getFalsePositiveCount() : 0).sum();
        stats.put("totalTriggerCount", totalTrigger);
        stats.put("totalFalsePositiveCount", totalFalsePositive);
        double falsePositiveRate = totalTrigger > 0
                ? BigDecimal.valueOf(totalFalsePositive).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalTrigger), 2, RoundingMode.HALF_UP).doubleValue()
                : 0.0;
        stats.put("falsePositiveRate", falsePositiveRate);
        stats.put("accuracyRate", BigDecimal.valueOf(100 - falsePositiveRate)
                .setScale(2, RoundingMode.HALF_UP).doubleValue());
        // 按规则类型统计触发数
        Map<String, Long> triggerByType = all.stream()
                .filter(r -> r.getRuleType() != null)
                .collect(Collectors.groupingBy(ScrmBlacklistRuleEntity::getRuleType,
                        Collectors.summingLong(r -> r.getTriggerCount() != null ? r.getTriggerCount() : 0)));
        stats.put("triggerByType", triggerByType);
        return stats;
    }

    /**
     * 风险趋势 (按月统计事件数)。
     *
     * @param months 回溯月数
     * @return 趋势结果 Map {month, labels, eventCounts, highRiskCounts}
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getRiskTrend(int months) {
        LocalDateTime startTime = LocalDateTime.now().minusMonths(months);
        List<ScrmRiskEventEntity> events = eventRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.greaterThanOrEqualTo(root.get("triggerTime"), startTime));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
        Map<String, List<ScrmRiskEventEntity>> byMonth = events.stream()
                .collect(Collectors.groupingBy(e -> e.getTriggerTime().getYear() + "-"
                        + String.format("%02d", e.getTriggerTime().getMonthValue())));
        List<String> labels = new ArrayList<>();
        List<Long> eventCounts = new ArrayList<>();
        List<Long> highRiskCounts = new ArrayList<>();
        for (int i = months - 1; i >= 0; i--) {
            LocalDateTime month = LocalDateTime.now().minusMonths(i);
            String key = month.getYear() + "-" + String.format("%02d", month.getMonthValue());
            List<ScrmRiskEventEntity> monthEvents = byMonth.getOrDefault(key, List.of());
            labels.add(key);
            eventCounts.add((long) monthEvents.size());
            highRiskCounts.add(monthEvents.stream()
                    .filter(e -> ScrmRiskEventService.CRITICAL_LEVELS.contains(e.getRiskLevel())).count());
        }
        Map<String, Object> trend = new LinkedHashMap<>();
        trend.put("labels", labels);
        trend.put("eventCounts", eventCounts);
        trend.put("highRiskCounts", highRiskCounts);
        return trend;
    }

    /**
     * 触发最多的规则 Top N。
     *
     * @param limit 返回条数
     * @return 规则列表 (按 triggerCount DESC)
     */
    @Transactional(readOnly = true)
    public List<ScrmBlacklistRuleEntity> getTopRiskRules(int limit) {
        List<ScrmBlacklistRuleEntity> all = ruleRepository.findAll(
                (root, query, cb) -> cb.and());
        return all.stream()
                .sorted((a, b) -> Integer.compare(
                        b.getTriggerCount() != null ? b.getTriggerCount() : 0,
                        a.getTriggerCount() != null ? a.getTriggerCount() : 0))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 高风险客户 Top N (按事件数)。
     *
     * @param limit 返回条数
     * @return 高风险客户列表 [{customerId, eventCount, highRiskCount}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTopRiskCustomers(int limit) {
        List<ScrmRiskEventEntity> all = eventRepository.findAll(
                (root, query, cb) -> cb.and());
        Map<Long, List<ScrmRiskEventEntity>> byCustomer = all.stream()
                .filter(e -> e.getCustomerId() != null)
                .collect(Collectors.groupingBy(ScrmRiskEventEntity::getCustomerId));
        return byCustomer.entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("customerId", e.getKey());
                    m.put("eventCount", e.getValue().size());
                    m.put("highRiskCount", e.getValue().stream()
                            .filter(ev -> ScrmRiskEventService.CRITICAL_LEVELS.contains(ev.getRiskLevel())).count());
                    return m;
                })
                .sorted((a, b) -> Long.compare((Long) b.get("eventCount"), (Long) a.get("eventCount")))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 误报率统计。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 误报率 Map {totalEvents, falsePositiveCount, falsePositiveRate}
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getFalsePositiveRate(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> riskStats = getRiskStats(startTime, endTime);
        long total = ((Number) riskStats.getOrDefault("total", 0)).longValue();
        long falsePositive = ((Number) riskStats.getOrDefault("falsePositiveCount", 0)).longValue();
        double rate = total > 0
                ? BigDecimal.valueOf(falsePositive).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP).doubleValue()
                : 0.0;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalEvents", total);
        result.put("falsePositiveCount", falsePositive);
        result.put("falsePositiveRate", rate);
        return result;
    }

    /**
     * 平均处理时间 (小时)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 平均处理时间 Map {resolvedCount, averageResolutionHours}
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getAverageResolutionTime(LocalDateTime startTime, LocalDateTime endTime) {
        List<ScrmRiskEventEntity> resolved = eventRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), "RESOLVED"));
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("triggerTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("triggerTime"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        });
        double avg = resolved.stream()
                .filter(e -> e.getTriggerTime() != null && e.getResolvedAt() != null)
                .mapToLong(e -> java.time.Duration.between(e.getTriggerTime(), e.getResolvedAt()).toHours())
                .average()
                .orElse(0.0);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resolvedCount", resolved.size());
        result.put("averageResolutionHours", BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP).doubleValue());
        return result;
    }

    /**
     * 风险概览 (名单统计 + 风险统计 + 规则表现 + 待处理 / 严重事件数)。
     *
     * @return 概览 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getRiskOverview() {
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("blacklistStats", getBlacklistStats());
        overview.put("riskStats", getRiskStats(null, null));
        overview.put("rulePerformance", getRulePerformanceStats(null, null));
        overview.put("openEventCount", eventRepository.countByStatus("OPEN"));
        overview.put("criticalEventCount",
                eventRepository.countByRiskLevel("CRITICAL")
                        + eventRepository.countByRiskLevel("HIGH"));
        overview.put("activeBlacklistCount", getActiveCount().get("total"));
        return overview;
    }

    /**
     * 综合风险评估 (完整实现)。
     * <p>检查名单命中 → 评估所有启用规则 → 加权计算综合风险分 → 返回处置建议
     * (ALLOW / REVIEW / BLOCK)。</p>
     *
     * @param assessmentDto 评估参数
     * @return 评估结果 Map {targetType, targetValue, blacklistHit, triggeredRules, riskScore, riskLevel,
     *         recommendation}
     * @throws ScrmException 参数非法
     */
    @Transactional
    public Map<String, Object> assessRisk(ScrmRiskAssessmentDto assessmentDto) throws ScrmException {
        if (assessmentDto == null || assessmentDto.getTargetType() == null || assessmentDto.getTargetValue() == null) {
            throw ScrmException.badRequest("评估参数不能为空");
        }
        // 1. 检查名单命中
        ScrmBlacklistCheckDto checkDto = new ScrmBlacklistCheckDto();
        checkDto.setTargetType(assessmentDto.getTargetType());
        checkDto.setTargetValue(assessmentDto.getTargetValue());
        Map<String, Object> checkResult = blacklistService.checkBlacklist(checkDto);
        boolean blacklistHit = Boolean.TRUE.equals(checkResult.get("hit"));
        double blacklistScore = blacklistHit ? ScrmBlacklistManageService.MAX_RISK_SCORE
                : ScrmBlacklistManageService.MIN_RISK_SCORE;

        // 2. 评估所有规则
        Map<String, Object> ruleResult = ruleService.evaluateAllRules(assessmentDto);
        int triggeredCount = ((Number) ruleResult.getOrDefault("totalTriggered", 0)).intValue();
        double ruleScore = Math.min(triggeredCount * 25.0, ScrmBlacklistManageService.MAX_RISK_SCORE);

        // 3. 加权计算综合风险分
        Map<String, Double> factors = new LinkedHashMap<>();
        factors.put("blacklist_hit", blacklistScore);
        factors.put("rule_triggered", ruleScore);
        factors.put("risk_level", ScrmBlacklistManageService.RISK_LEVEL_SCORE.getOrDefault(
                (String) ruleResult.getOrDefault("maxSeverity", "LOW"), 25.0));
        if (assessmentDto.getAmount() != null) {
            factors.put("amount", Math.min(assessmentDto.getAmount() / 100.0,
                    ScrmBlacklistManageService.MAX_RISK_SCORE));
        }
        Map<String, Object> scoreResult = calculateRiskScore(factors);
        double riskScore = ((Number) scoreResult.get("score")).doubleValue();
        String riskLevel = scoreToLevel(riskScore);

        // 4. 返回处置建议
        String recommendation;
        if (blacklistHit || riskScore >= 75) {
            recommendation = "BLOCK";
        } else if (riskScore >= 40 || triggeredCount > 0) {
            recommendation = "REVIEW";
        } else {
            recommendation = "ALLOW";
        }

        Map<String, Object> assessment = new LinkedHashMap<>();
        assessment.put("targetType", assessmentDto.getTargetType());
        assessment.put("targetValue", assessmentDto.getTargetValue());
        assessment.put("customerId", assessmentDto.getCustomerId());
        assessment.put("blacklistHit", blacklistHit);
        assessment.put("blacklistMatches", checkResult.get("matches"));
        assessment.put("triggeredRules", ruleResult.get("triggeredRules"));
        assessment.put("totalTriggered", triggeredCount);
        assessment.put("riskScore", BigDecimal.valueOf(riskScore).setScale(2, RoundingMode.HALF_UP).doubleValue());
        assessment.put("riskLevel", riskLevel);
        assessment.put("recommendation", recommendation);
        assessment.put("scoreBreakdown", scoreResult.get("breakdown"));
        return assessment;
    }

    /**
     * 客户风险评分 (基于客户关联名单与事件)。
     *
     * @param customerId 客户 ID
     * @return 风险评分 Map {customerId, riskScore, riskLevel, blacklistCount, eventCount}
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCustomerRiskScore(Long customerId) {
        List<ScrmBlacklistEntity> blacklists = blacklistRepository.findByCustomerId(customerId,
                PageRequest.of(0, 1000, Sort.by(Sort.Direction.DESC, "updateTime"))).getContent();
        long activeBlacklist = blacklists.stream().filter(b -> "ACTIVE".equals(b.getStatus())).count();
        long eventCount = eventRepository.countByCustomerId(customerId);
        double score = Math.min(activeBlacklist * 30 + eventCount * 5, ScrmBlacklistManageService.MAX_RISK_SCORE);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customerId", customerId);
        result.put("riskScore", BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP).doubleValue());
        result.put("riskLevel", scoreToLevel(score));
        result.put("activeBlacklistCount", activeBlacklist);
        result.put("eventCount", eventCount);
        return result;
    }

    /**
     * 目标风险评分 (基于目标关联名单与事件)。
     *
     * @param targetType  目标类型
     * @param targetValue 目标值
     * @return 风险评分 Map {targetType, targetValue, riskScore, riskLevel, blacklistCount, eventCount}
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getTargetRiskScore(String targetType, String targetValue) {
        List<ScrmBlacklistEntity> blacklists = blacklistService.getByTarget(targetType, targetValue);
        long activeBlacklist = blacklists.stream().filter(b -> "ACTIVE".equals(b.getStatus())).count();
        double maxBlacklistScore = blacklists.stream()
                .filter(b -> "ACTIVE".equals(b.getStatus()))
                .mapToDouble(b -> b.getRiskScore() != null ? b.getRiskScore() : 0.0)
                .max().orElse(0.0);
        double score = Math.max(maxBlacklistScore, Math.min(activeBlacklist * 30,
                ScrmBlacklistManageService.MAX_RISK_SCORE));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targetType", targetType);
        result.put("targetValue", targetValue);
        result.put("riskScore", BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP).doubleValue());
        result.put("riskLevel", scoreToLevel(score));
        result.put("activeBlacklistCount", activeBlacklist);
        return result;
    }

    /**
     * 计算风险分 (加权计算 0-100, 完整实现)。
     * <p>每个因子按预定义权重加权求和, 结果归一化到 0-100。返回评分与各因子贡献明细。
     * </p>
     *
     * @param factors 风险因子 Map {因子名: 因子分值(0-100)}
     * @return 评分结果 Map {score, breakdown}
     */
    public Map<String, Object> calculateRiskScore(Map<String, Double> factors) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (factors == null || factors.isEmpty()) {
            result.put("score", 0.0);
            result.put("breakdown", Map.of());
            return result;
        }
        double totalWeight = 0.0;
        double weightedSum = 0.0;
        Map<String, Object> breakdown = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : factors.entrySet()) {
            String factor = entry.getKey();
            double value = clamp(entry.getValue() != null ? entry.getValue() : 0.0);
            double weight = RISK_FACTOR_WEIGHTS.getOrDefault(factor, DEFAULT_FACTOR_WEIGHT);
            double contribution = value * weight;
            weightedSum += contribution;
            totalWeight += weight;
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("value", value);
            f.put("weight", weight);
            f.put("contribution", BigDecimal.valueOf(contribution).setScale(2, RoundingMode.HALF_UP).doubleValue());
            breakdown.put(factor, f);
        }
        double score = totalWeight > 0 ? clamp(weightedSum / totalWeight) : 0.0;
        result.put("score", BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP).doubleValue());
        result.put("breakdown", breakdown);
        return result;
    }

    /**
     * 获取目标风险因子 (名单命中 / 事件数 / 风险等级等)。
     *
     * @param targetType  目标类型
     * @param targetValue 目标值
     * @return 风险因子 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getRiskFactors(String targetType, String targetValue) {
        List<ScrmBlacklistEntity> blacklists = blacklistService.getByTarget(targetType, targetValue);
        long activeBlacklist = blacklists.stream().filter(b -> "ACTIVE".equals(b.getStatus())).count();
        double maxScore = blacklists.stream()
                .filter(b -> "ACTIVE".equals(b.getStatus()))
                .mapToDouble(b -> b.getRiskScore() != null ? b.getRiskScore() : 0.0)
                .max().orElse(0.0);
        String maxLevel = blacklists.stream()
                .filter(b -> "ACTIVE".equals(b.getStatus()))
                .map(ScrmBlacklistEntity::getRiskLevel)
                .max(blacklistService::compareSeverity)
                .orElse("LOW");
        Map<String, Object> factors = new LinkedHashMap<>();
        factors.put("blacklist_hit", activeBlacklist > 0 ? ScrmBlacklistManageService.MAX_RISK_SCORE : 0.0);
        factors.put("risk_level", ScrmBlacklistManageService.RISK_LEVEL_SCORE.getOrDefault(maxLevel, 25.0));
        factors.put("history", maxScore);
        return factors;
    }

    /**
     * 风险历史 (按月聚合客户风险事件)。
     *
     * @param customerId 客户 ID
     * @param months     回溯月数
     * @return 风险历史列表 [{month, eventCount, highRiskCount}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRiskHistory(Long customerId, int months) {
        LocalDateTime startTime = LocalDateTime.now().minusMonths(months);
        List<ScrmRiskEventEntity> events = eventRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("customerId"), customerId));
            predicates.add(cb.greaterThanOrEqualTo(root.get("triggerTime"), startTime));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
        Map<String, List<ScrmRiskEventEntity>> byMonth = events.stream()
                .collect(Collectors.groupingBy(e -> e.getTriggerTime().getYear() + "-"
                        + String.format("%02d", e.getTriggerTime().getMonthValue())));
        List<Map<String, Object>> history = new ArrayList<>();
        for (int i = months - 1; i >= 0; i--) {
            LocalDateTime month = LocalDateTime.now().minusMonths(i);
            String key = month.getYear() + "-" + String.format("%02d", month.getMonthValue());
            List<ScrmRiskEventEntity> monthEvents = byMonth.getOrDefault(key, List.of());
            long highRisk = monthEvents.stream()
                    .filter(e -> ScrmRiskEventService.CRITICAL_LEVELS.contains(e.getRiskLevel()))
                    .count();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("month", key);
            m.put("eventCount", monthEvents.size());
            m.put("highRiskCount", highRisk);
            history.add(m);
        }
        return history;
    }

    // ============================================================
    // 私有辅助方法
    // ============================================================

    /**
     * 评分转等级。
     *
     * @param score 风险评分
     * @return 风险等级
     */
    private String scoreToLevel(double score) {
        if (score >= 75) {
            return "CRITICAL";
        } else if (score >= 50) {
            return "HIGH";
        } else if (score >= 25) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    /**
     * 将评分限制在 0-100。
     *
     * @param value 原始值
     * @return 限制后的值
     */
    private double clamp(double value) {
        return Math.max(ScrmBlacklistManageService.MIN_RISK_SCORE,
                Math.min(ScrmBlacklistManageService.MAX_RISK_SCORE, value));
    }
}