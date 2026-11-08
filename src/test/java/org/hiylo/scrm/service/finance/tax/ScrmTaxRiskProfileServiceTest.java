/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmTaxRiskProfileServiceTest.java
 * Date : 2026/11/08 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.tax;

import org.hiylo.scrm.dto.ScrmTaxRiskProfileDto;
import org.hiylo.scrm.entity.ScrmTaxDeclarationProfileEntity;
import org.hiylo.scrm.entity.ScrmTaxDeclarationRecordEntity;
import org.hiylo.scrm.repository.ScrmTaxDeclarationProfileRepository;
import org.hiylo.scrm.repository.ScrmTaxDeclarationRecordRepository;
import org.hiylo.scrm.service.finance.voucher.ScrmFinancialReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ScrmTaxRiskProfileService} 单元测试。
 * <p>覆盖：税负率趋势（低/高/正常/数据不足）、逾期率预警、欠缴预警、
 * 空记录不抛异常、连续 3 月低税负预警。</p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@DisplayName("ScrmTaxRiskProfileService 税负风险画像")
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class ScrmTaxRiskProfileServiceTest {

    @Mock
    private ScrmTaxDeclarationProfileRepository profileRepository;

    @Mock
    private ScrmTaxDeclarationRecordRepository recordRepository;

    @Mock
    private ScrmFinancialReportService financialReportService;

    private ScrmTaxRiskProfileService service;

    @BeforeEach
    void setUp() {
        service = new ScrmTaxRiskProfileService(profileRepository, recordRepository,
                financialReportService);
    }

    /** 构造申报档案（taxType 任意） */
    private ScrmTaxDeclarationProfileEntity profile(Long id, String taxType) {
        ScrmTaxDeclarationProfileEntity p = new ScrmTaxDeclarationProfileEntity();
        p.setId(id);
        p.setCompanyId(100L);
        p.setTaxType(taxType);
        p.setStatus("ACTIVE");
        return p;
    }

    /** 构造申报记录 */
    private ScrmTaxDeclarationRecordEntity record(Long id, String taxType,
                                                  LocalDate periodStart, BigDecimal taxAmount,
                                                  BigDecimal paidAmount, LocalDate dueDate,
                                                  String status, Integer overdueDays) {
        ScrmTaxDeclarationRecordEntity r = new ScrmTaxDeclarationRecordEntity();
        r.setId(id);
        r.setProfileId(1L);
        r.setTaxType(taxType);
        r.setPeriodStart(periodStart);
        r.setPeriodEnd(periodStart.plusMonths(1).minusDays(1));
        r.setTaxAmount(taxAmount);
        r.setPaidAmount(paidAmount);
        r.setDueDate(dueDate);
        r.setDeclarationStatus(status);
        r.setOverdueDays(overdueDays);
        return r;
    }

    @Test
    @DisplayName("空申报记录 → 返回空画像不抛异常")
    void emptyProfile() {
        when(profileRepository.findByCompanyIdOrderByCreateTimeDesc(100L)).thenReturn(List.of());

        ScrmTaxRiskProfileDto profile = service.buildProfile(100L, 2026);

        assertThat(profile.getRecordTotal()).isZero();
        assertThat(profile.getOverdueCount()).isZero();
        assertThat(profile.getRisks()).isEmpty();
        assertThat(profile.getTrend()).hasSize(12);
        verify(recordRepository, never()).findByProfileIdInOrderByPeriodStartAsc(any());
    }

    @Test
    @DisplayName("逾期率 > 20% → CRITICAL 预警")
    void overdueRateWarns() {
        when(profileRepository.findByCompanyIdOrderByCreateTimeDesc(100L))
                .thenReturn(List.of(profile(1L, "VAT")));
        LocalDate p1 = LocalDate.of(2026, 1, 1);
        ScrmTaxDeclarationRecordEntity r1 = record(1L, "VAT",
                p1, new BigDecimal("100.00"), new BigDecimal("100.00"),
                LocalDate.of(2026, 1, 15), "PAID", 0);
        ScrmTaxDeclarationRecordEntity r2 = record(2L, "VAT",
                LocalDate.of(2026, 2, 1), new BigDecimal("100.00"), new BigDecimal("100.00"),
                LocalDate.of(2026, 2, 15), "PAID", 0);
        ScrmTaxDeclarationRecordEntity r3 = record(3L, "VAT",
                LocalDate.of(2026, 3, 1), new BigDecimal("100.00"), BigDecimal.ZERO,
                LocalDate.of(2026, 3, 15), "OVERDUE", 5);
        when(recordRepository.findByProfileIdInOrderByPeriodStartAsc(List.of(1L)))
                .thenReturn(List.of(r1, r2, r3));
        when(financialReportService.incomeStatement(100L, 2026, 1))
                .thenReturn(Map.of("revenueCurrent", new BigDecimal("20000.00")));
        when(financialReportService.incomeStatement(100L, 2026, 2))
                .thenReturn(Map.of("revenueCurrent", new BigDecimal("20000.00")));
        when(financialReportService.incomeStatement(100L, 2026, 3))
                .thenReturn(Map.of("revenueCurrent", new BigDecimal("20000.00")));

        ScrmTaxRiskProfileDto profile = service.buildProfile(100L, 2026);

        assertThat(profile.getRecordTotal()).isEqualTo(3);
        assertThat(profile.getOverdueCount()).isEqualTo(1);
        // 逾期率 33% > 20% → CRITICAL
        assertThat(profile.getRisks()).anyMatch(r ->
                "CRITICAL".equals(r.getLevel()) && r.getMessage().contains("逾期率"));
    }

    @Test
    @DisplayName("已到期欠缴（实缴 < 应缴 且未取消）→ WARN 预警")
    void underpaidWarns() {
        when(profileRepository.findByCompanyIdOrderByCreateTimeDesc(100L))
                .thenReturn(List.of(profile(1L, "VAT")));
        ScrmTaxDeclarationRecordEntity r = record(1L, "VAT",
                LocalDate.of(2026, 1, 1), new BigDecimal("5000.00"), new BigDecimal("2000.00"),
                LocalDate.of(2026, 1, 15), "FILED", 0); // 已申报未缴清，且已到期
        when(recordRepository.findByProfileIdInOrderByPeriodStartAsc(List.of(1L)))
                .thenReturn(List.of(r));
        when(financialReportService.incomeStatement(100L, 2026, 1))
                .thenReturn(Map.of("revenueCurrent", new BigDecimal("100000.00")));

        ScrmTaxRiskProfileDto profile = service.buildProfile(100L, 2026);

        assertThat(profile.getUnderpaidCount()).isEqualTo(1);
        assertThat(profile.getRisks()).anyMatch(risk ->
                "WARN".equals(risk.getLevel()) && risk.getMessage().contains("欠缴"));
    }

    @Test
    @DisplayName("连续 3 个月税负率低于 1% → WARN 偏低预警")
    void lowRatioConsecutiveWarns() {
        when(profileRepository.findByCompanyIdOrderByCreateTimeDesc(100L))
                .thenReturn(List.of(profile(1L, "VAT")));
        List<ScrmTaxDeclarationRecordEntity> records = List.of(
                record(1L, "VAT", LocalDate.of(2026, 1, 1),
                        new BigDecimal("500.00"), new BigDecimal("500.00"),
                        LocalDate.of(2026, 1, 15), "PAID", 0),
                record(2L, "VAT", LocalDate.of(2026, 2, 1),
                        new BigDecimal("500.00"), new BigDecimal("500.00"),
                        LocalDate.of(2026, 2, 15), "PAID", 0),
                record(3L, "VAT", LocalDate.of(2026, 3, 1),
                        new BigDecimal("500.00"), new BigDecimal("500.00"),
                        LocalDate.of(2026, 3, 15), "PAID", 0));
        when(recordRepository.findByProfileIdInOrderByPeriodStartAsc(List.of(1L)))
                .thenReturn(records);
        // 收入 100000 → 税负率 0.5% < 1% 连续 3 月
        when(financialReportService.incomeStatement(anyLong(), anyInt(), anyInt()))
                .thenReturn(Map.of("revenueCurrent", new BigDecimal("100000.00")));

        ScrmTaxRiskProfileDto profile = service.buildProfile(100L, 2026);

        assertThat(profile.getTrend()).hasSize(12);
        assertThat(profile.getTrend().get(0).getFlag()).isEqualTo("LOW");
        assertThat(profile.getRisks()).anyMatch(risk ->
                "WARN".equals(risk.getLevel()) && risk.getMessage().contains("低于 1%"));
    }

    @Test
    @DisplayName("利润表异常 → 税负率标记数据不足（NA）且不抛异常")
    void incomeStatementFailureMarksNa() {
        when(profileRepository.findByCompanyIdOrderByCreateTimeDesc(100L))
                .thenReturn(List.of(profile(1L, "VAT")));
        ScrmTaxDeclarationRecordEntity r = record(1L, "VAT",
                LocalDate.of(2026, 1, 1), new BigDecimal("500.00"), new BigDecimal("500.00"),
                LocalDate.of(2026, 1, 15), "PAID", 0);
        when(recordRepository.findByProfileIdInOrderByPeriodStartAsc(List.of(1L)))
                .thenReturn(List.of(r));
        when(financialReportService.incomeStatement(anyLong(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("利润表不可用"));

        ScrmTaxRiskProfileDto profile = service.buildProfile(100L, 2026);

        assertThat(profile.getTrend().get(0).getFlag()).isEqualTo("NA");
        assertThat(profile.getTrend().get(0).getTaxRate()).isEqualTo("-");
    }

    @Test
    @DisplayName("税负率正常（2%）→ 无低/高预警，flag=NORMAL")
    void normalRatioNoRisk() {
        when(profileRepository.findByCompanyIdOrderByCreateTimeDesc(100L))
                .thenReturn(List.of(profile(1L, "VAT")));
        // 单月 2000/100000 = 2%，既不低于 1% 也不高于 4.5%
        ScrmTaxDeclarationRecordEntity r = record(1L, "VAT",
                LocalDate.of(2026, 1, 1), new BigDecimal("2000.00"), new BigDecimal("2000.00"),
                LocalDate.of(2026, 1, 15), "PAID", 0);
        when(recordRepository.findByProfileIdInOrderByPeriodStartAsc(List.of(1L)))
                .thenReturn(List.of(r));
        when(financialReportService.incomeStatement(100L, 2026, 1))
                .thenReturn(Map.of("revenueCurrent", new BigDecimal("100000.00")));

        ScrmTaxRiskProfileDto profile = service.buildProfile(100L, 2026);

        assertThat(profile.getTrend().get(0).getFlag()).isEqualTo("NORMAL");
        assertThat(profile.getRisks()).noneMatch(risk ->
                risk.getMessage().contains("税负率连续"));
    }
}