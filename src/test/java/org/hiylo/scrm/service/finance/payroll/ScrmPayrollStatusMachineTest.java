/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmPayrollStatusMachineTest.java
 * Date : 2026/11/10 10:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.payroll;

import org.hiylo.scrm.config.ScrmPayrollProperties;
import org.hiylo.scrm.entity.ScrmIitRecordEntity;
import org.hiylo.scrm.entity.ScrmSocialSecurityRecordEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCompanyRepository;
import org.hiylo.scrm.repository.ScrmEmployeeRepository;
import org.hiylo.scrm.repository.ScrmIitRecordRepository;
import org.hiylo.scrm.repository.ScrmSocialSecurityRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 薪酬状态机（社保 / 个税 申报 ↔ 缴款）与批量操作单元测试。
 * <p>
 * 锁定的核心语义：<b>PAID 是终态，不允许退回「已申报」</b>。
 * 修复前 markSocialStatus / markIitStatus 没有任何状态校验，
 * 一次误调就能把已缴款记录降级回 DECLARED，造成对账数据错误。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("薪酬服务：社保/个税状态机与批量操作")
class ScrmPayrollStatusMachineTest {

    /** 员工仓库 */
    @Mock
    private ScrmEmployeeRepository employeeRepository;

    /** 社保仓库 */
    @Mock
    private ScrmSocialSecurityRecordRepository socialRepository;

    /** 个税仓库 */
    @Mock
    private ScrmIitRecordRepository iitRepository;

    /** 主体仓库 */
    @Mock
    private ScrmCompanyRepository companyRepository;

    /** 财税数据范围（本测试不涉及） */
    @Mock
    private org.hiylo.scrm.service.finance.FinanceDataScope financeDataScope;

    /** 被测服务 */
    private ScrmPayrollService service;

    /** 组装被测服务 */
    @BeforeEach
    void setUp() {
        ScrmPayrollCalculator calculator = new ScrmPayrollCalculator(new ScrmPayrollProperties());
        service = new ScrmPayrollService(employeeRepository, socialRepository,
                iitRepository, companyRepository, calculator, new ScrmPayrollProperties(),
                financeDataScope);
    }

    /**
     * 构造社保明细。
     *
     * @param id     ID
     * @param status 状态
     * @return 实体
     */
    private ScrmSocialSecurityRecordEntity social(Long id, String status) {
        ScrmSocialSecurityRecordEntity e = new ScrmSocialSecurityRecordEntity();
        e.setId(id);
        e.setStatus(status);
        return e;
    }

    /**
     * 构造个税明细。
     *
     * @param id     ID
     * @param status 状态
     * @return 实体
     */
    private ScrmIitRecordEntity iit(Long id, String status) {
        ScrmIitRecordEntity e = new ScrmIitRecordEntity();
        e.setId(id);
        e.setStatus(status);
        return e;
    }

    // ==================== 社保状态机 ====================

    @Test
    @DisplayName("社保：PENDING → DECLARED，回填申报时间")
    void social_pendingToDeclared() throws Exception {
        ScrmSocialSecurityRecordEntity e = social(1L, ScrmSocialSecurityRecordEntity.STATUS_PENDING);
        when(socialRepository.findById(1L)).thenReturn(Optional.of(e));
        when(socialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ScrmSocialSecurityRecordEntity r = service.markSocialStatus(1L, false);

        assertThat(r.getStatus()).isEqualTo(ScrmSocialSecurityRecordEntity.STATUS_DECLARED);
        assertThat(r.getDeclaredAt()).isNotNull();
        assertThat(r.getPaidAt()).isNull();
    }

    @Test
    @DisplayName("社保：PAID 不允许退回 DECLARED（防对账错误），且不落库")
    void social_paidCannotDowngrade() {
        ScrmSocialSecurityRecordEntity e = social(1L, ScrmSocialSecurityRecordEntity.STATUS_PAID);
        when(socialRepository.findById(1L)).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.markSocialStatus(1L, false))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("不能退回");
        verify(socialRepository, never()).save(any());
    }

    @Test
    @DisplayName("社保：PAID 重复缴款幂等通过，不刷新 paidAt")
    void social_paidIdempotent() throws Exception {
        ScrmSocialSecurityRecordEntity e = social(1L, ScrmSocialSecurityRecordEntity.STATUS_PAID);
        LocalDateTime originalPaidAt = LocalDateTime.of(2026, 10, 1, 9, 30);
        e.setPaidAt(originalPaidAt);
        when(socialRepository.findById(1L)).thenReturn(Optional.of(e));

        ScrmSocialSecurityRecordEntity r = service.markSocialStatus(1L, true);

        assertThat(r.getStatus()).isEqualTo(ScrmSocialSecurityRecordEntity.STATUS_PAID);
        assertThat(r.getPaidAt()).isEqualTo(originalPaidAt);
        verify(socialRepository, never()).save(any());
    }

