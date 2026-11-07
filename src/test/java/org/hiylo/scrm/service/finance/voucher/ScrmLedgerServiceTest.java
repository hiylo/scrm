/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmLedgerServiceTest.java
 * Date : 2026/10/25 23:20:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.voucher;

import org.hiylo.scrm.entity.ScrmAccountSubjectEntity;
import org.hiylo.scrm.entity.ScrmCompanyEntity;
import org.hiylo.scrm.exception.ScrmException;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * {@link ScrmLedgerService} 单元测试（总账 / 明细账 / 序时账）。
 * <p>
 * 明细账的<b>逐笔余额</b>是最容易算错的：期初余额要按科目前缀包含下级明细，
 * 逐笔余额要用「借−贷」累加（负数即贷方）。这里逐笔断言。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("账簿：总账 / 明细账逐笔余额 / 序时账")
class ScrmLedgerServiceTest {

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
    private ScrmLedgerService service;

    /**
     * 组装服务。
     */
    @BeforeEach
    void setUp() {
        service = new ScrmLedgerService(subjectRepository, entryRepository, companyRepository);
        ScrmCompanyEntity company = new ScrmCompanyEntity();
        company.setId(100L);
        company.setCompanyName("测试公司");
        when(companyRepository.findById(anyLong())).thenReturn(Optional.of(company));

        ScrmAccountSubjectEntity bank = new ScrmAccountSubjectEntity();
        bank.setSubjectCode("1002");
        bank.setSubjectName("银行存款");
        bank.setCategory(ScrmAccountSubjectEntity.CATEGORY_ASSET);
        bank.setDirection(ScrmAccountSubjectEntity.DIRECTION_DEBIT);
        when(subjectRepository.findByCompanyIdOrderBySubjectCodeAsc(anyLong()))
                .thenReturn(List.of(bank));
    }

    /**
     * 构造一条分录明细行。
     *
     * @param date   日期
     * @param no     凭证号
     * @param code   科目编码
     * @param debit  借方
     * @param credit 贷方
     * @return 行
     */
    private Object[] detail(String date, int no, String code, String debit, String credit) {
        return new Object[]{
                LocalDate.parse(date), "记", no, "摘要", code, "银行存款",
                debit == null ? BigDecimal.ZERO : new BigDecimal(debit),
                credit == null ? BigDecimal.ZERO : new BigDecimal(credit),
                "备注", 1L};
    }

    // ==================== 明细账 ====================

    @Test
    @DisplayName("明细账逐笔余额：期初 1000，借 500 贷 200 后余额 1300")
    @SuppressWarnings("unchecked")
    void detailLedgerComputesRunningBalance() throws Exception {
        // 期初：一级科目 1002 有 1000 借方
        when(entryRepository.sumBeforePeriod(100L, 202610)).thenReturn(List.<Object[]>of(
                new Object[]{"1002", "银行存款", new BigDecimal("1000.00"), BigDecimal.ZERO}));
        when(entryRepository.findEntryDetails(100L, 202610, 202610, "1002"))
                .thenReturn(List.<Object[]>of(
                        detail("2026-10-05", 1, "1002", "500.00", null),
                        detail("2026-10-18", 2, "1002", null, "200.00")));

        Map<String, Object> result = service.detailLedger(100L, "1002", 2026, 10);

        assertThat((BigDecimal) result.get("openingBalance")).isEqualByComparingTo("1000.00");
        assertThat(result.get("openingDirection")).isEqualTo("借");

        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertThat(items).hasSize(2);
        assertThat((BigDecimal) items.get(0).get("balance")).isEqualByComparingTo("1500.00");
        assertThat((BigDecimal) items.get(1).get("balance")).isEqualByComparingTo("1300.00");

        assertThat((BigDecimal) result.get("periodDebit")).isEqualByComparingTo("500.00");
        assertThat((BigDecimal) result.get("periodCredit")).isEqualByComparingTo("200.00");
        assertThat((BigDecimal) result.get("closingBalance")).isEqualByComparingTo("1300.00");
    }

