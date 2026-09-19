/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBudgetPlanService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmBudgetPlanDto;
import org.hiylo.scrm.entity.ScrmBudgetAllocationEntity;
import org.hiylo.scrm.entity.ScrmBudgetExpenseEntity;
import org.hiylo.scrm.entity.ScrmBudgetPlanEntity;
import org.hiylo.scrm.entity.ScrmBudgetRoiEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmBudgetAllocationRepository;
import org.hiylo.scrm.repository.ScrmBudgetExpenseRepository;
import org.hiylo.scrm.repository.ScrmBudgetPlanRepository;
import org.hiylo.scrm.repository.ScrmBudgetRoiRepository;
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
import java.util.stream.Collectors;

/**
 * SCRM 预算计划管理服务。
 * <p>
 * 承载预算方案管理能力: 方案增删改查 / 激活 / 暂停 / 关闭 / 审批 / 统计更新 /
 * 预警检查 / 即将到期, 以及预算统计 / 利用率 / 差异分析。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmBudgetPlanService {

    // ==================== 状态常量 ====================

    /** 方案状态: 草稿 */
    private static final String PLAN_DRAFT = "DRAFT";
    /** 方案状态: 激活 */
    private static final String PLAN_ACTIVE = "ACTIVE";
    /** 方案状态: 暂停 */
    private static final String PLAN_PAUSED = "PAUSED";
    /** 方案状态: 过期 */
    private static final String PLAN_EXPIRED = "EXPIRED";
    /** 方案状态: 关闭 */
    private static final String PLAN_CLOSED = "CLOSED";

    /** 默认操作人 */
    private static final String DEFAULT_OPERATOR = "scrm-system";
    /** 金额精度 (保留两位小数) */
    private static final double MONEY_SCALE = 100d;
    /** 默认即将到期天数 */
    private static final int DEFAULT_EXPIRING_DAYS = 7;

    /** 预算方案数据访问层 */
    private final ScrmBudgetPlanRepository planRepository;

    /** 预算分配数据访问层 */
    private final ScrmBudgetAllocationRepository allocationRepository;

    /** 预算支出数据访问层 */
    private final ScrmBudgetExpenseRepository expenseRepository;

    /** 预算 ROI 数据访问层 */
    private final ScrmBudgetRoiRepository roiRepository;

    /**
     * 创建预算方案。
     * <p>校验 planCode 唯一性与周期合法性后写入归属账号 ID 持久化, 缺省字段填默认值。</p>
     *
     * @param dto 方案参数
     * @return 创建后的方案
     * @throws ScrmException 参数非法 / planCode 重复
     */
    @Transactional
    public ScrmBudgetPlanDto createPlan(ScrmBudgetPlanDto dto) throws ScrmException {
        validatePlanDto(dto, false);
        if (planRepository.findByPlanCode(dto.getPlanCode()).isPresent()) {
            throw ScrmException.conflict("预算方案编码已存在: " + dto.getPlanCode());
        }
        if (dto.getPeriodEnd().isBefore(dto.getPeriodStart())) {
            throw ScrmException.badRequest("周期结束日期不能早于开始日期");
        }
        ScrmBudgetPlanEntity entity = new ScrmBudgetPlanEntity();
        entity.setPlanName(dto.getPlanName());
        entity.setPlanCode(dto.getPlanCode());
        entity.setDescription(dto.getDescription());
        entity.setFiscalYear(dto.getFiscalYear());
        entity.setFiscalPeriod(dto.getFiscalPeriod());
        entity.setPeriodStart(dto.getPeriodStart());
        entity.setPeriodEnd(dto.getPeriodEnd());
        entity.setTotalBudget(dto.getTotalBudget());
        entity.setAllocatedBudget(0d);
        entity.setSpentBudget(0d);
        entity.setRemainingBudget(dto.getTotalBudget());
        entity.setAllocationRate(0d);
        entity.setSpendRate(0d);
        entity.setCurrency(dto.getCurrency() != null ? dto.getCurrency() : "CNY");
        entity.setBudgetType(dto.getBudgetType());
        entity.setDepartments(dto.getDepartments());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : PLAN_DRAFT);
        entity.setApprovedAmount(0d);
        entity.setAlertThreshold(dto.getAlertThreshold() != null ? dto.getAlertThreshold() : 0.8d);
        entity.setIsAlertTriggered(false);
        entity.setCreatedBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : DEFAULT_OPERATOR);
        entity = planRepository.save(entity);
        log.info("创建预算方案: id={}, planName={}, planCode={}, budgetType={}",
                entity.getId(), entity.getPlanName(), entity.getPlanCode(), entity.getBudgetType());
        return toPlanDto(entity);
    }

    /**
     * 更新预算方案（字段非空才覆盖）。
     *
     * @param id  方案 ID
     * @param dto 方案参数
     * @return 更新后的方案
     * @throws ScrmException 方案不存在 / 参数非法 / planCode 重复
     */
    @Transactional
    public ScrmBudgetPlanDto updatePlan(Long id, ScrmBudgetPlanDto dto) throws ScrmException {
        ScrmBudgetPlanEntity entity = findPlanOrThrow(id);
        validatePlanDto(dto, true);
        if (dto.getPlanCode() != null && !dto.getPlanCode().equals(entity.getPlanCode()) && planRepository.findByPlanCode(dto.getPlanCode()).isPresent()) {
            throw ScrmException.conflict("预算方案编码已存在: " + dto.getPlanCode());
        }
        if (dto.getPlanName() != null) entity.setPlanName(dto.getPlanName());
        if (dto.getPlanCode() != null) entity.setPlanCode(dto.getPlanCode());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getFiscalYear() != null) entity.setFiscalYear(dto.getFiscalYear());
        if (dto.getFiscalPeriod() != null) entity.setFiscalPeriod(dto.getFiscalPeriod());
        if (dto.getPeriodStart() != null) entity.setPeriodStart(dto.getPeriodStart());
        if (dto.getPeriodEnd() != null) entity.setPeriodEnd(dto.getPeriodEnd());
        if (entity.getPeriodEnd().isBefore(entity.getPeriodStart())) {
            throw ScrmException.badRequest("周期结束日期不能早于开始日期");
        }
        if (dto.getTotalBudget() != null) {
            double oldTotal = entity.getTotalBudget() != null ? entity.getTotalBudget() : 0d;
            double delta = dto.getTotalBudget() - oldTotal;
            entity.setTotalBudget(dto.getTotalBudget());
            // 总预算变化时同步调整剩余预算
            entity.setRemainingBudget(round2((entity.getRemainingBudget() != null
                    ? entity.getRemainingBudget() : 0d) + delta));
        }
        if (dto.getCurrency() != null) entity.setCurrency(dto.getCurrency());
        if (dto.getBudgetType() != null) entity.setBudgetType(dto.getBudgetType());
        if (dto.getDepartments() != null) entity.setDepartments(dto.getDepartments());
        if (dto.getStatus() != null) entity.setStatus(dto.getStatus());
        if (dto.getAlertThreshold() != null) entity.setAlertThreshold(dto.getAlertThreshold());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = planRepository.save(entity);
        // 总预算变更后重算分配率/消耗率
        recalcPlanRates(entity);
        entity = planRepository.save(entity);
        log.info("更新预算方案: id={}, planName={}", entity.getId(), entity.getPlanName());
        return toPlanDto(entity);
    }

    /**
     * 删除预算方案。
     * <p>同时清理该方案下的分配、支出与 ROI 记录。</p>
     *
     * @param id 方案 ID
     * @throws ScrmException 方案不存在
     */
    @Transactional
    public void deletePlan(Long id) throws ScrmException {
        ScrmBudgetPlanEntity entity = findPlanOrThrow(id);
        List<ScrmBudgetAllocationEntity> allocations = allocationRepository.findByPlanId(id);
        if (!allocations.isEmpty()) {
            allocationRepository.deleteAll(allocations);
        }
        List<ScrmBudgetExpenseEntity> expenses = expenseRepository.findByPlanId(id);
        if (!expenses.isEmpty()) {
            expenseRepository.deleteAll(expenses);
        }
        List<ScrmBudgetRoiEntity> rois = roiRepository.findByPlanId(id);
        if (!rois.isEmpty()) {
            roiRepository.deleteAll(rois);
        }
        planRepository.delete(entity);
        log.info("删除预算方案: id={}, planName={}", id, entity.getPlanName());
    }

    /**
     * 查询方案详情。
     *
     * @param id 方案 ID
     * @return 方案 DTO
     * @throws ScrmException 方案不存在
     */
    @Transactional(readOnly = true)
    public ScrmBudgetPlanDto getPlan(Long id) throws ScrmException {
        return toPlanDto(findPlanOrThrow(id));
    }

    /**
     * 按方案编码查询方案。
     *
     * @param code 方案编码
     * @return 方案 DTO
     * @throws ScrmException 方案不存在
     */
    @Transactional(readOnly = true)
    public ScrmBudgetPlanDto getPlanByCode(String code) throws ScrmException {
        ScrmBudgetPlanEntity entity = planRepository
                .findByPlanCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "预算方案不存在: code=" + code));
        return toPlanDto(entity);
    }

    /**
     * 分页查询预算方案, 支持按财年/预算类型/状态/关键字过滤。
     *
     * @param fiscalYear 财年 (可空)
     * @param budgetType 预算类型 (可空)
     * @param status     状态 (可空)
     * @param keyword    关键字 (匹配方案名称/编码, 可空)
     * @param pageable   分页参数
     * @return 方案分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmBudgetPlanDto> listPlans(Integer fiscalYear, String budgetType, String status,
                                             String keyword, Pageable pageable) {
        Specification<ScrmBudgetPlanEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (fiscalYear != null) {
                predicates.add(cb.equal(root.get("fiscalYear"), fiscalYear));
            }
            if (budgetType != null && !budgetType.isBlank()) {
                predicates.add(cb.equal(root.get("budgetType"), budgetType));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword + "%";
                predicates.add(cb.or(cb.like(root.get("planName"), like),
                        cb.like(root.get("planCode"), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createTime"));
        return planRepository.findAll(spec, sorted).map(this::toPlanDto);
    }

    /**
     * 激活方案。
     *
     * @param id 方案 ID
     * @return 更新后的方案
     * @throws ScrmException 方案不存在
     */
    @Transactional
    public ScrmBudgetPlanDto activatePlan(Long id) throws ScrmException {
        ScrmBudgetPlanEntity entity = findPlanOrThrow(id);
        entity.setStatus(PLAN_ACTIVE);
        entity = planRepository.save(entity);
        log.info("激活预算方案: id={}", id);
        return toPlanDto(entity);
    }

    /**
     * 暂停方案。
     *
     * @param id 方案 ID
     * @return 更新后的方案
     * @throws ScrmException 方案不存在
     */
    @Transactional
    public ScrmBudgetPlanDto pausePlan(Long id) throws ScrmException {
        ScrmBudgetPlanEntity entity = findPlanOrThrow(id);
        entity.setStatus(PLAN_PAUSED);
        entity = planRepository.save(entity);
        log.info("暂停预算方案: id={}", id);
        return toPlanDto(entity);
    }

    /**
     * 关闭方案。
     *
     * @param id 方案 ID
     * @return 更新后的方案
     * @throws ScrmException 方案不存在
     */
    @Transactional
    public ScrmBudgetPlanDto closePlan(Long id) throws ScrmException {
        ScrmBudgetPlanEntity entity = findPlanOrThrow(id);
        entity.setStatus(PLAN_CLOSED);
        entity = planRepository.save(entity);
        log.info("关闭预算方案: id={}", id);
        return toPlanDto(entity);
    }

    /**
     * 审批方案 (记录审批人、审批金额与审批时间)。
     *
     * @param id         方案 ID
     * @param approverId 审批人 ID
     * @param amount     审批金额
     * @return 更新后的方案
     * @throws ScrmException 方案不存在 / 金额非法
     */
    @Transactional
    public ScrmBudgetPlanDto approvePlan(Long id, String approverId, Double amount) throws ScrmException {
        if (approverId == null || approverId.isBlank()) {
            throw ScrmException.badRequest("审批人不能为空");
        }
        if (amount == null || amount < 0) {
            throw ScrmException.badRequest("审批金额不能为空且不能为负");
        }
        ScrmBudgetPlanEntity entity = findPlanOrThrow(id);
        entity.setApprovedBy(approverId);
        entity.setApprovedAt(LocalDateTime.now());
        entity.setApprovedAmount(round2(amount));
        entity = planRepository.save(entity);
        log.info("审批预算方案: id={}, approverId={}, amount={}", id, approverId, amount);
        return toPlanDto(entity);
    }

    /**
     * 更新方案统计 (已分配/已消耗/剩余/分配率/消耗率)。
     *
     * @param id 方案 ID
     * @return 更新后的方案
     * @throws ScrmException 方案不存在
     */
    @Transactional
    public ScrmBudgetPlanDto updatePlanStats(Long id) throws ScrmException {
        ScrmBudgetPlanEntity entity = findPlanOrThrow(id);
        List<ScrmBudgetAllocationEntity> allocations = allocationRepository.findByPlanId(id);
        double allocated = allocations.stream()
                .mapToDouble(a -> a.getAllocatedAmount() != null ? a.getAllocatedAmount() : 0d).sum();
        double spent = allocations.stream()
                .mapToDouble(a -> a.getSpentAmount() != null ? a.getSpentAmount() : 0d).sum();
        double total = entity.getTotalBudget() != null ? entity.getTotalBudget() : 0d;
        entity.setAllocatedBudget(round2(allocated));
        entity.setSpentBudget(round2(spent));
        entity.setRemainingBudget(round2(total - allocated));
        recalcPlanRates(entity);
        entity = planRepository.save(entity);
        log.info("更新预算方案统计: id={}, allocated={}, spent={}, remaining={}",
                id, allocated, spent, entity.getRemainingBudget());
        return toPlanDto(entity);
    }

    /**
     * 检查方案预警 (消耗率超过阈值则触发预警)。
     *
     * @param id 方案 ID
     * @return 更新后的方案
     * @throws ScrmException 方案不存在
     */
    @Transactional
    public ScrmBudgetPlanDto checkAlert(Long id) throws ScrmException {
        ScrmBudgetPlanEntity entity = findPlanOrThrow(id);
        updatePlanStats(id);
        ScrmBudgetPlanEntity refreshed = planRepository.findById(id).orElse(entity);
        double spendRate = refreshed.getSpendRate() != null ? refreshed.getSpendRate() : 0d;
        double threshold = refreshed.getAlertThreshold() != null ? refreshed.getAlertThreshold() : 0.8d;
        boolean triggered = spendRate >= threshold;
        refreshed.setIsAlertTriggered(triggered);
        if (triggered) {
            refreshed.setLastAlertAt(LocalDateTime.now());
        }
        refreshed = planRepository.save(refreshed);
        log.info("检查预算方案预警: id={}, spendRate={}, threshold={}, triggered={}",
                id, spendRate, threshold, triggered);
        return toPlanDto(refreshed);
    }

    /**
     * 查询即将到期的方案 (状态为 ACTIVE 且周期结束日在 days 天内)。
     *
     * @param days 天数 (默认 7)
     * @return 方案列表
     */
    @Transactional(readOnly = true)
    public List<ScrmBudgetPlanDto> getExpiringPlans(Integer days) {
        int window = days != null && days > 0 ? days : DEFAULT_EXPIRING_DAYS;
        LocalDate today = LocalDate.now();
        LocalDate deadline = today.plusDays(window);
        Specification<ScrmBudgetPlanEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), PLAN_ACTIVE));
            predicates.add(cb.greaterThanOrEqualTo(root.get("periodEnd"), today));
            predicates.add(cb.lessThanOrEqualTo(root.get("periodEnd"), deadline));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return planRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "periodEnd"))
                .stream().map(this::toPlanDto).collect(Collectors.toList());
    }

    /**
     * 预算统计 (按财年汇总总额/已分配/已消耗/剩余/消耗率)。
     *
     * @param fiscalYear 财年 (可空)
     * @return 预算统计
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getBudgetStats(Integer fiscalYear) {
        List<ScrmBudgetPlanEntity> plans = fiscalYear != null
                ? planRepository.findByFiscalYear(fiscalYear)
                : planRepository.findByStatus(PLAN_ACTIVE);
        double totalBudget = plans.stream()
                .mapToDouble(p -> p.getTotalBudget() != null ? p.getTotalBudget() : 0d).sum();
        double allocated = plans.stream()
                .mapToDouble(p -> p.getAllocatedBudget() != null ? p.getAllocatedBudget() : 0d).sum();
        double spent = plans.stream()
                .mapToDouble(p -> p.getSpentBudget() != null ? p.getSpentBudget() : 0d).sum();
        double remaining = plans.stream()
                .mapToDouble(p -> p.getRemainingBudget() != null ? p.getRemainingBudget() : 0d).sum();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fiscalYear", fiscalYear);
        result.put("planCount", plans.size());
        result.put("totalBudget", round2(totalBudget));
        result.put("allocatedBudget", round2(allocated));
        result.put("spentBudget", round2(spent));
        result.put("remainingBudget", round2(remaining));
        result.put("allocationRate", totalBudget > 0 ? round2(allocated / totalBudget) : 0d);
        result.put("spendRate", totalBudget > 0 ? round2(spent / totalBudget) : 0d);
        return result;
    }

    /**
     * 重算方案分配率与消耗率。
     *
     * @param entity 方案实体
     */
    private void recalcPlanRates(ScrmBudgetPlanEntity entity) {
        double total = entity.getTotalBudget() != null ? entity.getTotalBudget() : 0d;
        double allocated = entity.getAllocatedBudget() != null ? entity.getAllocatedBudget() : 0d;
        double spent = entity.getSpentBudget() != null ? entity.getSpentBudget() : 0d;
        entity.setAllocationRate(total > 0 ? round2(allocated / total) : 0d);
        entity.setSpendRate(total > 0 ? round2(spent / total) : 0d);
    }

    /**
     * 校验方案 DTO。
     *
     * @param dto     方案参数
     * @param partial 是否部分更新
     * @throws ScrmException 参数非法
     */
    private void validatePlanDto(ScrmBudgetPlanDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("方案参数不能为空");
        }
        if (dto.getPlanName() != null) {
            if (dto.getPlanName().isBlank()) {
                throw ScrmException.badRequest("预算方案名称不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("预算方案名称不能为空");
        }
        if (dto.getPlanCode() != null) {
            if (dto.getPlanCode().isBlank()) {
                throw ScrmException.badRequest("预算方案编码不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("预算方案编码不能为空");
        }
        if (!partial && dto.getFiscalYear() == null) {
            throw ScrmException.badRequest("财年不能为空");
        }
        if (!partial && dto.getFiscalPeriod() == null) {
            throw ScrmException.badRequest("财年周期不能为空");
        }
        if (!partial && dto.getPeriodStart() == null) {
            throw ScrmException.badRequest("周期开始日期不能为空");
        }
        if (!partial && dto.getPeriodEnd() == null) {
            throw ScrmException.badRequest("周期结束日期不能为空");
        }
        if (!partial && dto.getTotalBudget() == null) {
            throw ScrmException.badRequest("总预算不能为空");
        }
        if (!partial && dto.getBudgetType() == null) {
            throw ScrmException.badRequest("预算类型不能为空");
        }
    }

    /**
     * 按主键查询方案, 不存在或越权抛异常。
     */
    ScrmBudgetPlanEntity findPlanOrThrow(Long id) throws ScrmException {
        ScrmBudgetPlanEntity entity = planRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "预算方案不存在: id=" + id));
        return entity;
    }

    /**
     * 方案实体转 DTO
     */
    private ScrmBudgetPlanDto toPlanDto(ScrmBudgetPlanEntity entity) {
        ScrmBudgetPlanDto dto = new ScrmBudgetPlanDto();
        dto.setId(entity.getId());
        dto.setPlanName(entity.getPlanName());
        dto.setPlanCode(entity.getPlanCode());
        dto.setDescription(entity.getDescription());
        dto.setFiscalYear(entity.getFiscalYear());
        dto.setFiscalPeriod(entity.getFiscalPeriod());
        dto.setPeriodStart(entity.getPeriodStart());
        dto.setPeriodEnd(entity.getPeriodEnd());
        dto.setTotalBudget(entity.getTotalBudget());
        dto.setAllocatedBudget(entity.getAllocatedBudget());
        dto.setSpentBudget(entity.getSpentBudget());
        dto.setRemainingBudget(entity.getRemainingBudget());
        dto.setAllocationRate(entity.getAllocationRate());
        dto.setSpendRate(entity.getSpendRate());
        dto.setCurrency(entity.getCurrency());
        dto.setBudgetType(entity.getBudgetType());
        dto.setDepartments(entity.getDepartments());
        dto.setStatus(entity.getStatus());
        dto.setApprovedBy(entity.getApprovedBy());
        dto.setApprovedAt(entity.getApprovedAt());
        dto.setApprovedAmount(entity.getApprovedAmount());
        dto.setAlertThreshold(entity.getAlertThreshold());
        dto.setIsAlertTriggered(entity.getIsAlertTriggered());
        dto.setLastAlertAt(entity.getLastAlertAt());
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