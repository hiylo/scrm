/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : CustomerConditionEvaluatorTest.java
 * Date : 2026/09/19 21:20:19
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CustomerConditionEvaluator} 单元测试。
 * <p>
 * 该评估器被客群动态计算与工作流条件节点共同复用, 一旦对非法输入「静默返回命中」,
 * 未配置/写错的条件就会误触发群发、打标签等真实副作用, 因此本用例重点验证:
 * </p>
 * <ul>
 *   <li>逐个操作符 (eq/ne/gt/lt/gte/lte/between/contains/in) 的命中与不命中</li>
 *   <li>ALL / ANY / NONE 三种组合语义</li>
 *   <li>字段值缺失 (null)、类型不符时的行为</li>
 *   <li>非法操作符、未知字段、未知条件组合一律「不命中」而非命中 (fail-closed)</li>
 * </ul>
 * 工作流侧「非法条件必须抛异常」的显式失败由
 * {@code ScrmWorkflowServiceConditionValidationTest} 覆盖。
 *
 * @author Hsi Chu
 */
@DisplayName("CustomerConditionEvaluator 单元测试")
class CustomerConditionEvaluatorTest {

    /** 被测评估器 (ObjectMapper 用真实实例) */
    private CustomerConditionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new CustomerConditionEvaluator(new ObjectMapper());
    }

    /**
     * 构造条件 Map
     */
    private Map<String, Object> condition(String field, String operator, Object value) {
        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("field", field);
        condition.put("operator", operator);
        condition.put("value", value);
        return condition;
    }

    /**
     * 构造命中详情条数可断言的匹配结果 Map
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> matchedDetails(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("matchedConditions");
    }

    // ============================================================
    // evaluateSingle: 逐个操作符
    // ============================================================

    @Test
    @DisplayName("evaluateSingle eq/ne: 字符串化比较, 相等与不等判定正确")
    void evaluateSingle_eqAndNe() {
        assertThat(evaluator.evaluateSingle("ACTIVE", "eq", "ACTIVE")).isTrue();
        // 数字与数字字符串按 toString 比较, 便于 JSON 配置书写
        assertThat(evaluator.evaluateSingle(5, "eq", "5")).isTrue();
        assertThat(evaluator.evaluateSingle("ACTIVE", "eq", "SLEEP")).isFalse();
        assertThat(evaluator.evaluateSingle("ACTIVE", "ne", "SLEEP")).isTrue();
        assertThat(evaluator.evaluateSingle("ACTIVE", "ne", "ACTIVE")).isFalse();
    }

    @Test
    @DisplayName("evaluateSingle gt/lt/gte/lte: 数值比较含边界")
    void evaluateSingle_numericComparisons() {
        assertThat(evaluator.evaluateSingle(10, "gt", 5)).isTrue();
        assertThat(evaluator.evaluateSingle(5, "gt", 5)).isFalse();
        assertThat(evaluator.evaluateSingle(4, "lt", 5)).isTrue();
        assertThat(evaluator.evaluateSingle(5, "lt", 5)).isFalse();
        assertThat(evaluator.evaluateSingle(5, "gte", 5)).isTrue();
        assertThat(evaluator.evaluateSingle(5, "lte", 5)).isTrue();
        assertThat(evaluator.evaluateSingle(6, "lte", 5)).isFalse();
        // 数字字符串可参与比较
        assertThat(evaluator.evaluateSingle("12", "gt", "3")).isTrue();
    }

    @Test
    @DisplayName("evaluateSingle between: 区间两端闭区间, 非二元数组不命中")
    void evaluateSingle_between() {
        List<Object> range = List.of(1, 5);
        assertThat(evaluator.evaluateSingle(3, "between", range)).isTrue();
        // 两端均为闭区间
        assertThat(evaluator.evaluateSingle(1, "between", range)).isTrue();
        assertThat(evaluator.evaluateSingle(5, "between", range)).isTrue();
        assertThat(evaluator.evaluateSingle(6, "between", range)).isFalse();
        assertThat(evaluator.evaluateSingle(0, "between", range)).isFalse();
        // 非二元数组 (元素数不足 / 非集合) 一律不命中, 不误放行
        assertThat(evaluator.evaluateSingle(3, "between", List.of(1))).isFalse();
        assertThat(evaluator.evaluateSingle(3, "between", "1-5")).isFalse();
        assertThat(evaluator.evaluateSingle(3, "between", null)).isFalse();
    }

    @Test
    @DisplayName("evaluateSingle contains: 子串包含判定")
    void evaluateSingle_contains() {
        assertThat(evaluator.evaluateSingle("张三丰", "contains", "张")).isTrue();
        assertThat(evaluator.evaluateSingle("vip-customer", "contains", "premium")).isFalse();
        // 条件值为 null 时按空字符串处理, 任何字符串都包含空串, 因此不应用 null 做 contains
        assertThat(evaluator.evaluateSingle("张三", "contains", null)).isTrue();
    }

    @Test
    @DisplayName("evaluateSingle in: 集合成员判定, 非集合条件值不命中")
    void evaluateSingle_in() {
        assertThat(evaluator.evaluateSingle("A", "in", List.of("A", "B"))).isTrue();
        // 集合元素为数字时同样按字符串化比较
        assertThat(evaluator.evaluateSingle("2", "in", List.of(1, 2, 3))).isTrue();
        assertThat(evaluator.evaluateSingle("C", "in", List.of("A", "B"))).isFalse();
        assertThat(evaluator.evaluateSingle("A", "in", "A,B")).isFalse();
        assertThat(evaluator.evaluateSingle("A", "in", Collections.emptyList())).isFalse();
    }

    // ============================================================
    // evaluateSingle: 异常输入必须 fail-closed
    // ============================================================

    @Test
    @DisplayName("evaluateSingle 非法操作符: 返回不命中而非默认命中 (防误触发动作)")
    void evaluateSingle_unknownOperatorFailsClosed() {
        for (String operator : List.of("equals", "==", "GT", "startsWith", "regex", " ")) {
            assertThat(evaluator.evaluateSingle("ACTIVE", operator, "ACTIVE"))
                    .as("操作符 %s 非法时必须不命中", operator)
                    .isFalse();
        }
        assertThat(evaluator.evaluateSingle(10, "eqqq", 10)).isFalse();
    }

    @Test
    @DisplayName("evaluateSingle null 操作符与 null 字段值: 全部不命中")
    void evaluateSingle_nullOperatorAndFieldValue() {
        assertThat(evaluator.evaluateSingle("ACTIVE", null, "ACTIVE")).isFalse();
        // 字段值为 null (未知字段 / 客户该字段缺失) 时任何操作符都不命中
        for (String operator : CustomerConditionEvaluator.VALID_OPERATORS) {
            assertThat(evaluator.evaluateSingle(null, operator, "X"))
                    .as("字段值为 null 时操作符 %s 必须不命中", operator)
                    .isFalse();
        }
        // ne 尤其容易写反: null != 任意值 仍按不命中处理, 避免未同步字段被误判
        assertThat(evaluator.evaluateSingle(null, "ne", "X")).isFalse();
    }

    @Test
    @DisplayName("evaluateSingle 类型不符: 任一侧非数字一律不命中 (不做强转 0 的过度匹配)")
    void evaluateSingle_nonNumericFailsClosed() {
        // 非数值字符串参与大小比较: 旧实现经 toDouble 归零, "gt/区间" 恰好不误命中,
        // 但 "lt/lte" 会被归零误判为命中 —— 例如按姓名字段筛"小于 5"会命中全部客户。
        assertThat(evaluator.evaluateSingle("abc", "gt", 5)).isFalse();
        assertThat(evaluator.evaluateSingle("abc", "between", List.of(1, 5))).isFalse();
        assertThat(evaluator.evaluateSingle("abc", "lt", 5)).isFalse();
        assertThat(evaluator.evaluateSingle("abc", "lte", 5)).isFalse();
        assertThat(evaluator.evaluateSingle("abc", "gte", -1)).isFalse();
        // 反向: 条件值配错成非数字时也不能命中 0 值客户
        assertThat(evaluator.evaluateSingle(0, "lte", "abc")).isFalse();
        assertThat(evaluator.evaluateSingle(0, "gte", "abc")).isFalse();
        assertThat(evaluator.evaluateSingle(3, "gt", "")).isFalse();
        // 数字字符串与带首尾空白的数字串仍可正常比较
        assertThat(evaluator.evaluateSingle(" 12 ", "gt", "3")).isTrue();
    }

    // ============================================================
    // evaluateWithContext: ALL / ANY / NONE
    // ============================================================

    @Test
    @DisplayName("evaluateWithContext ALL: 全部命中才返回匹配详情, 命中条件逐条记录")
    void evaluateWithContext_allMatches() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("customer_level", "ACTIVE");
        context.put("order_count", 8);
        Map<String, Object> result = evaluator.evaluateWithContext(context, List.of(
                condition("customer_level", "eq", "ACTIVE"),
                condition("order_count", "gte", 5)), CustomerConditionEvaluator.CONDITION_TYPE_ALL);
        assertThat(result).isNotNull();
        assertThat(result.get("matchScore")).isEqualTo(1.0);
        assertThat(matchedDetails(result)).hasSize(2)
                .allSatisfy(detail -> assertThat(detail).containsKeys(
                        "field", "operator", "value", "fieldValue"));
    }

    @Test
    @DisplayName("evaluateWithContext ALL: 任一条件不命中立即返回 null")
    void evaluateWithContext_allFailsOnFirstMismatch() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("customer_level", "SLEEP");
        context.put("order_count", 100);
        assertThat(evaluator.evaluateWithContext(context, List.of(
                condition("customer_level", "eq", "ACTIVE"),
                condition("order_count", "gt", 1)), CustomerConditionEvaluator.CONDITION_TYPE_ALL)).isNull();
    }

    @Test
    @DisplayName("evaluateWithContext ANY: 任一命中即返回, 且只记录命中的条件")
    void evaluateWithContext_anyMatches() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("customer_level", "SLEEP");
        context.put("order_count", 10);
        Map<String, Object> result = evaluator.evaluateWithContext(context, List.of(
                condition("customer_level", "eq", "ACTIVE"),
                condition("order_count", "gt", 5)), CustomerConditionEvaluator.CONDITION_TYPE_ANY);
        assertThat(result).isNotNull();
        assertThat(matchedDetails(result)).hasSize(1)
                .first().extracting("field").isEqualTo("order_count");
    }

    @Test
    @DisplayName("evaluateWithContext ANY: 全不命中返回 null")
    void evaluateWithContext_anyNoMatch() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("customer_level", "SLEEP");
        assertThat(evaluator.evaluateWithContext(context, List.of(
                condition("customer_level", "eq", "ACTIVE"),
                condition("customer_level", "eq", "LOST")), CustomerConditionEvaluator.CONDITION_TYPE_ANY))
                .isNull();
    }

    @Test
    @DisplayName("evaluateWithContext NONE: 全不命中返回空命中详情, 有命中返回 null")
    void evaluateWithContext_none() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("customer_level", "SLEEP");
        Map<String, Object> matched = evaluator.evaluateWithContext(context, List.of(
                condition("customer_level", "eq", "ACTIVE"),
                condition("order_count", "gt", 5)), CustomerConditionEvaluator.CONDITION_TYPE_NONE);
        assertThat(matched).isNotNull();
        assertThat(matchedDetails(matched)).isEmpty();

        context.put("order_count", 9);
        assertThat(evaluator.evaluateWithContext(context, List.of(
                condition("customer_level", "eq", "ACTIVE"),
                condition("order_count", "gt", 5)), CustomerConditionEvaluator.CONDITION_TYPE_NONE)).isNull();
    }

    @Test
    @DisplayName("evaluateWithContext 未知条件组合: null 与非 ALL/ANY/NONE 取值均不命中 (fail-closed)")
    void evaluateWithContext_unknownConditionTypeFailsClosed() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("customer_level", "ACTIVE");
        List<Map<String, Object>> conditions = List.of(condition("customer_level", "eq", "ACTIVE"));
        for (String conditionType : List.of("SOME", "all", "ALL ")) {
            assertThat(evaluator.evaluateWithContext(context, conditions, conditionType))
                    .as("conditionType=%s 不在白名单内时必须不命中", conditionType)
                    .isNull();
        }
        assertThat(evaluator.evaluateWithContext(context, conditions, null)).isNull();
    }

    @Test
    @DisplayName("evaluateWithContext 未知字段: 条件不命中, ALL 组合整体不命中")
    void evaluateWithContext_unknownField() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("customer_level", "ACTIVE");
        assertThat(evaluator.evaluateWithContext(context, List.of(
                condition("not_a_field", "eq", "ACTIVE")), CustomerConditionEvaluator.CONDITION_TYPE_ALL))
                .isNull();
    }

    @Test
    @DisplayName("evaluateWithContext 空条件: 返回空 Map (分群全量语义), 非 null 命中详情")
    void evaluateWithContext_emptyConditions() {
        Map<String, Object> context = Map.of("customer_level", "ACTIVE");
        // null / 空条件列表: 分群侧「无条件即全量」语义, 返回不含 matchedConditions 的空结果;
        // 工作流条件节点在调用前已按配置缺失显式抛错, 不会走到这里被误放行
        assertThat(evaluator.evaluateWithContext(context, new ArrayList<>(),
                CustomerConditionEvaluator.CONDITION_TYPE_ALL)).isEmpty();
        assertThat(evaluator.evaluateWithContext(context, null,
                CustomerConditionEvaluator.CONDITION_TYPE_ANY)).isEmpty();
    }

    // ============================================================
    // evaluate + buildCustomerContext
    // ============================================================

    @Test
    @DisplayName("evaluate: 由客户实体构建上下文并按 ALL 命中")
    void evaluate_withCustomerEntity() {
        ScrmCustomerEntity customer = new ScrmCustomerEntity();
        customer.setNickname("大客户张三");
        customer.setLifecycle("ACTIVE");
        customer.setLastInteractionAt(LocalDateTime.now().minusDays(3));
        customer.setCreateTime(LocalDateTime.now().minusDays(30));

        Map<String, Object> result = evaluator.evaluate(customer, List.of(
                condition("customer_level", "eq", "ACTIVE"),
                condition("customer_name", "contains", "张三"),
                condition("last_interaction_days", "between", List.of(1, 7))),
                CustomerConditionEvaluator.CONDITION_TYPE_ALL);
        assertThat(result).isNotNull();
        assertThat(matchedDetails(result)).hasSize(3);
    }

    @Test
    @DisplayName("buildCustomerContext: 日期字段按天数计算, 未对接字段给缺省值")
    void buildCustomerContext_fields() {
        ScrmCustomerEntity customer = new ScrmCustomerEntity();
        customer.setNickname("李四");
        customer.setLifecycle("NEW");
        customer.setLastInteractionAt(LocalDateTime.now().minusDays(10));
        customer.setCreateTime(LocalDateTime.now().minusDays(100));

        Map<String, Object> context = evaluator.buildCustomerContext(customer);
        assertThat(context).containsEntry("customer_name", "李四")
                .containsEntry("customer_level", "NEW")
                .containsEntry("last_interaction_days", 10L)
                .containsEntry("registration_days", 100L)
                .containsEntry("order_count", 0)
                .containsEntry("tag", "");
        // 输出的字段名与 AVAILABLE_FIELDS 一致, 防止上下文字段与可配置字段漂移
        assertThat(context.keySet()).containsExactlyInAnyOrderElementsOf(
                CustomerConditionEvaluator.AVAILABLE_FIELDS);
    }

    @Test
    @DisplayName("buildCustomerContext: 时间为空时给哨兵值 (从未互动视为极久未互动)")
    void buildCustomerContext_nullTimestamps() {
        ScrmCustomerEntity customer = new ScrmCustomerEntity();
        Map<String, Object> context = evaluator.buildCustomerContext(customer);
        assertThat(context).containsEntry("last_interaction_days", Long.MAX_VALUE)
                .containsEntry("registration_days", 0L);
        // 从未互动的客户可被 gt 条件命中, 不会被 lt 条件误命中
        assertThat((Long) context.get("last_interaction_days")).isGreaterThan(365L);
    }

    @Test
    @DisplayName("buildCustomerContext: 客户为 null 时返回空上下文, 所有条件均不命中")
    void buildCustomerContext_nullCustomer() {
        assertThat(evaluator.buildCustomerContext(null)).isEmpty();
        assertThat(evaluator.evaluate(null, List.of(condition("customer_level", "eq", "ACTIVE")),
                CustomerConditionEvaluator.CONDITION_TYPE_ALL)).isNull();
    }

    // ============================================================
    // parseConditions
    // ============================================================

    @Test
    @DisplayName("parseConditions: 合法 JSON 数组解析为条件列表")
    void parseConditions_validJson() {
        List<Map<String, Object>> conditions = evaluator.parseConditions(
                "[{\"field\":\"customer_level\",\"operator\":\"eq\",\"value\":\"ACTIVE\"}]");
        assertThat(conditions).hasSize(1);
        assertThat(conditions.get(0)).containsEntry("field", "customer_level")
                .containsEntry("operator", "eq")
                .containsEntry("value", "ACTIVE");
    }

    @Test
    @DisplayName("parseConditions: 空白与非法 JSON 宽松降级为空列表 (批量链路不中断)")
    void parseConditions_blankAndInvalid() {
        assertThat(evaluator.parseConditions(null)).isEmpty();
        assertThat(evaluator.parseConditions("  ")).isEmpty();
        assertThat(evaluator.parseConditions("{不是 JSON")).isEmpty();
    }
}
