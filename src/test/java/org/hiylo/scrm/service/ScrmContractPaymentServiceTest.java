/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContractPaymentServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmContractPaymentDto;
import org.hiylo.scrm.entity.ScrmContractEntity;
import org.hiylo.scrm.entity.ScrmContractPaymentEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmContractPaymentRepository;
import org.hiylo.scrm.repository.ScrmContractRepository;
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
 * ScrmContractPaymentService 单元测试
 * <p>
 * 聚焦合同付款管理 (创建默认值 / 编号唯一性 / 越权校验)、付款记录 (状态流转 / 金额校验)、
 * 按合同查询与付款统计 (已付/未付/总额/状态分布) 等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmContractPaymentService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmContractPaymentServiceTest {

    /** 合同付款数据仓库 Mock 桩 */
    @Mock
    private ScrmContractPaymentRepository paymentRepository;
    /** 合同数据仓库 Mock 桩 */
    @Mock
    private ScrmContractRepository contractRepository;

    /** 被测服务实例 */
    private ScrmContractPaymentService service;

    @BeforeEach
    void setUp() {
        service = new ScrmContractPaymentService(paymentRepository, contractRepository);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造已持久化的合同实体 (用于 findContractOrThrow 返回)
     */
    private ScrmContractEntity buildContractEntity(Long id) {
        ScrmContractEntity entity = new ScrmContractEntity();
        entity.setId(id);
        entity.setContractNo("HT20260805001");
        return entity;
    }

    /**
     * 构造已持久化的合同付款实体 (用于 findById / findByPaymentNo 返回)
     */
    private ScrmContractPaymentEntity buildPaymentEntity(Long id, String status) {
        ScrmContractPaymentEntity entity = new ScrmContractPaymentEntity();
        entity.setId(id);
        entity.setContractId(100L);
        entity.setContractNo("HT20260805001");
        entity.setPaymentNo("PAY_001");
        entity.setPaymentType("MILESTONE");
        entity.setPaymentStatus(status);
        entity.setPlannedAmount(1000.0);
        entity.setPaidAmount(0.0);
        entity.setUnpaidAmount(1000.0);
        entity.setCurrency("CNY");
        entity.setPlannedDate(LocalDate.of(2026, 8, 5));
        return entity;
    }

    @Test
    @DisplayName("addPayment: 写入归属账号与默认值后持久化")
    void addPayment_success() throws ScrmException {
        ScrmContractPaymentDto dto = new ScrmContractPaymentDto();
        dto.setContractId(100L);
        dto.setPaymentNo("PAY_001");
        dto.setPaymentType("MILESTONE");
        dto.setPlannedDate(LocalDate.now().plusDays(30));
        dto.setPlannedAmount(1000.0);
        when(contractRepository.findById(100L)).thenReturn(Optional.of(buildContractEntity(100L)));
        when(paymentRepository.findByPaymentNo("PAY_001")).thenReturn(Optional.empty());
        when(paymentRepository.save(any(ScrmContractPaymentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmContractPaymentDto result = service.addPayment(dto);

        ArgumentCaptor<ScrmContractPaymentEntity> captor =
                ArgumentCaptor.forClass(ScrmContractPaymentEntity.class);
        verify(paymentRepository, times(1)).save(captor.capture());
        ScrmContractPaymentEntity saved = captor.getValue();
        // 默认 paymentStatus=PENDING
        assertThat(saved.getPaymentStatus()).isEqualTo("PENDING");
        // 默认 currency=CNY
        assertThat(saved.getCurrency()).isEqualTo("CNY");
        // 默认 invoiceIssued=false
        assertThat(saved.getInvoiceIssued()).isFalse();
        // 默认 reminderSent=false
        assertThat(saved.getReminderSent()).isFalse();
        // 默认 reminderCount=0
        assertThat(saved.getReminderCount()).isZero();
        // 默认 overdueDays=0 (未传 dueDate 时按 plannedDate 计算, 计划日为今日不逾期)
        assertThat(saved.getOverdueDays()).isZero();
        // unpaidAmount 缺省时由 plannedAmount 填充
        assertThat(saved.getUnpaidAmount()).isEqualTo(1000.0);
        // 冗余 contractNo 由合同实体回填
        assertThat(saved.getContractNo()).isEqualTo("HT20260805001");
        assertThat(result.getPaymentNo()).isEqualTo("PAY_001");
    }

    @Test
    @DisplayName("addPayment: 付款编号重复抛 CONFLICT")
    void addPayment_duplicateNo() {
        ScrmContractPaymentDto dto = new ScrmContractPaymentDto();
        dto.setContractId(100L);
        dto.setPaymentNo("PAY_DUP");
        dto.setPaymentType("MILESTONE");
        dto.setPlannedDate(LocalDate.of(2026, 8, 5));
        when(contractRepository.findById(100L)).thenReturn(Optional.of(buildContractEntity(100L)));
        when(paymentRepository.findByPaymentNo("PAY_DUP"))
                .thenReturn(Optional.of(buildPaymentEntity(10L, "PENDING")));

        assertThatThrownBy(() -> service.addPayment(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("付款编号已存在");
        verify(paymentRepository, never()).save(any());
    }

    
    @Test
    @DisplayName("getPaymentsByContract: 按合同 ID 返回付款列表")
    void getPaymentsByContract_success() {
        ScrmContractPaymentEntity p1 = buildPaymentEntity(11L, "PENDING");
        ScrmContractPaymentEntity p2 = buildPaymentEntity(12L, "PAID");
        when(paymentRepository.findByContractIdOrderByPlannedDateAsc(eq(100L)))
                .thenReturn(List.of(p1, p2));

        List<ScrmContractPaymentDto> result = service.getPaymentsByContract(100L);

        assertThat(result).hasSize(2);
        verify(paymentRepository, times(1))
                .findByContractIdOrderByPlannedDateAsc(eq(100L));
    }

    @Test
    @DisplayName("recordPayment: 全额付款后状态 PAID 并设置 confirmedAt")
    void recordPayment_success() throws ScrmException {
        ScrmContractPaymentEntity entity = buildPaymentEntity(10L, "PENDING");
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(paymentRepository.save(any(ScrmContractPaymentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmContractPaymentDto result = service.recordPayment(10L, 1000.0,
                LocalDate.of(2026, 8, 5), "BANK_TRANSFER", "TXN_001");

        ArgumentCaptor<ScrmContractPaymentEntity> captor =
                ArgumentCaptor.forClass(ScrmContractPaymentEntity.class);
        verify(paymentRepository, times(1)).save(captor.capture());
        ScrmContractPaymentEntity saved = captor.getValue();
        // 已付清 → PAID
        assertThat(saved.getPaymentStatus()).isEqualTo("PAID");
        assertThat(saved.getPaidAmount()).isEqualTo(1000.0);
        assertThat(saved.getUnpaidAmount()).isEqualTo(0.0);
        assertThat(saved.getActualDate()).isEqualTo(LocalDate.of(2026, 8, 5));
        assertThat(saved.getPaymentMethod()).isEqualTo("BANK_TRANSFER");
        assertThat(saved.getTransactionNo()).isEqualTo("TXN_001");
        // confirmedAt 被设置
        assertThat(saved.getConfirmedAt()).isNotNull();
        assertThat(result.getPaymentStatus()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("recordPayment: 已付清状态记录付款抛 BAD_REQUEST")
    void recordPayment_wrongStatus() {
        ScrmContractPaymentEntity entity = buildPaymentEntity(10L, "PAID");
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.recordPayment(10L, 100.0, LocalDate.of(2026, 8, 5), null, null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("付款状态非法");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("recordPayment: 实付金额超过计划金额抛 BAD_REQUEST")
    void recordPayment_amountExceedsPlanned() {
        ScrmContractPaymentEntity entity = buildPaymentEntity(10L, "PENDING");
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.recordPayment(10L, 1500.0, LocalDate.of(2026, 8, 5), null, null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("实付金额超过计划金额");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("getPaymentStats: 按合同 ID 统计已付/未付金额与状态分布")
    void getPaymentStats_success() {
        ScrmContractPaymentEntity p1 = buildPaymentEntity(11L, "PAID");
        p1.setPaidAmount(1000.0);
        p1.setUnpaidAmount(0.0);
        ScrmContractPaymentEntity p2 = buildPaymentEntity(12L, "PENDING");
        when(paymentRepository.findByContractIdOrderByPlannedDateAsc(eq(100L)))
                .thenReturn(List.of(p1, p2));

        Map<String, Object> stats = service.getPaymentStats(100L);

        assertThat(stats.get("contractId")).isEqualTo(100L);
        assertThat(stats.get("totalPayments")).isEqualTo(2);
        assertThat((Double) stats.get("totalPlannedAmount")).isEqualTo(2000.0);
        assertThat((Double) stats.get("totalPaidAmount")).isEqualTo(1000.0);
        assertThat((Double) stats.get("totalUnpaidAmount")).isEqualTo(1000.0);
        // paidCount=1 (p1), overdueCount=0
        assertThat(stats.get("paidCount")).isEqualTo(1L);
        assertThat(stats.get("overdueCount")).isEqualTo(0L);
    }
}
