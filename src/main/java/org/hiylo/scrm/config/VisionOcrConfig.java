/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : VisionOcrConfig.java
 * Date : 2026/10/23 18:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 本地视觉识别配置装配。
 * <p>
 * 注册 {@link VisionModelProperties}（{@code scrm.vision.*}）, 使本地 llama-server
 * 的接入参数（地址/模型路径/进程托管/推理参数）可被 Spring 绑定。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Configuration
@EnableConfigurationProperties(VisionModelProperties.class)
public class VisionOcrConfig {
}
