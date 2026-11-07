/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : InspectionRateLimiter.java
 * Date : 2026/10/23 11:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.component;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 巡检域频率令牌桶（日预算窗口）。
 * <p>
 * 第三方工商 API 与税务公开公告抓取都是低频批量场景（T02: 500 家 × 每季 1 次 ≈ 日均
 * 6 次; T03: 单站日请求 ≤ 200、每客户企业 ≤ 2 次/天）。本组件提供<b>按日预算</b>的
 * 令牌限制: 每个 {@code key}（如 省份/企业/维度）在当日窗口内的累计调用次数不得超过
 * 预算, 超限直接拒绝并记录日志 —— 作为巡检频率红线的兜底, 天然防止误配 cron 或
 * 重复入队导致的超量调用。窗口按自然日滚动清零; 计数只存内存, 单实例部署足够
 * （scrm 为单体）。
 * </p>
 * <p><b>语义</b>: {@link #tryAcquire(String, int)} 返回 false 表示当日预算用尽,
 * 调用方应跳过该次查询并记 PARTIAL, 不重试、不排队（避免打满预算的任务堆积）。</p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Component
public class InspectionRateLimiter {

    /** 当前自然日窗口 → (key → 已用次数) */
    private final Map<String, Map<String, Integer>> windows = new ConcurrentHashMap<>();

    /**
     * 尝试获取配额。
     *
     * @param key           配额键（如 GD_SCRAPE / ZJ_SCRAPE / QIXIN_API / task:{id}:qixin）
     * @param dailyBudget   当日预算（> 0）
     * @return true 表示配额充足并使用一次; false 表示当日预算已用尽
     */
    public boolean tryAcquire(String key, int dailyBudget) {
        if (dailyBudget <= 0) {
            return false;
        }
        String day = LocalDate.now().toString();
        Map<String, Integer> bucket = windows.computeIfAbsent(day, k -> new ConcurrentHashMap<>());
        int used = bucket.merge(key, 1, Integer::sum);
        if (used <= dailyBudget) {
            return true;
        }
        // 超限回滚本次计数, 避免连续超限叠加
        bucket.merge(key, -1, Integer::sum);
        if (bucket.get(key) != null && bucket.get(key) <= 0) {
            bucket.remove(key);
        }
        log.warn("[巡检限流] 当日预算用尽, 拒绝调用: key={}, day={}, budget={}", key, day, dailyBudget);
        return false;
    }

    /**
     * 查询某键当日剩余配额。
     *
     * @param key         配额键
     * @param dailyBudget 当日预算
     * @return 剩余可调用次数（≥ 0）
     */
    public int remaining(String key, int dailyBudget) {
        String day = LocalDate.now().toString();
        Map<String, Integer> bucket = windows.get(day);
        int used = bucket == null ? 0 : bucket.getOrDefault(key, 0);
        return Math.max(0, dailyBudget - used);
    }

    /**
     * 清理过期窗口（可由调度器低频调用, 防内存缓慢增长; 单实例运行数十年也仅
     * 数百个窗口, 不清理亦可接受, 此方法保留供监控排查）。
     */
    public void cleanup() {
        String today = LocalDate.now().toString();
        windows.keySet().removeIf(day -> !today.equals(day));
    }
}