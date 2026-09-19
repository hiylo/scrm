/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmRiskRuleServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.callback.ConversationEventCallbackDto;
import org.hiylo.scrm.dto.ScrmRiskRuleDto;
import org.hiylo.scrm.entity.ScrmRiskRuleEntity;
import org.hiylo.scrm.entity.ScrmRiskSignalEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmRiskRuleRepository;
import org.hiylo.scrm.repository.ScrmRiskSignalRepository;
import org.hiylo.scrm.service.RiskRuleEvaluator.RiskRuleContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmRiskRuleService 单元测试
 * <p>
 * 聚焦风险规则 CRUD (参数校验 / 默认值填充 / ruleCode 唯一性)、规则启用/禁用、
 * 删除前信号引用检查、消息评估链路 (规则加载 / SpEL 评估 / 信号持久化 / 告警推送 /
 * 单条异常跳过)、数据隔离与分页查询等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmRiskRuleService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmRiskRuleServiceTest {

    /** 风险规则仓库 Mock */
    @Mock
    private ScrmRiskRuleRepository riskRuleRepository;
    /** 风险规则评估器 Mock */
    @Mock
    private RiskRuleEvaluator riskRuleEvaluator;
    /** 风险信号仓库 Mock */
    @Mock
    private ScrmRiskSignalRepository riskSignalRepository;
    /** 通知服务 Mock */
    @Mock
    private ScrmNotificationService notificationService;

    /** 被测服务实例 */
    private ScrmRiskRuleService service;

    @BeforeEach
    void setUp() {
        service = new ScrmRiskRuleService(riskRuleRepository, riskRuleEvaluator, riskSignalRepository
            , notificationService);
    }

    @AfterEach
    void tearDown() {
    }

    // ==================== 规则 CRUD ====================

    @Test
    @DisplayName("createRule: 成功创建, enabled 缺省 true / priority 缺省 100 / action 缺省 ALERT")
    void createRule_success() throws Exception {
        ScrmRiskRuleDto dto = buildRuleDto();
        when(riskRuleRepository.findByRuleCode("RULE_001")).thenReturn(Optional.empty());
        when(riskRuleRepository.save(any(ScrmRiskRuleEntity.class)))
                .thenAnswer(inv -> assignId(inv.getArgument(0), 1000L));

        ScrmRiskRuleEntity result = service.createRule(dto);

        assertThat(result.getId()).isEqualTo(1000L);
        ArgumentCaptor<ScrmRiskRuleEntity> captor = ArgumentCaptor.forClass(ScrmRiskRuleEntity.class);
        verify(riskRuleRepository).save(captor.capture());
        ScrmRiskRuleEntity saved = captor.getValue();
        assertThat(saved.getEnabled()).isTrue();
        assertThat(saved.getPriority()).isEqualTo(100);
        assertThat(saved.getAction()).isEqualTo("ALERT");
        assertThat(saved.getRuleName()).isEqualTo("关键词检测");
        assertThat(saved.getRuleCode()).isEqualTo("RULE_001");
    }

    @Test
    @DisplayName("createRule: dto 为 null 抛 BAD_REQUEST")
    void createRule_nullDto() {
        assertThatThrownBy(() -> service.createRule(null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("规则参数不能为空");
    }

    @Test
    @DisplayName("createRule: ruleName 为空抛 BAD_REQUEST")
    void createRule_blankRuleName() {
        ScrmRiskRuleDto dto = buildRuleDto();
        dto.setRuleName("");
        assertThatThrownBy(() -> service.createRule(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("规则名称不能为空");
    }

    @Test
    @DisplayName("createRule: ruleCode 为空抛 BAD_REQUEST")
    void createRule_blankRuleCode() {
        ScrmRiskRuleDto dto = buildRuleDto();
        dto.setRuleCode("");
        assertThatThrownBy(() -> service.createRule(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("规则代码不能为空");
    }

    @Test
    @DisplayName("createRule: conditionExpression 为空抛 BAD_REQUEST")
    void createRule_blankConditionExpression() {
        ScrmRiskRuleDto dto = buildRuleDto();
        dto.setConditionExpression("");
        assertThatThrownBy(() -> service.createRule(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("条件表达式不能为空");
    }

    @Test
    @DisplayName("createRule: riskLevel 为 null 抛 BAD_REQUEST")
    void createRule_nullRiskLevel() {
        ScrmRiskRuleDto dto = buildRuleDto();
        dto.setRiskLevel(null);
        assertThatThrownBy(() -> service.createRule(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("风险等级不能为空");
    }

    @Test
    @DisplayName("createRule: riskLevel 非法抛 BAD_REQUEST")
    void createRule_invalidRiskLevel() {
        ScrmRiskRuleDto dto = buildRuleDto();
        dto.setRiskLevel("URGENT");
        assertThatThrownBy(() -> service.createRule(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("风险等级非法");
    }

    @Test
    @DisplayName("createRule: signalType 为空抛 BAD_REQUEST")
    void createRule_blankSignalType() {
        ScrmRiskRuleDto dto = buildRuleDto();
        dto.setSignalType("");
        assertThatThrownBy(() -> service.createRule(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("信号类型不能为空");
    }

    @Test
    @DisplayName("createRule: action 非法抛 BAD_REQUEST")
    void createRule_invalidAction() {
        ScrmRiskRuleDto dto = buildRuleDto();
        dto.setAction("BLOCK");
        assertThatThrownBy(() -> service.createRule(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("触发动作非法");
    }

    @Test
    @DisplayName("createRule: priority 超出范围抛 BAD_REQUEST")
    void createRule_priorityOutOfRange() {
        ScrmRiskRuleDto dto = buildRuleDto();
        dto.setPriority(0);
        assertThatThrownBy(() -> service.createRule(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("优先级必须");
    }

    @Test
    @DisplayName("createRule: ruleCode 重复抛 CONFLICT")
    void createRule_duplicateRuleCode() {
        ScrmRiskRuleDto dto = buildRuleDto();
        when(riskRuleRepository.findByRuleCode("RULE_001"))
                .thenReturn(Optional.of(new ScrmRiskRuleEntity()));
        assertThatThrownBy(() -> service.createRule(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("规则代码已存在");
    }

    @Test
    @DisplayName("updateRule: 部分更新, ruleCode 变更且不冲突时成功")
    void updateRule_success() throws Exception {
        ScrmRiskRuleEntity existing = buildRuleEntity(1000L);
        when(riskRuleRepository.findById(1000L)).thenReturn(Optional.of(existing));
        when(riskRuleRepository.findByRuleCode("RULE_002")).thenReturn(Optional.empty());
        when(riskRuleRepository.save(any(ScrmRiskRuleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmRiskRuleDto dto = new ScrmRiskRuleDto();
        dto.setRuleCode("RULE_002");
        dto.setRuleName("更新后名称");
        ScrmRiskRuleEntity result = service.updateRule(1000L, dto);

        assertThat(result.getRuleCode()).isEqualTo("RULE_002");
        assertThat(result.getRuleName()).isEqualTo("更新后名称");
    }

    @Test
    @DisplayName("updateRule: ruleCode 被其他规则占用抛 CONFLICT")
    void updateRule_ruleCodeConflict() {
        ScrmRiskRuleEntity existing = buildRuleEntity(1000L);
        ScrmRiskRuleEntity other = buildRuleEntity(2000L);
        when(riskRuleRepository.findById(1000L)).thenReturn(Optional.of(existing));
        when(riskRuleRepository.findByRuleCode("RULE_OTHER")).thenReturn(Optional.of(other));

        ScrmRiskRuleDto dto = new ScrmRiskRuleDto();
        dto.setRuleCode("RULE_OTHER");
        assertThatThrownBy(() -> service.updateRule(1000L, dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("规则代码已被其他规则占用");
    }

    @Test
    @DisplayName("updateRule: 不存在抛 NOT_FOUND")
    void updateRule_notFound() {
        when(riskRuleRepository.findById(999L)).thenReturn(Optional.empty());
        ScrmRiskRuleDto dto = new ScrmRiskRuleDto();
        assertThatThrownBy(() -> service.updateRule(999L, dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("风险规则不存在");
    }

    @Test
    @DisplayName("deleteRule: 无信号引用时成功删除")
    void deleteRule_success() throws Exception {
        ScrmRiskRuleEntity existing = buildRuleEntity(1000L);
        when(riskRuleRepository.findById(1000L)).thenReturn(Optional.of(existing));
        when(riskSignalRepository.countByRuleId("1000")).thenReturn(0L);
        service.deleteRule(1000L);
        verify(riskRuleRepository).delete(existing);
    }

    @Test
    @DisplayName("deleteRule: 有信号引用抛 CONFLICT")
    void deleteRule_hasSignalReference() {
        ScrmRiskRuleEntity existing = buildRuleEntity(1000L);
        when(riskRuleRepository.findById(1000L)).thenReturn(Optional.of(existing));
        when(riskSignalRepository.countByRuleId("1000")).thenReturn(3L);
        assertThatThrownBy(() -> service.deleteRule(1000L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("仍有 3 条风控信号引用该规则");
        verify(riskRuleRepository, never()).delete(any(ScrmRiskRuleEntity.class));
    }

    
    @Test
    @DisplayName("getRule: 存在且同账号返回实体")
    void getRule_success() throws Exception {
        ScrmRiskRuleEntity existing = buildRuleEntity(1000L);
        when(riskRuleRepository.findById(1000L)).thenReturn(Optional.of(existing));
        ScrmRiskRuleEntity result = service.getRule(1000L);
        assertThat(result.getId()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("getRule: 不存在抛 NOT_FOUND")
    void getRule_notFound() {
        when(riskRuleRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getRule(999L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("风险规则不存在");
    }

    // ==================== 启用 / 禁用 ====================

    @Test
    @DisplayName("enableRule: 成功启用")
    void enableRule_success() throws Exception {
        ScrmRiskRuleEntity existing = buildRuleEntity(1000L);
        existing.setEnabled(false);
        when(riskRuleRepository.findById(1000L)).thenReturn(Optional.of(existing));
        when(riskRuleRepository.save(any(ScrmRiskRuleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        service.enableRule(1000L);
        assertThat(existing.getEnabled()).isTrue();
    }

    @Test
    @DisplayName("disableRule: 成功禁用")
    void disableRule_success() throws Exception {
        ScrmRiskRuleEntity existing = buildRuleEntity(1000L);
        existing.setEnabled(true);
        when(riskRuleRepository.findById(1000L)).thenReturn(Optional.of(existing));
        when(riskRuleRepository.save(any(ScrmRiskRuleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        service.disableRule(1000L);
        assertThat(existing.getEnabled()).isFalse();
    }

    // ==================== 消息评估 ====================

    @Test
    @DisplayName("evaluateMessage: event 为 null 返回空列表")
    void evaluateMessage_nullEvent() {
        List<ScrmRiskSignalEntity> result = service.evaluateMessage(null);
        assertThat(result).isEmpty();
        verify(riskRuleRepository, never()).findByEnabledTrueOrderByPriorityAsc();
    }

    @Test
    @DisplayName("evaluateMessage: 无启用规则返回空列表")
    void evaluateMessage_noRules() {
        when(riskRuleRepository.findByEnabledTrueOrderByPriorityAsc())
                .thenReturn(Collections.emptyList());
        ConversationEventCallbackDto event = buildEvent();
        List<ScrmRiskSignalEntity> result = service.evaluateMessage(event);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("evaluateMessage: 规则命中时持久化信号并推送告警")
    void evaluateMessage_hit() {
        ScrmRiskRuleEntity rule = buildRuleEntity(1000L);
        rule.setConditionExpression("#message.contains('微信')");
        when(riskRuleRepository.findByEnabledTrueOrderByPriorityAsc())
                .thenReturn(List.of(rule));
        when(riskRuleEvaluator.evaluate(eq("#message.contains('微信')"), any(RiskRuleContext.class)))
                .thenReturn(true);
        when(riskSignalRepository.save(any(ScrmRiskSignalEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ConversationEventCallbackDto event = buildEvent();
        event.setContent("加个微信吧");
        List<ScrmRiskSignalEntity> result = service.evaluateMessage(event);

        assertThat(result).hasSize(1);
        ArgumentCaptor<ScrmRiskSignalEntity> captor = ArgumentCaptor.forClass(ScrmRiskSignalEntity.class);
        verify(riskSignalRepository).save(captor.capture());
        ScrmRiskSignalEntity signal = captor.getValue();
        assertThat(signal.getRuleId()).isEqualTo("1000");
        assertThat(signal.getSignalType()).isEqualTo("keyword_match");
        assertThat(signal.getRiskLevel()).isEqualTo("HIGH");
        assertThat(signal.getDetail()).contains("关键词检测").contains("RULE_001");
        verify(notificationService).notifyRiskSignal(eq("1000"), eq(null), eq("HIGH"), anyString());
        verify(notificationService).sendRiskAlert(eq(500L), eq("RULE_001"), anyString());
    }

    @Test
    @DisplayName("evaluateMessage: 规则未命中不持久化信号")
    void evaluateMessage_miss() {
        ScrmRiskRuleEntity rule = buildRuleEntity(1000L);
        when(riskRuleRepository.findByEnabledTrueOrderByPriorityAsc())
                .thenReturn(List.of(rule));
        when(riskRuleEvaluator.evaluate(anyString(), any(RiskRuleContext.class)))
                .thenReturn(false);

        ConversationEventCallbackDto event = buildEvent();
        event.setContent("正常消息");
        List<ScrmRiskSignalEntity> result = service.evaluateMessage(event);

        assertThat(result).isEmpty();
        verify(riskSignalRepository, never()).save(any());
    }

    @Test
    @DisplayName("evaluateMessage: 评估异常跳过该规则, 继续评估后续规则")
    void evaluateMessage_evaluatorException_skipRule() {
        ScrmRiskRuleEntity rule1 = buildRuleEntity(1000L);
        ScrmRiskRuleEntity rule2 = buildRuleEntity(1001L);
        rule2.setRuleCode("RULE_002");
        rule2.setConditionExpression("#message.contains('转账')");
        when(riskRuleRepository.findByEnabledTrueOrderByPriorityAsc())
                .thenReturn(List.of(rule1, rule2));
        when(riskRuleEvaluator.evaluate(eq("#message.contains('微信')"), any(RiskRuleContext.class)))
                .thenThrow(new RuntimeException("SpEL 解析失败"));
        when(riskRuleEvaluator.evaluate(eq("#message.contains('转账')"), any(RiskRuleContext.class)))
                .thenReturn(true);
        when(riskSignalRepository.save(any(ScrmRiskSignalEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ConversationEventCallbackDto event = buildEvent();
        event.setContent("请转账");
        List<ScrmRiskSignalEntity> result = service.evaluateMessage(event);

        assertThat(result).hasSize(1);
        verify(riskSignalRepository, times(1)).save(any());
    }

    // ==================== 分页查询 ====================

    @Test
    @DisplayName("listRules: 返回分页结果")
    void listRules_success() {
        ScrmRiskRuleEntity rule = buildRuleEntity(1000L);
        Page<ScrmRiskRuleEntity> page = new PageImpl<>(List.of(rule), PageRequest.of(0, 10), 1);
        when(riskRuleRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        Page<ScrmRiskRuleEntity> result = service.listRules(null, null, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getRuleCode()).isEqualTo("RULE_001");
    }

    @Test
    @DisplayName("listRules: enabled=true 过滤返回匹配规则")
    void listRules_filterByEnabled() {
        ScrmRiskRuleEntity rule = buildRuleEntity(1000L);
        Page<ScrmRiskRuleEntity> page = new PageImpl<>(List.of(rule), PageRequest.of(0, 10), 1);
        when(riskRuleRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        Page<ScrmRiskRuleEntity> result = service.listRules(true, null, 0, 10);

        assertThat(result.getContent()).hasSize(1);
    }

    // ==================== 辅助方法 ====================

    private ScrmRiskRuleDto buildRuleDto() {
        ScrmRiskRuleDto dto = new ScrmRiskRuleDto();
        dto.setRuleName("关键词检测");
        dto.setRuleCode("RULE_001");
        dto.setConditionExpression("#message.contains('微信')");
        dto.setRiskLevel("HIGH");
        dto.setSignalType("keyword_match");
        return dto;
    }

    private ScrmRiskRuleEntity buildRuleEntity(Long id) {
        ScrmRiskRuleEntity entity = new ScrmRiskRuleEntity();
        entity.setId(id);
        entity.setRuleName("关键词检测");
        entity.setRuleCode("RULE_001");
        entity.setConditionExpression("#message.contains('微信')");
        entity.setRiskLevel("HIGH");
        entity.setSignalType("keyword_match");
        entity.setEnabled(true);
        entity.setPriority(100);
        entity.setAction("ALERT");
        return entity;
    }

    private ConversationEventCallbackDto buildEvent() {
        ConversationEventCallbackDto event = new ConversationEventCallbackDto();
        event.setPlatformType("wechat_personal");
        event.setAccountId("500");
        event.setCustomerId("cust_001");
        event.setContent("test");
        event.setSentAt(LocalDateTime.now());
        return event;
    }

    private ScrmRiskRuleEntity assignId(ScrmRiskRuleEntity entity, Long id) {
        entity.setId(id);
        return entity;
    }
}
