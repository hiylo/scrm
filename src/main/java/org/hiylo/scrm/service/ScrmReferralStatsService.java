/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmReferralStatsService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.entity.ScrmReferralEntity;
import org.hiylo.scrm.entity.ScrmReferralProgramEntity;
import org.hiylo.scrm.entity.ScrmReferralRewardEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmReferralRewardRepository;
import org.hiylo.scrm.repository.ScrmReferralRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SCRM 客户推荐管理 - 统计排行子域服务。
 * <p>
 * 承载推荐总览统计、活动统计、推荐人排行、推荐趋势、转化漏斗与奖励统计等
 * 只读聚合能力。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
public class ScrmReferralStatsService {

    /** 默认每页条数上限 */
    private static final int DEFAULT_LIMIT = 10;

    /** 默认趋势天数 */
    private static final int DEFAULT_TREND_DAYS = 7;

    /** 趋势日期格式 */
    private static final DateTimeFormatter TREND_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 推荐关系数据访问层 */
    private final ScrmReferralRepository referralRepository;

    /** 推荐奖励数据访问层 */
    private final ScrmReferralRewardRepository rewardRepository;

    /** 推荐活动子域服务 (活动校验) */
    private final ScrmReferralProgramService programService;

    /**
     * 推荐统计: 总推荐数 / 成功率 / 转化率 / 总奖励。
     * <p>时间范围按推荐创建时间过滤, 为空时统计全量。转化率 = 成功推荐数 / 总推荐数;
     * 奖励统计来自奖励表汇总 (排除已取消)。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getReferralStats(LocalDateTime startTime, LocalDateTime endTime) {
        Specification<ScrmReferralEntity> spec = buildTimeRangeSpec(startTime, endTime);
        List<ScrmReferralEntity> referrals = referralRepository.findAll(spec);
        long total = referrals.size();
        long successful = referrals.stream()
                .filter(r -> ScrmReferralProgramService.SUCCESSFUL_STATUSES.contains(r.getStatus())).count();
        long rewarded = referrals.stream()
                .filter(r -> ScrmReferralProgramService.REFERRAL_STATUS_REWARDED.equals(r.getStatus())).count();
        Map<String, Long> statusCount = new LinkedHashMap<>();
        for (String s : Arrays.asList(ScrmReferralProgramService.REFERRAL_STATUS_PENDING,
                ScrmReferralProgramService.REFERRAL_STATUS_SIGNED_UP,
                ScrmReferralProgramService.REFERRAL_STATUS_QUALIFIED,
                ScrmReferralProgramService.REFERRAL_STATUS_REWARDED,
                ScrmReferralProgramService.REFERRAL_STATUS_EXPIRED,
                ScrmReferralProgramService.REFERRAL_STATUS_CANCELLED)) {
            statusCount.put(s, 0L);
        }
        for (ScrmReferralEntity r : referrals) {
            if (r.getStatus() != null) {
                statusCount.merge(r.getStatus(), 1L, Long::sum);
            }
        }
        Double totalReward = rewardRepository.sumRewardValue(startTime, endTime);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalReferrals", total);
        stats.put("successfulReferrals", successful);
        stats.put("rewardedReferrals", rewarded);
        stats.put("successRate", total > 0
                ? Math.round((double) successful / total * 10000d) / 100d : 0);
        stats.put("conversionRate", total > 0
                ? Math.round((double) rewarded / total * 10000d) / 100d : 0);
        stats.put("byStatus", statusCount);
        stats.put("totalRewardValue", totalReward != null ? Math.round(totalReward * 100d) / 100d : 0);
        return stats;
    }

    /**
     * 活动统计: 总推荐数 / 成功推荐数 / 累计奖励价值 / 状态分布。
     *
     * @param programId 活动 ID
     * @return 统计结果
     * @throws ScrmException 活动不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getProgramStats(Long programId) throws ScrmException {
        ScrmReferralProgramEntity program = programService.findProgramOrThrow(programId);
        long total = referralRepository.countByProgramId(programId);
        long successful = referralRepository.countByProgramIdAndStatusIn(
                 programId, new ArrayList<>(ScrmReferralProgramService.SUCCESSFUL_STATUSES));
        // 状态分布
        Specification<ScrmReferralEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("programId"), programId));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmReferralEntity> referrals = referralRepository.findAll(spec);
        Map<String, Long> statusCount = new LinkedHashMap<>();
        for (String s : Arrays.asList(ScrmReferralProgramService.REFERRAL_STATUS_PENDING,
                ScrmReferralProgramService.REFERRAL_STATUS_SIGNED_UP,
                ScrmReferralProgramService.REFERRAL_STATUS_QUALIFIED,
                ScrmReferralProgramService.REFERRAL_STATUS_REWARDED,
                ScrmReferralProgramService.REFERRAL_STATUS_EXPIRED,
                ScrmReferralProgramService.REFERRAL_STATUS_CANCELLED)) {
            statusCount.put(s, 0L);
        }
        for (ScrmReferralEntity r : referrals) {
            if (r.getStatus() != null) {
                statusCount.merge(r.getStatus(), 1L, Long::sum);
            }
        }
        // 活动累计奖励价值
        Specification<ScrmReferralRewardEntity> rewardSpec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("programId"), programId));
            predicates.add(cb.notEqual(root.get("status"),
                    ScrmReferralProgramService.REWARD_STATUS_CANCELLED));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmReferralRewardEntity> rewards = rewardRepository.findAll(rewardSpec);
        double totalReward = rewards.stream()
                .filter(r -> r.getRewardValue() != null)
                .mapToDouble(ScrmReferralRewardEntity::getRewardValue).sum();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("programId", programId);
        stats.put("programName", program.getProgramName());
        stats.put("programCode", program.getProgramCode());
        stats.put("status", program.getStatus());
        stats.put("totalReferrals", total);
        stats.put("successfulReferrals", successful);
        stats.put("successRate", total > 0
                ? Math.round((double) successful / total * 10000d) / 100d : 0);
        stats.put("byStatus", statusCount);
        stats.put("totalRewardValue", Math.round(totalReward * 100d) / 100d);
        stats.put("maxReferralsPerReferrer", program.getMaxReferralsPerReferrer());
        stats.put("maxReferralsTotal", program.getMaxReferralsTotal());
        return stats;
    }

    /**
     * 推荐人排行: 按推荐数与成功推荐数倒序返回 Top N。
     *
     * @param limit 返回条数 (默认 10)
     * @return 推荐人排行列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getReferrerLeaderboard(int limit) {
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        }
        Specification<ScrmReferralEntity> spec = (root, query, cb) ->
                cb.and();
        List<ScrmReferralEntity> referrals = referralRepository.findAll(spec);
        // 按推荐人分组统计
        Map<Long, long[]> referrerStats = new LinkedHashMap<>();
        for (ScrmReferralEntity r : referrals) {
            if (r.getReferrerCustomerId() == null) {
                continue;
            }
            long[] arr = referrerStats.computeIfAbsent(r.getReferrerCustomerId(), k -> new long[2]);
            arr[0]++;
            if (ScrmReferralProgramService.SUCCESSFUL_STATUSES.contains(r.getStatus())) {
                arr[1]++;
            }
        }
        // 取推荐人名称
        Map<Long, String> referrerNames = new LinkedHashMap<>();
        for (ScrmReferralEntity r : referrals) {
            if (r.getReferrerCustomerId() != null && r.getReferrerName() != null
                    && !referrerNames.containsKey(r.getReferrerCustomerId())) {
                referrerNames.put(r.getReferrerCustomerId(), r.getReferrerName());
            }
        }
        return referrerStats.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .limit(limit)
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("referrerCustomerId", e.getKey());
                    item.put("referrerName", referrerNames.get(e.getKey()));
                    item.put("totalReferrals", e.getValue()[0]);
                    item.put("successfulReferrals", e.getValue()[1]);
                    item.put("successRate", e.getValue()[0] > 0
                            ? Math.round((double) e.getValue()[1] / e.getValue()[0] * 10000d) / 100d : 0);
                    return item;
                })
                .collect(Collectors.toList());
    }

    /**
     * 推荐趋势: 按天统计最近 N 天的推荐数量。
     *
     * @param days 天数 (默认 7)
     * @return 趋势数据 (date + count)
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getReferralTrend(int days) {
        if (days <= 0) {
            days = DEFAULT_TREND_DAYS;
        }
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(days - 1L);
        LocalDateTime startTime = start.atStartOfDay();
        LocalDateTime endTime = today.plusDays(1).atStartOfDay();
        Specification<ScrmReferralEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            predicates.add(cb.lessThan(root.get("createTime"), endTime));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmReferralEntity> referrals = referralRepository.findAll(spec);
        Map<LocalDate, Long> dailyCount = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            dailyCount.put(start.plusDays(i), 0L);
        }
        for (ScrmReferralEntity r : referrals) {
            if (r.getCreateTime() != null) {
                LocalDate day = r.getCreateTime().toLocalDate();
                dailyCount.merge(day, 1L, Long::sum);
            }
        }
        List<Map<String, Object>> trend = new ArrayList<>();
        for (Map.Entry<LocalDate, Long> e : dailyCount.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", e.getKey().format(TREND_DATE_FORMAT));
            item.put("count", e.getValue());
            trend.add(item);
        }
        return trend;
    }

    /**
     * 转化漏斗: 推荐 → 注册 → 达标 → 奖励。
     *
     * @param programId 活动 ID
     * @return 漏斗数据 (stage + count + conversionRate)
     * @throws ScrmException 活动不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getConversionFunnel(Long programId) throws ScrmException {
        programService.findProgramOrThrow(programId);
        Specification<ScrmReferralEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("programId"), programId));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmReferralEntity> referrals = referralRepository.findAll(spec);
        long total = referrals.size();
        long signedUp = referrals.stream()
                .filter(r -> r.getSignedUpAt() != null
                        || !ScrmReferralProgramService.REFERRAL_STATUS_PENDING.equals(r.getStatus())).count();
        long qualified = referrals.stream()
                .filter(r -> ScrmReferralProgramService.REFERRAL_STATUS_QUALIFIED.equals(r.getStatus())
                        || ScrmReferralProgramService.REFERRAL_STATUS_REWARDED.equals(r.getStatus())).count();
        long rewarded = referrals.stream()
                .filter(r -> ScrmReferralProgramService.REFERRAL_STATUS_REWARDED.equals(r.getStatus())).count();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("programId", programId);
        List<Map<String, Object>> stages = new ArrayList<>();
        stages.add(buildFunnelStage("referral", total, total));
        stages.add(buildFunnelStage("signedUp", signedUp, total));
        stages.add(buildFunnelStage("qualified", qualified, total));
        stages.add(buildFunnelStage("rewarded", rewarded, total));
        stats.put("stages", stages);
        return stats;
    }

    /**
     * 奖励统计: 各类型 / 发放率 / 兑换率。
     * <p>时间范围按奖励创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getRewardStats(LocalDateTime startTime, LocalDateTime endTime) {
        Specification<ScrmReferralRewardEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmReferralRewardEntity> rewards = rewardRepository.findAll(spec);
        long total = rewards.size();
        long issued = rewards.stream()
                .filter(r -> ScrmReferralProgramService.REWARD_STATUS_ISSUED.equals(r.getStatus())
                        || ScrmReferralProgramService.REWARD_STATUS_REDEEMED.equals(r.getStatus())).count();
        long redeemed = rewards.stream()
                .filter(r -> ScrmReferralProgramService.REWARD_STATUS_REDEEMED.equals(r.getStatus())).count();
        long expired = rewards.stream()
                .filter(r -> ScrmReferralProgramService.REWARD_STATUS_EXPIRED.equals(r.getStatus())).count();
        // 按类型统计
        Map<String, Long> typeCount = new LinkedHashMap<>();
        for (String t : Arrays.asList(ScrmReferralProgramService.REWARD_POINTS,
                ScrmReferralProgramService.REWARD_COUPON, ScrmReferralProgramService.REWARD_CASH,
                ScrmReferralProgramService.REWARD_DISCOUNT, ScrmReferralProgramService.REWARD_GIFT,
                ScrmReferralProgramService.REWARD_MEMBERSHIP)) {
            typeCount.put(t, 0L);
        }
        Map<String, Double> typeValue = new LinkedHashMap<>();
        for (String t : typeCount.keySet()) {
            typeValue.put(t, 0d);
        }
        for (ScrmReferralRewardEntity r : rewards) {
            if (r.getRewardType() != null) {
                typeCount.merge(r.getRewardType(), 1L, Long::sum);
                double val = r.getRewardValue() != null ? r.getRewardValue() : 0d;
                typeValue.merge(r.getRewardType(), val, Double::sum);
            }
        }
        double totalValue = rewards.stream()
                .filter(r -> r.getRewardValue() != null)
                .mapToDouble(ScrmReferralRewardEntity::getRewardValue).sum();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalRewards", total);
        stats.put("issuedRewards", issued);
        stats.put("redeemedRewards", redeemed);
        stats.put("expiredRewards", expired);
        stats.put("issueRate", total > 0
                ? Math.round((double) issued / total * 10000d) / 100d : 0);
        stats.put("redeemRate", total > 0
                ? Math.round((double) redeemed / total * 10000d) / 100d : 0);
        stats.put("byType", typeCount);
        stats.put("byTypeValue", typeValue);
        stats.put("totalRewardValue", Math.round(totalValue * 100d) / 100d);
        return stats;
    }

    /**
     * 构建时间范围查询条件 Specification (按创建时间过滤)。
     */
    private Specification<ScrmReferralEntity> buildTimeRangeSpec(LocalDateTime startTime, LocalDateTime endTime) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 构建漏斗阶段数据。
     *
     * @param stage 阶段名
     * @param count 数量
     * @param total 总数 (用于转化率)
     * @return 阶段数据
     */
    private Map<String, Object> buildFunnelStage(String stage, long count, long total) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("stage", stage);
        item.put("count", count);
        item.put("conversionRate", total > 0
                ? Math.round((double) count / total * 10000d) / 100d : 0);
        return item;
    }
}