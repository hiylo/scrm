/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMessageTemplateCenterController.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmMessageTemplateCenterDto;
import org.hiylo.scrm.dto.ScrmMessageTemplateGroupDto;
import org.hiylo.scrm.dto.ScrmMessageTemplateVersionDto;
import org.hiylo.scrm.dto.ScrmTemplateQueryDto;
import org.hiylo.scrm.dto.ScrmTemplateRenderDto;
import org.hiylo.scrm.dto.ScrmTemplateReviewDto;
import org.hiylo.scrm.entity.ScrmMessageTemplateCenterEntity;
import org.hiylo.scrm.entity.ScrmMessageTemplateGroupEntity;
import org.hiylo.scrm.entity.ScrmMessageTemplateVersionEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmMessageTemplateCenterService;
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
 * SCRM 消息模板中心控制器。
 * <p>
 * 提供统一消息模板管理完整能力: 模板分组 (CRUD / 启停 / 模板迁移 / 分组树 / 统计刷新),
 * 模板定义 (CRUD / 发布 / 归档 / 复制 / 标准模板 / 按分组/渠道/场景查询 / 使用统计更新),
 * 版本管理 (创建 / 激活 / 回滚 / 对比), 渲染 (渲染 / 按渠道渲染 / 变量校验 / 预览 / 提取变量 /
 * 批量渲染), 审批 (提交 / 审核 / 批量审核 / 待审核列表 / 审核历史), 统计 (总览 / 分组 / 热门 /
 * 效果 / 渠道)。
 * </p>
 * <p>
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/message-template-center")
@RequiredArgsConstructor
public class ScrmMessageTemplateCenterController {

    /** 消息模板中心服务 */
    private final ScrmMessageTemplateCenterService templateCenterService;

    // ============================================================
    // 模板分组管理
    // ============================================================

