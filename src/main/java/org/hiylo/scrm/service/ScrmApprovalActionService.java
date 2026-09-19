/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmApprovalActionService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmApprovalActionDto;
import org.hiylo.scrm.entity.ScrmApprovalFlowEntity;
import org.hiylo.scrm.entity.ScrmApprovalInstanceEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmApprovalInstanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SCRM 审批操作服务。
 * <p>
 * 承载审批动作子域: 同意 / 驳回 / 转交 / 加签 / 抄送 / 评论, 维护节点流转前对操作参数
 * (实例 ID / 操作类型匹配 / 操作人) 的校验与操作人可审批权限校验。节点处理与流转引擎
 * 委托给 {@link ScrmApprovalInstanceService}, 日志写入委托给 {@link ScrmApprovalLogService}。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmApprovalActionService {

    /** 审批实例数据访问层 */
    private final ScrmApprovalInstanceRepository instanceRepository;

    /** 审批流程定义服务 (流程查找与 JSON 解析) */
    private final ScrmApprovalFlowService flowService;

    /** 审批实例与流转服务 (实例查找 / 常量 / 节点历史 / 处理与流转) */
    private final ScrmApprovalInstanceService instanceService;

    /** 审批操作日志服务 (日志记录) */
    private final ScrmApprovalLogService logService;

    // ============================================================
    // 审批操作
    // ============================================================

    /**
     * 同意审批 (记录日志 → 检查是否最后节点 → 流转或完成)。
     *
     * @param actionDto 操作参数
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法 / 操作人无权审批
     */
    @Transactional
    public ScrmApprovalInstanceEntity approve(ScrmApprovalActionDto actionDto) throws ScrmException {
        validateActionDto(actionDto, ScrmApprovalInstanceService.ACTION_APPROVE);
        ScrmApprovalInstanceEntity instance = instanceService.findInstanceOrThrow(actionDto.getInstanceId());
        ensureApprovable(instance, actionDto.getOperatorId());
        return instanceService.processNode(instance, actionDto);
    }

    /**
     * 驳回审批 (记录日志 → 终止流程 → 通知申请人)。
     *
     * @param actionDto 操作参数
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法 / 操作人无权审批
     */
    @Transactional
    public ScrmApprovalInstanceEntity reject(ScrmApprovalActionDto actionDto) throws ScrmException {
        validateActionDto(actionDto, ScrmApprovalInstanceService.ACTION_REJECT);
        ScrmApprovalInstanceEntity instance = instanceService.findInstanceOrThrow(actionDto.getInstanceId());
        ensureApprovable(instance, actionDto.getOperatorId());
        String previousNodeId = instance.getCurrentNodeId();
        instance.setStatus(ScrmApprovalInstanceService.INSTANCE_REJECTED);
        instance.setRejectedBy(actionDto.getOperatorId());
        instance.setRejectedReason(actionDto.getComment());
        instance.setCompletedAt(LocalDateTime.now());
        instance.setApprovedAt(LocalDateTime.now());
        instance.setDurationHours(instanceService.calcDurationHours(instance.getStartedAt(), instance.getCompletedAt()));
        instance.setLastActivityAt(LocalDateTime.now());
        instance = instanceRepository.save(instance);
        instanceService.appendNodeHistory(instance, previousNodeId, actionDto.getOperatorId(),
                actionDto.getOperatorName(), ScrmApprovalInstanceService.ACTION_REJECT, actionDto.getComment());
        instance = instanceRepository.save(instance);
        logService.recordLog(instance, previousNodeId, instance.getCurrentNodeName(), instance.getCurrentNodeType(),
                ScrmApprovalInstanceService.ACTION_REJECT, actionDto.getOperatorId(), actionDto.getOperatorName(),
                actionDto.getOperatorRole(), ScrmApprovalInstanceService.OPERATOR_APPROVER, actionDto.getComment(),
                actionDto.getActionData(), previousNodeId, null, false);
        log.info("驳回审批: instanceId={}, operatorId={}", instance.getId(), actionDto.getOperatorId());
        return instance;
    }

    /**
     * 转交审批 (记录日志 → 更换审批人, 流程不前进)。
     *
     * @param actionDto 操作参数 (actionData.transferredTo 为转交目标人 ID)
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法 / 流程不允许转交
     */
    @Transactional
    public ScrmApprovalInstanceEntity transfer(ScrmApprovalActionDto actionDto) throws ScrmException {
        validateActionDto(actionDto, ScrmApprovalInstanceService.ACTION_TRANSFER);
        ScrmApprovalInstanceEntity instance = instanceService.findInstanceOrThrow(actionDto.getInstanceId());
        ensureApprovable(instance, actionDto.getOperatorId());
        ScrmApprovalFlowEntity flow = flowService.findFlowOrThrow(instance.getFlowId());
        if (!Boolean.TRUE.equals(flow.getAllowDelegation())) {
            throw ScrmException.conflict("当前流程不允许转交: flowId=" + flow.getId());
        }
        Map<String, Object> actionData = flowService.parseJsonMap(actionDto.getActionData(), "actionData");
        String transferredTo = flowService.toStr(actionData.get("transferredTo"));
        if (transferredTo.isEmpty()) {
            throw ScrmException.badRequest("转交目标人不能为空");
        }
        // 更新当前审批人列表: 移除操作人, 加入转交目标人
        List<String> approvers = new ArrayList<>(instanceService.parseApproverIds(instance.getCurrentApproverIds()));
        approvers.remove(actionDto.getOperatorId());
        if (!approvers.contains(transferredTo)) {
            approvers.add(transferredTo);
        }
        instance.setCurrentApproverIds(instanceService.joinApproverIds(approvers));
        instance.setStatus(ScrmApprovalInstanceService.INSTANCE_TRANSFERRED);
        // 转交后恢复为审批中, 等待新审批人处理
        instance.setStatus(ScrmApprovalInstanceService.INSTANCE_APPROVING);
        instance.setLastActivityAt(LocalDateTime.now());
        instance = instanceRepository.save(instance);
        instanceService.appendNodeHistory(instance, instance.getCurrentNodeId(), actionDto.getOperatorId(),
                actionDto.getOperatorName(), ScrmApprovalInstanceService.ACTION_TRANSFER, actionDto.getComment());
        instance = instanceRepository.save(instance);
        logService.recordLog(instance, instance.getCurrentNodeId(), instance.getCurrentNodeName(),
                instance.getCurrentNodeType(),
                ScrmApprovalInstanceService.ACTION_TRANSFER, actionDto.getOperatorId(), actionDto.getOperatorName(),
                actionDto.getOperatorRole(), ScrmApprovalInstanceService.OPERATOR_APPROVER, actionDto.getComment(),
                actionDto.getActionData(), instance.getCurrentNodeId(), instance.getCurrentNodeId(), false);
        log.info("转交审批: instanceId={}, from={}, to={}", instance.getId(), actionDto.getOperatorId(), transferredTo);
        return instance;
    }

    /**
     * 加签 (记录日志 → 增加审批人, 流程不前进)。
     *
     * @param actionDto 操作参数 (actionData.countersignUsers 为加签用户 ID 列表, 逗号分隔)
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法 / 流程不允许加签
     */
    @Transactional
    public ScrmApprovalInstanceEntity countersign(ScrmApprovalActionDto actionDto) throws ScrmException {
        validateActionDto(actionDto, ScrmApprovalInstanceService.ACTION_COUNTERSIGN);
        ScrmApprovalInstanceEntity instance = instanceService.findInstanceOrThrow(actionDto.getInstanceId());
        ensureApprovable(instance, actionDto.getOperatorId());
        ScrmApprovalFlowEntity flow = flowService.findFlowOrThrow(instance.getFlowId());
        if (!Boolean.TRUE.equals(flow.getAllowCountersign())) {
            throw ScrmException.conflict("当前流程不允许加签: flowId=" + flow.getId());
        }
        Map<String, Object> actionData = flowService.parseJsonMap(actionDto.getActionData(), "actionData");
        String countersignUsers = flowService.toStr(actionData.get("countersignUsers"));
        if (countersignUsers.isEmpty()) {
            throw ScrmException.badRequest("加签用户列表不能为空");
        }
        // 合并加签人到当前审批人列表
        List<String> approvers = new ArrayList<>(instanceService.parseApproverIds(instance.getCurrentApproverIds()));
        for (String user : countersignUsers.split(",")) {
            String trimmed = user.trim();
            if (!trimmed.isEmpty() && !approvers.contains(trimmed)) {
                approvers.add(trimmed);
            }
        }
        instance.setCurrentApproverIds(instanceService.joinApproverIds(approvers));
        instance.setLastActivityAt(LocalDateTime.now());
        instance = instanceRepository.save(instance);
        instanceService.appendNodeHistory(instance, instance.getCurrentNodeId(), actionDto.getOperatorId(),
                actionDto.getOperatorName(), ScrmApprovalInstanceService.ACTION_COUNTERSIGN, actionDto.getComment());
        instance = instanceRepository.save(instance);
        logService.recordLog(instance, instance.getCurrentNodeId(), instance.getCurrentNodeName(),
                instance.getCurrentNodeType(),
                ScrmApprovalInstanceService.ACTION_COUNTERSIGN, actionDto.getOperatorId(), actionDto.getOperatorName(),
                actionDto.getOperatorRole(), ScrmApprovalInstanceService.OPERATOR_APPROVER, actionDto.getComment(),
                actionDto.getActionData(), instance.getCurrentNodeId(), instance.getCurrentNodeId(), false);
        log.info("加签审批: instanceId={}, operatorId={}, added={}",
                instance.getId(), actionDto.getOperatorId(), countersignUsers);
        return instance;
    }

    /**
     * 抄送 (记录日志 → 通知抄送人, 流程不前进)。
     *
     * @param actionDto 操作参数 (actionData.ccUsers 为抄送人 ID 列表, 逗号分隔)
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法
     */
    @Transactional
    public ScrmApprovalInstanceEntity cc(ScrmApprovalActionDto actionDto) throws ScrmException {
        validateActionDto(actionDto, ScrmApprovalInstanceService.ACTION_CC);
        ScrmApprovalInstanceEntity instance = instanceService.findInstanceOrThrow(actionDto.getInstanceId());
        Map<String, Object> actionData = flowService.parseJsonMap(actionDto.getActionData(), "actionData");
        String ccUsers = flowService.toStr(actionData.get("ccUsers"));
        if (ccUsers.isEmpty()) {
            throw ScrmException.badRequest("抄送人列表不能为空");
        }
        // 合并抄送人到实例 ccUsers
        List<String> existing = instanceService.parseApproverIds(instance.getCcUsers());
        for (String user : ccUsers.split(",")) {
            String trimmed = user.trim();
            if (!trimmed.isEmpty() && !existing.contains(trimmed)) {
                existing.add(trimmed);
            }
        }
        instance.setCcUsers(instanceService.joinApproverIds(existing));
        instance.setNotifiedAt(LocalDateTime.now());
        instance.setLastActivityAt(LocalDateTime.now());
        instance = instanceRepository.save(instance);
        logService.recordLog(instance, instance.getCurrentNodeId(), instance.getCurrentNodeName(),
                instance.getCurrentNodeType(),
                ScrmApprovalInstanceService.ACTION_CC, actionDto.getOperatorId(), actionDto.getOperatorName(),
                actionDto.getOperatorRole(), ScrmApprovalInstanceService.OPERATOR_APPROVER, actionDto.getComment(),
                actionDto.getActionData(), null, null, false);
        log.info("抄送审批: instanceId={}, ccUsers={}", instance.getId(), ccUsers);
        return instance;
    }

    /**
     * 评论 (记录意见, 不改变流程)。
     *
     * @param actionDto 操作参数
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法
     */
    @Transactional
    public ScrmApprovalInstanceEntity comment(ScrmApprovalActionDto actionDto) throws ScrmException {
        validateActionDto(actionDto, ScrmApprovalInstanceService.ACTION_COMMENT);
        ScrmApprovalInstanceEntity instance = instanceService.findInstanceOrThrow(actionDto.getInstanceId());
        if (ScrmApprovalInstanceService.INSTANCE_APPROVED.equals(instance.getStatus())
                || ScrmApprovalInstanceService.INSTANCE_REJECTED.equals(instance.getStatus())
                || ScrmApprovalInstanceService.INSTANCE_CANCELLED.equals(instance.getStatus())
                || ScrmApprovalInstanceService.INSTANCE_WITHDRAWN.equals(instance.getStatus())) {
            throw ScrmException.conflict(
                    "已结束的实例不可评论: id=" + instance.getId() + ", status=" + instance.getStatus());
        }
        instance.setLastActivityAt(LocalDateTime.now());
        instance = instanceRepository.save(instance);
        logService.recordLog(instance, instance.getCurrentNodeId(), instance.getCurrentNodeName(),
                instance.getCurrentNodeType(),
                ScrmApprovalInstanceService.ACTION_COMMENT, actionDto.getOperatorId(), actionDto.getOperatorName(),
                actionDto.getOperatorRole(), ScrmApprovalInstanceService.OPERATOR_APPROVER, actionDto.getComment(),
                actionDto.getActionData(), null, null, false);
        log.info("评论审批: instanceId={}, operatorId={}", instance.getId(), actionDto.getOperatorId());
        return instance;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 校验审批操作参数。
     *
     * @param actionDto   操作参数
     * @param actionType  期望操作类型
     * @throws ScrmException 参数非法
     */
    private void validateActionDto(ScrmApprovalActionDto actionDto, String actionType) throws ScrmException {
        if (actionDto == null) {
            throw ScrmException.badRequest("操作参数不能为空");
        }
        if (actionDto.getInstanceId() == null) {
            throw ScrmException.badRequest("实例 ID 不能为空");
        }
        if (actionDto.getAction() == null || actionDto.getAction().isBlank()) {
            throw ScrmException.badRequest("操作类型不能为空");
        }
        if (!actionType.equals(actionDto.getAction())) {
            throw ScrmException.badRequest("操作类型不匹配: 期望 " + actionType + ", 实际 " + actionDto.getAction());
        }
        if (actionDto.getOperatorId() == null || actionDto.getOperatorId().isBlank()) {
            throw ScrmException.badRequest("操作人 ID 不能为空");
        }
    }

    /**
     * 校验实例可审批 (状态为 PENDING / APPROVING, 操作人在当前审批人列表中)。
     *
     * @param instance    实例实体
     * @param operatorId  操作人 ID
     * @throws ScrmException 状态非法 / 操作人无权审批
     */
    private void ensureApprovable(ScrmApprovalInstanceEntity instance, String operatorId) throws ScrmException {
        if (!ScrmApprovalInstanceService.INSTANCE_PENDING.equals(instance.getStatus())
                && !ScrmApprovalInstanceService.INSTANCE_APPROVING.equals(instance.getStatus())) {
            throw ScrmException.conflict(
                    "实例当前状态不可审批: id=" + instance.getId() + ", status=" + instance.getStatus());
        }
        List<String> approvers = instanceService.parseApproverIds(instance.getCurrentApproverIds());
        if (!approvers.contains(operatorId)) {
            throw ScrmException.forbidden(
                    "操作人不在当前审批人列表中: instanceId=" + instance.getId() + ", operatorId=" + operatorId);
        }
    }
}