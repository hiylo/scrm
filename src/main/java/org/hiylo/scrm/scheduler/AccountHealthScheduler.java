/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : AccountHealthScheduler.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.service.ScrmAccountHealthService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * SCRM 账号健康度定时检测调度器。
 * <p>
 * 提供两个定时任务:
 * <ol>
 *   <li>高频检测 (每 5 分钟): 检测所有账号健康度, 快速发现 LOGIN 账号掉线</li>
 *   <li>全量巡检 (每 6 小时): 全量检测所有账号 (含 LOGOUT / FROZEN), 兜底发现状态变化</li>
 * </ol>
 * 调度依赖 {@code @EnableScheduling} (已在 ScrmServerApplication 开启)。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountHealthScheduler {

    /** 账号健康度检测服务 */
    private final ScrmAccountHealthService accountHealthService;

    /**
     * 高频健康检测: 每 5 分钟检测一次所有账号。
     * <p>
     * 主要用于快速发现 LOGIN 状态账号掉线 (登录态变为 LOGOUT / FROZEN),
     * 单账号检测异常不影响整体流程。
     * </p>
     */
    @Scheduled(fixedDelay = 300_000)
    public void checkActiveAccounts() {
        log.info("[健康检测-高频] 开始检测所有账号");
        long startMs = System.currentTimeMillis();
        try {
            ScrmAccountHealthService.CheckSummary summary = accountHealthService.checkAllAccounts();
            long costMs = System.currentTimeMillis() - startMs;
            log.info("[健康检测-高频] 检测结束, 共检测 {} 个账号, 异常 {} 个, 耗时 {} ms",
                    summary.getCheckedCount(), summary.getErrorCount(), costMs);
        } catch (Exception e) {
            log.error("[健康检测-高频] 检测异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 全量健康巡检: 每 6 小时全量检测所有账号 (包括 LOGOUT / FROZEN)。
     * <p>
     * 作为高频检测的兜底, 周期性覆盖非 LOGIN 状态账号,
     * 发现账号恢复 (LOGOUT → LOGIN) 或冻结解除 (FROZEN → LOGIN) 等长周期变化。
     * </p>
     */
    @Scheduled(cron = "0 0 */6 * * ?")
    public void checkAllAccountsFully() {
        log.info("[健康检测-全量] 开始全量巡检所有账号 (含 LOGOUT / FROZEN)");
        long startMs = System.currentTimeMillis();
        try {
            ScrmAccountHealthService.CheckSummary summary = accountHealthService.checkAllAccounts();
            long costMs = System.currentTimeMillis() - startMs;
            log.info("[健康检测-全量] 巡检结束, 共检测 {} 个账号, 异常 {} 个, 耗时 {} ms",
                    summary.getCheckedCount(), summary.getErrorCount(), costMs);
        } catch (Exception e) {
            log.error("[健康检测-全量] 巡检异常: {}", e.getMessage(), e);
        }
    }
}
