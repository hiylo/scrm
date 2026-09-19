/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAutoReplyStatsService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmAutoReplyLogEntity;
import org.hiylo.scrm.entity.ScrmAutoReplyRuleEntity;
import org.hiylo.scrm.repository.ScrmAutoReplyLogRepository;
import org.hiylo.scrm.repository.ScrmAutoReplyRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SCRM 自动回复统计服务。
 * <p>
 * 承载回复统计、规则效果统计、响应时间统计与匹配率统计。各统计均聚合指定时间范围内的
 * 回复日志, 时间范围缺省时默认近 7 天。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026/09/19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmAutoReplyStatsService {

    /** 发送状态: 成功 */
    private static final String STATUS_SENT = "SENT";
    /** 发送状态: 失败 */
    private static final String STATUS_FAILED = "FAILED";
    /** 发送状态: 跳过 */
    private static final String STATUS_SKIPPED = "SKIPPED";

    /** 自动回复日志数据访问层 */
    private final ScrmAutoReplyLogRepository logRepository;

    /** 自动回复规则数据访问层 */
    private final ScrmAutoReplyRuleRepository ruleRepository;

    /**
     * 回复统计。
     * <p>聚合指定时间范围内的回复日志, 统计总触发次数、各规则类型触发次数、各渠道触发次数与发送成功率。</p>
     *
     * @param startTime 起始时间 (含, 可空, 默认近 7 天)
     * @param endTime   截止时间 (含, 可空, 默认当前时间)
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getReplyStats(LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime end = endTime != null ? endTime : LocalDateTime.now();
        LocalDateTime start = startTime != null ? startTime : end.minusDays(7);
        List<ScrmAutoReplyLogEntity> logs = logRepository
                .findBySentAtBetweenOrderBySentAtAsc(start, end);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalTriggered", logs.size());
        stats.put("startTime", start);
        stats.put("endTime", end);

        // 各规则类型触发次数
        Map<String, Long> byRuleType = logs.stream()
                .collect(Collectors.groupingBy(ScrmAutoReplyLogEntity::getRuleType, Collectors.counting()));
        stats.put("byRuleType", byRuleType);

        // 各渠道触发次数
        Map<String, Long> byChannel = logs.stream()
                .filter(l -> l.getChannel() != null)
                .collect(Collectors.groupingBy(ScrmAutoReplyLogEntity::getChannel, Collectors.counting()));
        stats.put("byChannel", byChannel);

        // 发送成功率
        long sentCount = logs.stream().filter(l -> STATUS_SENT.equals(l.getStatus())).count();
        long failedCount = logs.stream().filter(l -> STATUS_FAILED.equals(l.getStatus())).count();
        long skippedCount = logs.stream().filter(l -> STATUS_SKIPPED.equals(l.getStatus())).count();
        double successRate = logs.isEmpty() ? 0.0 : (double) sentCount / logs.size() * 100;
        stats.put("sentCount", sentCount);
        stats.put("failedCount", failedCount);
        stats.put("skippedCount", skippedCount);
        stats.put("successRate", successRate);
        stats.put("fallbackCount", logs.stream().filter(l -> Boolean.TRUE.equals(l.getIsFallback())).count());
        return stats;
    }

    /**
     * 规则效果统计。
     * <p>聚合指定时间范围内的回复日志, 按规则 ID 分组统计触发次数与匹配率 (各规则触发次数 / 总触发次数)。</p>
     *
     * @param startTime 起始时间 (含, 可空, 默认近 7 天)
     * @param endTime   截止时间 (含, 可空, 默认当前时间)
     * @return 统计结果列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRuleEffectiveness(LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime end = endTime != null ? endTime : LocalDateTime.now();
        LocalDateTime start = startTime != null ? startTime : end.minusDays(7);

        // 加载当前账号全部规则
        List<ScrmAutoReplyRuleEntity> rules = ruleRepository
                .findAll((root, query, cb) -> cb.and());
        // 加载时间窗口内全部回复日志
        List<ScrmAutoReplyLogEntity> logs = logRepository
                .findBySentAtBetweenOrderBySentAtAsc(start, end);

        long totalTriggered = logs.size();
        Map<Long, List<ScrmAutoReplyLogEntity>> logsByRule = logs.stream()
                .filter(l -> l.getRuleId() != null)
                .collect(Collectors.groupingBy(ScrmAutoReplyLogEntity::getRuleId));

        List<Map<String, Object>> stats = new ArrayList<>();
        for (ScrmAutoReplyRuleEntity rule : rules) {
            List<ScrmAutoReplyLogEntity> ruleLogs = logsByRule.getOrDefault(rule.getId(), List.of());
            long triggerCount = ruleLogs.size();
            double matchRate = totalTriggered > 0 ? (double) triggerCount / totalTriggered * 100 : 0.0;
            long sentCount = ruleLogs.stream().filter(l -> STATUS_SENT.equals(l.getStatus())).count();
            long failedCount = ruleLogs.stream().filter(l -> STATUS_FAILED.equals(l.getStatus())).count();

            Map<String, Object> stat = new LinkedHashMap<>();
            stat.put("ruleId", rule.getId());
            stat.put("ruleName", rule.getRuleName());
            stat.put("ruleType", rule.getRuleType());
            stat.put("enabled", rule.getEnabled());
            stat.put("priority", rule.getPriority());
            stat.put("isFallback", rule.getFallbackRule());
            stat.put("cumulativeTriggerCount", rule.getTriggerCount());
            stat.put("windowTriggerCount", triggerCount);
            stat.put("sentCount", sentCount);
            stat.put("failedCount", failedCount);
            stat.put("matchRate", matchRate);
            stat.put("lastTriggeredAt", rule.getLastTriggeredAt());
            stats.add(stat);
        }
        return stats;
    }

    /**
     * 响应时间统计。
     * <p>聚合指定时间范围内的回复日志, 统计平均/最大/最小响应时间。</p>
     *
     * @param startTime 起始时间 (含, 可空, 默认近 7 天)
     * @param endTime   截止时间 (含, 可空, 默认当前时间)
     * @return 响应时间统计
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getResponseTimeStats(LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime end = endTime != null ? endTime : LocalDateTime.now();
        LocalDateTime start = startTime != null ? startTime : end.minusDays(7);
        List<ScrmAutoReplyLogEntity> logs = logRepository
                .findBySentAtBetweenOrderBySentAtAsc(start, end);

        List<Integer> responseTimes = logs.stream()
                .map(ScrmAutoReplyLogEntity::getResponseTimeMs)
                .filter(t -> t != null && t > 0)
                .toList();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("sampleCount", responseTimes.size());
        if (responseTimes.isEmpty()) {
            stats.put("avgMs", 0.0);
            stats.put("maxMs", 0);
            stats.put("minMs", 0);
            stats.put("p50Ms", 0);
            stats.put("p95Ms", 0);
        } else {
            double avg = responseTimes.stream().mapToInt(Integer::intValue).average().orElse(0);
            int max = responseTimes.stream().mapToInt(Integer::intValue).max().orElse(0);
            int min = responseTimes.stream().mapToInt(Integer::intValue).min().orElse(0);
            List<Integer> sorted = responseTimes.stream().sorted().toList();
            int p50 = sorted.get(sorted.size() / 2);
            int p95 = sorted.get((int) Math.ceil(sorted.size() * 0.95) - 1);
            stats.put("avgMs", avg);
            stats.put("maxMs", max);
            stats.put("minMs", min);
            stats.put("p50Ms", p50);
            stats.put("p95Ms", p95);
        }
        stats.put("startTime", start);
        stats.put("endTime", end);
        return stats;
    }

    /**
     * 匹配率统计 (匹配成功 / 总消息)。
     * <p>匹配率 = 命中规则的回复日志数 / 总消息数。由于无命中的消息不记录日志,
     * 此处以"已记录的回复日志中非兜底回复的比例"近似匹配率。</p>
     *
     * @param startTime 起始时间 (含, 可空, 默认近 7 天)
     * @param endTime   截止时间 (含, 可空, 默认当前时间)
     * @return 匹配率统计
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getMatchRate(LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime end = endTime != null ? endTime : LocalDateTime.now();
        LocalDateTime start = startTime != null ? startTime : end.minusDays(7);
        List<ScrmAutoReplyLogEntity> logs = logRepository
                .findBySentAtBetweenOrderBySentAtAsc(start, end);

        long totalReplies = logs.size();
        long nonFallback = logs.stream().filter(l -> !Boolean.TRUE.equals(l.getIsFallback())).count();
        long fallback = logs.stream().filter(l -> Boolean.TRUE.equals(l.getIsFallback())).count();
        double matchRate = totalReplies > 0 ? (double) nonFallback / totalReplies * 100 : 0.0;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalReplies", totalReplies);
        stats.put("matchedCount", nonFallback);
        stats.put("fallbackCount", fallback);
        stats.put("matchRate", matchRate);
        stats.put("startTime", start);
        stats.put("endTime", end);
        return stats;
    }
}