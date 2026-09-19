/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : PlatformConfigChangedEvent.java
 * Date : 2026/07/29 21:19:51
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.config;

import org.springframework.context.ApplicationEvent;

/**
 * 平台配置变更事件
 * <p>
 * 当企微平台配置 (corpId/agentId/secret 等) 发生变更时发布此事件,
 * 供其他服务 (如 WeworkContactSyncScheduler) 监听并作出响应, 例如触发外部联系人同步。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public class PlatformConfigChangedEvent extends ApplicationEvent {

    /** 平台类型（如 "wework"） */
    private final String platformType;

    /** 配置来源（"db" 或 "yaml"） */
    private final String configSource;

    /**
     * 构造平台配置变更事件
     *
     * @param source       事件发布源
     * @param platformType 平台类型
     * @param configSource 配置来源
     */
    public PlatformConfigChangedEvent(Object source, String platformType, String configSource) {
        super(source);
        this.platformType = platformType;
        this.configSource = configSource;
    }

    /**
     * 获取平台类型
     *
     * @return 平台类型 (如 "wework")
     */
    public String getPlatformType() {
        return platformType;
    }

    /**
     * 获取配置来源
     *
     * @return 配置来源 ("db" 或 "yaml")
     */
    public String getConfigSource() {
        return configSource;
    }
}
