/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmPayrollCalculatorTest.java
 * Date : 2026/10/25 14:20:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.payroll;

import org.hiylo.scrm.config.ScrmPayrollProperties;
import org.hiylo.scrm.entity.ScrmEmployeeEntity;
import org.hiylo.scrm.entity.ScrmIitRecordEntity;
import org.hiylo.scrm.entity.ScrmSocialSecurityRecordEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ScrmPayrollCalculator} 单元测试。
 * <p>
 * 财务金额必须逐分对上，因此这里用<b>精确值断言</b>而非范围断言。
 * 覆盖两类风险最高的口径：社保基数上下限截断、个税累计预扣法跨档。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@DisplayName("薪酬计算器：社保分项 + 个税累计预扣")
class ScrmPayrollCalculatorTest {

    /** 被测计算器 */
    private ScrmPayrollCalculator calculator;

    /** 测试用员工 */
    private ScrmEmployeeEntity employee;

    /**
     * 构造按西安口径配置的计算器与一个标准员工。
     */
    @BeforeEach
    void setUp() {
        ScrmPayrollProperties properties = new ScrmPayrollProperties();
        calculator = new ScrmPayrollCalculator(properties);

        employee = new ScrmEmployeeEntity();
        employee.setId(1L);
        employee.setCompanyId(100L);
        employee.setCompanyName("测试公司");
        employee.setName("张三");
        employee.setSocialStatus(ScrmEmployeeEntity.SOCIAL_INSURED);
        employee.setSocialBase(new BigDecimal("10000"));
        employee.setSpecialDeduction(BigDecimal.ZERO);
    }

    // ==================== 社保：基数截断 ====================

    @Test
    @DisplayName("基数低于下限时按下限计缴")
    void baseBelowFloorUsesFloor() {
        assertThat(calculator.resolveBase(new BigDecimal("1000")))
                .isEqualByComparingTo(new BigDecimal("4000.00"));
    }

    @Test
    @DisplayName("基数高于上限时按上限计缴")
    void baseAboveCeilingUsesCeiling() {
        assertThat(calculator.resolveBase(new BigDecimal("90000")))
                .isEqualByComparingTo(new BigDecimal("25000.00"));
    }

    @Test
    @DisplayName("基数在区间内时原样使用")
    void baseWithinRangeKept() {
        assertThat(calculator.resolveBase(new BigDecimal("10000")))
                .isEqualByComparingTo(new BigDecimal("10000.00"));
    }

    @Test
    @DisplayName("未填基数时按下限兜底（实务不按 0 计缴）")
    void baseNullFallsBackToFloor() {
        assertThat(calculator.resolveBase(null))
                .isEqualByComparingTo(new BigDecimal("4000.00"));
    }

    // ==================== 社保：分项金额 ====================

    @Test
    @DisplayName("基数 10000 的各分项金额与合计精确匹配")
    void socialComponentsExact() {
        ScrmSocialSecurityRecordEntity record = calculator.calculateSocial(employee, 2026, 10);

        // 个人部分：养老 800 / 医疗 200 / 失业 50 / 公积金 500
        assertThat(record.getPensionPersonal()).isEqualByComparingTo("800.00");
        assertThat(record.getMedicalPersonal()).isEqualByComparingTo("200.00");
        assertThat(record.getUnemploymentPersonal()).isEqualByComparingTo("50.00");
        assertThat(record.getHousingFundPersonal()).isEqualByComparingTo("500.00");
        assertThat(record.getTotalPersonal()).isEqualByComparingTo("1550.00");

        // 单位部分：养老 1600 / 医疗 700 / 失业 50 / 工伤 40 / 生育 50 / 公积金 500
        assertThat(record.getPensionCompany()).isEqualByComparingTo("1600.00");
        assertThat(record.getMedicalCompany()).isEqualByComparingTo("700.00");
        assertThat(record.getUnemploymentCompany()).isEqualByComparingTo("50.00");
        assertThat(record.getInjuryCompany()).isEqualByComparingTo("40.00");
        assertThat(record.getMaternityCompany()).isEqualByComparingTo("50.00");
        assertThat(record.getHousingFundCompany()).isEqualByComparingTo("500.00");
        assertThat(record.getTotalCompany()).isEqualByComparingTo("2940.00");

        assertThat(record.getTotalAmount()).isEqualByComparingTo("4490.00");
    }

