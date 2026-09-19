/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMembershipStatsService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmCustomerMembershipEntity;
import org.hiylo.scrm.entity.ScrmMembershipBenefitEntity;
import org.hiylo.scrm.entity.ScrmMembershipTierEntity;
import org.hiylo.scrm.repository.ScrmCustomerMembershipRepository;
import org.hiylo.scrm.repository.ScrmMembershipBenefitRepository;
import org.hiylo.scrm.repository.ScrmMembershipTierRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SCRM 客户会员统计趋势服务。
 * <p>
 * 承载会员体系多维统计能力: 会员总览、等级分布、升级统计、留存统计、权益统计、收入统计与
 * 增长趋势 (按月新增)、同期群分析 (按加入月份分组) 等。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026/09/19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmMembershipStatsService {

    /** 会员状态: 活跃 */
    private static final String STATUS_ACTIVE = "ACTIVE";
    /** 会员状态: 冻结 */
    private static final String STATUS_FROZEN = "FROZEN";
    /** 会员状态: 过期 */
    private static final String STATUS_EXPIRED = "EXPIRED";
    /** 会员状态: 取消 */
    private static final String STATUS_CANCELLED = "CANCELLED";
    /** 会员状态: 待定 */
    private static final String STATUS_PENDING = "PENDING";

    /** 权益状态: 活跃 */
    private static final String BENEFIT_STATUS_ACTIVE = "ACTIVE";

    /** 默认每页条数上限 */
    private static final int DEFAULT_LIMIT = 10;
    /** 趋势日期格式 */
    private static final DateTimeFormatter TREND_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    /** 活跃状态集合 (留存统计用) */
    private static final Set<String> ACTIVE_STATUSES = new HashSet<>(Arrays.asList(
            STATUS_ACTIVE, STATUS_FROZEN));

    /** 会员等级数据访问层 */
    private final ScrmMembershipTierRepository tierRepository;
    /** 客户会员数据访问层 */
    private final ScrmCustomerMembershipRepository membershipRepository;
    /** 会员权益数据访问层 */
    private final ScrmMembershipBenefitRepository benefitRepository;

    /**
     * 会员统计: 总数 / 各等级数 / 活跃率 / 平均消费。
     *
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getMembershipStats() {
        Specification<ScrmCustomerMembershipEntity> spec = (root, query, cb) ->
                cb.and();
        List<ScrmCustomerMembershipEntity> members = membershipRepository.findAll(spec);
        long total = members.size();
        long active = members.stream().filter(m -> ACTIVE_STATUSES.contains(m.getMembershipStatus())).count();
        double totalSpend = members.stream()
                .filter(m -> m.getTotalSpend() != null)
                .mapToDouble(ScrmCustomerMembershipEntity::getTotalSpend).sum();
        Map<String, Long> statusCount = new LinkedHashMap<>();
        for (String s : Arrays.asList(STATUS_ACTIVE, STATUS_FROZEN, STATUS_EXPIRED, STATUS_CANCELLED, STATUS_PENDING)) {
            statusCount.put(s, 0L);
        }
        for (ScrmCustomerMembershipEntity m : members) {
            if (m.getMembershipStatus() != null) {
                statusCount.merge(m.getMembershipStatus(), 1L, Long::sum);
            }
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalMembers", total);
        stats.put("activeMembers", active);
        stats.put("activeRate", total > 0
                ? Math.round((double) active / total * 10000d) / 100d : 0);
        stats.put("byStatus", statusCount);
        stats.put("totalSpend", Math.round(totalSpend * 100d) / 100d);
        stats.put("avgSpend", total > 0
                ? Math.round(totalSpend / total * 100d) / 100d : 0);
        return stats;
    }

    /**
     * 等级分布: 各等级的会员数 / 总消费 / 平均消费。
     *
     * @return 等级分布列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTierDistribution() {
        List<ScrmMembershipTierEntity> tiers = tierRepository.findAllByOrderByTierLevelAsc();
        List<Map<String, Object>> distribution = new ArrayList<>();
        for (ScrmMembershipTierEntity tier : tiers) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("tierId", tier.getId());
            item.put("tierName", tier.getTierName());
            item.put("tierCode", tier.getTierCode());
            item.put("tierLevel", tier.getTierLevel());
            item.put("memberCount", tier.getMemberCount() != null ? tier.getMemberCount() : 0);
            item.put("totalSpend", tier.getTotalSpend() != null ? tier.getTotalSpend() : 0);
            item.put("avgSpend", tier.getAvgSpend() != null ? tier.getAvgSpend() : 0);
            distribution.add(item);
        }
        // 补充无等级匹配的会员统计 (等级被删除时)
        Set<Long> tierIds = tiers.stream().map(ScrmMembershipTierEntity::getId).collect(Collectors.toSet());
        Specification<ScrmCustomerMembershipEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!tierIds.isEmpty()) {
                predicates.add(cb.not(root.get("tierId").in(tierIds)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        long orphans = membershipRepository.count(spec);
        if (orphans > 0) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("tierId", null);
            item.put("tierName", "未匹配等级");
            item.put("tierCode", null);
            item.put("tierLevel", null);
            item.put("memberCount", orphans);
            item.put("totalSpend", 0);
            item.put("avgSpend", 0);
            distribution.add(item);
        }
        return distribution;
    }

    /**
     * 升级统计: 时间范围内各等级的升级次数 (按 lastUpgradeDate 过滤)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 升级统计
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getUpgradeStats(LocalDateTime startTime, LocalDateTime endTime) {
        Specification<ScrmCustomerMembershipEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNotNull(root.get("lastUpgradeDate")));
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("lastUpgradeDate"), startTime.toLocalDate()));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("lastUpgradeDate"), endTime.toLocalDate()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmCustomerMembershipEntity> members = membershipRepository.findAll(spec);
        Map<String, Long> byTier = new LinkedHashMap<>();
        for (ScrmCustomerMembershipEntity m : members) {
            String key = m.getTierName() != null ? m.getTierName() : "unknown";
            byTier.merge(key, 1L, Long::sum);
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUpgrades", members.size());
        stats.put("byTier", byTier);
        return stats;
    }

    /**
     * 留存统计: 按 period 月数统计加入 N 月后仍活跃的会员数。
     *
     * @param period 月数 (默认 3)
     * @return 留存统计
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getRetentionStats(int period) {
        if (period <= 0) {
            period = 3;
        }
        Specification<ScrmCustomerMembershipEntity> spec = (root, query, cb) ->
                cb.and();
        List<ScrmCustomerMembershipEntity> members = membershipRepository.findAll(spec);
        LocalDate today = LocalDate.now();
        LocalDate cohortStart = today.minusMonths(period);
        long cohortTotal = members.stream()
                .filter(m -> m.getJoinDate() != null && !m.getJoinDate().isAfter(cohortStart)).count();
        long cohortRetained = members.stream()
                .filter(m -> m.getJoinDate() != null && !m.getJoinDate().isAfter(cohortStart))
                .filter(m -> ACTIVE_STATUSES.contains(m.getMembershipStatus())).count();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("period", period);
        stats.put("cohortTotal", cohortTotal);
        stats.put("cohortRetained", cohortRetained);
        stats.put("retentionRate", cohortTotal > 0
                ? Math.round((double) cohortRetained / cohortTotal * 10000d) / 100d : 0);
        return stats;
    }

    /**
     * 权益统计: 使用率 / 节省金额 / 热门权益。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getBenefitStats(LocalDateTime startTime, LocalDateTime endTime) {
        Specification<ScrmMembershipBenefitEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmMembershipBenefitEntity> benefits = benefitRepository.findAll(spec);
        long total = benefits.size();
        long active = benefits.stream().filter(b -> BENEFIT_STATUS_ACTIVE.equals(b.getStatus())).count();
        long used = benefits.stream()
                .filter(b -> b.getCurrentUsageCount() != null && b.getCurrentUsageCount() > 0).count();
        double totalRedeemed = benefits.stream()
                .filter(b -> b.getTotalRedeemedValue() != null)
                .mapToDouble(ScrmMembershipBenefitEntity::getTotalRedeemedValue).sum();
        double totalSaved = benefits.stream()
                .filter(b -> b.getTotalSavedAmount() != null)
                .mapToDouble(ScrmMembershipBenefitEntity::getTotalSavedAmount).sum();
        Map<String, Long> byType = new LinkedHashMap<>();
        for (ScrmMembershipBenefitEntity b : benefits) {
            if (b.getBenefitType() != null) {
                byType.merge(b.getBenefitType(), 1L, Long::sum);
            }
        }
        List<Map<String, Object>> popular = benefits.stream()
                .sorted((a, bb) -> Double.compare(
                        bb.getTotalRedeemedValue() != null ? bb.getTotalRedeemedValue() : 0d,
                        a.getTotalRedeemedValue() != null ? a.getTotalRedeemedValue() : 0d))
                .limit(DEFAULT_LIMIT)
                .map(b -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("benefitId", b.getId());
                    item.put("benefitName", b.getBenefitName());
                    item.put("benefitType", b.getBenefitType());
                    item.put("currentUsageCount", b.getCurrentUsageCount());
                    item.put("totalRedeemedValue", b.getTotalRedeemedValue());
                    return item;
                })
                .collect(Collectors.toList());
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalBenefits", total);
        stats.put("activeBenefits", active);
        stats.put("usedBenefits", used);
        stats.put("usageRate", total > 0
                ? Math.round((double) used / total * 10000d) / 100d : 0);
        stats.put("byType", byType);
        stats.put("totalRedeemedValue", Math.round(totalRedeemed * 100d) / 100d);
        stats.put("totalSavedAmount", Math.round(totalSaved * 100d) / 100d);
        stats.put("popularBenefits", popular);
        return stats;
    }

    /**
     * 会员收入统计: 累计消费 / 各等级收入。
     *
     * @param startTime 起始时间 (可空, 按会员创建时间过滤)
     * @param endTime   截止时间 (可空, 按会员创建时间过滤)
     * @return 收入统计
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getRevenueStats(LocalDateTime startTime, LocalDateTime endTime) {
        Double totalRevenue = membershipRepository.sumTotalSpend(startTime, endTime);
        Specification<ScrmCustomerMembershipEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmCustomerMembershipEntity> members = membershipRepository.findAll(spec);
        Map<String, Double> byTier = new LinkedHashMap<>();
        Map<String, Long> countByTier = new LinkedHashMap<>();
        for (ScrmCustomerMembershipEntity m : members) {
            String key = m.getTierName() != null ? m.getTierName() : "unknown";
            byTier.merge(key, m.getTotalSpend() != null ? m.getTotalSpend() : 0d, Double::sum);
            countByTier.merge(key, 1L, Long::sum);
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalRevenue", totalRevenue != null ? Math.round(totalRevenue * 100d) / 100d : 0);
        stats.put("memberCount", members.size());
        stats.put("avgRevenue", members.size() > 0 && totalRevenue != null
                ? Math.round(totalRevenue / members.size() * 100d) / 100d : 0);
        stats.put("byTier", byTier);
        stats.put("countByTier", countByTier);
        return stats;
    }

    /**
     * 会员增长趋势: 按月统计最近 N 月的新增会员数。
     *
     * @param months 月数 (默认 6)
     * @return 趋势数据 (month + count)
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getMembershipTrend(int months) {
        if (months <= 0) {
            months = 6;
        }
        YearMonth currentMonth = YearMonth.now();
        YearMonth startMonth = currentMonth.minusMonths(months - 1L);
        LocalDateTime startTime = startMonth.atDay(1).atStartOfDay();
        LocalDateTime endTime = currentMonth.plusMonths(1).atDay(1).atStartOfDay();
        Specification<ScrmCustomerMembershipEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            predicates.add(cb.lessThan(root.get("createTime"), endTime));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmCustomerMembershipEntity> members = membershipRepository.findAll(spec);
        Map<String, Long> monthlyCount = new LinkedHashMap<>();
        for (int i = 0; i < months; i++) {
            monthlyCount.put(startMonth.plusMonths(i).format(TREND_MONTH_FORMAT), 0L);
        }
        for (ScrmCustomerMembershipEntity m : members) {
            if (m.getCreateTime() != null) {
                String month = m.getCreateTime().format(TREND_MONTH_FORMAT);
                monthlyCount.merge(month, 1L, Long::sum);
            }
        }
        List<Map<String, Object>> trend = new ArrayList<>();
        for (Map.Entry<String, Long> e : monthlyCount.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("month", e.getKey());
            item.put("count", e.getValue());
            trend.add(item);
        }
        return trend;
    }

    /**
     * 同期群分析: 按加入月份分组统计各 cohort 的会员数与活跃数。
     *
     * @param period 月数 (默认 6, 统计最近 N 月各月加入的 cohort)
     * @return 同期群分析
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCohortAnalysis(int period) {
        if (period <= 0) {
            period = 6;
        }
        Specification<ScrmCustomerMembershipEntity> spec = (root, query, cb) ->
                cb.and();
        List<ScrmCustomerMembershipEntity> members = membershipRepository.findAll(spec);
        YearMonth currentMonth = YearMonth.now();
        YearMonth startMonth = currentMonth.minusMonths(period - 1L);
        Map<String, List<ScrmCustomerMembershipEntity>> cohorts = new LinkedHashMap<>();
        for (int i = 0; i < period; i++) {
            cohorts.put(startMonth.plusMonths(i).format(TREND_MONTH_FORMAT), new ArrayList<>());
        }
        for (ScrmCustomerMembershipEntity m : members) {
            if (m.getJoinDate() != null) {
                String month = YearMonth.from(m.getJoinDate()).format(TREND_MONTH_FORMAT);
                if (cohorts.containsKey(month)) {
                    cohorts.get(month).add(m);
                }
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<ScrmCustomerMembershipEntity>> e : cohorts.entrySet()) {
            List<ScrmCustomerMembershipEntity> cohort = e.getValue();
            long total = cohort.size();
            long active = cohort.stream().filter(m -> ACTIVE_STATUSES.contains(m.getMembershipStatus())).count();
            double totalSpend = cohort.stream()
                    .filter(m -> m.getTotalSpend() != null)
                    .mapToDouble(ScrmCustomerMembershipEntity::getTotalSpend).sum();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("cohort", e.getKey());
            item.put("totalMembers", total);
            item.put("activeMembers", active);
            item.put("retentionRate", total > 0
                    ? Math.round((double) active / total * 10000d) / 100d : 0);
            item.put("totalSpend", Math.round(totalSpend * 100d) / 100d);
            item.put("avgSpend", total > 0
                    ? Math.round(totalSpend / total * 100d) / 100d : 0);
            result.add(item);
        }
        return result;
    }
}