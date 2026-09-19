/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmVisitStatsService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.entity.ScrmVisitPlanEntity;
import org.hiylo.scrm.entity.ScrmVisitTaskEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmVisitTaskRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRM 客户回访统计子域服务
 * <p>
 * 负责回访统计 (概览 / 计划统计 / 负责人统计 / 客户历史 / 趋势 / 满意度趋势 / 结果分布)。
 * 计划存在性校验复用 {@link ScrmVisitPlanService}。本服务为 {@link ScrmVisitService}
 * 门面的子域拆分, 不反向依赖门面。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
public class ScrmVisitStatsService {

    /** 任务状态: 待执行 */
    private static final String TASK_STATUS_PENDING = "PENDING";
    /** 任务状态: 已分配 */
    private static final String TASK_STATUS_ASSIGNED = "ASSIGNED";
    /** 任务状态: 进行中 */
    private static final String TASK_STATUS_IN_PROGRESS = "IN_PROGRESS";
    /** 任务状态: 已完成 */
    private static final String TASK_STATUS_COMPLETED = "COMPLETED";
    /** 任务状态: 已取消 */
    private static final String TASK_STATUS_CANCELLED = "CANCELLED";
    /** 任务状态: 已改期 */
    private static final String TASK_STATUS_RESCHEDULED = "RESCHEDULED";
    /** 任务状态: 已逾期 */
    private static final String TASK_STATUS_OVERDUE = "OVERDUE";
    /** 任务状态: 失败 */
    private static final String TASK_STATUS_FAILED = "FAILED";

    /** 回访结果: 成功 */
    private static final String OUTCOME_SUCCESS = "SUCCESS";
    /** 回访结果: 部分成功 */
    private static final String OUTCOME_PARTIAL = "PARTIAL";

    /** 回访任务数据访问层 */
    private final ScrmVisitTaskRepository taskRepository;

    /** 回访计划子域服务 (计划查询) */
    private final ScrmVisitPlanService planService;

