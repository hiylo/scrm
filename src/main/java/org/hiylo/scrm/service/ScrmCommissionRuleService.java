/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCommissionRuleService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmCommissionRuleDto;
import org.hiylo.scrm.entity.ScrmCommissionRecordEntity;
import org.hiylo.scrm.entity.ScrmCommissionRuleEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * SCRM 佣金规则管理服务 (规则子域)。
 * <p>
 * 承载佣金规则的增删改查 / 启用 / 禁用 / 统计更新与匹配规则查询。同时托管规则类型常量与
 * 条件匹配辅助方法 (matchesConditions / matchSingleCondition / toDouble), 供计算兄弟类
 * 以 package 级访问复用。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmCommissionRuleService {

    // ==================== 规则类型常量 (共享) ====================

    /** 规则类型: 固定比例 */
    static final String RULE_FLAT_RATE = "FLAT_RATE";
    /** 规则类型: 阶梯比例 */
    static final String RULE_TIERED_RATE = "TIERED_RATE";
    /** 规则类型: 奖金 */
    static final String RULE_BONUS = "BONUS";
    /** 规则类型: 倍数 */
    static final String RULE_MULTIPLIER = "MULTIPLIER";
    /** 规则类型: 扣减 */
    static final String RULE_DEDUCTION = "DEDUCTION";

    /** 佣金规则数据访问层 */
    private final ScrmCommissionRuleRepository ruleRepository;

    /** 佣金记录数据访问层 (统计更新用) */
    private final ScrmCommissionRecordRepository recordRepository;

    /** JSON 解析器 (解析 conditions / tierConfig) */
    private final ObjectMapper objectMapper;

    /** 佣金方案管理服务 (校验方案存在) */
    private final ScrmCommissionPlanService planService;

    // ============================================================
    // 规则管理
    // ============================================================

    /**
     * 创建佣金规则。
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException 参数非法 / 方案不存在
     */
    @Transactional
    public ScrmCommissionRuleDto createRule(ScrmCommissionRuleDto dto) throws ScrmException {
        validateRuleDto(dto, false);
        planService.findPlanOrThrow(dto.getPlanId());
        ScrmCommissionRuleEntity entity = new ScrmCommissionRuleEntity();
        entity.setPlanId(dto.getPlanId());
        entity.setRuleName(dto.getRuleName());
        entity.setRuleType(dto.getRuleType());
        entity.setConditions(dto.getConditions());
        entity.setCommissionRate(dto.getCommissionRate() != null ? dto.getCommissionRate() : 0d);
        entity.setCommissionAmount(dto.getCommissionAmount() != null ? dto.getCommissionAmount() : 0d);
        entity.setTierConfig(dto.getTierConfig());
        entity.setBonusAmount(dto.getBonusAmount() != null ? dto.getBonusAmount() : 0d);
        entity.setMultiplier(dto.getMultiplier() != null ? dto.getMultiplier() : 1.0d);
        entity.setDeductionAmount(dto.getDeductionAmount() != null ? dto.getDeductionAmount() : 0d);
        entity.setMinOrderAmount(dto.getMinOrderAmount() != null ? dto.getMinOrderAmount() : 0d);
        entity.setMaxCommissionPerOrder(dto.getMaxCommissionPerOrder() != null ? dto.getMaxCommissionPerOrder() : 0d);
        entity.setPriority(dto.getPriority() != null ? dto.getPriority() : 0);
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);
        entity.setMatchCount(0);
        entity.setTotalCommissionCalculated(0d);
        entity.setCreatedBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : ScrmCommissionPlanService.DEFAULT_OPERATOR);
        entity = ruleRepository.save(entity);
        log.info("创建佣金规则: id={}, planId={}, ruleName={}, ruleType={}",
                entity.getId(), entity.getPlanId(), entity.getRuleName(), entity.getRuleType());
        return toRuleDto(entity);
    }

    /**
     * 更新佣金规则（字段非空才覆盖）。
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 参数非法
     */
    @Transactional
    public ScrmCommissionRuleDto updateRule(Long id, ScrmCommissionRuleDto dto) throws ScrmException {
        ScrmCommissionRuleEntity entity = findRuleOrThrow(id);
        validateRuleDto(dto, true);
        if (dto.getRuleName() != null) entity.setRuleName(dto.getRuleName());
        if (dto.getRuleType() != null) entity.setRuleType(dto.getRuleType());
        if (dto.getConditions() != null) entity.setConditions(dto.getConditions());
        if (dto.getCommissionRate() != null) entity.setCommissionRate(dto.getCommissionRate());
        if (dto.getCommissionAmount() != null) entity.setCommissionAmount(dto.getCommissionAmount());
        if (dto.getTierConfig() != null) entity.setTierConfig(dto.getTierConfig());
        if (dto.getBonusAmount() != null) entity.setBonusAmount(dto.getBonusAmount());
        if (dto.getMultiplier() != null) entity.setMultiplier(dto.getMultiplier());
        if (dto.getDeductionAmount() != null) entity.setDeductionAmount(dto.getDeductionAmount());
        if (dto.getMinOrderAmount() != null) entity.setMinOrderAmount(dto.getMinOrderAmount());
        if (dto.getMaxCommissionPerOrder() != null) entity.setMaxCommissionPerOrder(dto.getMaxCommissionPerOrder());
        if (dto.getPriority() != null) entity.setPriority(dto.getPriority());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = ruleRepository.save(entity);
        log.info("更新佣金规则: id={}, ruleName={}", entity.getId(), entity.getRuleName());
        return toRuleDto(entity);
    }

    /**
     * 删除佣金规则。
     *
     * @param id 规则 ID
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public void deleteRule(Long id) throws ScrmException {
        ScrmCommissionRuleEntity entity = findRuleOrThrow(id);
        ruleRepository.delete(entity);
        log.info("删除佣金规则: id={}, ruleName={}", id, entity.getRuleName());
    }

    /**
     * 查询规则详情。
     *
     * @param id 规则 ID
     * @return 规则 DTO
     * @throws ScrmException 规则不存在
     */
    @Transactional(readOnly = true)
    public ScrmCommissionRuleDto getRule(Long id) throws ScrmException {
        return toRuleDto(findRuleOrThrow(id));
    }

    /**
     * 分页查询规则, 支持按方案 ID/规则类型/启用状态过滤。
     *
     * @param planId   方案 ID (可空)
     * @param ruleType 规则类型 (可空)
     * @param enabled  启用状态 (可空)
     * @param pageable 分页参数
     * @return 规则分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmCommissionRuleDto> listRules(Long planId, String ruleType, Boolean enabled, Pageable pageable) {
        Specification<ScrmCommissionRuleEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (planId != null) {
                predicates.add(cb.equal(root.get("planId"), planId));
            }
            if (ruleType != null && !ruleType.isBlank()) {
                predicates.add(cb.equal(root.get("ruleType"), ruleType));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "priority").and(Sort.by(Sort.Direction.DESC, "createTime")));
        return ruleRepository.findAll(spec, sorted).map(this::toRuleDto);
    }

    /**
     * 启用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public ScrmCommissionRuleDto enableRule(Long id) throws ScrmException {
        ScrmCommissionRuleEntity entity = findRuleOrThrow(id);
        entity.setEnabled(true);
        entity = ruleRepository.save(entity);
        log.info("启用佣金规则: id={}", id);
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
    public ScrmCommissionRuleDto disableRule(Long id) throws ScrmException {
        ScrmCommissionRuleEntity entity = findRuleOrThrow(id);
        entity.setEnabled(false);
        entity = ruleRepository.save(entity);
        log.info("禁用佣金规则: id={}", id);
        return toRuleDto(entity);
    }

    /**
     * 更新规则统计 (累计匹配次数与累计计算佣金)。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public ScrmCommissionRuleDto updateRuleStats(Long id) throws ScrmException {
        ScrmCommissionRuleEntity entity = findRuleOrThrow(id);
        List<ScrmCommissionRecordEntity> records = recordRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("ruleId"), id));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
        int matchCount = records.size();
        double totalCommission = records.stream()
                .mapToDouble(r -> r.getFinalCommission() != null ? r.getFinalCommission() : 0d)
                .sum();
        entity.setMatchCount(matchCount);
        entity.setTotalCommissionCalculated(totalCommission);
        entity = ruleRepository.save(entity);
        log.info("更新佣金规则统计: id={}, matchCount={}, totalCommissionCalculated={}",
                id, matchCount, totalCommission);
        return toRuleDto(entity);
    }

    /**
     * 获取匹配规则的规则列表。
     * <p>按 conditions JSON 过滤方案下启用的规则, 优先级倒序排列。conditions 为空时返回全部启用规则。</p>
     *
     * @param planId     方案 ID
     * @param conditions 匹配条件 Map (field → value)
     * @return 匹配规则列表
     * @throws ScrmException 方案不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmCommissionRuleEntity> getMatchingRules(Long planId, Map<String, Object> conditions)
            throws ScrmException {
        planService.findPlanOrThrow(planId);
        List<ScrmCommissionRuleEntity> rules = ruleRepository.findByPlanId(planId);
        List<ScrmCommissionRuleEntity> matched = rules.stream()
                .filter(r -> Boolean.TRUE.equals(r.getEnabled()))
                .filter(r -> matchesConditions(r, conditions))
                .sorted(Comparator.comparingInt(
                        (ScrmCommissionRuleEntity r) -> r.getPriority() == null ? 0 : r.getPriority()).reversed())
                .collect(Collectors.toList());
        return matched;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 校验规则 DTO。
     *
     * @param dto    规则参数
     * @param partial 是否部分更新
     * @throws ScrmException 参数非法
     */
    private void validateRuleDto(ScrmCommissionRuleDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("规则参数不能为空");
        }
        if (dto.getRuleName() != null) {
            if (dto.getRuleName().isBlank()) {
                throw ScrmException.badRequest("规则名称不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("规则名称不能为空");
        }
        if (!partial && dto.getPlanId() == null) {
            throw ScrmException.badRequest("方案 ID 不能为空");
        }
        if (!partial && dto.getRuleType() == null) {
            throw ScrmException.badRequest("规则类型不能为空");
        }
        if (dto.getConditions() != null && !dto.getConditions().isBlank()) {
            try {
                objectMapper.readTree(dto.getConditions());
            } catch (Exception e) {
                throw ScrmException.badRequest("规则条件 conditions JSON 解析失败: " + e.getMessage());
            }
        }
        if (dto.getTierConfig() != null && !dto.getTierConfig().isBlank()) {
            try {
                objectMapper.readTree(dto.getTierConfig());
            } catch (Exception e) {
                throw ScrmException.badRequest("阶梯配置 tierConfig JSON 解析失败: " + e.getMessage());
            }
        }
    }

    /**
     * 判断规则是否匹配条件。
     * <p>解析规则 conditions JSON [{field,operator,value}], 与传入条件 Map 比对。
     * conditions 为空表示无条件匹配。operator 支持: EQ/NE/GT/LT/GTE/LTE/IN/CONTAINS。</p>
     *
     * @param rule       规则
     * @param conditions 条件 Map
     * @return true 表示匹配
     */
    private boolean matchesConditions(ScrmCommissionRuleEntity rule, Map<String, Object> conditions) {
        if (rule.getConditions() == null || rule.getConditions().isBlank()) {
            return true;
        }
        List<Map<String, Object>> ruleConditions;
        try {
            ruleConditions = objectMapper.readValue(rule.getConditions(),
                    new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("规则条件 JSON 解析失败, 视为无条件: ruleId={}, err={}", rule.getId(), e.getMessage());
            return true;
        }
        if (ruleConditions.isEmpty()) {
            return true;
        }
        for (Map<String, Object> cond : ruleConditions) {
            String field = toStringValue(cond.get("field"));
            String operator = toStringValue(cond.get("operator")).toUpperCase();
            Object ruleValue = cond.get("value");
            Object actualValue = conditions.get(field);
            if (!matchSingleCondition(operator, ruleValue, actualValue)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 匹配单个条件。
     *
     * @param operator     操作符
     * @param ruleValue    规则值
     * @param actualValue  实际值
     * @return true 表示匹配
     */
    private boolean matchSingleCondition(String operator, Object ruleValue, Object actualValue) {
        if (actualValue == null) {
            return false;
        }
        switch (operator) {
            case "EQ":
                return Objects.equals(toStringValue(ruleValue), toStringValue(actualValue));
            case "NE":
                return !Objects.equals(toStringValue(ruleValue), toStringValue(actualValue));
            case "GT":
                return toDouble(actualValue) > toDouble(ruleValue);
            case "LT":
                return toDouble(actualValue) < toDouble(ruleValue);
            case "GTE":
                return toDouble(actualValue) >= toDouble(ruleValue);
            case "LTE":
                return toDouble(actualValue) <= toDouble(ruleValue);
            case "IN":
                if (ruleValue instanceof List<?> list) {
                    return list.stream().anyMatch(v -> Objects.equals(toStringValue(v), toStringValue(actualValue)));
                }
                return false;
            case "CONTAINS":
                return toStringValue(actualValue).contains(toStringValue(ruleValue));
            default:
                return Objects.equals(toStringValue(ruleValue), toStringValue(actualValue));
        }
    }

    /**
     * 按主键查询规则, 不存在或越权抛异常。
     */
    private ScrmCommissionRuleEntity findRuleOrThrow(Long id) throws ScrmException {
        ScrmCommissionRuleEntity entity = ruleRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "佣金规则不存在: id=" + id));
        return entity;
    }

    /**
     * 将对象转换为 double 数值 (供计算兄弟类解析阶梯配置复用)。
     *
     * @param obj 对象
     * @return double 值, 不可转换时返回 0
     */
    double toDouble(Object obj) {
        if (obj == null) {
            return 0d;
        }
        if (obj instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(obj.toString());
        } catch (NumberFormatException e) {
            return 0d;
        }
    }

    /**
     * 将对象转换为字符串。
     *
     * @param obj 对象
     * @return 字符串, null 返回空字符串
     */
    private String toStringValue(Object obj) {
        return obj == null ? "" : obj.toString();
    }

    /**
     * 规则实体转 DTO
     */
    private ScrmCommissionRuleDto toRuleDto(ScrmCommissionRuleEntity entity) {
        ScrmCommissionRuleDto dto = new ScrmCommissionRuleDto();
        dto.setId(entity.getId());
        dto.setPlanId(entity.getPlanId());
        dto.setRuleName(entity.getRuleName());
        dto.setRuleType(entity.getRuleType());
        dto.setConditions(entity.getConditions());
        dto.setCommissionRate(entity.getCommissionRate());
        dto.setCommissionAmount(entity.getCommissionAmount());
        dto.setTierConfig(entity.getTierConfig());
        dto.setBonusAmount(entity.getBonusAmount());
        dto.setMultiplier(entity.getMultiplier());
        dto.setDeductionAmount(entity.getDeductionAmount());
        dto.setMinOrderAmount(entity.getMinOrderAmount());
        dto.setMaxCommissionPerOrder(entity.getMaxCommissionPerOrder());
        dto.setPriority(entity.getPriority());
        dto.setEnabled(entity.getEnabled());
        dto.setMatchCount(entity.getMatchCount());
        dto.setTotalCommissionCalculated(entity.getTotalCommissionCalculated());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}
