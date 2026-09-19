/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBlacklistController.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmAppealDto;
import org.hiylo.scrm.dto.ScrmBlacklistCheckDto;
import org.hiylo.scrm.dto.ScrmBlacklistDto;
import org.hiylo.scrm.dto.ScrmBlacklistRuleDto;
import org.hiylo.scrm.dto.ScrmRiskAssessmentDto;
import org.hiylo.scrm.dto.ScrmRiskEventDto;
import org.hiylo.scrm.entity.ScrmBlacklistEntity;
import org.hiylo.scrm.entity.ScrmBlacklistRuleEntity;
import org.hiylo.scrm.entity.ScrmRiskEventEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmBlacklistService;
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
 * SCRM 黑名单风控管理控制器。
 * <p>
 * 提供风控名单管理、风控规则管理、风险事件管理、风险评估与多维统计接口。
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/risk")
@RequiredArgsConstructor
public class ScrmBlacklistController {

    /** 黑名单风控服务 */
    private final ScrmBlacklistService scrmBlacklistService;

    // ============================================================
    // 黑名单管理 /blacklist
    // ============================================================

    /**
     * 加入名单。
     *
     * @param dto 名单参数
     * @return 创建后的名单条目
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_risk", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/blacklist")
    public OperationResponse<ScrmBlacklistEntity> addToBlacklist(@Valid @RequestBody ScrmBlacklistDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmBlacklistService.addToBlacklist(dto));
    }

    /**
     * 从名单移出。
     *
     * @param id        名单条目 ID
     * @param reason    移除原因
     * @param removedBy 移除人 (可空, 缺省取当前用户)
     * @return 更新后的名单条目
     * @throws ScrmException 名单不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_risk", action = "delete")
    @DeleteMapping("/blacklist/{id}")
    public OperationResponse<ScrmBlacklistEntity> removeFromBlacklist(@PathVariable Long id,
                                                                       @RequestParam(required = false) String reason,
                                                                       @RequestParam(required = false) String removedBy)
            throws ScrmException {
        return OperationResponse.build(scrmBlacklistService.removeFromBlacklist(id, reason, removedBy));
    }

    /**
     * 查询名单详情。
     *
     * @param id 名单条目 ID
     * @return 名单条目
     * @throws ScrmException 名单不存在
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @GetMapping("/blacklist/{id}")
    public OperationResponse<ScrmBlacklistEntity> getBlacklist(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmBlacklistService.getBlacklist(id));
    }

    /**
     * 分页查询名单列表。
     *
     * @param listType   名单类型过滤（可空）
     * @param targetType 目标类型过滤（可空）
     * @param riskLevel  风险等级过滤（可空）
     * @param status     状态过滤（可空）
     * @param keyword    目标值/目标名称关键字（可空）
     * @param page       页码（从 0 开始, 默认 0）
     * @param size       每页大小（默认 20）
     * @return 名单分页结果 (按 updateTime DESC)
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @GetMapping("/blacklist/list")
    public OperationResponse<Page<ScrmBlacklistEntity>> listBlacklist(
            @RequestParam(required = false) String listType,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updateTime"));
        return OperationResponse.build(scrmBlacklistService.listBlacklist(listType,
                targetType, riskLevel, status, keyword, pageable));
    }

    /**
     * 检查目标是否在名单中。
     *
     * @param checkDto 检查参数
     * @return 检查结果
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @PostMapping("/blacklist/check")
    public OperationResponse<Map<String, Object>> checkBlacklist(@Valid @RequestBody ScrmBlacklistCheckDto checkDto)
            throws ScrmException {
        return OperationResponse.build(scrmBlacklistService.checkBlacklist(checkDto));
    }

    /**
     * 批量检查目标是否在名单中。
     *
     * @param targets 检查参数列表
     * @return 检查结果列表
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @PostMapping("/blacklist/batch-check")
    public OperationResponse<List<Map<String, Object>>> batchCheck(@RequestBody List<ScrmBlacklistCheckDto> targets) {
        return OperationResponse.build(scrmBlacklistService.batchCheck(targets));
    }

    /**
     * 按目标查询名单条目。
     *
     * @param targetType  目标类型
     * @param targetValue 目标值
     * @return 名单条目列表
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @GetMapping("/blacklist/by-target")
    public OperationResponse<List<ScrmBlacklistEntity>> getByTarget(@RequestParam String targetType,
                                                                     @RequestParam String targetValue) {
        return OperationResponse.build(scrmBlacklistService.getByTarget(targetType, targetValue));
    }

    /**
     * 查询已过期名单。
     *
     * @return 已过期名单列表
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @GetMapping("/blacklist/expired")
    public OperationResponse<List<ScrmBlacklistEntity>> getExpiredList() {
        return OperationResponse.build(scrmBlacklistService.getExpiredList());
    }

    /**
     * 查询即将到期名单。
     *
     * @param days 回溯天数（默认 7）
     * @return 即将到期名单列表
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @GetMapping("/blacklist/expiring")
    public OperationResponse<List<ScrmBlacklistEntity>> getExpiringSoon(
            @RequestParam(defaultValue = "7") int days) {
        return OperationResponse.build(scrmBlacklistService.getExpiringSoon(days));
    }

    /**
     * 发起申诉。
     *
     * @param appealDto 申诉参数
     * @return 更新后的名单条目
     * @throws ScrmException 名单不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_risk", action = "update")
    @PostMapping("/blacklist/appeal")
    public OperationResponse<ScrmBlacklistEntity> appeal(@Valid @RequestBody ScrmAppealDto appealDto)
            throws ScrmException {
        return OperationResponse.build(scrmBlacklistService.appeal(appealDto));
    }

    /**
     * 审核申诉。
     *
     * @param blacklistId 名单条目 ID
     * @param action      审核动作: APPROVED / REJECTED
     * @param reviewerId  审核人 ID (可空)
     * @param result      审核结果说明 (可空)
     * @return 更新后的名单条目
     * @throws ScrmException 名单不存在 / 动作非法
     */
    @RequirePermission(resource = "scrm_risk", action = "update")
    @PostMapping("/blacklist/review-appeal")
    public OperationResponse<ScrmBlacklistEntity> reviewAppeal(
            @RequestParam Long blacklistId,
            @RequestParam String action,
            @RequestParam(required = false) String reviewerId,
            @RequestParam(required = false) String result) throws ScrmException {
        return OperationResponse.build(scrmBlacklistService.reviewAppeal(blacklistId, action, reviewerId, result));
    }

