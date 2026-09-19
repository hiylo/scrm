/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBudgetController.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmBudgetAllocationDto;
import org.hiylo.scrm.dto.ScrmBudgetExpenseApproveDto;
import org.hiylo.scrm.dto.ScrmBudgetExpenseDto;
import org.hiylo.scrm.dto.ScrmBudgetPlanDto;
import org.hiylo.scrm.dto.ScrmBudgetRoiDto;
import org.hiylo.scrm.dto.ScrmBudgetTransferDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmBudgetService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 营销预算管理控制器。
 * <p>
 * 提供预算方案、预算分配、预算支出、ROI 追踪与统计接口。
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/budgets")
@RequiredArgsConstructor
public class ScrmBudgetController {

    /** 营销预算服务 */
    private final ScrmBudgetService scrmBudgetService;

    // ============================================================
    // 方案管理 /plans
    // ============================================================

    /**
     * 创建预算方案。
     *
     * @param dto 方案参数
     * @return 创建后的方案
     * @throws ScrmException 参数非法 / planCode 重复
     */
    @RequirePermission(resource = "scrm_budget", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/plans")
    public OperationResponse<ScrmBudgetPlanDto> createPlan(@Valid @RequestBody ScrmBudgetPlanDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmBudgetService.createPlan(dto));
    }

    /**
     * 更新预算方案。
     *
     * @param id  方案 ID
     * @param dto 方案参数
     * @return 更新后的方案
     * @throws ScrmException 方案不存在 / 参数非法 / planCode 重复
     */
    @RequirePermission(resource = "scrm_budget", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/plans/{id}")
    public OperationResponse<ScrmBudgetPlanDto> updatePlan(@PathVariable Long id,
                                                            @RequestBody ScrmBudgetPlanDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmBudgetService.updatePlan(id, dto));
    }

    /**
     * 删除预算方案。
     *
     * @param id 方案 ID
     * @return 空响应
     * @throws ScrmException 方案不存在
     */
    @RequirePermission(resource = "scrm_budget", action = "delete")
    @DeleteMapping("/plans/{id}")
    public OperationResponse<Void> deletePlan(@PathVariable Long id) throws ScrmException {
        scrmBudgetService.deletePlan(id);
        return OperationResponse.build();
    }

