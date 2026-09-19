/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWorkflowController.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmWorkflowDto;
import org.hiylo.scrm.dto.ScrmWorkflowTriggerDto;
import org.hiylo.scrm.entity.ScrmWorkflowEntity;
import org.hiylo.scrm.entity.ScrmWorkflowInstanceEntity;
import org.hiylo.scrm.entity.ScrmWorkflowNodeLogEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmWorkflowService;
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
 * SCRM 营销自动化工作流控制器。
 * <p>
 * 提供可视化营销自动化工作流的完整接口: 工作流定义 CRUD 与生命周期管理 (激活/暂停/归档/
 * 复制/发布/校验/图结构)、执行实例触发与查询 (取消/暂停/恢复/重试)、节点执行日志与时间线、
 * 统计分析 (工作流统计/实例统计/节点性能/趋势/转化漏斗)。
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 * <p><b>触发执行端点已启用 (实例创建 + 入口节点执行为真实逻辑, 外部动作执行待对接执行引擎)。</b></p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/workflows")
@RequiredArgsConstructor
public class ScrmWorkflowController {

    /** 工作流服务 */
    private final ScrmWorkflowService scrmWorkflowService;

    // ============================================================
    // 工作流管理
    // ============================================================

    /**
     * 创建工作流。
     *
     * @param dto 工作流参数
     * @return 创建后的工作流
     * @throws ScrmException 参数非法 / 编码重复
     */
    @RequirePermission(resource = "scrm_workflow", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping
    public OperationResponse<ScrmWorkflowEntity> createWorkflow(@Valid @RequestBody ScrmWorkflowDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmWorkflowService.createWorkflow(dto));
    }

    /**
     * 更新工作流。
     *
     * @param id  工作流 ID
     * @param dto 工作流参数
     * @return 更新后的工作流
     * @throws ScrmException 工作流不存在 / 参数非法 / 状态非法
     */
    @RequirePermission(resource = "scrm_workflow", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/{id}")
    public OperationResponse<ScrmWorkflowEntity> updateWorkflow(@PathVariable Long id,
                                                                 @RequestBody ScrmWorkflowDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmWorkflowService.updateWorkflow(id, dto));
    }

    /**
     * 删除工作流 (同时删除关联实例与节点日志)。
     *
     * @param id 工作流 ID
     * @return 空响应
     * @throws ScrmException 工作流不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_workflow", action = "delete")
    @DeleteMapping("/{id}")
    public OperationResponse<Void> deleteWorkflow(@PathVariable Long id) throws ScrmException {
        scrmWorkflowService.deleteWorkflow(id);
        return OperationResponse.build();
    }

    /**
     * 查询工作流详情。
     *
     * @param id 工作流 ID
     * @return 工作流详情
     * @throws ScrmException 工作流不存在
     */
    @RequirePermission(resource = "scrm_workflow", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmWorkflowEntity> getWorkflow(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmWorkflowService.getWorkflow(id));
    }

    /**
     * 按编码查询工作流。
     *
     * @param code 工作流编码
     * @return 工作流详情
     * @throws ScrmException 工作流不存在
     */
    @RequirePermission(resource = "scrm_workflow", action = "read")
    @GetMapping("/code/{code}")
    public OperationResponse<ScrmWorkflowEntity> getWorkflowByCode(
            @PathVariable String code) throws ScrmException {
        return OperationResponse.build(scrmWorkflowService.getWorkflowByCode(code));
    }

