/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAssetLibraryController.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmAssetCategoryDto;
import org.hiylo.scrm.dto.ScrmAssetDto;
import org.hiylo.scrm.dto.ScrmAssetReviewDto;
import org.hiylo.scrm.dto.ScrmAssetSearchDto;
import org.hiylo.scrm.dto.ScrmAssetUploadDto;
import org.hiylo.scrm.dto.ScrmAssetUsageDto;
import org.hiylo.scrm.entity.ScrmAssetCategoryEntity;
import org.hiylo.scrm.entity.ScrmAssetEntity;
import org.hiylo.scrm.entity.ScrmAssetUsageEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmAssetLibraryService;
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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * SCRM 营销素材库控制器。
 * <p>
 * 提供素材分类 / 素材 / 使用记录 / 搜索 / 统计的完整 REST 接口。素材由团队共享,
 * 运营人员可在营销活动、群发、快捷回复等场景选用, 选用后通过 {@code POST /{id}/use}
 * 自增使用次数, 通过 {@code POST /{id}/download} 自增下载次数。
 * </p>
 * <p>
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/assets")
@RequiredArgsConstructor
public class ScrmAssetLibraryController {

    /** 素材库服务 */
    private final ScrmAssetLibraryService assetLibraryService;

    // ============================================================
    // 素材分类
    // ============================================================

    /**
     * 创建素材分类。
     *
     * @param dto 分类参数
     * @return 创建后的分类
     * @throws ScrmException 参数非法 / 分类编码重复
     */
    @RequirePermission(resource = "scrm_asset", action = "create")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/categories")
    public OperationResponse<ScrmAssetCategoryEntity> createCategory(@Valid @RequestBody ScrmAssetCategoryDto dto)
            throws ScrmException {
        return OperationResponse.build(assetLibraryService.createCategory(dto));
    }

    /**
     * 更新素材分类。
     *
     * @param id  分类 ID
     * @param dto 分类参数
     * @return 更新后的分类
     * @throws ScrmException 分类不存在 / 父分类不存在
     */
    @RequirePermission(resource = "scrm_asset", action = "update")
    @PutMapping("/categories/{id}")
    public OperationResponse<ScrmAssetCategoryEntity> updateCategory(@PathVariable Long id,
                                                                     @RequestBody ScrmAssetCategoryDto dto)
            throws ScrmException {
        return OperationResponse.build(assetLibraryService.updateCategory(id, dto));
    }

    /**
     * 删除素材分类。
     *
     * @param id 分类 ID
     * @return 空响应
     * @throws ScrmException 分类不存在 / 仍有子分类
     */
    @RequirePermission(resource = "scrm_asset", action = "delete")
    @DeleteMapping("/categories/{id}")
    public OperationResponse<Void> deleteCategory(@PathVariable Long id) throws ScrmException {
        assetLibraryService.deleteCategory(id);
        return OperationResponse.build();
    }

    /**
     * 查询分类详情。
     *
     * @param id 分类 ID
     * @return 分类详情
     * @throws ScrmException 分类不存在
     */
    @RequirePermission(resource = "scrm_asset", action = "read")
    @GetMapping("/categories/{id}")
    public OperationResponse<ScrmAssetCategoryEntity> getCategory(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(assetLibraryService.getCategory(id));
    }

    /**
     * 按分类编码查询分类。
     *
     * @param code 分类编码
     * @return 分类详情
     * @throws ScrmException 分类不存在
     */
    @RequirePermission(resource = "scrm_asset", action = "read")
    @GetMapping("/categories/code/{code}")
    public OperationResponse<ScrmAssetCategoryEntity> getCategoryByCode(@PathVariable String code)
            throws ScrmException {
        return OperationResponse.build(assetLibraryService.getCategoryByCode(code));
    }

