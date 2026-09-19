/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmInteractionCalendarController.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmCalendarViewDto;
import org.hiylo.scrm.dto.ScrmInteractionCalendarDto;
import org.hiylo.scrm.dto.ScrmInteractionPlanCompleteDto;
import org.hiylo.scrm.dto.ScrmInteractionPlanDto;
import org.hiylo.scrm.dto.ScrmInteractionRescheduleDto;
import org.hiylo.scrm.entity.ScrmInteractionCalendarEntity;
import org.hiylo.scrm.entity.ScrmInteractionPlanEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmInteractionCalendarService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
 * SCRM 客户互动日历控制器。
 * <p>
 * 提供客户互动计划管理 (CRUD + 生命周期流转 + 批量操作 + 复制 + 改期 + 完成 +
 * 提醒 + 重复计划处理), 互动日历管理 (CRUD + 启停 + 共享 + 日/周/月/议程视图聚合 +
 * 空闲时段查找 + 冲突检测 + 统计), 互动统计 (概览 / 客户 / 负责人 / 趋势 / 完成率 /
 * 未到率 / 最佳时段 / 热力图) 接口。权限由 gateway-server 统一鉴权,
 * {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@RestController
@RequestMapping("/scrm/interaction-calendar")
@RequiredArgsConstructor
// Tag: SCRM 客户互动日历 -
public class ScrmInteractionCalendarController {

    /** 客户互动日历服务 */
    private final ScrmInteractionCalendarService interactionCalendarService;

    // ============================================================
    // 计划管理 /plans
    // ============================================================

    /**
     * 创建互动计划。
     *
     * @param dto 计划参数
     * @return 创建后的计划
     * @throws ScrmException 参数非法 / planCode 重复
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/plans")
    public OperationResponse<ScrmInteractionPlanEntity> createPlan(@Valid @RequestBody ScrmInteractionPlanDto dto)
            throws ScrmException {
        return OperationResponse.build(interactionCalendarService.createPlan(dto));
    }

    /**
     * 更新互动计划。
     *
     * @param id  计划 ID
     * @param dto 计划参数
     * @return 更新后的计划
     * @throws ScrmException 计划不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/plans/{id}")
    public OperationResponse<ScrmInteractionPlanEntity> updatePlan(@PathVariable Long id,
                                                                   @RequestBody ScrmInteractionPlanDto dto)
            throws ScrmException {
        return OperationResponse.build(interactionCalendarService.updatePlan(id, dto));
    }

    /**
     * 删除互动计划。
     *
     * @param id 计划 ID
     * @return 空响应
     * @throws ScrmException 计划不存在
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "delete")
    @DeleteMapping("/plans/{id}")
    public OperationResponse<Void> deletePlan(@PathVariable Long id) throws ScrmException {
        interactionCalendarService.deletePlan(id);
        return OperationResponse.build();
    }

    /**
     * 查询计划详情。
     *
     * @param id 计划 ID
     * @return 计划详情
     * @throws ScrmException 计划不存在
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "read")
    @GetMapping("/plans/{id}")
    public OperationResponse<ScrmInteractionPlanEntity> getPlan(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(interactionCalendarService.getPlan(id));
    }

    /**
     * 按计划编码查询计划。
     *
     * @param code 计划编码
     * @return 计划详情
     * @throws ScrmException 计划不存在
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "read")
    @GetMapping("/plans/code/{code}")
    public OperationResponse<ScrmInteractionPlanEntity> getPlanByCode(@PathVariable String code)
            throws ScrmException {
        return OperationResponse.build(interactionCalendarService.getPlanByCode(code));
    }

    /**
     * 分页查询计划列表, 支持多条件过滤。
     *
     * @param customerId      客户 ID (可选)
     * @param ownerId         负责人 ID (可选)
     * @param interactionType 互动类型 (可选)
     * @param status          状态 (可选)
     * @param priority        优先级 (可选)
     * @param startTime       计划开始时间下限 (可选)
     * @param endTime         计划开始时间上限 (可选)
     * @param keyword         关键字 (匹配计划名称/编码/主题, 可选)
     * @param page            页码 (从 0 开始, 默认 0)
     * @param size            每页大小 (默认 20)
     * @return 计划分页结果
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "read")
    @GetMapping("/plans/list")
    public OperationResponse<Page<ScrmInteractionPlanEntity>> listPlans(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String ownerId,
            @RequestParam(required = false) String interactionType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(interactionCalendarService.listPlans(
                customerId, ownerId, interactionType, status, priority,
                startTime, endTime, keyword, pageable));
    }

    /**
     * 按客户分页查询计划。
     *
     * @param customerId 客户 ID
     * @param page       页码 (从 0 开始, 默认 0)
     * @param size       每页大小 (默认 20)
     * @return 计划分页结果
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "read")
    @GetMapping("/plans/by-customer/{customerId}")
    public OperationResponse<Page<ScrmInteractionPlanEntity>> getPlansByCustomer(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(interactionCalendarService.getPlansByCustomer(customerId, pageable));
    }

    /**
     * 按负责人分页查询计划。
     *
     * @param ownerId 负责人 ID
     * @param page    页码 (从 0 开始, 默认 0)
     * @param size    每页大小 (默认 20)
     * @return 计划分页结果
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "read")
    @GetMapping("/plans/by-owner/{ownerId}")
    public OperationResponse<Page<ScrmInteractionPlanEntity>> getPlansByOwner(
            @PathVariable String ownerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(interactionCalendarService.getPlansByOwner(ownerId, pageable));
    }

    /**
     * 按日期范围查询计划 (区间交集)。
     *
     * @param startDate 开始时间 (含)
     * @param endDate   结束时间 (含)
     * @param ownerId   负责人 ID (可选)
     * @return 计划列表
     * @throws ScrmException 日期范围非法
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "read")
    @GetMapping("/plans/by-date-range")
    public OperationResponse<List<ScrmInteractionPlanEntity>> getPlansByDateRange(
            @RequestParam LocalDateTime startDate,
            @RequestParam LocalDateTime endDate,
            @RequestParam(required = false) String ownerId) throws ScrmException {
        return OperationResponse.build(
                interactionCalendarService.getPlansByDateRange(startDate, endDate, ownerId));
    }

    /**
     * 完成互动计划。
     *
     * @param dto 完成参数
     * @return 更新后的计划
     * @throws ScrmException 计划不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "execute")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/plans/complete")
    public OperationResponse<ScrmInteractionPlanEntity> completePlan(
            @Valid @RequestBody ScrmInteractionPlanCompleteDto dto) throws ScrmException {
        return OperationResponse.build(interactionCalendarService.completePlan(dto));
    }

    /**
     * 取消互动计划。
     *
     * @param id     计划 ID
     * @param reason 取消原因 (可选)
     * @return 更新后的计划
     * @throws ScrmException 计划不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "execute")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/plans/cancel")
    public OperationResponse<ScrmInteractionPlanEntity> cancelPlan(
            @RequestParam Long id,
            @RequestParam(required = false) String reason) throws ScrmException {
        return OperationResponse.build(interactionCalendarService.cancelPlan(id, reason));
    }

    /**
     * 改期互动计划。
     *
     * @param dto 改期参数
     * @return 更新后的计划
     * @throws ScrmException 计划不存在 / 状态非法 / 日期非法
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "execute")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/plans/reschedule")
    public OperationResponse<ScrmInteractionPlanEntity> reschedulePlan(
            @Valid @RequestBody ScrmInteractionRescheduleDto dto) throws ScrmException {
        return OperationResponse.build(interactionCalendarService.reschedulePlan(dto));
    }

    /**
     * 确认互动计划。
     *
     * @param id 计划 ID
     * @return 更新后的计划
     * @throws ScrmException 计划不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "execute")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/plans/{id}/confirm")
    public OperationResponse<ScrmInteractionPlanEntity> confirmPlan(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(interactionCalendarService.confirmPlan(id));
    }

    /**
     * 开始互动计划。
     *
     * @param id 计划 ID
     * @return 更新后的计划
     * @throws ScrmException 计划不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "execute")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/plans/{id}/start")
    public OperationResponse<ScrmInteractionPlanEntity> startPlan(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(interactionCalendarService.startPlan(id));
    }

    /**
     * 标记互动计划未到。
     *
     * @param id     计划 ID
     * @param reason 原因 (可选)
     * @return 更新后的计划
     * @throws ScrmException 计划不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "execute")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/plans/no-show")
    public OperationResponse<ScrmInteractionPlanEntity> markNoShow(
            @RequestParam Long id,
            @RequestParam(required = false) String reason) throws ScrmException {
        return OperationResponse.build(interactionCalendarService.markNoShow(id, reason));
    }

    /**
     * 发送互动提醒。
     *
     * @param id 计划 ID
     * @return 更新后的计划
     * @throws ScrmException 计划不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "execute")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/plans/{id}/reminder")
    public OperationResponse<ScrmInteractionPlanEntity> sendReminder(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(interactionCalendarService.sendReminder(id));
    }

    /**
     * 检查待提醒计划 (批量扫描并发送)。
     *
     * @return 已发送提醒的计划列表
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "execute")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/plans/check-reminders")
    public OperationResponse<List<ScrmInteractionPlanEntity>> checkReminders() {
        return OperationResponse.build(interactionCalendarService.checkReminders());
    }

    /**
     * 处理重复计划 (生成下一周期实例)。
     *
     * @return 生成的下一周期计划列表
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "execute")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/plans/process-recurring")
    public OperationResponse<List<ScrmInteractionPlanEntity>> processRecurringPlans() {
        return OperationResponse.build(interactionCalendarService.processRecurringPlans());
    }

    /**
     * 查询即将到来的计划。
     *
     * @param ownerId 负责人 ID (可选)
     * @param days    天数 (默认 7)
     * @return 计划列表
     * @throws ScrmException 天数非法
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "read")
    @GetMapping("/plans/upcoming")
    public OperationResponse<List<ScrmInteractionPlanEntity>> getUpcomingPlans(
            @RequestParam(required = false) String ownerId,
            @RequestParam(defaultValue = "7") int days) throws ScrmException {
        return OperationResponse.build(interactionCalendarService.getUpcomingPlans(ownerId, days));
    }

    /**
     * 查询逾期计划。
     *
     * @param ownerId 负责人 ID (可选)
     * @return 计划列表
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "read")
    @GetMapping("/plans/overdue")
    public OperationResponse<List<ScrmInteractionPlanEntity>> getOverduePlans(
            @RequestParam(required = false) String ownerId) {
        return OperationResponse.build(interactionCalendarService.getOverduePlans(ownerId));
    }

    /**
     * 查询今日计划。
     *
     * @param ownerId 负责人 ID (可选)
     * @return 计划列表
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "read")
    @GetMapping("/plans/today")
    public OperationResponse<List<ScrmInteractionPlanEntity>> getTodayPlans(
            @RequestParam(required = false) String ownerId) {
        return OperationResponse.build(interactionCalendarService.getTodayPlans(ownerId));
    }

    /**
     * 按状态分页查询计划。
     *
     * @param status 状态
     * @param page   页码 (从 0 开始, 默认 0)
     * @param size   每页大小 (默认 20)
     * @return 计划分页结果
     * @throws ScrmException 状态为空
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "read")
    @GetMapping("/plans/by-status")
    public OperationResponse<Page<ScrmInteractionPlanEntity>> getPlansByStatus(
            @RequestParam String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) throws ScrmException {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(interactionCalendarService.getPlansByStatus(status, pageable));
    }

    /**
     * 复制互动计划。
     *
     * @param id       源计划 ID
     * @param newStart 新开始时间
     * @return 复制后的计划
     * @throws ScrmException 源计划不存在 / 新开始时间为空
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/plans/duplicate")
    public OperationResponse<ScrmInteractionPlanEntity> duplicatePlan(
            @RequestParam Long id,
            @RequestParam LocalDateTime newStart) throws ScrmException {
        return OperationResponse.build(interactionCalendarService.duplicatePlan(id, newStart));
    }

    /**
     * 批量创建互动计划。
     *
     * @param dtos 计划参数列表
     * @return 创建后的计划列表
     * @throws ScrmException 列表为空
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "create")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/plans/batch")
    public OperationResponse<List<ScrmInteractionPlanEntity>> batchCreatePlans(
            @Valid @RequestBody List<ScrmInteractionPlanDto> dtos) throws ScrmException {
        return OperationResponse.build(interactionCalendarService.batchCreatePlans(dtos));
    }

    /**
     * 批量取消互动计划。
     *
     * @param planIds 计划 ID 列表
     * @param reason  取消原因 (可选)
     * @return 已取消的计划列表
     * @throws ScrmException 列表为空
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "execute")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/plans/batch-cancel")
    public OperationResponse<List<ScrmInteractionPlanEntity>> batchCancelPlans(
            @RequestParam List<Long> planIds,
            @RequestParam(required = false) String reason) throws ScrmException {
        return OperationResponse.build(interactionCalendarService.batchCancel(planIds, reason));
    }

    /**
     * 客户互动时间线 (按月聚合)。
     *
     * @param customerId 客户 ID
     * @param months     月数 (默认 6)
     * @return 时间线列表
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "read")
    @GetMapping("/plans/timeline/{customerId}")
    public OperationResponse<List<Map<String, Object>>> getPlanTimeline(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "6") int months) throws ScrmException {
        return OperationResponse.build(interactionCalendarService.getPlanTimeline(customerId, months));
    }

    /**
     * 客户互动频率 (按月聚合)。
     *
     * @param customerId 客户 ID
     * @param months     月数 (默认 6)
     * @return 频率列表
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "read")
    @GetMapping("/plans/frequency/{customerId}")
    public OperationResponse<List<Map<String, Object>>> getInteractionFrequency(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "6") int months) throws ScrmException {
        return OperationResponse.build(interactionCalendarService.getInteractionFrequency(customerId, months));
    }

    // ============================================================
    // 日历管理 /calendars
    // ============================================================

    /**
     * 创建互动日历。
     *
     * @param dto 日历参数
     * @return 创建后的日历
     * @throws ScrmException 参数非法 / calendarCode 重复
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/calendars")
    public OperationResponse<ScrmInteractionCalendarEntity> createCalendar(
            @Valid @RequestBody ScrmInteractionCalendarDto dto) throws ScrmException {
        return OperationResponse.build(interactionCalendarService.createCalendar(dto));
    }

    /**
     * 更新互动日历。
     *
     * @param id  日历 ID
     * @param dto 日历参数
     * @return 更新后的日历
     * @throws ScrmException 日历不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/calendars/{id}")
    public OperationResponse<ScrmInteractionCalendarEntity> updateCalendar(@PathVariable Long id,
                                                                           @RequestBody ScrmInteractionCalendarDto dto)
            throws ScrmException {
        return OperationResponse.build(interactionCalendarService.updateCalendar(id, dto));
    }

    /**
     * 删除互动日历。
     *
     * @param id 日历 ID
     * @return 空响应
     * @throws ScrmException 日历不存在
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "delete")
    @DeleteMapping("/calendars/{id}")
    public OperationResponse<Void> deleteCalendar(@PathVariable Long id) throws ScrmException {
        interactionCalendarService.deleteCalendar(id);
        return OperationResponse.build();
    }

    /**
     * 查询日历详情。
     *
     * @param id 日历 ID
     * @return 日历详情
     * @throws ScrmException 日历不存在
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "read")
    @GetMapping("/calendars/{id}")
    public OperationResponse<ScrmInteractionCalendarEntity> getCalendar(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(interactionCalendarService.getCalendar(id));
    }

    /**
     * 按日历编码查询日历。
     *
     * @param code 日历编码
     * @return 日历详情
     * @throws ScrmException 日历不存在
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "read")
    @GetMapping("/calendars/code/{code}")
    public OperationResponse<ScrmInteractionCalendarEntity> getCalendarByCode(@PathVariable String code)
            throws ScrmException {
        return OperationResponse.build(interactionCalendarService.getCalendarByCode(code));
    }

    /**
     * 分页查询日历列表。
     *
     * @param ownerId      所有者 ID (可选)
     * @param calendarType 日历类型 (可选)
     * @param enabled      启用状态 (可选)
     * @param page         页码 (从 0 开始, 默认 0)
     * @param size         每页大小 (默认 20)
     * @return 日历分页结果
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "read")
    @GetMapping("/calendars/list")
    public OperationResponse<Page<ScrmInteractionCalendarEntity>> listCalendars(
            @RequestParam(required = false) String ownerId,
            @RequestParam(required = false) String calendarType,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(
                interactionCalendarService.listCalendars(ownerId, calendarType, enabled, pageable));
    }

    /**
     * 启用日历。
     *
     * @param id 日历 ID
     * @return 更新后的日历
     * @throws ScrmException 日历不存在
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/calendars/{id}/enable")
    public OperationResponse<ScrmInteractionCalendarEntity> enableCalendar(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(interactionCalendarService.enableCalendar(id));
    }

    /**
     * 停用日历。
     *
     * @param id 日历 ID
     * @return 更新后的日历
     * @throws ScrmException 日历不存在
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/calendars/{id}/disable")
    public OperationResponse<ScrmInteractionCalendarEntity> disableCalendar(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(interactionCalendarService.disableCalendar(id));
    }

    /**
     * 共享日历给指定用户。
     *
     * @param id     日历 ID
     * @param userId 用户 ID
     * @return 更新后的日历
     * @throws ScrmException 日历不存在 / 用户 ID 非法
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/calendars/share")
    public OperationResponse<ScrmInteractionCalendarEntity> shareCalendar(
            @RequestParam Long id,
            @RequestParam String userId) throws ScrmException {
        return OperationResponse.build(interactionCalendarService.shareCalendar(id, userId));
    }

    /**
     * 查询共享给指定用户的日历。
     *
     * @param userId 用户 ID
     * @return 日历列表
     * @throws ScrmException 用户 ID 非法
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "read")
    @GetMapping("/calendars/shared/{userId}")
    public OperationResponse<List<ScrmInteractionCalendarEntity>> getSharedCalendars(@PathVariable String userId)
            throws ScrmException {
        return OperationResponse.build(interactionCalendarService.getSharedCalendars(userId));
    }

    /**
     * 日历视图: 按日期范围聚合计划。
     *
     * @param viewDto 视图查询参数
     * @return 日历视图
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "read")
    @PostMapping("/calendars/view")
    public OperationResponse<Map<String, Object>> getCalendarView(@Valid @RequestBody ScrmCalendarViewDto viewDto)
            throws ScrmException {
        return OperationResponse.build(interactionCalendarService.getCalendarView(viewDto));
    }

    /**
     * 月视图: 返回指定年月每天的互动计划。
     *
     * @param year    年
     * @param month   月 (1-12)
     * @param ownerId 负责人 ID (可选)
     * @return 月视图
     * @throws ScrmException 月份非法
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "read")
    @GetMapping("/calendars/month")
    public OperationResponse<Map<String, Object>> getMonthView(
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(required = false) String ownerId) throws ScrmException {
        return OperationResponse.build(interactionCalendarService.getMonthView(year, month, ownerId));
    }

    /**
     * 周视图: 返回指定周开始的 7 天互动计划。
     *
     * @param weekStart 周开始日期 (周一)
     * @param ownerId   负责人 ID (可选)
     * @return 周视图
     * @throws ScrmException 周开始日期为空
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "read")
    @GetMapping("/calendars/week")
    public OperationResponse<Map<String, Object>> getWeekView(
            @RequestParam LocalDate weekStart,
            @RequestParam(required = false) String ownerId) throws ScrmException {
        return OperationResponse.build(interactionCalendarService.getWeekView(weekStart, ownerId));
    }

    /**
     * 日视图: 返回指定日期的互动计划。
     *
     * @param date    日期
     * @param ownerId 负责人 ID (可选)
     * @return 日视图
     * @throws ScrmException 日期为空
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "read")
    @GetMapping("/calendars/day")
    public OperationResponse<Map<String, Object>> getDayView(
            @RequestParam LocalDate date,
            @RequestParam(required = false) String ownerId) throws ScrmException {
        return OperationResponse.build(interactionCalendarService.getDayView(date, ownerId));
    }

    /**
     * 议程视图: 返回未来 N 天的互动计划列表。
     *
     * @param ownerId 负责人 ID (可选)
     * @param days    天数 (默认 7)
     * @return 议程视图
     * @throws ScrmException 天数非法
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "read")
    @GetMapping("/calendars/agenda")
    public OperationResponse<Map<String, Object>> getAgendaView(
            @RequestParam(required = false) String ownerId,
            @RequestParam(defaultValue = "7") int days) throws ScrmException {
        return OperationResponse.build(interactionCalendarService.getAgendaView(ownerId, days));
    }

    /**
     * 更新日历统计指标。
     *
     * @param id 日历 ID
     * @return 更新后的日历
     * @throws ScrmException 日历不存在
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/calendars/{id}/stats")
    public OperationResponse<ScrmInteractionCalendarEntity> updateCalendarStats(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(interactionCalendarService.updateCalendarStats(id));
    }

    /**
     * 日历摘要: 指定时间范围内的计划统计。
     *
     * @param id        日历 ID
     * @param startTime 开始时间 (含, 可选)
     * @param endTime   结束时间 (含, 可选)
     * @return 摘要
     * @throws ScrmException 日历不存在
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "read")
    @GetMapping("/calendars/summary")
    public OperationResponse<Map<String, Object>> getCalendarSummary(
            @RequestParam Long id,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) throws ScrmException {
        return OperationResponse.build(
                interactionCalendarService.getCalendarSummary(id, startTime, endTime));
    }

    /**
     * 查找空闲时间段。
     *
     * @param ownerId         负责人 ID
     * @param date            日期
     * @param durationMinutes 所需时长分钟
     * @return 空闲时段列表
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "read")
    @PostMapping("/calendars/free-slots")
    public OperationResponse<List<Map<String, Object>>> findFreeSlots(
            @RequestParam String ownerId,
            @RequestParam LocalDate date,
            @RequestParam int durationMinutes) throws ScrmException {
        return OperationResponse.build(
                interactionCalendarService.findFreeSlots(ownerId, date, durationMinutes));
    }

    /**
     * 检测时间冲突。
     *
     * @param ownerId 负责人 ID
     * @param start   开始时间
     * @param end     结束时间
     * @return 冲突计划列表
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "read")
    @PostMapping("/calendars/conflicts")
    public OperationResponse<List<ScrmInteractionPlanEntity>> detectConflicts(
            @RequestParam String ownerId,
            @RequestParam LocalDateTime start,
            @RequestParam LocalDateTime end) throws ScrmException {
        return OperationResponse.build(interactionCalendarService.detectConflicts(ownerId, start, end));
    }

    // ============================================================
    // 统计 /stats
    // ============================================================

    /**
     * 互动统计概览: 各类型/各方式/完成率/平均时长。
     *
     * @param startTime 开始时间 (含, 可选)
     * @param endTime   结束时间 (含, 可选)
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getInteractionStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(interactionCalendarService.getInteractionStats(startTime, endTime));
    }

    /**
     * 客户互动统计: 各状态计数与互动类型分布。
     *
     * @param customerId 客户 ID
     * @return 统计结果
     * @throws ScrmException 客户 ID 为空
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "read")
    @GetMapping("/stats/customer/{customerId}")
    public OperationResponse<Map<String, Object>> getCustomerInteractionStats(@PathVariable Long customerId)
            throws ScrmException {
        return OperationResponse.build(interactionCalendarService.getCustomerInteractionStats(customerId));
    }

    /**
     * 负责人统计: 计划数 / 各状态 / 完成率。
     *
     * @param ownerId   负责人 ID
     * @param startTime 开始时间 (含, 可选)
     * @param endTime   结束时间 (含, 可选)
     * @return 统计结果
     * @throws ScrmException 负责人 ID 为空
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "read")
    @GetMapping("/stats/owner")
    public OperationResponse<Map<String, Object>> getOwnerStats(
            @RequestParam String ownerId,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) throws ScrmException {
        return OperationResponse.build(
                interactionCalendarService.getOwnerStats(ownerId, startTime, endTime));
    }

    /**
     * 互动趋势 (按月聚合最近 N 个月)。
     *
     * @param months 月数 (默认 6)
     * @return 趋势列表
     * @throws ScrmException 月数非法
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "read")
    @GetMapping("/stats/trend")
    public OperationResponse<List<Map<String, Object>>> getInteractionTrend(
            @RequestParam(defaultValue = "6") int months) throws ScrmException {
        return OperationResponse.build(interactionCalendarService.getInteractionTrend(months));
    }

    /**
     * 完成率。
     *
     * @param ownerId   负责人 ID
     * @param startTime 开始时间 (含, 可选)
     * @param endTime   结束时间 (含, 可选)
     * @return 完成率
     * @throws ScrmException 负责人 ID 为空
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "read")
    @GetMapping("/stats/completion-rate")
    public OperationResponse<Double> getCompletionRate(
            @RequestParam String ownerId,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) throws ScrmException {
        return OperationResponse.build(
                interactionCalendarService.getCompletionRate(ownerId, startTime, endTime));
    }

    /**
     * 未到率。
     *
     * @param ownerId   负责人 ID
     * @param startTime 开始时间 (含, 可选)
     * @param endTime   结束时间 (含, 可选)
     * @return 未到率
     * @throws ScrmException 负责人 ID 为空
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "read")
    @GetMapping("/stats/no-show-rate")
    public OperationResponse<Double> getNoShowRate(
            @RequestParam String ownerId,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) throws ScrmException {
        return OperationResponse.build(
                interactionCalendarService.getNoShowRate(ownerId, startTime, endTime));
    }

    /**
     * 最佳互动时间段 (按小时聚合)。
     *
     * @param ownerId 负责人 ID (可选)
     * @return 时段列表
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "read")
    @GetMapping("/stats/best-slots")
    public OperationResponse<List<Map<String, Object>>> getBestTimeSlots(
            @RequestParam(required = false) String ownerId) {
        return OperationResponse.build(interactionCalendarService.getBestTimeSlots(ownerId));
    }

    /**
     * 互动热力图 (按日聚合最近 N 个月)。
     *
     * @param ownerId 负责人 ID (可选)
     * @param months  月数 (默认 3)
     * @return 热力图列表
     * @throws ScrmException 月数非法
     */
    @RequirePermission(resource = "scrm_interaction_calendar", action = "read")
    @GetMapping("/stats/heatmap")
    public OperationResponse<List<Map<String, Object>>> getInteractionHeatmap(
            @RequestParam(required = false) String ownerId,
            @RequestParam(defaultValue = "3") int months) throws ScrmException {
        return OperationResponse.build(interactionCalendarService.getInteractionHeatmap(ownerId, months));
    }
}
