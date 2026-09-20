/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTaskSchedulerController.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmTaskDependencyDto;
import org.hiylo.scrm.dto.ScrmTaskExecuteDto;
import org.hiylo.scrm.dto.ScrmTaskRetryDto;
import org.hiylo.scrm.dto.ScrmScheduledTaskDto;
import org.hiylo.scrm.entity.ScrmScheduledTaskEntity;
import org.hiylo.scrm.entity.ScrmTaskDependencyEntity;
import org.hiylo.scrm.entity.ScrmTaskExecutionEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmTaskSchedulerService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
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
 * SCRM 任务调度引擎控制器。
 * <p>
 * 提供统一任务调度中心的全部接口: 定时任务 CRUD、启用/禁用/暂停/恢复、复制、
 * Cron 表达式校验与下次执行时间计算、任务执行 (创建执行记录→模拟执行→记录结果)、
 * 执行记录查询、取消、重试、进度更新、超时处理, 以及任务依赖关系管理与统计分析接口。
 * </p>
 * <p>
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * 任务执行为模拟实现, 实际项目应替换为对接 Quartz / Spring Scheduler 等真实调度框架。
 * </p>
 * <p><b>执行 / 重试 / 依赖任务执行端点已启用 (执行记录与状态流转为真实逻辑, 处理器调用为模拟实现)。</b></p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/task-scheduler")
@RequiredArgsConstructor
public class ScrmTaskSchedulerController {

    /** 任务调度服务 */
    private final ScrmTaskSchedulerService scrmTaskSchedulerService;

    // ============================================================
    // 任务管理
    // ============================================================

    /**
     * 创建任务调度配置。
     *
     * @param dto 任务参数
     * @return 创建后的任务
     * @throws ScrmException 参数非法 / 任务编码重复
     */
    @RequirePermission(resource = "scrm_scheduled_task", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping
    public OperationResponse<ScrmScheduledTaskEntity> createTask(@Valid @RequestBody ScrmScheduledTaskDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmTaskSchedulerService.createTask(dto));
    }

    /**
     * 更新任务调度配置。
     *
     * @param id  任务 ID
     * @param dto 任务参数
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_scheduled_task", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/{id}")
    public OperationResponse<ScrmScheduledTaskEntity> updateTask(@PathVariable Long id,
                                                                    @RequestBody ScrmScheduledTaskDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmTaskSchedulerService.updateTask(id, dto));
    }

    /**
     * 删除任务调度配置。
     *
     * @param id 任务 ID
     * @return 空响应
     * @throws ScrmException 任务不存在 / 仍有执行记录或依赖引用
     */
    @RequirePermission(resource = "scrm_scheduled_task", action = "delete")
    @DeleteMapping("/{id}")
    public OperationResponse<Void> deleteTask(@PathVariable Long id) throws ScrmException {
        scrmTaskSchedulerService.deleteTask(id);
        return OperationResponse.build();
    }

    /**
     * 查询任务详情。
     *
     * @param id 任务 ID
     * @return 任务详情
     * @throws ScrmException 任务不存在
     */
    @RequirePermission(resource = "scrm_scheduled_task", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmScheduledTaskEntity> getTask(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmTaskSchedulerService.getTask(id));
    }

    /**
     * 按任务编码查询任务详情。
     *
     * @param code 任务编码
     * @return 任务详情
     * @throws ScrmException 任务不存在
     */
    @RequirePermission(resource = "scrm_scheduled_task", action = "read")
    @GetMapping("/code/{code}")
    public OperationResponse<ScrmScheduledTaskEntity> getTaskByCode(@PathVariable String code)
            throws ScrmException {
        return OperationResponse.build(scrmTaskSchedulerService.getTaskByCode(code));
    }

