/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerTagController.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmCustomerTagDto;
import org.hiylo.scrm.dto.ScrmTagCustomerDto;
import org.hiylo.scrm.dto.ScrmTagRuleDto;
import org.hiylo.scrm.dto.ScrmTagRuleEvaluateDto;
import org.hiylo.scrm.dto.ScrmTagRuleTestDto;
import org.hiylo.scrm.entity.ScrmCustomerTagEntity;
import org.hiylo.scrm.entity.ScrmTagCustomerEntity;
import org.hiylo.scrm.entity.ScrmTagRuleEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmCustomerTagService;
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

import java.util.List;
import java.util.Map;

/**
 * SCRM 客户标签画像管理控制器。
 * <p>
 * 提供客户标签定义 CRUD / 启用禁用, 客户打标 (单条 / 批量) / 去标 / 按标签查客户 /
 * 查客户标签, 标签自动规则 CRUD / 启用禁用 / 规则测试 / 手动评估 / 批量执行全部规则接口。
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/customer-tags")
@RequiredArgsConstructor
public class ScrmCustomerTagController {

    /** 客户标签画像服务 */
    private final ScrmCustomerTagService scrmCustomerTagService;

    // ============================================================
    // 标签管理 Tag CRUD
    // ============================================================

    /**
     * 创建客户标签定义。
     *
     * @param dto 标签参数
     * @return 创建后的标签
     * @throws ScrmException 参数非法 / 标签编码重复
     */
    @RequirePermission(resource = "scrm_customer_tag", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping
    public OperationResponse<ScrmCustomerTagEntity> createTag(@Valid @RequestBody ScrmCustomerTagDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerTagService.createTag(dto));
    }

    /**
     * 更新客户标签定义。
     *
     * @param id  标签 ID
     * @param dto 标签参数
     * @return 更新后的标签
     * @throws ScrmException 标签不存在 / 参数非法 / 标签编码重复
     */
    @RequirePermission(resource = "scrm_customer_tag", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/{id}")
    public OperationResponse<ScrmCustomerTagEntity> updateTag(@PathVariable Long id,
                                                                @RequestBody ScrmCustomerTagDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerTagService.updateTag(id, dto));
    }

    /**
     * 删除客户标签定义 (级联清理客户-标签关联)。
     *
     * @param id 标签 ID
     * @return 空响应
     * @throws ScrmException 标签不存在 / 仍有规则引用
     */
    @RequirePermission(resource = "scrm_customer_tag", action = "delete")
    @DeleteMapping("/{id}")
    public OperationResponse<Void> deleteTag(@PathVariable Long id) throws ScrmException {
        scrmCustomerTagService.deleteTag(id);
        return OperationResponse.build();
    }

    /**
     * 查询标签详情。
     *
     * @param id 标签 ID
     * @return 标签详情
     * @throws ScrmException 标签不存在
     */
    @RequirePermission(resource = "scrm_customer_tag", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmCustomerTagEntity> getTag(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCustomerTagService.getTag(id));
    }

    /**
     * 分页查询标签定义列表。
     *
     * @param groupId  分组 ID 过滤（可空）
     * @param tagType  标签类型过滤（可空）
     * @param category 标签分类过滤（可空）
     * @param enabled  启用状态过滤（可空, null=全部）
     * @param keyword  关键字过滤（按 tagName / tagCode / description 模糊匹配, 可空）
     * @param page     页码（从 0 开始, 默认 0）
     * @param size     每页大小（默认 20）
     * @return 标签分页结果 (按 displayOrder ASC, createTime DESC)
     */
    @RequirePermission(resource = "scrm_customer_tag", action = "read")
    @GetMapping("/list")
    public OperationResponse<Page<ScrmCustomerTagEntity>> listTags(
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) String tagType,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmCustomerTagService.listTags(
                groupId, tagType, category, enabled, keyword, pageable));
    }

    /**
     * 启用标签。
     *
     * @param id 标签 ID
     * @return 更新后的标签
     * @throws ScrmException 标签不存在
     */
    @RequirePermission(resource = "scrm_customer_tag", action = "update")
    @PostMapping("/{id}/enable")
    public OperationResponse<ScrmCustomerTagEntity> enableTag(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCustomerTagService.enableTag(id));
    }

    /**
     * 禁用标签。
     *
     * @param id 标签 ID
     * @return 更新后的标签
     * @throws ScrmException 标签不存在
     */
    @RequirePermission(resource = "scrm_customer_tag", action = "update")
    @PostMapping("/{id}/disable")
    public OperationResponse<ScrmCustomerTagEntity> disableTag(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCustomerTagService.disableTag(id));
    }

    // ============================================================
    // 客户打标 Customer Tag Assignment
    // ============================================================

    /**
     * 为客户打标 (单条)。
     *
     * @param dto 打标参数 (customerId + tagId + tagValue + 来源 + 打标人)
     * @return 创建或更新后的客户标签关联
     * @throws ScrmException 标签不存在或已禁用
     */
    @RequirePermission(resource = "scrm_customer_tag", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/assign")
    public OperationResponse<ScrmTagCustomerEntity> assignTag(@Valid @RequestBody ScrmTagCustomerDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerTagService.assignTag(dto));
    }

    /**
     * 批量打标。
     *
     * @param dtos 打标参数列表
     * @return 成功打标数
     * @throws ScrmException 列表为空
     */
    @RequirePermission(resource = "scrm_customer_tag", action = "update")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/batch-assign")
    public OperationResponse<Integer> batchAssignTags(@RequestBody List<ScrmTagCustomerDto> dtos)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerTagService.batchAssignTags(dtos));
    }

    /**
     * 移除客户标签 (去标)。
     *
     * @param customerId 客户 ID
     * @param tagId      标签 ID
     * @return 空响应
     * @throws ScrmException 关联不存在 / 自动标签不可手动去标
     */
    @RequirePermission(resource = "scrm_customer_tag", action = "update")
    @DeleteMapping("/assign/{customerId}/{tagId}")
    public OperationResponse<Void> removeTag(@PathVariable Long customerId, @PathVariable Long tagId)
            throws ScrmException {
        scrmCustomerTagService.removeTag(customerId, tagId);
        return OperationResponse.build();
    }

    /**
     * 查询客户的所有标签关联 (查客户标签)。
     *
     * @param customerId 客户 ID
     * @return 标签关联列表
     */
    @RequirePermission(resource = "scrm_customer_tag", action = "read")
    @GetMapping("/customer/{customerId}")
    public OperationResponse<List<ScrmTagCustomerEntity>> getCustomerTags(@PathVariable Long customerId) {
        return OperationResponse.build(scrmCustomerTagService.getCustomerTags(customerId));
    }

    /**
     * 分页查询标签下的客户 (按标签查客户)。
     *
     * @param tagId 标签 ID
     * @param page  页码（从 0 开始, 默认 0）
     * @param size  每页大小（默认 20）
     * @return 客户关联分页结果
     */
    @RequirePermission(resource = "scrm_customer_tag", action = "read")
    @GetMapping("/{tagId}/customers")
    public OperationResponse<Page<ScrmTagCustomerEntity>> getTagCustomers(
            @PathVariable Long tagId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmCustomerTagService.getTagCustomers(tagId, pageable));
    }

    // ============================================================
    // 规则管理 Rule CRUD
    // ============================================================

    /**
     * 创建标签规则。
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException 参数非法 / 关联标签不存在或已禁用 / conditions 非合法 JSON
     */
    @RequirePermission(resource = "scrm_customer_tag", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/rules")
    public OperationResponse<ScrmTagRuleEntity> createRule(@Valid @RequestBody ScrmTagRuleDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerTagService.createRule(dto));
    }

    /**
     * 更新标签规则。
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 参数非法 / 关联标签不存在或已禁用
     */
    @RequirePermission(resource = "scrm_customer_tag", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/rules/{id}")
    public OperationResponse<ScrmTagRuleEntity> updateRule(@PathVariable Long id,
                                                             @RequestBody ScrmTagRuleDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerTagService.updateRule(id, dto));
    }

    /**
     * 删除标签规则。
     *
     * @param id 规则 ID
     * @return 空响应
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_customer_tag", action = "delete")
    @DeleteMapping("/rules/{id}")
    public OperationResponse<Void> deleteRule(@PathVariable Long id) throws ScrmException {
        scrmCustomerTagService.deleteRule(id);
        return OperationResponse.build();
    }

    /**
     * 查询规则详情。
     *
     * @param id 规则 ID
     * @return 规则详情
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_customer_tag", action = "read")
    @GetMapping("/rules/{id}")
    public OperationResponse<ScrmTagRuleEntity> getRule(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCustomerTagService.getRule(id));
    }

    /**
     * 分页查询规则列表。
     *
     * @param tagId   标签 ID 过滤（可空）
     * @param status  状态过滤: ACTIVE / INACTIVE / DRAFT（可空）
     * @param keyword 关键字过滤（按 ruleName / description 模糊匹配, 可空）
     * @param page    页码（从 0 开始, 默认 0）
     * @param size    每页大小（默认 20）
     * @return 规则分页结果 (按 createTime DESC)
     */
    @RequirePermission(resource = "scrm_customer_tag", action = "read")
    @GetMapping("/rules/list")
    public OperationResponse<Page<ScrmTagRuleEntity>> listRules(
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmCustomerTagService.listRules(tagId, status, keyword, pageable));
    }

    /**
     * 启用规则 (status=ACTIVE)。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_customer_tag", action = "update")
    @PostMapping("/rules/{id}/enable")
    public OperationResponse<ScrmTagRuleEntity> enableRule(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCustomerTagService.enableRule(id));
    }

    /**
     * 禁用规则 (status=INACTIVE)。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_customer_tag", action = "update")
    @PostMapping("/rules/{id}/disable")
    public OperationResponse<ScrmTagRuleEntity> disableRule(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCustomerTagService.disableRule(id));
    }

    // ============================================================
    // 规则评估 Rule Evaluation
    // ============================================================

    /**
     * 测试规则匹配 (不实际打标)。
     *
     * @param testDto 测试参数 (ruleId + customerIds)
     * @return 测试结果: {ruleId, ruleName, matchedCustomerIds, matchedCount, details}
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_customer_tag", action = "read")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/rules/test")
    public OperationResponse<Map<String, Object>> testRule(@Valid @RequestBody ScrmTagRuleTestDto testDto)
            throws ScrmException {
        return OperationResponse.build(scrmCustomerTagService.testRule(testDto));
    }

    /**
     * 手动评估规则 (事件驱动, 命中则自动打标)。
     *
     * @param dto 评估参数 (customerId + triggerEvent + customerContext)
     * @return 评估结果: {customerId, triggerEvent, matchedCount, matchedRules}
     * @throws ScrmException 客户不存在
     */
    @RequirePermission(resource = "scrm_customer_tag", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/rules/evaluate")
    public OperationResponse<Map<String, Object>> evaluateRule(
            @Valid @RequestBody ScrmTagRuleEvaluateDto dto) throws ScrmException {
        return OperationResponse.build(scrmCustomerTagService.evaluateRule(dto));
    }

    /**
     * 批量执行所有活跃规则 (运行所有规则)。
     *
     * @return 各规则执行结果: [{ruleId, ruleName, matchedCount}]
     */
    @RequirePermission(resource = "scrm_customer_tag", action = "update")
    @RateLimit(capacity = 1, refillTokens = 1, refillPeriodSeconds = 60)
    @PostMapping("/rules/execute")
    public OperationResponse<List<Map<String, Object>>> runAllRules() {
        return OperationResponse.build(scrmCustomerTagService.runAllRules());
    }
}
