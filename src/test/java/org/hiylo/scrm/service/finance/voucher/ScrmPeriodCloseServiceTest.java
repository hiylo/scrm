/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmPeriodCloseServiceTest.java
 * Date : 2026/10/25 22:40:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.voucher;

import org.hiylo.scrm.entity.ScrmAccountPeriodEntity;
import org.hiylo.scrm.entity.ScrmAccountSubjectEntity;
import org.hiylo.scrm.entity.ScrmCompanyEntity;
import org.hiylo.scrm.entity.ScrmVoucherEntity;
import org.hiylo.scrm.entity.ScrmVoucherEntryEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmAccountPeriodRepository;
import org.hiylo.scrm.repository.ScrmAccountSubjectRepository;
import org.hiylo.scrm.repository.ScrmCompanyRepository;
import org.hiylo.scrm.repository.ScrmVoucherEntryRepository;
import org.hiylo.scrm.repository.ScrmVoucherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ScrmPeriodCloseService} 单元测试（期末结转 / 封账）。
 * <p>
 * 期末结转一旦算错，本年利润与利润表都会错，而且错在"权益"上很难被发现。
 * 因此这里对结转分录的<b>科目、方向、金额</b>逐条断言，而不只看是否成功。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("期末结账：结转损益 / 封账 / 反结账")
class ScrmPeriodCloseServiceTest {

    /** 期间仓储 */
    @Mock
    private ScrmAccountPeriodRepository periodRepository;

    /** 科目仓储 */
    @Mock
    private ScrmAccountSubjectRepository subjectRepository;

    /** 凭证仓储 */
    @Mock
    private ScrmVoucherRepository voucherRepository;

    /** 分录仓储 */
    @Mock
    private ScrmVoucherEntryRepository entryRepository;

    /** 主体仓储 */
    @Mock
    private ScrmCompanyRepository companyRepository;

    /** 被测服务 */
    private ScrmPeriodCloseService service;

    /** 捕获保存的凭证 */
    private final ScrmVoucherEntity[] savedVoucher = new ScrmVoucherEntity[1];

    /** 捕获保存的分录 */
    private List<ScrmVoucherEntryEntity> savedEntries;

    /**
     * 取捕获到的分录（带类型，避免裸 List 泛型擦除导致的类型错误）。
     *
     * @return 保存时传入的分录列表
     */
    private List<ScrmVoucherEntryEntity> capturedEntries() {
        return savedEntries == null ? List.of() : savedEntries;
    }

