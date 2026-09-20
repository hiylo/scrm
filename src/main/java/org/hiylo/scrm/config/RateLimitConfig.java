/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : RateLimitConfig.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * SCRM API 限流默认配置
 * <p>
 * 集中管理限流的默认参数, 可通过 {@code application.yml} 的 {@code scrm.rate-limit.*} 覆盖。
 * {@link org.hiylo.scrm.rbac.annotation.RateLimit} 注解上显式声明的参数会覆盖此处默认值。
 * </p>
 *
 * @author Hsi Chu
 */
@Configuration
public class RateLimitConfig {

    /** 默认桶容量 (突发上限) */
    @Value("${scrm.rate-limit.default-capacity:60}")
    private long defaultCapacity;

    /** 默认每个补充周期补充的令牌数 */
    @Value("${scrm.rate-limit.default-refill-tokens:60}")
    private long defaultRefillTokens;

    /** 默认令牌补充周期 (秒) */
    @Value("${scrm.rate-limit.default-refill-period-seconds:60}")
    private long defaultRefillPeriodSeconds;

    /**
     * 默认限流桶配置, 每分钟 60 次。
     * <p>
     * 作为全局兜底配置, 端点可通过 {@link org.hiylo.scrm.rbac.annotation.RateLimit}
     * 注解参数覆盖。
     * </p>
     *
     * @return 默认 BucketConfiguration
     */
    @Bean
    public BucketConfiguration defaultBucketConfiguration() {
        Bandwidth bandwidth = Bandwidth.classic(defaultCapacity,
                Refill.intervally(defaultRefillTokens, Duration.ofSeconds(defaultRefillPeriodSeconds)));
        return BucketConfiguration.builder().addLimit(bandwidth).build();
    }
}
