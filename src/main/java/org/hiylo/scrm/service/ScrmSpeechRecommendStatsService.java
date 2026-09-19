/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSpeechRecommendStatsService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

package org.hiylo.scrm.service;


import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.hiylo.scrm.entity.ScrmSalesSpeechEntity;
import org.hiylo.scrm.entity.ScrmSpeechRecommendationEntity;
import org.hiylo.scrm.entity.ScrmSpeechScenarioEntity;
import org.hiylo.scrm.repository.ScrmSalesSpeechRepository;
import org.hiylo.scrm.repository.ScrmSpeechRecommendationRepository;
import org.hiylo.scrm.repository.ScrmSpeechScenarioRepository;

import static org.hiylo.scrm.service.ScrmSpeechRecommendMatchService.MAX_RECOMMEND_LIMIT;
import static org.hiylo.scrm.service.ScrmSpeechRecommendMatchService.VALID_OUTCOMES;
import static org.hiylo.scrm.service.ScrmSpeechService.VALID_SPEECH_TYPES;

/**
 * 话术推荐统计与建议兄弟服务。
 * <p>
 * 承载话术 / 场景 / 推荐统计、高绩效话术与改进建议。
 * 作为 {@link ScrmSpeechRecommendService} 的统计子域拆分产物, 由门面注入并委托调用。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmSpeechRecommendStatsService {

    // ==================== 依赖注入 ====================

    /** 话术数据访问层 */
    private final ScrmSalesSpeechRepository speechRepository;

    /** 场景数据访问层 */
    private final ScrmSpeechScenarioRepository scenarioRepository;

    /** 推荐记录数据访问层 */
    private final ScrmSpeechRecommendationRepository recommendationRepository;

    /**
     * 话术统计: 总数 / 各类型 / 使用率 / 成功率。
     *
     * @param startTime 创建时间起始 (可空)
     * @param endTime   创建时间截止 (可空)
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSpeechStats(LocalDateTime startTime, LocalDateTime endTime) {
        List<ScrmSalesSpeechEntity> speeches = speechRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        });
        Map<String, Long> typeCount = new LinkedHashMap<>();
        for (String t : VALID_SPEECH_TYPES) {
            typeCount.put(t, 0L);
        }
        long usageSum = 0;
        long successSum = 0;
        long enabledCount = 0;
        long verifiedCount = 0;
        long recommendedCount = 0;
        for (ScrmSalesSpeechEntity s : speeches) {
            if (s.getSpeechType() != null) {
                typeCount.merge(s.getSpeechType(), 1L, Long::sum);
            }
            if (s.getUsageCount() != null) {
                usageSum += s.getUsageCount();
            }
            if (s.getSuccessCount() != null) {
                successSum += s.getSuccessCount();
            }
            if (Boolean.TRUE.equals(s.getEnabled())) {
                enabledCount++;
            }
            if (Boolean.TRUE.equals(s.getIsVerified())) {
                verifiedCount++;
            }
            if (Boolean.TRUE.equals(s.getIsRecommended())) {
                recommendedCount++;
            }
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", (long) speeches.size());
        stats.put("byType", typeCount);
        stats.put("enabled", enabledCount);
        stats.put("verified", verifiedCount);
        stats.put("recommended", recommendedCount);
        stats.put("totalUsage", usageSum);
        stats.put("totalSuccess", successSum);
        stats.put("successRate", usageSum > 0 ? Math.round(successSum * 100.0 / usageSum * 100d) / 100d : 0.0);
        return stats;
    }

    /**
     * 场景统计: 各场景使用 / 成功率。
     *
     * @param startTime 创建时间起始 (可空)
     * @param endTime   创建时间截止 (可空)
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getScenarioStats(LocalDateTime startTime, LocalDateTime endTime) {
        List<ScrmSpeechScenarioEntity> scenarios = scenarioRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        });
        List<Map<String, Object>> scenarioList = new ArrayList<>();
        long totalUsage = 0;
        long totalSuccess = 0;
        for (ScrmSpeechScenarioEntity s : scenarios) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("scenarioId", s.getId());
            m.put("scenarioName", s.getScenarioName());
            m.put("scenarioCode", s.getScenarioCode());
            m.put("scenarioCategory", s.getScenarioCategory());
            m.put("speechCount", s.getSpeechCount() != null ? s.getSpeechCount() : 0);
            m.put("usageCount", s.getUsageCount() != null ? s.getUsageCount() : 0);
            m.put("successRate", s.getSuccessRate() != null ? s.getSuccessRate() : 0.0);
            m.put("avgRating", s.getAvgRating() != null ? s.getAvgRating() : 0.0);
            m.put("enabled", s.getEnabled());
            scenarioList.add(m);
            if (s.getUsageCount() != null) {
                totalUsage += s.getUsageCount();
            }
            // 估算成功次数: usageCount * successRate
            if (s.getUsageCount() != null && s.getSuccessRate() != null) {
                totalSuccess += Math.round(s.getUsageCount() * s.getSuccessRate());
            }
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", (long) scenarios.size());
        stats.put("scenarios", scenarioList);
        stats.put("totalUsage", totalUsage);
        stats.put("estimatedSuccess", totalSuccess);
        stats.put("estimatedSuccessRate", totalUsage > 0
                ? Math.round(totalSuccess * 100.0 / totalUsage * 100d) / 100d : 0.0);
        return stats;
    }

    /**
     * 推荐统计: 推荐数 / 采纳率 / 反馈率。
     *
     * @param startTime 推荐时间起始 (可空)
     * @param endTime   推荐时间截止 (可空)
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getRecommendationStats(LocalDateTime startTime, LocalDateTime endTime) {
        List<ScrmSpeechRecommendationEntity> recs = recommendationRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("recommendedAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("recommendedAt"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        });
        long adopted = 0;
        long feedbackCount = 0;
        long positiveCount = 0;
        long negativeCount = 0;
        long successCount = 0;
        Map<String, Long> outcomeCount = new LinkedHashMap<>();
        for (String o : VALID_OUTCOMES) {
            outcomeCount.put(o, 0L);
        }
        for (ScrmSpeechRecommendationEntity r : recs) {
            if (r.getSelectedSpeechId() != null) {
                adopted++;
            }
            if (r.getFeedback() != null) {
                feedbackCount++;
                if ("POSITIVE".equals(r.getFeedback())) {
                    positiveCount++;
                } else if ("NEGATIVE".equals(r.getFeedback())) {
                    negativeCount++;
                }
            }
            if (r.getOutcome() != null) {
                outcomeCount.merge(r.getOutcome(), 1L, Long::sum);
                if ("SUCCESS".equals(r.getOutcome()) || "PARTIAL".equals(r.getOutcome())) {
                    successCount++;
                }
            }
        }
        long total = recs.size();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("adopted", adopted);
        stats.put("adoptionRate", total > 0 ? Math.round(adopted * 100.0 / total * 100d) / 100d : 0.0);
        stats.put("feedbackCount", feedbackCount);
        stats.put("feedbackRate", total > 0 ? Math.round(feedbackCount * 100.0 / total * 100d) / 100d : 0.0);
        stats.put("positiveFeedback", positiveCount);
        stats.put("negativeFeedback", negativeCount);
        stats.put("byOutcome", outcomeCount);
        stats.put("successCount", successCount);
        stats.put("successRate", total > 0 ? Math.round(successCount * 100.0 / total * 100d) / 100d : 0.0);
        return stats;
    }

    /**
     * 高绩效话术 (按 usageCount * successRate 综合排序)。
     *
     * @param limit 返回条数
     * @return 话术列表
     */
    @Transactional(readOnly = true)
    public List<ScrmSalesSpeechEntity> getTopPerformingSpeeches(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_RECOMMEND_LIMIT));
        List<ScrmSalesSpeechEntity> all = speechRepository.findAll((root, query, cb) ->
                cb.equal(root.get("enabled"), true));
        return all.stream()
                .sorted((a, b) -> {
                    double scoreA = performanceScore(a);
                    double scoreB = performanceScore(b);
                    return Double.compare(scoreB, scoreA);
                })
                .limit(safeLimit)
                .collect(Collectors.toList());
    }

    /**
     * 改进建议 (低评分话术)。
     * <p>筛选条件: usageCount ≥ 5 且评分 < 3.0 的话术, 按评分升序返回。</p>
     *
     * @return 改进建议 Map 列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getImprovementSuggestions() {
        List<ScrmSalesSpeechEntity> all = speechRepository.findAll((root, query, cb) ->
                cb.equal(root.get("enabled"), true));
        return all.stream()
                .filter(s -> s.getUsageCount() != null && s.getUsageCount() >= 5)
                .filter(s -> s.getRating() != null && s.getRating() < 3.0)
                .sorted((a, b) -> Double.compare(
                        a.getRating() != null ? a.getRating() : 0.0,
                        b.getRating() != null ? b.getRating() : 0.0))
                .limit(MAX_RECOMMEND_LIMIT)
                .map(this::buildSuggestionMap)
                .collect(Collectors.toList());
    }

    /**
     * 计算话术绩效分数 (用于高绩效排序)。
     *
     * @param speech 话术实体
     * @return 绩效分数
     */
    private double performanceScore(ScrmSalesSpeechEntity speech) {
        int usageCount = speech.getUsageCount() != null ? speech.getUsageCount() : 0;
        int successCount = speech.getSuccessCount() != null ? speech.getSuccessCount() : 0;
        double successRate = usageCount > 0 ? successCount * 1.0 / usageCount : 0.0;
        double rating = speech.getRating() != null ? speech.getRating() : 0.0;
        return usageCount * successRate + rating * 2.0;
    }

    /**
     * 构建改进建议 Map。
     *
     * @param speech 话术实体
     * @return 建议 Map
     */
    private Map<String, Object> buildSuggestionMap(ScrmSalesSpeechEntity speech) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("speechId", speech.getId());
        m.put("speechTitle", speech.getSpeechTitle());
        m.put("scenarioId", speech.getScenarioId());
        m.put("rating", speech.getRating());
        m.put("usageCount", speech.getUsageCount());
        m.put("successCount", speech.getSuccessCount());
        m.put("feedbackCount", speech.getFeedbackCount());
        m.put("positiveFeedback", speech.getPositiveFeedback());
        m.put("negativeFeedback", speech.getNegativeFeedback());
        int usageCount = speech.getUsageCount() != null ? speech.getUsageCount() : 0;
        int successCount = speech.getSuccessCount() != null ? speech.getSuccessCount() : 0;
        m.put("successRate", usageCount > 0 ? Math.round(successCount * 100.0 / usageCount * 100d) / 100d : 0.0);
        List<String> suggestions = new ArrayList<>();
        if (speech.getRating() != null && speech.getRating() < 2.0) {
            suggestions.add("评分较低, 建议优化话术内容或重新设计");
        } else if (speech.getRating() != null && speech.getRating() < 3.0) {
            suggestions.add("评分有提升空间, 建议调整话术风格或关键词");
        }
        int positiveFeedback = speech.getPositiveFeedback() != null ? speech.getPositiveFeedback() : 0;
        int negativeFeedback = speech.getNegativeFeedback() != null ? speech.getNegativeFeedback() : 0;
        if (negativeFeedback > positiveFeedback) {
            suggestions.add("负面反馈多于正面反馈, 建议暂停使用并重新评估");
        }
        if (usageCount > 0 && successCount * 2 < usageCount) {
            suggestions.add("成功率低于 50%, 建议优化话术或调整适用场景");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("建议持续监控话术表现");
        }
        m.put("suggestions", suggestions);
        return m;
    }

}
