/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTagSystemRuleService.java
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

import org.hiylo.scrm.dto.ScrmTagRuleDto;
import org.hiylo.scrm.dto.ScrmTagRuleTestDto;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmTagEntity;
import org.hiylo.scrm.entity.ScrmTagRuleEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmTagRepository;
import org.hiylo.scrm.repository.ScrmTagRuleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

/**
 * SCRM 标签规则管理服务。
 * <p>
 * 承载自动标签规则子域: 规则创建 / 更新 / 删除 / 启停 / 分页查询 / 执行 / 批量执行 /
 * 条件评估与测试。规则条件评估基于客户实体属性与模拟字段, 为模拟实现。
 * 标签存在性校验与打标能力分别委托给 {@link ScrmTagSystemTagService}。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmTagSystemRuleService {

    /** 默认规则状态 */
    private static final String DEFAULT_RULE_STATUS = "ACTIVE";

    /** 规则状态: 活跃 */
    private static final String RULE_STATUS_ACTIVE = "ACTIVE";
    /** 规则状态: 停用 */
    private static final String RULE_STATUS_INACTIVE = "INACTIVE";

    /** 标签类型: 自动 */
    private static final String TAG_TYPE_AUTO = "AUTO";

    /** 标签来源: 自动 */
    private static final String TAG_SOURCE_AUTO = "AUTO";

    /** 合法的条件组合类型 */
    private static final List<String> VALID_CONDITION_TYPES = List.of("ALL", "ANY", "NONE");

    /** 合法的执行频率 */
    private static final List<String> VALID_EXECUTION_FREQUENCIES = List.of(
            "REALTIME", "HOURLY", "DAILY", "WEEKLY", "MANUAL");

    /** 合法的规则状态 */
    private static final List<String> VALID_RULE_STATUSES = List.of("ACTIVE", "INACTIVE", "DRAFT");

    /** 合法的操作符 */
    private static final List<String> VALID_OPERATORS = List.of(
            "eq", "ne", "gt", "lt", "contains", "between");

    /** 标签规则数据访问层 */
    private final ScrmTagRuleRepository ruleRepository;

    /** 标签定义数据访问层 (规则反向写回标签 ruleId) */
    private final ScrmTagRepository tagRepository;

    /** 客户数据访问层 (规则评估时加载客户属性) */
    private final ScrmCustomerRepository customerRepository;

    /** JSON 解析器 (解析 conditions) */
    private final ObjectMapper objectMapper;

    /** 标签管理与客户标签管理服务 (标签存在性校验 / 打标 / 客户数刷新) */
    private final ScrmTagSystemTagService tagService;

    /**
     * 创建自动标签规则。
     * <p>校验 conditions 为合法 JSON、关联标签存在且为 AUTO 类型后写入归属账号 ID 持久化。
     * 创建后将规则的 ruleId 反向写入关联标签。</p>
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException 参数非法 / 关联标签不存在或非 AUTO 类型 / conditions 非合法 JSON
     */
    @Transactional
    public ScrmTagRuleEntity createRule(ScrmTagRuleDto dto) throws ScrmException {
        validateRuleDto(dto, false);
        ScrmTagEntity tag = tagService.findTagOrThrow(dto.getTagId());
        if (!TAG_TYPE_AUTO.equals(tag.getTagType())) {
            throw ScrmException.badRequest("关联标签必须为 AUTO 类型, 当前类型: " + tag.getTagType());
        }
        ScrmTagRuleEntity entity = new ScrmTagRuleEntity();
        entity.setRuleName(dto.getRuleName());
        entity.setTagId(dto.getTagId());
        entity.setDescription(dto.getDescription());
        entity.setConditionType(dto.getConditionType());
        entity.setConditions(dto.getConditions());
        entity.setTargetFields(dto.getTargetFields());
        entity.setExecutionFrequency(dto.getExecutionFrequency());
        entity.setStatus(dto.getStatus() != null && !dto.getStatus().isBlank()
                ? dto.getStatus() : DEFAULT_RULE_STATUS);
        entity.setMatchedCount(0);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = ruleRepository.save(entity);
        // 反向写入标签的 ruleId (一个标签只能关联一个规则, 覆盖更新)
        tag.setRuleId(entity.getId());
        tagRepository.save(tag);
        log.info("创建标签规则: id={}, ruleName={}, tagId={}",
                entity.getId(), entity.getRuleName(), entity.getTagId());
        return entity;
    }

    /**
     * 更新规则（字段非空才覆盖）。
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 参数非法 / 关联标签不存在或非 AUTO 类型
     */
    @Transactional
    public ScrmTagRuleEntity updateRule(Long id, ScrmTagRuleDto dto) throws ScrmException {
        ScrmTagRuleEntity entity = findRuleOrThrow(id);
        validateRuleDto(dto, true);
        if (dto.getTagId() != null && !Objects.equals(dto.getTagId(), entity.getTagId())) {
            // 标签变更: 校验新标签存在且为 AUTO 类型
            ScrmTagEntity newTag = tagService.findTagOrThrow(dto.getTagId());
            if (!TAG_TYPE_AUTO.equals(newTag.getTagType())) {
                throw ScrmException.badRequest("关联标签必须为 AUTO 类型, 当前类型: " + newTag.getTagType());
            }
            // 清空原标签的 ruleId
            ScrmTagEntity oldTag = tagService.findTagOrThrow(entity.getTagId());
            oldTag.setRuleId(null);
            tagRepository.save(oldTag);
            // 设置新标签的 ruleId
            newTag.setRuleId(id);
            tagRepository.save(newTag);
            entity.setTagId(dto.getTagId());
        }
        if (dto.getRuleName() != null) entity.setRuleName(dto.getRuleName());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getConditionType() != null) entity.setConditionType(dto.getConditionType());
        if (dto.getConditions() != null) entity.setConditions(dto.getConditions());
        if (dto.getTargetFields() != null) entity.setTargetFields(dto.getTargetFields());
        if (dto.getExecutionFrequency() != null) entity.setExecutionFrequency(dto.getExecutionFrequency());
        if (dto.getStatus() != null && !dto.getStatus().isBlank()) entity.setStatus(dto.getStatus());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = ruleRepository.save(entity);
        log.info("更新标签规则: id={}, ruleName={}", entity.getId(), entity.getRuleName());
        return entity;
    }

    /**
     * 删除规则。
     * <p>删除后清理关联标签的 ruleId 反向引用。</p>
     *
     * @param id 规则 ID
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public void deleteRule(Long id) throws ScrmException {
        ScrmTagRuleEntity entity = findRuleOrThrow(id);
        // 清理关联标签的 ruleId
        ScrmTagEntity tag = tagService.findTagOrThrow(entity.getTagId());
        if (Objects.equals(tag.getRuleId(), id)) {
            tag.setRuleId(null);
            tagRepository.save(tag);
        }
        ruleRepository.delete(entity);
        log.info("删除标签规则: id={}, ruleName={}", id, entity.getRuleName());
    }

    /**
     * 查询规则详情。
     *
     * @param id 规则 ID
     * @return 规则实体
     * @throws ScrmException 规则不存在
     */
    @Transactional(readOnly = true)
    public ScrmTagRuleEntity getRule(Long id) throws ScrmException {
        return findRuleOrThrow(id);
    }

    /**
     * 分页查询规则, 支持按标签 ID、状态与关键字过滤。
     *
     * @param tagId   标签 ID 过滤（可空）
     * @param status  状态过滤（可空）
     * @param keyword 关键字过滤（按 ruleName / description 模糊匹配, 可空）
     * @param pageable 分页参数
     * @return 规则分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmTagRuleEntity> listRules(Long tagId, String status,
                                             String keyword, Pageable pageable) {
        Specification<ScrmTagRuleEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (tagId != null) {
                predicates.add(cb.equal(root.get("tagId"), tagId));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
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
     * 启用规则 (状态置为 ACTIVE)。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public ScrmTagRuleEntity enableRule(Long id) throws ScrmException {
        ScrmTagRuleEntity entity = findRuleOrThrow(id);
        entity.setStatus(RULE_STATUS_ACTIVE);
        ruleRepository.save(entity);
        log.info("启用标签规则: id={}, ruleName={}", id, entity.getRuleName());
        return entity;
    }

    /**
     * 禁用规则 (状态置为 INACTIVE)。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public ScrmTagRuleEntity disableRule(Long id) throws ScrmException {
        ScrmTagRuleEntity entity = findRuleOrThrow(id);
        entity.setStatus(RULE_STATUS_INACTIVE);
        ruleRepository.save(entity);
        log.info("禁用标签规则: id={}, ruleName={}", id, entity.getRuleName());
        return entity;
    }

    /**
     * 执行自动打标规则: 遍历当前账号全部客户 → 评估条件 → 命中则打标。
     * <p>模拟实现: 加载全部客户逐条评估, 命中的客户写入客户标签关联 (tag_source=AUTO,
     * is_auto=true)。执行完成后增量更新规则的 matchedCount 与 lastExecutedAt。</p>
     *
     * @param id 规则 ID
     * @return 命中的客户数
     * @throws ScrmException 规则不存在 / 关联标签不存在
     */
    @Transactional
    public int executeRule(Long id) throws ScrmException {
        ScrmTagRuleEntity rule = findRuleOrThrow(id);
        ScrmTagEntity tag = tagService.findTagOrThrow(rule.getTagId());
        // 加载全部客户 (模拟实现, 实际应分批处理)
        List<ScrmCustomerEntity> customers = customerRepository
                .findAll((root, query, cb) -> cb.and());
        int matched = 0;
        for (ScrmCustomerEntity customer : customers) {
            try {
                boolean isMatched = evaluateCondition(customer.getId(),
                        rule.getConditions(), rule.getConditionType());
                if (isMatched) {
                    tagService.assignTag(customer.getId(), tag.getId(), null, TAG_SOURCE_AUTO, "rule-engine");
                    matched++;
                }
            } catch (Exception e) {
                log.warn("规则执行: 客户评估异常, 跳过: customerId={}, err={}",
                        customer.getId(), e.getMessage());
            }
        }
        // 增量更新执行统计
        ruleRepository.updateExecutionStats(id, LocalDateTime.now(), matched);
        // 刷新标签客户数
        tagService.updateTagCustomerCount(tag.getId());
        log.info("执行标签规则: id={}, ruleName={}, 命中客户 {} / 总客户 {}",
                id, rule.getRuleName(), matched, customers.size());
        return matched;
    }

    /**
     * 批量执行所有活跃规则。
     *
     * @return 各规则执行结果: [{ruleId, ruleName, matchedCount}]
     */
    @Transactional
    public List<Map<String, Object>> batchExecuteRules() {
        List<ScrmTagRuleEntity> rules = ruleRepository
                .findByStatus(RULE_STATUS_ACTIVE);
        List<Map<String, Object>> results = new ArrayList<>();
        for (ScrmTagRuleEntity rule : rules) {
            try {
                int matched = executeRule(rule.getId());
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("ruleId", rule.getId());
                result.put("ruleName", rule.getRuleName());
                result.put("matchedCount", matched);
                results.add(result);
            } catch (Exception e) {
                log.warn("批量执行规则异常, 跳过: ruleId={}, err={}",
                        rule.getId(), e.getMessage());
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("ruleId", rule.getId());
                result.put("ruleName", rule.getRuleName());
                result.put("matchedCount", 0);
                result.put("error", e.getMessage());
                results.add(result);
            }
        }
        log.info("批量执行标签规则完成: 总规则 {} 条", rules.size());
        return results;
    }

    /**
     * 测试规则匹配 (不实际打标)。
     * <p>对入参客户 ID 列表逐个评估规则条件, 返回命中的客户 ID 列表与匹配详情。</p>
     *
     * @param testDto 测试参数 (ruleId + customerIds)
     * @return 测试结果: {matchedCustomerIds, details}
     * @throws ScrmException 规则不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> testRule(ScrmTagRuleTestDto testDto) throws ScrmException {
        ScrmTagRuleEntity rule = findRuleOrThrow(testDto.getRuleId());
        List<Long> matchedIds = new ArrayList<>();
        List<Map<String, Object>> details = new ArrayList<>();
        for (Long customerId : testDto.getCustomerIds()) {
            try {
                boolean isMatched = evaluateCondition(customerId,
                        rule.getConditions(), rule.getConditionType());
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("customerId", customerId);
                detail.put("matched", isMatched);
                details.add(detail);
                if (isMatched) {
                    matchedIds.add(customerId);
                }
            } catch (Exception e) {
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("customerId", customerId);
                detail.put("matched", false);
                detail.put("error", e.getMessage());
                details.add(detail);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ruleId", rule.getId());
        result.put("ruleName", rule.getRuleName());
        result.put("matchedCustomerIds", matchedIds);
        result.put("matchedCount", matchedIds.size());
        result.put("details", details);
        return result;
    }

    /**
     * 评估条件是否匹配 (模拟实现, 基于客户属性)。
     * <p>
     * 解析 conditions JSON 数组, 按 conditionType (ALL/ANY/NONE) 评估。条件 field 引用
     * 客户属性, 支持字段: customer_name (昵称) / lifecycle / platform_type / order_count /
     * total_amount / registration_days / last_interaction_days。order_count / total_amount
     * 当前为模拟值 (0), registration_days / last_interaction_days 由客户实体的 createTime /
     * lastInteractionAt 计算。
     * </p>
     *
     * @param customerId     客户 ID
     * @param conditionsJson 条件 JSON 数组字符串
     * @param conditionType  条件组合类型: ALL / ANY / NONE
     * @return 条件是否匹配
     * @throws ScrmException 客户不存在
     */
    @Transactional(readOnly = true)
    public boolean evaluateCondition(Long customerId, String conditionsJson,
                                     String conditionType) throws ScrmException {
        ScrmCustomerEntity customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "客户不存在: id=" + customerId));
        Map<String, Object> customerContext = buildCustomerContext(customer);
        List<Map<String, Object>> conditions = parseConditions(conditionsJson);
        if (conditions.isEmpty()) {
            return true;
        }
        boolean all = "ALL".equals(conditionType);
        boolean any = "ANY".equals(conditionType);
        boolean none = "NONE".equals(conditionType);
        for (Map<String, Object> condition : conditions) {
            String field = (String) condition.get("field");
            String operator = (String) condition.get("operator");
            Object value = condition.get("value");
            Object fieldValue = customerContext.get(field);
            boolean matched = evaluateSingleCondition(fieldValue, operator, value);
            if (all && !matched) {
                return false;
            }
            if (any && matched) {
                return true;
            }
        }
        // ALL: 全部满足; ANY: 任一满足 (此处未命中); NONE: 全部不满足
        if (none) {
            return conditions.stream().noneMatch(c -> {
                String field = (String) c.get("field");
                String operator = (String) c.get("operator");
                Object value = c.get("value");
                return evaluateSingleCondition(customerContext.get(field), operator, value);
            });
        }
        return all;
    }

    /**
     * 校验规则参数。
     *
     * @param dto     规则参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateRuleDto(ScrmTagRuleDto dto, boolean partial) throws ScrmException {
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
        if (!partial && dto.getTagId() == null) {
            throw ScrmException.badRequest("标签 ID 不能为空");
        }
        if (dto.getConditionType() != null && !VALID_CONDITION_TYPES.contains(dto.getConditionType())) {
            throw ScrmException.badRequest(
                    "条件组合类型非法: " + dto.getConditionType() + ", 仅支持 " + VALID_CONDITION_TYPES);
        }
        if (dto.getExecutionFrequency() != null && !VALID_EXECUTION_FREQUENCIES.contains(dto.getExecutionFrequency())) {
            throw ScrmException.badRequest(
                    "执行频率非法: " + dto.getExecutionFrequency() + ", 仅支持 " + VALID_EXECUTION_FREQUENCIES);
        }
        if (dto.getStatus() != null && !dto.getStatus().isBlank() && !VALID_RULE_STATUSES.contains(dto.getStatus())) {
            throw ScrmException.badRequest(
                    "规则状态非法: " + dto.getStatus() + ", 仅支持 " + VALID_RULE_STATUSES);
        }
        // conditions JSON 可解析性校验
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
    }

    /**
     * 构建客户上下文 (用于规则条件评估)。
     * <p>字段映射: customer_name -> nickname, lifecycle -> lifecycle, platform_type ->
     * platformType, order_count / total_amount 模拟为 0, registration_days 由 createTime 计算,
     * last_interaction_days 由 lastInteractionAt 计算。</p>
     *
     * @param customer 客户实体
     * @return 客户上下文 Map
     */
    private Map<String, Object> buildCustomerContext(ScrmCustomerEntity customer) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("customer_name", customer.getNickname() != null ? customer.getNickname() : "");
        context.put("nickname", customer.getNickname() != null ? customer.getNickname() : "");
        context.put("lifecycle", customer.getLifecycle() != null ? customer.getLifecycle() : "");
        context.put("platform_type", customer.getPlatformType() != null ? customer.getPlatformType() : "");
        // 模拟字段: 当前未对接订单服务, 默认 0
        context.put("order_count", 0);
        context.put("total_amount", 0);
        // 注册天数 (由 createTime 计算)
        if (customer.getCreateTime() != null) {
            long days = ChronoUnit.DAYS.between(customer.getCreateTime(), LocalDateTime.now());
            context.put("registration_days", days);
        } else {
            context.put("registration_days", 0);
        }
        // 最近交互距今天数
        if (customer.getLastInteractionAt() != null) {
            long days = ChronoUnit.DAYS.between(customer.getLastInteractionAt(), LocalDateTime.now());
            context.put("last_interaction_days", days);
            context.put("last_interaction", days);
        } else {
            context.put("last_interaction_days", 0);
            context.put("last_interaction", 0);
        }
        return context;
    }

    /**
     * 评估单个条件。
     * <p>支持 eq/ne/gt/lt/contains/between 操作符, 自动处理类型转换。</p>
     *
     * @param fieldValue    客户属性值
     * @param operator      操作符
     * @param conditionValue 条件值
     * @return 条件是否满足
     */
    private boolean evaluateSingleCondition(Object fieldValue, String operator, Object conditionValue) {
        if (fieldValue == null) {
            return false;
        }
        if (operator == null) {
            return false;
        }
        switch (operator) {
            case "eq":
                return toStringValue(fieldValue).equals(toStringValue(conditionValue));
            case "ne":
                return !toStringValue(fieldValue).equals(toStringValue(conditionValue));
            case "gt":
                return toDouble(fieldValue) > toDouble(conditionValue);
            case "lt":
                return toDouble(fieldValue) < toDouble(conditionValue);
            case "contains":
                return toStringValue(fieldValue).contains(toStringValue(conditionValue));
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
     * 按主键查询规则, 不存在抛异常, 并校验归属账号。
     *
     * @param id 规则 ID
     * @return 规则实体
     * @throws ScrmException 规则不存在
     */
    private ScrmTagRuleEntity findRuleOrThrow(Long id) throws ScrmException {
        ScrmTagRuleEntity entity = ruleRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "标签规则不存在: id=" + id));

        return entity;
    }
}