/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : TaxNoticeScrapeTaskHandler.java
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
 * 税务公告抓取调度处理器（白名单处理器）。
 * <p>
 * 只执行 {@link TaxNoticeScrapeService#scrapeAll()} 的税务公告抓取, 供
 * {@code scrm_scheduled_task} 独立配置 cron（如 1 次/天错峰）, 与合并巡检任务解耦。
 * handlerClass 写入任务表时用 {@code org.hiylo.scrm.service.TaxNoticeScrapeTaskHandler}。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaxNoticeScrapeTaskHandler implements ScheduledTaskHandler {

    /** 税务公告抓取服务 */
    private final TaxNoticeScrapeService taxNoticeScrapeService;

    /**
     * 执行一轮税务公告抓取。
     *
     * @param task 任务配置（可空, 处理器不依赖其字段）
     * @return 抓取汇总说明
     */
    @Override
    public String handle(ScrmScheduledTaskEntity task) {
        TaxNoticeScrapeService.ScrapeSummary summary = taxNoticeScrapeService.scrapeAll();
        String result = String.format(
                "tax scrape: scanned=%d, matched=%d, captchaBlocked=%d, failed=%d",
                summary.taskScanned(), summary.recordsMatched(),
                summary.captchaBlocked(), summary.failed());
        log.info("[调度-税务公告抓取] 完成: {}", result);
        return result;
    }
}