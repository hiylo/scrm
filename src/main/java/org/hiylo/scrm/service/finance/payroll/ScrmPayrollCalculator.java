/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmPayrollCalculator.java
 * Date : 2026/10/25 13:20:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.payroll;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.config.ScrmPayrollProperties;
import org.hiylo.scrm.entity.ScrmEmployeeEntity;
import org.hiylo.scrm.entity.ScrmIitRecordEntity;
import org.hiylo.scrm.entity.ScrmSocialSecurityRecordEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 薪酬计算器：社保分项 + 工资薪金个税（累计预扣法）。
 * <p>
 * <b>关键约束</b>：金额一律由本类精确计算，禁止任何 LLM 或外部服务生成数值。
 * 财务金额差一分钱都是事故，全部使用 {@link BigDecimal}、
 * 中途 {@code scale=4} 保留精度、只在最终结果 {@code scale=2} 四舍五入。
 * </p>
 * <p>
 * <b>个税口径</b>（《个人所得税扣缴申报管理办法（试行）》累计预扣法）：
 * </p>
 * <pre>
 *   累计应纳税所得额 = 累计收入 − 累计减除费用 − 累计专项扣除 − 累计专项附加扣除
 *   累计应纳税额     = 累计应纳税所得额 × 适用税率 − 速算扣除数
 *   本期应预扣       = 累计应纳税额 − 累计已预扣税额
 * </pre>
 * <p>
 * 累计应纳税所得额 ≤ 0 时本期税额为 0（不产生负数退税，年度汇算时统一处理）。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScrmPayrollCalculator {

    /** 中间计算精度（避免分项四舍五入累积误差） */
    private static final int CALC_SCALE = 4;

    /** 最终金额精度 */
    private static final int MONEY_SCALE = 2;

    /** 个税年度税率表：{累计应纳税所得额上限, 税率, 速算扣除数}，上限 null 表示无上限 */
    private static final Object[][] IIT_BRACKETS = {
            {new BigDecimal("36000"), new BigDecimal("0.03"), new BigDecimal("0")},
            {new BigDecimal("144000"), new BigDecimal("0.10"), new BigDecimal("2520")},
            {new BigDecimal("300000"), new BigDecimal("0.20"), new BigDecimal("16920")},
            {new BigDecimal("420000"), new BigDecimal("0.25"), new BigDecimal("31920")},
            {new BigDecimal("660000"), new BigDecimal("0.30"), new BigDecimal("52920")},
            {new BigDecimal("960000"), new BigDecimal("0.35"), new BigDecimal("85920")},
            {null, new BigDecimal("0.45"), new BigDecimal("181920")},
    };

    /** 薪酬域配置（费率与基数上下限，各地可配） */
    private final ScrmPayrollProperties properties;

    /**
     * 按员工档案计算某月社保分项。
     *
     * @param employee    员工档案
     * @param periodYear  年
     * @param periodMonth 月
     * @return 已填好分项与合计的社保明细（未持久化）
     */
    public ScrmSocialSecurityRecordEntity calculateSocial(ScrmEmployeeEntity employee,
                                                          int periodYear, int periodMonth) {
        ScrmSocialSecurityRecordEntity record = new ScrmSocialSecurityRecordEntity();
        record.setCompanyId(employee.getCompanyId());
        record.setCompanyName(employee.getCompanyName());
        record.setEmployeeId(employee.getId());
        record.setEmployeeName(employee.getName());
        record.setPeriodYear(periodYear);
        record.setPeriodMonth(periodMonth);
        record.setStatus(ScrmSocialSecurityRecordEntity.STATUS_PENDING);
        record.setCreatedBy("SYSTEM");

        // 未参保员工不产生缴费明细
        if (!ScrmEmployeeEntity.SOCIAL_INSURED.equals(employee.getSocialStatus())) {
            record.setBaseAmount(BigDecimal.ZERO);
            record.recalculateTotals();
            return record;
        }

        BigDecimal base = resolveBase(employee.getSocialBase());
        record.setBaseAmount(base);

        BigDecimal fundBase = resolveBase(employee.getHousingFundBase() != null
                ? employee.getHousingFundBase() : base);

        record.setPensionPersonal(rate(base, properties.getPensionPersonalRate()));
        record.setPensionCompany(rate(base, properties.getPensionCompanyRate()));
        record.setMedicalPersonal(rate(base, properties.getMedicalPersonalRate()));
        record.setMedicalCompany(rate(base, properties.getMedicalCompanyRate()));
        record.setUnemploymentPersonal(rate(base, properties.getUnemploymentPersonalRate()));
        record.setUnemploymentCompany(rate(base, properties.getUnemploymentCompanyRate()));
        record.setInjuryCompany(rate(base, properties.getInjuryCompanyRate()));
        record.setMaternityCompany(rate(base, properties.getMaternityCompanyRate()));
        record.setHousingFundPersonal(rate(fundBase, properties.getHousingFundPersonalRate()));
        record.setHousingFundCompany(rate(fundBase, properties.getHousingFundCompanyRate()));

        record.recalculateTotals();
        return record;
    }

    /**
     * 按员工档案 + 当年历史累计计算某月个税。
     *
     * @param employee        员工档案
     * @param periodYear      年
     * @param periodMonth     月（1-12）
     * @param monthlyIncome   本期税前收入
     * @param socialDeduction 本期专项扣除（社保 + 公积金个人部分），通常取自社保明细
     * @param priorRecords    本年度此前各月的个税记录（用于取累计已预扣）
     * @return 已填好各字段的个税明细（未持久化）
     */
    public ScrmIitRecordEntity calculateIit(ScrmEmployeeEntity employee,
                                            int periodYear, int periodMonth,
                                            BigDecimal monthlyIncome,
                                            BigDecimal socialDeduction,
                                            List<ScrmIitRecordEntity> priorRecords) {
        ScrmIitRecordEntity record = new ScrmIitRecordEntity();
        record.setCompanyId(employee.getCompanyId());
        record.setCompanyName(employee.getCompanyName());
        record.setEmployeeId(employee.getId());
        record.setEmployeeName(employee.getName());
        record.setPeriodYear(periodYear);
        record.setPeriodMonth(periodMonth);
        record.setStatus(ScrmIitRecordEntity.STATUS_PENDING);
        record.setCreatedBy("SYSTEM");

        BigDecimal income = nz(monthlyIncome);
        BigDecimal basicDeduction = nz(properties.getIitBasicDeduction());
        BigDecimal social = nz(socialDeduction);
        BigDecimal special = nz(employee.getSpecialDeduction());

        // 本期字段
        record.setIncomeAmount(income.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        record.setBasicDeduction(basicDeduction.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        record.setSocialDeduction(social.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        record.setSpecialDeduction(special.setScale(MONEY_SCALE, RoundingMode.HALF_UP));

        // 历史累计（只累计本年度 1 月至上月）
        BigDecimal cumulativeIncome = income;
        BigDecimal cumulativeDeduction = basicDeduction.add(social).add(special);
        BigDecimal paidTax = BigDecimal.ZERO;
        if (priorRecords != null) {
            for (ScrmIitRecordEntity prior : priorRecords) {
                if (prior.getPeriodMonth() == null || prior.getPeriodMonth() >= periodMonth) {
                    continue;
                }
                cumulativeIncome = cumulativeIncome.add(nz(prior.getIncomeAmount()));
                cumulativeDeduction = cumulativeDeduction
                        .add(nz(prior.getBasicDeduction()))
                        .add(nz(prior.getSocialDeduction()))
                        .add(nz(prior.getSpecialDeduction()));
                paidTax = paidTax.add(nz(prior.getCurrentTax()));
            }
        }

        BigDecimal cumulativeTaxable = cumulativeIncome.subtract(cumulativeDeduction)
                .max(BigDecimal.ZERO);

        // 按年度税率表定位适用税率与速算扣除数（统一走 resolveBracket，避免口径分叉）
        BigDecimal[] bracket = resolveBracket(cumulativeTaxable);
        BigDecimal taxRate = bracket[0];
        BigDecimal quickDeduction = bracket[1];

        BigDecimal cumulativeTax = cumulativeTaxable.multiply(taxRate)
                .subtract(quickDeduction)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP)
                .max(BigDecimal.ZERO);

        BigDecimal currentTax = cumulativeTax.subtract(paidTax)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        record.setCumulativeIncome(cumulativeIncome.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        record.setCumulativeDeduction(cumulativeDeduction.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        record.setCumulativeTaxable(cumulativeTaxable.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        record.setTaxRate(taxRate);
        record.setQuickDeduction(quickDeduction);
        record.setCumulativeTax(cumulativeTax);
        record.setPaidTax(paidTax.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        record.setCurrentTax(currentTax);
        return record;
    }

    /**
     * 按年度税率表定位适用税率与速算扣除数。
     * <p>
     * 服务层重算累计链时也走这里，保证「算新月份」和「重算历史」用的是同一份口径，
     * 不至于两处各写一份税率表而在跨档处对不上。
     * </p>
     *
     * @param cumulativeTaxable 累计应纳税所得额
     * @return [税率, 速算扣除数]
     */
    public BigDecimal[] resolveBracket(BigDecimal cumulativeTaxable) {
        BigDecimal taxable = nz(cumulativeTaxable);
        for (Object[] bracket : IIT_BRACKETS) {
            BigDecimal ceiling = (BigDecimal) bracket[0];
            if (ceiling == null || taxable.compareTo(ceiling) <= 0) {
                return new BigDecimal[]{(BigDecimal) bracket[1], (BigDecimal) bracket[2]};
            }
        }
        // 理论上不可达（最后一档无上限）
        Object[] last = IIT_BRACKETS[IIT_BRACKETS.length - 1];
        return new BigDecimal[]{(BigDecimal) last[1], (BigDecimal) last[2]};
    }

    /**
     * 社保基数按上下限截断。
     *
     * @param raw 原始基数（可空）
     * @return 实际缴费基数
     */
    public BigDecimal resolveBase(BigDecimal raw) {
        BigDecimal base = nz(raw);
        if (base.compareTo(BigDecimal.ZERO) <= 0) {
            // 未填基数的按当地下限计缴（实务中不低于下限）
            return nz(properties.getBaseFloor()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal floor = nz(properties.getBaseFloor());
        BigDecimal ceiling = nz(properties.getBaseCeiling());
        if (base.compareTo(floor) < 0) {
            base = floor;
        }
        if (ceiling.compareTo(BigDecimal.ZERO) > 0 && base.compareTo(ceiling) > 0) {
            base = ceiling;
        }
        return base.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 基数 × 费率，保留两位小数。
     *
     * @param base 基数
     * @param rate 费率
     * @return 金额
     */
    private BigDecimal rate(BigDecimal base, BigDecimal rate) {
        return nz(base).multiply(nz(rate))
                .setScale(CALC_SCALE, RoundingMode.HALF_UP)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** null 安全取零。 */
    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
