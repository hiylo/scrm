/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmAccountOpeningBalanceServiceTest.java
 * Date : 2026/11/10 18:20:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.voucher;

import org.hiylo.scrm.entity.ScrmAccountOpeningBalanceEntity;
import org.hiylo.scrm.entity.ScrmAccountSubjectEntity;
import org.hiylo.scrm.entity.ScrmCompanyEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmAccountOpeningBalanceRepository;
import org.hiylo.scrm.repository.ScrmAccountSubjectRepository;
import org.hiylo.scrm.repository.ScrmCompanyRepository;
import org.hiylo.scrm.repository.ScrmVoucherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
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
 * {@link ScrmAccountOpeningBalanceService} 单元测试。
 * <p>
 * 锁定的都是「错了要很久之后才会被发现、且发现后极难修」的口径：
 * </p>
 * <ol>
 *   <li><b>借贷必须平</b>：不平的期初会被后面每一张报表继承，账从此永久不平；</li>
 *   <li><b>只进期初/累计口径</b>：期初混进「本期发生额」会让本期借贷虚增、利润表失真；</li>
 *   <li><b>早于启用期不生效</b>：翻看接户之前的月份不能被未来的期初倒灌；</li>
 *   <li><b>启用期前有凭证要拒绝</b>：否则期初 + 历史重复计算，余额凭空变大。</li>
 * </ol>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("期初余额：平衡校验 / 合并口径 / 启用期边界")
class ScrmAccountOpeningBalanceServiceTest {

    /** 期初余额仓储 */
    @Mock
    private ScrmAccountOpeningBalanceRepository openingRepository;

    /** 科目仓储 */
    @Mock
    private ScrmAccountSubjectRepository subjectRepository;

    /** 主体仓储 */
    @Mock
    private ScrmCompanyRepository companyRepository;

    /** 凭证仓储 */
    @Mock
    private ScrmVoucherRepository voucherRepository;

    /** 财税数据范围 */
    @Mock
    private org.hiylo.scrm.service.finance.FinanceDataScope financeDataScope;

    /** 被测服务 */
    private ScrmAccountOpeningBalanceService service;

    /** 主体 ID */
    private static final Long COMPANY_ID = 100L;

    /** 启用期间 2026-01 */
    private static final int OPENING_PERIOD = 202601;

