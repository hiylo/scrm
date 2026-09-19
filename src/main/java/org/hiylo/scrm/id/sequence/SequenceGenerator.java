/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : SequenceGenerator.java
 * Date : 2026/09/17 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

package org.hiylo.scrm.id.sequence;

import org.springframework.stereotype.Component;

/**
 * 序列号生成器，封装 SingletonSequence 提供按名称生成唯一 ID 的便捷方法
 * <p>
 * 通过构造注入 Spring 容器管理的 SingletonSequence 单例 Bean，
 * 避免直接 new 创建第二实例导致 workerId 冲突。
 * </p>
 *
 * @author Hsi Chu
 */
@Component
public class SequenceGenerator {

    /** 单例序列号生成器，按 workerId 生成唯一 ID */
    private final SingletonSequence sequence;

    /**
     * 构造注入 SingletonSequence Bean（由 @EnableSequence 通过 @Import 注册）
     *
     * @param sequence Spring 容器管理的 SingletonSequence 实例
     */
    public SequenceGenerator(SingletonSequence sequence) {
        this.sequence = sequence;
    }

    /**
     * 获取下一个唯一 ID
     *
     * @param name 业务名称（当前实现忽略，预留扩展）
     * @return 唯一 ID
     */
    public long nextId(String name) {
        return sequence.nextId();
    }

    /**
     * 获取下一个唯一 ID 字符串
     *
     * @param name 业务名称（当前实现忽略，预留扩展）
     * @return 唯一 ID 字符串
     */
    public String nextIdString(String name) {
        return sequence.nextIdString();
    }
}
