/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBlacklistRuleService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.dto.ScrmBlacklistRuleDto;
import org.hiylo.scrm.dto.ScrmRiskAssessmentDto;
import org.hiylo.scrm.entity.ScrmBlacklistRuleEntity;
import org.hiylo.scrm.entity.ScrmRiskEventEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmBlacklistRuleRepository;
import org.hiylo.scrm.repository.ScrmRiskEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * SCRM 风控规则管理服务。
 * <p>
 * 承载风控规则子域: 规则增删改查、启停、单条与全量评估、规则统计 (误报 / 准确率)
 * 与复制、触发历史查询。规则评估命中后委托 {@link ScrmRiskEventService} 触发风险事件
 * 并执行动作。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmBlacklistRuleService {

    // ==================== 默认值常量 ====================

    /** 默认严重程度 */
    private static final String DEFAULT_SEVERITY = "MEDIUM";
    /** 默认规则启用状态 */
    private static final boolean DEFAULT_RULE_ENABLED = true;
    /** 默认优先级 */
    private static final int DEFAULT_PRIORITY = 0;

    // ==================== 合法枚举值 ====================

    /** 合法的规则类型 */
    private static final List<String> VALID_RULE_TYPES = List.of(
            "FREQUENCY", "AMOUNT", "BEHAVIOR", "PATTERN", "BLACKLIST_MATCH", "COMPOSITE",
            "TIME", "LOCATION", "DEVICE", "TRANSACTION");
    /** 合法的风险类别 */
    private static final List<String> VALID_RISK_CATEGORIES =
            List.of("FRAUD", "ABUSE", "SPAM", "HARASSMENT", "FAKE", "VIOLATION", "POLICY", "SECURITY");
    /** 合法的条件操作符 */
    private static final List<String> VALID_OPERATORS =
            List.of("GT", "GTE", "LT", "LTE", "EQ", "NE", "CONTAINS", "NOT_CONTAINS", "IN", "NOT_IN", "REGEX", "MATCH");
    /** 合法的动作 */
    private static final List<String> VALID_ACTIONS =
            List.of("ALERT", "BLOCK", "REVIEW", "QUARANTINE", "AUTO_BLACKLIST", "NOTIFY");
    /** 合法的严重程度 */
    private static final List<String> VALID_SEVERITIES = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    // ==================== 依赖注入 ====================

    /** 风控规则数据访问层 */
    private final ScrmBlacklistRuleRepository ruleRepository;
    /** 风险事件数据访问层 (触发历史查询) */
    private final ScrmRiskEventRepository eventRepository;
    /** JSON 解析器 */
    private final ObjectMapper objectMapper;
    /** 黑名单管理子域服务 (共享风险等级比较与常量) */
    private final ScrmBlacklistManageService blacklistService;
    /** 风险事件子域服务 (评估命中后触发事件) */
    private final ScrmRiskEventService eventService;

    /**
     * 创建风控规则。
     * <p>校验 ruleCode 唯一, severity / action / enabled / priority 缺省时填默认值。</p>
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException 参数非法 / 编码重复
     */
    @Transactional
    public ScrmBlacklistRuleEntity createRule(ScrmBlacklistRuleDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("规则参数不能为空");
        }
        validateRuleEnums(dto, false);
        if (ruleRepository.findByRuleCode(dto.getRuleCode()).isPresent()) {
            throw ScrmException.conflict("规则代码已存在: " + dto.getRuleCode());
        }
        ScrmBlacklistRuleEntity entity = new ScrmBlacklistRuleEntity();
        entity.setRuleName(dto.getRuleName());
        entity.setRuleCode(dto.getRuleCode());
        entity.setDescription(dto.getDescription());
        entity.setRuleType(dto.getRuleType());
        entity.setRiskCategory(dto.getRiskCategory());
        entity.setConditionField(dto.getConditionField());
        entity.setConditionOperator(dto.getConditionOperator());
        entity.setConditionValue(dto.getConditionValue());
        entity.setConditionValue2(dto.getConditionValue2());
        entity.setTimeWindowMinutes(dto.getTimeWindowMinutes());
        entity.setThresholdCount(dto.getThresholdCount());
        entity.setThresholdAmount(dto.getThresholdAmount());
        entity.setSeverity(dto.getSeverity() != null ? dto.getSeverity() : DEFAULT_SEVERITY);
        entity.setAction(dto.getAction() != null ? dto.getAction() : ScrmBlacklistManageService.DEFAULT_ACTION);
        entity.setActionParams(dto.getActionParams());
        entity.setApplicableModules(dto.getApplicableModules());
        entity.setApplicableScenarios(dto.getApplicableScenarios());
        entity.setTargetListType(dto.getTargetListType());
        entity.setNotificationChannels(dto.getNotificationChannels());
        entity.setNotificationRecipients(dto.getNotificationRecipients());
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : DEFAULT_RULE_ENABLED);
        entity.setPriority(dto.getPriority() != null ? dto.getPriority() : DEFAULT_PRIORITY);
        entity.setTriggerCount(0);
        entity.setFalsePositiveCount(0);
        entity.setFalsePositiveRate(0.0);
        entity.setAccuracyRate(0.0);
        entity.setEvaluationCount(0);
        entity.setTags(dto.getTags());
        entity.setCreatedBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : currentOperator());
        entity = ruleRepository.save(entity);
        log.info("创建风控规则: id={}, code={}", entity.getId(), entity.getRuleCode());
        return entity;
    }

    /**
     * 更新风控规则 (字段非空才覆盖)。
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 参数非法 / 编码重复
     */
    @Transactional
    public ScrmBlacklistRuleEntity updateRule(Long id, ScrmBlacklistRuleDto dto) throws ScrmException {
        ScrmBlacklistRuleEntity entity = findRuleOrThrow(id);
        if (dto == null) {
            throw ScrmException.badRequest("规则参数不能为空");
        }
        validateRuleEnums(dto, true);
        if (dto.getRuleCode() != null && !dto.getRuleCode().equals(entity.getRuleCode())) {
            Optional<ScrmBlacklistRuleEntity> existing = ruleRepository
                    .findByRuleCode(dto.getRuleCode());
            if (existing.isPresent() && !existing.get().getId().equals(id)) {
                throw ScrmException.conflict("规则代码已存在: " + dto.getRuleCode());
            }
            entity.setRuleCode(dto.getRuleCode());
        }
        if (dto.getRuleName() != null) entity.setRuleName(dto.getRuleName());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getRuleType() != null) entity.setRuleType(dto.getRuleType());
        if (dto.getRiskCategory() != null) entity.setRiskCategory(dto.getRiskCategory());
        if (dto.getConditionField() != null) entity.setConditionField(dto.getConditionField());
        if (dto.getConditionOperator() != null) entity.setConditionOperator(dto.getConditionOperator());
        if (dto.getConditionValue() != null) entity.setConditionValue(dto.getConditionValue());
        if (dto.getConditionValue2() != null) entity.setConditionValue2(dto.getConditionValue2());
        if (dto.getTimeWindowMinutes() != null) entity.setTimeWindowMinutes(dto.getTimeWindowMinutes());
        if (dto.getThresholdCount() != null) entity.setThresholdCount(dto.getThresholdCount());
        if (dto.getThresholdAmount() != null) entity.setThresholdAmount(dto.getThresholdAmount());
        if (dto.getSeverity() != null) entity.setSeverity(dto.getSeverity());
        if (dto.getAction() != null) entity.setAction(dto.getAction());
        if (dto.getActionParams() != null) entity.setActionParams(dto.getActionParams());
        if (dto.getApplicableModules() != null) entity.setApplicableModules(dto.getApplicableModules());
        if (dto.getApplicableScenarios() != null) entity.setApplicableScenarios(dto.getApplicableScenarios());
        if (dto.getTargetListType() != null) entity.setTargetListType(dto.getTargetListType());
        if (dto.getNotificationChannels() != null) entity.setNotificationChannels(dto.getNotificationChannels());
        if (dto.getNotificationRecipients() != null) entity.setNotificationRecipients(dto.getNotificationRecipients());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getPriority() != null) entity.setPriority(dto.getPriority());
        if (dto.getTags() != null) entity.setTags(dto.getTags());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = ruleRepository.save(entity);
        log.info("更新风控规则: id={}, code={}", id, entity.getRuleCode());
        return entity;
    }

    /**
     * 删除风控规则。
     *
     * @param id 规则 ID
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public void deleteRule(Long id) throws ScrmException {
        ScrmBlacklistRuleEntity entity = findRuleOrThrow(id);
        ruleRepository.delete(entity);
        log.info("删除风控规则: id={}, code={}", id, entity.getRuleCode());
    }

    /**
     * 查询规则详情。
     *
     * @param id 规则 ID
     * @return 规则实体
     * @throws ScrmException 规则不存在
     */
    @Transactional(readOnly = true)
    public ScrmBlacklistRuleEntity getRule(Long id) throws ScrmException {
        return findRuleOrThrow(id);
    }

    /**
     * 按规则代码查询规则。
     *
     * @param code 规则代码
     * @return 规则实体
     * @throws ScrmException 规则不存在
     */
    @Transactional(readOnly = true)
    public ScrmBlacklistRuleEntity getRuleByCode(String code) throws ScrmException {
        if (code == null || code.isBlank()) {
            throw ScrmException.badRequest("规则代码不能为空");
        }
        return ruleRepository.findByRuleCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "风控规则不存在: code=" + code));
    }

    /**
     * 分页查询规则, 支持按规则类型 / 风险类别 / 严重程度 / 启用状态 / 关键字过滤。
     *
     * @param ruleType      规则类型过滤（可空）
     * @param riskCategory  风险类别过滤（可空）
     * @param severity      严重程度过滤（可空）
     * @param enabled       启用状态过滤（可空）
     * @param keyword       名称/代码关键字模糊匹配（可空）
     * @param pageable      分页参数
     * @return 规则分页结果 (按 priority ASC, updateTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmBlacklistRuleEntity> listRules(String ruleType, String riskCategory, String severity,
                                                    Boolean enabled, String keyword, Pageable pageable) {
        Specification<ScrmBlacklistRuleEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (ruleType != null && !ruleType.isBlank()) {
                predicates.add(cb.equal(root.get("ruleType"), ruleType));
            }
            if (riskCategory != null && !riskCategory.isBlank()) {
                predicates.add(cb.equal(root.get("riskCategory"), riskCategory));
            }
            if (severity != null && !severity.isBlank()) {
                predicates.add(cb.equal(root.get("severity"), severity));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword + "%";
                predicates.add(cb.or(
                        cb.like(root.get("ruleName"), like),
                        cb.like(root.get("ruleCode"), like)));
            }
            query.orderBy(cb.asc(root.get("priority")), cb.desc(root.get("updateTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return ruleRepository.findAll(spec, pageable);
    }

    /**
     * 启用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public ScrmBlacklistRuleEntity enableRule(Long id) throws ScrmException {
        ScrmBlacklistRuleEntity entity = findRuleOrThrow(id);
        entity.setEnabled(true);
        entity = ruleRepository.save(entity);
        log.info("启用规则: id={}", id);
        return entity;
    }

    /**
     * 禁用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public ScrmBlacklistRuleEntity disableRule(Long id) throws ScrmException {
        ScrmBlacklistRuleEntity entity = findRuleOrThrow(id);
        entity.setEnabled(false);
        entity = ruleRepository.save(entity);
        log.info("禁用规则: id={}", id);
        return entity;
    }

    /**
     * 评估单条规则 (完整实现)。
     * <p>从 context 中取出 conditionField 对应的值, 按 conditionOperator 与 conditionValue 比较,
     * 返回是否触发及匹配详情。</p>
     *
     * @param ruleId  规则 ID
     * @param context 评估上下文
     * @return 评估结果 Map {triggered, ruleId, ruleCode, ruleName, field, value, condition, expected,
     *         severity, action}
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public Map<String, Object> evaluateRule(Long ruleId, Map<String, Object> context) throws ScrmException {
        ScrmBlacklistRuleEntity rule = findRuleOrThrow(ruleId);
        return evaluateRuleInternal(rule, context != null ? context : Map.of());
    }

    /**
     * 评估所有启用规则 (完整实现)。
     * <p>按优先级升序遍历所有启用规则, 逐条评估, 命中则触发风险事件并执行动作, 返回
     * 汇总结果。</p>
     *
     * @param assessmentDto 风险评估参数
     * @return 评估结果 Map {triggeredRules, events, totalTriggered, maxSeverity, recommendedAction}
     * @throws ScrmException 参数非法
     */
    @Transactional
    public Map<String, Object> evaluateAllRules(ScrmRiskAssessmentDto assessmentDto) throws ScrmException {
        if (assessmentDto == null || assessmentDto.getTargetType() == null || assessmentDto.getTargetValue() == null) {
            throw ScrmException.badRequest("评估参数不能为空");
        }
        List<ScrmBlacklistRuleEntity> rules = ruleRepository.findByEnabledOrderByPriorityAsc(true);
        Map<String, Object> context = assessmentDto.getContext() != null ?
                assessmentDto.getContext() : new LinkedHashMap<>();
        if (assessmentDto.getAmount() != null) {
            context.put("amount", assessmentDto.getAmount());
        }
        context.put("targetType", assessmentDto.getTargetType());
        context.put("targetValue", assessmentDto.getTargetValue());

        List<Map<String, Object>> triggeredRules = new ArrayList<>();
        List<ScrmRiskEventEntity> events = new ArrayList<>();
        String maxSeverity = "LOW";
        String recommendedAction = "ALLOW";
        for (ScrmBlacklistRuleEntity rule : rules) {
            Map<String, Object> result = evaluateRuleInternal(rule, context);
            // 更新评估统计
            rule.setEvaluationCount((rule.getEvaluationCount() != null ? rule.getEvaluationCount() : 0) + 1);
            rule.setLastEvaluatedAt(LocalDateTime.now());
            if (Boolean.TRUE.equals(result.get("triggered"))) {
                rule.setTriggerCount((rule.getTriggerCount() != null ? rule.getTriggerCount() : 0) + 1);
                rule.setLastTriggeredAt(LocalDateTime.now());
                triggeredRules.add(result);
                // 触发风险事件
                ScrmRiskEventEntity event = eventService.fireEvent(rule.getId(), assessmentDto.getCustomerId(),
                        assessmentDto.getTargetType(), assessmentDto.getTargetValue(),
                        toJson(Map.of("field", rule.getConditionField(), "value", result.get("value"),
                                "condition", rule.getConditionOperator(), "expected", rule.getConditionValue())));
                events.add(event);
                if (blacklistService.compareSeverity(rule.getSeverity(), maxSeverity) > 0) {
                    maxSeverity = rule.getSeverity();
                    recommendedAction = rule.getAction();
                }
            }
            ruleRepository.save(rule);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalEvaluated", rules.size());
        summary.put("totalTriggered", triggeredRules.size());
        summary.put("triggeredRules", triggeredRules);
        summary.put("events", events);
        summary.put("maxSeverity", maxSeverity);
        summary.put("recommendedAction", recommendedAction);
        return summary;
    }

    /**
     * 按风险类别分页查询规则。
     *
     * @param category 风险类别
     * @param pageable 分页参数
     * @return 规则分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmBlacklistRuleEntity> getRulesByCategory(String category, Pageable pageable) {
        return ruleRepository.findByRiskCategory(category, pageable);
    }

    /**
     * 按适用模块分页查询规则。
     *
     * @param module   模块名
     * @param pageable 分页参数
     * @return 规则分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmBlacklistRuleEntity> getRulesByModule(String module, Pageable pageable) {
        return ruleRepository.findByApplicableModulesContaining(
                 module, pageable);
    }

    /**
     * 更新规则统计 (重新计算误报率 / 准确率)。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public ScrmBlacklistRuleEntity updateRuleStats(Long id) throws ScrmException {
        ScrmBlacklistRuleEntity rule = findRuleOrThrow(id);
        int triggerCount = rule.getTriggerCount() != null ? rule.getTriggerCount() : 0;
        int falsePositive = rule.getFalsePositiveCount() != null ? rule.getFalsePositiveCount() : 0;
        if (triggerCount > 0) {
            double fpr = BigDecimal.valueOf(falsePositive)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(triggerCount), 2, RoundingMode.HALF_UP)
                    .doubleValue();
            rule.setFalsePositiveRate(fpr);
            rule.setAccuracyRate(BigDecimal.valueOf(100 - fpr).setScale(2, RoundingMode.HALF_UP).doubleValue());
        } else {
            rule.setFalsePositiveRate(0.0);
            rule.setAccuracyRate(0.0);
        }
        rule = ruleRepository.save(rule);
        log.info("更新规则统计: id={}, triggerCount={}, fpr={}", id, triggerCount, rule.getFalsePositiveRate());
        return rule;
    }

    /**
     * 标记规则误报 (误报次数 +1, 关联事件置为误报)。
     *
     * @param ruleId  规则 ID
     * @param eventId 事件 ID
     * @return 更新后的规则
     * @throws ScrmException 规则 / 事件不存在
     */
    @Transactional
    public ScrmBlacklistRuleEntity markFalsePositive(Long ruleId, Long eventId) throws ScrmException {
        ScrmBlacklistRuleEntity rule = findRuleOrThrow(ruleId);
        rule.setFalsePositiveCount((rule.getFalsePositiveCount() != null ? rule.getFalsePositiveCount() : 0) + 1);
        rule = ruleRepository.save(rule);
        if (eventId != null) {
            eventService.markFalsePositive(eventId, "规则误报标记");
        }
        updateRuleStats(ruleId);
        log.info("标记规则误报: ruleId={}, eventId={}", ruleId, eventId);
        return rule;
    }

    /**
     * 查询规则准确率。
     *
     * @param id 规则 ID
     * @return 准确率 Map {accuracyRate, falsePositiveRate, triggerCount, falsePositiveCount}
     * @throws ScrmException 规则不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getRuleAccuracy(Long id) throws ScrmException {
        ScrmBlacklistRuleEntity rule = findRuleOrThrow(id);
        Map<String, Object> accuracy = new LinkedHashMap<>();
        accuracy.put("ruleId", rule.getId());
        accuracy.put("ruleCode", rule.getRuleCode());
        accuracy.put("triggerCount", rule.getTriggerCount() != null ? rule.getTriggerCount() : 0);
        accuracy.put("falsePositiveCount", rule.getFalsePositiveCount() != null ? rule.getFalsePositiveCount() : 0);
        accuracy.put("accuracyRate", rule.getAccuracyRate() != null ? rule.getAccuracyRate() : 0.0);
        accuracy.put("falsePositiveRate", rule.getFalsePositiveRate() != null ? rule.getFalsePositiveRate() : 0.0);
        return accuracy;
    }

    /**
     * 复制规则 (生成新代码的副本)。
     *
     * @param id      源规则 ID
     * @param newCode 新规则代码
     * @return 复制后的规则
     * @throws ScrmException 规则不存在 / 编码重复
     */
    @Transactional
    public ScrmBlacklistRuleEntity duplicateRule(Long id, String newCode) throws ScrmException {
        ScrmBlacklistRuleEntity source = findRuleOrThrow(id);
        if (newCode == null || newCode.isBlank()) {
            throw ScrmException.badRequest("新规则代码不能为空");
        }
        if (ruleRepository.findByRuleCode(newCode).isPresent()) {
            throw ScrmException.conflict("规则代码已存在: " + newCode);
        }
        ScrmBlacklistRuleEntity copy = new ScrmBlacklistRuleEntity();
        copy.setRuleName(source.getRuleName() + " (副本)");
        copy.setRuleCode(newCode);
        copy.setDescription(source.getDescription());
        copy.setRuleType(source.getRuleType());
        copy.setRiskCategory(source.getRiskCategory());
        copy.setConditionField(source.getConditionField());
        copy.setConditionOperator(source.getConditionOperator());
        copy.setConditionValue(source.getConditionValue());
        copy.setConditionValue2(source.getConditionValue2());
        copy.setTimeWindowMinutes(source.getTimeWindowMinutes());
        copy.setThresholdCount(source.getThresholdCount());
        copy.setThresholdAmount(source.getThresholdAmount());
        copy.setSeverity(source.getSeverity());
        copy.setAction(source.getAction());
        copy.setActionParams(source.getActionParams());
        copy.setApplicableModules(source.getApplicableModules());
        copy.setApplicableScenarios(source.getApplicableScenarios());
        copy.setTargetListType(source.getTargetListType());
        copy.setNotificationChannels(source.getNotificationChannels());
        copy.setNotificationRecipients(source.getNotificationRecipients());
        copy.setEnabled(source.getEnabled());
        copy.setPriority(source.getPriority());
        copy.setTriggerCount(0);
        copy.setFalsePositiveCount(0);
        copy.setFalsePositiveRate(0.0);
        copy.setAccuracyRate(0.0);
        copy.setEvaluationCount(0);
        copy.setTags(source.getTags());
        copy.setCreatedBy(currentOperator());
        copy = ruleRepository.save(copy);
        log.info("复制规则: sourceId={}, newId={}, newCode={}", id, copy.getId(), newCode);
        return copy;
    }

    /**
     * 查询规则触发历史 (按规则 ID 分页查询关联风险事件)。
     *
     * @param ruleId   规则 ID
     * @param pageable 分页参数
     * @return 事件分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmRiskEventEntity> getRuleTriggerHistory(Long ruleId, Pageable pageable) {
        return eventRepository.findByRuleId(ruleId, pageable);
    }

    // ============================================================
    // 私有辅助方法
    // ============================================================

    /**
     * 按主键查询规则, 不存在抛异常。
     *
     * @param id 规则 ID
     * @return 规则实体
     * @throws ScrmException 规则不存在
     */
    private ScrmBlacklistRuleEntity findRuleOrThrow(Long id) throws ScrmException {
        ScrmBlacklistRuleEntity entity = ruleRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "风控规则不存在: id=" + id));
        return entity;
    }

    /**
     * 当前操作人 (优先取 UserContext, 缺省 scrm-system)。
     *
     * @return 当前操作人
     */
    private String currentOperator() {
        String userId = UserContext.getUserId();
        return userId != null ? userId : ScrmBlacklistManageService.DEFAULT_OPERATOR;
    }

    /**
     * 校验规则 DTO 枚举字段。
     *
     * @param dto      规则参数
     * @param isUpdate 是否更新场景
     * @throws ScrmException 参数非法
     */
    private void validateRuleEnums(ScrmBlacklistRuleDto dto, boolean isUpdate) throws ScrmException {
        if (!isUpdate) {
            if (dto.getRuleType() != null && !VALID_RULE_TYPES.contains(dto.getRuleType())) {
                throw ScrmException.badRequest("规则类型非法: " + dto.getRuleType()
                        + ", 仅支持 " + VALID_RULE_TYPES);
            }
            if (dto.getRiskCategory() != null && !VALID_RISK_CATEGORIES.contains(dto.getRiskCategory())) {
                throw ScrmException.badRequest(
                        "风险类别非法: " + dto.getRiskCategory() + ", 仅支持 " + VALID_RISK_CATEGORIES);
            }
            if (dto.getConditionOperator() != null && !VALID_OPERATORS.contains(dto.getConditionOperator())) {
                throw ScrmException.badRequest(
                        "条件操作符非法: " + dto.getConditionOperator() + ", 仅支持 " + VALID_OPERATORS);
            }
        }
        if (dto.getSeverity() != null && !VALID_SEVERITIES.contains(dto.getSeverity())) {
            throw ScrmException.badRequest("严重程度非法: " + dto.getSeverity()
                    + ", 仅支持 " + VALID_SEVERITIES);
        }
        if (dto.getAction() != null && !VALID_ACTIONS.contains(dto.getAction())) {
            throw ScrmException.badRequest("执行动作非法: " + dto.getAction() + ", 仅支持 " + VALID_ACTIONS);
        }
    }

    /**
     * 评估单条规则 (内部实现)。
     * <p>从 context 中按 conditionField 取值, 按 conditionOperator 与 conditionValue 比较。</p>
     *
     * @param rule    规则实体
     * @param context 评估上下文
     * @return 评估结果 Map
     */
    private Map<String, Object> evaluateRuleInternal(ScrmBlacklistRuleEntity rule, Map<String, Object> context) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ruleId", rule.getId());
        result.put("ruleCode", rule.getRuleCode());
        result.put("ruleName", rule.getRuleName());
        result.put("field", rule.getConditionField());
        result.put("condition", rule.getConditionOperator());
        result.put("expected", rule.getConditionValue());
        result.put("severity", rule.getSeverity());
        result.put("action", rule.getAction());
        Object actualValue = context.get(rule.getConditionField());
        result.put("value", actualValue);
        boolean triggered = evaluateCondition(rule.getConditionOperator(), rule.getConditionValue(),
                rule.getConditionValue2(), actualValue);
        result.put("triggered", triggered);
        return result;
    }

    /**
     * 条件评估核心逻辑。
     *
     * @param operator 操作符
     * @param expected 期望值 (条件值)
     * @param expected2 第二期望值 (区间上限, 可空)
     * @param actual   实际值
     * @return 是否命中
     */
    private boolean evaluateCondition(String operator, String expected, String expected2, Object actual) {
        if (actual == null) {
            return false;
        }
        String actualStr = String.valueOf(actual);
        switch (operator) {
            case "EQ":
                return actualStr.equals(expected);
            case "NE":
                return !actualStr.equals(expected);
            case "GT":
                return toDouble(actual) > toDouble(expected);
            case "GTE":
                return toDouble(actual) >= toDouble(expected);
            case "LT":
                return toDouble(actual) < toDouble(expected);
            case "LTE":
                return toDouble(actual) <= toDouble(expected);
            case "CONTAINS":
                return actualStr.contains(expected);
            case "NOT_CONTAINS":
                return !actualStr.contains(expected);
            case "IN":
                return Arrays.asList(expected.split(",")).contains(actualStr.trim());
            case "NOT_IN":
                return !Arrays.asList(expected.split(",")).contains(actualStr.trim());
            case "REGEX":
            case "MATCH":
                try {
                    return Pattern.matches(expected, actualStr);
                } catch (Exception e) {
                    return false;
                }
            default:
                return false;
        }
    }

    /**
     * 安全转换为 double。
     *
     * @param value 原始值
     * @return double 值
     */
    private double toDouble(Object value) {
        if (value == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * 对象序列化为 JSON 字符串。
     *
     * @param obj 目标对象
     * @return JSON 字符串
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("JSON 序列化失败: {}", e.getMessage());
            return "{}";
        }
    }
}