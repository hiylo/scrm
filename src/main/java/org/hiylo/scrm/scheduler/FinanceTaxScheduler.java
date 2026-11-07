/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : FinanceTaxScheduler.java
 * Date : 2026/10/23 12:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.config.ScrmPolicyPushProperties;
import org.hiylo.scrm.config.ScrmRenewalProperties;
import org.hiylo.scrm.config.ScrmTaxCalendarProperties;
import org.hiylo.scrm.service.finance.policy.ScrmPolicyPushService;
import org.hiylo.scrm.service.finance.renewal.ScrmRenewalService;
import org.hiylo.scrm.service.finance.tax.ScrmTaxDeclarationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 财税三域开箱即用调度器（报税日历 / 续费流失 / 政策推送）。
 * <p>
 * 复用 Spring {@code TaskScheduler}（{@code @EnableScheduling} 已在启动类开启）,
 * 默认节奏对齐 T08 文档: 报税记录生成每天 01:00、状态推进每天 08:00、预警推送每天
 * 09:00（配置项 {@code scrm.tax-calendar.generate-cron} / {@code advance-cron} /
 * {@code warning-cron}）; 续费扫描每天 08:00、提醒每天 09:00（{@code scrm.renewal.scan-cron}）;
 * 政策快照补算每天 03:00（{@code scrm.policy-push.refresh-cron}）。
 * 用 {@code AtomicBoolean} 防重入, 上一轮未完成时跳过本轮。与
 * {@code scrm_scheduled_task} 统一调度中心互补: 三个逻辑同时注册了
 * {@code ScheduledTaskHandler} 白名单处理器, 运营也可在统一调度中心自行配置 cron。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinanceTaxScheduler {

    /** 报税日历服务 */
    private final ScrmTaxDeclarationService taxDeclarationService;

    /** 续费流失服务 */
    private final ScrmRenewalService renewalService;

    /** 政策推送服务 */
    private final ScrmPolicyPushService policyPushService;

    /** 报税日历配置 */
    private final ScrmTaxCalendarProperties taxCalendarProperties;

    /** 续费配置 */
    private final ScrmRenewalProperties renewalProperties;

    /** 政策推送配置 */
    private final ScrmPolicyPushProperties policyPushProperties;

    /** 报税生成防重入标记 */
    private final AtomicBoolean generateRunning = new AtomicBoolean(false);

    /** 报税推进防重入标记 */
    private final AtomicBoolean advanceRunning = new AtomicBoolean(false);

    /** 报税预警防重入标记 */
    private final AtomicBoolean warningRunning = new AtomicBoolean(false);

    /** 续费扫描防重入标记 */
    private final AtomicBoolean renewalRunning = new AtomicBoolean(false);

    /** 政策快照防重入标记 */
    private final AtomicBoolean policyRunning = new AtomicBoolean(false);

    /**
     * 生成下期申报记录（默认每天 01:00）。
     */
    @Scheduled(cron = "${scrm.tax-calendar.generate-cron:0 0 1 * * ?}")
    public void generateTaxRecords() {
        if (!generateRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            int created = taxDeclarationService.generateNextRecords();
            log.info("[调度-报税生成] 完成: created={}", created);
        } catch (Exception e) {
            log.error("[调度-报税生成] 异常: {}", e.getMessage(), e);
        } finally {
            generateRunning.set(false);
        }
    }

    /**
     * 状态推进 + 逾期判定（默认每天 08:00）。
     */
    @Scheduled(cron = "${scrm.tax-calendar.advance-cron:0 0 8 * * ?}")
    public void advanceTaxStates() {
        if (!advanceRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            int[] stats = taxDeclarationService.advanceStates();
            log.info("[调度-报税推进] 完成: pending={}, overdue={}, escalated={}",
                    stats[0], stats[1], stats[2]);
        } catch (Exception e) {
            log.error("[调度-报税推进] 异常: {}", e.getMessage(), e);
        } finally {
            advanceRunning.set(false);
        }
    }

    /**
     * 报税预警推送（默认每天 09:00）。
     */
    @Scheduled(cron = "${scrm.tax-calendar.warning-cron:0 0 9 * * ?}")
    public void pushTaxWarnings() {
        if (!warningRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            int pushed = taxDeclarationService.pushWarnings();
            log.info("[调度-报税预警] 完成: pushed={}", pushed);
        } catch (Exception e) {
            log.error("[调度-报税预警] 异常: {}", e.getMessage(), e);
        } finally {
            warningRunning.set(false);
        }
    }

    /**
     * 续费到期扫描 + 任务生成 + 到期未续转 OVERDUE（默认每天 08:00）。
     */
    @Scheduled(cron = "${scrm.renewal.scan-cron:0 0 8 * * ?}")
    public void scanRenewalTasks() {
        if (!renewalRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            int generated = renewalService.scanAndGenerateTasks();
            int marked = renewalService.markExpiredTasks();
            log.info("[调度-续费扫描] 完成: generated={}, expiredMarked={}", generated, marked);
        } catch (Exception e) {
            log.error("[调度-续费扫描] 异常: {}", e.getMessage(), e);
        } finally {
            renewalRunning.set(false);
        }
    }

    /**
     * 续费三级提醒推送（默认每天 09:00）。
     */
    @Scheduled(cron = "${scrm.renewal.remind-cron:0 0 9 * * ?}")
    public void pushRenewalReminders() {
        if (!renewalRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            int pushed = renewalService.pushReminders();
            log.info("[调度-续费提醒] 完成: pushed={}", pushed);
        } catch (Exception e) {
            log.error("[调度-续费提醒] 异常: {}", e.getMessage(), e);
        } finally {
            renewalRunning.set(false);
        }
    }

    /**
     * 政策推送草稿快照补算（默认每天 03:00）。
     */
    @Scheduled(cron = "${scrm.policy-push.refresh-cron:0 0 3 * * ?}")
    public void refreshPolicySnapshots() {
        if (!policyRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            int refreshed = policyPushService.refreshDraftSnapshots();
            log.info("[调度-政策快照] 完成: refreshed={}", refreshed);
        } catch (Exception e) {
            log.error("[调度-政策快照] 异常: {}", e.getMessage(), e);
        } finally {
            policyRunning.set(false);
        }
    }
}
