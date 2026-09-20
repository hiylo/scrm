/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : SequenceGeneratorHolder.java
 * Date : 2026/07/29 21:19:51
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.config;

import org.hiylo.scrm.id.sequence.SingletonSequence;

/**
 * Snowflake 序列生成器静态持有者
 * <p>
 * 由于 Hibernate {@link org.hibernate.id.IdentifierGenerator} 实例由 Hibernate 内部创建，
 * 无法直接注入 Spring Bean，因此通过静态持有者间接获取 Spring 容器管理的
 * {@link SingletonSequence} 实例。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
public class SequenceGeneratorHolder {

    /** 序列生成器实例（由 Spring 配置类在启动时设置，供 Hibernate ID 生成器读取） */
    private static volatile SingletonSequence instance;

    /**
     * 设置序列生成器实例（由 Spring 配置类在启动时调用）
     *
     * @param sequence Spring 容器管理的 SingletonSequence Bean
     */
    public static void setSequenceGenerator(SingletonSequence sequence) {
        instance = sequence;
    }

    /**
     * 获取序列生成器实例
     *
     * @return SingletonSequence 实例
     * @throws IllegalStateException 如果序列生成器尚未初始化
     */
    public static SingletonSequence getSequenceGenerator() {
        SingletonSequence gen = instance;
        if (gen == null) {
            throw new IllegalStateException("SingletonSequence not initialized. Ensure @EnableSequence is configured.");
        }
        return gen;
    }
}
