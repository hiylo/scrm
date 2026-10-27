/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : PolicyPushTaskHandler.java
 * Date : 2026/10/23 12:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.policy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmScheduledTaskEntity;
import org.hiylo.scrm.service.ScheduledTaskHandler;
import org.springframework.stereotype.Component;

/**
 * 政策推送每日快照补算调度处理器（白名单处理器, 复用 {@code scrm_scheduled_task}）。
 * <p>
 * 每日对草稿期推送任务按最新标签/分群重算命中客户快照（新客户/改标签自动补入,
 * 发布直接用快照）。handlerClass 写入任务表时用
 * {@code org.hiylo.scrm.service.finance.policy.PolicyPushTaskHandler}。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PolicyPushTaskHandler implements ScheduledTaskHandler {

    /** 政策推送核心服务 */
    private final ScrmPolicyPushService policyPushService;

    /**
     * 执行一轮草稿任务快照补算。
     *
     * @param task 任务配置（可空）
     * @return 补算任务数说明
     */
    @Override
    public String handle(ScrmScheduledTaskEntity task) {
        int refreshed = policyPushService.refreshDraftSnapshots();
        String result = "draftSnapshotsRefreshed=" + refreshed;
        log.info("[调度-政策推送] 完成: {}", result);
        return result;
    }
}