    /**
     * 分页查询任务列表。
     *
     * @param taskCategory 任务类别过滤（可空）
     * @param taskType     任务类型过滤（可空）
     * @param status       状态过滤（可空）
     * @param keyword      关键字过滤（按任务名称/编码/描述模糊匹配, 可空）
     * @param page         页码（从 0 开始, 默认 0）
     * @param size         每页大小（默认 20）
     * @return 任务分页结果
     */
    @RequirePermission(resource = "scrm_scheduled_task", action = "read")
    @GetMapping("/list")
    public OperationResponse<Page<ScrmScheduledTaskEntity>> listTasks(
            @RequestParam(required = false) String taskCategory,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmTaskSchedulerService.listTasks(
                taskCategory, taskType, status, keyword, pageable));
    }

    /**
     * 启用任务。
     *
     * @param id 任务 ID
     * @return 空响应
     * @throws ScrmException 任务不存在
     */
    @RequirePermission(resource = "scrm_scheduled_task", action = "update")
    @PostMapping("/{id}/enable")
    public OperationResponse<Void> enableTask(@PathVariable Long id) throws ScrmException {
        scrmTaskSchedulerService.enableTask(id);
        return OperationResponse.build();
    }

    /**
     * 禁用任务。
     *
     * @param id 任务 ID
     * @return 空响应
     * @throws ScrmException 任务不存在
     */
    @RequirePermission(resource = "scrm_scheduled_task", action = "update")
    @PostMapping("/{id}/disable")
    public OperationResponse<Void> disableTask(@PathVariable Long id) throws ScrmException {
        scrmTaskSchedulerService.disableTask(id);
        return OperationResponse.build();
    }

    /**
     * 暂停任务。
     *
     * @param id 任务 ID
     * @return 空响应
     * @throws ScrmException 任务不存在
     */
    @RequirePermission(resource = "scrm_scheduled_task", action = "update")
    @PostMapping("/{id}/pause")
    public OperationResponse<Void> pauseTask(@PathVariable Long id) throws ScrmException {
        scrmTaskSchedulerService.pauseTask(id);
        return OperationResponse.build();
    }

    /**
     * 恢复任务。
     *
     * @param id 任务 ID
     * @return 空响应
     * @throws ScrmException 任务不存在
     */
    @RequirePermission(resource = "scrm_scheduled_task", action = "update")
    @PostMapping("/{id}/resume")
    public OperationResponse<Void> resumeTask(@PathVariable Long id) throws ScrmException {
        scrmTaskSchedulerService.resumeTask(id);
        return OperationResponse.build();
    }

    /**
     * 复制任务。
     *
     * @param id      源任务 ID
     * @param newCode 新任务编码
     * @return 复制后的任务
     * @throws ScrmException 源任务不存在 / 新编码重复
     */
    @RequirePermission(resource = "scrm_scheduled_task", action = "create")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/{id}/copy")
    public OperationResponse<ScrmScheduledTaskEntity> copyTask(@PathVariable Long id,
                                                                  @RequestParam String newCode)
            throws ScrmException {
        return OperationResponse.build(scrmTaskSchedulerService.copyTask(id, newCode));
    }

    /**
     * 校验 Cron 表达式合法性。
     *
     * @param cronExpression Cron 表达式
     * @return 校验结果 (合法返回 true)
     * @throws ScrmException 表达式非法
     */
    @RequirePermission(resource = "scrm_scheduled_task", action = "read")
    @PostMapping("/validate-cron")
    public OperationResponse<Boolean> validateCron(@RequestParam String cronExpression)
            throws ScrmException {
        return OperationResponse.build(scrmTaskSchedulerService.validateCron(cronExpression));
    }

    /**
     * 计算指定 Cron 表达式的下次执行时间。
     *
     * @param cronExpression Cron 表达式
     * @return 下次执行时间
     * @throws ScrmException 表达式非法
     */
    @RequirePermission(resource = "scrm_scheduled_task", action = "read")
    @PostMapping("/calculate-next")
    public OperationResponse<LocalDateTime> calculateNextExecution(@RequestParam String cronExpression)
            throws ScrmException {
        return OperationResponse.build(scrmTaskSchedulerService.calculateNextExecution(cronExpression));
    }

    // ============================================================
    // 任务执行 /executions
    // ============================================================

    /**
     * 执行任务（模拟实现）。
     * <p>
     * 注意: 任务执行 (simulateHandlerInvocation) 为占位实现, 未对接 Quartz / Spring Scheduler
     * 等真实调度框架, 该端点返回 501 NOT_IMPLEMENTED, 待对接后恢复。
     * </p>
     *
     * @param dto 执行参数 (任务 ID + 本次参数 + 触发者)
     * @return 执行记录 (终态)
     * @throws ScrmException 任务不存在 / 状态不允许执行
     */
    @RequirePermission(resource = "scrm_scheduled_task", action = "execute")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/executions/execute")
    public OperationResponse<ScrmTaskExecutionEntity> executeTask(@Valid @RequestBody ScrmTaskExecuteDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmTaskSchedulerService.executeTask(dto));
    }

    /**
     * 查询执行记录详情。
     *
     * @param id 执行 ID
     * @return 执行记录详情
     * @throws ScrmException 执行记录不存在
     */
    @RequirePermission(resource = "scrm_scheduled_task", action = "read")
    @GetMapping("/executions/{id}")
    public OperationResponse<ScrmTaskExecutionEntity> getExecution(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmTaskSchedulerService.getExecution(id));
    }

    /**
     * 按执行编号查询执行记录详情。
     *
     * @param executionNo 执行编号
     * @return 执行记录详情
     * @throws ScrmException 执行记录不存在
     */
    @RequirePermission(resource = "scrm_scheduled_task", action = "read")
    @GetMapping("/executions/by-no/{executionNo}")
    public OperationResponse<ScrmTaskExecutionEntity> getExecutionByNo(@PathVariable String executionNo)
            throws ScrmException {
        return OperationResponse.build(scrmTaskSchedulerService.getExecutionByNo(executionNo));
    }

    /**
     * 分页查询执行记录。
     *
     * @param taskId     任务 ID 过滤（可空）
     * @param status     执行状态过滤（可空）
     * @param triggerType 触发类型过滤（可空）
     * @param startTime  起始时间 (含, 可空)
     * @param endTime    截止时间 (含, 可空)
     * @param page       页码（从 0 开始, 默认 0）
     * @param size       每页大小（默认 20）
     * @return 执行记录分页结果
     */
    @RequirePermission(resource = "scrm_scheduled_task", action = "read")
    @GetMapping("/executions/list")
    public OperationResponse<Page<ScrmTaskExecutionEntity>> listExecutions(
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String triggerType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "startedAt"));
        return OperationResponse.build(scrmTaskSchedulerService.listExecutions(
                taskId, status, triggerType, startTime, endTime, pageable));
    }

    /**
     * 取消执行。
     *
     * @param id     执行 ID
     * @param reason 取消原因（可空）
     * @return 更新后的执行记录
     * @throws ScrmException 执行记录不存在 / 状态不允许取消
     */
    @RequirePermission(resource = "scrm_scheduled_task", action = "execute")
    @PostMapping("/executions/{id}/cancel")
    public OperationResponse<ScrmTaskExecutionEntity> cancelExecution(@PathVariable Long id,
                                                                       @RequestParam(required = false) String reason)
            throws ScrmException {
        return OperationResponse.build(scrmTaskSchedulerService.cancelExecution(id, reason));
    }

    /**
     * 查询运行中（RUNNING）的执行记录。
     *
     * @return 运行中执行记录列表
     */
    @RequirePermission(resource = "scrm_scheduled_task", action = "read")
    @GetMapping("/executions/running")
    public OperationResponse<List<ScrmTaskExecutionEntity>> getRunningExecutions() {
        return OperationResponse.build(scrmTaskSchedulerService.getRunningExecutions());
    }

    /**
     * 查询任务最近一次执行记录。
     *
     * @param taskId 任务 ID
     * @return 最近一次执行记录 (无执行时 data 为 null)
     */
    @RequirePermission(resource = "scrm_scheduled_task", action = "read")
    @GetMapping("/executions/latest/{taskId}")
    public OperationResponse<ScrmTaskExecutionEntity> getLatestExecution(@PathVariable Long taskId) {
        return OperationResponse.build(scrmTaskSchedulerService.getLatestExecution(taskId));
    }

    /**
     * 重试执行。
     * <p>
     * 注意: 重试执行内部调用 runExecution → simulateHandlerInvocation (占位实现),
     * 未对接真实调度框架, 该端点返回 501 NOT_IMPLEMENTED, 待对接后恢复。
     * </p>
     *
     * @param dto 重试参数 (原执行 ID + 可选自定义延迟秒)
     * @return 新创建的执行记录
     * @throws ScrmException 原执行记录不存在 / 状态不允许重试 / 超过最大重试次数
     */
    @RequirePermission(resource = "scrm_scheduled_task", action = "execute")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/executions/retry")
    public OperationResponse<ScrmTaskExecutionEntity> retryExecution(@Valid @RequestBody ScrmTaskRetryDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmTaskSchedulerService.retryExecution(dto));
    }

    /**
     * 处理执行超时。
     *
     * @param executionId 执行 ID
     * @return 更新后的执行记录
     * @throws ScrmException 执行记录不存在 / 状态非 RUNNING
     */
    @RequirePermission(resource = "scrm_scheduled_task", action = "execute")
    @PostMapping("/executions/timeout")
    public OperationResponse<ScrmTaskExecutionEntity> handleTimeout(@RequestParam Long executionId)
            throws ScrmException {
        return OperationResponse.build(scrmTaskSchedulerService.handleTimeout(executionId));
    }

    /**
     * 更新执行进度。
     *
     * @param executionId 执行 ID
     * @param progress    进度 0-100
     * @param message     进度消息（可空）
     * @return 空响应
     * @throws ScrmException 执行记录不存在 / 进度非法
     */
    @RequirePermission(resource = "scrm_scheduled_task", action = "execute")
    @PostMapping("/executions/progress")
    public OperationResponse<Void> updateProgress(@RequestParam Long executionId,
                                                     @RequestParam Integer progress,
                                                     @RequestParam(required = false) String message)
            throws ScrmException {
        scrmTaskSchedulerService.updateProgress(executionId, progress, message);
        return OperationResponse.build();
    }

    // ============================================================
    // 任务依赖 /dependencies
    // ============================================================

    /**
     * 创建任务依赖关系。
     *
     * @param dto 依赖参数
     * @return 创建后的依赖关系
     * @throws ScrmException 参数非法 / 重复依赖
     */
    @RequirePermission(resource = "scrm_task_dependency", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/dependencies")
    public OperationResponse<ScrmTaskDependencyEntity> createDependency(
            @Valid @RequestBody ScrmTaskDependencyDto dto) throws ScrmException {
        return OperationResponse.build(scrmTaskSchedulerService.createDependency(dto));
    }

    /**
     * 更新任务依赖关系。
     *
     * @param id  依赖 ID
     * @param dto 依赖参数
     * @return 更新后的依赖关系
     * @throws ScrmException 依赖关系不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_task_dependency", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/dependencies/{id}")
    public OperationResponse<ScrmTaskDependencyEntity> updateDependency(@PathVariable Long id,
                                                                          @RequestBody ScrmTaskDependencyDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmTaskSchedulerService.updateDependency(id, dto));
    }

    /**
     * 删除任务依赖关系。
     *
     * @param id 依赖 ID
     * @return 空响应
     * @throws ScrmException 依赖关系不存在
     */
    @RequirePermission(resource = "scrm_task_dependency", action = "delete")
    @DeleteMapping("/dependencies/{id}")
    public OperationResponse<Void> deleteDependency(@PathVariable Long id) throws ScrmException {
        scrmTaskSchedulerService.deleteDependency(id);
        return OperationResponse.build();
    }

    /**
     * 查询任务依赖关系详情。
     *
     * @param id 依赖 ID
     * @return 依赖关系详情
     * @throws ScrmException 依赖关系不存在
     */
    @RequirePermission(resource = "scrm_task_dependency", action = "read")
    @GetMapping("/dependencies/{id}")
    public OperationResponse<ScrmTaskDependencyEntity> getDependency(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmTaskSchedulerService.getDependency(id));
    }

    /**
     * 分页查询任务依赖关系。
     *
     * @param taskId         主任务 ID 过滤（可空）
     * @param dependsOnTaskId 依赖任务 ID 过滤（可空）
     * @param page            页码（从 0 开始, 默认 0）
     * @param size            每页大小（默认 20）
     * @return 依赖关系分页结果
     */
    @RequirePermission(resource = "scrm_task_dependency", action = "read")
    @GetMapping("/dependencies/list")
    public OperationResponse<Page<ScrmTaskDependencyEntity>> listDependencies(
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) Long dependsOnTaskId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmTaskSchedulerService.listDependencies(
                taskId, dependsOnTaskId, pageable));
    }

    /**
     * 检查任务依赖是否满足。
     *
     * @param taskId 任务 ID
     * @return 检查结果 (true 表示所有必须依赖均已满足)
     * @throws ScrmException 任务不存在
     */
    @RequirePermission(resource = "scrm_task_dependency", action = "read")
    @GetMapping("/dependencies/check/{taskId}")
    public OperationResponse<Boolean> checkDependencies(@PathVariable Long taskId) throws ScrmException {
        return OperationResponse.build(scrmTaskSchedulerService.checkDependencies(taskId));
    }

    /**
     * 执行依赖任务（父执行完成后触发）。
     * <p>
     * 注意: 依赖任务执行内部调用 executeTask → runExecution → simulateHandlerInvocation (占位实现),
     * 未对接真实调度框架, 该端点返回 501 NOT_IMPLEMENTED, 待对接后恢复。
     * </p>
     *
     * @param parentExecutionId 父执行 ID
     * @return 触发的依赖执行记录列表
     * @throws ScrmException 父执行不存在
     */
    @RequirePermission(resource = "scrm_scheduled_task", action = "execute")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/dependencies/execute-dependent")
    public OperationResponse<List<ScrmTaskExecutionEntity>> executeDependentTasks(
            @RequestParam Long parentExecutionId) throws ScrmException {
        return OperationResponse.build(scrmTaskSchedulerService.executeDependentTasks(parentExecutionId));
    }

    /**
     * 获取任务的依赖链（递归查询所有上游依赖）。
     *
     * @param taskId 任务 ID
     * @return 依赖链列表
     * @throws ScrmException 任务不存在
     */
    @RequirePermission(resource = "scrm_task_dependency", action = "read")
    @GetMapping("/dependencies/chain/{taskId}")
    public OperationResponse<List<ScrmTaskDependencyEntity>> getDependencyChain(@PathVariable Long taskId)
            throws ScrmException {
        return OperationResponse.build(scrmTaskSchedulerService.getDependencyChain(taskId));
    }

    // ============================================================
    // 统计 /stats
    // ============================================================

    /**
     * 任务统计概览 (总数/活跃/执行次数/成功率/平均时长)。
     *
     * @param startTime 起始时间 (含, 可空, 默认近 7 天)
     * @param endTime   截止时间 (含, 可空, 默认当前时间)
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_scheduled_task", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getTaskStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmTaskSchedulerService.getTaskStats(startTime, endTime));
    }

    /**
     * 执行统计 (各状态/触发类型/平均时长)。
     *
     * @param startTime 起始时间 (含, 可空, 默认近 7 天)
     * @param endTime   截止时间 (含, 可空, 默认当前时间)
     * @return 执行统计
     */
    @RequirePermission(resource = "scrm_scheduled_task", action = "read")
    @GetMapping("/stats/executions")
    public OperationResponse<Map<String, Object>> getExecutionStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmTaskSchedulerService.getExecutionStats(startTime, endTime));
    }

    /**
     * 失败分析 (失败原因/失败任务/趋势)。
     *
     * @param startTime 起始时间 (含, 可空, 默认近 7 天)
     * @param endTime   截止时间 (含, 可空, 默认当前时间)
     * @return 失败分析结果
     */
    @RequirePermission(resource = "scrm_scheduled_task", action = "read")
    @GetMapping("/stats/failures")
    public OperationResponse<Map<String, Object>> getFailureAnalysis(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmTaskSchedulerService.getFailureAnalysis(startTime, endTime));
    }

    /**
     * 性能统计 (最慢任务/最快任务/平均时长趋势)。
     *
     * @param startTime 起始时间 (含, 可空, 默认近 7 天)
     * @param endTime   截止时间 (含, 可空, 默认当前时间)
     * @return 性能统计
     */
    @RequirePermission(resource = "scrm_scheduled_task", action = "read")
    @GetMapping("/stats/performance")
    public OperationResponse<Map<String, Object>> getPerformanceStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmTaskSchedulerService.getPerformanceStats(startTime, endTime));
    }

    /**
     * 任务健康度 (各任务成功率/连续失败/上次执行)。
     *
     * @return 任务健康度列表
     */
    @RequirePermission(resource = "scrm_scheduled_task", action = "read")
    @GetMapping("/stats/health")
    public OperationResponse<List<Map<String, Object>>> getTaskHealth() {
        return OperationResponse.build(scrmTaskSchedulerService.getTaskHealth());
    }

    /**
     * 执行趋势 (按天聚合执行次数与成功率)。
     *
     * @param days 统计天数（默认 7）
     * @return 趋势数据列表
     */
    @RequirePermission(resource = "scrm_scheduled_task", action = "read")
    @GetMapping("/stats/trend")
    public OperationResponse<List<Map<String, Object>>> getExecutionTrend(
            @RequestParam(required = false, defaultValue = "7") Integer days) {
        return OperationResponse.build(scrmTaskSchedulerService.getExecutionTrend(days));
    }
}
