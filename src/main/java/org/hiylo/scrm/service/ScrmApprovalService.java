/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmApprovalService.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.dto.ScrmApprovalActionDto;
import org.hiylo.scrm.dto.ScrmApprovalFlowDto;
import org.hiylo.scrm.dto.ScrmApprovalSubmitDto;
import org.hiylo.scrm.entity.ScrmApprovalFlowEntity;
import org.hiylo.scrm.entity.ScrmApprovalInstanceEntity;
import org.hiylo.scrm.entity.ScrmApprovalLogEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 审批工作流服务 (门面)。
 * <p>
 * 作为审批模块的统一入口, 保持对外 public 方法签名不变, 实际能力按子域委托给
 * {@link ScrmApprovalFlowService} (流程定义)、{@link ScrmApprovalInstanceService} (实例与流转)、
 * {@link ScrmApprovalActionService} (审批操作)、{@link ScrmApprovalLogService} (操作日志)
 * 与 {@link ScrmApprovalStatsService} (统计分析)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
public class ScrmApprovalService {

    /** 审批流定义子域服务 */
    private final ScrmApprovalFlowService flowService;
    /** 审批实例与流转子域服务 */
    private final ScrmApprovalInstanceService instanceService;
    /** 审批操作子域服务 */
    private final ScrmApprovalActionService actionService;
    /** 操作日志子域服务 */
    private final ScrmApprovalLogService logService;
    /** 统计分析子域服务 */
    private final ScrmApprovalStatsService statsService;

    // ============================================================
    // 流程管理
    // ============================================================

    /**
     * 创建审批流程定义。
     *
     * @param dto 流程参数
     * @return 创建后的流程
     * @throws ScrmException 参数非法 / 编码重复
     */
    public ScrmApprovalFlowEntity createFlow(ScrmApprovalFlowDto dto) throws ScrmException {
        return flowService.createFlow(dto);
    }

    /**
     * 更新审批流程。
     *
     * @param id  流程 ID
     * @param dto 流程参数
     * @return 更新后的流程
     * @throws ScrmException 流程不存在 / 参数非法 / 状态非法 / 编码重复
     */
    public ScrmApprovalFlowEntity updateFlow(Long id, ScrmApprovalFlowDto dto) throws ScrmException {
        return flowService.updateFlow(id, dto);
    }

    /**
     * 删除审批流程。
     *
     * @param id 流程 ID
     * @throws ScrmException 流程不存在 / 状态非法
     */
    public void deleteFlow(Long id) throws ScrmException {
        flowService.deleteFlow(id);
    }

    /**
     * 查询流程详情。
     *
     * @param id 流程 ID
     * @return 流程实体
     * @throws ScrmException 流程不存在
     */
    public ScrmApprovalFlowEntity getFlow(Long id) throws ScrmException {
        return flowService.getFlow(id);
    }

    /**
     * 按编码查询流程。
     *
     * @param code 流程编码
     * @return 流程实体
     * @throws ScrmException 流程不存在
     */
    public ScrmApprovalFlowEntity getFlowByCode(String code) throws ScrmException {
        return flowService.getFlowByCode(code);
    }

    /**
     * 分页查询流程。
     *
     * @param flowType 流程类型过滤（可空）
     * @param status   状态过滤（可空）
     * @param keyword  流程名称关键字模糊匹配（可空）
     * @param pageable 分页参数
     * @return 流程分页结果
     */
    public Page<ScrmApprovalFlowEntity> listFlows(String flowType, String status, String keyword, Pageable pageable) {
        return flowService.listFlows(flowType, status, keyword, pageable);
    }

    /**
     * 激活流程。
     *
     * @param id 流程 ID
     * @return 更新后的流程
     * @throws ScrmException 流程不存在 / 状态非法
     */
    public ScrmApprovalFlowEntity activateFlow(Long id) throws ScrmException {
        return flowService.activateFlow(id);
    }

    /**
     * 停用流程。
     *
     * @param id 流程 ID
     * @return 更新后的流程
     * @throws ScrmException 流程不存在 / 状态非法
     */
    public ScrmApprovalFlowEntity deactivateFlow(Long id) throws ScrmException {
        return flowService.deactivateFlow(id);
    }