    /**
     * 恢复已移除的名单条目。
     *
     * @param blacklistId 名单条目 ID
     * @param reason      恢复原因
     * @param restoredBy  恢复人 (可空)
     * @return 更新后的名单条目
     * @throws ScrmException 名单不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_risk", action = "update")
    @PostMapping("/blacklist/restore")
    public OperationResponse<ScrmBlacklistEntity> restore(
            @RequestParam Long blacklistId,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) String restoredBy) throws ScrmException {
        return OperationResponse.build(scrmBlacklistService.restore(blacklistId, reason, restoredBy));
    }

    /**
     * 延期名单到期日。
     *
     * @param blacklistId   名单条目 ID
     * @param newExpiryDate 新到期日期 (ISO 格式: yyyy-MM-dd)
     * @return 更新后的名单条目
     * @throws ScrmException 名单不存在 / 日期非法
     */
    @RequirePermission(resource = "scrm_risk", action = "update")
    @PostMapping("/blacklist/extend")
    public OperationResponse<ScrmBlacklistEntity> extendExpiry(
            @RequestParam Long blacklistId, @RequestParam LocalDate newExpiryDate) throws ScrmException {
        return OperationResponse.build(scrmBlacklistService.extendExpiry(blacklistId, newExpiryDate));
    }

    /**
     * 更新名单风险评分。
     *
     * @param blacklistId 名单条目 ID
     * @param score       风险评分 0-100
     * @return 更新后的名单条目
     * @throws ScrmException 名单不存在 / 评分越界
     */
    @RequirePermission(resource = "scrm_risk", action = "update")
    @PostMapping("/blacklist/score")
    public OperationResponse<ScrmBlacklistEntity> updateRiskScore(
            @RequestParam Long blacklistId, @RequestParam double score) throws ScrmException {
        return OperationResponse.build(scrmBlacklistService.updateRiskScore(blacklistId, score));
    }

