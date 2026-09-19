/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWorkflowStatsService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmWorkflowInstanceEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmWorkflowInstanceRepository;
import org.hiylo.scrm.repository.ScrmWorkflowNodeLogRepository;
import org.hiylo.scrm.repository.ScrmWorkflowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRM 工作流统计分析服务。
 * <p>
 * 承载统计分析子域: 工作流统计 (状态分布 / 执行指标)、实例统计 (状态数 / 平均时长 / 完成率)、
 * 节点性能聚合、最近 N 天工作流趋势与按节点通过率的转化漏斗。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmWorkflowStatsService {

    /** 工作流数据访问层 */
    private final ScrmWorkflowRepository workflowRepository;

    /** 工作流实例数据访问层 */
    private final ScrmWorkflowInstanceRepository instanceRepository;

    /** 节点日志数据访问层 (节点性能聚合) */
    private final ScrmWorkflowNodeLogRepository nodeLogRepository;

    /** 工作流定义与版本子域服务 (共享常量与按主键查找) */
    private final ScrmWorkflowDefinitionService definitionService;

    // ============================================================
    // 统计分析
    // ============================================================

    /**
     * 工作流统计: 总数 / 活跃数 / 执行次数 / 成功率 / 平均时长。
     *
     * @param startTime 创建时间起始 (含, 可空)
     * @param endTime   创建时间截止 (含, 可空)
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getWorkflowStats(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> stats = new LinkedHashMap<>();
        List<Object[]> byStatus = workflowRepository.countByStatus(startTime, endTime);
        Map<String, Long> statusCount = new LinkedHashMap<>();
        for (String s : ScrmWorkflowDefinitionService.VALID_WORKFLOW_STATUSES) {
            statusCount.put(s, 0L);
        }
        long total = 0L;
        for (Object[] row : byStatus) {
            String status = (String) row[0];
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            statusCount.put(status, count);
            total += count;
        }
        stats.put("statusCount", statusCount);
        stats.put("total", total);
        stats.put("active", statusCount.getOrDefault(ScrmWorkflowDefinitionService.STATUS_ACTIVE, 0L));
        stats.put("draft", statusCount.getOrDefault(ScrmWorkflowDefinitionService.STATUS_DRAFT, 0L));
        stats.put("paused", statusCount.getOrDefault(ScrmWorkflowDefinitionService.STATUS_PAUSED, 0L));
        stats.put("archived", statusCount.getOrDefault(ScrmWorkflowDefinitionService.STATUS_ARCHIVED, 0L));
        // 执行指标汇总
        Object[] metrics = workflowRepository.sumExecutionMetrics(startTime, endTime);
        long executionCount = metrics[0] == null ? 0L : ((Number) metrics[0]).longValue();
        long successCount = metrics[1] == null ? 0L : ((Number) metrics[1]).longValue();
        long failureCount = metrics[2] == null ? 0L : ((Number) metrics[2]).longValue();
        long avgTimeSum = metrics[3] == null ? 0L : ((Number) metrics[3]).longValue();
        long workflowCount = metrics[4] == null ? 0L : ((Number) metrics[4]).longValue();
        stats.put("executionCount", executionCount);
        stats.put("successCount", successCount);
        stats.put("failureCount", failureCount);
        stats.put("successRate", executionCount > 0 ? successCount * 1.0 / executionCount : 0.0);
        stats.put("avgExecutionTimeMs", workflowCount > 0 ? avgTimeSum / workflowCount : 0);
        return stats;
    }

    /**
     * 实例统计: 各状态数 / 平均时长 / 转化率 (完成率)。
     *
     * @param workflowId 工作流 ID
     * @return 统计结果 Map
     * @throws ScrmException 工作流不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getInstanceStats(Long workflowId) throws ScrmException {
        definitionService.findWorkflowOrThrow(workflowId);
        Map<String, Object> stats = new LinkedHashMap<>();
        List<Object[]> byStatus = instanceRepository.countByStatus(workflowId);
        Map<String, Long> statusCount = new LinkedHashMap<>();
        statusCount.put(ScrmWorkflowInstanceService.INSTANCE_RUNNING, 0L);
        statusCount.put(ScrmWorkflowInstanceService.INSTANCE_PAUSED, 0L);
        statusCount.put(ScrmWorkflowInstanceService.INSTANCE_COMPLETED, 0L);
        statusCount.put(ScrmWorkflowInstanceService.INSTANCE_FAILED, 0L);
        statusCount.put(ScrmWorkflowInstanceService.INSTANCE_CANCELLED, 0L);
        statusCount.put(ScrmWorkflowInstanceService.INSTANCE_WAITING, 0L);
        long total = 0L;
        for (Object[] row : byStatus) {
            String status = (String) row[0];
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            statusCount.put(status, count);
            total += count;
        }
        stats.put("statusCount", statusCount);
        stats.put("total", total);
        stats.put("running", statusCount.getOrDefault(ScrmWorkflowInstanceService.INSTANCE_RUNNING, 0L));
        stats.put("completed", statusCount.getOrDefault(ScrmWorkflowInstanceService.INSTANCE_COMPLETED, 0L));
        stats.put("failed", statusCount.getOrDefault(ScrmWorkflowInstanceService.INSTANCE_FAILED, 0L));
        stats.put("conversionRate", total > 0
                ? statusCount.getOrDefault(ScrmWorkflowInstanceService.INSTANCE_COMPLETED, 0L) * 1.0 / total : 0.0);
        Object[] duration = instanceRepository.aggregateDuration(workflowId);
        double avgDuration = duration[0] == null ? 0.0 : ((Number) duration[0]).doubleValue();
        int maxDuration = duration[1] == null ? 0 : ((Number) duration[1]).intValue();
        long durationCount = duration[2] == null ? 0L : ((Number) duration[2]).longValue();
        stats.put("avgDurationMs", avgDuration);
        stats.put("maxDurationMs", maxDuration);
        stats.put("durationSampleCount", durationCount);
        return stats;
    }

    /**
     * 节点性能: 各节点成功率 / 平均耗时。
     *
     * @param workflowId 工作流 ID
     * @return 节点性能列表
     * @throws ScrmException 工作流不存在
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getNodePerformance(Long workflowId) throws ScrmException {
        definitionService.findWorkflowOrThrow(workflowId);
        List<Object[]> rows = nodeLogRepository.aggregateNodePerformance(workflowId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nodeId", row[0]);
            m.put("nodeName", row[1]);
            m.put("nodeType", row[2]);
            long totalCount = row[3] == null ? 0L : ((Number) row[3]).longValue();
            long successCount = row[4] == null ? 0L : ((Number) row[4]).longValue();
            long failedCount = row[5] == null ? 0L : ((Number) row[5]).longValue();
            double avgDuration = row[6] == null ? 0.0 : ((Number) row[6]).doubleValue();
            m.put("totalCount", totalCount);
            m.put("successCount", successCount);
            m.put("failedCount", failedCount);
            m.put("successRate", totalCount > 0 ? successCount * 1.0 / totalCount : 0.0);
            m.put("avgDurationMs", avgDuration);
            result.add(m);
        }
        return result;
    }

    /**
     * 工作流趋势: 最近 N 天每日执行实例数。
     *
     * @param days 天数
     * @return 趋势列表 [{date, count}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getWorkflowTrend(int days) {
        if (days <= 0) {
            days = 7;
        }
        LocalDateTime startTime = LocalDate.now().minusDays(days - 1L).atStartOfDay();
        List<ScrmWorkflowInstanceEntity> instances = instanceRepository.findAll((root, query, cb) ->
                cb.and(
                        cb.greaterThanOrEqualTo(root.get("startedAt"), startTime)));
        Map<LocalDate, Long> dailyCount = new LinkedHashMap<>();
        for (int i = days - 1; i >= 0; i--) {
            dailyCount.put(LocalDate.now().minusDays(i), 0L);
        }
        for (ScrmWorkflowInstanceEntity instance : instances) {
            if (instance.getStartedAt() != null) {
                LocalDate day = instance.getStartedAt().toLocalDate();
                dailyCount.compute(day, (k, v) -> v == null ? 1L : v + 1);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<LocalDate, Long> entry : dailyCount.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", entry.getKey().toString());
            m.put("count", entry.getValue());
            result.add(m);
        }
        return result;
    }

    /**
     * 转化漏斗: 各节点通过率 (基于节点日志)。
     *
     * @param workflowId 工作流 ID
     * @return 漏斗 Map {workflowId, funnel:[{nodeId, nodeName, nodeType, totalCount, successCount, passRate}]}
     * @throws ScrmException 工作流不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getConversionFunnel(Long workflowId) throws ScrmException {
        definitionService.findWorkflowOrThrow(workflowId);
        List<Map<String, Object>> performance = getNodePerformance(workflowId);
        List<Map<String, Object>> funnel = new ArrayList<>();
        long prevSuccess = -1;
        for (Map<String, Object> node : performance) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nodeId", node.get("nodeId"));
            m.put("nodeName", node.get("nodeName"));
            m.put("nodeType", node.get("nodeType"));
            long totalCount = ((Number) node.get("totalCount")).longValue();
            long successCount = ((Number) node.get("successCount")).longValue();
            m.put("totalCount", totalCount);
            m.put("successCount", successCount);
            m.put("passRate", totalCount > 0 ? successCount * 1.0 / totalCount : 0.0);
            if (prevSuccess > 0) {
                m.put("retentionRate", successCount * 1.0 / prevSuccess);
            } else {
                m.put("retentionRate", 1.0);
            }
            prevSuccess = successCount;
            funnel.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workflowId", workflowId);
        result.put("funnel", funnel);
        return result;
    }
}