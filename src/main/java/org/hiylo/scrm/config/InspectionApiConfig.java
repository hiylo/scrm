/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : InspectionApiConfig.java
 * Date : 2026/10/23 09:20:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.config;

import org.hiylo.scrm.feign.QixinApiConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 巡检域外部 API 配置装配。
 * <p>
 * 注册 {@link QixinApiConfig}（启信宝开放平台, token 走环境变量, 不落库）与
 * {@link ScrmInspectionProperties}（调度 Cron / 告警接收人）两个配置类,
 * 使 {@code scrm.inspection.*} 前缀的配置项可被 Spring 绑定。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Configuration
@EnableConfigurationProperties({QixinApiConfig.class, ScrmInspectionProperties.class})
public class InspectionApiConfig {
}