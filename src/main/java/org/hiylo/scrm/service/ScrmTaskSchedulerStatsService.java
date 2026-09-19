/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTaskSchedulerStatsService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmScheduledTaskEntity;
import org.hiylo.scrm.entity.ScrmTaskExecutionEntity;
import org.hiylo.scrm.repository.ScrmScheduledTaskRepository;
import org.hiylo.scrm.repository.ScrmTaskExecutionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * SCRM 任务调度 - 统计子域服务。
 * <p>
 * 承载任务监控指标能力: 任务统计概览、执行统计、失败分析、性能统计、任务健康度
 * 与执行趋势等聚合查询。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmTaskSchedulerStatsService {

    /** 默认统计时间窗口 (天) */
    private static final int DEFAULT_STATS_DAYS = 7;

    /** 任务调度配置数据访问层 */
    private final ScrmScheduledTaskRepository taskRepository;

    /** 任务执行记录数据访问层 */
    private final ScrmTaskExecutionRepository executionRepository;

    /**
     * 任务统计概览。
     * <p>统计指定时间范围内任务的总数、活跃任务数、执行次数、成功率与平均执行时长。
     * startTime / endTime 缺省时默认统计近 7 天。</p>
     *
     * @param startTime 起始时间 (含, 可空, 默认近 7 天)
     * @param endTime   截止时间 (含, 可空, 默认当前时间)
     * @return 任务统计概览
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getTaskStats(LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime end = endTime != null ? endTime : LocalDateTime.now();
        LocalDateTime start = startTime != null ? startTime : end.minusDays(DEFAULT_STATS_DAYS);
        List<ScrmScheduledTaskEntity> tasks = taskRepository.findAll(
                (root, query, cb) -> cb.and());
        List<ScrmTaskExecutionEntity> executions = executionRepository
                .findByStartedAtBetweenOrderByStartedAtAsc(start, end);

        long totalTasks = tasks.size();
        long activeTasks = tasks.stream().filter(t -> "ACTIVE".equals(t.getStatus())).count();
        long totalExecutions = executions.size();
        long successCount = executions.stream().filter(e -> "SUCCESS".equals(e.getStatus())).count();
        long failedCount = executions.stream()
                .filter(e -> "FAILED".equals(e.getStatus()) || "TIMEOUT".equals(e.getStatus())).count();
        double successRate = totalExecutions > 0 ? (double) successCount / totalExecutions * 100 : 0.0;
        double avgExecutionMs = executions.stream()
                .map(ScrmTaskExecutionEntity::getDurationMs)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalTasks", totalTasks);
        stats.put("activeTasks", activeTasks);
        stats.put("pausedTasks", tasks.stream().filter(t -> "PAUSED".equals(t.getStatus())).count());
        stats.put("errorTasks", tasks.stream().filter(t -> "ERROR".equals(t.getStatus())).count());
        stats.put("disabledTasks", tasks.stream().filter(t -> "DISABLED".equals(t.getStatus())).count());
        stats.put("totalExecutions", totalExecutions);
        stats.put("successCount", successCount);
        stats.put("failedCount", failedCount);
        stats.put("successRate", successRate);
        stats.put("avgExecutionMs", avgExecutionMs);
        stats.put("startTime", start);
        stats.put("endTime", end);
        return stats;
    }

    /**
     * 执行统计。
     * <p>聚合指定时间范围内的执行记录, 按状态/触发类型分组统计, 并计算平均执行时长。</p>
     *
     * @param startTime 起始时间 (含, 可空, 默认近 7 天)
     * @param endTime   截止时间 (含, 可空, 默认当前时间)
     * @return 执行统计
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getExecutionStats(LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime end = endTime != null ? endTime : LocalDateTime.now();
        LocalDateTime start = startTime != null ? startTime : end.minusDays(DEFAULT_STATS_DAYS);
        List<ScrmTaskExecutionEntity> executions = executionRepository
                .findByStartedAtBetweenOrderByStartedAtAsc(start, end);

        Map<String, Long> byStatus = executions.stream()
                .collect(Collectors.groupingBy(e -> e.getStatus() != null ? e.getStatus() : "UNKNOWN",
                        Collectors.counting()));
        Map<String, Long> byTriggerType = executions.stream()
                .collect(Collectors.groupingBy(e -> e.getTriggerType() != null ? e.getTriggerType() : "UNKNOWN",
                        Collectors.counting()));
        double avgDurationMs = executions.stream()
                .map(ScrmTaskExecutionEntity::getDurationMs)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalExecutions", executions.size());
        stats.put("byStatus", byStatus);
        stats.put("byTriggerType", byTriggerType);
        stats.put("avgDurationMs", avgDurationMs);
        stats.put("startTime", start);
        stats.put("endTime", end);
        return stats;
    }

    /**
     * 失败分析。
     * <p>聚合指定时间范围内的失败/超时执行, 按错误信息与任务分组统计, 并按天聚合失败趋势。</p>
     *
     * @param startTime 起始时间 (含, 可空, 默认近 7 天)
     * @param endTime   截止时间 (含, 可空, 默认当前时间)
     * @return 失败分析结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getFailureAnalysis(LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime end = endTime != null ? endTime : LocalDateTime.now();
        LocalDateTime start = startTime != null ? startTime : end.minusDays(DEFAULT_STATS_DAYS);
        List<ScrmTaskExecutionEntity> executions = executionRepository
                .findByStartedAtBetweenOrderByStartedAtAsc(start, end);
        List<ScrmTaskExecutionEntity> failures = executions.stream()
                .filter(e -> "FAILED".equals(e.getStatus()) || "TIMEOUT".equals(e.getStatus()))
                .toList();

        // 按错误信息分类
        Map<String, Long> byError = failures.stream()
                .filter(e -> e.getErrorMessage() != null)
                .collect(Collectors.groupingBy(ScrmTaskExecutionEntity::getErrorMessage, Collectors.counting()));
        // 按任务分类
        Map<String, Long> byTask = failures.stream()
                .filter(e -> e.getTaskName() != null)
                .collect(Collectors.groupingBy(ScrmTaskExecutionEntity::getTaskName, Collectors.counting()));
        // 按天趋势
        Map<String, Long> trend = failures.stream()
                .filter(e -> e.getStartedAt() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getStartedAt().toLocalDate().toString(), Collectors.counting()));

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalFailures", failures.size());
        stats.put("byError", byError);
        stats.put("byTask", byTask);
        stats.put("trend", trend);
        stats.put("startTime", start);
        stats.put("endTime", end);
        return stats;
    }

    /**
     * 性能统计。
     * <p>聚合指定时间范围内的执行记录, 统计最慢任务、最快任务与平均执行时长趋势。</p>
     *
     * @param startTime 起始时间 (含, 可空, 默认近 7 天)
     * @param endTime   截止时间 (含, 可空, 默认当前时间)
     * @return 性能统计
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getPerformanceStats(LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime end = endTime != null ? endTime : LocalDateTime.now();
        LocalDateTime start = startTime != null ? startTime : end.minusDays(DEFAULT_STATS_DAYS);
        List<ScrmTaskExecutionEntity> executions = executionRepository
                .findByStartedAtBetweenOrderByStartedAtAsc(start, end);

        // 按任务聚合平均执行时长
        Map<String, Double> avgByTask = executions.stream()
                .filter(e -> e.getTaskName() != null && e.getDurationMs() != null)
                .collect(Collectors.groupingBy(ScrmTaskExecutionEntity::getTaskName,
                        Collectors.averagingInt(ScrmTaskExecutionEntity::getDurationMs)));
        // 最慢任务 (Top 10)
        List<Map<String, Object>> slowestTasks = avgByTask.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(10)
                .map(e -> {
                    Map<String, Object> t = new LinkedHashMap<>();
                    t.put("taskName", e.getKey());
                    t.put("avgDurationMs", e.getValue());
                    return t;
                })
                .toList();
        // 最快任务 (Top 10)
        List<Map<String, Object>> fastestTasks = avgByTask.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(10)
                .map(e -> {
                    Map<String, Object> t = new LinkedHashMap<>();
                    t.put("taskName", e.getKey());
                    t.put("avgDurationMs", e.getValue());
                    return t;
                })
                .toList();
        // 按天聚合平均执行时长趋势
        Map<String, Double> trend = executions.stream()
                .filter(e -> e.getStartedAt() != null && e.getDurationMs() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getStartedAt().toLocalDate().toString(),
                        Collectors.averagingInt(ScrmTaskExecutionEntity::getDurationMs)));

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("slowestTasks", slowestTasks);
        stats.put("fastestTasks", fastestTasks);
        stats.put("trend", trend);
        stats.put("overallAvgMs", executions.stream()
                .map(ScrmTaskExecutionEntity::getDurationMs)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0));
        stats.put("startTime", start);
        stats.put("endTime", end);
        return stats;
    }

    /**
     * 任务健康度。
     * <p>统计每个任务的成功率、连续失败次数与最近执行时间, 用于识别健康度低、需关注任务。</p>
     *
     * @return 任务健康度列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTaskHealth() {
        List<ScrmScheduledTaskEntity> tasks = taskRepository.findAll(
                (root, query, cb) -> cb.and());
        List<Map<String, Object>> health = new ArrayList<>();
        for (ScrmScheduledTaskEntity task : tasks) {
            Map<String, Object> h = new LinkedHashMap<>();
            h.put("taskId", task.getId());
            h.put("taskName", task.getTaskName());
            h.put("taskCode", task.getTaskCode());
            h.put("status", task.getStatus());
            h.put("isEnabled", task.getIsEnabled());
            h.put("totalExecutions", task.getTotalExecutions());
            h.put("successCount", task.getSuccessCount());
            h.put("failureCount", task.getFailureCount());
            h.put("timeoutCount", task.getTimeoutCount());
            double successRate = task.getTotalExecutions() != null && task.getTotalExecutions() > 0
                    ? (double) (task.getSuccessCount() != null ? task.getSuccessCount() : 0)
                    / task.getTotalExecutions() * 100
                    : 0.0;
            h.put("successRate", successRate);
            h.put("consecutiveFailures", task.getConsecutiveFailures());
            h.put("lastExecutedAt", task.getLastExecutedAt());
            h.put("lastExecutionStatus", task.getLastExecutionStatus());
            h.put("lastErrorMessage", task.getLastErrorMessage());
            // 健康度评级
            String healthLevel;
            if (task.getConsecutiveFailures() != null && task.getConsecutiveFailures() >= 5) {
                healthLevel = "CRITICAL";
            } else if (task.getConsecutiveFailures() != null && task.getConsecutiveFailures() >= 3) {
                healthLevel = "WARNING";
            } else if (successRate >= 90) {
                healthLevel = "HEALTHY";
            } else {
                healthLevel = "FAIR";
            }
            h.put("healthLevel", healthLevel);
            health.add(h);
        }
        return health;
    }

    /**
     * 执行趋势 (按天聚合执行次数与成功率)。
     *
     * @param days 统计天数 (默认 7)
     * @return 趋势数据列表 (按日期升序)
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getExecutionTrend(Integer days) {
        int safeDays = days != null && days > 0 ? days : DEFAULT_STATS_DAYS;
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(safeDays);
        List<ScrmTaskExecutionEntity> executions = executionRepository
                .findByStartedAtBetweenOrderByStartedAtAsc(start, end);
        // 按天聚合
        Map<String, List<ScrmTaskExecutionEntity>> byDay = executions.stream()
                .filter(e -> e.getStartedAt() != null)
                .collect(Collectors.groupingBy(e -> e.getStartedAt().toLocalDate().toString()));
        List<Map<String, Object>> trend = new ArrayList<>();
        for (Map.Entry<String, List<ScrmTaskExecutionEntity>> entry : byDay.entrySet()) {
            List<ScrmTaskExecutionEntity> dayExecs = entry.getValue();
            long total = dayExecs.size();
            long success = dayExecs.stream().filter(e -> "SUCCESS".equals(e.getStatus())).count();
            double successRate = total > 0 ? (double) success / total * 100 : 0.0;
            double avgMs = dayExecs.stream()
                    .map(ScrmTaskExecutionEntity::getDurationMs)
                    .filter(Objects::nonNull)
                    .mapToInt(Integer::intValue)
                    .average()
                    .orElse(0.0);
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", entry.getKey());
            day.put("totalExecutions", total);
            day.put("successCount", success);
            day.put("failedCount", total - success);
            day.put("successRate", successRate);
            day.put("avgDurationMs", avgMs);
            trend.add(day);
        }
        return trend;
    }
}