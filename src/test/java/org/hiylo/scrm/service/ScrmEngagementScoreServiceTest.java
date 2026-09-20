/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmEngagementScoreServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmEngagementRuleDto;
import org.hiylo.scrm.entity.ScrmEngagementRuleEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmEngagementEventRepository;
import org.hiylo.scrm.repository.ScrmEngagementLevelRepository;
import org.hiylo.scrm.repository.ScrmEngagementRuleRepository;
import org.hiylo.scrm.repository.ScrmEngagementScoreRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
 * ScrmEngagementScoreService 单元测试
 * <p>
 * 聚焦互动评分规则管理 (创建 / 更新 / 启停 / 默认值填充)、规则匹配 (行为类型 + 渠道过滤)、
 * 衰减算法 (LINEAR / EXPONENTIAL / STEP / NONE)、活跃等级判定与越权访问校验等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmEngagementScoreService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmEngagementScoreServiceTest {

    /** 互动评分规则仓库 Mock */
    @Mock
    private ScrmEngagementRuleRepository ruleRepository;
    /** 互动行为事件仓库 Mock */
    @Mock
    private ScrmEngagementEventRepository eventRepository;
    /** 互动评分仓库 Mock */
    @Mock
    private ScrmEngagementScoreRepository scoreRepository;
    /** 互动活跃等级仓库 Mock */
    @Mock
    private ScrmEngagementLevelRepository levelRepository;

    /** 被测服务实例 */
    private ScrmEngagementScoreService service;

    @BeforeEach
    void setUp() {
        ScrmEngagementScoreRuleService ruleService =
                new ScrmEngagementScoreRuleService(ruleRepository);
        ScrmEngagementScoreLevelService levelService =
                new ScrmEngagementScoreLevelService(levelRepository);
        ScrmEngagementScoreEventService eventScoreService =
                new ScrmEngagementScoreEventService(eventRepository, scoreRepository,
                        ruleRepository, ruleService, levelService);
        ScrmEngagementScoreStatsService statsService =
                new ScrmEngagementScoreStatsService(eventRepository, scoreRepository,
                        ruleRepository, eventScoreService);
        service = new ScrmEngagementScoreService(ruleService, eventScoreService, levelService, statsService);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造已持久化的互动评分规则实体 (用于 findById 返回)
     */
    private ScrmEngagementRuleEntity buildRuleEntity(Long id, String behaviorType, String channel) {
        ScrmEngagementRuleEntity entity = new ScrmEngagementRuleEntity();
        entity.setId(id);
        entity.setRuleName("浏览打分规则");
        entity.setBehaviorType(behaviorType);
        entity.setChannel(channel);
        entity.setPoints(2);
        entity.setDailyLimit(0);
        entity.setWeeklyLimit(0);
        entity.setMonthlyLimit(0);
        entity.setDecayDays(30);
        entity.setDecayType("LINEAR");
        entity.setWeight(1.0);
        entity.setEnabled(true);
        entity.setMatchCount(0);
        return entity;
    }

    @Test
    @DisplayName("createRule: 写入归属账号与默认值后持久化")
    void createRule_success() throws ScrmException {
        ScrmEngagementRuleDto dto = new ScrmEngagementRuleDto();
        dto.setRuleName("浏览打分规则");
        dto.setBehaviorType("PAGE_VIEW");
        dto.setPoints(2);
        when(ruleRepository.save(any(ScrmEngagementRuleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmEngagementRuleDto result = service.createRule(dto);

        ArgumentCaptor<ScrmEngagementRuleEntity> captor =
                ArgumentCaptor.forClass(ScrmEngagementRuleEntity.class);
        verify(ruleRepository, times(1)).save(captor.capture());
        ScrmEngagementRuleEntity saved = captor.getValue();
        assertThat(saved.getEnabled()).isTrue();
        assertThat(saved.getDecayType()).isEqualTo("LINEAR");
        assertThat(saved.getDecayDays()).isEqualTo(30);
        assertThat(saved.getWeight()).isEqualTo(1.0);
        assertThat(saved.getMatchCount()).isZero();
        assertThat(saved.getDailyLimit()).isZero();
        assertThat(saved.getWeeklyLimit()).isZero();
        assertThat(saved.getMonthlyLimit()).isZero();
        assertThat(result.getRuleName()).isEqualTo("浏览打分规则");
    }

    @Test
    @DisplayName("createRule: 衰减类型非法抛 BAD_REQUEST")
    void createRule_invalidDecayType() {
        ScrmEngagementRuleDto dto = new ScrmEngagementRuleDto();
        dto.setRuleName("浏览打分规则");
        dto.setBehaviorType("PAGE_VIEW");
        dto.setPoints(2);
        dto.setDecayType("INVALID");

        assertThatThrownBy(() -> service.createRule(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("衰减类型非法");
        verify(ruleRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateRule: 字段非空才覆盖, 保留未提供字段原值")
    void updateRule_partialUpdate() throws ScrmException {
        ScrmEngagementRuleEntity entity = buildRuleEntity(10L, "PAGE_VIEW", "WECHAT");
        when(ruleRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(ruleRepository.save(any(ScrmEngagementRuleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmEngagementRuleDto dto = new ScrmEngagementRuleDto();
        dto.setRuleName("更新后的规则名");
        dto.setPoints(5);
        ScrmEngagementRuleDto result = service.updateRule(10L, dto);

        assertThat(result.getRuleName()).isEqualTo("更新后的规则名");
        assertThat(result.getPoints()).isEqualTo(5);
        // 未提供的字段保留原值
        assertThat(result.getBehaviorType()).isEqualTo("PAGE_VIEW");
        assertThat(result.getChannel()).isEqualTo("WECHAT");
        assertThat(result.getDecayType()).isEqualTo("LINEAR");
    }

    @Test
    @DisplayName("enableRule: 设置 enabled=true 并持久化")
    void enableRule_success() throws ScrmException {
        ScrmEngagementRuleEntity entity = buildRuleEntity(10L, "PAGE_VIEW", "WECHAT");
        entity.setEnabled(false);
        when(ruleRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(ruleRepository.save(any(ScrmEngagementRuleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.enableRule(10L);

        ArgumentCaptor<ScrmEngagementRuleEntity> captor =
                ArgumentCaptor.forClass(ScrmEngagementRuleEntity.class);
        verify(ruleRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getEnabled()).isTrue();
    }

    @Test
    @DisplayName("matchRule: channel 非空时匹配 channel 为空或精确等于的规则")
    void matchRule_channelFilter() {
        // channel 为空 (任意渠道) 的规则 + channel=WECHAT 的规则 + channel=WEB 的规则
        ScrmEngagementRuleEntity anyChannel = buildRuleEntity(10L, "PAGE_VIEW", null);
        ScrmEngagementRuleEntity wechatRule = buildRuleEntity(11L, "PAGE_VIEW", "WECHAT");
        ScrmEngagementRuleEntity webRule = buildRuleEntity(12L, "PAGE_VIEW", "WEB");
        when(ruleRepository.findByBehaviorTypeAndEnabledTrue(eq("PAGE_VIEW")))
                .thenReturn(List.of(anyChannel, wechatRule, webRule));

        List<ScrmEngagementRuleDto> result = service.matchRule("PAGE_VIEW", "WECHAT");

        // channel=WEB 被过滤, 保留任意渠道 + WECHAT
        assertThat(result).hasSize(2);
        assertThat(result).extracting(ScrmEngagementRuleDto::getChannel)
                .containsExactlyInAnyOrder(null, "WECHAT");
    }

    @Test
    @DisplayName("applyDecay: LINEAR 衰减 15 天后得分减半")
    void applyDecay_linear() {
        // decayDays=30, daysSince=15, factor = 1 - 15/30 = 0.5
        LocalDateTime eventTime = LocalDateTime.now().minusDays(15);
        double decayed = service.applyDecay(10, eventTime, 30, "LINEAR");
        assertThat(decayed).isEqualTo(5.0);
    }

    @Test
    @DisplayName("applyDecay: NONE 不衰减返回原值")
    void applyDecay_none() {
        LocalDateTime eventTime = LocalDateTime.now().minusDays(60);
        double decayed = service.applyDecay(10, eventTime, 30, "NONE");
        assertThat(decayed).isEqualTo(10.0);
    }

    @Test
    @DisplayName("applyDecay: STEP 三段式衰减 (中段半分)")
    void applyDecay_step() {
        // decayDays=30, third=10; daysSince=15 落在 [10,20) 中段, factor=0.5
        LocalDateTime eventTime = LocalDateTime.now().minusDays(15);
        double decayed = service.applyDecay(10, eventTime, 30, "STEP");
        assertThat(decayed).isEqualTo(5.0);
    }

    @Test
    @DisplayName("determineLevel: 无自定义等级时按默认阈值映射 (score>=100 → VERY_HIGH)")
    void determineLevel_defaultThresholds() {
        when(levelRepository.findByEnabledTrue()).thenReturn(List.of());

        assertThat(service.determineLevel(100)).isEqualTo("VERY_HIGH");
        assertThat(service.determineLevel(50)).isEqualTo("HIGH");
        assertThat(service.determineLevel(20)).isEqualTo("MEDIUM");
        assertThat(service.determineLevel(1)).isEqualTo("LOW");
        assertThat(service.determineLevel(0)).isEqualTo("INACTIVE");
    }

    @Test
    @DisplayName("determineLevel: 自定义等级按 priority 倒序匹配")
    void determineLevel_customLevels() {
        ScrmEngagementRuleEntity ruleUnused = buildRuleEntity(1L, "PAGE_VIEW", null);
        // 自定义等级: HIGH [50,100), LOW [0,20)
        org.hiylo.scrm.entity.ScrmEngagementLevelEntity high =
                new org.hiylo.scrm.entity.ScrmEngagementLevelEntity();
        high.setLevelCode("HIGH");
        high.setMinScore(50.0);
        high.setMaxScore(100.0);
        high.setPriority(2);
        high.setEnabled(true);
        org.hiylo.scrm.entity.ScrmEngagementLevelEntity low =
                new org.hiylo.scrm.entity.ScrmEngagementLevelEntity();
        low.setLevelCode("LOW");
        low.setMinScore(0.0);
        low.setMaxScore(20.0);
        low.setPriority(1);
        low.setEnabled(true);
        when(levelRepository.findByEnabledTrue()).thenReturn(List.of(low, high));

        assertThat(service.determineLevel(60)).isEqualTo("HIGH");
        assertThat(service.determineLevel(10)).isEqualTo("LOW");
    }

    
    @Test
    @DisplayName("applyDecay: score<=0 或 eventTime 为空返回 0")
    void applyDecay_zeroOrNullOrig() {
        assertThat(service.applyDecay(0, LocalDateTime.now(), 30, "LINEAR")).isZero();
        assertThat(service.applyDecay(10, null, 30, "LINEAR")).isZero();
    }

    @Test
    @DisplayName("createRule: 得分非正抛 BAD_REQUEST")
    void createRule_nonPositivePoints() {
        ScrmEngagementRuleDto dto = new ScrmEngagementRuleDto();
        dto.setRuleName("浏览打分规则");
        dto.setBehaviorType("PAGE_VIEW");
        dto.setPoints(0);

        assertThatThrownBy(() -> service.createRule(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("得分必须为正数");
        verify(ruleRepository, never()).save(any());
    }

    @Test
    @DisplayName("disableRule: 设置 enabled=false 并持久化")
    void disableRule_success() throws ScrmException {
        ScrmEngagementRuleEntity entity = buildRuleEntity(10L, "PAGE_VIEW", "WECHAT");
        when(ruleRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(ruleRepository.save(any(ScrmEngagementRuleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.disableRule(10L);

        ArgumentCaptor<ScrmEngagementRuleEntity> captor =
                ArgumentCaptor.forClass(ScrmEngagementRuleEntity.class);
        verify(ruleRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getEnabled()).isFalse();
    }
}
