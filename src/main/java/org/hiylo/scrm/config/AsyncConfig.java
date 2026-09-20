/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : AsyncConfig.java
 * Date : 2026/06/27 02:52:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ConcurrentTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 异步任务配置
 * <p>
 * 基于 Java 21 虚拟线程执行器 ({@link Executors#newVirtualThreadPerTaskExecutor()}),
 * 经 {@link ConcurrentTaskExecutor} 适配为 Spring 的 {@link AsyncTaskExecutor},
 * 适用于 IO 密集型的 SCRM 自动化任务 (内容分发、平台 API 调用、数据回收等)。
 * 虚拟线程无需调优池大小, 不会因队列满而阻塞调用者。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 异步任务执行器 (虚拟线程)
     *
     * @return AsyncTaskExecutor
     */
    @Bean(name = "asyncTaskExecutor")
    public AsyncTaskExecutor asyncTaskExecutor() {
        ExecutorService delegate = Executors.newVirtualThreadPerTaskExecutor();
        ConcurrentTaskExecutor executor = new ConcurrentTaskExecutor(delegate);
        log.info("SCRM server async task executor initialized: virtual threads (per-task executor)");
        return executor;
    }

    /**
     * RestTemplate HTTP 客户端 (微信公众号 / 视频号 SDK 依赖)
     *
     * @return RestTemplate 实例
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