    /**
     * 设置为默认流程。
     *
     * @param id 流程 ID
     * @return 更新后的流程
     * @throws ScrmException 流程不存在 / 状态非法
     */
    public ScrmApprovalFlowEntity setDefault(Long id) throws ScrmException {
        return flowService.setDefault(id);
    }

    /**
     * 复制流程。
     *
     * @param id      流程 ID
     * @param newCode 新流程编码
     * @return 复制后的流程
     * @throws ScrmException 流程不存在 / 编码重复
     */
    public ScrmApprovalFlowEntity copyFlow(Long id, String newCode) throws ScrmException {
        return flowService.copyFlow(id, newCode);
    }

    /**
     * 验证流程。
     *
     * @param id 流程 ID
     * @return 验证结果 Map
     * @throws ScrmException 流程不存在
     */
    public Map<String, Object> validateFlow(Long id) throws ScrmException {
        return flowService.validateFlow(id);
    }

    /**
     * 按业务类型获取默认流程。
     *
     * @param businessType 业务类型
     * @return 流程实体
     * @throws ScrmException 业务类型非法 / 流程不存在
     */
    public ScrmApprovalFlowEntity getFlowByBusinessType(String businessType) throws ScrmException {
        return flowService.getFlowByBusinessType(businessType);
    }

    /**
     * 递增流程使用次数。
     *
     * @param id 流程 ID
     */
    public void incrementUsage(Long id) {
        flowService.incrementUsage(id);
    }

    // ============================================================
    // 实例管理
    // ============================================================

    /**
     * 提交审批。
     *
     * @param submitDto 提交参数
     * @return 创建后的实例
     * @throws ScrmException 流程不存在 / 状态非法 / 参数非法
     */
    public ScrmApprovalInstanceEntity submit(ScrmApprovalSubmitDto submitDto) throws ScrmException {
        return instanceService.submit(submitDto);
    }

    /**
     * 查询实例详情。
     *
     * @param id 实例 ID
     * @return 实例实体
     * @throws ScrmException 实例不存在
     */
    public ScrmApprovalInstanceEntity getInstance(Long id) throws ScrmException {
        return instanceService.getInstance(id);
    }

    /**
     * 按实例编号查询实例。
     *
     * @param instanceNo 实例编号
     * @return 实例实体
     * @throws ScrmException 实例不存在
     */
    public ScrmApprovalInstanceEntity getInstanceByNo(String instanceNo) throws ScrmException {
        return instanceService.getInstanceByNo(instanceNo);
    }

    /**
     * 分页查询实例。
     *
     * @param flowId       流程 ID 过滤（可空）
     * @param businessType 业务类型过滤（可空）
     * @param status       状态过滤（可空）
     * @param applicantId  申请人 ID 过滤（可空）
     * @param approverId   审批人 ID 过滤（可空）
     * @param isUrgent     是否加急过滤（可空）
     * @param startTime    开始时间起始 (含, 可空)
     * @param endTime      开始时间截止 (含, 可空)
     * @param pageable     分页参数
     * @return 实例分页结果
     */
    public Page<ScrmApprovalInstanceEntity> listInstances(Long flowId, String businessType, String status,
                                                            String applicantId, String approverId, Boolean isUrgent,
                                                            LocalDateTime startTime, LocalDateTime endTime,
                                                            Pageable pageable) {
        return instanceService.listInstances(flowId, businessType, status, applicantId, approverId, isUrgent,
                startTime, endTime, pageable);
    }

    /**
     * 我的待审批。
     *
     * @param approverId 审批人 ID
     * @param pageable   分页参数
     * @return 实例分页结果
     */
    public Page<ScrmApprovalInstanceEntity> getMyPending(String approverId, Pageable pageable) {
        return instanceService.getMyPending(approverId, pageable);
    }

    /**
     * 我提交的。
     *
     * @param applicantId 申请人 ID
     * @param status      状态过滤（可空）
     * @param pageable    分页参数
     * @return 实例分页结果
     */
    public Page<ScrmApprovalInstanceEntity> getMySubmitted(String applicantId, String status, Pageable pageable) {
        return instanceService.getMySubmitted(applicantId, status, pageable);
    }

