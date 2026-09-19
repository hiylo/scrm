/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmDataDictionaryController.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmDataDictionaryDto;
import org.hiylo.scrm.dto.ScrmDataDictionaryItemDto;
import org.hiylo.scrm.dto.ScrmDataDictionaryUsageDto;
import org.hiylo.scrm.dto.ScrmDictBatchImportDto;
import org.hiylo.scrm.dto.ScrmDictQueryDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmDataDictionaryService;
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
 * SCRM 数据字典管理控制器。
 * <p>
 * 提供字典 / 字典项 / 缓存 / 使用记录 / 导入导出 / 统计的完整 REST 接口。
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/data-dictionaries")
@RequiredArgsConstructor
public class ScrmDataDictionaryController {

    /** 数据字典服务 */
    private final ScrmDataDictionaryService scrmDataDictionaryService;

    // ============================================================
    // 字典 CRUD
    // ============================================================

    /**
     * 创建字典。
     *
     * @param dto 字典参数
     * @return 创建后的字典
     * @throws ScrmException 参数非法 / 字典编码重复
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "create")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60, message = "创建字典过于频繁，请稍后重试")
    @PostMapping
    public OperationResponse<ScrmDataDictionaryDto> createDictionary(@Valid @RequestBody ScrmDataDictionaryDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmDataDictionaryService.createDictionary(dto));
    }

    /**
     * 更新字典 (字段非空才覆盖)。
     *
     * @param id  字典 ID
     * @param dto 字典参数
     * @return 更新后的字典
     * @throws ScrmException 字典不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "update")
    @PutMapping("/{id}")
    public OperationResponse<ScrmDataDictionaryDto> updateDictionary(@PathVariable Long id,
                                                                     @RequestBody ScrmDataDictionaryDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmDataDictionaryService.updateDictionary(id, dto));
    }

    /**
     * 删除字典 (级联清理字典项与使用记录)。
     *
     * @param id 字典 ID
     * @return 空响应
     * @throws ScrmException 字典不存在 / 系统内置字典不允许删除
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "delete")
    @DeleteMapping("/{id}")
    public OperationResponse<Void> deleteDictionary(@PathVariable Long id) throws ScrmException {
        scrmDataDictionaryService.deleteDictionary(id);
        return OperationResponse.build();
    }

    /**
     * 查询字典详情。
     *
     * @param id 字典 ID
     * @return 字典详情
     * @throws ScrmException 字典不存在
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmDataDictionaryDto> getDictionary(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmDataDictionaryService.getDictionary(id));
    }

    /**
     * 按字典编码查询字典。
     *
     * @param code 字典编码
     * @return 字典详情
     * @throws ScrmException 字典不存在
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "read")
    @GetMapping("/code/{code}")
    public OperationResponse<ScrmDataDictionaryDto> getDictionaryByCode(@PathVariable String code)
            throws ScrmException {
        return OperationResponse.build(scrmDataDictionaryService.getDictionaryByCode(code));
    }

    /**
     * 分页查询字典, 支持按类型 / 分类 / 模块 / 启用状态 / 关键词过滤。
     *
     * @param queryDto 查询条件
     * @param page     页码 (从 0 开始, 默认 0)
     * @param size     每页大小 (默认 20)
     * @return 字典分页结果 (按排序值与创建时间倒序)
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "read")
    @PostMapping("/list")
    public OperationResponse<Page<ScrmDataDictionaryDto>> listDictionaries(
            @RequestBody(required = false) ScrmDictQueryDto queryDto,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "sortOrder")
                        .and(Sort.by(Sort.Direction.DESC, "createTime")));
        return OperationResponse.build(scrmDataDictionaryService.listDictionaries(
                queryDto != null ? queryDto : new ScrmDictQueryDto(), pageable));
    }

    /**
     * 启用字典。
     *
     * @param id 字典 ID
     * @return 更新后的字典
     * @throws ScrmException 字典不存在
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "update")
    @PostMapping("/{id}/enable")
    public OperationResponse<ScrmDataDictionaryDto> enableDictionary(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmDataDictionaryService.enableDictionary(id));
    }

    /**
     * 禁用字典。
     *
     * @param id 字典 ID
     * @return 更新后的字典
     * @throws ScrmException 字典不存在 / 系统内置字典不允许禁用
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "update")
    @PostMapping("/{id}/disable")
    public OperationResponse<ScrmDataDictionaryDto> disableDictionary(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmDataDictionaryService.disableDictionary(id));
    }

    /**
     * 复制字典 (含字典项)。
     *
     * @param id      源字典 ID
     * @param newCode 新字典编码
     * @return 新字典
     * @throws ScrmException 字典不存在 / 新编码已存在
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "execute")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/{id}/copy")
    public OperationResponse<ScrmDataDictionaryDto> copyDictionary(@PathVariable Long id,
                                                                    @RequestParam String newCode)
            throws ScrmException {
        return OperationResponse.build(scrmDataDictionaryService.copyDictionary(id, newCode));
    }

    /**
     * 合并字典 (源字典字典项合并到目标字典, 重复项跳过)。
     *
     * @param params 请求体, 含 sourceId 与 targetId
     * @return 目标字典
     * @throws ScrmException 字典不存在 / 源与目标相同
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "execute")
    @PostMapping("/merge")
    public OperationResponse<ScrmDataDictionaryDto> mergeDictionaries(@RequestBody Map<String, Long> params)
            throws ScrmException {
        if (params == null || params.get("sourceId") == null || params.get("targetId") == null) {
            throw ScrmException.badRequest("sourceId 与 targetId 不能为空");
        }
        return OperationResponse.build(scrmDataDictionaryService.mergeDictionaries(
                params.get("sourceId"), params.get("targetId")));
    }

    /**
     * 更新字典统计 (重新计算 itemCount 与 usageCount)。
     *
     * @param id 字典 ID
     * @return 更新后的字典
     * @throws ScrmException 字典不存在
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "update")
    @PostMapping("/{id}/stats")
    public OperationResponse<ScrmDataDictionaryDto> updateDictionaryStats(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmDataDictionaryService.updateDictionaryStats(id));
    }

    /**
     * 字典树 (分类 → 字典列表)。
     *
     * @return 分类 → 字典列表 映射
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "read")
    @GetMapping("/tree")
    public OperationResponse<Map<String, List<ScrmDataDictionaryDto>>> getDictionaryTree() {
        return OperationResponse.build(scrmDataDictionaryService.getDictionaryTree());
    }

    // ============================================================
    // 字典项 CRUD
    // ============================================================

    /**
     * 创建字典项。
     *
     * @param dto 字典项参数
     * @return 创建后的字典项
     * @throws ScrmException 参数非法 / 字典不存在
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "create")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/items")
    public OperationResponse<ScrmDataDictionaryItemDto> createItem(@Valid @RequestBody ScrmDataDictionaryItemDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmDataDictionaryService.createItem(dto));
    }

    /**
     * 更新字典项 (字段非空才覆盖)。
     *
     * @param id  字典项 ID
     * @param dto 字典项参数
     * @return 更新后的字典项
     * @throws ScrmException 字典项不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "update")
    @PutMapping("/items/{id}")
    public OperationResponse<ScrmDataDictionaryItemDto> updateItem(@PathVariable Long id,
                                                                    @RequestBody ScrmDataDictionaryItemDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmDataDictionaryService.updateItem(id, dto));
    }

    /**
     * 删除字典项 (级联清理子项与使用记录)。
     *
     * @param id 字典项 ID
     * @return 空响应
     * @throws ScrmException 字典项不存在
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "delete")
    @DeleteMapping("/items/{id}")
    public OperationResponse<Void> deleteItem(@PathVariable Long id) throws ScrmException {
        scrmDataDictionaryService.deleteItem(id);
        return OperationResponse.build();
    }

    /**
     * 查询字典项详情。
     *
     * @param id 字典项 ID
     * @return 字典项详情
     * @throws ScrmException 字典项不存在
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "read")
    @GetMapping("/items/{id}")
    public OperationResponse<ScrmDataDictionaryItemDto> getItem(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmDataDictionaryService.getItem(id));
    }

    /**
     * 按字典编码 + 字典项值查询。
     *
     * @param dictCode  字典编码
     * @param itemValue 字典项值
     * @return 字典项详情
     * @throws ScrmException 字典项不存在
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "read")
    @GetMapping("/items/by-value")
    public OperationResponse<ScrmDataDictionaryItemDto> getItemByValue(
            @RequestParam String dictCode, @RequestParam String itemValue) throws ScrmException {
        return OperationResponse.build(scrmDataDictionaryService.getItemByValue(dictCode, itemValue));
    }

    /**
     * 按字典编码 + 字典项编码查询。
     *
     * @param dictCode 字典编码
     * @param itemCode 字典项编码
     * @return 字典项详情
     * @throws ScrmException 字典项不存在
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "read")
    @GetMapping("/items/by-code")
    public OperationResponse<ScrmDataDictionaryItemDto> getItemByCode(
            @RequestParam String dictCode, @RequestParam String itemCode) throws ScrmException {
        return OperationResponse.build(scrmDataDictionaryService.getItemByCode(dictCode, itemCode));
    }

    /**
     * 分页查询字典项, 支持按字典 ID / 父项 / 启用状态 / 关键词过滤。
     *
     * @param dictId   字典 ID 过滤 (可空)
     * @param parentId 父项 ID 过滤 (可空)
     * @param enabled  启用状态过滤 (可空)
     * @param keyword  关键词过滤 (可空)
     * @param page     页码 (从 0 开始, 默认 0)
     * @param size     每页大小 (默认 20)
     * @return 字典项分页结果 (按排序值升序)
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "read")
    @GetMapping("/items/list")
    public OperationResponse<Page<ScrmDataDictionaryItemDto>> listItems(
            @RequestParam(required = false) Long dictId,
            @RequestParam(required = false) Long parentId,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "sortOrder")
                        .and(Sort.by(Sort.Direction.DESC, "createTime")));
        return OperationResponse.build(scrmDataDictionaryService.listItems(dictId,
                parentId, enabled, keyword, pageable));
    }

    /**
     * 按字典编码获取所有字典项。
     *
     * @param dictCode 字典编码
     * @return 字典项列表
     * @throws ScrmException 字典不存在
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "read")
    @GetMapping("/items/by-dict/{dictCode}")
    public OperationResponse<List<ScrmDataDictionaryItemDto>> getItemsByDictCode(@PathVariable String dictCode)
            throws ScrmException {
        return OperationResponse.build(scrmDataDictionaryService.getItemsByDictCode(dictCode));
    }

    /**
     * 树形字典项。
     *
     * @param dictCode 字典编码
     * @return 树形结构
     * @throws ScrmException 字典不存在
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "read")
    @GetMapping("/items/tree/{dictCode}")
    public OperationResponse<List<Map<String, Object>>> getTreeItems(@PathVariable String dictCode)
            throws ScrmException {
        return OperationResponse.build(scrmDataDictionaryService.getTreeItems(dictCode));
    }

    /**
     * 启用字典项。
     *
     * @param id 字典项 ID
     * @return 更新后的字典项
     * @throws ScrmException 字典项不存在
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "update")
    @PostMapping("/items/{id}/enable")
    public OperationResponse<ScrmDataDictionaryItemDto> enableItem(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmDataDictionaryService.enableItem(id));
    }

    /**
     * 禁用字典项。
     *
     * @param id 字典项 ID
     * @return 更新后的字典项
     * @throws ScrmException 字典项不存在
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "update")
    @PostMapping("/items/{id}/disable")
    public OperationResponse<ScrmDataDictionaryItemDto> disableItem(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmDataDictionaryService.disableItem(id));
    }

    /**
     * 设为默认 (清除同字典内其他默认项)。
     *
     * @param id 字典项 ID
     * @return 更新后的字典项
     * @throws ScrmException 字典项不存在
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "update")
    @PostMapping("/items/{id}/default")
    public OperationResponse<ScrmDataDictionaryItemDto> setDefault(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmDataDictionaryService.setDefault(id));
    }

    /**
     * 移动字典项 (变更父项与排序值)。
     *
     * @param params 请求体, 含 id / newParentId / newSortOrder
     * @return 更新后的字典项
     * @throws ScrmException 字典项不存在 / 父项非法
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "update")
    @PostMapping("/items/move")
    public OperationResponse<ScrmDataDictionaryItemDto> moveItem(@RequestBody Map<String, Object> params)
            throws ScrmException {
        if (params == null || params.get("id") == null) {
            throw ScrmException.badRequest("字典项 ID 不能为空");
        }
        Long id = Long.valueOf(String.valueOf(params.get("id")));
        Long newParentId = params.get("newParentId") != null
                ? Long.valueOf(String.valueOf(params.get("newParentId"))) : null;
        Integer newSortOrder = params.get("newSortOrder") != null
                ? Integer.valueOf(String.valueOf(params.get("newSortOrder"))) : null;
        return OperationResponse.build(scrmDataDictionaryService.moveItem(id, newParentId, newSortOrder));
    }

    /**
     * 批量导入字典项。
     *
     * @param batchImportDto 批量导入参数
     * @return 导入结果 (新增数 / 更新数 / 跳过数)
     * @throws ScrmException 字典不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "execute")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/items/batch-import")
    public OperationResponse<Map<String, Object>> batchImport(
            @Valid @RequestBody ScrmDictBatchImportDto batchImportDto) throws ScrmException {
        return OperationResponse.build(scrmDataDictionaryService.batchImport(batchImportDto));
    }

    /**
     * 批量更新字典项。
     *
     * @param updates 字典项更新列表
     * @return 更新数量
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "update")
    @PostMapping("/items/batch-update")
    public OperationResponse<Map<String, Object>> batchUpdateItems(
            @RequestBody List<ScrmDataDictionaryItemDto> updates) throws ScrmException {
        return OperationResponse.build(scrmDataDictionaryService.batchUpdateItems(updates));
    }

    /**
     * 重排序字典项。
     *
     * @param params 请求体, 含 dictId 与 itemOrders (字典项 ID 顺序列表)
     * @return 重排序数量
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "update")
    @PostMapping("/items/reorder")
    public OperationResponse<Map<String, Object>> reorderItems(@RequestBody Map<String, Object> params)
            throws ScrmException {
        if (params == null || params.get("dictId") == null || params.get("itemOrders") == null) {
            throw ScrmException.badRequest("dictId 与 itemOrders 不能为空");
        }
        Long dictId = Long.valueOf(String.valueOf(params.get("dictId")));
        @SuppressWarnings("unchecked")
        List<Object> rawOrders = (List<Object>) params.get("itemOrders");
        List<Long> itemOrders = rawOrders.stream()
                .map(o -> Long.valueOf(String.valueOf(o)))
                .collect(java.util.stream.Collectors.toList());
        return OperationResponse.build(scrmDataDictionaryService.reorderItems(dictId, itemOrders));
    }

    /**
     * 按路径查询字典项。
     *
     * @param dictCode 字典编码
     * @param path     路径前缀 (如 '1/5/')
     * @return 字典项列表
     * @throws ScrmException 字典不存在
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "read")
    @GetMapping("/items/by-path")
    public OperationResponse<List<ScrmDataDictionaryItemDto>> getItemsByPath(
            @RequestParam String dictCode, @RequestParam String path) throws ScrmException {
        return OperationResponse.build(scrmDataDictionaryService.getItemsByPath(dictCode, path));
    }

    // ============================================================
    // 缓存
    // ============================================================

    /**
     * 获取缓存的字典项 (缓存未命中自动加载)。
     *
     * @param dictCode 字典编码
     * @return 字典项列表 (仅启用且可见项)
     * @throws ScrmException 字典不存在
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "read")
    @GetMapping("/cache/{dictCode}")
    public OperationResponse<List<ScrmDataDictionaryItemDto>> getCachedItems(@PathVariable String dictCode)
            throws ScrmException {
        return OperationResponse.build(scrmDataDictionaryService.getCachedItems(dictCode));
    }

    /**
     * 刷新字典缓存。
     *
     * @param dictCode 字典编码
     * @return 刷新后的字典项列表
     * @throws ScrmException 字典不存在
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "execute")
    @PostMapping("/cache/refresh/{dictCode}")
    public OperationResponse<List<ScrmDataDictionaryItemDto>> refreshCache(@PathVariable String dictCode)
            throws ScrmException {
        return OperationResponse.build(scrmDataDictionaryService.refreshCache(dictCode));
    }

    /**
     * 清除指定字典缓存。
     *
     * @param dictCode 字典编码
     * @return 空响应
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "execute")
    @DeleteMapping("/cache/{dictCode}")
    public OperationResponse<Void> clearCache(@PathVariable String dictCode) {
        scrmDataDictionaryService.clearCache(dictCode);
        return OperationResponse.build();
    }

    /**
     * 清除所有字典缓存。
     *
     * @return 清除的缓存条目数
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "execute")
    @DeleteMapping("/cache/all")
    public OperationResponse<Map<String, Object>> clearAllCache() {
        return OperationResponse.build(scrmDataDictionaryService.clearAllCache());
    }

    /**
     * 缓存统计。
     *
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "read")
    @GetMapping("/cache/stats")
    public OperationResponse<Map<String, Object>> getCacheStats() {
        return OperationResponse.build(scrmDataDictionaryService.getCacheStats());
    }

    // ============================================================
    // 使用记录
    // ============================================================

    /**
     * 记录字典使用。
     *
     * @param usageDto 使用记录参数
     * @return 使用记录
     * @throws ScrmException 参数非法 / 字典不存在
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "execute")
    @RateLimit(capacity = 120, refillTokens = 120, refillPeriodSeconds = 60)
    @PostMapping("/usage/record")
    public OperationResponse<ScrmDataDictionaryUsageDto> recordUsage(
            @Valid @RequestBody ScrmDataDictionaryUsageDto usageDto) throws ScrmException {
        return OperationResponse.build(scrmDataDictionaryService.recordUsage(usageDto));
    }

    /**
     * 字典使用统计 (按时间范围过滤)。
     *
     * @param dictId    字典 ID
     * @param startTime 起始时间 (可空, ISO 格式)
     * @param endTime   截止时间 (可空, ISO 格式)
     * @return 统计结果
     * @throws ScrmException 字典不存在
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "read")
    @GetMapping("/usage/stats/{dictId}")
    public OperationResponse<Map<String, Object>> getUsageStats(
            @PathVariable Long dictId,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) throws ScrmException {
        org.springframework.format.annotation.DateTimeFormat.ISO iso =
                org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME;
        java.time.LocalDateTime start = parseDateTime(startTime, iso);
        java.time.LocalDateTime end = parseDateTime(endTime, iso);
        return OperationResponse.build(scrmDataDictionaryService.getUsageStats(dictId, start, end));
    }

    /**
     * 按模块统计字典使用。
     *
     * @param dictId 字典 ID
     * @return 模块 → 使用次数
     * @throws ScrmException 字典不存在
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "read")
    @GetMapping("/usage/by-module/{dictId}")
    public OperationResponse<Map<String, Object>> getUsageByModule(@PathVariable Long dictId)
            throws ScrmException {
        return OperationResponse.build(scrmDataDictionaryService.getUsageByModule(dictId));
    }

    /**
     * 查询未使用的字典项。
     *
     * @param dictId 字典 ID
     * @param days   天数阈值 (默认 30)
     * @return 未使用的字典项列表
     * @throws ScrmException 字典不存在
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "read")
    @GetMapping("/usage/unused/{dictId}")
    public OperationResponse<List<ScrmDataDictionaryItemDto>> getUnusedItems(
            @PathVariable Long dictId, @RequestParam(defaultValue = "30") int days)
            throws ScrmException {
        return OperationResponse.build(scrmDataDictionaryService.getUnusedItems(dictId, days));
    }

    /**
     * 热门字典项。
     *
     * @param dictId 字典 ID
     * @param limit  取前 N 条 (默认 10)
     * @return 热门字典项列表
     * @throws ScrmException 字典不存在
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "read")
    @GetMapping("/usage/popular/{dictId}")
    public OperationResponse<List<Map<String, Object>>> getPopularItems(
            @PathVariable Long dictId, @RequestParam(defaultValue = "10") int limit)
            throws ScrmException {
        return OperationResponse.build(scrmDataDictionaryService.getPopularItems(dictId, limit));
    }

    /**
     * 清理过期使用记录。
     *
     * @param days 天数阈值 (默认 90)
     * @return 清理的记录数
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "execute")
    @PostMapping("/usage/cleanup")
    public OperationResponse<Map<String, Object>> cleanupUsage(@RequestParam(defaultValue = "90") int days) {
        return OperationResponse.build(scrmDataDictionaryService.cleanupUsage(days));
    }

    // ============================================================
    // 导入导出
    // ============================================================

    /**
     * 导出字典 (含字典元信息与全部字典项)。
     *
     * @param dictCode 字典编码
     * @return 导出数据
     * @throws ScrmException 字典不存在
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "read")
    @GetMapping("/export/{dictCode}")
    public OperationResponse<Map<String, Object>> exportDictionary(@PathVariable String dictCode)
            throws ScrmException {
        return OperationResponse.build(scrmDataDictionaryService.exportDictionary(dictCode));
    }

    /**
     * 导出全部字典。
     *
     * @return 全部字典导出数据列表
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "read")
    @GetMapping("/export/all")
    public OperationResponse<List<Map<String, Object>>> exportAllDictionaries() {
        return OperationResponse.build(scrmDataDictionaryService.exportAllDictionaries());
    }

    /**
     * 导入字典 (字典元信息 + 字典项)。
     *
     * @param importData 导入数据
     * @return 导入结果
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "execute")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/export/import")
    public OperationResponse<Map<String, Object>> importDictionary(@RequestBody Map<String, Object> importData)
            throws ScrmException {
        return OperationResponse.build(scrmDataDictionaryService.importDictionary(importData));
    }

    /**
     * 获取导入模板。
     *
     * @return 模板数据
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "read")
    @GetMapping("/export/template")
    public OperationResponse<Map<String, Object>> getExportTemplate() {
        return OperationResponse.build(scrmDataDictionaryService.getExportTemplate());
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 字典统计 (总数 / 各类型 / 各分类 / 系统数 / 自定义数)。
     *
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getDictionaryStats() {
        return OperationResponse.build(scrmDataDictionaryService.getDictionaryStats());
    }

    /**
     * 字典项统计。
     *
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "read")
    @GetMapping("/stats/items")
    public OperationResponse<Map<String, Object>> getItemStats() {
        return OperationResponse.build(scrmDataDictionaryService.getItemStats());
    }

    /**
     * 模块统计。
     *
     * @return 模块 → 统计信息
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "read")
    @GetMapping("/stats/modules")
    public OperationResponse<Map<String, Object>> getModuleStats() {
        return OperationResponse.build(scrmDataDictionaryService.getModuleStats());
    }

    /**
     * 字典健康度 (空字典 / 无引用 / 禁用项数)。
     *
     * @return 健康度信息
     */
    @RequirePermission(resource = "scrm_data_dictionary", action = "read")
    @GetMapping("/stats/health")
    public OperationResponse<Map<String, Object>> getDictionaryHealth() {
        return OperationResponse.build(scrmDataDictionaryService.getDictionaryHealth());
    }

    // ============================================================
    // 内部工具
    // ============================================================

    /**
     * 解析 ISO 日期时间字符串 (可空返回 null)。
     *
     * @param value 字符串值
     * @param iso   ISO 格式
     * @return LocalDateTime 或 null
     */
    private java.time.LocalDateTime parseDateTime(String value,
                                                   org.springframework.format.annotation.DateTimeFormat.ISO iso) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return java.time.LocalDateTime.parse(value,
                    java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            log.warn("解析日期时间失败, 忽略过滤: value={}", value);
            return null;
        }
    }
}
