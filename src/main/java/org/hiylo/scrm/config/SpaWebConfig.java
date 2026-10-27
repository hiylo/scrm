/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : SpaWebConfig.java
 * Date : 2026/10/24 19:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * 前端单页应用（SPA）静态资源托管配置。
 * <p>
 * <b>解决什么问题</b>：前端构建产物（Vite {@code dist/}）打进 jar 的 {@code static/} 后，
 * 直接访问 {@code /companies} 这类<b>前端路由</b>会被 Spring 当成后端路径处理并返回 404——
 * 因为这些路由只存在于浏览器端历史记录里，磁盘上没有对应文件。
 * </p>
 * <p>
 * 本配置把「找不到的静态资源」回落到 {@code index.html}，交由前端路由接管；
 * 但明确<b>排除</b>后端路径前缀（{@code /scrm}、{@code /api}、{@code /actuator}、
 * {@code /ws}），避免把后端 404 也改写成 HTML 页面——接口调用方需要的是 JSON 错误。
 * </p>
 * <p>
 * <b>安全边界</b>：这里只负责「把文件发出去」，鉴权仍由 {@code SecurityConfig} 控制。
 * 前端壳是公开的，数据接口 {@code /scrm/**} 一律要求 JWT。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    /** 后端路径前缀：这些路径不做 SPA 回落，保持 JSON 404 */
    private static final String[] BACKEND_PREFIXES = {"/scrm", "/api", "/actuator", "/ws"};

    /**
     * 注册静态资源处理器：优先命中真实文件，命中不到则回落 index.html。
     *
     * @param registry 资源处理器注册表
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // 后端接口路径不回落到前端页面, 让调用方拿到 JSON 404 而不是 HTML
                        for (String prefix : BACKEND_PREFIXES) {
                            if (resourcePath.equals(prefix.substring(1)) || resourcePath.startsWith(prefix + "/")) {
                                return null;
                            }
                        }
                        // 带扩展名的静态资源缺失 -> 真 404（不要返回 HTML 冒充 JS/CSS）
                        if (resourcePath.contains(".")) {
                            return null;
                        }
                        // 其余视为前端路由 -> 交给 index.html
                        Resource index = new ClassPathResource("static/index.html");
                        return index.exists() ? index : null;
                    }
                });
    }
}