    /**
     * 分页查询工作流列表。
     *
     * @param workflowType 工作流类型过滤（可空）: *
       * MARKETING/ONBOARDING/RETENTION/RE_ENGAGEMENT/POST_PURCHASE/ABANDONED_CART/BIRTHDAY/ANNIVERSARY/CUSTOM * @param
       * triggerType 触发器类型过滤（可空）: EVENT/SCHEDULE/SEGMENT/WEBHOOK/MANUAL
     * @param status       状态过滤（可空）: DRAFT/ACTIVE/PAUSED/ARCHIVED
     * @param keyword      工作流名称关键字模糊匹配（可空）
     * @param page         页码（从 0 开始, 默认 0）
     * @param size         每页大小（默认 20）
     * @return 工作流分页结果 (按 createTime DESC)
     */
    @RequirePermission(resource = "scrm_workflow", action = "read")
    @GetMapping("/list")
    public OperationResponse<Page<ScrmWorkflowEntity>> listWorkflows(
            @RequestParam(required = false) String workflowType,
            @RequestParam(required = false) String triggerType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmWorkflowService.listWorkflows(
                workflowType, triggerType, status, keyword, pageable));
    }

    /**
     * 激活工作流。
     *
     * @param id 工作流 ID
     * @return 更新后的工作流
     * @throws ScrmException 工作流不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_workflow", action = "update")
    @PostMapping("/{id}/activate")
    public OperationResponse<ScrmWorkflowEntity> activateWorkflow(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmWorkflowService.activateWorkflow(id));
    }

    /**
     * 暂停工作流。
     *
     * @param id 工作流 ID
     * @return 更新后的工作流
     * @throws ScrmException 工作流不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_workflow", action = "update")
    @PostMapping("/{id}/pause")
    public OperationResponse<ScrmWorkflowEntity> pauseWorkflow(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmWorkflowService.pauseWorkflow(id));
    }

    /**
     * 归档工作流。
     *
     * @param id 工作流 ID
     * @return 更新后的工作流
     * @throws ScrmException 工作流不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_workflow", action = "update")
    @PostMapping("/{id}/archive")
    public OperationResponse<ScrmWorkflowEntity> archiveWorkflow(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmWorkflowService.archiveWorkflow(id));
    }

    /**
     * 复制工作流 (生成新编码副本)。
     *
     * @param id 工作流 ID
     * @return 复制后的工作流
     * @throws ScrmException 工作流不存在
     */
    @RequirePermission(resource = "scrm_workflow", action = "create")
    @PostMapping("/{id}/copy")
    public OperationResponse<ScrmWorkflowEntity> copyWorkflow(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmWorkflowService.copyWorkflow(id));
    }

    /**
     * 发布新版本。
     *
     * @param id 工作流 ID
     * @return 更新后的工作流
     * @throws ScrmException 工作流不存在
     */
    @RequirePermission(resource = "scrm_workflow", action = "update")
    @PostMapping("/{id}/publish")
    public OperationResponse<ScrmWorkflowEntity> publishVersion(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmWorkflowService.publishVersion(id));
    }

    /**
     * 验证工作流 (检查节点完整性 / 连接有效性)。
     *
     * @param id 工作流 ID
     * @return 验证结果 Map
     * @throws ScrmException 工作流不存在
     */
    @RequirePermission(resource = "scrm_workflow", action = "read")
    @PostMapping("/{id}/validate")
    public OperationResponse<Map<String, Object>> validateWorkflow(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmWorkflowService.validateWorkflow(id));
    }

    /**
     * 获取工作流图结构 (节点 + 连接 + 入口)。
     *
     * @param id 工作流 ID
     * @return 图结构 Map
     * @throws ScrmException 工作流不存在
     */
    @RequirePermission(resource = "scrm_workflow", action = "read")
    @GetMapping("/{id}/graph")
    public OperationResponse<Map<String, Object>> getWorkflowGraph(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmWorkflowService.getWorkflowGraph(id));
    }

    // ============================================================
    // 执行实例
    // ============================================================

    /**
     * 触发工作流 (创建实例并按节点图流转)。
     * <p>
     * 注意: 当前工作流执行链路 (条件评估 / 动作执行 / 延迟调度) 均为占位实现,
     * 该端点返回 501 NOT_IMPLEMENTED, 待对接真实执行引擎后恢复。
     * </p>
     *
     * @param triggerDto 触发参数
     * @return 创建后的实例
     * @throws ScrmException 工作流不存在 / 状态非法 / 超过并发上限
     */
    @RequirePermission(resource = "scrm_workflow", action = "execute")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/instances/trigger")
    public OperationResponse<ScrmWorkflowInstanceEntity> triggerWorkflow(
            @Valid @RequestBody ScrmWorkflowTriggerDto triggerDto) throws ScrmException {
        return OperationResponse.build(scrmWorkflowService.triggerWorkflow(triggerDto));
    }

    /**
     * 查询实例详情。
     *
     * @param id 实例 ID
     * @return 实例详情
     * @throws ScrmException 实例不存在
     */
    @RequirePermission(resource = "scrm_workflow", action = "read")
    @GetMapping("/instances/{id}")
    public OperationResponse<ScrmWorkflowInstanceEntity> getInstance(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmWorkflowService.getInstance(id));
    }

    /**
     * 分页查询实例列表。
     *
     * @param workflowId 工作流 ID 过滤（可空）
     * @param customerId 客户 ID 过滤（可空）
     * @param status     状态过滤（可空）: RUNNING/PAUSED/COMPLETED/FAILED/CANCELLED/WAITING
     * @param startTime  开始时间起始 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime    开始时间截止 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param page       页码（从 0 开始, 默认 0）
     * @param size       每页大小（默认 20）
     * @return 实例分页结果 (按 startedAt DESC)
     */
    @RequirePermission(resource = "scrm_workflow", action = "read")
    @GetMapping("/instances/list")
    public OperationResponse<Page<ScrmWorkflowInstanceEntity>> listInstances(
            @RequestParam(required = false) Long workflowId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "startedAt"));
        return OperationResponse.build(scrmWorkflowService.listInstances(
                workflowId, customerId, status, startTime, endTime, pageable));
    }

    /**
     * 取消实例。
     *
     * @param id 实例 ID
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_workflow", action = "execute")
    @PostMapping("/instances/{id}/cancel")
    public OperationResponse<ScrmWorkflowInstanceEntity> cancelInstance(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmWorkflowService.cancelInstance(id));
    }

    /**
     * 暂停实例。
     *
     * @param id 实例 ID
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_workflow", action = "execute")
    @PostMapping("/instances/{id}/pause")
    public OperationResponse<ScrmWorkflowInstanceEntity> pauseInstance(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmWorkflowService.pauseInstance(id));
    }

    /**
     * 恢复实例。
     *
     * @param id 实例 ID
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_workflow", action = "execute")
    @PostMapping("/instances/{id}/resume")
    public OperationResponse<ScrmWorkflowInstanceEntity> resumeInstance(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmWorkflowService.resumeInstance(id));
    }

    /**
     * 重试失败实例。
     *
     * @param id 实例 ID
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_workflow", action = "execute")
    @PostMapping("/instances/{id}/retry")
    public OperationResponse<ScrmWorkflowInstanceEntity> retryInstance(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmWorkflowService.retryInstance(id));
    }

    /**
     * 获取工作流的活跃实例。
     *
     * @param workflowId 工作流 ID
     * @return 活跃实例列表
     * @throws ScrmException 工作流不存在
     */
    @RequirePermission(resource = "scrm_workflow", action = "read")
    @GetMapping("/instances/active/{workflowId}")
    public OperationResponse<List<ScrmWorkflowInstanceEntity>> getActiveInstances(@PathVariable Long workflowId)
            throws ScrmException {
        return OperationResponse.build(scrmWorkflowService.getActiveInstances(workflowId));
    }

    /**
     * 获取客户的工作流实例。
     *
     * @param customerId 客户 ID
     * @return 实例列表
     */
    @RequirePermission(resource = "scrm_workflow", action = "read")
    @GetMapping("/instances/customer/{customerId}")
    public OperationResponse<List<ScrmWorkflowInstanceEntity>> getCustomerInstances(@PathVariable Long customerId) {
        return OperationResponse.build(scrmWorkflowService.getCustomerInstances(customerId));
    }

    // ============================================================
    // 节点日志
    // ============================================================

    /**
     * 查询实例的节点执行日志。
     *
     * @param instanceId 实例 ID
     * @return 节点日志列表
     * @throws ScrmException 实例不存在
     */
    @RequirePermission(resource = "scrm_workflow", action = "read")
    @GetMapping("/node-logs/instance/{instanceId}")
    public OperationResponse<List<ScrmWorkflowNodeLogEntity>> getNodeLogs(@PathVariable Long instanceId)
            throws ScrmException {
        return OperationResponse.build(scrmWorkflowService.getNodeLogs(instanceId));
    }

    /**
     * 查询节点日志详情。
     *
     * @param id 节点日志 ID
     * @return 节点日志详情
     * @throws ScrmException 节点日志不存在
     */
    @RequirePermission(resource = "scrm_workflow", action = "read")
    @GetMapping("/node-logs/{id}")
    public OperationResponse<ScrmWorkflowNodeLogEntity> getNodeLog(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmWorkflowService.getNodeLog(id));
    }

    /**
     * 获取实例执行时间线。
     *
     * @param instanceId 实例 ID
     * @return 时间线 Map
     * @throws ScrmException 实例不存在
     */
    @RequirePermission(resource = "scrm_workflow", action = "read")
    @GetMapping("/node-logs/timeline/{instanceId}")
    public OperationResponse<Map<String, Object>> getInstanceTimeline(@PathVariable Long instanceId)
            throws ScrmException {
        return OperationResponse.build(scrmWorkflowService.getInstanceTimeline(instanceId));
    }

    /**
     * 分页查询工作流的失败节点列表。
     *
     * @param workflowId 工作流 ID
     * @param page       页码（从 0 开始, 默认 0）
     * @param size       每页大小（默认 20）
     * @return 失败节点日志分页结果
     * @throws ScrmException 工作流不存在
     */
    @RequirePermission(resource = "scrm_workflow", action = "read")
    @GetMapping("/node-logs/failed/{workflowId}")
    public OperationResponse<Page<ScrmWorkflowNodeLogEntity>> getFailedNodes(
            @PathVariable Long workflowId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) throws ScrmException {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "startedAt"));
        return OperationResponse.build(scrmWorkflowService.getFailedNodes(workflowId, pageable));
    }

    // ============================================================
    // 统计分析
    // ============================================================

    /**
     * 工作流统计概览: 总数 / 活跃数 / 执行次数 / 成功率 / 平均时长。
     *
     * @param startTime 创建时间起始 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   创建时间截止 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_workflow", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getWorkflowStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmWorkflowService.getWorkflowStats(startTime, endTime));
    }

    /**
     * 实例统计: 各状态数 / 平均时长 / 转化率。
     *
     * @param workflowId 工作流 ID
     * @return 统计结果 Map
     * @throws ScrmException 工作流不存在
     */
    @RequirePermission(resource = "scrm_workflow", action = "read")
    @GetMapping("/stats/instances/{workflowId}")
    public OperationResponse<Map<String, Object>> getInstanceStats(@PathVariable Long workflowId)
            throws ScrmException {
        return OperationResponse.build(scrmWorkflowService.getInstanceStats(workflowId));
    }

    /**
     * 节点性能: 各节点成功率 / 平均耗时。
     *
     * @param workflowId 工作流 ID
     * @return 节点性能列表
     * @throws ScrmException 工作流不存在
     */
    @RequirePermission(resource = "scrm_workflow", action = "read")
    @GetMapping("/stats/nodes/{workflowId}")
    public OperationResponse<List<Map<String, Object>>> getNodePerformance(@PathVariable Long workflowId)
            throws ScrmException {
        return OperationResponse.build(scrmWorkflowService.getNodePerformance(workflowId));
    }

    /**
     * 工作流趋势: 最近 N 天每日执行实例数。
     *
     * @param days 天数 (默认 7)
     * @return 趋势列表
     */
    @RequirePermission(resource = "scrm_workflow", action = "read")
    @GetMapping("/stats/trend")
    public OperationResponse<List<Map<String, Object>>> getWorkflowTrend(
            @RequestParam(defaultValue = "7") int days) {
        return OperationResponse.build(scrmWorkflowService.getWorkflowTrend(days));
    }

    /**
     * 转化漏斗: 各节点通过率。
     *
     * @param workflowId 工作流 ID
     * @return 漏斗 Map
     * @throws ScrmException 工作流不存在
     */
    @RequirePermission(resource = "scrm_workflow", action = "read")
    @GetMapping("/stats/funnel/{workflowId}")
    public OperationResponse<Map<String, Object>> getConversionFunnel(@PathVariable Long workflowId)
            throws ScrmException {
        return OperationResponse.build(scrmWorkflowService.getConversionFunnel(workflowId));
    }
}
