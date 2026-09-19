/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWorkflowServiceConditionValidationTest.java
 * Date : 2026-09-19 10:12:40
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmWorkflowEntity;
import org.hiylo.scrm.entity.ScrmWorkflowInstanceEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmWorkflowInstanceRepository;
import org.hiylo.scrm.repository.ScrmWorkflowNodeLogRepository;
import org.hiylo.scrm.repository.ScrmWorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link ScrmWorkflowService#evaluateCondition} 条件节点校验语义单元测试。
 * <p>
 * {@link CustomerConditionEvaluator} 本身对未知字段 / 非法操作符按「不命中」fail-closed,
 * 但条件节点必须更进一步: 配置写错时<b>显式失败</b>把节点置 FAILED, 绝不能在条件缺失、
 * JSON 非法、字段未知、操作符非法时默认返回 TRUE —— 静默放行会让未配置的条件直接触发
 * 群发 / 打标签等真实副作用。本用例锁定这条失败策略。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmWorkflowService 条件节点显式失败单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmWorkflowServiceConditionValidationTest {

    /** 实例 ID */
    private static final Long INSTANCE_ID = 900L;
    /** 工作流 ID */
    private static final Long WORKFLOW_ID = 800L;
    /** 客户 ID */
    private static final Long CUSTOMER_ID = 500L;
    /** 条件节点 ID */
    private static final String NODE_ID = "c1";

    @Mock
    private ScrmWorkflowRepository workflowRepository;
    @Mock
    private ScrmWorkflowInstanceRepository instanceRepository;
    @Mock
    private ScrmWorkflowNodeLogRepository nodeLogRepository;
    @Mock
    private ScrmCustomerRepository customerRepository;
    @Mock
    private WorkflowActionExecutor actionExecutor;

    /** 被测服务 */
    private ScrmWorkflowService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        ScrmWorkflowDefinitionService definitionService =
                new ScrmWorkflowDefinitionService(workflowRepository, instanceRepository, nodeLogRepository,
                        objectMapper);
        ScrmWorkflowNodeService nodeService = new ScrmWorkflowNodeService(workflowRepository, instanceRepository,
                nodeLogRepository, customerRepository, new CustomerConditionEvaluator(objectMapper),
                actionExecutor, objectMapper, definitionService);
        ScrmWorkflowInstanceService instanceService = new ScrmWorkflowInstanceService(workflowRepository,
                instanceRepository, nodeLogRepository, definitionService, nodeService);
        ScrmWorkflowStatsService statsService = new ScrmWorkflowStatsService(workflowRepository,
                instanceRepository, nodeLogRepository, definitionService);
        service = new ScrmWorkflowService(definitionService, instanceService, nodeService, statsService);
    }

    /**
     * 装配工作流图: 单个 CONDITION 节点, config 由用例给定
     */
    private void givenConditionNodeConfig(String configJson, String variables) {
        ScrmWorkflowInstanceEntity instance = new ScrmWorkflowInstanceEntity();
        instance.setId(INSTANCE_ID);
        instance.setWorkflowId(WORKFLOW_ID);
        instance.setCustomerId(CUSTOMER_ID);
        instance.setStatus("RUNNING");
        instance.setCurrentNodeId(NODE_ID);
        instance.setVariables(variables);
        when(instanceRepository.findById(INSTANCE_ID)).thenReturn(Optional.of(instance));

        ScrmWorkflowEntity workflow = new ScrmWorkflowEntity();
        workflow.setId(WORKFLOW_ID);
        workflow.setNodes("[{\"id\":\"" + NODE_ID + "\",\"type\":\"CONDITION\",\"name\":\"等级判断\","
                + "\"config\":" + configJson + "}]");
        when(workflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(workflow));
    }

    /**
     * 装配客户档案 (生命周期 ACTIVE)
     */
    private void givenActiveCustomer() {
        ScrmCustomerEntity customer = new ScrmCustomerEntity();
        customer.setId(CUSTOMER_ID);
        customer.setNickname("张三");
        customer.setLifecycle("ACTIVE");
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
    }

    /**
     * 宽松装配客户档案: 条件解析先于客户上下文构建失败的用例不会用到该桩
     */
    private void givenActiveCustomerLeniently() {
        ScrmCustomerEntity customer = new ScrmCustomerEntity();
        customer.setId(CUSTOMER_ID);
        customer.setNickname("张三");
        customer.setLifecycle("ACTIVE");
        lenient().when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
    }

    /**
     * 断言非法条件配置抛出携带指定原因的 ScrmException
     */
    private void assertRejected(String configJson, String expectedMessagePart) {
        givenConditionNodeConfig(configJson, null);
        // 构建客户上下文在条件校验之前, 校验类用例需要客户存在才能走到条件校验
        givenActiveCustomerLeniently();
        assertThatThrownBy(() -> service.evaluateCondition(INSTANCE_ID, NODE_ID))
                .isInstanceOf(ScrmException.class)
                .hasFieldOrPropertyWithValue("code", "SCRM_BAD_REQUEST")
                .hasMessageContaining(expectedMessagePart);
    }

    // ============================================================
    // 合法配置: 命中 / 不命中
    // ============================================================

    @Test
    @DisplayName("evaluateCondition: ALL 全部命中返回 TRUE")
    void evaluateCondition_allMatchReturnsTrue() throws Exception {
        givenConditionNodeConfig("{\"conditions\":["
                + "{\"field\":\"customer_level\",\"operator\":\"eq\",\"value\":\"ACTIVE\"},"
                + "{\"field\":\"customer_name\",\"operator\":\"contains\",\"value\":\"张\"}]}", null);
        givenActiveCustomer();

        assertThat(service.evaluateCondition(INSTANCE_ID, NODE_ID)).isEqualTo("TRUE");
    }

    @Test
    @DisplayName("evaluateCondition: ALL 任一不命中返回 FALSE")
    void evaluateCondition_partialMatchReturnsFalse() throws Exception {
        givenConditionNodeConfig("{\"conditions\":["
                + "{\"field\":\"customer_level\",\"operator\":\"eq\",\"value\":\"ACTIVE\"},"
                + "{\"field\":\"customer_level\",\"operator\":\"eq\",\"value\":\"LOST\"}]}", null);
        givenActiveCustomer();

        assertThat(service.evaluateCondition(INSTANCE_ID, NODE_ID)).isEqualTo("FALSE");
    }

    @Test
    @DisplayName("evaluateCondition: ANY 任一命中即 TRUE, NONE 全不命中才 TRUE")
    void evaluateCondition_anyAndNone() throws Exception {
        givenConditionNodeConfig("{\"conditionType\":\"ANY\",\"conditions\":["
                + "{\"field\":\"customer_level\",\"operator\":\"eq\",\"value\":\"LOST\"},"
                + "{\"field\":\"customer_level\",\"operator\":\"eq\",\"value\":\"ACTIVE\"}]}", null);
        givenActiveCustomer();
        assertThat(service.evaluateCondition(INSTANCE_ID, NODE_ID)).isEqualTo("TRUE");

        givenConditionNodeConfig("{\"conditionType\":\"NONE\",\"conditions\":["
                + "{\"field\":\"customer_level\",\"operator\":\"eq\",\"value\":\"LOST\"}]}", null);
        assertThat(service.evaluateCondition(INSTANCE_ID, NODE_ID)).isEqualTo("TRUE");
    }

    @Test
    @DisplayName("evaluateCondition: 实例变量可覆盖客户上下文字段做判断")
    void evaluateCondition_usesInstanceVariables() throws Exception {
        givenConditionNodeConfig("{\"conditions\":["
                + "{\"field\":\"orderAmount\",\"operator\":\"gte\",\"value\":1000}]}",
                "{\"orderAmount\":1200}");
        givenActiveCustomer();

        assertThat(service.evaluateCondition(INSTANCE_ID, NODE_ID)).isEqualTo("TRUE");
    }

    @Test
    @DisplayName("evaluateCondition: 三元简化表达式与操作符别名可用")
    void evaluateCondition_simpleExpressionAlias() throws Exception {
        givenConditionNodeConfig("{\"expression\":\"customer_level == ACTIVE\"}", null);
        givenActiveCustomer();

        assertThat(service.evaluateCondition(INSTANCE_ID, NODE_ID)).isEqualTo("TRUE");
    }

    // ============================================================
    // 非法配置: 必须显式失败, 绝不默认 TRUE
    // ============================================================

    @Test
    @DisplayName("evaluateCondition: 非法操作符抛异常置失败, 不静默放行")
    void evaluateCondition_rejectsUnknownOperator() {
        assertRejected("{\"conditions\":[{\"field\":\"customer_level\",\"operator\":\"equals\","
                + "\"value\":\"ACTIVE\"}]}", "条件操作符非法");
    }

    @Test
    @DisplayName("evaluateCondition: 未知字段抛异常置失败")
    void evaluateCondition_rejectsUnknownField() {
        assertRejected("{\"conditions\":[{\"field\":\"wechat_union_id\",\"operator\":\"eq\","
                + "\"value\":\"x\"}]}", "条件字段未知");
    }

    @Test
    @DisplayName("evaluateCondition: 缺少 field / operator / value 分别抛异常")
    void evaluateCondition_rejectsIncompleteCondition() {
        assertRejected("{\"conditions\":[{\"operator\":\"eq\",\"value\":\"ACTIVE\"}]}", "条件缺少 field");
        assertRejected("{\"conditions\":[{\"field\":\"customer_level\",\"value\":\"ACTIVE\"}]}",
                "条件缺少 operator");
        assertRejected("{\"conditions\":[{\"field\":\"customer_level\",\"operator\":\"eq\"}]}",
                "条件缺少 value");
    }

    @Test
    @DisplayName("evaluateCondition: 空条件数组与未配置条件均视为配置缺失抛异常 (分群侧全量语义不得带入工作流)")
    void evaluateCondition_rejectsEmptyConditions() {
        assertRejected("{\"conditions\":[]}", "条件节点条件为空");
        assertRejected("{}", "未配置 conditions 或 expression");
        assertRejected("{\"expression\":\"   \"}", "条件节点条件为空");
    }

    @Test
    @DisplayName("evaluateCondition: conditionType 非法抛异常, 不按默认组合放行")
    void evaluateCondition_rejectsUnknownConditionType() {
        assertRejected("{\"conditionType\":\"MOST\",\"conditions\":["
                + "{\"field\":\"customer_level\",\"operator\":\"eq\",\"value\":\"ACTIVE\"}]}",
                "条件组合类型非法");
    }

    @Test
    @DisplayName("evaluateCondition: 条件书写格式非法 (坏 JSON / 坏简化式 / 类型不对) 抛异常而非降级为不命中")
    void evaluateCondition_rejectsMalformedConditionConfig() {
        assertRejected("{\"conditions\":\"[oops\"}", "条件 JSON 解析失败");
        assertRejected("{\"expression\":\"customerlevel\"}", "简化条件表达式格式非法");
        assertRejected("{\"conditions\":123}", "条件配置类型非法");
        assertRejected("{\"conditions\":[\"不是对象\"]}", "条件项必须为对象");
        // 条件节点校验阶段绝不触碰动作执行器, 未通过校验的配置不可能触发后续动作
        verifyNoInteractions(actionExecutor);
    }

    @Test
    @DisplayName("evaluateCondition: 客户档案缺失时抛异常, 不因取不到值而判 TRUE")
    void evaluateCondition_rejectsMissingCustomer() throws Exception {
        givenConditionNodeConfig("{\"conditions\":[{\"field\":\"customer_level\",\"operator\":\"ne\","
                + "\"value\":\"ACTIVE\"}]}", null);
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.evaluateCondition(INSTANCE_ID, NODE_ID))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("客户不存在");
    }
}
