/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmVocStatsService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.entity.ScrmVocTopicEntity;
import org.hiylo.scrm.entity.ScrmVocVoiceEntity;
import org.hiylo.scrm.repository.ScrmVocInsightRepository;
import org.hiylo.scrm.repository.ScrmVocTopicRepository;
import org.hiylo.scrm.repository.ScrmVocVoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SCRM 客户之声 (VoC) 统计子域服务。
 * <p>
 * 承载多维统计能力: 概览 / 情感 / 来源 / 分类 / 趋势 / 解决 / 客户 / 主题。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
public class ScrmVocStatsService {

    /** 合法的情感 */
    private static final List<String> VALID_SENTIMENTS = List.of("POSITIVE", "NEUTRAL", "NEGATIVE", "MIXED");

    /** VoC 声音数据访问层 */
    private final ScrmVocVoiceRepository voiceRepository;
    /** VoC 主题数据访问层 */
    private final ScrmVocTopicRepository topicRepository;
    /** VoC 洞察数据访问层 */
    private final ScrmVocInsightRepository insightRepository;
    /** VoC 声音子域服务 (复用声音时间线查询) */
    private final ScrmVocVoiceService voiceService;
    /** VoC 主题子域服务 (复用数值四舍五入) */
    private final ScrmVocTopicService topicService;