    /**
     * 按客户分页查询名单。
     *
     * @param customerId 客户 ID
     * @param page       页码（从 0 开始, 默认 0）
     * @param size       每页大小（默认 20）
     * @return 名单分页结果
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @GetMapping("/blacklist/by-customer/{customerId}")
    public OperationResponse<Page<ScrmBlacklistEntity>> getBlacklistByCustomer(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updateTime"));
        return OperationResponse.build(scrmBlacklistService.getBlacklistByCustomer(customerId, pageable));
    }

    /**
     * 按风险等级分页查询名单。
     *
     * @param level 风险等级: LOW / MEDIUM / HIGH / CRITICAL
     * @param page  页码（从 0 开始, 默认 0）
     * @param size  每页大小（默认 20）
     * @return 名单分页结果
     * @throws ScrmException 风险等级非法
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @GetMapping("/blacklist/by-risk-level/{level}")
    public OperationResponse<Page<ScrmBlacklistEntity>> getBlacklistByRiskLevel(
            @PathVariable String level,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) throws ScrmException {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updateTime"));
        return OperationResponse.build(scrmBlacklistService.getBlacklistByRiskLevel(level, pageable));
    }

    /**
     * 各名单类型活跃数。
     *
     * @return 统计 Map
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @GetMapping("/blacklist/count")
    public OperationResponse<Map<String, Long>> getActiveCount() {
        return OperationResponse.build(scrmBlacklistService.getActiveCount());
    }

    /**
     * 批量导入名单。
     *
     * @param items 名单参数列表
     * @return 导入结果 {total, success, failed}
     */
    @RequirePermission(resource = "scrm_risk", action = "create")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/blacklist/import")
    public OperationResponse<Map<String, Integer>> importBlacklist(@RequestBody List<ScrmBlacklistDto> items) {
        return OperationResponse.build(scrmBlacklistService.importBlacklist(items));
    }

    /**
     * 导出名单。
     *
     * @param listType 名单类型 (可空, 为空导出全部)
     * @return 名单条目列表
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @GetMapping("/blacklist/export")
    public OperationResponse<List<ScrmBlacklistEntity>> exportBlacklist(
            @RequestParam(required = false) String listType) {
        return OperationResponse.build(scrmBlacklistService.exportBlacklist(listType));
    }

    // ============================================================
    // 风控规则管理 /rules
    // ============================================================

    /**
     * 创建风控规则。
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException 参数非法 / 编码重复
     */
    @RequirePermission(resource = "scrm_risk", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/rules")
    public OperationResponse<ScrmBlacklistRuleEntity> createRule(@Valid @RequestBody ScrmBlacklistRuleDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmBlacklistService.createRule(dto));
    }

    /**
     * 更新风控规则。
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 参数非法 / 编码重复
     */
    @RequirePermission(resource = "scrm_risk", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/rules/{id}")
    public OperationResponse<ScrmBlacklistRuleEntity> updateRule(@PathVariable Long id,
                                                                   @RequestBody ScrmBlacklistRuleDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmBlacklistService.updateRule(id, dto));
    }

