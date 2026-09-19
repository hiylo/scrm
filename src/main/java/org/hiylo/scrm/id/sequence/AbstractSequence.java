/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : AbstractSequence.java
 * Date : 2026/09/17 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

package org.hiylo.scrm.id.sequence;

/**
 * 序列生成器抽象基类
 * <p>
 * 基于 Twitter Snowflake 算法的序列生成器基类，定义了 ID 生成的核心参数和通用方法。
 * </p>
 *
 * <p>Snowflake ID 结构（64 位）：</p>
 * <ul>
 *   <li>1 位 - 符号位（始终为 0）</li>
 *   <li>41 位 - 时间戳（毫秒，支持约 69 年）</li>
 *   <li>10 位 - 机器 ID（5 位数据中心 + 5 位工作机器，支持 1024 个节点）</li>
 *   <li>12 位 - 序列号（每毫秒每节点可生成 4096 个 ID）</li>
 * </ul>
 *
 * @author Hsi Chu
 * @since 1.0.0
 */
public abstract class AbstractSequence {

    /** 工作机器 ID 占用位数 */
    static final long workerIdBits = 5L;

    /** 数据中心 ID 占用位数 */
    static final long datacenterIdBits = 5L;

    /** 支持的最大机器 ID（31） */
    static final long maxWorkerId = -1L ^ (-1L << workerIdBits);

    /** 支持的最大数据中心 ID（31） */
    static final long maxDatacenterId = -1L ^ (-1L << datacenterIdBits);

    /** 起始时间戳（2024-01-01 00:00:00 UTC），相比 Twitter 原版延长约 14 年寿命 */
    static final long twepoch = 1704067200000L;

    /** 序列号占用位数 */
    static final long sequenceBits = 12L;

    /** 机器 ID 左移位数（12） */
    static final long workerIdShift = sequenceBits;

    /** 数据中心 ID 左移位数（17 = 12 + 5） */
    static final long datacenterIdShift = sequenceBits + workerIdBits;

    /** 时间戳左移位数（22 = 12 + 5 + 5） */
    static final long timestampLeftShift = sequenceBits + workerIdBits + datacenterIdBits;

    /** 序列号掩码（4095 = 0xFFF） */
    static final long sequenceMask = -1L ^ (-1L << sequenceBits);

    /**
     * 获取 String 类型的 ID
     *
     * @return ID 字符串
     */
    public abstract String nextIdString();

    /**
     * 获取 long 类型的 ID
     *
     * @return 唯一 ID
     */
    public abstract long nextId();

    /**
     * 阻塞到下一个毫秒，直到获得新的时间戳
     *
     * @param lastTimestamp 上次生成 ID 的时间戳
     * @return 当前时间戳
     */
    protected long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    /**
     * 获取当前时间戳（毫秒），使用 SystemClock 提供高性能时间读取
     *
     * @return 当前时间（毫秒）
     */
    protected long timeGen() {
        return SystemClock.now();
    }
}
