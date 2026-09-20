/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : WorkflowDelayResumeScheduler.java
 * Date : 2026/09/19 21:20:11
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmWorkflowInstanceEntity;
import org.hiylo.scrm.repository.ScrmWorkflowInstanceRepository;
import org.hiylo.scrm.service.ScrmWorkflowService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 工作流延迟节点恢复调度器。
 * <p>
 * DELAY 节点把实例置为 WAITING 并登记 {@code nextExecutionAt}, 本调度器按
 * {@code scrm.workflow.delay-resume-interval-ms} 配置的间隔 (默认 60 秒) 扫描到期实例,
 * 逐个交由 {@link ScrmWorkflowService#resumeDelayedInstance} 抢占并从下一条边继续流转,
 * 使延迟节点真正生效。
 * </p>
 * <p>
 * 使用 fixedDelay 天然避免任务重叠, 并辅以 {@link AtomicBoolean} 防重入双重保险;
 * 多实例部署下同一到期实例可能被多个节点同时扫到, 由 status + version 条件更新保证仅一个节点恢复。
 * 单实例恢复失败只记录错误日志, 不中断本轮扫描; 调度依赖 {@code @EnableScheduling}
 * (已在 ScrmServerApplication 开启)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowDelayResumeScheduler {

    /** 单轮扫描恢复的实例数上限, 避免延迟实例集中到期时单轮耗时过长 */
    private static final int BATCH_LIMIT = 50;

    /** 工作流服务 */
    private final ScrmWorkflowService workflowService;

    /** 工作流实例数据访问层 */
    private final ScrmWorkflowInstanceRepository instanceRepository;

    /** 防重入标记: 上一轮扫描未完成时跳过本轮, 避免任务重叠 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 定时恢复延迟到期的工作流实例。
     * <p>默认每 60 秒扫描一次, 可通过 {@code scrm.workflow.delay-resume-interval-ms} 调整。</p>
     *
     * @return 本轮成功恢复 (含被其他节点抢先恢复后跳过) 的实例数
     */
    @Scheduled(fixedDelayString = "${scrm.workflow.delay-resume-interval-ms:60000}")
    public int resumeDueInstances() {
        if (!running.compareAndSet(false, true)) {
            log.debug("[工作流延迟恢复] 上一轮执行未完成, 跳过本轮");
            return 0;
        }
        try {
            return doResumeDueInstances();
        } catch (Exception e) {
            log.error("[工作流延迟恢复] 扫描异常: {}", e.getMessage(), e);
            return 0;
        } finally {
            running.set(false);
        }
    }

    /**
     * 执行一轮到期实例扫描与恢复。
     *
     * @return 成功恢复的实例数
     */
    private int doResumeDueInstances() {
        Pageable limit = PageRequest.of(0, BATCH_LIMIT);
        List<ScrmWorkflowInstanceEntity> dueInstances =
                instanceRepository.findDueWaitingInstances(LocalDateTime.now(), limit);
        if (dueInstances.isEmpty()) {
            log.debug("[工作流延迟恢复] 无到期实例, 跳过");
            return 0;
        }
        log.info("[工作流延迟恢复] 扫描命中: count={}", dueInstances.size());
        int resumed = 0;
        for (ScrmWorkflowInstanceEntity instance : dueInstances) {
            try {
                ScrmWorkflowInstanceEntity result = workflowService.resumeDelayedInstance(
                        instance.getId(), instance.getVersion());
                if (result != null) {
                    resumed++;
                }
            } catch (Exception e) {
                log.error("[工作流延迟恢复] 实例 {} 恢复异常: {}", instance.getId(), e.getMessage(), e);
            }
        }
        log.info("[工作流延迟恢复] 本轮扫描完成: scanned={}, resumed={}", dueInstances.size(), resumed);
        return resumed;
    }
}
