/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMessageTemplateController.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmMessageTemplateDto;
import org.hiylo.scrm.entity.ScrmMessageTemplateEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmMessageTemplateService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
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
 * SCRM 消息模板控制器（快捷回复）。
 * <p>
 * 提供消息模板的增删改查、启用/禁用、适用模板查询与变量插值渲染接口。运营人员维护可复用
 * 消息模板, 发送消息时按分类与平台类型选用, 通过 {@code POST /{id}/render} 完成变量插值
 * 生成最终内容。
 * </p>
 * <p>
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/message-templates")
@RequiredArgsConstructor
public class ScrmMessageTemplateController {

    /** 消息模板服务 */
    private final ScrmMessageTemplateService messageTemplateService;

    /**
     * 创建消息模板。
     *
     * @param dto 模板参数
     * @return 创建后的模板
     * @throws ScrmException 模板名称已存在
     */
    @RequirePermission(resource = "scrm_message_template", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping
    public OperationResponse<ScrmMessageTemplateEntity> create(@Valid @RequestBody ScrmMessageTemplateDto dto)
            throws ScrmException {
        return OperationResponse.build(messageTemplateService.createTemplate(dto));
    }

    /**
     * 更新消息模板。
     *
     * @param id  模板 ID
     * @param dto 模板参数
     * @return 更新后的模板
     * @throws ScrmException 模板不存在 / 模板名称冲突
     */
    @RequirePermission(resource = "scrm_message_template", action = "update")
    @PutMapping("/{id}")
    public OperationResponse<ScrmMessageTemplateEntity> update(@PathVariable Long id,
                                                                @RequestBody ScrmMessageTemplateDto dto)
            throws ScrmException {
        return OperationResponse.build(messageTemplateService.updateTemplate(id, dto));
    }

    /**
     * 删除消息模板。
     *
     * @param id 模板 ID
     * @return 空响应
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_message_template", action = "delete")
    @DeleteMapping("/{id}")
    public OperationResponse<Void> delete(@PathVariable Long id) throws ScrmException {
        messageTemplateService.deleteTemplate(id);
        return OperationResponse.build();
    }

    /**
     * 查询模板详情。
     *
     * @param id 模板 ID
     * @return 模板详情
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_message_template", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmMessageTemplateEntity> get(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(messageTemplateService.getTemplate(id));
    }

    /**
     * 分页查询模板列表, 支持按分类、启用状态与关键字过滤。
     *
     * @param category 分类过滤（可空）
     * @param enabled  启用状态过滤（可空）
     * @param keyword  关键字过滤（按模板名称/分类模糊匹配, 可空）
     * @param page     页码（从 0 开始, 默认 0）
     * @param size     每页大小（默认 20）
     * @return 模板分页结果
     */
    @RequirePermission(resource = "scrm_message_template", action = "read")
    @GetMapping
    public OperationResponse<Page<ScrmMessageTemplateEntity>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(
                messageTemplateService.listTemplates(category, enabled, keyword, page, size));
    }

    /**
     * 查询适用模板（含通用模板 platformType 为空）, 仅返回启用模板。
     *
     * @param platformType 平台类型（可空, 空则不按平台过滤）
     * @return 适用模板列表（按 sortOrder ASC）
     */
    @RequirePermission(resource = "scrm_message_template", action = "read")
    @GetMapping("/applicable")
    public OperationResponse<List<ScrmMessageTemplateEntity>> applicable(
            @RequestParam(required = false) String platformType) {
        return OperationResponse.build(messageTemplateService.getApplicableTemplates(platformType));
    }

    /**
     * 渲染模板: 将 {@code {{key}}} 替换为变量值, 缺失变量替换为空字符串。
     *
     * @param id        模板 ID
     * @param variables 变量值映射
     * @return 渲染后的内容
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_message_template", action = "read")
    @PostMapping("/{id}/render")
    public OperationResponse<String> render(@PathVariable Long id,
                                             @RequestBody Map<String, String> variables) throws ScrmException {
        return OperationResponse.build(messageTemplateService.renderTemplate(id, variables));
    }

    /**
     * 启用模板。
     *
     * @param id 模板 ID
     * @return 空响应
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_message_template", action = "update")
    @PostMapping("/{id}/enable")
    public OperationResponse<Void> enable(@PathVariable Long id) throws ScrmException {
        messageTemplateService.enableTemplate(id);
        return OperationResponse.build();
    }

    /**
     * 禁用模板。
     *
     * @param id 模板 ID
     * @return 空响应
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_message_template", action = "update")
    @PostMapping("/{id}/disable")
    public OperationResponse<Void> disable(@PathVariable Long id) throws ScrmException {
        messageTemplateService.disableTemplate(id);
        return OperationResponse.build();
    }
}
