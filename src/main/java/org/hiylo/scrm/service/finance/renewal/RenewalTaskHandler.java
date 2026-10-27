/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : RenewalTaskHandler.java
 * Date : 2026/10/23 12:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.renewal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmScheduledTaskEntity;
import org.hiylo.scrm.service.ScheduledTaskHandler;
import org.springframework.stereotype.Component;

/**
 * 服务续费流失调度处理器（白名单处理器, 复用 {@code scrm_scheduled_task}）。
 * <p>
 * 一次调度执行到期扫描生成任务 + 到期未续转 OVERDUE + 三级提醒推送三个环节。
 * handlerClass 写入任务表时用
 * {@code org.hiylo.scrm.service.finance.renewal.RenewalTaskHandler}。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RenewalTaskHandler implements ScheduledTaskHandler {

    /** 续费流失核心服务 */
    private final ScrmRenewalService renewalService;

    /**
     * 执行一轮续费流失调度。
     *
     * @param task 任务配置（可空）
     * @return 执行结果说明（生成/逾期/提醒汇总）
     */
    @Override
    public String handle(ScrmScheduledTaskEntity task) {
        int generated = renewalService.scanAndGenerateTasks();
        int marked = renewalService.markExpiredTasks();
        int pushed = renewalService.pushReminders();
        String result = String.format(
                "generated=%d | expiredMarked=%d | remindersPushed=%d",
                generated, marked, pushed);
        log.info("[调度-续费流失] 完成: {}", result);
        return result;
    }
}
