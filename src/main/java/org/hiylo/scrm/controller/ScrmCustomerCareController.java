/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerCareController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmCareExecuteDto;
import org.hiylo.scrm.dto.ScrmCareRecordDto;
import org.hiylo.scrm.dto.ScrmCareRuleDto;
import org.hiylo.scrm.dto.ScrmCareTaskDto;
import org.hiylo.scrm.dto.ScrmFestivalDto;
import org.hiylo.scrm.entity.ScrmCareRecordEntity;
import org.hiylo.scrm.entity.ScrmCareRuleEntity;
import org.hiylo.scrm.entity.ScrmCareTaskEntity;
import org.hiylo.scrm.entity.ScrmFestivalEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmCustomerCareService;
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
 * SCRM 客户关怀控制器。
 * <p>
 * 提供关怀规则管理、关怀任务管理与执行、关怀任务调度生成、节日配置管理、关怀记录管理与
 * 关怀效果统计接口。权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/customer-care")
@RequiredArgsConstructor
public class ScrmCustomerCareController {

    /** 客户关怀服务 */
    private final ScrmCustomerCareService scrmCustomerCareService;

    // ============================================================
    // 规则管理 /rules
    // ============================================================

    /**
     * 创建关怀规则。
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_customer_care", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/rules")
    public OperationResponse<ScrmCareRuleEntity> createRule(@Valid @RequestBody ScrmCareRuleDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerCareService.createRule(dto));
    }

    /**
     * 更新关怀规则。
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_customer_care", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/rules/{id}")
    public OperationResponse<ScrmCareRuleEntity> updateRule(@PathVariable Long id,
                                                              @RequestBody ScrmCareRuleDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerCareService.updateRule(id, dto));
    }

    /**
     * 删除关怀规则。
     *
     * @param id 规则 ID
     * @return 空响应
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_customer_care", action = "delete")
    @DeleteMapping("/rules/{id}")
    public OperationResponse<Void> deleteRule(@PathVariable Long id) throws ScrmException {
        scrmCustomerCareService.deleteRule(id);
        return OperationResponse.build();
    }

    /**
     * 查询规则详情。
     *
     * @param id 规则 ID
     * @return 规则详情
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_customer_care", action = "read")
    @GetMapping("/rules/{id}")
    public OperationResponse<ScrmCareRuleEntity> getRule(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCustomerCareService.getRule(id));
    }

    /**
     * 分页查询规则列表。
     *
     * @param careType 关怀类型过滤（可空）: BIRTHDAY/FESTIVAL/ANNIVERSARY/MEMBERSHIP_EXPIRY/INACTIVITY_REMINDER/CUSTOM
     * @param enabled  启用状态过滤（可空）
     * @param keyword  规则名称关键字模糊匹配（可空）
     * @param page     页码（从 0 开始, 默认 0）
     * @param size     每页大小（默认 20）
     * @return 规则分页结果 (按 priority ASC, createTime DESC)
     */
    @RequirePermission(resource = "scrm_customer_care", action = "read")
    @GetMapping("/rules/list")
    public OperationResponse<Page<ScrmCareRuleEntity>> listRules(
            @RequestParam(required = false) String careType,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "priority")
                        .and(Sort.by(Sort.Direction.DESC, "createTime")));
        return OperationResponse.build(scrmCustomerCareService.listRules(careType, enabled, keyword, pageable));
    }

    /**
     * 启用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_customer_care", action = "update")
    @PostMapping("/rules/{id}/enable")
    public OperationResponse<ScrmCareRuleEntity> enableRule(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCustomerCareService.enableRule(id));
    }

    /**
     * 禁用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_customer_care", action = "update")
    @PostMapping("/rules/{id}/disable")
    public OperationResponse<ScrmCareRuleEntity> disableRule(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCustomerCareService.disableRule(id));
    }

    // ============================================================
    // 任务管理 /tasks
    // ============================================================

    /**
     * 创建关怀任务 (手动创建, ruleId 可空)。
     *
     * @param dto 任务参数
     * @return 创建后的任务
     * @throws ScrmException 参数非法 / 客户不存在
     */
    @RequirePermission(resource = "scrm_customer_care", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/tasks")
    public OperationResponse<ScrmCareTaskEntity> createTask(@Valid @RequestBody ScrmCareTaskDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerCareService.createTask(dto));
    }

    /**
     * 更新关怀任务。
     *
     * @param id  任务 ID
     * @param dto 任务参数
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_customer_care", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/tasks/{id}")
    public OperationResponse<ScrmCareTaskEntity> updateTask(@PathVariable Long id,
                                                              @RequestBody ScrmCareTaskDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerCareService.updateTask(id, dto));
    }

    /**
     * 删除关怀任务。
     *
     * @param id 任务 ID
     * @return 空响应
     * @throws ScrmException 任务不存在
     */
    @RequirePermission(resource = "scrm_customer_care", action = "delete")
    @DeleteMapping("/tasks/{id}")
    public OperationResponse<Void> deleteTask(@PathVariable Long id) throws ScrmException {
        scrmCustomerCareService.deleteTask(id);
        return OperationResponse.build();
    }

    /**
     * 查询任务详情。
     *
     * @param id 任务 ID
     * @return 任务详情
     * @throws ScrmException 任务不存在
     */
    @RequirePermission(resource = "scrm_customer_care", action = "read")
    @GetMapping("/tasks/{id}")
    public OperationResponse<ScrmCareTaskEntity> getTask(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCustomerCareService.getTask(id));
    }

    /**
     * 分页查询任务列表。
     *
     * @param careType   关怀类型过滤（可空）
     * @param status     任务状态过滤（可空）: PENDING/EXECUTING/SUCCESS/FAILED/CANCELLED
     * @param assigneeId 负责人 ID 过滤（可空）
     * @param customerId 客户 ID 过滤（可空）
     * @param startDate  关怀日期起始 (可空, ISO 格式: yyyy-MM-dd)
     * @param endDate    关怀日期截止 (可空, ISO 格式: yyyy-MM-dd)
     * @param page       页码（从 0 开始, 默认 0）
     * @param size       每页大小（默认 20）
     * @return 任务分页结果 (按 scheduledAt DESC)
     */
    @RequirePermission(resource = "scrm_customer_care", action = "read")
    @GetMapping("/tasks/list")
    public OperationResponse<Page<ScrmCareTaskEntity>> listTasks(
            @RequestParam(required = false) String careType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String assigneeId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "scheduledAt"));
        return OperationResponse.build(scrmCustomerCareService.listTasks(
                careType, status, assigneeId, customerId, startDate, endDate, pageable));
    }

    /**
     * 执行关怀任务 (模拟实现)。
     *
     * @param executeDto 执行请求 (taskId + result + response)
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法 / 参数非法
     */
    @RequirePermission(resource = "scrm_customer_care", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/tasks/execute")
    public OperationResponse<ScrmCareTaskEntity> executeTask(@Valid @RequestBody ScrmCareExecuteDto executeDto)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerCareService.executeTask(executeDto));
    }

    /**
     * 取消关怀任务。
     *
     * @param id   任务 ID
     * @param body 请求体, 可包含 reason 字段 (取消原因)
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_customer_care", action = "update")
    @PostMapping("/tasks/{id}/cancel")
    public OperationResponse<ScrmCareTaskEntity> cancelTask(
            @PathVariable Long id,
            @RequestParam(required = false) Map<String, String> body) throws ScrmException {
        String reason = body != null ? body.get("reason") : null;
        return OperationResponse.build(scrmCustomerCareService.cancelTask(id, reason));
    }

    /**
     * 批量执行关怀任务。
     *
     * @param taskIds 任务 ID 列表
     * @return 执行结果: {total, success, failed}
     */
    @RequirePermission(resource = "scrm_customer_care", action = "update")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/tasks/batch-execute")
    public OperationResponse<Map<String, Integer>> batchExecuteTasks(@RequestBody List<Long> taskIds) {
        return OperationResponse.build(scrmCustomerCareService.batchExecuteTasks(taskIds));
    }

    // ============================================================
    // 调度生成 /schedule
    // ============================================================

    /**
     * 根据规则生成某日关怀任务。
     *
     * @param ruleId 规则 ID
     * @param date   关怀日期 (可空, 默认今天, ISO 格式: yyyy-MM-dd)
     * @return 创建的任务数
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_customer_care", action = "update")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/schedule/from-rule")
    public OperationResponse<Integer> generateTasksFromDate(
            @RequestParam Long ruleId,
            @RequestParam(required = false) LocalDate date) throws ScrmException {
        return OperationResponse.build(scrmCustomerCareService.generateTasksFromDate(ruleId, date));
    }

    /**
     * 生成某日所有关怀任务 (模拟实现)。
     *
     * @param date 关怀日期 (可空, 默认今天, ISO 格式: yyyy-MM-dd)
     * @return 生成结果: {totalCreated, rulesProcessed, failed}
     */
    @RequirePermission(resource = "scrm_customer_care", action = "update")
    @RateLimit(capacity = 5, refillTokens = 5, refillPeriodSeconds = 60)
    @PostMapping("/schedule/daily")
    public OperationResponse<Map<String, Integer>> generateDailyTasks(
            @RequestParam(required = false) LocalDate date) {
        return OperationResponse.build(scrmCustomerCareService.generateDailyTasks(date));
    }

    /**
     * 生成生日关怀任务。
     *
     * @param date 关怀日期 (可空, 默认今天, ISO 格式: yyyy-MM-dd)
     * @return 创建的任务数
     */
    @RequirePermission(resource = "scrm_customer_care", action = "update")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/schedule/birthday")
    public OperationResponse<Integer> generateBirthdayTasks(
            @RequestParam(required = false) LocalDate date) {
        return OperationResponse.build(scrmCustomerCareService.generateBirthdayTasks(date));
    }

    /**
     * 生成节日关怀任务。
     *
     * @param festivalId 节日 ID
     * @param date       关怀日期 (可空, 默认今天, ISO 格式: yyyy-MM-dd)
     * @return 创建的任务数
     * @throws ScrmException 节日不存在
     */
    @RequirePermission(resource = "scrm_customer_care", action = "update")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/schedule/festival")
    public OperationResponse<Integer> generateFestivalTasks(
            @RequestParam Long festivalId,
            @RequestParam(required = false) LocalDate date) throws ScrmException {
        return OperationResponse.build(scrmCustomerCareService.generateFestivalTasks(festivalId, date));
    }

    // ============================================================
    // 节日配置 /festivals
    // ============================================================

    /**
     * 创建节日配置。
     *
     * @param dto 节日参数
     * @return 创建后的节日
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_customer_care", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/festivals")
    public OperationResponse<ScrmFestivalEntity> createFestival(@Valid @RequestBody ScrmFestivalDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerCareService.createFestival(dto));
    }

    /**
     * 更新节日配置。
     *
     * @param id  节日 ID
     * @param dto 节日参数
     * @return 更新后的节日
     * @throws ScrmException 节日不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_customer_care", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/festivals/{id}")
    public OperationResponse<ScrmFestivalEntity> updateFestival(@PathVariable Long id,
                                                                  @RequestBody ScrmFestivalDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerCareService.updateFestival(id, dto));
    }

    /**
     * 删除节日配置。
     *
     * @param id 节日 ID
     * @return 空响应
     * @throws ScrmException 节日不存在
     */
    @RequirePermission(resource = "scrm_customer_care", action = "delete")
    @DeleteMapping("/festivals/{id}")
    public OperationResponse<Void> deleteFestival(@PathVariable Long id) throws ScrmException {
        scrmCustomerCareService.deleteFestival(id);
        return OperationResponse.build();
    }

    /**
     * 查询节日详情。
     *
     * @param id 节日 ID
     * @return 节日详情
     * @throws ScrmException 节日不存在
     */
    @RequirePermission(resource = "scrm_customer_care", action = "read")
    @GetMapping("/festivals/{id}")
    public OperationResponse<ScrmFestivalEntity> getFestival(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCustomerCareService.getFestival(id));
    }

    /**
     * 分页查询节日列表。
     *
     * @param festivalType 节日类型过滤（可空）: SOLAR/LUNAR/FIXED/CUSTOM
     * @param enabled      启用状态过滤（可空）
     * @param page         页码（从 0 开始, 默认 0）
     * @param size         每页大小（默认 20）
     * @return 节日分页结果 (按 festivalDate ASC)
     */
    @RequirePermission(resource = "scrm_customer_care", action = "read")
    @GetMapping("/festivals/list")
    public OperationResponse<Page<ScrmFestivalEntity>> listFestivals(
            @RequestParam(required = false) String festivalType,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "festivalDate"));
        return OperationResponse.build(scrmCustomerCareService.listFestivals(festivalType, enabled, pageable));
    }

    /**
     * 启用节日。
     *
     * @param id 节日 ID
     * @return 更新后的节日
     * @throws ScrmException 节日不存在
     */
    @RequirePermission(resource = "scrm_customer_care", action = "update")
    @PostMapping("/festivals/{id}/enable")
    public OperationResponse<ScrmFestivalEntity> enableFestival(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCustomerCareService.enableFestival(id));
    }

    /**
     * 禁用节日。
     *
     * @param id 节日 ID
     * @return 更新后的节日
     * @throws ScrmException 节日不存在
     */
    @RequirePermission(resource = "scrm_customer_care", action = "update")
    @PostMapping("/festivals/{id}/disable")
    public OperationResponse<ScrmFestivalEntity> disableFestival(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCustomerCareService.disableFestival(id));
    }

    /**
     * 查询即将到来的节日。
     *
     * @param days 未来天数 (默认 30)
     * @return 即将到来的节日列表
     */
    @RequirePermission(resource = "scrm_customer_care", action = "read")
    @GetMapping("/festivals/upcoming")
    public OperationResponse<List<ScrmFestivalEntity>> getUpcomingFestivals(
            @RequestParam(defaultValue = "30") int days) {
        return OperationResponse.build(scrmCustomerCareService.getUpcomingFestivals(days));
    }

    // ============================================================
    // 关怀记录 /records
    // ============================================================

    /**
     * 创建关怀记录。
     *
     * @param dto 关怀记录参数
     * @return 创建后的关怀记录
     * @throws ScrmException 参数非法 / 客户不存在
     */
    @RequirePermission(resource = "scrm_customer_care", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/records")
    public OperationResponse<ScrmCareRecordEntity> createRecord(@Valid @RequestBody ScrmCareRecordDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerCareService.createRecord(dto));
    }

    /**
     * 分页查询关怀记录列表。
     *
     * @param customerId 客户 ID 过滤（可空）
     * @param careType   关怀类型过滤（可空）
     * @param careResult 关怀结果过滤（可空）: SUCCESS/NO_RESPONSE/REJECTED/FAILED
     * @param startTime  执行时间起始 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime    执行时间截止 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param page       页码（从 0 开始, 默认 0）
     * @param size       每页大小（默认 20）
     * @return 关怀记录分页结果 (按 executedAt DESC)
     */
    @RequirePermission(resource = "scrm_customer_care", action = "read")
    @PostMapping("/records/list")
    public OperationResponse<Page<ScrmCareRecordEntity>> listRecords(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String careType,
            @RequestParam(required = false) String careResult,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "executedAt"));
        return OperationResponse.build(scrmCustomerCareService.getRecords(
                customerId, careType, careResult, startTime, endTime, pageable));
    }

    /**
     * 查询关怀记录详情。
     *
     * @param id 记录 ID
     * @return 关怀记录详情
     * @throws ScrmException 记录不存在
     */
    @RequirePermission(resource = "scrm_customer_care", action = "read")
    @GetMapping("/records/{id}")
    public OperationResponse<ScrmCareRecordEntity> getRecord(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCustomerCareService.getRecord(id));
    }

    // ============================================================
    // 统计 /stats
    // ============================================================

    /**
     * 关怀统计概览: 关怀数、成功率、客户回应率、各类型分布、各结果分布。
     *
     * @param startTime 执行时间起始 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   执行时间截止 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_customer_care", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getCareStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmCustomerCareService.getCareStats(startTime, endTime));
    }

    /**
     * 客户关怀历史: 按客户查询全部关怀记录。
     *
     * @param customerId 客户 ID
     * @return 关怀记录列表 (按 executedAt DESC)
     */
    @RequirePermission(resource = "scrm_customer_care", action = "read")
    @GetMapping("/stats/customer/{customerId}")
    public OperationResponse<List<ScrmCareRecordEntity>> getCustomerCareHistory(
            @PathVariable Long customerId) {
        return OperationResponse.build(scrmCustomerCareService.getCustomerCareHistory(customerId));
    }

    /**
     * 关怀效果分析: 关怀后互动变化。
     *
     * @param startTime 执行时间起始 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   执行时间截止 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 效果分析结果 Map
     */
    @RequirePermission(resource = "scrm_customer_care", action = "read")
    @GetMapping("/stats/effectiveness")
    public OperationResponse<Map<String, Object>> getCareEffectiveness(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmCustomerCareService.getCareEffectiveness(startTime, endTime));
    }
}
