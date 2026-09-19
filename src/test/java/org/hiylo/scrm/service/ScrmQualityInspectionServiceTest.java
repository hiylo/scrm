/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmQualityInspectionServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.entity.ScrmConversationMessageEntity;
import org.hiylo.scrm.entity.ScrmQualityInspectionRuleEntity;
import org.hiylo.scrm.repository.ScrmAccountRepository;
import org.hiylo.scrm.repository.ScrmConversationMessageRepository;
import org.hiylo.scrm.repository.ScrmConversationRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmQualityInspectionResultRepository;
import org.hiylo.scrm.repository.ScrmQualityInspectionRuleRepository;
import org.hiylo.scrm.repository.ScrmQualityInspectionTaskRepository;
import org.hiylo.scrm.service.evaluator.QualityAiEvaluator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * ScrmQualityInspectionService 单元测试
 * <p>
 * 聚焦 {@code evaluateRule} 五种规则类型 (关键词 / 正则 / 会话时长 / 响应时长 / AI 评估)
 * 的评估逻辑验证, 使用 Mockito 隔离 Repository 与 AI 评估器, ObjectMapper 使用真实实例
 * 以正确解析规则配置 JSON。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmQualityInspectionService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmQualityInspectionServiceTest {

    /** 质检规则仓库 Mock */
    @Mock
    private ScrmQualityInspectionRuleRepository ruleRepository;
    /** 质检任务仓库 Mock */
    @Mock
    private ScrmQualityInspectionTaskRepository taskRepository;
    /** 质检结果仓库 Mock */
    @Mock
    private ScrmQualityInspectionResultRepository resultRepository;
    /** 会话仓库 Mock */
    @Mock
    private ScrmConversationRepository conversationRepository;
    /** 会话消息仓库 Mock */
    @Mock
    private ScrmConversationMessageRepository messageRepository;
    /** 客户档案仓库 Mock */
    @Mock
    private ScrmCustomerRepository customerRepository;
    /** 账号仓库 Mock */
    @Mock
    private ScrmAccountRepository accountRepository;
    /** AI 质检评估器 Mock */
    @Mock
    private QualityAiEvaluator qualityAiEvaluator;

    /** 规则配置 JSON 解析需真实 ObjectMapper, 故不使用 Mock */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 被测服务实例 */
    private ScrmQualityInspectionService service;

    @BeforeEach
    void setUp() {
        // 手动构造: objectMapper 需为真实实例以解析 ruleConfig JSON
        ScrmQualityInspectionResultService resultService =
                new ScrmQualityInspectionResultService(resultRepository, qualityAiEvaluator, objectMapper);
        service = new ScrmQualityInspectionService(
                new ScrmQualityInspectionRuleService(ruleRepository, objectMapper),
                new ScrmQualityInspectionTaskService(taskRepository, ruleRepository, resultRepository,
                        conversationRepository, messageRepository, customerRepository, accountRepository,
                        objectMapper, resultService),
                resultService);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造质检规则实体
     */
    private ScrmQualityInspectionRuleEntity buildRule(String ruleType, String ruleConfig, String passCondition) {
        ScrmQualityInspectionRuleEntity rule = new ScrmQualityInspectionRuleEntity();
        rule.setId(1L);
        rule.setRuleName("test-rule");
        rule.setCategory("SCRIPT_COMPLIANCE");
        rule.setRuleType(ruleType);
        rule.setRuleConfig(ruleConfig);
        rule.setPassCondition(passCondition);
        rule.setScoreWeight(1.0);
        rule.setEnabled(true);
        return rule;
    }

    /**
     * 构造会话消息实体
     */
    private ScrmConversationMessageEntity buildMessage(String direction, String content, LocalDateTime sentAt) {
        ScrmConversationMessageEntity msg = new ScrmConversationMessageEntity();
        msg.setMessageId("m-" + sentAt);
        msg.setConversationId(100L);
        msg.setMessageType("TEXT");
        msg.setDirection(direction);
        msg.setContent(content);
        msg.setSentAt(sentAt);
        return msg;
    }

    @Test
    @DisplayName("evaluateRule_keywordMatch_hit: 关键词命中, score=100, passed=true")
    void evaluateRule_keywordMatch_hit() {
        ScrmQualityInspectionRuleEntity rule = buildRule("KEYWORD_MATCH",
                "{\"keywords\":[\"hello\"]}", "CONTAINS");
        List<ScrmConversationMessageEntity> messages = List.of(
                buildMessage("IN", "hello world", LocalDateTime.of(2026, 1, 1, 10, 0, 0)));

        Map<String, Object> result = service.evaluateRule(rule, messages);

        assertThat(result.get("ruleType")).isEqualTo("KEYWORD_MATCH");
        assertThat((Double) result.get("score")).isEqualTo(100.0);
        assertThat(result.get("passed")).isEqualTo(true);
        assertThat(result.get("detail").toString()).contains("hello");
    }

    @Test
    @DisplayName("evaluateRule_keywordMatch_miss: 关键词未命中, score=0, passed=false")
    void evaluateRule_keywordMatch_miss() {
        ScrmQualityInspectionRuleEntity rule = buildRule("KEYWORD_MATCH",
                "{\"keywords\":[\"hello\"]}", "CONTAINS");
        List<ScrmConversationMessageEntity> messages = List.of(
                buildMessage("IN", "goodbye world", LocalDateTime.of(2026, 1, 1, 10, 0, 0)));

        Map<String, Object> result = service.evaluateRule(rule, messages);

        assertThat((Double) result.get("score")).isEqualTo(0.0);
        assertThat(result.get("passed")).isEqualTo(false);
    }

    @Test
    @DisplayName("evaluateRule_regex_hit: 正则匹配命中, score=100, passed=true")
    void evaluateRule_regex_hit() {
        ScrmQualityInspectionRuleEntity rule = buildRule("REGEX",
                "{\"regex\":\"\\\\d{3}\"}", "CONTAINS");
        List<ScrmConversationMessageEntity> messages = List.of(
                buildMessage("IN", "订单号 abc123def", LocalDateTime.of(2026, 1, 1, 10, 0, 0)));

        Map<String, Object> result = service.evaluateRule(rule, messages);

        assertThat((Double) result.get("score")).isEqualTo(100.0);
        assertThat(result.get("passed")).isEqualTo(true);
    }

    @Test
    @DisplayName("evaluateRule_regex_miss: 正则未匹配, score=0, passed=false")
    void evaluateRule_regex_miss() {
        ScrmQualityInspectionRuleEntity rule = buildRule("REGEX",
                "{\"regex\":\"\\\\d{3}\"}", "CONTAINS");
        List<ScrmConversationMessageEntity> messages = List.of(
                buildMessage("IN", "没有数字的纯文本消息", LocalDateTime.of(2026, 1, 1, 10, 0, 0)));

        Map<String, Object> result = service.evaluateRule(rule, messages);

        assertThat((Double) result.get("score")).isEqualTo(0.0);
        assertThat(result.get("passed")).isEqualTo(false);
    }

    @Test
    @DisplayName("evaluateRule_duration_over: 会话时长超过阈值, score=100, passed=true")
    void evaluateRule_duration_over() {
        ScrmQualityInspectionRuleEntity rule = buildRule("DURATION",
                "{\"minDurationSeconds\":60}", "GTE:80");
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 10, 0, 0);
        List<ScrmConversationMessageEntity> messages = List.of(
                buildMessage("IN", "hi", base),
                buildMessage("OUT", "hello", base.plusSeconds(120)));

        Map<String, Object> result = service.evaluateRule(rule, messages);

        assertThat((Double) result.get("score")).isEqualTo(100.0);
        assertThat(result.get("passed")).isEqualTo(true);
    }

    @Test
    @DisplayName("evaluateRule_duration_under: 会话时长低于阈值, score=50, passed=false")
    void evaluateRule_duration_under() {
        ScrmQualityInspectionRuleEntity rule = buildRule("DURATION",
                "{\"minDurationSeconds\":60}", "GTE:80");
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 10, 0, 0);
        List<ScrmConversationMessageEntity> messages = List.of(
                buildMessage("IN", "hi", base),
                buildMessage("OUT", "hello", base.plusSeconds(30)));

        Map<String, Object> result = service.evaluateRule(rule, messages);

        assertThat((Double) result.get("score")).isEqualTo(50.0);
        assertThat(result.get("passed")).isEqualTo(false);
    }

    @Test
    @DisplayName("evaluateRule_responseTime_over: 响应时长超过阈值, score=50, passed=false")
    void evaluateRule_responseTime_over() {
        ScrmQualityInspectionRuleEntity rule = buildRule("RESPONSE_TIME",
                "{\"maxResponseSeconds\":60}", "GTE:80");
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 10, 0, 0);
        List<ScrmConversationMessageEntity> messages = List.of(
                buildMessage("IN", "问题", base),
                buildMessage("OUT", "回答", base.plusSeconds(120)));

        Map<String, Object> result = service.evaluateRule(rule, messages);

        assertThat((Double) result.get("score")).isEqualTo(50.0);
        assertThat(result.get("passed")).isEqualTo(false);
    }

    @Test
    @DisplayName("evaluateRule_responseTime_under: 响应时长低于阈值, score=100, passed=true")
    void evaluateRule_responseTime_under() {
        ScrmQualityInspectionRuleEntity rule = buildRule("RESPONSE_TIME",
                "{\"maxResponseSeconds\":60}", "GTE:80");
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 10, 0, 0);
        List<ScrmConversationMessageEntity> messages = List.of(
                buildMessage("IN", "问题", base),
                buildMessage("OUT", "回答", base.plusSeconds(30)));

        Map<String, Object> result = service.evaluateRule(rule, messages);

        assertThat((Double) result.get("score")).isEqualTo(100.0);
        assertThat(result.get("passed")).isEqualTo(true);
    }

    @Test
    @DisplayName("evaluateRule_aiEvaluate: 调用 QualityAiEvaluator 返回 90 分, score=90, passed=true")
    void evaluateRule_aiEvaluate() {
        ScrmQualityInspectionRuleEntity rule = buildRule("AI_EVALUATE",
                "{\"promptTemplate\":\"评估服务态度\"}", "GTE:80");
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 10, 0, 0);
        List<ScrmConversationMessageEntity> messages = List.of(
                buildMessage("IN", "你好", base));

        when(qualityAiEvaluator.evaluate(any(), any(), any()))
                .thenReturn(new QualityAiEvaluator.EvaluationResult(90.0, "test"));

        Map<String, Object> result = service.evaluateRule(rule, messages);

        assertThat((Double) result.get("score")).isEqualTo(90.0);
        assertThat(result.get("passed")).isEqualTo(true);
        assertThat(result.get("detail")).isEqualTo("test");
    }
}
