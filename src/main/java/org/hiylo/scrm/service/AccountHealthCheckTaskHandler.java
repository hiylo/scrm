/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : AccountHealthCheckTaskHandler.java
 * Date : 2026/09/19 21:20:11
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmScheduledTaskEntity;
import org.springframework.stereotype.Component;

/**
 * 账号健康度巡检调度处理器 (白名单处理器)。
 * <p>
 * 复用 {@link ScrmAccountHealthService#checkAllAccounts()} 的真实巡检逻辑, 供
 * {@code scrm_scheduled_task} 以自定义 cron 触发 (与 {@code AccountHealthScheduler} 的固定
 * 频率互补, 便于按运营节奏调整巡检时间), 返回检测数与异常数。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountHealthCheckTaskHandler implements ScheduledTaskHandler {

    /** 账号健康度服务 */
    private final ScrmAccountHealthService accountHealthService;

    /**
     * 执行一次全量账号健康检测。
     *
     * @param task 任务配置
     * @return 检测结果说明
     */
    @Override
    public String handle(ScrmScheduledTaskEntity task) {
        ScrmAccountHealthService.CheckSummary summary = accountHealthService.checkAllAccounts();
        log.info("[调度-账号健康巡检] 完成: taskId={}, checked={}, error={}",
                task.getId(), summary.getCheckedCount(), summary.getErrorCount());
        return "checked=" + summary.getCheckedCount() + ", error=" + summary.getErrorCount();
    }
}
