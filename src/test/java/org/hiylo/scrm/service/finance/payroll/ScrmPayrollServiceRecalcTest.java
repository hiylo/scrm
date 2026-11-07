/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmPayrollServiceRecalcTest.java
 * Date : 2026/10/25 15:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.payroll;

import org.hiylo.scrm.config.ScrmPayrollProperties;
import org.hiylo.scrm.entity.ScrmEmployeeEntity;
import org.hiylo.scrm.entity.ScrmIitRecordEntity;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * {@link ScrmPayrollService#recalculateIitChain} 单元测试。
 * <p>
 * 锁定的核心语义：<b>补齐历史月份后，后续月份的累计税额必须被修正</b>。
 * 这是累计预扣法最容易出错的地方——先做 10 月、再补 8 月，
 * 若不重算，9/10 月的累计应纳税所得额会永久少算。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("薪酬服务：个税累计链重算")
class ScrmPayrollServiceRecalcTest {

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
    /** 财税数据范围（测试默认不限制：resolveAccessibleCompanyIds 返回 null） */
    @Mock
    private org.hiylo.scrm.service.finance.FinanceDataScope financeDataScope;


    /** 被测服务 */
    private ScrmPayrollService service;

    /**
     * 组装被测服务（计算器用真实实现，保证税率口径一致）。
     */
    @BeforeEach
    void setUp() {
        ScrmPayrollCalculator calculator = new ScrmPayrollCalculator(new ScrmPayrollProperties());
        service = new ScrmPayrollService(employeeRepository, socialRepository,
                iitRepository, companyRepository, calculator, new ScrmPayrollProperties(), financeDataScope);
    }

    /**
     * 构造一个月度个税记录（PENDING，金额放在 append 之后由重算写入）。
     *
     * @param month  月份
     * @param income 本期收入
     * @param social 本期专项扣除
     * @param tax    当前存的本期税额（重算前）
     * @return 记录
     */
    private ScrmIitRecordEntity record(int month, String income, String social, String tax) {
        ScrmIitRecordEntity entity = new ScrmIitRecordEntity();
        entity.setPeriodMonth(month);
        entity.setIncomeAmount(new BigDecimal(income));
        entity.setBasicDeduction(new BigDecimal("5000"));
        entity.setSocialDeduction(new BigDecimal(social));
        entity.setSpecialDeduction(BigDecimal.ZERO);
        entity.setCurrentTax(new BigDecimal(tax));
        entity.setStatus(ScrmIitRecordEntity.STATUS_PENDING);
        return entity;
    }

    @Test
    @DisplayName("补齐 8 月后，9/10 月的累计应纳税所得额与税额被修正")
    void recalculatesFollowingMonths() {
        ScrmEmployeeEntity employee = new ScrmEmployeeEntity();
        employee.setId(1L);
        employee.setCompanyId(100L);
        employee.setName("张三");
        when(employeeRepository.findByCompanyIdOrderByHireDateAsc(100L))
                .thenReturn(List.of(employee));

        // 模拟：先做了 9/10 月（各 20000，税额按只看当月算成 403.50），后补 8 月
        ScrmIitRecordEntity aug = record(8, "20000", "1550", "0");
        ScrmIitRecordEntity sep = record(9, "20000", "1550", "403.50");
        ScrmIitRecordEntity oct = record(10, "20000", "1550", "403.50");
        when(iitRepository.findByEmployeeIdAndPeriodYearOrderByPeriodMonthAsc(anyLong(), any()))
                .thenReturn(new ArrayList<>(List.of(aug, sep, oct)));

        // 从 8 月起重算（补齐 8 月）
        Map<String, Object> result = service.recalculateIitChain(100L, 2026, 8);

        // 8 月：累计应税 13450 → 3% = 403.50
        assertThat(aug.getCumulativeTaxable()).isEqualByComparingTo("13450.00");
        assertThat(aug.getCurrentTax()).isEqualByComparingTo("403.50");
        // 9 月：累计应税 26900 → 3% = 807.00，本期 807 - 403.50 = 403.50
        assertThat(sep.getCumulativeTaxable()).isEqualByComparingTo("26900.00");
        assertThat(sep.getCurrentTax()).isEqualByComparingTo("403.50");
        // 10 月：累计应税 40350 → 超 36000，10% - 2520 = 1515，本期 1515 - 807 = 708
        assertThat(oct.getCumulativeTaxable()).isEqualByComparingTo("40350.00");
        assertThat(oct.getTaxRate()).isEqualByComparingTo("0.10");
        assertThat(oct.getCumulativeTax()).isEqualByComparingTo("1515.00");
        assertThat(oct.getCurrentTax()).isEqualByComparingTo("708.00");

        assertThat(result.get("recalculated")).isEqualTo(3);
        assertThat(result.get("skippedLocked")).isEqualTo(0);
    }

    @Test
    @DisplayName("已申报的月份金额不被篡改，只跳过并计数")
    void doesNotTouchDeclaredMonths() {
        ScrmEmployeeEntity employee = new ScrmEmployeeEntity();
        employee.setId(1L);
        employee.setCompanyId(100L);
        when(employeeRepository.findByCompanyIdOrderByHireDateAsc(100L))
                .thenReturn(List.of(employee));

        ScrmIitRecordEntity aug = record(8, "20000", "1550", "0");
        ScrmIitRecordEntity sep = record(9, "20000", "1550", "403.50");
        sep.setStatus(ScrmIitRecordEntity.STATUS_DECLARED);
        when(iitRepository.findByEmployeeIdAndPeriodYearOrderByPeriodMonthAsc(anyLong(), any()))
                .thenReturn(new ArrayList<>(List.of(aug, sep)));

        Map<String, Object> result = service.recalculateIitChain(100L, 2026, 8);

        // 8 月被重算
        assertThat(aug.getCurrentTax()).isEqualByComparingTo("403.50");
        // 9 月已申报，金额原样保留
        assertThat(sep.getCurrentTax()).isEqualByComparingTo("403.50");
        assertThat(sep.getStatus()).isEqualTo(ScrmIitRecordEntity.STATUS_DECLARED);
        assertThat(result.get("recalculated")).isEqualTo(1);
        assertThat(result.get("skippedLocked")).isEqualTo(1);
    }

    @Test
    @DisplayName("只重算 fromMonth 及以后，之前的月份不动")
    void onlyRecalculatesFromGivenMonth() {
        ScrmEmployeeEntity employee = new ScrmEmployeeEntity();
        employee.setId(1L);
        employee.setCompanyId(100L);
        when(employeeRepository.findByCompanyIdOrderByHireDateAsc(100L))
                .thenReturn(List.of(employee));

        ScrmIitRecordEntity aug = record(8, "20000", "1550", "999.99");
        ScrmIitRecordEntity sep = record(9, "20000", "1550", "403.50");
        when(iitRepository.findByEmployeeIdAndPeriodYearOrderByPeriodMonthAsc(anyLong(), any()))
                .thenReturn(new ArrayList<>(List.of(aug, sep)));

        service.recalculateIitChain(100L, 2026, 9);

        // 8 月不在重算范围，金额保持原值（即便它是错的）
        assertThat(aug.getCurrentTax()).isEqualByComparingTo("999.99");
        // 9 月参与重算，且 8 月的税额被计入累计已预扣
        assertThat(sep.getCumulativeTaxable()).isEqualByComparingTo("26900.00");
    }
}
