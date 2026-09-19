/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMaterialController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmMaterialDto;
import org.hiylo.scrm.entity.ScrmMaterialEntity;
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

/**
 * SCRM 素材库控制器。
 * <p>
 * 提供素材库的增删改查、关键字搜索与下载次数自增接口。素材支持 IMAGE / VIDEO / FILE /
 * AUDIO / LINK 五种类型, 客服或运营人员可在话术或消息中引用素材, 通过
 * {@code POST /{id}/download} 自增下载次数用于使用统计。
 * </p>
 * <p>
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/materials")
@RequiredArgsConstructor
public class ScrmMaterialController {

    /** 话术库服务 */
    private final ScrmSpeechLibraryService speechLibraryService;

    /**
     * 创建素材。
     *
     * @param dto 素材参数
     * @return 创建后的素材
     * @throws ScrmException 分类不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_material", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping
    public OperationResponse<ScrmMaterialEntity> create(@Valid @RequestBody ScrmMaterialDto dto)
            throws ScrmException {
        return OperationResponse.build(speechLibraryService.createMaterial(dto));
    }

    /**
     * 更新素材。
     *
     * @param id  素材 ID
     * @param dto 素材参数
     * @return 更新后的素材
     * @throws ScrmException 素材不存在 / 分类不存在
     */
    @RequirePermission(resource = "scrm_material", action = "update")
    @PutMapping("/{id}")
    public OperationResponse<ScrmMaterialEntity> update(@PathVariable Long id,
                                                          @RequestBody ScrmMaterialDto dto)
            throws ScrmException {
        return OperationResponse.build(speechLibraryService.updateMaterial(id, dto));
    }

    /**
     * 删除素材。
     *
     * @param id 素材 ID
     * @return 空响应
     * @throws ScrmException 素材不存在
     */
    @RequirePermission(resource = "scrm_material", action = "delete")
    @DeleteMapping("/{id}")
    public OperationResponse<Void> delete(@PathVariable Long id) throws ScrmException {
        speechLibraryService.deleteMaterial(id);
        return OperationResponse.build();
    }

    /**
     * 查询素材详情。
     *
     * @param id 素材 ID
     * @return 素材详情
     * @throws ScrmException 素材不存在
     */
    @RequirePermission(resource = "scrm_material", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmMaterialEntity> get(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(speechLibraryService.getMaterial(id));
    }

    /**
     * 分页查询素材列表, 支持按类型、分类与关键字过滤。
     *
     * @param materialType 类型过滤（可空）
     * @param categoryId   分类过滤（可空）
     * @param keyword      关键字过滤（可空）
     * @param page         页码（从 0 开始, 默认 0）
     * @param size         每页大小（默认 20）
     * @return 素材分页结果
     */
    @RequirePermission(resource = "scrm_material", action = "read")
    @GetMapping("/list")
    public OperationResponse<Page<ScrmMaterialEntity>> list(
            @RequestParam(required = false) String materialType,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(speechLibraryService.listMaterials(materialType, categoryId, keyword, pageable));
    }

    /**
     * 素材下载次数 +1。
     *
     * @param id 素材 ID
     * @return 更新后的素材
     * @throws ScrmException 素材不存在
     */
    @RequirePermission(resource = "scrm_material", action = "read")
    @PostMapping("/{id}/download")
    public OperationResponse<ScrmMaterialEntity> download(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(speechLibraryService.incrementDownloadCount(id));
    }
}
