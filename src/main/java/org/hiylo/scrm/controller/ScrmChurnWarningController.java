/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmChurnWarningController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmChurnRecoveryDto;
import org.hiylo.scrm.dto.ScrmChurnRuleDto;
import org.hiylo.scrm.dto.ScrmChurnScanDto;
import org.hiylo.scrm.entity.ScrmChurnRecoveryEntity;
import org.hiylo.scrm.entity.ScrmChurnRuleEntity;
import org.hiylo.scrm.entity.ScrmChurnWarningEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmChurnWarningService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 客户流失预警控制器。
 * <p>
 * 提供流失规则管理、客户流失风险扫描、预警处理、挽留记录管理与流失统计接口。
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/churn-warnings")
@RequiredArgsConstructor
public class ScrmChurnWarningController {

    /** 流失预警服务 */
    private final ScrmChurnWarningService scrmChurnWarningService;

    // ============================================================
    // 规则管理 /rules
    // ============================================================

    /**
     * 创建流失预警规则。
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_churn_warning", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/rules")
    public OperationResponse<ScrmChurnRuleEntity> createRule(@Valid @RequestBody ScrmChurnRuleDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmChurnWarningService.createRule(dto));
    }

    /**
     * 更新流失预警规则。
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_churn_warning", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/rules/{id}")
    public OperationResponse<ScrmChurnRuleEntity> updateRule(@PathVariable Long id,
                                                              @RequestBody ScrmChurnRuleDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmChurnWarningService.updateRule(id, dto));
    }

    /**
     * 删除流失预警规则。
     *
     * @param id 规则 ID
     * @return 空响应
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_churn_warning", action = "delete")
    @DeleteMapping("/rules/{id}")
    public OperationResponse<Void> deleteRule(@PathVariable Long id) throws ScrmException {
        scrmChurnWarningService.deleteRule(id);
        return OperationResponse.build();
    }

    /**
     * 查询规则详情。
     *
     * @param id 规则 ID
     * @return 规则详情
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_churn_warning", action = "read")
    @GetMapping("/rules/{id}")
    public OperationResponse<ScrmChurnRuleEntity> getRule(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmChurnWarningService.getRule(id));
    }

    /**
     * 分页查询规则列表。
     *
     * @param riskLevel 风险等级过滤（可空）: HIGH / MEDIUM / LOW
     * @param enabled   启用状态过滤（可空）
     * @param keyword   规则名称关键字模糊匹配（可空）
     * @param page      页码（从 0 开始, 默认 0）
     * @param size      每页大小（默认 20）
     * @return 规则分页结果 (按 priority ASC, createTime DESC)
     */
    @RequirePermission(resource = "scrm_churn_warning", action = "read")
    @GetMapping("/rules/list")
    public OperationResponse<Page<ScrmChurnRuleEntity>> listRules(
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "priority")
                        .and(Sort.by(Sort.Direction.DESC, "createTime")));
        return OperationResponse.build(scrmChurnWarningService.listRules(riskLevel, enabled, keyword, pageable));
    }

    /**
     * 启用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_churn_warning", action = "update")
    @PostMapping("/rules/{id}/enable")
    public OperationResponse<ScrmChurnRuleEntity> enableRule(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmChurnWarningService.enableRule(id));
    }

    /**
     * 禁用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_churn_warning", action = "update")
    @PostMapping("/rules/{id}/disable")
    public OperationResponse<ScrmChurnRuleEntity> disableRule(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmChurnWarningService.disableRule(id));
    }

    // ============================================================
    // 扫描 /scan
    // ============================================================

    /**
     * 扫描单客户流失风险。
     *
     * @param customerId 客户 ID
     * @return 创建的预警 (无命中返回 null)
     * @throws ScrmException 客户不存在
     */
    @RequirePermission(resource = "scrm_churn_warning", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/scan/customer/{customerId}")
    public OperationResponse<ScrmChurnWarningEntity> scanCustomer(@PathVariable Long customerId)
            throws ScrmException {
        return OperationResponse.build(scrmChurnWarningService.scanCustomer(customerId));
    }

    /**
     * 批量扫描客户流失风险。
     *
     * @param dto 批量扫描请求 (daysBack / customerIds)
     * @return 扫描结果: {total, warned, failed}
     */
    @RequirePermission(resource = "scrm_churn_warning", action = "update")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/scan/batch")
    public OperationResponse<Map<String, Integer>> scanAll(@RequestBody(required = false) ScrmChurnScanDto dto) {
        return OperationResponse.build(scrmChurnWarningService.scanAll(dto));
    }

    /**
     * 按风险等级扫描客户。
     *
     * @param riskLevel 风险等级: HIGH / MEDIUM / LOW
     * @return 扫描结果: {total, warned, failed}
     * @throws ScrmException 风险等级非法
     */
    @RequirePermission(resource = "scrm_churn_warning", action = "update")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/scan/by-level/{riskLevel}")
    public OperationResponse<Map<String, Integer>> scanByLevel(@PathVariable String riskLevel)
            throws ScrmException {
        return OperationResponse.build(scrmChurnWarningService.scanByLevel(riskLevel));
    }

    // ============================================================
    // 预警 /warnings
    // ============================================================

    /**
     * 分页查询预警列表。
     *
     * @param status     预警状态过滤（可空）: ACTIVE / RESOLVED / IGNORED / ESCALATED
     * @param riskLevel  风险等级过滤（可空）: HIGH / MEDIUM / LOW
     * @param assigneeId 负责人 ID 过滤（可空）
     * @param startTime  检测时间起始 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime    检测时间截止 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param page       页码（从 0 开始, 默认 0）
     * @param size       每页大小（默认 20）
     * @return 预警分页结果 (按 detectedAt DESC)
     */
    @RequirePermission(resource = "scrm_churn_warning", action = "read")
    @GetMapping("/warnings/list")
    public OperationResponse<Page<ScrmChurnWarningEntity>> listWarnings(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String assigneeId,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "detectedAt"));
        return OperationResponse.build(scrmChurnWarningService.listWarnings(
                status, riskLevel, assigneeId, startTime, endTime, pageable));
    }

    /**
     * 查询预警详情。
     *
     * @param id 预警 ID
     * @return 预警详情
     * @throws ScrmException 预警不存在
     */
    @RequirePermission(resource = "scrm_churn_warning", action = "read")
    @GetMapping("/warnings/{id}")
    public OperationResponse<ScrmChurnWarningEntity> getWarning(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmChurnWarningService.getWarning(id));
    }

    /**
     * 标记预警为已解决。
     *
     * @param id     预警 ID
     * @param body   请求体, 可包含 resolutionNote 字段 (处理说明)
     * @return 更新后的预警
     * @throws ScrmException 预警不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_churn_warning", action = "update")
    @PostMapping("/warnings/{id}/resolve")
    public OperationResponse<ScrmChurnWarningEntity> resolveWarning(
            @PathVariable Long id,
            @RequestParam(required = false) Map<String, String> body) throws ScrmException {
        String note = body != null ? body.get("resolutionNote") : null;
        return OperationResponse.build(scrmChurnWarningService.resolveWarning(id, note));
    }

    /**
     * 忽略预警。
     *
     * @param id   预警 ID
     * @param body 请求体, 可包含 reason 字段 (忽略原因)
     * @return 更新后的预警
     * @throws ScrmException 预警不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_churn_warning", action = "update")
    @PostMapping("/warnings/{id}/ignore")
    public OperationResponse<ScrmChurnWarningEntity> ignoreWarning(
            @PathVariable Long id,
            @RequestParam(required = false) Map<String, String> body) throws ScrmException {
        String reason = body != null ? body.get("reason") : null;
        return OperationResponse.build(scrmChurnWarningService.ignoreWarning(id, reason));
    }

    /**
     * 升级预警处理。
     *
     * @param id   预警 ID
     * @param body 请求体, 可包含 reason 字段 (升级原因)
     * @return 更新后的预警
     * @throws ScrmException 预警不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_churn_warning", action = "update")
    @PostMapping("/warnings/{id}/escalate")
    public OperationResponse<ScrmChurnWarningEntity> escalateWarning(
            @PathVariable Long id,
            @RequestParam(required = false) Map<String, String> body) throws ScrmException {
        String reason = body != null ? body.get("reason") : null;
        return OperationResponse.build(scrmChurnWarningService.escalateWarning(id, reason));
    }

    // ============================================================
    // 挽留记录 /recoveries
    // ============================================================

    /**
     * 创建挽留记录。
     *
     * @param dto 挽留记录参数
     * @return 创建后的挽留记录
     * @throws ScrmException 关联预警不存在
     */
    @RequirePermission(resource = "scrm_churn_warning", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/recoveries")
    public OperationResponse<ScrmChurnRecoveryEntity> createRecovery(@Valid @RequestBody ScrmChurnRecoveryDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmChurnWarningService.createRecovery(dto));
    }

    /**
     * 分页查询挽留记录列表。
     *
     * @param warningId  预警 ID 过滤（可空, 优先级高）
     * @param customerId 客户 ID 过滤（可空, warningId 为空时生效）
     * @param page       页码（从 0 开始, 默认 0）
     * @param size       每页大小（默认 20）
     * @return 挽留记录分页结果 (按 actionExecutedAt DESC)
     */
    @RequirePermission(resource = "scrm_churn_warning", action = "read")
    @GetMapping("/recoveries/list")
    public OperationResponse<Page<ScrmChurnRecoveryEntity>> listRecoveries(
            @RequestParam(required = false) Long warningId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "actionExecutedAt"));
        return OperationResponse.build(scrmChurnWarningService.getRecoveries(warningId, customerId, pageable));
    }

    /**
     * 标记挽留记录为已激活, 并同步将关联预警标记为已解决。
     *
     * @param recoveryId 挽留记录 ID
     * @param body       请求体, 可包含 notes 字段 (备注)
     * @return 更新后的挽留记录
     * @throws ScrmException 挽留记录不存在
     */
    @RequirePermission(resource = "scrm_churn_warning", action = "update")
    @PostMapping("/recoveries/{recoveryId}/reactivate")
    public OperationResponse<ScrmChurnRecoveryEntity> markReactivated(
            @PathVariable Long recoveryId,
            @RequestParam(required = false) Map<String, String> body) throws ScrmException {
        String notes = body != null ? body.get("notes") : null;
        return OperationResponse.build(scrmChurnWarningService.markReactivated(recoveryId, notes));
    }

    // ============================================================
    // 统计 /stats
    // ============================================================

    /**
     * 流失统计概览: 各风险等级预警数、各状态预警数、解决率、激活率、平均风险分。
     *
     * @param startTime 检测时间起始 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   检测时间截止 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_churn_warning", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getChurnStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmChurnWarningService.getChurnStats(startTime, endTime));
    }

    /**
     * 高风险客户列表。
     *
     * @param page 页码（从 0 开始, 默认 0）
     * @param size 每页大小（默认 20）
     * @return 高风险客户列表 (按风险分降序)
     */
    @RequirePermission(resource = "scrm_churn_warning", action = "read")
    @GetMapping("/stats/at-risk")
    public OperationResponse<List<Map<String, Object>>> getAtRiskCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmChurnWarningService.getAtRiskCustomers(pageable));
    }

    /**
     * 挽留成功率统计。
     *
     * @param startTime 动作执行时间起始 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   动作执行时间截止 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 挽留成功率统计 {total, reactivated, rate}
     */
    @RequirePermission(resource = "scrm_churn_warning", action = "read")
    @GetMapping("/stats/recovery-rate")
    public OperationResponse<Map<String, Object>> getRecoveryRate(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmChurnWarningService.getRecoveryRate(startTime, endTime));
    }
}