    // ==================== 个税状态机 ====================

    @Test
    @DisplayName("个税：DECLARED → PAID，回填缴款时间")
    void iit_declaredToPaid() throws Exception {
        ScrmIitRecordEntity e = iit(2L, ScrmIitRecordEntity.STATUS_DECLARED);
        when(iitRepository.findById(2L)).thenReturn(Optional.of(e));
        when(iitRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ScrmIitRecordEntity r = service.markIitStatus(2L, true);

        assertThat(r.getStatus()).isEqualTo(ScrmIitRecordEntity.STATUS_PAID);
        assertThat(r.getPaidAt()).isNotNull();
    }

    @Test
    @DisplayName("个税：PAID 不允许退回 DECLARED，且不落库")
    void iit_paidCannotDowngrade() {
        ScrmIitRecordEntity e = iit(2L, ScrmIitRecordEntity.STATUS_PAID);
        when(iitRepository.findById(2L)).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.markIitStatus(2L, false))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("不能退回");
        verify(iitRepository, never()).save(any());
    }

    // ==================== 批量 ====================

    @Test
    @DisplayName("社保批量申报：全部 PENDING → 全成功")
    void batchSocial_allSuccess() {
        ScrmSocialSecurityRecordEntity a = social(1L, ScrmSocialSecurityRecordEntity.STATUS_PENDING);
        ScrmSocialSecurityRecordEntity b = social(2L, ScrmSocialSecurityRecordEntity.STATUS_PENDING);
        when(socialRepository.findById(1L)).thenReturn(Optional.of(a));
        when(socialRepository.findById(2L)).thenReturn(Optional.of(b));
        when(socialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> r = service.batchMarkSocialStatus(List.of(1L, 2L), false);

        assertThat(r.get("successCount")).isEqualTo(2);
        assertThat(r.get("failCount")).isEqualTo(0);
        assertThat(a.getStatus()).isEqualTo(ScrmSocialSecurityRecordEntity.STATUS_DECLARED);
        assertThat(b.getStatus()).isEqualTo(ScrmSocialSecurityRecordEntity.STATUS_DECLARED);
    }

    @Test
    @DisplayName("社保批量申报：含已缴费行 → 该行失败，其它行仍成功")
    void batchSocial_partialFailure() {
        ScrmSocialSecurityRecordEntity pending = social(1L, ScrmSocialSecurityRecordEntity.STATUS_PENDING);
        ScrmSocialSecurityRecordEntity paid = social(2L, ScrmSocialSecurityRecordEntity.STATUS_PAID);
        when(socialRepository.findById(1L)).thenReturn(Optional.of(pending));
        when(socialRepository.findById(2L)).thenReturn(Optional.of(paid));
        when(socialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> r = service.batchMarkSocialStatus(List.of(1L, 2L), false);

        assertThat(r.get("successCount")).isEqualTo(1);
        assertThat(r.get("failCount")).isEqualTo(1);
        assertThat(pending.getStatus()).isEqualTo(ScrmSocialSecurityRecordEntity.STATUS_DECLARED);
        assertThat(paid.getStatus()).isEqualTo(ScrmSocialSecurityRecordEntity.STATUS_PAID);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> failures = (List<Map<String, Object>>) r.get("failures");
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0).get("id")).isEqualTo(2L);
    }

    @Test
    @DisplayName("个税批量缴款：PENDING/DECLARED → PAID 全成功")
    void batchIit_allToPaid() {
        ScrmIitRecordEntity a = iit(1L, ScrmIitRecordEntity.STATUS_PENDING);
        ScrmIitRecordEntity b = iit(2L, ScrmIitRecordEntity.STATUS_DECLARED);
        when(iitRepository.findById(1L)).thenReturn(Optional.of(a));
        when(iitRepository.findById(2L)).thenReturn(Optional.of(b));
        when(iitRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> r = service.batchMarkIitStatus(List.of(1L, 2L), true);

        assertThat(r.get("successCount")).isEqualTo(2);
        assertThat(a.getStatus()).isEqualTo(ScrmIitRecordEntity.STATUS_PAID);
        assertThat(b.getStatus()).isEqualTo(ScrmIitRecordEntity.STATUS_PAID);
        assertThat(a.getDeclaredAt()).isNotNull();
    }

    @Test
    @DisplayName("批量：空列表 / null → 0 成功 0 失败，不抛")
    void batchEmptySafe() {
        assertThat(service.batchMarkSocialStatus(List.of(), false).get("successCount")).isEqualTo(0);
        assertThat(service.batchMarkIitStatus(null, true).get("failCount")).isEqualTo(0);
    }
}
