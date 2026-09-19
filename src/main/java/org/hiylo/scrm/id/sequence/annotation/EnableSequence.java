/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : EnableSequence.java
 * Date : 2026/09/17 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

package org.hiylo.scrm.id.sequence.annotation;

import org.hiylo.scrm.id.sequence.SequenceConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 启用单例序列生成器注解
 * <p>
 * 在 Spring Boot 应用中使用此注解启用雪花算法 ID 生成功能。
 * 导入 {@link SequenceConfiguration}，支持通过 application.yml 配置 workerId/datacenterId：
 * <pre>
 * sequence:
 *   worker-id: 1        # 0-31，不配置则自动生成
 *   datacenter-id: 1    # 0-31，不配置则自动生成
 * </pre>
 * </p>
 *
 * @author Hsi Chu
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Import(SequenceConfiguration.class)
public @interface EnableSequence {
}
