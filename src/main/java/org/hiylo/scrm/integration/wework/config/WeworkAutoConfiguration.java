/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : WeworkAutoConfiguration.java
 * Date : 2026/09/19 21:20:11
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.integration.wework.config;

import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.integration.wework.service.WeworkService;
import org.hiylo.scrm.integration.wework.service.impl.WeworkServiceImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 企业微信平台自动配置
 * <p>
 * 当 {@code wework.enabled=true} 时启用,注册 {@link WeworkServiceImpl} 为 Spring Bean。
 * 同时通过 {@code @EnableConfigurationProperties} 启用 {@link WeworkConfig} 配置绑定。
 * </p>
 *
 * @author Hsi Chu
 * @since 1.0.0
 */
@Slf4j
@Configuration
@ConditionalOnClass(WeworkService.class)
@ConditionalOnProperty(prefix = "wework", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(WeworkConfig.class)
public class WeworkAutoConfiguration {

    /**
     * 注册企业微信平台服务 Bean
     * <p>
     * 当容器中不存在 {@link WeworkService} 实现时,使用默认实现 {@link WeworkServiceImpl}。
     * 业务方可通过覆盖此 Bean 注入自定义实现。
     * </p>
     *
     * @param weworkConfig 企微配置
     * @return 企微服务实现
     */
    @Bean
    @ConditionalOnMissingBean(WeworkService.class)
    public WeworkService weworkService(WeworkConfig weworkConfig) {
        log.info("注册企业微信平台服务: corpId={}, agentId={}",
                weworkConfig.getCorpId(), weworkConfig.getAgentId());
        return new WeworkServiceImpl(weworkConfig);
    }
}
