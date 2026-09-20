/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSequenceConfig.java
 * Date : 2026/07/29 21:19:51
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.config;

import org.hiylo.scrm.id.sequence.SingletonSequence;
import org.hiylo.scrm.id.sequence.annotation.EnableSequence;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * SCRM 序列号生成器配置
 * <p>
 * 启用 Snowflake 雪花算法 ID 生成功能，并通过 {@link SequenceGeneratorHolder}
 * 将 Spring 容器管理的 {@link SingletonSequence} Bean 注入到 Hibernate 的
 * 自定义 ID 生成器中。
 * </p>
 *
 * <p>支持通过 application.yml 配置 workerId/datacenterId：</p>
 * <pre>
 * sequence:
 *   worker-id: 1        # 0-31，不配置则自动生成
 *   datacenter-id: 1    # 0-31，不配置则自动生成
 * </pre>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Configuration
@EnableSequence
public class ScrmSequenceConfig {

    /** 雪花算法序列生成器实例，用于生成全局唯一 ID */
    private final SingletonSequence singletonSequence;

    /**
     * 构造注入 SingletonSequence Bean（由 @EnableSequence 通过 @Import 注册的 SequenceConfiguration 创建）
     *
     * @param singletonSequence Spring 容器管理的 SingletonSequence 实例
     */
    public ScrmSequenceConfig(SingletonSequence singletonSequence) {
        this.singletonSequence = singletonSequence;
    }

    /**
     * 初始化后，将 SingletonSequence 实例注册到静态持有者
     * 供 Hibernate 的 SnowflakeIdGenerator 访问
     */
    @PostConstruct
    public void init() {
        SequenceGeneratorHolder.setSequenceGenerator(singletonSequence);
    }
}
