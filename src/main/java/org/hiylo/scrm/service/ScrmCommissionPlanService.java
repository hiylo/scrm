/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCommissionPlanService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmCommissionPlanDto;
import org.hiylo.scrm.entity.ScrmCommissionPlanEntity;
import org.hiylo.scrm.entity.ScrmCommissionRecordEntity;
import org.hiylo.scrm.entity.ScrmCommissionRuleEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCommissionPlanRepository;
import org.hiylo.scrm.repository.ScrmCommissionRecordRepository;
import org.hiylo.scrm.repository.ScrmCommissionRuleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * SCRM 佣金方案管理服务 (方案子域)。
 * <p>
 * 承载佣金方案的增删改查 / 激活 / 暂停 / 过期 / 设默认 / 统计更新。同时托管佣金模块共享常量
 * (方案类型 / 状态 / 记录状态 / 默认操作人) 与按主键查询方案能力, 供规则 / 计算 / 记录
 * 兄弟类以 package 级访问复用。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmCommissionPlanService {

    // ==================== 方案类型常量 (共享) ====================

    /** 方案类型: 基于营收 */
    static final String PLAN_REVENUE_BASED = "REVENUE_BASED";
    /** 方案类型: 基于利润 */
    static final String PLAN_PROFIT_BASED = "PROFIT_BASED";
    /** 方案类型: 基于配额 */
    static final String PLAN_QUOTA_BASED = "QUOTA_BASED";
    /** 方案类型: 阶梯 */
    static final String PLAN_TIERED = "TIERED";
    /** 方案类型: 奖金 */
    static final String PLAN_BONUS = "BONUS";
    /** 方案类型: 组合 */
    static final String PLAN_COMBO = "COMBO";

    // ==================== 状态常量 (共享) ====================

    /** 方案状态: 激活 */
    static final String STATUS_ACTIVE = "ACTIVE";
    /** 方案状态: 暂停 */
    static final String STATUS_PAUSED = "PAUSED";
    /** 方案状态: 过期 */
    static final String STATUS_EXPIRED = "EXPIRED";
    /** 方案状态: 草稿 */
    static final String STATUS_DRAFT = "DRAFT";

    // ==================== 记录状态常量 (共享) ====================

    /** 记录状态: 已计算 */
    static final String RECORD_CALCULATED = "CALCULATED";
    /** 记录状态: 待审批 */
    static final String RECORD_PENDING_APPROVAL = "PENDING_APPROVAL";
    /** 记录状态: 已审批 */
    static final String RECORD_APPROVED = "APPROVED";
    /** 记录状态: 已驳回 */
    static final String RECORD_REJECTED = "REJECTED";
    /** 记录状态: 已发放 */
    static final String RECORD_PAID = "PAID";
    /** 记录状态: 追回 */
    static final String RECORD_CLAWBACK = "CLAWBACK";
    /** 记录状态: 已调整 */
    static final String RECORD_ADJUSTED = "ADJUSTED";

    /** 默认操作人 (共享) */
    static final String DEFAULT_OPERATOR = "scrm-system";

    /** 佣金方案数据访问层 */
    private final ScrmCommissionPlanRepository planRepository;

    /** 佣金规则数据访问层 (删除方案时清理规则用) */
    private final ScrmCommissionRuleRepository ruleRepository;

    /** 佣金记录数据访问层 (统计更新用) */
    private final ScrmCommissionRecordRepository recordRepository;

    // ============================================================
    // 方案管理
    // ============================================================

    /**
     * 创建佣金方案。
     * <p>校验 planCode 唯一性后写入归属账号 ID 持久化, 缺省字段填默认值。若设为默认, 清理旧默认。</p>
     *
     * @param dto 方案参数
     * @return 创建后的方案
     * @throws ScrmException 参数非法 / planCode 重复
     */
    @Transactional
    public ScrmCommissionPlanDto createPlan(ScrmCommissionPlanDto dto) throws ScrmException {
        validatePlanDto(dto, false);
        if (planRepository.findByPlanCode(dto.getPlanCode()).isPresent()) {
            throw ScrmException.conflict("佣金方案编码已存在: " + dto.getPlanCode());
        }
        if (dto.getEndDate() != null && dto.getEndDate().isBefore(dto.getStartDate())) {
            throw ScrmException.badRequest("生效结束日期不能早于开始日期");
        }
        ScrmCommissionPlanEntity entity = new ScrmCommissionPlanEntity();
        entity.setPlanName(dto.getPlanName());
        entity.setPlanCode(dto.getPlanCode());
        entity.setDescription(dto.getDescription());
        entity.setPlanType(dto.getPlanType());
        entity.setCalculationBasis(dto.getCalculationBasis());
        entity.setStartDate(dto.getStartDate());
        entity.setEndDate(dto.getEndDate());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : STATUS_ACTIVE);
        entity.setTargetAmount(dto.getTargetAmount() != null ? dto.getTargetAmount() : 0d);
        entity.setCapAmount(dto.getCapAmount() != null ? dto.getCapAmount() : 0d);
        entity.setMinAmount(dto.getMinAmount() != null ? dto.getMinAmount() : 0d);
        entity.setClawbackDays(dto.getClawbackDays() != null ? dto.getClawbackDays() : 0);
        entity.setPayoutFrequency(dto.getPayoutFrequency() != null ? dto.getPayoutFrequency() : "MONTHLY");
        entity.setPayoutDay(dto.getPayoutDay() != null ? dto.getPayoutDay() : 15);
        entity.setApplicableProducts(dto.getApplicableProducts());
        entity.setApplicableTeams(dto.getApplicableTeams());
        entity.setIsDefault(dto.getIsDefault() != null ? dto.getIsDefault() : false);
        entity.setTotalCommissionPaid(0d);
        entity.setTotalSalesAmount(0d);
        entity.setTotalOrders(0);
        entity.setCreatedBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : DEFAULT_OPERATOR);
        if (Boolean.TRUE.equals(entity.getIsDefault())) {
            planRepository.clearDefaultFlag();
        }
        entity = planRepository.save(entity);
        log.info("创建佣金方案: id={}, planName={}, planCode={}, planType={}",
                entity.getId(), entity.getPlanName(), entity.getPlanCode(), entity.getPlanType());
        return toPlanDto(entity);
    }

    /**
     * 更新佣金方案（字段非空才覆盖）。
     *
     * @param id  方案 ID
     * @param dto 方案参数
     * @return 更新后的方案
     * @throws ScrmException 方案不存在 / 参数非法 / planCode 重复
     */
    @Transactional
    public ScrmCommissionPlanDto updatePlan(Long id, ScrmCommissionPlanDto dto) throws ScrmException {
        ScrmCommissionPlanEntity entity = findPlanOrThrow(id);
        validatePlanDto(dto, true);
        if (dto.getPlanCode() != null && !dto.getPlanCode().equals(entity.getPlanCode()) && planRepository.findByPlanCode(dto.getPlanCode()).isPresent()) {
            throw ScrmException.conflict("佣金方案编码已存在: " + dto.getPlanCode());
        }
        if (dto.getPlanName() != null) entity.setPlanName(dto.getPlanName());
        if (dto.getPlanCode() != null) entity.setPlanCode(dto.getPlanCode());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getPlanType() != null) entity.setPlanType(dto.getPlanType());
        if (dto.getCalculationBasis() != null) entity.setCalculationBasis(dto.getCalculationBasis());
        if (dto.getStartDate() != null) entity.setStartDate(dto.getStartDate());
        if (dto.getEndDate() != null) entity.setEndDate(dto.getEndDate());
        if (entity.getEndDate() != null && entity.getEndDate().isBefore(entity.getStartDate())) {
            throw ScrmException.badRequest("生效结束日期不能早于开始日期");
        }
        if (dto.getStatus() != null) entity.setStatus(dto.getStatus());
        if (dto.getTargetAmount() != null) entity.setTargetAmount(dto.getTargetAmount());
        if (dto.getCapAmount() != null) entity.setCapAmount(dto.getCapAmount());
        if (dto.getMinAmount() != null) entity.setMinAmount(dto.getMinAmount());
        if (dto.getClawbackDays() != null) entity.setClawbackDays(dto.getClawbackDays());
        if (dto.getPayoutFrequency() != null) entity.setPayoutFrequency(dto.getPayoutFrequency());
        if (dto.getPayoutDay() != null) entity.setPayoutDay(dto.getPayoutDay());
        if (dto.getApplicableProducts() != null) entity.setApplicableProducts(dto.getApplicableProducts());
        if (dto.getApplicableTeams() != null) entity.setApplicableTeams(dto.getApplicableTeams());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        if (dto.getIsDefault() != null) {
            if (Boolean.TRUE.equals(dto.getIsDefault()) && !Boolean.TRUE.equals(entity.getIsDefault())) {
                planRepository.clearDefaultFlag();
            }
            entity.setIsDefault(dto.getIsDefault());
        }
        entity = planRepository.save(entity);
        log.info("更新佣金方案: id={}, planName={}", entity.getId(), entity.getPlanName());
        return toPlanDto(entity);
    }

    /**
     * 删除佣金方案。
     * <p>同时清理该方案下的规则。</p>
     *
     * @param id 方案 ID
     * @throws ScrmException 方案不存在
     */
    @Transactional
    public void deletePlan(Long id) throws ScrmException {
        ScrmCommissionPlanEntity entity = findPlanOrThrow(id);
        List<ScrmCommissionRuleEntity> rules = ruleRepository.findByPlanId(id);
        if (!rules.isEmpty()) {
            ruleRepository.deleteAll(rules);
        }
        planRepository.delete(entity);
        log.info("删除佣金方案: id={}, planName={}", id, entity.getPlanName());
    }

    /**
     * 查询方案详情。
     *
     * @param id 方案 ID
     * @return 方案 DTO
     * @throws ScrmException 方案不存在
     */
    @Transactional(readOnly = true)
    public ScrmCommissionPlanDto getPlan(Long id) throws ScrmException {
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
    public ScrmCommissionPlanDto getPlanByCode(String code) throws ScrmException {
        ScrmCommissionPlanEntity entity = planRepository
                .findByPlanCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "佣金方案不存在: code=" + code));
        return toPlanDto(entity);
    }

    /**
     * 分页查询佣金方案, 支持按方案类型/状态/关键字过滤。
     *
     * @param planType 方案类型 (可空)
     * @param status   状态 (可空)
     * @param keyword  关键字 (匹配方案名称/编码, 可空)
     * @param pageable 分页参数
     * @return 方案分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmCommissionPlanDto> listPlans(String planType, String status, String keyword, Pageable pageable) {
        Specification<ScrmCommissionPlanEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (planType != null && !planType.isBlank()) {
                predicates.add(cb.equal(root.get("planType"), planType));
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
    public ScrmCommissionPlanDto activatePlan(Long id) throws ScrmException {
        ScrmCommissionPlanEntity entity = findPlanOrThrow(id);
        entity.setStatus(STATUS_ACTIVE);
        entity = planRepository.save(entity);
        log.info("激活佣金方案: id={}", id);
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
    public ScrmCommissionPlanDto pausePlan(Long id) throws ScrmException {
        ScrmCommissionPlanEntity entity = findPlanOrThrow(id);
        entity.setStatus(STATUS_PAUSED);
        entity = planRepository.save(entity);
        log.info("暂停佣金方案: id={}", id);
        return toPlanDto(entity);
    }

    /**
     * 使方案过期。
     *
     * @param id 方案 ID
     * @return 更新后的方案
     * @throws ScrmException 方案不存在
     */
    @Transactional
    public ScrmCommissionPlanDto expirePlan(Long id) throws ScrmException {
        ScrmCommissionPlanEntity entity = findPlanOrThrow(id);
        entity.setStatus(STATUS_EXPIRED);
        entity = planRepository.save(entity);
        log.info("过期佣金方案: id={}", id);
        return toPlanDto(entity);
    }

    /**
     * 设为默认方案 (清理旧默认)。
     *
     * @param id 方案 ID
     * @return 更新后的方案
     * @throws ScrmException 方案不存在
     */
    @Transactional
    public ScrmCommissionPlanDto setDefault(Long id) throws ScrmException {
        ScrmCommissionPlanEntity entity = findPlanOrThrow(id);
        if (!Boolean.TRUE.equals(entity.getIsDefault())) {
            planRepository.clearDefaultFlag();
            entity.setIsDefault(true);
            entity = planRepository.save(entity);
        }
        log.info("设置默认佣金方案: id={}", id);
        return toPlanDto(entity);
    }

    /**
     * 更新方案统计 (累计已发放佣金 / 销售总额 / 订单数)。
     *
     * @param id 方案 ID
     * @return 更新后的方案
     * @throws ScrmException 方案不存在
     */
    @Transactional
    public ScrmCommissionPlanDto updatePlanStats(Long id) throws ScrmException {
        ScrmCommissionPlanEntity entity = findPlanOrThrow(id);
        List<ScrmCommissionRecordEntity> records = recordRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("planId"), id));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
        double totalPaid = records.stream()
                .filter(r -> RECORD_PAID.equals(r.getStatus()))
                .mapToDouble(r -> r.getPaidAmount() != null ? r.getPaidAmount() : 0d)
                .sum();
        double totalSales = records.stream()
                .mapToDouble(r -> r.getOrderAmount() != null ? r.getOrderAmount() : 0d)
                .sum();
        int totalOrders = (int) records.stream()
                .filter(r -> r.getOrderId() != null)
                .count();
        entity.setTotalCommissionPaid(totalPaid);
        entity.setTotalSalesAmount(totalSales);
        entity.setTotalOrders(totalOrders);
        entity = planRepository.save(entity);
        log.info("更新佣金方案统计: id={}, totalCommissionPaid={}, totalSalesAmount={}, totalOrders={}",
                id, totalPaid, totalSales, totalOrders);
        return toPlanDto(entity);
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 校验方案 DTO。
     *
     * @param dto    方案参数
     * @param partial 是否部分更新
     * @throws ScrmException 参数非法
     */
    private void validatePlanDto(ScrmCommissionPlanDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("方案参数不能为空");
        }
        if (dto.getPlanName() != null) {
            if (dto.getPlanName().isBlank()) {
                throw ScrmException.badRequest("方案名称不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("方案名称不能为空");
        }
        if (dto.getPlanCode() != null) {
            if (dto.getPlanCode().isBlank()) {
                throw ScrmException.badRequest("方案编码不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("方案编码不能为空");
        }
        if (!partial && dto.getPlanType() == null) {
            throw ScrmException.badRequest("方案类型不能为空");
        }
        if (!partial && dto.getCalculationBasis() == null) {
            throw ScrmException.badRequest("计算基础不能为空");
        }
        if (!partial && dto.getStartDate() == null) {
            throw ScrmException.badRequest("生效开始日期不能为空");
        }
    }

    /**
     * 按主键查询方案, 不存在或越权抛异常。
     */
    ScrmCommissionPlanEntity findPlanOrThrow(Long id) throws ScrmException {
        ScrmCommissionPlanEntity entity = planRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "佣金方案不存在: id=" + id));
        return entity;
    }

    /**
     * 方案实体转 DTO
     */
    private ScrmCommissionPlanDto toPlanDto(ScrmCommissionPlanEntity entity) {
        ScrmCommissionPlanDto dto = new ScrmCommissionPlanDto();
        dto.setId(entity.getId());
        dto.setPlanName(entity.getPlanName());
        dto.setPlanCode(entity.getPlanCode());
        dto.setDescription(entity.getDescription());
        dto.setPlanType(entity.getPlanType());
        dto.setCalculationBasis(entity.getCalculationBasis());
        dto.setStartDate(entity.getStartDate());
        dto.setEndDate(entity.getEndDate());
        dto.setStatus(entity.getStatus());
        dto.setTargetAmount(entity.getTargetAmount());
        dto.setCapAmount(entity.getCapAmount());
        dto.setMinAmount(entity.getMinAmount());
        dto.setClawbackDays(entity.getClawbackDays());
        dto.setPayoutFrequency(entity.getPayoutFrequency());
        dto.setPayoutDay(entity.getPayoutDay());
        dto.setApplicableProducts(entity.getApplicableProducts());
        dto.setApplicableTeams(entity.getApplicableTeams());
        dto.setIsDefault(entity.getIsDefault());
        dto.setTotalCommissionPaid(entity.getTotalCommissionPaid());
        dto.setTotalSalesAmount(entity.getTotalSalesAmount());
        dto.setTotalOrders(entity.getTotalOrders());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}
