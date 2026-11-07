/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmAiProviderController.java
 * Date : 2026/11/07 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.common.OperationResponse;
import org.hiylo.scrm.dto.ScrmAiProviderDto;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmAiProviderService;
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

/**
 * AI Provider 配置控制器。
 * <p>
 * 系统管理内维护模型接入点：增删改查、连通测试、拉取模型列表、设默认文本/多模态
 * provider、启停。任何变更即时刷新路由快照，文本任务与证照 OCR 无需重启即切换。
 * 权限由 gateway 统一鉴权，{@code @RequirePermission} 为端点权限元数据声明。</p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@RestController
@RequestMapping("/scrm/ai-providers")
@RequiredArgsConstructor
public class ScrmAiProviderController {

    /** AI Provider 配置服务 */
    private final ScrmAiProviderService aiProviderService;

    /**
     * 获取全部 provider（按优先级升序）。
     *
     * @return provider 列表
     */
    @RequirePermission(resource = "scrm_ai_provider", action = "read")
    @GetMapping({"", "/list"})
    public OperationResponse<List<ScrmAiProviderDto>> list() {
        return OperationResponse.build(aiProviderService.list());
    }

    /**
     * 获取单个 provider。
     *
     * @param id Provider ID
     * @return provider 详情
     */
    @RequirePermission(resource = "scrm_ai_provider", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmAiProviderDto> get(@PathVariable Long id) {
        return OperationResponse.build(aiProviderService.get(id));
    }

    /**
     * 新增 provider。
     *
     * @param dto Provider 参数
     * @return 新增后的 provider
     */
    @RequirePermission(resource = "scrm_ai_provider", action = "create")
    @PostMapping
    public OperationResponse<ScrmAiProviderDto> create(
            @Valid @RequestBody ScrmAiProviderDto dto) {
        return OperationResponse.build(aiProviderService.create(dto));
    }

    /**
     * 更新 provider。
     *
     * @param id  Provider ID
     * @param dto Provider 参数
     * @return 更新后的 provider
     */
    @RequirePermission(resource = "scrm_ai_provider", action = "update")
    @PutMapping("/{id}")
    public OperationResponse<ScrmAiProviderDto> update(@PathVariable Long id,
                                                       @Valid @RequestBody ScrmAiProviderDto dto) {
        return OperationResponse.build(aiProviderService.update(id, dto));
    }

    /**
     * 删除 provider。
     *
     * @param id Provider ID
     * @return 操作结果
     */
    @RequirePermission(resource = "scrm_ai_provider", action = "delete")
    @DeleteMapping("/{id}")
    public OperationResponse<Void> delete(@PathVariable Long id) {
        aiProviderService.delete(id);
        return OperationResponse.build(null);
    }

    /**
     * 测试 provider 连通性。
     *
     * @param id Provider ID
     * @return 更新连接状态后的 provider
     */
    @RequirePermission(resource = "scrm_ai_provider", action = "update")
    @PostMapping("/{id}/test")
    public OperationResponse<ScrmAiProviderDto> testConnection(@PathVariable Long id) {
        return OperationResponse.build(aiProviderService.testConnection(id));
    }

    /**
     * 拉取 provider 模型列表（缓存到 provider，供管理端展示与选择默认模型）。
     *
     * @param id Provider ID
     * @return 模型清单
     */
    @RequirePermission(resource = "scrm_ai_provider", action = "read")
    @PostMapping("/{id}/models")
    public OperationResponse<List<ScrmAiProviderDto.AiModelInfo>> fetchModels(
            @PathVariable Long id) {
        return OperationResponse.build(aiProviderService.fetchModels(id));
    }

    /**
     * 设默认文本 provider。
     *
     * @param id Provider ID
     * @return 更新后的 provider
     */
    @RequirePermission(resource = "scrm_ai_provider", action = "update")
    @PostMapping("/{id}/default-text")
    public OperationResponse<ScrmAiProviderDto> setDefaultText(@PathVariable Long id) {
        return OperationResponse.build(aiProviderService.setDefaultText(id));
    }

    /**
     * 设默认多模态 provider。
     *
     * @param id Provider ID
     * @return 更新后的 provider
     */
    @RequirePermission(resource = "scrm_ai_provider", action = "update")
    @PostMapping("/{id}/default-image")
    public OperationResponse<ScrmAiProviderDto> setDefaultImage(@PathVariable Long id) {
        return OperationResponse.build(aiProviderService.setDefaultImage(id));
    }

    /**
     * 启停 provider。
     *
     * @param id      Provider ID
     * @param enabled 是否启用
     * @return 更新后的 provider
     */
    @RequirePermission(resource = "scrm_ai_provider", action = "update")
    @PostMapping("/{id}/enabled")
    public OperationResponse<ScrmAiProviderDto> setEnabled(@PathVariable Long id,
                                                           @RequestParam boolean enabled) {
        return OperationResponse.build(aiProviderService.setEnabled(id, enabled));
    }
}