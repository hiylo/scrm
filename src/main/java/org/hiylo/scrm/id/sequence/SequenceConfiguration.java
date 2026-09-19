/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : SequenceConfiguration.java
 * Date : 2026/09/17 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

package org.hiylo.scrm.id.sequence;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 序列生成器自动配置
 * <p>
 * 支持通过 application.yml 配置 workerId 和 datacenterId：
 * <pre>
 * sequence:
 *   worker-id: 1        # 工作机器 ID（0-31），不配置则自动生成
 *   datacenter-id: 1    # 数据中心 ID（0-31），不配置则自动生成
 * </pre>
 * 在容器/K8s 环境中建议显式配置，避免多实例 MAC 相同导致 workerId 冲突。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@Configuration
public class SequenceConfiguration {

    /** 工作机器 ID（-1 表示自动生成） */
    @Value("${sequence.worker-id:-1}")
    private long workerId;

    /** 数据中心 ID（-1 表示自动生成） */
    @Value("${sequence.datacenter-id:-1}")
    private long datacenterId;

    /**
     * 创建 SingletonSequence Bean
     * <p>
     * 如果配置了 workerId/datacenterId 则使用配置值，否则使用自动生成。
     * </p>
     *
     * @return SingletonSequence 实例
     */
    @Bean
    public SingletonSequence singletonSequence() {
        if (workerId >= 0 && datacenterId >= 0) {
            log.info("SingletonSequence initialized from config: datacenterId={}, workerId={}", datacenterId, workerId);
            return SingletonSequence.of((int) datacenterId, (int) workerId);
        }
        // 未配置则使用默认构造函数自动生成
        return SingletonSequence.of();
    }
}