    /**
     * 删除风控规则。
     *
     * @param id 规则 ID
     * @return 空响应
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_risk", action = "delete")
    @DeleteMapping("/rules/{id}")
    public OperationResponse<Void> deleteRule(@PathVariable Long id) throws ScrmException {
        scrmBlacklistService.deleteRule(id);
        return OperationResponse.build();
    }

    /**
     * 查询规则详情。
     *
     * @param id 规则 ID
     * @return 规则实体
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @GetMapping("/rules/{id}")
    public OperationResponse<ScrmBlacklistRuleEntity> getRule(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmBlacklistService.getRule(id));
    }

    /**
     * 按规则代码查询规则。
     *
     * @param code 规则代码
     * @return 规则实体
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @GetMapping("/rules/code/{code}")
    public OperationResponse<ScrmBlacklistRuleEntity> getRuleByCode(
            @PathVariable String code) throws ScrmException {
        return OperationResponse.build(scrmBlacklistService.getRuleByCode(code));
    }

    /**
     * 分页查询规则列表。
     *
     * @param ruleType     规则类型过滤（可空）
     * @param riskCategory 风险类别过滤（可空）
     * @param severity     严重程度过滤（可空）
     * @param enabled      启用状态过滤（可空）
     * @param keyword      名称/代码关键字（可空）
     * @param page         页码（从 0 开始, 默认 0）
     * @param size         每页大小（默认 20）
     * @return 规则分页结果 (按 priority ASC, updateTime DESC)
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @GetMapping("/rules/list")
    public OperationResponse<Page<ScrmBlacklistRuleEntity>> listRules(
            @RequestParam(required = false) String ruleType,
            @RequestParam(required = false) String riskCategory,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "priority"));
        return OperationResponse.build(scrmBlacklistService.listRules(ruleType,
                riskCategory, severity, enabled, keyword, pageable));
    }

    /**
     * 启用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_risk", action = "update")
    @PostMapping("/rules/{id}/enable")
    public OperationResponse<ScrmBlacklistRuleEntity> enableRule(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmBlacklistService.enableRule(id));
    }

    /**
     * 禁用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_risk", action = "update")
    @PostMapping("/rules/{id}/disable")
    public OperationResponse<ScrmBlacklistRuleEntity> disableRule(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmBlacklistService.disableRule(id));
    }

    /**
     * 评估单条规则。
     *
     * @param ruleId  规则 ID
     * @param context 评估上下文
     * @return 评估结果
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @PostMapping("/rules/evaluate")
    public OperationResponse<Map<String, Object>> evaluateRule(
            @RequestParam Long ruleId, @RequestBody Map<String, Object> context) throws ScrmException {
        return OperationResponse.build(scrmBlacklistService.evaluateRule(ruleId, context));
    }

    /**
     * 评估所有启用规则。
     *
     * @param assessmentDto 风险评估参数
     * @return 评估汇总结果
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @PostMapping("/rules/evaluate-all")
    public OperationResponse<Map<String, Object>> evaluateAllRules(
            @Valid @RequestBody ScrmRiskAssessmentDto assessmentDto)
            throws ScrmException {
        return OperationResponse.build(scrmBlacklistService.evaluateAllRules(assessmentDto));
    }

    /**
     * 按风险类别分页查询规则。
     *
     * @param category 风险类别
     * @param page     页码（从 0 开始, 默认 0）
     * @param size     每页大小（默认 20）
     * @return 规则分页结果
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @GetMapping("/rules/by-category/{category}")
    public OperationResponse<Page<ScrmBlacklistRuleEntity>> getRulesByCategory(
            @PathVariable String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "priority"));
        return OperationResponse.build(scrmBlacklistService.getRulesByCategory(category, pageable));
    }

    /**
     * 按适用模块分页查询规则。
     *
     * @param module 模块名
     * @param page   页码（从 0 开始, 默认 0）
     * @param size   每页大小（默认 20）
     * @return 规则分页结果
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @GetMapping("/rules/by-module")
    public OperationResponse<Page<ScrmBlacklistRuleEntity>> getRulesByModule(
            @RequestParam String module,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "priority"));
        return OperationResponse.build(scrmBlacklistService.getRulesByModule(module, pageable));
    }

    /**
     * 更新规则统计。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_risk", action = "update")
    @PostMapping("/rules/{id}/stats")
    public OperationResponse<ScrmBlacklistRuleEntity> updateRuleStats(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmBlacklistService.updateRuleStats(id));
    }

    /**
     * 标记规则误报。
     *
     * @param ruleId  规则 ID
     * @param eventId 事件 ID (可空)
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_risk", action = "update")
    @PostMapping("/rules/false-positive")
    public OperationResponse<ScrmBlacklistRuleEntity> markRuleFalsePositive(
            @RequestParam Long ruleId, @RequestParam(required = false) Long eventId) throws ScrmException {
        return OperationResponse.build(scrmBlacklistService.markFalsePositive(ruleId, eventId));
    }

    /**
     * 查询规则准确率。
     *
     * @param id 规则 ID
     * @return 准确率 Map
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @GetMapping("/rules/accuracy")
    public OperationResponse<Map<String, Object>> getRuleAccuracy(@RequestParam Long id) throws ScrmException {
        return OperationResponse.build(scrmBlacklistService.getRuleAccuracy(id));
    }

    /**
     * 复制规则。
     *
     * @param id      源规则 ID
     * @param newCode 新规则代码
     * @return 复制后的规则
     * @throws ScrmException 规则不存在 / 编码重复
     */
    @RequirePermission(resource = "scrm_risk", action = "create")
    @PostMapping("/rules/duplicate")
    public OperationResponse<ScrmBlacklistRuleEntity> duplicateRule(
            @RequestParam Long id, @RequestParam String newCode) throws ScrmException {
        return OperationResponse.build(scrmBlacklistService.duplicateRule(id, newCode));
    }

