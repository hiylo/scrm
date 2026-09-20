/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmVisitController.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmVisitCompleteDto;
import org.hiylo.scrm.dto.ScrmVisitPlanDto;
import org.hiylo.scrm.dto.ScrmVisitRescheduleDto;
import org.hiylo.scrm.dto.ScrmVisitTaskDto;
import org.hiylo.scrm.dto.ScrmVisitTemplateDto;
import org.hiylo.scrm.entity.ScrmVisitPlanEntity;
import org.hiylo.scrm.entity.ScrmVisitTaskEntity;
import org.hiylo.scrm.entity.ScrmVisitTemplateEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmVisitService;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 客户回访管理控制器。
 * <p>
 * 提供客户回访的计划管理、任务管理、模板管理、提醒发送、统计分析接口。
 * 涵盖计划 CRUD 与生命周期管理 (激活/暂停/完成 + 任务生成 + 统计), 任务 CRUD
 * 与生命周期管理 (分配/开始/完成/取消/改期 + 今日/逾期/按负责人/按客户/批量分配),
 * 模板 CRUD 与启停 + 复制, 提醒 (单发 + 批发 + 待发查询),
 * 统计 (概览/计划/负责人/客户/趋势/满意度趋势/结果分布)。
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/visits")
@RequiredArgsConstructor
public class ScrmVisitController {

    /** 回访服务 */
    private final ScrmVisitService scrmVisitService;

    // ============================================================
    // 计划管理
    // ============================================================

    /**
     * 创建回访计划。
     *
     * @param dto 计划参数
     * @return 创建后的计划
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_visit", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/plans")
    public OperationResponse<ScrmVisitPlanEntity> createPlan(@Valid @RequestBody ScrmVisitPlanDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmVisitService.createPlan(dto));
    }

    /**
     * 更新回访计划。
     *
     * @param id  计划 ID
     * @param dto 计划参数
     * @return 更新后的计划
     * @throws ScrmException 计划不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_visit", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/plans/{id}")
    public OperationResponse<ScrmVisitPlanEntity> updatePlan(@PathVariable Long id,
                                                              @RequestBody ScrmVisitPlanDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmVisitService.updatePlan(id, dto));
    }

    /**
     * 删除回访计划。
     *
     * @param id 计划 ID
     * @return 空响应
     * @throws ScrmException 计划不存在
     */
    @RequirePermission(resource = "scrm_visit", action = "delete")
    @DeleteMapping("/plans/{id}")
    public OperationResponse<Void> deletePlan(@PathVariable Long id) throws ScrmException {
        scrmVisitService.deletePlan(id);
        return OperationResponse.build();
    }

