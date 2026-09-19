/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTagSystemController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmBatchTagDto;
import org.hiylo.scrm.dto.ScrmTagCustomerDto;
import org.hiylo.scrm.dto.ScrmTagDto;
import org.hiylo.scrm.dto.ScrmTagGroupDto;
import org.hiylo.scrm.dto.ScrmTagRuleDto;
import org.hiylo.scrm.dto.ScrmTagRuleTestDto;
import org.hiylo.scrm.entity.ScrmTagCustomerEntity;
import org.hiylo.scrm.entity.ScrmTagEntity;
import org.hiylo.scrm.entity.ScrmTagGroupEntity;
import org.hiylo.scrm.entity.ScrmTagRuleEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmTagSystemService;
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
 * SCRM 客户标签体系管理控制器。
 * <p>
 * 提供完整的客户标签体系接口: 标签分组管理、标签定义、标签规则 (自动打标)、批量打标/去标、
 * 标签统计与标签合并。权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为
 * 端点权限元数据声明。
 * </p>
 * <p>
 * 路由分组:
 * <ul>
 *   <li>/groups: 分组管理</li>
 *   <li>/tags: 标签定义管理</li>
 *   <li>/rules: 自动规则管理</li>
 *   <li>/customer-tags: 客户标签关联操作</li>
 *   <li>/stats: 统计与覆盖率</li>
 * </ul>
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/tag-system")
@RequiredArgsConstructor
public class ScrmTagSystemController {

    /** 标签体系服务 */
    private final ScrmTagSystemService scrmTagSystemService;

    // ============================================================
    // 分组管理 /groups
    // ============================================================

    /**
     * 创建标签分组。
     *
     * @param dto 分组参数
     * @return 创建后的分组
     * @throws ScrmException 参数非法 / 分组编码重复
     */
    @RequirePermission(resource = "scrm_tag_system", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/groups")
    public OperationResponse<ScrmTagGroupEntity> createGroup(@Valid @RequestBody ScrmTagGroupDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmTagSystemService.createGroup(dto));
    }

    /**
     * 更新标签分组。
     *
     * @param id  分组 ID
     * @param dto 分组参数
     * @return 更新后的分组
     * @throws ScrmException 分组不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_tag_system", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/groups/{id}")
    public OperationResponse<ScrmTagGroupEntity> updateGroup(@PathVariable Long id,
                                                              @RequestBody ScrmTagGroupDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmTagSystemService.updateGroup(id, dto));
    }

    /**
     * 删除标签分组。
     *
     * @param id 分组 ID
     * @return 空响应
     * @throws ScrmException 分组不存在 / 系统内置 / 仍有标签引用
     */
    @RequirePermission(resource = "scrm_tag_system", action = "delete")
    @DeleteMapping("/groups/{id}")
    public OperationResponse<Void> deleteGroup(@PathVariable Long id) throws ScrmException {
        scrmTagSystemService.deleteGroup(id);
        return OperationResponse.build();
    }

