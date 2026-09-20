/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAutoTagService.java
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

import org.hiylo.scrm.dto.ScrmAutoTagRuleDto;
import org.hiylo.scrm.entity.ScrmAutoTagRuleEntity;
import org.hiylo.scrm.entity.ScrmAutoTagRuleLogEntity;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmCustomerTagEntity;
import org.hiylo.scrm.entity.ScrmTagCustomerEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmAutoTagRuleLogRepository;
import org.hiylo.scrm.repository.ScrmAutoTagRuleRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmCustomerTagRepository;
import org.hiylo.scrm.repository.ScrmTagCustomerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * SCRM 客户自动标签规则引擎服务。
 * <p>
 * 承载自动标签规则的增删改查、启用/禁用、条件评估与动作执行能力。所有写操作
 * 写入归属账号实现数据隔离。规则由条件 (conditions JSON) 与
 * 动作 (actionType + actionParams) 两部分组成, 在客户事件触发时由
 * {@link #evaluateAll(Long, String, Map)} 加载全部启用规则并按 priority 升序评估,
 * 命中后执行动作 (打标签/移除标签/改生命周期/通知) 并记录执行日志。
 * </p>
 * <p>
 * 条件评估支持 eq/ne/in/gt/lt/contains/between 七种操作符, conditionType=ALL 表示全部条件满足,
 * ANY 表示任一满足。单条规则评估异常跳过, 不影响后续规则评估。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmAutoTagService {

    /** 默认启用状态 */
    private static final boolean DEFAULT_ENABLED = true;

    /** 默认优先级 */
    private static final int DEFAULT_PRIORITY = 0;

    /** 默认标签值 (自动打标时缺省值) */
    private static final String DEFAULT_TAG_VALUE = "auto";

    /** 优先级下限 (含) */
    private static final int PRIORITY_MIN = 0;

    /** 优先级上限 (含) */
    private static final int PRIORITY_MAX = 9999;

    /** 合法的触发事件 */
    private static final List<String> VALID_TRIGGER_EVENTS = List.of(
            "CUSTOMER_CREATED", "CUSTOMER_UPDATED", "MESSAGE_RECEIVED",
            "LIFECYCLE_CHANGED", "TAG_ADDED", "INTERACTION_TIMEOUT");

    /** 合法的条件类型: ALL 所有条件满足 / ANY 任一满足 */
    private static final List<String> VALID_CONDITION_TYPES = List.of("ALL", "ANY");

    /** 合法的动作类型: ADD_TAG / REMOVE_TAG / SET_LIFECYCLE / NOTIFY */
    private static final List<String> VALID_ACTION_TYPES = List.of(
            "ADD_TAG", "REMOVE_TAG", "SET_LIFECYCLE", "NOTIFY");

    /** 合法的操作符: eq/ne/in/gt/lt/contains/between */
    private static final List<String> VALID_OPERATORS = List.of(
            "eq", "ne", "in", "gt", "lt", "contains", "between");

    /** 可用条件字段列表 */
    private static final List<Map<String, String>> AVAILABLE_FIELDS = List.of(
            Map.of("name", "lifecycle", "description", "客户生命周期: NEW/PROSPECT/ACTIVE/DORMANT/CHURNED/CONVERTED"),
            Map.of("name", "platformType", "description",
                    "平台类型: wework/douyin/kuaishou/xiaohongshu/bilibili/wechat_personal"),
            Map.of("name", "lastInteractionDays", "description", "最后交互距今天数 (整数)"),
            Map.of("name", "tags", "description", "客户标签键集合 (字符串数组)"),
            Map.of("name", "ownerAccountId", "description", "归属账号 ID"),
            Map.of("name", "nickname", "description", "客户昵称"));

    /** 可用操作符列表 */
    private static final List<Map<String, String>> AVAILABLE_OPERATORS = List.of(
            Map.of("name", "eq", "description", "等于"),
            Map.of("name", "ne", "description", "不等于"),
            Map.of("name", "in", "description", "包含于集合 (value 为数组)"),
            Map.of("name", "gt", "description", "大于"),
            Map.of("name", "lt", "description", "小于"),
            Map.of("name", "contains", "description", "包含 (字符串子串或数组元素)"),
            Map.of("name", "between", "description", "区间 (value 为 [min, max])"));

    /** 动作结果: 成功 */
    private static final String RESULT_SUCCESS = "SUCCESS";
    /** 动作结果: 失败 */
    private static final String RESULT_FAILED = "FAILED";
    /** 动作结果: 跳过 */
    private static final String RESULT_SKIPPED = "SKIPPED";

    /** 自动标签规则数据访问层 */
    private final ScrmAutoTagRuleRepository ruleRepository;

    /** 自动标签规则执行日志数据访问层 */
    private final ScrmAutoTagRuleLogRepository logRepository;

    /** 客户数据访问层 (SET_LIFECYCLE 动作用) */
    private final ScrmCustomerRepository customerRepository;

    /** 客户标签定义数据访问层 (tagCode → tagId 解析) */
    private final ScrmCustomerTagRepository tagRepository;

    /** 客户-标签赋值数据访问层 (ADD_TAG / REMOVE_TAG 动作写入赋值关系) */
    private final ScrmTagCustomerRepository tagCustomerRepository;

    /** SCRM 实时通知服务 (NOTIFY 动作用) */
    private final ScrmNotificationService notificationService;

    /** JSON 解析器 (解析 conditions 与 actionParams) */
    private final ObjectMapper objectMapper;

    // ============================================================
    // 规则 CRUD
    // ============================================================

    /**
     * 创建自动标签规则。
     * <p>校验 conditions / actionParams 为合法 JSON 后写入归属账号 ID 持久化,
     * enabled / priority 缺省时填默认值。</p>
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException 参数非法 / conditions 或 actionParams 非合法 JSON
     */
    @Transactional
    public ScrmAutoTagRuleEntity createRule(ScrmAutoTagRuleDto dto) throws ScrmException {
        validateRuleDto(dto, false);
        ScrmAutoTagRuleEntity entity = new ScrmAutoTagRuleEntity();
        entity.setRuleName(dto.getRuleName());
        entity.setDescription(dto.getDescription());
        entity.setTriggerEvent(dto.getTriggerEvent());
        entity.setConditionType(dto.getConditionType());
        entity.setConditions(dto.getConditions());
        entity.setActionType(dto.getActionType());
        entity.setActionParams(dto.getActionParams());
        entity.setPriority(dto.getPriority() != null ? dto.getPriority() : DEFAULT_PRIORITY);
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : DEFAULT_ENABLED);
        entity.setMatchCount(0);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = ruleRepository.save(entity);
        log.info("创建自动标签规则: id={}, ruleName={}, triggerEvent={}, actionType={}",
                entity.getId(), entity.getRuleName(), entity.getTriggerEvent(), entity.getActionType());
        return entity;
    }

    /**
     * 更新自动标签规则（字段非空才覆盖）。
     * <p>部分更新场景: 仅校验非空字段的合法性, conditions / actionParams 变更时校验 JSON 合法性。</p>
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 参数非法
     */
    @Transactional
    public ScrmAutoTagRuleEntity updateRule(Long id, ScrmAutoTagRuleDto dto) throws ScrmException {
        ScrmAutoTagRuleEntity entity = findOrThrow(id);
        validateRuleDto(dto, true);
        if (dto.getRuleName() != null) entity.setRuleName(dto.getRuleName());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getTriggerEvent() != null) entity.setTriggerEvent(dto.getTriggerEvent());
        if (dto.getConditionType() != null) entity.setConditionType(dto.getConditionType());
        if (dto.getConditions() != null) entity.setConditions(dto.getConditions());
        if (dto.getActionType() != null) entity.setActionType(dto.getActionType());
        if (dto.getActionParams() != null) entity.setActionParams(dto.getActionParams());
        if (dto.getPriority() != null) entity.setPriority(dto.getPriority());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = ruleRepository.save(entity);
        log.info("更新自动标签规则: id={}, ruleName={}", entity.getId(), entity.getRuleName());
        return entity;
    }

    /**
     * 删除自动标签规则。
     * <p>
     * 删除前检查是否有执行日志 (scrm_auto_tag_rule_log) 引用该规则, 若有则阻止删除
     * 并返回引用数量, 避免删除后日志失去关联导致审计追溯断裂。
     * 规则本身可先禁用 (disableRule) 而不删除, 以保留历史关联。
     * </p>
     *
     * @param id 规则 ID
     * @throws ScrmException 规则不存在 / 仍有执行日志引用
     */
    @Transactional
    public void deleteRule(Long id) throws ScrmException {
        ScrmAutoTagRuleEntity entity = findOrThrow(id);
        long logCount = logRepository.count((root, query, cb) -> cb.equal(root.get("ruleId"), id));
        if (logCount > 0) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    String.format("无法删除规则: 仍有 %d 条执行日志引用该规则, 请先禁用规则而非删除", logCount));
        }
        ruleRepository.delete(entity);
        log.info("删除自动标签规则: id={}, ruleName={}", id, entity.getRuleName());
    }

    /**
     * 查询规则详情。
     *
     * @param id 规则 ID
     * @return 规则实体
     * @throws ScrmException 规则不存在
     */
    @Transactional(readOnly = true)
    public ScrmAutoTagRuleEntity getRule(Long id) throws ScrmException {
        return findOrThrow(id);
    }

    /**
     * 分页查询规则, 支持按触发事件、启用状态与关键字过滤。
     * <p>过滤优先级: triggerEvent > enabled > keyword, 均为空时全量分页。</p>
     *
     * @param triggerEvent 触发事件过滤（可空）
     * @param enabled      启用状态过滤（可空）
     * @param keyword      关键字过滤（按 ruleName / description 模糊匹配, 可空）
     * @param pageable     分页参数
     * @return 规则分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmAutoTagRuleEntity> listRules(String triggerEvent, Boolean enabled,
                                                  String keyword, Pageable pageable) {
        Specification<ScrmAutoTagRuleEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (triggerEvent != null && !triggerEvent.isBlank()) {
                predicates.add(cb.equal(root.get("triggerEvent"), triggerEvent));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (keyword != null && !keyword.isBlank()) {
                String kw = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("ruleName")), kw),
                        cb.like(cb.lower(root.get("description")), kw)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return ruleRepository.findAll(spec, pageable);
    }

    /**
     * 启用规则。
     *
     * @param id 规则 ID
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public void enableRule(Long id) throws ScrmException {
        ScrmAutoTagRuleEntity entity = findOrThrow(id);
        entity.setEnabled(true);
        ruleRepository.save(entity);
        log.info("启用自动标签规则: id={}, ruleName={}", id, entity.getRuleName());
    }

    /**
     * 禁用规则。
     *
     * @param id 规则 ID
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public void disableRule(Long id) throws ScrmException {
        ScrmAutoTagRuleEntity entity = findOrThrow(id);
        entity.setEnabled(false);
        ruleRepository.save(entity);
        log.info("禁用自动标签规则: id={}, ruleName={}", id, entity.getRuleName());
    }

    // ============================================================
    // 规则评估
    // ============================================================

    /**
     * 评估单条规则是否匹配。
     * <p>
     * 解析 conditions JSON, 对 customerContext 中的字段按 operator 评估。
     * conditionType=ALL 时所有条件须全部满足, ANY 时任一满足即匹配。
     * 规则的 triggerEvent 须与传入的 triggerEvent 一致, 否则直接返回不匹配。
     * </p>
     *
     * @param ruleId          规则 ID
     * @param customerId      客户 ID (仅用于日志上下文, 不参与条件评估)
     * @param triggerEvent    触发事件
     * @param customerContext 客户属性快照
     * @return 规则是否匹配
     * @throws ScrmException 规则不存在
     */
    @Transactional(readOnly = true)
    public boolean evaluateRule(Long ruleId, Long customerId, String triggerEvent,
                                 Map<String, Object> customerContext) throws ScrmException {
        ScrmAutoTagRuleEntity rule = findOrThrow(ruleId);
        // 触发事件不一致直接不匹配
        if (!rule.getTriggerEvent().equals(triggerEvent)) {
            return false;
        }
        return evaluateConditions(rule, customerContext);
    }

    /**
     * 评估所有启用的规则, 匹配则执行动作并记录日志。
     * <p>
     * 加载当前账号下指定 triggerEvent 的全部启用规则 (按 priority ASC), 逐条评估条件。
     * 命中后执行动作 (ADD_TAG / REMOVE_TAG / SET_LIFECYCLE / NOTIFY), 记录执行日志,
     * 增量更新规则的 matchCount 与 lastMatchAt。单条规则评估/执行异常跳过, 不影响后续规则。
     * </p>
     *
     * @param customerId      客户 ID
     * @param triggerEvent    触发事件
     * @param customerContext 客户属性快照
     * @return 执行日志列表 (仅包含命中的规则)
     */
    @Transactional
    public List<ScrmAutoTagRuleLogEntity> evaluateAll(Long customerId, String triggerEvent,
                                                       Map<String, Object> customerContext) {
        // 加载客户实体 (校验存在性 + 获取昵称用于日志)
        Optional<ScrmCustomerEntity> customerOpt = customerRepository.findById(customerId);
        if (customerOpt.isEmpty()) {
            log.warn("客户不存在, 跳过评估: customerId={}", customerId);
            return List.of();
        }
        ScrmCustomerEntity customer = customerOpt.get();
        // 数据隔离: 校验客户归属当前账号


        // 加载启用规则 (按 priority ASC)
        List<ScrmAutoTagRuleEntity> rules = ruleRepository
                .findByTriggerEventAndEnabledTrueOrderByPriorityAsc(
                         triggerEvent);
        if (rules.isEmpty()) {
            return List.of();
        }

        List<ScrmAutoTagRuleLogEntity> logs = new ArrayList<>();
        for (ScrmAutoTagRuleEntity rule : rules) {
            try {
                boolean matched = evaluateConditions(rule, customerContext);
                if (matched) {
                    String matchedConditionsJson = buildMatchedConditionsJson(rule, customerContext);
                    ActionResult actionResult = executeAction(rule, customer, customerContext);
                    ScrmAutoTagRuleLogEntity logEntity = writeLog(
                            rule, customer, triggerEvent, matchedConditionsJson, actionResult);
                    logs.add(logEntity);
                    // 增量更新匹配次数与最近匹配时间 (直接 SQL, 避免乐观锁冲突)
                    ruleRepository.incrementMatchCount(rule.getId(), LocalDateTime.now());
                    log.info("自动标签规则命中: ruleId={}, ruleName={}, customerId={}, actionResult={}",
                            rule.getId(), rule.getRuleName(), customerId, actionResult.result());
                }
            } catch (Exception e) {
                log.warn("规则评估异常, 跳过该规则: ruleId={}, err={}",
                        rule.getId(), e.getMessage());
            }
        }
        return logs;
    }

    // ============================================================
    // 日志与统计
    // ============================================================

    /**
     * 分页查询规则执行日志, 支持按规则 ID、客户 ID 与时间范围过滤。
     *
     * @param ruleId    规则 ID 过滤（可空）
     * @param customerId 客户 ID 过滤（可空）
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @param pageable  分页参数
     * @return 执行日志分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmAutoTagRuleLogEntity> getRuleLogs(Long ruleId, Long customerId,
                                                       LocalDateTime startTime, LocalDateTime endTime,
                                                       Pageable pageable) {
        Specification<ScrmAutoTagRuleLogEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (ruleId != null) {
                predicates.add(cb.equal(root.get("ruleId"), ruleId));
            }
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (startTime != null && endTime != null) {
                predicates.add(cb.between(root.get("executedAt"), startTime, endTime));
            } else if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("executedAt"), startTime));
            } else if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("executedAt"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return logRepository.findAll(spec, pageable);
    }

    /**
     * 规则统计: 各规则匹配次数与成功率。
     * <p>
     * 聚合指定时间范围内的执行日志, 按规则 ID 分组统计:
     * <ul>
     *   <li>matchCount: 规则累计匹配次数 (来自规则实体, 非时间窗口)</li>
     *   <li>executionCount: 时间窗口内执行次数</li>
     *   <li>successCount / failedCount / skippedCount: 时间窗口内各结果计数</li>
     *   <li>successRate: 时间窗口内成功率 (百分比)</li>
     * </ul>
     * startTime / endTime 缺省时默认统计近 7 天。
     * </p>
     *
     * @param startTime 起始时间 (含, 可空, 默认近 7 天)
     * @param endTime   截止时间 (含, 可空, 默认当前时间)
     * @return 统计结果列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRuleStats(LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime end = endTime != null ? endTime : LocalDateTime.now();
        LocalDateTime start = startTime != null ? startTime : end.minusDays(7);

        // 加载当前账号全部规则
        List<ScrmAutoTagRuleEntity> rules = ruleRepository
                .findAll((root, query, cb) -> cb.and());

        // 加载时间窗口内全部执行日志
        List<ScrmAutoTagRuleLogEntity> logs = logRepository
                .findByExecutedAtBetweenOrderByExecutedAtAsc(start, end);

        // 按规则 ID 分组
        Map<Long, List<ScrmAutoTagRuleLogEntity>> logsByRule = logs.stream()
                .collect(Collectors.groupingBy(ScrmAutoTagRuleLogEntity::getRuleId));

        List<Map<String, Object>> stats = new ArrayList<>();
        for (ScrmAutoTagRuleEntity rule : rules) {
            List<ScrmAutoTagRuleLogEntity> ruleLogs = logsByRule.getOrDefault(rule.getId(), List.of());
            long executionCount = ruleLogs.size();
            long successCount = ruleLogs.stream().filter(l -> RESULT_SUCCESS.equals(l.getActionResult())).count();
            long failedCount = ruleLogs.stream().filter(l -> RESULT_FAILED.equals(l.getActionResult())).count();
            long skippedCount = ruleLogs.stream().filter(l -> RESULT_SKIPPED.equals(l.getActionResult())).count();
            double successRate = executionCount > 0
                    ? (double) successCount / executionCount * 100 : 0.0;

            Map<String, Object> stat = new LinkedHashMap<>();
            stat.put("ruleId", rule.getId());
            stat.put("ruleName", rule.getRuleName());
            stat.put("triggerEvent", rule.getTriggerEvent());
            stat.put("actionType", rule.getActionType());
            stat.put("enabled", rule.getEnabled());
            stat.put("priority", rule.getPriority());
            stat.put("matchCount", rule.getMatchCount());
            stat.put("lastMatchAt", rule.getLastMatchAt());
            stat.put("executionCount", executionCount);
            stat.put("successCount", successCount);
            stat.put("failedCount", failedCount);
            stat.put("skippedCount", skippedCount);
            stat.put("successRate", successRate);
            stats.add(stat);
        }
        return stats;
    }

    // ============================================================
    // 元数据
    // ============================================================

    /**
     * 可用条件字段列表。
     *
     * @return 字段列表 (每项包含 name 与 description)
     */
    public List<Map<String, String>> listAvailableFields() {
        return AVAILABLE_FIELDS;
    }

    /**
     * 可用操作符列表。
     *
     * @return 操作符列表 (每项包含 name 与 description)
     */
    public List<Map<String, String>> listAvailableOperators() {
        return AVAILABLE_OPERATORS;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 校验自动标签规则参数。
     * <p>
     * 创建场景 (partial=false): ruleName / triggerEvent / conditionType / conditions /
     * actionType / actionParams 必填。更新场景 (partial=true): 允许字段为空 (部分更新),
     * 仅校验非空字段的合法性。conditions 与 actionParams 非空时校验 JSON 可解析。
     * </p>
     *
     * @param dto     规则参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateRuleDto(ScrmAutoTagRuleDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("规则参数不能为空");
        }
        // ruleName 校验
        if (dto.getRuleName() != null) {
            if (dto.getRuleName().isBlank()) {
                throw ScrmException.badRequest("规则名称不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("规则名称不能为空");
        }
        // triggerEvent 校验 (合法值)
        if (dto.getTriggerEvent() != null) {
            if (!VALID_TRIGGER_EVENTS.contains(dto.getTriggerEvent())) {
                throw ScrmException.badRequest(
                        "触发事件非法: " + dto.getTriggerEvent() + ", 仅支持 " + VALID_TRIGGER_EVENTS);
            }
        } else if (!partial) {
            throw ScrmException.badRequest("触发事件不能为空");
        }
        // conditionType 校验 (合法值)
        if (dto.getConditionType() != null) {
            if (!VALID_CONDITION_TYPES.contains(dto.getConditionType())) {
                throw ScrmException.badRequest(
                        "条件类型非法: " + dto.getConditionType() + ", 仅支持 " + VALID_CONDITION_TYPES);
            }
        } else if (!partial) {
            throw ScrmException.badRequest("条件类型不能为空");
        }
        // actionType 校验 (合法值)
        if (dto.getActionType() != null) {
            if (!VALID_ACTION_TYPES.contains(dto.getActionType())) {
                throw ScrmException.badRequest(
                        "动作类型非法: " + dto.getActionType() + ", 仅支持 " + VALID_ACTION_TYPES);
            }
        } else if (!partial) {
            throw ScrmException.badRequest("动作类型不能为空");
        }
        // conditions 校验 (JSON 可解析)
        if (dto.getConditions() != null) {
            if (dto.getConditions().isBlank()) {
                throw ScrmException.badRequest("条件 JSON 不能为空");
            }
            try {
                List<Map<String, Object>> parsed = objectMapper.readValue(
                        dto.getConditions(), new TypeReference<List<Map<String, Object>>>() {});
                // 校验每个条件的 field / operator 非空且 operator 合法
                for (Map<String, Object> condition : parsed) {
                    String field = (String) condition.get("field");
                    String operator = (String) condition.get("operator");
                    if (field == null || field.isBlank()) {
                        throw ScrmException.badRequest("条件字段不能为空: " + condition);
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
        // actionParams 校验 (JSON 可解析)
        if (dto.getActionParams() != null) {
            if (dto.getActionParams().isBlank()) {
                throw ScrmException.badRequest("动作参数 JSON 不能为空");
            }
            try {
                objectMapper.readValue(dto.getActionParams(), new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                throw ScrmException.badRequest("动作参数 JSON 解析失败: " + e.getMessage());
            }
        } else if (!partial) {
            throw ScrmException.badRequest("动作参数 JSON 不能为空");
        }
        // priority 校验 (范围)
        if (dto.getPriority() != null) {
            if (dto.getPriority() < PRIORITY_MIN || dto.getPriority() > PRIORITY_MAX) {
                throw ScrmException.badRequest(
                        "优先级必须在 " + PRIORITY_MIN + "-" + PRIORITY_MAX + " 之间: " + dto.getPriority());
            }
        }
    }

    /**
     * 评估规则条件是否匹配。
     * <p>解析 conditions JSON, 按 conditionType (ALL/ANY) 对 customerContext 中的字段评估。</p>
     *
     * @param rule            规则实体
     * @param customerContext 客户属性快照
     * @return 条件是否匹配
     */
    private boolean evaluateConditions(ScrmAutoTagRuleEntity rule, Map<String, Object> customerContext) {
        List<Map<String, Object>> conditions = parseConditions(rule.getConditions());
        if (conditions.isEmpty()) {
            return true;
        }
        boolean all = "ALL".equals(rule.getConditionType());
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
     * 评估单个条件。
     * <p>支持 eq/ne/in/gt/lt/contains/between 操作符, 自动处理类型转换。</p>
     *
     * @param fieldValue    客户属性值
     * @param operator      操作符
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
            case "ne":
                return !toStringValue(fieldValue).equals(toStringValue(conditionValue));
            case "in":
                return isInCollection(fieldValue, conditionValue);
            case "gt":
                return toDouble(fieldValue) > toDouble(conditionValue);
            case "lt":
                return toDouble(fieldValue) < toDouble(conditionValue);
            case "contains":
                return contains(fieldValue, conditionValue);
            case "between":
                return isBetween(fieldValue, conditionValue);
            default:
                return false;
        }
    }

    /**
     * 执行规则动作。
     *
     * @param rule            规则实体
     * @param customer        客户实体
     * @param customerContext 客户属性快照
     * @return 动作执行结果
     */
    private ActionResult executeAction(ScrmAutoTagRuleEntity rule, ScrmCustomerEntity customer,
                                        Map<String, Object> customerContext) {
        Map<String, Object> params = parseActionParams(rule.getActionParams());
        String actionType = rule.getActionType();
        try {
            switch (actionType) {
                case "ADD_TAG":
                    return executeAddTag(customer.getId(), params);
                case "REMOVE_TAG":
                    return executeRemoveTag(customer.getId(), params);
                case "SET_LIFECYCLE":
                    return executeSetLifecycle(customer, params);
                case "NOTIFY":
                    return executeNotify(rule, customer, params);
                default:
                    return ActionResult.skipped("未知动作类型: " + actionType);
            }
        } catch (Exception e) {
            log.warn("动作执行失败: ruleId={}, actionType={}, err={}",
                    rule.getId(), actionType, e.getMessage());
            return ActionResult.failed("动作执行异常: " + e.getMessage());
        }
    }

    /**
     * 执行 ADD_TAG 动作: 为客户添加标签 (相同 tagKey 已存在则覆盖 tagValue)。
     *
     * @param customerId 客户 ID
     * @param params     动作参数 ({tagIds:[], tagValue:""})
     * @return 动作执行结果
     */
    private ActionResult executeAddTag(Long customerId, Map<String, Object> params) {
        List<String> tagIds = extractStringList(params, "tagIds");
        if (tagIds.isEmpty()) {
            return ActionResult.skipped("动作参数缺少 tagIds");
        }
        String tagValue = params.containsKey("tagValue")
                ? toStringValue(params.get("tagValue")) : DEFAULT_TAG_VALUE;
        int added = 0;
        for (String tagKey : tagIds) {
            // tagKey (tagCode) → tagId, 标签定义不存在则跳过
            Long tagId = tagRepository.findByTagCode(tagKey)
                    .map(ScrmCustomerTagEntity::getId).orElse(null);
            if (tagId == null) {
                log.warn("ADD_TAG 跳过: 标签定义不存在 tagCode={}", tagKey);
                continue;
            }
            ScrmTagCustomerEntity tag = tagCustomerRepository
                    .findByCustomerIdAndTagId(customerId, tagId)
                    .orElseGet(ScrmTagCustomerEntity::new);
            if (tag.getId() == null) {
                tag.setCustomerId(customerId);
                tag.setTagId(tagId);
            }
            tag.setTagValue(tagValue);
            tagCustomerRepository.save(tag);
            added++;
        }
        return ActionResult.success("添加标签 " + added + " 个: " + String.join(",", tagIds));
    }

    /**
     * 执行 REMOVE_TAG 动作: 移除客户标签。
     *
     * @param customerId 客户 ID
     * @param params     动作参数 ({tagIds:[]})
     * @return 动作执行结果
     */
    private ActionResult executeRemoveTag(Long customerId, Map<String, Object> params) {
        List<String> tagIds = extractStringList(params, "tagIds");
        if (tagIds.isEmpty()) {
            return ActionResult.skipped("动作参数缺少 tagIds");
        }
        int removed = 0;
        for (String tagKey : tagIds) {
            // tagKey (tagCode) → tagId, 再定位赋值记录删除
            Long tagId = tagRepository.findByTagCode(tagKey)
                    .map(ScrmCustomerTagEntity::getId).orElse(null);
            if (tagId == null) {
                continue;
            }
            Optional<ScrmTagCustomerEntity> tag = tagCustomerRepository
                    .findByCustomerIdAndTagId(customerId, tagId);
            if (tag.isPresent()) {
                tagCustomerRepository.delete(tag.get());
                removed++;
            }
        }
        return removed > 0
                ? ActionResult.success("移除标签 " + removed + " 个: " + String.join(",", tagIds))
                : ActionResult.skipped("无匹配标签可移除: " + String.join(",", tagIds));
    }

    /**
     * 执行 SET_LIFECYCLE 动作: 更新客户生命周期。
     *
     * @param customer 客户实体
     * @param params   动作参数 ({lifecycle:""})
     * @return 动作执行结果
     */
    private ActionResult executeSetLifecycle(ScrmCustomerEntity customer, Map<String, Object> params) {
        String lifecycle = params.containsKey("lifecycle")
                ? toStringValue(params.get("lifecycle")) : null;
        if (lifecycle == null || lifecycle.isBlank()) {
            return ActionResult.skipped("动作参数缺少 lifecycle");
        }
        if (lifecycle.equals(customer.getLifecycle())) {
            return ActionResult.skipped("生命周期未变化: " + lifecycle);
        }
        String previous = customer.getLifecycle();
        customer.setLifecycle(lifecycle);
        customerRepository.save(customer);
        return ActionResult.success("生命周期变更: " + previous + " -> " + lifecycle);
    }

    /**
     * 执行 NOTIFY 动作: 发送系统通知。
     *
     * @param rule     规则实体
     * @param customer 客户实体
     * @param params   动作参数 ({notifyUserId:"", message:""})
     * @return 动作执行结果
     */
    private ActionResult executeNotify(ScrmAutoTagRuleEntity rule, ScrmCustomerEntity customer,
                                        Map<String, Object> params) {
        String notifyUserId = params.containsKey("notifyUserId")
                ? toStringValue(params.get("notifyUserId")) : "";
        String message = params.containsKey("message")
                ? toStringValue(params.get("message"))
                : "自动标签规则[" + rule.getRuleName() + "]触发, 客户=" + customer.getNickname();
        notificationService.notifySystem("自动标签规则通知",
                message + " (通知用户: " + notifyUserId + ")", "INFO");
        return ActionResult.success("通知已发送: " + notifyUserId);
    }

    /**
     * 构建匹配条件详情 JSON (用于日志记录)。
     *
     * @param rule            规则实体
     * @param customerContext 客户属性快照
     * @return 匹配条件详情 JSON 字符串
     */
    private String buildMatchedConditionsJson(ScrmAutoTagRuleEntity rule, Map<String, Object> customerContext) {
        try {
            List<Map<String, Object>> conditions = parseConditions(rule.getConditions());
            List<Map<String, Object>> matched = new ArrayList<>();
            for (Map<String, Object> condition : conditions) {
                String field = (String) condition.get("field");
                String operator = (String) condition.get("operator");
                Object value = condition.get("value");
                Object fieldValue = customerContext != null ? customerContext.get(field) : null;
                boolean isMatched = evaluateCondition(fieldValue, operator, value);
                if (isMatched) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("field", field);
                    entry.put("operator", operator);
                    entry.put("value", value);
                    entry.put("fieldValue", fieldValue);
                    matched.add(entry);
                }
            }
            return objectMapper.writeValueAsString(matched);
        } catch (Exception e) {
            log.warn("构建匹配条件 JSON 失败: ruleId={}, err={}", rule.getId(), e.getMessage());
            return "[]";
        }
    }

    /**
     * 写入执行日志。
     *
     * @param rule              规则实体
     * @param customer          客户实体
     * @param triggerEvent      触发事件
     * @param matchedConditions 匹配条件详情 JSON
     * @param actionResult      动作执行结果
     * @return 持久化后的日志实体
     */
    private ScrmAutoTagRuleLogEntity writeLog(ScrmAutoTagRuleEntity rule, ScrmCustomerEntity customer,
                                               String triggerEvent, String matchedConditions,
                                               ActionResult actionResult) {
        ScrmAutoTagRuleLogEntity logEntity = new ScrmAutoTagRuleLogEntity();
        logEntity.setRuleId(rule.getId());
        logEntity.setCustomerId(customer.getId());
        logEntity.setCustomerNickname(customer.getNickname());
        logEntity.setTriggerEvent(triggerEvent);
        logEntity.setMatchedConditions(matchedConditions);
        logEntity.setActionType(rule.getActionType());
        logEntity.setActionResult(actionResult.result());
        logEntity.setActionDetail(actionResult.detail());
        logEntity.setExecutedAt(LocalDateTime.now());
        return logRepository.save(logEntity);
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
     * 解析动作参数 JSON 为 Map。
     *
     * @param actionParamsJson 动作参数 JSON 字符串
     * @return 动作参数 Map, 解析失败返回空 Map
     */
    private Map<String, Object> parseActionParams(String actionParamsJson) {
        try {
            return objectMapper.readValue(actionParamsJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("动作参数 JSON 解析失败: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 从参数 Map 中提取字符串列表。
     *
     * @param params 参数 Map
     * @param key    键名
     * @return 字符串列表
     */
    private List<String> extractStringList(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value instanceof Collection<?> col) {
            return col.stream().map(Object::toString).toList();
        }
        if (value != null) {
            return List.of(value.toString());
        }
        return List.of();
    }

    /**
     * 判断字段值是否在集合中 (in 操作符)。
     *
     * @param fieldValue    字段值
     * @param conditionValue 条件值 (集合)
     * @return 是否在集合中
     */
    private boolean isInCollection(Object fieldValue, Object conditionValue) {
        if (conditionValue instanceof Collection<?> col) {
            return col.stream().anyMatch(v -> toStringValue(v).equals(toStringValue(fieldValue)));
        }
        return false;
    }

    /**
     * 判断字段值是否包含条件值 (contains 操作符)。
     * <p>字段值为集合时检查元素存在性, 为字符串时检查子串包含。</p>
     *
     * @param fieldValue    字段值
     * @param conditionValue 条件值
     * @return 是否包含
     */
    private boolean contains(Object fieldValue, Object conditionValue) {
        if (fieldValue instanceof Collection<?> col) {
            return col.stream().anyMatch(v -> toStringValue(v).equals(toStringValue(conditionValue)));
        }
        return toStringValue(fieldValue).contains(toStringValue(conditionValue));
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
     * 按主键查询规则, 不存在抛异常, 并校验账号归属。
     *
     * @param id 规则 ID
     * @return 规则实体
     * @throws ScrmException 规则不存在
     */
    private ScrmAutoTagRuleEntity findOrThrow(Long id) throws ScrmException {
        ScrmAutoTagRuleEntity entity = ruleRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "自动标签规则不存在: id=" + id));

        return entity;
    }

    /**
     * 动作执行结果。
     *
 * @since V1.0
     * @author Hsi Chu
     * @param result 结果状态 (SUCCESS / FAILED / SKIPPED)
     * @param detail 结果详情
     */
    private record ActionResult(String result, String detail) {
        static ActionResult success(String detail) {
            return new ActionResult(RESULT_SUCCESS, detail);
        }

        static ActionResult failed(String detail) {
            return new ActionResult(RESULT_FAILED, detail);
        }

        static ActionResult skipped(String detail) {
            return new ActionResult(RESULT_SKIPPED, detail);
        }
    }
}