    /**
     * 查询规则触发历史。
     *
     * @param ruleId 规则 ID
     * @param page   页码（从 0 开始, 默认 0）
     * @param size   每页大小（默认 20）
     * @return 事件分页结果
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @GetMapping("/rules/trigger-history/{ruleId}")
    public OperationResponse<Page<ScrmRiskEventEntity>> getRuleTriggerHistory(
            @PathVariable Long ruleId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "triggerTime"));
        return OperationResponse.build(scrmBlacklistService.getRuleTriggerHistory(ruleId, pageable));
    }

    // ============================================================
    // 风险事件管理 /events
    // ============================================================

    /**
     * 创建风险事件。
     *
     * @param dto 事件参数
     * @return 创建后的事件
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_risk", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/events")
    public OperationResponse<ScrmRiskEventEntity> createEvent(@Valid @RequestBody ScrmRiskEventDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmBlacklistService.createEvent(dto));
    }

    /**
     * 查询事件详情。
     *
     * @param id 事件 ID
     * @return 事件实体
     * @throws ScrmException 事件不存在
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @GetMapping("/events/{id}")
    public OperationResponse<ScrmRiskEventEntity> getEvent(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmBlacklistService.getEvent(id));
    }

    /**
     * 按事件编号查询事件。
     *
     * @param eventNo 事件编号
     * @return 事件实体
     * @throws ScrmException 事件不存在
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @GetMapping("/events/by-no/{eventNo}")
    public OperationResponse<ScrmRiskEventEntity> getEventByNo(@PathVariable String eventNo) throws ScrmException {
        return OperationResponse.build(scrmBlacklistService.getEventByNo(eventNo));
    }

    /**
     * 分页查询事件列表。
     *
     * @param ruleId       规则 ID 过滤（可空）
     * @param customerId   客户 ID 过滤（可空）
     * @param riskCategory 风险类别过滤（可空）
     * @param riskLevel    风险等级过滤（可空）
     * @param status       状态过滤（可空）
     * @param startTime    触发起始时间（可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss）
     * @param endTime      触发截止时间（可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss）
     * @param page         页码（从 0 开始, 默认 0）
     * @param size         每页大小（默认 20）
     * @return 事件分页结果 (按 triggerTime DESC)
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @GetMapping("/events/list")
    public OperationResponse<Page<ScrmRiskEventEntity>> listEvents(
            @RequestParam(required = false) Long ruleId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String riskCategory,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "triggerTime"));
        return OperationResponse.build(scrmBlacklistService.listEvents(ruleId, customerId, riskCategory, riskLevel,
                status, startTime, endTime, pageable));
    }

    /**
     * 触发风险事件。
     *
     * @param ruleId      规则 ID
     * @param customerId  客户 ID (可空)
     * @param targetType  目标类型
     * @param targetValue 目标值
     * @param triggerData JSON 触发数据 (可空)
     * @return 创建后的事件
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_risk", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/events/fire")
    public OperationResponse<ScrmRiskEventEntity> fireEvent(
            @RequestParam Long ruleId,
            @RequestParam(required = false) Long customerId,
            @RequestParam String targetType,
            @RequestParam String targetValue,
            @RequestParam(required = false) String triggerData) throws ScrmException {
        return OperationResponse.build(scrmBlacklistService.fireEvent(ruleId,
                customerId, targetType, targetValue, triggerData));
    }

    /**
     * 分配事件处理人。
     *
     * @param id         事件 ID
     * @param assigneeId 处理人 ID
     * @return 更新后的事件
     * @throws ScrmException 事件不存在
     */
    @RequirePermission(resource = "scrm_risk", action = "update")
    @PostMapping("/events/assign")
    public OperationResponse<ScrmRiskEventEntity> assignEvent(
            @RequestParam Long id, @RequestParam String assigneeId) throws ScrmException {
        return OperationResponse.build(scrmBlacklistService.assignEvent(id, assigneeId));
    }