    @Test
    @DisplayName("明细账期初按科目前缀汇总（含下级明细科目）")
    void detailLedgerOpeningIncludesChildSubjects() throws Exception {
        when(entryRepository.sumBeforePeriod(100L, 202610)).thenReturn(List.<Object[]>of(
                new Object[]{"1002", "银行存款", new BigDecimal("800.00"), BigDecimal.ZERO},
                new Object[]{"100201", "银行存款-工行", new BigDecimal("200.00"), BigDecimal.ZERO}));
        when(entryRepository.findEntryDetails(anyLong(), any(), any(), any()))
                .thenReturn(List.<Object[]>of());

        Map<String, Object> result = service.detailLedger(100L, "1002", 2026, 10);

        // 800 + 200 = 1000
        assertThat((BigDecimal) result.get("openingBalance")).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("明细账期末为贷方余额时方向标识为「贷」")
    void detailLedgerMarksCreditBalance() throws Exception {
        when(entryRepository.sumBeforePeriod(100L, 202610)).thenReturn(List.of());
        when(entryRepository.findEntryDetails(100L, 202610, 202610, "1002"))
                .thenReturn(List.<Object[]>of(detail("2026-10-05", 1, "1002", null, "300.00")));

        Map<String, Object> result = service.detailLedger(100L, "1002", 2026, 10);

        assertThat((BigDecimal) result.get("closingBalance")).isEqualByComparingTo("-300.00");
        assertThat(result.get("closingDirection")).isEqualTo("贷");
    }

    @Test
    @DisplayName("明细账科目编码为空时报错")
    void detailLedgerRejectsBlankSubject() {
        assertThatThrownBy(() -> service.detailLedger(100L, "  ", 2026, 10))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("科目编码不能为空");
    }

    // ==================== 总账 ====================

    @Test
    @DisplayName("总账把明细科目归并到一级科目并给出平衡结论")
    void generalLedgerRollsUpAndBalances() throws Exception {
        when(entryRepository.sumBeforePeriod(100L, 202610)).thenReturn(List.of());
        when(entryRepository.sumInPeriod(100L, 202610)).thenReturn(List.<Object[]>of(
                new Object[]{"1002", "银行存款", new BigDecimal("500.00"), BigDecimal.ZERO},
                new Object[]{"100201", "银行存款-工行", new BigDecimal("300.00"), BigDecimal.ZERO},
                new Object[]{"3001", "实收资本", BigDecimal.ZERO, new BigDecimal("800.00")}));

        Map<String, Object> result = service.generalLedger(100L, 2026, 10);

        assertThat((BigDecimal) result.get("totalPeriodDebit")).isEqualByComparingTo("800.00");
        assertThat((BigDecimal) result.get("totalPeriodCredit")).isEqualByComparingTo("800.00");
        assertThat((BigDecimal) result.get("totalClosingDebit")).isEqualByComparingTo("800.00");
        assertThat((BigDecimal) result.get("totalClosingCredit")).isEqualByComparingTo("800.00");
        assertThat(result.get("balanced")).isEqualTo(true);
    }

    // ==================== 序时账 ====================

    @Test
    @DisplayName("序时账汇总借贷并给出平衡结论")
    void journalTotalsBalanced() throws Exception {
        when(entryRepository.findEntryDetails(100L, 202610, 202610, null)).thenReturn(List.<Object[]>of(
                detail("2026-10-05", 1, "1002", "500.00", null),
                detail("2026-10-05", 1, "5001", null, "500.00")));

        Map<String, Object> result = service.journal(100L, 2026, 10);

        assertThat((BigDecimal) result.get("totalDebit")).isEqualByComparingTo("500.00");
        assertThat((BigDecimal) result.get("totalCredit")).isEqualByComparingTo("500.00");
        assertThat(result.get("balanced")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).get("voucherLabel")).isEqualTo("记-0001");
    }
}
