/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFollowUpController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmFollowUpRecordDto;
import org.hiylo.scrm.dto.ScrmFollowUpTaskDto;
import org.hiylo.scrm.dto.ScrmFollowUpTemplateDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmFollowUpService;
import org.hiylo.scrm.vo.FollowUpCalendarVo;
import org.hiylo.scrm.vo.FollowUpTaskStatsVo;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

/**
 * SCRM 跟进计划/任务管理控制器
 * <p>
 * 提供销售跟进任务的全生命周期管理、跟进模板维护与应用、跟进记录登记与查询、
 * 待提醒任务扫描、任务统计与跟进日历等接口。
 * 权限由 gateway-server 统一鉴权, 此处通过 {@link RequirePermission} 声明资源与动作元数据。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/follow-ups")
@RequiredArgsConstructor
public class ScrmFollowUpController {

    /** 跟进任务服务 */
    private final ScrmFollowUpService followUpService;

    // ============================================================
    // 任务管理
    // ============================================================

    /**
     * 创建跟进任务
     *
     * @param dto 任务参数
     * @return 创建后的任务
     */
    @RequirePermission(resource = "scrm_follow_up_task", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/tasks")
    public OperationResponse<ScrmFollowUpTaskDto> createTask(@Valid @RequestBody ScrmFollowUpTaskDto dto)
            throws ScrmException {
        return OperationResponse.build(followUpService.createTask(dto));
    }

    /**
     * 更新跟进任务
     *
     * @param id  任务 ID
     * @param dto 任务参数
     * @return 更新后的任务
     */
    @RequirePermission(resource = "scrm_follow_up_task", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/tasks/{id}")
    public OperationResponse<ScrmFollowUpTaskDto> updateTask(@PathVariable Long id,
                                                              @RequestBody ScrmFollowUpTaskDto dto)
            throws ScrmException {
        return OperationResponse.build(followUpService.updateTask(id, dto));
    }

    /**
     * 删除跟进任务
     *
     * @param id 任务 ID
     * @return 空响应
     * @throws ScrmException 任务不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_follow_up_task", action = "delete")
    @DeleteMapping("/tasks/{id}")
    public OperationResponse<Void> deleteTask(@PathVariable Long id) throws ScrmException {
        followUpService.deleteTask(id);
        return OperationResponse.build();
    }

    /**
     * 查询跟进任务详情
     *
     * @param id 任务 ID
     * @return 任务详情
     * @throws ScrmException 任务不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_follow_up_task", action = "read")
    @GetMapping("/tasks/{id}")
    public OperationResponse<ScrmFollowUpTaskDto> getTask(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(followUpService.getTask(id));
    }

    /**
     * 分页查询跟进任务, 支持按客户 / 负责人 / 状态 / 类型 / 优先级 / 计划时间区间过滤
     *
     * @param customerId 客户 ID 过滤 (可选)
     * @param assigneeId 负责人 ID 过滤 (可选)
     * @param status     状态过滤 (可选)
     * @param taskType   跟进类型过滤 (可选)
     * @param priority   优先级过滤 (可选)
     * @param startDate  计划起始时间 (可选, yyyy-MM-dd'T'HH:mm:ss)
     * @param endDate    计划截止时间 (可选, yyyy-MM-dd'T'HH:mm:ss)
     * @param page       页码 (从 0 开始, 默认 0)
     * @param size       每页大小 (默认 20)
     * @return 任务分页结果
     */
    @RequirePermission(resource = "scrm_follow_up_task", action = "read")
    @GetMapping("/tasks/list")
    public OperationResponse<Page<ScrmFollowUpTaskDto>> listTasks(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String assigneeId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(followUpService.listTasks(customerId, assigneeId, status,
                taskType, priority, startDate, endDate, pageable));
    }

    /**
     * 完成跟进任务
     *
     * @param id             任务 ID
     * @param followUpResult 跟进结果 (可选)
     * @param nextFollowUpAt 下次跟进时间 (可选, yyyy-MM-dd'T'HH:mm:ss)
     * @return 更新后的任务
     */
    @RequirePermission(resource = "scrm_follow_up_task", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/tasks/{id}/complete")
    public OperationResponse<ScrmFollowUpTaskDto> completeTask(
            @PathVariable Long id,
            @RequestParam(required = false) String followUpResult,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime nextFollowUpAt)
            throws ScrmException {
        return OperationResponse.build(followUpService.completeTask(id, followUpResult, nextFollowUpAt));
    }

    /**
     * 取消跟进任务
     *
     * @param id 任务 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_follow_up_task", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/tasks/{id}/cancel")
    public OperationResponse<ScrmFollowUpTaskDto> cancelTask(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(followUpService.cancelTask(id));
    }

    /**
     * 分配跟进任务 (变更负责人)
     *
     * @param id         任务 ID
     * @param assigneeId 新负责人 ID
     * @return 更新后的任务
     */
    @RequirePermission(resource = "scrm_follow_up_task", action = "assign")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/tasks/{id}/assign")
    public OperationResponse<ScrmFollowUpTaskDto> assignTask(@PathVariable Long id,
                                                              @RequestParam String assigneeId)
            throws ScrmException {
        return OperationResponse.build(followUpService.assignTask(id, assigneeId));
    }

    /**
     * 批量创建跟进任务
     *
     * @param tasks 任务参数列表
     * @return 成功创建的任务列表
     * @throws ScrmException 参数非法 / 批量创建失败
     */
    @RequirePermission(resource = "scrm_follow_up_task", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/tasks/batch")
    public OperationResponse<List<ScrmFollowUpTaskDto>> batchCreateTasks(
            @Valid @RequestBody List<ScrmFollowUpTaskDto> tasks) throws ScrmException {
        return OperationResponse.build(followUpService.batchCreate(tasks));
    }

    // ============================================================
    // 模板管理
    // ============================================================

    /**
     * 创建跟进模板
     *
     * @param dto 模板参数
     * @return 创建后的模板
     * @throws ScrmException 参数非法 / 模板编码重复
     */
    @RequirePermission(resource = "scrm_follow_up_template", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/templates")
    public OperationResponse<ScrmFollowUpTemplateDto> createTemplate(
            @Valid @RequestBody ScrmFollowUpTemplateDto dto) throws ScrmException {
        return OperationResponse.build(followUpService.createTemplate(dto));
    }

    /**
     * 更新跟进模板
     *
     * @param id  模板 ID
     * @param dto 模板参数
     * @return 更新后的模板
     */
    @RequirePermission(resource = "scrm_follow_up_template", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/templates/{id}")
    public OperationResponse<ScrmFollowUpTemplateDto> updateTemplate(@PathVariable Long id,
                                                                       @RequestBody ScrmFollowUpTemplateDto dto)
            throws ScrmException {
        return OperationResponse.build(followUpService.updateTemplate(id, dto));
    }

    /**
     * 删除跟进模板
     *
     * @param id 模板 ID
     * @return 空响应
     * @throws ScrmException 模板不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_follow_up_template", action = "delete")
    @DeleteMapping("/templates/{id}")
    public OperationResponse<Void> deleteTemplate(@PathVariable Long id) throws ScrmException {
        followUpService.deleteTemplate(id);
        return OperationResponse.build();
    }

    /**
     * 查询跟进模板详情
     *
     * @param id 模板 ID
     * @return 模板详情
     * @throws ScrmException 模板不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_follow_up_template", action = "read")
    @GetMapping("/templates/{id}")
    public OperationResponse<ScrmFollowUpTemplateDto> getTemplate(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(followUpService.getTemplate(id));
    }

    /**
     * 分页查询跟进模板, 支持按跟进类型 / 场景 / 启用状态过滤
     *
     * @param taskType 跟进类型过滤 (可选)
     * @param scenario 场景过滤 (可选)
     * @param enabled  启用状态过滤 (可选)
     * @param page     页码 (从 0 开始, 默认 0)
     * @param size     每页大小 (默认 20)
     * @return 模板分页结果
     */
    @RequirePermission(resource = "scrm_follow_up_template", action = "read")
    @GetMapping("/templates/list")
    public OperationResponse<Page<ScrmFollowUpTemplateDto>> listTemplates(
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String scenario,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(followUpService.listTemplates(taskType, scenario, enabled, pageable));
    }

    /**
     * 应用模板创建任务
     *
     * @param id         模板 ID
     * @param customerId 客户 ID
     * @param assigneeId 负责人 ID
     * @param plannedAt  计划跟进时间 (yyyy-MM-dd'T'HH:mm:ss)
     * @return 创建后的任务
     */
    @RequirePermission(resource = "scrm_follow_up_template", action = "apply")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/templates/{id}/apply")
    public OperationResponse<ScrmFollowUpTaskDto> applyTemplate(
            @PathVariable("id") Long id,
            @RequestParam Long customerId,
            @RequestParam String assigneeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime plannedAt)
            throws ScrmException {
        return OperationResponse.build(followUpService.applyTemplate(id, customerId, assigneeId, plannedAt));
    }

    // ============================================================
    // 跟进记录
    // ============================================================

    /**
     * 创建跟进记录
     *
     * @param dto 跟进记录参数
     * @return 创建后的跟进记录
     * @throws ScrmException 参数非法 / 客户或任务不存在
     */
    @RequirePermission(resource = "scrm_follow_up_record", action = "create")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/records")
    public OperationResponse<ScrmFollowUpRecordDto> createRecord(
            @Valid @RequestBody ScrmFollowUpRecordDto dto) throws ScrmException {
        return OperationResponse.build(followUpService.createRecord(dto));
    }

    /**
     * 分页查询跟进记录, 支持按客户 / 任务过滤
     *
     * @param customerId 客户 ID 过滤 (可选)
     * @param taskId     任务 ID 过滤 (可选)
     * @param page       页码 (从 0 开始, 默认 0)
     * @param size       每页大小 (默认 20)
     * @return 跟进记录分页结果
     */
    @RequirePermission(resource = "scrm_follow_up_record", action = "read")
    @GetMapping("/records/list")
    public OperationResponse<Page<ScrmFollowUpRecordDto>> listRecords(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long taskId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(followUpService.getRecords(customerId, taskId, pageable));
    }

    // ============================================================
    // 提醒
    // ============================================================

    /**
     * 获取待提醒任务列表 (reminded=false 且 plannedAt - reminderMinutes <= now)
     *
     * @return 待提醒任务列表
     */
    @RequirePermission(resource = "scrm_follow_up_task", action = "read")
    @GetMapping("/reminders")
    public OperationResponse<List<ScrmFollowUpTaskDto>> getPendingReminders() {
        return OperationResponse.build(followUpService.getPendingReminders());
    }

    // ============================================================
    // 统计与日历
    // ============================================================

    /**
     * 任务统计: 总数 / 完成数 / 完成率 / 逾期数 / 各类型分布
     *
     * @param assigneeId 负责人 ID 过滤 (可选, 空表示全员)
     * @param startDate  计划起始时间 (可选, yyyy-MM-dd'T'HH:mm:ss)
     * @param endDate    计划截止时间 (可选, yyyy-MM-dd'T'HH:mm:ss)
     * @return 任务统计结果
     */
    @RequirePermission(resource = "scrm_follow_up_task", action = "read")
    @GetMapping("/stats")
    public OperationResponse<FollowUpTaskStatsVo> getTaskStats(
            @RequestParam(required = false) String assigneeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return OperationResponse.build(followUpService.getTaskStats(assigneeId, startDate, endDate));
    }

    /**
     * 跟进日历: 按天聚合任务数 (用于日历视图)
     *
     * @param assigneeId 负责人 ID 过滤 (可选, 空表示全员)
     * @param month      月份 (yyyy-MM, 可选, 默认当月)
     * @return 跟进日历结果
     */
    @RequirePermission(resource = "scrm_follow_up_task", action = "read")
    @GetMapping("/calendar")
    public OperationResponse<FollowUpCalendarVo> getFollowUpCalendar(
            @RequestParam(required = false) String assigneeId,
            @RequestParam(required = false) String month) {
        return OperationResponse.build(followUpService.getFollowUpCalendar(assigneeId, month));
    }
}
