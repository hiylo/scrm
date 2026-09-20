/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : CorsConfig.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS 跨域配置
 * <p>
 * 允许前端应用访问 SCRM 服务 API, 默认放行 localhost:3002 (SCRM 前端 dev 端口)。
 * 生产环境通过环境变量 SCRM_CORS_ALLOWED_ORIGINS 注入允许的前端域名。
 * </p>
 *
 * @author Hsi Chu
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /** 允许的前端来源, 逗号分隔 */
    @Value("${scrm.cors.allowed-origins:http://localhost:3002}")
    private String[] allowedOrigins;

    /**
     * 注册 CORS 映射规则
     *
     * @param registry CorsRegistry
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/scrm/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
        // actuator 端点允许跨域访问便于监控
        registry.addMapping("/actuator/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