    /**
     * 查询分组详情。
     *
     * @param id 分组 ID
     * @return 分组详情
     * @throws ScrmException 分组不存在
     */
    @RequirePermission(resource = "scrm_tag_system", action = "read")
    @GetMapping("/groups/{id}")
    public OperationResponse<ScrmTagGroupEntity> getGroup(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmTagSystemService.getGroup(id));
    }

    /**
     * 分页查询分组列表。
     *
     * @param enabled 启用状态过滤（可空）
     * @param keyword 关键字过滤（按分组名称/描述/编码模糊匹配, 可空）
     * @param page    页码（从 0 开始, 默认 0）
     * @param size    每页大小（默认 20）
     * @return 分组分页结果 (按 sortOrder ASC, createTime DESC)
     */
    @RequirePermission(resource = "scrm_tag_system", action = "read")
    @GetMapping("/groups/list")
    public OperationResponse<Page<ScrmTagGroupEntity>> listGroups(
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "sortOrder")
                        .and(Sort.by(Sort.Direction.DESC, "createTime")));
        return OperationResponse.build(scrmTagSystemService.listGroups(enabled, keyword, pageable));
    }

    /**
     * 启用分组。
     *
     * @param id 分组 ID
     * @return 更新后的分组
     * @throws ScrmException 分组不存在
     */
    @RequirePermission(resource = "scrm_tag_system", action = "update")
    @PostMapping("/groups/{id}/enable")
    public OperationResponse<ScrmTagGroupEntity> enableGroup(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmTagSystemService.enableGroup(id));
    }

    /**
     * 禁用分组。
     *
     * @param id 分组 ID
     * @return 更新后的分组
     * @throws ScrmException 分组不存在
     */
    @RequirePermission(resource = "scrm_tag_system", action = "update")
    @PostMapping("/groups/{id}/disable")
    public OperationResponse<ScrmTagGroupEntity> disableGroup(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmTagSystemService.disableGroup(id));
    }

    // ============================================================
    // 标签定义管理 /tags
    // ============================================================

    /**
     * 创建标签。
     *
     * @param dto 标签参数
     * @return 创建后的标签
     * @throws ScrmException 参数非法 / 标签编码重复 / 所属分组不存在
     */
    @RequirePermission(resource = "scrm_tag_system", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/tags")
    public OperationResponse<ScrmTagEntity> createTag(@Valid @RequestBody ScrmTagDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmTagSystemService.createTag(dto));
    }

    /**
     * 更新标签。
     *
     * @param id  标签 ID
     * @param dto 标签参数
     * @return 更新后的标签
     * @throws ScrmException 标签不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_tag_system", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/tags/{id}")
    public OperationResponse<ScrmTagEntity> updateTag(@PathVariable Long id,
                                                       @RequestBody ScrmTagDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmTagSystemService.updateTag(id, dto));
    }

    /**
     * 删除标签。
     *
     * @param id 标签 ID
     * @return 空响应
     * @throws ScrmException 标签不存在 / 系统内置 / 仍有活跃规则
     */
    @RequirePermission(resource = "scrm_tag_system", action = "delete")
    @DeleteMapping("/tags/{id}")
    public OperationResponse<Void> deleteTag(@PathVariable Long id) throws ScrmException {
        scrmTagSystemService.deleteTag(id);
        return OperationResponse.build();
    }

    /**
     * 查询标签详情。
     *
     * @param id 标签 ID
     * @return 标签详情
     * @throws ScrmException 标签不存在
     */
    @RequirePermission(resource = "scrm_tag_system", action = "read")
    @GetMapping("/tags/{id}")
    public OperationResponse<ScrmTagEntity> getTag(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmTagSystemService.getTag(id));
    }

    /**
     * 按编码查询标签。
     *
     * @param code 标签编码
     * @return 标签详情
     * @throws ScrmException 标签不存在
     */
    @RequirePermission(resource = "scrm_tag_system", action = "read")
    @GetMapping("/tags/code/{code}")
    public OperationResponse<ScrmTagEntity> getTagByCode(@PathVariable String code) throws ScrmException {
        return OperationResponse.build(scrmTagSystemService.getTagByCode(code));
    }

    /**
     * 分页查询标签列表。
     *
     * @param groupId 分组 ID 过滤（可空）
     * @param tagType 标签类型过滤: MANUAL/AUTO/COMPUTED（可空）
     * @param enabled 启用状态过滤（可空）
     * @param keyword 关键字过滤（按标签名称/描述/编码模糊匹配, 可空）
     * @param page    页码（从 0 开始, 默认 0）
     * @param size    每页大小（默认 20）
     * @return 标签分页结果 (按 sortOrder ASC, createTime DESC)
     */
    @RequirePermission(resource = "scrm_tag_system", action = "read")
    @GetMapping("/tags/list")
    public OperationResponse<Page<ScrmTagEntity>> listTags(
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) String tagType,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "sortOrder")
                        .and(Sort.by(Sort.Direction.DESC, "createTime")));
        return OperationResponse.build(scrmTagSystemService.listTags(groupId, tagType, enabled, keyword, pageable));
    }

    /**
     * 启用标签。
     *
     * @param id 标签 ID
     * @return 更新后的标签
     * @throws ScrmException 标签不存在
     */
    @RequirePermission(resource = "scrm_tag_system", action = "update")
    @PostMapping("/tags/{id}/enable")
    public OperationResponse<ScrmTagEntity> enableTag(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmTagSystemService.enableTag(id));
    }

    /**
     * 禁用标签。
     *
     * @param id 标签 ID
     * @return 更新后的标签
     * @throws ScrmException 标签不存在
     */
    @RequirePermission(resource = "scrm_tag_system", action = "update")
    @PostMapping("/tags/{id}/disable")
    public OperationResponse<ScrmTagEntity> disableTag(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmTagSystemService.disableTag(id));
    }

    /**
     * 合并标签: 源标签客户关联迁移到目标标签, 删除源标签。
     *
     * @param request 合并请求 (sourceId + targetId)
     * @return 迁移的客户关联数
     * @throws ScrmException 标签不存在 / 同一标签 / 源标签为系统内置
     */
    @RequirePermission(resource = "scrm_tag_system", action = "update")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/tags/merge")
    public OperationResponse<Integer> mergeTags(@RequestBody MergeTagRequest request) throws ScrmException {
        return OperationResponse.build(scrmTagSystemService.mergeTags(request.getSourceId(), request.getTargetId()));
    }

    /**
     * 刷新标签的被打标客户数。
     *
     * @param id 标签 ID
     * @return 更新后的标签
     * @throws ScrmException 标签不存在
     */
    @RequirePermission(resource = "scrm_tag_system", action = "update")
    @PostMapping("/tags/{id}/customer-count")
    public OperationResponse<ScrmTagEntity> updateTagCustomerCount(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmTagSystemService.updateTagCustomerCount(id));
    }

    // ============================================================
    // 规则管理 /rules
    // ============================================================

    /**
     * 创建自动标签规则。
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException 参数非法 / 关联标签不存在或非 AUTO 类型 / conditions 非合法 JSON
     */
    @RequirePermission(resource = "scrm_tag_system", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/rules")
    public OperationResponse<ScrmTagRuleEntity> createRule(@Valid @RequestBody ScrmTagRuleDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmTagSystemService.createRule(dto));
    }

    /**
     * 更新规则。
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 参数非法 / 关联标签不存在或非 AUTO 类型
     */
    @RequirePermission(resource = "scrm_tag_system", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/rules/{id}")
    public OperationResponse<ScrmTagRuleEntity> updateRule(@PathVariable Long id,
                                                            @RequestBody ScrmTagRuleDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmTagSystemService.updateRule(id, dto));
    }

    /**
     * 删除规则。
     *
     * @param id 规则 ID
     * @return 空响应
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_tag_system", action = "delete")
    @DeleteMapping("/rules/{id}")
    public OperationResponse<Void> deleteRule(@PathVariable Long id) throws ScrmException {
        scrmTagSystemService.deleteRule(id);
        return OperationResponse.build();
    }

    /**
     * 查询规则详情。
     *
     * @param id 规则 ID
     * @return 规则详情
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_tag_system", action = "read")
    @GetMapping("/rules/{id}")
    public OperationResponse<ScrmTagRuleEntity> getRule(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmTagSystemService.getRule(id));
    }

    /**
     * 分页查询规则列表。
     *
     * @param tagId   标签 ID 过滤（可空）
     * @param status  状态过滤: ACTIVE/INACTIVE/DRAFT（可空）
     * @param keyword 关键字过滤（按规则名称/描述模糊匹配, 可空）
     * @param page    页码（从 0 开始, 默认 0）
     * @param size    每页大小（默认 20）
     * @return 规则分页结果 (按 createTime DESC)
     */
    @RequirePermission(resource = "scrm_tag_system", action = "read")
    @GetMapping("/rules/list")
    public OperationResponse<Page<ScrmTagRuleEntity>> listRules(
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmTagSystemService.listRules(tagId, status, keyword, pageable));
    }

    /**
     * 启用规则 (状态置为 ACTIVE)。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_tag_system", action = "update")
    @PostMapping("/rules/{id}/enable")
    public OperationResponse<ScrmTagRuleEntity> enableRule(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmTagSystemService.enableRule(id));
    }

    /**
     * 禁用规则 (状态置为 INACTIVE)。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_tag_system", action = "update")
    @PostMapping("/rules/{id}/disable")
    public OperationResponse<ScrmTagRuleEntity> disableRule(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmTagSystemService.disableRule(id));
    }

    /**
     * 执行自动打标规则: 遍历客户 → 匹配条件 → 打标。
     *
     * @param id 规则 ID
     * @return 命中的客户数
     * @throws ScrmException 规则不存在 / 关联标签不存在
     */
    @RequirePermission(resource = "scrm_tag_system", action = "update")
    @RateLimit(capacity = 5, refillTokens = 5, refillPeriodSeconds = 60)
    @PostMapping("/rules/{id}/execute")
    public OperationResponse<Integer> executeRule(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmTagSystemService.executeRule(id));
    }

    /**
     * 批量执行所有活跃规则。
     *
     * @return 各规则执行结果: [{ruleId, ruleName, matchedCount}]
     */
    @RequirePermission(resource = "scrm_tag_system", action = "update")
    @RateLimit(capacity = 1, refillTokens = 1, refillPeriodSeconds = 60)
    @PostMapping("/rules/batch-execute")
    public OperationResponse<List<Map<String, Object>>> batchExecuteRules() {
        return OperationResponse.build(scrmTagSystemService.batchExecuteRules());
    }

    /**
     * 测试规则匹配 (不实际打标)。
     *
     * @param testDto 测试参数 (ruleId + customerIds)
     * @return 测试结果: {matchedCustomerIds, details}
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_tag_system", action = "read")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/rules/test")
    public OperationResponse<Map<String, Object>> testRule(@Valid @RequestBody ScrmTagRuleTestDto testDto)
            throws ScrmException {
        return OperationResponse.build(scrmTagSystemService.testRule(testDto));
    }

    // ============================================================
    // 客户标签关联 /customer-tags
    // ============================================================

    /**
     * 为客户打标。
     *
     * @param dto 打标参数 (customerId + tagId + tagValue + source + assignedBy + ...)
     * @return 创建或更新后的客户标签关联
     * @throws ScrmException 标签不存在或已禁用
     */
    @RequirePermission(resource = "scrm_tag_system", action = "update")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/customer-tags/assign")
    public OperationResponse<ScrmTagCustomerEntity> assignTag(@Valid @RequestBody ScrmTagCustomerDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmTagSystemService.assignTag(
                dto.getCustomerId(), dto.getTagId(), dto.getTagValue(),
                dto.getTagSource(), dto.getAssignedBy()));
    }

    /**
     * 批量打标/去标。
     *
     * @param batchDto 批量参数 (customerIds + tagIds + action: ADD/REMOVE)
     * @return 成功操作数
     * @throws ScrmException 标签不存在或已禁用
     */
    @RequirePermission(resource = "scrm_tag_system", action = "update")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/customer-tags/batch")
    public OperationResponse<Integer> batchAssign(@Valid @RequestBody ScrmBatchTagDto batchDto)
            throws ScrmException {
        return OperationResponse.build(scrmTagSystemService.batchAssign(batchDto));
    }

    /**
     * 移除客户标签。
     *
     * @param dto 打标参数 (customerId + tagId)
     * @return 空响应
     * @throws ScrmException 关联不存在 / 自动标签不可手动去标
     */
    @RequirePermission(resource = "scrm_tag_system", action = "update")
    @PostMapping("/customer-tags/remove")
    public OperationResponse<Void> removeTag(@RequestBody ScrmTagCustomerDto dto) throws ScrmException {
        scrmTagSystemService.removeTag(dto.getCustomerId(), dto.getTagId());
        return OperationResponse.build();
    }

    /**
     * 查询客户的所有标签关联。
     *
     * @param customerId 客户 ID
     * @return 标签关联列表
     */
    @RequirePermission(resource = "scrm_tag_system", action = "read")
    @GetMapping("/customer-tags/customer/{customerId}")
    public OperationResponse<List<ScrmTagCustomerEntity>> getCustomerTags(@PathVariable Long customerId) {
        return OperationResponse.build(scrmTagSystemService.getCustomerTags(customerId));
    }

    /**
     * 分页查询标签下的客户。
     *
     * @param tagId 标签 ID
     * @param page  页码（从 0 开始, 默认 0）
     * @param size  每页大小（默认 20）
     * @return 客户关联分页结果 (按 assignedAt DESC)
     */
    @RequirePermission(resource = "scrm_tag_system", action = "read")
    @GetMapping("/customer-tags/tag/{tagId}")
    public OperationResponse<Page<ScrmTagCustomerEntity>> getTagCustomers(
            @PathVariable Long tagId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "assignedAt"));
        return OperationResponse.build(scrmTagSystemService.getTagCustomers(tagId, pageable));
    }

    /**
     * 检查客户是否具有指定标签。
     *
     * @param customerId 客户 ID
     * @param tagId      标签 ID
     * @return 是否具有标签
     */
    @RequirePermission(resource = "scrm_tag_system", action = "read")
    @GetMapping("/customer-tags/check")
    public OperationResponse<Boolean> checkTag(@RequestParam Long customerId,
                                                @RequestParam Long tagId) {
        return OperationResponse.build(scrmTagSystemService.checkTag(customerId, tagId));
    }

    /**
     * 批量获取客户标签关联。
     *
     * @param request 批量请求 (含客户 ID 列表)
     * @return 客户标签关联 Map (key: customerId, value: 关联列表)
     */
    @RequirePermission(resource = "scrm_tag_system", action = "read")
    @PostMapping("/customer-tags/batch-get")
    public OperationResponse<Map<Long, List<ScrmTagCustomerEntity>>> getTagsByCustomerIds(
            @RequestBody BatchGetRequest request) {
        return OperationResponse.build(scrmTagSystemService.getTagsByCustomerIds(request.getCustomerIds()));
    }

    // ============================================================
    // 统计 /stats
    // ============================================================

    /**
     * 标签统计概览: 总标签数 / 各类型 / 各分组 / 覆盖率。
     *
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_tag_system", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getTagStats() {
        return OperationResponse.build(scrmTagSystemService.getTagStats());
    }

    /**
     * 标签云: 按 customer_count 倒序返回热门标签。
     *
     * @param limit 返回数量 (默认 100)
     * @return 标签云列表
     */
    @RequirePermission(resource = "scrm_tag_system", action = "read")
    @GetMapping("/stats/tag-cloud")
    public OperationResponse<List<Map<String, Object>>> getTagCloud(
            @RequestParam(defaultValue = "100") int limit) {
        return OperationResponse.build(scrmTagSystemService.getTagCloud(limit));
    }

    /**
     * 客户标签数。
     *
     * @param customerId 客户 ID
     * @return 标签数
     */
    @RequirePermission(resource = "scrm_tag_system", action = "read")
    @GetMapping("/stats/customer/{customerId}/count")
    public OperationResponse<Long> getCustomerTagCount(@PathVariable Long customerId) {
        return OperationResponse.build(scrmTagSystemService.getCustomerTagCount(customerId));
    }

    /**
     * 分组覆盖率: 各分组的标签数与覆盖客户数。
     *
     * @return 分组覆盖率列表
     */
    @RequirePermission(resource = "scrm_tag_system", action = "read")
    @GetMapping("/stats/group-coverage")
    public OperationResponse<List<Map<String, Object>>> getGroupCoverage() {
        return OperationResponse.build(scrmTagSystemService.getGroupCoverage());
    }

    /**
     * 规则统计: 各规则的执行次数与匹配率。
     *
     * @return 规则统计列表
     */
    @RequirePermission(resource = "scrm_tag_system", action = "read")
    @GetMapping("/stats/rules")
    public OperationResponse<List<Map<String, Object>>> getRuleStats() {
        return OperationResponse.build(scrmTagSystemService.getRuleStats());
    }

    // ============================================================
    // 内部请求体
    // ============================================================

    /**
     * 合并标签请求体。
     * @author Hsi Chu
     */
    @lombok.Data
    public static class MergeTagRequest {
        /** 源标签 ID (将被删除) */
        private Long sourceId;
        /** 目标标签 ID (将接收客户关联) */
        private Long targetId;
    }

    /**
     * 批量获取客户标签请求体。
     * @author Hsi Chu
     */
    @lombok.Data
    public static class BatchGetRequest {
        /** 客户 ID 列表 */
        private List<Long> customerIds;
    }
}