    /**
     * 分页查询分类, 支持按父分类 / 启用状态 / 关键词过滤。
     *
     * @param parentId 父分类过滤 (可空)
     * @param enabled  启用状态过滤 (可空)
     * @param keyword  关键词过滤 (可空)
     * @param page     页码 (从 0 开始, 默认 0)
     * @param size     每页大小 (默认 20)
     * @return 分类分页结果
     */
    @RequirePermission(resource = "scrm_asset", action = "read")
    @GetMapping("/categories/list")
    public OperationResponse<Page<ScrmAssetCategoryEntity>> listCategories(
            @RequestParam(required = false) Long parentId,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(assetLibraryService.listCategories(parentId, enabled, keyword, pageable));
    }

    /**
     * 查询分类树。
     *
     * @return 顶级分类列表
     */
    @RequirePermission(resource = "scrm_asset", action = "read")
    @GetMapping("/categories/tree")
    public OperationResponse<List<ScrmAssetCategoryEntity>> getCategoryTree() {
        return OperationResponse.build(assetLibraryService.getCategoryTree());
    }

    /**
     * 启用分类。
     *
     * @param id 分类 ID
     * @return 更新后的分类
     * @throws ScrmException 分类不存在
     */
    @RequirePermission(resource = "scrm_asset", action = "update")
    @PostMapping("/categories/{id}/enable")
    public OperationResponse<ScrmAssetCategoryEntity> enableCategory(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(assetLibraryService.enableCategory(id));
    }

    /**
     * 禁用分类。
     *
     * @param id 分类 ID
     * @return 更新后的分类
     * @throws ScrmException 分类不存在
     */
    @RequirePermission(resource = "scrm_asset", action = "update")
    @PostMapping("/categories/{id}/disable")
    public OperationResponse<ScrmAssetCategoryEntity> disableCategory(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(assetLibraryService.disableCategory(id));
    }

    /**
     * 移动分类 (变更父分类与排序值)。
     *
     * @param params 请求体, 含 id / newParentId / newSortOrder
     * @return 更新后的分类
     * @throws ScrmException 分类不存在 / 父分类非法
     */
    @RequirePermission(resource = "scrm_asset", action = "update")
    @PostMapping("/categories/move")
    public OperationResponse<ScrmAssetCategoryEntity> moveCategory(@RequestBody Map<String, Object> params)
            throws ScrmException {
        if (params == null || params.get("id") == null) {
            throw ScrmException.badRequest("分类 ID 不能为空");
        }
        Long id = Long.valueOf(String.valueOf(params.get("id")));
        Long newParentId = params.get("newParentId") != null
                ? Long.valueOf(String.valueOf(params.get("newParentId"))) : null;
        Integer newSortOrder = params.get("newSortOrder") != null
                ? Integer.valueOf(String.valueOf(params.get("newSortOrder"))) : null;
        return OperationResponse.build(assetLibraryService.moveCategory(id, newParentId, newSortOrder));
    }

    /**
     * 更新分类统计 (重新计算 assetCount 与 totalSizeBytes)。
     *
     * @param id 分类 ID
     * @return 更新后的分类
     * @throws ScrmException 分类不存在
     */
    @RequirePermission(resource = "scrm_asset", action = "update")
    @PostMapping("/categories/{id}/stats")
    public OperationResponse<ScrmAssetCategoryEntity> updateCategoryStats(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(assetLibraryService.updateCategoryStats(id));
    }

    // ============================================================
    // 素材
    // ============================================================

