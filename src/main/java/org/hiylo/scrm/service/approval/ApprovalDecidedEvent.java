/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ApprovalDecidedEvent.java
 * Date : 2026/10/27 09:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.approval;

/**
 * 审批进入终态事件。
 * <p>
 * 审批引擎此前只改实例状态、不通知任何业务方，导致业务侧（如续费）无法感知
 * 「审批到底过没过」。本事件在实例变为 APPROVED / REJECTED 时发布，
 * 由各业务域的监听器自行认领（按 {@code businessType} 分流）。
 * </p>
 * <p>
 * 发布方须在<b>事务提交后</b>发布，避免业务监听器读到尚未提交的实例状态。
 * </p>
 *
 * @param instanceId   审批实例 ID
 * @param businessType 业务类型（CONTRACT / RENEWAL / ...）
 * @param businessId   业务 ID
 * @param approved     是否通过（false = 驳回）
 * @param comment      审批意见（驳回原因等，可空）
 * @author Hsi Chu
 * @since V1.0
 */
public record ApprovalDecidedEvent(
        Long instanceId,
        String businessType,
        String businessId,
        boolean approved,
        String comment) {
}
