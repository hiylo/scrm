/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContractStatsServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.entity.ScrmContractChangeEntity;
import org.hiylo.scrm.entity.ScrmContractEntity;
import org.hiylo.scrm.entity.ScrmContractPaymentEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmContractChangeRepository;
import org.hiylo.scrm.repository.ScrmContractPaymentRepository;
import org.hiylo.scrm.repository.ScrmContractRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmContractStatsService 单元测试
 * <p>
 * 聚焦合同统计 (总数 / 类型 / 状态 / 金额聚合)、付款统计、合同风险计算 (金额 / 期限 / 逾期 / 变更 /
 * 即将到期因素综合评分映射等级)、Top 客户排行、合同克隆校验与越权访问等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmContractStatsService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmContractStatsServiceTest {

    /** 合同数据仓库 Mock 桩 */
    @Mock
    private ScrmContractRepository contractRepository;
    /** 合同付款数据仓库 Mock 桩 */
    @Mock
    private ScrmContractPaymentRepository paymentRepository;
    /** 合同变更数据仓库 Mock 桩 */
    @Mock
    private ScrmContractChangeRepository changeRepository;

    /** 被测服务实例 */
    private ScrmContractStatsService service;

    @BeforeEach
    void setUp() {
        service = new ScrmContractStatsService(contractRepository, paymentRepository, changeRepository);
    }

    @AfterEach
    void tearDown() {
    }

    // ==================== 统计 ====================

    @Test
    @DisplayName("getContractStats: 聚合总数 / 类型 / 状态 / 总金额 / 平均金额")
    void getContractStats_aggregates() {
        when(contractRepository.findAll(any(Specification.class))).thenReturn(List.of(
                buildContract(1L, "C001", "销售合同", "SALES", "ACTIVE", 100L, "客户A", 100000d),
                buildContract(2L, "C002", "服务合同", "SERVICE", "SIGNED", 101L, "客户B", 200000d),
                buildContract(3L, "C003", "销售合同", "SALES", "ACTIVE", 100L, "客户A", null)));

        Map<String, Object> stats = service.getContractStats(null, null);

        assertThat(stats.get("total")).isEqualTo(3);
        @SuppressWarnings("unchecked")
        Map<String, Long> byType = (Map<String, Long>) stats.get("byType");
        assertThat(byType.get("SALES")).isEqualTo(2L);
        assertThat(byType.get("SERVICE")).isEqualTo(1L);
        @SuppressWarnings("unchecked")
        Map<String, Long> byStatus = (Map<String, Long>) stats.get("byStatus");
        assertThat(byStatus.get("ACTIVE")).isEqualTo(2L);
        assertThat(byStatus.get("SIGNED")).isEqualTo(1L);
        // 总金额 = 100000 + 200000 + 0 = 300000, 平均 = 100000
        assertThat((Double) stats.get("totalAmount")).isEqualTo(300000d);
        assertThat((Double) stats.get("avgAmount")).isEqualTo(100000d);
    }

    @Test
    @DisplayName("getPaymentStats: 聚合总数 / 状态分布 / 计划/已付/未付金额 / 逾期数")
    void getPaymentStats_aggregates() {
        when(paymentRepository.findAll(any(Specification.class))).thenReturn(List.of(
                buildPayment(1L, "PAID", 10000d, 10000d, 0d),
                buildPayment(2L, "OVERDUE", 20000d, 5000d, 15000d),
                buildPayment(3L, "PENDING", 30000d, 0d, 30000d)));

        Map<String, Object> stats = service.getPaymentStats(null, null);

        assertThat(stats.get("total")).isEqualTo(3);
        assertThat(stats.get("overdueCount")).isEqualTo(1L);
        assertThat((Double) stats.get("totalPlannedAmount")).isEqualTo(60000d);
        assertThat((Double) stats.get("totalPaidAmount")).isEqualTo(15000d);
        assertThat((Double) stats.get("totalUnpaidAmount")).isEqualTo(45000d);
    }

    @Test
    @DisplayName("getTopCustomers: 按客户合同总金额降序排列并限制数量")
    void getTopCustomers_sortedDesc() {
        when(contractRepository.findAll()).thenReturn(List.of(
                buildContract(1L, "C001", "合同1", "SALES", "ACTIVE", 100L, "客户A", 500d),
                buildContract(2L, "C002", "合同2", "SALES", "ACTIVE", 101L, "客户B", 1000d),
                buildContract(3L, "C003", "合同3", "SALES", "ACTIVE", 100L, "客户A", 300d)));

        List<Map<String, Object>> top = service.getTopCustomers(10);

        assertThat(top).hasSize(2);
        // 客户B 总额 1000 排前, 客户A 总额 800 排后
        assertThat(top.get(0).get("customerId")).isEqualTo(101L);
        assertThat(top.get(0).get("totalAmount")).isEqualTo(1000d);
        assertThat(top.get(0).get("contractCount")).isEqualTo(1);
        assertThat(top.get(1).get("customerId")).isEqualTo(100L);
        assertThat(top.get(1).get("totalAmount")).isEqualTo(800d);
        assertThat(top.get(1).get("contractCount")).isEqualTo(2);
    }

    // ==================== 合同风险计算 ====================

    @Test
    @DisplayName("calculateContractRisk: 合同不存在抛 NOT_FOUND")
    void calculateContractRisk_notFound() {
        when(contractRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.calculateContractRisk(999L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("合同不存在");
    }

    
    @Test
    @DisplayName("calculateContractRisk: 大额 + 长期 + 逾期付款 + 变更频次综合评分映射 CRITICAL")
    void calculateContractRisk_criticalLevel() throws Exception {
        ScrmContractEntity contract = buildContract(1L, "C001", "大额合同", "SALES", "ACTIVE", 100L, "客户A", 2_000_000d);
        contract.setDurationMonths(30);
        contract.setEndDate(LocalDate.now().plusDays(180));
        when(contractRepository.findById(1L)).thenReturn(Optional.of(contract));
        // 2 笔逾期付款 → +20 (min(20, 30))
        when(paymentRepository.findByContractIdOrderByPlannedDateAsc(1L))
                .thenReturn(List.of(
                        buildPayment(10L, "OVERDUE", 1000d, 0d, 1000d),
                        buildPayment(11L, "OVERDUE", 2000d, 0d, 2000d)));
        // 3 次变更 → +15 (min(15, 20))
        when(changeRepository.findByContractIdOrderByCreateTimeAsc(1L))
                .thenReturn(List.of(
                        buildChange(20L, "AMEND", "PENDING", 500d),
                        buildChange(21L, "AMEND", "APPROVED", 300d),
                        buildChange(22L, "AMEND", "PENDING", 200d)));

        Map<String, Object> result = service.calculateContractRisk(1L);

        // 30 (金额) + 20 (期限) + 20 (逾期) + 15 (变更) = 85 → CRITICAL
        assertThat(result.get("riskScore")).isEqualTo(85);
        assertThat(result.get("riskLevel")).isEqualTo("CRITICAL");
        @SuppressWarnings("unchecked")
        List<String> factors = (List<String>) result.get("riskFactors");
        assertThat(factors).hasSize(4);
    }

    @Test
    @DisplayName("calculateContractRisk: 已终止合同风险为 0 / LOW")
    void calculateContractRisk_terminatedLow() throws Exception {
        ScrmContractEntity contract = buildContract(1L, "C001", "合同", "SALES", "TERMINATED", 100L, "客户A", 5_000_000d);
        when(contractRepository.findById(1L)).thenReturn(Optional.of(contract));

        Map<String, Object> result = service.calculateContractRisk(1L);

        assertThat(result.get("riskScore")).isEqualTo(0);
        assertThat(result.get("riskLevel")).isEqualTo("LOW");
    }

    // ==================== 克隆 ====================

    @Test
    @DisplayName("cloneContract: 新合同编号为空抛 BAD_REQUEST, 不查询源合同")
    void cloneContract_blankNo() {
        assertThatThrownBy(() -> service.cloneContract(1L, "  "))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("新合同编号不能为空");
        verify(contractRepository, never()).findById(any());
    }

    @Test
    @DisplayName("cloneContract: 新编号已存在抛 CONFLICT")
    void cloneContract_duplicateNo() {
        when(contractRepository.findById(1L)).thenReturn(
                Optional.of(buildContract(1L, "C001", "合同", "SALES", "ACTIVE", 100L, "A", 100d)));
        when(contractRepository.findByContractNo("C002")).thenReturn(
                Optional.of(buildContract(2L, "C002", "合同2", "SALES", "ACTIVE", 101L, "B", 200d)));

        assertThatThrownBy(() -> service.cloneContract(1L, "C002"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("合同编号已存在");
    }

    // ==================== 辅助方法 ====================

    private ScrmContractEntity buildContract(Long id, String no, String name, String type, String status, Long customerId, String customerName, Double amount) {
        ScrmContractEntity entity = new ScrmContractEntity();
        entity.setId(id);
        entity.setContractNo(no);
        entity.setContractName(name);
        entity.setContractType(type);
        entity.setStatus(status);
        entity.setCustomerId(customerId);
        entity.setCustomerName(customerName);
        entity.setContractAmount(amount);
        entity.setCreateTime(LocalDateTime.of(2026, 8, 1, 10, 0));
        return entity;
    }

    private ScrmContractPaymentEntity buildPayment(Long id, String status, Double planned, Double paid, Double unpaid) {
        ScrmContractPaymentEntity entity = new ScrmContractPaymentEntity();
        entity.setId(id);
        entity.setPaymentStatus(status);
        entity.setPlannedAmount(planned);
        entity.setPaidAmount(paid);
        entity.setUnpaidAmount(unpaid);
        entity.setCreateTime(LocalDateTime.of(2026, 8, 1, 10, 0));
        return entity;
    }

private ScrmContractChangeEntity buildChange(Long id, String type, String status, Double valueChange) {
        ScrmContractChangeEntity entity = new ScrmContractChangeEntity();
        entity.setId(id);
        entity.setChangeType(type);
        entity.setChangeStatus(status);
        entity.setValueChange(valueChange);
        entity.setCreateTime(LocalDateTime.of(2026, 8, 1, 10, 0));
        return entity;
    }
}
