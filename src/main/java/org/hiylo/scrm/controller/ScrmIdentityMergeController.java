/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmIdentityMergeController.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmCustomerIdentityDto;
import org.hiylo.scrm.dto.ScrmIdentityMergeActionDto;
import org.hiylo.scrm.dto.ScrmIdentityMergeExecuteDto;
import org.hiylo.scrm.dto.ScrmIdentityMergeRuleDto;
import org.hiylo.scrm.dto.ScrmIdentityMergeTaskDto;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmCustomerIdentityEntity;
import org.hiylo.scrm.entity.ScrmIdentityMergeHistoryEntity;
import org.hiylo.scrm.entity.ScrmIdentityMergeRuleEntity;
import org.hiylo.scrm.entity.ScrmIdentityMergeTaskEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmIdentityMergeService;
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
 * SCRM 客户身份合并控制器。
 * <p>
 * 提供跨平台客户身份合并完整能力: 身份标识管理 (CRUD / 主身份 / 验证 / 按身份查找),
 * 合并任务 (CRUD / 审核 / 取消 / 执行 / 重试 / 建议), 合并规则 (CRUD / 启停 / 执行 / 批量执行),
 * 重复检测 (单/批量检测 / 匹配分数 / 潜在重复 / 重复报告), 合并历史 (查询 / 回滚 / 影响分析),
 * 统计分析 (合并 / 重复 / 规则 / 身份)。
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/identity-merge")
@RequiredArgsConstructor
public class ScrmIdentityMergeController {

    /** 身份合并服务 */
    private final ScrmIdentityMergeService scrmIdentityMergeService;

    // ============================================================
    // 身份标识管理
    // ============================================================

    /**
     * 添加客户身份标识。
     *
     * @param dto 身份参数
     * @return 创建后的身份
     * @throws ScrmException 参数非法 / 身份已存在
     */
    @RequirePermission(resource = "scrm_identity_merge", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/identities")
    public OperationResponse<ScrmCustomerIdentityEntity> addIdentity(@Valid @RequestBody ScrmCustomerIdentityDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmIdentityMergeService.addIdentity(dto));
    }

    /**
     * 移除客户身份标识 (软删除, 置为非活跃)。
     *
     * @param id 身份 ID
     * @return 空响应
     * @throws ScrmException 身份不存在
     */
    @RequirePermission(resource = "scrm_identity_merge", action = "delete")
    @DeleteMapping("/identities/{id}")
    public OperationResponse<Void> removeIdentity(@PathVariable Long id) throws ScrmException {
        scrmIdentityMergeService.removeIdentity(id);
        return OperationResponse.build();
    }

