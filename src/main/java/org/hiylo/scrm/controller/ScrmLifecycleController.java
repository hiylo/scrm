/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLifecycleController.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmLifecycleBulkTransitionDto;
import org.hiylo.scrm.dto.ScrmLifecycleTransitionActionDto;
import org.hiylo.scrm.dto.ScrmLifecycleTransitionDto;
import org.hiylo.scrm.dto.ScrmLifecycleStageDto;
import org.hiylo.scrm.entity.ScrmCustomerLifecycleEntity;
import org.hiylo.scrm.entity.ScrmLifecycleHistoryEntity;
import org.hiylo.scrm.entity.ScrmLifecycleStageEntity;
import org.hiylo.scrm.entity.ScrmLifecycleTransitionEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmLifecycleService;
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
 * SCRM 客户生命周期阶段管理控制器。
 * <p>
 * 提供客户生命周期管理完整能力: 阶段定义 (CRUD / 启停 / 重排 / 统计刷新 / 阶段漏斗),
 * 阶段流转规则 (CRUD / 启停 / 可用转换查询), 客户当前阶段 (查询 / 按阶段查询 / 超期查询 /
 * 手动分配 / 单/批量转换 / 事件触发转换), 转换历史 (客户历史 / 列表查询 / 阶段转换趋势),
 * 统计分析 (生命周期概览 / 转换统计 / 转化漏斗 / 阶段停留分析 / 流失率)。
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/lifecycle")
@RequiredArgsConstructor
public class ScrmLifecycleController {

    /** 生命周期服务 */
    private final ScrmLifecycleService scrmLifecycleService;

    // ============================================================
    // 阶段管理
    // ============================================================

    /**
     * 创建生命周期阶段。
     *
     * @param dto 阶段参数
     * @return 创建后的阶段
     * @throws ScrmException 参数非法 / 编码重复
     */
    @RequirePermission(resource = "scrm_lifecycle", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/stages")
    public OperationResponse<ScrmLifecycleStageEntity> createStage(@Valid @RequestBody ScrmLifecycleStageDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmLifecycleService.createStage(dto));
    }

    /**
     * 更新生命周期阶段（字段非空才覆盖）。
     *
     * @param id  阶段 ID
     * @param dto 阶段参数
     * @return 更新后的阶段
     * @throws ScrmException 阶段不存在 / 参数非法 / 编码重复
     */
    @RequirePermission(resource = "scrm_lifecycle", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/stages/{id}")
    public OperationResponse<ScrmLifecycleStageEntity> updateStage(@PathVariable Long id,
                                                                    @RequestBody ScrmLifecycleStageDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmLifecycleService.updateStage(id, dto));
    }

    /**
     * 删除生命周期阶段 (阶段下有客户时拒绝, 同时清理关联转换规则)。
     *
     * @param id 阶段 ID
     * @return 空响应
     * @throws ScrmException 阶段不存在 / 阶段下仍有客户
     */
    @RequirePermission(resource = "scrm_lifecycle", action = "delete")
    @DeleteMapping("/stages/{id}")
    public OperationResponse<Void> deleteStage(@PathVariable Long id) throws ScrmException {
        scrmLifecycleService.deleteStage(id);
        return OperationResponse.build();
    }

