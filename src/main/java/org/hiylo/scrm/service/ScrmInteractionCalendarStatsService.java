/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmInteractionCalendarStatsService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmInteractionPlanEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmInteractionPlanRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRM 客户互动统计与冲突检测服务。
 * <p>
 * 承载互动统计能力: 互动统计概览 / 客户统计 / 负责人统计 / 互动趋势 /
 * 完成率 / 未到率 / 最佳时段 / 热力图 / 客户时间线 / 互动频率, 以及
 * 空闲时段查找与时间冲突检测。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmInteractionCalendarStatsService {

    /** 计划状态: 已完成 */
    private static final String STATUS_COMPLETED = "COMPLETED";
    /** 计划状态: 已取消 */
    private static final String STATUS_CANCELLED = "CANCELLED";
    /** 计划状态: 未到 */
    private static final String STATUS_NO_SHOW = "NO_SHOW";

    /** 非终态状态集合 (可流转的状态) */
    private static final List<String> ACTIVE_STATUSES = List.of(
            "PLANNED", "CONFIRMED", "IN_PROGRESS", "RESCHEDULED");

    /** 互动计划数据访问层 */
    private final ScrmInteractionPlanRepository planRepository;

    /**
     * 客户互动时间线 (按月聚合最近 N 个月的互动记录)。
     *
     * @param customerId 客户 ID
     * @param months     月数
     * @return 时间线列表 [{yearMonth, plans:[...]}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getPlanTimeline(Long customerId, int months) {
        if (customerId == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        if (months <= 0) {
            throw ScrmException.badRequest("月数必须为正数");
        }
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusMonths(months).withDayOfMonth(1);
        Specification<ScrmInteractionPlanEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("customerId"), customerId));
            predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledStart"), startDate.atStartOfDay()));
            query.orderBy(cb.desc(root.get("scheduledStart")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmInteractionPlanEntity> plans = planRepository.findAll(spec);
        // 按年月分组
        Map<String, List<ScrmInteractionPlanEntity>> grouped = new LinkedHashMap<>();
        for (ScrmInteractionPlanEntity plan : plans) {
            String yearMonth = plan.getScheduledStart().getYear() + "-"
                    + String.format("%02d", plan.getScheduledStart().getMonthValue());
            grouped.computeIfAbsent(yearMonth, k -> new ArrayList<>()).add(plan);
        }
        List<Map<String, Object>> timeline = new ArrayList<>();
        for (Map.Entry<String, List<ScrmInteractionPlanEntity>> entry : grouped.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("yearMonth", entry.getKey());
            m.put("count", entry.getValue().size());
            m.put("plans", entry.getValue());
            timeline.add(m);
        }
        return timeline;
    }

    /**
     * 客户互动频率 (按月聚合最近 N 个月的互动次数)。
     *
     * @param customerId 客户 ID
     * @param months     月数
     * @return 频率列表 [{yearMonth, count}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getInteractionFrequency(Long customerId, int months) {
        if (customerId == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        if (months <= 0) {
            throw ScrmException.badRequest("月数必须为正数");
        }
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusMonths(months).withDayOfMonth(1);
        Specification<ScrmInteractionPlanEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("customerId"), customerId));
            predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledStart"), startDate.atStartOfDay()));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmInteractionPlanEntity> plans = planRepository.findAll(spec);
        Map<String, Long> grouped = new LinkedHashMap<>();
        for (ScrmInteractionPlanEntity plan : plans) {
            String yearMonth = plan.getScheduledStart().getYear() + "-"
                    + String.format("%02d", plan.getScheduledStart().getMonthValue());
            grouped.merge(yearMonth, 1L, Long::sum);
        }
        // 补全空月份
        List<Map<String, Object>> result = new ArrayList<>();
        YearMonth ym = YearMonth.from(startDate);
        YearMonth endYm = YearMonth.from(endDate);
        while (!ym.isAfter(endYm)) {
            String key = ym.getYear() + "-" + String.format("%02d", ym.getMonthValue());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("yearMonth", key);
            m.put("count", grouped.getOrDefault(key, 0L));
            result.add(m);
            ym = ym.plusMonths(1);
        }
        return result;
    }

    /**
     * 互动统计概览: 各类型/各方式/完成率/平均时长。
     *
     * @param startTime 开始时间 (含, 可空)
     * @param endTime   结束时间 (含, 可空)
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getInteractionStats(LocalDateTime startTime, LocalDateTime endTime) {
        // 各状态计数
        List<Object[]> byStatus = planRepository.countByStatus(startTime, endTime);
        Map<String, Long> statusCount = new LinkedHashMap<>();
        long total = 0L;
        long completed = 0L;
        for (Object[] row : byStatus) {
            String status = (String) row[0];
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            statusCount.put(status, count);
            total += count;
            if (STATUS_COMPLETED.equals(status)) {
                completed += count;
            }
        }
        // 各互动类型计数
        List<Object[]> byType = planRepository.countByInteractionType(startTime, endTime);
        Map<String, Long> typeCount = new LinkedHashMap<>();
        for (Object[] row : byType) {
            typeCount.put((String) row[0], row[1] == null ? 0L : ((Number) row[1]).longValue());
        }
        // 各互动方式计数
        List<Object[]> byMethod = planRepository.countByInteractionMethod(startTime, endTime);
        Map<String, Long> methodCount = new LinkedHashMap<>();
        for (Object[] row : byMethod) {
            methodCount.put((String) row[0], row[1] == null ? 0L : ((Number) row[1]).longValue());
        }
        // 平均时长 (基于已完成且有实际时间的计划)
        List<ScrmInteractionPlanEntity> completedPlans =
                planRepository.findCompletedWithActualTime(startTime, endTime);
        double avgDuration = 0.0;
        if (!completedPlans.isEmpty()) {
            long totalMinutes = 0L;
            int count = 0;
            for (ScrmInteractionPlanEntity p : completedPlans) {
                long minutes = Duration.between(p.getActualStart(), p.getActualEnd()).toMinutes();
                if (minutes >= 0) {
                    totalMinutes += minutes;
                    count++;
                }
            }
            avgDuration = count == 0 ? 0.0 : (double) totalMinutes / count;
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("completed", completed);
        stats.put("completionRate", total == 0 ? 0.0 : (double) completed / total);
        stats.put("avgDurationMinutes", avgDuration);
        stats.put("statusCount", statusCount);
        stats.put("typeCount", typeCount);
        stats.put("methodCount", methodCount);
        return stats;
    }

    /**
     * 客户互动统计: 各状态计数与互动类型分布。
     *
     * @param customerId 客户 ID
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCustomerInteractionStats(Long customerId) {
        if (customerId == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        List<Object[]> byStatus = planRepository.countByCustomerAndStatus(customerId);
        Map<String, Long> statusCount = new LinkedHashMap<>();
        long total = 0L;
        long completed = 0L;
        for (Object[] row : byStatus) {
            String status = (String) row[0];
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            statusCount.put(status, count);
            total += count;
            if (STATUS_COMPLETED.equals(status)) {
                completed += count;
            }
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("customerId", customerId);
        stats.put("total", total);
        stats.put("completed", completed);
        stats.put("completionRate", total == 0 ? 0.0 : (double) completed / total);
        stats.put("statusCount", statusCount);
        return stats;
    }

    /**
     * 负责人统计: 计划数 / 各状态 / 完成率。
     *
     * @param ownerId   负责人 ID
     * @param startTime 开始时间 (含, 可空)
     * @param endTime   结束时间 (含, 可空)
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getOwnerStats(String ownerId, LocalDateTime startTime, LocalDateTime endTime) {
        if (ownerId == null || ownerId.isBlank()) {
            throw ScrmException.badRequest("负责人 ID 不能为空");
        }
        List<Object[]> byStatus = planRepository.countByOwnerAndStatus(ownerId, startTime, endTime);
        Map<String, Long> statusCount = new LinkedHashMap<>();
        long total = 0L;
        long completed = 0L;
        for (Object[] row : byStatus) {
            String status = (String) row[0];
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            statusCount.put(status, count);
            total += count;
            if (STATUS_COMPLETED.equals(status)) {
                completed += count;
            }
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("ownerId", ownerId);
        stats.put("total", total);
        stats.put("completed", completed);
        stats.put("completionRate", total == 0 ? 0.0 : (double) completed / total);
        stats.put("statusCount", statusCount);
        return stats;
    }

    /**
     * 互动趋势: 按月聚合最近 N 个月的计划数。
     *
     * @param months 月数
     * @return 趋势列表 [{yearMonth, count}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getInteractionTrend(int months) {
        if (months <= 0) {
            throw ScrmException.badRequest("月数必须为正数");
        }
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusMonths(months).withDayOfMonth(1);
        List<Object[]> rows = planRepository.countByMonth(startDate, endDate);
        Map<String, Long> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String ym = (String) row[0];
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            map.put(ym, count);
        }
        // 补全空月份
        List<Map<String, Object>> result = new ArrayList<>();
        YearMonth ym = YearMonth.from(startDate);
        YearMonth endYm = YearMonth.from(endDate);
        while (!ym.isAfter(endYm)) {
            String key = ym.getYear() + "-" + String.format("%02d", ym.getMonthValue());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("yearMonth", key);
            m.put("count", map.getOrDefault(key, 0L));
            result.add(m);
            ym = ym.plusMonths(1);
        }
        return result;
    }

    /**
     * 完成率: 指定负责人在指定时间区间的已完成计划占比。
     *
     * @param ownerId   负责人 ID
     * @param startTime 开始时间 (含, 可空)
     * @param endTime   结束时间 (含, 可空)
     * @return 完成率
     */
    @Transactional(readOnly = true)
    public double getCompletionRate(String ownerId, LocalDateTime startTime, LocalDateTime endTime) {
        if (ownerId == null || ownerId.isBlank()) {
            throw ScrmException.badRequest("负责人 ID 不能为空");
        }
        List<Object[]> byStatus = planRepository.countByOwnerAndStatus(ownerId, startTime, endTime);
        long total = 0L;
        long completed = 0L;
        for (Object[] row : byStatus) {
            String status = (String) row[0];
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            total += count;
            if (STATUS_COMPLETED.equals(status)) {
                completed += count;
            }
        }
        return total == 0 ? 0.0 : (double) completed / total;
    }

    /**
     * 未到率: 指定负责人在指定时间区间的 NO_SHOW 计划占比。
     *
     * @param ownerId   负责人 ID
     * @param startTime 开始时间 (含, 可空)
     * @param endTime   结束时间 (含, 可空)
     * @return 未到率
     */
    @Transactional(readOnly = true)
    public double getNoShowRate(String ownerId, LocalDateTime startTime, LocalDateTime endTime) {
        if (ownerId == null || ownerId.isBlank()) {
            throw ScrmException.badRequest("负责人 ID 不能为空");
        }
        List<Object[]> byStatus = planRepository.countByOwnerAndStatus(ownerId, startTime, endTime);
        long total = 0L;
        long noShow = 0L;
        for (Object[] row : byStatus) {
            String status = (String) row[0];
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            total += count;
            if (STATUS_NO_SHOW.equals(status)) {
                noShow += count;
            }
        }
        return total == 0 ? 0.0 : (double) noShow / total;
    }

    /**
     * 最佳互动时间段: 按小时聚合计划数, 返回互动最频繁的时段。
     *
     * @param ownerId 负责人 ID (可空, 为空则查询全部)
     * @return 时段列表 [{hour, count}] (按 count DESC)
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getBestTimeSlots(String ownerId) {
        List<Object[]> rows = planRepository.countByHour(ownerId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("hour", row[0]);
            m.put("count", row[1] == null ? 0L : ((Number) row[1]).longValue());
            result.add(m);
        }
        // 按计数降序
        result.sort((a, b) -> Long.compare((Long) b.get("count"), (Long) a.get("count")));
        return result;
    }

    /**
     * 互动热力图: 按日聚合最近 N 个月的计划数。
     *
     * @param ownerId 负责人 ID (可空, 为空则查询全部)
     * @param months  月数
     * @return 热力图列表 [{date, count}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getInteractionHeatmap(String ownerId, int months) {
        if (months <= 0) {
            throw ScrmException.badRequest("月数必须为正数");
        }
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusMonths(months).withDayOfMonth(1);
        List<Object[]> rows = planRepository.countByDayHeatmap(startDate, endDate, ownerId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", row[0]);
            m.put("count", row[1] == null ? 0L : ((Number) row[1]).longValue());
            result.add(m);
        }
        return result;
    }

    /**
     * 查找空闲时间段: 基于负责人当天已有计划与工作时间, 返回可用的空闲时段。
     *
     * @param ownerId         负责人 ID
     * @param date            日期
     * @param durationMinutes 所需时长分钟
     * @return 空闲时段列表 [{start, end}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> findFreeSlots(String ownerId, LocalDate date, int durationMinutes) {
        if (ownerId == null || ownerId.isBlank()) {
            throw ScrmException.badRequest("负责人 ID 不能为空");
        }
        if (date == null) {
            throw ScrmException.badRequest("日期不能为空");
        }
        if (durationMinutes <= 0) {
            throw ScrmException.badRequest("时长必须为正数");
        }
        // 工作时间 (默认 09:00-18:00)
        LocalTime workStart = LocalTime.of(9, 0);
        LocalTime workEnd = LocalTime.of(18, 0);
        // 查询负责人当天已有计划 (非取消/非未到)
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
        Specification<ScrmInteractionPlanEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("ownerId"), ownerId));
            predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledStart"), dayStart));
            predicates.add(cb.lessThan(root.get("scheduledStart"), dayEnd));
            predicates.add(cb.notEqual(root.get("status"), STATUS_CANCELLED));
            predicates.add(cb.notEqual(root.get("status"), STATUS_NO_SHOW));
            query.orderBy(cb.asc(root.get("scheduledStart")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmInteractionPlanEntity> busy = planRepository.findAll(spec);
        // 构建忙碌区间 (LocalTime 对)
        List<LocalTime[]> busySlots = new ArrayList<>();
        for (ScrmInteractionPlanEntity plan : busy) {
            LocalTime s = plan.getScheduledStart().toLocalTime();
            LocalTime e = plan.getScheduledEnd() != null ? plan.getScheduledEnd().toLocalTime() : s.plusMinutes(30);
            busySlots.add(new LocalTime[]{s, e});
        }
        // 周末检查 (周六/周日视为非工作日)
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            log.info("查找空闲时段: 非工作日, ownerId={}, date={}", ownerId, date);
            return List.of();
        }
        // 计算空闲时段
        List<Map<String, Object>> freeSlots = new ArrayList<>();
        LocalTime cursor = workStart;
        for (LocalTime[] busyRange : busySlots) {
            // 空闲区间 [cursor, busyRange.start)
            if (cursor.isBefore(busyRange[0])) {
                long gapMin = Duration.between(cursor, busyRange[0]).toMinutes();
                if (gapMin >= durationMinutes) {
                    Map<String, Object> slot = new LinkedHashMap<>();
                    slot.put("start", LocalDateTime.of(date, cursor));
                    slot.put("end", LocalDateTime.of(date, busyRange[0]));
                    freeSlots.add(slot);
                }
            }
            // 移动游标到忙碌区间结束
            if (cursor.isBefore(busyRange[1])) {
                cursor = busyRange[1];
            }
        }
        // 末尾空闲区间 [cursor, workEnd)
        if (cursor.isBefore(workEnd)) {
            long gapMin = Duration.between(cursor, workEnd).toMinutes();
            if (gapMin >= durationMinutes) {
                Map<String, Object> slot = new LinkedHashMap<>();
                slot.put("start", LocalDateTime.of(date, cursor));
                slot.put("end", LocalDateTime.of(date, workEnd));
                freeSlots.add(slot);
            }
        }
        return freeSlots;
    }

    /**
     * 检测时间冲突: 返回与指定时间区间重叠的计划列表。
     *
     * @param ownerId 负责人 ID
     * @param start   开始时间
     * @param end     结束时间
     * @return 冲突计划列表
     */
    @Transactional(readOnly = true)
    public List<ScrmInteractionPlanEntity> detectConflicts(String ownerId, LocalDateTime start, LocalDateTime end) {
        if (ownerId == null || ownerId.isBlank()) {
            throw ScrmException.badRequest("负责人 ID 不能为空");
        }
        if (start == null || end == null) {
            throw ScrmException.badRequest("时间区间不能为空");
        }
        Specification<ScrmInteractionPlanEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("ownerId"), ownerId));
            predicates.add(root.get("status").in(ACTIVE_STATUSES));
            // 区间交集: scheduledStart < end AND scheduledEnd > start (scheduledEnd 为空时视为 scheduledStart + 30min)
            predicates.add(cb.lessThan(root.get("scheduledStart"), end));
            predicates.add(cb.or(
                    cb.isNull(root.get("scheduledEnd")),
                    cb.greaterThan(root.get("scheduledEnd"), start)));
            query.orderBy(cb.asc(root.get("scheduledStart")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return planRepository.findAll(spec);
    }

}