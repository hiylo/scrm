/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : TaskExecutionConfig.java
 * Date : 2026/09/18 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.execution;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 营销任务执行引擎装配配置。
 * <p>
 * 以 {@code @Bean} 方式注册兜底实现 {@link LocalTaskExecutionService}, 使
 * {@code @ConditionalOnMissingBean} 条件生效——该条件注解仅对自动装配类中的
 * {@code @Bean} 工厂方法有效, 直接标注在 {@code @Service} 组件类上不会被正确求值,
 * 会导致容器内不存在任何 {@code TaskExecutionService} Bean 而启动失败。
 * 接入真实执行引擎时, 只需声明自己的 {@code TaskExecutionService} Bean 即可覆盖本实现。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Configuration
public class TaskExecutionConfig {

    /**
     * 注册营销任务执行引擎兜底实现, 容器内已有同类型 Bean 时不创建。
     *
     * @return 本地执行引擎实现 (仅登记不派发)
     */
    @Bean
    @ConditionalOnMissingBean(TaskExecutionService.class)
    public TaskExecutionService taskExecutionService() {
        return new LocalTaskExecutionService();
    }
}
