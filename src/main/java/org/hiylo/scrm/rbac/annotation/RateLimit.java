/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : RateLimit.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.rbac.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * SCRM API 限流注解
 * <p>
 * 标记在 Controller 方法上,声明该端点的访问频率限制。基于 Bucket4j Token Bucket 算法,
 * 由 {@link org.hiylo.scrm.aspect.RateLimitAspect} 切面统一拦截并按
 * 用户 ID (或客户端 IP) 维度统计请求量,超限返回 429。
 * </p>
 * <p>
 * 限流维度: 同一限流 key + 同一调用方 (X-User-Id 头, 未登录回退客户端 IP) 共享一个令牌桶。
 * 注解参数可覆盖 {@code RateLimitConfig} 中的默认配置。
 * </p>
 *
 * @author Hsi Chu
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * 限流 key, 空则用 "类名.方法名" + ":" + userId或IP 自动生成。
     *
     * @return 限流 key
     */
    String key() default "";

    /**
     * 桶容量, 即允许的突发请求上限。
     *
     * @return 桶容量
     */
    int capacity() default 60;

    /**
     * 每个补充周期内补充的令牌数。
     *
     * @return 补充令牌数
     */
    int refillTokens() default 60;

    /**
     * 令牌补充周期 (秒)。
     *
     * @return 补充周期秒数
     */
    long refillPeriodSeconds() default 60;

    /**
     * 触发限流拒绝时返回给前端的提示消息。
     *
     * @return 拒绝提示消息
     */
    String message() default "请求过于频繁，请稍后重试";
}
