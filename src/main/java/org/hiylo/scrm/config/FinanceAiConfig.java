/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : FinanceAiConfig.java
 * Date : 2026/10/23 12:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 财税模块⑤ AI 接待 + 意向分级配置装配。
 * <p>
 * 注册 {@link ScrmFinanceLeadPushProperties}（高意向线索推送参数, {@code scrm.finance-lead-push.*}
 * 前缀）, 使配置可被 Spring 绑定。沿用 D04 {@link MarketingBatchApiConfig} / D05
 * {@link InspectionApiConfig} 的 {@code @EnableConfigurationProperties} 装配模式, 不修改
 * application.yml。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Configuration
@EnableConfigurationProperties(ScrmFinanceLeadPushProperties.class)
public class FinanceAiConfig {
}