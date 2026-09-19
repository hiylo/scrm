/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmApprovalController.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmApprovalActionDto;
import org.hiylo.scrm.dto.ScrmApprovalFlowDto;
import org.hiylo.scrm.dto.ScrmApprovalSubmitDto;
import org.hiylo.scrm.entity.ScrmApprovalFlowEntity;
import org.hiylo.scrm.entity.ScrmApprovalInstanceEntity;
import org.hiylo.scrm.entity.ScrmApprovalLogEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmApprovalService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 审批工作流控制器。
 * <p>
 * 提供通用审批工作流的完整接口: 流程定义 CRUD 与生命周期 (激活/停用/默认/复制/校验/
 * 按业务类型查找)、实例提交与查询 (撤回/催办/加急/取消)、审批操作 (同意/驳回/转交/加签/
 * 抄送/评论)、操作日志与时间线、统计分析 (审批统计/流程统计/审批人统计/待审老化/趋势/
 * 效率)。权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/approvals")
@RequiredArgsConstructor
public class ScrmApprovalController {

    /** 审批服务 */
    private final ScrmApprovalService scrmApprovalService;

    // ============================================================
    // 流程管理
    // ============================================================

    /**
     * 创建审批流程。
     *
     * @param dto 流程参数
     * @return 创建后的流程
     * @throws ScrmException 参数非法 / 编码重复
     */
    @RequirePermission(resource = "scrm_approval", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/flows")
    public OperationResponse<ScrmApprovalFlowEntity> createFlow(@Valid @RequestBody ScrmApprovalFlowDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmApprovalService.createFlow(dto));
    }

    /**
     * 更新审批流程。
     *
     * @param id  流程 ID
     * @param dto 流程参数
     * @return 更新后的流程
     * @throws ScrmException 流程不存在 / 参数非法 / 状态非法
     */
    @RequirePermission(resource = "scrm_approval", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/flows/{id}")
    public OperationResponse<ScrmApprovalFlowEntity> updateFlow(@PathVariable Long id,
                                                                  @RequestBody ScrmApprovalFlowDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmApprovalService.updateFlow(id, dto));
    }

