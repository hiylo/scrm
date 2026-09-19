/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : WeworkConfigProvider.java
 * Date : 2026/07/29 21:19:51
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.entity.ScrmPlatformConfigEntity;
import org.hiylo.scrm.integration.wework.config.WeworkConfig;
import org.hiylo.scrm.integration.wework.service.WeworkService;
import org.hiylo.scrm.repository.ScrmPlatformConfigRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 企微配置动态提供者
 * <p>
 * 从数据库 scrm_platform_config 表读取企微配置并应用到 {@link WeworkConfig} Bean。
 * 配置优先级: 数据库记录 > YAML 文件默认值。
 * 启动时通过 {@link #refreshConfig()} 自动加载, 运行时可在保存配置后手动调用刷新。
 * </p>
 * <p>
 * 配置变更后会使 {@link WeworkService} 的 access_token 缓存失效, 并发布
 * {@link PlatformConfigChangedEvent} 事件供其他服务监听响应。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@Slf4j
public class WeworkConfigProvider {

    /** 企微平台类型标识 */
    private static final String PLATFORM_TYPE_WEWORK = "wework";

    /** 配置来源标识 - 数据库 */
    private static final String CONFIG_SOURCE_DB = "db";

    /** 企微配置 Bean */
    private final WeworkConfig weworkConfig;
    /** 平台配置仓库 (读取 scrm_platform_config 表) */
    private final ScrmPlatformConfigRepository platformConfigRepository;
    /** Spring 事件发布器 (发布配置变更事件) */
    private final ApplicationEventPublisher eventPublisher;
    /** 企微服务 (用于失效 access_token 缓存) */
    private final WeworkService weworkService;

    /**
     * 构造企微配置提供者
     *
     * @param weworkConfig             企微配置 Bean
     * @param platformConfigRepository 平台配置仓库
     * @param eventPublisher           Spring 事件发布器
     * @param weworkService            企微服务 (用于失效 token 缓存)
     */
    public WeworkConfigProvider(WeworkConfig weworkConfig,
                                ScrmPlatformConfigRepository platformConfigRepository,
                                ApplicationEventPublisher eventPublisher,
                                WeworkService weworkService) {
        this.weworkConfig = weworkConfig;
        this.platformConfigRepository = platformConfigRepository;
        this.eventPublisher = eventPublisher;
        this.weworkService = weworkService;
    }

    /**
     * 加载数据库中的企微配置并应用到 {@link WeworkConfig} Bean
     * <p>
     * 应用成功后使 access_token 缓存失效并发布配置变更事件;
     * 数据库无记录时保留 YAML 默认值。启动阶段由 {@link PostConstruct} 调用,
     * 失败仅记录警告不阻断启动。
     * </p>
     */
    @PostConstruct
    public void refreshConfig() {
        try {
            Optional<ScrmPlatformConfigEntity> entity =
                    platformConfigRepository.findByPlatformType(PLATFORM_TYPE_WEWORK);
            if (entity.isPresent()) {
                applyToWeworkConfig(entity.get());
                invalidateTokenCacheSafely();
                log.info("企微配置已从数据库加载(覆盖 YAML 默认值): corpId={}", weworkConfig.getCorpId());
                publishConfigChangedEvent(CONFIG_SOURCE_DB);
            } else {
                log.info("数据库无企微配置, 使用 YAML 默认值: corpId={}", weworkConfig.getCorpId());
            }
        } catch (Exception e) {
            // 启动阶段数据库可能未就绪, 仅记录警告, 不阻断启动
            log.warn("企微配置从数据库加载失败, 使用 YAML 默认值: {}", e.getMessage());
        }
    }

    /**
     * 将数据库配置实体应用到 {@link WeworkConfig} Bean
     *
     * @param entity 数据库配置实体
     */
    private void applyToWeworkConfig(ScrmPlatformConfigEntity entity) {
        if (entity.getCorpId() != null) {
            weworkConfig.setCorpId(entity.getCorpId());
        }
        if (entity.getAgentId() != null) {
            weworkConfig.setAgentId(entity.getAgentId());
        }
        if (entity.getSecret() != null) {
            weworkConfig.setSecret(entity.getSecret());
        }
        if (entity.getAesKey() != null) {
            weworkConfig.setAesKey(entity.getAesKey());
        }
        if (entity.getToken() != null) {
            weworkConfig.setToken(entity.getToken());
        }
        if (entity.getBaseUrl() != null) {
            weworkConfig.setBaseUrl(entity.getBaseUrl());
        }
        weworkConfig.setMockMode(entity.isMockMode());
        weworkConfig.setRealApiEnabled(entity.isRealApiEnabled());
        weworkConfig.setTimeout(entity.getTimeout());
        weworkConfig.setRetryCount(entity.getRetryCount());
    }

    /**
     * 将源配置的字段值写入目标配置
     *
     * @param source 源配置
     * @param target 目标配置
     */
    private void applyWeworkConfigFields(WeworkConfig source, WeworkConfig target) {
        target.setCorpId(source.getCorpId());
        target.setAgentId(source.getAgentId());
        target.setSecret(source.getSecret());
        target.setAesKey(source.getAesKey());
        target.setToken(source.getToken());
        target.setBaseUrl(source.getBaseUrl());
        target.setMockMode(source.isMockMode());
        target.setRealApiEnabled(source.isRealApiEnabled());
        target.setTimeout(source.getTimeout());
        target.setRetryCount(source.getRetryCount());
    }

    /**
     * 安全地使 WeworkService 的 access_token 缓存失效
     */
    private void invalidateTokenCacheSafely() {
        try {
            weworkService.invalidateTokenCache();
        } catch (Exception e) {
            log.warn("使企微 access_token 缓存失效失败: {}", e.getMessage());
        }
    }

    /**
     * 发布配置变更事件
     *
     * @param configSource 配置来源
     */
    private void publishConfigChangedEvent(String configSource) {
        try {
            eventPublisher.publishEvent(
                    new PlatformConfigChangedEvent(this, PLATFORM_TYPE_WEWORK, configSource));
            log.debug("已发布配置变更事件: platformType={}, source={}", PLATFORM_TYPE_WEWORK, configSource);
        } catch (Exception e) {
            log.warn("发布配置变更事件失败: {}", e.getMessage());
        }
    }
}