    /**
     * 创建模板分组。
     *
     * @param dto 分组参数
     * @return 创建后的分组
     * @throws ScrmException 分组编码已存在
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/groups")
    public OperationResponse<ScrmMessageTemplateGroupEntity> createGroup(
            @Valid @RequestBody ScrmMessageTemplateGroupDto dto)
            throws ScrmException {
        return OperationResponse.build(templateCenterService.createGroup(dto));
    }

    /**
     * 更新模板分组。
     *
     * @param id  分组 ID
     * @param dto 分组参数
     * @return 更新后的分组
     * @throws ScrmException 分组不存在 / 分组编码冲突
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "update")
    @PutMapping("/groups/{id}")
    public OperationResponse<ScrmMessageTemplateGroupEntity> updateGroup(@PathVariable Long id,
                                                                          @RequestBody ScrmMessageTemplateGroupDto dto)
            throws ScrmException {
        return OperationResponse.build(templateCenterService.updateGroup(id, dto));
    }

    /**
     * 删除模板分组。
     *
     * @param id 分组 ID
     * @return 空响应
     * @throws ScrmException 分组不存在 / 分组下存在模板
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "delete")
    @DeleteMapping("/groups/{id}")
    public OperationResponse<Void> deleteGroup(@PathVariable Long id) throws ScrmException {
        templateCenterService.deleteGroup(id);
        return OperationResponse.build();
    }

    /**
     * 查询分组详情。
     *
     * @param id 分组 ID
     * @return 分组详情
     * @throws ScrmException 分组不存在
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "read")
    @GetMapping("/groups/{id}")
    public OperationResponse<ScrmMessageTemplateGroupEntity> getGroup(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(templateCenterService.getGroup(id));
    }

    /**
     * 按分组编码查询分组。
     *
     * @param code 分组编码
     * @return 分组详情
     * @throws ScrmException 分组不存在
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "read")
    @GetMapping("/groups/code/{code}")
    public OperationResponse<ScrmMessageTemplateGroupEntity> getGroupByCode(
            @PathVariable String code) throws ScrmException {
        return OperationResponse.build(templateCenterService.getGroupByCode(code));
    }

    /**
     * 分页查询分组列表。
     *
     * @param groupType 分组类型过滤（可空）
     * @param parentId  父分组 ID 过滤（可空）
     * @param enabled   启用状态过滤（可空）
     * @param page      页码（从 0 开始, 默认 0）
     * @param size      每页大小（默认 20）
     * @return 分组分页结果
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "read")
    @GetMapping("/groups/list")
    public OperationResponse<Page<ScrmMessageTemplateGroupEntity>> listGroups(
            @RequestParam(required = false) String groupType,
            @RequestParam(required = false) Long parentId,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(templateCenterService.listGroups(groupType, parentId, enabled,
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "sortOrder"))));
    }

    /**
     * 启用分组。
     *
     * @param id 分组 ID
     * @return 空响应
     * @throws ScrmException 分组不存在
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "update")
    @PostMapping("/groups/{id}/enable")
    public OperationResponse<Void> enableGroup(@PathVariable Long id) throws ScrmException {
        templateCenterService.enableGroup(id);
        return OperationResponse.build();
    }

    /**
     * 禁用分组。
     *
     * @param id 分组 ID
     * @return 空响应
     * @throws ScrmException 分组不存在
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "update")
    @PostMapping("/groups/{id}/disable")
    public OperationResponse<Void> disableGroup(@PathVariable Long id) throws ScrmException {
        templateCenterService.disableGroup(id);
        return OperationResponse.build();
    }

    /**
     * 将源分组下的模板迁移到目标分组。
     *
     * @param sourceGroupId 源分组 ID
     * @param targetGroupId 目标分组 ID
     * @return 迁移的模板数量
     * @throws ScrmException 源/目标分组不存在
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "update")
    @PostMapping("/groups/move")
    public OperationResponse<Integer> moveTemplatesToGroup(@RequestParam Long sourceGroupId,
                                                            @RequestParam Long targetGroupId)
                                                                    throws ScrmException {
        return OperationResponse.build(templateCenterService.moveTemplatesToGroup(sourceGroupId, targetGroupId));
    }

    /**
     * 刷新分组的模板数量统计。
     *
     * @param id 分组 ID
     * @return 空响应
     * @throws ScrmException 分组不存在
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "update")
    @PostMapping("/groups/{id}/stats")
    public OperationResponse<Void> updateGroupStats(@PathVariable Long id) throws ScrmException {
        templateCenterService.updateGroupStats(id);
        return OperationResponse.build();
    }

    /**
     * 查询分组树。
     *
     * @return 分组树
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "read")
    @GetMapping("/groups/tree")
    public OperationResponse<List<Map<String, Object>>> getGroupTree() {
        return OperationResponse.build(templateCenterService.getGroupTree());
    }

    // ============================================================
    // 模板定义管理
    // ============================================================

    /**
     * 创建模板。
     *
     * @param dto 模板参数
     * @return 创建后的模板
     * @throws ScrmException 模板编码已存在
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping
    public OperationResponse<ScrmMessageTemplateCenterEntity> createTemplate(
            @Valid @RequestBody ScrmMessageTemplateCenterDto dto) throws ScrmException {
        return OperationResponse.build(templateCenterService.createTemplate(dto));
    }

    /**
     * 更新模板。
     *
     * @param id  模板 ID
     * @param dto 模板参数
     * @return 更新后的模板
     * @throws ScrmException 模板不存在 / 模板编码冲突
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "update")
    @PutMapping("/{id}")
    public OperationResponse<ScrmMessageTemplateCenterEntity> updateTemplate(@PathVariable Long id,
            @RequestBody ScrmMessageTemplateCenterDto dto)
            throws ScrmException {
        return OperationResponse.build(templateCenterService.updateTemplate(id, dto));
    }

    /**
     * 删除模板。
     *
     * @param id 模板 ID
     * @return 空响应
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "delete")
    @DeleteMapping("/{id}")
    public OperationResponse<Void> deleteTemplate(@PathVariable Long id) throws ScrmException {
        templateCenterService.deleteTemplate(id);
        return OperationResponse.build();
    }

    /**
     * 查询模板详情。
     *
     * @param id 模板 ID
     * @return 模板详情
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmMessageTemplateCenterEntity> getTemplate(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(templateCenterService.getTemplate(id));
    }

    /**
     * 按模板编码查询模板。
     *
     * @param code 模板编码
     * @return 模板详情
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "read")
    @GetMapping("/code/{code}")
    public OperationResponse<ScrmMessageTemplateCenterEntity> getTemplateByCode(
            @PathVariable String code) throws ScrmException {
        return OperationResponse.build(templateCenterService.getTemplateByCode(code));
    }

    /**
     * 分页查询模板列表, 支持多条件过滤。
     *
     * @param groupId      分组 ID 过滤（可空）
     * @param templateType 模板类型过滤（可空）
     * @param channels     渠道过滤（可空）
     * @param status       状态过滤（可空）
     * @param category     分类过滤（可空）
     * @param keyword      关键字过滤（可空）
     * @param page         页码（从 0 开始, 默认 0）
     * @param size         每页大小（默认 20）
     * @return 模板分页结果
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "read")
    @GetMapping("/list")
    public OperationResponse<Page<ScrmMessageTemplateCenterEntity>> listTemplates(
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) String templateType,
            @RequestParam(required = false) String channels,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ScrmTemplateQueryDto queryDto = new ScrmTemplateQueryDto();
        queryDto.setGroupId(groupId);
        queryDto.setTemplateType(templateType);
        queryDto.setChannels(channels);
        queryDto.setStatus(status);
        queryDto.setCategory(category);
        queryDto.setKeyword(keyword);
        return OperationResponse.build(templateCenterService.listTemplates(queryDto,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"))));
    }

    /**
     * 发布模板。
     *
     * @param id 模板 ID
     * @return 更新后的模板
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "update")
    @PostMapping("/{id}/publish")
    public OperationResponse<ScrmMessageTemplateCenterEntity> publishTemplate(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(templateCenterService.publishTemplate(id));
    }

    /**
     * 归档模板。
     *
     * @param id 模板 ID
     * @return 更新后的模板
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "update")
    @PostMapping("/{id}/archive")
    public OperationResponse<ScrmMessageTemplateCenterEntity> archiveTemplate(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(templateCenterService.archiveTemplate(id));
    }

    /**
     * 复制模板。
     *
     * @param id      源模板 ID
     * @param newCode 新模板编码
     * @return 复制后的模板
     * @throws ScrmException 模板不存在 / 新编码已存在
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "create")
    @PostMapping("/{id}/duplicate")
    public OperationResponse<ScrmMessageTemplateCenterEntity> duplicateTemplate(@PathVariable Long id,
            @RequestParam String newCode) throws ScrmException {
        return OperationResponse.build(templateCenterService.duplicateTemplate(id, newCode));
    }

    /**
     * 设为标准模板。
     *
     * @param id 模板 ID
     * @return 更新后的模板
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "update")
    @PostMapping("/{id}/standard")
    public OperationResponse<ScrmMessageTemplateCenterEntity> setStandard(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(templateCenterService.setStandard(id));
    }

    /**
     * 按分组查询模板。
     *
     * @param groupId 分组 ID
     * @param page    页码（默认 0）
     * @param size    每页大小（默认 20）
     * @return 模板分页结果
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "read")
    @GetMapping("/by-group/{groupId}")
    public OperationResponse<Page<ScrmMessageTemplateCenterEntity>> getTemplatesByGroup(@PathVariable Long groupId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(templateCenterService.getTemplatesByGroup(groupId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"))));
    }

    /**
     * 按渠道查询模板。
     *
     * @param channel 渠道
     * @param page    页码（默认 0）
     * @param size    每页大小（默认 20）
     * @return 模板分页结果
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "read")
    @GetMapping("/by-channel/{channel}")
    public OperationResponse<Page<ScrmMessageTemplateCenterEntity>> getTemplatesByChannel(@PathVariable String channel,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(templateCenterService.getTemplatesByChannel(channel,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"))));
    }

    /**
     * 按场景查询模板。
     *
     * @param scenario 场景
     * @param page     页码（默认 0）
     * @param size     每页大小（默认 20）
     * @return 模板分页结果
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "read")
    @GetMapping("/by-scenario/{scenario}")
    public OperationResponse<Page<ScrmMessageTemplateCenterEntity>> getTemplatesByScenario(
            @PathVariable String scenario,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(templateCenterService.getTemplatesByScenario(scenario,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"))));
    }

    /**
     * 更新模板使用统计。
     *
     * @param id      模板 ID
     * @param success 是否成功（可空, 空则不更新成功率）
     * @return 空响应
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "update")
    @PostMapping("/{id}/usage")
    public OperationResponse<Void> incrementUsage(@PathVariable Long id,
                                                   @RequestParam(required = false)
                                                           Boolean success) throws ScrmException {
        templateCenterService.incrementUsage(id, success);
        return OperationResponse.build();
    }

    // ============================================================
    // 版本管理
    // ============================================================

    /**
     * 创建模板新版本。
     *
     * @param dto 版本参数 (templateId + changeLog)
     * @return 创建后的版本
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "create")
    @PostMapping("/versions")
    public OperationResponse<ScrmMessageTemplateVersionEntity> createVersion(
            @Valid @RequestBody ScrmMessageTemplateVersionDto dto) throws ScrmException {
        return OperationResponse.build(templateCenterService.createVersion(dto.getTemplateId(), dto.getChangeLog()));
    }

    /**
     * 查询版本详情。
     *
     * @param id 版本 ID
     * @return 版本详情
     * @throws ScrmException 版本不存在
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "read")
    @GetMapping("/versions/{id}")
    public OperationResponse<ScrmMessageTemplateVersionEntity> getVersion(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(templateCenterService.getVersion(id));
    }

    /**
     * 分页查询模板版本列表 (按版本号降序)。
     *
     * @param templateId 模板 ID
     * @param page       页码（默认 0）
     * @param size       每页大小（默认 20）
     * @return 版本分页结果
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "read")
    @GetMapping("/versions/list")
    public OperationResponse<Page<ScrmMessageTemplateVersionEntity>> listVersions(
            @RequestParam Long templateId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(templateCenterService.listVersions(templateId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "versionNumber"))));
    }

    /**
     * 激活版本。
     *
     * @param id 版本 ID
     * @return 激活后的版本
     * @throws ScrmException 版本不存在
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "update")
    @PostMapping("/versions/{id}/activate")
    public OperationResponse<ScrmMessageTemplateVersionEntity> activateVersion(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(templateCenterService.activateVersion(id));
    }

    /**
     * 回滚到指定版本。
     *
     * @param templateId 模板 ID
     * @param versionId  目标版本 ID
     * @return 回滚后的版本
     * @throws ScrmException 模板/版本不存在
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "update")
    @PostMapping("/versions/rollback")
    public OperationResponse<ScrmMessageTemplateVersionEntity> rollbackToVersion(@RequestParam Long templateId,
            @RequestParam
                    Long versionId) throws ScrmException {
        return OperationResponse.build(templateCenterService.rollbackToVersion(templateId, versionId));
    }

    /**
     * 对比两个版本差异。
     *
     * @param versionId1 版本 ID 1
     * @param versionId2 版本 ID 2
     * @return 对比结果
     * @throws ScrmException 版本不存在
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "read")
    @PostMapping("/versions/compare")
    public OperationResponse<Map<String, Object>> compareVersions(@RequestParam Long versionId1,
                                                                    @RequestParam
                                                                          Long versionId2) throws ScrmException {
        return OperationResponse.build(templateCenterService.compareVersions(versionId1, versionId2));
    }

    // ============================================================
    // 渲染
    // ============================================================

    /**
     * 渲染模板 (变量替换 + 渠道适配)。
     *
     * @param dto 渲染参数
     * @return 渲染结果
     * @throws ScrmException 模板不存在 / 渠道不适用 / 变量校验失败
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "read")
    @RateLimit(capacity = 100, refillTokens = 100, refillPeriodSeconds = 60)
    @PostMapping("/render")
    public OperationResponse<Map<String, Object>> render(
            @Valid @RequestBody ScrmTemplateRenderDto dto) throws ScrmException {
        return OperationResponse.build(templateCenterService.render(dto));
    }

    /**
     * 按渠道渲染模板。
     *
     * @param templateId 模板 ID
     * @param channel    目标渠道
     * @param variables  变量值映射
     * @return 渲染结果
     * @throws ScrmException 模板不存在 / 渠道不适用 / 变量校验失败
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "read")
    @PostMapping("/render/channel")
    public OperationResponse<Map<String, Object>> renderForChannel(@RequestParam Long templateId,
            @RequestParam String channel,
            @RequestBody(required = false) Map<String, Object> variables) throws ScrmException {
        return OperationResponse.build(templateCenterService.renderForChannel(templateId, channel, variables));
    }

    /**
     * 校验模板变量。
     *
     * @param templateId 模板 ID
     * @param variables  变量值映射
     * @return 校验结果
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "read")
    @PostMapping("/render/validate")
    public OperationResponse<Map<String, Object>> validateVariables(@RequestParam Long templateId,
            @RequestBody(required = false) Map<String, Object> variables) throws ScrmException {
        return OperationResponse.build(templateCenterService.validateVariables(templateId, variables));
    }

    /**
     * 预览模板渲染结果。
     *
     * @param templateId 模板 ID
     * @param variables  变量值映射
     * @return 预览结果
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "read")
    @PostMapping("/render/preview")
    public OperationResponse<Map<String, Object>> preview(@RequestParam Long templateId,
            @RequestBody(required = false) Map<String, Object> variables) throws ScrmException {
        return OperationResponse.build(templateCenterService.preview(templateId, variables));
    }

    /**
     * 从内容中提取变量名列表。
     *
     * @param body 含 content 字段的请求体
     * @return 变量名列表
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "read")
    @PostMapping("/render/extract")
    public OperationResponse<List<String>> extractVariables(@RequestBody Map<String, Object> body) {
        Object content = body == null ? null : body.get("content");
        return OperationResponse.build(templateCenterService.extractVariables(content == null ?
                null : String.valueOf(content)));
    }

    /**
     * 批量渲染模板。
     *
     * @param renderDtos 渲染参数列表
     * @return 渲染结果列表
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "read")
    @PostMapping("/render/batch")
    public OperationResponse<List<Map<String, Object>>> batchRender(
            @RequestBody List<ScrmTemplateRenderDto> renderDtos) {
        return OperationResponse.build(templateCenterService.batchRender(renderDtos));
    }

    // ============================================================
    // 审批
    // ============================================================

    /**
     * 提交模板审核。
     *
     * @param templateId 模板 ID
     * @return 更新后的模板
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "update")
    @PostMapping("/reviews/submit")
    public OperationResponse<ScrmMessageTemplateCenterEntity> submitForReview(
            @RequestParam Long templateId) throws ScrmException {
        return OperationResponse.build(templateCenterService.submitForReview(templateId));
    }

    /**
     * 审核模板 (通过/驳回)。
     *
     * @param dto 审核参数
     * @return 更新后的模板
     * @throws ScrmException 模板不存在 / 审核动作非法
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "update")
    @PostMapping("/reviews/review")
    public OperationResponse<ScrmMessageTemplateCenterEntity> review(@Valid @RequestBody ScrmTemplateReviewDto dto)
            throws ScrmException {
        return OperationResponse.build(templateCenterService.review(dto));
    }

    /**
     * 批量审核模板。
     *
     * @param templateIds 模板 ID 列表
     * @param action      审核动作 (APPROVE/REJECT)
     * @param comment     审核意见（可空）
     * @return 审核结果列表
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "update")
    @PostMapping("/reviews/batch")
    public OperationResponse<List<Map<String, Object>>> batchReview(@RequestParam List<Long> templateIds,
                                                                     @RequestParam String action,
                                                                     @RequestParam(required = false) String comment) {
        return OperationResponse.build(templateCenterService.batchReview(templateIds, action, comment));
    }

    /**
     * 查询待审核模板列表。
     *
     * @param page 页码（默认 0）
     * @param size 每页大小（默认 20）
     * @return 待审核模板分页结果
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "read")
    @GetMapping("/reviews/pending")
    public OperationResponse<Page<ScrmMessageTemplateCenterEntity>> getPendingReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(templateCenterService.getPendingReviews(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"))));
    }

    /**
     * 查询模板审核历史。
     *
     * @param templateId 模板 ID
     * @param page       页码（默认 0）
     * @param size       每页大小（默认 20）
     * @return 审核历史分页结果
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "read")
    @GetMapping("/reviews/history/{templateId}")
    public OperationResponse<Page<ScrmMessageTemplateVersionEntity>> getReviewHistory(@PathVariable Long templateId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(templateCenterService.getReviewHistory(templateId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "approvedAt"))));
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 模板统计总览。
     *
     * @param startTime 起始时间过滤（可空）
     * @param endTime   结束时间过滤（可空）
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getTemplateStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(templateCenterService.getTemplateStats(startTime, endTime));
    }

    /**
     * 分组统计。
     *
     * @return 分组统计列表
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "read")
    @GetMapping("/stats/groups")
    public OperationResponse<List<Map<String, Object>>> getGroupStats() {
        return OperationResponse.build(templateCenterService.getGroupStats());
    }

    /**
     * 热门模板 (按使用次数降序)。
     *
     * @param limit 数量（默认 10）
     * @return 模板列表
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "read")
    @GetMapping("/stats/top")
    public OperationResponse<List<ScrmMessageTemplateCenterEntity>> getTopTemplates(
            @RequestParam(defaultValue = "10") int limit) {
        return OperationResponse.build(templateCenterService.getTopTemplates(limit));
    }

    /**
     * 模板效果分析。
     *
     * @param templateId 模板 ID
     * @return 效果分析结果
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "read")
    @GetMapping("/stats/effectiveness/{templateId}")
    public OperationResponse<Map<String, Object>> getTemplateEffectiveness(
            @PathVariable Long templateId) throws ScrmException {
        return OperationResponse.build(templateCenterService.getTemplateEffectiveness(templateId));
    }

    /**
     * 渠道效果分析。
     *
     * @param startTime 起始时间过滤（可空）
     * @param endTime   结束时间过滤（可空）
     * @return 渠道效果
     */
    @RequirePermission(resource = "scrm_message_template_center", action = "read")
    @GetMapping("/stats/channels")
    public OperationResponse<Map<String, Object>> getChannelEffectiveness(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(templateCenterService.getChannelEffectiveness(startTime, endTime));
    }
}
