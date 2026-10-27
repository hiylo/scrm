/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmRenewalApprovalListener.java
 * Date : 2026/10/27 09:20:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.renewal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.entity.ScrmRenewalTaskEntity;
import org.hiylo.scrm.service.approval.ApprovalDecidedEvent;
import org.hiylo.scrm.service.approval.RenewalApprovalGatewayImpl;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 续费审批终态监听器。
 * <p>
 * 审批通过 → 续费任务 {@code APPROVAL_PENDING → APPROVED}；
 * 审批驳回 → 退回 {@code PENDING} 并清掉被否的折扣/赠期，等销售改方案重提。
 * </p>
 * <p>
 * 只在<b>事务提交后</b>处理（{@link TransactionPhase#AFTER_COMMIT}），
 * 保证读到的是已落库的实例状态；新开事务写入，避免与原审批事务共生死。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScrmRenewalApprovalListener {

    /** 续费服务（回填与回退） */
    private final ScrmRenewalService renewalService;

    /**
     * 认领续费类审批终态事件。
     * <p>
     * 非 RENEWAL 业务一律忽略；业务 ID 非法或任务已不存在时只记日志，
     * 绝不向外抛异常——审批本身已经完成，不能因为回调失败让它看起来失败。
     * </p>
     *
     * @param event 审批终态事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onApprovalDecided(ApprovalDecidedEvent event) {
        if (event == null
                || !RenewalApprovalGatewayImpl.BUSINESS_TYPE_RENEWAL.equals(event.businessType())) {
            return;
        }
        Long taskId = parseId(event.businessId());
        if (taskId == null) {
            log.warn("[续费-审批回调] 业务 ID 非法, 跳过: instanceId={}, businessId={}",
                    event.instanceId(), event.businessId());
            return;
        }
        try {
            if (event.approved()) {
                handleApproved(taskId, event.instanceId());
            } else {
                renewalService.rejectByApproval(taskId, event.comment());
            }
        } catch (RuntimeException e) {
            // 回调失败不影响审批结果本身，留日志供人工排查
            log.error("[续费-审批回调] 处理失败: taskId={}, instanceId={}, approved={}",
                    taskId, event.instanceId(), event.approved(), e);
        }
    }

    /**
     * 审批通过：回填实例 ID 并转为已批准。
     * <p>
     * 折扣/赠期/金额已在任务创建时写入（续费任务保存的就是申请值），
     * 此处不再重复覆盖，只推进状态与关联。
     * </p>
     *
     * @param taskId     续费任务 ID
     * @param instanceId 审批实例 ID
     */
    private void handleApproved(Long taskId, Long instanceId) {
        ScrmRenewalTaskEntity task = renewalService.findByIdForApproval(taskId);
        if (task == null) {
            log.warn("[续费-审批回调] 任务不存在, 跳过: taskId={}", taskId);
            return;
        }
        if (!ScrmRenewalService.STATUS_APPROVAL_PENDING.equals(task.getStatus())) {
            // 幂等：人工可能已通过接口处理过
            log.info("[续费-审批回调] 状态非待审批, 跳过: taskId={}, status={}", taskId, task.getStatus());
            return;
        }
        renewalService.approve(taskId, task.getDiscountPct(), task.getGraceMonths(),
                task.getRenewalAmount(), instanceId);
        log.info("[续费-审批回调] 通过回填完成: taskId={}, instanceId={}", taskId, instanceId);
    }

    /**
     * 解析业务 ID。
     *
     * @param raw 原始字符串
     * @return 解析结果，非法返回 null
     */
    private Long parseId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