    /**
     * 回访统计概览: 总数 / 完成率 / 满意度 / 成功率 / 各类型。
     *
     * @param startTime 开始时间 (含, 可空)
     * @param endTime   结束时间 (含, 可空)
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getVisitStats(LocalDateTime startTime, LocalDateTime endTime) {
        List<Object[]> byStatus = taskRepository.countByStatus(startTime, endTime);
        Map<String, Long> statusCount = new LinkedHashMap<>();
        statusCount.put(TASK_STATUS_PENDING, 0L);
        statusCount.put(TASK_STATUS_ASSIGNED, 0L);
        statusCount.put(TASK_STATUS_IN_PROGRESS, 0L);
        statusCount.put(TASK_STATUS_COMPLETED, 0L);
        statusCount.put(TASK_STATUS_CANCELLED, 0L);
        statusCount.put(TASK_STATUS_RESCHEDULED, 0L);
        statusCount.put(TASK_STATUS_OVERDUE, 0L);
        statusCount.put(TASK_STATUS_FAILED, 0L);
        long total = 0L;
        long completed = 0L;
        for (Object[] row : byStatus) {
            String status = (String) row[0];
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            statusCount.put(status, count);
            total += count;
            if (TASK_STATUS_COMPLETED.equals(status)) {
                completed += count;
            }
        }
        // 各类型任务数
        List<Object[]> byType = taskRepository.countByVisitType(startTime, endTime);
        Map<String, Long> typeCount = new LinkedHashMap<>();
        for (Object[] row : byType) {
            String type = (String) row[0];
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            typeCount.put(type, count);
        }
        // 平均满意度
        Double avgScore = taskRepository.avgSatisfactionScore(startTime, endTime);
        // 成功率 (基于已完成任务的结果)
        List<Object[]> byOutcome = taskRepository.countByVisitOutcome(startTime, endTime);
        long outcomeTotal = 0L;
        long success = 0L;
        for (Object[] row : byOutcome) {
            String outcome = (String) row[0];
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            outcomeTotal += count;
            if (OUTCOME_SUCCESS.equals(outcome) || OUTCOME_PARTIAL.equals(outcome)) {
                success += count;
            }
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("completed", completed);
        stats.put("completionRate", total == 0 ? 0.0 : (double) completed / total);
        stats.put("avgSatisfactionScore", avgScore != null ? avgScore : 0.0);
        stats.put("successRate", outcomeTotal == 0 ? 0.0 : (double) success / outcomeTotal);
        stats.put("statusCount", statusCount);
        stats.put("typeCount", typeCount);
        return stats;
    }

    /**
     * 计划统计: 任务数 / 完成率 / 满意度 / 成功率。
     *
     * @param planId 计划 ID
     * @return 统计结果 Map
     * @throws ScrmException 计划不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getPlanStats(Long planId) throws ScrmException {
        ScrmVisitPlanEntity plan = planService.findPlanOrThrow(planId);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("planId", plan.getId());
        stats.put("planName", plan.getPlanName());
        stats.put("planCode", plan.getPlanCode());
        stats.put("status", plan.getStatus());
        stats.put("totalTasks", plan.getTotalTasks());
        stats.put("completedTasks", plan.getCompletedTasks());
        stats.put("pendingTasks", plan.getPendingTasks());
        stats.put("overdueTasks", plan.getOverdueTasks());
        stats.put("completionRate", plan.getCompletionRate());
        stats.put("avgSatisfactionScore", plan.getAvgSatisfactionScore());
        stats.put("successRate", plan.getSuccessRate());
        return stats;
    }

    /**
     * 负责人统计: 任务数 / 各状态 / 满意度。
     *
     * @param assigneeId 负责人 ID
     * @param startTime  开始时间 (含, 可空)
     * @param endTime    结束时间 (含, 可空)
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getAssigneeStats(String assigneeId, LocalDateTime startTime, LocalDateTime endTime) {
        if (assigneeId == null || assigneeId.isBlank()) {
            throw ScrmException.badRequest("负责人 ID 不能为空");
        }
        List<Object[]> byStatus = taskRepository.countByAssigneeAndStatus(assigneeId, startTime, endTime);
        Map<String, Long> statusCount = new LinkedHashMap<>();
        long total = 0L;
        long completed = 0L;
        for (Object[] row : byStatus) {
            String status = (String) row[0];
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            statusCount.put(status, count);
            total += count;
            if (TASK_STATUS_COMPLETED.equals(status)) {
                completed += count;
            }
        }
        Double avgScore = taskRepository.avgSatisfactionScoreByAssignee(assigneeId, startTime, endTime);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("assigneeId", assigneeId);
        stats.put("totalTasks", total);
        stats.put("completedTasks", completed);
        stats.put("completionRate", total == 0 ? 0.0 : (double) completed / total);
        stats.put("avgSatisfactionScore", avgScore != null ? avgScore : 0.0);
        stats.put("statusCount", statusCount);
        return stats;
    }

    /**
     * 客户回访历史。
     *
     * @param customerId 客户 ID
     * @return 任务列表 (按 scheduledDate DESC)
     */
    @Transactional(readOnly = true)
    public List<ScrmVisitTaskEntity> getCustomerVisitHistory(Long customerId) {
        if (customerId == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        Specification<ScrmVisitTaskEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("customerId"), customerId));
            query.orderBy(cb.desc(root.get("scheduledDate")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return taskRepository.findAll(spec);
    }

    /**
     * 回访趋势: 按日聚合任务数。
     *
     * @param days 天数 (从今天向前推算)
     * @return 趋势列表 [{date, count}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getVisitTrend(int days) {
        if (days <= 0) {
            throw ScrmException.badRequest("天数必须为正数");
        }
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1L);
        List<Object[]> rows = taskRepository.countByDay(startDate, endDate);
        Map<LocalDate, Long> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            LocalDate date = (LocalDate) row[0];
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            map.put(date, count);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate d = startDate.plusDays(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", d);
            m.put("count", map.getOrDefault(d, 0L));
            result.add(m);
        }
        return result;
    }

    /**
     * 满意度趋势: 按日聚合已完成任务的平均满意度。
     *
     * @param days 天数 (从今天向前推算)
     * @return 趋势列表 [{date, avgScore}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSatisfactionTrend(int days) {
        if (days <= 0) {
            throw ScrmException.badRequest("天数必须为正数");
        }
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1L);
        List<Object[]> rows = taskRepository.avgSatisfactionByDay(startDate, endDate);
        Map<LocalDate, Double> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            LocalDate date = (LocalDate) row[0];
            double avg = row[1] == null ? 0.0 : ((Number) row[1]).doubleValue();
            map.put(date, avg);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate d = startDate.plusDays(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", d);
            m.put("avgScore", map.getOrDefault(d, 0.0));
            result.add(m);
        }
        return result;
    }

    /**
     * 回访结果分布: 按结果聚合任务数。
     *
     * @param startTime 开始时间 (含, 可空)
     * @param endTime   结束时间 (含, 可空)
     * @return 分布列表 [{outcome, count}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getOutcomeDistribution(LocalDateTime startTime, LocalDateTime endTime) {
        List<Object[]> rows = taskRepository.countByVisitOutcome(startTime, endTime);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("outcome", row[0]);
            m.put("count", row[1] == null ? 0L : ((Number) row[1]).longValue());
            result.add(m);
        }
        return result;
    }
}
