/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : CustomerConditionEvaluator.java
 * Date : 2026/09/19 10:12:40
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;

/**
 * 客户条件评估共享组件。
 * <p>
 * 条件评估逻辑的唯一实现, 由 {@link ScrmSegmentService} (客群动态计算) 与
 * {@link ScrmWorkflowService} (工作流条件节点) 共同复用, 避免两处规则漂移。
 * </p>
 * <p>
 * 条件集合的 JSON 形状统一为
 * {@code [{"field":"customer_level","operator":"eq","value":"ACTIVE"}]},
 * 条件组合 {@code conditionType} 支持 ALL / ANY / NONE:
 * </p>
 * <ul>
 *   <li>ALL: 所有条件均命中</li>
 *   <li>ANY: 任一条件命中即返回</li>
 *   <li>NONE: 所有条件均不命中</li>
 * </ul>
 * <p>
 * 可用字段见 {@link #AVAILABLE_FIELDS}, 操作符见 {@link #VALID_OPERATORS}
 * (eq/ne/gt/lt/gte/lte/between/contains/in)。字段值由 {@link #buildCustomerContext}
 * 从客户实体构建; 其中 order_count / total_amount / engagement_score / tag / rfm_segment
 * 仍为缺省值 (待对接订单 / 互动服务), 调用方传入的上下文可覆盖这些字段。
 * </p>
 *
 * @author Hsi Chu
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerConditionEvaluator {

    /** 条件组合: 全部满足 */
    public static final String CONDITION_TYPE_ALL = "ALL";
    /** 条件组合: 任一满足 */
    public static final String CONDITION_TYPE_ANY = "ANY";
    /** 条件组合: 全不满足 */
    public static final String CONDITION_TYPE_NONE = "NONE";

    /** 合法的条件组合 */
    public static final List<String> VALID_CONDITION_TYPES =
            List.of(CONDITION_TYPE_ALL, CONDITION_TYPE_ANY, CONDITION_TYPE_NONE);

    /** 合法的操作符 */
    public static final List<String> VALID_OPERATORS =
            List.of("eq", "ne", "gt", "lt", "gte", "lte", "between", "contains", "in");

    /** 可用条件字段 */
    public static final List<String> AVAILABLE_FIELDS = List.of(
            "customer_name", "order_count", "total_amount", "last_interaction_days",
            "registration_days", "engagement_score", "customer_level", "tag", "rfm_segment");

    /** 默认匹配分数 */
    private static final double DEFAULT_MATCH_SCORE = 1.0;

    /** JSON 解析器 (解析条件集合) */
    private final ObjectMapper objectMapper;

    /**
     * 评估分群条件集合, 返回匹配详情 (不匹配返回 null)。
     * <p>按 conditionType (ALL/ANY/NONE) 评估所有条件, 匹配详情记录每个命中条件的字段与取值。</p>
     *
     * @param customer      客户实体
     * @param conditions    条件列表
     * @param conditionType 条件组合
     * @return 匹配详情 (matchedConditions / matchScore), 不匹配返回 null
     */
    public Map<String, Object> evaluate(ScrmCustomerEntity customer,
                                        List<Map<String, Object>> conditions,
                                        String conditionType) {
        return evaluateWithContext(buildCustomerContext(customer), conditions, conditionType);
    }

    /**
     * 基于已构建的字段上下文评估条件集合, 返回匹配详情 (不匹配返回 null)。
     * <p>供工作流条件节点等需要在客户上下文之上叠加流程变量的场景使用。</p>
     *
     * @param context       字段上下文
     * @param conditions    条件列表
     * @param conditionType 条件组合
     * @return 匹配详情 (matchedConditions / matchScore), 不匹配返回 null
     */
    public Map<String, Object> evaluateWithContext(Map<String, Object> context,
                                                   List<Map<String, Object>> conditions,
                                                   String conditionType) {
        if (conditions == null || conditions.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Map<String, Object>> matchedDetails = new ArrayList<>();
        boolean all = CONDITION_TYPE_ALL.equals(conditionType);
        boolean any = CONDITION_TYPE_ANY.equals(conditionType);
        boolean none = CONDITION_TYPE_NONE.equals(conditionType);
        for (Map<String, Object> condition : conditions) {
            String field = (String) condition.get("field");
            String operator = (String) condition.get("operator");
            Object value = condition.get("value");
            Object fieldValue = context.get(field);
            boolean matched = evaluateSingle(fieldValue, operator, value);
            if (matched) {
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("field", field);
                detail.put("operator", operator);
                detail.put("value", value);
                detail.put("fieldValue", fieldValue);
                matchedDetails.add(detail);
            }
            if (all && !matched) {
                return null;
            }
            if (any && matched) {
                return buildMatchResult(matchedDetails);
            }
        }
        if (none && matchedDetails.isEmpty()) {
            return buildMatchResult(matchedDetails);
        }
        if (all) {
            return buildMatchResult(matchedDetails);
        }
        // any 且无命中 / none 且有命中
        return null;
    }

    /**
     * 评估单个条件。
     * <p>支持 eq/ne/gt/lt/gte/lte/between/contains/in 操作符, 自动处理类型转换;
     * 字段值为 null 或操作符未知时按不匹配处理。</p>
     *
     * @param fieldValue     字段值
     * @param operator       操作符
     * @param conditionValue 条件值
     * @return 条件是否满足
     */
    public boolean evaluateSingle(Object fieldValue, String operator, Object conditionValue) {
        if (fieldValue == null) {
            return false;
        }
        switch (operator == null ? "" : operator) {
            case "eq":
                return toStringValue(fieldValue).equals(toStringValue(conditionValue));
            case "ne":
                return !toStringValue(fieldValue).equals(toStringValue(conditionValue));
            case "gt":
                return compareNumeric(fieldValue, conditionValue, c -> c > 0);
            case "lt":
                return compareNumeric(fieldValue, conditionValue, c -> c < 0);
            case "gte":
                return compareNumeric(fieldValue, conditionValue, c -> c >= 0);
            case "lte":
                return compareNumeric(fieldValue, conditionValue, c -> c <= 0);
            case "between":
                return isBetween(fieldValue, conditionValue);
            case "contains":
                return toStringValue(fieldValue).contains(toStringValue(conditionValue));
            case "in":
                return isIn(fieldValue, conditionValue);
            default:
                return false;
        }
    }

    /**
     * 构建客户上下文 (条件评估用)。
     * <p>customer_name / customer_level 取客户实体字段; last_interaction_days / registration_days
     * 由客户 lastInteractionAt / createTime 计算; order_count / total_amount / engagement_score /
     * tag / rfm_segment 当前缺省 0 或空 (待对接订单 / 互动服务)。</p>
     *
     * @param customer 客户实体
     * @return 客户上下文 Map
     */
    public Map<String, Object> buildCustomerContext(ScrmCustomerEntity customer) {
        Map<String, Object> context = new LinkedHashMap<>();
        if (customer == null) {
            return context;
        }
        LocalDateTime now = LocalDateTime.now();
        context.put("customer_name", customer.getNickname());
        context.put("customer_level", customer.getLifecycle());
        if (customer.getLastInteractionAt() != null) {
            long days = ChronoUnit.DAYS.between(customer.getLastInteractionAt().toLocalDate(), now.toLocalDate());
            context.put("last_interaction_days", days);
        } else {
            context.put("last_interaction_days", Long.MAX_VALUE);
        }
        if (customer.getCreateTime() != null) {
            long days = ChronoUnit.DAYS.between(customer.getCreateTime().toLocalDate(), now.toLocalDate());
            context.put("registration_days", days);
        } else {
            context.put("registration_days", 0L);
        }
        context.put("order_count", 0);
        context.put("total_amount", 0.0);
        context.put("engagement_score", 0.0);
        context.put("tag", "");
        context.put("rfm_segment", "");
        return context;
    }

    /**
     * 解析条件 JSON 为 List (宽松模式)。
     * <p>解析失败返回空列表并记录告警, 供分群计算等批量链路降级使用;
     * 需要显式失败的调用方应自行解析。</p>
     *
     * @param conditionsJson 条件 JSON 字符串
     * @return 条件列表, 解析失败返回空列表
     */
    public List<Map<String, Object>> parseConditions(String conditionsJson) {
        if (conditionsJson == null || conditionsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(conditionsJson, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception e) {
            log.warn("条件 JSON 解析失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 构建匹配结果 Map。
     *
     * @param matchedDetails 命中条件详情
     * @return 匹配结果
     */
    private Map<String, Object> buildMatchResult(List<Map<String, Object>> matchedDetails) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matchedConditions", matchedDetails);
        result.put("matchScore", DEFAULT_MATCH_SCORE);
        return result;
    }

    /**
     * 判断字段值是否在区间内 (between 操作符)。
     * <p>条件值应为 [min, max] 二元数组, 区间两端均包含。</p>
     *
     * @param fieldValue     字段值
     * @param conditionValue 条件值 ([min, max])
     * @return 是否在区间内
     */
    private boolean isBetween(Object fieldValue, Object conditionValue) {
        if (conditionValue instanceof Collection<?> col && col.size() == 2) {
            Object[] arr = col.toArray();
            Double min = toNumeric(arr[0]);
            Double max = toNumeric(arr[1]);
            Double val = toNumeric(fieldValue);
            return min != null && max != null && val != null && val >= min && val <= max;
        }
        return false;
    }

    /**
     * 数值比较: 任一侧无法解析为数字时判定为<b>不命中</b>。
     * <p>历史实现把非数字强转成 0 再比较, 于是 {@code customer_name lt 5} 会因为
     * 左侧转成 0 而命中, {@code order_count lte "abc"} 会因为右侧转成 0 而命中 0 值客户 ——
     * 属于过度匹配。缺数据 / 配错条件值都应判否, 不应判是。</p>
     *
     * @param fieldValue     字段值
     * @param conditionValue 条件值
     * @param matches        对 {@code Double.compare(字段值, 条件值)} 结果的判定
     * @return 是否命中
     */
    private boolean compareNumeric(Object fieldValue, Object conditionValue, IntPredicate matches) {
        Double left = toNumeric(fieldValue);
        Double right = toNumeric(conditionValue);
        return left != null && right != null && matches.test(Double.compare(left, right));
    }

    /**
     * 解析为数字, 无法解析返回 null (不做 0 兜底)。
     *
     * @param obj 待解析值
     * @return 数字值, 或 null 表示不是数字
     */
    private Double toNumeric(Object obj) {
        if (obj instanceof Number n) {
            return n.doubleValue();
        }
        if (obj == null) {
            return null;
        }
        try {
            return Double.parseDouble(obj.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 判断字段值是否在集合内 (in 操作符)。
     *
     * @param fieldValue     字段值
     * @param conditionValue 条件值 (集合)
     * @return 是否在集合内
     */
    private boolean isIn(Object fieldValue, Object conditionValue) {
        if (conditionValue instanceof Collection<?> col) {
            return col.stream().anyMatch(v -> toStringValue(v).equals(toStringValue(fieldValue)));
        }
        return false;
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
}
