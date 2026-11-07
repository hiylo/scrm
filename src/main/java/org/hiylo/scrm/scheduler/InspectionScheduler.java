/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : InspectionScheduler.java
 * Date : 2026/10/23 10:50:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.config.ScrmInspectionProperties;
import org.hiylo.scrm.service.ScrmInspectionRunService;
import org.hiylo.scrm.service.ScrmInspectionQueryService;
import org.hiylo.scrm.service.TaxNoticeScrapeService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 工商税务巡检调度器。
 * <p>
 * 复用 Spring {@code TaskScheduler}（{@code @EnableScheduling} 已在启动类开启）:
 * 工商 API 巡检默认每天 02:30（错峰, T03 建议凌晨 02:00–06:00）, 税务公告抓取
 * 默认每天 03:00。两个任务彼此独立, 可分别调整 cron（配置项
 * {@code scrm.inspection.business-cron} / {@code scrm.inspection.tax-cron}）。
 * 用 {@code AtomicBoolean} 防重入, 上一轮未完成时跳过本轮。
 * </p>
 * <p>
 * 该调度器与 {@code scrm_scheduled_task} 统一调度中心互补: 统一调度中心负责
 * 需要人工/页面配置 cron 的任务, 本调度器提供「开箱即用」的默认巡检节奏,
 * 同时两个巡检逻辑都注册了 {@code ScheduledTaskHandler} 白名单处理器,
 * 运营也可在统一调度中心自行配置。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InspectionScheduler {

    /** 工商巡检服务 */
    private final ScrmInspectionQueryService inspectionQueryService;

    /** 税务公告抓取服务 */
    private final TaxNoticeScrapeService taxNoticeScrapeService;

    /** 巡检调度配置 */
    private final ScrmInspectionProperties inspectionProperties;

    /** 工商巡检防重入标记 */
    private final AtomicBoolean businessRunning = new AtomicBoolean(false);

    /** 税务抓取防重入标记 */
    private final AtomicBoolean taxRunning = new AtomicBoolean(false);

    /** 巡检/抓取执行历史服务 */
    private final ScrmInspectionRunService inspectionRunService;

    /**
     * 工商状态定时巡检（默认每天 02:30）。
     */
    @Scheduled(cron = "${scrm.inspection.business-cron:0 30 2 * * ?}")
    public void runBusinessInspection() {
        if (!businessRunning.compareAndSet(false, true)) {
            log.debug("[巡检调度-工商] 上一轮执行未完成, 跳过本轮");
            return;
        }
        java.time.LocalDateTime startAt = java.time.LocalDateTime.now();
        long startMs = System.currentTimeMillis();
        try {
            ScrmInspectionQueryService.BusinessSummary summary = inspectionQueryService.inspectAll();
            long cost = System.currentTimeMillis() - startMs;
            inspectionRunService.recordBusiness(summary,
                    org.hiylo.scrm.entity.ScrmInspectionRunEntity.TRIGGER_SCHEDULED, startAt, cost);
            log.info("[巡检调度-工商] 巡检完成: scanned={}, success={}, failed={}, changed={}, "
                            + "skipReason={}, costMs={}",
                    summary.taskScanned(), summary.success(), summary.failed(), summary.changed(),
                    summary.skipReason(), cost);
        } catch (Exception e) {
            log.error("[巡检调度-工商] 巡检异常: {}", e.getMessage(), e);
        } finally {
            businessRunning.set(false);
        }
    }

    /**
     * 税务公开公告定时抓取（默认每天 03:00）。
     */
    @Scheduled(cron = "${scrm.inspection.tax-cron:0 0 3 * * ?}")
    public void runTaxNoticeScrape() {
        if (!taxRunning.compareAndSet(false, true)) {
            log.debug("[巡检调度-税务] 上一轮执行未完成, 跳过本轮");
            return;
        }
        java.time.LocalDateTime startAt = java.time.LocalDateTime.now();
        long startMs = System.currentTimeMillis();
        try {
            TaxNoticeScrapeService.ScrapeSummary summary = taxNoticeScrapeService.scrapeAll();
            long cost = System.currentTimeMillis() - startMs;
            inspectionRunService.recordTax(summary,
                    org.hiylo.scrm.entity.ScrmInspectionRunEntity.TRIGGER_SCHEDULED, startAt, cost);
            log.info("[巡检调度-税务] 抓取完成: scanned={}, matched={}, captchaBlocked={}, failed={}, costMs={}",
                    summary.taskScanned(), summary.recordsMatched(), summary.captchaBlocked(),
                    summary.failed(), cost);
        } catch (Exception e) {
            log.error("[巡检调度-税务] 抓取异常: {}", e.getMessage(), e);
        } finally {
            taxRunning.set(false);
        }
    }
}