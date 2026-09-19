/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerLifecycleController.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmCustomerLifecycleDto;
import org.hiylo.scrm.dto.ScrmLifecycleStageDto;
import org.hiylo.scrm.dto.ScrmLifecycleTransitionRequestDto;
import org.hiylo.scrm.entity.ScrmCustomerLifecycleEntity;
import org.hiylo.scrm.entity.ScrmLifecycleHistoryEntity;
import org.hiylo.scrm.entity.ScrmLifecycleStageEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmCustomerLifecycleService;
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
 * SCRM 客户生命周期管理控制器。
 * <p>
 * 提供客户生命周期管理完整能力:
 * <ul>
 *   <li>阶段管理: CRUD / 启停 / 重排 / 统计刷新 / 分类查询 / 阶段树 / 上下阶段 / 阶段流转图</li>
 *   <li>生命周期: CRUD / 多维查询 (阶段 / 价值分层 / 风险等级 / 流失 / 高价值 / 风险) /
 *       检索 / 单/批量流转 / 自动流转 / 唤醒 / 标记流失 / 评分重算 / 时间线 / 旅程 / 最佳行动</li>
 *   <li>转换记录: 查询 / 撤销 / 统计 / 模式分析 / 瓶颈 / 转化漏斗</li>
 *   <li>统计分析: 概览 / 分布 / 流失率 / 留存率 / 平均周期 / 转化率 / 获客渠道 / 趋势</li>
 * </ul>
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/customer-lifecycle")
@RequiredArgsConstructor
public class ScrmCustomerLifecycleController {

