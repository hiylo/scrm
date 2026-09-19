/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : SystemClock.java
 * Date : 2026/09/17 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

package org.hiylo.scrm.id.sequence;

import lombok.extern.slf4j.Slf4j;

import java.sql.Timestamp;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 系统时钟，提供高精度的当前时间获取
 *
 * @author Hsi Chu
 * @since 1.0.0
 */
@Slf4j
public class SystemClock {

    /**
     * 默认更新周期（毫秒）
     */
    private static final long DEFAULT_PERIOD = 1L;

    /**
     * 最小更新周期（毫秒）
     */
    private static final long MIN_PERIOD = 1L;

    /**
     * 最大更新周期（毫秒）
     */
    private static final long MAX_PERIOD = 100L;

    /** 长 */
    private final long period;
    /** atomic长 */
    private final AtomicLong now;
    /** scheduled执行器 */
    private final ScheduledExecutorService scheduledExecutor;
    /** 是否正在运行 */
    private volatile boolean running = true;

    /**
     * 系统时钟
     * @param period 长
     * @return 私有
     */
    private SystemClock(long period) {
        this.period = validatePeriod(period);
        this.now = new AtomicLong(System.currentTimeMillis());
        this.scheduledExecutor = createScheduledExecutor();
        scheduleClockUpdating();
        registerShutdownHook();
    }

    /**
     * 获取单例实例
     */
    private static SystemClock instance() {
        return InstanceHolder.INSTANCE;
    }

    /**
     * 获取当前时间戳（毫秒）
     *
     * @return 当前时间戳
     */
    public static long now() {
        return instance().currentTimeMillis();
    }

    /**
     * 获取当前时间字符串
     *
     * @return 当前时间字符串
     */
    public static String nowDate() {
        return new Timestamp(instance().currentTimeMillis()).toString();
    }

    /**
     * get精确的当前时间戳（直接调用系统时间）
     * 用于need to高精度时间的场景
     *
     * @return 精确的当前时间戳
     */
    public static long preciseNow() {
        return System.currentTimeMillis();
    }

    /**
     * 检查时钟是否在运行
     *
     * @return 是否在运行
     */
    public static boolean isRunning() {
        return instance().running;
    }

    /**
     * get时钟精度（更新周期）
     *
     * @return 更新周期（毫秒）
     */
    public static long getPrecision() {
        return instance().period;
    }

    /**
     * 创建自定义周期的SystemClock实例
     * 注意：这会创建新的实例，不是单例
     *
     * @param period 更新周期（毫秒）
     * @return SystemClock实例
     */
    public static SystemClock createInstance(long period) {
        return new SystemClock(period);
    }

    /**
     * 验证更新周期参数
     */
    private long validatePeriod(long period) {
        if (period < MIN_PERIOD) {
            log.warn("Period {} is too small, using minimum period {}", period, MIN_PERIOD);
            return MIN_PERIOD;
        }
        if (period > MAX_PERIOD) {
            log.warn("Period {} is too large, using maximum period {}", period, MAX_PERIOD);
            return MAX_PERIOD;
        }
        return period;
    }

    /**
     * 创建调度执行器
     */
    private ScheduledExecutorService createScheduledExecutor() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
            /**
             * new线程
             * @param r 结果
             * @return 线程
             */
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "SystemClock-Updater");
                thread.setDaemon(true);
                thread.setPriority(Thread.MAX_PRIORITY);
                return thread;
            }
        });

        executor.setRemoveOnCancelPolicy(true);
        executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);

        return executor;
    }

    /**
     * 注册JVM关闭钩子
     */
    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.debug("SystemClock shutdown hook triggered");
            shutdown();
        }, "SystemClock-Shutdown"));
    }

    /**
     * 调度时钟更新任务
     */
    private void scheduleClockUpdating() {
        scheduledExecutor.scheduleAtFixedRate(new ClockUpdater(), 0, period, TimeUnit.MILLISECONDS);
        log.debug("SystemClock started with period {} ms", period);
    }

    /**
     * get当前时间戳
     */
    private long currentTimeMillis() {
        return now.get();
    }

    /**
     * 优雅关闭时钟
     */
    private void shutdown() {
        if (!running) {
            return;
        }

        running = false;

        if (scheduledExecutor != null && !scheduledExecutor.isShutdown()) {
            scheduledExecutor.shutdown();
            try {
                if (!scheduledExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    scheduledExecutor.shutdownNow();
                    if (!scheduledExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                        log.warn("SystemClock executor did not terminate gracefully");
                    }
                }
            } catch (InterruptedException e) {
                scheduledExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        log.debug("SystemClock shutdown completed");
    }

    /**
     * 单例持有者（延迟初始化）
     * @author Hsi Chu
     */
    private static class InstanceHolder {
        static final SystemClock INSTANCE = new SystemClock(DEFAULT_PERIOD);
    }

    /**
     * 时钟更新任务
     * @author Hsi Chu
     */
    private class ClockUpdater implements Runnable {
        /**
         * 运行
         */
        @Override
        public void run() {
            if (running) {
                try {
                    now.set(System.currentTimeMillis());
                } catch (Exception e) {
                    log.error("错误: updating system clock", e);
                }
            }
        }
    }
}
