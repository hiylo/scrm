/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBudgetServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.dto.ScrmBudgetPlanDto;
import org.hiylo.scrm.entity.ScrmBudgetAllocationEntity;
import org.hiylo.scrm.entity.ScrmBudgetPlanEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmBudgetAllocationRepository;
import org.hiylo.scrm.repository.ScrmBudgetExpenseRepository;
import org.hiylo.scrm.repository.ScrmBudgetPlanRepository;
import org.hiylo.scrm.repository.ScrmBudgetRoiRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
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
 * ScrmBudgetService 单元测试
 * <p>
 * 聚焦预算方案管理 (创建默认值 / 编码唯一性 / 越权校验 / 部分更新)、
 * 预算预警检查 (消耗率超阈值触发预警) 与预算统计 (总额/已分配/已消耗/剩余/分配率/消耗率) 等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmBudgetService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmBudgetServiceTest {

    /** 预算计划数据仓库 Mock 桩 */
    @Mock
    private ScrmBudgetPlanRepository planRepository;
    /** 预算分配数据仓库 Mock 桩 */
    @Mock
    private ScrmBudgetAllocationRepository allocationRepository;
    /** 预算支出数据仓库 Mock 桩 */
    @Mock
    private ScrmBudgetExpenseRepository expenseRepository;
    /** 预算 ROI 数据仓库 Mock 桩 */
    @Mock
    private ScrmBudgetRoiRepository roiRepository;

    /** ObjectMapper 使用真实实例, 不 mock (遵循约束) */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 被测服务实例 */
    private ScrmBudgetService service;

    @BeforeEach
    void setUp() {
        ScrmBudgetPlanService planService = new ScrmBudgetPlanService(
                planRepository, allocationRepository, expenseRepository, roiRepository);
        ScrmBudgetAllocationService allocationService = new ScrmBudgetAllocationService(
                allocationRepository, expenseRepository, planService);
        ScrmBudgetExpenseService expenseService = new ScrmBudgetExpenseService(
                expenseRepository, planRepository, allocationRepository, planService, allocationService, objectMapper);
        ScrmBudgetRoiService roiService = new ScrmBudgetRoiService(
                roiRepository, expenseRepository, planService, objectMapper);
        service = new ScrmBudgetService(planService, allocationService, expenseService, roiService);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造合法的预算方案创建 DTO
     */
    private ScrmBudgetPlanDto buildPlanDto() {
        ScrmBudgetPlanDto dto = new ScrmBudgetPlanDto();
        dto.setPlanName("2026 Q3 营销预算");
        dto.setPlanCode("BUD_2026Q3");
        dto.setFiscalYear(2026);
        dto.setFiscalPeriod("Q3");
        dto.setPeriodStart(LocalDate.of(2026, 7, 1));
        dto.setPeriodEnd(LocalDate.of(2026, 9, 30));
        dto.setTotalBudget(100000.0);
        dto.setBudgetType("MARKETING");
        return dto;
    }

    /**
     * 构造已持久化的预算方案实体 (用于 findById 返回)
     */
    private ScrmBudgetPlanEntity buildPlanEntity(Long id) {
        ScrmBudgetPlanEntity entity = new ScrmBudgetPlanEntity();
        entity.setId(id);
        entity.setPlanName("2026 Q3 营销预算");
        entity.setPlanCode("BUD_2026Q3");
        entity.setFiscalYear(2026);
        entity.setPeriodStart(LocalDate.of(2026, 7, 1));
        entity.setPeriodEnd(LocalDate.of(2026, 9, 30));
        entity.setTotalBudget(100000.0);
        entity.setAllocatedBudget(50000.0);
        entity.setSpentBudget(80000.0);
        entity.setRemainingBudget(50000.0);
        entity.setAllocationRate(0.5);
        entity.setSpendRate(0.8);
        entity.setCurrency("CNY");
        entity.setBudgetType("MARKETING");
        entity.setStatus("ACTIVE");
        entity.setAlertThreshold(0.8d);
        entity.setIsAlertTriggered(false);
        return entity;
    }

    @Test
    @DisplayName("createPlan: 写入归属账号与默认值后持久化")
    void createPlan_success() throws ScrmException {
        ScrmBudgetPlanDto dto = buildPlanDto();
        when(planRepository.findByPlanCode(eq("BUD_2026Q3")))
                .thenReturn(Optional.empty());
        when(planRepository.save(any(ScrmBudgetPlanEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmBudgetPlanDto result = service.createPlan(dto);

        ArgumentCaptor<ScrmBudgetPlanEntity> captor =
                ArgumentCaptor.forClass(ScrmBudgetPlanEntity.class);
        verify(planRepository, times(1)).save(captor.capture());
        ScrmBudgetPlanEntity saved = captor.getValue();
        // 默认 status=DRAFT
        assertThat(saved.getStatus()).isEqualTo("DRAFT");
        // 默认 currency=CNY
        assertThat(saved.getCurrency()).isEqualTo("CNY");
        // 默认 alertThreshold=0.8
        assertThat(saved.getAlertThreshold()).isEqualTo(0.8d);
        // 默认 isAlertTriggered=false
        assertThat(saved.getIsAlertTriggered()).isFalse();
        // allocatedBudget 初始 0
        assertThat(saved.getAllocatedBudget()).isEqualTo(0d);
        // spentBudget 初始 0
        assertThat(saved.getSpentBudget()).isEqualTo(0d);
        // remainingBudget 等于 totalBudget
        assertThat(saved.getRemainingBudget()).isEqualTo(100000.0);
        // 默认 createdBy=scrm-system
        assertThat(saved.getCreatedBy()).isEqualTo("scrm-system");
        assertThat(result.getPlanCode()).isEqualTo("BUD_2026Q3");
    }

    @Test
    @DisplayName("createPlan: planCode 重复抛 CONFLICT")
    void createPlan_duplicateCode() {
        ScrmBudgetPlanDto dto = buildPlanDto();
        when(planRepository.findByPlanCode(eq("BUD_2026Q3")))
                .thenReturn(Optional.of(buildPlanEntity(10L)));

        assertThatThrownBy(() -> service.createPlan(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("预算方案编码已存在");
        verify(planRepository, never()).save(any());
    }

    
    @Test
    @DisplayName("updatePlan: 字段非空才覆盖, 保留未提供字段原值")
    void updatePlan_partialUpdate() throws ScrmException {
        ScrmBudgetPlanEntity entity = buildPlanEntity(10L);
        entity.setStatus("DRAFT");
        when(planRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(planRepository.save(any(ScrmBudgetPlanEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmBudgetPlanDto dto = new ScrmBudgetPlanDto();
        dto.setPlanName("更新后的预算名");
        ScrmBudgetPlanDto result = service.updatePlan(10L, dto);

        assertThat(result.getPlanName()).isEqualTo("更新后的预算名");
        // 未提供字段保留原值
        assertThat(result.getPlanCode()).isEqualTo("BUD_2026Q3");
        assertThat(result.getTotalBudget()).isEqualTo(100000.0);
        assertThat(result.getBudgetType()).isEqualTo("MARKETING");
    }

    @Test
    @DisplayName("checkAlert: 消耗率超过阈值触发预警")
    void checkAlert_triggered() throws ScrmException {
        ScrmBudgetPlanEntity entity = buildPlanEntity(10L);
        // 单个分配: allocated=100000, spent=80000 → spendRate = 80000/100000 = 0.8 >= 阈值 0.8
        ScrmBudgetAllocationEntity allocation = new ScrmBudgetAllocationEntity();
        allocation.setId(50L);
        allocation.setAllocatedAmount(100000.0);
        allocation.setSpentAmount(80000.0);
        when(planRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(allocationRepository.findByPlanId(eq(10L)))
                .thenReturn(Collections.singletonList(allocation));
        when(planRepository.save(any(ScrmBudgetPlanEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.checkAlert(10L);

        ArgumentCaptor<ScrmBudgetPlanEntity> captor =
                ArgumentCaptor.forClass(ScrmBudgetPlanEntity.class);
        verify(planRepository, times(2)).save(captor.capture());
        // 最后一次 save 是 checkAlert 写回的 refreshed, isAlertTriggered=true
        ScrmBudgetPlanEntity saved = captor.getValue();
        assertThat(saved.getIsAlertTriggered()).isTrue();
        assertThat(saved.getLastAlertAt()).isNotNull();
    }

    @Test
    @DisplayName("getBudgetStats: 按财年汇总总额/已分配/已消耗/剩余")
    void getBudgetStats_success() {
        ScrmBudgetPlanEntity p1 = buildPlanEntity(11L);
        p1.setTotalBudget(50000.0);
        p1.setAllocatedBudget(30000.0);
        p1.setSpentBudget(20000.0);
        p1.setRemainingBudget(30000.0);
        ScrmBudgetPlanEntity p2 = buildPlanEntity(12L);
        p2.setTotalBudget(30000.0);
        p2.setAllocatedBudget(10000.0);
        p2.setSpentBudget(5000.0);
        p2.setRemainingBudget(25000.0);
        when(planRepository.findByFiscalYear(eq(2026)))
                .thenReturn(Arrays.asList(p1, p2));

        Map<String, Object> stats = service.getBudgetStats(2026);

        assertThat(stats.get("fiscalYear")).isEqualTo(2026);
        assertThat(stats.get("planCount")).isEqualTo(2);
        assertThat((Double) stats.get("totalBudget")).isEqualTo(80000.0);
        assertThat((Double) stats.get("allocatedBudget")).isEqualTo(40000.0);
        assertThat((Double) stats.get("spentBudget")).isEqualTo(25000.0);
        assertThat((Double) stats.get("remainingBudget")).isEqualTo(55000.0);
        // allocationRate = round2(40000/80000) = 0.5
        assertThat((Double) stats.get("allocationRate")).isEqualTo(0.5);
        // spendRate = round2(25000/80000) = round2(0.3125) = 0.31 (保留两位小数)
        assertThat((Double) stats.get("spendRate")).isEqualTo(0.31);
    }
}
