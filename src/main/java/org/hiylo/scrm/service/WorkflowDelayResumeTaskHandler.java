/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : WorkflowDelayResumeTaskHandler.java
 * Date : 2026/09/19 21:20:11
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmScheduledTaskEntity;
import org.hiylo.scrm.scheduler.WorkflowDelayResumeScheduler;
import org.springframework.stereotype.Component;

/**
 * 工作流延迟实例恢复调度处理器 (白名单处理器)。
 * <p>
 * 与 {@link WorkflowDelayResumeScheduler} 的定时扫描等价, 供 {@code scrm_scheduled_task} 以
 * 自定义 cron / 参数触发同一份恢复逻辑 (如只在业务低峰时段恢复), 返回本轮恢复的实例数。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WorkflowDelayResumeTaskHandler implements ScheduledTaskHandler {

    /** 工作流延迟恢复调度器 */
    private final WorkflowDelayResumeScheduler delayResumeScheduler;

    /**
     * 扫描并恢复延迟到期的工作流实例。
     *
     * @param task 任务配置
     * @return 本轮恢复实例数
     */
    @Override
    public String handle(ScrmScheduledTaskEntity task) {
        int resumed = delayResumeScheduler.resumeDueInstances();
        log.info("[调度-工作流延迟恢复] 完成: taskId={}, resumed={}", task.getId(), resumed);
        return "resumed=" + resumed;
    }
}