    /**
     * 上传素材 (创建记录→生成编码→初始化统计)。
     *
     * @param uploadDto 上传参数
     * @return 创建后的素材
     * @throws ScrmException 参数非法 / 分类不存在
     */
    @RequirePermission(resource = "scrm_asset", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/upload")
    public OperationResponse<ScrmAssetEntity> uploadAsset(@Valid @RequestBody ScrmAssetUploadDto uploadDto)
            throws ScrmException {
        return OperationResponse.build(assetLibraryService.uploadAsset(uploadDto));
    }

    /**
     * 更新素材。
     *
     * @param id  素材 ID
     * @param dto 素材参数
     * @return 更新后的素材
     * @throws ScrmException 素材不存在 / 分类不存在
     */
    @RequirePermission(resource = "scrm_asset", action = "update")
    @PutMapping("/{id}")
    public OperationResponse<ScrmAssetEntity> updateAsset(@PathVariable Long id,
                                                          @RequestBody ScrmAssetDto dto)
            throws ScrmException {
        return OperationResponse.build(assetLibraryService.updateAsset(id, dto));
    }

    /**
     * 删除素材。
     *
     * @param id 素材 ID
     * @return 空响应
     * @throws ScrmException 素材不存在 / 素材使用中
     */
    @RequirePermission(resource = "scrm_asset", action = "delete")
    @DeleteMapping("/{id}")
    public OperationResponse<Void> deleteAsset(@PathVariable Long id) throws ScrmException {
        assetLibraryService.deleteAsset(id);
        return OperationResponse.build();
    }

    /**
     * 查询素材详情 (增加浏览量)。
     *
     * @param id 素材 ID
     * @return 素材详情
     * @throws ScrmException 素材不存在
     */
    @RequirePermission(resource = "scrm_asset", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmAssetEntity> getAsset(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(assetLibraryService.getAsset(id));
    }

    /**
     * 按素材编码查询素材。
     *
     * @param code 素材编码
     * @return 素材详情
     * @throws ScrmException 素材不存在
     */
    @RequirePermission(resource = "scrm_asset", action = "read")
    @GetMapping("/code/{code}")
    public OperationResponse<ScrmAssetEntity> getAssetByCode(@PathVariable String code) throws ScrmException {
        return OperationResponse.build(assetLibraryService.getAssetByCode(code));
    }

    /**
     * 分页查询素材, 支持按分类 / 类型 / 标签 / 状态 / 关键词过滤与排序。
     *
     * @param categoryId 分类过滤 (可空)
     * @param assetType  类型过滤 (可空)
     * @param tags       标签过滤 (可空)
     * @param status     状态过滤 (可空)
     * @param keyword    关键词过滤 (可空)
     * @param sortBy     排序维度: NEWEST/POPULAR/DOWNLOADS/VIEWS/SIZE (默认 NEWEST)
     * @param page       页码 (从 0 开始, 默认 0)
     * @param size       每页大小 (默认 20)
     * @return 素材分页结果
     */
    @RequirePermission(resource = "scrm_asset", action = "read")
    @GetMapping("/list")
    public OperationResponse<Page<ScrmAssetEntity>> listAssets(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String assetType,
            @RequestParam(required = false) String tags,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ScrmAssetSearchDto searchDto = new ScrmAssetSearchDto();
        searchDto.setCategoryId(categoryId);
        searchDto.setAssetType(assetType);
        searchDto.setTags(tags);
        searchDto.setStatus(status);
        searchDto.setKeyword(keyword);
        searchDto.setSortBy(sortBy);
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(assetLibraryService.listAssets(searchDto, pageable));
    }

    /**
     * 发布素材。
     *
     * @param id 素材 ID
     * @return 更新后的素材
     * @throws ScrmException 素材不存在
     */
    @RequirePermission(resource = "scrm_asset", action = "update")
    @PostMapping("/{id}/publish")
    public OperationResponse<ScrmAssetEntity> publishAsset(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(assetLibraryService.publishAsset(id));
    }

    /**
     * 归档素材。
     *
     * @param id 素材 ID
     * @return 更新后的素材
     * @throws ScrmException 素材不存在
     */
    @RequirePermission(resource = "scrm_asset", action = "update")
    @PostMapping("/{id}/archive")
    public OperationResponse<ScrmAssetEntity> archiveAsset(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(assetLibraryService.archiveAsset(id));
    }

    /**
     * 审核素材。
     *
     * @param reviewDto 审核参数
     * @return 更新后的素材
     * @throws ScrmException 素材不存在 / 审核动作非法
     */
    @RequirePermission(resource = "scrm_asset", action = "execute")
    @PostMapping("/review")
    public OperationResponse<ScrmAssetEntity> reviewAsset(@Valid @RequestBody ScrmAssetReviewDto reviewDto)
            throws ScrmException {
        return OperationResponse.build(assetLibraryService.reviewAsset(reviewDto));
    }

    /**
     * 批量审核素材。
     *
     * @param params 请求体, 含 assetIds / action / comment
     * @return 审核结果 (成功数 / 失败数)
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_asset", action = "execute")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/batch-review")
    public OperationResponse<Map<String, Object>> batchReview(@RequestBody Map<String, Object> params)
            throws ScrmException {
        if (params == null || params.get("assetIds") == null || params.get("action") == null) {
            throw ScrmException.badRequest("assetIds 与 action 不能为空");
        }
        @SuppressWarnings("unchecked")
        List<Object> rawIds = (List<Object>) params.get("assetIds");
        List<Long> assetIds = rawIds.stream()
                .map(o -> Long.valueOf(String.valueOf(o)))
                .collect(java.util.stream.Collectors.toList());
        String action = String.valueOf(params.get("action"));
        String comment = params.get("comment") != null ? String.valueOf(params.get("comment")) : null;
        return OperationResponse.build(assetLibraryService.batchReview(assetIds, action, comment));
    }

    /**
     * 复制素材 (生成新编码)。
     *
     * @param id      源素材 ID
     * @param newCode 新素材编码 (可空, 为空时自动生成)
     * @return 新素材
     * @throws ScrmException 素材不存在 / 新编码已存在
     */
    @RequirePermission(resource = "scrm_asset", action = "execute")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/{id}/duplicate")
    public OperationResponse<ScrmAssetEntity> duplicateAsset(@PathVariable Long id,
                                                             @RequestParam(required = false) String newCode)
            throws ScrmException {
        return OperationResponse.build(assetLibraryService.duplicateAsset(id, newCode));
    }

    /**
     * 按分类分页查询素材。
     *
     * @param categoryId 分类 ID
     * @param page       页码 (从 0 开始, 默认 0)
     * @param size       每页大小 (默认 20)
     * @return 素材分页结果
     */
    @RequirePermission(resource = "scrm_asset", action = "read")
    @GetMapping("/by-category/{categoryId}")
    public OperationResponse<Page<ScrmAssetEntity>> getAssetsByCategory(@PathVariable Long categoryId,
                                                                       @RequestParam(defaultValue = "0") int page,
                                                                       @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(assetLibraryService.getAssetsByCategory(categoryId, pageable));
    }

    /**
     * 按类型分页查询素材。
     *
     * @param assetType 素材类型
     * @param page      页码 (从 0 开始, 默认 0)
     * @param size      每页大小 (默认 20)
     * @return 素材分页结果
     */
    @RequirePermission(resource = "scrm_asset", action = "read")
    @GetMapping("/by-type/{assetType}")
    public OperationResponse<Page<ScrmAssetEntity>> getAssetsByType(@PathVariable String assetType,
                                                                    @RequestParam(defaultValue = "0") int page,
                                                                    @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(assetLibraryService.getAssetsByType(assetType, pageable));
    }

    /**
     * 按标签分页查询素材。
     *
     * @param tag  标签
     * @param page 页码 (从 0 开始, 默认 0)
     * @param size 每页大小 (默认 20)
     * @return 素材分页结果
     */
    @RequirePermission(resource = "scrm_asset", action = "read")
    @GetMapping("/by-tag/{tag}")
    public OperationResponse<Page<ScrmAssetEntity>> getAssetsByTag(@PathVariable String tag,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(assetLibraryService.getAssetsByTag(tag, pageable));
    }

    /**
     * 热门素材 (按使用次数倒序)。
     *
     * @param limit 取前 N 条 (默认 10)
     * @return 素材列表
     */
    @RequirePermission(resource = "scrm_asset", action = "read")
    @GetMapping("/popular")
    public OperationResponse<List<ScrmAssetEntity>> getPopularAssets(
            @RequestParam(defaultValue = "10") int limit) {
        return OperationResponse.build(assetLibraryService.getPopularAssets(limit));
    }

    /**
     * 最新素材 (按上传时间倒序)。
     *
     * @param limit 取前 N 条 (默认 10)
     * @return 素材列表
     */
    @RequirePermission(resource = "scrm_asset", action = "read")
    @GetMapping("/recent")
    public OperationResponse<List<ScrmAssetEntity>> getRecentAssets(
            @RequestParam(defaultValue = "10") int limit) {
        return OperationResponse.build(assetLibraryService.getRecentAssets(limit));
    }

    /**
     * 即将过期素材 (days 天内过期)。
     *
     * @param days 天数阈值 (默认 7)
     * @return 素材列表
     */
    @RequirePermission(resource = "scrm_asset", action = "read")
    @GetMapping("/expiring")
    public OperationResponse<List<ScrmAssetEntity>> getExpiringAssets(
            @RequestParam(defaultValue = "7") int days) {
        return OperationResponse.build(assetLibraryService.getExpiringAssets(days));
    }

    /**
     * 素材浏览次数 +1。
     *
     * @param id 素材 ID
     * @return 更新后的素材
     * @throws ScrmException 素材不存在
     */
    @RequirePermission(resource = "scrm_asset", action = "update")
    @PostMapping("/{id}/view")
    public OperationResponse<ScrmAssetEntity> incrementViewCount(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(assetLibraryService.incrementViewCount(id));
    }

    /**
     * 素材下载次数 +1。
     *
     * @param id 素材 ID
     * @return 更新后的素材
     * @throws ScrmException 素材不存在
     */
    @RequirePermission(resource = "scrm_asset", action = "update")
    @PostMapping("/{id}/download")
    public OperationResponse<ScrmAssetEntity> incrementDownloadCount(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(assetLibraryService.incrementDownloadCount(id));
    }

    /**
     * 素材使用次数 +1。
     *
     * @param id            素材 ID
     * @param usageEntity   使用实体 (可空, 如 campaign_id)
     * @return 更新后的素材
     * @throws ScrmException 素材不存在
     */
    @RequirePermission(resource = "scrm_asset", action = "update")
    @PostMapping("/{id}/use")
    public OperationResponse<ScrmAssetEntity> incrementUseCount(@PathVariable Long id,
                                                                @RequestParam(required = false) String usageEntity)
            throws ScrmException {
        return OperationResponse.build(assetLibraryService.incrementUseCount(id, usageEntity));
    }

    // ============================================================
    // 使用记录
    // ============================================================

    /**
     * 记录素材使用。
     *
     * @param usageDto 使用记录参数
     * @return 使用记录
     * @throws ScrmException 参数非法 / 素材不存在
     */
    @RequirePermission(resource = "scrm_asset", action = "execute")
    @RateLimit(capacity = 120, refillTokens = 120, refillPeriodSeconds = 60)
    @PostMapping("/usage/record")
    public OperationResponse<ScrmAssetUsageEntity> recordUsage(@Valid @RequestBody ScrmAssetUsageDto usageDto)
            throws ScrmException {
        return OperationResponse.build(assetLibraryService.recordUsage(usageDto));
    }

    /**
     * 查询使用记录详情。
     *
     * @param id 使用记录 ID
     * @return 使用记录
     * @throws ScrmException 使用记录不存在
     */
    @RequirePermission(resource = "scrm_asset", action = "read")
    @GetMapping("/usage/{id}")
    public OperationResponse<ScrmAssetUsageEntity> getUsage(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(assetLibraryService.getUsage(id));
    }

    /**
     * 分页查询使用记录, 支持按素材 / 使用类型 / 使用模块 / 时间范围过滤。
     *
     * @param assetId     素材 ID (可空)
     * @param usageType   使用类型 (可空)
     * @param usageModule 使用模块 (可空)
     * @param startTime   起始时间 (可空, ISO 格式)
     * @param endTime     截止时间 (可空, ISO 格式)
     * @param page        页码 (从 0 开始, 默认 0)
     * @param size        每页大小 (默认 20)
     * @return 使用记录分页结果
     */
    @RequirePermission(resource = "scrm_asset", action = "read")
    @GetMapping("/usage/list")
    public OperationResponse<Page<ScrmAssetUsageEntity>> listUsage(
            @RequestParam(required = false) Long assetId,
            @RequestParam(required = false) String usageType,
            @RequestParam(required = false) String usageModule,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        LocalDateTime start = parseDateTime(startTime);
        LocalDateTime end = parseDateTime(endTime);
        return OperationResponse.build(assetLibraryService.listUsage(
                assetId, usageType, usageModule, start, end, pageable));
    }

    /**
     * 素材使用统计 (按时间范围过滤)。
     *
     * @param assetId   素材 ID
     * @param startTime 起始时间 (可空, ISO 格式)
     * @param endTime   截止时间 (可空, ISO 格式)
     * @return 统计结果
     * @throws ScrmException 素材不存在
     */
    @RequirePermission(resource = "scrm_asset", action = "read")
    @GetMapping("/usage/stats/{assetId}")
    public OperationResponse<Map<String, Object>> getUsageStats(
            @PathVariable Long assetId,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) throws ScrmException {
        LocalDateTime start = parseDateTime(startTime);
        LocalDateTime end = parseDateTime(endTime);
        return OperationResponse.build(assetLibraryService.getUsageStats(assetId, start, end));
    }

    /**
     * 按模块统计素材使用。
     *
     * @param assetId 素材 ID
     * @return 模块 → 使用次数
     * @throws ScrmException 素材不存在
     */
    @RequirePermission(resource = "scrm_asset", action = "read")
    @GetMapping("/usage/by-module/{assetId}")
    public OperationResponse<Map<String, Object>> getUsageByModule(@PathVariable Long assetId)
            throws ScrmException {
        return OperationResponse.build(assetLibraryService.getUsageByModule(assetId));
    }

    /**
     * 素材使用趋势 (按天聚合)。
     *
     * @param assetId 素材 ID
     * @param days    天数 (默认 30)
     * @return 日期 → 使用次数
     * @throws ScrmException 素材不存在
     */
    @RequirePermission(resource = "scrm_asset", action = "read")
    @GetMapping("/usage/trend/{assetId}")
    public OperationResponse<Map<String, Object>> getUsageTrend(@PathVariable Long assetId,
                                                                @RequestParam(defaultValue = "30") int days)
            throws ScrmException {
        return OperationResponse.build(assetLibraryService.getUsageTrend(assetId, days));
    }

    /**
     * 热门素材 (按使用次数聚合)。
     *
     * @param limit 取前 N 条 (默认 10)
     * @return 热门素材列表
     */
    @RequirePermission(resource = "scrm_asset", action = "read")
    @GetMapping("/usage/popular")
    public OperationResponse<List<Map<String, Object>>> getPopularAssetsByUsage(
            @RequestParam(defaultValue = "10") int limit) {
        return OperationResponse.build(assetLibraryService.getPopularAssetsByUsage(limit));
    }

    /**
     * 清理过期使用记录。
     *
     * @param days 天数阈值 (默认 90)
     * @return 清理的记录数
     */
    @RequirePermission(resource = "scrm_asset", action = "execute")
    @PostMapping("/usage/cleanup")
    public OperationResponse<Map<String, Object>> cleanupUsage(@RequestParam(defaultValue = "90") int days) {
        return OperationResponse.build(assetLibraryService.cleanupUsage(days));
    }

    // ============================================================
    // 搜索
    // ============================================================

    /**
     * 搜索素材 (关键词匹配名称 / 描述 / 标签, 排序按相关度)。
     *
     * @param searchDto 搜索条件
     * @return 素材列表 (按相关度排序)
     */
    @RequirePermission(resource = "scrm_asset", action = "read")
    @PostMapping("/search")
    public OperationResponse<List<ScrmAssetEntity>> search(@RequestBody ScrmAssetSearchDto searchDto) {
        return OperationResponse.build(assetLibraryService.search(searchDto));
    }

    /**
     * 自动补全 (按素材名称前缀匹配)。
     *
     * @param prefix 前缀
     * @return 素材名称列表
     */
    @RequirePermission(resource = "scrm_asset", action = "read")
    @GetMapping("/auto-complete")
    public OperationResponse<List<String>> autoComplete(@RequestParam String prefix) {
        return OperationResponse.build(assetLibraryService.autoComplete(prefix));
    }

    /**
     * 获取所有标签。
     *
     * @return 标签列表
     */
    @RequirePermission(resource = "scrm_asset", action = "read")
    @GetMapping("/tags")
    public OperationResponse<List<String>> getTags() {
        return OperationResponse.build(assetLibraryService.getTags());
    }

    /**
     * 获取所有关键词。
     *
     * @return 关键词列表
     */
    @RequirePermission(resource = "scrm_asset", action = "read")
    @GetMapping("/keywords")
    public OperationResponse<List<String>> getKeywords() {
        return OperationResponse.build(assetLibraryService.getKeywords());
    }

    /**
     * 多条件筛选素材。
     *
     * @param filters 过滤条件 (categoryId / assetType / tags / status / keyword / sortBy)
     * @param page    页码 (从 0 开始, 默认 0)
     * @param size    每页大小 (默认 20)
     * @return 素材分页结果
     */
    @RequirePermission(resource = "scrm_asset", action = "read")
    @PostMapping("/filter")
    public OperationResponse<Page<ScrmAssetEntity>> filterAssets(
            @RequestBody(required = false) Map<String, Object> filters,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(assetLibraryService.filterAssets(filters, pageable));
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 素材统计 (总数 / 各类型数 / 各状态数 / 总大小)。
     *
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_asset", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getAssetStats() {
        return OperationResponse.build(assetLibraryService.getAssetStats());
    }

    /**
     * 分类统计 (各分类素材数与总大小)。
     *
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_asset", action = "read")
    @GetMapping("/stats/categories")
    public OperationResponse<Map<String, Object>> getCategoryStats() {
        return OperationResponse.build(assetLibraryService.getCategoryStats());
    }

    /**
     * 存储统计 (各存储类型素材数与大小分布)。
     *
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_asset", action = "read")
    @GetMapping("/stats/storage")
    public OperationResponse<Map<String, Object>> getStorageStats() {
        return OperationResponse.build(assetLibraryService.getStorageStats());
    }

    /**
     * 使用统计概览 (按时间范围过滤)。
     *
     * @param startTime 起始时间 (可空, ISO 格式)
     * @param endTime   截止时间 (可空, ISO 格式)
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_asset", action = "read")
    @GetMapping("/stats/usage-overview")
    public OperationResponse<Map<String, Object>> getUsageStatsOverview(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        LocalDateTime start = parseDateTime(startTime);
        LocalDateTime end = parseDateTime(endTime);
        return OperationResponse.build(assetLibraryService.getUsageStatsOverview(start, end));
    }

    /**
     * 审核统计 (各审核状态素材数)。
     *
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_asset", action = "read")
    @GetMapping("/stats/reviews")
    public OperationResponse<Map<String, Object>> getReviewStats() {
        return OperationResponse.build(assetLibraryService.getReviewStats());
    }

    /**
     * 趋势统计 (按天聚合最近 days 天的上传量)。
     *
     * @param days 天数 (默认 30)
     * @return 日期 → 上传量
     */
    @RequirePermission(resource = "scrm_asset", action = "read")
    @GetMapping("/stats/trend")
    public OperationResponse<Map<String, Object>> getTrend(@RequestParam(defaultValue = "30") int days) {
        return OperationResponse.build(assetLibraryService.getTrend(days));
    }

    // ============================================================
    // 内部工具
    // ============================================================

    /**
     * 解析 ISO 日期时间字符串 (可空返回 null)。
     *
     * @param value 字符串值
     * @return LocalDateTime 或 null
     */
    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            log.warn("解析日期时间失败, 忽略过滤: value={}", value);
            return null;
        }
    }
}