    /**
     * 调查事件。
     *
     * @param id             事件 ID
     * @param investigatorId 调查人 ID
     * @param notes          调查备注 (可空)
     * @return 更新后的事件
     * @throws ScrmException 事件不存在
     */
    @RequirePermission(resource = "scrm_risk", action = "update")
    @PostMapping("/events/investigate")
    public OperationResponse<ScrmRiskEventEntity> investigate(
            @RequestParam Long id,
            @RequestParam String investigatorId,
            @RequestParam(required = false) String notes) throws ScrmException {
        return OperationResponse.build(scrmBlacklistService.investigate(id, investigatorId, notes));
    }

    /**
     * 确认风险。
     *
     * @param id          事件 ID
     * @param confirmedBy 确认人 ID (可空)
     * @return 更新后的事件
     * @throws ScrmException 事件不存在
     */
    @RequirePermission(resource = "scrm_risk", action = "update")
    @PostMapping("/events/confirm")
    public OperationResponse<ScrmRiskEventEntity> confirmRisk(
            @RequestParam Long id, @RequestParam(required = false) String confirmedBy) throws ScrmException {
        return OperationResponse.build(scrmBlacklistService.confirmRisk(id, confirmedBy));
    }

    /**
     * 标记事件为误报。
     *
     * @param id     事件 ID
     * @param reason 误报原因 (可空)
     * @return 更新后的事件
     * @throws ScrmException 事件不存在
     */
    @RequirePermission(resource = "scrm_risk", action = "update")
    @PostMapping("/events/false-positive")
    public OperationResponse<ScrmRiskEventEntity> markEventFalsePositive(
            @RequestParam Long id, @RequestParam(required = false) String reason) throws ScrmException {
        return OperationResponse.build(scrmBlacklistService.markFalsePositive(id, reason));
    }

    /**
     * 解决事件。
     *
     * @param id         事件 ID
     * @param resolution 处理结果 (可空)
     * @param resolvedBy 解决人 ID (可空)
     * @return 更新后的事件
     * @throws ScrmException 事件不存在
     */
    @RequirePermission(resource = "scrm_risk", action = "update")
    @PostMapping("/events/resolve")
    public OperationResponse<ScrmRiskEventEntity> resolveEvent(
            @RequestParam Long id,
            @RequestParam(required = false) String resolution,
            @RequestParam(required = false) String resolvedBy) throws ScrmException {
        return OperationResponse.build(scrmBlacklistService.resolveEvent(id, resolution, resolvedBy));
    }

    /**
     * 升级事件。
     *
     * @param id           事件 ID
     * @param escalatedTo  升级给
     * @return 更新后的事件
     * @throws ScrmException 事件不存在
     */
    @RequirePermission(resource = "scrm_risk", action = "update")
    @PostMapping("/events/escalate")
    public OperationResponse<ScrmRiskEventEntity> escalateEvent(
            @RequestParam Long id, @RequestParam String escalatedTo) throws ScrmException {
        return OperationResponse.build(scrmBlacklistService.escalateEvent(id, escalatedTo));
    }

