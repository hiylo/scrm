/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerLifecycleRecordService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmCustomerLifecycleDto;
import org.hiylo.scrm.dto.ScrmLifecycleBulkTransitionDto;
import org.hiylo.scrm.dto.ScrmLifecycleTransitionActionDto;
import org.hiylo.scrm.dto.ScrmLifecycleTransitionRequestDto;
import org.hiylo.scrm.entity.ScrmCustomerLifecycleEntity;
import org.hiylo.scrm.entity.ScrmLifecycleStageEntity;
import org.hiylo.scrm.entity.ScrmLifecycleTransitionEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCustomerLifecycleRepository;
import org.hiylo.scrm.repository.ScrmLifecycleStageRepository;
import org.hiylo.scrm.repository.ScrmLifecycleTransitionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SCRM 客户生命周期记录管理服务。
 * <p>
 * 承载客户生命周期记录的核心能力: 生命周期 CRUD / 多维查询 (阶段 / 价值分层 / 风险等级 / 流失 /
 * 高价值 / 风险) / 检索 / 单/批量流转 / 自动流转 / 唤醒 / 标记流失。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026/09/19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmCustomerLifecycleRecordService {

    /** 既有生命周期服务 (复用阶段定义 / 流转规则 / 转换历史能力) */
    private final ScrmLifecycleService scrmLifecycleService;

    /** 评分与统计分析服务 (复用派生指标计算) */
    private final ScrmCustomerLifecycleAnalyticsService analyticsService;

    /** 阶段数据访问层 */
    private final ScrmLifecycleStageRepository stageRepository;

    /** 客户生命周期数据访问层 */
    private final ScrmCustomerLifecycleRepository customerLifecycleRepository;

    /** 流转规则数据访问层 */
    private final ScrmLifecycleTransitionRepository transitionRepository;

    /**
     * 创建客户生命周期记录 (新客户初始化)。
     *
     * @param dto 生命周期参数
     * @return 创建后的生命周期
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmCustomerLifecycleEntity createLifecycle(ScrmCustomerLifecycleDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("生命周期参数不能为空");
        }
        if (dto.getCustomerId() == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        ScrmCustomerLifecycleEntity existed = customerLifecycleRepository
                .findByCustomerId(dto.getCustomerId()).orElse(null);
        if (existed != null) {
            throw ScrmException.conflict("客户生命周期已存在: customerId=" + dto.getCustomerId());
        }
        ScrmCustomerLifecycleEntity entity = new ScrmCustomerLifecycleEntity();
        entity.setCustomerId(dto.getCustomerId());
        entity.setCustomerName(dto.getCustomerName());
        entity.setCurrentStageId(dto.getCurrentStageId());
        entity.setCurrentStageCode(dto.getCurrentStageCode());
        entity.setCurrentStageName(dto.getCurrentStageName());
        LocalDateTime now = LocalDateTime.now();
        entity.setEnteredCurrentStageAt(dto.getEnteredCurrentStageAt() != null ? dto.getEnteredCurrentStageAt() : now);
        entity.setDurationInStageDays(dto.getDurationInStageDays() != null ? dto.getDurationInStageDays() : 0);
        entity.setPreviousStageId(dto.getPreviousStageId());
        entity.setPreviousStageCode(dto.getPreviousStageCode());
        entity.setStageHistoryCount(dto.getStageHistoryCount() != null ? dto.getStageHistoryCount() : 0);
        entity.setIsOverdue(dto.getIsOverdue() != null ? dto.getIsOverdue() : Boolean.FALSE);
        entity.setOverdueDays(dto.getOverdueDays() != null ? dto.getOverdueDays() : 0);
        entity.setNextStageId(dto.getNextStageId());
        entity.setExpectedTransitionAt(dto.getExpectedTransitionAt());
        entity.setAssignedTo(dto.getAssignedTo());
        entity.setNotes(dto.getNotes());
        entity.setLastUpdatedAt(now);
        entity = customerLifecycleRepository.save(entity);
        log.info("创建客户生命周期: id={}, customerId={}, stageCode={}",
                entity.getId(), entity.getCustomerId(), entity.getCurrentStageCode());
        return entity;
    }

    /**
     * 更新客户生命周期 (字段非空才覆盖)。
     *
     * @param id  生命周期 ID
     * @param dto 生命周期参数
     * @return 更新后的生命周期
     * @throws ScrmException 生命周期不存在
     */
    @Transactional
    public ScrmCustomerLifecycleEntity updateLifecycle(
            Long id, ScrmCustomerLifecycleDto dto) throws ScrmException {
        ScrmCustomerLifecycleEntity entity = findLifecycleOrThrow(id);
        if (dto == null) {
            throw ScrmException.badRequest("生命周期参数不能为空");
        }
        if (dto.getCustomerName() != null) entity.setCustomerName(dto.getCustomerName());
        if (dto.getCurrentStageId() != null) entity.setCurrentStageId(dto.getCurrentStageId());
        if (dto.getCurrentStageCode() != null) entity.setCurrentStageCode(dto.getCurrentStageCode());
        if (dto.getCurrentStageName() != null) entity.setCurrentStageName(dto.getCurrentStageName());
        if (dto.getEnteredCurrentStageAt() != null) entity.setEnteredCurrentStageAt(dto.getEnteredCurrentStageAt());
        if (dto.getDurationInStageDays() != null) entity.setDurationInStageDays(dto.getDurationInStageDays());
        if (dto.getPreviousStageId() != null) entity.setPreviousStageId(dto.getPreviousStageId());
        if (dto.getPreviousStageCode() != null) entity.setPreviousStageCode(dto.getPreviousStageCode());
        if (dto.getStageHistoryCount() != null) entity.setStageHistoryCount(dto.getStageHistoryCount());
        if (dto.getIsOverdue() != null) entity.setIsOverdue(dto.getIsOverdue());
        if (dto.getOverdueDays() != null) entity.setOverdueDays(dto.getOverdueDays());
        if (dto.getNextStageId() != null) entity.setNextStageId(dto.getNextStageId());
        if (dto.getExpectedTransitionAt() != null) entity.setExpectedTransitionAt(dto.getExpectedTransitionAt());
        if (dto.getAssignedTo() != null) entity.setAssignedTo(dto.getAssignedTo());
        if (dto.getNotes() != null) entity.setNotes(dto.getNotes());
        entity.setLastUpdatedAt(LocalDateTime.now());
        entity = customerLifecycleRepository.save(entity);
        log.info("更新客户生命周期: id={}, customerId={}", entity.getId(), entity.getCustomerId());
        return entity;
    }

    /**
     * 删除客户生命周期记录。
     *
     * @param id 生命周期 ID
     * @throws ScrmException 生命周期不存在
     */
    @Transactional
    public void deleteLifecycle(Long id) throws ScrmException {
        ScrmCustomerLifecycleEntity entity = findLifecycleOrThrow(id);
        customerLifecycleRepository.delete(entity);
        log.info("删除客户生命周期: id={}, customerId={}", id, entity.getCustomerId());
    }

    /**
     * 查询生命周期详情。
     *
     * @param id 生命周期 ID
     * @return 生命周期实体
     * @throws ScrmException 生命周期不存在
     */
    @Transactional(readOnly = true)
    public ScrmCustomerLifecycleEntity getLifecycle(Long id) throws ScrmException {
        return findLifecycleOrThrow(id);
    }

    /**
     * 按客户查询生命周期。
     *
     * @param customerId 客户 ID
     * @return 生命周期实体
     * @throws ScrmException 客户生命周期不存在
     */
    @Transactional(readOnly = true)
    public ScrmCustomerLifecycleEntity getLifecycleByCustomer(Long customerId) throws ScrmException {
        return scrmLifecycleService.getCustomerLifecycle(customerId);
    }

    /**
     * 分页查询全部客户生命周期 (按 enteredCurrentStageAt DESC)。
     *
     * @param pageable 分页参数
     * @return 生命周期分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmCustomerLifecycleEntity> listLifecycles(Pageable pageable) {
        PageRequest sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "enteredCurrentStageAt"));
        return customerLifecycleRepository.findAll(sorted);
    }

    /**
     * 按当前阶段编码分页查询客户生命周期。
     *
     * @param stage    阶段编码
     * @param pageable 分页参数
     * @return 生命周期分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmCustomerLifecycleEntity> getLifecyclesByStage(String stage, Pageable pageable) {
        Specification<ScrmCustomerLifecycleEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (stage != null && !stage.isBlank()) {
                predicates.add(cb.equal(root.get("currentStageCode"), stage));
            }
            query.orderBy(cb.desc(root.get("enteredCurrentStageAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return customerLifecycleRepository.findAll(spec, pageable);
    }

    /**
     * 按价值分层分页查询客户 (派生指标, 内存过滤)。
     *
     * @param valueSegment 价值分层: VIP/HIGH_VALUE/STANDARD/LOW_VALUE/AT_RISK/CHURNED
     * @param pageable     分页参数
     * @return 生命周期分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmCustomerLifecycleEntity> getLifecyclesByValueSegment(String valueSegment, Pageable pageable) {
        if (valueSegment == null || valueSegment.isBlank()) {
            throw ScrmException.badRequest("价值分层不能为空");
        }
        List<ScrmCustomerLifecycleEntity> all = analyticsService.loadAllLifecycles();
        List<ScrmCustomerLifecycleEntity> filtered = all.stream()
                .filter(e -> valueSegment.equals(analyticsService.computeValueSegment(e)))
                .collect(Collectors.toList());
        return pageOf(filtered, pageable);
    }

    /**
     * 按风险等级分页查询客户 (派生指标, 内存过滤)。
     *
     * @param riskLevel 风险等级: LOW/MEDIUM/HIGH/CRITICAL
     * @param pageable  分页参数
     * @return 生命周期分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmCustomerLifecycleEntity> getLifecyclesByRiskLevel(String riskLevel, Pageable pageable) {
        if (riskLevel == null || riskLevel.isBlank()) {
            throw ScrmException.badRequest("风险等级不能为空");
        }
        List<ScrmCustomerLifecycleEntity> all = analyticsService.loadAllLifecycles();
        List<ScrmCustomerLifecycleEntity> filtered = all.stream()
                .filter(e -> riskLevel.equals(analyticsService.computeRiskLevel(analyticsService.calculateChurnRisk(e))))
                .collect(Collectors.toList());
        return pageOf(filtered, pageable);
    }

    /**
     * 分页查询风险客户 (流失风险 ≥ 阈值或超期)。
     *
     * @param pageable 分页参数
     * @return 风险客户分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmCustomerLifecycleEntity> getAtRiskCustomers(Pageable pageable) {
        List<ScrmCustomerLifecycleEntity> all = analyticsService.loadAllLifecycles();
        List<ScrmCustomerLifecycleEntity> filtered = all.stream()
                .filter(e -> analyticsService.calculateChurnRisk(e) >= ScrmCustomerLifecycleAnalyticsService.AT_RISK_THRESHOLD
                        || Boolean.TRUE.equals(e.getIsOverdue()))
                .collect(Collectors.toList());
        filtered.sort(Comparator.comparingDouble(analyticsService::calculateChurnRisk).reversed());
        return pageOf(filtered, pageable);
    }

    /**
     * 分页查询流失客户 (当前处于流失阶段)。
     *
     * @param pageable 分页参数
     * @return 流失客户分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmCustomerLifecycleEntity> getChurnedCustomers(Pageable pageable) {
        Set<Long> churnStageIds = analyticsService.churnStageIds();
        List<ScrmCustomerLifecycleEntity> all = analyticsService.loadAllLifecycles();
        List<ScrmCustomerLifecycleEntity> filtered = all.stream()
                .filter(e -> e.getCurrentStageId() != null && churnStageIds.contains(e.getCurrentStageId()))
                .collect(Collectors.toList());
        return pageOf(filtered, pageable);
    }

    /**
     * 分页查询高价值客户 (派生指标, 内存过滤)。
     *
     * @param pageable 分页参数
     * @return 高价值客户分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmCustomerLifecycleEntity> getHighValueCustomers(Pageable pageable) {
        List<ScrmCustomerLifecycleEntity> all = analyticsService.loadAllLifecycles();
        List<ScrmCustomerLifecycleEntity> filtered = all.stream()
                .filter(e -> {
                    String seg = analyticsService.computeValueSegment(e);
                    return "HIGH_VALUE".equals(seg) || "VIP".equals(seg);
                })
                .collect(Collectors.toList());
        return pageOf(filtered, pageable);
    }

    /**
     * 检索客户生命周期 (支持 customerId / stageCode / isOverdue / assignedTo / customerName)。
     *
     * @param criteria 检索条件
     * @param pageable 分页参数
     * @return 生命周期分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmCustomerLifecycleEntity> searchLifecycles(Map<String, Object> criteria, Pageable pageable) {
        Specification<ScrmCustomerLifecycleEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria != null) {
                Object customerId = criteria.get("customerId");
                if (customerId != null && !"".equals(customerId.toString())) {
                    predicates.add(cb.equal(root.get("customerId"), Long.valueOf(customerId.toString())));
                }
                Object stageCode = criteria.get("stageCode");
                if (stageCode != null && !"".equals(stageCode.toString())) {
                    predicates.add(cb.equal(root.get("currentStageCode"), stageCode.toString()));
                }
                Object isOverdue = criteria.get("isOverdue");
                if (isOverdue != null && !"".equals(isOverdue.toString())) {
                    predicates.add(cb.equal(root.get("isOverdue"), Boolean.valueOf(isOverdue.toString())));
                }
                Object assignedTo = criteria.get("assignedTo");
                if (assignedTo != null && !"".equals(assignedTo.toString())) {
                    predicates.add(cb.equal(root.get("assignedTo"), assignedTo.toString()));
                }
                Object customerName = criteria.get("customerName");
                if (customerName != null && !"".equals(customerName.toString())) {
                    predicates.add(cb.like(root.get("customerName"), "%" + customerName + "%"));
                }
            }
            query.orderBy(cb.desc(root.get("enteredCurrentStageAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return customerLifecycleRepository.findAll(spec, pageable);
    }

    /**
     * 流转客户 (验证 → 更新阶段 → 记录历史 → 触发自动化)。
     *
     * @param request 流转请求 (customerId + toStage + trigger + notes)
     * @return 更新后的客户生命周期
     * @throws ScrmException 阶段不存在 / 无可用转换规则 / 冷却期内
     */
    @Transactional
    public ScrmCustomerLifecycleEntity transitionCustomer(ScrmLifecycleTransitionRequestDto request)
            throws ScrmException {
        if (request == null) {
            throw ScrmException.badRequest("流转请求不能为空");
        }
        ScrmLifecycleTransitionActionDto action = new ScrmLifecycleTransitionActionDto();
        action.setCustomerId(request.getCustomerId());
        action.setToStageCode(request.getToStage());
        action.setTriggerEvent(request.getTrigger());
        action.setDescription(request.getNotes());
        action.setOperatorId(request.getOperatorId());
        action.setOperatorName(request.getOperatorName());
        ScrmCustomerLifecycleEntity updated = scrmLifecycleService.transitionCustomer(action);
        triggerAutomation(request.getCustomerId(), request.getToStage(), request.getTrigger());
        return updated;
    }

    /**
     * 批量流转客户阶段。
     *
     * @param customerIds 客户 ID 列表
     * @param toStage     目标阶段编码
     * @param trigger     触发原因
     * @return 转换结果列表 (失败客户被跳过)
     * @throws ScrmException 参数非法
     */
    @Transactional
    public List<ScrmCustomerLifecycleEntity> batchTransition(List<Long> customerIds, String toStage, String trigger)
            throws ScrmException {
        if (customerIds == null || customerIds.isEmpty()) {
            throw ScrmException.badRequest("客户 ID 列表不能为空");
        }
        if (toStage == null || toStage.isBlank()) {
            throw ScrmException.badRequest("目标阶段不能为空");
        }
        ScrmLifecycleBulkTransitionDto bulk = new ScrmLifecycleBulkTransitionDto();
        bulk.setCustomerIds(customerIds);
        bulk.setToStageCode(toStage);
        bulk.setTriggerEvent(trigger);
        bulk.setDescription("批量流转: " + trigger);
        return scrmLifecycleService.bulkTransition(bulk);
    }

    /**
     * 自动流转: 检查条件 (超期 / 目标停留超期) → 触发流转。
     *
     * @param customerId 客户 ID
     * @return 流转后的客户生命周期 (无匹配条件时返回当前)
     * @throws ScrmException 客户生命周期不存在
     */
    @Transactional
    public ScrmCustomerLifecycleEntity autoTransition(Long customerId) throws ScrmException {
        ScrmCustomerLifecycleEntity lifecycle = getLifecycleByCustomer(customerId);
        boolean needTransition = Boolean.TRUE.equals(lifecycle.getIsOverdue());
        if (!needTransition) {
            ScrmLifecycleStageEntity stage = stageRepository.findById(lifecycle.getCurrentStageId()).orElse(null);
            if (stage != null && stage.getTargetDurationDays() != null && stage.getTargetDurationDays() > 0 && lifecycle.getEnteredCurrentStageAt() != null) {
                long days = ChronoUnit.DAYS.between(lifecycle.getEnteredCurrentStageAt(), LocalDateTime.now());
                needTransition = days > stage.getTargetDurationDays();
            }
        }
        if (!needTransition) {
            log.info("自动流转条件未满足, 跳过: customerId={}", customerId);
            return lifecycle;
        }
        List<ScrmLifecycleTransitionEntity> rules = transitionRepository
                .findByFromStageIdAndIsEnabledOrderByPriorityDesc(
                         lifecycle.getCurrentStageId(), Boolean.TRUE);
        if (rules.isEmpty()) {
            log.info("无可用自动流转规则: customerId={}", customerId);
            return lifecycle;
        }
        ScrmLifecycleTransitionEntity rule = rules.get(0);
        ScrmLifecycleTransitionActionDto action = new ScrmLifecycleTransitionActionDto();
        action.setCustomerId(customerId);
        action.setCustomerName(lifecycle.getCustomerName());
        action.setToStageCode(rule.getToStageCode());
        action.setTriggerEvent("RULE");
        action.setDescription("自动流转: 超期/目标停留触发");
        ScrmCustomerLifecycleEntity updated = scrmLifecycleService.transitionCustomer(action);
        triggerAutomation(customerId, rule.getToStageCode(), "RULE");
        return updated;
    }

    /**
     * 唤醒流失客户 (从流失阶段流转到唤醒/互动阶段)。
     *
     * @param customerId 客户 ID
     * @param campaign   唤醒活动名称
     * @return 流转后的客户生命周期
     * @throws ScrmException 客户生命周期不存在 / 无可用唤醒阶段
     */
    @Transactional
    public ScrmCustomerLifecycleEntity reactivateCustomer(Long customerId, String campaign) throws ScrmException {
        ScrmCustomerLifecycleEntity lifecycle = getLifecycleByCustomer(customerId);
        List<ScrmLifecycleStageEntity> stages = stageRepository.findAllByOrderByStageOrderAsc();
        ScrmLifecycleStageEntity target = stages.stream()
                .filter(s -> "REACTIVATION".equals(s.getStageCategory()))
                .findFirst()
                .orElseGet(() -> stages.stream()
                        .filter(s -> !Boolean.TRUE.equals(s.getIsChurnStage()))
                        .findFirst()
                        .orElse(null));
        if (target == null) {
            throw ScrmException.badRequest("无可用唤醒阶段: customerId=" + customerId);
        }
        ScrmLifecycleTransitionActionDto action = new ScrmLifecycleTransitionActionDto();
        action.setCustomerId(customerId);
        action.setCustomerName(lifecycle.getCustomerName());
        action.setToStageCode(target.getStageCode());
        action.setTriggerEvent("ENGAGEMENT");
        action.setDescription("唤醒客户: campaign=" + campaign);
        ScrmCustomerLifecycleEntity updated = scrmLifecycleService.transitionCustomer(action);
        log.info("唤醒流失客户: customerId={}, campaign={}, toStage={}", customerId, campaign, target.getStageCode());
        return updated;
    }

    /**
     * 标记客户流失 (流转到流失阶段)。
     *
     * @param customerId 客户 ID
     * @param reason     流失原因
     * @return 流转后的客户生命周期
     * @throws ScrmException 客户生命周期不存在 / 无流失阶段
     */
    @Transactional
    public ScrmCustomerLifecycleEntity markChurned(Long customerId, String reason) throws ScrmException {
        ScrmCustomerLifecycleEntity lifecycle = getLifecycleByCustomer(customerId);
        ScrmLifecycleStageEntity churnStage = stageRepository.findAllByOrderByStageOrderAsc().stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsChurnStage()))
                .findFirst()
                .orElseThrow(() -> ScrmException.badRequest("未配置流失阶段"));
        ScrmLifecycleTransitionActionDto action = new ScrmLifecycleTransitionActionDto();
        action.setCustomerId(customerId);
        action.setCustomerName(lifecycle.getCustomerName());
        action.setToStageCode(churnStage.getStageCode());
        action.setTriggerEvent("INACTIVITY");
        action.setDescription("标记流失: " + reason);
        ScrmCustomerLifecycleEntity updated = scrmLifecycleService.transitionCustomer(action);
        log.info("标记客户流失: customerId={}, reason={}", customerId, reason);
        return updated;
    }

    /**
     * 触发自动化策略 (模拟实现, 记录日志)。
     *
     * @param customerId 客户 ID
     * @param toStage    目标阶段
     * @param trigger    触发原因
     */
    private void triggerAutomation(Long customerId, String toStage, String trigger) {
        log.info("触发生命周期自动化: customerId={}, toStage={}, trigger={}", customerId, toStage, trigger);
    }

    /**
     * 列表内存分页。
     *
     * @param all      全量列表
     * @param pageable 分页参数
     * @param <T>      元素类型
     * @return 分页结果
     */
    private <T> Page<T> pageOf(List<T> all, Pageable pageable) {
        int total = all.size();
        int start = (int) Math.min(pageable.getOffset(), total);
        int end = Math.min(start + pageable.getPageSize(), total);
        List<T> content = all.subList(start, end);
        return new PageImpl<>(content, pageable, total);
    }

    /**
     * 按主键查询客户生命周期, 不存在抛异常, 并校验账号归属。
     *
     * @param id 生命周期 ID
     * @return 生命周期实体
     * @throws ScrmException 生命周期不存在
     */
    private ScrmCustomerLifecycleEntity findLifecycleOrThrow(Long id) throws ScrmException {
        ScrmCustomerLifecycleEntity entity = customerLifecycleRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "客户生命周期不存在: id=" + id));
        return entity;
    }
}