    /**
     * 我已审批的。
     *
     * @param approverId 审批人 ID
     * @param pageable   分页参数
     * @return 实例分页结果
     */
    public Page<ScrmApprovalInstanceEntity> getMyApproved(String approverId, Pageable pageable) {
        return instanceService.getMyApproved(approverId, pageable);
    }

    /**
     * 撤回审批。
     *
     * @param id          实例 ID
     * @param applicantId 申请人 ID
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 申请人不匹配 / 状态非法
     */
    public ScrmApprovalInstanceEntity withdraw(Long id, String applicantId) throws ScrmException {
        return instanceService.withdraw(id, applicantId);
    }

    /**
     * 催办审批。
     *
     * @param id        实例 ID
     * @param urgerId   催办人 ID
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法
     */
    public ScrmApprovalInstanceEntity urge(Long id, String urgerId) throws ScrmException {
        return instanceService.urge(id, urgerId);
    }

    /**
     * 标记加急。
     *
     * @param id     实例 ID
     * @param reason 加急原因
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法 / 流程不允许加急
     */
    public ScrmApprovalInstanceEntity markUrgent(Long id, String reason) throws ScrmException {
        return instanceService.markUrgent(id, reason);
    }

    /**
     * 取消审批。
     *
     * @param id     实例 ID
     * @param reason 取消原因
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法
     */
    public ScrmApprovalInstanceEntity cancel(Long id, String reason) throws ScrmException {
        return instanceService.cancel(id, reason);
    }

    // ============================================================
    // 审批操作
    // ============================================================

    /**
     * 同意审批。
     *
     * @param actionDto 操作参数
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法 / 操作人无权审批
     */
    public ScrmApprovalInstanceEntity approve(ScrmApprovalActionDto actionDto) throws ScrmException {
        return actionService.approve(actionDto);
    }

    /**
     * 驳回审批。
     *
     * @param actionDto 操作参数
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法 / 操作人无权审批
     */
    public ScrmApprovalInstanceEntity reject(ScrmApprovalActionDto actionDto) throws ScrmException {
        return actionService.reject(actionDto);
    }

    /**
     * 转交审批。
     *
     * @param actionDto 操作参数
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法 / 流程不允许转交
     */
    public ScrmApprovalInstanceEntity transfer(ScrmApprovalActionDto actionDto) throws ScrmException {
        return actionService.transfer(actionDto);
    }

    /**
     * 加签。
     *
     * @param actionDto 操作参数
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法 / 流程不允许加签
     */
    public ScrmApprovalInstanceEntity countersign(ScrmApprovalActionDto actionDto) throws ScrmException {
        return actionService.countersign(actionDto);
    }

    /**
     * 抄送。
     *
     * @param actionDto 操作参数
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法
     */
    public ScrmApprovalInstanceEntity cc(ScrmApprovalActionDto actionDto) throws ScrmException {
        return actionService.cc(actionDto);
    }

    /**
     * 评论。
     *
     * @param actionDto 操作参数
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法
     */
    public ScrmApprovalInstanceEntity comment(ScrmApprovalActionDto actionDto) throws ScrmException {
        return actionService.comment(actionDto);
    }

    /**
     * 处理当前节点。
     *
     * @param instance  实例实体
     * @param actionDto 操作参数
     * @return 更新后的实例
     * @throws ScrmException 节点不存在 / 流转失败
     */
    public ScrmApprovalInstanceEntity processNode(ScrmApprovalInstanceEntity instance,
                                                    ScrmApprovalActionDto actionDto) throws ScrmException {
        return instanceService.processNode(instance, actionDto);
    }

    /**
     * 流转到下一节点。
     *
     * @param instanceId 实例 ID
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 流程不存在 / 节点不存在
     */
    public ScrmApprovalInstanceEntity moveToNextNode(Long instanceId) throws ScrmException {
        return instanceService.moveToNextNode(instanceId);
    }

