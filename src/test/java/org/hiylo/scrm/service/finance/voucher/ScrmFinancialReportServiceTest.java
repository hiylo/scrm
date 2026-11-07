/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmFinancialReportServiceTest.java
 * Date : 2026/10/25 23:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.voucher;

import org.hiylo.scrm.entity.ScrmCompanyEntity;
import org.hiylo.scrm.repository.ScrmAccountSubjectRepository;
import org.hiylo.scrm.repository.ScrmCompanyRepository;
import org.hiylo.scrm.repository.ScrmVoucherEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * {@link ScrmFinancialReportService} 单元测试（资产负债表 / 利润表）。
 * <p>
 * <b>最关键的断言是会计恒等式</b>：资产 = 负债 + 所有者权益。
 * 只要这条不成立，报表就是错的，其他数字再对也没意义。
 * 这里特别覆盖「损益未结转时也必须平衡」——这是最容易漏的场景。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("财务报表：资产负债表恒等式 / 利润表")
class ScrmFinancialReportServiceTest {

    /** 科目仓储 */
    @Mock
    private ScrmAccountSubjectRepository subjectRepository;

    /** 分录仓储 */
    @Mock
    private ScrmVoucherEntryRepository entryRepository;

    /** 主体仓储 */
    @Mock
    private ScrmCompanyRepository companyRepository;

    /** 被测服务 */
    private ScrmFinancialReportService service;

    /**
     * 组装服务。
     */
    @BeforeEach
    void setUp() {
        service = new ScrmFinancialReportService(subjectRepository, entryRepository, companyRepository);
        ScrmCompanyEntity company = new ScrmCompanyEntity();
        company.setId(100L);
        company.setCompanyName("测试公司");
        when(companyRepository.findById(anyLong())).thenReturn(Optional.of(company));
        when(subjectRepository.findByCompanyIdOrderBySubjectCodeAsc(anyLong())).thenReturn(List.of());
    }

    /** 构造聚合行。 */
    private Object[] row(String code, String name, String debit, String credit) {
        return new Object[]{code, name,
                debit == null ? BigDecimal.ZERO : new BigDecimal(debit),
                credit == null ? BigDecimal.ZERO : new BigDecimal(credit)};
    }