    /**
     * 查询阶段详情。
     *
     * @param id 阶段 ID
     * @return 阶段详情
     * @throws ScrmException 阶段不存在
     */
    @RequirePermission(resource = "scrm_lifecycle", action = "read")
    @GetMapping("/stages/{id}")
    public OperationResponse<ScrmLifecycleStageEntity> getStage(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmLifecycleService.getStage(id));
    }

    /**
     * 按编码查询阶段。
     *
     * @param code 阶段编码
     * @return 阶段详情
     * @throws ScrmException 阶段不存在
     */
    @RequirePermission(resource = "scrm_lifecycle", action = "read")
    @GetMapping("/stages/code/{code}")
    public OperationResponse<ScrmLifecycleStageEntity> getStageByCode(@PathVariable String code)
            throws ScrmException {
        return OperationResponse.build(scrmLifecycleService.getStageByCode(code));
    }

    /**
     * 分页查询阶段列表。
     *
     * @param stageCategory 阶段类别过滤（可空）: ACQUISITION/ENGAGEMENT/ACTIVATION/RETENTION/ADVOCACY/CHURN/REACTIVATION
     * @param enabled       启用状态过滤（可空）
     * @param page          页码（从 0 开始, 默认 0）
     * @param size          每页大小（默认 20）
     * @return 阶段分页结果 (按 stageOrder ASC)
     */
    @RequirePermission(resource = "scrm_lifecycle", action = "read")
    @GetMapping("/stages")
    public OperationResponse<Page<ScrmLifecycleStageEntity>> listStages(
            @RequestParam(required = false) String stageCategory,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "stageOrder"));
        return OperationResponse.build(scrmLifecycleService.listStages(stageCategory, enabled, pageable));
    }

    /**
     * 启用阶段。
     *
     * @param id 阶段 ID
     * @return 更新后的阶段
     * @throws ScrmException 阶段不存在
     */
    @RequirePermission(resource = "scrm_lifecycle", action = "update")
    @PostMapping("/stages/{id}/enable")
    public OperationResponse<ScrmLifecycleStageEntity> enableStage(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmLifecycleService.enableStage(id));
    }

    /**
     * 禁用阶段。
     *
     * @param id 阶段 ID
     * @return 更新后的阶段
     * @throws ScrmException 阶段不存在
     */
    @RequirePermission(resource = "scrm_lifecycle", action = "update")
    @PostMapping("/stages/{id}/disable")
    public OperationResponse<ScrmLifecycleStageEntity> disableStage(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmLifecycleService.disableStage(id));
    }

    /**
     * 重排阶段顺序。
     *
     * @param stageOrders 阶段 ID → 新顺序映射
     * @return 更新后的阶段列表 (按 stageOrder ASC)
     * @throws ScrmException 阶段不存在 / 顺序映射为空
     */
    @RequirePermission(resource = "scrm_lifecycle", action = "update")
    @PutMapping("/stages/reorder")
    public OperationResponse<List<ScrmLifecycleStageEntity>> reorderStages(@RequestBody Map<Long, Integer> stageOrders)
            throws ScrmException {
        return OperationResponse.build(scrmLifecycleService.reorderStages(stageOrders));
    }

    /**
     * 刷新阶段统计 (客户数 / 平均停留 / 转化率)。
     *
     * @param id 阶段 ID
     * @return 更新后的阶段
     * @throws ScrmException 阶段不存在
     */
    @RequirePermission(resource = "scrm_lifecycle", action = "update")
    @PostMapping("/stages/{id}/stats")
    public OperationResponse<ScrmLifecycleStageEntity> updateStageStats(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmLifecycleService.updateStageStats(id));
    }

    /**
     * 阶段漏斗: 各阶段客户数与转化率 (按 stageOrder ASC)。
     *
     * @return 漏斗数据列表
     */
    @RequirePermission(resource = "scrm_lifecycle", action = "read")
    @GetMapping("/stages/funnel")
    public OperationResponse<List<Map<String, Object>>> getStageFunnel() {
        return OperationResponse.build(scrmLifecycleService.getStageFunnel());
    }

    // ============================================================
    // 流转规则管理
    // ============================================================

    /**
     * 创建流转规则。
     *
     * @param dto 流转规则参数
     * @return 创建后的流转规则
     * @throws ScrmException 参数非法 / 目标阶段不存在 / 阶段编码与 ID 不匹配
     */
    @RequirePermission(resource = "scrm_lifecycle", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/transitions")
    public OperationResponse<ScrmLifecycleTransitionEntity> createTransition(
            @Valid @RequestBody ScrmLifecycleTransitionDto dto) throws ScrmException {
        return OperationResponse.build(scrmLifecycleService.createTransition(dto));
    }

    /**
     * 更新流转规则（字段非空才覆盖）。
     *
     * @param id  规则 ID
     * @param dto 流转规则参数
     * @return 更新后的流转规则
     * @throws ScrmException 规则不存在 / 参数非法 / 阶段编码与 ID 不匹配
     */
    @RequirePermission(resource = "scrm_lifecycle", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/transitions/{id}")
    public OperationResponse<ScrmLifecycleTransitionEntity> updateTransition(@PathVariable Long id,
                                                                              @RequestBody ScrmLifecycleTransitionDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmLifecycleService.updateTransition(id, dto));
    }

    /**
     * 删除流转规则。
     *
     * @param id 规则 ID
     * @return 空响应
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_lifecycle", action = "delete")
    @DeleteMapping("/transitions/{id}")
    public OperationResponse<Void> deleteTransition(@PathVariable Long id) throws ScrmException {
        scrmLifecycleService.deleteTransition(id);
        return OperationResponse.build();
    }

    /**
     * 查询流转规则详情。
     *
     * @param id 规则 ID
     * @return 流转规则详情
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_lifecycle", action = "read")
    @GetMapping("/transitions/{id}")
    public OperationResponse<ScrmLifecycleTransitionEntity> getTransition(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmLifecycleService.getTransition(id));
    }

    /**
     * 分页查询流转规则列表。
     *
     * @param fromStageId    源阶段 ID 过滤（可空）
     * @param toStageId      目标阶段 ID 过滤（可空）
     * @param transitionType 转换类型过滤（可空）: AUTO/MANUAL/SYSTEM
     * @param enabled        启用状态过滤（可空）
     * @param page          页码（从 0 开始, 默认 0）
     * @param size          每页大小（默认 20）
     * @return 流转规则分页结果 (按 priority DESC, createTime DESC)
     */
    @RequirePermission(resource = "scrm_lifecycle", action = "read")
    @GetMapping("/transitions")
    public OperationResponse<Page<ScrmLifecycleTransitionEntity>> listTransitions(
            @RequestParam(required = false) Long fromStageId,
            @RequestParam(required = false) Long toStageId,
            @RequestParam(required = false) String transitionType,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "priority", "createTime"));
        return OperationResponse.build(scrmLifecycleService.listTransitions(
                fromStageId, toStageId, transitionType, enabled, pageable));
    }

    /**
     * 启用流转规则。
     *
     * @param id 规则 ID
     * @return 更新后的流转规则
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_lifecycle", action = "update")
    @PostMapping("/transitions/{id}/enable")
    public OperationResponse<ScrmLifecycleTransitionEntity> enableTransition(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmLifecycleService.enableTransition(id));
    }

    /**
     * 禁用流转规则。
     *
     * @param id 规则 ID
     * @return 更新后的流转规则
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_lifecycle", action = "update")
    @PostMapping("/transitions/{id}/disable")
    public OperationResponse<ScrmLifecycleTransitionEntity> disableTransition(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmLifecycleService.disableTransition(id));
    }

    /**
     * 获取指定阶段的可用转换规则 (按优先级降序)。
     *
     * @param stageId 阶段 ID
     * @return 可用转换规则列表
     * @throws ScrmException 阶段不存在
     */
    @RequirePermission(resource = "scrm_lifecycle", action = "read")
    @GetMapping("/transitions/available/{stageId}")
    public OperationResponse<List<ScrmLifecycleTransitionEntity>> getAvailableTransitions(
            @PathVariable Long stageId) throws ScrmException {
        return OperationResponse.build(scrmLifecycleService.getAvailableTransitions(stageId));
    }

    // ============================================================
    // 客户生命周期管理
    // ============================================================

    /**
     * 获取客户当前生命周期。
     *
     * @param customerId 客户 ID
     * @return 客户生命周期详情
     * @throws ScrmException 客户生命周期不存在
     */
    @RequirePermission(resource = "scrm_lifecycle", action = "read")
    @GetMapping("/customers/{customerId}")
    public OperationResponse<ScrmCustomerLifecycleEntity> getCustomerLifecycle(@PathVariable Long customerId)
            throws ScrmException {
        return OperationResponse.build(scrmLifecycleService.getCustomerLifecycle(customerId));
    }

    /**
     * 按阶段分页查询客户。
     *
     * @param stageId 阶段 ID
     * @param page    页码（从 0 开始, 默认 0）
     * @param size    每页大小（默认 20）
     * @return 客户生命周期分页结果 (按 enteredCurrentStageAt DESC)
     * @throws ScrmException 阶段不存在
     */
    @RequirePermission(resource = "scrm_lifecycle", action = "read")
    @GetMapping("/customers/by-stage/{stageId}")
    public OperationResponse<Page<ScrmCustomerLifecycleEntity>> listCustomersByStage(
            @PathVariable Long stageId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) throws ScrmException {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "enteredCurrentStageAt"));
        return OperationResponse.build(scrmLifecycleService.listCustomersByStage(stageId, pageable));
    }

    /**
     * 分页查询超期客户。
     *
     * @param stageId 阶段 ID 过滤（可空, null 表示全部阶段）
     * @param page    页码（从 0 开始, 默认 0）
     * @param size    每页大小（默认 20）
     * @return 超期客户分页结果 (按 overdueDays DESC)
     */
    @RequirePermission(resource = "scrm_lifecycle", action = "read")
    @GetMapping("/customers/overdue")
    public OperationResponse<Page<ScrmCustomerLifecycleEntity>> listOverdueCustomers(
            @RequestParam(required = false) Long stageId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "overdueDays"));
        return OperationResponse.build(scrmLifecycleService.listOverdueCustomers(stageId, pageable));
    }

    /**
     * 手动分配客户到指定阶段 (新客户初始化或强制重置)。
     *
     * @param customerId 客户 ID
     * @param stageCode  目标阶段编码
     * @return 客户生命周期实体
     * @throws ScrmException 阶段不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_lifecycle", action = "update")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/customers/assign")
    public OperationResponse<ScrmCustomerLifecycleEntity> assignCustomer(
            @RequestParam Long customerId,
            @RequestParam String stageCode) throws ScrmException {
        return OperationResponse.build(scrmLifecycleService.assignCustomer(customerId, stageCode));
    }

    /**
     * 客户阶段转换 (验证转换规则 → 更新当前阶段 → 记录历史 → 更新统计)。
     *
     * @param actionDto 转换动作参数
     * @return 更新后的客户生命周期
     * @throws ScrmException 阶段不存在 / 无可用转换规则 / 冷却期内
     */
    @RequirePermission(resource = "scrm_lifecycle", action = "update")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/customers/transition")
    public OperationResponse<ScrmCustomerLifecycleEntity> transitionCustomer(
            @Valid @RequestBody ScrmLifecycleTransitionActionDto actionDto) throws ScrmException {
        return OperationResponse.build(scrmLifecycleService.transitionCustomer(actionDto));
    }

    /**
     * 批量转换客户阶段。
     *
     * @param bulkDto 批量转换参数
     * @return 转换结果列表 (失败客户被跳过并记录日志)
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_lifecycle", action = "update")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/customers/bulk-transition")
    public OperationResponse<List<ScrmCustomerLifecycleEntity>> bulkTransition(
            @Valid @RequestBody ScrmLifecycleBulkTransitionDto bulkDto) throws ScrmException {
        return OperationResponse.build(scrmLifecycleService.bulkTransition(bulkDto));
    }

    /**
     * 检查事件触发自动转换 (按 triggerEvents 字段匹配)。
     *
     * @param customerId 客户 ID
     * @param event      触发事件: PURCHASE/LOGIN/INACTIVE_DAYS/FIRST_CONTACT/REFUND/CUSTOM
     * @return 转换后的客户生命周期 (无匹配规则时返回当前生命周期)
     * @throws ScrmException 客户生命周期不存在 / 触发事件为空
     */
    @RequirePermission(resource = "scrm_lifecycle", action = "update")
    @PostMapping("/customers/{customerId}/check-transition")
    public OperationResponse<ScrmCustomerLifecycleEntity> checkAndTransition(
            @PathVariable Long customerId,
            @RequestParam String event) throws ScrmException {
        return OperationResponse.build(scrmLifecycleService.checkAndTransition(customerId, event));
    }

    /**
     * 处理事件 (检查触发条件 → 执行转换)。
     *
     * @param customerId 客户 ID
     * @param eventType  事件类型
     * @param eventData  事件数据 JSON (可空)
     * @return 转换后的客户生命周期
     * @throws ScrmException 客户生命周期不存在
     */
    @RequirePermission(resource = "scrm_lifecycle", action = "update")
    @PostMapping("/customers/{customerId}/process-event")
    public OperationResponse<ScrmCustomerLifecycleEntity> processEvent(
            @PathVariable Long customerId,
            @RequestParam String eventType,
            @RequestParam(required = false) String eventData) throws ScrmException {
        return OperationResponse.build(scrmLifecycleService.processEvent(customerId, eventType, eventData));
    }

    // ============================================================
    // 转换历史
    // ============================================================

    /**
     * 查询客户阶段转换历史 (按 transitionTime 降序)。
     *
     * @param customerId 客户 ID
     * @return 历史列表
     * @throws ScrmException 客户 ID 非法
     */
    @RequirePermission(resource = "scrm_lifecycle", action = "read")
    @GetMapping("/history/customers/{customerId}")
    public OperationResponse<List<ScrmLifecycleHistoryEntity>> getCustomerHistory(@PathVariable Long customerId)
            throws ScrmException {
        return OperationResponse.build(scrmLifecycleService.getCustomerHistory(customerId));
    }

    /**
     * 分页查询转换历史。
     *
     * @param customerId     客户 ID 过滤（可空）
     * @param fromStage      源阶段编码过滤（可空）
     * @param toStage        目标阶段编码过滤（可空）
     * @param transitionType 转换类型过滤（可空）: AUTO/MANUAL/SYSTEM
     * @param startTime      转换时间起始（可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss）
     * @param endTime        转换时间截止（可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss）
     * @param page           页码（从 0 开始, 默认 0）
     * @param size           每页大小（默认 20）
     * @return 历史分页结果 (按 transitionTime DESC)
     */
    @RequirePermission(resource = "scrm_lifecycle", action = "read")
    @GetMapping("/history")
    public OperationResponse<Page<ScrmLifecycleHistoryEntity>> listHistory(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String fromStage,
            @RequestParam(required = false) String toStage,
            @RequestParam(required = false) String transitionType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "transitionTime"));
        return OperationResponse.build(scrmLifecycleService.listHistory(
                customerId, fromStage, toStage, transitionType, startTime, endTime, pageable));
    }

    /**
     * 阶段转换趋势 (指定阶段每日进入客户数)。
     *
     * @param stageId 阶段 ID
     * @param days    统计天数 (从今天往前推, 默认 30)
     * @return 趋势数据列表 (按日期升序)
     * @throws ScrmException 阶段不存在
     */
    @RequirePermission(resource = "scrm_lifecycle", action = "read")
    @GetMapping("/history/trend/{stageId}")
    public OperationResponse<List<Map<String, Object>>> getStageTransitionTrend(
            @PathVariable Long stageId,
            @RequestParam(defaultValue = "30") int days) throws ScrmException {
        return OperationResponse.build(scrmLifecycleService.getStageTransitionTrend(stageId, days));
    }

    // ============================================================
    // 统计分析
    // ============================================================

    /**
     * 生命周期统计: 各阶段客户数 / 分布 / 平均停留。
     *
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_lifecycle", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getLifecycleStats() {
        return OperationResponse.build(scrmLifecycleService.getLifecycleStats());
    }

    /**
     * 转换统计: 各转换触发次数与成功率。
     *
     * @param startTime 转换时间起始（可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss）
     * @param endTime   转换时间截止（可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss）
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_lifecycle", action = "read")
    @GetMapping("/stats/transitions")
    public OperationResponse<Map<String, Object>> getTransitionStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmLifecycleService.getTransitionStats(startTime, endTime));
    }

    /**
     * 转化漏斗: 各阶段 → 下一阶段转化率。
     *
     * @return 漏斗数据列表
     */
    @RequirePermission(resource = "scrm_lifecycle", action = "read")
    @GetMapping("/stats/funnel")
    public OperationResponse<List<Map<String, Object>>> getConversionFunnel() {
        return OperationResponse.build(scrmLifecycleService.getConversionFunnel());
    }

    /**
     * 阶段停留分析: 各阶段平均停留天数与目标停留天数。
     *
     * @return 停留分析列表
     */
    @RequirePermission(resource = "scrm_lifecycle", action = "read")
    @GetMapping("/stats/duration")
    public OperationResponse<List<Map<String, Object>>> getStageDurationAnalysis() {
        return OperationResponse.build(scrmLifecycleService.getStageDurationAnalysis());
    }

    /**
     * 流失率: 指定阶段在时间区间内的流失率。
     *
     * @param stageId   阶段 ID
     * @param startTime 起始时间（可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss）
     * @param endTime   截止时间（可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss）
     * @return 流失率统计 Map
     * @throws ScrmException 阶段不存在
     */
    @RequirePermission(resource = "scrm_lifecycle", action = "read")
    @GetMapping("/stats/churn/{stageId}")
    public OperationResponse<Map<String, Object>> getChurnRate(
            @PathVariable Long stageId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime)
            throws ScrmException {
        return OperationResponse.build(scrmLifecycleService.getChurnRate(stageId, startTime, endTime));
    }
}