    /**
     * 查询待处理事件。
     *
     * @param page 页码（从 0 开始, 默认 0）
     * @param size 每页大小（默认 20）
     * @return 事件分页结果
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @GetMapping("/events/open")
    public OperationResponse<Page<ScrmRiskEventEntity>> getOpenEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "triggerTime"));
        return OperationResponse.build(scrmBlacklistService.getOpenEvents(pageable));
    }

    /**
     * 查询严重事件 (HIGH / CRITICAL)。
     *
     * @param page 页码（从 0 开始, 默认 0）
     * @param size 每页大小（默认 20）
     * @return 事件分页结果
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @GetMapping("/events/critical")
    public OperationResponse<Page<ScrmRiskEventEntity>> getCriticalEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "triggerTime"));
        return OperationResponse.build(scrmBlacklistService.getCriticalEvents(pageable));
    }

    /**
     * 按客户分页查询事件。
     *
     * @param customerId 客户 ID
     * @param page       页码（从 0 开始, 默认 0）
     * @param size       每页大小（默认 20）
     * @return 事件分页结果
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @GetMapping("/events/by-customer/{customerId}")
    public OperationResponse<Page<ScrmRiskEventEntity>> getEventsByCustomer(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "triggerTime"));
        return OperationResponse.build(scrmBlacklistService.getEventsByCustomer(customerId, pageable));
    }

    /**
     * 按规则分页查询事件。
     *
     * @param ruleId 规则 ID
     * @param page   页码（从 0 开始, 默认 0）
     * @param size   每页大小（默认 20）
     * @return 事件分页结果
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @GetMapping("/events/by-rule/{ruleId}")
    public OperationResponse<Page<ScrmRiskEventEntity>> getEventsByRule(
            @PathVariable Long ruleId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "triggerTime"));
        return OperationResponse.build(scrmBlacklistService.getEventsByRule(ruleId, pageable));
    }

    /**
     * 事件时间线。
     *
     * @param eventId 事件 ID
     * @return 事件列表
     * @throws ScrmException 事件不存在
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @GetMapping("/events/timeline/{eventId}")
    public OperationResponse<List<ScrmRiskEventEntity>> getEventTimeline(@PathVariable Long eventId)
            throws ScrmException {
        return OperationResponse.build(scrmBlacklistService.getEventTimeline(eventId));
    }

    /**
     * 查询相关事件。
     *
     * @param eventId 事件 ID
     * @return 相关事件列表
     * @throws ScrmException 事件不存在
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @GetMapping("/events/related/{eventId}")
    public OperationResponse<List<ScrmRiskEventEntity>> getRelatedEvents(@PathVariable Long eventId)
            throws ScrmException {
        return OperationResponse.build(scrmBlacklistService.getRelatedEvents(eventId));
    }

    /**
     * 执行事件动作。
     *
     * @param eventId 事件 ID
     * @return 更新后的事件
     * @throws ScrmException 事件不存在
     */
    @RequirePermission(resource = "scrm_risk", action = "update")
    @PostMapping("/events/execute")
    public OperationResponse<ScrmRiskEventEntity> executeAction(@RequestParam Long eventId) throws ScrmException {
        return OperationResponse.build(scrmBlacklistService.executeAction(eventId));
    }

    /**
     * 批量解决事件。
     *
     * @param eventIds  事件 ID 列表
     * @param resolution 处理结果 (可空)
     * @param resolvedBy 解决人 ID (可空)
     * @return 批量结果 {total, success, failed}
     */
    @RequirePermission(resource = "scrm_risk", action = "update")
    @PostMapping("/events/batch-resolve")
    public OperationResponse<Map<String, Integer>> batchResolve(
            @RequestBody List<Long> eventIds,
            @RequestParam(required = false) String resolution,
            @RequestParam(required = false) String resolvedBy) {
        return OperationResponse.build(scrmBlacklistService.batchResolve(eventIds, resolution, resolvedBy));
    }

    // ============================================================
    // 风险评估 /assessment
    // ============================================================

