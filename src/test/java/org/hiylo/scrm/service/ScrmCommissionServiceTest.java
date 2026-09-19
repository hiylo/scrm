/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCommissionServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.dto.ScrmCommissionCalculateDto;
import org.hiylo.scrm.dto.ScrmCommissionPlanDto;
import org.hiylo.scrm.dto.ScrmCommissionRuleDto;
import org.hiylo.scrm.entity.ScrmCommissionPlanEntity;
import org.hiylo.scrm.entity.ScrmCommissionRuleEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCommissionPlanRepository;
import org.hiylo.scrm.repository.ScrmCommissionRecordRepository;
import org.hiylo.scrm.repository.ScrmCommissionRuleRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmCommissionService 单元测试
 * <p>
 * 聚焦佣金方案管理 (创建 / 默认值 / 唯一性校验)、佣金计算
 * (FLAT_RATE / TIERED_RATE)、方案状态校验与越权访问等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmCommissionService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmCommissionServiceTest {

    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 佣金方案数据仓库 Mock 桩 */
    @Mock
    private ScrmCommissionPlanRepository planRepository;
    /** 佣金规则数据仓库 Mock 桩 */
    @Mock
    private ScrmCommissionRuleRepository ruleRepository;
    /** 佣金记录数据仓库 Mock 桩 */
    @Mock
    private ScrmCommissionRecordRepository recordRepository;

    /** 被测服务实例 */
    private ScrmCommissionService service;

    @BeforeEach
    void setUp() {
        ScrmCommissionPlanService planService =
                new ScrmCommissionPlanService(planRepository, ruleRepository, recordRepository);
        ScrmCommissionRuleService ruleService =
                new ScrmCommissionRuleService(ruleRepository, recordRepository, objectMapper, planService);
        ScrmCommissionRecordService recordService =
                new ScrmCommissionRecordService(recordRepository, planService);
        ScrmCommissionCalculateService calculateService =
                new ScrmCommissionCalculateService(planRepository, ruleRepository, recordRepository,
                        objectMapper, planService, ruleService, recordService);
        service = new ScrmCommissionService(planService, ruleService, calculateService, recordService);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造已持久化的佣金方案实体 (用于 findById 返回)
     */
    private ScrmCommissionPlanEntity buildPlanEntity(Long id, String status) {
        ScrmCommissionPlanEntity entity = new ScrmCommissionPlanEntity();
        entity.setId(id);
        entity.setPlanName("标准销售佣金方案");
        entity.setPlanCode("COMM-STD-001");
        entity.setPlanType("REVENUE_BASED");
        entity.setCalculationBasis("ORDER_AMOUNT");
        entity.setStartDate(LocalDate.now().minusDays(30));
        entity.setStatus(status);
        entity.setTargetAmount(0d);
        entity.setCapAmount(0d);
        entity.setMinAmount(0d);
        entity.setClawbackDays(0);
        entity.setPayoutFrequency("MONTHLY");
        entity.setPayoutDay(15);
        entity.setIsDefault(false);
        entity.setTotalCommissionPaid(0d);
        entity.setTotalSalesAmount(0d);
        entity.setTotalOrders(0);
        return entity;
    }

    /**
     * 构造已持久化的佣金规则实体 (FLAT_RATE 固定比例)
     */
    private ScrmCommissionRuleEntity buildFlatRateRule(Long id, Long planId, double rate) {
        ScrmCommissionRuleEntity entity = new ScrmCommissionRuleEntity();
        entity.setId(id);
        entity.setPlanId(planId);
        entity.setRuleName("固定比例规则");
        entity.setRuleType("FLAT_RATE");
        entity.setCommissionRate(rate);
        entity.setCommissionAmount(0d);
        entity.setBonusAmount(0d);
        entity.setMultiplier(1.0d);
        entity.setDeductionAmount(0d);
        entity.setMinOrderAmount(0d);
        entity.setMaxCommissionPerOrder(0d);
        entity.setPriority(0);
        entity.setEnabled(true);
        entity.setMatchCount(0);
        entity.setTotalCommissionCalculated(0d);
        return entity;
    }

    @Test
    @DisplayName("createPlan: 写入归属账号与默认值后持久化")
    void createPlan_success() throws ScrmException {
        ScrmCommissionPlanDto dto = new ScrmCommissionPlanDto();
        dto.setPlanName("标准销售佣金方案");
        dto.setPlanCode("COMM-STD-001");
        dto.setPlanType("REVENUE_BASED");
        dto.setCalculationBasis("ORDER_AMOUNT");
        dto.setStartDate(LocalDate.now());
        when(planRepository.findByPlanCode("COMM-STD-001"))
                .thenReturn(Optional.empty());
        when(planRepository.save(any(ScrmCommissionPlanEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmCommissionPlanDto result = service.createPlan(dto);

        ArgumentCaptor<ScrmCommissionPlanEntity> captor =
                ArgumentCaptor.forClass(ScrmCommissionPlanEntity.class);
        verify(planRepository, times(1)).save(captor.capture());
        ScrmCommissionPlanEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getIsDefault()).isFalse();
        assertThat(saved.getTargetAmount()).isZero();
        assertThat(saved.getCapAmount()).isZero();
        assertThat(saved.getMinAmount()).isZero();
        assertThat(saved.getClawbackDays()).isZero();
        assertThat(saved.getPayoutFrequency()).isEqualTo("MONTHLY");
        assertThat(saved.getPayoutDay()).isEqualTo(15);
        assertThat(saved.getTotalCommissionPaid()).isZero();
        assertThat(saved.getTotalSalesAmount()).isZero();
        assertThat(saved.getTotalOrders()).isZero();
        assertThat(saved.getCreatedBy()).isEqualTo("scrm-system");
        assertThat(result.getPlanName()).isEqualTo("标准销售佣金方案");
    }

    @Test
    @DisplayName("createPlan: planCode 重复时抛 CONFLICT")
    void createPlan_duplicateCode() {
        ScrmCommissionPlanDto dto = new ScrmCommissionPlanDto();
        dto.setPlanName("标准销售佣金方案");
        dto.setPlanCode("COMM-STD-001");
        dto.setPlanType("REVENUE_BASED");
        dto.setCalculationBasis("ORDER_AMOUNT");
        dto.setStartDate(LocalDate.now());
        when(planRepository.findByPlanCode("COMM-STD-001"))
                .thenReturn(Optional.of(buildPlanEntity(10L, "ACTIVE")));

        assertThatThrownBy(() -> service.createPlan(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("佣金方案编码已存在");
        verify(planRepository, never()).save(any());
    }

    @Test
    @DisplayName("createPlan: 标记为默认方案时清除旧默认")
    void createPlan_defaultClearsOld() throws ScrmException {
        ScrmCommissionPlanDto dto = new ScrmCommissionPlanDto();
        dto.setPlanName("默认方案");
        dto.setPlanCode("COMM-DEFAULT");
        dto.setPlanType("REVENUE_BASED");
        dto.setCalculationBasis("ORDER_AMOUNT");
        dto.setStartDate(LocalDate.now());
        dto.setIsDefault(true);
        when(planRepository.findByPlanCode("COMM-DEFAULT"))
                .thenReturn(Optional.empty());
        when(planRepository.save(any(ScrmCommissionPlanEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.createPlan(dto);

        verify(planRepository, times(1)).clearDefaultFlag();
    }

    @Test
    @DisplayName("calculateCommission: FLAT_RATE 规则按订单金额比例计算佣金")
    void calculateCommission_flatRate() throws ScrmException {
        ScrmCommissionPlanEntity plan = buildPlanEntity(10L, "ACTIVE");
        when(planRepository.findById(10L)).thenReturn(Optional.of(plan));
        ScrmCommissionRuleEntity rule = buildFlatRateRule(20L, 10L, 0.10d);
        when(ruleRepository.findByPlanId(10L))
                .thenReturn(List.of(rule));
        when(ruleRepository.save(any(ScrmCommissionRuleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(recordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(planRepository.save(any(ScrmCommissionPlanEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmCommissionCalculateDto calcDto = new ScrmCommissionCalculateDto();
        calcDto.setPlanId(10L);
        calcDto.setSalesPersonId("S001");
        calcDto.setSalesPersonName("张三");
        calcDto.setOrderId("ORD-001");
        calcDto.setOrderAmount(1000d);

        var result = service.calculateCommission(calcDto);

        // 佣金 = 订单金额 * 比例 = 1000 * 0.10 = 100
        assertThat(result.getFinalCommission()).isEqualTo(100d);
        assertThat(result.getCommissionBasis()).isEqualTo(1000d);
        assertThat(result.getCommissionRate()).isEqualTo(0.10d);
        assertThat(result.getStatus()).isEqualTo("CALCULATED");
        assertThat(result.getRecordNo()).startsWith("COMM");
        // 规则匹配次数累加
        ArgumentCaptor<ScrmCommissionRuleEntity> ruleCaptor =
                ArgumentCaptor.forClass(ScrmCommissionRuleEntity.class);
        verify(ruleRepository, times(1)).save(ruleCaptor.capture());
        assertThat(ruleCaptor.getValue().getMatchCount()).isEqualTo(1);
        // 方案订单数与销售总额累加
        ArgumentCaptor<ScrmCommissionPlanEntity> planCaptor =
                ArgumentCaptor.forClass(ScrmCommissionPlanEntity.class);
        verify(planRepository, times(1)).save(planCaptor.capture());
        assertThat(planCaptor.getValue().getTotalOrders()).isEqualTo(1);
        assertThat(planCaptor.getValue().getTotalSalesAmount()).isEqualTo(1000d);
    }

    @Test
    @DisplayName("calculateCommission: 方案非激活状态抛 BAD_REQUEST")
    void calculateCommission_planNotActive() {
        ScrmCommissionPlanEntity plan = buildPlanEntity(10L, "PAUSED");
        when(planRepository.findById(10L)).thenReturn(Optional.of(plan));

        ScrmCommissionCalculateDto calcDto = new ScrmCommissionCalculateDto();
        calcDto.setPlanId(10L);
        calcDto.setSalesPersonId("S001");
        calcDto.setOrderAmount(1000d);

        assertThatThrownBy(() -> service.calculateCommission(calcDto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("佣金方案非激活状态");
        verify(recordRepository, never()).save(any());
    }

    @Test
    @DisplayName("calculateCommission: 订单金额非正数抛 BAD_REQUEST")
    void calculateCommission_invalidOrderAmount() {
        ScrmCommissionPlanEntity plan = buildPlanEntity(10L, "ACTIVE");
        when(planRepository.findById(10L)).thenReturn(Optional.of(plan));

        ScrmCommissionCalculateDto calcDto = new ScrmCommissionCalculateDto();
        calcDto.setPlanId(10L);
        calcDto.setSalesPersonId("S001");
        calcDto.setOrderAmount(0d);

        assertThatThrownBy(() -> service.calculateCommission(calcDto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("订单金额必须大于 0");
        verify(recordRepository, never()).save(any());
    }

    @Test
    @DisplayName("applyFlatRate: 计算后应用每单最大佣金上限")
    void applyFlatRate_cap() {
        // basis=1000, rate=0.10, commission=100, cap=80 -> 取 min(100, 80) = 80
        double commission = service.applyFlatRate(1000d, 0.10d, 80d);
        assertThat(commission).isEqualTo(80d);
    }

    @Test
    @DisplayName("applyFlatRate: 无上限时按比例计算")
    void applyFlatRate_noCap() {
        double commission = service.applyFlatRate(1000d, 0.10d, 0d);
        assertThat(commission).isEqualTo(100d);
    }

    @Test
    @DisplayName("applyTieredRate: 命中阶梯区间后按区间比例计算")
    void applyTieredRate_hit() {
        // 阶梯: [0, 500) rate=0.05; [500, 5000] rate=0.10
        String tierConfig = "[{\"minValue\":0,\"maxValue\":500,\"rate\":0.05,\"bonusAmount\":0},"
                + "{\"minValue\":500,\"maxValue\":5000,\"rate\":0.10,\"bonusAmount\":0}]";
        double[] result = service.applyTieredRate(1000d, tierConfig);
        // 命中第二阶梯: 1000 * 0.10 = 100
        assertThat(result[0]).isEqualTo(100d);
        assertThat(result[1]).isEqualTo(0.10d);
    }

    @Test
    @DisplayName("applyTieredRate: 配置为空时返回 0")
    void applyTieredRate_emptyConfig() {
        double[] result = service.applyTieredRate(1000d, "");
        assertThat(result[0]).isZero();
        assertThat(result[1]).isZero();
    }

    
}
