/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSystemMonitorHealthService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmAlertEventEntity;
import org.hiylo.scrm.entity.ScrmMonitorMetricEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmAlertEventRepository;
import org.hiylo.scrm.repository.ScrmMonitorMetricRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRM 系统监控健康度与统计服务。
 * <p>
 * 承载系统健康度子域与监控统计子域: 系统 / 组件健康度、健康历史、可用性 / 性能 / 资源统计 +
 * 仪表盘数据 (实时指标 + 活跃告警 + 趋势), 以及监控统计 / 告警统计 / 指标概览 /
 * Top 指标 / 告警趋势 / 严重度分布。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmSystemMonitorHealthService {

    /** 仪表盘 Top 指标默认数量 */
    private static final int DEFAULT_TOP_LIMIT = 10;

    /** 事件状态: 触发中 */
    private static final String STATUS_FIRING = "FIRING";
    /** 事件状态: 已恢复 */
    private static final String STATUS_RESOLVED = "RESOLVED";

    /** 严重程度: 信息 */
    private static final String SEVERITY_INFO = "INFO";
    /** 严重程度: 警告 */
    private static final String SEVERITY_WARNING = "WARNING";
    /** 严重程度: 严重 */
    private static final String SEVERITY_CRITICAL = "CRITICAL";
    /** 严重程度: 致命 */
    private static final String SEVERITY_FATAL = "FATAL";

    /** 合法的严重程度 */
    private static final List<String> VALID_SEVERITIES = List.of(
            "INFO", "WARNING", "CRITICAL", "FATAL");

    /** 监控指标数据访问层 */
    private final ScrmMonitorMetricRepository metricRepository;
    /** 告警事件数据访问层 */
    private final ScrmAlertEventRepository eventRepository;
    /** 监控指标子域服务 (告警指标 / 指标汇总) */
    private final ScrmSystemMonitorMetricService metricService;
    /** 告警事件子域服务 (触发中事件 / 事件统计) */
    private final ScrmSystemMonitorEventService eventService;

    /**
     * 系统健康度: 汇总各指标 → 计算健康分 → 返回状态。
     * <p>健康分 = 100 - 告警指标扣分 (CRITICAL/FATAL 各扣 10/20 分, WARNING 扣 5 分)。</p>
     *
     * @return 健康度 Map {score, status, totalMetrics, alertingMetrics, severityBreakdown}
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSystemHealth() {
        List<ScrmMonitorMetricEntity> metrics = metricRepository.findByEnabled(Boolean.TRUE);
        int total = metrics.size();
        int alerting = 0;
        Map<String, Integer> severityBreakdown = new LinkedHashMap<>();
        severityBreakdown.put(SEVERITY_INFO, 0);
        severityBreakdown.put(SEVERITY_WARNING, 0);
        severityBreakdown.put(SEVERITY_CRITICAL, 0);
        severityBreakdown.put(SEVERITY_FATAL, 0);
        double penalty = 0.0;
        // 统计触发中事件的严重度分布
        Page<ScrmAlertEventEntity> firingPage = eventRepository.findByStatusOrderByTriggerTimeDesc(
                 STATUS_FIRING, Pageable.ofSize(100));
        for (ScrmAlertEventEntity event : firingPage.getContent()) {
            String severity = event.getSeverity();
            severityBreakdown.put(severity, severityBreakdown.getOrDefault(severity, 0) + 1);
            alerting++;
            switch (severity) {
                case SEVERITY_FATAL:
                    penalty += 20.0;
                    break;
                case SEVERITY_CRITICAL:
                    penalty += 10.0;
                    break;
                case SEVERITY_WARNING:
                    penalty += 5.0;
                    break;
                default:
                    penalty += 1.0;
                    break;
            }
        }
        double score = Math.max(0.0, 100.0 - penalty);
        String status;
        if (score >= 90) {
            status = "HEALTHY";
        } else if (score >= 70) {
            status = "WARNING";
        } else if (score >= 50) {
            status = "CRITICAL";
        } else {
            status = "FATAL";
        }
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("score", Math.round(score * 100.0) / 100.0);
        health.put("status", status);
        health.put("totalMetrics", total);
        health.put("alertingMetrics", metricRepository.countByIsAlertActive(Boolean.TRUE));
        health.put("firingEvents", alerting);
        health.put("severityBreakdown", severityBreakdown);
        health.put("checkedAt", LocalDateTime.now());
        return health;
    }

    /**
     * 组件健康度: 按指标分组查询组件健康状态。
     *
     * @param component 组件名 (指标分组)
     * @return 组件健康度 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getComponentHealth(String component) {
        if (component == null || component.isBlank()) {
            throw ScrmException.badRequest("组件名不能为空");
        }
        List<ScrmMonitorMetricEntity> metrics = metricRepository.findByEnabled(Boolean.TRUE);
        List<Map<String, Object>> componentMetrics = new ArrayList<>();
        int alerting = 0;
        for (ScrmMonitorMetricEntity m : metrics) {
            if (component.equals(m.getMetricGroup())) {
                Map<String, Object> mm = new LinkedHashMap<>();
                mm.put("metricId", m.getId());
                mm.put("metricCode", m.getMetricCode());
                mm.put("metricName", m.getMetricName());
                mm.put("currentValue", m.getCurrentValue());
                mm.put("unit", m.getUnit());
                mm.put("warningThreshold", m.getWarningThreshold());
                mm.put("criticalThreshold", m.getCriticalThreshold());
                mm.put("isAlertActive", m.getIsAlertActive());
                mm.put("lastCollectedAt", m.getLastCollectedAt());
                componentMetrics.add(mm);
                if (Boolean.TRUE.equals(m.getIsAlertActive())) {
                    alerting++;
                }
            }
        }
        double score = componentMetrics.isEmpty() ? 100.0 : Math.max(0.0, 100.0 - alerting * 15.0);
        String status;
        if (score >= 90) {
            status = "HEALTHY";
        } else if (score >= 70) {
            status = "WARNING";
        } else if (score >= 50) {
            status = "CRITICAL";
        } else {
            status = "FATAL";
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("component", component);
        result.put("score", Math.round(score * 100.0) / 100.0);
        result.put("status", status);
        result.put("totalMetrics", componentMetrics.size());
        result.put("alertingMetrics", alerting);
        result.put("metrics", componentMetrics);
        result.put("checkedAt", LocalDateTime.now());
        return result;
    }

    /**
     * 健康历史: 最近 N 天每日健康分 (基于每日告警事件数推算)。
     *
     * @param days 天数
     * @return 健康历史列表 [{date, score, alertCount}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getHealthHistory(int days) {
        if (days <= 0) {
            days = 7;
        }
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusDays(days);
        List<Object[]> byDay = eventRepository.countByDay(startTime, endTime);
        List<Map<String, Object>> history = new ArrayList<>();
        // 填充每一天
        Map<String, Long> dayCount = new LinkedHashMap<>();
        for (Object[] row : byDay) {
            String day = String.valueOf(row[0]);
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            dayCount.put(day, count);
        }
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            String dayKey = date.toString();
            long count = dayCount.getOrDefault(dayKey, 0L);
            double score = Math.max(0.0, 100.0 - count * 5.0);
            Map<String, Object> h = new LinkedHashMap<>();
            h.put("date", dayKey);
            h.put("score", Math.round(score * 100.0) / 100.0);
            h.put("alertCount", count);
            history.add(h);
        }
        return history;
    }

    /**
     * 可用性统计: 基于 AVAILABILITY 分组指标计算可用率。
     *
     * @param startTime 起始时间（可空）
     * @param endTime   结束时间（可空）
     * @return 可用性统计 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getAvailabilityStats(LocalDateTime startTime, LocalDateTime endTime) {
        List<ScrmMonitorMetricEntity> metrics = metricRepository.findByEnabled(Boolean.TRUE);
        List<Map<String, Object>> availabilityMetrics = new ArrayList<>();
        for (ScrmMonitorMetricEntity m : metrics) {
            if ("AVAILABILITY".equals(m.getMetricGroup())) {
                Map<String, Object> mm = new LinkedHashMap<>();
                mm.put("metricCode", m.getMetricCode());
                mm.put("metricName", m.getMetricName());
                mm.put("currentValue", m.getCurrentValue());
                mm.put("unit", m.getUnit());
                mm.put("targetValue", m.getTargetValue());
                mm.put("isAlertActive", m.getIsAlertActive());
                availabilityMetrics.add(mm);
            }
        }
        // 可用率 = 1 - 告警时间占比 (简化: 基于告警事件数估算)
        long totalEvents = 0L;
        long resolvedEvents = 0L;
        List<Object[]> byStatus = eventRepository.countByStatus(startTime, endTime);
        for (Object[] row : byStatus) {
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            totalEvents += count;
            if (STATUS_RESOLVED.equals(row[0])) {
                resolvedEvents += count;
            }
        }
        double availability = totalEvents > 0 ? resolvedEvents * 100.0 / totalEvents : 100.0;
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("availability", Math.round(availability * 100.0) / 100.0);
        stats.put("totalEvents", totalEvents);
        stats.put("resolvedEvents", resolvedEvents);
        stats.put("metrics", availabilityMetrics);
        stats.put("startTime", startTime);
        stats.put("endTime", endTime);
        return stats;
    }

    /**
     * 性能统计: 基于 PERFORMANCE 分组指标汇总。
     *
     * @param startTime 起始时间（可空）
     * @param endTime   结束时间（可空）
     * @return 性能统计 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getPerformanceStats(LocalDateTime startTime, LocalDateTime endTime) {
        return getGroupStats("PERFORMANCE", startTime, endTime);
    }

    /**
     * 资源统计: CPU / 内存 / 磁盘 等 RESOURCE 分组指标汇总。
     *
     * @param startTime 起始时间（可空）
     * @param endTime   结束时间（可空）
     * @return 资源统计 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getResourceStats(LocalDateTime startTime, LocalDateTime endTime) {
        return getGroupStats("RESOURCE", startTime, endTime);
    }

    /**
     * 仪表盘数据: 实时指标 + 活跃告警 + 趋势。
     *
     * @return 仪表盘 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getDashboardData() {
        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("health", getSystemHealth());
        dashboard.put("firingEvents", eventService.getFiringEvents(Pageable.ofSize(DEFAULT_TOP_LIMIT)).getContent());
        dashboard.put("alertingMetrics",
                metricService.getAlertingMetrics(Pageable.ofSize(DEFAULT_TOP_LIMIT)).getContent());
        dashboard.put("alertTrend", getAlertTrend(7));
        dashboard.put("severityDistribution", getSeverityDistribution(null, null));
        dashboard.put("generatedAt", LocalDateTime.now());
        return dashboard;
    }

    /**
     * 监控统计: 指标数 / 告警数 / 平均恢复时间 (MTTR)。
     *
     * @param startTime 起始时间（可空）
     * @param endTime   结束时间（可空）
     * @return 监控统计 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getMonitorStats(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> stats = new LinkedHashMap<>();
        // 指标统计
        List<ScrmMonitorMetricEntity> allMetrics = metricRepository.findByEnabled(Boolean.TRUE);
        stats.put("totalMetrics", allMetrics.size());
        stats.put("alertingMetrics", metricRepository.countByIsAlertActive(Boolean.TRUE));
        // 告警事件统计
        Map<String, Object> eventStats = eventService.getEventStats(startTime, endTime);
        stats.put("totalAlerts", eventStats.get("total"));
        stats.put("firingAlerts", eventStats.get("firing"));
        stats.put("resolvedAlerts", eventStats.get("resolved"));
        // MTTR 平均恢复时长
        List<ScrmAlertEventEntity> resolved = eventRepository.findResolvedEvents(
                 STATUS_RESOLVED, startTime, endTime);
        double mttr = resolved.stream()
                .filter(e -> e.getDurationSeconds() != null)
                .mapToInt(ScrmAlertEventEntity::getDurationSeconds)
                .average()
                .orElse(0.0);
        stats.put("mttrSeconds", Math.round(mttr * 100.0) / 100.0);
        stats.put("startTime", startTime);
        stats.put("endTime", endTime);
        return stats;
    }

    /**
     * 告警统计: 各严重度 / 各规则 / MTTR / MTTA。
     *
     * @param startTime 起始时间（可空）
     * @param endTime   结束时间（可空）
     * @return 告警统计 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getAlertStats(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> stats = new LinkedHashMap<>();
        // 按严重度聚合
        List<Object[]> bySeverity = eventRepository.countBySeverity(startTime, endTime);
        Map<String, Long> severityCount = new LinkedHashMap<>();
        long total = 0L;
        for (Object[] row : bySeverity) {
            String severity = (String) row[0];
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            severityCount.put(severity, count);
            total += count;
        }
        stats.put("severityCount", severityCount);
        stats.put("total", total);
        // 按规则聚合 (Top 告警规则)
        List<Object[]> byRule = eventRepository.countByRule(startTime, endTime);
        List<Map<String, Object>> topRules = new ArrayList<>();
        for (Object[] row : byRule) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("ruleId", row[0]);
            r.put("ruleName", row[1]);
            r.put("count", row[2] == null ? 0L : ((Number) row[2]).longValue());
            topRules.add(r);
        }
        stats.put("topRules", topRules);
        // MTTR 平均恢复时长
        List<ScrmAlertEventEntity> resolved = eventRepository.findResolvedEvents(
                 STATUS_RESOLVED, startTime, endTime);
        double mttr = resolved.stream()
                .filter(e -> e.getDurationSeconds() != null)
                .mapToInt(ScrmAlertEventEntity::getDurationSeconds)
                .average()
                .orElse(0.0);
        stats.put("mttrSeconds", Math.round(mttr * 100.0) / 100.0);
        // MTTA 平均确认时长
        List<ScrmAlertEventEntity> acknowledged = eventRepository.findAcknowledgedEvents(
                 startTime, endTime);
        double mtta = acknowledged.stream()
                .filter(e -> e.getTriggerTime() != null && e.getAcknowledgedAt() != null)
                .mapToLong(e -> ChronoUnit.SECONDS.between(e.getTriggerTime(), e.getAcknowledgedAt()))
                .average()
                .orElse(0.0);
        stats.put("mttaSeconds", Math.round(mtta * 100.0) / 100.0);
        stats.put("startTime", startTime);
        stats.put("endTime", endTime);
        return stats;
    }

    /**
     * 指标统计概览: 总数 / 各分组数 / 告警态数。
     *
     * @return 指标统计概览 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getMetricStatsOverview() {
        return metricService.getMetricSummary(null);
    }

    /**
     * 告警最多的指标 (Top N)。
     *
     * @param limit 返回数量
     * @return Top 指标列表 [{metricId, metricName, metricCode, alertCount}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTopAlertedMetrics(int limit) {
        if (limit <= 0) {
            limit = DEFAULT_TOP_LIMIT;
        }
        List<ScrmMonitorMetricEntity> metrics = metricRepository.findByEnabled(Boolean.TRUE);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ScrmMonitorMetricEntity m : metrics) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("metricId", m.getId());
            r.put("metricName", m.getMetricName());
            r.put("metricCode", m.getMetricCode());
            r.put("metricGroup", m.getMetricGroup());
            r.put("alertCount", m.getAlertCount() != null ? m.getAlertCount() : 0);
            r.put("isAlertActive", m.getIsAlertActive());
            r.put("currentValue", m.getCurrentValue());
            result.add(r);
        }
        result.sort((a, b) -> Integer.compare(
                ((Number) b.get("alertCount")).intValue(),
                ((Number) a.get("alertCount")).intValue()));
        if (result.size() > limit) {
            return result.subList(0, limit);
        }
        return result;
    }

    /**
     * 告警趋势: 最近 N 天每日告警事件数。
     *
     * @param days 天数
     * @return 趋势列表 [{date, count}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAlertTrend(int days) {
        if (days <= 0) {
            days = 7;
        }
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusDays(days);
        List<Object[]> byDay = eventRepository.countByDay(startTime, endTime);
        Map<String, Long> dayCount = new LinkedHashMap<>();
        for (Object[] row : byDay) {
            String day = String.valueOf(row[0]);
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            dayCount.put(day, count);
        }
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            String dayKey = date.toString();
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", dayKey);
            day.put("count", dayCount.getOrDefault(dayKey, 0L));
            trend.add(day);
        }
        return trend;
    }

    /**
     * 严重度分布: 按严重度聚合事件数。
     *
     * @param startTime 起始时间（可空）
     * @param endTime   结束时间（可空）
     * @return 严重度分布 Map {INFO, WARNING, CRITICAL, FATAL}
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSeverityDistribution(LocalDateTime startTime, LocalDateTime endTime) {
        List<Object[]> bySeverity = eventRepository.countBySeverity(startTime, endTime);
        Map<String, Object> distribution = new LinkedHashMap<>();
        for (String s : VALID_SEVERITIES) {
            distribution.put(s, 0L);
        }
        long total = 0L;
        for (Object[] row : bySeverity) {
            String severity = (String) row[0];
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            distribution.put(severity, count);
            total += count;
        }
        distribution.put("total", total);
        return distribution;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 按分组统计 (通用, 用于性能/资源统计)。
     *
     * @param group     指标分组
     * @param startTime 起始时间（可空）
     * @param endTime   结束时间（可空）
     * @return 统计 Map
     */
    private Map<String, Object> getGroupStats(String group, LocalDateTime startTime, LocalDateTime endTime) {
        List<ScrmMonitorMetricEntity> metrics = metricRepository.findByEnabled(Boolean.TRUE);
        List<Map<String, Object>> groupMetrics = new ArrayList<>();
        int alerting = 0;
        for (ScrmMonitorMetricEntity m : metrics) {
            if (group.equals(m.getMetricGroup())) {
                Map<String, Object> mm = new LinkedHashMap<>();
                mm.put("metricId", m.getId());
                mm.put("metricCode", m.getMetricCode());
                mm.put("metricName", m.getMetricName());
                mm.put("currentValue", m.getCurrentValue());
                mm.put("minValue", m.getMinValue());
                mm.put("maxValue", m.getMaxValue());
                mm.put("avgValue", m.getAvgValue());
                mm.put("unit", m.getUnit());
                mm.put("warningThreshold", m.getWarningThreshold());
                mm.put("criticalThreshold", m.getCriticalThreshold());
                mm.put("isAlertActive", m.getIsAlertActive());
                mm.put("lastCollectedAt", m.getLastCollectedAt());
                groupMetrics.add(mm);
                if (Boolean.TRUE.equals(m.getIsAlertActive())) {
                    alerting++;
                }
            }
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("group", group);
        stats.put("totalMetrics", groupMetrics.size());
        stats.put("alertingMetrics", alerting);
        stats.put("metrics", groupMetrics);
        stats.put("startTime", startTime);
        stats.put("endTime", endTime);
        return stats;
    }
}