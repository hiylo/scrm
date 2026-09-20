/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : RateLimitAspect.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.aspect;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * SCRM API 限流切面
 * <p>
 * 基于 Bucket4j Token Bucket 算法, 拦截带 {@link RateLimit} 注解的 Controller 方法,
 * 按 "限流 key + 调用方 (用户 ID / 客户端 IP)" 维度维护独立令牌桶, 超限抛出
 * {@link ScrmException#tooManyRequests(String)} (HTTP 429)。
 * </p>
 * <p>
 * 桶缓存在内存 ({@link ConcurrentHashMap}), 通过 {@link ScheduledExecutorService}
 * 每 10 分钟清理 1 小时未访问的桶, 避免内存泄漏。本服务为单机限流, 多实例部署需替换为
 * Redis 等分布式存储。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    /** 用户 ID 请求头 (由网关注入) */
    private static final String USER_ID_HEADER = "X-User-Id";

    /** 反向代理转发的客户端 IP 头, 按优先级排列 */
    private static final String[] IP_HEADERS = {
            "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"
    };

    /** 无请求上下文或解析失败时的兜底调用方标识 */
    private static final String UNKNOWN_CALLER = "unknown";

    /** 桶未访问过期时间 (纳秒, 1 小时) */
    private static final long BUCKET_EXPIRE_NANOS = TimeUnit.HOURS.toNanos(1);

    /** 桶清理调度周期 (分钟) */
    private static final long CLEANUP_INTERVAL_MINUTES = 10;

    /** 桶缓存: 限流 key -> 桶条目 (含最后访问时间) */
    private final Map<String, BucketEntry> bucketCache = new ConcurrentHashMap<>();

    /** 当前 HTTP 请求 (Spring 注入的请求作用域代理, 调用时委托给当前线程的请求) */
    @Autowired
    private HttpServletRequest request;

    /** 桶清理调度器 */
    private ScheduledExecutorService cleanupExecutor;

    /**
     * 初始化桶清理调度器, 每 10 分钟清理一次过期桶。
     */
    @PostConstruct
    public void init() {
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "rate-limit-bucket-cleanup");
            thread.setDaemon(true);
            return thread;
        });
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredBuckets,
                CLEANUP_INTERVAL_MINUTES, CLEANUP_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * 容器销毁时关闭清理调度器, 释放线程资源。
     */
    @PreDestroy
    public void destroy() {
        if (cleanupExecutor != null) {
            cleanupExecutor.shutdownNow();
        }
    }

    /**
     * 环绕通知: 拦截带 {@link RateLimit} 注解的方法, 按令牌桶算法限流。
     * <p>
     * 放行则执行原方法; 拒绝则抛出 {@link ScrmException#tooManyRequests(String)}。
     * </p>
     *
     * @param pjp       连接点
     * @param rateLimit 限流注解 (由 Pointcut 绑定)
     * @return 目标方法的原始返回值
     * @throws Throwable 目标方法抛出的异常 (原样抛出, 不吞异常)
     */
    @Around("@annotation(rateLimit)")
    public Object aroundRateLimit(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        String rateLimitKey = buildKey(pjp, rateLimit);
        Bucket bucket = getOrCreateBucket(rateLimitKey, rateLimit);
        if (bucket.tryConsume(1)) {
            log.debug("限流放行: key={}, availableTokens={}", rateLimitKey, bucket.getAvailableTokens());
            return pjp.proceed();
        }
        log.debug("限流拒绝: key={}, availableTokens={}", rateLimitKey, bucket.getAvailableTokens());
        throw ScrmException.tooManyRequests(rateLimit.message());
    }

    /**
     * 构建限流 key: 优先用注解的 {@code key}, 为空时用 "类名.方法名" + ":" + 调用方。
     *
     * @param pjp        连接点
     * @param rateLimit  限流注解
     * @return 限流 key
     */
    private String buildKey(ProceedingJoinPoint pjp, RateLimit rateLimit) {
        String annotationKey = rateLimit.key();
        if (annotationKey != null && !annotationKey.isBlank()) {
            return annotationKey + ":" + getUserIdOrIp();
        }
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        String methodKey = method.getDeclaringClass().getSimpleName() + "." + method.getName();
        return methodKey + ":" + getUserIdOrIp();
    }

    /**
     * 从缓存获取或创建令牌桶, 按注解参数配置 Bandwidth。
     * <p>
     * 使用 {@link ConcurrentHashMap#computeIfAbsent} 保证同 key 桶只创建一次,
     * 并更新最后访问时间。
     * </p>
     *
     * @param key        限流 key
     * @param rateLimit  限流注解 (提供桶容量与补充速率)
     * @return 令牌桶
     */
    private Bucket getOrCreateBucket(String key, RateLimit rateLimit) {
        BucketEntry entry = bucketCache.computeIfAbsent(key, k -> {
            Bandwidth bandwidth = Bandwidth.classic(rateLimit.capacity(),
                    Refill.intervally(rateLimit.refillTokens(),
                            Duration.ofSeconds(rateLimit.refillPeriodSeconds())));
            Bucket bucket = Bucket.builder().addLimit(bandwidth).build();
            return new BucketEntry(bucket);
        });
        entry.lastAccessNanos = System.nanoTime();
        return entry.bucket;
    }

    /**
     * 获取调用方标识: 优先取 {@code X-User-Id} 请求头, 为空则取客户端 IP。
     * <p>
     * 无请求上下文 (如异步线程调用) 时回退为 "unknown"。
     * </p>
     *
     * @return 用户 ID 或客户端 IP
     */
    private String getUserIdOrIp() {
        try {
            String userId = request.getHeader(USER_ID_HEADER);
            if (userId != null && !userId.isBlank()) {
                return userId;
            }
            return resolveClientIp(request);
        } catch (Exception e) {
            log.debug("获取调用方标识失败, 回退 unknown: {}", e.getMessage());
            return UNKNOWN_CALLER;
        }
    }

    /**
     * 解析客户端真实 IP, 优先从反向代理头取首个 IP。
     *
     * @param request HTTP 请求
     * @return 客户端 IP
     */
    private String resolveClientIp(HttpServletRequest request) {
        for (String header : IP_HEADERS) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value)) {
                int comma = value.indexOf(',');
                return comma > 0 ? value.substring(0, comma).trim() : value.trim();
            }
        }
        String remote = request.getRemoteAddr();
        return remote != null ? remote : UNKNOWN_CALLER;
    }

    /**
     * 清理超过 1 小时未访问的桶, 避免内存泄漏。
     */
    private void cleanupExpiredBuckets() {
        long now = System.nanoTime();
        Iterator<Map.Entry<String, BucketEntry>> iterator = bucketCache.entrySet().iterator();
        int removed = 0;
        while (iterator.hasNext()) {
            Map.Entry<String, BucketEntry> entry = iterator.next();
            if (now - entry.getValue().lastAccessNanos > BUCKET_EXPIRE_NANOS) {
                iterator.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.debug("清理过期限流桶: removed={}, remaining={}", removed, bucketCache.size());
        }
    }

    /**
     * 桶缓存条目, 持有令牌桶与最后访问时间 (纳秒)。
 * @since V1.0
     * @author Hsi Chu
     */
    private static class BucketEntry {

        /** 令牌桶 */
        private final Bucket bucket;

        /** 最后访问时间 (纳秒), 用于过期清理判断 */
        private volatile long lastAccessNanos;

        /**
         * 构造桶条目, 初始化最后访问时间为当前纳秒。
         *
         * @param bucket 令牌桶
         */
        BucketEntry(Bucket bucket) {
            this.bucket = bucket;
            this.lastAccessNanos = System.nanoTime();
        }
    }
}
