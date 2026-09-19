/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSpeechController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmSpeechDto;
import org.hiylo.scrm.entity.ScrmSpeechEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmSpeechLibraryService;
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

import java.util.Map;

/**
 * SCRM 话术条目控制器。
 * <p>
 * 提供话术条目的增删改查、关键字搜索、使用次数 / 点赞数自增与状态切换接口。
 * 话术由团队共享, 客服或运营人员可在发送消息时按分类 / 平台 / 场景选用, 选用后通过
 * {@code POST /{id}/use} 自增使用次数, 通过 {@code POST /{id}/like} 自增点赞数。
 * </p>
 * <p>
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/speeches")
@RequiredArgsConstructor
public class ScrmSpeechController {

    /** 话术库服务 */
    private final ScrmSpeechLibraryService speechLibraryService;

    /**
     * 创建话术。
     *
     * @param dto 话术参数
     * @return 创建后的话术
     * @throws ScrmException 分类不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_speech", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping
    public OperationResponse<ScrmSpeechEntity> create(@Valid @RequestBody ScrmSpeechDto dto)
            throws ScrmException {
        return OperationResponse.build(speechLibraryService.createSpeech(dto));
    }

    /**
     * 更新话术。
     *
     * @param id  话术 ID
     * @param dto 话术参数
     * @return 更新后的话术
     * @throws ScrmException 话术不存在 / 分类不存在
     */
    @RequirePermission(resource = "scrm_speech", action = "update")
    @PutMapping("/{id}")
    public OperationResponse<ScrmSpeechEntity> update(@PathVariable Long id,
                                                       @RequestBody ScrmSpeechDto dto)
            throws ScrmException {
        return OperationResponse.build(speechLibraryService.updateSpeech(id, dto));
    }

    /**
     * 删除话术。
     *
     * @param id 话术 ID
     * @return 空响应
     * @throws ScrmException 话术不存在
     */
    @RequirePermission(resource = "scrm_speech", action = "delete")
    @DeleteMapping("/{id}")
    public OperationResponse<Void> delete(@PathVariable Long id) throws ScrmException {
        speechLibraryService.deleteSpeech(id);
        return OperationResponse.build();
    }

    /**
     * 查询话术详情。
     *
     * @param id 话术 ID
     * @return 话术详情
     * @throws ScrmException 话术不存在
     */
    @RequirePermission(resource = "scrm_speech", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmSpeechEntity> get(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(speechLibraryService.getSpeech(id));
    }

    /**
     * 分页查询话术列表, 支持按分类、平台、场景与关键字过滤。
     *
     * @param categoryId   分类过滤（可空）
     * @param platformType 平台过滤（可空）
     * @param scenario     场景过滤（可空）
     * @param keyword      关键字过滤（可空）
     * @param page         页码（从 0 开始, 默认 0）
     * @param size         每页大小（默认 20）
     * @return 话术分页结果
     */
    @RequirePermission(resource = "scrm_speech", action = "read")
    @GetMapping("/list")
    public OperationResponse<Page<ScrmSpeechEntity>> list(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String platformType,
            @RequestParam(required = false) String scenario,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(speechLibraryService.listSpeeches(
                categoryId, platformType, scenario, keyword, pageable));
    }

    /**
     * 关键字搜索话术 (按标题 / 内容 / 标签模糊匹配)。
     *
     * @param keyword 关键字
     * @param page    页码（从 0 开始, 默认 0）
     * @param size    每页大小（默认 20）
     * @return 话术分页结果
     */
    @RequirePermission(resource = "scrm_speech", action = "read")
    @GetMapping("/search")
    public OperationResponse<Page<ScrmSpeechEntity>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(speechLibraryService.searchSpeeches(keyword, pageable));
    }

    /**
     * 话术使用次数 +1。
     *
     * @param id 话术 ID
     * @return 更新后的话术
     * @throws ScrmException 话术不存在
     */
    @RequirePermission(resource = "scrm_speech", action = "update")
    @PostMapping("/{id}/use")
    public OperationResponse<ScrmSpeechEntity> use(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(speechLibraryService.incrementUseCount(id));
    }

    /**
     * 话术点赞数 +1。
     *
     * @param id 话术 ID
     * @return 更新后的话术
     * @throws ScrmException 话术不存在
     */
    @RequirePermission(resource = "scrm_speech", action = "update")
    @PostMapping("/{id}/like")
    public OperationResponse<ScrmSpeechEntity> like(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(speechLibraryService.likeSpeech(id));
    }

    /**
     * 切换话术状态 (ACTIVE / INACTIVE / DRAFT)。
     *
     * @param id     话术 ID
     * @param body   请求体, 必须包含 status 字段
     * @return 更新后的话术
     * @throws ScrmException 话术不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_speech", action = "update")
    @PostMapping("/{id}/toggle-status")
    public OperationResponse<ScrmSpeechEntity> toggleStatus(@PathVariable Long id,
                                                              @RequestBody Map<String, String> body)
            throws ScrmException {
        String status = body == null ? null : body.get("status");
        return OperationResponse.build(speechLibraryService.toggleSpeechStatus(id, status));
    }
}
