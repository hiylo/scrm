/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContractStatService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmContractEntity;
import org.hiylo.scrm.entity.ScrmContractReminderEntity;
import org.hiylo.scrm.repository.ScrmContractReminderRepository;
import org.hiylo.scrm.repository.ScrmContractRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SCRM 合同统计服务 (统计子域)。
 * <p>
 * 承载合同总数 / 类型 / 状态 / 金额 / 期限统计、到期统计、续约统计、销售人员合同统计、
 * 金额统计、合同趋势与提醒统计。进行中状态集合等共享常量复用 {@link ScrmContractManageService}。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmContractStatService {

    /** 合同实例数据访问层 */
    private final ScrmContractRepository contractRepository;

    /** 合同提醒数据访问层 (提醒统计用) */
    private final ScrmContractReminderRepository reminderRepository;

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 合同统计: 总数 / 各类型 / 各状态 / 总金额 / 平均期限。
     * <p>时间范围按合同创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getContractStats(LocalDateTime startTime, LocalDateTime endTime) {
        List<ScrmContractEntity> contracts = listContractsByTimeRange(startTime, endTime);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", contracts.size());
        stats.put("byType", contracts.stream()
                .collect(Collectors.groupingBy(ScrmContractEntity::getContractType, Collectors.counting())));
        stats.put("byStatus", contracts.stream()
                .collect(Collectors.groupingBy(ScrmContractEntity::getStatus, Collectors.counting())));
        double totalAmount = contracts.stream()
                .mapToDouble(c -> c.getContractAmount() != null ? c.getContractAmount() : 0d).sum();
        stats.put("totalAmount", round(totalAmount));
        stats.put("avgAmount", contracts.isEmpty() ? 0d : round(totalAmount / contracts.size()));
        double avgDuration = contracts.stream()
                .mapToInt(c -> c.getDurationMonths() != null ? c.getDurationMonths() : 0)
                .average().orElse(0d);
        stats.put("avgDurationMonths", round(avgDuration));
        return stats;
    }

    /**
     * 到期统计: 未来 N 个月内到期的合同数量与金额。
     *
     * @param months 月数
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getExpiryStats(int months) {
        LocalDate today = LocalDate.now();
        LocalDate threshold = today.plusMonths(months);
        List<ScrmContractEntity> contracts = contractRepository.findByStatusInAndEndDateLessThanEqual(
                 ScrmContractManageService.ACTIVE_STATUSES, threshold);
        List<ScrmContractEntity> expiring = contracts.stream()
                .filter(c -> c.getEndDate() != null && !c.getEndDate().isBefore(today))
                .collect(Collectors.toList());
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("months", months);
        stats.put("expiringCount", expiring.size());
        stats.put("expiringAmount", round(expiring.stream()
                .mapToDouble(c -> c.getContractAmount() != null ? c.getContractAmount() : 0d).sum()));
        // 按月分组
        Map<String, Long> byMonth = expiring.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getEndDate().format(DateTimeFormatter.ofPattern("yyyy-MM")),
                        Collectors.counting()));
        stats.put("byMonth", byMonth);
        return stats;
    }

    /**
     * 续约统计: 续约率 / 平均续约金额。
     * <p>时间范围按合同创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getRenewalStats(LocalDateTime startTime, LocalDateTime endTime) {
        List<ScrmContractEntity> contracts = listContractsByTimeRange(startTime, endTime);
        long totalContracts = contracts.size();
        List<ScrmContractEntity> renewals = contracts.stream()
                .filter(c -> c.getRenewalOfId() != null)
                .collect(Collectors.toList());
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalContracts", totalContracts);
        stats.put("renewalCount", renewals.size());
        stats.put("renewalRate", totalContracts > 0 ? round(renewals.size() * 100d / totalContracts) : 0d);
        stats.put("avgRenewalAmount", renewals.isEmpty() ? 0d : round(renewals.stream()
                .mapToDouble(c -> c.getContractAmount() != null ? c.getContractAmount() : 0d)
                .average().orElse(0d)));
        return stats;
    }

    /**
     * 销售人员合同统计: 合同数 / 总金额 / 各状态分布。
     * <p>时间范围按合同创建时间过滤, 为空时统计全量。</p>
     *
     * @param salesPersonId 销售人员 ID
     * @param startTime     起始时间 (可空)
     * @param endTime       截止时间 (可空)
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSalesPersonContractStats(String salesPersonId, LocalDateTime startTime,
                                                            LocalDateTime endTime) {
        Specification<ScrmContractEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("salesPersonId"), salesPersonId));
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmContractEntity> contracts = contractRepository.findAll(spec);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("salesPersonId", salesPersonId);
        stats.put("totalContracts", contracts.size());
        stats.put("totalAmount", round(contracts.stream()
                .mapToDouble(c -> c.getContractAmount() != null ? c.getContractAmount() : 0d).sum()));
        stats.put("byStatus", contracts.stream()
                .collect(Collectors.groupingBy(ScrmContractEntity::getStatus, Collectors.counting())));
        stats.put("byType", contracts.stream()
                .collect(Collectors.groupingBy(ScrmContractEntity::getContractType, Collectors.counting())));
        return stats;
    }

    /**
     * 金额统计: 总金额 / 平均金额 / 各类型金额。
     * <p>时间范围按合同创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getValueStats(LocalDateTime startTime, LocalDateTime endTime) {
        List<ScrmContractEntity> contracts = listContractsByTimeRange(startTime, endTime);
        double totalAmount = contracts.stream()
                .mapToDouble(c -> c.getContractAmount() != null ? c.getContractAmount() : 0d).sum();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalAmount", round(totalAmount));
        stats.put("avgAmount", contracts.isEmpty() ? 0d : round(totalAmount / contracts.size()));
        stats.put("contractCount", contracts.size());
        // 按类型分组金额
        Map<String, Double> amountByType = new LinkedHashMap<>();
        contracts.stream()
                .collect(Collectors.groupingBy(ScrmContractEntity::getContractType))
                .forEach((type, list) -> amountByType.put(type, round(list.stream()
                        .mapToDouble(c -> c.getContractAmount() != null ? c.getContractAmount() : 0d).sum())));
        stats.put("amountByType", amountByType);
        return stats;
    }

    /**
     * 合同趋势: 过去 N 个月每月新增合同数与金额。
     *
     * @param months 月数
     * @return 趋势数据
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getContractTrend(int months) {
        LocalDateTime startTime = LocalDateTime.now().minusMonths(months);
        Specification<ScrmContractEntity> spec = (root, query, cb) -> cb.and(
                cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
        List<ScrmContractEntity> contracts = contractRepository.findAll(spec);
        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM");
        Map<String, Long> countByMonth = contracts.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getCreateTime().format(monthFormatter), Collectors.counting()));
        Map<String, Double> amountByMonth = new LinkedHashMap<>();
        contracts.stream()
                .collect(Collectors.groupingBy(c -> c.getCreateTime().format(monthFormatter)))
                .forEach((month, list) -> amountByMonth.put(month, round(list.stream()
                        .mapToDouble(c -> c.getContractAmount() != null ? c.getContractAmount() : 0d).sum())));
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("months", months);
        stats.put("countByMonth", countByMonth);
        stats.put("amountByMonth", amountByMonth);
        return stats;
    }

    /**
     * 提醒统计: 总数 / 各类型 / 各状态 / 已处理率。
     * <p>时间范围按提醒创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getReminderStats(LocalDateTime startTime, LocalDateTime endTime) {
        Specification<ScrmContractReminderEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmContractReminderEntity> reminders = reminderRepository.findAll(spec);
        long totalReminders = reminders.size();
        long actionTakenCount = reminders.stream().filter(r -> Boolean.TRUE.equals(r.getActionTaken())).count();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", totalReminders);
        stats.put("byType", reminders.stream()
                .collect(Collectors.groupingBy(ScrmContractReminderEntity::getReminderType, Collectors.counting())));
        stats.put("byStatus", reminders.stream()
                .collect(Collectors.groupingBy(ScrmContractReminderEntity::getStatus, Collectors.counting())));
        stats.put("actionTakenRate", totalReminders > 0 ? round(actionTakenCount * 100d / totalReminders) : 0d);
        stats.put("sentCount", reminders.stream()
                .mapToInt(r -> r.getSentCount() != null ? r.getSentCount() : 0).sum());
        return stats;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 按时间范围查询当前账号合同列表 (统计用)。
     */
    private List<ScrmContractEntity> listContractsByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        Specification<ScrmContractEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return contractRepository.findAll(spec);
    }

    /**
     * 保留两位小数。
     */
    private double round(double value) {
        return Math.round(value * 100d) / 100d;
    }
}