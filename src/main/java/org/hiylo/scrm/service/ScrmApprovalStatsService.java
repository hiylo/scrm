/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmApprovalStatsService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmApprovalInstanceEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmApprovalInstanceRepository;
import org.hiylo.scrm.repository.ScrmApprovalLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRM 审批统计分析服务。
 * <p>
 * 承载统计分析子域: 审批统计概览 / 流程统计 / 审批人统计 / 待审老化分析 / 审批趋势 / 效率统计。
 * 实例与流程共享状态常量取自 {@link ScrmApprovalInstanceService}, 流程存在性校验委托给
 * {@link ScrmApprovalFlowService}。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmApprovalStatsService {

    /** 审批实例数据访问层 */
    private final ScrmApprovalInstanceRepository instanceRepository;

    /** 操作日志数据访问层 */
    private final ScrmApprovalLogRepository logRepository;

    /** 审批流程定义服务 (流程存在性校验) */
    private final ScrmApprovalFlowService flowService;

    // ============================================================
    // 统计分析
    // ============================================================

    /**
     * 审批统计概览: 总数 / 各状态数 / 平均时长 / 通过率。
     *
     * @param startTime 开始时间起始 (含, 可空)
     * @param endTime   开始时间截止 (含, 可空)
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getApprovalStats(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> stats = new LinkedHashMap<>();
        List<Object[]> byStatus = instanceRepository.countByStatus(startTime, endTime);
        Map<String, Long> statusCount = new LinkedHashMap<>();
        for (String s : Arrays.asList(ScrmApprovalInstanceService.INSTANCE_PENDING,
                ScrmApprovalInstanceService.INSTANCE_APPROVING,
                ScrmApprovalInstanceService.INSTANCE_APPROVED,
                ScrmApprovalInstanceService.INSTANCE_REJECTED,
                ScrmApprovalInstanceService.INSTANCE_CANCELLED,
                ScrmApprovalInstanceService.INSTANCE_TRANSFERRED,
                ScrmApprovalInstanceService.INSTANCE_TIMEOUT,
                ScrmApprovalInstanceService.INSTANCE_WITHDRAWN)) {
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
        stats.put("pending", statusCount.getOrDefault(ScrmApprovalInstanceService.INSTANCE_PENDING, 0L));
        stats.put("approving", statusCount.getOrDefault(ScrmApprovalInstanceService.INSTANCE_APPROVING, 0L));
        stats.put("approved", statusCount.getOrDefault(ScrmApprovalInstanceService.INSTANCE_APPROVED, 0L));
        stats.put("rejected", statusCount.getOrDefault(ScrmApprovalInstanceService.INSTANCE_REJECTED, 0L));
        stats.put("cancelled", statusCount.getOrDefault(ScrmApprovalInstanceService.INSTANCE_CANCELLED, 0L));
        stats.put("timeout", statusCount.getOrDefault(ScrmApprovalInstanceService.INSTANCE_TIMEOUT, 0L));
        long approved = statusCount.getOrDefault(ScrmApprovalInstanceService.INSTANCE_APPROVED, 0L);
        long rejected = statusCount.getOrDefault(ScrmApprovalInstanceService.INSTANCE_REJECTED, 0L);
        long finished = approved + rejected;
        stats.put("passRate", finished > 0 ? approved * 1.0 / finished : 0.0);
        stats.put("rejectRate", finished > 0 ? rejected * 1.0 / finished : 0.0);
        Object[] duration = instanceRepository.aggregateDuration(startTime, endTime);
        double avgDuration = duration[0] == null ? 0.0 : ((Number) duration[0]).doubleValue();
        int maxDuration = duration[1] == null ? 0 : ((Number) duration[1]).intValue();
        long durationCount = duration[2] == null ? 0L : ((Number) duration[2]).longValue();
        stats.put("avgDurationHours", avgDuration);
        stats.put("maxDurationHours", maxDuration);
        stats.put("durationSampleCount", durationCount);
        return stats;
    }

    /**
     * 流程统计: 该流程的实例数 / 各状态数 / 平均时长 / 通过率。
     *
     * @param flowId    流程 ID
     * @param startTime 开始时间起始 (含, 可空)
     * @param endTime   开始时间截止 (含, 可空)
     * @return 统计结果 Map
     * @throws ScrmException 流程不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getFlowStats(
            Long flowId, LocalDateTime startTime, LocalDateTime endTime) throws ScrmException {
        flowService.findFlowOrThrow(flowId);
        Map<String, Object> stats = new LinkedHashMap<>();
        // 基于实例的 Specification 过滤
        List<ScrmApprovalInstanceEntity> instances = instanceRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("flowId"), flowId));
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startedAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("startedAt"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        });
        Map<String, Long> statusCount = new LinkedHashMap<>();
        long total = instances.size();
        long approved = 0;
        long rejected = 0;
        long totalDuration = 0;
        int durationSampleCount = 0;
        for (ScrmApprovalInstanceEntity instance : instances) {
            statusCount.merge(instance.getStatus(), 1L, Long::sum);
            if (ScrmApprovalInstanceService.INSTANCE_APPROVED.equals(instance.getStatus())) {
                approved++;
            }
            if (ScrmApprovalInstanceService.INSTANCE_REJECTED.equals(instance.getStatus())) {
                rejected++;
            }
            if (instance.getDurationHours() != null) {
                totalDuration += instance.getDurationHours();
                durationSampleCount++;
            }
        }
        long finished = approved + rejected;
        stats.put("flowId", flowId);
        stats.put("total", total);
        stats.put("statusCount", statusCount);
        stats.put("approved", approved);
        stats.put("rejected", rejected);
        stats.put("passRate", finished > 0 ? approved * 1.0 / finished : 0.0);
        stats.put("avgDurationHours", durationSampleCount > 0 ? totalDuration * 1.0 / durationSampleCount : 0.0);
        return stats;
    }

    /**
     * 审批人统计: 审批数 / 通过率 / 平均时长。
     *
     * @param approverId 审批人 ID
     * @param startTime 操作时间起始 (含, 可空)
     * @param endTime   操作时间截止 (含, 可空)
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getApproverStats(String approverId, LocalDateTime startTime, LocalDateTime endTime) {
        if (approverId == null || approverId.isBlank()) {
            throw ScrmException.badRequest("审批人 ID 不能为空");
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        Object[] actions = logRepository.aggregateByOperator(approverId);
        long approveCount = actions[0] == null ? 0L : ((Number) actions[0]).longValue();
        long rejectCount = actions[1] == null ? 0L : ((Number) actions[1]).longValue();
        long totalCount = actions[2] == null ? 0L : ((Number) actions[2]).longValue();
        stats.put("approverId", approverId);
        stats.put("totalCount", totalCount);
        stats.put("approveCount", approveCount);
        stats.put("rejectCount", rejectCount);
        stats.put("passRate", totalCount > 0 ? approveCount * 1.0 / totalCount : 0.0);
        // 平均审批时长 (基于已审批实例)
        Object[] duration = logRepository.aggregateApproverDuration(approverId, startTime, endTime);
        long distinctCount = duration[0] == null ? 0L : ((Number) duration[0]).longValue();
        double avgDurationHours = duration[1] == null ? 0.0 : ((Number) duration[1]).doubleValue();
        stats.put("distinctInstanceCount", distinctCount);
        stats.put("avgDurationHours", avgDurationHours);
        return stats;
    }

    /**
     * 待审老化分析: 各时间段待审数 (0-1天 / 1-3天 / 3-7天 / 7+天)。
     *
     * @return 老化分析 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getPendingAging() {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> stats = new LinkedHashMap<>();
        // countPendingBefore(cutoff) 返回 startedAt <= cutoff 的待审实例数
        // 时间段: 0-1天 / 1-3天 / 3-7天 / 7-30天 / 30+天
        long totalPending = instanceRepository.countPendingBefore(now);
        long before1Day = instanceRepository.countPendingBefore(now.minusDays(1));
        long before3Days = instanceRepository.countPendingBefore(now.minusDays(3));
        long before7Days = instanceRepository.countPendingBefore(now.minusDays(7));
        long before30Days = instanceRepository.countPendingBefore(now.minusDays(30));
        Map<String, Long> buckets = new LinkedHashMap<>();
        buckets.put("0-1d", totalPending - before1Day);
        buckets.put("1-3d", before1Day - before3Days);
        buckets.put("3-7d", before3Days - before7Days);
        buckets.put("7-30d", before7Days - before30Days);
        buckets.put("30d+", before30Days);
        stats.put("totalPending", totalPending);
        stats.put("agingBuckets", buckets);
        stats.put("overdue", before30Days);
        return stats;
    }

    /**
     * 审批趋势: 最近 N 天每日提交实例数与通过数。
     *
     * @param days 天数
     * @return 趋势列表 [{date, submitted, approved}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getApprovalTrend(int days) {
        if (days <= 0) {
            days = 7;
        }
        LocalDateTime startTime = LocalDate.now().minusDays(days - 1L).atStartOfDay();
        List<ScrmApprovalInstanceEntity> instances = instanceRepository.findAll((root, query, cb) ->
                cb.and(
                        cb.greaterThanOrEqualTo(root.get("startedAt"), startTime)));
        Map<LocalDate, long[]> dailyCount = new LinkedHashMap<>();
        for (int i = days - 1; i >= 0; i--) {
            dailyCount.put(LocalDate.now().minusDays(i), new long[]{0L, 0L});
        }
        for (ScrmApprovalInstanceEntity instance : instances) {
            if (instance.getStartedAt() != null) {
                LocalDate day = instance.getStartedAt().toLocalDate();
                long[] counter = dailyCount.computeIfAbsent(day, k -> new long[]{0L, 0L});
                counter[0]++;
                if (ScrmApprovalInstanceService.INSTANCE_APPROVED.equals(instance.getStatus())
                        && instance.getApprovedAt() != null
                        && instance.getApprovedAt().toLocalDate().equals(day)) {
                    counter[1]++;
                }
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<LocalDate, long[]> entry : dailyCount.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", entry.getKey().toString());
            m.put("submitted", entry.getValue()[0]);
            m.put("approved", entry.getValue()[1]);
            result.add(m);
        }
        return result;
    }

    /**
     * 效率统计: 平均时长 / 超时率 / 自动审批率。
     *
     * @param startTime 开始时间起始 (含, 可空)
     * @param endTime   开始时间截止 (含, 可空)
     * @return 效率统计 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getEfficiencyStats(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> stats = new LinkedHashMap<>();
        // 平均时长
        Object[] duration = instanceRepository.aggregateDuration(startTime, endTime);
        double avgDurationHours = duration[0] == null ? 0.0 : ((Number) duration[0]).doubleValue();
        long durationCount = duration[2] == null ? 0L : ((Number) duration[2]).longValue();
        stats.put("avgDurationHours", avgDurationHours);
        stats.put("durationSampleCount", durationCount);
        // 超时率
        List<ScrmApprovalInstanceEntity> allInstances = instanceRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startedAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("startedAt"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        });
        long total = allInstances.size();
        long overdueCount = allInstances.stream()
                .filter(i -> Boolean.TRUE.equals(i.getIsOverdue())
                        || ScrmApprovalInstanceService.INSTANCE_TIMEOUT.equals(i.getStatus()))
                .count();
        stats.put("total", total);
        stats.put("overdueCount", overdueCount);
        stats.put("overdueRate", total > 0 ? overdueCount * 1.0 / total : 0.0);
        // 自动审批率 (基于日志表统计 AUTO_APPROVE 操作)
        long autoApproved = logRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("actionType"), ScrmApprovalInstanceService.ACTION_AUTO_APPROVE));
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("actedAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("actedAt"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }).size();
        stats.put("autoApprovedCount", autoApproved);
        stats.put("autoApprovalRate", total > 0 ? autoApproved * 1.0 / total : 0.0);
        return stats;
    }
}