/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : FinanceTaxConfig.java
 * Date : 2026/10/23 12:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 财税模块 报税日历/续费流失/政策推送 配置装配。
 * <p>
 * 注册 {@link ScrmTaxCalendarProperties}（{@code scrm.tax-calendar.*}）、
 * {@link ScrmRenewalProperties}（{@code scrm.renewal.*}）与
 * {@link ScrmPolicyPushProperties}（{@code scrm.policy-push.*}）, 使各域调度
 * Cron / 预警节奏 / 配额可被 Spring 绑定。沿用 D01/D04/D05 的
 * {@code @EnableConfigurationProperties} 装配模式, 不修改 application.yml。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Configuration
@EnableConfigurationProperties({
        ScrmTaxCalendarProperties.class,
        ScrmRenewalProperties.class,
        ScrmPolicyPushProperties.class
})
public class FinanceTaxConfig {
}
