/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmChurnWarningServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.dto.ScrmChurnRuleDto;
import org.hiylo.scrm.entity.ScrmChurnRuleEntity;
import org.hiylo.scrm.entity.ScrmChurnWarningEntity;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmChurnRecoveryRepository;
import org.hiylo.scrm.repository.ScrmChurnRuleRepository;
import org.hiylo.scrm.repository.ScrmChurnWarningRepository;
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
 * ScrmChurnWarningService 单元测试
 * <p>
 * 聚焦客户流失预警规则管理 (创建 / 默认值填充 / 参数校验)、流失风险扫描
 * (规则匹配 / 风险分计算 / 等级判定 / 预警创建 / 动作执行)、预警处理
 * (解决 / 重复操作校验) 与越权访问校验等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmChurnWarningService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmChurnWarningServiceTest {

    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 流失预警规则数据仓库 Mock 桩 */
    @Mock
    private ScrmChurnRuleRepository ruleRepository;
    /** 流失预警数据仓库 Mock 桩 */
    @Mock
    private ScrmChurnWarningRepository warningRepository;
    /** 流失挽回数据仓库 Mock 桩 */
    @Mock
    private ScrmChurnRecoveryRepository recoveryRepository;
    /** 客户数据仓库 Mock 桩 */
    @Mock
    private ScrmCustomerRepository customerRepository;

    /** 被测服务实例 */
    private ScrmChurnWarningService service;

    @BeforeEach
    void setUp() {
        service = new ScrmChurnWarningService(ruleRepository, warningRepository,
                recoveryRepository, customerRepository, objectMapper);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造已持久化的流失预警规则实体 (用于 findById / find 返回)
     * <p>
     * 条件: lastInteractionDays > 30, 风险等级 HIGH, 动作 NOTIFY_ASSIGNEE
     * </p>
     */
    private ScrmChurnRuleEntity buildRuleEntity(Long id) {
        ScrmChurnRuleEntity entity = new ScrmChurnRuleEntity();
        entity.setId(id);
        entity.setRuleName("高流失风险规则");
        entity.setRiskLevel("HIGH");
        entity.setConditionType("ALL");
        entity.setConditions("[{\"field\":\"lastInteractionDays\",\"operator\":\"gt\",\"value\":30}]");
        entity.setActionType("NOTIFY_ASSIGNEE");
        entity.setActionParams("{}");
        entity.setCooldownDays(7);
        entity.setPriority(0);
        entity.setEnabled(true);
        entity.setMatchCount(0);
        return entity;
    }

    /**
     * 构造已持久化的客户实体 (lastInteractionAt 设为 60 天前, 使 lastInteractionDays=60 命中 >30 规则)
     */
    private ScrmCustomerEntity buildCustomerEntity(Long id) {
        ScrmCustomerEntity entity = new ScrmCustomerEntity();
        entity.setId(id);
        entity.setNickname("张三");
        entity.setLifecycle("ACTIVE");
        entity.setLastInteractionAt(LocalDateTime.now().minusDays(60));
        entity.setCreateTime(LocalDateTime.now().minusDays(100));
        return entity;
    }

    /**
     * 构造已持久化的流失预警实体
     */
    private ScrmChurnWarningEntity buildWarningEntity(Long id, String status) {
        ScrmChurnWarningEntity entity = new ScrmChurnWarningEntity();
        entity.setId(id);
        entity.setCustomerId(100L);
        entity.setCustomerName("张三");
        entity.setRuleId(10L);
        entity.setRuleName("高流失风险规则");
        entity.setRiskLevel("HIGH");
        entity.setRiskScore(85.0);
        entity.setRiskFactors("[]");
        entity.setStatus(status);
        entity.setActionType("NOTIFY_ASSIGNEE");
        entity.setDetectedAt(LocalDateTime.now());
        return entity;
    }

    @Test
    @DisplayName("createRule: 写入归属账号与默认值后持久化")
    void createRule_success() throws ScrmException {
        when(ruleRepository.save(any(ScrmChurnRuleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmChurnRuleDto dto = new ScrmChurnRuleDto();
        dto.setRuleName("高流失风险规则");
        dto.setRiskLevel("HIGH");
        dto.setConditions("[{\"field\":\"lastInteractionDays\",\"operator\":\"gt\",\"value\":30}]");
        dto.setActionType("NOTIFY_ASSIGNEE");
        dto.setCreatedBy("admin01");

        ScrmChurnRuleEntity result = service.createRule(dto);

        ArgumentCaptor<ScrmChurnRuleEntity> captor =
                ArgumentCaptor.forClass(ScrmChurnRuleEntity.class);
        verify(ruleRepository, times(1)).save(captor.capture());
        ScrmChurnRuleEntity saved = captor.getValue();
        assertThat(saved.getConditionType()).isEqualTo("ALL");
        assertThat(saved.getActionParams()).isEqualTo("{}");
        assertThat(saved.getCooldownDays()).isEqualTo(7);
        assertThat(saved.getPriority()).isZero();
        assertThat(saved.getEnabled()).isTrue();
        assertThat(saved.getMatchCount()).isZero();
        assertThat(saved.getCreatedBy()).isEqualTo("admin01");
        assertThat(result.getRuleName()).isEqualTo("高流失风险规则");
    }

    @Test
    @DisplayName("createRule: 风险等级非法时抛 BAD_REQUEST")
    void createRule_invalidRiskLevel() {
        ScrmChurnRuleDto dto = new ScrmChurnRuleDto();
        dto.setRuleName("非法规则");
        dto.setRiskLevel("CRITICAL");
        dto.setConditions("[{\"field\":\"lastInteractionDays\",\"operator\":\"gt\",\"value\":30}]");
        dto.setActionType("NOTIFY_ASSIGNEE");

        assertThatThrownBy(() -> service.createRule(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("风险等级非法");
        verify(ruleRepository, never()).save(any());
    }

    @Test
    @DisplayName("scanCustomer: 命中规则后创建预警并执行动作")
    void scanCustomer_success() throws ScrmException {
        ScrmCustomerEntity customer = buildCustomerEntity(100L);
        ScrmChurnRuleEntity rule = buildRuleEntity(10L);
        when(customerRepository.findById(100L)).thenReturn(Optional.of(customer));
        when(ruleRepository.findByEnabledTrueOrderByPriorityAsc())
                .thenReturn(List.of(rule));
        when(warningRepository.findFirstByCustomerIdAndStatusAndDetectedAtAfterOrderByDetectedAtDesc(eq(100L), eq("ACTIVE"), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
        when(warningRepository.save(any(ScrmChurnWarningEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmChurnWarningEntity result = service.scanCustomer(100L);

        ArgumentCaptor<ScrmChurnWarningEntity> captor =
                ArgumentCaptor.forClass(ScrmChurnWarningEntity.class);
        // save 调用两次: scanCustomer 创建预警 + executeAction 执行动作
        verify(warningRepository, times(2)).save(captor.capture());
        List<ScrmChurnWarningEntity> saved = captor.getAllValues();
        // 第一次保存 (创建预警)
        ScrmChurnWarningEntity firstSave = saved.get(0);
        assertThat(firstSave.getCustomerId()).isEqualTo(100L);
        assertThat(firstSave.getCustomerName()).isEqualTo("张三");
        assertThat(firstSave.getRuleId()).isEqualTo(10L);
        assertThat(firstSave.getRuleName()).isEqualTo("高流失风险规则");
        // 风险分 = HIGH(80) + 1 条命中 * 5 = 85, 等级 HIGH(≥75)
        assertThat(firstSave.getRiskScore()).isEqualTo(85.0);
        assertThat(firstSave.getRiskLevel()).isEqualTo("HIGH");
        assertThat(firstSave.getStatus()).isEqualTo("ACTIVE");
        assertThat(firstSave.getActionType()).isEqualTo("NOTIFY_ASSIGNEE");
        // 第二次保存 (executeAction 执行动作后)
        ScrmChurnWarningEntity secondSave = saved.get(1);
        assertThat(secondSave.getActionResult()).contains("已通知负责人");
        assertThat(secondSave.getActionExecutedAt()).isNotNull();
        // 返回的预警已含动作执行结果
        assertThat(result.getRiskLevel()).isEqualTo("HIGH");
        assertThat(result.getActionResult()).contains("已通知负责人");
        // 递增规则匹配次数
        verify(ruleRepository, times(1)).incrementMatchCount(eq(10L), any(LocalDateTime.class));
    }

    
    @Test
    @DisplayName("resolveWarning: ACTIVE 预警标记为 RESOLVED")
    void resolveWarning_success() throws ScrmException {
        ScrmChurnWarningEntity warning = buildWarningEntity(50L, "ACTIVE");
        when(warningRepository.findById(50L)).thenReturn(Optional.of(warning));
        when(warningRepository.save(any(ScrmChurnWarningEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmChurnWarningEntity result = service.resolveWarning(50L, "客户已回访");

        ArgumentCaptor<ScrmChurnWarningEntity> captor =
                ArgumentCaptor.forClass(ScrmChurnWarningEntity.class);
        verify(warningRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("RESOLVED");
        assertThat(captor.getValue().getResolutionNote()).isEqualTo("客户已回访");
        assertThat(captor.getValue().getResolvedAt()).isNotNull();
        assertThat(captor.getValue().getResolvedBy()).isNotNull();
        assertThat(result.getStatus()).isEqualTo("RESOLVED");
    }

    @Test
    @DisplayName("resolveWarning: 已处理预警重复操作抛 CONFLICT")
    void resolveWarning_alreadyProcessed() {
        ScrmChurnWarningEntity warning = buildWarningEntity(50L, "RESOLVED");
        when(warningRepository.findById(50L)).thenReturn(Optional.of(warning));

        assertThatThrownBy(() -> service.resolveWarning(50L, "再次处理"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("预警已处理");
        verify(warningRepository, never()).save(any());
    }

    
}