    /**
     * VoC 统计 (总数 / 各状态 / 各情感 / 各优先级)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getVocStats(LocalDateTime startTime, LocalDateTime endTime) {
        List<ScrmVocVoiceEntity> all = findVoicesByTimeRange(startTime, endTime);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", all.size());
        Map<String, Long> byStatus = all.stream()
                .filter(v -> v.getStatus() != null)
                .collect(Collectors.groupingBy(ScrmVocVoiceEntity::getStatus, Collectors.counting()));
        stats.put("statusCount", byStatus);
        Map<String, Long> bySentiment = all.stream()
                .filter(v -> v.getSentiment() != null)
                .collect(Collectors.groupingBy(ScrmVocVoiceEntity::getSentiment, Collectors.counting()));
        Map<String, Long> sentimentFull = new LinkedHashMap<>();
        for (String s : VALID_SENTIMENTS) {
            sentimentFull.put(s, bySentiment.getOrDefault(s, 0L));
        }
        stats.put("sentimentCount", sentimentFull);
        Map<String, Long> byPriority = all.stream()
                .filter(v -> v.getPriority() != null)
                .collect(Collectors.groupingBy(ScrmVocVoiceEntity::getPriority, Collectors.counting()));
        stats.put("priorityCount", byPriority);
        return stats;
    }

    /**
     * 情感分布。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 情感分布 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSentimentDistribution(LocalDateTime startTime, LocalDateTime endTime) {
        List<ScrmVocVoiceEntity> all = findVoicesByTimeRange(startTime, endTime);
        Map<String, Long> bySentiment = all.stream()
                .filter(v -> v.getSentiment() != null)
                .collect(Collectors.groupingBy(ScrmVocVoiceEntity::getSentiment, Collectors.counting()));
        Map<String, Object> result = new LinkedHashMap<>();
        long total = all.size();
        for (String s : VALID_SENTIMENTS) {
            long count = bySentiment.getOrDefault(s, 0L);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("count", count);
            item.put("rate", total > 0 ? topicService.round2(count * 100.0 / total) : 0.0);
            result.put(s, item);
        }
        result.put("total", total);
        return result;
    }

    /**
     * 来源渠道分布。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 来源分布 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSourceDistribution(LocalDateTime startTime, LocalDateTime endTime) {
        List<ScrmVocVoiceEntity> all = findVoicesByTimeRange(startTime, endTime);
        Map<String, Long> bySource = all.stream()
                .filter(v -> v.getSource() != null)
                .collect(Collectors.groupingBy(ScrmVocVoiceEntity::getSource, Collectors.counting()));
        return new LinkedHashMap<>(bySource);
    }

    /**
     * 分类分布。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 分类分布 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCategoryDistribution(LocalDateTime startTime, LocalDateTime endTime) {
        List<ScrmVocVoiceEntity> all = findVoicesByTimeRange(startTime, endTime);
        Map<String, Long> byCategory = all.stream()
                .filter(v -> v.getCategory() != null && !v.getCategory().isBlank())
                .collect(Collectors.groupingBy(ScrmVocVoiceEntity::getCategory, Collectors.counting()));
        return new LinkedHashMap<>(byCategory);
    }

    /**
     * 情感趋势 (按月统计情感均分)。
     *
     * @param months 回溯月数
     * @return 趋势结果 Map {labels, sentimentScores, voiceCounts}
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSentimentTrend(int months) {
        LocalDateTime startTime = LocalDateTime.now().minusMonths(months);
        List<ScrmVocVoiceEntity> voices = voiceRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.greaterThanOrEqualTo(root.get("collectedAt"), startTime));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
        Map<String, List<ScrmVocVoiceEntity>> byMonth = voices.stream()
                .collect(Collectors.groupingBy(v -> v.getCollectedAt().getYear() + "-"
                        + String.format("%02d", v.getCollectedAt().getMonthValue())));
        List<String> labels = new ArrayList<>();
        List<Double> sentimentScores = new ArrayList<>();
        List<Long> voiceCounts = new ArrayList<>();
        for (int i = months - 1; i >= 0; i--) {
            LocalDateTime month = LocalDateTime.now().minusMonths(i);
            String key = month.getYear() + "-" + String.format("%02d", month.getMonthValue());
            List<ScrmVocVoiceEntity> monthVoices = byMonth.getOrDefault(key, List.of());
            labels.add(key);
            sentimentScores.add(monthVoices.stream()
                    .filter(v -> v.getSentimentScore() != null)
                    .mapToDouble(ScrmVocVoiceEntity::getSentimentScore)
                    .average().orElse(0.0));
            voiceCounts.add((long) monthVoices.size());
        }
        Map<String, Object> trend = new LinkedHashMap<>();
        trend.put("labels", labels);
        trend.put("sentimentScores", sentimentScores);
        trend.put("voiceCounts", voiceCounts);
        return trend;
    }

    /**
     * 解决统计 (解决率 / 平均解决时长 / 平均满意度)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 解决统计 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getResolutionStats(LocalDateTime startTime, LocalDateTime endTime) {
        List<ScrmVocVoiceEntity> all = findVoicesByTimeRange(startTime, endTime);
        long resolved = all.stream().filter(v -> "RESOLVED".equals(v.getStatus())
                || "CLOSED".equals(v.getStatus())).count();
        double resolutionRate = all.isEmpty() ? 0.0 : topicService.round2(resolved * 100.0 / all.size());
        Double avgHours = voiceRepository.avgResolutionHours(startTime, endTime);
        Double avgSatisfaction = voiceRepository.avgSatisfaction(startTime, endTime);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", all.size());
        stats.put("resolvedCount", resolved);
        stats.put("resolutionRate", resolutionRate);
        stats.put("averageResolutionHours", avgHours != null ? topicService.round2(avgHours) : 0.0);
        stats.put("averageSatisfaction", avgSatisfaction != null ? topicService.round2(avgSatisfaction) : 0.0);
        return stats;
    }

    /**
     * VoC 趋势 (按月统计声音数与负面数)。
     *
     * @param months 回溯月数
     * @return 趋势结果 Map {labels, voiceCounts, negativeCounts}
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getVocTrend(int months) {
        LocalDateTime startTime = LocalDateTime.now().minusMonths(months);
        List<ScrmVocVoiceEntity> voices = voiceRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.greaterThanOrEqualTo(root.get("collectedAt"), startTime));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
        Map<String, List<ScrmVocVoiceEntity>> byMonth = voices.stream()
                .collect(Collectors.groupingBy(v -> v.getCollectedAt().getYear() + "-"
                        + String.format("%02d", v.getCollectedAt().getMonthValue())));
        List<String> labels = new ArrayList<>();
        List<Long> voiceCounts = new ArrayList<>();
        List<Long> negativeCounts = new ArrayList<>();
        for (int i = months - 1; i >= 0; i--) {
            LocalDateTime month = LocalDateTime.now().minusMonths(i);
            String key = month.getYear() + "-" + String.format("%02d", month.getMonthValue());
            List<ScrmVocVoiceEntity> monthVoices = byMonth.getOrDefault(key, List.of());
            labels.add(key);
            voiceCounts.add((long) monthVoices.size());
            negativeCounts.add(monthVoices.stream().filter(v -> "NEGATIVE".equals(v.getSentiment())).count());
        }
        Map<String, Object> trend = new LinkedHashMap<>();
        trend.put("labels", labels);
        trend.put("voiceCounts", voiceCounts);
        trend.put("negativeCounts", negativeCounts);
        return trend;
    }

    /**
     * 客户 VoC 统计 (声音数 / 各情感 / 各状态 / 平均满意度)。
     *
     * @param customerId 客户 ID
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCustomerVoCStats(Long customerId) {
        List<ScrmVocVoiceEntity> voices = voiceService.getVoiceTimeline(customerId);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("customerId", customerId);
        stats.put("totalVoices", voices.size());
        Map<String, Long> bySentiment = voices.stream()
                .filter(v -> v.getSentiment() != null)
                .collect(Collectors.groupingBy(ScrmVocVoiceEntity::getSentiment, Collectors.counting()));
        stats.put("sentimentCount", bySentiment);
        Map<String, Long> byStatus = voices.stream()
                .filter(v -> v.getStatus() != null)
                .collect(Collectors.groupingBy(ScrmVocVoiceEntity::getStatus, Collectors.counting()));
        stats.put("statusCount", byStatus);
        double avgSatisfaction = voices.stream()
                .filter(v -> v.getCustomerSatisfaction() != null)
                .mapToInt(ScrmVocVoiceEntity::getCustomerSatisfaction)
                .average().orElse(0.0);
        stats.put("averageSatisfaction", topicService.round2(avgSatisfaction));
        long unresolved = voices.stream()
                .filter(v -> !List.of("RESOLVED", "CLOSED", "ARCHIVED", "IGNORED").contains(v.getStatus()))
                .count();
        stats.put("unresolvedCount", unresolved);
        return stats;
    }

    /**
     * 主题统计 (主题数 / 热点数 / 新兴数 / 平均优先级)。
     *
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getTopicStats() {
        List<ScrmVocTopicEntity> all = topicRepository.findAll(
                (root, query, cb) -> cb.and());
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", all.size());
        long enabled = all.stream().filter(t -> Boolean.TRUE.equals(t.getEnabled())).count();
        stats.put("enabled", enabled);
        long hot = all.stream().filter(t -> Boolean.TRUE.equals(t.getIsHotTopic())).count();
        stats.put("hotTopics", hot);
        long emerging = all.stream().filter(t -> Boolean.TRUE.equals(t.getIsEmerging())).count();
        stats.put("emergingTopics", emerging);
        double avgPriority = all.stream()
                .filter(t -> t.getPriorityScore() != null)
                .mapToDouble(ScrmVocTopicEntity::getPriorityScore)
                .average().orElse(0.0);
        stats.put("averagePriorityScore", topicService.round2(avgPriority));
        Map<String, Long> byCategory = all.stream()
                .filter(t -> t.getCategory() != null)
                .collect(Collectors.groupingBy(ScrmVocTopicEntity::getCategory, Collectors.counting()));
        stats.put("categoryCount", byCategory);
        return stats;
    }

    /**
     * VoC 概览 (声音统计 + 解决统计 + 主题统计 + 洞察统计)。
     *
     * @return 概览 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getVocOverview() {
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("voiceStats", getVocStats(null, null));
        overview.put("resolutionStats", getResolutionStats(null, null));
        overview.put("sentimentDistribution", getSentimentDistribution(null, null));
        overview.put("sourceDistribution", getSourceDistribution(null, null));
        overview.put("topicStats", getTopicStats());
        long insightTotal = insightRepository.findAll(
                (root, query, cb) -> cb.and()).size();
        overview.put("insightTotal", insightTotal);
        return overview;
    }

    /**
     * 按时间范围查询声音。
     */
    private List<ScrmVocVoiceEntity> findVoicesByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        return voiceRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("collectedAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("collectedAt"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        });
    }
}