    /**
     * 组装服务，准备「银行存款 / 主营业务收入 / 管理费用 / 本年利润」四个科目。
     */
    @BeforeEach
    void setUp() {
        service = new ScrmPeriodCloseService(periodRepository, subjectRepository,
                voucherRepository, entryRepository, companyRepository);

        ScrmCompanyEntity company = new ScrmCompanyEntity();
        company.setId(100L);
        company.setCompanyName("测试公司");
        when(companyRepository.findById(100L)).thenReturn(Optional.of(company));

        when(subjectRepository.findByCompanyIdOrderBySubjectCodeAsc(100L)).thenReturn(List.of(
                subject("1002", "银行存款", ScrmAccountSubjectEntity.CATEGORY_ASSET,
                        ScrmAccountSubjectEntity.DIRECTION_DEBIT),
                subject("3103", "本年利润", ScrmAccountSubjectEntity.CATEGORY_EQUITY,
                        ScrmAccountSubjectEntity.DIRECTION_CREDIT),
                subject("5001", "主营业务收入", ScrmAccountSubjectEntity.CATEGORY_INCOME,
                        ScrmAccountSubjectEntity.DIRECTION_CREDIT),
                subject("5602", "管理费用", ScrmAccountSubjectEntity.CATEGORY_EXPENSE,
                        ScrmAccountSubjectEntity.DIRECTION_DEBIT)));

        when(periodRepository.findByCompanyIdAndPeriodYearAndPeriodMonth(anyLong(), any(), any()))
                .thenReturn(Optional.empty());
        when(voucherRepository.countByCompanyIdAndPeriodYearAndPeriodMonthAndStatus(
                anyLong(), any(), any(), anyString())).thenReturn(0L);
        when(voucherRepository.findMaxVoucherNo(anyLong(), any(), any(), anyString())).thenReturn(null);
        when(voucherRepository.save(any())).thenAnswer(inv -> {
            ScrmVoucherEntity entity = inv.getArgument(0);
            entity.setId(9L);
            savedVoucher[0] = entity;
            return entity;
        });
        when(entryRepository.saveAll(any())).thenAnswer(inv -> {
            Object arg = inv.getArgument(0);
            if (arg instanceof List<?> list) {
                @SuppressWarnings("unchecked")
                List<ScrmVoucherEntryEntity> typed = (List<ScrmVoucherEntryEntity>) list;
                savedEntries = typed;
            }
            return arg;
        });
        when(periodRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    /** 构造科目。 */
    private ScrmAccountSubjectEntity subject(String code, String name, String category, String direction) {
        ScrmAccountSubjectEntity entity = new ScrmAccountSubjectEntity();
        entity.setSubjectCode(code);
        entity.setSubjectName(name);
        entity.setCategory(category);
        entity.setDirection(direction);
        entity.setStatus(ScrmAccountSubjectEntity.STATUS_ACTIVE);
        return entity;
    }

    // ==================== 结转金额与方向 ====================

    @Test
    @DisplayName("收入 500 / 费用 200 → 结转凭证借收入 500、贷费用 200、贷本年利润 300")
    void closeTransfersProfitCorrectly() throws Exception {
        when(entryRepository.sumUpTo(100L, 202610)).thenReturn(List.<Object[]>of(
                new Object[]{"5001", "主营业务收入", BigDecimal.ZERO, new BigDecimal("500.00")},
                new Object[]{"5602", "管理费用", new BigDecimal("200.00"), BigDecimal.ZERO}));

        Map<String, Object> result = service.closePeriod(100L, 2026, 10);

        ScrmVoucherEntity voucher = savedVoucher[0];
        assertThat(voucher.getStatus()).isEqualTo(ScrmVoucherEntity.STATUS_POSTED);
        assertThat(voucher.getIsClosing()).isTrue();
        assertThat(voucher.getVoucherWord()).isEqualTo("转");
        assertThat(voucher.getTotalDebit()).isEqualByComparingTo("500.00");
        assertThat(voucher.getTotalCredit()).isEqualByComparingTo("500.00");

        List<ScrmVoucherEntryEntity> entries = capturedEntries();
        assertThat(entries).hasSize(3);

        // 收入：借方冲平
        ScrmVoucherEntryEntity income = entries.stream()
                .filter(e -> "5001".equals(e.getSubjectCode())).findFirst().orElseThrow();
        assertThat(income.getDebitAmount()).isEqualByComparingTo("500.00");
        assertThat(income.getCreditAmount()).isEqualByComparingTo("0.00");

        // 费用：贷方冲平
        ScrmVoucherEntryEntity expense = entries.stream()
                .filter(e -> "5602".equals(e.getSubjectCode())).findFirst().orElseThrow();
        assertThat(expense.getDebitAmount()).isEqualByComparingTo("0.00");
        assertThat(expense.getCreditAmount()).isEqualByComparingTo("200.00");

        // 本年利润：贷 300（盈利）
        ScrmVoucherEntryEntity profit = entries.stream()
                .filter(e -> "3103".equals(e.getSubjectCode())).findFirst().orElseThrow();
        assertThat(profit.getCreditAmount()).isEqualByComparingTo("300.00");
        assertThat(profit.getDebitAmount()).isEqualByComparingTo("0.00");

        assertThat((BigDecimal) result.get("profit")).isEqualByComparingTo("300.00");
        assertThat(result.get("status")).isEqualTo(ScrmAccountPeriodEntity.STATUS_CLOSED);
    }

    @Test
    @DisplayName("费用大于收入（亏损）时，本年利润在借方")
    void closeHandlesLoss() throws Exception {
        when(entryRepository.sumUpTo(100L, 202610)).thenReturn(List.<Object[]>of(
                new Object[]{"5001", "主营业务收入", BigDecimal.ZERO, new BigDecimal("100.00")},
                new Object[]{"5602", "管理费用", new BigDecimal("400.00"), BigDecimal.ZERO}));

        Map<String, Object> result = service.closePeriod(100L, 2026, 10);

        ScrmVoucherEntity voucher = savedVoucher[0];
        assertThat(voucher.getTotalDebit()).isEqualByComparingTo("400.00");
        assertThat(voucher.getTotalCredit()).isEqualByComparingTo("400.00");

        ScrmVoucherEntryEntity profit = capturedEntries().stream()
                .filter(e -> "3103".equals(e.getSubjectCode())).findFirst().orElseThrow();
        assertThat(profit.getDebitAmount()).isEqualByComparingTo("300.00");
        assertThat(profit.getCreditAmount()).isEqualByComparingTo("0.00");

        // 亏损为负数，前端可直接显示为负
        assertThat((BigDecimal) result.get("profit")).isEqualByComparingTo("-300.00");
    }

    @Test
    @DisplayName("本期无损益发生额时不生成结转凭证，但仍封账")
    void closeWithoutProfitSkipsVoucher() throws Exception {
        when(entryRepository.sumUpTo(100L, 202610)).thenReturn(List.of());

        Map<String, Object> result = service.closePeriod(100L, 2026, 10);

        verify(voucherRepository, never()).save(any());
        assertThat(result.get("closingVoucherId")).isNull();
        assertThat(result.get("status")).isEqualTo(ScrmAccountPeriodEntity.STATUS_CLOSED);

        ArgumentCaptor<ScrmAccountPeriodEntity> captor =
                ArgumentCaptor.forClass(ScrmAccountPeriodEntity.class);
        verify(periodRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ScrmAccountPeriodEntity.STATUS_CLOSED);
    }

    // ==================== 封账保护 ====================

    @Test
    @DisplayName("存在未记账草稿时拒绝结账")
    void rejectsCloseWhenDraftExists() {
        when(voucherRepository.countByCompanyIdAndPeriodYearAndPeriodMonthAndStatus(
                100L, 2026, 10, ScrmVoucherEntity.STATUS_DRAFT)).thenReturn(2L);

        assertThatThrownBy(() -> service.closePeriod(100L, 2026, 10))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("草稿凭证未记账");
    }

    @Test
    @DisplayName("重复结账被拒绝（幂等保护）")
    void rejectsDoubleClose() {
        ScrmAccountPeriodEntity closed = new ScrmAccountPeriodEntity();
        closed.setStatus(ScrmAccountPeriodEntity.STATUS_CLOSED);
        when(periodRepository.findByCompanyIdAndPeriodYearAndPeriodMonth(100L, 2026, 10))
                .thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> service.closePeriod(100L, 2026, 10))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("已结账");
    }

    @Test
    @DisplayName("已结账期间 isClosed 返回 true（凭证服务据此拦截）")
    void isClosedReturnsTrueForClosedPeriod() {
        ScrmAccountPeriodEntity closed = new ScrmAccountPeriodEntity();
        closed.setStatus(ScrmAccountPeriodEntity.STATUS_CLOSED);
        when(periodRepository.findByCompanyIdAndPeriodYearAndPeriodMonth(100L, 2026, 10))
                .thenReturn(Optional.of(closed));

        assertThat(service.isClosed(100L, 2026, 10)).isTrue();
        // 未建期间记录视为 OPEN
        assertThat(service.isClosed(100L, 2026, 11)).isFalse();
    }

    // ==================== 反结账 ====================

    @Test
    @DisplayName("反结账作废结转凭证并恢复 OPEN，不删除历史")
    void reopenVoidsClosingVoucher() throws Exception {
        ScrmAccountPeriodEntity closed = new ScrmAccountPeriodEntity();
        closed.setStatus(ScrmAccountPeriodEntity.STATUS_CLOSED);
        closed.setClosingVoucherId(9L);
        when(periodRepository.findByCompanyIdAndPeriodYearAndPeriodMonth(100L, 2026, 10))
                .thenReturn(Optional.of(closed));

        ScrmVoucherEntity closingVoucher = new ScrmVoucherEntity();
        closingVoucher.setId(9L);
        closingVoucher.setStatus(ScrmVoucherEntity.STATUS_POSTED);
        closingVoucher.setRemark("期末结转损益 2026-10");
        when(voucherRepository.findById(9L)).thenReturn(Optional.of(closingVoucher));

        Map<String, Object> result = service.reopenPeriod(100L, 2026, 10);

        // 结转凭证被作废而非删除
        assertThat(closingVoucher.getStatus()).isEqualTo(ScrmVoucherEntity.STATUS_VOID);
        assertThat(closingVoucher.getRemark()).contains("反结账作废");
        assertThat(closed.getStatus()).isEqualTo(ScrmAccountPeriodEntity.STATUS_OPEN);
        assertThat(result.get("status")).isEqualTo(ScrmAccountPeriodEntity.STATUS_OPEN);
    }

    @Test
    @DisplayName("未结账期间反结账被拒绝")
    void rejectsReopenWhenNotClosed() {
        when(periodRepository.findByCompanyIdAndPeriodYearAndPeriodMonth(anyLong(), any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reopenPeriod(100L, 2026, 10))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("未结账");
    }

    @Test
    @DisplayName("缺少本年利润科目时明确报错，而不是静默结账")
    void failsWhenProfitSubjectMissing() {
        when(subjectRepository.findByCompanyIdOrderBySubjectCodeAsc(100L)).thenReturn(List.of(
                subject("5001", "主营业务收入", ScrmAccountSubjectEntity.CATEGORY_INCOME,
                        ScrmAccountSubjectEntity.DIRECTION_CREDIT)));
        when(entryRepository.sumUpTo(100L, 202610)).thenReturn(List.<Object[]>of(
                new Object[]{"5001", "主营业务收入", BigDecimal.ZERO, new BigDecimal("100.00")}));

        assertThatThrownBy(() -> service.closePeriod(100L, 2026, 10))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("本年利润");
    }
}
