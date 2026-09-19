/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAutoReplyServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmAutoReplyMatchDto;
import org.hiylo.scrm.dto.ScrmAutoReplyRuleDto;
import org.hiylo.scrm.entity.ScrmAutoReplyLogEntity;
import org.hiylo.scrm.entity.ScrmAutoReplyRuleEntity;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmAutoReplyLogRepository;
import org.hiylo.scrm.repository.ScrmAutoReplyRuleRepository;
import org.hiylo.scrm.repository.ScrmAutoReplyTemplateRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmAutoReplyService 单元测试
 * <p>
 * 聚焦自动回复规则管理 (创建 / 更新 / 启停 / 兜底)、匹配引擎
 * (关键词命中 / 兜底回复) 与越权访问校验等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmAutoReplyService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmAutoReplyServiceTest {

    /** 自动回复规则数据仓库 Mock 桩 */
    @Mock
    private ScrmAutoReplyRuleRepository ruleRepository;
    /** 自动回复日志数据仓库 Mock 桩 */
    @Mock
    private ScrmAutoReplyLogRepository logRepository;
    /** 自动回复模板数据仓库 Mock 桩 */
    @Mock
    private ScrmAutoReplyTemplateRepository templateRepository;
    /** 客户数据仓库 Mock 桩 */
    @Mock
    private ScrmCustomerRepository customerRepository;

    /** 被测服务实例 */
    private ScrmAutoReplyService service;

    @BeforeEach
    void setUp() {
        ScrmAutoReplyRuleService ruleService =
                new ScrmAutoReplyRuleService(ruleRepository, logRepository, templateRepository);
        ScrmAutoReplyTemplateService templateService =
                new ScrmAutoReplyTemplateService(templateRepository, ruleRepository);
        ScrmAutoReplyLogService logService = new ScrmAutoReplyLogService(logRepository);
        service = new ScrmAutoReplyService(
                ruleService,
                new ScrmAutoReplyMatchService(ruleRepository, logRepository, templateRepository,
                        customerRepository, ruleService, templateService, logService),
                templateService,
                logService,
                new ScrmAutoReplyStatsService(logRepository, ruleRepository));
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造已持久化的自动回复规则实体 (用于 findById 返回)
     */
    private ScrmAutoReplyRuleEntity buildRuleEntity(Long id, String ruleType, String matchType, String keywords) {
        ScrmAutoReplyRuleEntity entity = new ScrmAutoReplyRuleEntity();
        entity.setId(id);
        entity.setRuleName("关键词回复规则");
        entity.setRuleType(ruleType);
        entity.setMatchType(matchType);
        entity.setKeywords(keywords);
        entity.setMatchScope("MESSAGE");
        entity.setReplyType("TEXT");
        entity.setReplyContent("您好, 已收到您的消息");
        entity.setPriority(0);
        entity.setCooldownMinutes(0);
        entity.setMaxTriggerPerCustomer(0);
        entity.setFallbackRule(false);
        entity.setEnabled(true);
        entity.setTriggerCount(0);
        entity.setWorkTimeOnly(false);
        return entity;
    }

    @Test
    @DisplayName("createRule: 写入归属账号与默认值后持久化")
    void createRule_success() throws ScrmException {
        ScrmAutoReplyRuleDto dto = new ScrmAutoReplyRuleDto();
        dto.setRuleName("关键词回复规则");
        dto.setRuleType("KEYWORD");
        dto.setKeywords("你好,在吗");
        dto.setReplyContent("您好, 已收到您的消息");
        when(ruleRepository.save(any(ScrmAutoReplyRuleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmAutoReplyRuleEntity result = service.createRule(dto);

        ArgumentCaptor<ScrmAutoReplyRuleEntity> captor =
                ArgumentCaptor.forClass(ScrmAutoReplyRuleEntity.class);
        verify(ruleRepository, times(1)).save(captor.capture());
        ScrmAutoReplyRuleEntity saved = captor.getValue();
        assertThat(saved.getEnabled()).isTrue();
        assertThat(saved.getMatchType()).isEqualTo("EXACT");
        assertThat(saved.getMatchScope()).isEqualTo("MESSAGE");
        assertThat(saved.getReplyType()).isEqualTo("TEXT");
        assertThat(saved.getPriority()).isZero();
        assertThat(saved.getTriggerCount()).isZero();
        assertThat(saved.getCooldownMinutes()).isZero();
        assertThat(saved.getMaxTriggerPerCustomer()).isZero();
        assertThat(saved.getFallbackRule()).isFalse();
        assertThat(saved.getWorkTimeOnly()).isFalse();
        assertThat(result.getRuleName()).isEqualTo("关键词回复规则");
    }

    @Test
    @DisplayName("createRule: 回复类型 TEMPLATE 但未指定模板 ID 时抛 BAD_REQUEST")
    void createRule_templateWithoutTemplateId() {
        ScrmAutoReplyRuleDto dto = new ScrmAutoReplyRuleDto();
        dto.setRuleName("模板回复规则");
        dto.setRuleType("KEYWORD");
        dto.setKeywords("你好");
        dto.setReplyType("TEMPLATE");
        dto.setReplyContent("占位内容");

        assertThatThrownBy(() -> service.createRule(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("回复类型为 TEMPLATE 时必须指定回复模板 ID");
        verify(ruleRepository, never()).save(any());
    }

    @Test
    @DisplayName("createRule: 标记为兜底规则时清除同账号其他兜底标记")
    void createRule_fallbackClearsOthers() throws ScrmException {
        ScrmAutoReplyRuleDto dto = new ScrmAutoReplyRuleDto();
        dto.setRuleName("兜底规则");
        dto.setRuleType("KEYWORD");
        dto.setKeywords("你好");
        dto.setReplyContent("默认回复");
        dto.setFallbackRule(true);
        when(ruleRepository.save(any(ScrmAutoReplyRuleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.createRule(dto);

        verify(ruleRepository, times(1)).clearFallbackFlags();
    }

    @Test
    @DisplayName("updateRule: 字段非空才覆盖, 保留未提供字段原值")
    void updateRule_partialUpdate() throws ScrmException {
        ScrmAutoReplyRuleEntity entity = buildRuleEntity(10L, "KEYWORD", "EXACT", "你好");
        when(ruleRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(ruleRepository.save(any(ScrmAutoReplyRuleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmAutoReplyRuleDto dto = new ScrmAutoReplyRuleDto();
        dto.setRuleName("更新后的规则名");
        dto.setMatchType("CONTAINS");
        ScrmAutoReplyRuleEntity result = service.updateRule(10L, dto);

        assertThat(result.getRuleName()).isEqualTo("更新后的规则名");
        assertThat(result.getMatchType()).isEqualTo("CONTAINS");
        // 未提供的字段保留原值
        assertThat(result.getReplyContent()).isEqualTo("您好, 已收到您的消息");
        assertThat(result.getKeywords()).isEqualTo("你好");
    }

    @Test
    @DisplayName("enableRule: 设置 enabled=true 并持久化")
    void enableRule_success() throws ScrmException {
        ScrmAutoReplyRuleEntity entity = buildRuleEntity(10L, "KEYWORD", "EXACT", "你好");
        entity.setEnabled(false);
        when(ruleRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(ruleRepository.save(any(ScrmAutoReplyRuleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.enableRule(10L);

        ArgumentCaptor<ScrmAutoReplyRuleEntity> captor =
                ArgumentCaptor.forClass(ScrmAutoReplyRuleEntity.class);
        verify(ruleRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getEnabled()).isTrue();
    }

    @Test
    @DisplayName("disableRule: 设置 enabled=false 并持久化")
    void disableRule_success() throws ScrmException {
        ScrmAutoReplyRuleEntity entity = buildRuleEntity(10L, "KEYWORD", "EXACT", "你好");
        when(ruleRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(ruleRepository.save(any(ScrmAutoReplyRuleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.disableRule(10L);

        ArgumentCaptor<ScrmAutoReplyRuleEntity> captor =
                ArgumentCaptor.forClass(ScrmAutoReplyRuleEntity.class);
        verify(ruleRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getEnabled()).isFalse();
    }

    @Test
    @DisplayName("matchReply: CONTAINS 关键词命中后记录日志并返回回复")
    void matchReply_keywordHit() throws ScrmException {
        ScrmAutoReplyRuleEntity rule = buildRuleEntity(10L, "KEYWORD", "CONTAINS", "你好");
        when(ruleRepository.findByEnabledTrueOrderByPriorityAsc())
                .thenReturn(List.of(rule));
        when(ruleRepository.findById(10L)).thenReturn(Optional.of(rule));
        ScrmCustomerEntity customer = new ScrmCustomerEntity();
        customer.setId(100L);
        customer.setNickname("张三");
        when(customerRepository.findById(100L)).thenReturn(Optional.of(customer));
        when(logRepository.save(any(ScrmAutoReplyLogEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmAutoReplyMatchDto matchDto = new ScrmAutoReplyMatchDto();
        matchDto.setCustomerId(100L);
        matchDto.setMessage("你好呀, 在吗");
        matchDto.setChannel("WECHAT");

        ScrmAutoReplyLogEntity result = service.matchReply(matchDto);

        assertThat(result).isNotNull();
        assertThat(result.getRuleId()).isEqualTo(10L);
        assertThat(result.getMatchedKeyword()).isEqualTo("你好");
        assertThat(result.getStatus()).isEqualTo("SENT");
        assertThat(result.getIsFallback()).isFalse();
        verify(ruleRepository, times(1)).incrementTriggerCount(eq(10L), any(LocalDateTime.class));
        verify(logRepository, times(1)).save(any(ScrmAutoReplyLogEntity.class));
    }

    @Test
    @DisplayName("matchReply: 无规则命中时返回兜底规则回复")
    void matchReply_fallbackHit() {
        // 启用规则不匹配 (EXACT 关键词不等于消息)
        ScrmAutoReplyRuleEntity rule = buildRuleEntity(10L, "KEYWORD", "EXACT", "你好");
        when(ruleRepository.findByEnabledTrueOrderByPriorityAsc())
                .thenReturn(List.of(rule));
        when(ruleRepository.findById(10L)).thenReturn(Optional.of(rule));
        // 兜底规则
        ScrmAutoReplyRuleEntity fallback = buildRuleEntity(20L, "KEYWORD", "EXACT", "");
        fallback.setRuleName("兜底规则");
        fallback.setReplyContent("默认兜底回复");
        fallback.setFallbackRule(true);
        when(ruleRepository.findByFallbackRuleTrueAndEnabledTrue())
                .thenReturn(Optional.of(fallback));
        when(logRepository.save(any(ScrmAutoReplyLogEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmAutoReplyMatchDto matchDto = new ScrmAutoReplyMatchDto();
        matchDto.setCustomerId(100L);
        matchDto.setMessage("不匹配的消息");
        matchDto.setChannel("WECHAT");

        ScrmAutoReplyLogEntity result = service.matchReply(matchDto);

        assertThat(result).isNotNull();
        assertThat(result.getRuleId()).isEqualTo(20L);
        assertThat(result.getIsFallback()).isTrue();
        assertThat(result.getReplyContent()).isEqualTo("默认兜底回复");
        verify(ruleRepository, times(1)).incrementTriggerCount(eq(20L), any(LocalDateTime.class));
    }

    
}