    @Test
    @DisplayName("未参保员工不产生任何缴费金额")
    void notInsuredHasZeroAmount() {
        employee.setSocialStatus(ScrmEmployeeEntity.SOCIAL_NOT_INSURED);
        ScrmSocialSecurityRecordEntity record = calculator.calculateSocial(employee, 2026, 10);
        assertThat(record.getTotalAmount()).isEqualByComparingTo("0.00");
        assertThat(record.getTotalPersonal()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("高基数员工的社保按上限 25000 计缴而不是原始基数")
    void highBaseCappedInSocial() {
        employee.setSocialBase(new BigDecimal("50000"));
        ScrmSocialSecurityRecordEntity record = calculator.calculateSocial(employee, 2026, 10);
        assertThat(record.getBaseAmount()).isEqualByComparingTo("25000.00");
        assertThat(record.getPensionPersonal()).isEqualByComparingTo("2000.00");
    }

    // ==================== 个税：累计预扣法 ====================

    @Test
    @DisplayName("月收入 8000 首月：应纳税所得额 1450，按 3% 缴 43.50")
    void iitFirstMonthWithinFirstBracket() {
        ScrmIitRecordEntity record = calculator.calculateIit(
                employee, 2026, 1,
                new BigDecimal("8000"), new BigDecimal("1550"), new ArrayList<>());

        // 8000 - 5000 - 1550 = 1450 → 3% = 43.50
        assertThat(record.getCumulativeTaxable()).isEqualByComparingTo("1450.00");
        assertThat(record.getTaxRate()).isEqualByComparingTo("0.03");
        assertThat(record.getCurrentTax()).isEqualByComparingTo("43.50");
    }

    @Test
    @DisplayName("月收入 6000 首月：低于 5000 减除 + 社保，应纳税所得额为 0")
    void iitIncomeBelowDeduction() {
        ScrmIitRecordEntity record = calculator.calculateIit(
                employee, 2026, 1,
                new BigDecimal("6000"), new BigDecimal("1550"), new ArrayList<>());

        // 6000 - 5000 - 1550 = -550 → 0
        assertThat(record.getCumulativeTaxable()).isEqualByComparingTo("0.00");
        assertThat(record.getCurrentTax()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("月收入 20000 首月：应纳税所得额 13450，按 3% 缴 403.5")
    void iitFirstMonthWithTaxableIncome() {
        ScrmIitRecordEntity record = calculator.calculateIit(
                employee, 2026, 1,
                new BigDecimal("20000"), new BigDecimal("1550"), new ArrayList<>());

        // 20000 - 5000 - 1550 = 13450 → 3% = 403.50
        assertThat(record.getCumulativeTaxable()).isEqualByComparingTo("13450.00");
        assertThat(record.getTaxRate()).isEqualByComparingTo("0.03");
        assertThat(record.getCumulativeTax()).isEqualByComparingTo("403.50");
        assertThat(record.getCurrentTax()).isEqualByComparingTo("403.50");
    }

    @Test
    @DisplayName("累计跨档：第 4 个月累计应纳税所得额超 36000，税率升到 10%")
    void iitCrossesBracketInFourthMonth() {
        // 历史 3 个月各 20000 收入、1550 专项扣除、税额 403.50
        List<ScrmIitRecordEntity> priors = new ArrayList<>();
        for (int month = 1; month <= 3; month++) {
            ScrmIitRecordEntity prior = new ScrmIitRecordEntity();
            prior.setPeriodMonth(month);
            prior.setIncomeAmount(new BigDecimal("20000.00"));
            prior.setBasicDeduction(new BigDecimal("5000.00"));
            prior.setSocialDeduction(new BigDecimal("1550.00"));
            prior.setSpecialDeduction(BigDecimal.ZERO);
            prior.setCurrentTax(new BigDecimal("403.50"));
            priors.add(prior);
        }

        ScrmIitRecordEntity record = calculator.calculateIit(
                employee, 2026, 4,
                new BigDecimal("20000"), new BigDecimal("1550"), priors);

        // 累计收入 80000 - 累计扣除 (4×6550=26200) = 53800 → 10% - 2520 = 2860
        assertThat(record.getCumulativeIncome()).isEqualByComparingTo("80000.00");
        assertThat(record.getCumulativeDeduction()).isEqualByComparingTo("26200.00");
        assertThat(record.getCumulativeTaxable()).isEqualByComparingTo("53800.00");
        assertThat(record.getTaxRate()).isEqualByComparingTo("0.10");
        assertThat(record.getCumulativeTax()).isEqualByComparingTo("2860.00");
        // 本期 = 2860 - 已扣 3×403.50=1210.50 = 1649.50
        assertThat(record.getCurrentTax()).isEqualByComparingTo("1649.50");
    }

    @Test
    @DisplayName("专项附加扣除参与累计，降低应纳税所得额")
    void iitSpecialDeductionReducesTax() {
        employee.setSpecialDeduction(new BigDecimal("2000"));
        ScrmIitRecordEntity record = calculator.calculateIit(
                employee, 2026, 1,
                new BigDecimal("20000"), new BigDecimal("1550"), new ArrayList<>());

        // 20000 - 5000 - 1550 - 2000 = 11450 → 3% = 343.50
        assertThat(record.getCumulativeTaxable()).isEqualByComparingTo("11450.00");
        assertThat(record.getCurrentTax()).isEqualByComparingTo("343.50");
    }

    @Test
    @DisplayName("收入低于扣除项时税额为 0，不产生负数")
    void iitNoNegativeTax() {
        ScrmIitRecordEntity record = calculator.calculateIit(
                employee, 2026, 1,
                new BigDecimal("3000"), new BigDecimal("1550"), new ArrayList<>());

        assertThat(record.getCumulativeTaxable()).isEqualByComparingTo("0.00");
        assertThat(record.getCurrentTax()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("去年历史不参与本年累计（只累加同年度前面的月份）")
    void iitIgnoresOtherMonthsOfDifferentYear() {
        List<ScrmIitRecordEntity> priors = new ArrayList<>();
        ScrmIitRecordEntity prior = new ScrmIitRecordEntity();
        prior.setPeriodMonth(1);
        prior.setIncomeAmount(new BigDecimal("50000.00"));
        prior.setBasicDeduction(new BigDecimal("5000.00"));
        prior.setSocialDeduction(BigDecimal.ZERO);
        prior.setSpecialDeduction(BigDecimal.ZERO);
        prior.setCurrentTax(new BigDecimal("1000.00"));
        priors.add(prior);

        // 第 1 个月不应把「同月」历史算进累计
        ScrmIitRecordEntity record = calculator.calculateIit(
                employee, 2026, 1,
                new BigDecimal("20000"), new BigDecimal("1550"), priors);

        assertThat(record.getCumulativeTaxable()).isEqualByComparingTo("13450.00");
        assertThat(record.getCurrentTax()).isEqualByComparingTo("403.50");
    }
}
