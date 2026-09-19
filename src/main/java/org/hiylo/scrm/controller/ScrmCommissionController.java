/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCommissionController.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmCommissionApproveDto;
import org.hiylo.scrm.dto.ScrmCommissionCalculateDto;
import org.hiylo.scrm.dto.ScrmCommissionPayoutDto;
import org.hiylo.scrm.dto.ScrmCommissionPlanDto;
import org.hiylo.scrm.dto.ScrmCommissionRecordDto;
import org.hiylo.scrm.dto.ScrmCommissionRuleDto;
import org.hiylo.scrm.entity.ScrmCommissionRuleEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmCommissionService;
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
 * SCRM 销售佣金管理控制器。
 * <p>
 * 提供佣金方案管理、佣金规则管理、佣金记录与计算、佣金审批、佣金发放以及佣金统计接口。
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/commissions")
@RequiredArgsConstructor
public class ScrmCommissionController {

    /** 佣金服务 */
    private final ScrmCommissionService scrmCommissionService;

    // ============================================================
    // 方案管理 /plans
    // ============================================================

    /**
     * 创建佣金方案。
     *
     * @param dto 方案参数
     * @return 创建后的方案
     * @throws ScrmException 参数非法 / planCode 重复
     */
    @RequirePermission(resource = "scrm_commission", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/plans")
    public OperationResponse<ScrmCommissionPlanDto> createPlan(@Valid @RequestBody ScrmCommissionPlanDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmCommissionService.createPlan(dto));
    }

    /**
     * 更新佣金方案。
     *
     * @param id  方案 ID
     * @param dto 方案参数
     * @return 更新后的方案
     * @throws ScrmException 方案不存在 / 参数非法 / planCode 重复
     */
    @RequirePermission(resource = "scrm_commission", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/plans/{id}")
    public OperationResponse<ScrmCommissionPlanDto> updatePlan(@PathVariable Long id,
                                                                @RequestBody ScrmCommissionPlanDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmCommissionService.updatePlan(id, dto));
    }

    /**
     * 删除佣金方案。
     *
     * @param id 方案 ID
     * @return 空响应
     * @throws ScrmException 方案不存在
     */
    @RequirePermission(resource = "scrm_commission", action = "delete")
    @DeleteMapping("/plans/{id}")
    public OperationResponse<Void> deletePlan(@PathVariable Long id) throws ScrmException {
        scrmCommissionService.deletePlan(id);
        return OperationResponse.build();
    }