    /** 客户生命周期服务 */
    private final ScrmCustomerLifecycleService scrmCustomerLifecycleService;

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
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/stages")
    public OperationResponse<ScrmLifecycleStageEntity> createStage(@Valid @RequestBody ScrmLifecycleStageDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerLifecycleService.createStage(dto));
    }

    /**
     * 更新生命周期阶段 (字段非空才覆盖)。
     *
     * @param id  阶段 ID
     * @param dto 阶段参数
     * @return 更新后的阶段
     * @throws ScrmException 阶段不存在 / 参数非法 / 编码重复
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/stages/{id}")
    public OperationResponse<ScrmLifecycleStageEntity> updateStage(@PathVariable Long id,
                                                                   @RequestBody ScrmLifecycleStageDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerLifecycleService.updateStage(id, dto));
    }

    /**
     * 删除生命周期阶段 (阶段下有客户时拒绝)。
     *
     * @param id 阶段 ID
     * @return 空响应
     * @throws ScrmException 阶段不存在 / 阶段下仍有客户
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "delete")
    @DeleteMapping("/stages/{id}")
    public OperationResponse<Void> deleteStage(@PathVariable Long id) throws ScrmException {
        scrmCustomerLifecycleService.deleteStage(id);
        return OperationResponse.build();
    }

    /**
     * 查询阶段详情。
     *
     * @param id 阶段 ID
     * @return 阶段详情
     * @throws ScrmException 阶段不存在
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "read")
    @GetMapping("/stages/{id}")
    public OperationResponse<ScrmLifecycleStageEntity> getStage(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCustomerLifecycleService.getStage(id));
    }

    /**
     * 按编码查询阶段。
     *
     * @param code 阶段编码
     * @return 阶段详情
     * @throws ScrmException 阶段不存在
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "read")
    @GetMapping("/stages/code/{code}")
    public OperationResponse<ScrmLifecycleStageEntity> getStageByCode(@PathVariable String code)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerLifecycleService.getStageByCode(code));
    }

    /**
     * 分页查询阶段列表。
     *
     * @param stageCategory 阶段类别过滤 (可空)
     * @param enabled       启用状态过滤 (可空)
     * @param page          页码 (从 0 开始, 默认 0)
     * @param size          每页大小 (默认 20)
     * @return 阶段分页结果
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "read")
    @GetMapping("/stages/list")
    public OperationResponse<Page<ScrmLifecycleStageEntity>> listStages(
            @RequestParam(required = false) String stageCategory,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "stageOrder"));
        return OperationResponse.build(scrmCustomerLifecycleService.listStages(stageCategory, enabled, pageable));
    }

    /**
     * 按阶段类别查询全部阶段。
     *
     * @param category 阶段类别
     * @return 阶段列表
     * @throws ScrmException 阶段类别为空
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "read")
    @GetMapping("/stages/by-category/{category}")
    public OperationResponse<List<ScrmLifecycleStageEntity>> getStagesByCategory(@PathVariable String category)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerLifecycleService.getStagesByCategory(category));
    }

    /**
     * 阶段树 (按类别分组)。
     *
     * @return 阶段树
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "read")
    @GetMapping("/stages/tree")
    public OperationResponse<Map<String, Object>> getStageTree() {
        return OperationResponse.build(scrmCustomerLifecycleService.getStageTree());
    }

    /**
     * 查询指定阶段的下一阶段。
     *
     * @param id 阶段 ID
     * @return 下一阶段列表
     * @throws ScrmException 阶段不存在
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "read")
    @GetMapping("/stages/{id}/next")
    public OperationResponse<List<ScrmLifecycleStageEntity>> getNextStages(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerLifecycleService.getNextStages(id));
    }

    /**
     * 查询指定阶段的上一阶段。
     *
     * @param id 阶段 ID
     * @return 上一阶段列表
     * @throws ScrmException 阶段不存在
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "read")
    @GetMapping("/stages/{id}/previous")
    public OperationResponse<List<ScrmLifecycleStageEntity>> getPreviousStages(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerLifecycleService.getPreviousStages(id));
    }

    /**
     * 启用阶段。
     *
     * @param id 阶段 ID
     * @return 更新后的阶段
     * @throws ScrmException 阶段不存在
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "update")
    @PostMapping("/stages/{id}/enable")
    public OperationResponse<ScrmLifecycleStageEntity> enableStage(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCustomerLifecycleService.enableStage(id));
    }

    /**
     * 禁用阶段。
     *
     * @param id 阶段 ID
     * @return 更新后的阶段
     * @throws ScrmException 阶段不存在
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "update")
    @PostMapping("/stages/{id}/disable")
    public OperationResponse<ScrmLifecycleStageEntity> disableStage(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCustomerLifecycleService.disableStage(id));
    }

    /**
     * 刷新阶段统计。
     *
     * @param id 阶段 ID
     * @return 更新后的阶段
     * @throws ScrmException 阶段不存在
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "update")
    @PostMapping("/stages/{id}/stats")
    public OperationResponse<ScrmLifecycleStageEntity> updateStageStats(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerLifecycleService.updateStageStats(id));
    }

    /**
     * 重排阶段顺序。
     *
     * @param stageOrders 阶段 ID → 新顺序映射
     * @return 更新后的阶段列表
     * @throws ScrmException 阶段不存在 / 顺序映射为空
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "update")
    @PostMapping("/stages/reorder")
    public OperationResponse<List<ScrmLifecycleStageEntity>> reorderStages(@RequestBody Map<Long, Integer> stageOrders)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerLifecycleService.reorderStages(stageOrders));
    }

    /**
     * 阶段流转图 (节点 + 边)。
     *
     * @return 流转图
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "read")
    @GetMapping("/stages/flow")
    public OperationResponse<Map<String, Object>> getStageFlow() {
        return OperationResponse.build(scrmCustomerLifecycleService.getStageFlow());
    }

    // ============================================================
    // 客户生命周期管理
    // ============================================================

    /**
     * 创建客户生命周期记录。
     *
     * @param dto 生命周期参数
     * @return 创建后的生命周期
     * @throws ScrmException 参数非法 / 已存在
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/lifecycles")
    public OperationResponse<ScrmCustomerLifecycleEntity> createLifecycle(
            @Valid @RequestBody ScrmCustomerLifecycleDto dto) throws ScrmException {
        return OperationResponse.build(scrmCustomerLifecycleService.createLifecycle(dto));
    }

    /**
     * 更新客户生命周期 (字段非空才覆盖)。
     *
     * @param id  生命周期 ID
     * @param dto 生命周期参数
     * @return 更新后的生命周期
     * @throws ScrmException 生命周期不存在
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/lifecycles/{id}")
    public OperationResponse<ScrmCustomerLifecycleEntity> updateLifecycle(@PathVariable Long id,
                                                                          @RequestBody ScrmCustomerLifecycleDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerLifecycleService.updateLifecycle(id, dto));
    }

    /**
     * 删除客户生命周期记录。
     *
     * @param id 生命周期 ID
     * @return 空响应
     * @throws ScrmException 生命周期不存在
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "delete")
    @DeleteMapping("/lifecycles/{id}")
    public OperationResponse<Void> deleteLifecycle(@PathVariable Long id) throws ScrmException {
        scrmCustomerLifecycleService.deleteLifecycle(id);
        return OperationResponse.build();
    }

    /**
     * 查询生命周期详情。
     *
     * @param id 生命周期 ID
     * @return 生命周期详情
     * @throws ScrmException 生命周期不存在
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "read")
    @GetMapping("/lifecycles/{id}")
    public OperationResponse<ScrmCustomerLifecycleEntity> getLifecycle(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCustomerLifecycleService.getLifecycle(id));
    }

    /**
     * 按客户查询生命周期。
     *
     * @param customerId 客户 ID
     * @return 生命周期详情
     * @throws ScrmException 客户生命周期不存在
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "read")
    @GetMapping("/lifecycles/by-customer/{customerId}")
    public OperationResponse<ScrmCustomerLifecycleEntity> getLifecycleByCustomer(@PathVariable Long customerId)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerLifecycleService.getLifecycleByCustomer(customerId));
    }

    /**
     * 分页查询全部客户生命周期。
     *
     * @param page 页码 (默认 0)
     * @param size 每页大小 (默认 20)
     * @return 生命周期分页结果
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "read")
    @GetMapping("/lifecycles/list")
    public OperationResponse<Page<ScrmCustomerLifecycleEntity>> listLifecycles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "enteredCurrentStageAt"));
        return OperationResponse.build(scrmCustomerLifecycleService.listLifecycles(pageable));
    }

    /**
     * 按当前阶段编码分页查询客户生命周期。
     *
     * @param stage 阶段编码
     * @param page  页码 (默认 0)
     * @param size  每页大小 (默认 20)
     * @return 生命周期分页结果
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "read")
    @GetMapping("/lifecycles/by-stage/{stage}")
    public OperationResponse<Page<ScrmCustomerLifecycleEntity>> getLifecyclesByStage(
            @PathVariable String stage,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "enteredCurrentStageAt"));
        return OperationResponse.build(scrmCustomerLifecycleService.getLifecyclesByStage(stage, pageable));
    }

    /**
     * 按价值分层分页查询客户。
     *
     * @param valueSegment 价值分层: VIP/HIGH_VALUE/STANDARD/LOW_VALUE/AT_RISK/CHURNED
     * @param page         页码 (默认 0)
     * @param size         每页大小 (默认 20)
     * @return 生命周期分页结果
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "read")
    @GetMapping("/lifecycles/by-value/{valueSegment}")
    public OperationResponse<Page<ScrmCustomerLifecycleEntity>> getLifecyclesByValueSegment(
            @PathVariable String valueSegment,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(scrmCustomerLifecycleService.getLifecyclesByValueSegment(
                valueSegment, PageRequest.of(page, size)));
    }

    /**
     * 按风险等级分页查询客户。
     *
     * @param riskLevel 风险等级: LOW/MEDIUM/HIGH/CRITICAL
     * @param page      页码 (默认 0)
     * @param size      每页大小 (默认 20)
     * @return 生命周期分页结果
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "read")
    @GetMapping("/lifecycles/by-risk/{riskLevel}")
    public OperationResponse<Page<ScrmCustomerLifecycleEntity>> getLifecyclesByRiskLevel(
            @PathVariable String riskLevel,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(scrmCustomerLifecycleService.getLifecyclesByRiskLevel(
                riskLevel, PageRequest.of(page, size)));
    }

    /**
     * 分页查询风险客户。
     *
     * @param page 页码 (默认 0)
     * @param size 每页大小 (默认 20)
     * @return 风险客户分页结果
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "read")
    @GetMapping("/lifecycles/at-risk")
    public OperationResponse<Page<ScrmCustomerLifecycleEntity>> getAtRiskCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(scrmCustomerLifecycleService.getAtRiskCustomers(PageRequest.of(page, size)));
    }

    /**
     * 分页查询流失客户。
     *
     * @param page 页码 (默认 0)
     * @param size 每页大小 (默认 20)
     * @return 流失客户分页结果
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "read")
    @GetMapping("/lifecycles/churned")
    public OperationResponse<Page<ScrmCustomerLifecycleEntity>> getChurnedCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(scrmCustomerLifecycleService.getChurnedCustomers(PageRequest.of(page, size)));
    }

    /**
     * 分页查询高价值客户。
     *
     * @param page 页码 (默认 0)
     * @param size 每页大小 (默认 20)
     * @return 高价值客户分页结果
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "read")
    @GetMapping("/lifecycles/high-value")
    public OperationResponse<Page<ScrmCustomerLifecycleEntity>> getHighValueCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(scrmCustomerLifecycleService.getHighValueCustomers(PageRequest.of(page, size)));
    }

    /**
     * 检索客户生命周期。
     *
     * @param criteria 检索条件
     * @param page     页码 (默认 0)
     * @param size     每页大小 (默认 20)
     * @return 生命周期分页结果
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "read")
    @PostMapping("/lifecycles/search")
    public OperationResponse<Page<ScrmCustomerLifecycleEntity>> searchLifecycles(
            @RequestBody Map<String, Object> criteria,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(scrmCustomerLifecycleService.searchLifecycles(
                criteria, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "enteredCurrentStageAt"))));
    }

    /**
     * 流转客户 (验证 → 更新阶段 → 记录历史 → 触发自动化)。
     *
     * @param request 流转请求
     * @return 更新后的客户生命周期
     * @throws ScrmException 阶段不存在 / 无可用转换规则 / 冷却期内
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "update")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/lifecycles/transition")
    public OperationResponse<ScrmCustomerLifecycleEntity> transitionCustomer(
            @Valid @RequestBody ScrmLifecycleTransitionRequestDto request) throws ScrmException {
        return OperationResponse.build(scrmCustomerLifecycleService.transitionCustomer(request));
    }

    /**
     * 批量流转客户阶段。
     *
     * @param customerIds 客户 ID 列表
     * @param toStage     目标阶段编码
     * @param trigger     触发原因
     * @return 转换结果列表
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "update")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/lifecycles/batch-transition")
    public OperationResponse<List<ScrmCustomerLifecycleEntity>> batchTransition(
            @RequestParam List<Long> customerIds,
            @RequestParam String toStage,
            @RequestParam String trigger) throws ScrmException {
        return OperationResponse.build(scrmCustomerLifecycleService.batchTransition(customerIds, toStage, trigger));
    }

    /**
     * 自动流转 (检查条件 → 触发流转)。
     *
     * @param customerId 客户 ID
     * @return 流转后的客户生命周期
     * @throws ScrmException 客户生命周期不存在
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "update")
    @PostMapping("/lifecycles/auto-transition")
    public OperationResponse<ScrmCustomerLifecycleEntity> autoTransition(@RequestParam Long customerId)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerLifecycleService.autoTransition(customerId));
    }

    /**
     * 唤醒流失客户。
     *
     * @param customerId 客户 ID
     * @param campaign   唤醒活动名称
     * @return 流转后的客户生命周期
     * @throws ScrmException 客户生命周期不存在 / 无可用唤醒阶段
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "update")
    @PostMapping("/lifecycles/reactivate")
    public OperationResponse<ScrmCustomerLifecycleEntity> reactivateCustomer(
            @RequestParam Long customerId, @RequestParam String campaign) throws ScrmException {
        return OperationResponse.build(scrmCustomerLifecycleService.reactivateCustomer(customerId, campaign));
    }

    /**
     * 标记客户流失。
     *
     * @param customerId 客户 ID
     * @param reason     流失原因
     * @return 流转后的客户生命周期
     * @throws ScrmException 客户生命周期不存在 / 无流失阶段
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "update")
    @PostMapping("/lifecycles/mark-churned")
    public OperationResponse<ScrmCustomerLifecycleEntity> markChurned(
            @RequestParam Long customerId, @RequestParam String reason) throws ScrmException {
        return OperationResponse.build(scrmCustomerLifecycleService.markChurned(customerId, reason));
    }

    /**
     * 计算客户流失风险。
     *
     * @param customerId 客户 ID
     * @return 风险评分结果
     * @throws ScrmException 客户生命周期不存在
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "update")
    @PostMapping("/lifecycles/{customerId}/churn-risk")
    public OperationResponse<Map<String, Object>> updateChurnRisk(@PathVariable Long customerId)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerLifecycleService.updateChurnRisk(customerId));
    }

    /**
     * 计算客户 LTV。
     *
     * @param customerId 客户 ID
     * @return LTV 估算结果
     * @throws ScrmException 客户生命周期不存在
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "update")
    @PostMapping("/lifecycles/{customerId}/ltv")
    public OperationResponse<Map<String, Object>> updateLTV(@PathVariable Long customerId) throws ScrmException {
        return OperationResponse.build(scrmCustomerLifecycleService.updateLTV(customerId));
    }

    /**
     * 计算客户活跃度评分。
     *
     * @param customerId 客户 ID
     * @return 活跃度评分结果
     * @throws ScrmException 客户生命周期不存在
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "update")
    @PostMapping("/lifecycles/{customerId}/engagement")
    public OperationResponse<Map<String, Object>> updateEngagementScore(@PathVariable Long customerId)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerLifecycleService.updateEngagementScore(customerId));
    }

    /**
     * 重算所有客户评分。
     *
     * @return 重算汇总
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "update")
    @RateLimit(capacity = 5, refillTokens = 5, refillPeriodSeconds = 60)
    @PostMapping("/lifecycles/recalculate-scores")
    public OperationResponse<Map<String, Object>> recalculateScores() {
        return OperationResponse.build(scrmCustomerLifecycleService.recalculateAllScores());
    }

    /**
     * 生命周期时间线。
     *
     * @param customerId 客户 ID
     * @param months     月数 (默认 6)
     * @return 时间线列表
     * @throws ScrmException 客户 ID 非法
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "read")
    @GetMapping("/lifecycles/timeline/{customerId}")
    public OperationResponse<List<Map<String, Object>>> getLifecycleTimeline(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "6") int months) throws ScrmException {
        return OperationResponse.build(scrmCustomerLifecycleService.getLifecycleTimeline(customerId, months));
    }

    /**
     * 下一步最佳行动。
     *
     * @param customerId 客户 ID
     * @return 行动建议
     * @throws ScrmException 客户生命周期不存在
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "read")
    @GetMapping("/lifecycles/next-best-action/{customerId}")
    public OperationResponse<Map<String, Object>> getNextBestAction(@PathVariable Long customerId)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerLifecycleService.getNextBestAction(customerId));
    }

    /**
     * 客户旅程。
     *
     * @param customerId 客户 ID
     * @return 旅程节点列表
     * @throws ScrmException 客户 ID 非法
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "read")
    @GetMapping("/lifecycles/journey/{customerId}")
    public OperationResponse<List<Map<String, Object>>> getCustomerJourney(@PathVariable Long customerId)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerLifecycleService.getCustomerJourney(customerId));
    }

    // ============================================================
    // 转换记录管理
    // ============================================================

    /**
     * 查询转换记录详情。
     *
     * @param id 转换记录 ID
     * @return 转换记录详情
     * @throws ScrmException 转换记录不存在
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "read")
    @GetMapping("/transitions/{id}")
    public OperationResponse<ScrmLifecycleHistoryEntity> getTransition(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerLifecycleService.getTransition(id));
    }

    /**
     * 查询客户全部转换记录。
     *
     * @param customerId 客户 ID
     * @return 转换记录列表
     * @throws ScrmException 客户 ID 非法
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "read")
    @GetMapping("/transitions/by-customer/{customerId}")
    public OperationResponse<List<ScrmLifecycleHistoryEntity>> getTransitionsByCustomer(
            @PathVariable Long customerId) throws ScrmException {
        return OperationResponse.build(scrmCustomerLifecycleService.getTransitionsByCustomer(customerId));
    }

    /**
     * 按阶段分页查询转换记录。
     *
     * @param stage 阶段编码
     * @param page  页码 (默认 0)
     * @param size  每页大小 (默认 20)
     * @return 转换记录分页结果
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "read")
    @GetMapping("/transitions/by-stage/{stage}")
    public OperationResponse<Page<ScrmLifecycleHistoryEntity>> getTransitionsByStage(
            @PathVariable String stage,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "transitionTime"));
        return OperationResponse.build(scrmCustomerLifecycleService.getTransitionsByStage(stage, pageable));
    }

    /**
     * 按流转类型分页查询转换记录。
     *
     * @param type 流转类型
     * @param page 页码 (默认 0)
     * @param size 每页大小 (默认 20)
     * @return 转换记录分页结果
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "read")
    @GetMapping("/transitions/by-type/{type}")
    public OperationResponse<Page<ScrmLifecycleHistoryEntity>> getTransitionsByType(
            @PathVariable String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "transitionTime"));
        return OperationResponse.build(scrmCustomerLifecycleService.getTransitionsByType(type, pageable));
    }

    /**
     * 按时间区间分页查询转换记录。
     *
     * @param startTime 起始时间 (可空, ISO: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO: yyyy-MM-dd'T'HH:mm:ss)
     * @param page      页码 (默认 0)
     * @param size      每页大小 (默认 20)
     * @return 转换记录分页结果
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "read")
    @GetMapping("/transitions/by-date-range")
    public OperationResponse<Page<ScrmLifecycleHistoryEntity>> getTransitionsByDateRange(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "transitionTime"));
        return OperationResponse.build(scrmCustomerLifecycleService.getTransitionsByDateRange(
                startTime, endTime, pageable));
    }

    /**
     * 撤销流转。
     *
     * @param id         转换记录 ID
     * @param reason     撤销原因
     * @param reversedBy 撤销人
     * @return 撤销结果
     * @throws ScrmException 转换记录不存在 / 无法撤销
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "update")
    @PostMapping("/transitions/reverse")
    public OperationResponse<Map<String, Object>> reverseTransition(
            @RequestParam Long id, @RequestParam String reason, @RequestParam String reversedBy)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerLifecycleService.reverseTransition(id, reason, reversedBy));
    }

    /**
     * 转换统计概览。
     *
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "read")
    @GetMapping("/transitions/stats")
    public OperationResponse<Map<String, Object>> getTransitionStats() {
        return OperationResponse.build(scrmCustomerLifecycleService.getTransitionStats());
    }

    /**
     * 分析流转模式。
     *
     * @return 模式分析结果
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "read")
    @PostMapping("/transitions/analyze-patterns")
    public OperationResponse<Map<String, Object>> analyzeTransitionPatterns() {
        return OperationResponse.build(scrmCustomerLifecycleService.analyzeTransitionPatterns());
    }

    /**
     * 识别瓶颈阶段。
     *
     * @return 瓶颈阶段列表
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "read")
    @GetMapping("/transitions/bottlenecks")
    public OperationResponse<List<Map<String, Object>>> getBottleneckStages() {
        return OperationResponse.build(scrmCustomerLifecycleService.getBottleneckStages());
    }

    /**
     * 转化漏斗。
     *
     * @return 漏斗数据列表
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "read")
    @GetMapping("/transitions/conversion-funnel")
    public OperationResponse<List<Map<String, Object>>> getConversionFunnel() {
        return OperationResponse.build(scrmCustomerLifecycleService.getConversionFunnel());
    }

    // ============================================================
    // 统计分析
    // ============================================================

    /**
     * 生命周期概览。
     *
     * @return 概览统计
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getLifecycleOverview() {
        return OperationResponse.build(scrmCustomerLifecycleService.getLifecycleOverview());
    }

    /**
     * 阶段分布。
     *
     * @return 阶段分布列表
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "read")
    @GetMapping("/stats/stage-distribution")
    public OperationResponse<List<Map<String, Object>>> getStageDistribution() {
        return OperationResponse.build(scrmCustomerLifecycleService.getStageDistribution());
    }

    /**
     * 价值分层分布。
     *
     * @return 价值分层分布
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "read")
    @GetMapping("/stats/value-distribution")
    public OperationResponse<Map<String, Object>> getValueSegmentDistribution() {
        return OperationResponse.build(scrmCustomerLifecycleService.getValueSegmentDistribution());
    }

    /**
     * 风险等级分布。
     *
     * @return 风险等级分布
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "read")
    @GetMapping("/stats/risk-distribution")
    public OperationResponse<Map<String, Object>> getRiskLevelDistribution() {
        return OperationResponse.build(scrmCustomerLifecycleService.getRiskLevelDistribution());
    }

    /**
     * 整体流失率。
     *
     * @return 流失率统计
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "read")
    @GetMapping("/stats/churn-rate")
    public OperationResponse<Map<String, Object>> getChurnRate() {
        return OperationResponse.build(scrmCustomerLifecycleService.getChurnRate());
    }

    /**
     * 整体留存率。
     *
     * @return 留存率统计
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "read")
    @GetMapping("/stats/retention-rate")
    public OperationResponse<Map<String, Object>> getRetentionRate() {
        return OperationResponse.build(scrmCustomerLifecycleService.getRetentionRate());
    }

    /**
     * 平均生命周期时长。
     *
     * @return 平均周期统计
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "read")
    @GetMapping("/stats/avg-duration")
    public OperationResponse<Map<String, Object>> getAverageLifecycleDuration() {
        return OperationResponse.build(scrmCustomerLifecycleService.getAverageLifecycleDuration());
    }

    /**
     * 各阶段转化率。
     *
     * @return 阶段转化率列表
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "read")
    @GetMapping("/stats/conversion-rates")
    public OperationResponse<List<Map<String, Object>>> getStageConversionRates() {
        return OperationResponse.build(scrmCustomerLifecycleService.getStageConversionRates());
    }

    /**
     * 获客渠道统计。
     *
     * @return 渠道统计
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "read")
    @GetMapping("/stats/acquisition-channels")
    public OperationResponse<Map<String, Object>> getAcquisitionChannelStats() {
        return OperationResponse.build(scrmCustomerLifecycleService.getAcquisitionChannelStats());
    }

    /**
     * 生命周期趋势 (最近 N 月)。
     *
     * @param months 月数 (默认 6)
     * @return 趋势数据列表
     */
    @RequirePermission(resource = "scrm_customer_lifecycle", action = "read")
    @GetMapping("/stats/trend")
    public OperationResponse<List<Map<String, Object>>> getLifecycleTrend(
            @RequestParam(defaultValue = "6") int months) {
        return OperationResponse.build(scrmCustomerLifecycleService.getLifecycleTrend(months));
    }
}
