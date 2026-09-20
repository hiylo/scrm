/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerLevelController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmCustomerLevelAssignDto;
import org.hiylo.scrm.dto.ScrmCustomerLevelDto;
import org.hiylo.scrm.dto.ScrmCustomerLevelHistoryDto;
import org.hiylo.scrm.dto.ScrmCustomerLevelRuleDto;
import org.hiylo.scrm.entity.ScrmCustomerLevelEntity;
import org.hiylo.scrm.entity.ScrmCustomerLevelHistoryEntity;
import org.hiylo.scrm.entity.ScrmCustomerLevelRuleEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmCustomerLevelService;
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

import java.util.List;
import java.util.Map;

/**
 * SCRM 客户分级/分层管理控制器。
 * <p>
 * 提供客户等级定义、升降级规则、手动/批量分配、自动评估、客户当前等级查询、
 * 等级变更历史查询与等级分布统计接口。权限由 gateway-server 统一鉴权,
 * {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/customer-levels")
@RequiredArgsConstructor
public class ScrmCustomerLevelController {

    /** 客户分级服务 */
    private final ScrmCustomerLevelService scrmCustomerLevelService;

    // ============================================================
    // 等级管理
    // ============================================================

    /**
     * 创建客户等级。
     *
     * @param dto 等级参数
     * @return 创建后的等级
     * @throws ScrmException 参数非法 / 等级编码重复
     */
    @RequirePermission(resource = "scrm_customer_level", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping
    public OperationResponse<ScrmCustomerLevelEntity> createLevel(@Valid @RequestBody ScrmCustomerLevelDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerLevelService.createLevel(dto));
    }

    /**
     * 更新客户等级。
     *
     * @param id  等级 ID
     * @param dto 等级参数
     * @return 更新后的等级
     * @throws ScrmException 等级不存在 / 参数非法 / 等级编码重复
     */
    @RequirePermission(resource = "scrm_customer_level", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/{id}")
    public OperationResponse<ScrmCustomerLevelEntity> updateLevel(@PathVariable Long id,
                                                                   @RequestBody ScrmCustomerLevelDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerLevelService.updateLevel(id, dto));
    }

    /**
     * 删除客户等级。
     *
     * @param id 等级 ID
     * @return 空响应
     * @throws ScrmException 等级不存在 / 仍有规则或历史引用
     */
    @RequirePermission(resource = "scrm_customer_level", action = "delete")
    @DeleteMapping("/{id}")
    public OperationResponse<Void> deleteLevel(@PathVariable Long id) throws ScrmException {
        scrmCustomerLevelService.deleteLevel(id);
        return OperationResponse.build();
    }

    /**
     * 查询等级详情。
     *
     * @param id 等级 ID
     * @return 等级详情
     * @throws ScrmException 等级不存在
     */
    @RequirePermission(resource = "scrm_customer_level", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmCustomerLevelEntity> getLevel(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCustomerLevelService.getLevel(id));
    }

    /**
     * 分页查询客户等级列表。
     *
     * @param enabled 启用状态过滤（可空, null=全部）
     * @param page    页码（从 0 开始, 默认 0）
     * @param size    每页大小（默认 20）
     * @return 等级分页结果 (按 levelOrder ASC)
     */
    @RequirePermission(resource = "scrm_customer_level", action = "read")
    @GetMapping("/list")
    public OperationResponse<Page<ScrmCustomerLevelEntity>> listLevels(
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "levelOrder"));
        return OperationResponse.build(scrmCustomerLevelService.listLevels(enabled, pageable));
    }

    /**
     * 设置为默认等级。
     *
     * @param id 等级 ID
     * @return 更新后的等级
     * @throws ScrmException 等级不存在
     */
    @RequirePermission(resource = "scrm_customer_level", action = "update")
    @PostMapping("/{id}/default")
    public OperationResponse<ScrmCustomerLevelEntity> setDefaultLevel(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerLevelService.setDefaultLevel(id));
    }

    /**
     * 启用等级。
     *
     * @param id 等级 ID
     * @return 更新后的等级
     * @throws ScrmException 等级不存在
     */
    @RequirePermission(resource = "scrm_customer_level", action = "update")
    @PostMapping("/{id}/enable")
    public OperationResponse<ScrmCustomerLevelEntity> enableLevel(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerLevelService.enableLevel(id));
    }

    /**
     * 禁用等级。
     *
     * @param id 等级 ID
     * @return 更新后的等级
     * @throws ScrmException 等级不存在
     */
    @RequirePermission(resource = "scrm_customer_level", action = "update")
    @PostMapping("/{id}/disable")
    public OperationResponse<ScrmCustomerLevelEntity> disableLevel(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerLevelService.disableLevel(id));
    }

    // ============================================================
    // 规则管理 /rules
    // ============================================================

    /**
     * 创建升降级规则。
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException 参数非法 / 目标等级不存在 / conditions 非合法 JSON
     */
    @RequirePermission(resource = "scrm_customer_level", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/rules")
    public OperationResponse<ScrmCustomerLevelRuleEntity> createRule(@Valid @RequestBody ScrmCustomerLevelRuleDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerLevelService.createRule(dto));
    }

    /**
     * 更新升降级规则。
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 参数非法 / 目标等级不存在
     */
    @RequirePermission(resource = "scrm_customer_level", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/rules/{id}")
    public OperationResponse<ScrmCustomerLevelRuleEntity> updateRule(@PathVariable Long id,
                                                                     @RequestBody ScrmCustomerLevelRuleDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerLevelService.updateRule(id, dto));
    }

    /**
     * 删除升降级规则。
     *
     * @param id 规则 ID
     * @return 空响应
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_customer_level", action = "delete")
    @DeleteMapping("/rules/{id}")
    public OperationResponse<Void> deleteRule(@PathVariable Long id) throws ScrmException {
        scrmCustomerLevelService.deleteRule(id);
        return OperationResponse.build();
    }

    /**
     * 查询规则详情。
     *
     * @param id 规则 ID
     * @return 规则详情
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_customer_level", action = "read")
    @GetMapping("/rules/{id}")
    public OperationResponse<ScrmCustomerLevelRuleEntity> getRule(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCustomerLevelService.getRule(id));
    }

    /**
     * 分页查询规则列表。
     *
     * @param targetLevelId 目标等级 ID 过滤（可空）
     * @param ruleType      规则类型过滤: UPGRADE / DOWNGRADE（可空）
     * @param enabled       启用状态过滤（可空）
     * @param page          页码（从 0 开始, 默认 0）
     * @param size          每页大小（默认 20）
     * @return 规则分页结果 (按 priority ASC, createTime DESC)
     */
    @RequirePermission(resource = "scrm_customer_level", action = "read")
    @GetMapping("/rules/list")
    public OperationResponse<Page<ScrmCustomerLevelRuleEntity>> listRules(
            @RequestParam(required = false) Long targetLevelId,
            @RequestParam(required = false) String ruleType,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "priority")
                        .and(Sort.by(Sort.Direction.DESC, "createTime")));
        return OperationResponse.build(scrmCustomerLevelService.listRules(targetLevelId, ruleType, enabled, pageable));
    }

    /**
     * 启用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_customer_level", action = "update")
    @PostMapping("/rules/{id}/enable")
    public OperationResponse<ScrmCustomerLevelRuleEntity> enableRule(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerLevelService.enableRule(id));
    }

    /**
     * 禁用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_customer_level", action = "update")
    @PostMapping("/rules/{id}/disable")
    public OperationResponse<ScrmCustomerLevelRuleEntity> disableRule(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerLevelService.disableRule(id));
    }

    // ============================================================
    // 等级分配 /assign
    // ============================================================

    /**
     * 手动分配客户等级 (记录历史)。
     *
     * @param assignDto 分配请求 (customerId + levelId + reason)
     * @return 创建后的历史记录 (同等级跳过时返回 null)
     * @throws ScrmException 客户不存在 / 等级不存在或已禁用
     */
    @RequirePermission(resource = "scrm_customer_level", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/assign")
    public OperationResponse<ScrmCustomerLevelHistoryEntity> assignLevel(
            @Valid @RequestBody ScrmCustomerLevelAssignDto assignDto) throws ScrmException {
        return OperationResponse.build(scrmCustomerLevelService.assignLevel(assignDto));
    }

    /**
     * 批量分配客户等级。
     *
     * @param request 批量分配请求 (customerIds / levelId / reason)
     * @return 成功分配的客户数
     * @throws ScrmException 等级不存在
     */
    @RequirePermission(resource = "scrm_customer_level", action = "update")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/batch-assign")
    public OperationResponse<Integer> batchAssignLevel(@RequestBody BatchAssignRequest request)
            throws ScrmException {
        int success = scrmCustomerLevelService.batchAssignLevel(
                request.getCustomerIds(), request.getLevelId(), request.getReason());
        return OperationResponse.build(success);
    }

    // ============================================================
    // 自动评估 /evaluate
    // ============================================================

    /**
     * 评估客户等级规则 (自动升降级)。
     * <p>同步返回变更后的历史记录, 无变更返回 null。用于规则配置后的效果验证或定时任务触发。</p>
     *
     * @param customerId 客户 ID
     * @return 变更后的历史记录 (无变更返回 null)
     * @throws ScrmException 客户不存在
     */
    @RequirePermission(resource = "scrm_customer_level", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/evaluate/{customerId}")
    public OperationResponse<ScrmCustomerLevelHistoryEntity> evaluateRules(@PathVariable Long customerId)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerLevelService.evaluateRules(customerId));
    }

    /**
     * 批量评估所有已分级客户 (定时任务用)。
     *
     * @return 评估结果: {total, changed, failed}
     */
    @RequirePermission(resource = "scrm_customer_level", action = "update")
    @RateLimit(capacity = 1, refillTokens = 1, refillPeriodSeconds = 60)
    @PostMapping("/evaluate/batch")
    public OperationResponse<Map<String, Integer>> batchEvaluate() {
        return OperationResponse.build(scrmCustomerLevelService.batchEvaluate());
    }

    // ============================================================
    // 客户等级查询 /customer /history /distribution
    // ============================================================

    /**
     * 查询客户当前等级。
     *
     * @param customerId 客户 ID
     * @return 客户当前等级 (无历史记录返回默认等级, 仍无则返回 null)
     * @throws ScrmException 客户不存在
     */
    @RequirePermission(resource = "scrm_customer_level", action = "read")
    @GetMapping("/customer/{customerId}")
    public OperationResponse<ScrmCustomerLevelEntity> getCustomerLevel(@PathVariable Long customerId)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerLevelService.getCustomerLevel(customerId));
    }

    /**
     * 查询客户等级变更历史 (按变更时间倒序)。
     *
     * @param customerId 客户 ID
     * @param page       页码（从 0 开始, 默认 0）
     * @param size       每页大小（默认 20）
     * @return 历史分页结果
     * @throws ScrmException 客户不存在
     */
    @RequirePermission(resource = "scrm_customer_level", action = "read")
    @GetMapping("/history/{customerId}")
    public OperationResponse<Page<ScrmCustomerLevelHistoryDto>> getLevelHistory(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) throws ScrmException {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "changedAt"));
        return OperationResponse.build(scrmCustomerLevelService.getLevelHistory(customerId, pageable));
    }

    /**
     * 等级分布统计: 各等级当前客户数。
     *
     * @return 等级分布统计列表 (按 levelOrder ASC, 末尾追加总计行)
     */
    @RequirePermission(resource = "scrm_customer_level", action = "read")
    @GetMapping("/distribution")
    public OperationResponse<List<Map<String, Object>>> getLevelDistribution() {
        return OperationResponse.build(scrmCustomerLevelService.getLevelDistribution());
    }

    /**
     * 批量分配请求体。
     * @author Hsi Chu
     */
    @lombok.Data
    public static class BatchAssignRequest {
        /** 客户 ID 列表 */
        private List<Long> customerIds;
        /** 目标等级 ID */
        private Long levelId;
        /** 变更原因 (可空) */
        private String reason;
    }
}
