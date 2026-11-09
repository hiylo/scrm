/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmClientPortalServiceTest.java
 * Date : 2026/11/08 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.portal;

import org.hiylo.scrm.entity.ScrmCompanyEntity;
import org.hiylo.scrm.entity.ScrmTaxDeclarationProfileEntity;
import org.hiylo.scrm.entity.ScrmTaxDeclarationRecordEntity;
import org.hiylo.scrm.repository.ScrmCompanyRepository;
import org.hiylo.scrm.repository.ScrmTaxDeclarationProfileRepository;
import org.hiylo.scrm.repository.ScrmTaxDeclarationRecordRepository;
import org.hiylo.scrm.service.finance.asset.ScrmFixedAssetService;
import org.hiylo.scrm.service.finance.tax.ScrmTaxRiskProfileService;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * {@link ScrmClientPortalService} 单元测试。
 * <p>覆盖：聚合含 5 块、逐块容错、报税统计口径、空档案、区块字段。</p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@DisplayName("ScrmClientPortalService 客户查账")
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class ScrmClientPortalServiceTest {

    @Mock
    private ScrmCompanyRepository companyRepository;

    @Mock
    private ScrmTaxDeclarationProfileRepository profileRepository;

    @Mock
    private ScrmTaxDeclarationRecordRepository recordRepository;

    @Mock
    private ScrmFinancialReportService financialReportService;

    @Mock
    private ScrmFixedAssetService fixedAssetService;

    @Mock
    private ScrmTaxRiskProfileService riskProfileService;

    private ScrmClientPortalService service;

    @BeforeEach
    void setUp() {
        service = new ScrmClientPortalService(companyRepository, profileRepository,
                recordRepository, financialReportService, fixedAssetService, riskProfileService);
    }

    private ScrmTaxDeclarationProfileEntity profile(Long id) {
        ScrmTaxDeclarationProfileEntity p = new ScrmTaxDeclarationProfileEntity();
        p.setId(id);
        p.setCompanyId(100L);
        p.setTaxType("VAT");
        return p;
    }

    private ScrmTaxDeclarationRecordEntity record(Long id, String taxType, LocalDate periodStart,
                                                  BigDecimal tax, BigDecimal paid, String status,
                                                  Integer overdueDays) {
        ScrmTaxDeclarationRecordEntity r = new ScrmTaxDeclarationRecordEntity();
        r.setId(id);
        r.setProfileId(id);
        r.setTaxType(taxType);
        r.setPeriodStart(periodStart);
        r.setPeriodEnd(periodStart.plusMonths(1).minusDays(1));
        r.setDueDate(periodStart.plusMonths(1).plusDays(15));
        r.setTaxAmount(tax);
        r.setPaidAmount(paid);
        r.setDeclarationStatus(status);
        r.setOverdueDays(overdueDays == null ? 0 : overdueDays);
        return r;
    }

    @Test
    @DisplayName("聚合含公司/报税/报表/资产/税负 5 块")
    void overviewContainsAllBlocks() {
        ScrmCompanyEntity company = new ScrmCompanyEntity();
        company.setId(100L);
        company.setCompanyName("测试企业");
        company.setCreditCode("9137...");
        company.setAccountantName("张三");
        when(companyRepository.findById(100L)).thenReturn(Optional.of(company));

        ScrmTaxDeclarationProfileEntity p = profile(1L);
        when(profileRepository.findByCompanyIdOrderByCreateTimeDesc(100L)).thenReturn(List.of(p));
        ScrmTaxDeclarationRecordEntity r = record(1L, "VAT",
                LocalDate.of(2026, 1, 1), new BigDecimal("500.00"), new BigDecimal("500.00"),
                "PAID", 0);
        when(recordRepository.findByProfileIdInOrderByPeriodStartAsc(List.of(1L)))
                .thenReturn(List.of(r));
        when(financialReportService.incomeStatement(anyLong(), anyInt(), anyInt()))
                .thenReturn(Map.of("period", "202611",
                        "revenueCurrent", new BigDecimal("100000.00"),
                        "netProfitCurrent", new BigDecimal("20000.00")));
        when(financialReportService.balanceSheet(anyLong(), anyInt(), anyInt()))
                .thenReturn(Map.of("period", "202611",
                        "totalAssetClosing", new BigDecimal("500000.00"),
                        "totalLiabilityClosing", new BigDecimal("300000.00"),
                        "totalEquityClosing", new BigDecimal("200000.00"), "balanced", true));
        when(fixedAssetService.companySummary(100L))
                .thenReturn(Map.of("assetCount", 1, "netTotal", new BigDecimal("30000.00")));
        when(riskProfileService.buildProfile(100L, 2026))
                .thenReturn(org.hiylo.scrm.dto.ScrmTaxRiskProfileDto.builder()
                        .recordTotal(1).overdueCount(0).underpaidCount(0).build());

        Map<String, Object> result = service.overview(100L, 2026);

        assertThat(result.get("companyId")).isEqualTo(100L);
        assertThat(result.get("year")).isEqualTo(2026);
        assertThat(result.get("company")).isInstanceOf(Map.class);
        assertThat(result.get("taxSummary")).isNotNull();
        assertThat(result.get("reports")).isNotNull();
        assertThat(result.get("assets")).isNotNull();
        assertThat(result.get("taxRisk")).isNotNull();
    }

    @Test
    @DisplayName("公司概况含关键字段")
    void companyInfoFields() {
        ScrmCompanyEntity company = new ScrmCompanyEntity();
        company.setId(100L);
        company.setCompanyName("测试企业");
        company.setCreditCode("9137ABC");
        company.setLegalRepresentative("李四");
        company.setTaxStatus("NORMAL");
        company.setAccountantName("张三");
        when(companyRepository.findById(100L)).thenReturn(Optional.of(company));

        @SuppressWarnings("unchecked")
        Map<String, Object> companyMap = (Map<String, Object>) service.overview(100L, 2026)
                .get("company");
        assertThat(companyMap.get("companyName")).isEqualTo("测试企业");
        assertThat(companyMap.get("creditCode")).isEqualTo("9137ABC");
        assertThat(companyMap.get("legalRepresentative")).isEqualTo("李四");
        assertThat(companyMap.get("accountantName")).isEqualTo("张三");
    }

    @Test
    @DisplayName("报税统计：PAID 计入 filed，OVERDUE 计数逾期")
    void taxSummaryCounts() {
        when(profileRepository.findByCompanyIdOrderByCreateTimeDesc(100L))
                .thenReturn(List.of(profile(1L)));
        when(recordRepository.findByProfileIdInOrderByPeriodStartAsc(List.of(1L)))
                .thenReturn(List.of(
                        record(1L, "VAT", LocalDate.of(2026, 1, 1),
                                new BigDecimal("500.00"), new BigDecimal("500.00"), "PAID", 0),
                        record(2L, "VAT", LocalDate.of(2026, 2, 1),
                                new BigDecimal("300.00"), new BigDecimal("300.00"), "FILED", 0),
                        record(3L, "VAT", LocalDate.of(2026, 3, 1),
                                new BigDecimal("200.00"), BigDecimal.ZERO, "OVERDUE", 5)));

        Map<String, Object> summary = (Map<String, Object>) service.overview(100L, 2026)
                .get("taxSummary");
        assertThat(summary.get("recordTotal")).isEqualTo(3);
        assertThat(summary.get("filedCount")).isEqualTo(2L);
        assertThat(summary.get("overdueCount")).isEqualTo(1L);
    }

    @Test
    @DisplayName("空档案 → 返回空报税统计（recordTotal=0）")
    void emptyProfileGivesEmptyTaxSummary() {
        when(profileRepository.findByCompanyIdOrderByCreateTimeDesc(100L)).thenReturn(List.of());

        Map<String, Object> summary = (Map<String, Object>) service.overview(100L, 2026)
                .get("taxSummary");
        assertThat(summary.get("recordTotal")).isEqualTo(0L);
        assertThat(summary.get("recentRecords")).asList().isEmpty();
    }

    @Test
    @DisplayName("逐块容错：资产负债表异常不影响其它块")
    void reportFailureDoesNotBreakOthers() {
        when(companyRepository.findById(100L)).thenReturn(Optional.of(new ScrmCompanyEntity()));
        when(profileRepository.findByCompanyIdOrderByCreateTimeDesc(100L)).thenReturn(List.of());
        when(financialReportService.incomeStatement(anyLong(), anyInt(), anyInt()))
                .thenReturn(Map.of("period", "202611",
                        "revenueCurrent", new BigDecimal("100000.00")));
        when(financialReportService.balanceSheet(anyLong(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("报表不可用"));
        when(fixedAssetService.companySummary(100L))
                .thenReturn(Map.of("assetCount", 0, "netTotal", BigDecimal.ZERO));

        Map<String, Object> result = service.overview(100L, 2026);

        Map<String, Object> reports = (Map<String, Object>) result.get("reports");
        assertThat(reports.get("balanceSheet")).isNull();
        assertThat(reports.get("incomeStatements")).isNotNull();
        // 公司/报税块不受影响
        assertThat(result.get("company")).isNotNull();
        assertThat(result.get("taxSummary")).isNotNull();
        assertThat(result.get("assets")).isNotNull();
    }

    @Test
    @DisplayName("公司不存在 → company=null 但其余块照常")
    void missingCompanyDoesNotBreak() {
        when(companyRepository.findById(100L)).thenReturn(Optional.empty());
        when(profileRepository.findByCompanyIdOrderByCreateTimeDesc(100L)).thenReturn(List.of());

        Map<String, Object> result = service.overview(100L, 2026);
        assertThat(result.get("company")).isNull();
        assertThat(result.get("taxSummary")).isNotNull();
        assertThat(result.get("reports")).isNotNull();
    }
}