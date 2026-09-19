/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCommissionRecordService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmCommissionApproveDto;
import org.hiylo.scrm.dto.ScrmCommissionPayoutDto;
import org.hiylo.scrm.dto.ScrmCommissionRecordDto;
import org.hiylo.scrm.entity.ScrmCommissionRecordEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCommissionRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * SCRM 佣金记录服务 (记录与提现统计子域)。
 * <p>
 * 承载佣金记录查询、审批 (提交/通过/批量审批/驳回)、发放 (发放/批量发放/汇总/追回/标记已发放)
 * 与统计 (佣金概览 / 销售排行 / 团队统计 / 方案效果 / 佣金趋势 / 发放报告)。同时托管金额舍入、
 * 记录转换与按主键查询记录能力, 供计算兄弟类以 package 级访问复用。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmCommissionRecordService {

    /** 审批动作: 通过 */
    private static final String ACTION_APPROVE = "APPROVE";
    /** 审批动作: 驳回 */
    private static final String ACTION_REJECT = "REJECT";

    /** 金额精度 (保留两位小数) */
    private static final double MONEY_SCALE = 100d;
    /** 默认税率 (10%) */
    private static final double DEFAULT_TAX_RATE = 0.10d;
    /** 默认排行条数 */
    private static final int DEFAULT_RANKING_LIMIT = 10;
    /** 默认趋势天数 */
    private static final int DEFAULT_TREND_DAYS = 7;

    /** 佣金记录数据访问层 */
    private final ScrmCommissionRecordRepository recordRepository;

    /** 佣金方案管理服务 (校验方案存在与共享常量) */
    private final ScrmCommissionPlanService planService;

    // ============================================================
    // 记录查询
    // ============================================================

    /**
     * 查询佣金记录详情。
     *
     * @param id 记录 ID
     * @return 记录 DTO
     * @throws ScrmException 记录不存在
     */
    @Transactional(readOnly = true)
    public ScrmCommissionRecordDto getRecord(Long id) throws ScrmException {
        return toRecordDto(findRecordOrThrow(id));
    }

    /**
     * 按佣金编号查询记录。
     *
     * @param recordNo 佣金编号
     * @return 记录 DTO
     * @throws ScrmException 记录不存在
     */
    @Transactional(readOnly = true)
    public ScrmCommissionRecordDto getRecordByNo(String recordNo) throws ScrmException {
        ScrmCommissionRecordEntity entity = recordRepository
                .findByRecordNo(recordNo)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "佣金记录不存在: recordNo=" + recordNo));
        return toRecordDto(entity);
    }

    /**
     * 分页查询佣金记录, 支持按方案/销售人员/状态/周期/时间范围过滤。
     *
     * @param planId        方案 ID (可空)
     * @param salesPersonId 销售人员 ID (可空)
     * @param status        状态 (可空)
     * @param period        所属周期 (可空)
     * @param startTime     计算时间下限 (可空)
     * @param endTime       计算时间上限 (可空)
     * @param pageable      分页参数
     * @return 记录分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmCommissionRecordDto> listRecords(Long planId, String salesPersonId, String status,
                                                      String period, LocalDateTime startTime, LocalDateTime endTime,
                                                      Pageable pageable) {
        Specification<ScrmCommissionRecordEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (planId != null) {
                predicates.add(cb.equal(root.get("planId"), planId));
            }
            if (salesPersonId != null && !salesPersonId.isBlank()) {
                predicates.add(cb.equal(root.get("salesPersonId"), salesPersonId));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (period != null && !period.isBlank()) {
                predicates.add(cb.equal(root.get("period"), period));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("calculatedAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("calculatedAt"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "calculatedAt"));
        return recordRepository.findAll(spec, sorted).map(this::toRecordDto);
    }

    /**
     * 按销售人员查询记录。
     *
     * @param salesPersonId 销售人员 ID
     * @param period        所属周期 (可空)
     * @param pageable      分页参数
     * @return 记录分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmCommissionRecordDto> getRecordsBySalesPerson(String salesPersonId, String period,
                                                                   Pageable pageable) {
        Specification<ScrmCommissionRecordEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("salesPersonId"), salesPersonId));
            if (period != null && !period.isBlank()) {
                predicates.add(cb.equal(root.get("period"), period));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "calculatedAt"));
        return recordRepository.findAll(spec, sorted).map(this::toRecordDto);
    }

    /**
     * 按订单查询记录。
     *
     * @param orderId 订单 ID
     * @return 记录列表
     */
    @Transactional(readOnly = true)
    public List<ScrmCommissionRecordDto> getRecordsByOrder(String orderId) {
        return recordRepository.findByOrderId(orderId)
                .stream().map(this::toRecordDto).collect(Collectors.toList());
    }

    // ============================================================
    // 审批管理
    // ============================================================

    /**
     * 提交审批 (将记录状态从 CALCULATED 改为 PENDING_APPROVAL)。
     *
     * @param recordIds 记录 ID 列表
     * @return 已提交的记录数
     * @throws ScrmException 记录不存在
     */
    @Transactional
    public int submitForApproval(List<Long> recordIds) throws ScrmException {
        if (recordIds == null || recordIds.isEmpty()) {
            throw ScrmException.badRequest("记录 ID 列表不能为空");
        }
        int count = 0;
        for (Long id : recordIds) {
            ScrmCommissionRecordEntity record = findRecordOrThrow(id);
            if (ScrmCommissionPlanService.RECORD_CALCULATED.equals(record.getStatus()) || ScrmCommissionPlanService.RECORD_ADJUSTED.equals(record.getStatus())) {
                record.setStatus(ScrmCommissionPlanService.RECORD_PENDING_APPROVAL);
                recordRepository.save(record);
                count++;
            }
        }
        log.info("提交佣金审批: total={}, submitted={}", recordIds.size(), count);
        return count;
    }

    /**
     * 审批 (通过/驳回)。
     *
     * @param approveDto 审批参数
     * @return 已审批的记录数
     * @throws ScrmException 记录不存在
     */
    @Transactional
    public int approve(ScrmCommissionApproveDto approveDto) throws ScrmException {
        if (approveDto == null || approveDto.getRecordIds() == null || approveDto.getRecordIds().isEmpty()) {
            throw ScrmException.badRequest("审批参数与记录 ID 列表不能为空");
        }
        return batchApprove(approveDto.getRecordIds(), approveDto.getAction(), approveDto.getNote());
    }

    /**
     * 批量审批。
     *
     * @param recordIds 记录 ID 列表
     * @param action    审批动作: APPROVE/REJECT
     * @param note      审批备注 (可空)
     * @return 已审批的记录数
     * @throws ScrmException 记录不存在 / 动作非法
     */
    @Transactional
    public int batchApprove(List<Long> recordIds, String action, String note) throws ScrmException {
        if (recordIds == null || recordIds.isEmpty()) {
            throw ScrmException.badRequest("记录 ID 列表不能为空");
        }
        if (!ACTION_APPROVE.equals(action) && !ACTION_REJECT.equals(action)) {
            throw ScrmException.badRequest("审批动作仅支持 APPROVE/REJECT");
        }
        int count = 0;
        LocalDateTime now = LocalDateTime.now();
        for (Long id : recordIds) {
            ScrmCommissionRecordEntity record = findRecordOrThrow(id);
            if (!ScrmCommissionPlanService.RECORD_PENDING_APPROVAL.equals(record.getStatus())) {
                log.warn("记录非待审批状态, 跳过: id={}, status={}", id, record.getStatus());
                continue;
            }
            if (ACTION_APPROVE.equals(action)) {
                record.setStatus(ScrmCommissionPlanService.RECORD_APPROVED);
            } else {
                record.setStatus(ScrmCommissionPlanService.RECORD_REJECTED);
            }
            record.setApprovedBy(ScrmCommissionPlanService.DEFAULT_OPERATOR);
            record.setApprovedAt(now);
            record.setApprovalNote(note);
            recordRepository.save(record);
            count++;
        }
        log.info("批量审批佣金: total={}, action={}, processed={}", recordIds.size(), action, count);
        return count;
    }

    /**
     * 驳回单条记录。
     *
     * @param recordId 记录 ID
     * @param reason   驳回原因
     * @return 更新后的记录
     * @throws ScrmException 记录不存在
     */
    @Transactional
    public ScrmCommissionRecordDto reject(Long recordId, String reason) throws ScrmException {
        ScrmCommissionRecordEntity record = findRecordOrThrow(recordId);
        record.setStatus(ScrmCommissionPlanService.RECORD_REJECTED);
        record.setApprovedBy(ScrmCommissionPlanService.DEFAULT_OPERATOR);
        record.setApprovedAt(LocalDateTime.now());
        record.setApprovalNote(reason);
        record = recordRepository.save(record);
        log.info("驳回佣金记录: id={}, reason={}", recordId, reason);
        return toRecordDto(record);
    }

    /**
     * 调整佣金金额。
     *
     * @param recordId  记录 ID
     * @param newAmount 新佣金金额
     * @param reason    调整原因
     * @return 更新后的记录
     * @throws ScrmException 记录不存在 / 金额非法
     */
    @Transactional
    public ScrmCommissionRecordDto adjustCommission(Long recordId, Double newAmount, String reason)
            throws ScrmException {
        if (newAmount == null || newAmount < 0) {
            throw ScrmException.badRequest("调整金额不能为空且不能为负");
        }
        ScrmCommissionRecordEntity record = findRecordOrThrow(recordId);
        record.setFinalCommission(round2(newAmount));
        record.setStatus(ScrmCommissionPlanService.RECORD_ADJUSTED);
        record.setNotes(reason);
        record = recordRepository.save(record);
        log.info("调整佣金: id={}, newAmount={}, reason={}", recordId, newAmount, reason);
        return toRecordDto(record);
    }

    // ============================================================
    // 发放管理
    // ============================================================

    /**
     * 发放佣金 (查询已审批 → 计算税额 → 创建发放记录 → 更新状态)。
     * <p>模拟实现: 税率 10%, 实发金额 = finalCommission - taxAmount, 发放后状态置为 PAID。</p>
     *
     * @param payoutDto 发放参数
     * @return 发放汇总
     * @throws ScrmException 参数非法
     */
    @Transactional
    public Map<String, Object> payout(ScrmCommissionPayoutDto payoutDto) throws ScrmException {
        if (payoutDto == null || payoutDto.getPeriod() == null || payoutDto.getPeriod().isBlank()) {
            throw ScrmException.badRequest("发放周期不能为空");
        }
        Specification<ScrmCommissionRecordEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("period"), payoutDto.getPeriod()));
            predicates.add(cb.equal(root.get("status"), ScrmCommissionPlanService.RECORD_APPROVED));
            if (payoutDto.getSalesPersonIds() != null && !payoutDto.getSalesPersonIds().isEmpty()) {
                predicates.add(root.get("salesPersonId").in(payoutDto.getSalesPersonIds()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmCommissionRecordEntity> records = recordRepository.findAll(spec);
        LocalDateTime now = LocalDateTime.now();
        LocalDate payoutDate = LocalDate.now();
        double totalGross = 0d;
        double totalTax = 0d;
        double totalNet = 0d;
        int paidCount = 0;
        for (ScrmCommissionRecordEntity record : records) {
            double gross = record.getFinalCommission() != null ? record.getFinalCommission() : 0d;
            double tax = round2(gross * DEFAULT_TAX_RATE);
            double net = round2(gross - tax);
            record.setStatus(ScrmCommissionPlanService.RECORD_PAID);
            record.setPayoutDate(payoutDate);
            record.setPaidAt(now);
            record.setPaidAmount(net);
            record.setTaxAmount(tax);
            recordRepository.save(record);
            totalGross += gross;
            totalTax += tax;
            totalNet += net;
            paidCount++;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", payoutDto.getPeriod());
        result.put("paidCount", paidCount);
        result.put("totalGross", round2(totalGross));
        result.put("totalTax", round2(totalTax));
        result.put("totalNet", round2(totalNet));
        result.put("payoutDate", payoutDate);
        log.info("发放佣金: period={}, paidCount={}, totalNet={}", payoutDto.getPeriod(), paidCount, totalNet);
        return result;
    }

    /**
     * 批量发放 (等价于 payout, 支持多个销售人员)。
     *
     * @param payoutDto 发放参数
     * @return 发放汇总
     * @throws ScrmException 参数非法
     */
    @Transactional
    public Map<String, Object> batchPayout(ScrmCommissionPayoutDto payoutDto) throws ScrmException {
        return payout(payoutDto);
    }

    /**
     * 发放汇总 (按周期统计已发放记录)。
     *
     * @param period 所属周期
     * @return 发放汇总
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getPayoutSummary(String period) {
        Specification<ScrmCommissionRecordEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (period != null && !period.isBlank()) {
                predicates.add(cb.equal(root.get("period"), period));
            }
            predicates.add(cb.equal(root.get("status"), ScrmCommissionPlanService.RECORD_PAID));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmCommissionRecordEntity> records = recordRepository.findAll(spec);
        double totalGross = records.stream()
                .mapToDouble(r -> r.getFinalCommission() != null ? r.getFinalCommission() : 0d).sum();
        double totalTax = records.stream()
                .mapToDouble(r -> r.getTaxAmount() != null ? r.getTaxAmount() : 0d).sum();
        double totalNet = records.stream()
                .mapToDouble(r -> r.getPaidAmount() != null ? r.getPaidAmount() : 0d).sum();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", period);
        result.put("paidCount", records.size());
        result.put("totalGross", round2(totalGross));
        result.put("totalTax", round2(totalTax));
        result.put("totalNet", round2(totalNet));
        return result;
    }

    /**
     * 退款追回 (将已发放记录置为 CLAWBACK, 记录追回金额与原因)。
     *
     * @param recordId 记录 ID
     * @param reason   追回原因
     * @param amount   追回金额
     * @return 更新后的记录
     * @throws ScrmException 记录不存在
     */
    @Transactional
    public ScrmCommissionRecordDto processClawback(Long recordId, String reason, Double amount)
            throws ScrmException {
        ScrmCommissionRecordEntity record = findRecordOrThrow(recordId);
        if (amount == null || amount < 0) {
            throw ScrmException.badRequest("追回金额不能为空且不能为负");
        }
        record.setStatus(ScrmCommissionPlanService.RECORD_CLAWBACK);
        record.setClawbackAmount(round2(amount));
        record.setClawbackReason(reason);
        record.setClawbackAt(LocalDateTime.now());
        record = recordRepository.save(record);
        log.info("佣金退款追回: id={}, amount={}, reason={}", recordId, amount, reason);
        return toRecordDto(record);
    }

    /**
     * 标记已发放。
     *
     * @param recordId   记录 ID
     * @param paidAmount 实发金额
     * @return 更新后的记录
     * @throws ScrmException 记录不存在
     */
    @Transactional
    public ScrmCommissionRecordDto markAsPaid(Long recordId, Double paidAmount) throws ScrmException {
        ScrmCommissionRecordEntity record = findRecordOrThrow(recordId);
        if (paidAmount == null || paidAmount < 0) {
            throw ScrmException.badRequest("实发金额不能为空且不能为负");
        }
        record.setStatus(ScrmCommissionPlanService.RECORD_PAID);
        record.setPaidAmount(round2(paidAmount));
        record.setPaidAt(LocalDateTime.now());
        record.setPayoutDate(LocalDate.now());
        record = recordRepository.save(record);
        log.info("标记佣金已发放: id={}, paidAmount={}", recordId, paidAmount);
        return toRecordDto(record);
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 佣金统计 (总佣金/已发放/待审批/各方案/各团队)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   结束时间 (可空)
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCommissionStats(LocalDateTime startTime, LocalDateTime endTime) {
        Specification<ScrmCommissionRecordEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("calculatedAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("calculatedAt"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmCommissionRecordEntity> records = recordRepository.findAll(spec);
        double totalCommission = records.stream()
                .mapToDouble(r -> r.getFinalCommission() != null ? r.getFinalCommission() : 0d).sum();
        double totalPaid = records.stream()
                .filter(r -> ScrmCommissionPlanService.RECORD_PAID.equals(r.getStatus()))
                .mapToDouble(r -> r.getPaidAmount() != null ? r.getPaidAmount() : 0d).sum();
        long pendingApproval = records.stream()
                .filter(r -> ScrmCommissionPlanService.RECORD_PENDING_APPROVAL.equals(r.getStatus())).count();
        long approved = records.stream()
                .filter(r -> ScrmCommissionPlanService.RECORD_APPROVED.equals(r.getStatus())).count();
        // 按方案汇总
        Map<String, Double> byPlan = records.stream()
                .filter(r -> r.getPlanName() != null)
                .collect(Collectors.groupingBy(ScrmCommissionRecordEntity::getPlanName,
                        Collectors.summingDouble(r -> r.getFinalCommission() != null ? r.getFinalCommission() : 0d)));
        // 按团队汇总
        Map<String, Double> byTeam = records.stream()
                .filter(r -> r.getTeamName() != null)
                .collect(Collectors.groupingBy(ScrmCommissionRecordEntity::getTeamName,
                        Collectors.summingDouble(r -> r.getFinalCommission() != null ? r.getFinalCommission() : 0d)));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalRecords", records.size());
        result.put("totalCommission", round2(totalCommission));
        result.put("totalPaid", round2(totalPaid));
        result.put("pendingApproval", pendingApproval);
        result.put("approved", approved);
        result.put("byPlan", byPlan.entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("planName", e.getKey());
                    m.put("totalCommission", round2(e.getValue()));
                    return m;
                })
                .collect(Collectors.toList()));
        result.put("byTeam", byTeam.entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("teamName", e.getKey());
                    m.put("totalCommission", round2(e.getValue()));
                    return m;
                })
                .collect(Collectors.toList()));
        return result;
    }

    /**
     * 销售人员佣金排行。
     *
     * @param period 所属周期 (可空)
     * @param limit  返回条数 (默认 10)
     * @return 排行列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSalesPersonRanking(String period, Integer limit) {
        int topN = limit != null && limit > 0 ? limit : DEFAULT_RANKING_LIMIT;
        Specification<ScrmCommissionRecordEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (period != null && !period.isBlank()) {
                predicates.add(cb.equal(root.get("period"), period));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmCommissionRecordEntity> records = recordRepository.findAll(spec);
        Map<String, Double> bySalesPerson = records.stream()
                .collect(Collectors.groupingBy(ScrmCommissionRecordEntity::getSalesPersonId,
                        Collectors.summingDouble(r -> r.getFinalCommission() != null ? r.getFinalCommission() : 0d)));
        Map<String, String> nameMap = records.stream()
                .filter(r -> r.getSalesPersonName() != null)
                .collect(Collectors.toMap(ScrmCommissionRecordEntity::getSalesPersonId,
                        ScrmCommissionRecordEntity::getSalesPersonName, (a, b) -> a));
        return bySalesPerson.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topN)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("salesPersonId", e.getKey());
                    m.put("salesPersonName", nameMap.getOrDefault(e.getKey(), e.getKey()));
                    m.put("totalCommission", round2(e.getValue()));
                    return m;
                })
                .collect(Collectors.toList());
    }

    /**
     * 团队统计。
     *
     * @param period 所属周期 (可空)
     * @return 团队统计列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTeamStats(String period) {
        Specification<ScrmCommissionRecordEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (period != null && !period.isBlank()) {
                predicates.add(cb.equal(root.get("period"), period));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmCommissionRecordEntity> records = recordRepository.findAll(spec);
        Map<String, List<ScrmCommissionRecordEntity>> byTeam = records.stream()
                .filter(r -> r.getTeamId() != null)
                .collect(Collectors.groupingBy(ScrmCommissionRecordEntity::getTeamId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<ScrmCommissionRecordEntity>> entry : byTeam.entrySet()) {
            String teamId = entry.getKey();
            List<ScrmCommissionRecordEntity> teamRecords = entry.getValue();
            String teamName = teamRecords.stream()
                    .map(ScrmCommissionRecordEntity::getTeamName)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(teamId);
            double totalCommission = teamRecords.stream()
                    .mapToDouble(r -> r.getFinalCommission() != null ? r.getFinalCommission() : 0d).sum();
            long salesPersonCount = teamRecords.stream()
                    .map(ScrmCommissionRecordEntity::getSalesPersonId)
                    .distinct().count();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("teamId", teamId);
            m.put("teamName", teamName);
            m.put("totalCommission", round2(totalCommission));
            m.put("recordCount", teamRecords.size());
            m.put("salesPersonCount", salesPersonCount);
            result.add(m);
        }
        result.sort((a, b) -> Double.compare((Double) b.get("totalCommission"), (Double) a.get("totalCommission")));
        return result;
    }

    /**
     * 方案效果统计。
     *
     * @param planId    方案 ID
     * @param startTime 起始时间 (可空)
     * @param endTime   结束时间 (可空)
     * @return 方案效果
     * @throws ScrmException 方案不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getPlanPerformance(Long planId, LocalDateTime startTime, LocalDateTime endTime)
            throws ScrmException {
        planService.findPlanOrThrow(planId);
        Specification<ScrmCommissionRecordEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("planId"), planId));
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("calculatedAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("calculatedAt"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmCommissionRecordEntity> records = recordRepository.findAll(spec);
        double totalCommission = records.stream()
                .mapToDouble(r -> r.getFinalCommission() != null ? r.getFinalCommission() : 0d).sum();
        double totalOrderAmount = records.stream()
                .mapToDouble(r -> r.getOrderAmount() != null ? r.getOrderAmount() : 0d).sum();
        double totalPaid = records.stream()
                .filter(r -> ScrmCommissionPlanService.RECORD_PAID.equals(r.getStatus()))
                .mapToDouble(r -> r.getPaidAmount() != null ? r.getPaidAmount() : 0d).sum();
        long paidCount = records.stream().filter(r -> ScrmCommissionPlanService.RECORD_PAID.equals(r.getStatus())).count();
        long pendingCount = records.stream().filter(r -> ScrmCommissionPlanService.RECORD_PENDING_APPROVAL.equals(r.getStatus())).count();
        double avgCommission = records.isEmpty() ? 0d : totalCommission / records.size();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("planId", planId);
        result.put("totalRecords", records.size());
        result.put("totalCommission", round2(totalCommission));
        result.put("totalOrderAmount", round2(totalOrderAmount));
        result.put("totalPaid", round2(totalPaid));
        result.put("paidCount", paidCount);
        result.put("pendingCount", pendingCount);
        result.put("avgCommission", round2(avgCommission));
        result.put("commissionRate", totalOrderAmount > 0
                ? Math.round(totalCommission / totalOrderAmount * 10000d) / 100d : 0d);
        return result;
    }

    /**
     * 佣金趋势 (最近 days 天每日佣金总额)。
     *
     * @param days 天数 (默认 7)
     * @return 趋势数据列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCommissionTrend(Integer days) {
        int trendDays = days != null && days > 0 ? days : DEFAULT_TREND_DAYS;
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(trendDays - 1L);
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = today.plusDays(1L).atStartOfDay();
        Specification<ScrmCommissionRecordEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.greaterThanOrEqualTo(root.get("calculatedAt"), startDateTime));
            predicates.add(cb.lessThan(root.get("calculatedAt"), endDateTime));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmCommissionRecordEntity> records = recordRepository.findAll(spec);
        Map<LocalDate, Double> dailyMap = new LinkedHashMap<>();
        for (ScrmCommissionRecordEntity r : records) {
            LocalDate date = r.getCalculatedAt().toLocalDate();
            dailyMap.merge(date, r.getFinalCommission() != null ? r.getFinalCommission() : 0d, Double::sum);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < trendDays; i++) {
            LocalDate date = startDate.plusDays(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", date);
            m.put("totalCommission", round2(dailyMap.getOrDefault(date, 0d)));
            result.add(m);
        }
        return result;
    }

    /**
     * 发放报告 (按周期 + 销售人员汇总发放金额)。
     *
     * @param period 所属周期
     * @return 发放报告
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getPayoutReport(String period) {
        Specification<ScrmCommissionRecordEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (period != null && !period.isBlank()) {
                predicates.add(cb.equal(root.get("period"), period));
            }
            predicates.add(cb.equal(root.get("status"), ScrmCommissionPlanService.RECORD_PAID));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmCommissionRecordEntity> records = recordRepository.findAll(spec);
        double totalGross = records.stream()
                .mapToDouble(r -> r.getFinalCommission() != null ? r.getFinalCommission() : 0d).sum();
        double totalTax = records.stream()
                .mapToDouble(r -> r.getTaxAmount() != null ? r.getTaxAmount() : 0d).sum();
        double totalNet = records.stream()
                .mapToDouble(r -> r.getPaidAmount() != null ? r.getPaidAmount() : 0d).sum();
        Map<String, Double> bySalesPerson = records.stream()
                .collect(Collectors.groupingBy(ScrmCommissionRecordEntity::getSalesPersonId,
                        Collectors.summingDouble(r -> r.getPaidAmount() != null ? r.getPaidAmount() : 0d)));
        Map<String, String> nameMap = records.stream()
                .filter(r -> r.getSalesPersonName() != null)
                .collect(Collectors.toMap(ScrmCommissionRecordEntity::getSalesPersonId,
                        ScrmCommissionRecordEntity::getSalesPersonName, (a, b) -> a));
        List<Map<String, Object>> details = bySalesPerson.entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("salesPersonId", e.getKey());
                    m.put("salesPersonName", nameMap.getOrDefault(e.getKey(), e.getKey()));
                    m.put("paidAmount", round2(e.getValue()));
                    return m;
                })
                .sorted((a, b) -> Double.compare((Double) b.get("paidAmount"), (Double) a.get("paidAmount")))
                .collect(Collectors.toList());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", period);
        result.put("paidCount", records.size());
        result.put("totalGross", round2(totalGross));
        result.put("totalTax", round2(totalTax));
        result.put("totalNet", round2(totalNet));
        result.put("details", details);
        return result;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 按主键查询记录, 不存在或越权抛异常。
     */
    ScrmCommissionRecordEntity findRecordOrThrow(Long id) throws ScrmException {
        ScrmCommissionRecordEntity entity = recordRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "佣金记录不存在: id=" + id));
        return entity;
    }

    /**
     * 金额保留两位小数。
     *
     * @param value 金额
     * @return 保留两位小数后的金额
     */
    double round2(double value) {
        return Math.round(value * MONEY_SCALE) / MONEY_SCALE;
    }

    /**
     * 记录实体转 DTO
     */
    ScrmCommissionRecordDto toRecordDto(ScrmCommissionRecordEntity entity) {
        ScrmCommissionRecordDto dto = new ScrmCommissionRecordDto();
        dto.setId(entity.getId());
        dto.setRecordNo(entity.getRecordNo());
        dto.setPlanId(entity.getPlanId());
        dto.setPlanName(entity.getPlanName());
        dto.setRuleId(entity.getRuleId());
        dto.setRuleName(entity.getRuleName());
        dto.setSalesPersonId(entity.getSalesPersonId());
        dto.setSalesPersonName(entity.getSalesPersonName());
        dto.setTeamId(entity.getTeamId());
        dto.setTeamName(entity.getTeamName());
        dto.setOrderId(entity.getOrderId());
        dto.setOrderAmount(entity.getOrderAmount());
        dto.setOrderProfit(entity.getOrderProfit());
        dto.setOrderDate(entity.getOrderDate());
        dto.setProductCategory(entity.getProductCategory());
        dto.setCustomerType(entity.getCustomerType());
        dto.setCommissionBasis(entity.getCommissionBasis());
        dto.setCommissionRate(entity.getCommissionRate());
        dto.setCommissionAmount(entity.getCommissionAmount());
        dto.setBonusAmount(entity.getBonusAmount());
        dto.setDeductionAmount(entity.getDeductionAmount());
        dto.setFinalCommission(entity.getFinalCommission());
        dto.setCalculationDetails(entity.getCalculationDetails());
        dto.setStatus(entity.getStatus());
        dto.setPeriod(entity.getPeriod());
        dto.setPayoutDate(entity.getPayoutDate());
        dto.setApprovedBy(entity.getApprovedBy());
        dto.setApprovedAt(entity.getApprovedAt());
        dto.setApprovalNote(entity.getApprovalNote());
        dto.setPaidAt(entity.getPaidAt());
        dto.setPaidAmount(entity.getPaidAmount());
        dto.setTaxAmount(entity.getTaxAmount());
        dto.setDeductionNote(entity.getDeductionNote());
        dto.setClawbackAmount(entity.getClawbackAmount());
        dto.setClawbackReason(entity.getClawbackReason());
        dto.setClawbackAt(entity.getClawbackAt());
        dto.setNotes(entity.getNotes());
        dto.setCalculatedAt(entity.getCalculatedAt());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}