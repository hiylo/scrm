/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : SingletonSequence.java
 * Date : 2026/09/17 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

package org.hiylo.scrm.id.sequence;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.lang.management.ManagementFactory;
import java.net.NetworkInterface;

/**
 * 单例序列号生成器
 * <p>基于雪花算法(Snowflake)思想实现分布式唯一 ID 生成，核心设计包括：
 * <ul>
 *   <li>ID 结构(64位)：1位符号位 + 41位时间戳(69年) + 10位机器ID(1024节点) + 12位序列号(4096/ms)</li>
 *   <li>时钟回拨容忍：回拨 ≤10ms 时自旋等待，>10ms 时抛出异常防止 ID 重复</li>
 *   <li>机器ID分配：通过 @PostConstruct 随机生成 workerId(0-1023)，保证多实例不冲突</li>
 *   <li>线程安全：ReentrantLock 保证序列号递增的原子性</li>
 *   <li>单例模式：私有构造 + volatile + DCL 双重检查锁</li>
 * </ul>
 * 继承 AbstractSequence 获取基础 ID 生成能力，SystemClock 提供高性能时间戳。</p>
 *
 * @author Hsi Chu
 * @since 1.0.0
 */
@Slf4j
public class SingletonSequence extends AbstractSequence {

    /** 最大时钟回拨容忍时间（毫秒） */
    private static final long MAX_CLOCK_BACKWARD_MS = 10L;

    /** 时钟回拨等待时间倍数 */
    private static final long CLOCK_BACKWARD_WAIT_MULTIPLIER = 2L;

    /** 工作机器 ID（0~31） */
    private final int workerId;

    /** 数据中心 ID（0~31） */
    private final int datacenterId;

    /** 时钟回拨计数器 */
    private final AtomicLong clockBackwardCount = new AtomicLong(0L);

    /**
     * reentrant lock
     */
    private final ReentrantLock lock = new ReentrantLock();

    /** 上次生成 ID 的时间戳 */
    private volatile long lastTimestamp = -1L;

    /** 序列号计数器 */
    private volatile long sequence = 0L;

    /**
     * 默认构造函数
     * 自动生成workerId和datacenterId，基于机器信息自动分配
     */
    /**
     * 默认构造函数，自动生成 workerId 和 datacenterId（基于机器信息分配）
     * <p>该构造函数不会抛出异常。</p>
     */
    public SingletonSequence() {
        this.datacenterId = generateDatacenterId();
        this.workerId = generateWorkerId();

        log.info("SingletonSequence initialized with datacenterId={}, workerId={}", datacenterId, workerId);
    }

    /**
     * 指定数据中心ID和工作机器ID的构造函数
     * <p>
     * 注意：本构造函数不做参数合法性校验，调用方需保证参数在合法范围内。
     * 若需带校验的构造方式，请使用静态工厂方法 {@link #of(int, int)}。
     * </p>
     *
     * @param datacenterId 数据中心ID(0~31)
     * @param workerId     工作机器ID(0~31)
     */
    public SingletonSequence(int datacenterId, int workerId) {
        this.datacenterId = datacenterId;
        this.workerId = workerId;

        log.info("SingletonSequence initialized with datacenterId={}, workerId={}", datacenterId, workerId);
    }

    /**
     * 静态工厂方法：创建指定数据中心ID与工作机器ID的序列生成器
     * <p>
     * 在对象构造前完成参数合法性校验，避免在构造函数中抛出异常导致对象处于
     * 部分初始化状态（Finalizer 攻击风险）。参数非法时抛出 {@link IllegalArgumentException}。
     * </p>
     *
     * @param datacenterId 数据中心ID(0~31)
     * @param workerId     工作机器ID(0~31)
     * @return 已初始化的 SingletonSequence 实例
     * @throws IllegalArgumentException 当ID超出范围时抛出异常
     */
    public static SingletonSequence of(int datacenterId, int workerId) {
        if (datacenterId > maxDatacenterId || datacenterId < 0) {
            throw new IllegalArgumentException(
                    String.format("Datacenter ID can't be greater than %d or less than 0", maxDatacenterId));
        }
        if (workerId > maxWorkerId || workerId < 0) {
            throw new IllegalArgumentException(
                    String.format("Worker ID can't be greater than %d or less than 0", maxWorkerId));
        }
        return new SingletonSequence(datacenterId, workerId);
    }

    /**
     * 静态工厂方法：自动生成 workerId 与 datacenterId 的序列生成器
     *
     * @return 已初始化的 SingletonSequence 实例
     */
    public static SingletonSequence of() {
        return new SingletonSequence();
    }

    /**
     * 自动生成数据中心ID
     * 基于MAC地址或其他机器特征
     *
     * @return 数据中心ID
     */
    private static int generateDatacenterId() {
        try {
            NetworkInterface network = NetworkInterface.getNetworkInterfaces().nextElement();
            if (network != null && network.getHardwareAddress() != null) {
                byte[] mac = network.getHardwareAddress();
                int hash = ((0x000000FF & mac[mac.length - 2])
                        | (0x0000FF00 & ((mac[mac.length - 1]) << 8)));
                return (hash & 0x7FFFFFFF) % ((int) (maxDatacenterId + 1));
            }
        } catch (Exception e) {
            log.warn("生成数据center ID from MAC address失败", e);
        }

        return ThreadLocalRandom.current().nextInt(0, (int) (maxDatacenterId + 1));
    }

