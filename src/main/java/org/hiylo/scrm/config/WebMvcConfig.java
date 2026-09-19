/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : WebMvcConfig.java
 * Date : 2026/09/17 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 * <p>
 * 显式指定 bean 名 {@code scrmWebMvcConfig}, 避免与其他模块同名的
 * {@code WebMvcConfig} 默认 bean 名冲突。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Configuration("scrmWebMvcConfig")
public class WebMvcConfig implements WebMvcConfigurer {
}