    /**
     * 查询方案详情。
     *
     * @param id 方案 ID
     * @return 方案详情
     * @throws ScrmException 方案不存在
     */
    @RequirePermission(resource = "scrm_commission", action = "read")
    @GetMapping("/plans/{id}")
    public OperationResponse<ScrmCommissionPlanDto> getPlan(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCommissionService.getPlan(id));
    }

    /**
     * 按方案编码查询方案。
     *
     * @param code 方案编码
     * @return 方案详情
     * @throws ScrmException 方案不存在
     */
    @RequirePermission(resource = "scrm_commission", action = "read")
    @GetMapping("/plans/code/{code}")
    public OperationResponse<ScrmCommissionPlanDto> getPlanByCode(@PathVariable String code) throws ScrmException {
        return OperationResponse.build(scrmCommissionService.getPlanByCode(code));
    }

    /**
     * 分页查询佣金方案, 支持按方案类型/状态/关键字过滤。
     *
     * @param planType 方案类型 (可选)
     * @param status   状态 (可选)
     * @param keyword  关键字 (匹配方案名称/编码, 可选)
     * @param page     页码 (从 0 开始, 默认 0)
     * @param size     每页大小 (默认 20)
     * @return 方案分页结果
     */
    @RequirePermission(resource = "scrm_commission", action = "read")
    @GetMapping("/plans/list")
    public OperationResponse<Page<ScrmCommissionPlanDto>> listPlans(
            @RequestParam(required = false) String planType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmCommissionService.listPlans(planType, status, keyword, pageable));
    }

    /**
     * 激活方案。
     *
     * @param id 方案 ID
     * @return 更新后的方案
     * @throws ScrmException 方案不存在
     */
    @RequirePermission(resource = "scrm_commission", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/plans/{id}/activate")
    public OperationResponse<ScrmCommissionPlanDto> activatePlan(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCommissionService.activatePlan(id));
    }

    /**
     * 暂停方案。
     *
     * @param id 方案 ID
     * @return 更新后的方案
     * @throws ScrmException 方案不存在
     */
    @RequirePermission(resource = "scrm_commission", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/plans/{id}/pause")
    public OperationResponse<ScrmCommissionPlanDto> pausePlan(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCommissionService.pausePlan(id));
    }

    /**
     * 使方案过期。
     *
     * @param id 方案 ID
     * @return 更新后的方案
     * @throws ScrmException 方案不存在
     */
    @RequirePermission(resource = "scrm_commission", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/plans/{id}/expire")
    public OperationResponse<ScrmCommissionPlanDto> expirePlan(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCommissionService.expirePlan(id));
    }

    /**
     * 设为默认方案。
     *
     * @param id 方案 ID
     * @return 更新后的方案
     * @throws ScrmException 方案不存在
     */
    @RequirePermission(resource = "scrm_commission", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/plans/{id}/default")
    public OperationResponse<ScrmCommissionPlanDto> setDefaultPlan(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCommissionService.setDefault(id));
    }

    // ============================================================
    // 规则管理 /rules
    // ============================================================

    /**
     * 创建佣金规则。
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException 参数非法 / 方案不存在
     */
    @RequirePermission(resource = "scrm_commission", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/rules")
    public OperationResponse<ScrmCommissionRuleDto> createRule(@Valid @RequestBody ScrmCommissionRuleDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmCommissionService.createRule(dto));
    }

    /**
     * 更新佣金规则。
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_commission", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/rules/{id}")
    public OperationResponse<ScrmCommissionRuleDto> updateRule(@PathVariable Long id,
                                                                @RequestBody ScrmCommissionRuleDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmCommissionService.updateRule(id, dto));
    }

    /**
     * 删除佣金规则。
     *
     * @param id 规则 ID
     * @return 空响应
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_commission", action = "delete")
    @DeleteMapping("/rules/{id}")
    public OperationResponse<Void> deleteRule(@PathVariable Long id) throws ScrmException {
        scrmCommissionService.deleteRule(id);
        return OperationResponse.build();
    }

    /**
     * 查询规则详情。
     *
     * @param id 规则 ID
     * @return 规则详情
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_commission", action = "read")
    @GetMapping("/rules/{id}")
    public OperationResponse<ScrmCommissionRuleDto> getRule(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCommissionService.getRule(id));
    }

    /**
     * 分页查询规则, 支持按方案 ID/规则类型/启用状态过滤。
     *
     * @param planId   方案 ID (可选)
     * @param ruleType 规则类型 (可选)
     * @param enabled  启用状态 (可选)
     * @param page     页码 (从 0 开始, 默认 0)
     * @param size     每页大小 (默认 20)
     * @return 规则分页结果
     */
    @RequirePermission(resource = "scrm_commission", action = "read")
    @GetMapping("/rules/list")
    public OperationResponse<Page<ScrmCommissionRuleDto>> listRules(
            @RequestParam(required = false) Long planId,
            @RequestParam(required = false) String ruleType,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmCommissionService.listRules(planId, ruleType, enabled, pageable));
    }

    /**
     * 启用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_commission", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/rules/{id}/enable")
    public OperationResponse<ScrmCommissionRuleDto> enableRule(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCommissionService.enableRule(id));
    }

    /**
     * 禁用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_commission", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/rules/{id}/disable")
    public OperationResponse<ScrmCommissionRuleDto> disableRule(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCommissionService.disableRule(id));
    }

    /**
     * 获取匹配规则 (按方案 ID 与条件 Map 过滤)。
     *
     * @param planId 方案 ID
     * @param body   请求体, conditions Map
     * @return 匹配规则列表
     * @throws ScrmException 方案不存在
     */
    @RequirePermission(resource = "scrm_commission", action = "read")
    @PostMapping("/rules/matching")
    public OperationResponse<List<ScrmCommissionRuleEntity>> getMatchingRules(
            @RequestParam Long planId,
            @RequestBody(required = false) Map<String, Object> body) throws ScrmException {
        return OperationResponse.build(scrmCommissionService.getMatchingRules(planId, body));
    }

    // ============================================================
    // 佣金记录与计算 /records
    // ============================================================

    /**
     * 计算佣金 (匹配方案 → 匹配规则 → 应用计算 → 创建记录)。
     *
     * @param dto 计算参数
     * @return 创建的佣金记录
     * @throws ScrmException 方案不存在 / 方案非激活 / 订单金额非法
     */
    @RequirePermission(resource = "scrm_commission", action = "execute")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/records/calculate")
    public OperationResponse<ScrmCommissionRecordDto> calculateCommission(
            @Valid @RequestBody ScrmCommissionCalculateDto dto) throws ScrmException {
        return OperationResponse.build(scrmCommissionService.calculateCommission(dto));
    }

    /**
     * 批量计算佣金。
     *
     * @param dtos 计算参数列表
     * @return 创建的佣金记录列表
     * @throws ScrmException 部分失败时跳过
     */
    @RequirePermission(resource = "scrm_commission", action = "execute")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/records/batch-calculate")
    public OperationResponse<List<ScrmCommissionRecordDto>> batchCalculate(
            @RequestBody List<ScrmCommissionCalculateDto> dtos) throws ScrmException {
        return OperationResponse.build(scrmCommissionService.batchCalculate(dtos));
    }

    /**
     * 为订单计算佣金 (使用默认方案)。
     *
     * @param orderId       订单 ID
     * @param salesPersonId 销售人员 ID
     * @param orderAmount   订单金额
     * @return 创建的佣金记录
     * @throws ScrmException 默认方案不存在 / 订单金额非法
     */
    @RequirePermission(resource = "scrm_commission", action = "execute")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/records/calculate-order")
    public OperationResponse<ScrmCommissionRecordDto> calculateForOrder(
            @RequestParam String orderId,
            @RequestParam String salesPersonId,
            @RequestParam Double orderAmount) throws ScrmException {
        return OperationResponse.build(
                scrmCommissionService.calculateForOrder(orderId, salesPersonId, orderAmount));
    }

    /**
     * 计算周期佣金 (汇总查询)。
     *
     * @param planId        方案 ID
     * @param period        所属周期 (yyyy-MM, 可选)
     * @param salesPersonId 销售人员 ID (可选)
     * @return 周期佣金汇总
     * @throws ScrmException 方案不存在
     */
    @RequirePermission(resource = "scrm_commission", action = "read")
    @PostMapping("/records/calculate-period")
    public OperationResponse<Map<String, Object>> calculateForPeriod(
            @RequestParam Long planId,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String salesPersonId) throws ScrmException {
        return OperationResponse.build(
                scrmCommissionService.calculateForPeriod(planId, period, salesPersonId));
    }

    /**
     * 重新计算佣金记录。
     *
     * @param id 记录 ID
     * @return 重新计算后的记录
     * @throws ScrmException 记录不存在
     */
    @RequirePermission(resource = "scrm_commission", action = "execute")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/records/{id}/recalculate")
    public OperationResponse<ScrmCommissionRecordDto> recalculate(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCommissionService.recalculate(id));
    }

    /**
     * 查询佣金记录详情。
     *
     * @param id 记录 ID
     * @return 记录详情
     * @throws ScrmException 记录不存在
     */
    @RequirePermission(resource = "scrm_commission", action = "read")
    @GetMapping("/records/{id}")
    public OperationResponse<ScrmCommissionRecordDto> getRecord(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCommissionService.getRecord(id));
    }

    /**
     * 按佣金编号查询记录。
     *
     * @param recordNo 佣金编号
     * @return 记录详情
     * @throws ScrmException 记录不存在
     */
    @RequirePermission(resource = "scrm_commission", action = "read")
    @GetMapping("/records/by-no/{recordNo}")
    public OperationResponse<ScrmCommissionRecordDto> getRecordByNo(@PathVariable String recordNo)
            throws ScrmException {
        return OperationResponse.build(scrmCommissionService.getRecordByNo(recordNo));
    }

    /**
     * 分页查询佣金记录, 支持按方案/销售人员/状态/周期/时间范围过滤。
     *
     * @param planId        方案 ID (可选)
     * @param salesPersonId 销售人员 ID (可选)
     * @param status        状态 (可选)
     * @param period        所属周期 (可选)
     * @param startTime     计算时间下限 (可选)
     * @param endTime       计算时间上限 (可选)
     * @param page          页码 (从 0 开始, 默认 0)
     * @param size          每页大小 (默认 20)
     * @return 记录分页结果
     */
    @RequirePermission(resource = "scrm_commission", action = "read")
    @GetMapping("/records/list")
    public OperationResponse<Page<ScrmCommissionRecordDto>> listRecords(
            @RequestParam(required = false) Long planId,
            @RequestParam(required = false) String salesPersonId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmCommissionService.listRecords(
                planId, salesPersonId, status, period, startTime, endTime, pageable));
    }

    /**
     * 按销售人员查询记录。
     *
     * @param salesPersonId 销售人员 ID
     * @param period        所属周期 (可选)
     * @param page          页码 (从 0 开始, 默认 0)
     * @param size          每页大小 (默认 20)
     * @return 记录分页结果
     */
    @RequirePermission(resource = "scrm_commission", action = "read")
    @GetMapping("/records/by-sales-person/{salesPersonId}")
    public OperationResponse<Page<ScrmCommissionRecordDto>> getRecordsBySalesPerson(
            @PathVariable String salesPersonId,
            @RequestParam(required = false) String period,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(
                scrmCommissionService.getRecordsBySalesPerson(salesPersonId, period, pageable));
    }

    /**
     * 按订单查询记录。
     *
     * @param orderId 订单 ID
     * @return 记录列表
     */
    @RequirePermission(resource = "scrm_commission", action = "read")
    @GetMapping("/records/by-order/{orderId}")
    public OperationResponse<List<ScrmCommissionRecordDto>> getRecordsByOrder(@PathVariable String orderId) {
        return OperationResponse.build(scrmCommissionService.getRecordsByOrder(orderId));
    }

    // ============================================================
    // 审批管理 /approvals
    // ============================================================

    /**
     * 提交审批。
     *
     * @param recordIds 记录 ID 列表
     * @return 已提交的记录数
     * @throws ScrmException 记录不存在
     */
    @RequirePermission(resource = "scrm_commission", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/approvals/submit")
    public OperationResponse<Integer> submitForApproval(@RequestBody List<Long> recordIds) throws ScrmException {
        return OperationResponse.build(scrmCommissionService.submitForApproval(recordIds));
    }

    /**
     * 审批 (通过/驳回)。
     *
     * @param approveDto 审批参数
     * @return 已审批的记录数
     * @throws ScrmException 记录不存在
     */
    @RequirePermission(resource = "scrm_commission", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/approvals/approve")
    public OperationResponse<Integer> approve(@Valid @RequestBody ScrmCommissionApproveDto approveDto)
            throws ScrmException {
        return OperationResponse.build(scrmCommissionService.approve(approveDto));
    }

    /**
     * 批量审批。
     *
     * @param recordIds 记录 ID 列表
     * @param action    审批动作: APPROVE/REJECT
     * @param note      审批备注 (可选)
     * @return 已审批的记录数
     * @throws ScrmException 记录不存在 / 动作非法
     */
    @RequirePermission(resource = "scrm_commission", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/approvals/batch-approve")
    public OperationResponse<Integer> batchApprove(
            @RequestParam List<Long> recordIds,
            @RequestParam String action,
            @RequestParam(required = false) String note) throws ScrmException {
        return OperationResponse.build(scrmCommissionService.batchApprove(recordIds, action, note));
    }

    /**
     * 驳回单条记录。
     *
     * @param id     记录 ID
     * @param reason 驳回原因
     * @return 更新后的记录
     * @throws ScrmException 记录不存在
     */
    @RequirePermission(resource = "scrm_commission", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/approvals/{id}/reject")
    public OperationResponse<ScrmCommissionRecordDto> reject(@PathVariable Long id,
                                                              @RequestParam String reason) throws ScrmException {
        return OperationResponse.build(scrmCommissionService.reject(id, reason));
    }

    /**
     * 调整佣金金额。
     *
     * @param id        记录 ID
     * @param newAmount 新佣金金额
     * @param reason    调整原因 (可选)
     * @return 更新后的记录
     * @throws ScrmException 记录不存在 / 金额非法
     */
    @RequirePermission(resource = "scrm_commission", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/approvals/{id}/adjust")
    public OperationResponse<ScrmCommissionRecordDto> adjustCommission(
            @PathVariable Long id,
            @RequestParam Double newAmount,
            @RequestParam(required = false) String reason) throws ScrmException {
        return OperationResponse.build(scrmCommissionService.adjustCommission(id, newAmount, reason));
    }

    // ============================================================
    // 发放管理 /payouts
    // ============================================================

    /**
     * 发放佣金。
     *
     * @param payoutDto 发放参数
     * @return 发放汇总
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_commission", action = "execute")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/payouts")
    public OperationResponse<Map<String, Object>> payout(@Valid @RequestBody ScrmCommissionPayoutDto payoutDto)
            throws ScrmException {
        return OperationResponse.build(scrmCommissionService.payout(payoutDto));
    }

    /**
     * 批量发放。
     *
     * @param payoutDto 发放参数
     * @return 发放汇总
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_commission", action = "execute")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/payouts/batch")
    public OperationResponse<Map<String, Object>> batchPayout(@Valid @RequestBody ScrmCommissionPayoutDto payoutDto)
            throws ScrmException {
        return OperationResponse.build(scrmCommissionService.batchPayout(payoutDto));
    }

    /**
     * 发放汇总。
     *
     * @param period 所属周期 (可选)
     * @return 发放汇总
     */
    @RequirePermission(resource = "scrm_commission", action = "read")
    @GetMapping("/payouts/summary")
    public OperationResponse<Map<String, Object>> getPayoutSummary(@RequestParam(required = false) String period) {
        return OperationResponse.build(scrmCommissionService.getPayoutSummary(period));
    }

    /**
     * 退款追回。
     *
     * @param recordId 记录 ID
     * @param reason   追回原因
     * @param amount   追回金额
     * @return 更新后的记录
     * @throws ScrmException 记录不存在
     */
    @RequirePermission(resource = "scrm_commission", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/payouts/clawback")
    public OperationResponse<ScrmCommissionRecordDto> processClawback(
            @RequestParam Long recordId,
            @RequestParam String reason,
            @RequestParam Double amount) throws ScrmException {
        return OperationResponse.build(scrmCommissionService.processClawback(recordId, reason, amount));
    }

    /**
     * 标记已发放。
     *
     * @param id         记录 ID
     * @param paidAmount 实发金额
     * @return 更新后的记录
     * @throws ScrmException 记录不存在
     */
    @RequirePermission(resource = "scrm_commission", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/payouts/{id}/mark-paid")
    public OperationResponse<ScrmCommissionRecordDto> markAsPaid(@PathVariable Long id,
                                                                  @RequestParam Double paidAmount)
            throws ScrmException {
        return OperationResponse.build(scrmCommissionService.markAsPaid(id, paidAmount));
    }

    // ============================================================
    // 统计 /stats
    // ============================================================

    /**
     * 佣金统计 (总佣金/已发放/待审批/各方案/各团队)。
     *
     * @param startTime 起始时间 (可选)
     * @param endTime   结束时间 (可选)
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_commission", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getCommissionStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmCommissionService.getCommissionStats(startTime, endTime));
    }

    /**
     * 销售人员佣金排行。
     *
     * @param period 所属周期 (可选)
     * @param limit  返回条数 (默认 10)
     * @return 排行列表
     */
    @RequirePermission(resource = "scrm_commission", action = "read")
    @GetMapping("/stats/ranking")
    public OperationResponse<List<Map<String, Object>>> getSalesPersonRanking(
            @RequestParam(required = false) String period,
            @RequestParam(defaultValue = "10") Integer limit) {
        return OperationResponse.build(scrmCommissionService.getSalesPersonRanking(period, limit));
    }

    /**
     * 团队统计。
     *
     * @param period 所属周期 (可选)
     * @return 团队统计列表
     */
    @RequirePermission(resource = "scrm_commission", action = "read")
    @GetMapping("/stats/teams")
    public OperationResponse<List<Map<String, Object>>> getTeamStats(
            @RequestParam(required = false) String period) {
        return OperationResponse.build(scrmCommissionService.getTeamStats(period));
    }

    /**
     * 方案效果统计。
     *
     * @param planId    方案 ID
     * @param startTime 起始时间 (可选)
     * @param endTime   结束时间 (可选)
     * @return 方案效果
     * @throws ScrmException 方案不存在
     */
    @RequirePermission(resource = "scrm_commission", action = "read")
    @GetMapping("/stats/plan/{planId}")
    public OperationResponse<Map<String, Object>> getPlanPerformance(
            @PathVariable Long planId,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) throws ScrmException {
        return OperationResponse.build(
                scrmCommissionService.getPlanPerformance(planId, startTime, endTime));
    }

    /**
     * 佣金趋势 (最近 days 天每日佣金总额)。
     *
     * @param days 天数 (默认 7)
     * @return 趋势数据列表
     */
    @RequirePermission(resource = "scrm_commission", action = "read")
    @GetMapping("/stats/trend")
    public OperationResponse<List<Map<String, Object>>> getCommissionTrend(
            @RequestParam(defaultValue = "7") Integer days) {
        return OperationResponse.build(scrmCommissionService.getCommissionTrend(days));
    }

    /**
     * 发放报告 (按周期 + 销售人员汇总发放金额)。
     *
     * @param period 所属周期 (可选)
     * @return 发放报告
     */
    @RequirePermission(resource = "scrm_commission", action = "read")
    @GetMapping("/stats/report")
    public OperationResponse<Map<String, Object>> getPayoutReport(
            @RequestParam(required = false) String period) {
        return OperationResponse.build(scrmCommissionService.getPayoutReport(period));
    }
}
