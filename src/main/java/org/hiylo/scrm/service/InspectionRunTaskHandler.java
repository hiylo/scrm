/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : InspectionRunTaskHandler.java
 * Date : 2026/10/23 10:40:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmScheduledTaskEntity;
import org.springframework.stereotype.Component;

/**
 * 工商税务巡检调度处理器（白名单处理器）。
 * <p>
 * 复用 {@link ScrmInspectionQueryService#inspectAll()} 与
 * {@link TaxNoticeScrapeService#scrapeAll()} 的真实巡检逻辑, 供
 * {@code scrm_scheduled_task} 以 cron 触发（与 {@code InspectionScheduler} 的固定
 * 频率互补, 便于按运营节奏调整巡检时间）。handlerClass 写入巡检任务表时用
 * {@code org.hiylo.scrm.service.InspectionRunTaskHandler}, 命中
 * {@link ScheduledTaskHandlerRegistry} 白名单后执行。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InspectionRunTaskHandler implements ScheduledTaskHandler {

    /** 工商巡检服务 */
    private final ScrmInspectionQueryService inspectionQueryService;

    /** 税务公告抓取服务 */
    private final TaxNoticeScrapeService taxNoticeScrapeService;

    /**
     * 执行一轮工商巡检 + 税务公告抓取。
     *
     * @param task 任务配置（可空, 处理器不依赖其字段）
     * @return 执行结果说明（工商 / 税务汇总）
     */
    @Override
    public String handle(ScrmScheduledTaskEntity task) {
        ScrmInspectionQueryService.BusinessSummary business = inspectionQueryService.inspectAll();
        TaxNoticeScrapeService.ScrapeSummary tax = taxNoticeScrapeService.scrapeAll();
        String result = String.format(
                "business: scanned=%d, success=%d, failed=%d, changed=%d | "
                        + "tax: scanned=%d, matched=%d, captchaBlocked=%d, failed=%d",
                business.taskScanned(), business.success(), business.failed(), business.changed(),
                tax.taskScanned(), tax.recordsMatched(), tax.captchaBlocked(), tax.failed());
        log.info("[调度-工商税务巡检] 完成: {}", result);
        return result;
    }
}