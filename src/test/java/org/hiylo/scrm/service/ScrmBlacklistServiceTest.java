/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBlacklistServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.dto.ScrmBlacklistCheckDto;
import org.hiylo.scrm.dto.ScrmBlacklistDto;
import org.hiylo.scrm.dto.ScrmBlacklistRuleDto;
import org.hiylo.scrm.entity.ScrmBlacklistEntity;
import org.hiylo.scrm.entity.ScrmBlacklistRuleEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmBlacklistRepository;
import org.hiylo.scrm.repository.ScrmBlacklistRuleRepository;
import org.hiylo.scrm.repository.ScrmRiskEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
 * ScrmBlacklistService 单元测试
 * <p>
 * 聚焦名单管理 / 规则评估 / 风险评分计算 / 越权访问校验等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmBlacklistService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmBlacklistServiceTest {

    /** 黑名单数据仓库 Mock 桩 */
    @Mock
    private ScrmBlacklistRepository blacklistRepository;
    /** 黑名单规则数据仓库 Mock 桩 */
    @Mock
    private ScrmBlacklistRuleRepository ruleRepository;
    /** 风险事件数据仓库 Mock 桩 */
    @Mock
    private ScrmRiskEventRepository eventRepository;

    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 被测服务实例 */
    private ScrmBlacklistService service;

    @BeforeEach
    void setUp() {
        ScrmBlacklistManageService blacklistManageService =
                new ScrmBlacklistManageService(blacklistRepository);
        ScrmRiskEventService eventService =
                new ScrmRiskEventService(eventRepository, ruleRepository, blacklistManageService);
        ScrmBlacklistRuleService ruleService = new ScrmBlacklistRuleService(
                ruleRepository, eventRepository, objectMapper, blacklistManageService, eventService);
        ScrmBlacklistStatsService statsService = new ScrmBlacklistStatsService(
                blacklistRepository, ruleRepository, eventRepository, blacklistManageService, ruleService);
        service = new ScrmBlacklistService(blacklistManageService, ruleService, eventService, statsService);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造已持久化的名单条目 (用于 findById 返回)
     */
    private ScrmBlacklistEntity buildBlacklistEntity(Long id, String status) {
        ScrmBlacklistEntity entity = new ScrmBlacklistEntity();
        entity.setId(id);
        entity.setListType("BLACKLIST");
        entity.setTargetType("PHONE");
        entity.setTargetValue("13800000000");
        entity.setRiskLevel("HIGH");
        entity.setRiskScore(80.0);
        entity.setStatus(status);
        entity.setIsPermanent(false);
        return entity;
    }

    /**
     * 构造已持久化的规则实体 (用于 findById 返回)
     */
    private ScrmBlacklistRuleEntity buildRuleEntity(Long id) {
        ScrmBlacklistRuleEntity entity = new ScrmBlacklistRuleEntity();
        entity.setId(id);
        entity.setRuleName("高频下单规则");
        entity.setRuleCode("RULE-" + id);
        entity.setRuleType("FREQUENCY");
        entity.setRiskCategory("FRAUD");
        entity.setConditionField("orderCount");
        entity.setConditionOperator("GTE");
        entity.setConditionValue("10");
        entity.setSeverity("HIGH");
        entity.setAction("BLOCK");
        entity.setEnabled(true);
        entity.setPriority(0);
        return entity;
    }

    @Test
    @DisplayName("addToBlacklist: 写入归属账号与默认值后持久化")
    void addToBlacklist_success() throws ScrmException {
        ScrmBlacklistDto dto = new ScrmBlacklistDto();
        dto.setListType("BLACKLIST");
        dto.setTargetType("PHONE");
        dto.setTargetValue("13800000000");
        dto.setReason("欺诈下单");
        dto.setSource("MANUAL");
        dto.setAddedBy("admin");
        when(blacklistRepository.save(any(ScrmBlacklistEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmBlacklistEntity result = service.addToBlacklist(dto);

        ArgumentCaptor<ScrmBlacklistEntity> captor =
                ArgumentCaptor.forClass(ScrmBlacklistEntity.class);
        verify(blacklistRepository, times(1)).save(captor.capture());
        ScrmBlacklistEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getRiskLevel()).isEqualTo("MEDIUM");
        assertThat(saved.getRiskScore()).isZero();
        assertThat(saved.getIsPermanent()).isFalse();
        assertThat(saved.getEffectiveDate()).isEqualTo(LocalDate.now());
        assertThat(saved.getReviewCount()).isZero();
        assertThat(saved.getAddedAt()).isNotNull();
        assertThat(result.getListType()).isEqualTo("BLACKLIST");
    }

    @Test
    @DisplayName("addToBlacklist: 名单类型非法时抛 BAD_REQUEST")
    void addToBlacklist_invalidListType() {
        ScrmBlacklistDto dto = new ScrmBlacklistDto();
        dto.setListType("INVALID");
        dto.setTargetType("PHONE");
        dto.setTargetValue("13800000000");
        dto.setReason("欺诈下单");
        dto.setSource("MANUAL");
        dto.setAddedBy("admin");

        assertThatThrownBy(() -> service.addToBlacklist(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("名单类型非法");
        verify(blacklistRepository, never()).save(any());
    }

    @Test
    @DisplayName("removeFromBlacklist: 已移除条目再次移除抛 BAD_REQUEST")
    void removeFromBlacklist_alreadyRemoved() {
        ScrmBlacklistEntity entity = buildBlacklistEntity(10L, "REMOVED");
        when(blacklistRepository.findById(10L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.removeFromBlacklist(10L, "重复移除", "admin"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("名单条目已移除");
        verify(blacklistRepository, never()).save(any());
    }

    @Test
    @DisplayName("removeFromBlacklist: 状态置 REMOVED 并记录移除信息")
    void removeFromBlacklist_success() throws ScrmException {
        ScrmBlacklistEntity entity = buildBlacklistEntity(10L, "ACTIVE");
        when(blacklistRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(blacklistRepository.save(any(ScrmBlacklistEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmBlacklistEntity result = service.removeFromBlacklist(10L, "误报", "admin");

        ArgumentCaptor<ScrmBlacklistEntity> captor =
                ArgumentCaptor.forClass(ScrmBlacklistEntity.class);
        verify(blacklistRepository, times(1)).save(captor.capture());
        ScrmBlacklistEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("REMOVED");
        assertThat(saved.getRemovedBy()).isEqualTo("admin");
        assertThat(saved.getRemovedAt()).isNotNull();
        assertThat(saved.getRemoveReason()).isEqualTo("误报");
        assertThat(result.getStatus()).isEqualTo("REMOVED");
    }

    @Test
    @DisplayName("checkBlacklist: 命中活跃名单时返回 hit=true 与最大风险等级")
    void checkBlacklist_hit() throws ScrmException {
        ScrmBlacklistEntity match = buildBlacklistEntity(10L, "ACTIVE");
        when(blacklistRepository.findByTargetTypeAndTargetValueAndStatus(eq("PHONE"), eq("13800000000"), eq("ACTIVE")))
                .thenReturn(List.of(match));

        ScrmBlacklistCheckDto checkDto = new ScrmBlacklistCheckDto();
        checkDto.setTargetType("PHONE");
        checkDto.setTargetValue("13800000000");

        Map<String, Object> result = service.checkBlacklist(checkDto);

        assertThat(result.get("hit")).isEqualTo(Boolean.TRUE);
        assertThat(result.get("matchCount")).isEqualTo(1);
        assertThat(result.get("maxRiskLevel")).isEqualTo("HIGH");
        assertThat(result.get("maxRiskScore")).isEqualTo(80.0);
        assertThat((List<?>) result.get("matches")).hasSize(1);
    }

    @Test
    @DisplayName("createRule: 规则代码重复时抛 CONFLICT")
    void createRule_duplicateCode() {
        ScrmBlacklistRuleDto dto = new ScrmBlacklistRuleDto();
        dto.setRuleName("高频下单");
        dto.setRuleCode("RULE-DUP");
        dto.setRuleType("FREQUENCY");
        dto.setRiskCategory("FRAUD");
        dto.setConditionField("orderCount");
        dto.setConditionOperator("GTE");
        dto.setConditionValue("10");
        when(ruleRepository.findByRuleCode("RULE-DUP"))
                .thenReturn(Optional.of(buildRuleEntity(99L)));

        assertThatThrownBy(() -> service.createRule(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("规则代码已存在");
        verify(ruleRepository, never()).save(any());
    }

    @Test
    @DisplayName("createRule: 写入归属账号与默认值后持久化")
    void createRule_success() throws ScrmException {
        ScrmBlacklistRuleDto dto = new ScrmBlacklistRuleDto();
        dto.setRuleName("高频下单");
        dto.setRuleCode("RULE-NEW");
        dto.setRuleType("FREQUENCY");
        dto.setRiskCategory("FRAUD");
        dto.setConditionField("orderCount");
        dto.setConditionOperator("GTE");
        dto.setConditionValue("10");
        when(ruleRepository.findByRuleCode("RULE-NEW"))
                .thenReturn(Optional.empty());
        when(ruleRepository.save(any(ScrmBlacklistRuleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmBlacklistRuleEntity result = service.createRule(dto);

        ArgumentCaptor<ScrmBlacklistRuleEntity> captor =
                ArgumentCaptor.forClass(ScrmBlacklistRuleEntity.class);
        verify(ruleRepository, times(1)).save(captor.capture());
        ScrmBlacklistRuleEntity saved = captor.getValue();
        assertThat(saved.getSeverity()).isEqualTo("MEDIUM");
        assertThat(saved.getAction()).isEqualTo("ALERT");
        assertThat(saved.getEnabled()).isTrue();
        assertThat(saved.getPriority()).isZero();
        assertThat(saved.getTriggerCount()).isZero();
        assertThat(saved.getFalsePositiveCount()).isZero();
        assertThat(result.getRuleCode()).isEqualTo("RULE-NEW");
    }

    @Test
    @DisplayName("evaluateRule: GTE 条件命中返回 triggered=true")
    void evaluateRule_conditionMet() throws ScrmException {
        ScrmBlacklistRuleEntity rule = buildRuleEntity(10L);
        when(ruleRepository.findById(10L)).thenReturn(Optional.of(rule));

        Map<String, Object> result = service.evaluateRule(10L, Map.of("orderCount", 15));

        assertThat(result.get("triggered")).isEqualTo(Boolean.TRUE);
        assertThat(result.get("ruleCode")).isEqualTo("RULE-10");
        assertThat(result.get("value")).isEqualTo(15);
        assertThat(result.get("severity")).isEqualTo("HIGH");
        assertThat(result.get("action")).isEqualTo("BLOCK");
    }

    @Test
    @DisplayName("evaluateRule: GTE 条件未命中返回 triggered=false")
    void evaluateRule_conditionNotMet() throws ScrmException {
        ScrmBlacklistRuleEntity rule = buildRuleEntity(10L);
        when(ruleRepository.findById(10L)).thenReturn(Optional.of(rule));

        Map<String, Object> result = service.evaluateRule(10L, Map.of("orderCount", 5));

        assertThat(result.get("triggered")).isEqualTo(Boolean.FALSE);
    }

    @Test
    @DisplayName("calculateRiskScore: 加权计算风险分并返回明细")
    void calculateRiskScore_weightedSum() {
        Map<String, Double> factors = Map.of(
                "blacklist_hit", 100.0,
                "rule_triggered", 50.0,
                "risk_level", 75.0);

        Map<String, Object> result = service.calculateRiskScore(factors);

        double score = ((Number) result.get("score")).doubleValue();
        assertThat(score).isGreaterThan(0.0).isLessThanOrEqualTo(100.0);
        Map<String, Object> breakdown = (Map<String, Object>) result.get("breakdown");
        assertThat(breakdown).containsOnlyKeys("blacklist_hit", "rule_triggered", "risk_level");
        Map<String, Object> blacklistFactor = (Map<String, Object>) breakdown.get("blacklist_hit");
        assertThat(blacklistFactor.get("weight")).isEqualTo(0.4);
        assertThat(blacklistFactor.get("value")).isEqualTo(100.0);
    }

    
}
