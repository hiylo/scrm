/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAutoTagServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.dto.ScrmAutoTagRuleDto;
import org.hiylo.scrm.entity.ScrmAutoTagRuleEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmAutoTagRuleLogRepository;
import org.hiylo.scrm.repository.ScrmAutoTagRuleRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmCustomerTagRepository;
import org.hiylo.scrm.repository.ScrmTagCustomerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
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
 * ScrmAutoTagService 单元测试
 * <p>
 * 聚焦自动标签规则管理 (创建 / 默认值填充 / 参数校验)、规则启用/禁用、
 * 规则评估 (触发事件匹配) 与越权访问校验等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmAutoTagService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmAutoTagServiceTest {

    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 自动打标规则数据仓库 Mock 桩 */
    @Mock
    private ScrmAutoTagRuleRepository ruleRepository;
    /** 自动打标日志数据仓库 Mock 桩 */
    @Mock
    private ScrmAutoTagRuleLogRepository logRepository;
    /** 客户数据仓库 Mock 桩 */
    @Mock
    private ScrmCustomerRepository customerRepository;
    /** 客户标签数据仓库 Mock 桩 */
    @Mock
    private ScrmCustomerTagRepository tagRepository;
    /** 客户标签关联数据仓库 Mock 桩 */
    @Mock
    private ScrmTagCustomerRepository tagCustomerRepository;
    /** 通知服务 Mock 桩 */
    @Mock
    private ScrmNotificationService notificationService;

    /** 被测服务实例 */
    private ScrmAutoTagService service;

    @BeforeEach
    void setUp() {
        service = new ScrmAutoTagService(ruleRepository, logRepository, customerRepository,
                tagRepository, tagCustomerRepository, notificationService, objectMapper);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造已持久化的自动标签规则实体 (用于 findById 返回)
     */
    private ScrmAutoTagRuleEntity buildRuleEntity(Long id, boolean enabled) {
        ScrmAutoTagRuleEntity entity = new ScrmAutoTagRuleEntity();
        entity.setId(id);
        entity.setRuleName("高价值客户打标");
        entity.setTriggerEvent("CUSTOMER_CREATED");
        entity.setConditionType("ALL");
        entity.setConditions("[{\"field\":\"lifecycle\",\"operator\":\"eq\",\"value\":\"ACTIVE\"}]");
        entity.setActionType("ADD_TAG");
        entity.setActionParams("{\"tagIds\":[\"vip\"]}");
        entity.setPriority(0);
        entity.setEnabled(enabled);
        entity.setMatchCount(0);
        return entity;
    }

    @Test
    @DisplayName("createRule: 写入归属账号与默认值后持久化")
    void createRule_success() throws ScrmException {
        when(ruleRepository.save(any(ScrmAutoTagRuleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmAutoTagRuleDto dto = new ScrmAutoTagRuleDto();
        dto.setRuleName("高价值客户打标");
        dto.setTriggerEvent("CUSTOMER_CREATED");
        dto.setConditionType("ALL");
        dto.setConditions("[{\"field\":\"lifecycle\",\"operator\":\"eq\",\"value\":\"ACTIVE\"}]");
        dto.setActionType("ADD_TAG");
        dto.setActionParams("{\"tagIds\":[\"vip\"]}");
        dto.setCreatedBy("admin01");

        ScrmAutoTagRuleEntity result = service.createRule(dto);

        ArgumentCaptor<ScrmAutoTagRuleEntity> captor =
                ArgumentCaptor.forClass(ScrmAutoTagRuleEntity.class);
        verify(ruleRepository, times(1)).save(captor.capture());
        ScrmAutoTagRuleEntity saved = captor.getValue();
        assertThat(saved.getPriority()).isZero();
        assertThat(saved.getEnabled()).isTrue();
        assertThat(saved.getMatchCount()).isZero();
        assertThat(saved.getCreatedBy()).isEqualTo("admin01");
        assertThat(result.getRuleName()).isEqualTo("高价值客户打标");
    }

    @Test
    @DisplayName("createRule: 触发事件非法时抛 BAD_REQUEST")
    void createRule_invalidTriggerEvent() {
        ScrmAutoTagRuleDto dto = new ScrmAutoTagRuleDto();
        dto.setRuleName("非法规则");
        dto.setTriggerEvent("INVALID_EVENT");
        dto.setConditionType("ALL");
        dto.setConditions("[{\"field\":\"lifecycle\",\"operator\":\"eq\",\"value\":\"ACTIVE\"}]");
        dto.setActionType("ADD_TAG");
        dto.setActionParams("{\"tagIds\":[\"vip\"]}");

        assertThatThrownBy(() -> service.createRule(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("触发事件非法");
        verify(ruleRepository, never()).save(any());
    }

    @Test
    @DisplayName("enableRule: 启用规则后 enabled 置 true 并持久化")
    void enableRule_success() throws ScrmException {
        ScrmAutoTagRuleEntity rule = buildRuleEntity(10L, false);
        when(ruleRepository.findById(10L)).thenReturn(Optional.of(rule));
        when(ruleRepository.save(any(ScrmAutoTagRuleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.enableRule(10L);

        ArgumentCaptor<ScrmAutoTagRuleEntity> captor =
                ArgumentCaptor.forClass(ScrmAutoTagRuleEntity.class);
        verify(ruleRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getEnabled()).isTrue();
    }

    @Test
    @DisplayName("disableRule: 禁用规则后 enabled 置 false 并持久化")
    void disableRule_success() throws ScrmException {
        ScrmAutoTagRuleEntity rule = buildRuleEntity(10L, true);
        when(ruleRepository.findById(10L)).thenReturn(Optional.of(rule));
        when(ruleRepository.save(any(ScrmAutoTagRuleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.disableRule(10L);

        ArgumentCaptor<ScrmAutoTagRuleEntity> captor =
                ArgumentCaptor.forClass(ScrmAutoTagRuleEntity.class);
        verify(ruleRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getEnabled()).isFalse();
    }

    @Test
    @DisplayName("evaluateRule: 触发事件不一致时返回不匹配")
    void evaluateRule_triggerMismatch_returnsFalse() throws ScrmException {
        ScrmAutoTagRuleEntity rule = buildRuleEntity(10L, true);
        when(ruleRepository.findById(10L)).thenReturn(Optional.of(rule));

        Map<String, Object> context = new HashMap<>();
        context.put("lifecycle", "ACTIVE");

        boolean matched = service.evaluateRule(10L, 100L, "MESSAGE_RECEIVED", context);

        assertThat(matched).isFalse();
    }

    
}