    /** 从结果里取某一行金额。 */
    @SuppressWarnings("unchecked")
    private BigDecimal amountOf(Map<String, Object> result, String key, String name, String field) {
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get(key);
        return rows.stream()
                .filter(r -> name.equals(r.get("name")))
                .map(r -> (BigDecimal) r.get(field))
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    // ==================== 资产负债表 ====================

    @Test
    @DisplayName("实收资本 1000 / 银行存款 1000：资产 = 负债 + 权益")
    void balanceSheetBalancesWithCapital() throws Exception {
        when(entryRepository.sumUpTo(100L, 202610)).thenReturn(List.<Object[]>of(
                row("1002", "银行存款", "1000.00", null),
                row("3001", "实收资本", null, "1000.00")));

        Map<String, Object> result = service.balanceSheet(100L, 2026, 10);

        assertThat((BigDecimal) result.get("totalAssetClosing")).isEqualByComparingTo("1000.00");
        assertThat((BigDecimal) result.get("totalLiabilityAndEquityClosing"))
                .isEqualByComparingTo("1000.00");
        assertThat(result.get("balanced")).isEqualTo(true);
        assertThat(amountOf(result, "assets", "货币资金", "closing"))
                .isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("损益未结转时资产负债表仍然平衡（未分配利润含未结转损益）")
    void balanceSheetBalancesWithUnclosedProfit() throws Exception {
        // 收到 1300，其中 500 收入、200 费用 → 未结转利润 300
        when(entryRepository.sumUpTo(100L, 202610)).thenReturn(List.<Object[]>of(
                row("1002", "银行存款", "1300.00", null),
                row("3001", "实收资本", null, "1000.00"),
                row("5001", "主营业务收入", null, "500.00"),
                row("5602", "管理费用", "200.00", null)));

        Map<String, Object> result = service.balanceSheet(100L, 2026, 10);

        assertThat((BigDecimal) result.get("totalAssetClosing")).isEqualByComparingTo("1300.00");
        assertThat(amountOf(result, "equities", "实收资本", "closing"))
                .isEqualByComparingTo("1000.00");
        // 未分配利润 = 未结转的 500 - 200 = 300
        assertThat(amountOf(result, "equities", "未分配利润", "closing"))
                .isEqualByComparingTo("300.00");
        assertThat((BigDecimal) result.get("totalLiabilityAndEquityClosing"))
                .isEqualByComparingTo("1300.00");
        assertThat(result.get("balanced")).isEqualTo(true);
    }

    @Test
    @DisplayName("累计折旧以负数并入固定资产，自动抵减")
    void balanceSheetHandlesAccumulatedDepreciation() throws Exception {
        when(entryRepository.sumUpTo(100L, 202610)).thenReturn(List.<Object[]>of(
                row("1601", "固定资产", "10000.00", null),
                row("1602", "累计折旧", null, "2000.00"),
                row("3001", "实收资本", null, "8000.00")));

        Map<String, Object> result = service.balanceSheet(100L, 2026, 10);

        assertThat(amountOf(result, "assets", "固定资产原价", "closing"))
                .isEqualByComparingTo("10000.00");
        assertThat(amountOf(result, "assets", "减：累计折旧", "closing"))
                .isEqualByComparingTo("-2000.00");
        // 资产合计 = 10000 - 2000 = 8000
        assertThat((BigDecimal) result.get("totalAssetClosing")).isEqualByComparingTo("8000.00");
        assertThat(result.get("balanced")).isEqualTo(true);
    }

    @Test
    @DisplayName("明细科目自动归并到一级科目（560201 → 管理费用）")
    void rollsUpDetailSubjects() throws Exception {
        when(entryRepository.sumUpTo(100L, 202610)).thenReturn(List.<Object[]>of(
                row("1002", "银行存款", "300.00", null),
                row("3001", "实收资本", null, "1000.00"),
                row("560201", "管理费用-办公费", "700.00", null)));

        Map<String, Object> result = service.balanceSheet(100L, 2026, 10);

        // 未结转损益 = -700，未分配利润 = 1000 - 700 = 300
        assertThat(amountOf(result, "equities", "未分配利润", "closing"))
                .isEqualByComparingTo("-700.00");
        assertThat(result.get("balanced")).isEqualTo(true);
    }

    // ==================== 利润表 ====================

    @Test
    @DisplayName("利润表：收入 1000 − 成本 400 − 费用 100 = 营业利润 500")
    void incomeStatementComputesOperatingProfit() throws Exception {
        when(entryRepository.sumBetweenExcludingClosing(anyLong(), anyInt(), anyInt()))
                .thenReturn(List.<Object[]>of(
                        row("5001", "主营业务收入", null, "1000.00"),
                        row("5401", "主营业务成本", "400.00", null),
                        row("5602", "管理费用", "100.00", null)));

        Map<String, Object> result = service.incomeStatement(100L, 2026, 10);

        assertThat((BigDecimal) result.get("revenueCurrent")).isEqualByComparingTo("1000.00");
        assertThat((BigDecimal) result.get("netProfitCurrent")).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("利润表含营业外收支与所得税")
    void incomeStatementIncludesNonOperatingAndTax() throws Exception {
        when(entryRepository.sumBetweenExcludingClosing(anyLong(), anyInt(), anyInt()))
                .thenReturn(List.<Object[]>of(
                        row("5001", "主营业务收入", null, "1000.00"),
                        row("5301", "营业外收入", null, "100.00"),
                        row("5711", "营业外支出", "50.00", null),
                        row("5801", "所得税费用", "30.00", null)));

        Map<String, Object> result = service.incomeStatement(100L, 2026, 10);

        // 营业利润 1000 + 营业外收入 100 - 营业外支出 50 = 1050；减所得税 30 = 1020
        assertThat((BigDecimal) result.get("netProfitCurrent")).isEqualByComparingTo("1020.00");
    }

    @Test
    @DisplayName("利润表聚合排除结转凭证（否则结账后利润变 0）")
    void incomeStatementExcludesClosingVouchers() throws Exception {
        service.incomeStatement(100L, 2026, 10);

        // 必须走「排除结转凭证」的那条查询，不能走普通 sumBetween
        org.mockito.Mockito.verify(entryRepository, org.mockito.Mockito.atLeastOnce())
                .sumBetweenExcludingClosing(anyLong(), anyInt(), anyInt());
        org.mockito.Mockito.verify(entryRepository, org.mockito.Mockito.never())
                .sumBetween(anyLong(), anyInt(), anyInt());
    }
}
