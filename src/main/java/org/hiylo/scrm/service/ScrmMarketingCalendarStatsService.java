/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMarketingCalendarStatsService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.entity.ScrmCalendarEventEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCalendarEventRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRM 营销日历统计服务 (统计子域)。
 * <p>
 * 承载日历概览 / 月度统计 / 渠道负载 / 负责人工作量 / 繁忙时段分析, 托管统计数值转换与
 * CSV 解析 {@link #parseCsv(String)} 供节日 / 冲突兄弟类以包级 static 复用。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
public class ScrmMarketingCalendarStatsService {

    /** 事件状态: 已计划 */
    private static final String STATUS_PLANNED = "PLANNED";
    /** 事件状态: 已确认 */
    private static final String STATUS_CONFIRMED = "CONFIRMED";
    /** 事件状态: 进行中 */
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    /** 事件状态: 已完成 */
    private static final String STATUS_COMPLETED = "COMPLETED";
    /** 事件状态: 已取消 */
    private static final String STATUS_CANCELLED = "CANCELLED";
    /** 事件状态: 已延期 */
    private static final String STATUS_POSTPONED = "POSTPONED";

    /** 事件数据访问层 */
    private final ScrmCalendarEventRepository eventRepository;

    /**
     * 日历统计概览: 事件数 / 各类型 / 各状态 / 完成率。
     *
     * @param startDate 开始日期 (含, 可空)
     * @param endDate   结束日期 (含, 可空)
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCalendarStats(LocalDate startDate, LocalDate endDate) {
        // 各状态事件数
        List<Object[]> byStatus = eventRepository.countByStatus(startDate, endDate);
        Map<String, Long> statusCount = new LinkedHashMap<>();
        statusCount.put(STATUS_PLANNED, 0L);
        statusCount.put(STATUS_CONFIRMED, 0L);
        statusCount.put(STATUS_IN_PROGRESS, 0L);
        statusCount.put(STATUS_COMPLETED, 0L);
        statusCount.put(STATUS_CANCELLED, 0L);
        statusCount.put(STATUS_POSTPONED, 0L);
        long total = 0L;
        for (Object[] row : byStatus) {
            String status = (String) row[0];
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            statusCount.put(status, count);
            total += count;
        }
        // 各类型事件数
        List<Object[]> byType = eventRepository.countByEventType(startDate, endDate);
        Map<String, Long> typeCount = new LinkedHashMap<>();
        for (Object[] row : byType) {
            String type = (String) row[0];
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            typeCount.put(type, count);
        }
        // 完成率
        long completed = statusCount.getOrDefault(STATUS_COMPLETED, 0L);
        double completionRate = total == 0 ? 0.0 : (double) completed / total;
        // 总预算
        Double sumBudget = eventRepository.sumBudget(startDate, endDate);
        double totalBudget = sumBudget == null ? 0.0 : sumBudget;
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("statusCount", statusCount);
        stats.put("typeCount", typeCount);
        stats.put("completed", completed);
        stats.put("completionRate", completionRate);
        stats.put("totalBudget", totalBudget);
        return stats;
    }

    /**
     * 月度统计: 指定月份的事件数 / 各类型 / 各状态 / 完成率。
     *
     * @param year  年份
     * @param month 月份 (1-12)
     * @return 统计结果 Map
     * @throws ScrmException 月份非法
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getMonthlyStats(int year, int month) {
        if (month < 1 || month > 12) {
            throw ScrmException.badRequest("月份非法: " + month);
        }
        YearMonth ym = YearMonth.of(year, month);
        return getCalendarStats(ym.atDay(1), ym.atEndOfMonth());
    }

    /**
     * 渠道负载: 各渠道事件分布。
     * <p>遍历查询区间内的事件, 按渠道聚合事件数与预算。</p>
     *
     * @param startDate 开始日期 (含, 可空)
     * @param endDate   结束日期 (含, 可空)
     * @return 渠道负载列表 [{channel, eventCount, totalBudget}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getChannelLoad(LocalDate startDate, LocalDate endDate) {
        Specification<ScrmCalendarEventEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startDate"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("endDate"), endDate));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmCalendarEventEntity> events = eventRepository.findAll(spec);
        Map<String, long[]> channelAgg = new LinkedHashMap<>();
        Map<String, Double> channelBudget = new LinkedHashMap<>();
        for (ScrmCalendarEventEntity event : events) {
            List<String> channels = parseCsv(event.getChannels());
            if (channels.isEmpty()) {
                channels = List.of("UNKNOWN");
            }
            for (String channel : channels) {
                long[] agg = channelAgg.computeIfAbsent(channel, k -> new long[]{0L});
                agg[0] += 1;
                channelAgg.put(channel, agg);
                double budget = event.getBudget() != null ? event.getBudget() : 0.0;
                channelBudget.merge(channel, budget, Double::sum);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : channelAgg.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("channel", entry.getKey());
            m.put("eventCount", entry.getValue()[0]);
            m.put("totalBudget", channelBudget.getOrDefault(entry.getKey(), 0.0));
            result.add(m);
        }
        return result;
    }

    /**
     * 负责人工作量: 指定负责人在区间内的事件数与预算。
     *
     * @param ownerId   负责人 ID
     * @param startDate 开始日期 (含, 可空)
     * @param endDate   结束日期 (含, 可空)
     * @return 工作量 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getOwnerWorkload(String ownerId, LocalDate startDate, LocalDate endDate) {
        if (ownerId == null || ownerId.isBlank()) {
            throw ScrmException.badRequest("负责人 ID 不能为空");
        }
        List<Object[]> agg = eventRepository.aggregateByOwner(ownerId, startDate, endDate);
        Map<String, Object> result = new LinkedHashMap<>();
        if (agg.isEmpty()) {
            result.put("ownerId", ownerId);
            result.put("ownerName", "");
            result.put("eventCount", 0L);
            result.put("totalBudget", 0.0);
            result.put("totalEstimatedReach", 0L);
        } else {
            Object[] row = agg.get(0);
            result.put("ownerId", row[0]);
            result.put("ownerName", row[1]);
            result.put("eventCount", toLong(row[2]));
            result.put("totalBudget", toDouble(row[3]));
            result.put("totalEstimatedReach", toLong(row[4]));
        }
        return result;
    }

    /**
     * 繁忙时段分析: 按日聚合事件数, 找出事件数排名前列的繁忙日期。
     *
     * @param startDate 开始日期 (含, 可空)
     * @param endDate   结束日期 (含, 可空)
     * @return 繁忙时段列表 [{date, eventCount, totalBudget}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getBusyPeriods(LocalDate startDate, LocalDate endDate) {
        Specification<ScrmCalendarEventEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.notEqual(root.get("status"), STATUS_CANCELLED));
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startDate"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("endDate"), endDate));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmCalendarEventEntity> events = eventRepository.findAll(spec);
        // 按日展开: 每个事件在其 [startDate, endDate] 区间内的每一天都计入
        Map<LocalDate, long[]> dayAgg = new LinkedHashMap<>();
        Map<LocalDate, Double> dayBudget = new LinkedHashMap<>();
        for (ScrmCalendarEventEntity event : events) {
            LocalDate d = event.getStartDate();
            while (d != null && !d.isAfter(event.getEndDate())) {
                long[] agg = dayAgg.computeIfAbsent(d, k -> new long[]{0L});
                agg[0] += 1;
                dayAgg.put(d, agg);
                double budget = event.getBudget() != null ? event.getBudget() : 0.0;
                // 预算按事件持续天数均摊
                long duration = event.getEndDate().toEpochDay() - event.getStartDate().toEpochDay() + 1;
                dayBudget.merge(d, budget / Math.max(1, duration), Double::sum);
                d = d.plusDays(1);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<LocalDate, long[]> entry : dayAgg.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", entry.getKey());
            m.put("eventCount", entry.getValue()[0]);
            m.put("totalBudget", dayBudget.getOrDefault(entry.getKey(), 0.0));
            result.add(m);
        }
        // 按事件数降序, 取前 20
        result.sort((a, b) -> Long.compare(((Number) b.get("eventCount")).longValue(),
                ((Number) a.get("eventCount")).longValue()));
        if (result.size() > 20) {
            return result.subList(0, 20);
        }
        return result;
    }

    /**
     * 解析逗号分隔字符串为列表。
     *
     * @param csv 逗号分隔字符串 (可空)
     * @return 字符串列表 (空则返回空列表)
     */
    static List<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String s : csv.split(",")) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * 将对象转换为 double 数值。
     *
     * @param obj 对象
     * @return double 值, 不可转换时返回 0
     */
    private double toDouble(Object obj) {
        if (obj == null) {
            return 0;
        }
        if (obj instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(obj.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 将对象转换为 long 数值。
     *
     * @param obj 对象
     * @return long 值, 不可转换时返回 0
     */
    private long toLong(Object obj) {
        if (obj == null) {
            return 0;
        }
        if (obj instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(obj.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}