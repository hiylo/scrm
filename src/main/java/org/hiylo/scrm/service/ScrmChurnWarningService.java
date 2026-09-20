/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmChurnWarningService.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.dto.ScrmChurnRecoveryDto;
import org.hiylo.scrm.dto.ScrmChurnRuleDto;
import org.hiylo.scrm.dto.ScrmChurnScanDto;
import org.hiylo.scrm.entity.ScrmChurnRecoveryEntity;
import org.hiylo.scrm.entity.ScrmChurnRuleEntity;
import org.hiylo.scrm.entity.ScrmChurnWarningEntity;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmChurnRecoveryRepository;
import org.hiylo.scrm.repository.ScrmChurnRuleRepository;
import org.hiylo.scrm.repository.ScrmChurnWarningRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * SCRM 客户流失预警服务。
 * <p>
 * 承载客户流失风险预测与预警的核心能力: 流失规则增删改查与启用禁用, 客户流失风险扫描
 * (单客户 / 批量 / 按风险等级), 风险分计算与等级判定, 预警动作执行, 预警处理 (解决 / 忽略 /
 * 升级), 挽留记录管理与激活标记, 流失统计与高风险客户分析。所有写操作写入归属账号实现数据隔离。
 * </p>
 * <p>
 * 扫描流程: 加载账号启用规则 (priority ASC) → 逐条评估条件 → 命中规则收集 → 综合计算风险分 →
 * 确定风险等级 → 冷却期检查 → 创建预警 → 执行动作。条件评估支持 gt/lt/eq/between 操作符,
 * 可用字段: lastInteractionDays / noInteractionDays / totalInteractions / lifecycle /
 * customerDays / orderFrequency。
 * </p>
 * <p>
 * 风险分计算: 取命中的最高风险等级规则的等级分值 (HIGH=80, MEDIUM=50, LOW=25), 叠加命中规则数
 * 加分 (每条 +5, 上限 100)。风险等级判定: ≥75 HIGH, ≥45 MEDIUM, 否则 LOW。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmChurnWarningService {

    /** 默认启用状态 */
    private static final boolean DEFAULT_ENABLED = true;

    /** 默认优先级 */
    private static final int DEFAULT_PRIORITY = 0;

    /** 默认冷却天数 */
    private static final int DEFAULT_COOLDOWN_DAYS = 7;

    /** 默认条件类型 */
    private static final String DEFAULT_CONDITION_TYPE = "ALL";

    /** 默认动作参数 (空 JSON 对象) */
    private static final String DEFAULT_ACTION_PARAMS = "{}";

    /** 默认匹配次数初值 */
    private static final int DEFAULT_MATCH_COUNT = 0;

    /** 默认扫描回溯天数 */
    private static final int DEFAULT_DAYS_BACK = 30;

    /** 风险等级: 高 */
    private static final String RISK_LEVEL_HIGH = "HIGH";
    /** 风险等级: 中 */
    private static final String RISK_LEVEL_MEDIUM = "MEDIUM";
    /** 风险等级: 低 */
    private static final String RISK_LEVEL_LOW = "LOW";

    /** 风险等级分值: HIGH=80 */
    private static final int SCORE_HIGH = 80;
    /** 风险等级分值: MEDIUM=50 */
    private static final int SCORE_MEDIUM = 50;
    /** 风险等级分值: LOW=25 */
    private static final int SCORE_LOW = 25;

    /** 风险等级判定阈值: ≥75 为 HIGH */
    private static final double THRESHOLD_HIGH = 75.0;
    /** 风险等级判定阈值: ≥45 为 MEDIUM */
    private static final double THRESHOLD_MEDIUM = 45.0;

    /** 每条命中规则加分 */
    private static final int PER_RULE_BONUS = 5;

    /** 风险分上限 */
    private static final double MAX_RISK_SCORE = 100.0;

    /** 预警状态: 待处理 */
    private static final String STATUS_ACTIVE = "ACTIVE";
    /** 预警状态: 已解决 */
    private static final String STATUS_RESOLVED = "RESOLVED";
    /** 预警状态: 已忽略 */
    private static final String STATUS_IGNORED = "IGNORED";
    /** 预警状态: 已升级 */
    private static final String STATUS_ESCALATED = "ESCALATED";

    /** 条件类型: 全部满足 */
    private static final String CONDITION_TYPE_ALL = "ALL";
    /** 条件类型: 任一满足 */
    private static final String CONDITION_TYPE_ANY = "ANY";

    /** 预警动作: 通知负责人 */
    private static final String ACTION_NOTIFY_ASSIGNEE = "NOTIFY_ASSIGNEE";
    /** 预警动作: 创建跟进任务 */
    private static final String ACTION_CREATE_FOLLOW_UP = "CREATE_FOLLOW_UP";
    /** 预警动作: 触发群发 */
    private static final String ACTION_TRIGGER_MASS_SEND = "TRIGGER_MASS_SEND";
    /** 预警动作: 添加标签 */
    private static final String ACTION_ADD_TAG = "ADD_TAG";
    /** 预警动作: 变更生命周期 */
    private static final String ACTION_CHANGE_LIFECYCLE = "CHANGE_LIFECYCLE";
    /** 预警动作: Webhook 回调 */
    private static final String ACTION_WEBHOOK = "WEBHOOK";

    /** 默认操作人 (请求头未透传时使用) */
    private static final String DEFAULT_OPERATOR = "scrm-system";

    /** 合法的风险等级 */
    private static final List<String> VALID_RISK_LEVELS = List.of(RISK_LEVEL_HIGH, RISK_LEVEL_MEDIUM, RISK_LEVEL_LOW);

    /** 合法的条件类型 */
    private static final List<String> VALID_CONDITION_TYPES = List.of(CONDITION_TYPE_ALL, CONDITION_TYPE_ANY);

    /** 合法的预警动作 */
    private static final List<String> VALID_ACTION_TYPES = List.of(
            ACTION_NOTIFY_ASSIGNEE, ACTION_CREATE_FOLLOW_UP, ACTION_TRIGGER_MASS_SEND,
            ACTION_ADD_TAG, ACTION_CHANGE_LIFECYCLE, ACTION_WEBHOOK);

    /** 合法的操作符 */
    private static final List<String> VALID_OPERATORS = List.of("eq", "gt", "lt", "between");

    /** 可用条件字段 */
    private static final List<String> AVAILABLE_FIELDS = List.of(
            "lastInteractionDays", "noInteractionDays", "totalInteractions",
            "lifecycle", "customerDays", "orderFrequency");

    /** 流失规则数据访问层 */
    private final ScrmChurnRuleRepository ruleRepository;

    /** 流失预警数据访问层 */
    private final ScrmChurnWarningRepository warningRepository;

    /** 挽留记录数据访问层 */
    private final ScrmChurnRecoveryRepository recoveryRepository;

    /** 客户数据访问层 (查询客户属性用于规则评估) */
    private final ScrmCustomerRepository customerRepository;

    /** JSON 解析器 (解析 conditions / actionParams / riskFactors) */
    private final ObjectMapper objectMapper;

    // ============================================================
    // 规则管理
    // ============================================================

    /**
     * 创建流失预警规则。
     * <p>校验 riskLevel / actionType / conditions 合法性后写入归属账号 ID 持久化,
     * conditionType / cooldownDays / priority / enabled 缺省时填默认值。</p>
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmChurnRuleEntity createRule(ScrmChurnRuleDto dto) throws ScrmException {
        validateRuleDto(dto, false);
        ScrmChurnRuleEntity entity = new ScrmChurnRuleEntity();
        entity.setRuleName(dto.getRuleName());
        entity.setDescription(dto.getDescription());
        entity.setRiskLevel(dto.getRiskLevel());
        entity.setConditionType(dto.getConditionType() != null ? dto.getConditionType() : DEFAULT_CONDITION_TYPE);
        entity.setConditions(dto.getConditions());
        entity.setActionType(dto.getActionType());
        entity.setActionParams(dto.getActionParams() != null ? dto.getActionParams() : DEFAULT_ACTION_PARAMS);
        entity.setCooldownDays(dto.getCooldownDays() != null ? dto.getCooldownDays() : DEFAULT_COOLDOWN_DAYS);
        entity.setPriority(dto.getPriority() != null ? dto.getPriority() : DEFAULT_PRIORITY);
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : DEFAULT_ENABLED);
        entity.setMatchCount(DEFAULT_MATCH_COUNT);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = ruleRepository.save(entity);
        log.info("创建流失预警规则: id={}, ruleName={}, riskLevel={}, actionType={}",
                entity.getId(), entity.getRuleName(), entity.getRiskLevel(), entity.getActionType());
        return entity;
    }

    /**
     * 更新流失预警规则（字段非空才覆盖）。
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 参数非法
     */
    @Transactional
    public ScrmChurnRuleEntity updateRule(Long id, ScrmChurnRuleDto dto) throws ScrmException {
        ScrmChurnRuleEntity entity = findRuleOrThrow(id);
        validateRuleDto(dto, true);
        if (dto.getRuleName() != null) entity.setRuleName(dto.getRuleName());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getRiskLevel() != null) entity.setRiskLevel(dto.getRiskLevel());
        if (dto.getConditionType() != null) entity.setConditionType(dto.getConditionType());
        if (dto.getConditions() != null) entity.setConditions(dto.getConditions());
        if (dto.getActionType() != null) entity.setActionType(dto.getActionType());
        if (dto.getActionParams() != null) entity.setActionParams(dto.getActionParams());
        if (dto.getCooldownDays() != null) entity.setCooldownDays(dto.getCooldownDays());
        if (dto.getPriority() != null) entity.setPriority(dto.getPriority());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = ruleRepository.save(entity);
        log.info("更新流失预警规则: id={}, ruleName={}", entity.getId(), entity.getRuleName());
        return entity;
    }

    /**
     * 删除流失预警规则。
     *
     * @param id 规则 ID
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public void deleteRule(Long id) throws ScrmException {
        ScrmChurnRuleEntity entity = findRuleOrThrow(id);
        ruleRepository.delete(entity);
        log.info("删除流失预警规则: id={}, ruleName={}", id, entity.getRuleName());
    }

    /**
     * 查询规则详情。
     *
     * @param id 规则 ID
     * @return 规则实体
     * @throws ScrmException 规则不存在
     */
    @Transactional(readOnly = true)
    public ScrmChurnRuleEntity getRule(Long id) throws ScrmException {
        return findRuleOrThrow(id);
    }

    /**
     * 分页查询规则, 支持按风险等级、启用状态与关键字过滤。
     *
     * @param riskLevel 风险等级过滤（可空）
     * @param enabled   启用状态过滤（可空）
     * @param keyword   规则名称关键字模糊匹配（可空）
     * @param pageable  分页参数
     * @return 规则分页结果 (按 priority ASC, createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmChurnRuleEntity> listRules(String riskLevel, Boolean enabled, String keyword, Pageable pageable) {
        Specification<ScrmChurnRuleEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (riskLevel != null && !riskLevel.isBlank()) {
                predicates.add(cb.equal(root.get("riskLevel"), riskLevel));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(root.get("ruleName"), "%" + keyword + "%"));
            }
            query.orderBy(cb.asc(root.get("priority")), cb.desc(root.get("createTime")));
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
    public ScrmChurnRuleEntity enableRule(Long id) throws ScrmException {
        ScrmChurnRuleEntity entity = findRuleOrThrow(id);
        entity.setEnabled(true);
        entity = ruleRepository.save(entity);
        log.info("启用流失预警规则: id={}, ruleName={}", id, entity.getRuleName());
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
    public ScrmChurnRuleEntity disableRule(Long id) throws ScrmException {
        ScrmChurnRuleEntity entity = findRuleOrThrow(id);
        entity.setEnabled(false);
        entity = ruleRepository.save(entity);
        log.info("禁用流失预警规则: id={}, ruleName={}", id, entity.getRuleName());
        return entity;
    }

    // ============================================================
    // 扫描
    // ============================================================

    /**
     * 扫描单客户流失风险。
     * <p>
     * 流程:
     * <ol>
     *   <li>校验客户存在且归属当前账号</li>
     *   <li>构建客户上下文: lastInteractionDays / noInteractionDays / totalInteractions /
     *       lifecycle / customerDays / orderFrequency</li>
     *   <li>加载启用规则 (priority ASC), 逐条评估, 收集命中规则</li>
     *   <li>无命中返回 null; 有命中则计算综合风险分, 确定风险等级</li>
     *   <li>取最高优先级命中规则的动作类型作为预警动作, 执行冷却期检查</li>
     *   <li>创建预警记录, 增量更新规则匹配统计, 执行预警动作</li>
     * </ol>
     * </p>
     *
     * @param customerId 客户 ID
     * @return 创建的预警 (无命中返回 null)
     * @throws ScrmException 客户不存在
     */
    @Transactional
    public ScrmChurnWarningEntity scanCustomer(Long customerId) throws ScrmException {
        ScrmCustomerEntity customer = findCustomerOrThrow(customerId);
        Map<String, Object> context = buildCustomerContext(customer);
        // 加载启用规则并按优先级升序评估
        List<ScrmChurnRuleEntity> rules = ruleRepository.findByEnabledTrueOrderByPriorityAsc();
        List<ScrmChurnRuleEntity> matchedRules = new ArrayList<>();
        List<Map<String, Object>> riskFactors = new ArrayList<>();
        for (ScrmChurnRuleEntity rule : rules) {
            try {
                if (!evaluateRule(rule, context)) {
                    continue;
                }
                matchedRules.add(rule);
                Map<String, Object> factor = new LinkedHashMap<>();
                factor.put("factor", rule.getRuleName());
                factor.put("value", rule.getRiskLevel());
                factor.put("detail", "规则命中: " + rule.getRuleName());
                riskFactors.add(factor);
            } catch (Exception e) {
                log.warn("流失规则评估异常, 跳过: ruleId={}, err={}", rule.getId(), e.getMessage());
            }
        }
        if (matchedRules.isEmpty()) {
            log.debug("客户无流失风险命中: customerId={}", customerId);
            return null;
        }
        // 计算综合风险分与等级
        double riskScore = calculateRiskScore(matchedRules);
        String riskLevel = determineRiskLevel(riskScore);
        // 取最高优先级 (列表已按 priority ASC, 第一个为最高优先级)
        ScrmChurnRuleEntity primaryRule = matchedRules.get(0);
        // 冷却期检查: 同一客户在冷却期内已有 ACTIVE 预警则跳过
        int cooldownDays = primaryRule.getCooldownDays() != null ? primaryRule.getCooldownDays()
                : DEFAULT_COOLDOWN_DAYS;
        LocalDateTime since = LocalDateTime.now().minusDays(cooldownDays);
        Optional<ScrmChurnWarningEntity> existing = warningRepository
                .findFirstByCustomerIdAndStatusAndDetectedAtAfterOrderByDetectedAtDesc(
                         customerId, STATUS_ACTIVE, since);
        if (existing.isPresent()) {
            log.debug("客户在冷却期内已有 ACTIVE 预警, 跳过: customerId={}, existingWarningId={}",
                    customerId, existing.get().getId());
            return null;
        }
        // 创建预警
        ScrmChurnWarningEntity warning = new ScrmChurnWarningEntity();
        warning.setCustomerId(customerId);
        warning.setCustomerName(customer.getNickname());
        warning.setRuleId(primaryRule.getId());
        warning.setRuleName(primaryRule.getRuleName());
        warning.setRiskLevel(riskLevel);
        warning.setRiskScore(riskScore);
        warning.setRiskFactors(toJson(riskFactors));
        warning.setStatus(STATUS_ACTIVE);
        warning.setActionType(primaryRule.getActionType());
        warning.setDetectedAt(LocalDateTime.now());
        warning.setLastInteractionAt(customer.getLastInteractionAt());
        warning = warningRepository.save(warning);
        // 增量更新命中规则的匹配统计
        for (ScrmChurnRuleEntity rule : matchedRules) {
            try {
                ruleRepository.incrementMatchCount(rule.getId(), LocalDateTime.now());
            } catch (Exception e) {
                log.warn("更新规则匹配统计失败, 忽略: ruleId={}, err={}", rule.getId(), e.getMessage());
            }
        }
        // 执行预警动作
        executeAction(warning);
        log.info("客户流失预警创建: customerId={}, warningId={}, riskLevel={}, riskScore={}, matchedRules={}",
                customerId, warning.getId(), riskLevel, riskScore, matchedRules.size());
        return warning;
    }

    /**
     * 批量扫描客户流失风险。
     * <p>优先使用 dto.customerIds; 未提供则按 daysBack 取最近有互动的客户列表。
     * 单个客户失败跳过, 不阻断其他客户。</p>
     *
     * @param dto 批量扫描请求
     * @return 扫描结果: {total, warned, failed}
     */
    @Transactional
    public Map<String, Integer> scanAll(ScrmChurnScanDto dto) {
        if (dto == null) {
            dto = new ScrmChurnScanDto();
        }
        List<Long> customerIds;
        if (dto.getCustomerIds() != null && !dto.getCustomerIds().isEmpty()) {
            customerIds = dto.getCustomerIds();
        } else {
            int daysBack = dto.getDaysBack() != null ? dto.getDaysBack() : DEFAULT_DAYS_BACK;
            LocalDateTime since = LocalDateTime.now().minusDays(daysBack);
            List<ScrmCustomerEntity> customers = customerRepository.findAll().stream()
                    .filter(c -> c.getLastInteractionAt() != null && c.getLastInteractionAt().isAfter(since))
                    .collect(Collectors.toList());
            customerIds = customers.stream().map(ScrmCustomerEntity::getId).collect(Collectors.toList());
        }
        int warned = 0;
        int failed = 0;
        for (Long customerId : customerIds) {
            if (customerId == null) {
                continue;
            }
            try {
                ScrmChurnWarningEntity warning = scanCustomer(customerId);
                if (warning != null) {
                    warned++;
                }
            } catch (Exception e) {
                failed++;
                log.warn("批量扫描客户流失风险失败, 跳过: customerId={}, err={}", customerId, e.getMessage());
            }
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("total", customerIds.size());
        result.put("warned", warned);
        result.put("failed", failed);
        log.info("批量扫描流失风险完成:, total={}, warned={}, failed={}", customerIds.size(), warned, failed);
        return result;
    }

    /**
     * 按风险等级扫描客户。
     * <p>仅使用指定等级的启用规则进行扫描, 客户范围同 {@link #scanAll}。
     * 单个客户失败跳过, 不阻断其他客户。</p>
     *
     * @param riskLevel 风险等级: HIGH / MEDIUM / LOW
     * @return 扫描结果: {total, warned, failed}
     * @throws ScrmException 风险等级非法
     */
    @Transactional
    public Map<String, Integer> scanByLevel(String riskLevel) throws ScrmException {
        if (riskLevel == null || !VALID_RISK_LEVELS.contains(riskLevel)) {
            throw ScrmException.badRequest(
                    "风险等级非法: " + riskLevel + ", 仅支持 " + VALID_RISK_LEVELS);
        }
        // 校验该等级是否有启用规则, 无则跳过扫描
        List<ScrmChurnRuleEntity> rules = ruleRepository
                .findByRiskLevelAndEnabledTrueOrderByPriorityAsc(riskLevel);
        if (rules.isEmpty()) {
            log.info("指定等级无启用规则, 跳过扫描:, riskLevel={}", riskLevel);
            Map<String, Integer> empty = new LinkedHashMap<>();
            empty.put("total", 0);
            empty.put("warned", 0);
            empty.put("failed", 0);
            return empty;
        }
        // 取所有客户逐一扫描 (扫描时全部规则仍会评估, 仅当命中等级规则的预警才被计入)
        // 注: scanCustomer 内部会评估全部启用规则, 此处仅在统计层按等级过滤。
        List<ScrmCustomerEntity> customers = customerRepository.findAll();
        int warned = 0;
        int failed = 0;
        for (ScrmCustomerEntity customer : customers) {
            try {
                ScrmChurnWarningEntity warning = scanCustomer(customer.getId());
                if (warning != null && riskLevel.equals(warning.getRiskLevel())) {
                    warned++;
                }
            } catch (Exception e) {
                failed++;
                log.warn("按等级扫描客户流失风险失败, 跳过: customerId={}, err={}",
                        customer.getId(), e.getMessage());
            }
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("total", customers.size());
        result.put("warned", warned);
        result.put("failed", failed);
        log.info("按等级扫描流失风险完成:, riskLevel={}, total={}, warned={}, failed={}", riskLevel, customers.size(), warned, failed);
        return result;
    }

    // ============================================================
    // 预警处理
    // ============================================================

    /**
     * 查询预警详情。
     *
     * @param id 预警 ID
     * @return 预警实体
     * @throws ScrmException 预警不存在
     */
    @Transactional(readOnly = true)
    public ScrmChurnWarningEntity getWarning(Long id) throws ScrmException {
        return findWarningOrThrow(id);
    }

    /**
     * 分页查询预警, 支持按状态、风险等级、负责人与时间范围过滤。
     *
     * @param status     预警状态过滤（可空）
     * @param riskLevel  风险等级过滤（可空）
     * @param assigneeId 负责人 ID 过滤（可空）
     * @param startTime  检测时间起始 (含, 可空)
     * @param endTime    检测时间截止 (含, 可空)
     * @param pageable   分页参数
     * @return 预警分页结果 (按 detectedAt DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmChurnWarningEntity> listWarnings(String status, String riskLevel, String assigneeId,
                                                      LocalDateTime startTime, LocalDateTime endTime,
                                                      Pageable pageable) {
        Specification<ScrmChurnWarningEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (riskLevel != null && !riskLevel.isBlank()) {
                predicates.add(cb.equal(root.get("riskLevel"), riskLevel));
            }
            if (assigneeId != null && !assigneeId.isBlank()) {
                predicates.add(cb.equal(root.get("assigneeId"), assigneeId));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("detectedAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("detectedAt"), endTime));
            }
            query.orderBy(cb.desc(root.get("detectedAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return warningRepository.findAll(spec, pageable);
    }

    /**
     * 标记预警为已解决。
     * <p>状态须为 ACTIVE / ESCALATED, 已解决 / 已忽略不允许重复处理。</p>
     *
     * @param id            预警 ID
     * @param resolutionNote 处理说明
     * @return 更新后的预警
     * @throws ScrmException 预警不存在 / 状态非法
     */
    @Transactional
    public ScrmChurnWarningEntity resolveWarning(Long id, String resolutionNote) throws ScrmException {
        ScrmChurnWarningEntity entity = findWarningOrThrow(id);
        if (STATUS_RESOLVED.equals(entity.getStatus()) || STATUS_IGNORED.equals(entity.getStatus())) {
            throw ScrmException.conflict("预警已处理, 不允许重复操作: id=" + id);
        }
        entity.setStatus(STATUS_RESOLVED);
        entity.setResolutionNote(resolutionNote);
        entity.setResolvedAt(LocalDateTime.now());
        entity.setResolvedBy(currentOperator());
        entity = warningRepository.save(entity);
        log.info("流失预警已标记为已解决: id={}, operator={}", id, entity.getResolvedBy());
        return entity;
    }

    /**
     * 忽略预警。
     * <p>状态须为 ACTIVE / ESCALATED, 已解决 / 已忽略不允许重复处理。</p>
     *
     * @param id     预警 ID
     * @param reason 忽略原因
     * @return 更新后的预警
     * @throws ScrmException 预警不存在 / 状态非法
     */
    @Transactional
    public ScrmChurnWarningEntity ignoreWarning(Long id, String reason) throws ScrmException {
        ScrmChurnWarningEntity entity = findWarningOrThrow(id);
        if (STATUS_RESOLVED.equals(entity.getStatus()) || STATUS_IGNORED.equals(entity.getStatus())) {
            throw ScrmException.conflict("预警已处理, 不允许重复操作: id=" + id);
        }
        entity.setStatus(STATUS_IGNORED);
        entity.setResolutionNote(reason);
        entity.setResolvedAt(LocalDateTime.now());
        entity.setResolvedBy(currentOperator());
        entity = warningRepository.save(entity);
        log.info("流失预警已标记为已忽略: id={}, operator={}", id, entity.getResolvedBy());
        return entity;
    }

    /**
     * 升级预警处理 (通常用于风险加剧后转高级处理)。
     * <p>状态须为 ACTIVE, 已终态 (RESOLVED / IGNORED) 不允许升级。</p>
     *
     * @param id     预警 ID
     * @param reason 升级原因
     * @return 更新后的预警
     * @throws ScrmException 预警不存在 / 状态非法
     */
    @Transactional
    public ScrmChurnWarningEntity escalateWarning(Long id, String reason) throws ScrmException {
        ScrmChurnWarningEntity entity = findWarningOrThrow(id);
        if (STATUS_RESOLVED.equals(entity.getStatus()) || STATUS_IGNORED.equals(entity.getStatus())) {
            throw ScrmException.conflict("预警已处理, 不允许升级: id=" + id);
        }
        entity.setStatus(STATUS_ESCALATED);
        entity.setResolutionNote(reason);
        entity.setResolvedAt(LocalDateTime.now());
        entity.setResolvedBy(currentOperator());
        entity = warningRepository.save(entity);
        log.info("流失预警已升级处理: id={}, operator={}", id, entity.getResolvedBy());
        return entity;
    }

    // ============================================================
    // 挽留记录
    // ============================================================

    /**
     * 创建挽留记录。
     * <p>校验关联预警存在, 客户字段缺省时从预警冗余填充。actionExecutedAt 缺省取当前时间,
     * customerResponded / reactivated 缺省 false。</p>
     *
     * @param dto 挽留记录参数
     * @return 创建后的挽留记录
     * @throws ScrmException 关联预警不存在
     */
    @Transactional
    public ScrmChurnRecoveryEntity createRecovery(ScrmChurnRecoveryDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("挽留记录参数不能为空");
        }
        ScrmChurnWarningEntity warning = findWarningOrThrow(dto.getWarningId());
        ScrmChurnRecoveryEntity entity = new ScrmChurnRecoveryEntity();
        entity.setWarningId(dto.getWarningId());
        entity.setCustomerId(dto.getCustomerId() != null ? dto.getCustomerId() : warning.getCustomerId());
        entity.setCustomerName(dto.getCustomerName() != null ? dto.getCustomerName() : warning.getCustomerName());
        entity.setRecoveryAction(dto.getRecoveryAction());
        entity.setActionDetail(dto.getActionDetail());
        entity.setActionExecutedAt(dto.getActionExecutedAt() != null ? dto.getActionExecutedAt() : LocalDateTime.now());
        entity.setExecutedBy(dto.getExecutedBy());
        entity.setResult(dto.getResult());
        entity.setCustomerResponded(dto.getCustomerResponded() != null ? dto.getCustomerResponded() : false);
        entity.setResponseAt(dto.getResponseAt());
        entity.setReactivated(dto.getReactivated() != null ? dto.getReactivated() : false);
        entity.setNotes(dto.getNotes());
        entity = recoveryRepository.save(entity);
        log.info("创建挽留记录: id={}, warningId={}, customerId={}, action={}, result={}",
                entity.getId(), entity.getWarningId(), entity.getCustomerId(),
                entity.getRecoveryAction(), entity.getResult());
        return entity;
    }

    /**
     * 分页查询挽留记录, 支持按预警 ID 或客户 ID 过滤 (优先 warningId)。
     *
     * @param warningId  预警 ID 过滤（可空, 优先级高）
     * @param customerId 客户 ID 过滤（可空, warningId 为空时生效）
     * @param pageable   分页参数
     * @return 挽留记录分页结果 (按 actionExecutedAt DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmChurnRecoveryEntity> getRecoveries(Long warningId, Long customerId, Pageable pageable) {
        if (warningId != null) {
            return recoveryRepository.findByWarningIdOrderByActionExecutedAtDesc(
                     warningId, pageable);
        }
        if (customerId != null) {
            return recoveryRepository.findByCustomerIdOrderByActionExecutedAtDesc(
                     customerId, pageable);
        }
        // 无过滤条件, 全部数据分页查询
        Specification<ScrmChurnRecoveryEntity> spec = (root, query, cb) -> {
            query.orderBy(cb.desc(root.get("actionExecutedAt")));
            return cb.and();
        };
        return recoveryRepository.findAll(spec, pageable);
    }

    /**
     * 标记挽留记录为已激活, 并同步将关联预警标记为已解决。
     *
     * @param recoveryId 挽留记录 ID
     * @param notes      备注
     * @return 更新后的挽留记录
     * @throws ScrmException 挽留记录不存在
     */
    @Transactional
    public ScrmChurnRecoveryEntity markReactivated(Long recoveryId, String notes) throws ScrmException {
        ScrmChurnRecoveryEntity entity = findRecoveryOrThrow(recoveryId);
        entity.setReactivated(true);
        entity.setCustomerResponded(true);
        if (entity.getResponseAt() == null) {
            entity.setResponseAt(LocalDateTime.now());
        }
        if (notes != null) {
            entity.setNotes(notes);
        }
        entity = recoveryRepository.save(entity);
        // 同步将关联预警标记为已解决 (若仍为 ACTIVE)
        try {
            ScrmChurnWarningEntity warning = warningRepository.findById(entity.getWarningId()).orElse(null);

        } catch (Exception e) {
            log.warn("同步标记关联预警为已解决失败, 忽略: warningId={}, err={}",
                    entity.getWarningId(), e.getMessage());
        }
        log.info("挽留记录已标记为已激活: recoveryId={}, warningId={}", recoveryId, entity.getWarningId());
        return entity;
    }

    // ============================================================
    // 评估
    // ============================================================

    /**
     * 评估规则是否匹配客户上下文。
     * <p>解析 conditions JSON, 按 conditionType (ALL/ANY) 评估。条件为空数组视为全部匹配。</p>
     *
     * @param rule            规则实体
     * @param customerContext 客户属性快照
     * @return 规则是否匹配
     */
    public boolean evaluateRule(ScrmChurnRuleEntity rule, Map<String, Object> customerContext) {
        List<Map<String, Object>> conditions = parseConditions(rule.getConditions());
        if (conditions.isEmpty()) {
            return true;
        }
        boolean all = CONDITION_TYPE_ALL.equals(rule.getConditionType());
        for (Map<String, Object> condition : conditions) {
            String field = (String) condition.get("field");
            String operator = (String) condition.get("operator");
            Object value = condition.get("value");
            Object fieldValue = customerContext != null ? customerContext.get(field) : null;
            boolean matched = evaluateCondition(fieldValue, operator, value);
            if (!matched && all) {
                return false;
            }
            if (matched && !all) {
                return true;
            }
        }
        return all;
    }

    /**
     * 计算综合风险分 (0-100)。
     * <p>取命中规则中最高风险等级对应的分值 (HIGH=80, MEDIUM=50, LOW=25),
     * 叠加命中规则数加分 (每条 +5), 上限 100。</p>
     *
     * @param matchedRules 命中规则列表
     * @return 综合风险分
     */
    public double calculateRiskScore(List<ScrmChurnRuleEntity> matchedRules) {
        if (matchedRules == null || matchedRules.isEmpty()) {
            return 0.0;
        }
        int maxLevelScore = 0;
        for (ScrmChurnRuleEntity rule : matchedRules) {
            int levelScore = levelToScore(rule.getRiskLevel());
            if (levelScore > maxLevelScore) {
                maxLevelScore = levelScore;
            }
        }
        double score = (double) maxLevelScore + (double) matchedRules.size() * PER_RULE_BONUS;
        return Math.min(score, MAX_RISK_SCORE);
    }

    /**
     * 根据风险分确定风险等级。
     * <p>≥75 HIGH, ≥45 MEDIUM, 否则 LOW。</p>
     *
     * @param score 风险分
     * @return 风险等级
     */
    public String determineRiskLevel(double score) {
        if (score >= THRESHOLD_HIGH) {
            return RISK_LEVEL_HIGH;
        }
        if (score >= THRESHOLD_MEDIUM) {
            return RISK_LEVEL_MEDIUM;
        }
        return RISK_LEVEL_LOW;
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 流失统计: 各风险等级预警数、各状态预警数、解决率、激活率、平均风险分。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getChurnStats(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> stats = new LinkedHashMap<>();
        // 各风险等级预警数
        List<Object[]> byRiskLevel = warningRepository.countByRiskLevel(startTime, endTime);
        Map<String, Long> riskLevelCount = new LinkedHashMap<>();
        for (String level : VALID_RISK_LEVELS) {
            riskLevelCount.put(level, 0L);
        }
        for (Object[] row : byRiskLevel) {
            String level = (String) row[0];
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            riskLevelCount.put(level, count);
        }
        stats.put("riskLevelCount", riskLevelCount);
        // 各状态预警数
        List<Object[]> byStatus = warningRepository.countByStatus(startTime, endTime);
        Map<String, Long> statusCount = new LinkedHashMap<>();
        statusCount.put(STATUS_ACTIVE, 0L);
        statusCount.put(STATUS_RESOLVED, 0L);
        statusCount.put(STATUS_IGNORED, 0L);
        statusCount.put(STATUS_ESCALATED, 0L);
        long total = 0L;
        for (Object[] row : byStatus) {
            String status = (String) row[0];
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            statusCount.put(status, count);
            total += count;
        }
        stats.put("statusCount", statusCount);
        stats.put("total", total);
        // 解决率
        long resolved = statusCount.getOrDefault(STATUS_RESOLVED, 0L);
        stats.put("resolveRate", total == 0 ? 0.0 : (double) resolved / total);
        // 激活率 (基于挽留记录)
        Object[] recoveryStats = recoveryRepository.countRecoveryStats(startTime, endTime);
        long recoveryTotal = 0L;
        long reactivated = 0L;
        if (recoveryStats != null && recoveryStats.length == 2) {
            recoveryTotal = recoveryStats[0] == null ? 0L : ((Number) recoveryStats[0]).longValue();
            reactivated = recoveryStats[1] == null ? 0L : ((Number) recoveryStats[1]).longValue();
        }
        stats.put("recoveryTotal", recoveryTotal);
        stats.put("reactivatedCount", reactivated);
        stats.put("reactivatedRate", recoveryTotal == 0 ? 0.0 : (double) reactivated / recoveryTotal);
        // 平均风险分
        Double avgScore = warningRepository.averageRiskScore(startTime, endTime);
        stats.put("averageRiskScore", avgScore != null ? avgScore : 0.0);
        return stats;
    }

    /**
     * 高风险客户列表: 按客户聚合 ACTIVE 预警的最大风险分, 取风险分最高的客户。
     *
     * @param pageable 分页参数
     * @return 高风险客户列表 [{customerId, customerName, maxRiskScore, riskLevel, latestDetectedAt}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAtRiskCustomers(Pageable pageable) {
        List<Object[]> rows = warningRepository.findAtRiskCustomers(pageable);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("customerId", row[0]);
            entry.put("customerName", row[1]);
            entry.put("maxRiskScore", row[2] == null ? 0.0 : ((Number) row[2]).doubleValue());
            entry.put("riskLevel", row[3]);
            entry.put("latestDetectedAt", row[4]);
            result.add(entry);
        }
        return result;
    }

    /**
     * 挽留成功率: 时间区间内挽留动作总数与激活数。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return 挽留成功率统计 {total, reactivated, rate}
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getRecoveryRate(LocalDateTime startTime, LocalDateTime endTime) {
        Object[] stats = recoveryRepository.countRecoveryStats(startTime, endTime);
        long total = 0L;
        long reactivated = 0L;
        if (stats != null && stats.length == 2) {
            total = stats[0] == null ? 0L : ((Number) stats[0]).longValue();
            reactivated = stats[1] == null ? 0L : ((Number) stats[1]).longValue();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("reactivated", reactivated);
        result.put("rate", total == 0 ? 0.0 : (double) reactivated / total);
        return result;
    }

    // ============================================================
    // 动作执行
    // ============================================================

    /**
     * 执行预警动作 (模拟实现)。
     * <p>根据 {@link ScrmChurnWarningEntity#getActionType()} 分派不同动作:
     * NOTIFY_ASSIGNEE / CREATE_FOLLOW_UP / TRIGGER_MASS_SEND / ADD_TAG / CHANGE_LIFECYCLE / WEBHOOK。
     * 当前为模拟实现, 仅记录执行结果与时间, 不实际触发外部系统。</p>
     *
     * @param warning 预警实体
     */
    @Transactional
    public void executeAction(ScrmChurnWarningEntity warning) {
        String actionType = warning.getActionType();
        String result;
        switch (actionType == null ? "" : actionType) {
            case ACTION_NOTIFY_ASSIGNEE:
                result = "已通知负责人: " + (warning.getAssigneeName() != null ? warning.getAssigneeName() : "(未分配)");
                break;
            case ACTION_CREATE_FOLLOW_UP:
                result = "已创建跟进任务: customerId=" + warning.getCustomerId();
                break;
            case ACTION_TRIGGER_MASS_SEND:
                result = "已触发群发任务: customerId=" + warning.getCustomerId();
                break;
            case ACTION_ADD_TAG:
                result = "已添加流失风险标签: customerId=" + warning.getCustomerId();
                break;
            case ACTION_CHANGE_LIFECYCLE:
                result = "已变更生命周期为 DORMANT: customerId=" + warning.getCustomerId();
                break;
            case ACTION_WEBHOOK:
                result = "已触发 Webhook 回调: warningId=" + warning.getId();
                break;
            default:
                result = "未知动作类型, 跳过执行: " + actionType;
                break;
        }
        warning.setActionResult(result);
        warning.setActionExecutedAt(LocalDateTime.now());
        warningRepository.save(warning);
        log.info("执行流失预警动作: warningId={}, actionType={}, result={}",
                warning.getId(), actionType, result);
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 校验流失预警规则参数。
     * <p>
     * 创建场景 (partial=false): ruleName / riskLevel / conditions / actionType 必填。
     * 更新场景 (partial=true): 允许字段为空, 仅校验非空字段的合法性。conditions 非空时校验 JSON 可解析。
     * actionParams 缺省时填默认值 (创建场景)。
     * </p>
     *
     * @param dto     规则参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateRuleDto(ScrmChurnRuleDto dto, boolean partial) throws ScrmException {
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
        if (dto.getRiskLevel() != null) {
            if (!VALID_RISK_LEVELS.contains(dto.getRiskLevel())) {
                throw ScrmException.badRequest(
                        "风险等级非法: " + dto.getRiskLevel() + ", 仅支持 " + VALID_RISK_LEVELS);
            }
        } else if (!partial) {
            throw ScrmException.badRequest("风险等级不能为空");
        }
        if (dto.getConditionType() != null && !VALID_CONDITION_TYPES.contains(dto.getConditionType())) {
            throw ScrmException.badRequest(
                    "条件类型非法: " + dto.getConditionType() + ", 仅支持 " + VALID_CONDITION_TYPES);
        }
        if (dto.getActionType() != null) {
            if (!VALID_ACTION_TYPES.contains(dto.getActionType())) {
                throw ScrmException.badRequest(
                        "预警动作非法: " + dto.getActionType() + ", 仅支持 " + VALID_ACTION_TYPES);
            }
        } else if (!partial) {
            throw ScrmException.badRequest("预警动作不能为空");
        }
        if (dto.getConditions() != null) {
            if (dto.getConditions().isBlank()) {
                throw ScrmException.badRequest("条件 JSON 不能为空");
            }
            try {
                List<Map<String, Object>> parsed = objectMapper.readValue(
                        dto.getConditions(), new TypeReference<List<Map<String, Object>>>() {});
                for (Map<String, Object> condition : parsed) {
                    String field = (String) condition.get("field");
                    String operator = (String) condition.get("operator");
                    if (field == null || field.isBlank()) {
                        throw ScrmException.badRequest("条件字段不能为空: " + condition);
                    }
                    if (!AVAILABLE_FIELDS.contains(field)) {
                        throw ScrmException.badRequest(
                                "条件字段非法: " + field + ", 仅支持 " + AVAILABLE_FIELDS);
                    }
                    if (operator == null || !VALID_OPERATORS.contains(operator)) {
                        throw ScrmException.badRequest(
                                "操作符非法: " + operator + ", 仅支持 " + VALID_OPERATORS);
                    }
                }
            } catch (ScrmException e) {
                throw e;
            } catch (Exception e) {
                throw ScrmException.badRequest("条件 JSON 解析失败: " + e.getMessage());
            }
        } else if (!partial) {
            throw ScrmException.badRequest("条件 JSON 不能为空");
        }
        if (dto.getActionParams() != null) {
            try {
                objectMapper.readTree(dto.getActionParams());
            } catch (Exception e) {
                throw ScrmException.badRequest("动作参数 JSON 解析失败: " + e.getMessage());
            }
        }
    }

    /**
     * 构建客户上下文 (规则评估用)。
     * <p>lastInteractionDays / noInteractionDays 由客户 lastInteractionAt 计算 (无互动取 Long.MAX_VALUE),
     * customerDays 由客户 createTime 计算, lifecycle 取客户实体字段;
     * totalInteractions / orderFrequency 当前缺省 0 (待对接订单服务)。</p>
     *
     * @param customer 客户实体
     * @return 客户上下文 Map
     */
    private Map<String, Object> buildCustomerContext(ScrmCustomerEntity customer) {
        Map<String, Object> context = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now();
        // 最近互动距今天数 / 无互动天数
        if (customer.getLastInteractionAt() != null) {
            long days = ChronoUnit.DAYS.between(customer.getLastInteractionAt().toLocalDate(), now.toLocalDate());
            context.put("lastInteractionDays", days);
            context.put("noInteractionDays", days);
        } else {
            context.put("lastInteractionDays", Long.MAX_VALUE);
            context.put("noInteractionDays", Long.MAX_VALUE);
        }
        // 入客天数
        if (customer.getCreateTime() != null) {
            long days = ChronoUnit.DAYS.between(customer.getCreateTime().toLocalDate(), now.toLocalDate());
            context.put("customerDays", days);
        } else {
            context.put("customerDays", 0L);
        }
        // 生命周期
        context.put("lifecycle", customer.getLifecycle());
        // 累计互动次数 / 消费频率 (待对接, 当前缺省 0)
        context.put("totalInteractions", 0);
        context.put("orderFrequency", 0);
        return context;
    }

    /**
     * 评估单个条件。
     * <p>支持 eq/gt/lt/between 操作符, 自动处理类型转换。</p>
     *
     * @param fieldValue     客户属性值
     * @param operator       操作符
     * @param conditionValue 条件值
     * @return 条件是否满足
     */
    private boolean evaluateCondition(Object fieldValue, String operator, Object conditionValue) {
        if (fieldValue == null) {
            return false;
        }
        switch (operator) {
            case "eq":
                return toStringValue(fieldValue).equals(toStringValue(conditionValue));
            case "gt":
                return toDouble(fieldValue) > toDouble(conditionValue);
            case "lt":
                return toDouble(fieldValue) < toDouble(conditionValue);
            case "between":
                return isBetween(fieldValue, conditionValue);
            default:
                return false;
        }
    }

    /**
     * 判断字段值是否在区间内 (between 操作符)。
     * <p>条件值应为 [min, max] 二元数组, 区间两端均包含。</p>
     *
     * @param fieldValue    字段值
     * @param conditionValue 条件值 ([min, max])
     * @return 是否在区间内
     */
    private boolean isBetween(Object fieldValue, Object conditionValue) {
        if (conditionValue instanceof Collection<?> col && col.size() == 2) {
            Object[] arr = col.toArray();
            double min = toDouble(arr[0]);
            double max = toDouble(arr[1]);
            double val = toDouble(fieldValue);
            return val >= min && val <= max;
        }
        return false;
    }

    /**
     * 解析条件 JSON 为 List。
     *
     * @param conditionsJson 条件 JSON 字符串
     * @return 条件列表, 解析失败返回空列表
     */
    private List<Map<String, Object>> parseConditions(String conditionsJson) {
        try {
            return objectMapper.readValue(conditionsJson, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("条件 JSON 解析失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 风险等级转换为分值。
     *
     * @param riskLevel 风险等级
     * @return 分值 (HIGH=80, MEDIUM=50, LOW=25, 未知=0)
     */
    private int levelToScore(String riskLevel) {
        switch (riskLevel == null ? "" : riskLevel) {
            case RISK_LEVEL_HIGH:
                return SCORE_HIGH;
            case RISK_LEVEL_MEDIUM:
                return SCORE_MEDIUM;
            case RISK_LEVEL_LOW:
                return SCORE_LOW;
            default:
                return 0;
        }
    }

    /**
     * 将对象序列化为 JSON 字符串。
     *
     * @param obj 对象
     * @return JSON 字符串, 序列化失败返回 "[]"
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("对象序列化为 JSON 失败: {}", e.getMessage());
            return "[]";
        }
    }

    /**
     * 将对象转换为 double 数值。
     *
     * @param obj 对象
     * @return double 值, 不可转换时返回 0
     */
    private double toDouble(Object obj) {
        if (obj == null) {
            return 0;
        }
        if (obj instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(obj.toString());
        } catch (NumberFormatException e) {
            return 0;
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
     * 获取当前操作人 (优先从 UserContext 获取)。
     *
     * @return 操作人用户名
     */
    private String currentOperator() {
        String username = UserContext.getUsername();
        return username != null ? username : DEFAULT_OPERATOR;
    }

    /**
     * 按主键查询规则, 不存在抛异常, 并校验账号归属。
     *
     * @param id 规则 ID
     * @return 规则实体
     * @throws ScrmException 规则不存在
     */
    private ScrmChurnRuleEntity findRuleOrThrow(Long id) throws ScrmException {
        ScrmChurnRuleEntity entity = ruleRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "流失预警规则不存在: id=" + id));
        return entity;
    }

    /**
     * 按主键查询预警, 不存在抛异常, 并校验账号归属。
     *
     * @param id 预警 ID
     * @return 预警实体
     * @throws ScrmException 预警不存在
     */
    private ScrmChurnWarningEntity findWarningOrThrow(Long id) throws ScrmException {
        ScrmChurnWarningEntity entity = warningRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "流失预警不存在: id=" + id));
        return entity;
    }

    /**
     * 按主键查询挽留记录, 不存在抛异常, 并校验账号归属。
     *
     * @param id 挽留记录 ID
     * @return 挽留记录实体
     * @throws ScrmException 挽留记录不存在
     */
    private ScrmChurnRecoveryEntity findRecoveryOrThrow(Long id) throws ScrmException {
        ScrmChurnRecoveryEntity entity = recoveryRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "挽留记录不存在: id=" + id));
        return entity;
    }

    /**
     * 按主键查询客户, 不存在抛异常, 并校验账号归属。
     *
     * @param id 客户 ID
     * @return 客户实体
     * @throws ScrmException 客户不存在
     */
    private ScrmCustomerEntity findCustomerOrThrow(Long id) throws ScrmException {
        ScrmCustomerEntity customer = customerRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "客户不存在: id=" + id));
        return customer;
    }

}