    /**
     * 综合风险评估。
     *
     * @param assessmentDto 评估参数
     * @return 评估结果
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @PostMapping("/assessment/assess")
    public OperationResponse<Map<String, Object>> assessRisk(@Valid @RequestBody ScrmRiskAssessmentDto assessmentDto)
            throws ScrmException {
        return OperationResponse.build(scrmBlacklistService.assessRisk(assessmentDto));
    }

    /**
     * 客户风险评分。
     *
     * @param customerId 客户 ID
     * @return 风险评分 Map
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @GetMapping("/assessment/customer-score/{customerId}")
    public OperationResponse<Map<String, Object>> getCustomerRiskScore(@PathVariable Long customerId) {
        return OperationResponse.build(scrmBlacklistService.getCustomerRiskScore(customerId));
    }

    /**
     * 目标风险评分。
     *
     * @param targetType  目标类型
     * @param targetValue 目标值
     * @return 风险评分 Map
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @GetMapping("/assessment/target-score")
    public OperationResponse<Map<String, Object>> getTargetRiskScore(
            @RequestParam String targetType, @RequestParam String targetValue) {
        return OperationResponse.build(scrmBlacklistService.getTargetRiskScore(targetType, targetValue));
    }

    /**
     * 计算风险分 (加权计算)。
     *
     * @param factors 风险因子 Map {因子名: 因子分值(0-100)}
     * @return 评分结果
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @PostMapping("/assessment/calculate")
    public OperationResponse<Map<String, Object>> calculateRiskScore(@RequestBody Map<String, Double> factors) {
        return OperationResponse.build(scrmBlacklistService.calculateRiskScore(factors));
    }

    /**
     * 获取目标风险因子。
     *
     * @param targetType  目标类型
     * @param targetValue 目标值
     * @return 风险因子 Map
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @GetMapping("/assessment/factors")
    public OperationResponse<Map<String, Object>> getRiskFactors(
            @RequestParam String targetType, @RequestParam String targetValue) {
        return OperationResponse.build(scrmBlacklistService.getRiskFactors(targetType, targetValue));
    }

    /**
     * 风险历史 (按月聚合)。
     *
     * @param customerId 客户 ID
     * @param months     回溯月数（默认 6）
     * @return 风险历史列表
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @GetMapping("/assessment/history/{customerId}")
    public OperationResponse<List<Map<String, Object>>> getRiskHistory(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "6") int months) {
        return OperationResponse.build(scrmBlacklistService.getRiskHistory(customerId, months));
    }

    // ============================================================
    // 统计 /stats
    // ============================================================

    /**
     * 风险概览。
     *
     * @return 概览 Map
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getRiskOverview() {
        return OperationResponse.build(scrmBlacklistService.getRiskOverview());
    }

    /**
     * 名单统计。
     *
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @GetMapping("/stats/blacklist")
    public OperationResponse<Map<String, Object>> getBlacklistStats() {
        return OperationResponse.build(scrmBlacklistService.getBlacklistStats());
    }

    /**
     * 风险统计。
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @GetMapping("/stats/risk")
    public OperationResponse<Map<String, Object>> getRiskStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmBlacklistService.getRiskStats(startTime, endTime));
    }

    /**
     * 规则表现统计。
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @GetMapping("/stats/rule-performance")
    public OperationResponse<Map<String, Object>> getRulePerformanceStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmBlacklistService.getRulePerformanceStats(startTime, endTime));
    }

    /**
     * 风险趋势。
     *
     * @param months 回溯月数（默认 12）
     * @return 趋势结果 Map
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @GetMapping("/stats/trend")
    public OperationResponse<Map<String, Object>> getRiskTrend(@RequestParam(defaultValue = "12") int months) {
        return OperationResponse.build(scrmBlacklistService.getRiskTrend(months));
    }

    /**
     * 触发最多的规则 Top N。
     *
     * @param limit 返回条数（默认 10）
     * @return 规则列表
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @GetMapping("/stats/top-rules")
    public OperationResponse<List<ScrmBlacklistRuleEntity>> getTopRiskRules(
            @RequestParam(defaultValue = "10") int limit) {
        return OperationResponse.build(scrmBlacklistService.getTopRiskRules(limit));
    }

    /**
     * 高风险客户 Top N。
     *
     * @param limit 返回条数（默认 10）
     * @return 高风险客户列表
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @GetMapping("/stats/top-customers")
    public OperationResponse<List<Map<String, Object>>> getTopRiskCustomers(
            @RequestParam(defaultValue = "10") int limit) {
        return OperationResponse.build(scrmBlacklistService.getTopRiskCustomers(limit));
    }

    /**
     * 误报率统计。
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 误报率 Map
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @GetMapping("/stats/false-positive-rate")
    public OperationResponse<Map<String, Object>> getFalsePositiveRate(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmBlacklistService.getFalsePositiveRate(startTime, endTime));
    }

    /**
     * 平均处理时间。
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 平均处理时间 Map
     */
    @RequirePermission(resource = "scrm_risk", action = "read")
    @GetMapping("/stats/resolution-time")
    public OperationResponse<Map<String, Object>> getAverageResolutionTime(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmBlacklistService.getAverageResolutionTime(startTime, endTime));
    }
}