    /**
     * 查询方案详情。
     *
     * @param id 方案 ID
     * @return 方案详情
     * @throws ScrmException 方案不存在
     */
    @RequirePermission(resource = "scrm_budget", action = "read")
    @GetMapping("/plans/{id}")
    public OperationResponse<ScrmBudgetPlanDto> getPlan(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmBudgetService.getPlan(id));
    }

    /**
     * 按方案编码查询方案。
     *
     * @param code 方案编码
     * @return 方案详情
     * @throws ScrmException 方案不存在
     */
    @RequirePermission(resource = "scrm_budget", action = "read")
    @GetMapping("/plans/code/{code}")
    public OperationResponse<ScrmBudgetPlanDto> getPlanByCode(@PathVariable String code) throws ScrmException {
        return OperationResponse.build(scrmBudgetService.getPlanByCode(code));
    }

    /**
     * 分页查询预算方案, 支持按财年/预算类型/状态/关键字过滤。
     *
     * @param fiscalYear 财年 (可选)
     * @param budgetType 预算类型 (可选)
     * @param status     状态 (可选)
     * @param keyword    关键字 (匹配方案名称/编码, 可选)
     * @param page       页码 (从 0 开始, 默认 0)
     * @param size       每页大小 (默认 20)
     * @return 方案分页结果
     */
    @RequirePermission(resource = "scrm_budget", action = "read")
    @GetMapping("/plans/list")
    public OperationResponse<Page<ScrmBudgetPlanDto>> listPlans(
            @RequestParam(required = false) Integer fiscalYear,
            @RequestParam(required = false) String budgetType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(
                scrmBudgetService.listPlans(fiscalYear, budgetType, status, keyword, pageable));
    }

    /**
     * 激活方案。
     *
     * @param id 方案 ID
     * @return 更新后的方案
     * @throws ScrmException 方案不存在
     */
    @RequirePermission(resource = "scrm_budget", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/plans/{id}/activate")
    public OperationResponse<ScrmBudgetPlanDto> activatePlan(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmBudgetService.activatePlan(id));
    }

    /**
     * 暂停方案。
     *
     * @param id 方案 ID
     * @return 更新后的方案
     * @throws ScrmException 方案不存在
     */
    @RequirePermission(resource = "scrm_budget", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/plans/{id}/pause")
    public OperationResponse<ScrmBudgetPlanDto> pausePlan(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmBudgetService.pausePlan(id));
    }

    /**
     * 关闭方案。
     *
     * @param id 方案 ID
     * @return 更新后的方案
     * @throws ScrmException 方案不存在
     */
    @RequirePermission(resource = "scrm_budget", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/plans/{id}/close")
    public OperationResponse<ScrmBudgetPlanDto> closePlan(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmBudgetService.closePlan(id));
    }

    /**
     * 审批方案。
     *
     * @param id         方案 ID
     * @param approverId 审批人 ID
     * @param amount     审批金额
     * @return 更新后的方案
     * @throws ScrmException 方案不存在 / 金额非法
     */
    @RequirePermission(resource = "scrm_budget", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/plans/{id}/approve")
    public OperationResponse<ScrmBudgetPlanDto> approvePlan(@PathVariable Long id,
                                                             @RequestParam String approverId,
                                                             @RequestParam Double amount) throws ScrmException {
        return OperationResponse.build(scrmBudgetService.approvePlan(id, approverId, amount));
    }

    /**
     * 更新方案统计 (已分配/已消耗/剩余)。
     *
     * @param id 方案 ID
     * @return 更新后的方案
     * @throws ScrmException 方案不存在
     */
    @RequirePermission(resource = "scrm_budget", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/plans/{id}/stats")
    public OperationResponse<ScrmBudgetPlanDto> updatePlanStats(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmBudgetService.updatePlanStats(id));
    }

    /**
     * 检查方案预警。
     *
     * @param id 方案 ID
     * @return 更新后的方案
     * @throws ScrmException 方案不存在
     */
    @RequirePermission(resource = "scrm_budget", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/plans/{id}/check-alert")
    public OperationResponse<ScrmBudgetPlanDto> checkPlanAlert(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmBudgetService.checkAlert(id));
    }

    /**
     * 查询即将到期的方案。
     *
     * @param days 天数 (默认 7)
     * @return 方案列表
     */
    @RequirePermission(resource = "scrm_budget", action = "read")
    @GetMapping("/plans/expiring")
    public OperationResponse<List<ScrmBudgetPlanDto>> getExpiringPlans(
            @RequestParam(defaultValue = "7") Integer days) {
        return OperationResponse.build(scrmBudgetService.getExpiringPlans(days));
    }

    // ============================================================
    // 分配管理 /allocations
    // ============================================================

    /**
     * 创建预算分配。
     *
     * @param dto 分配参数
     * @return 创建后的分配
     * @throws ScrmException 参数非法 / 方案不存在
     */
    @RequirePermission(resource = "scrm_budget", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/allocations")
    public OperationResponse<ScrmBudgetAllocationDto> createAllocation(
            @Valid @RequestBody ScrmBudgetAllocationDto dto) throws ScrmException {
        return OperationResponse.build(scrmBudgetService.createAllocation(dto));
    }

    /**
     * 更新预算分配。
     *
     * @param id  分配 ID
     * @param dto 分配参数
     * @return 更新后的分配
     * @throws ScrmException 分配不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_budget", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/allocations/{id}")
    public OperationResponse<ScrmBudgetAllocationDto> updateAllocation(@PathVariable Long id,
                                                                        @RequestBody ScrmBudgetAllocationDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmBudgetService.updateAllocation(id, dto));
    }

    /**
     * 删除预算分配。
     *
     * @param id 分配 ID
     * @return 空响应
     * @throws ScrmException 分配不存在
     */
    @RequirePermission(resource = "scrm_budget", action = "delete")
    @DeleteMapping("/allocations/{id}")
    public OperationResponse<Void> deleteAllocation(@PathVariable Long id) throws ScrmException {
        scrmBudgetService.deleteAllocation(id);
        return OperationResponse.build();
    }

    /**
     * 查询分配详情。
     *
     * @param id 分配 ID
     * @return 分配详情
     * @throws ScrmException 分配不存在
     */
    @RequirePermission(resource = "scrm_budget", action = "read")
    @GetMapping("/allocations/{id}")
    public OperationResponse<ScrmBudgetAllocationDto> getAllocation(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmBudgetService.getAllocation(id));
    }

    /**
     * 分页查询预算分配, 支持按方案 ID/分配类型/状态过滤。
     *
     * @param planId         方案 ID (可选)
     * @param allocationType 分配类型 (可选)
     * @param status         状态 (可选)
     * @param page           页码 (从 0 开始, 默认 0)
     * @param size           每页大小 (默认 20)
     * @return 分配分页结果
     */
    @RequirePermission(resource = "scrm_budget", action = "read")
    @GetMapping("/allocations/list")
    public OperationResponse<Page<ScrmBudgetAllocationDto>> listAllocations(
            @RequestParam(required = false) Long planId,
            @RequestParam(required = false) String allocationType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(
                scrmBudgetService.listAllocations(planId, allocationType, status, pageable));
    }

    /**
     * 预算调拨 (从源分配调拨金额到目标分配)。
     *
     * @param transferDto 调拨参数
     * @return 调拨结果汇总
     * @throws ScrmException 分配不存在 / 金额非法 / 源分配余额不足
     */
    @RequirePermission(resource = "scrm_budget", action = "update")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/allocations/transfer")
    public OperationResponse<Map<String, Object>> transferBudget(@Valid @RequestBody ScrmBudgetTransferDto transferDto)
            throws ScrmException {
        return OperationResponse.build(scrmBudgetService.transferBudget(transferDto));
    }

    /**
     * 更新分配消耗统计。
     *
     * @param id 分配 ID
     * @return 更新后的分配
     * @throws ScrmException 分配不存在
     */
    @RequirePermission(resource = "scrm_budget", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/allocations/{id}/stats")
    public OperationResponse<ScrmBudgetAllocationDto> updateAllocationStats(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmBudgetService.updateAllocationStats(id));
    }

    /**
     * 检查分配预警。
     *
     * @param id 分配 ID
     * @return 更新后的分配
     * @throws ScrmException 分配不存在
     */
    @RequirePermission(resource = "scrm_budget", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/allocations/{id}/check-alert")
    public OperationResponse<ScrmBudgetAllocationDto> checkAllocationAlert(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmBudgetService.checkAllocationAlert(id));
    }

    /**
     * 查询已耗尽分配。
     *
     * @return 分配列表
     */
    @RequirePermission(resource = "scrm_budget", action = "read")
    @GetMapping("/allocations/exhausted")
    public OperationResponse<List<ScrmBudgetAllocationDto>> getExhaustedAllocations() {
        return OperationResponse.build(scrmBudgetService.getExhaustedAllocations());
    }

    /**
     * 分配汇总。
     *
     * @param planId 方案 ID
     * @return 分配汇总
     * @throws ScrmException 方案不存在
     */
    @RequirePermission(resource = "scrm_budget", action = "read")
    @GetMapping("/allocations/summary/{planId}")
    public OperationResponse<Map<String, Object>> getAllocationSummary(@PathVariable Long planId)
            throws ScrmException {
        return OperationResponse.build(scrmBudgetService.getAllocationSummary(planId));
    }

    // ============================================================
    // 支出管理 /expenses
    // ============================================================

    /**
     * 创建支出。
     *
     * @param dto 支出参数
     * @return 创建后的支出
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_budget", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/expenses")
    public OperationResponse<ScrmBudgetExpenseDto> createExpense(@Valid @RequestBody ScrmBudgetExpenseDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmBudgetService.createExpense(dto));
    }

    /**
     * 更新支出。
     *
     * @param id  支出 ID
     * @param dto 支出参数
     * @return 更新后的支出
     * @throws ScrmException 支出不存在 / 参数非法 / 状态不允许
     */
    @RequirePermission(resource = "scrm_budget", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/expenses/{id}")
    public OperationResponse<ScrmBudgetExpenseDto> updateExpense(@PathVariable Long id,
                                                                  @RequestBody ScrmBudgetExpenseDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmBudgetService.updateExpense(id, dto));
    }

    /**
     * 删除支出。
     *
     * @param id 支出 ID
     * @return 空响应
     * @throws ScrmException 支出不存在
     */
    @RequirePermission(resource = "scrm_budget", action = "delete")
    @DeleteMapping("/expenses/{id}")
    public OperationResponse<Void> deleteExpense(@PathVariable Long id) throws ScrmException {
        scrmBudgetService.deleteExpense(id);
        return OperationResponse.build();
    }

    /**
     * 查询支出详情。
     *
     * @param id 支出 ID
     * @return 支出详情
     * @throws ScrmException 支出不存在
     */
    @RequirePermission(resource = "scrm_budget", action = "read")
    @GetMapping("/expenses/{id}")
    public OperationResponse<ScrmBudgetExpenseDto> getExpense(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmBudgetService.getExpense(id));
    }

    /**
     * 按支出编号查询支出。
     *
     * @param expenseNo 支出编号
     * @return 支出详情
     * @throws ScrmException 支出不存在
     */
    @RequirePermission(resource = "scrm_budget", action = "read")
    @GetMapping("/expenses/by-no/{expenseNo}")
    public OperationResponse<ScrmBudgetExpenseDto> getExpenseByNo(@PathVariable String expenseNo)
            throws ScrmException {
        return OperationResponse.build(scrmBudgetService.getExpenseByNo(expenseNo));
    }

    /**
     * 分页查询支出, 支持按方案/分配/活动/类型/状态/时间范围过滤。
     *
     * @param planId       方案 ID (可选)
     * @param allocationId 分配 ID (可选)
     * @param campaignId   营销活动 ID (可选)
     * @param expenseType  支出类型 (可选)
     * @param status       状态 (可选)
     * @param startTime    支出日期下限 (可选)
     * @param endTime      支出日期上限 (可选)
     * @param page         页码 (从 0 开始, 默认 0)
     * @param size         每页大小 (默认 20)
     * @return 支出分页结果
     */
    @RequirePermission(resource = "scrm_budget", action = "read")
    @GetMapping("/expenses/list")
    public OperationResponse<Page<ScrmBudgetExpenseDto>> listExpenses(
            @RequestParam(required = false) Long planId,
            @RequestParam(required = false) Long allocationId,
            @RequestParam(required = false) Long campaignId,
            @RequestParam(required = false) String expenseType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmBudgetService.listExpenses(
                planId, allocationId, campaignId, expenseType, status, startTime, endTime, pageable));
    }

    /**
     * 审批支出 (通过/驳回)。
     *
     * @param approveDto 审批参数
     * @return 更新后的支出
     * @throws ScrmException 支出不存在 / 动作非法 / 状态不允许
     */
    @RequirePermission(resource = "scrm_budget", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/expenses/approve")
    public OperationResponse<ScrmBudgetExpenseDto> approveExpense(
            @Valid @RequestBody ScrmBudgetExpenseApproveDto approveDto) throws ScrmException {
        return OperationResponse.build(scrmBudgetService.approveExpense(approveDto));
    }

    /**
     * 驳回支出。
     *
     * @param id     支出 ID
     * @param reason 驳回原因
     * @return 更新后的支出
     * @throws ScrmException 支出不存在
     */
    @RequirePermission(resource = "scrm_budget", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/expenses/{id}/reject")
    public OperationResponse<ScrmBudgetExpenseDto> rejectExpense(@PathVariable Long id,
                                                                  @RequestParam String reason)
            throws ScrmException {
        return OperationResponse.build(scrmBudgetService.rejectExpense(id, reason));
    }

    /**
     * 标记支出已付款。
     *
     * @param id 支出 ID
     * @return 更新后的支出
     * @throws ScrmException 支出不存在 / 状态不允许
     */
    @RequirePermission(resource = "scrm_budget", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/expenses/{id}/paid")
    public OperationResponse<ScrmBudgetExpenseDto> markAsPaid(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmBudgetService.markAsPaid(id));
    }

    /**
     * 分页查询待审批支出。
     *
     * @param page 页码 (从 0 开始, 默认 0)
     * @param size 每页大小 (默认 20)
     * @return 支出分页结果
     */
    @RequirePermission(resource = "scrm_budget", action = "read")
    @GetMapping("/expenses/pending")
    public OperationResponse<Page<ScrmBudgetExpenseDto>> getPendingExpenses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmBudgetService.getPendingExpenses(pageable));
    }

    /**
     * 按方案分页查询支出。
     *
     * @param planId 方案 ID
     * @param page   页码 (从 0 开始, 默认 0)
     * @param size   每页大小 (默认 20)
     * @return 支出分页结果
     */
    @RequirePermission(resource = "scrm_budget", action = "read")
    @GetMapping("/expenses/by-plan/{planId}")
    public OperationResponse<Page<ScrmBudgetExpenseDto>> getExpensesByPlan(
            @PathVariable Long planId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmBudgetService.getExpensesByPlan(planId, pageable));
    }

    /**
     * 按营销活动分页查询支出。
     *
     * @param campaignId 营销活动 ID
     * @param page       页码 (从 0 开始, 默认 0)
     * @param size       每页大小 (默认 20)
     * @return 支出分页结果
     */
    @RequirePermission(resource = "scrm_budget", action = "read")
    @GetMapping("/expenses/by-campaign/{campaignId}")
    public OperationResponse<Page<ScrmBudgetExpenseDto>> getExpensesByCampaign(
            @PathVariable Long campaignId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmBudgetService.getExpensesByCampaign(campaignId, pageable));
    }

    /**
     * 批量审批支出。
     *
     * @param expenseIds 支出 ID 列表
     * @param action     审批动作: APPROVE/REJECT
     * @param comment    审批备注 (可选)
     * @return 已审批的记录数
     * @throws ScrmException 列表为空 / 动作非法
     */
    @RequirePermission(resource = "scrm_budget", action = "update")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/expenses/batch-approve")
    public OperationResponse<Integer> batchApprove(
            @RequestParam List<Long> expenseIds,
            @RequestParam String action,
            @RequestParam(required = false) String comment) throws ScrmException {
        return OperationResponse.build(scrmBudgetService.batchApprove(expenseIds, action, comment));
    }

    // ============================================================
    // ROI 追踪 /roi
    // ============================================================

    /**
     * 计算方案在指定周期的 ROI。
     *
     * @param planId 方案 ID
     * @param period 周期 (yyyy-MM, 可选默认当月)
     * @return ROI DTO
     * @throws ScrmException 方案不存在
     */
    @RequirePermission(resource = "scrm_budget", action = "execute")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/roi/calculate")
    public OperationResponse<ScrmBudgetRoiDto> calculateRoi(
            @RequestParam Long planId,
            @RequestParam(required = false) String period) throws ScrmException {
        return OperationResponse.build(scrmBudgetService.calculateRoi(planId, period));
    }

    /**
     * 查询 ROI 详情。
     *
     * @param id ROI ID
     * @return ROI 详情
     * @throws ScrmException ROI 不存在
     */
    @RequirePermission(resource = "scrm_budget", action = "read")
    @GetMapping("/roi/{id}")
    public OperationResponse<ScrmBudgetRoiDto> getRoi(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmBudgetService.getRoi(id));
    }

    /**
     * 分页查询 ROI, 支持按方案/活动/周期过滤。
     *
     * @param planId     方案 ID (可选)
     * @param campaignId 营销活动 ID (可选)
     * @param period     周期 (可选)
     * @param page       页码 (从 0 开始, 默认 0)
     * @param size       每页大小 (默认 20)
     * @return ROI 分页结果
     */
    @RequirePermission(resource = "scrm_budget", action = "read")
    @GetMapping("/roi/list")
    public OperationResponse<Page<ScrmBudgetRoiDto>> listRoi(
            @RequestParam(required = false) Long planId,
            @RequestParam(required = false) Long campaignId,
            @RequestParam(required = false) String period,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmBudgetService.listRoi(planId, campaignId, period, pageable));
    }

    /**
     * 活动维度 ROI。
     *
     * @param campaignId 营销活动 ID
     * @param startTime  起始时间 (可选)
     * @param endTime    结束时间 (可选)
     * @return 活动 ROI 汇总
     */
    @RequirePermission(resource = "scrm_budget", action = "read")
    @GetMapping("/roi/campaign/{campaignId}")
    public OperationResponse<Map<String, Object>> getCampaignRoi(
            @PathVariable Long campaignId,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(
                scrmBudgetService.getCampaignRoi(campaignId, startTime, endTime));
    }

    /**
     * 渠道维度 ROI。
     *
     * @param planId    方案 ID (可选)
     * @param startTime 起始时间 (可选)
     * @param endTime   结束时间 (可选)
     * @return 渠道 ROI 列表
     */
    @RequirePermission(resource = "scrm_budget", action = "read")
    @GetMapping("/roi/channel")
    public OperationResponse<List<Map<String, Object>>> getChannelRoi(
            @RequestParam(required = false) Long planId,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(
                scrmBudgetService.getChannelRoi(planId, startTime, endTime));
    }

    /**
     * ROI 趋势 (最近 months 个月)。
     *
     * @param planId 方案 ID
     * @param months 月数 (默认 6)
     * @return 趋势数据列表
     * @throws ScrmException 方案不存在
     */
    @RequirePermission(resource = "scrm_budget", action = "read")
    @GetMapping("/roi/trend")
    public OperationResponse<List<Map<String, Object>>> getRoiTrend(
            @RequestParam Long planId,
            @RequestParam(defaultValue = "6") Integer months) throws ScrmException {
        return OperationResponse.build(scrmBudgetService.getRoiTrend(planId, months));
    }

    /**
     * ROI 对比 (多方案在同一周期的 ROI 对比)。
     *
     * @param planIds 方案 ID 列表
     * @param period  周期 (可选默认当月)
     * @return 对比结果列表
     */
    @RequirePermission(resource = "scrm_budget", action = "read")
    @PostMapping("/roi/comparison")
    public OperationResponse<List<Map<String, Object>>> getRoiComparison(
            @RequestParam List<Long> planIds,
            @RequestParam(required = false) String period) {
        return OperationResponse.build(scrmBudgetService.getRoiComparison(planIds, period));
    }

    /**
     * 更新活动 ROI。
     *
     * @param campaignId 营销活动 ID
     * @return 活动 ROI DTO
     */
    @RequirePermission(resource = "scrm_budget", action = "execute")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/roi/update-campaign/{campaignId}")
    public OperationResponse<ScrmBudgetRoiDto> updateCampaignRoi(@PathVariable Long campaignId) {
        return OperationResponse.build(scrmBudgetService.updateCampaignRoi(campaignId));
    }

    // ============================================================
    // 统计 /stats
    // ============================================================

    /**
     * 预算统计 (按财年汇总总额/已分配/已消耗/剩余/消耗率)。
     *
     * @param fiscalYear 财年 (可选)
     * @return 预算统计
     */
    @RequirePermission(resource = "scrm_budget", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getBudgetStats(
            @RequestParam(required = false) Integer fiscalYear) {
        return OperationResponse.build(scrmBudgetService.getBudgetStats(fiscalYear));
    }

    /**
     * 支出统计 (按类型/部门/活动汇总)。
     *
     * @param startTime 起始时间 (可选)
     * @param endTime   结束时间 (可选)
     * @return 支出统计
     */
    @RequirePermission(resource = "scrm_budget", action = "read")
    @GetMapping("/stats/spend")
    public OperationResponse<Map<String, Object>> getSpendStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmBudgetService.getSpendStats(startTime, endTime));
    }

    /**
     * ROI 概览 (按时间范围汇总总支出/总收入/ROI/ROAS)。
     *
     * @param startTime 起始时间 (可选)
     * @param endTime   结束时间 (可选)
     * @return ROI 概览
     */
    @RequirePermission(resource = "scrm_budget", action = "read")
    @GetMapping("/stats/roi")
    public OperationResponse<Map<String, Object>> getRoiOverview(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmBudgetService.getRoiOverview(startTime, endTime));
    }

    /**
     * 预算利用率。
     *
     * @param planId 方案 ID
     * @return 预算利用率
     * @throws ScrmException 方案不存在
     */
    @RequirePermission(resource = "scrm_budget", action = "read")
    @GetMapping("/stats/utilization/{planId}")
    public OperationResponse<Map<String, Object>> getBudgetUtilization(@PathVariable Long planId)
            throws ScrmException {
        return OperationResponse.build(scrmBudgetService.getBudgetUtilization(planId));
    }

    /**
     * 预算趋势 (最近 months 个月每月支出总额)。
     *
     * @param months 月数 (默认 6)
     * @return 趋势数据列表
     */
    @RequirePermission(resource = "scrm_budget", action = "read")
    @GetMapping("/stats/trend")
    public OperationResponse<List<Map<String, Object>>> getBudgetTrend(
            @RequestParam(defaultValue = "6") Integer months) {
        return OperationResponse.build(scrmBudgetService.getBudgetTrend(months));
    }

    /**
     * 支出最高的活动。
     *
     * @param limit 返回条数 (默认 10)
     * @return 活动支出排行列表
     */
    @RequirePermission(resource = "scrm_budget", action = "read")
    @GetMapping("/stats/top-campaigns")
    public OperationResponse<List<Map<String, Object>>> getTopSpendingCampaigns(
            @RequestParam(defaultValue = "10") Integer limit) {
        return OperationResponse.build(scrmBudgetService.getTopSpendingCampaigns(limit));
    }

    /**
     * 预算差异分析 (实际 vs 计划)。
     *
     * @param planId 方案 ID
     * @return 预算差异分析
     * @throws ScrmException 方案不存在
     */
    @RequirePermission(resource = "scrm_budget", action = "read")
    @GetMapping("/stats/variance/{planId}")
    public OperationResponse<Map<String, Object>> getBudgetVariance(@PathVariable Long planId)
            throws ScrmException {
        return OperationResponse.build(scrmBudgetService.getBudgetVariance(planId));
    }
}
