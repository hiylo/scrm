/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : LicenseOcrConfig.java
 * Date : 2026/10/23 14:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.config;

import org.hiylo.scrm.feign.OcrApiConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 证照 OCR（模块②）外部服务配置装配。
 * <p>
 * 注册 {@link OcrApiConfig}（OCR 识别服务地址 + 内部令牌 + 超时, {@code scrm.ocr.*}
 * 前缀）。沿用 D01/D04/D05 的 {@code @EnableConfigurationProperties} 装配模式,
 * 不修改 application.yml——配置说明与集成步骤见 docs/tasks/D04-config.md。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Configuration
@EnableConfigurationProperties(OcrApiConfig.class)
public class LicenseOcrConfig {
}