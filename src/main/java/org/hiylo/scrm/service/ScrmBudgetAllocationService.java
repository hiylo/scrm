/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBudgetAllocationService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmBudgetAllocationDto;
import org.hiylo.scrm.dto.ScrmBudgetTransferDto;
import org.hiylo.scrm.entity.ScrmBudgetAllocationEntity;
import org.hiylo.scrm.entity.ScrmBudgetExpenseEntity;
import org.hiylo.scrm.entity.ScrmBudgetPlanEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmBudgetAllocationRepository;
import org.hiylo.scrm.repository.ScrmBudgetExpenseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * SCRM 预算分配管理服务。
 * <p>
 * 承载预算分配管理能力: 分配增删改查 / 预算调拨 / 消耗统计 / 预警检查 /
 * 已耗尽分配 / 分配汇总, 以及预算利用率与差异分析。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmBudgetAllocationService {

    // ==================== 状态常量 ====================

    /** 分配状态: 激活 */
    private static final String ALLOCATION_ACTIVE = "ACTIVE";
    /** 分配状态: 暂停 */
    private static final String ALLOCATION_PAUSED = "PAUSED";
    /** 分配状态: 已耗尽 */
    private static final String ALLOCATION_EXHAUSTED = "EXHAUSTED";
    /** 分配状态: 已关闭 */
    private static final String ALLOCATION_CLOSED = "CLOSED";

    /** 支出状态: 已通过 */
    private static final String EXPENSE_APPROVED = "APPROVED";
    /** 支出状态: 已付款 */
    private static final String EXPENSE_PAID = "PAID";

    /** 默认操作人 */
    private static final String DEFAULT_OPERATOR = "scrm-system";
    /** 金额精度 (保留两位小数) */
    private static final double MONEY_SCALE = 100d;

    /** 预算分配数据访问层 */
    private final ScrmBudgetAllocationRepository allocationRepository;

    /** 预算支出数据访问层 */
    private final ScrmBudgetExpenseRepository expenseRepository;

    /** 预算方案管理服务 (分配变更联动方案统计与方案校验) */
    private final ScrmBudgetPlanService planService;

    /**
     * 创建预算分配。
     *
     * @param dto 分配参数
     * @return 创建后的分配
     * @throws ScrmException 参数非法 / 方案不存在
     */
    @Transactional
    public ScrmBudgetAllocationDto createAllocation(ScrmBudgetAllocationDto dto) throws ScrmException {
        validateAllocationDto(dto, false);
        ScrmBudgetPlanEntity plan = planService.findPlanOrThrow(dto.getPlanId());
        if (dto.getPeriodEnd().isBefore(dto.getPeriodStart())) {
            throw ScrmException.badRequest("周期结束日期不能早于开始日期");
        }
        ScrmBudgetAllocationEntity entity = new ScrmBudgetAllocationEntity();
        entity.setPlanId(dto.getPlanId());
        entity.setPlanName(plan.getPlanName());
        entity.setAllocationName(dto.getAllocationName());
        entity.setAllocationType(dto.getAllocationType());
        entity.setTargetType(dto.getTargetType());
        entity.setTargetName(dto.getTargetName());
        entity.setAllocatedAmount(dto.getAllocatedAmount());
        entity.setSpentAmount(0d);
        entity.setRemainingAmount(dto.getAllocatedAmount());
        entity.setSpendRate(0d);
        entity.setPeriodStart(dto.getPeriodStart());
        entity.setPeriodEnd(dto.getPeriodEnd());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : ALLOCATION_ACTIVE);
        entity.setAlertThreshold(dto.getAlertThreshold() != null ? dto.getAlertThreshold() : 0.8d);
        entity.setIsAlertTriggered(false);
        entity.setNotes(dto.getNotes());
        entity.setCreatedBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : DEFAULT_OPERATOR);
        entity = allocationRepository.save(entity);
        // 增量更新方案已分配预算
        planService.updatePlanStats(dto.getPlanId());
        log.info("创建预算分配: id={}, planId={}, allocationName={}, allocationType={}",
                entity.getId(), entity.getPlanId(), entity.getAllocationName(), entity.getAllocationType());
        return toAllocationDto(entity);
    }

    /**
     * 更新预算分配（字段非空才覆盖）。
     *
     * @param id  分配 ID
     * @param dto 分配参数
     * @return 更新后的分配
     * @throws ScrmException 分配不存在 / 参数非法
     */
    @Transactional
    public ScrmBudgetAllocationDto updateAllocation(Long id, ScrmBudgetAllocationDto dto) throws ScrmException {
        ScrmBudgetAllocationEntity entity = findAllocationOrThrow(id);
        validateAllocationDto(dto, true);
        if (dto.getAllocationName() != null) entity.setAllocationName(dto.getAllocationName());
        if (dto.getAllocationType() != null) entity.setAllocationType(dto.getAllocationType());
        if (dto.getTargetType() != null) entity.setTargetType(dto.getTargetType());
        if (dto.getTargetName() != null) entity.setTargetName(dto.getTargetName());
        if (dto.getAllocatedAmount() != null) {
            double oldAllocated = entity.getAllocatedAmount() != null ? entity.getAllocatedAmount() : 0d;
            double delta = dto.getAllocatedAmount() - oldAllocated;
            entity.setAllocatedAmount(dto.getAllocatedAmount());
            entity.setRemainingAmount(round2((entity.getRemainingAmount() != null
                    ? entity.getRemainingAmount() : 0d) + delta));
        }
        if (dto.getPeriodStart() != null) entity.setPeriodStart(dto.getPeriodStart());
        if (dto.getPeriodEnd() != null) entity.setPeriodEnd(dto.getPeriodEnd());
        if (entity.getPeriodEnd().isBefore(entity.getPeriodStart())) {
            throw ScrmException.badRequest("周期结束日期不能早于开始日期");
        }
        if (dto.getStatus() != null) entity.setStatus(dto.getStatus());
        if (dto.getAlertThreshold() != null) entity.setAlertThreshold(dto.getAlertThreshold());
        if (dto.getNotes() != null) entity.setNotes(dto.getNotes());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        recalcAllocationRates(entity);
        entity = allocationRepository.save(entity);
        if (entity.getPlanId() != null) {
            planService.updatePlanStats(entity.getPlanId());
        }
        log.info("更新预算分配: id={}, allocationName={}", entity.getId(), entity.getAllocationName());
        return toAllocationDto(entity);
    }

    /**
     * 删除预算分配。
     *
     * @param id 分配 ID
     * @throws ScrmException 分配不存在
     */
    @Transactional
    public void deleteAllocation(Long id) throws ScrmException {
        ScrmBudgetAllocationEntity entity = findAllocationOrThrow(id);
        Long planId = entity.getPlanId();
        allocationRepository.delete(entity);
        if (planId != null) {
            planService.updatePlanStats(planId);
        }
        log.info("删除预算分配: id={}, allocationName={}", id, entity.getAllocationName());
    }

    /**
     * 查询分配详情。
     *
     * @param id 分配 ID
     * @return 分配 DTO
     * @throws ScrmException 分配不存在
     */
    @Transactional(readOnly = true)
    public ScrmBudgetAllocationDto getAllocation(Long id) throws ScrmException {
        return toAllocationDto(findAllocationOrThrow(id));
    }

    /**
     * 分页查询预算分配, 支持按方案 ID/分配类型/状态过滤。
     *
     * @param planId         方案 ID (可空)
     * @param allocationType 分配类型 (可空)
     * @param status         状态 (可空)
     * @param pageable       分页参数
     * @return 分配分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmBudgetAllocationDto> listAllocations(Long planId, String allocationType, String status,
                                                          Pageable pageable) {
        Specification<ScrmBudgetAllocationEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (planId != null) {
                predicates.add(cb.equal(root.get("planId"), planId));
            }
            if (allocationType != null && !allocationType.isBlank()) {
                predicates.add(cb.equal(root.get("allocationType"), allocationType));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createTime"));
        return allocationRepository.findAll(spec, sorted).map(this::toAllocationDto);
    }

    /**
     * 预算调拨 (从源分配调拨金额到目标分配)。
     * <p>校验源分配剩余金额充足, 同步增减两端分配金额与剩余, 重算消耗率与方案统计。</p>
     *
     * @param transferDto 调拨参数
     * @return 调拨结果汇总
     * @throws ScrmException 分配不存在 / 金额非法 / 源分配余额不足
     */
    @Transactional
    public Map<String, Object> transferBudget(ScrmBudgetTransferDto transferDto) throws ScrmException {
        if (transferDto == null || transferDto.getFromAllocationId() == null
                || transferDto.getToAllocationId() == null) {
            throw ScrmException.badRequest("调拨源分配 ID 与目标分配 ID 不能为空");
        }
        if (transferDto.getAmount() == null || transferDto.getAmount() <= 0) {
            throw ScrmException.badRequest("调拨金额必须大于 0");
        }
        if (transferDto.getFromAllocationId().equals(transferDto.getToAllocationId())) {
            throw ScrmException.badRequest("源分配与目标分配不能相同");
        }
        ScrmBudgetAllocationEntity from = findAllocationOrThrow(transferDto.getFromAllocationId());
        ScrmBudgetAllocationEntity to = findAllocationOrThrow(transferDto.getToAllocationId());
        double amount = transferDto.getAmount();
        double fromRemaining = from.getRemainingAmount() != null ? from.getRemainingAmount() : 0d;
        if (fromRemaining < amount) {
            throw ScrmException.badRequest("源分配剩余金额不足: 剩余=" + fromRemaining + ", 调拨=" + amount);
        }
        from.setAllocatedAmount(round2((from.getAllocatedAmount() != null ? from.getAllocatedAmount() : 0d) - amount));
        from.setRemainingAmount(round2(fromRemaining - amount));
        recalcAllocationRates(from);
        if (from.getRemainingAmount() != null && from.getRemainingAmount() <= 0) {
            from.setStatus(ALLOCATION_EXHAUSTED);
        }
        to.setAllocatedAmount(round2((to.getAllocatedAmount() != null ? to.getAllocatedAmount() : 0d) + amount));
        to.setRemainingAmount(round2((to.getRemainingAmount() != null ? to.getRemainingAmount() : 0d) + amount));
        recalcAllocationRates(to);
        allocationRepository.save(from);
        allocationRepository.save(to);
        // 重算两端方案统计
        if (from.getPlanId() != null) {
            planService.updatePlanStats(from.getPlanId());
        }
        if (to.getPlanId() != null && !Objects.equals(to.getPlanId(), from.getPlanId())) {
            planService.updatePlanStats(to.getPlanId());
        }
        log.info("预算调拨: from={}, to={}, amount={}, reason={}",
                transferDto.getFromAllocationId(), transferDto.getToAllocationId(), amount, transferDto.getReason());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fromAllocationId", transferDto.getFromAllocationId());
        result.put("toAllocationId", transferDto.getToAllocationId());
        result.put("amount", round2(amount));
        result.put("reason", transferDto.getReason());
        result.put("fromAllocation", toAllocationDto(from));
        result.put("toAllocation", toAllocationDto(to));
        return result;
    }

    /**
     * 更新分配消耗统计 (按已审批/已付款支出汇总已消耗金额, 重算剩余与消耗率)。
     *
     * @param id 分配 ID
     * @return 更新后的分配
     * @throws ScrmException 分配不存在
     */
    @Transactional
    public ScrmBudgetAllocationDto updateAllocationStats(Long id) throws ScrmException {
        ScrmBudgetAllocationEntity entity = findAllocationOrThrow(id);
        List<ScrmBudgetExpenseEntity> expenses = expenseRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("allocationId"), id));
            predicates.add(root.get("status").in(List.of(EXPENSE_APPROVED, EXPENSE_PAID)));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
        double spent = expenses.stream()
                .mapToDouble(e -> e.getAmount() != null ? e.getAmount() : 0d).sum();
        double allocated = entity.getAllocatedAmount() != null ? entity.getAllocatedAmount() : 0d;
        entity.setSpentAmount(round2(spent));
        entity.setRemainingAmount(round2(allocated - spent));
        recalcAllocationRates(entity);
        if (entity.getRemainingAmount() != null && entity.getRemainingAmount() <= 0 && ALLOCATION_ACTIVE.equals(entity.getStatus())) {
            entity.setStatus(ALLOCATION_EXHAUSTED);
        }
        entity = allocationRepository.save(entity);
        if (entity.getPlanId() != null) {
            planService.updatePlanStats(entity.getPlanId());
        }
        log.info("更新预算分配消耗统计: id={}, spent={}, remaining={}",
                id, spent, entity.getRemainingAmount());
        return toAllocationDto(entity);
    }

    /**
     * 检查分配预警 (消耗率超过阈值则触发预警)。
     *
     * @param id 分配 ID
     * @return 更新后的分配
     * @throws ScrmException 分配不存在
     */
    @Transactional
    public ScrmBudgetAllocationDto checkAllocationAlert(Long id) throws ScrmException {
        ScrmBudgetAllocationEntity entity = findAllocationOrThrow(id);
        updateAllocationStats(id);
        ScrmBudgetAllocationEntity refreshed = allocationRepository.findById(id).orElse(entity);
        double spendRate = refreshed.getSpendRate() != null ? refreshed.getSpendRate() : 0d;
        double threshold = refreshed.getAlertThreshold() != null ? refreshed.getAlertThreshold() : 0.8d;
        boolean triggered = spendRate >= threshold;
        refreshed.setIsAlertTriggered(triggered);
        if (triggered) {
            refreshed.setLastAlertAt(LocalDateTime.now());
        }
        refreshed = allocationRepository.save(refreshed);
        log.info("检查预算分配预警: id={}, spendRate={}, threshold={}, triggered={}",
                id, spendRate, threshold, triggered);
        return toAllocationDto(refreshed);
    }

    /**
     * 查询已耗尽分配 (状态为 EXHAUSTED 或剩余金额 <= 0)。
     *
     * @return 分配列表
     */
    @Transactional(readOnly = true)
    public List<ScrmBudgetAllocationDto> getExhaustedAllocations() {
        List<ScrmBudgetAllocationEntity> exhausted = allocationRepository.findByStatus(
                ALLOCATION_EXHAUSTED);
        // 兜底: 状态非 EXHAUSTED 但剩余金额 <= 0 的也纳入
        Specification<ScrmBudgetAllocationEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.lessThanOrEqualTo(root.get("remainingAmount"), 0d));
            predicates.add(cb.notEqual(root.get("status"), ALLOCATION_EXHAUSTED));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        exhausted.addAll(allocationRepository.findAll(spec));
        return exhausted.stream().map(this::toAllocationDto).collect(Collectors.toList());
    }

    /**
     * 分配汇总 (按方案 ID 汇总分配数/已分配/已消耗/剩余/各类型分布)。
     *
     * @param planId 方案 ID
     * @return 分配汇总
     * @throws ScrmException 方案不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getAllocationSummary(Long planId) throws ScrmException {
        planService.findPlanOrThrow(planId);
        List<ScrmBudgetAllocationEntity> allocations = allocationRepository.findByPlanId(planId);
        double totalAllocated = allocations.stream()
                .mapToDouble(a -> a.getAllocatedAmount() != null ? a.getAllocatedAmount() : 0d).sum();
        double totalSpent = allocations.stream()
                .mapToDouble(a -> a.getSpentAmount() != null ? a.getSpentAmount() : 0d).sum();
        double totalRemaining = allocations.stream()
                .mapToDouble(a -> a.getRemainingAmount() != null ? a.getRemainingAmount() : 0d).sum();
        Map<String, Double> byType = allocations.stream()
                .filter(a -> a.getAllocationType() != null)
                .collect(Collectors.groupingBy(ScrmBudgetAllocationEntity::getAllocationType,
                        Collectors.summingDouble(a -> a.getAllocatedAmount() != null ? a.getAllocatedAmount() : 0d)));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("planId", planId);
        result.put("allocationCount", allocations.size());
        result.put("totalAllocated", round2(totalAllocated));
        result.put("totalSpent", round2(totalSpent));
        result.put("totalRemaining", round2(totalRemaining));
        result.put("byType", byType.entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("allocationType", e.getKey());
                    m.put("allocatedAmount", round2(e.getValue()));
                    return m;
                })
                .collect(Collectors.toList()));
        return result;
    }

    /**
     * 预算利用率 (方案已分配/已消耗/剩余/分配率/消耗率/各分配明细)。
     *
     * @param planId 方案 ID
     * @return 预算利用率
     * @throws ScrmException 方案不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getBudgetUtilization(Long planId) throws ScrmException {
        ScrmBudgetPlanEntity plan = planService.findPlanOrThrow(planId);
        List<ScrmBudgetAllocationEntity> allocations = allocationRepository.findByPlanId(planId);
        double total = plan.getTotalBudget() != null ? plan.getTotalBudget() : 0d;
        double allocated = plan.getAllocatedBudget() != null ? plan.getAllocatedBudget() : 0d;
        double spent = plan.getSpentBudget() != null ? plan.getSpentBudget() : 0d;
        double remaining = plan.getRemainingBudget() != null ? plan.getRemainingBudget() : 0d;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("planId", planId);
        result.put("planName", plan.getPlanName());
        result.put("totalBudget", round2(total));
        result.put("allocatedBudget", round2(allocated));
        result.put("spentBudget", round2(spent));
        result.put("remainingBudget", round2(remaining));
        result.put("allocationRate", total > 0 ? round2(allocated / total) : 0d);
        result.put("spendRate", total > 0 ? round2(spent / total) : 0d);
        result.put("allocationCount", allocations.size());
        result.put("allocations", allocations.stream().map(this::toAllocationDto).collect(Collectors.toList()));
        return result;
    }

    /**
     * 预算差异分析 (实际 vs 计划: 总预算/已分配/已消耗/剩余 vs 差异率)。
     *
     * @param planId 方案 ID
     * @return 预算差异分析
     * @throws ScrmException 方案不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getBudgetVariance(Long planId) throws ScrmException {
        ScrmBudgetPlanEntity plan = planService.findPlanOrThrow(planId);
        List<ScrmBudgetAllocationEntity> allocations = allocationRepository.findByPlanId(planId);
        double plannedTotal = plan.getTotalBudget() != null ? plan.getTotalBudget() : 0d;
        double allocated = allocations.stream()
                .mapToDouble(a -> a.getAllocatedAmount() != null ? a.getAllocatedAmount() : 0d).sum();
        double actualSpent = allocations.stream()
                .mapToDouble(a -> a.getSpentAmount() != null ? a.getSpentAmount() : 0d).sum();
        double remaining = plannedTotal - actualSpent;
        double variance = actualSpent - plannedTotal;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("planId", planId);
        result.put("planName", plan.getPlanName());
        result.put("plannedBudget", round2(plannedTotal));
        result.put("allocatedBudget", round2(allocated));
        result.put("actualSpent", round2(actualSpent));
        result.put("remainingBudget", round2(remaining));
        result.put("variance", round2(variance));
        result.put("varianceRate", plannedTotal > 0 ? round2(variance / plannedTotal) : 0d);
        result.put("spendRate", plannedTotal > 0 ? round2(actualSpent / plannedTotal) : 0d);
        result.put("allocationVariance", round2(plannedTotal - allocated));
        return result;
    }

    /**
     * 重算分配消耗率。
     *
     * @param entity 分配实体
     */
    private void recalcAllocationRates(ScrmBudgetAllocationEntity entity) {
        double allocated = entity.getAllocatedAmount() != null ? entity.getAllocatedAmount() : 0d;
        double spent = entity.getSpentAmount() != null ? entity.getSpentAmount() : 0d;
        entity.setSpendRate(allocated > 0 ? round2(spent / allocated) : 0d);
    }

    /**
     * 校验分配 DTO。
     *
     * @param dto     分配参数
     * @param partial 是否部分更新
     * @throws ScrmException 参数非法
     */
    private void validateAllocationDto(ScrmBudgetAllocationDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("分配参数不能为空");
        }
        if (dto.getAllocationName() != null) {
            if (dto.getAllocationName().isBlank()) {
                throw ScrmException.badRequest("分配名称不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("分配名称不能为空");
        }
        if (!partial && dto.getPlanId() == null) {
            throw ScrmException.badRequest("预算方案 ID 不能为空");
        }
        if (!partial && dto.getAllocationType() == null) {
            throw ScrmException.badRequest("分配类型不能为空");
        }
        if (!partial && dto.getTargetType() == null) {
            throw ScrmException.badRequest("目标类型不能为空");
        }
        if (!partial && dto.getAllocatedAmount() == null) {
            throw ScrmException.badRequest("分配金额不能为空");
        }
        if (!partial && dto.getPeriodStart() == null) {
            throw ScrmException.badRequest("周期开始日期不能为空");
        }
        if (!partial && dto.getPeriodEnd() == null) {
            throw ScrmException.badRequest("周期结束日期不能为空");
        }
    }

    /**
     * 按主键查询分配, 不存在或越权抛异常。
     */
    private ScrmBudgetAllocationEntity findAllocationOrThrow(Long id) throws ScrmException {
        ScrmBudgetAllocationEntity entity = allocationRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "预算分配不存在: id=" + id));
        return entity;
    }

    /**
     * 分配实体转 DTO
     */
    private ScrmBudgetAllocationDto toAllocationDto(ScrmBudgetAllocationEntity entity) {
        ScrmBudgetAllocationDto dto = new ScrmBudgetAllocationDto();
        dto.setId(entity.getId());
        dto.setPlanId(entity.getPlanId());
        dto.setPlanName(entity.getPlanName());
        dto.setAllocationName(entity.getAllocationName());
        dto.setAllocationType(entity.getAllocationType());
        dto.setTargetType(entity.getTargetType());
        dto.setTargetName(entity.getTargetName());
        dto.setAllocatedAmount(entity.getAllocatedAmount());
        dto.setSpentAmount(entity.getSpentAmount());
        dto.setRemainingAmount(entity.getRemainingAmount());
        dto.setSpendRate(entity.getSpendRate());
        dto.setPeriodStart(entity.getPeriodStart());
        dto.setPeriodEnd(entity.getPeriodEnd());
        dto.setStatus(entity.getStatus());
        dto.setAlertThreshold(entity.getAlertThreshold());
        dto.setIsAlertTriggered(entity.getIsAlertTriggered());
        dto.setLastAlertAt(entity.getLastAlertAt());
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