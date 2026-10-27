/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmPayrollProperties.java
 * Date : 2026/10/25 13:10:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 薪酬域（社保 / 个税）配置。
 * <p>
 * 社保费率与缴费基数上下限<b>各地不同且每年调整</b>，因此全部做成配置项，
 * 不硬编码在计算逻辑里——西安 2026 年的比例明年可能就变了。
 * 个税税率表属全国统一，直接写在服务层常量里。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "scrm.payroll")
public class ScrmPayrollProperties {

    /** 社保缴费基数下限（低于按下限计缴） */
    private BigDecimal baseFloor = new BigDecimal("4000");

    /** 社保缴费基数上限（高于按上限计缴） */
    private BigDecimal baseCeiling = new BigDecimal("25000");

    /** 养老保险个人比例（默认 8%） */
    private BigDecimal pensionPersonalRate = new BigDecimal("0.08");
    /** 养老保险单位比例（默认 16%） */
    private BigDecimal pensionCompanyRate = new BigDecimal("0.16");

    /** 医疗保险个人比例（默认 2%） */
    private BigDecimal medicalPersonalRate = new BigDecimal("0.02");
    /** 医疗保险单位比例（默认 7%） */
    private BigDecimal medicalCompanyRate = new BigDecimal("0.07");

    /** 失业保险个人比例（默认 0.5%） */
    private BigDecimal unemploymentPersonalRate = new BigDecimal("0.005");
    /** 失业保险单位比例（默认 0.5%） */
    private BigDecimal unemploymentCompanyRate = new BigDecimal("0.005");

    /** 工伤保险单位比例（默认 0.4%，仅单位） */
    private BigDecimal injuryCompanyRate = new BigDecimal("0.004");

    /** 生育保险单位比例（默认 0.5%，仅单位） */
    private BigDecimal maternityCompanyRate = new BigDecimal("0.005");

    /** 住房公积金个人比例（默认 5%） */
    private BigDecimal housingFundPersonalRate = new BigDecimal("0.05");
    /** 住房公积金单位比例（默认 5%） */
    private BigDecimal housingFundCompanyRate = new BigDecimal("0.05");

    /** 个税基本减除费用（元/月，全国统一 5000） */
    private BigDecimal iitBasicDeduction = new BigDecimal("5000");

    /** 单次批量生成的员工数上限（防止误操作一次性生成过大批量） */
    private Integer maxGenerateBatch = 500;
}