    /**
     * 检查自动审批条件。
     *
     * @param node     节点 Map
     * @param instance 实例实体
     * @return 是否触发自动审批
     */
    public boolean checkAutoApprove(Map<String, Object> node, ScrmApprovalInstanceEntity instance) {
        return instanceService.checkAutoApprove(node, instance);
    }

    /**
     * 处理超时。
     *
     * @param instanceId 实例 ID
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法
     */
    public ScrmApprovalInstanceEntity handleTimeout(Long instanceId) throws ScrmException {
        return instanceService.handleTimeout(instanceId);
    }

    // ============================================================
    // 操作日志
    // ============================================================

    /**
     * 查询日志详情。
     *
     * @param id 日志 ID
     * @return 日志实体
     * @throws ScrmException 日志不存在
     */
    public ScrmApprovalLogEntity getLog(Long id) throws ScrmException {
        return logService.getLog(id);
    }

    /**
     * 分页查询实例的日志。
     *
     * @param instanceId 实例 ID
     * @param pageable  分页参数
     * @return 日志分页结果
     * @throws ScrmException 实例不存在
     */
    public Page<ScrmApprovalLogEntity> listLogs(Long instanceId, Pageable pageable) throws ScrmException {
        return logService.listLogs(instanceId, pageable);
    }

    /**
     * 获取实例审批时间线。
     *
     * @param instanceId 实例 ID
     * @return 时间线 Map
     * @throws ScrmException 实例不存在
     */
    public Map<String, Object> getInstanceTimeline(Long instanceId) throws ScrmException {
        return logService.getInstanceTimeline(instanceId);
    }

    /**
     * 分页查询操作人的日志。
     *
     * @param operatorId 操作人 ID
     * @param pageable   分页参数
     * @return 日志分页结果
     */
    public Page<ScrmApprovalLogEntity> getLogsByOperator(String operatorId, Pageable pageable) {
        return logService.getLogsByOperator(operatorId, pageable);
    }

    // ============================================================
    // 统计分析
    // ============================================================

    /**
     * 审批统计概览。
     *
     * @param startTime 开始时间起始 (含, 可空)
     * @param endTime   开始时间截止 (含, 可空)
     * @return 统计结果 Map
     */
    public Map<String, Object> getApprovalStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getApprovalStats(startTime, endTime);
    }

    /**
     * 流程统计。
     *
     * @param flowId    流程 ID
     * @param startTime 开始时间起始 (含, 可空)
     * @param endTime   开始时间截止 (含, 可空)
     * @return 统计结果 Map
     * @throws ScrmException 流程不存在
     */
    public Map<String, Object> getFlowStats(
            Long flowId, LocalDateTime startTime, LocalDateTime endTime) throws ScrmException {
        return statsService.getFlowStats(flowId, startTime, endTime);
    }

    /**
     * 审批人统计。
     *
     * @param approverId 审批人 ID
     * @param startTime 操作时间起始 (含, 可空)
     * @param endTime   操作时间截止 (含, 可空)
     * @return 统计结果 Map
     */
    public Map<String, Object> getApproverStats(String approverId, LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getApproverStats(approverId, startTime, endTime);
    }

    /**
     * 待审老化分析。
     *
     * @return 老化分析 Map
     */
    public Map<String, Object> getPendingAging() {
        return statsService.getPendingAging();
    }

    /**
     * 审批趋势。
     *
     * @param days 天数
     * @return 趋势列表
     */
    public List<Map<String, Object>> getApprovalTrend(int days) {
        return statsService.getApprovalTrend(days);
    }

    /**
     * 效率统计。
     *
     * @param startTime 开始时间起始 (含, 可空)
     * @param endTime   开始时间截止 (含, 可空)
     * @return 效率统计 Map
     */
    public Map<String, Object> getEfficiencyStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getEfficiencyStats(startTime, endTime);
    }

    // ============================================================
    // 内部工具
    // ============================================================

    /**
     * 生成实例编号 (格式: AP + 年月日 + 6 位随机序号)。
     *
     * @return 实例编号
     */
    public String generateInstanceNo() {
        return instanceService.generateInstanceNo();
    }
}