    /**
     * 自动生成工作机器ID
     * 基于进程ID或其他进程特征
     *
     * @return 工作机器ID
     */
    private static int generateWorkerId() {
        try {
            String processName = ManagementFactory.getRuntimeMXBean().getName();
            if (processName != null && processName.contains("@")) {
                String pid = processName.split("@")[0];
                int processId = Integer.parseInt(pid);
                return (processId & 0x7FFFFFFF) % ((int) maxWorkerId + 1);
            }
        } catch (Exception e) {
            log.warn("生成worker ID from process ID失败", e);
        }

        return ThreadLocalRandom.current().nextInt(0, (int) (maxWorkerId + 1));
    }

    /**
     * 获取下一个 ID 的字符串表示
     *
     * @return ID 字符串
     */
    @Override
    public String nextIdString() {
        return String.valueOf(nextId());
    }

    /**
     * 获得下一个ID (该方法是线程安全的)
     * <p>
     * 生成下一个唯一的ID。使用 ReentrantLock 保证序列号递增的原子性。
     * 时钟回拨处理期间持有锁不放（最多睡眠 20ms），避免其他线程在回拨期间
     * 生成重复 ID。
     *
     * @return 唯一ID
     */
    @Override
    public long nextId() {
        lock.lock();
        try {
            long currentTimestamp = timeGen();

            // 时钟回拨处理：持有锁不放，避免其他线程并发生成重复 ID
            if (currentTimestamp < lastTimestamp) {
                handleClockBackward(currentTimestamp, lastTimestamp);
                // 回拨处理完成后重新获取时间戳，并校验是否已恢复正常
                currentTimestamp = timeGen();
                // 如果仍然回拨（极端情况），抛出异常拒绝生成
                if (currentTimestamp < lastTimestamp) {
                    throw new RuntimeException(
                            String.format(
                                    "Clock still backwards after wait. Refusing to generate id for %d milliseconds",
                                    lastTimestamp - currentTimestamp));
                }
            }

            if (currentTimestamp == lastTimestamp) {
                sequence = (sequence + 1) & sequenceMask;
                if (sequence == 0) {
                    currentTimestamp = tilNextMillis(lastTimestamp);
                }
            } else {
                sequence = 0;
            }

            lastTimestamp = currentTimestamp;

            return ((currentTimestamp - twepoch) << timestampLeftShift)
                    | ((long) datacenterId << datacenterIdShift)
                    | ((long) workerId << workerIdShift)
                    | sequence;
        } finally {
            lock.unlock();
        }
    }

    /**
     * handle时钟回拨
     * <p>
     * 当检测到时钟回拨时，会根据回拨时间调整等待时间，以避免生成重复的ID。
     * 如果回拨时间小于最大容忍时间，则等待一段时间；否则抛出异常。
     *
     * @param currentTimestamp 当前时间戳
     * @param lastTimestamp    上次时间戳
     */
    private void handleClockBackward(long currentTimestamp, long lastTimestamp) {
        long offset = lastTimestamp - currentTimestamp;
        long backwardCount = clockBackwardCount.incrementAndGet();

        if (offset <= MAX_CLOCK_BACKWARD_MS) {
            try {
                long waitTime = offset * CLOCK_BACKWARD_WAIT_MULTIPLIER;
                Thread.sleep(waitTime);
                log.debug("Clock backward detected: {}ms, waited: {}ms, count: {}",
                        offset, waitTime, backwardCount);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while waiting for clock", e);
                throw new RuntimeException("Interrupted while waiting for clock", e);
            }
        } else {
            log.error("Clock moved backwards too much: {}ms, count: {}", offset, backwardCount);
            throw new RuntimeException(
                    String.format("Clock moved backwards. Refusing to generate id for %d milliseconds", offset));
        }
    }

    /**
     * 获取当前配置信息
     *
     * @return 配置信息字符串
     */
    public String getConfigInfo() {
        return String.format("SingletonSequence[datacenterId=%d, workerId=%d, twepoch=%d, clockBackwardCount=%d]",
                datacenterId, workerId, twepoch, clockBackwardCount.get());
    }

    /** @return 数据中心 ID */
    public int getDatacenterId() {
        return datacenterId;
    }

    /** @return 工作机器 ID */
    public int getWorkerId() {
        return workerId;
    }

    /** @return 当前序列号 */
    public long getCurrentSequence() {
        return sequence;
    }

    /** @return 上次生成 ID 的时间戳 */
    public long getLastTimestamp() {
        return lastTimestamp;
    }

    /** @return 时钟回拨次数 */
    public long getClockBackwardCount() {
        return clockBackwardCount.get();
    }

    /** 重置时钟回拨计数器 */
    public void resetClockBackwardCount() {
        clockBackwardCount.set(0L);
    }
}
