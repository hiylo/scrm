/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPointsRuleService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmPointsRuleDto;
import org.hiylo.scrm.entity.ScrmPointsRuleEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmPointsRuleRepository;
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
 * SCRM 积分规则管理服务 (积分规则子域)。
 * <p>
 * 承载积分规则的增删改查 / 启用禁用 = 按类型 / 触发事件 / 启用状态分页过滤,
 * 并托管规则子域常量、分页默认排序工具 {@link #ensureSort(Pageable, String)}
 * 供账户 / 流水 / 兑换 / 访问兄弟类以包级 static 复用。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmPointsRuleService {

    /** 规则类型: 获取 */
    static final String RULE_TYPE_EARN = "EARN";
    /** 规则类型: 消耗 */
    static final String RULE_TYPE_REDEEM = "REDEEM";

    /** 积分计算类型: 固定 */
    static final String POINTS_TYPE_FIXED = "FIXED";
    /** 积分计算类型: 百分比 */
    static final String POINTS_TYPE_PERCENTAGE = "PERCENTAGE";

    /** 默认启用状态 */
    private static final boolean DEFAULT_ENABLED = true;
    /** 默认积分计算类型 */
    private static final String DEFAULT_POINTS_TYPE = POINTS_TYPE_FIXED;
    /** 默认最少积分 */
    static final int DEFAULT_MIN_POINTS = 0;
    /** 默认触发次数初值 */
    private static final int DEFAULT_TRIGGER_COUNT = 0;

    /** 积分规则数据访问层 */
    private final ScrmPointsRuleRepository ruleRepository;

    /**
     * 创建积分规则。
     * <p>校验参数合法性后写入归属账号 ID 持久化, pointsType / enabled / minPoints / triggerCount
     * 缺省时填默认值。</p>
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmPointsRuleDto createRule(ScrmPointsRuleDto dto) throws ScrmException {
        validateRuleDto(dto, false);
        ScrmPointsRuleEntity entity = new ScrmPointsRuleEntity();
        entity.setRuleName(dto.getRuleName());
        entity.setRuleType(dto.getRuleType());
        entity.setTriggerEvent(dto.getTriggerEvent());
        entity.setPointsValue(dto.getPointsValue());
        entity.setPointsType(dto.getPointsType() != null ? dto.getPointsType() : DEFAULT_POINTS_TYPE);
        entity.setBasisField(dto.getBasisField());
        entity.setDailyLimit(dto.getDailyLimit());
        entity.setMonthlyLimit(dto.getMonthlyLimit());
        entity.setMinPoints(dto.getMinPoints() != null ? dto.getMinPoints() : DEFAULT_MIN_POINTS);
        entity.setMaxPoints(dto.getMaxPoints());
        entity.setDescription(dto.getDescription());
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : DEFAULT_ENABLED);
        entity.setTriggerCount(DEFAULT_TRIGGER_COUNT);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = ruleRepository.save(entity);
        log.info("创建积分规则: id={}, ruleName={}, ruleType={}, triggerEvent={}",
                entity.getId(), entity.getRuleName(), entity.getRuleType(), entity.getTriggerEvent());
        return toRuleDto(entity);
    }

    /**
     * 更新积分规则（字段非空才覆盖）。
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 参数非法
     */
    @Transactional
    public ScrmPointsRuleDto updateRule(Long id, ScrmPointsRuleDto dto) throws ScrmException {
        ScrmPointsRuleEntity entity = findRule(id);
        validateRuleDto(dto, true);
        if (dto.getRuleName() != null) entity.setRuleName(dto.getRuleName());
        if (dto.getRuleType() != null) entity.setRuleType(dto.getRuleType());
        if (dto.getTriggerEvent() != null) entity.setTriggerEvent(dto.getTriggerEvent());
        if (dto.getPointsValue() != null) entity.setPointsValue(dto.getPointsValue());
        if (dto.getPointsType() != null) entity.setPointsType(dto.getPointsType());
        if (dto.getBasisField() != null) entity.setBasisField(dto.getBasisField());
        if (dto.getDailyLimit() != null) entity.setDailyLimit(dto.getDailyLimit());
        if (dto.getMonthlyLimit() != null) entity.setMonthlyLimit(dto.getMonthlyLimit());
        if (dto.getMinPoints() != null) entity.setMinPoints(dto.getMinPoints());
        if (dto.getMaxPoints() != null) entity.setMaxPoints(dto.getMaxPoints());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = ruleRepository.save(entity);
        log.info("更新积分规则: id={}, ruleName={}", entity.getId(), entity.getRuleName());
        return toRuleDto(entity);
    }

    /**
     * 删除积分规则。
     *
     * @param id 规则 ID
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public void deleteRule(Long id) throws ScrmException {
        ScrmPointsRuleEntity entity = findRule(id);
        ruleRepository.delete(entity);
        log.info("删除积分规则: id={}, ruleName={}", id, entity.getRuleName());
    }

    /**
     * 查询规则详情。
     *
     * @param id 规则 ID
     * @return 规则 DTO
     * @throws ScrmException 规则不存在
     */
    @Transactional(readOnly = true)
    public ScrmPointsRuleDto getRule(Long id) throws ScrmException {
        return toRuleDto(findRule(id));
    }

    /**
     * 分页查询积分规则, 支持按规则类型 / 触发事件 / 启用状态过滤。
     *
     * @param ruleType     规则类型过滤: EARN / REDEEM（可空）
     * @param triggerEvent 触发事件过滤（可空）
     * @param enabled      启用状态过滤（可空）
     * @param pageable     分页参数
     * @return 规则分页结果 (按 createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmPointsRuleDto> listRules(String ruleType, String triggerEvent,
                                             Boolean enabled, Pageable pageable) {
        Specification<ScrmPointsRuleEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (ruleType != null && !ruleType.isBlank()) {
                predicates.add(cb.equal(root.get("ruleType"), ruleType));
            }
            if (triggerEvent != null && !triggerEvent.isBlank()) {
                predicates.add(cb.equal(root.get("triggerEvent"), triggerEvent));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return ruleRepository.findAll(spec, ensureSortable(pageable, "createTime")).map(this::toRuleDto);
    }

    /**
     * 启用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public ScrmPointsRuleDto enableRule(Long id) throws ScrmException {
        ScrmPointsRuleEntity entity = findRule(id);
        entity.setEnabled(true);
        entity = ruleRepository.save(entity);
        log.info("启用积分规则: id={}, ruleName={}", id, entity.getRuleName());
        return toRuleDto(entity);
    }

    /**
     * 禁用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public ScrmPointsRuleDto disableRule(Long id) throws ScrmException {
        ScrmPointsRuleEntity entity = findRule(id);
        entity.setEnabled(false);
        entity = ruleRepository.save(entity);
        log.info("禁用积分规则: id={}, ruleName={}", id, entity.getRuleName());
        return toRuleDto(entity);
    }

    /**
     * 确保分页参数带默认排序 (按指定字段倒序)。
     *
     * @param pageable 分页参数
     * @param field    默认排序字段
     * @return 处理后的分页参数
     */
    static Pageable ensureSortable(Pageable pageable, String field) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, field));
    }

    /**
     * 校验积分规则参数。
     *
     * @param dto     规则参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateRuleDto(ScrmPointsRuleDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("规则参数不能为空");
        }
        if (dto.getRuleName() == null || dto.getRuleName().isBlank()) {
            if (!partial) {
                throw ScrmException.badRequest("规则名称不能为空");
            }
        }
        if (dto.getRuleType() != null && !RULE_TYPE_EARN.equals(dto.getRuleType()) && !RULE_TYPE_REDEEM.equals(dto.getRuleType())) {
            throw ScrmException.badRequest("规则类型非法: " + dto.getRuleType() + ", 仅支持 EARN/REDEEM");
        } else if (dto.getRuleType() == null && !partial) {
            throw ScrmException.badRequest("规则类型不能为空");
        }
        if (dto.getTriggerEvent() == null || dto.getTriggerEvent().isBlank()) {
            if (!partial) {
                throw ScrmException.badRequest("触发事件不能为空");
            }
        }
        if (dto.getPointsValue() == null && !partial) {
            throw ScrmException.badRequest("积分值不能为空");
        }
        if (dto.getPointsType() != null && !POINTS_TYPE_FIXED.equals(dto.getPointsType()) && !POINTS_TYPE_PERCENTAGE.equals(dto.getPointsType())) {
            throw ScrmException.badRequest("积分计算类型非法: " + dto.getPointsType() + ", 仅支持 FIXED/PERCENTAGE");
        }
    }

    /**
     * 按主键查询规则并校验归属账号, 不存在或越权抛异常。
     *
     * @param id 规则 ID
     * @return 规则实体
     * @throws ScrmException 规则不存在
     */
    private ScrmPointsRuleEntity findRule(Long id) throws ScrmException {
        return ruleRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "积分规则不存在: id=" + id));
    }

    /**
     * 规则实体转 DTO。
     */
    private ScrmPointsRuleDto toRuleDto(ScrmPointsRuleEntity entity) {
        ScrmPointsRuleDto dto = new ScrmPointsRuleDto();
        dto.setId(entity.getId());
        dto.setRuleName(entity.getRuleName());
        dto.setRuleType(entity.getRuleType());
        dto.setTriggerEvent(entity.getTriggerEvent());
        dto.setPointsValue(entity.getPointsValue());
        dto.setPointsType(entity.getPointsType());
        dto.setBasisField(entity.getBasisField());
        dto.setDailyLimit(entity.getDailyLimit());
        dto.setMonthlyLimit(entity.getMonthlyLimit());
        dto.setMinPoints(entity.getMinPoints());
        dto.setMaxPoints(entity.getMaxPoints());
        dto.setDescription(entity.getDescription());
        dto.setEnabled(entity.getEnabled());
        dto.setTriggerCount(entity.getTriggerCount());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}