    /**
     * 查询身份详情。
     *
     * @param id 身份 ID
     * @return 身份详情
     * @throws ScrmException 身份不存在
     */
    @RequirePermission(resource = "scrm_identity_merge", action = "read")
    @GetMapping("/identities/{id}")
    public OperationResponse<ScrmCustomerIdentityEntity> getIdentity(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmIdentityMergeService.getIdentity(id));
    }

    /**
     * 分页查询客户身份。
     *
     * @param customerId   客户 ID 过滤（可空）
     * @param identityType 身份类型过滤（可空）
     * @param platform     平台过滤（可空）
     * @param page         页码（从 0 开始, 默认 0）
     * @param size         每页大小（默认 20）
     * @return 身份分页结果
     */
    @RequirePermission(resource = "scrm_identity_merge", action = "read")
    @GetMapping("/identities/list")
    public OperationResponse<Page<ScrmCustomerIdentityEntity>> listIdentities(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String identityType,
            @RequestParam(required = false) String platform,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "isPrimary", "createTime"));
        return OperationResponse.build(scrmIdentityMergeService.listIdentities(
                customerId, identityType, platform, pageable));
    }

    /**
     * 获取客户主身份。
     *
     * @param customerId 客户 ID
     * @return 主身份详情
     * @throws ScrmException 主身份不存在
     */
    @RequirePermission(resource = "scrm_identity_merge", action = "read")
    @GetMapping("/identities/primary/{customerId}")
    public OperationResponse<ScrmCustomerIdentityEntity> getPrimaryIdentity(@PathVariable Long customerId)
            throws ScrmException {
        return OperationResponse.build(scrmIdentityMergeService.getPrimaryIdentity(customerId));
    }

    /**
     * 设置客户主身份。
     *
     * @param customerId     客户 ID
     * @param identityType   身份类型
     * @param identityValue  身份值
     * @return 更新后的主身份
     * @throws ScrmException 身份不存在
     */
    @RequirePermission(resource = "scrm_identity_merge", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/identities/primary")
    public OperationResponse<ScrmCustomerIdentityEntity> setPrimaryIdentity(
            @RequestParam Long customerId,
            @RequestParam String identityType,
            @RequestParam String identityValue) throws ScrmException {
        return OperationResponse.build(scrmIdentityMergeService.setPrimaryIdentity(
                customerId, identityType, identityValue));
    }

    /**
     * 验证身份。
     *
     * @param id 身份 ID
     * @return 更新后的身份
     * @throws ScrmException 身份不存在
     */
    @RequirePermission(resource = "scrm_identity_merge", action = "update")
    @PostMapping("/identities/{id}/verify")
    public OperationResponse<ScrmCustomerIdentityEntity> verifyIdentity(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmIdentityMergeService.verifyIdentity(id));
    }

    /**
     * 按身份查找客户。
     *
     * @param identityType  身份类型
     * @param identityValue  身份值
     * @return 客户详情
     * @throws ScrmException 身份不存在 / 客户不存在
     */
    @RequirePermission(resource = "scrm_identity_merge", action = "read")
    @GetMapping("/identities/find")
    public OperationResponse<ScrmCustomerEntity> findCustomerByIdentity(
            @RequestParam String identityType,
            @RequestParam String identityValue) throws ScrmException {
        return OperationResponse.build(scrmIdentityMergeService.findCustomerByIdentity(identityType, identityValue));
    }

    /**
     * 分页查询全部身份 (跨客户)。
     *
     * @param identityType 身份类型过滤（可空）
     * @param platform     平台过滤（可空）
     * @param keyword      关键词过滤（可空, 前缀匹配身份值）
     * @param page         页码（从 0 开始, 默认 0）
     * @param size         每页大小（默认 20）
     * @return 身份分页结果
     */
    @RequirePermission(resource = "scrm_identity_merge", action = "read")
    @GetMapping("/identities/all")
    public OperationResponse<Page<ScrmCustomerIdentityEntity>> listAllIdentities(
            @RequestParam(required = false) String identityType,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmIdentityMergeService.listAllIdentities(
                identityType, platform, keyword, pageable));
    }

    // ============================================================
    // 合并任务管理
    // ============================================================

    /**
     * 创建合并任务。
     *
     * @param dto 任务参数
     * @return 创建后的任务
     * @throws ScrmException 参数非法 / 客户不存在 / 源目标相同
     */
    @RequirePermission(resource = "scrm_identity_merge", action = "create")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/tasks")
    public OperationResponse<ScrmIdentityMergeTaskEntity> createMergeTask(
            @Valid @RequestBody ScrmIdentityMergeTaskDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmIdentityMergeService.createMergeTask(dto));
    }

    /**
     * 查询合并任务详情。
     *
     * @param id 任务 ID
     * @return 任务详情
     * @throws ScrmException 任务不存在
     */
    @RequirePermission(resource = "scrm_identity_merge", action = "read")
    @GetMapping("/tasks/{id}")
    public OperationResponse<ScrmIdentityMergeTaskEntity> getMergeTask(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmIdentityMergeService.getMergeTask(id));
    }

    /**
     * 分页查询合并任务。
     *
     * @param status     状态过滤（可空）
     * @param mergeType  合并类型过滤（可空）
     * @param startTime  创建时间起始（可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss）
     * @param endTime    创建时间截止（可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss）
     * @param page       页码（从 0 开始, 默认 0）
     * @param size       每页大小（默认 20）
     * @return 任务分页结果
     */
    @RequirePermission(resource = "scrm_identity_merge", action = "read")
    @GetMapping("/tasks/list")
    public OperationResponse<Page<ScrmIdentityMergeTaskEntity>> listMergeTasks(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String mergeType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmIdentityMergeService.listMergeTasks(
                status, mergeType, startTime, endTime, pageable));
    }

    /**
     * 审核合并任务 (APPROVE / REJECT / CANCEL)。
     *
     * @param actionDto 审核动作
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_identity_merge", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/tasks/review")
    public OperationResponse<ScrmIdentityMergeTaskEntity> reviewMergeTask(
            @Valid @RequestBody ScrmIdentityMergeActionDto actionDto) throws ScrmException {
        return OperationResponse.build(scrmIdentityMergeService.reviewMergeTask(actionDto));
    }

    /**
     * 执行合并 (完整实现: 身份迁移 → 交易迁移 → 字段合并 → 标签合并 → 历史记录 → 停用源客户)。
     *
     * @param id          任务 ID
     * @param executeDto 执行参数 (含字段覆盖)
     * @return 合并历史
     * @throws ScrmException 任务不存在 / 状态非法 / 执行失败
     */
    @RequirePermission(resource = "scrm_identity_merge", action = "update")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/tasks/{id}/execute")
    public OperationResponse<ScrmIdentityMergeHistoryEntity> executeMerge(
            @PathVariable Long id,
            @RequestBody(required = false) ScrmIdentityMergeExecuteDto executeDto) throws ScrmException {
        if (executeDto == null) {
            executeDto = new ScrmIdentityMergeExecuteDto();
            executeDto.setTaskId(id);
        } else {
            executeDto.setTaskId(id);
        }
        return OperationResponse.build(scrmIdentityMergeService.executeMerge(executeDto));
    }

    /**
     * 取消合并任务。
     *
     * @param id     任务 ID
     * @param reason 取消原因（可空）
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_identity_merge", action = "update")
    @PostMapping("/tasks/{id}/cancel")
    public OperationResponse<ScrmIdentityMergeTaskEntity> cancelMergeTask(
            @PathVariable Long id,
            @RequestParam(required = false) String reason) throws ScrmException {
        return OperationResponse.build(scrmIdentityMergeService.cancelMergeTask(id, reason));
    }

    /**
     * 重试合并任务 (将 FAILED 状态任务重置为 APPROVED 后重新执行)。
     *
     * @param id 任务 ID
     * @return 合并历史
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_identity_merge", action = "update")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/tasks/{id}/retry")
    public OperationResponse<ScrmIdentityMergeHistoryEntity> retryMerge(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmIdentityMergeService.retryMerge(id));
    }

    /**
     * 获取合并建议 (检测可能的重复客户)。
     *
     * @param customerId 客户 ID
     * @return 合并建议列表
     * @throws ScrmException 客户不存在
     */
    @RequirePermission(resource = "scrm_identity_merge", action = "read")
    @GetMapping("/tasks/suggestions/{customerId}")
    public OperationResponse<List<Map<String, Object>>> getMergeSuggestions(@PathVariable Long customerId)
            throws ScrmException {
        return OperationResponse.build(scrmIdentityMergeService.getMergeSuggestions(customerId));
    }

    // ============================================================
    // 合并规则管理
    // ============================================================

    /**
     * 创建合并规则。
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_identity_merge", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/rules")
    public OperationResponse<ScrmIdentityMergeRuleEntity> createRule(@Valid @RequestBody ScrmIdentityMergeRuleDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmIdentityMergeService.createRule(dto));
    }

    /**
     * 更新合并规则（字段非空才覆盖）。
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_identity_merge", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/rules/{id}")
    public OperationResponse<ScrmIdentityMergeRuleEntity> updateRule(@PathVariable Long id,
                                                                      @RequestBody ScrmIdentityMergeRuleDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmIdentityMergeService.updateRule(id, dto));
    }

    /**
     * 删除合并规则。
     *
     * @param id 规则 ID
     * @return 空响应
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_identity_merge", action = "delete")
    @DeleteMapping("/rules/{id}")
    public OperationResponse<Void> deleteRule(@PathVariable Long id) throws ScrmException {
        scrmIdentityMergeService.deleteRule(id);
        return OperationResponse.build();
    }

    /**
     * 查询规则详情。
     *
     * @param id 规则 ID
     * @return 规则详情
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_identity_merge", action = "read")
    @GetMapping("/rules/{id}")
    public OperationResponse<ScrmIdentityMergeRuleEntity> getRule(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmIdentityMergeService.getRule(id));
    }

    /**
     * 分页查询规则。
     *
     * @param enabled 启用状态过滤（可空）
     * @param keyword 关键词过滤（可空, 匹配规则名称）
     * @param page    页码（从 0 开始, 默认 0）
     * @param size    每页大小（默认 20）
     * @return 规则分页结果
     */
    @RequirePermission(resource = "scrm_identity_merge", action = "read")
    @GetMapping("/rules/list")
    public OperationResponse<Page<ScrmIdentityMergeRuleEntity>> listRules(
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "priority", "createTime"));
        return OperationResponse.build(scrmIdentityMergeService.listRules(enabled, keyword, pageable));
    }

    /**
     * 启用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_identity_merge", action = "update")
    @PostMapping("/rules/{id}/enable")
    public OperationResponse<ScrmIdentityMergeRuleEntity> enableRule(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmIdentityMergeService.enableRule(id));
    }

    /**
     * 禁用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_identity_merge", action = "update")
    @PostMapping("/rules/{id}/disable")
    public OperationResponse<ScrmIdentityMergeRuleEntity> disableRule(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmIdentityMergeService.disableRule(id));
    }

    /**
     * 执行规则 (扫描所有客户 → 匹配 → 创建合并任务)。
     *
     * @param id 规则 ID
     * @return 创建的合并任务列表
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_identity_merge", action = "update")
    @RateLimit(capacity = 5, refillTokens = 5, refillPeriodSeconds = 60)
    @PostMapping("/rules/{id}/execute")
    public OperationResponse<List<ScrmIdentityMergeTaskEntity>> executeRule(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmIdentityMergeService.executeRule(id));
    }

    /**
     * 批量执行所有启用的规则。
     *
     * @return 各规则创建的任务数统计
     */
    @RequirePermission(resource = "scrm_identity_merge", action = "update")
    @RateLimit(capacity = 2, refillTokens = 2, refillPeriodSeconds = 60)
    @PostMapping("/rules/batch-execute")
    public OperationResponse<List<Map<String, Object>>> batchExecuteRules() {
        return OperationResponse.build(scrmIdentityMergeService.batchExecuteRules());
    }

    // ============================================================
    // 重复检测
    // ============================================================

    /**
     * 检测指定客户的重复。
     *
     * @param customerId 客户 ID
     * @return 重复列表
     * @throws ScrmException 客户不存在
     */
    @RequirePermission(resource = "scrm_identity_merge", action = "read")
    @PostMapping("/detection/detect/{customerId}")
    public OperationResponse<List<Map<String, Object>>> detectDuplicates(@PathVariable Long customerId)
            throws ScrmException {
        return OperationResponse.build(scrmIdentityMergeService.detectDuplicates(customerId));
    }

    /**
     * 批量检测重复 (扫描全部客户)。
     *
     * @return 重复检测结果列表
     */
    @RequirePermission(resource = "scrm_identity_merge", action = "read")
    @RateLimit(capacity = 5, refillTokens = 5, refillPeriodSeconds = 60)
    @PostMapping("/detection/batch-detect")
    public OperationResponse<List<Map<String, Object>>> batchDetectDuplicates() {
        return OperationResponse.build(scrmIdentityMergeService.batchDetectDuplicates());
    }

    /**
     * 计算两个客户的匹配分数。
     *
     * @param customer1Id 客户 1 ID
     * @param customer2Id 客户 2 ID
     * @return 匹配分数 (0~1)
     * @throws ScrmException 客户不存在
     */
    @RequirePermission(resource = "scrm_identity_merge", action = "read")
    @PostMapping("/detection/match-score")
    public OperationResponse<Map<String, Object>> calculateMatchScore(
            @RequestParam Long customer1Id,
            @RequestParam Long customer2Id) throws ScrmException {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("customer1Id", customer1Id);
        result.put("customer2Id", customer2Id);
        // 委派给 service: 通过 detectDuplicates 间接计算
        List<Map<String, Object>> dups = scrmIdentityMergeService.detectDuplicates(customer1Id);
        double score = 0.0;
        for (Map<String, Object> dup : dups) {
            if (customer2Id.equals(dup.get("customerId"))) {
                score = (Double) dup.get("matchScore");
                break;
            }
        }
        result.put("matchScore", score);
        return OperationResponse.build(result);
    }

    /**
     * 查找潜在重复客户。
     *
     * @param keyword 关键词（可空）
     * @param limit   返回数量上限（默认 50）
     * @return 潜在重复客户列表
     */
    @RequirePermission(resource = "scrm_identity_merge", action = "read")
    @GetMapping("/detection/potential")
    public OperationResponse<List<Map<String, Object>>> findPotentialDuplicates(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "50") int limit) {
        return OperationResponse.build(scrmIdentityMergeService.findPotentialDuplicates(keyword, limit));
    }

    /**
     * 重复报告。
     *
     * @return 报告 Map
     */
    @RequirePermission(resource = "scrm_identity_merge", action = "read")
    @GetMapping("/detection/report")
    public OperationResponse<Map<String, Object>> getDuplicateReport() {
        return OperationResponse.build(scrmIdentityMergeService.getDuplicateReport());
    }

    // ============================================================
    // 合并历史
    // ============================================================

    /**
     * 查询合并历史详情。
     *
     * @param id 历史 ID
     * @return 历史详情
     * @throws ScrmException 历史不存在
     */
    @RequirePermission(resource = "scrm_identity_merge", action = "read")
    @GetMapping("/history/{id}")
    public OperationResponse<ScrmIdentityMergeHistoryEntity> getHistory(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmIdentityMergeService.getHistory(id));
    }

    /**
     * 按任务 ID 查询合并历史。
     *
     * @param taskId 任务 ID
     * @return 历史详情
     * @throws ScrmException 历史不存在
     */
    @RequirePermission(resource = "scrm_identity_merge", action = "read")
    @GetMapping("/history/task/{taskId}")
    public OperationResponse<ScrmIdentityMergeHistoryEntity> getHistoryByTask(@PathVariable Long taskId)
            throws ScrmException {
        return OperationResponse.build(scrmIdentityMergeService.getHistoryByTask(taskId));
    }

    /**
     * 分页查询合并历史。
     *
     * @param customerId 客户 ID 过滤（可空, 匹配源或目标客户）
     * @param startTime  合并时间起始（可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss）
     * @param endTime    合并时间截止（可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss）
     * @param page       页码（从 0 开始, 默认 0）
     * @param size       每页大小（默认 20）
     * @return 历史分页结果
     */
    @RequirePermission(resource = "scrm_identity_merge", action = "read")
    @GetMapping("/history/list")
    public OperationResponse<Page<ScrmIdentityMergeHistoryEntity>> listHistory(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "mergedAt"));
        return OperationResponse.build(scrmIdentityMergeService.listHistory(customerId, startTime, endTime, pageable));
    }

    /**
     * 回滚合并。
     *
     * @param id     历史 ID
     * @param reason 回滚原因（可空）
     * @return 更新后的历史
     * @throws ScrmException 历史不存在 / 不可回滚
     */
    @RequirePermission(resource = "scrm_identity_merge", action = "update")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/history/{id}/rollback")
    public OperationResponse<ScrmIdentityMergeHistoryEntity> rollbackMerge(
            @PathVariable Long id,
            @RequestParam(required = false) String reason) throws ScrmException {
        return OperationResponse.build(scrmIdentityMergeService.rollbackMerge(id, reason));
    }

    /**
     * 合并影响分析。
     *
     * @param taskId 任务 ID
     * @return 影响分析 Map
     * @throws ScrmException 任务不存在
     */
    @RequirePermission(resource = "scrm_identity_merge", action = "read")
    @GetMapping("/history/impact/{taskId}")
    public OperationResponse<Map<String, Object>> getMergeImpact(@PathVariable Long taskId) throws ScrmException {
        return OperationResponse.build(scrmIdentityMergeService.getMergeImpact(taskId));
    }

    // ============================================================
    // 统计分析
    // ============================================================

    /**
     * 合并统计概览 (任务数 / 完成率 / 平均合并时间)。
     *
     * @param startTime 起始时间（可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss）
     * @param endTime   截止时间（可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss）
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_identity_merge", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getMergeStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmIdentityMergeService.getMergeStats(startTime, endTime));
    }

    /**
     * 重复统计 (重复对数 / 各匹配类型)。
     *
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_identity_merge", action = "read")
    @GetMapping("/stats/duplicates")
    public OperationResponse<Map<String, Object>> getDuplicateStats() {
        return OperationResponse.build(scrmIdentityMergeService.getDuplicateStats());
    }

    /**
     * 规则统计 (匹配数 / 合并数)。
     *
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_identity_merge", action = "read")
    @GetMapping("/stats/rules")
    public OperationResponse<Map<String, Object>> getRuleStats() {
        return OperationResponse.build(scrmIdentityMergeService.getRuleStats());
    }

    /**
     * 身份统计 (各类型 / 各平台 / 验证率)。
     *
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_identity_merge", action = "read")
    @GetMapping("/stats/identities")
    public OperationResponse<Map<String, Object>> getIdentityStats() {
        return OperationResponse.build(scrmIdentityMergeService.getIdentityStats());
    }
}