    /**
     * 查询计划详情。
     *
     * @param id 计划 ID
     * @return 计划详情
     * @throws ScrmException 计划不存在
     */
    @RequirePermission(resource = "scrm_visit", action = "read")
    @GetMapping("/plans/{id}")
    public OperationResponse<ScrmVisitPlanEntity> getPlan(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmVisitService.getPlan(id));
    }

    /**
     * 按编码查询计划。
     *
     * @param code 计划编码
     * @return 计划详情
     * @throws ScrmException 计划不存在
     */
    @RequirePermission(resource = "scrm_visit", action = "read")
    @GetMapping("/plans/code/{code}")
    public OperationResponse<ScrmVisitPlanEntity> getPlanByCode(@PathVariable String code) throws ScrmException {
        return OperationResponse.build(scrmVisitService.getPlanByCode(code));
    }

    /**
     * 分页查询计划列表。
     *
     * @param planType 计划类型过滤（可空）
     * @param status   状态过滤（可空）
     * @param keyword  计划名称/编码关键字模糊匹配（可空）
     * @param page     页码（从 0 开始, 默认 0）
     * @param size     每页大小（默认 20）
     * @return 计划分页结果 (按 createTime DESC)
     */
    @RequirePermission(resource = "scrm_visit", action = "read")
    @GetMapping("/plans/list")
    public OperationResponse<Page<ScrmVisitPlanEntity>> listPlans(
            @RequestParam(required = false) String planType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmVisitService.listPlans(planType, status, keyword, pageable));
    }

    /**
     * 激活计划。
     *
     * @param id 计划 ID
     * @return 更新后的计划
     * @throws ScrmException 计划不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_visit", action = "update")
    @PostMapping("/plans/{id}/activate")
    public OperationResponse<ScrmVisitPlanEntity> activatePlan(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmVisitService.activatePlan(id));
    }

    /**
     * 暂停计划。
     *
     * @param id 计划 ID
     * @return 更新后的计划
     * @throws ScrmException 计划不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_visit", action = "update")
    @PostMapping("/plans/{id}/pause")
    public OperationResponse<ScrmVisitPlanEntity> pausePlan(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmVisitService.pausePlan(id));
    }

    /**
     * 完成计划。
     *
     * @param id 计划 ID
     * @return 更新后的计划
     * @throws ScrmException 计划不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_visit", action = "update")
    @PostMapping("/plans/{id}/complete")
    public OperationResponse<ScrmVisitPlanEntity> completePlan(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmVisitService.completePlan(id));
    }

    /**
     * 根据计划生成回访任务。
     *
     * @param id 计划 ID
     * @return 生成的任务列表
     * @throws ScrmException 计划不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_visit", action = "update")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/plans/{id}/generate-tasks")
    public OperationResponse<List<ScrmVisitTaskEntity>> generateTasks(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmVisitService.generateTasks(id));
    }

    /**
     * 更新计划统计指标。
     *
     * @param id 计划 ID
     * @return 更新后的计划
     * @throws ScrmException 计划不存在
     */
    @RequirePermission(resource = "scrm_visit", action = "update")
    @PostMapping("/plans/{id}/stats")
    public OperationResponse<ScrmVisitPlanEntity> updatePlanStats(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmVisitService.updatePlanStats(id));
    }

    /**
     * 查询客户相关的计划。
     *
     * @param customerId 客户 ID
     * @return 计划列表
     */
    @RequirePermission(resource = "scrm_visit", action = "read")
    @GetMapping("/plans/by-customer/{customerId}")
    public OperationResponse<List<ScrmVisitPlanEntity>> getPlansByCustomer(@PathVariable Long customerId) {
        return OperationResponse.build(scrmVisitService.getPlansByCustomer(customerId));
    }

    // ============================================================
    // 任务管理
    // ============================================================

    /**
     * 创建回访任务。
     *
     * @param dto 任务参数
     * @return 创建后的任务
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_visit", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/tasks")
    public OperationResponse<ScrmVisitTaskEntity> createTask(@Valid @RequestBody ScrmVisitTaskDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmVisitService.createTask(dto));
    }

    /**
     * 更新回访任务。
     *
     * @param id  任务 ID
     * @param dto 任务参数
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_visit", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/tasks/{id}")
    public OperationResponse<ScrmVisitTaskEntity> updateTask(@PathVariable Long id,
                                                              @RequestBody ScrmVisitTaskDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmVisitService.updateTask(id, dto));
    }

    /**
     * 删除回访任务。
     *
     * @param id 任务 ID
     * @return 空响应
     * @throws ScrmException 任务不存在
     */
    @RequirePermission(resource = "scrm_visit", action = "delete")
    @DeleteMapping("/tasks/{id}")
    public OperationResponse<Void> deleteTask(@PathVariable Long id) throws ScrmException {
        scrmVisitService.deleteTask(id);
        return OperationResponse.build();
    }

    /**
     * 查询任务详情。
     *
     * @param id 任务 ID
     * @return 任务详情
     * @throws ScrmException 任务不存在
     */
    @RequirePermission(resource = "scrm_visit", action = "read")
    @GetMapping("/tasks/{id}")
    public OperationResponse<ScrmVisitTaskEntity> getTask(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmVisitService.getTask(id));
    }

    /**
     * 按任务编号查询。
     *
     * @param taskNo 任务编号
     * @return 任务详情
     * @throws ScrmException 任务不存在
     */
    @RequirePermission(resource = "scrm_visit", action = "read")
    @GetMapping("/tasks/by-no/{taskNo}")
    public OperationResponse<ScrmVisitTaskEntity> getTaskByNo(@PathVariable String taskNo) throws ScrmException {
        return OperationResponse.build(scrmVisitService.getTaskByNo(taskNo));
    }

    /**
     * 分页查询任务列表。
     *
     * @param planId      计划 ID 过滤（可空）
     * @param customerId  客户 ID 过滤（可空）
     * @param visitType   回访类型过滤（可空）
     * @param visitMethod 回访方式过滤（可空）
     * @param status      状态过滤（可空）
     * @param assignedTo  负责人过滤（可空）
     * @param startDate   计划日期起始 (含, 可空, ISO 格式: yyyy-MM-dd)
     * @param endDate     计划日期截止 (含, 可空, ISO 格式: yyyy-MM-dd)
     * @param keyword     任务编号/客户名称关键字模糊匹配（可空）
     * @param page        页码（从 0 开始, 默认 0）
     * @param size        每页大小（默认 20）
     * @return 任务分页结果 (按 createTime DESC)
     */
    @RequirePermission(resource = "scrm_visit", action = "read")
    @GetMapping("/tasks/list")
    public OperationResponse<Page<ScrmVisitTaskEntity>> listTasks(
            @RequestParam(required = false) Long planId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String visitType,
            @RequestParam(required = false) String visitMethod,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String assignedTo,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmVisitService.listTasks(planId, customerId, visitType,
                visitMethod, status, assignedTo, startDate, endDate, keyword, pageable));
    }

    /**
     * 分配任务给指定负责人。
     *
     * @param id         任务 ID
     * @param assigneeId 负责人 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_visit", action = "update")
    @PostMapping("/tasks/{id}/assign")
    public OperationResponse<ScrmVisitTaskEntity> assignTask(@PathVariable Long id,
                                                              @RequestParam String assigneeId)
            throws ScrmException {
        return OperationResponse.build(scrmVisitService.assignTask(id, assigneeId));
    }

    /**
     * 开始回访。
     *
     * @param id 任务 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_visit", action = "update")
    @PostMapping("/tasks/{id}/start")
    public OperationResponse<ScrmVisitTaskEntity> startTask(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmVisitService.startTask(id));
    }

    /**
     * 完成回访。
     *
     * @param dto 完成参数
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_visit", action = "update")
    @PostMapping("/tasks/complete")
    public OperationResponse<ScrmVisitTaskEntity> completeTask(@Valid @RequestBody ScrmVisitCompleteDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmVisitService.completeTask(dto));
    }

    /**
     * 取消任务。
     *
     * @param id     任务 ID
     * @param reason 取消原因（可空）
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_visit", action = "update")
    @PostMapping("/tasks/{id}/cancel")
    public OperationResponse<ScrmVisitTaskEntity> cancelTask(@PathVariable Long id,
                                                              @RequestParam(required = false) String reason)
            throws ScrmException {
        return OperationResponse.build(scrmVisitService.cancelTask(id, reason));
    }

    /**
     * 改期任务。
     *
     * @param dto 改期参数
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法 / 日期非法
     */
    @RequirePermission(resource = "scrm_visit", action = "update")
    @PostMapping("/tasks/reschedule")
    public OperationResponse<ScrmVisitTaskEntity> rescheduleTask(@Valid @RequestBody ScrmVisitRescheduleDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmVisitService.rescheduleTask(dto));
    }

    /**
     * 分页查询逾期任务。
     *
     * @param page 页码（从 0 开始, 默认 0）
     * @param size 每页大小（默认 20）
     * @return 任务分页结果 (按 scheduledDate ASC)
     */
    @RequirePermission(resource = "scrm_visit", action = "read")
    @GetMapping("/tasks/overdue")
    public OperationResponse<Page<ScrmVisitTaskEntity>> getOverdueTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "scheduledDate"));
        return OperationResponse.build(scrmVisitService.getOverdueTasks(pageable));
    }

    /**
     * 查询指定负责人的今日任务。
     *
     * @param assigneeId 负责人 ID（可空, 为空则查询全部今日任务）
     * @param page       页码（从 0 开始, 默认 0）
     * @param size       每页大小（默认 20）
     * @return 任务分页结果 (按 scheduledTime ASC)
     */
    @RequirePermission(resource = "scrm_visit", action = "read")
    @GetMapping("/tasks/today")
    public OperationResponse<Page<ScrmVisitTaskEntity>> getTodayTasks(
            @RequestParam(required = false) String assigneeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "scheduledTime"));
        return OperationResponse.build(scrmVisitService.getTodayTasks(assigneeId, pageable));
    }

    /**
     * 按负责人查询任务。
     *
     * @param assigneeId 负责人 ID
     * @param status     状态过滤（可空）
     * @param page       页码（从 0 开始, 默认 0）
     * @param size       每页大小（默认 20）
     * @return 任务分页结果 (按 createTime DESC)
     */
    @RequirePermission(resource = "scrm_visit", action = "read")
    @GetMapping("/tasks/by-assignee/{assigneeId}")
    public OperationResponse<Page<ScrmVisitTaskEntity>> getTasksByAssignee(
            @PathVariable String assigneeId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmVisitService.getTasksByAssignee(assigneeId, status, pageable));
    }

    /**
     * 按客户查询任务。
     *
     * @param customerId 客户 ID
     * @param page       页码（从 0 开始, 默认 0）
     * @param size       每页大小（默认 20）
     * @return 任务分页结果 (按 scheduledDate DESC)
     */
    @RequirePermission(resource = "scrm_visit", action = "read")
    @GetMapping("/tasks/by-customer/{customerId}")
    public OperationResponse<Page<ScrmVisitTaskEntity>> getTasksByCustomer(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "scheduledDate"));
        return OperationResponse.build(scrmVisitService.getTasksByCustomer(customerId, pageable));
    }

    /**
     * 批量分配任务。
     *
     * @param taskIds    任务 ID 列表
     * @param assigneeId 负责人 ID
     * @return 已分配的任务列表
     * @throws ScrmException 负责人 ID 非法
     */
    @RequirePermission(resource = "scrm_visit", action = "update")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/tasks/batch-assign")
    public OperationResponse<List<ScrmVisitTaskEntity>> batchAssignTasks(
            @RequestParam List<Long> taskIds,
            @RequestParam String assigneeId) throws ScrmException {
        return OperationResponse.build(scrmVisitService.batchAssignTasks(taskIds, assigneeId));
    }

    // ============================================================
    // 模板管理
    // ============================================================

    /**
     * 创建回访模板。
     *
     * @param dto 模板参数
     * @return 创建后的模板
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_visit", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/templates")
    public OperationResponse<ScrmVisitTemplateEntity> createTemplate(@Valid @RequestBody ScrmVisitTemplateDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmVisitService.createTemplate(dto));
    }

    /**
     * 更新回访模板。
     *
     * @param id  模板 ID
     * @param dto 模板参数
     * @return 更新后的模板
     * @throws ScrmException 模板不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_visit", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/templates/{id}")
    public OperationResponse<ScrmVisitTemplateEntity> updateTemplate(@PathVariable Long id,
                                                                      @RequestBody ScrmVisitTemplateDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmVisitService.updateTemplate(id, dto));
    }

    /**
     * 删除回访模板。
     *
     * @param id 模板 ID
     * @return 空响应
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_visit", action = "delete")
    @DeleteMapping("/templates/{id}")
    public OperationResponse<Void> deleteTemplate(@PathVariable Long id) throws ScrmException {
        scrmVisitService.deleteTemplate(id);
        return OperationResponse.build();
    }

    /**
     * 查询模板详情。
     *
     * @param id 模板 ID
     * @return 模板详情
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_visit", action = "read")
    @GetMapping("/templates/{id}")
    public OperationResponse<ScrmVisitTemplateEntity> getTemplate(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmVisitService.getTemplate(id));
    }

    /**
     * 按编码查询模板。
     *
     * @param code 模板编码
     * @return 模板详情
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_visit", action = "read")
    @GetMapping("/templates/code/{code}")
    public OperationResponse<ScrmVisitTemplateEntity> getTemplateByCode(
            @PathVariable String code) throws ScrmException {
        return OperationResponse.build(scrmVisitService.getTemplateByCode(code));
    }

    /**
     * 分页查询模板列表。
     *
     * @param visitType   回访类型过滤（可空）
     * @param visitMethod 回访方式过滤（可空）
     * @param enabled     启用状态过滤（可空）
     * @param page        页码（从 0 开始, 默认 0）
     * @param size        每页大小（默认 20）
     * @return 模板分页结果 (按 createTime DESC)
     */
    @RequirePermission(resource = "scrm_visit", action = "read")
    @GetMapping("/templates/list")
    public OperationResponse<Page<ScrmVisitTemplateEntity>> listTemplates(
            @RequestParam(required = false) String visitType,
            @RequestParam(required = false) String visitMethod,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmVisitService.listTemplates(visitType, visitMethod, enabled, pageable));
    }

    /**
     * 启用模板。
     *
     * @param id 模板 ID
     * @return 更新后的模板
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_visit", action = "update")
    @PostMapping("/templates/{id}/enable")
    public OperationResponse<ScrmVisitTemplateEntity> enableTemplate(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmVisitService.enableTemplate(id));
    }

    /**
     * 停用模板。
     *
     * @param id 模板 ID
     * @return 更新后的模板
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_visit", action = "update")
    @PostMapping("/templates/{id}/disable")
    public OperationResponse<ScrmVisitTemplateEntity> disableTemplate(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmVisitService.disableTemplate(id));
    }

    /**
     * 复制模板。
     *
     * @param id      源模板 ID
     * @param newCode 新模板编码
     * @return 复制后的模板
     * @throws ScrmException 源模板不存在 / 编码已存在
     */
    @RequirePermission(resource = "scrm_visit", action = "create")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/templates/{id}/copy")
    public OperationResponse<ScrmVisitTemplateEntity> copyTemplate(@PathVariable Long id,
                                                                    @RequestParam String newCode)
            throws ScrmException {
        return OperationResponse.build(scrmVisitService.copyTemplate(id, newCode));
    }

    // ============================================================
    // 提醒
    // ============================================================

    /**
     * 发送任务提醒。
     *
     * @param taskId 任务 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在
     */
    @RequirePermission(resource = "scrm_visit", action = "update")
    @PostMapping("/reminders/send/{taskId}")
    public OperationResponse<ScrmVisitTaskEntity> sendReminder(@PathVariable Long taskId) throws ScrmException {
        return OperationResponse.build(scrmVisitService.sendReminder(taskId));
    }

    /**
     * 批量发送提醒。
     *
     * @return 已发送提醒的任务列表
     */
    @RequirePermission(resource = "scrm_visit", action = "update")
    @RateLimit(capacity = 1, refillTokens = 1, refillPeriodSeconds = 60)
    @PostMapping("/reminders/batch-send")
    public OperationResponse<List<ScrmVisitTaskEntity>> batchSendReminders() {
        return OperationResponse.build(scrmVisitService.batchSendReminders());
    }

    /**
     * 查询待发送提醒的任务。
     *
     * @return 任务列表
     */
    @RequirePermission(resource = "scrm_visit", action = "read")
    @GetMapping("/reminders/pending")
    public OperationResponse<List<ScrmVisitTaskEntity>> getPendingReminders() {
        return OperationResponse.build(scrmVisitService.getPendingReminders());
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 回访统计概览: 总数 / 完成率 / 满意度 / 成功率 / 各类型。
     *
     * @param startTime 开始时间 (含, 可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   结束时间 (含, 可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_visit", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getVisitStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmVisitService.getVisitStats(startTime, endTime));
    }

    /**
     * 计划统计。
     *
     * @param planId 计划 ID
     * @return 统计结果 Map
     * @throws ScrmException 计划不存在
     */
    @RequirePermission(resource = "scrm_visit", action = "read")
    @GetMapping("/stats/plan/{planId}")
    public OperationResponse<Map<String, Object>> getPlanStats(@PathVariable Long planId) throws ScrmException {
        return OperationResponse.build(scrmVisitService.getPlanStats(planId));
    }

    /**
     * 负责人统计。
     *
     * @param assigneeId 负责人 ID
     * @param startTime  开始时间 (含, 可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime    结束时间 (含, 可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_visit", action = "read")
    @GetMapping("/stats/assignee/{assigneeId}")
    public OperationResponse<Map<String, Object>> getAssigneeStats(
            @PathVariable String assigneeId,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmVisitService.getAssigneeStats(assigneeId, startTime, endTime));
    }

    /**
     * 客户回访历史。
     *
     * @param customerId 客户 ID
     * @return 任务列表
     */
    @RequirePermission(resource = "scrm_visit", action = "read")
    @GetMapping("/stats/customer/{customerId}")
    public OperationResponse<List<ScrmVisitTaskEntity>> getCustomerVisitHistory(@PathVariable Long customerId) {
        return OperationResponse.build(scrmVisitService.getCustomerVisitHistory(customerId));
    }

    /**
     * 回访趋势: 按日聚合任务数。
     *
     * @param days 天数 (从今天向前推算, 默认 7)
     * @return 趋势列表 [{date, count}]
     */
    @RequirePermission(resource = "scrm_visit", action = "read")
    @GetMapping("/stats/trend")
    public OperationResponse<List<Map<String, Object>>> getVisitTrend(
            @RequestParam(defaultValue = "7") int days) {
        return OperationResponse.build(scrmVisitService.getVisitTrend(days));
    }

    /**
     * 满意度趋势: 按日聚合已完成任务的平均满意度。
     *
     * @param days 天数 (从今天向前推算, 默认 7)
     * @return 趋势列表 [{date, avgScore}]
     */
    @RequirePermission(resource = "scrm_visit", action = "read")
    @GetMapping("/stats/satisfaction-trend")
    public OperationResponse<List<Map<String, Object>>> getSatisfactionTrend(
            @RequestParam(defaultValue = "7") int days) {
        return OperationResponse.build(scrmVisitService.getSatisfactionTrend(days));
    }

    /**
     * 回访结果分布: 按结果聚合任务数。
     *
     * @param startTime 开始时间 (含, 可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   结束时间 (含, 可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 分布列表 [{outcome, count}]
     */
    @RequirePermission(resource = "scrm_visit", action = "read")
    @GetMapping("/stats/outcome")
    public OperationResponse<List<Map<String, Object>>> getOutcomeDistribution(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmVisitService.getOutcomeDistribution(startTime, endTime));
    }
}
