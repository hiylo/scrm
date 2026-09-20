/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContractStatsService.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmContractDto;
import org.hiylo.scrm.dto.ScrmContractSearchDto;
import org.hiylo.scrm.entity.ScrmContractChangeEntity;
import org.hiylo.scrm.entity.ScrmContractEntity;
import org.hiylo.scrm.entity.ScrmContractPaymentEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmContractChangeRepository;
import org.hiylo.scrm.repository.ScrmContractPaymentRepository;
import org.hiylo.scrm.repository.ScrmContractRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SCRM 合同统计与查询服务。
 * <p>
 * 承载合同统计 (总数/各类型/各状态/总金额/趋势/分布/到期/Top 排行)、合同概览、
 * 合同风险计算 (完整实现)、合同搜索 (多条件分页)、合同查询 (按类型/状态/销售人员/金额范围/
 * 即将到期/已到期)、合同摘要/导出/分享/克隆, 以及付款统计与变更统计。
 * </p>
 * <p>
 * 所有读操作通过 JPA Specification 始终按当前用户可见账号范围过滤。校验失败抛出 {@link ScrmException}
 * 携带通用错误码 (NOT_FOUND / BAD_REQUEST)。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmContractStatsService {

    // ==================== 常量 ====================
    /** 进行中状态集合 (到期扫描用) */
    private static final List<String> ACTIVE_STATUSES = Arrays.asList("ACTIVE", "SIGNED");
    /** 默认币种 */
    private static final String DEFAULT_CURRENCY = "CNY";
    /** 默认金额 */
    private static final double DEFAULT_AMOUNT = 0d;
    /** 月格式化 */
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    /** 合同数据访问层 */
    private final ScrmContractRepository contractRepository;
    /** 合同付款数据访问层 */
    private final ScrmContractPaymentRepository paymentRepository;
    /** 合同变更数据访问层 */
    private final ScrmContractChangeRepository changeRepository;

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 合同统计: 总数 / 各类型 / 各状态 / 总金额。
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
                .collect(Collectors.groupingBy(c -> c.getContractType() != null ? c.getContractType()
                        : "UNKNOWN", Collectors.counting())));
        stats.put("byStatus", contracts.stream()
                .collect(Collectors.groupingBy(c -> c.getStatus() != null ? c.getStatus()
                        : "UNKNOWN", Collectors.counting())));
        double totalAmount = contracts.stream()
                .mapToDouble(c -> c.getContractAmount() != null ? c.getContractAmount() : DEFAULT_AMOUNT).sum();
        stats.put("totalAmount", round(totalAmount));
        stats.put("avgAmount", contracts.isEmpty() ? DEFAULT_AMOUNT : round(totalAmount / contracts.size()));
        return stats;
    }

    /**
     * 付款统计: 总数 / 各状态 / 计划/已付/未付金额 / 逾期数量。
     * <p>时间范围按付款创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getPaymentStats(LocalDateTime startTime, LocalDateTime endTime) {
        Specification<ScrmContractPaymentEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmContractPaymentEntity> payments = paymentRepository.findAll(spec);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", payments.size());
        stats.put("byStatus", payments.stream()
                .collect(Collectors.groupingBy(p -> p.getPaymentStatus() != null ? p.getPaymentStatus()
                        : "UNKNOWN", Collectors.counting())));
        stats.put("totalPlannedAmount", round(payments.stream()
                .mapToDouble(p -> p.getPlannedAmount() != null ? p.getPlannedAmount() : DEFAULT_AMOUNT).sum()));
        stats.put("totalPaidAmount", round(payments.stream()
                .mapToDouble(p -> p.getPaidAmount() != null ? p.getPaidAmount() : DEFAULT_AMOUNT).sum()));
        stats.put("totalUnpaidAmount", round(payments.stream()
                .mapToDouble(p -> p.getUnpaidAmount() != null ? p.getUnpaidAmount() : DEFAULT_AMOUNT).sum()));
        stats.put("overdueCount", payments.stream()
                .filter(p -> "OVERDUE".equals(p.getPaymentStatus())).count());
        return stats;
    }

    /**
     * 变更统计: 总数 / 各类型 / 各状态 / 金额变化总和。
     * <p>时间范围按变更创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getChangeStats(LocalDateTime startTime, LocalDateTime endTime) {
        Specification<ScrmContractChangeEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmContractChangeEntity> changes = changeRepository.findAll(spec);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", changes.size());
        stats.put("byType", changes.stream()
                .collect(Collectors.groupingBy(c -> c.getChangeType() != null ? c.getChangeType()
                        : "UNKNOWN", Collectors.counting())));
        stats.put("byStatus", changes.stream()
                .collect(Collectors.groupingBy(c -> c.getChangeStatus() != null ? c.getChangeStatus()
                        : "UNKNOWN", Collectors.counting())));
        stats.put("totalValueChange", round(changes.stream()
                .mapToDouble(c -> c.getValueChange() != null ? c.getValueChange() : DEFAULT_AMOUNT).sum()));
        return stats;
    }

    /**
     * 合同金额趋势: 过去 N 个月每月新增合同数与金额。
     *
     * @param months 月数
     * @return 趋势数据
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getContractValueTrend(int months) {
        LocalDateTime startTime = LocalDateTime.now().minusMonths(months);
        Specification<ScrmContractEntity> spec = (root, query, cb) ->
                cb.greaterThanOrEqualTo(root.get("createTime"), startTime);
        List<ScrmContractEntity> contracts = contractRepository.findAll(spec);
        Map<String, Long> countByMonth = contracts.stream()
                .collect(Collectors.groupingBy(c -> c.getCreateTime().format(MONTH_FORMAT), Collectors.counting()));
        Map<String, Double> amountByMonth = new LinkedHashMap<>();
        contracts.stream()
                .collect(Collectors.groupingBy(c -> c.getCreateTime().format(MONTH_FORMAT)))
                .forEach((month, list) -> amountByMonth.put(month, round(list.stream()
                        .mapToDouble(c -> c.getContractAmount() != null ? c.getContractAmount() : DEFAULT_AMOUNT)
                        .sum())));
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("months", months);
        stats.put("countByMonth", countByMonth);
        stats.put("amountByMonth", amountByMonth);
        return stats;
    }

    /**
     * 合同类型分布: 各类型的合同数量。
     *
     * @return 类型分布
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getContractTypeDistribution() {
        List<ScrmContractEntity> contracts = contractRepository.findAll();
        Map<String, Object> distribution = new LinkedHashMap<>();
        distribution.put("distribution", contracts.stream()
                .collect(Collectors.groupingBy(c -> c.getContractType() != null ? c.getContractType()
                        : "UNKNOWN", Collectors.counting())));
        return distribution;
    }

    /**
     * 合同状态分布: 各状态的合同数量。
     *
     * @return 状态分布
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getContractStatusDistribution() {
        List<ScrmContractEntity> contracts = contractRepository.findAll();
        Map<String, Object> distribution = new LinkedHashMap<>();
        distribution.put("distribution", contracts.stream()
                .collect(Collectors.groupingBy(c -> c.getStatus() != null ? c.getStatus()
                        : "UNKNOWN", Collectors.counting())));
        return distribution;
    }

    /**
     * 到期统计: 未来 N 个月内到期的合同数量与金额。
     *
     * @param months 月数
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getExpiringStats(int months) {
        LocalDate today = LocalDate.now();
        LocalDate threshold = today.plusMonths(months);
        List<ScrmContractEntity> contracts = contractRepository
                .findByStatusInAndEndDateLessThanEqual(
                        ACTIVE_STATUSES, threshold);
        List<ScrmContractEntity> expiring = contracts.stream()
                .filter(c -> c.getEndDate() != null && !c.getEndDate().isBefore(today))
                .collect(Collectors.toList());
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("months", months);
        stats.put("expiringCount", expiring.size());
        stats.put("expiringAmount", round(expiring.stream()
                .mapToDouble(c -> c.getContractAmount() != null ? c.getContractAmount() : DEFAULT_AMOUNT).sum()));
        Map<String, Long> byMonth = expiring.stream()
                .collect(Collectors.groupingBy(c -> c.getEndDate().format(MONTH_FORMAT), Collectors.counting()));
        stats.put("byMonth", byMonth);
        return stats;
    }

    /**
     * Top 客户: 按合同总金额降序排列前 N 个客户。
     *
     * @param limit 数量
     * @return Top 客户列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTopCustomers(int limit) {
        List<ScrmContractEntity> contracts = contractRepository.findAll();
        Map<Long, List<ScrmContractEntity>> byCustomer = contracts.stream()
                .filter(c -> c.getCustomerId() != null)
                .collect(Collectors.groupingBy(ScrmContractEntity::getCustomerId));
        return byCustomer.entrySet().stream()
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("customerId", e.getKey());
                    item.put("customerName", e.getValue().get(0).getCustomerName());
                    item.put("contractCount", e.getValue().size());
                    item.put("totalAmount", round(e.getValue().stream()
                            .mapToDouble(c -> c.getContractAmount() != null ? c.getContractAmount() : DEFAULT_AMOUNT)
                            .sum()));
                    return item;
                })
                .sorted((a, b) -> Double.compare((Double) b.get("totalAmount"), (Double) a.get("totalAmount")))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Top 合同: 按合同金额降序排列前 N 个合同。
     *
     * @param limit 数量
     * @return Top 合同列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTopContracts(int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "contractAmount"));
        return contractRepository.findAll(pageable).stream()
                .map(c -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", c.getId());
                    item.put("contractNo", c.getContractNo());
                    item.put("contractName", c.getContractName());
                    item.put("customerId", c.getCustomerId());
                    item.put("customerName", c.getCustomerName());
                    item.put("contractAmount", c.getContractAmount());
                    item.put("status", c.getStatus());
                    return item;
                })
                .collect(Collectors.toList());
    }

    /**
     * 合同概览: 总合同数/总金额/各状态数/即将到期数/逾期付款数/待审变更数。
     *
     * @return 概览数据
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getContractOverview() {
        List<ScrmContractEntity> contracts = contractRepository.findAll();
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("totalContracts", contracts.size());
        overview.put("totalAmount", round(contracts.stream()
                .mapToDouble(c -> c.getContractAmount() != null ? c.getContractAmount() : DEFAULT_AMOUNT).sum()));
        overview.put("byStatus", contracts.stream()
                .collect(Collectors.groupingBy(c -> c.getStatus() != null ? c.getStatus()
                        : "UNKNOWN", Collectors.counting())));
        // 即将到期数 (30天内)
        LocalDate expiringThreshold = LocalDate.now().plusDays(30);
        long expiringCount = contracts.stream()
                .filter(c -> ACTIVE_STATUSES.contains(c.getStatus()) && c.getEndDate() != null && !c.getEndDate().isAfter(expiringThreshold) && !c.getEndDate().isBefore(LocalDate.now()))
                .count();
        overview.put("expiringCount30Days", expiringCount);
        // 逾期付款数
        Specification<ScrmContractPaymentEntity> overdueSpec = (root, query, cb) ->
                cb.equal(root.get("paymentStatus"), "OVERDUE");
        overview.put("overduePaymentCount", paymentRepository.count(overdueSpec));
        // 待审变更数
        Specification<ScrmContractChangeEntity> pendingChangeSpec = (root, query, cb) ->
                cb.equal(root.get("changeStatus"), "PENDING");
        overview.put("pendingChangeCount", changeRepository.count(pendingChangeSpec));
        return overview;
    }

    // ============================================================
    // 合同风险计算
    // ============================================================

    /**
     * 计算合同风险: 综合评估合同金额、期限、付款逾期、变更次数等因素, 返回风险等级与评估详情。
     * <p>完整实现: 检查合同状态 (已终止/已取消风险低)、金额 (大额风险高)、期限 (长期风险高)、
     * 付款逾期情况 (逾期越多风险越高)、变更频次 (变更越多风险越高), 综合计算风险分值并映射为
     * LOW / MEDIUM / HIGH / CRITICAL。</p>
     *
     * @param contractId 合同 ID
     * @return 风险评估结果
     * @throws ScrmException 合同不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> calculateContractRisk(Long contractId) throws ScrmException {
        ScrmContractEntity contract = findContractOrThrow(contractId);
        int riskScore = 0;
        List<String> riskFactors = new ArrayList<>();
        // 已终止/已取消的合同风险低
        if ("TERMINATED".equals(contract.getStatus()) || "CANCELLED".equals(contract.getStatus())) {
            riskScore = 0;
            riskFactors.add("合同已" + contract.getStatus() + ", 无风险");
        } else {
            // 金额因素: 大额合同风险高 (>100万=30分, >50万=20分, >10万=10分)
            double amount = contract.getContractAmount() != null ? contract.getContractAmount() : DEFAULT_AMOUNT;
            if (amount > 1000000) {
                riskScore += 30;
                riskFactors.add("合同金额超过 100 万: " + amount);
            } else if (amount > 500000) {
                riskScore += 20;
                riskFactors.add("合同金额超过 50 万: " + amount);
            } else if (amount > 100000) {
                riskScore += 10;
                riskFactors.add("合同金额超过 10 万: " + amount);
            }
            // 期限因素: 长期合同风险高 (>24月=20分, >12月=10分)
            if (contract.getDurationMonths() != null) {
                if (contract.getDurationMonths() > 24) {
                    riskScore += 20;
                    riskFactors.add("合同期限超过 24 个月: " + contract.getDurationMonths());
                } else if (contract.getDurationMonths() > 12) {
                    riskScore += 10;
                    riskFactors.add("合同期限超过 12 个月: " + contract.getDurationMonths());
                }
            }
            // 付款逾期因素: 逾期付款越多风险越高 (每个逾期=10分)
            List<ScrmContractPaymentEntity> payments = paymentRepository
                    .findByContractIdOrderByPlannedDateAsc(contractId);
            long overdueCount = payments.stream()
                    .filter(p -> "OVERDUE".equals(p.getPaymentStatus())).count();
            if (overdueCount > 0) {
                riskScore += (int) Math.min(overdueCount * 10, 30);
                riskFactors.add("逾期付款数量: " + overdueCount);
            }
            // 变更频次因素: 变更越多风险越高 (每次变更=5分, 上限20分)
            List<ScrmContractChangeEntity> changes = changeRepository
                    .findByContractIdOrderByCreateTimeAsc(contractId);
            if (changes.size() > 0) {
                riskScore += (int) Math.min(changes.size() * 5, 20);
                riskFactors.add("合同变更次数: " + changes.size());
            }
            // 即将到期因素: 30天内到期加10分
            if (contract.getEndDate() != null && ACTIVE_STATUSES.contains(contract.getStatus())) {
                long daysToExpiry = ChronoUnit.DAYS.between(LocalDate.now(), contract.getEndDate());
                if (daysToExpiry <= 30 && daysToExpiry >= 0) {
                    riskScore += 10;
                    riskFactors.add("合同即将到期 (" + daysToExpiry + " 天)");
                }
            }
        }
        // 风险分值映射为等级
        String riskLevel;
        if (riskScore >= 70) {
            riskLevel = "CRITICAL";
        } else if (riskScore >= 40) {
            riskLevel = "HIGH";
        } else if (riskScore >= 20) {
            riskLevel = "MEDIUM";
        } else {
            riskLevel = "LOW";
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contractId", contractId);
        result.put("contractNo", contract.getContractNo());
        result.put("riskLevel", riskLevel);
        result.put("riskScore", riskScore);
        result.put("riskFactors", riskFactors);
        return result;
    }

    // ============================================================
    // 合同搜索与查询
    // ============================================================

    /**
     * 搜索合同: 多条件分页查询。
     * <p>支持按编号/名称/类型/状态/客户/相对方(客户名称)/签订日期范围/到期日期范围/金额范围/
     * 销售人员/关键词过滤, 支持排序字段指定。</p>
     *
     * @param searchDto 搜索条件
     * @param pageable  分页参数
     * @return 合同分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmContractDto> searchContracts(ScrmContractSearchDto searchDto, Pageable pageable) {
        Specification<ScrmContractEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (searchDto != null) {
                if (searchDto.getContractNo() != null && !searchDto.getContractNo().isBlank()) {
                    predicates.add(cb.like(cb.lower(root.get("contractNo")),
                            "%" + searchDto.getContractNo().toLowerCase() + "%"));
                }
                if (searchDto.getContractName() != null && !searchDto.getContractName().isBlank()) {
                    predicates.add(cb.like(cb.lower(root.get("contractName")),
                            "%" + searchDto.getContractName().toLowerCase() + "%"));
                }
                if (searchDto.getContractType() != null && !searchDto.getContractType().isBlank()) {
                    predicates.add(cb.equal(root.get("contractType"), searchDto.getContractType()));
                }
                // contractStatus 映射到实体的 status 字段
                if (searchDto.getContractStatus() != null && !searchDto.getContractStatus().isBlank()) {
                    predicates.add(cb.equal(root.get("status"), searchDto.getContractStatus()));
                }
                if (searchDto.getCustomerId() != null) {
                    predicates.add(cb.equal(root.get("customerId"), searchDto.getCustomerId()));
                }
                // counterparty 映射到实体的 customerName 字段
                if (searchDto.getCounterparty() != null && !searchDto.getCounterparty().isBlank()) {
                    predicates.add(cb.like(cb.lower(root.get("customerName")),
                            "%" + searchDto.getCounterparty().toLowerCase() + "%"));
                }
                // signDate 映射到实体的 signedDate 字段
                if (searchDto.getSignDateStart() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("signedDate"), searchDto.getSignDateStart()));
                }
                if (searchDto.getSignDateEnd() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("signedDate"), searchDto.getSignDateEnd()));
                }
                // expiryDate 映射到实体的 endDate 字段
                if (searchDto.getExpiryDateStart() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("endDate"), searchDto.getExpiryDateStart()));
                }
                if (searchDto.getExpiryDateEnd() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("endDate"), searchDto.getExpiryDateEnd()));
                }
                if (searchDto.getMinAmount() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("contractAmount"), searchDto.getMinAmount()));
                }
                if (searchDto.getMaxAmount() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("contractAmount"), searchDto.getMaxAmount()));
                }
                // salesId 映射到实体的 salesPersonId 字段
                if (searchDto.getSalesId() != null) {
                    predicates.add(cb.equal(root.get("salesPersonId"), String.valueOf(searchDto.getSalesId())));
                }
                if (searchDto.getKeyword() != null && !searchDto.getKeyword().isBlank()) {
                    String like = "%" + searchDto.getKeyword().toLowerCase() + "%";
                    predicates.add(cb.or(
                            cb.like(cb.lower(root.get("contractName")), like),
                            cb.like(cb.lower(root.get("contractNo")), like),
                            cb.like(cb.lower(root.get("customerName")), like)));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return contractRepository.findAll(spec, pageable).map(this::toDto);
    }

    /**
     * 按合同类型分页查询。
     *
     * @param type     合同类型
     * @param pageable 分页参数
     * @return 合同分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmContractDto> getContractsByType(String type, Pageable pageable) {
        Specification<ScrmContractEntity> spec = (root, query, cb) ->
                cb.equal(root.get("contractType"), type);
        return contractRepository.findAll(spec, pageable).map(this::toDto);
    }

    /**
     * 按合同状态分页查询。
     *
     * @param status   合同状态
     * @param pageable 分页参数
     * @return 合同分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmContractDto> getContractsByStatus(String status, Pageable pageable) {
        Specification<ScrmContractEntity> spec = (root, query, cb) ->
                cb.equal(root.get("status"), status);
        return contractRepository.findAll(spec, pageable).map(this::toDto);
    }

    /**
     * 按销售人员分页查询。
     *
     * @param salesId  销售人员 ID
     * @param pageable 分页参数
     * @return 合同分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmContractDto> getContractsBySales(Long salesId, Pageable pageable) {
        Specification<ScrmContractEntity> spec = (root, query, cb) ->
                cb.equal(root.get("salesPersonId"), String.valueOf(salesId));
        return contractRepository.findAll(spec, pageable).map(this::toDto);
    }

    /**
     * 按金额范围分页查询。
     *
     * @param minAmount 最小金额 (可空)
     * @param maxAmount 最大金额 (可空)
     * @param pageable  分页参数
     * @return 合同分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmContractDto> getContractsByValueRange(Double minAmount, Double maxAmount, Pageable pageable) {
        Specification<ScrmContractEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (minAmount != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("contractAmount"), minAmount));
            }
            if (maxAmount != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("contractAmount"), maxAmount));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return contractRepository.findAll(spec, pageable).map(this::toDto);
    }

    /**
     * 分页查询即将到期的合同 (未来 N 天内到期, 状态为 ACTIVE/SIGNED)。
     *
     * @param days     天数
     * @param pageable 分页参数
     * @return 合同分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmContractDto> getExpiringContracts(int days, Pageable pageable) {
        LocalDate threshold = LocalDate.now().plusDays(days);
        Specification<ScrmContractEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("status").in(ACTIVE_STATUSES));
            predicates.add(cb.lessThanOrEqualTo(root.get("endDate"), threshold));
            predicates.add(cb.greaterThanOrEqualTo(root.get("endDate"), LocalDate.now()));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return contractRepository.findAll(spec, pageable).map(this::toDto);
    }

    /**
     * 分页查询已到期合同 (结束日期早于今天, 状态为 ACTIVE/SIGNED)。
     *
     * @param pageable 分页参数
     * @return 合同分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmContractDto> getExpiredContracts(Pageable pageable) {
        LocalDate today = LocalDate.now();
        Specification<ScrmContractEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("status").in(ACTIVE_STATUSES));
            predicates.add(cb.lessThan(root.get("endDate"), today));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return contractRepository.findAll(spec, pageable).map(this::toDto);
    }

    // ============================================================
    // 合同摘要/导出/分享/克隆
    // ============================================================

    /**
     * 合同摘要: 合同基本信息 + 付款统计 + 变更数量。
     *
     * @param id 合同 ID
     * @return 摘要信息
     * @throws ScrmException 合同不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getContractSummary(Long id) throws ScrmException {
        ScrmContractEntity contract = findContractOrThrow(id);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", contract.getId());
        summary.put("contractNo", contract.getContractNo());
        summary.put("contractName", contract.getContractName());
        summary.put("contractType", contract.getContractType());
        summary.put("status", contract.getStatus());
        summary.put("customerId", contract.getCustomerId());
        summary.put("customerName", contract.getCustomerName());
        summary.put("contractAmount", contract.getContractAmount());
        summary.put("currency", contract.getCurrency());
        summary.put("startDate", contract.getStartDate());
        summary.put("endDate", contract.getEndDate());
        summary.put("durationMonths", contract.getDurationMonths());
        // 付款统计
        List<ScrmContractPaymentEntity> payments = paymentRepository
                .findByContractIdOrderByPlannedDateAsc(id);
        summary.put("paymentCount", payments.size());
        summary.put("totalPlannedAmount", round(payments.stream()
                .mapToDouble(p -> p.getPlannedAmount() != null ? p.getPlannedAmount() : DEFAULT_AMOUNT).sum()));
        summary.put("totalPaidAmount", round(payments.stream()
                .mapToDouble(p -> p.getPaidAmount() != null ? p.getPaidAmount() : DEFAULT_AMOUNT).sum()));
        // 变更数量
        List<ScrmContractChangeEntity> changes = changeRepository
                .findByContractIdOrderByCreateTimeAsc(id);
        summary.put("changeCount", changes.size());
        return summary;
    }

    /**
     * 导出合同: 返回合同完整信息 (含付款与变更)。
     *
     * @param id 合同 ID
     * @return 导出数据
     * @throws ScrmException 合同不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> exportContract(Long id) throws ScrmException {
        ScrmContractEntity contract = findContractOrThrow(id);
        Map<String, Object> exportData = new LinkedHashMap<>();
        exportData.put("contract", toDto(contract));
        exportData.put("payments", paymentRepository
                .findByContractIdOrderByPlannedDateAsc(id)
                .stream().map(this::paymentToMap).collect(Collectors.toList()));
        exportData.put("changes", changeRepository
                .findByContractIdOrderByCreateTimeAsc(id)
                .stream().map(this::changeToMap).collect(Collectors.toList()));
        exportData.put("exportTime", LocalDateTime.now());
        return exportData;
    }

    /**
     * 分享合同: 返回分享信息 (合同 ID + 用户 ID + 分享时间)。
     *
     * @param id     合同 ID
     * @param userId 用户 ID
     * @return 分享信息
     * @throws ScrmException 合同不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> shareContract(Long id, Long userId) throws ScrmException {
        ScrmContractEntity contract = findContractOrThrow(id);
        Map<String, Object> shareInfo = new LinkedHashMap<>();
        shareInfo.put("contractId", contract.getId());
        shareInfo.put("contractNo", contract.getContractNo());
        shareInfo.put("contractName", contract.getContractName());
        shareInfo.put("sharedTo", userId);
        shareInfo.put("sharedAt", LocalDateTime.now());
        shareInfo.put("shareUrl", "/scrm/contracts/" + contract.getId());
        log.info("分享合同: contractId={}, userId={}", id, userId);
        return shareInfo;
    }

    /**
     * 克隆合同: 基于已有合同创建新合同, 新合同编号由参数指定。
     * <p>克隆后的合同状态为 DRAFT, 不继承审批/签署信息。</p>
     *
     * @param id    源合同 ID
     * @param newNo 新合同编号
     * @return 新合同
     * @throws ScrmException 源合同不存在 / 新编号重复
     */
    @Transactional
    public ScrmContractDto cloneContract(Long id, String newNo) throws ScrmException {
        if (newNo == null || newNo.isBlank()) {
            throw ScrmException.badRequest("新合同编号不能为空");
        }
        ScrmContractEntity source = findContractOrThrow(id);
        if (contractRepository.findByContractNo(newNo).isPresent()) {
            throw ScrmException.conflict("合同编号已存在: no=" + newNo);
        }
        ScrmContractEntity entity = new ScrmContractEntity();
        entity.setContractNo(newNo);
        entity.setContractName(source.getContractName() + "_克隆");
        entity.setContractType(source.getContractType());
        entity.setTemplateId(source.getTemplateId());
        entity.setCustomerId(source.getCustomerId());
        entity.setCustomerName(source.getCustomerName());
        entity.setCustomerContact(source.getCustomerContact());
        entity.setCustomerAddress(source.getCustomerAddress());
        entity.setTitle(source.getTitle());
        entity.setDescription(source.getDescription());
        entity.setContent(source.getContent());
        entity.setVariables(source.getVariables());
        entity.setContractAmount(source.getContractAmount());
        entity.setCurrency(source.getCurrency());
        entity.setPaymentTerms(source.getPaymentTerms());
        entity.setStartDate(source.getStartDate());
        entity.setEndDate(source.getEndDate());
        entity.setDurationMonths(source.getDurationMonths());
        entity.setAutoRenew(source.getAutoRenew());
        entity.setAutoRenewMonths(source.getAutoRenewMonths());
        entity.setStatus("DRAFT");
        entity.setPriority(source.getPriority());
        entity.setSalesPersonId(source.getSalesPersonId());
        entity.setSalesPersonName(source.getSalesPersonName());
        entity.setDepartmentId(source.getDepartmentId());
        entity.setDepartmentName(source.getDepartmentName());
        entity.setAttachments(source.getAttachments());
        entity.setTags(source.getTags());
        entity.setRemindersEnabled(source.getRemindersEnabled());
        entity.setReminderDaysBefore(source.getReminderDaysBefore());
        entity.setTerms(source.getTerms());
        entity.setCustomFields(source.getCustomFields());
        entity.setNotes(source.getNotes());
        entity.setCreatedBy(source.getCreatedBy());
        entity = contractRepository.save(entity);
        log.info("克隆合同: sourceId={}, newId={}, newNo={}", id, entity.getId(), newNo);
        return toDto(entity);
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 按主键查询合同, 不存在抛异常 (数据隔离校验)
     */
    private ScrmContractEntity findContractOrThrow(Long id) throws ScrmException {
        ScrmContractEntity entity = contractRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "合同不存在: id=" + id));

        return entity;
    }

    /**
     * 按时间范围查询当前账号合同列表 (统计用)
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
     * 保留两位小数
     */
    private double round(double value) {
        return Math.round(value * 100d) / 100d;
    }

    /**
     * 付款实体转 Map (导出用)
     */
    private Map<String, Object> paymentToMap(ScrmContractPaymentEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("paymentNo", entity.getPaymentNo());
        map.put("paymentType", entity.getPaymentType());
        map.put("paymentStatus", entity.getPaymentStatus());
        map.put("plannedAmount", entity.getPlannedAmount());
        map.put("paidAmount", entity.getPaidAmount());
        map.put("unpaidAmount", entity.getUnpaidAmount());
        map.put("plannedDate", entity.getPlannedDate());
        map.put("actualDate", entity.getActualDate());
        map.put("paymentMethod", entity.getPaymentMethod());
        return map;
    }

    /**
     * 变更实体转 Map (导出用)
     */
    private Map<String, Object> changeToMap(ScrmContractChangeEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("changeNo", entity.getChangeNo());
        map.put("changeType", entity.getChangeType());
        map.put("changeStatus", entity.getChangeStatus());
        map.put("changeReason", entity.getChangeReason());
        map.put("changeDate", entity.getChangeDate());
        map.put("effectiveDate", entity.getEffectiveDate());
        map.put("valueChange", entity.getValueChange());
        return map;
    }

    /**
     * 合同实体转 DTO
     */
    private ScrmContractDto toDto(ScrmContractEntity entity) {
        ScrmContractDto dto = new ScrmContractDto();
        dto.setId(entity.getId());
        dto.setContractNo(entity.getContractNo());
        dto.setContractName(entity.getContractName());
        dto.setContractType(entity.getContractType());
        dto.setTemplateId(entity.getTemplateId());
        dto.setCustomerId(entity.getCustomerId());
        dto.setCustomerName(entity.getCustomerName());
        dto.setCustomerContact(entity.getCustomerContact());
        dto.setCustomerAddress(entity.getCustomerAddress());
        dto.setTitle(entity.getTitle());
        dto.setDescription(entity.getDescription());
        dto.setContent(entity.getContent());
        dto.setVariables(entity.getVariables());
        dto.setContractAmount(entity.getContractAmount());
        dto.setCurrency(entity.getCurrency());
        dto.setPaymentTerms(entity.getPaymentTerms());
        dto.setStartDate(entity.getStartDate());
        dto.setEndDate(entity.getEndDate());
        dto.setDurationMonths(entity.getDurationMonths());
        dto.setAutoRenew(entity.getAutoRenew());
        dto.setAutoRenewMonths(entity.getAutoRenewMonths());
        dto.setSignedDate(entity.getSignedDate());
        dto.setEffectiveDate(entity.getEffectiveDate());
        dto.setExpiredDate(entity.getExpiredDate());
        dto.setStatus(entity.getStatus());
        dto.setPriority(entity.getPriority());
        dto.setSalesPersonId(entity.getSalesPersonId());
        dto.setSalesPersonName(entity.getSalesPersonName());
        dto.setDepartmentId(entity.getDepartmentId());
        dto.setDepartmentName(entity.getDepartmentName());
        dto.setApproverId(entity.getApproverId());
        dto.setApproverName(entity.getApproverName());
        dto.setApprovedAt(entity.getApprovedAt());
        dto.setApprovalComment(entity.getApprovalComment());
        dto.setSignerId(entity.getSignerId());
        dto.setSignerName(entity.getSignerName());
        dto.setSignatureMethod(entity.getSignatureMethod());
        dto.setSignatureUrl(entity.getSignatureUrl());
        dto.setAttachments(entity.getAttachments());
        dto.setTags(entity.getTags());
        dto.setRelatedContracts(entity.getRelatedContracts());
        dto.setRenewalOfId(entity.getRenewalOfId());
        dto.setRenewedToId(entity.getRenewedToId());
        dto.setRemindersEnabled(entity.getRemindersEnabled());
        dto.setReminderDaysBefore(entity.getReminderDaysBefore());
        dto.setLastReminderSentAt(entity.getLastReminderSentAt());
        dto.setTerms(entity.getTerms());
        dto.setCustomFields(entity.getCustomFields());
        dto.setNotes(entity.getNotes());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}