    /**
     * 删除审批流程 (须为 INACTIVE / DRAFT 状态)。
     *
     * @param id 流程 ID
     * @return 空响应
     * @throws ScrmException 流程不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_approval", action = "delete")
    @DeleteMapping("/flows/{id}")
    public OperationResponse<Void> deleteFlow(@PathVariable Long id) throws ScrmException {
        scrmApprovalService.deleteFlow(id);
        return OperationResponse.build();
    }

    /**
     * 查询流程详情。
     *
     * @param id 流程 ID
     * @return 流程详情
     * @throws ScrmException 流程不存在
     */
    @RequirePermission(resource = "scrm_approval", action = "read")
    @GetMapping("/flows/{id}")
    public OperationResponse<ScrmApprovalFlowEntity> getFlow(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmApprovalService.getFlow(id));
    }

    /**
     * 按编码查询流程。
     *
     * @param code 流程编码
     * @return 流程详情
     * @throws ScrmException 流程不存在
     */
    @RequirePermission(resource = "scrm_approval", action = "read")
    @GetMapping("/flows/code/{code}")
    public OperationResponse<ScrmApprovalFlowEntity> getFlowByCode(
            @PathVariable String code) throws ScrmException {
        return OperationResponse.build(scrmApprovalService.getFlowByCode(code));
    }

    /**
     * 分页查询流程列表。
     *
     * @param flowType 流程类型过滤（可空）: *
     * CONTRACT/EXPENSE/LEAVE/REFUND/DISCOUNT/PRICE_CHANGE/CUSTOMER_MERGE/CONTENT/PURCHASE/OTHER/CUSTOM * @param
        * status
       * 状态过滤（可空）: ACTIVE/INACTIVE/DRAFT
     * @param keyword  流程名称关键字模糊匹配（可空）
     * @param page     页码（从 0 开始, 默认 0）
     * @param size     每页大小（默认 20）
     * @return 流程分页结果 (按 createTime DESC)
     */
    @RequirePermission(resource = "scrm_approval", action = "read")
    @GetMapping("/flows/list")
    public OperationResponse<Page<ScrmApprovalFlowEntity>> listFlows(
            @RequestParam(required = false) String flowType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmApprovalService.listFlows(flowType, status, keyword, pageable));
    }

    /**
     * 激活流程。
     *
     * @param id 流程 ID
     * @return 更新后的流程
     * @throws ScrmException 流程不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_approval", action = "update")
    @PostMapping("/flows/{id}/activate")
    public OperationResponse<ScrmApprovalFlowEntity> activateFlow(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmApprovalService.activateFlow(id));
    }

    /**
     * 停用流程。
     *
     * @param id 流程 ID
     * @return 更新后的流程
     * @throws ScrmException 流程不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_approval", action = "update")
    @PostMapping("/flows/{id}/deactivate")
    public OperationResponse<ScrmApprovalFlowEntity> deactivateFlow(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmApprovalService.deactivateFlow(id));
    }

    /**
     * 设置为默认流程。
     *
     * @param id 流程 ID
     * @return 更新后的流程
     * @throws ScrmException 流程不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_approval", action = "update")
    @PostMapping("/flows/{id}/default")
    public OperationResponse<ScrmApprovalFlowEntity> setDefaultFlow(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmApprovalService.setDefault(id));
    }

    /**
     * 复制流程 (生成新编码副本)。
     *
     * @param id      流程 ID
     * @param newCode 新流程编码
     * @return 复制后的流程
     * @throws ScrmException 流程不存在 / 编码重复
     */
    @RequirePermission(resource = "scrm_approval", action = "create")
    @PostMapping("/flows/{id}/copy")
    public OperationResponse<ScrmApprovalFlowEntity> copyFlow(@PathVariable Long id,
                                                                @RequestParam String newCode) throws ScrmException {
        return OperationResponse.build(scrmApprovalService.copyFlow(id, newCode));
    }

    /**
     * 验证流程 (检查节点完整性 / 连接有效性)。
     *
     * @param id 流程 ID
     * @return 验证结果 Map
     * @throws ScrmException 流程不存在
     */
    @RequirePermission(resource = "scrm_approval", action = "read")
    @PostMapping("/flows/{id}/validate")
    public OperationResponse<Map<String, Object>> validateFlow(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmApprovalService.validateFlow(id));
    }

    /**
     * 按业务类型获取默认流程。
     *
     * @param businessType 业务类型: CONTRACT/EXPENSE/LEAVE/REFUND/DISCOUNT/OTHER
     * @return 流程详情
     * @throws ScrmException 业务类型非法 / 流程不存在
     */
    @RequirePermission(resource = "scrm_approval", action = "read")
    @GetMapping("/flows/by-business-type/{businessType}")
    public OperationResponse<ScrmApprovalFlowEntity> getFlowByBusinessType(@PathVariable String businessType)
            throws ScrmException {
        return OperationResponse.build(scrmApprovalService.getFlowByBusinessType(businessType));
    }

    // ============================================================
    // 实例管理
    // ============================================================

    /**
     * 提交审批 (创建实例并初始化流程)。
     *
     * @param submitDto 提交参数
     * @return 创建后的实例
     * @throws ScrmException 流程不存在 / 状态非法 / 参数非法
     */
    @RequirePermission(resource = "scrm_approval", action = "create")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/submit")
    public OperationResponse<ScrmApprovalInstanceEntity> submit(@Valid @RequestBody ScrmApprovalSubmitDto submitDto)
            throws ScrmException {
        return OperationResponse.build(scrmApprovalService.submit(submitDto));
    }

    /**
     * 查询实例详情。
     *
     * @param id 实例 ID
     * @return 实例详情
     * @throws ScrmException 实例不存在
     */
    @RequirePermission(resource = "scrm_approval", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmApprovalInstanceEntity> getInstance(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmApprovalService.getInstance(id));
    }

    /**
     * 按实例编号查询实例。
     *
     * @param instanceNo 实例编号
     * @return 实例详情
     * @throws ScrmException 实例不存在
     */
    @RequirePermission(resource = "scrm_approval", action = "read")
    @GetMapping("/by-no/{instanceNo}")
    public OperationResponse<ScrmApprovalInstanceEntity> getInstanceByNo(@PathVariable String instanceNo)
            throws ScrmException {
        return OperationResponse.build(scrmApprovalService.getInstanceByNo(instanceNo));
    }

    /**
     * 分页查询实例列表。
     *
     * @param flowId       流程 ID 过滤（可空）
     * @param businessType 业务类型过滤（可空）: CONTRACT/EXPENSE/LEAVE/REFUND/DISCOUNT/OTHER
     * @param status       状态过滤（可空）: PENDING/APPROVING/APPROVED/REJECTED/CANCELLED/TRANSFERRED/TIMEOUT/WITHDRAWN
     * @param applicantId  申请人 ID 过滤（可空）
     * @param approverId   审批人 ID 过滤（可空, 模糊匹配当前审批人列表）
     * @param isUrgent     是否加急过滤（可空）
     * @param startTime    开始时间起始 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime      开始时间截止 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param page         页码（从 0 开始, 默认 0）
     * @param size         每页大小（默认 20）
     * @return 实例分页结果 (按 startedAt DESC)
     */
    @RequirePermission(resource = "scrm_approval", action = "read")
    @GetMapping("/list")
    public OperationResponse<Page<ScrmApprovalInstanceEntity>> listInstances(
            @RequestParam(required = false) Long flowId,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String applicantId,
            @RequestParam(required = false) String approverId,
            @RequestParam(required = false) Boolean isUrgent,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "startedAt"));
        return OperationResponse.build(scrmApprovalService.listInstances(
                flowId, businessType, status, applicantId, approverId, isUrgent,
                startTime, endTime, pageable));
    }

    /**
     * 我的待审批。
     *
     * @param approverId 审批人 ID
     * @param page       页码（从 0 开始, 默认 0）
     * @param size       每页大小（默认 20）
     * @return 实例分页结果 (按加急优先、优先级降序、开始时间升序)
     */
    @RequirePermission(resource = "scrm_approval", action = "read")
    @GetMapping("/my-pending/{approverId}")
    public OperationResponse<Page<ScrmApprovalInstanceEntity>> getMyPending(
            @PathVariable String approverId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "isUrgent")
                        .and(Sort.by(Sort.Direction.DESC, "priority"))
                        .and(Sort.by(Sort.Direction.ASC, "startedAt")));
        return OperationResponse.build(scrmApprovalService.getMyPending(approverId, pageable));
    }

    /**
     * 我提交的审批。
     *
     * @param applicantId 申请人 ID
     * @param status      状态过滤（可空）: PENDING/APPROVING/APPROVED/REJECTED/CANCELLED/TRANSFERRED/TIMEOUT/WITHDRAWN
     * @param page        页码（从 0 开始, 默认 0）
     * @param size        每页大小（默认 20）
     * @return 实例分页结果 (按 startedAt DESC)
     */
    @RequirePermission(resource = "scrm_approval", action = "read")
    @GetMapping("/my-submitted/{applicantId}")
    public OperationResponse<Page<ScrmApprovalInstanceEntity>> getMySubmitted(
            @PathVariable String applicantId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "startedAt"));
        return OperationResponse.build(scrmApprovalService.getMySubmitted(applicantId, status, pageable));
    }

    /**
     * 我已审批的。
     *
     * @param approverId 审批人 ID
     * @param page       页码（从 0 开始, 默认 0）
     * @param size       每页大小（默认 20）
     * @return 实例分页结果 (按 startedAt DESC)
     */
    @RequirePermission(resource = "scrm_approval", action = "read")
    @GetMapping("/my-approved/{approverId}")
    public OperationResponse<Page<ScrmApprovalInstanceEntity>> getMyApproved(
            @PathVariable String approverId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "startedAt"));
        return OperationResponse.build(scrmApprovalService.getMyApproved(approverId, pageable));
    }

    /**
     * 撤回审批 (仅申请人可撤回)。
     *
     * @param id          实例 ID
     * @param applicantId 申请人 ID
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 申请人不匹配 / 状态非法
     */
    @RequirePermission(resource = "scrm_approval", action = "execute")
    @PostMapping("/withdraw")
    public OperationResponse<ScrmApprovalInstanceEntity> withdraw(@RequestParam Long id,
                                                                     @RequestParam String applicantId)
            throws ScrmException {
        return OperationResponse.build(scrmApprovalService.withdraw(id, applicantId));
    }

    /**
     * 催办审批。
     *
     * @param id      实例 ID
     * @param urgerId 催办人 ID
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_approval", action = "execute")
    @PostMapping("/urge")
    public OperationResponse<ScrmApprovalInstanceEntity> urge(@RequestParam Long id,
                                                                @RequestParam String urgerId)
            throws ScrmException {
        return OperationResponse.build(scrmApprovalService.urge(id, urgerId));
    }

    /**
     * 标记加急。
     *
     * @param id     实例 ID
     * @param reason 加急原因
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法 / 流程不允许加急
     */
    @RequirePermission(resource = "scrm_approval", action = "execute")
    @PostMapping("/urgent")
    public OperationResponse<ScrmApprovalInstanceEntity> markUrgent(@RequestParam Long id,
                                                                      @RequestParam(required = false) String reason)
            throws ScrmException {
        return OperationResponse.build(scrmApprovalService.markUrgent(id, reason));
    }

    /**
     * 取消审批 (系统/管理员取消)。
     *
     * @param id     实例 ID
     * @param reason 取消原因
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_approval", action = "execute")
    @PostMapping("/cancel")
    public OperationResponse<ScrmApprovalInstanceEntity> cancel(@RequestParam Long id,
                                                                  @RequestParam(required = false) String reason)
            throws ScrmException {
        return OperationResponse.build(scrmApprovalService.cancel(id, reason));
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
    @RequirePermission(resource = "scrm_approval", action = "execute")
    @RateLimit(capacity = 120, refillTokens = 120, refillPeriodSeconds = 60)
    @PostMapping("/actions/approve")
    public OperationResponse<ScrmApprovalInstanceEntity> approve(@Valid @RequestBody ScrmApprovalActionDto actionDto)
            throws ScrmException {
        return OperationResponse.build(scrmApprovalService.approve(actionDto));
    }

    /**
     * 驳回审批。
     *
     * @param actionDto 操作参数
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法 / 操作人无权审批
     */
    @RequirePermission(resource = "scrm_approval", action = "execute")
    @PostMapping("/actions/reject")
    public OperationResponse<ScrmApprovalInstanceEntity> reject(@Valid @RequestBody ScrmApprovalActionDto actionDto)
            throws ScrmException {
        return OperationResponse.build(scrmApprovalService.reject(actionDto));
    }

    /**
     * 转交审批。
     *
     * @param actionDto 操作参数 (actionData.transferredTo 为转交目标人 ID)
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法 / 流程不允许转交
     */
    @RequirePermission(resource = "scrm_approval", action = "execute")
    @PostMapping("/actions/transfer")
    public OperationResponse<ScrmApprovalInstanceEntity> transfer(@Valid @RequestBody ScrmApprovalActionDto actionDto)
            throws ScrmException {
        return OperationResponse.build(scrmApprovalService.transfer(actionDto));
    }

    /**
     * 加签。
     *
     * @param actionDto 操作参数 (actionData.countersignUsers 为加签用户 ID 列表, 逗号分隔)
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法 / 流程不允许加签
     */
    @RequirePermission(resource = "scrm_approval", action = "execute")
    @PostMapping("/actions/countersign")
    public OperationResponse<ScrmApprovalInstanceEntity> countersign(
            @Valid @RequestBody ScrmApprovalActionDto actionDto)
            throws ScrmException {
        return OperationResponse.build(scrmApprovalService.countersign(actionDto));
    }

    /**
     * 抄送。
     *
     * @param actionDto 操作参数 (actionData.ccUsers 为抄送人 ID 列表, 逗号分隔)
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_approval", action = "execute")
    @PostMapping("/actions/cc")
    public OperationResponse<ScrmApprovalInstanceEntity> cc(@Valid @RequestBody ScrmApprovalActionDto actionDto)
            throws ScrmException {
        return OperationResponse.build(scrmApprovalService.cc(actionDto));
    }

    /**
     * 评论。
     *
     * @param actionDto 操作参数
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_approval", action = "execute")
    @PostMapping("/actions/comment")
    public OperationResponse<ScrmApprovalInstanceEntity> comment(@Valid @RequestBody ScrmApprovalActionDto actionDto)
            throws ScrmException {
        return OperationResponse.build(scrmApprovalService.comment(actionDto));
    }

    // ============================================================
    // 操作日志
    // ============================================================

    /**
     * 查询日志详情。
     *
     * @param id 日志 ID
     * @return 日志详情
     * @throws ScrmException 日志不存在
     */
    @RequirePermission(resource = "scrm_approval", action = "read")
    @GetMapping("/logs/{id}")
    public OperationResponse<ScrmApprovalLogEntity> getLog(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmApprovalService.getLog(id));
    }

    /**
     * 分页查询实例的日志。
     *
     * @param instanceId 实例 ID
     * @param page       页码（从 0 开始, 默认 0）
     * @param size       每页大小（默认 20）
     * @return 日志分页结果 (按 sequence ASC)
     * @throws ScrmException 实例不存在
     */
    @RequirePermission(resource = "scrm_approval", action = "read")
    @GetMapping("/logs/list")
    public OperationResponse<Page<ScrmApprovalLogEntity>> listLogs(
            @RequestParam Long instanceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) throws ScrmException {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "sequence"));
        return OperationResponse.build(scrmApprovalService.listLogs(instanceId, pageable));
    }

    /**
     * 获取实例审批时间线。
     *
     * @param instanceId 实例 ID
     * @return 时间线 Map
     * @throws ScrmException 实例不存在
     */
    @RequirePermission(resource = "scrm_approval", action = "read")
    @GetMapping("/logs/timeline/{instanceId}")
    public OperationResponse<Map<String, Object>> getInstanceTimeline(@PathVariable Long instanceId)
            throws ScrmException {
        return OperationResponse.build(scrmApprovalService.getInstanceTimeline(instanceId));
    }

    /**
     * 分页查询操作人的日志。
     *
     * @param operatorId 操作人 ID
     * @param page       页码（从 0 开始, 默认 0）
     * @param size       每页大小（默认 20）
     * @return 日志分页结果 (按 actedAt DESC)
     */
    @RequirePermission(resource = "scrm_approval", action = "read")
    @GetMapping("/logs/by-operator/{operatorId}")
    public OperationResponse<Page<ScrmApprovalLogEntity>> getLogsByOperator(
            @PathVariable String operatorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "actedAt"));
        return OperationResponse.build(scrmApprovalService.getLogsByOperator(operatorId, pageable));
    }

    // ============================================================
    // 统计分析
    // ============================================================

    /**
     * 审批统计概览: 总数 / 各状态数 / 平均时长 / 通过率。
     *
     * @param startTime 开始时间起始 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   开始时间截止 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_approval", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getApprovalStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmApprovalService.getApprovalStats(startTime, endTime));
    }

    /**
     * 流程统计: 该流程的实例数 / 各状态数 / 平均时长 / 通过率。
     *
     * @param flowId    流程 ID
     * @param startTime 开始时间起始 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   开始时间截止 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果 Map
     * @throws ScrmException 流程不存在
     */
    @RequirePermission(resource = "scrm_approval", action = "read")
    @GetMapping("/stats/flow/{flowId}")
    public OperationResponse<Map<String, Object>> getFlowStats(
            @PathVariable Long flowId,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) throws ScrmException {
        return OperationResponse.build(scrmApprovalService.getFlowStats(flowId, startTime, endTime));
    }

    /**
     * 审批人统计: 审批数 / 通过率 / 平均时长。
     *
     * @param approverId 审批人 ID
     * @param startTime  操作时间起始 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime    操作时间截止 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_approval", action = "read")
    @GetMapping("/stats/approver/{approverId}")
    public OperationResponse<Map<String, Object>> getApproverStats(
            @PathVariable String approverId,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmApprovalService.getApproverStats(approverId, startTime, endTime));
    }

    /**
     * 待审老化分析: 各时间段待审数。
     *
     * @return 老化分析 Map
     */
    @RequirePermission(resource = "scrm_approval", action = "read")
    @GetMapping("/stats/pending-aging")
    public OperationResponse<Map<String, Object>> getPendingAging() {
        return OperationResponse.build(scrmApprovalService.getPendingAging());
    }

    /**
     * 审批趋势: 最近 N 天每日提交实例数与通过数。
     *
     * @param days 天数 (默认 7)
     * @return 趋势列表
     */
    @RequirePermission(resource = "scrm_approval", action = "read")
    @GetMapping("/stats/trend")
    public OperationResponse<List<Map<String, Object>>> getApprovalTrend(
            @RequestParam(defaultValue = "7") int days) {
        return OperationResponse.build(scrmApprovalService.getApprovalTrend(days));
    }

    /**
     * 效率统计: 平均时长 / 超时率 / 自动审批率。
     *
     * @param startTime 开始时间起始 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   开始时间截止 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 效率统计 Map
     */
    @RequirePermission(resource = "scrm_approval", action = "read")
    @GetMapping("/stats/efficiency")
    public OperationResponse<Map<String, Object>> getEfficiencyStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmApprovalService.getEfficiencyStats(startTime, endTime));
    }
}