    /** 组装被测服务与标准科目表（银行存款 / 应收账款 / 实收资本 / 主营业务收入）。 */
    @BeforeEach
    void setUp() {
        service = new ScrmAccountOpeningBalanceService(openingRepository, subjectRepository,
                companyRepository, voucherRepository, financeDataScope);

        ScrmCompanyEntity company = new ScrmCompanyEntity();
        company.setId(COMPANY_ID);
        company.setCompanyName("测试公司");
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(financeDataScope.canAccessCompany(COMPANY_ID)).thenReturn(true);

        List<ScrmAccountSubjectEntity> subjects = new ArrayList<>();
        subjects.add(subject("1002", "银行存款", ScrmAccountSubjectEntity.CATEGORY_ASSET,
                ScrmAccountSubjectEntity.DIRECTION_DEBIT));
        subjects.add(subject("1122", "应收账款", ScrmAccountSubjectEntity.CATEGORY_ASSET,
                ScrmAccountSubjectEntity.DIRECTION_DEBIT));
        subjects.add(subject("4001", "实收资本", ScrmAccountSubjectEntity.CATEGORY_EQUITY,
                ScrmAccountSubjectEntity.DIRECTION_CREDIT));
        subjects.add(subject("5001", "主营业务收入", ScrmAccountSubjectEntity.CATEGORY_INCOME,
                ScrmAccountSubjectEntity.DIRECTION_CREDIT));
        when(subjectRepository.findByCompanyIdOrderBySubjectCodeAsc(COMPANY_ID)).thenReturn(subjects);

        // 默认：启用期之前没有任何有效凭证
        when(voucherRepository.countEffectiveBeforePeriod(anyLong(), any())).thenReturn(0L);
        when(openingRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(openingRepository.findByCompanyIdOrderBySubjectCodeAsc(COMPANY_ID)).thenReturn(List.of());
    }

    /** 造科目。 */
    private ScrmAccountSubjectEntity subject(String code, String name, String category,
                                             String direction) {
        ScrmAccountSubjectEntity entity = new ScrmAccountSubjectEntity();
        entity.setCompanyId(COMPANY_ID);
        entity.setSubjectCode(code);
        entity.setSubjectName(name);
        entity.setCategory(category);
        entity.setDirection(direction);
        entity.setStatus(ScrmAccountSubjectEntity.STATUS_ACTIVE);
        return entity;
    }

    /** 造一行期初余额入参。 */
    private ScrmAccountOpeningBalanceEntity row(String code, String debit, String credit) {
        ScrmAccountOpeningBalanceEntity item = new ScrmAccountOpeningBalanceEntity();
        item.setSubjectCode(code);
        item.setDebitAmount(new BigDecimal(debit));
        item.setCreditAmount(new BigDecimal(credit));
        return item;
    }

    // ==================== 保存：平衡与合法性 ====================

    @Test
    @DisplayName("借贷不平的期初被拒绝，且不写库")
    void rejectsUnbalancedOpening() {
        assertThatThrownBy(() -> service.save(COMPANY_ID, OPENING_PERIOD, List.of(
                row("1002", "1000.00", "0"),
                row("4001", "0", "900.00"))))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("期初借贷不平衡")
                .hasMessageContaining("差额 100.00");
        verify(openingRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("同一科目重复出现被拒绝（避免每行各算一半）")
    void rejectsDuplicateSubjectRows() {
        assertThatThrownBy(() -> service.save(COMPANY_ID, OPENING_PERIOD, List.of(
                row("1002", "500.00", "0"),
                row("1002", "500.00", "0"),
                row("4001", "0", "1000.00"))))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("重复出现");
    }

    @Test
    @DisplayName("负数余额被拒绝：方向用借/贷两列表达，不用负号")
    void rejectsNegativeAmount() {
        assertThatThrownBy(() -> service.save(COMPANY_ID, OPENING_PERIOD, List.of(
                row("1002", "-100.00", "0"),
                row("4001", "0", "-100.00"))))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("不能为负数");
    }

    @Test
    @DisplayName("同一行借贷同时非零被拒绝")
    void rejectsBothSidesNonZero() {
        assertThatThrownBy(() -> service.save(COMPANY_ID, OPENING_PERIOD, List.of(
                row("1002", "100.00", "100.00"),
                row("4001", "0", "0.00"))))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("不能同时非零");
    }

    @Test
    @DisplayName("不存在的科目被拒绝")
    void rejectsUnknownSubject() {
        assertThatThrownBy(() -> service.save(COMPANY_ID, OPENING_PERIOD, List.of(
                row("9999", "100.00", "0"),
                row("4001", "0", "100.00"))))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("科目不存在");
    }

    @Test
    @DisplayName("全 0 行等价于没录，被拒绝并给出可读原因")
    void rejectsAllZeroRows() {
        assertThatThrownBy(() -> service.save(COMPANY_ID, OPENING_PERIOD, List.of(
                row("1002", "0", "0"),
                row("4001", "0", "0"))))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("至少录入一行有余额的科目");
    }

    @Test
    @DisplayName("启用期非法（月份越界）被拒绝")
    void rejectsInvalidOpeningPeriod() {
        assertThatThrownBy(() -> service.save(COMPANY_ID, 202613, List.of(
                row("1002", "100.00", "0"),
                row("4001", "0", "100.00"))))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("启用期间不合法");
    }

    @Test
    @DisplayName("启用期之前已有有效凭证（含草稿）时拒绝设置期初（防重复计算）")
    void rejectsWhenHistoryExistsBeforeOpeningPeriod() {
        when(voucherRepository.countEffectiveBeforePeriod(COMPANY_ID, OPENING_PERIOD)).thenReturn(2L);

        assertThatThrownBy(() -> service.save(COMPANY_ID, OPENING_PERIOD, List.of(
                row("1002", "1000.00", "0"),
                row("4001", "0", "1000.00"))))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("之前已存在 2 张未作废凭证");
        verify(openingRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("无权访问的主体不能设置期初，且不写库")
    void rejectsInaccessibleCompany() {
        when(financeDataScope.canAccessCompany(COMPANY_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.save(COMPANY_ID, OPENING_PERIOD, List.of(
                row("1002", "100.00", "0"),
                row("4001", "0", "100.00"))))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("无权访问该主体");
        verify(openingRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("合法期初整户替换保存：先删后建，金额与启用期落库正确")
    void savesBalancedOpeningReplacingAll() {
        service.save(COMPANY_ID, OPENING_PERIOD, List.of(
                row("1002", "80000.00", "0"),
                row("1122", "20000.00", "0"),
                row("4001", "0", "100000.00")));

        verify(openingRepository).deleteByCompanyId(COMPANY_ID);
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<ScrmAccountOpeningBalanceEntity>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(openingRepository).saveAll(captor.capture());
        List<ScrmAccountOpeningBalanceEntity> saved = captor.getValue();

        assertThat(saved).hasSize(3);
        assertThat(saved).allSatisfy(e -> {
            assertThat(e.getCompanyId()).isEqualTo(COMPANY_ID);
            assertThat(e.getOpeningPeriod()).isEqualTo(OPENING_PERIOD);
        });
        // 科目名称做了快照
        assertThat(saved).anySatisfy(e -> {
            assertThat(e.getSubjectCode()).isEqualTo("1002");
            assertThat(e.getSubjectName()).isEqualTo("银行存款");
            assertThat(e.getDebitAmount()).isEqualByComparingTo("80000.00");
        });
    }

    // ==================== 合并口径 ====================

    @Test
    @DisplayName("查询期不早于启用期时，期初余额并入聚合结果（同科目金额相加）")
    void mergesOpeningIntoAggregate() {
        givenSavedOpening();

        List<Object[]> result = service.mergeInto(
                List.<Object[]>of(new Object[]{"1002", "银行存款", new BigDecimal("500.00"), BigDecimal.ZERO}),
                202610, COMPANY_ID);

        // 入参 1 行（1002）+ 期初 2 行（1002 与 4001），1002 同科目相加 → 共 2 行
        assertThat(result).hasSize(2);
        Object[] bank = find(result, "1002");
        assertThat(toDecimal(bank[2])).isEqualByComparingTo("80500.00");
        assertThat(toDecimal(bank[3])).isEqualByComparingTo("0");
        Object[] capital = find(result, "4001");
        assertThat(toDecimal(capital[3])).isEqualByComparingTo("80000.00");
    }

    @Test
    @DisplayName("查询期早于启用期时不合并：接户之前的月份应是真实发生额")
    void doesNotMergeBeforeOpeningPeriod() {
        givenSavedOpening();

        List<Object[]> result = service.mergeInto(
                List.<Object[]>of(new Object[]{"1002", "银行存款", new BigDecimal("500.00"), BigDecimal.ZERO}),
                202512, COMPANY_ID);

        assertThat(result).hasSize(1);
        Object[] bank = find(result, "1002");
        assertThat(toDecimal(bank[2])).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("启用期当期的查询要合并（会计看接户第一个月，期初必须出现）")
    void mergesOnOpeningPeriodItself() {
        givenSavedOpening();

        List<Object[]> result = service.mergeInto(List.of(), OPENING_PERIOD, COMPANY_ID);

        assertThat(result).hasSize(2);
        assertThat(toDecimal(find(result, "1002")[2])).isEqualByComparingTo("80000.00");
    }

    @Test
    @DisplayName("未设置期初时原样返回，不改动入参列表")
    void passesThroughWhenNoOpening() {
        List<Object[]> input = new ArrayList<>();
        input.add(new Object[]{"1002", "银行存款", new BigDecimal("500.00"), BigDecimal.ZERO});

        List<Object[]> result = service.mergeInto(input, 202610, COMPANY_ID);

        assertThat(result).hasSize(1);
        assertThat(input).hasSize(1);
        assertThat(toDecimal(input.get(0)[2])).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("companyId 为空时不合并（无主体上下文）")
    void doesNotMergeWithoutCompany() {
        List<Object[]> result = service.mergeInto(
                List.<Object[]>of(new Object[]{"1002", "银行存款", BigDecimal.TEN, BigDecimal.ZERO}),
                202610, null);

        assertThat(result).hasSize(1);
        assertThat(toDecimal(result.get(0)[2])).isEqualByComparingTo("10");
    }

    // ==================== 视图与清空 ====================

    @Test
    @DisplayName("录入视图列出全部在用科目，合计与平衡判定正确")
    void viewListsAllActiveSubjectsWithTotals() {
        givenSavedOpening();

        Map<String, Object> view = service.view(COMPANY_ID);

        assertThat(view.get("openingPeriod")).isEqualTo(OPENING_PERIOD);
        assertThat(view.get("balanced")).isEqualTo(true);
        assertThat((BigDecimal) view.get("totalDebit")).isEqualByComparingTo("80000.00");
        assertThat((BigDecimal) view.get("totalCredit")).isEqualByComparingTo("80000.00");
        assertThat(view.get("configured")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) view.get("items");
        // 4 个在用科目全部列出（含未设期初的应收账款），会计可直接照着上家报表填
        assertThat(items).hasSize(4);
    }

    @Test
    @DisplayName("清空期初返回删除行数")
    void clearReturnsDeletedCount() {
        when(openingRepository.deleteByCompanyId(COMPANY_ID)).thenReturn(2);

        assertThat(service.clear(COMPANY_ID)).isEqualTo(2);
    }

    // ==================== 工具 ====================

    /** 铺一份已保存的两行期初（银行存款 80000 借 / 实收资本 80000 贷）。 */
    private void givenSavedOpening() {
        ScrmAccountOpeningBalanceEntity bank = new ScrmAccountOpeningBalanceEntity();
        bank.setCompanyId(COMPANY_ID);
        bank.setOpeningPeriod(OPENING_PERIOD);
        bank.setSubjectCode("1002");
        bank.setSubjectName("银行存款");
        bank.setDebitAmount(new BigDecimal("80000.00"));
        bank.setCreditAmount(BigDecimal.ZERO);

        ScrmAccountOpeningBalanceEntity capital = new ScrmAccountOpeningBalanceEntity();
        capital.setCompanyId(COMPANY_ID);
        capital.setOpeningPeriod(OPENING_PERIOD);
        capital.setSubjectCode("4001");
        capital.setSubjectName("实收资本");
        capital.setDebitAmount(BigDecimal.ZERO);
        capital.setCreditAmount(new BigDecimal("80000.00"));

        when(openingRepository.findByCompanyIdOrderBySubjectCodeAsc(COMPANY_ID))
                .thenReturn(List.of(bank, capital));
    }

    /** 按科目编码取行。 */
    private Object[] find(List<Object[]> rows, String code) {
        return rows.stream().filter(r -> code.equals(String.valueOf(r[0]))).findFirst()
                .orElseThrow(() -> new AssertionError("聚合结果里没有科目 " + code));
    }

    /** Object → BigDecimal。 */
    private BigDecimal toDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(String.valueOf(value));
    }
}
