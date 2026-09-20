/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSpeechCategoryController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmSpeechCategoryDto;
import org.hiylo.scrm.entity.ScrmSpeechCategoryEntity;
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

import java.util.List;

/**
 * SCRM 话术分类控制器。
 * <p>
 * 提供话术分类的增删改查与分类树查询接口。分类支持多级父子结构,
 * 通过 {@code GET /tree} 加载当前账号全量分类并构建树形结构, 供前端侧边栏渲染。
 * </p>
 * <p>
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/speech-categories")
@RequiredArgsConstructor
public class ScrmSpeechCategoryController {

    /** 话术库服务 */
    private final ScrmSpeechLibraryService speechLibraryService;

    /**
     * 创建话术分类。
     *
     * @param dto 分类参数
     * @return 创建后的分类
     * @throws ScrmException 父分类不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_speech_category", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping
    public OperationResponse<ScrmSpeechCategoryEntity> create(@Valid @RequestBody ScrmSpeechCategoryDto dto)
            throws ScrmException {
        return OperationResponse.build(speechLibraryService.createCategory(dto));
    }

    /**
     * 更新话术分类。
     *
     * @param id  分类 ID
     * @param dto 分类参数
     * @return 更新后的分类
     * @throws ScrmException 分类不存在 / 父分类不存在 / 自引用
     */
    @RequirePermission(resource = "scrm_speech_category", action = "update")
    @PutMapping("/{id}")
    public OperationResponse<ScrmSpeechCategoryEntity> update(@PathVariable Long id,
                                                                @RequestBody ScrmSpeechCategoryDto dto)
            throws ScrmException {
        return OperationResponse.build(speechLibraryService.updateCategory(id, dto));
    }

    /**
     * 删除话术分类。
     *
     * @param id 分类 ID
     * @return 空响应
     * @throws ScrmException 分类不存在 / 仍有子分类
     */
    @RequirePermission(resource = "scrm_speech_category", action = "delete")
    @DeleteMapping("/{id}")
    public OperationResponse<Void> delete(@PathVariable Long id) throws ScrmException {
        speechLibraryService.deleteCategory(id);
        return OperationResponse.build();
    }

    /**
     * 查询分类详情。
     *
     * @param id 分类 ID
     * @return 分类详情
     * @throws ScrmException 分类不存在
     */
    @RequirePermission(resource = "scrm_speech_category", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmSpeechCategoryEntity> get(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(speechLibraryService.getCategory(id));
    }

    /**
     * 分页查询子分类列表。
     *
     * @param parentId 父分类 ID（可空, 空表示顶级分类）
     * @param page     页码（从 0 开始, 默认 0）
     * @param size     每页大小（默认 20）
     * @return 分类分页结果
     */
    @RequirePermission(resource = "scrm_speech_category", action = "read")
    @GetMapping("/list")
    public OperationResponse<Page<ScrmSpeechCategoryEntity>> list(
            @RequestParam(required = false) Long parentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(speechLibraryService.listCategories(parentId, pageable));
    }

    /**
     * 查询分类树 (顶级分类列表, 含 children 子节点)。
     *
     * @return 顶级分类列表
     */
    @RequirePermission(resource = "scrm_speech_category", action = "read")
    @GetMapping("/tree")
    public OperationResponse<List<ScrmSpeechCategoryEntity>> tree() {
        return OperationResponse.build(speechLibraryService.getCategoryTree());
    }
}
