/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : FinanceKnowledgeConfig.java
 * Date : 2026/10/23 12:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 模块③ 财税知识库增强配置装配。
 * <p>
 * 注册 {@link ScrmFinanceKnowledgeProperties}（批量导入 / 复核周期参数,
 * {@code scrm.finance-knowledge.*} 前缀）, 使配置可被 Spring 绑定。沿用
 * D04 {@link MarketingBatchApiConfig} / D05 {@link InspectionApiConfig} /
 * D01 {@link FinanceAiConfig} 的 {@code @EnableConfigurationProperties}
 * 装配模式, 不修改 application.yml。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Configuration
@EnableConfigurationProperties(ScrmFinanceKnowledgeProperties.class)
public class FinanceKnowledgeConfig {
}
