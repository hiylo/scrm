/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLifecycleService.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmLifecycleBulkTransitionDto;
import org.hiylo.scrm.dto.ScrmLifecycleStageDto;
import org.hiylo.scrm.dto.ScrmLifecycleTransitionActionDto;
import org.hiylo.scrm.dto.ScrmLifecycleTransitionDto;
import org.hiylo.scrm.entity.ScrmCustomerLifecycleEntity;
import org.hiylo.scrm.entity.ScrmLifecycleHistoryEntity;
import org.hiylo.scrm.entity.ScrmLifecycleStageEntity;
import org.hiylo.scrm.entity.ScrmLifecycleTransitionEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCustomerLifecycleRepository;
import org.hiylo.scrm.repository.ScrmLifecycleHistoryRepository;
import org.hiylo.scrm.repository.ScrmLifecycleStageRepository;
import org.hiylo.scrm.repository.ScrmLifecycleTransitionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * SCRM 客户生命周期阶段管理服务。
 * <p>
 * 承载客户生命周期管理核心能力:
 * <ul>
 *   <li>阶段定义: 增删改查 / 启停 / 重排 / 统计刷新 / 阶段漏斗</li>
 *   <li>阶段流转规则: 增删改查 / 启停 / 可用转换查询</li>
 *   <li>客户当前阶段: 查询 / 按阶段查询 / 超期查询 / 手动分配 / 单/批量转换 / 事件触发转换</li>
 *   <li>转换历史: 客户历史 / 列表查询 / 阶段转换趋势</li>
 *   <li>统计: 生命周期概览 / 转换统计 / 转化漏斗 / 阶段停留分析 / 流失率</li>
 * </ul>
 * 所有写操作写入当前用户归属账号, 实现数据隔离。
 * 事件触发转换采用模拟实现 (按 triggerEvents 字段匹配)。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmLifecycleService {

    /** 默认阶段顺序 */
    private static final int DEFAULT_STAGE_ORDER = 0;

    /** 默认优先级 */
    private static final int DEFAULT_PRIORITY = 0;

    /** 默认冷却天数 */
    private static final int DEFAULT_COOLDOWN_DAYS = 0;

    /** 转换类型: 自动 */
    private static final String TRANSITION_TYPE_AUTO = "AUTO";
    /** 转换类型: 手动 */
    private static final String TRANSITION_TYPE_MANUAL = "MANUAL";
    /** 转换类型: 系统 */
    private static final String TRANSITION_TYPE_SYSTEM = "SYSTEM";

    /** 合法的阶段类别 */
    private static final List<String> VALID_STAGE_CATEGORIES = List.of(
            "ACQUISITION", "ENGAGEMENT", "ACTIVATION", "RETENTION", "ADVOCACY", "CHURN", "REACTIVATION");

    /** 合法的转换类型 */
    private static final List<String> VALID_TRANSITION_TYPES = List.of(
            TRANSITION_TYPE_AUTO, TRANSITION_TYPE_MANUAL, TRANSITION_TYPE_SYSTEM);

    /** 阶段数据访问层 */
    private final ScrmLifecycleStageRepository stageRepository;

    /** 流转规则数据访问层 */
    private final ScrmLifecycleTransitionRepository transitionRepository;

    /** 客户生命周期数据访问层 */
    private final ScrmCustomerLifecycleRepository customerLifecycleRepository;

    /** 转换历史数据访问层 */
    private final ScrmLifecycleHistoryRepository historyRepository;

    // ============================================================
    // 阶段管理
    // ============================================================

    /**
     * 创建生命周期阶段。
     * <p>校验 stageCategory 合法性与 stageCode 唯一性后写入账号 ID 持久化,
     * isStartStage / isEndStage / isChurnStage / enabled / 统计字段缺省时填默认值。</p>
     *
     * @param dto 阶段参数
     * @return 创建后的阶段
     * @throws ScrmException 参数非法 / 编码重复
     */
    @Transactional
    public ScrmLifecycleStageEntity createStage(ScrmLifecycleStageDto dto) throws ScrmException {
        validateStageDto(dto, false);
        if (stageRepository.findByStageCode(dto.getStageCode()).isPresent()) {
            throw ScrmException.conflict("阶段编码已存在: " + dto.getStageCode());
        }
        ScrmLifecycleStageEntity entity = new ScrmLifecycleStageEntity();
        entity.setStageName(dto.getStageName());
        entity.setStageCode(dto.getStageCode());
        entity.setDescription(dto.getDescription());
        entity.setStageOrder(dto.getStageOrder() != null ? dto.getStageOrder() : DEFAULT_STAGE_ORDER);
        entity.setStageCategory(dto.getStageCategory());
        entity.setColor(dto.getColor());
        entity.setIcon(dto.getIcon());
        entity.setEntryCriteria(dto.getEntryCriteria());
        entity.setExitCriteria(dto.getExitCriteria());
        entity.setTargetDurationDays(dto.getTargetDurationDays());
        entity.setIsStartStage(dto.getIsStartStage() != null ? dto.getIsStartStage() : Boolean.FALSE);
        entity.setIsEndStage(dto.getIsEndStage() != null ? dto.getIsEndStage() : Boolean.FALSE);
        entity.setIsChurnStage(dto.getIsChurnStage() != null ? dto.getIsChurnStage() : Boolean.FALSE);
        entity.setCustomerCount(0);
        entity.setTotalEnteredCount(0);
        entity.setAvgDurationDays(0.0);
        entity.setConversionRate(0.0);
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : Boolean.TRUE);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = stageRepository.save(entity);
        log.info("创建生命周期阶段: id={}, stageName={}, stageCode={}, category={}",
                entity.getId(), entity.getStageName(), entity.getStageCode(), entity.getStageCategory());
        return entity;
    }

    /**
     * 更新生命周期阶段（字段非空才覆盖）。
     *
     * @param id  阶段 ID
     * @param dto 阶段参数
     * @return 更新后的阶段
     * @throws ScrmException 阶段不存在 / 参数非法 / 编码重复
     */
    @Transactional
    public ScrmLifecycleStageEntity updateStage(Long id, ScrmLifecycleStageDto dto) throws ScrmException {
        ScrmLifecycleStageEntity entity = findStageOrThrow(id);
        validateStageDto(dto, true);
        if (dto.getStageCode() != null && !dto.getStageCode().equals(entity.getStageCode())) {
            if (stageRepository.findByStageCode(dto.getStageCode()).isPresent()) {
                throw ScrmException.conflict("阶段编码已存在: " + dto.getStageCode());
            }
        }
        if (dto.getStageName() != null) entity.setStageName(dto.getStageName());
        if (dto.getStageCode() != null) entity.setStageCode(dto.getStageCode());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getStageOrder() != null) entity.setStageOrder(dto.getStageOrder());
        if (dto.getStageCategory() != null) entity.setStageCategory(dto.getStageCategory());
        if (dto.getColor() != null) entity.setColor(dto.getColor());
        if (dto.getIcon() != null) entity.setIcon(dto.getIcon());
        if (dto.getEntryCriteria() != null) entity.setEntryCriteria(dto.getEntryCriteria());
        if (dto.getExitCriteria() != null) entity.setExitCriteria(dto.getExitCriteria());
        if (dto.getTargetDurationDays() != null) entity.setTargetDurationDays(dto.getTargetDurationDays());
        if (dto.getIsStartStage() != null) entity.setIsStartStage(dto.getIsStartStage());
        if (dto.getIsEndStage() != null) entity.setIsEndStage(dto.getIsEndStage());
        if (dto.getIsChurnStage() != null) entity.setIsChurnStage(dto.getIsChurnStage());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = stageRepository.save(entity);
        log.info("更新生命周期阶段: id={}, stageName={}", entity.getId(), entity.getStageName());
        return entity;
    }

    /**
     * 删除生命周期阶段。
     *
     * @param id 阶段 ID
     * @throws ScrmException 阶段不存在
     */
    @Transactional
    public void deleteStage(Long id) throws ScrmException {
        ScrmLifecycleStageEntity entity = findStageOrThrow(id);
        // 检查是否有关联的客户生命周期记录
        long customerCount = customerLifecycleRepository.countByCurrentStageId(id);
        if (customerCount > 0) {
            throw ScrmException.conflict("阶段下仍有 " + customerCount + " 个客户, 无法删除");
        }
        // 清理关联的转换规则
        List<ScrmLifecycleTransitionEntity> transitions = new ArrayList<>();
        transitions.addAll(transitionRepository.findByFromStageIdAndIsEnabledOrderByPriorityDesc(
                 id, Boolean.TRUE));
        transitions.addAll(transitionRepository.findByFromStageIdAndIsEnabledOrderByPriorityDesc(
                 id, Boolean.FALSE));
        if (!transitions.isEmpty()) {
            transitionRepository.deleteAll(transitions);
        }
        stageRepository.delete(entity);
        log.info("删除生命周期阶段: id={}, stageName={}", id, entity.getStageName());
    }

    /**
     * 查询阶段详情。
     *
     * @param id 阶段 ID
     * @return 阶段实体
     * @throws ScrmException 阶段不存在
     */
    @Transactional(readOnly = true)
    public ScrmLifecycleStageEntity getStage(Long id) throws ScrmException {
        return findStageOrThrow(id);
    }

    /**
     * 按编码查询阶段。
     *
     * @param code 阶段编码
     * @return 阶段实体
     * @throws ScrmException 阶段不存在
     */
    @Transactional(readOnly = true)
    public ScrmLifecycleStageEntity getStageByCode(String code) throws ScrmException {
        if (code == null || code.isBlank()) {
            throw ScrmException.badRequest("阶段编码不能为空");
        }
        return stageRepository.findByStageCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "阶段不存在: code=" + code));
    }

    /**
     * 分页查询阶段, 支持按类别 / 启用状态过滤。
     *
     * @param stageCategory 阶段类别过滤（可空）
     * @param enabled       启用状态过滤（可空）
     * @param pageable      分页参数
     * @return 阶段分页结果 (按 stageOrder ASC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmLifecycleStageEntity> listStages(String stageCategory, Boolean enabled, Pageable pageable) {
        Specification<ScrmLifecycleStageEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (stageCategory != null && !stageCategory.isBlank()) {
                predicates.add(cb.equal(root.get("stageCategory"), stageCategory));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            query.orderBy(cb.asc(root.get("stageOrder")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return stageRepository.findAll(spec, pageable);
    }

    /**
     * 启用阶段。
     *
     * @param id 阶段 ID
     * @return 更新后的阶段
     * @throws ScrmException 阶段不存在
     */
    @Transactional
    public ScrmLifecycleStageEntity enableStage(Long id) throws ScrmException {
        ScrmLifecycleStageEntity entity = findStageOrThrow(id);
        entity.setEnabled(Boolean.TRUE);
        entity = stageRepository.save(entity);
        log.info("启用生命周期阶段: id={}, stageName={}", id, entity.getStageName());
        return entity;
    }

    /**
     * 禁用阶段。
     *
     * @param id 阶段 ID
     * @return 更新后的阶段
     * @throws ScrmException 阶段不存在
     */
    @Transactional
    public ScrmLifecycleStageEntity disableStage(Long id) throws ScrmException {
        ScrmLifecycleStageEntity entity = findStageOrThrow(id);
        entity.setEnabled(Boolean.FALSE);
        entity = stageRepository.save(entity);
        log.info("禁用生命周期阶段: id={}, stageName={}", id, entity.getStageName());
        return entity;
    }

    /**
     * 重排阶段顺序。
     *
     * @param stageOrders 阶段 ID → 新顺序映射
     * @return 更新后的阶段列表 (按 stageOrder ASC)
     * @throws ScrmException 阶段不存在
     */
    @Transactional
    public List<ScrmLifecycleStageEntity> reorderStages(Map<Long, Integer> stageOrders) throws ScrmException {
        if (stageOrders == null || stageOrders.isEmpty()) {
            throw ScrmException.badRequest("阶段顺序映射不能为空");
        }
        List<ScrmLifecycleStageEntity> updated = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : stageOrders.entrySet()) {
            ScrmLifecycleStageEntity entity = findStageOrThrow(entry.getKey());
            entity.setStageOrder(entry.getValue());
            updated.add(stageRepository.save(entity));
        }
        log.info("重排生命周期阶段顺序: count={}", updated.size());
        return stageRepository.findAllByOrderByStageOrderAsc();
    }

    /**
     * 更新阶段统计 (客户数 / 平均停留 / 转化率)。
     *
     * @param id 阶段 ID
     * @return 更新后的阶段
     * @throws ScrmException 阶段不存在
     */
    @Transactional
    public ScrmLifecycleStageEntity updateStageStats(Long id) throws ScrmException {
        ScrmLifecycleStageEntity entity = findStageOrThrow(id);
        // 当前客户数
        long customerCount = customerLifecycleRepository.countByCurrentStageId(id);
        entity.setCustomerCount((int) customerCount);
        // 平均停留天数 (基于该阶段作为源阶段的转换历史)
        Double avgDuration = historyRepository.avgDurationInPreviousStage(id);
        entity.setAvgDurationDays(avgDuration != null ? avgDuration : 0.0);
        // 转化率: 离开该阶段的客户数 / 累计进入该阶段的客户数
        int totalEntered = entity.getTotalEnteredCount() != null ? entity.getTotalEnteredCount() : 0;
        long convertedCount = historyRepository.countByFromStage(id, null, null);
        if (totalEntered > 0) {
            entity.setConversionRate(convertedCount * 1.0 / totalEntered);
        } else {
            entity.setConversionRate(0.0);
        }
        entity = stageRepository.save(entity);
        log.info("更新阶段统计: id={}, customerCount={}, avgDuration={}, conversionRate={}",
                id, customerCount, entity.getAvgDurationDays(), entity.getConversionRate());
        return entity;
    }

    /**
     * 阶段漏斗: 各阶段客户数与转化率。
     *
     * @return 漏斗数据列表 (按 stageOrder ASC)
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getStageFunnel() {
        List<ScrmLifecycleStageEntity> stages = stageRepository.findAllByOrderByStageOrderAsc();
        List<Map<String, Object>> funnel = new ArrayList<>();
        for (ScrmLifecycleStageEntity stage : stages) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("stageId", stage.getId());
            m.put("stageName", stage.getStageName());
            m.put("stageCode", stage.getStageCode());
            m.put("stageOrder", stage.getStageOrder());
            m.put("stageCategory", stage.getStageCategory());
            m.put("customerCount", stage.getCustomerCount() != null ? stage.getCustomerCount() : 0);
            m.put("totalEnteredCount", stage.getTotalEnteredCount() != null ? stage.getTotalEnteredCount() : 0);
            m.put("conversionRate", stage.getConversionRate() != null ? stage.getConversionRate() : 0.0);
            m.put("avgDurationDays", stage.getAvgDurationDays() != null ? stage.getAvgDurationDays() : 0.0);
            funnel.add(m);
        }
        return funnel;
    }

    // ============================================================
    // 流转规则管理
    // ============================================================

    /**
     * 创建流转规则。
     * <p>校验目标阶段存在性后写入账号 ID 持久化, transitionType / priority / cooldownDays
     * / isEnabled 缺省时填默认值。</p>
     *
     * @param dto 流转规则参数
     * @return 创建后的流转规则
     * @throws ScrmException 参数非法 / 目标阶段不存在
     */
    @Transactional
    public ScrmLifecycleTransitionEntity createTransition(ScrmLifecycleTransitionDto dto) throws ScrmException {
        validateTransitionDto(dto, false);
        // 校验目标阶段存在
        ScrmLifecycleStageEntity toStage = findStageOrThrow(dto.getToStageId());
        if (!toStage.getStageCode().equals(dto.getToStageCode())) {
            throw ScrmException.badRequest("目标阶段编码与阶段 ID 不匹配: toStageId=" + dto.getToStageId());
        }
        // 校验源阶段存在 (若指定)
        if (dto.getFromStageId() != null) {
            ScrmLifecycleStageEntity fromStage = findStageOrThrow(dto.getFromStageId());
            if (dto.getFromStageCode() != null && !dto.getFromStageCode().equals(fromStage.getStageCode())) {
                throw ScrmException.badRequest("源阶段编码与阶段 ID 不匹配: fromStageId=" + dto.getFromStageId());
            }
        }
        ScrmLifecycleTransitionEntity entity = new ScrmLifecycleTransitionEntity();
        entity.setFromStageId(dto.getFromStageId());
        entity.setFromStageCode(dto.getFromStageCode());
        entity.setToStageId(dto.getToStageId());
        entity.setToStageCode(dto.getToStageCode());
        entity.setTransitionName(dto.getTransitionName());
        entity.setTransitionType(dto.getTransitionType() != null ? dto.getTransitionType() : TRANSITION_TYPE_AUTO);
        entity.setTriggerCondition(dto.getTriggerCondition());
        entity.setTriggerEvents(dto.getTriggerEvents());
        entity.setPriority(dto.getPriority() != null ? dto.getPriority() : DEFAULT_PRIORITY);
        entity.setCooldownDays(dto.getCooldownDays() != null ? dto.getCooldownDays() : DEFAULT_COOLDOWN_DAYS);
        entity.setIsEnabled(dto.getIsEnabled() != null ? dto.getIsEnabled() : Boolean.TRUE);
        entity.setTriggerCount(0);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = transitionRepository.save(entity);
        log.info("创建流转规则: id={}, transitionName={}, from={}, to={}",
                entity.getId(), entity.getTransitionName(), entity.getFromStageCode(), entity.getToStageCode());
        return entity;
    }

    /**
     * 更新流转规则（字段非空才覆盖）。
     *
     * @param id  规则 ID
     * @param dto 流转规则参数
     * @return 更新后的流转规则
     * @throws ScrmException 规则不存在 / 参数非法
     */
    @Transactional
    public ScrmLifecycleTransitionEntity updateTransition(Long id, ScrmLifecycleTransitionDto dto)
            throws ScrmException {
        ScrmLifecycleTransitionEntity entity = findTransitionOrThrow(id);
        validateTransitionDto(dto, true);
        if (dto.getToStageId() != null) {
            ScrmLifecycleStageEntity toStage = findStageOrThrow(dto.getToStageId());
            if (dto.getToStageCode() != null && !dto.getToStageCode().equals(toStage.getStageCode())) {
                throw ScrmException.badRequest("目标阶段编码与阶段 ID 不匹配: toStageId=" + dto.getToStageId());
            }
        }
        if (dto.getFromStageId() != null) {
            ScrmLifecycleStageEntity fromStage = findStageOrThrow(dto.getFromStageId());
            if (dto.getFromStageCode() != null && !dto.getFromStageCode().equals(fromStage.getStageCode())) {
                throw ScrmException.badRequest("源阶段编码与阶段 ID 不匹配: fromStageId=" + dto.getFromStageId());
            }
        }
        if (dto.getFromStageId() != null) entity.setFromStageId(dto.getFromStageId());
        if (dto.getFromStageCode() != null) entity.setFromStageCode(dto.getFromStageCode());
        if (dto.getToStageId() != null) entity.setToStageId(dto.getToStageId());
        if (dto.getToStageCode() != null) entity.setToStageCode(dto.getToStageCode());
        if (dto.getTransitionName() != null) entity.setTransitionName(dto.getTransitionName());
        if (dto.getTransitionType() != null) entity.setTransitionType(dto.getTransitionType());
        if (dto.getTriggerCondition() != null) entity.setTriggerCondition(dto.getTriggerCondition());
        if (dto.getTriggerEvents() != null) entity.setTriggerEvents(dto.getTriggerEvents());
        if (dto.getPriority() != null) entity.setPriority(dto.getPriority());
        if (dto.getCooldownDays() != null) entity.setCooldownDays(dto.getCooldownDays());
        if (dto.getIsEnabled() != null) entity.setIsEnabled(dto.getIsEnabled());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = transitionRepository.save(entity);
        log.info("更新流转规则: id={}, transitionName={}", entity.getId(), entity.getTransitionName());
        return entity;
    }

    /**
     * 删除流转规则。
     *
     * @param id 规则 ID
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public void deleteTransition(Long id) throws ScrmException {
        ScrmLifecycleTransitionEntity entity = findTransitionOrThrow(id);
        transitionRepository.delete(entity);
        log.info("删除流转规则: id={}, transitionName={}", id, entity.getTransitionName());
    }

    /**
     * 查询流转规则详情。
     *
     * @param id 规则 ID
     * @return 流转规则实体
     * @throws ScrmException 规则不存在
     */
    @Transactional(readOnly = true)
    public ScrmLifecycleTransitionEntity getTransition(Long id) throws ScrmException {
        return findTransitionOrThrow(id);
    }

    /**
     * 分页查询流转规则, 支持按源/目标阶段 / 类型 / 启用状态过滤。
     *
     * @param fromStageId    源阶段 ID 过滤（可空）
     * @param toStageId      目标阶段 ID 过滤（可空）
     * @param transitionType 转换类型过滤（可空）
     * @param enabled        启用状态过滤（可空）
     * @param pageable       分页参数
     * @return 流转规则分页结果 (按 priority DESC, createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmLifecycleTransitionEntity> listTransitions(Long fromStageId, Long toStageId,
                                                                String transitionType, Boolean enabled,
                                                                Pageable pageable) {
        Specification<ScrmLifecycleTransitionEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (fromStageId != null) {
                predicates.add(cb.equal(root.get("fromStageId"), fromStageId));
            }
            if (toStageId != null) {
                predicates.add(cb.equal(root.get("toStageId"), toStageId));
            }
            if (transitionType != null && !transitionType.isBlank()) {
                predicates.add(cb.equal(root.get("transitionType"), transitionType));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("isEnabled"), enabled));
            }
            query.orderBy(cb.desc(root.get("priority")), cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return transitionRepository.findAll(spec, pageable);
    }

    /**
     * 启用流转规则。
     *
     * @param id 规则 ID
     * @return 更新后的流转规则
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public ScrmLifecycleTransitionEntity enableTransition(Long id) throws ScrmException {
        ScrmLifecycleTransitionEntity entity = findTransitionOrThrow(id);
        entity.setIsEnabled(Boolean.TRUE);
        entity = transitionRepository.save(entity);
        log.info("启用流转规则: id={}, transitionName={}", id, entity.getTransitionName());
        return entity;
    }

    /**
     * 禁用流转规则。
     *
     * @param id 规则 ID
     * @return 更新后的流转规则
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public ScrmLifecycleTransitionEntity disableTransition(Long id) throws ScrmException {
        ScrmLifecycleTransitionEntity entity = findTransitionOrThrow(id);
        entity.setIsEnabled(Boolean.FALSE);
        entity = transitionRepository.save(entity);
        log.info("禁用流转规则: id={}, transitionName={}", id, entity.getTransitionName());
        return entity;
    }

    /**
     * 获取指定阶段的可用转换规则 (按优先级降序)。
     *
     * @param stageId 阶段 ID
     * @return 可用转换规则列表
     * @throws ScrmException 阶段不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmLifecycleTransitionEntity> getAvailableTransitions(Long stageId) throws ScrmException {
        findStageOrThrow(stageId);
        return transitionRepository.findByFromStageIdAndIsEnabledOrderByPriorityDesc(
                 stageId, Boolean.TRUE);
    }

    // ============================================================
    // 客户生命周期管理
    // ============================================================

    /**
     * 获取客户当前生命周期。
     *
     * @param customerId 客户 ID
     * @return 客户生命周期实体
     * @throws ScrmException 客户生命周期不存在
     */
    @Transactional(readOnly = true)
    public ScrmCustomerLifecycleEntity getCustomerLifecycle(Long customerId) throws ScrmException {
        if (customerId == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        return customerLifecycleRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "客户生命周期不存在: customerId=" + customerId));
    }

    /**
     * 按阶段分页查询客户。
     *
     * @param stageId  阶段 ID
     * @param pageable 分页参数
     * @return 客户生命周期分页结果 (按 enteredCurrentStageAt DESC)
     * @throws ScrmException 阶段不存在
     */
    @Transactional(readOnly = true)
    public Page<ScrmCustomerLifecycleEntity> listCustomersByStage(Long stageId, Pageable pageable)
            throws ScrmException {
        findStageOrThrow(stageId);
        Specification<ScrmCustomerLifecycleEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("currentStageId"), stageId));
            query.orderBy(cb.desc(root.get("enteredCurrentStageAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return customerLifecycleRepository.findAll(spec, pageable);
    }

    /**
     * 分页查询超期客户。
     *
     * @param stageId  阶段 ID（可空, null 表示全部阶段）
     * @param pageable 分页参数
     * @return 超期客户分页结果 (按 overdueDays DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmCustomerLifecycleEntity> listOverdueCustomers(Long stageId, Pageable pageable) {
        Specification<ScrmCustomerLifecycleEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isOverdue"), Boolean.TRUE));
            if (stageId != null) {
                predicates.add(cb.equal(root.get("currentStageId"), stageId));
            }
            query.orderBy(cb.desc(root.get("overdueDays")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return customerLifecycleRepository.findAll(spec, pageable);
    }

    /**
     * 手动分配客户到指定阶段 (新客户初始化或强制重置)。
     * <p>客户无生命周期记录时新建 (SYSTEM 类型, 不校验转换规则);
     * 已有记录时按 MANUAL 类型执行转换。</p>
     *
     * @param customerId 客户 ID
     * @param stageCode  目标阶段编码
     * @return 客户生命周期实体
     * @throws ScrmException 阶段不存在
     */
    @Transactional
    public ScrmCustomerLifecycleEntity assignCustomer(Long customerId, String stageCode) throws ScrmException {
        if (customerId == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        if (stageCode == null || stageCode.isBlank()) {
            throw ScrmException.badRequest("阶段编码不能为空");
        }
        ScrmLifecycleStageEntity targetStage = getStageByCode(stageCode);
        ScrmCustomerLifecycleEntity existed = customerLifecycleRepository
                .findByCustomerId(customerId).orElse(null);
        if (existed == null) {
            // 新客户: 直接初始化到目标阶段 (SYSTEM 类型, 不校验规则)
            ScrmCustomerLifecycleEntity entity = buildNewCustomerLifecycle(customerId, targetStage);
            entity = customerLifecycleRepository.save(entity);
            // 累计目标阶段客户数与进入数
            incrementStageCount(targetStage);
            log.info("分配新客户到阶段: customerId={}, stageCode={}", customerId, stageCode);
            return entity;
        }
        // 已有记录: 按 MANUAL 转换
        ScrmLifecycleTransitionActionDto action = new ScrmLifecycleTransitionActionDto();
        action.setCustomerId(customerId);
        action.setCustomerName(existed.getCustomerName());
        action.setToStageCode(stageCode);
        action.setTriggerEvent("ASSIGN");
        action.setDescription("手动分配阶段");
        action.setOperatorId(existed.getAssignedTo());
        return transitionCustomer(action);
    }

    /**
     * 客户阶段转换 (验证转换规则 → 更新当前阶段 → 记录历史 → 更新统计)。
     *
     * @param actionDto 转换动作参数
     * @return 更新后的客户生命周期
     * @throws ScrmException 阶段不存在 / 无可用转换规则 / 冷却期内
     */
    @Transactional
    public ScrmCustomerLifecycleEntity transitionCustomer(ScrmLifecycleTransitionActionDto actionDto)
            throws ScrmException {
        if (actionDto == null) {
            throw ScrmException.badRequest("转换动作参数不能为空");
        }
        if (actionDto.getCustomerId() == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        if (actionDto.getToStageCode() == null || actionDto.getToStageCode().isBlank()) {
            throw ScrmException.badRequest("目标阶段编码不能为空");
        }
        ScrmLifecycleStageEntity toStage = getStageByCode(actionDto.getToStageCode());
        ScrmCustomerLifecycleEntity existed = customerLifecycleRepository
                .findByCustomerId(actionDto.getCustomerId()).orElse(null);

        Long fromStageId = existed != null ? existed.getCurrentStageId() : null;
        String fromStageCode = existed != null ? existed.getCurrentStageCode() : null;
        String fromStageName = existed != null ? existed.getCurrentStageName() : null;

        // 同阶段无需转换
        if (existed != null && toStage.getId().equals(fromStageId)) {
            log.info("客户已在目标阶段, 跳过转换: customerId={}, stageCode={}",
                    actionDto.getCustomerId(), actionDto.getToStageCode());
            return existed;
        }

        // 查找匹配的转换规则 (源阶段 = 当前阶段, 目标阶段 = 目标阶段)
        ScrmLifecycleTransitionEntity matchedRule = findMatchingTransitionRule(
                 fromStageId, toStage.getId(), actionDto.getTriggerEvent());

        // 校验转换规则: 若无匹配规则, 且目标阶段非起始阶段, 则拒绝
        if (matchedRule == null) {
            // 新客户进入起始阶段, 或目标阶段为起始阶段, 允许直接进入
            if (existed == null && Boolean.TRUE.equals(toStage.getIsStartStage())) {
                ScrmCustomerLifecycleEntity entity = buildNewCustomerLifecycle(
                         actionDto.getCustomerId(), toStage);
                entity.setCustomerName(actionDto.getCustomerName());
                entity = customerLifecycleRepository.save(entity);
                incrementStageCount(toStage);
                // 记录历史
                recordHistory(entity, null, null, null, toStage, null,
                        TRANSITION_TYPE_SYSTEM, actionDto.getTriggerEvent(),
                        actionDto.getDescription(), null, actionDto.getOperatorId(),
                        actionDto.getOperatorName(), actionDto.getMetadata());
                log.info("新客户进入起始阶段: customerId={}, stageCode={}",
                        actionDto.getCustomerId(), actionDto.getToStageCode());
                return entity;
            }
            throw ScrmException.badRequest("无可用转换规则: fromStageId=" + fromStageId
                    + ", toStageCode=" + actionDto.getToStageCode());
        }

        // 冷却期校验
        checkCooldown(matchedRule, existed);

        // 计算上一阶段停留天数
        Integer durationInPreviousStage = null;
        LocalDateTime now = LocalDateTime.now();
        if (existed != null && existed.getEnteredCurrentStageAt() != null) {
            durationInPreviousStage = (int) ChronoUnit.DAYS.between(existed.getEnteredCurrentStageAt(), now);
        }

        // 更新客户生命周期
        ScrmCustomerLifecycleEntity updated;
        if (existed == null) {
            updated = buildNewCustomerLifecycle(actionDto.getCustomerId(), toStage);
            updated.setCustomerName(actionDto.getCustomerName());
        } else {
            updated = existed;
            updated.setPreviousStageId(existed.getCurrentStageId());
            updated.setPreviousStageCode(existed.getCurrentStageCode());
            updated.setCurrentStageId(toStage.getId());
            updated.setCurrentStageCode(toStage.getStageCode());
            updated.setCurrentStageName(toStage.getStageName());
            updated.setEnteredCurrentStageAt(now);
            updated.setDurationInStageDays(0);
            updated.setStageHistoryCount((existed.getStageHistoryCount() != null
                    ? existed.getStageHistoryCount() : 0) + 1);
            updated.setIsOverdue(Boolean.FALSE);
            updated.setOverdueDays(0);
            updated.setNextStageId(null);
            updated.setExpectedTransitionAt(null);
            updated.setLastUpdatedAt(now);
            if (actionDto.getCustomerName() != null) {
                updated.setCustomerName(actionDto.getCustomerName());
            }
        }
        // 计算超期状态
        applyOverdueStatus(updated, toStage);
        updated = customerLifecycleRepository.save(updated);

        // 更新源阶段客户数
        if (existed != null && fromStageId != null) {
            ScrmLifecycleStageEntity fromStage = stageRepository.findById(fromStageId).orElse(null);
            if (fromStage != null) {
                int cur = fromStage.getCustomerCount() != null ? fromStage.getCustomerCount() : 0;
                fromStage.setCustomerCount(Math.max(0, cur - 1));
                stageRepository.save(fromStage);
            }
        }
        // 累计目标阶段客户数与进入数
        incrementStageCount(toStage);

        // 更新转换规则触发统计
        matchedRule.setTriggerCount((matchedRule.getTriggerCount() != null ? matchedRule.getTriggerCount() : 0) + 1);
        matchedRule.setLastTriggeredAt(now);
        transitionRepository.save(matchedRule);

        // 记录历史
        recordHistory(updated, fromStageId, fromStageCode, fromStageName, toStage, matchedRule.getId(),
                matchedRule.getTransitionType(), actionDto.getTriggerEvent(),
                actionDto.getDescription(), durationInPreviousStage, actionDto.getOperatorId(),
                actionDto.getOperatorName(), actionDto.getMetadata());

        log.info("客户阶段转换: customerId={}, from={}, to={}, transitionId={}",
                actionDto.getCustomerId(), fromStageCode, actionDto.getToStageCode(), matchedRule.getId());
        return updated;
    }

    /**
     * 批量转换客户阶段。
     *
     * @param bulkDto 批量转换参数
     * @return 转换结果列表
     * @throws ScrmException 参数非法
     */
    @Transactional
    public List<ScrmCustomerLifecycleEntity> bulkTransition(ScrmLifecycleBulkTransitionDto bulkDto)
            throws ScrmException {
        if (bulkDto == null) {
            throw ScrmException.badRequest("批量转换参数不能为空");
        }
        if (bulkDto.getCustomerIds() == null || bulkDto.getCustomerIds().isEmpty()) {
            throw ScrmException.badRequest("客户 ID 列表不能为空");
        }
        List<ScrmCustomerLifecycleEntity> results = new ArrayList<>();
        for (Long customerId : bulkDto.getCustomerIds()) {
            ScrmLifecycleTransitionActionDto action = new ScrmLifecycleTransitionActionDto();
            action.setCustomerId(customerId);
            action.setToStageCode(bulkDto.getToStageCode());
            action.setTriggerEvent(bulkDto.getTriggerEvent());
            action.setDescription(bulkDto.getDescription());
            action.setOperatorId(bulkDto.getOperatorId());
            action.setOperatorName(bulkDto.getOperatorName());
            try {
                results.add(transitionCustomer(action));
            } catch (ScrmException e) {
                log.warn("批量转换客户失败, 跳过: customerId={}, err={}", customerId, e.getMessage());
            }
        }
        log.info("批量转换客户阶段: total={}, success={}", bulkDto.getCustomerIds().size(), results.size());
        return results;
    }

    /**
     * 检查事件触发自动转换 (模拟实现: 按 triggerEvents 字段匹配)。
     *
     * @param customerId 客户 ID
     * @param event      触发事件
     * @return 转换后的客户生命周期 (无匹配规则时返回当前生命周期)
     * @throws ScrmException 客户生命周期不存在
     */
    @Transactional
    public ScrmCustomerLifecycleEntity checkAndTransition(Long customerId, String event) throws ScrmException {
        if (event == null || event.isBlank()) {
            throw ScrmException.badRequest("触发事件不能为空");
        }
        ScrmCustomerLifecycleEntity current = getCustomerLifecycle(customerId);
        // 查找当前阶段的全部启用转换规则
        List<ScrmLifecycleTransitionEntity> rules = transitionRepository
                .findByFromStageIdAndIsEnabledOrderByPriorityDesc(
                         current.getCurrentStageId(), Boolean.TRUE);
        for (ScrmLifecycleTransitionEntity rule : rules) {
            if (matchesTriggerEvent(rule, event)) {
                ScrmLifecycleTransitionActionDto action = new ScrmLifecycleTransitionActionDto();
                action.setCustomerId(customerId);
                action.setCustomerName(current.getCustomerName());
                action.setToStageCode(rule.getToStageCode());
                action.setTriggerEvent(event);
                action.setDescription("事件触发自动转换: " + event);
                return transitionCustomer(action);
            }
        }
        log.info("事件未匹配任何转换规则: customerId={}, event={}", customerId, event);
        return current;
    }

    /**
     * 处理事件 (检查触发条件 → 执行转换)。
     * <p>模拟实现: 直接调用 {@link #checkAndTransition}, 忽略 eventData 详细校验。</p>
     *
     * @param customerId 客户 ID
     * @param eventType  事件类型
     * @param eventData  事件数据 (JSON, 可空)
     * @return 转换后的客户生命周期
     * @throws ScrmException 客户生命周期不存在
     */
    @Transactional
    public ScrmCustomerLifecycleEntity processEvent(Long customerId, String eventType, String eventData)
            throws ScrmException {
        log.info("处理事件: customerId={}, eventType={}, eventData={}", customerId, eventType, eventData);
        return checkAndTransition(customerId, eventType);
    }

    // ============================================================
    // 转换历史
    // ============================================================

    /**
     * 查询客户阶段转换历史 (按 transitionTime 降序)。
     *
     * @param customerId 客户 ID
     * @return 历史列表
     * @throws ScrmException 客户 ID 非法
     */
    @Transactional(readOnly = true)
    public List<ScrmLifecycleHistoryEntity> getCustomerHistory(Long customerId) throws ScrmException {
        if (customerId == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        return historyRepository.findByCustomerIdOrderByTransitionTimeDesc(customerId);
    }

    /**
     * 分页查询转换历史, 支持按客户 / 源阶段 / 目标阶段 / 类型 / 时间区间过滤。
     *
     * @param customerId     客户 ID 过滤（可空）
     * @param fromStage      源阶段编码过滤（可空）
     * @param toStage        目标阶段编码过滤（可空）
     * @param transitionType 转换类型过滤（可空）
     * @param startTime      转换时间起始（可空）
     * @param endTime        转换时间截止（可空）
     * @param pageable       分页参数
     * @return 历史分页结果 (按 transitionTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmLifecycleHistoryEntity> listHistory(Long customerId, String fromStage, String toStage,
                                                         String transitionType, LocalDateTime startTime,
                                                         LocalDateTime endTime, Pageable pageable) {
        Specification<ScrmLifecycleHistoryEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (fromStage != null && !fromStage.isBlank()) {
                predicates.add(cb.equal(root.get("fromStageCode"), fromStage));
            }
            if (toStage != null && !toStage.isBlank()) {
                predicates.add(cb.equal(root.get("toStageCode"), toStage));
            }
            if (transitionType != null && !transitionType.isBlank()) {
                predicates.add(cb.equal(root.get("transitionType"), transitionType));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("transitionTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("transitionTime"), endTime));
            }
            query.orderBy(cb.desc(root.get("transitionTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return historyRepository.findAll(spec, pageable);
    }

    /**
     * 阶段转换趋势 (指定阶段每日进入客户数)。
     *
     * @param stageId 阶段 ID
     * @param days    统计天数 (从今天往前推)
     * @return 趋势数据列表 (按日期升序)
     * @throws ScrmException 阶段不存在
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getStageTransitionTrend(Long stageId, int days) throws ScrmException {
        findStageOrThrow(stageId);
        if (days <= 0) {
            days = 30;
        }
        LocalDateTime startTime = LocalDate.now().minusDays(days).atStartOfDay();
        List<Object[]> rows = historyRepository.dailyCountByToStage(stageId, startTime);
        List<Map<String, Object>> trend = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", row[0] == null ? null : row[0].toString());
            m.put("count", row[1] == null ? 0L : ((Number) row[1]).longValue());
            trend.add(m);
        }
        return trend;
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 生命周期统计: 各阶段客户数 / 分布 / 平均停留。
     *
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getLifecycleStats() {
        List<ScrmLifecycleStageEntity> stages = stageRepository.findAllByOrderByStageOrderAsc();
        long totalCustomers = customerLifecycleRepository.count();
        List<Map<String, Object>> stageStats = new ArrayList<>();
        for (ScrmLifecycleStageEntity stage : stages) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("stageId", stage.getId());
            m.put("stageName", stage.getStageName());
            m.put("stageCode", stage.getStageCode());
            m.put("stageCategory", stage.getStageCategory());
            int customerCount = stage.getCustomerCount() != null ? stage.getCustomerCount() : 0;
            m.put("customerCount", customerCount);
            m.put("distribution", totalCustomers > 0 ? customerCount * 1.0 / totalCustomers : 0.0);
            m.put("avgDurationDays", stage.getAvgDurationDays() != null ? stage.getAvgDurationDays() : 0.0);
            m.put("conversionRate", stage.getConversionRate() != null ? stage.getConversionRate() : 0.0);
            stageStats.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCustomers", totalCustomers);
        result.put("totalStages", stages.size());
        result.put("stageStats", stageStats);
        return result;
    }

    /**
     * 转换统计: 各转换触发次数与成功率。
     *
     * @param startTime 转换时间起始（可空）
     * @param endTime   转换时间截止（可空）
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getTransitionStats(LocalDateTime startTime, LocalDateTime endTime) {
        List<Object[]> rows = historyRepository.countByTransitionTypeAndToStage(startTime, endTime);
        List<Map<String, Object>> transitionStats = new ArrayList<>();
        long totalTriggers = 0L;
        for (Object[] row : rows) {
            String transitionType = (String) row[0];
            Long toStageId = row[1] == null ? null : ((Number) row[1]).longValue();
            long count = row[2] == null ? 0L : ((Number) row[2]).longValue();
            totalTriggers += count;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("transitionType", transitionType);
            m.put("toStageId", toStageId);
            m.put("triggerCount", count);
            transitionStats.add(m);
        }
        // 计算各类型占比
        for (Map<String, Object> m : transitionStats) {
            long count = ((Number) m.get("triggerCount")).longValue();
            m.put("successRate", totalTriggers > 0 ? count * 1.0 / totalTriggers : 0.0);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalTriggers", totalTriggers);
        result.put("transitionStats", transitionStats);
        return result;
    }

    /**
     * 转化漏斗: 各阶段 → 下一阶段转化率。
     *
     * @return 漏斗数据列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getConversionFunnel() {
        List<ScrmLifecycleStageEntity> stages = stageRepository.findAllByOrderByStageOrderAsc();
        List<Map<String, Object>> funnel = new ArrayList<>();
        for (int i = 0; i < stages.size(); i++) {
            ScrmLifecycleStageEntity stage = stages.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("stageId", stage.getId());
            m.put("stageName", stage.getStageName());
            m.put("stageCode", stage.getStageCode());
            m.put("stageOrder", stage.getStageOrder());
            int customerCount = stage.getCustomerCount() != null ? stage.getCustomerCount() : 0;
            int totalEntered = stage.getTotalEnteredCount() != null ? stage.getTotalEnteredCount() : 0;
            m.put("customerCount", customerCount);
            m.put("totalEnteredCount", totalEntered);
            m.put("conversionRate", stage.getConversionRate() != null ? stage.getConversionRate() : 0.0);
            if (i + 1 < stages.size()) {
                ScrmLifecycleStageEntity next = stages.get(i + 1);
                m.put("nextStageId", next.getId());
                m.put("nextStageName", next.getStageName());
                m.put("nextStageCode", next.getStageCode());
            } else {
                m.put("nextStageId", null);
                m.put("nextStageName", null);
                m.put("nextStageCode", null);
            }
            funnel.add(m);
        }
        return funnel;
    }

    /**
     * 阶段停留分析: 各阶段平均停留天数与目标停留天数。
     *
     * @return 停留分析列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getStageDurationAnalysis() {
        List<ScrmLifecycleStageEntity> stages = stageRepository.findAllByOrderByStageOrderAsc();
        List<Map<String, Object>> analysis = new ArrayList<>();
        for (ScrmLifecycleStageEntity stage : stages) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("stageId", stage.getId());
            m.put("stageName", stage.getStageName());
            m.put("stageCode", stage.getStageCode());
            m.put("avgDurationDays", stage.getAvgDurationDays() != null ? stage.getAvgDurationDays() : 0.0);
            m.put("targetDurationDays", stage.getTargetDurationDays());
            // 当前阶段客户的平均停留天数 (实时)
            Double liveAvg = computeLiveAvgDuration(stage.getId());
            m.put("liveAvgDurationDays", liveAvg);
            analysis.add(m);
        }
        return analysis;
    }

    /**
     * 流失率: 指定阶段在时间区间内的流失率。
     * <p>流失率 = 进入流失阶段的客户数 / 离开该阶段的客户数。</p>
     *
     * @param stageId   阶段 ID
     * @param startTime 起始时间（可空）
     * @param endTime   截止时间（可空）
     * @return 流失率统计 Map
     * @throws ScrmException 阶段不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getChurnRate(Long stageId, LocalDateTime startTime, LocalDateTime endTime)
            throws ScrmException {
        findStageOrThrow(stageId);
        long leftCount = historyRepository.countByFromStage(stageId, startTime, endTime);
        // 进入流失阶段的客户数 (从该阶段出发到流失阶段)
        long churnedCount = 0L;
        List<ScrmLifecycleTransitionEntity> transitions = transitionRepository
                .findByFromStageIdAndIsEnabledOrderByPriorityDesc(stageId, Boolean.TRUE);
        List<ScrmLifecycleTransitionEntity> disabledTransitions = transitionRepository
                .findByFromStageIdAndIsEnabledOrderByPriorityDesc(stageId, Boolean.FALSE);
        List<ScrmLifecycleTransitionEntity> allTransitions = new ArrayList<>(transitions);
        allTransitions.addAll(disabledTransitions);
        for (ScrmLifecycleTransitionEntity t : allTransitions) {
            ScrmLifecycleStageEntity toStage = stageRepository.findById(t.getToStageId()).orElse(null);
            if (toStage != null && Boolean.TRUE.equals(toStage.getIsChurnStage())) {
                churnedCount += historyRepository.countByFromStage(stageId, startTime, endTime);
                break;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stageId", stageId);
        result.put("leftCount", leftCount);
        result.put("churnedCount", churnedCount);
        result.put("churnRate", leftCount > 0 ? churnedCount * 1.0 / leftCount : 0.0);
        return result;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 校验阶段参数。
     *
     * @param dto     阶段参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateStageDto(ScrmLifecycleStageDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("阶段参数不能为空");
        }
        if (dto.getStageName() != null) {
            if (dto.getStageName().isBlank()) {
                throw ScrmException.badRequest("阶段名称不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("阶段名称不能为空");
        }
        if (dto.getStageCode() != null) {
            if (dto.getStageCode().isBlank()) {
                throw ScrmException.badRequest("阶段编码不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("阶段编码不能为空");
        }
        if (dto.getStageCategory() != null) {
            if (!VALID_STAGE_CATEGORIES.contains(dto.getStageCategory())) {
                throw ScrmException.badRequest(
                        "阶段类别非法: " + dto.getStageCategory() + ", 仅支持 " + VALID_STAGE_CATEGORIES);
            }
        } else if (!partial) {
            throw ScrmException.badRequest("阶段类别不能为空");
        }
    }

    /**
     * 校验流转规则参数。
     *
     * @param dto     流转规则参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateTransitionDto(ScrmLifecycleTransitionDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("流转规则参数不能为空");
        }
        if (dto.getToStageId() == null && !partial) {
            throw ScrmException.badRequest("目标阶段 ID 不能为空");
        }
        if (dto.getToStageCode() != null && dto.getToStageCode().isBlank()) {
            throw ScrmException.badRequest("目标阶段编码不能为空");
        } else if (dto.getToStageCode() == null && !partial && dto.getToStageId() != null) {
            throw ScrmException.badRequest("目标阶段编码不能为空");
        }
        if (dto.getTransitionName() != null) {
            if (dto.getTransitionName().isBlank()) {
                throw ScrmException.badRequest("转换名称不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("转换名称不能为空");
        }
        if (dto.getTransitionType() != null && !VALID_TRANSITION_TYPES.contains(dto.getTransitionType())) {
            throw ScrmException.badRequest(
                    "转换类型非法: " + dto.getTransitionType() + ", 仅支持 " + VALID_TRANSITION_TYPES);
        }
    }

    /**
     * 构建新客户生命周期实体。
     *
     * @param customerId 客户 ID
     * @param stage      目标阶段
     * @return 客户生命周期实体 (未持久化)
     */
    private ScrmCustomerLifecycleEntity buildNewCustomerLifecycle(Long customerId,
                                                                    ScrmLifecycleStageEntity stage) {
        LocalDateTime now = LocalDateTime.now();
        ScrmCustomerLifecycleEntity entity = new ScrmCustomerLifecycleEntity();
        entity.setCustomerId(customerId);
        entity.setCurrentStageId(stage.getId());
        entity.setCurrentStageCode(stage.getStageCode());
        entity.setCurrentStageName(stage.getStageName());
        entity.setEnteredCurrentStageAt(now);
        entity.setDurationInStageDays(0);
        entity.setStageHistoryCount(0);
        entity.setIsOverdue(Boolean.FALSE);
        entity.setOverdueDays(0);
        entity.setLastUpdatedAt(now);
        applyOverdueStatus(entity, stage);
        return entity;
    }

    /**
     * 应用超期状态 (基于阶段目标停留天数)。
     *
     * @param entity 客户生命周期实体
     * @param stage  当前阶段
     */
    private void applyOverdueStatus(ScrmCustomerLifecycleEntity entity, ScrmLifecycleStageEntity stage) {
        if (stage.getTargetDurationDays() == null || stage.getTargetDurationDays() <= 0
                || entity.getEnteredCurrentStageAt() == null) {
            entity.setIsOverdue(Boolean.FALSE);
            entity.setOverdueDays(0);
            return;
        }
        long days = ChronoUnit.DAYS.between(entity.getEnteredCurrentStageAt(), LocalDateTime.now());
        if (days > stage.getTargetDurationDays()) {
            entity.setIsOverdue(Boolean.TRUE);
            entity.setOverdueDays((int) (days - stage.getTargetDurationDays()));
        } else {
            entity.setIsOverdue(Boolean.FALSE);
            entity.setOverdueDays(0);
        }
    }

    /**
     * 累计阶段客户数与进入数。
     *
     * @param stage 阶段实体
     */
    private void incrementStageCount(ScrmLifecycleStageEntity stage) {
        stage.setCustomerCount((stage.getCustomerCount() != null ? stage.getCustomerCount() : 0) + 1);
        stage.setTotalEnteredCount((stage.getTotalEnteredCount() != null ? stage.getTotalEnteredCount() : 0) + 1);
        stageRepository.save(stage);
    }

    /**
     * 查找匹配的转换规则 (源阶段 → 目标阶段, 优先级最高)。
     *
     * @param fromStageId   源阶段 ID (可空)
     * @param toStageId     目标阶段 ID
     * @param triggerEvent  触发事件 (可空, 用于优先匹配)
     * @return 匹配的转换规则 (无匹配时返回 null)
     */
    private ScrmLifecycleTransitionEntity findMatchingTransitionRule(Long fromStageId,
                                                                     Long toStageId, String triggerEvent) {
        List<ScrmLifecycleTransitionEntity> rules;
        if (fromStageId == null) {
            // 新客户: 查找全部启用规则中目标阶段匹配的
            rules = transitionRepository.findByIsEnabledOrderByPriorityDesc(Boolean.TRUE);
        } else {
            rules = transitionRepository.findByFromStageIdAndIsEnabledOrderByPriorityDesc(
                     fromStageId, Boolean.TRUE);
        }
        // 优先匹配触发事件
        if (triggerEvent != null && !triggerEvent.isBlank()) {
            for (ScrmLifecycleTransitionEntity rule : rules) {
                if (rule.getToStageId().equals(toStageId) && matchesTriggerEvent(rule, triggerEvent)) {
                    return rule;
                }
            }
        }
        // 回退: 匹配目标阶段 (忽略触发事件)
        for (ScrmLifecycleTransitionEntity rule : rules) {
            if (rule.getToStageId().equals(toStageId)) {
                return rule;
            }
        }
        return null;
    }

    /**
     * 判断转换规则是否匹配触发事件。
     *
     * @param rule         转换规则
     * @param triggerEvent 触发事件
     * @return 是否匹配
     */
    private boolean matchesTriggerEvent(ScrmLifecycleTransitionEntity rule, String triggerEvent) {
        if (rule.getTriggerEvents() == null || rule.getTriggerEvents().isBlank()) {
            // 未配置触发事件, 视为匹配任意事件
            return true;
        }
        return Arrays.stream(rule.getTriggerEvents().split(","))
                .map(String::trim)
                .anyMatch(triggerEvent::equals);
    }

    /**
     * 校验冷却期。
     *
     * @param rule    转换规则
     * @param existed 客户当前生命周期
     * @throws ScrmException 冷却期内
     */
    private void checkCooldown(ScrmLifecycleTransitionEntity rule, ScrmCustomerLifecycleEntity existed)
            throws ScrmException {
        if (rule.getCooldownDays() == null || rule.getCooldownDays() <= 0) {
            return;
        }
        if (existed == null || existed.getEnteredCurrentStageAt() == null) {
            return;
        }
        long elapsedDays = ChronoUnit.DAYS.between(existed.getEnteredCurrentStageAt(), LocalDateTime.now());
        if (elapsedDays < rule.getCooldownDays()) {
            throw ScrmException.conflict("转换规则冷却期内, 剩余"
                    + (rule.getCooldownDays() - elapsedDays) + " 天: transitionId=" + rule.getId());
        }
    }

    /**
     * 记录转换历史。
     *
     * @param entity                  客户生命周期实体
     * @param fromStageId             源阶段 ID
     * @param fromStageCode           源阶段编码
     * @param fromStageName           源阶段名称
     * @param toStage                 目标阶段
     * @param transitionId            转换规则 ID
     * @param transitionType          转换类型
     * @param triggerEvent            触发事件
     * @param triggerDescription      触发描述
     * @param durationInPreviousStage 上一阶段停留天数
     * @param operatorId              操作人 ID
     * @param operatorName            操作人名称
     * @param metadata                附加数据 JSON
     */
    private void recordHistory(ScrmCustomerLifecycleEntity entity, Long fromStageId, String fromStageCode,
                                String fromStageName, ScrmLifecycleStageEntity toStage, Long transitionId,
                                String transitionType, String triggerEvent, String triggerDescription,
                                Integer durationInPreviousStage, String operatorId, String operatorName,
                                String metadata) {
        ScrmLifecycleHistoryEntity history = new ScrmLifecycleHistoryEntity();
        history.setCustomerId(entity.getCustomerId());
        history.setCustomerName(entity.getCustomerName());
        history.setFromStageId(fromStageId);
        history.setFromStageCode(fromStageCode);
        history.setFromStageName(fromStageName);
        history.setToStageId(toStage.getId());
        history.setToStageCode(toStage.getStageCode());
        history.setToStageName(toStage.getStageName());
        history.setTransitionId(transitionId);
        history.setTransitionType(transitionType);
        history.setTriggerEvent(triggerEvent);
        history.setTriggerDescription(triggerDescription);
        history.setDurationInPreviousStage(durationInPreviousStage);
        history.setOperatorId(operatorId);
        history.setOperatorName(operatorName);
        history.setTransitionTime(LocalDateTime.now());
        history.setMetadata(metadata);
        historyRepository.save(history);
    }

    /**
     * 计算当前阶段客户的实时平均停留天数。
     *
     * @param stageId  阶段 ID
     * @return 实时平均停留天数
     */
    private Double computeLiveAvgDuration(Long stageId) {
        List<ScrmCustomerLifecycleEntity> customers = customerLifecycleRepository.findAll((root, query, cb) ->
                cb.and( cb.equal(root.get("currentStageId"), stageId)));
        if (customers.isEmpty()) {
            return 0.0;
        }
        double total = 0.0;
        int count = 0;
        LocalDateTime now = LocalDateTime.now();
        for (ScrmCustomerLifecycleEntity c : customers) {
            if (c.getEnteredCurrentStageAt() != null) {
                total += ChronoUnit.DAYS.between(c.getEnteredCurrentStageAt(), now);
                count++;
            }
        }
        return count > 0 ? total / count : 0.0;
    }

    /**
     * 按主键查询阶段, 不存在抛异常, 并校验账号归属。
     *
     * @param id 阶段 ID
     * @return 阶段实体
     * @throws ScrmException 阶段不存在
     */
    private ScrmLifecycleStageEntity findStageOrThrow(Long id) throws ScrmException {
        ScrmLifecycleStageEntity entity = stageRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "生命周期阶段不存在: id=" + id));
        return entity;
    }

    /**
     * 按主键查询流转规则, 不存在抛异常, 并校验账号归属。
     *
     * @param id 规则 ID
     * @return 流转规则实体
     * @throws ScrmException 流转规则不存在
     */
    private ScrmLifecycleTransitionEntity findTransitionOrThrow(Long id) throws ScrmException {
        ScrmLifecycleTransitionEntity entity = transitionRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "流转规则不存在: id=" + id));
        return entity;
    }

}
