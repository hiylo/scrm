/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : TaxDeclarationTaskHandler.java
 * Date : 2026/10/23 12:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.tax;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmScheduledTaskEntity;
import org.hiylo.scrm.service.ScheduledTaskHandler;
import org.springframework.stereotype.Component;

/**
 * 报税日历调度处理器（白名单处理器, 复用 {@code scrm_scheduled_task}）。
 * <p>
 * 一次调度执行申报记录生成 + 状态推进 + 逾期预警推送三个环节（与
 * {@code FinanceTaxScheduler} 的固定频率互补, 便于按运营节奏调整 cron）。
 * handlerClass 写入任务表时用 {@code org.hiylo.scrm.service.finance.tax.TaxDeclarationTaskHandler},
 * 命中 {@link org.hiylo.scrm.service.ScheduledTaskHandlerRegistry} 白名单后执行。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaxDeclarationTaskHandler implements ScheduledTaskHandler {

    /** 报税日历核心服务 */
    private final ScrmTaxDeclarationService taxDeclarationService;

    /**
     * 执行一轮报税日历调度。
     *
     * @param task 任务配置（可空, 处理器不依赖其字段）
     * @return 执行结果说明（生成/推进/预警汇总）
     */
    @Override
    public String handle(ScrmScheduledTaskEntity task) {
        int generated = taxDeclarationService.generateNextRecords();
        int[] advanced = taxDeclarationService.advanceStates();
        int pushed = taxDeclarationService.pushWarnings();
        String result = String.format(
                "generated=%d | advanced: pending=%d, overdue=%d, escalated=%d | warningsPushed=%d",
                generated, advanced[0], advanced[1], advanced[2], pushed);
        log.info("[调度-报税日历] 完成: {}", result);
        return result;
    }
}
