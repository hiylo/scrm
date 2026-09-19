/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWorkOrderStatsService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.entity.ScrmWorkOrderEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmWorkOrderRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SCRM 工单统计服务。
 * <p>
 * 承载工单统计子域: 工单统计、SLA 统计、响应/解决/满意度统计、处理人工作量、类型/状态分布、
 * 月度趋势、Top 处理人与产品、工单概览。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
public class ScrmWorkOrderStatsService {

    /** 工单数据访问层 */
    private final ScrmWorkOrderRepository orderRepository;
    /** 工单订单管理服务 (共享工具) */
    private final ScrmWorkOrderCrudService crudService;

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 工单统计: 总数 / 各类型 / 各状态 / 各优先级。
     * <p>时间范围按工单创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getWorkOrderStats(LocalDateTime startTime, LocalDateTime endTime) {
        Specification<ScrmWorkOrderEntity> spec = crudService.buildTimeRangeSpec(startTime, endTime);
        List<ScrmWorkOrderEntity> orders = orderRepository.findAll(spec);
        Map<String, Long> typeCount = new LinkedHashMap<>();
        for (String t : Arrays.asList("COMPLAINT", "CONSULTATION", "MAINTENANCE", "INSTALLATION",
                "REPAIR", "SERVICE_REQUEST", "TECH_SUPPORT", "BILLING", "RETURN", "EXCHANGE",
                "FEEDBACK", "OTHER")) {
            typeCount.put(t, 0L);
        }
        Map<String, Long> statusCount = new LinkedHashMap<>();
        for (String s : Arrays.asList(ScrmWorkOrderCrudService.STATUS_OPEN, ScrmWorkOrderCrudService.STATUS_ASSIGNED,
                ScrmWorkOrderCrudService.STATUS_IN_PROGRESS,
                ScrmWorkOrderCrudService.STATUS_PENDING_CUSTOMER, ScrmWorkOrderCrudService.STATUS_RESOLVED,
                ScrmWorkOrderCrudService.STATUS_CLOSED, ScrmWorkOrderCrudService.STATUS_CANCELLED,
                ScrmWorkOrderCrudService.STATUS_REOPENED)) {
            statusCount.put(s, 0L);
        }
        Map<String, Long> priorityCount = new LinkedHashMap<>();
        for (String p : Arrays.asList(ScrmWorkOrderCrudService.PRIORITY_URGENT, ScrmWorkOrderCrudService.PRIORITY_HIGH,
                ScrmWorkOrderCrudService.PRIORITY_NORMAL, ScrmWorkOrderCrudService.PRIORITY_LOW)) {
            priorityCount.put(p, 0L);
        }
        for (ScrmWorkOrderEntity o : orders) {
            if (o.getOrderType() != null) {
                typeCount.merge(o.getOrderType(), 1L, Long::sum);
            }
            if (o.getOrderStatus() != null) {
                statusCount.merge(o.getOrderStatus(), 1L, Long::sum);
            }
            if (o.getPriority() != null) {
                priorityCount.merge(o.getPriority(), 1L, Long::sum);
            }
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", (long) orders.size());
        stats.put("byType", typeCount);
        stats.put("byStatus", statusCount);
        stats.put("byPriority", priorityCount);
        return stats;
    }

    /**
     * SLA 统计: 总数 / 违规数 / 达标数 / 违规率 / 平均响应与解决时长。
     * <p>时间范围按工单创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return SLA 统计
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSlaStats(LocalDateTime startTime, LocalDateTime endTime) {
        long total = orderRepository.countByTimeRange(startTime, endTime);
        long breached = orderRepository.countSlaBreached(startTime, endTime);
        Double avgResponse = orderRepository.avgResponseMinutes(startTime, endTime);
        Double avgResolution = orderRepository.avgResolutionMinutes(startTime, endTime);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("breached", breached);
        stats.put("met", Math.max(0, total - breached));
        stats.put("breachRate", total > 0 ? Math.round((double) breached / total * 10000d) / 100d : 0d);
        stats.put("avgResponseMinutes", avgResponse != null ? Math.round(avgResponse) : 0);
        stats.put("avgResolutionMinutes", avgResolution != null ? Math.round(avgResolution) : 0);
        return stats;
    }

    /**
     * 响应时间统计: 平均/最大/最小响应时长。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 响应时间统计
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getResponseTimeStats(LocalDateTime startTime, LocalDateTime endTime) {
        Specification<ScrmWorkOrderEntity> spec = crudService.buildTimeRangeSpec(startTime, endTime);
        List<ScrmWorkOrderEntity> orders = orderRepository.findAll(spec);
        long sum = 0;
        int count = 0;
        int max = 0;
        int min = Integer.MAX_VALUE;
        for (ScrmWorkOrderEntity o : orders) {
            if (o.getResponseTimeMinutes() != null && o.getResponseTimeMinutes() >= 0) {
                sum += o.getResponseTimeMinutes();
                count++;
                if (o.getResponseTimeMinutes() > max) {
                    max = o.getResponseTimeMinutes();
                }
                if (o.getResponseTimeMinutes() < min) {
                    min = o.getResponseTimeMinutes();
                }
            }
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("count", count);
        stats.put("avgMinutes", count > 0 ? Math.round((double) sum / count) : 0);
        stats.put("maxMinutes", count > 0 ? max : 0);
        stats.put("minMinutes", count > 0 ? min : 0);
        return stats;
    }

    /**
     * 解决时间统计: 平均/最大/最小解决时长。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 解决时间统计
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getResolutionTimeStats(LocalDateTime startTime, LocalDateTime endTime) {
        Specification<ScrmWorkOrderEntity> spec = crudService.buildTimeRangeSpec(startTime, endTime);
        List<ScrmWorkOrderEntity> orders = orderRepository.findAll(spec);
        long sum = 0;
        int count = 0;
        int max = 0;
        int min = Integer.MAX_VALUE;
        for (ScrmWorkOrderEntity o : orders) {
            if (o.getResolutionTimeMinutes() != null && o.getResolutionTimeMinutes() >= 0) {
                sum += o.getResolutionTimeMinutes();
                count++;
                if (o.getResolutionTimeMinutes() > max) {
                    max = o.getResolutionTimeMinutes();
                }
                if (o.getResolutionTimeMinutes() < min) {
                    min = o.getResolutionTimeMinutes();
                }
            }
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("count", count);
        stats.put("avgMinutes", count > 0 ? Math.round((double) sum / count) : 0);
        stats.put("maxMinutes", count > 0 ? max : 0);
        stats.put("minMinutes", count > 0 ? min : 0);
        return stats;
    }

    /**
     * 满意度统计: 平均分 / 评价数 / 各分数段分布。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 满意度统计
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSatisfactionStats(LocalDateTime startTime, LocalDateTime endTime) {
        Specification<ScrmWorkOrderEntity> spec = crudService.buildTimeRangeSpec(startTime, endTime);
        List<ScrmWorkOrderEntity> orders = orderRepository.findAll(spec);
        long sum = 0;
        int count = 0;
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) {
            distribution.put(i, 0L);
        }
        for (ScrmWorkOrderEntity o : orders) {
            if (o.getSatisfactionScore() != null && o.getSatisfactionScore() >= 1 && o.getSatisfactionScore() <= 5) {
                sum += o.getSatisfactionScore();
                count++;
                distribution.merge(o.getSatisfactionScore(), 1L, Long::sum);
            }
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("count", count);
        stats.put("avgScore", count > 0 ? Math.round((double) sum / count * 100d) / 100d : 0d);
        stats.put("distribution", distribution);
        return stats;
    }

    /**
     * 处理人工作量统计: 各状态工单数。
     *
     * @param assigneeId 处理人 ID
     * @return 统计结果
     * @throws ScrmException 处理人 ID 为空
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getAssigneeWorkload(Long assigneeId) throws ScrmException {
        if (assigneeId == null) {
            throw ScrmException.badRequest("处理人 ID 不能为空");
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("assigneeId", assigneeId);
        stats.put("openCount", orderRepository.countByAssignedToIdAndOrderStatusIn(
                 assigneeId, List.of(ScrmWorkOrderCrudService.STATUS_OPEN, ScrmWorkOrderCrudService.STATUS_ASSIGNED,
                        ScrmWorkOrderCrudService.STATUS_REOPENED)));
        stats.put("inProgressCount", orderRepository.countByAssignedToIdAndOrderStatusIn(
                 assigneeId, List.of(ScrmWorkOrderCrudService.STATUS_IN_PROGRESS,
                        ScrmWorkOrderCrudService.STATUS_PENDING_CUSTOMER)));
        stats.put("resolvedCount", orderRepository.countByAssignedToIdAndOrderStatusIn(
                 assigneeId, List.of(ScrmWorkOrderCrudService.STATUS_RESOLVED)));
        stats.put("closedCount", orderRepository.countByAssignedToIdAndOrderStatusIn(
                 assigneeId, List.of(ScrmWorkOrderCrudService.STATUS_CLOSED)));
        stats.put("cancelledCount", orderRepository.countByAssignedToIdAndOrderStatusIn(
                 assigneeId, List.of(ScrmWorkOrderCrudService.STATUS_CANCELLED)));
        stats.put("totalCount", orderRepository.countByAssignedToIdAndOrderStatusIn(
                 assigneeId, Arrays.asList(ScrmWorkOrderCrudService.STATUS_OPEN, ScrmWorkOrderCrudService.STATUS_ASSIGNED,
                        ScrmWorkOrderCrudService.STATUS_IN_PROGRESS,
                        ScrmWorkOrderCrudService.STATUS_PENDING_CUSTOMER, ScrmWorkOrderCrudService.STATUS_RESOLVED,
                        ScrmWorkOrderCrudService.STATUS_CLOSED, ScrmWorkOrderCrudService.STATUS_CANCELLED,
                        ScrmWorkOrderCrudService.STATUS_REOPENED)));
        return stats;
    }

    /**
     * 工单类型分布。
     *
     * @return 类型分布
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getOrderTypeDistribution() {
        return getWorkOrderStats(null, null);
    }

    /**
     * 工单状态分布。
     *
     * @return 状态分布
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getOrderStatusDistribution() {
        Specification<ScrmWorkOrderEntity> spec = (root, query, cb) ->
                cb.and();
        List<ScrmWorkOrderEntity> orders = orderRepository.findAll(spec);
        Map<String, Long> statusCount = new LinkedHashMap<>();
        for (String s : Arrays.asList(ScrmWorkOrderCrudService.STATUS_OPEN, ScrmWorkOrderCrudService.STATUS_ASSIGNED,
                ScrmWorkOrderCrudService.STATUS_IN_PROGRESS,
                ScrmWorkOrderCrudService.STATUS_PENDING_CUSTOMER, ScrmWorkOrderCrudService.STATUS_RESOLVED,
                ScrmWorkOrderCrudService.STATUS_CLOSED, ScrmWorkOrderCrudService.STATUS_CANCELLED,
                ScrmWorkOrderCrudService.STATUS_REOPENED)) {
            statusCount.put(s, 0L);
        }
        for (ScrmWorkOrderEntity o : orders) {
            if (o.getOrderStatus() != null) {
                statusCount.merge(o.getOrderStatus(), 1L, Long::sum);
            }
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", (long) orders.size());
        stats.put("byStatus", statusCount);
        return stats;
    }

    /**
     * 工单趋势 (按月统计最近 N 个月的工单数)。
     *
     * @param months 月数
     * @return 趋势数据
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getOrderTrend(int months) {
        if (months <= 0) {
            months = 6;
        }
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusMonths(months);
        Specification<ScrmWorkOrderEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), endTime));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmWorkOrderEntity> orders = orderRepository.findAll(spec);
        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("yyyy-MM");
        Map<String, Long> trend = new LinkedHashMap<>();
        for (int i = months - 1; i >= 0; i--) {
            trend.put(endTime.minusMonths(i).format(monthFmt), 0L);
        }
        for (ScrmWorkOrderEntity o : orders) {
            if (o.getCreateTime() != null) {
                String key = o.getCreateTime().format(monthFmt);
                trend.merge(key, 1L, Long::sum);
            }
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("months", months);
        stats.put("trend", trend);
        return stats;
    }

    /**
     * Top 处理人 (按已解决/已关闭工单数排名)。
     *
     * @param limit 返回数量
     * @return Top 处理人列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTopAssignees(int limit) {
        if (limit <= 0) {
            limit = 10;
        }
        Specification<ScrmWorkOrderEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("orderStatus").in(Arrays.asList(ScrmWorkOrderCrudService.STATUS_RESOLVED,
                    ScrmWorkOrderCrudService.STATUS_CLOSED)));
            predicates.add(root.get("assignedToId").isNotNull());
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmWorkOrderEntity> orders = orderRepository.findAll(spec);
        Map<Long, long[]> counter = new LinkedHashMap<>();
        Map<Long, String> nameMap = new LinkedHashMap<>();
        for (ScrmWorkOrderEntity o : orders) {
            if (o.getAssignedToId() == null) {
                continue;
            }
            long[] arr = counter.computeIfAbsent(o.getAssignedToId(), k -> new long[1]);
            arr[0]++;
            if (o.getAssignedTo() != null) {
                nameMap.putIfAbsent(o.getAssignedToId(), o.getAssignedTo());
            }
        }
        return counter.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .limit(limit)
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("assigneeId", e.getKey());
                    item.put("assigneeName", nameMap.get(e.getKey()));
                    item.put("resolvedCount", e.getValue()[0]);
                    return item;
                })
                .collect(Collectors.toList());
    }

    /**
     * Top 产品 (按工单数排名)。
     *
     * @param limit 返回数量
     * @return Top 产品列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTopProducts(int limit) {
        if (limit <= 0) {
            limit = 10;
        }
        Specification<ScrmWorkOrderEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("productId").isNotNull());
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmWorkOrderEntity> orders = orderRepository.findAll(spec);
        Map<Long, long[]> counter = new LinkedHashMap<>();
        Map<Long, String> nameMap = new LinkedHashMap<>();
        for (ScrmWorkOrderEntity o : orders) {
            if (o.getProductId() == null) {
                continue;
            }
            long[] arr = counter.computeIfAbsent(o.getProductId(), k -> new long[1]);
            arr[0]++;
            if (o.getProductName() != null) {
                nameMap.putIfAbsent(o.getProductId(), o.getProductName());
            }
        }
        return counter.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .limit(limit)
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("productId", e.getKey());
                    item.put("productName", nameMap.get(e.getKey()));
                    item.put("orderCount", e.getValue()[0]);
                    return item;
                })
                .collect(Collectors.toList());
    }

    /**
     * 工单概览 (总数 / 各状态 / 各优先级 / SLA 违规 / 紧急 / 超期)。
     *
     * @return 概览数据
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getWorkOrderOverview() {
        Specification<ScrmWorkOrderEntity> spec = (root, query, cb) ->
                cb.and();
        List<ScrmWorkOrderEntity> orders = orderRepository.findAll(spec);
        Map<String, Long> statusCount = new LinkedHashMap<>();
        Map<String, Long> priorityCount = new LinkedHashMap<>();
        long breached = 0;
        long urgent = 0;
        long overdue = 0;
        LocalDateTime now = LocalDateTime.now();
        for (ScrmWorkOrderEntity o : orders) {
            if (o.getOrderStatus() != null) {
                statusCount.merge(o.getOrderStatus(), 1L, Long::sum);
            }
            if (o.getPriority() != null) {
                priorityCount.merge(o.getPriority(), 1L, Long::sum);
            }
            if (Boolean.TRUE.equals(o.getSlaBreached())) {
                breached++;
            }
            if (Boolean.TRUE.equals(o.getIsUrgent())) {
                urgent++;
            }
            if (o.getSlaResolutionDue() != null && o.getSlaResolutionDue().isBefore(now)
                    && ScrmWorkOrderCrudService.OPEN_STATUSES.contains(o.getOrderStatus())) {
                overdue++;
            }
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", (long) orders.size());
        stats.put("byStatus", statusCount);
        stats.put("byPriority", priorityCount);
        stats.put("slaBreachedCount", breached);
        stats.put("urgentCount", urgent);
        stats.put("overdueCount", overdue);
        return stats;
    }
}