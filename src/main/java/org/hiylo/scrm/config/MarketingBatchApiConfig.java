/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : MarketingBatchApiConfig.java
 * Date : 2026/10/23 16:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 财税营销 AI 批量生成配置装配。
 * <p>
 * 注册 {@link ScrmMarketingBatchProperties}（模型/变体数量/字数上限/提示词资源路径）,
 * 使 {@code scrm.marketing-batch.*} 前缀配置可被 Spring 绑定。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Configuration
@EnableConfigurationProperties(ScrmMarketingBatchProperties.class)
public class MarketingBatchApiConfig {
}