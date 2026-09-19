/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBudgetExpenseService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmBudgetExpenseApproveDto;
import org.hiylo.scrm.dto.ScrmBudgetExpenseDto;
import org.hiylo.scrm.entity.ScrmBudgetAllocationEntity;
import org.hiylo.scrm.entity.ScrmBudgetExpenseEntity;
import org.hiylo.scrm.entity.ScrmBudgetPlanEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmBudgetAllocationRepository;
import org.hiylo.scrm.repository.ScrmBudgetExpenseRepository;
import org.hiylo.scrm.repository.ScrmBudgetPlanRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * SCRM 预算费用管理服务。
 * <p>
 * 承载预算支出管理能力: 支出增删改查 / 审批 (通过更新预算消耗与 ROI) / 驳回 /
 * 标记已付款 / 待审批 / 批量审批, 以及支出统计、预算趋势与支出最高活动排行。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmBudgetExpenseService {

    // ==================== 状态常量 ====================

    /** 支出状态: 待审批 */
    private static final String EXPENSE_PENDING = "PENDING";
    /** 支出状态: 已通过 */
    private static final String EXPENSE_APPROVED = "APPROVED";
    /** 支出状态: 已驳回 */
    private static final String EXPENSE_REJECTED = "REJECTED";
    /** 支出状态: 已付款 */
    private static final String EXPENSE_PAID = "PAID";

    /** 付款状态: 待付款 */
    private static final String PAYMENT_PENDING = "PENDING";
    /** 付款状态: 已付款 */
    private static final String PAYMENT_PAID = "PAID";
    /** 付款状态: 已取消 */
    private static final String PAYMENT_CANCELLED = "CANCELLED";

    /** 审批动作: 通过 */
    private static final String ACTION_APPROVE = "APPROVE";
    /** 审批动作: 驳回 */
    private static final String ACTION_REJECT = "REJECT";

    /** 默认操作人 */
    private static final String DEFAULT_OPERATOR = "scrm-system";
    /** 支出编号前缀 */
    private static final String EXPENSE_NO_PREFIX = "EXP";
    /** 金额精度 (保留两位小数) */
    private static final double MONEY_SCALE = 100d;
    /** 默认排行条数 */
    private static final int DEFAULT_LIMIT = 10;
    /** 默认趋势月数 */
    private static final int DEFAULT_TREND_MONTHS = 6;

    /** 预算支出数据访问层 */
    private final ScrmBudgetExpenseRepository expenseRepository;

    /** 预算方案数据访问层 */
    private final ScrmBudgetPlanRepository planRepository;

    /** 预算分配数据访问层 */
    private final ScrmBudgetAllocationRepository allocationRepository;

    /** 预算方案管理服务 (支出审批联动方案统计) */
    private final ScrmBudgetPlanService planService;

    /** 预算分配管理服务 (支出审批联动分配消耗统计) */
    private final ScrmBudgetAllocationService allocationService;

    /** JSON 解析器 (解析 breakdown / attachments) */
    private final ObjectMapper objectMapper;

    /**
     * 创建支出。
     * <p>自动生成支出编号 (EXP+年月日+序号), 关联方案与分配名称冗余便于展示。</p>
     *
     * @param dto 支出参数
     * @return 创建后的支出
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmBudgetExpenseDto createExpense(ScrmBudgetExpenseDto dto) throws ScrmException {
        validateExpenseDto(dto, false);
        ScrmBudgetExpenseEntity entity = new ScrmBudgetExpenseEntity();
        entity.setExpenseNo(generateExpenseNo());
        entity.setPlanId(dto.getPlanId());
        if (dto.getPlanId() != null) {
            ScrmBudgetPlanEntity plan = planRepository.findById(dto.getPlanId()).orElse(null);

        }
        entity.setAllocationId(dto.getAllocationId());
        if (dto.getAllocationId() != null) {
            ScrmBudgetAllocationEntity allocation = allocationRepository.findById(dto.getAllocationId()).orElse(null);

        }
        entity.setExpenseType(dto.getExpenseType());
        entity.setCampaignId(dto.getCampaignId());
        entity.setCampaignName(dto.getCampaignName());
        entity.setExpenseDate(dto.getExpenseDate());
        entity.setAmount(dto.getAmount());
        entity.setCurrency(dto.getCurrency() != null ? dto.getCurrency() : "CNY");
        entity.setDescription(dto.getDescription());
        entity.setVendor(dto.getVendor());
        entity.setInvoiceNo(dto.getInvoiceNo());
        entity.setPaymentMethod(dto.getPaymentMethod());
        entity.setPaymentStatus(PAYMENT_PENDING);
        entity.setStatus(EXPENSE_PENDING);
        entity.setReceiptUrl(dto.getReceiptUrl());
        entity.setAttachments(dto.getAttachments());
        entity.setDepartmentId(dto.getDepartmentId());
        entity.setDepartmentName(dto.getDepartmentName());
        entity.setRequesterId(dto.getRequesterId());
        entity.setRequesterName(dto.getRequesterName());
        entity.setTags(dto.getTags());
        entity.setNotes(dto.getNotes());
        entity.setCreatedBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : DEFAULT_OPERATOR);
        entity = expenseRepository.save(entity);
        log.info("创建预算支出: id={}, expenseNo={}, expenseType={}, amount={}",
                entity.getId(), entity.getExpenseNo(), entity.getExpenseType(), entity.getAmount());
        return toExpenseDto(entity);
    }

    /**
     * 更新支出（字段非空才覆盖, 仅 PENDING 状态可更新）。
     *
     * @param id  支出 ID
     * @param dto 支出参数
     * @return 更新后的支出
     * @throws ScrmException 支出不存在 / 参数非法 / 状态不允许
     */
    @Transactional
    public ScrmBudgetExpenseDto updateExpense(Long id, ScrmBudgetExpenseDto dto) throws ScrmException {
        ScrmBudgetExpenseEntity entity = findExpenseOrThrow(id);
        if (!EXPENSE_PENDING.equals(entity.getStatus())) {
            throw ScrmException.badRequest("仅待审批状态支出可更新, 当前状态: " + entity.getStatus());
        }
        validateExpenseDto(dto, true);
        if (dto.getExpenseType() != null) entity.setExpenseType(dto.getExpenseType());
        if (dto.getCampaignId() != null) entity.setCampaignId(dto.getCampaignId());
        if (dto.getCampaignName() != null) entity.setCampaignName(dto.getCampaignName());
        if (dto.getExpenseDate() != null) entity.setExpenseDate(dto.getExpenseDate());
        if (dto.getAmount() != null) entity.setAmount(dto.getAmount());
        if (dto.getCurrency() != null) entity.setCurrency(dto.getCurrency());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getVendor() != null) entity.setVendor(dto.getVendor());
        if (dto.getInvoiceNo() != null) entity.setInvoiceNo(dto.getInvoiceNo());
        if (dto.getPaymentMethod() != null) entity.setPaymentMethod(dto.getPaymentMethod());
        if (dto.getReceiptUrl() != null) entity.setReceiptUrl(dto.getReceiptUrl());
        if (dto.getAttachments() != null) entity.setAttachments(dto.getAttachments());
        if (dto.getDepartmentId() != null) entity.setDepartmentId(dto.getDepartmentId());
        if (dto.getDepartmentName() != null) entity.setDepartmentName(dto.getDepartmentName());
        if (dto.getRequesterId() != null) entity.setRequesterId(dto.getRequesterId());
        if (dto.getRequesterName() != null) entity.setRequesterName(dto.getRequesterName());
        if (dto.getTags() != null) entity.setTags(dto.getTags());
        if (dto.getNotes() != null) entity.setNotes(dto.getNotes());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = expenseRepository.save(entity);
        log.info("更新预算支出: id={}, expenseNo={}", entity.getId(), entity.getExpenseNo());
        return toExpenseDto(entity);
    }

    /**
     * 删除支出。
     *
     * @param id 支出 ID
     * @throws ScrmException 支出不存在
     */
    @Transactional
    public void deleteExpense(Long id) throws ScrmException {
        ScrmBudgetExpenseEntity entity = findExpenseOrThrow(id);
        expenseRepository.delete(entity);
        log.info("删除预算支出: id={}, expenseNo={}", id, entity.getExpenseNo());
    }

    /**
     * 查询支出详情。
     *
     * @param id 支出 ID
     * @return 支出 DTO
     * @throws ScrmException 支出不存在
     */
    @Transactional(readOnly = true)
    public ScrmBudgetExpenseDto getExpense(Long id) throws ScrmException {
        return toExpenseDto(findExpenseOrThrow(id));
    }

    /**
     * 按支出编号查询支出。
     *
     * @param expenseNo 支出编号
     * @return 支出 DTO
     * @throws ScrmException 支出不存在
     */
    @Transactional(readOnly = true)
    public ScrmBudgetExpenseDto getExpenseByNo(String expenseNo) throws ScrmException {
        ScrmBudgetExpenseEntity entity = expenseRepository
                .findByExpenseNo(expenseNo)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "预算支出不存在: expenseNo=" + expenseNo));
        return toExpenseDto(entity);
    }

    /**
     * 分页查询支出, 支持按方案/分配/活动/类型/状态/时间范围过滤。
     *
     * @param planId       方案 ID (可空)
     * @param allocationId 分配 ID (可空)
     * @param campaignId   营销活动 ID (可空)
     * @param expenseType  支出类型 (可空)
     * @param status       状态 (可空)
     * @param startTime    支出日期下限 (可空)
     * @param endTime      支出日期上限 (可空)
     * @param pageable     分页参数
     * @return 支出分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmBudgetExpenseDto> listExpenses(Long planId, Long allocationId, Long campaignId,
                                                    String expenseType, String status,
                                                    LocalDateTime startTime, LocalDateTime endTime,
                                                    Pageable pageable) {
        Specification<ScrmBudgetExpenseEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (planId != null) {
                predicates.add(cb.equal(root.get("planId"), planId));
            }
            if (allocationId != null) {
                predicates.add(cb.equal(root.get("allocationId"), allocationId));
            }
            if (campaignId != null) {
                predicates.add(cb.equal(root.get("campaignId"), campaignId));
            }
            if (expenseType != null && !expenseType.isBlank()) {
                predicates.add(cb.equal(root.get("expenseType"), expenseType));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("expenseDate"), startTime.toLocalDate()));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("expenseDate"), endTime.toLocalDate()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "expenseDate").and(Sort.by(Sort.Direction.DESC, "createTime")));
        return expenseRepository.findAll(spec, sorted).map(this::toExpenseDto);
    }

    /**
     * 审批支出 (通过 → 更新预算消耗与 ROI; 驳回 → 仅更新状态)。
     *
     * @param approveDto 审批参数
     * @return 更新后的支出
     * @throws ScrmException 支出不存在 / 动作非法 / 状态不允许
     */
    @Transactional
    public ScrmBudgetExpenseDto approveExpense(ScrmBudgetExpenseApproveDto approveDto) throws ScrmException {
        if (approveDto == null || approveDto.getExpenseId() == null) {
            throw ScrmException.badRequest("审批参数与支出 ID 不能为空");
        }
        if (!ACTION_APPROVE.equals(approveDto.getAction()) && !ACTION_REJECT.equals(approveDto.getAction())) {
            throw ScrmException.badRequest("审批动作仅支持 APPROVE/REJECT");
        }
        ScrmBudgetExpenseEntity entity = findExpenseOrThrow(approveDto.getExpenseId());
        if (!EXPENSE_PENDING.equals(entity.getStatus())) {
            throw ScrmException.badRequest("支出非待审批状态, 当前状态: " + entity.getStatus());
        }
        LocalDateTime now = LocalDateTime.now();
        if (ACTION_APPROVE.equals(approveDto.getAction())) {
            entity.setStatus(EXPENSE_APPROVED);
            // 通过后更新分配消耗统计与方案统计
            if (entity.getAllocationId() != null) {
                allocationService.updateAllocationStats(entity.getAllocationId());
            }
            if (entity.getPlanId() != null) {
                planService.updatePlanStats(entity.getPlanId());
            }
            log.info("审批通过预算支出: id={}, expenseNo={}", entity.getId(), entity.getExpenseNo());
        } else {
            entity.setStatus(EXPENSE_REJECTED);
            log.info("驳回预算支出: id={}, expenseNo={}", entity.getId(), entity.getExpenseNo());
        }
        entity.setApprovedBy(DEFAULT_OPERATOR);
        entity.setApprovedAt(now);
        entity.setApproverComment(approveDto.getComment());
        entity = expenseRepository.save(entity);
        return toExpenseDto(entity);
    }

    /**
     * 驳回支出。
     *
     * @param id     支出 ID
     * @param reason 驳回原因
     * @return 更新后的支出
     * @throws ScrmException 支出不存在
     */
    @Transactional
    public ScrmBudgetExpenseDto rejectExpense(Long id, String reason) throws ScrmException {
        ScrmBudgetExpenseEntity entity = findExpenseOrThrow(id);
        entity.setStatus(EXPENSE_REJECTED);
        entity.setApprovedBy(DEFAULT_OPERATOR);
        entity.setApprovedAt(LocalDateTime.now());
        entity.setApproverComment(reason);
        entity = expenseRepository.save(entity);
        log.info("驳回预算支出: id={}, reason={}", id, reason);
        return toExpenseDto(entity);
    }

    /**
     * 标记支出已付款。
     *
     * @param id 支出 ID
     * @return 更新后的支出
     * @throws ScrmException 支出不存在 / 状态不允许
     */
    @Transactional
    public ScrmBudgetExpenseDto markAsPaid(Long id) throws ScrmException {
        ScrmBudgetExpenseEntity entity = findExpenseOrThrow(id);
        if (!EXPENSE_APPROVED.equals(entity.getStatus())) {
            throw ScrmException.badRequest("仅已通过状态支出可标记已付款, 当前状态: " + entity.getStatus());
        }
        entity.setStatus(EXPENSE_PAID);
        entity.setPaymentStatus(PAYMENT_PAID);
        entity.setPaidAt(LocalDateTime.now());
        entity = expenseRepository.save(entity);
        log.info("标记预算支出已付款: id={}, expenseNo={}", id, entity.getExpenseNo());
        return toExpenseDto(entity);
    }

    /**
     * 分页查询待审批支出。
     *
     * @param pageable 分页参数
     * @return 支出分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmBudgetExpenseDto> getPendingExpenses(Pageable pageable) {
        Specification<ScrmBudgetExpenseEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), EXPENSE_PENDING));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.ASC, "expenseDate"));
        return expenseRepository.findAll(spec, sorted).map(this::toExpenseDto);
    }

    /**
     * 按方案分页查询支出。
     *
     * @param planId   方案 ID
     * @param pageable 分页参数
     * @return 支出分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmBudgetExpenseDto> getExpensesByPlan(Long planId, Pageable pageable) {
        Specification<ScrmBudgetExpenseEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("planId"), planId));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "expenseDate"));
        return expenseRepository.findAll(spec, sorted).map(this::toExpenseDto);
    }

    /**
     * 按营销活动分页查询支出。
     *
     * @param campaignId 营销活动 ID
     * @param pageable   分页参数
     * @return 支出分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmBudgetExpenseDto> getExpensesByCampaign(Long campaignId, Pageable pageable) {
        Specification<ScrmBudgetExpenseEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("campaignId"), campaignId));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "expenseDate"));
        return expenseRepository.findAll(spec, sorted).map(this::toExpenseDto);
    }

    /**
     * 批量审批支出。
     *
     * @param expenseIds 支出 ID 列表
     * @param action     审批动作: APPROVE/REJECT
     * @param comment    审批备注 (可空)
     * @return 已审批的记录数
     * @throws ScrmException 列表为空 / 动作非法
     */
    @Transactional
    public int batchApprove(List<Long> expenseIds, String action, String comment) throws ScrmException {
        if (expenseIds == null || expenseIds.isEmpty()) {
            throw ScrmException.badRequest("支出 ID 列表不能为空");
        }
        if (!ACTION_APPROVE.equals(action) && !ACTION_REJECT.equals(action)) {
            throw ScrmException.badRequest("审批动作仅支持 APPROVE/REJECT");
        }
        int count = 0;
        for (Long id : expenseIds) {
            try {
                ScrmBudgetExpenseApproveDto dto = new ScrmBudgetExpenseApproveDto();
                dto.setExpenseId(id);
                dto.setAction(action);
                dto.setComment(comment);
                approveExpense(dto);
                count++;
            } catch (ScrmException e) {
                log.warn("批量审批支出跳过: id={}, err={}", id, e.getMessage());
            }
        }
        log.info("批量审批预算支出: total={}, action={}, processed={}", expenseIds.size(), action, count);
        return count;
    }

    /**
     * 支出统计 (按类型/部门/活动汇总)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   结束时间 (可空)
     * @return 支出统计
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSpendStats(LocalDateTime startTime, LocalDateTime endTime) {
        Specification<ScrmBudgetExpenseEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("expenseDate"), startTime.toLocalDate()));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("expenseDate"), endTime.toLocalDate()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmBudgetExpenseEntity> expenses = expenseRepository.findAll(spec);
        double totalAmount = expenses.stream()
                .mapToDouble(e -> e.getAmount() != null ? e.getAmount() : 0d).sum();
        Map<String, Double> byType = expenses.stream()
                .filter(e -> e.getExpenseType() != null)
                .collect(Collectors.groupingBy(ScrmBudgetExpenseEntity::getExpenseType,
                        Collectors.summingDouble(e -> e.getAmount() != null ? e.getAmount() : 0d)));
        Map<String, Double> byDepartment = expenses.stream()
                .filter(e -> e.getDepartmentName() != null)
                .collect(Collectors.groupingBy(ScrmBudgetExpenseEntity::getDepartmentName,
                        Collectors.summingDouble(e -> e.getAmount() != null ? e.getAmount() : 0d)));
        Map<String, Double> byCampaign = expenses.stream()
                .filter(e -> e.getCampaignName() != null)
                .collect(Collectors.groupingBy(ScrmBudgetExpenseEntity::getCampaignName,
                        Collectors.summingDouble(e -> e.getAmount() != null ? e.getAmount() : 0d)));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("expenseCount", expenses.size());
        result.put("totalAmount", round2(totalAmount));
        result.put("byType", byType.entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("expenseType", e.getKey());
                    m.put("amount", round2(e.getValue()));
                    return m;
                })
                .collect(Collectors.toList()));
        result.put("byDepartment", byDepartment.entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("departmentName", e.getKey());
                    m.put("amount", round2(e.getValue()));
                    return m;
                })
                .collect(Collectors.toList()));
        result.put("byCampaign", byCampaign.entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("campaignName", e.getKey());
                    m.put("amount", round2(e.getValue()));
                    return m;
                })
                .collect(Collectors.toList()));
        return result;
    }

    /**
     * 预算趋势 (最近 months 个月每月支出总额)。
     *
     * @param months 月数 (默认 6)
     * @return 趋势数据列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getBudgetTrend(Integer months) {
        int trendMonths = months != null && months > 0 ? months : DEFAULT_TREND_MONTHS;
        YearMonth current = YearMonth.now();
        YearMonth start = current.minusMonths(trendMonths - 1L);
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < trendMonths; i++) {
            YearMonth ym = start.plusMonths(i);
            LocalDate periodStart = ym.atDay(1);
            LocalDate periodEnd = ym.atEndOfMonth();
            Specification<ScrmBudgetExpenseEntity> spec = (root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();
                predicates.add(cb.greaterThanOrEqualTo(root.get("expenseDate"), periodStart));
                predicates.add(cb.lessThanOrEqualTo(root.get("expenseDate"), periodEnd));
                return cb.and(predicates.toArray(new Predicate[0]));
            };
            List<ScrmBudgetExpenseEntity> expenses = expenseRepository.findAll(spec);
            double monthlySpend = expenses.stream()
                    .mapToDouble(e -> e.getAmount() != null ? e.getAmount() : 0d).sum();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("period", ym.format(DateTimeFormatter.ofPattern("yyyy-MM")));
            m.put("expenseCount", expenses.size());
            m.put("totalSpend", round2(monthlySpend));
            result.add(m);
        }
        return result;
    }

    /**
     * 支出最高的活动 (按活动汇总支出金额倒序)。
     *
     * @param limit 返回条数 (默认 10)
     * @return 活动支出排行列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTopSpendingCampaigns(Integer limit) {
        int topN = limit != null && limit > 0 ? limit : DEFAULT_LIMIT;
        List<ScrmBudgetExpenseEntity> expenses = expenseRepository.findAll((root, query, cb) ->
                cb.and());
        Map<Long, List<ScrmBudgetExpenseEntity>> byCampaign = expenses.stream()
                .filter(e -> e.getCampaignId() != null)
                .collect(Collectors.groupingBy(ScrmBudgetExpenseEntity::getCampaignId));
        return byCampaign.entrySet().stream()
                .map(e -> {
                    String campaignName = e.getValue().stream()
                            .map(ScrmBudgetExpenseEntity::getCampaignName)
                            .filter(Objects::nonNull)
                            .findFirst()
                            .orElse(String.valueOf(e.getKey()));
                    double totalSpend = e.getValue().stream()
                            .mapToDouble(exp -> exp.getAmount() != null ? exp.getAmount() : 0d).sum();
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("campaignId", e.getKey());
                    m.put("campaignName", campaignName);
                    m.put("expenseCount", e.getValue().size());
                    m.put("totalSpend", round2(totalSpend));
                    return m;
                })
                .sorted((a, b) -> Double.compare((Double) b.get("totalSpend"), (Double) a.get("totalSpend")))
                .limit(topN)
                .collect(Collectors.toList());
    }

    /**
     * 生成支出编号: EXP + yyyyMMdd + 6 位毫秒时间戳后缀。
     *
     * @return 支出编号
     */
    public String generateExpenseNo() {
        String ymd = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String suffix = String.format("%06d", System.currentTimeMillis() % 1000000L);
        return EXPENSE_NO_PREFIX + ymd + suffix;
    }

    /**
     * 校验支出 DTO。
     *
     * @param dto     支出参数
     * @param partial 是否部分更新
     * @throws ScrmException 参数非法
     */
    private void validateExpenseDto(ScrmBudgetExpenseDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("支出参数不能为空");
        }
        if (!partial && dto.getExpenseType() == null) {
            throw ScrmException.badRequest("支出类型不能为空");
        }
        if (!partial && dto.getExpenseDate() == null) {
            throw ScrmException.badRequest("支出日期不能为空");
        }
        if (!partial && dto.getAmount() == null) {
            throw ScrmException.badRequest("支出金额不能为空");
        }
        if (dto.getAmount() != null && dto.getAmount() < 0) {
            throw ScrmException.badRequest("支出金额不能为负");
        }
        if (dto.getAttachments() != null && !dto.getAttachments().isBlank()) {
            try {
                objectMapper.readTree(dto.getAttachments());
            } catch (Exception e) {
                throw ScrmException.badRequest("附件 attachments JSON 解析失败: " + e.getMessage());
            }
        }
    }

    /**
     * 按主键查询支出, 不存在或越权抛异常。
     */
    private ScrmBudgetExpenseEntity findExpenseOrThrow(Long id) throws ScrmException {
        ScrmBudgetExpenseEntity entity = expenseRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "预算支出不存在: id=" + id));
        return entity;
    }

    /**
     * 支出实体转 DTO
     */
    private ScrmBudgetExpenseDto toExpenseDto(ScrmBudgetExpenseEntity entity) {
        ScrmBudgetExpenseDto dto = new ScrmBudgetExpenseDto();
        dto.setId(entity.getId());
        dto.setExpenseNo(entity.getExpenseNo());
        dto.setPlanId(entity.getPlanId());
        dto.setPlanName(entity.getPlanName());
        dto.setAllocationId(entity.getAllocationId());
        dto.setAllocationName(entity.getAllocationName());
        dto.setExpenseType(entity.getExpenseType());
        dto.setCampaignId(entity.getCampaignId());
        dto.setCampaignName(entity.getCampaignName());
        dto.setExpenseDate(entity.getExpenseDate());
        dto.setAmount(entity.getAmount());
        dto.setCurrency(entity.getCurrency());
        dto.setDescription(entity.getDescription());
        dto.setVendor(entity.getVendor());
        dto.setInvoiceNo(entity.getInvoiceNo());
        dto.setPaymentMethod(entity.getPaymentMethod());
        dto.setPaymentStatus(entity.getPaymentStatus());
        dto.setPaidAt(entity.getPaidAt());
        dto.setReceiptUrl(entity.getReceiptUrl());
        dto.setAttachments(entity.getAttachments());
        dto.setStatus(entity.getStatus());
        dto.setApprovedBy(entity.getApprovedBy());
        dto.setApprovedAt(entity.getApprovedAt());
        dto.setApproverComment(entity.getApproverComment());
        dto.setDepartmentId(entity.getDepartmentId());
        dto.setDepartmentName(entity.getDepartmentName());
        dto.setRequesterId(entity.getRequesterId());
        dto.setRequesterName(entity.getRequesterName());
        dto.setTags(entity.getTags());
        dto.setNotes(entity.getNotes());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 金额保留两位小数。
     *
     * @param value 金额
     * @return 保留两位小数后的金额
     */
    private double round2(double value) {
        return Math.round(value * MONEY_SCALE) / MONEY_SCALE;
    }

}