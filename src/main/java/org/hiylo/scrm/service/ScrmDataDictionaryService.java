/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmDataDictionaryService.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.dto.ScrmDataDictionaryDto;
import org.hiylo.scrm.dto.ScrmDataDictionaryItemDto;
import org.hiylo.scrm.dto.ScrmDataDictionaryUsageDto;
import org.hiylo.scrm.dto.ScrmDictBatchImportDto;
import org.hiylo.scrm.dto.ScrmDictQueryDto;
import org.hiylo.scrm.exception.ScrmException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 数据字典管理服务 (门面)。
 * <p>
 * 作为数据字典模块的统一入口, 保持对外 public 方法签名不变, 实际能力按子域委托给
 * {@link ScrmDataDictionaryManageService} (字典管理与导入导出)、{@link ScrmDataDictionaryItemService}
 * (字典项管理)、{@link ScrmDataDictionaryCacheService} (缓存与使用统计) 与
 * {@link ScrmDataDictionaryStatsService} (统计概览)。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
public class ScrmDataDictionaryService {

    /** 字典管理子域服务 */
    private final ScrmDataDictionaryManageService manageService;
    /** 字典项管理子域服务 */
    private final ScrmDataDictionaryItemService itemService;
    /** 缓存与使用统计子域服务 */
    private final ScrmDataDictionaryCacheService cacheService;
    /** 统计概览子域服务 */
    private final ScrmDataDictionaryStatsService statsService;

    // ============================================================
    // 字典管理
    // ============================================================

    /**
     * 创建字典。
     *
     * @param dto 字典参数
     * @return 创建后的字典
     * @throws ScrmException 参数非法 / 字典编码重复
     */
    public ScrmDataDictionaryDto createDictionary(ScrmDataDictionaryDto dto) throws ScrmException {
        return manageService.createDictionary(dto);
    }

    /**
     * 更新字典 (字段非空才覆盖)。
     *
     * @param id  字典 ID
     * @param dto 字典参数
     * @return 更新后的字典
     * @throws ScrmException 字典不存在 / 参数非法
     */
    public ScrmDataDictionaryDto updateDictionary(Long id, ScrmDataDictionaryDto dto) throws ScrmException {
        return manageService.updateDictionary(id, dto);
    }

    /**
     * 删除字典 (级联清理字典项与使用记录)。
     *
     * @param id 字典 ID
     * @throws ScrmException 字典不存在 / 系统内置字典不允许删除
     */
    public void deleteDictionary(Long id) throws ScrmException {
        manageService.deleteDictionary(id);
    }

    /**
     * 查询字典详情。
     *
     * @param id 字典 ID
     * @return 字典 DTO
     * @throws ScrmException 字典不存在
     */
    public ScrmDataDictionaryDto getDictionary(Long id) throws ScrmException {
        return manageService.getDictionary(id);
    }

    /**
     * 按字典编码查询字典。
     *
     * @param code 字典编码
     * @return 字典 DTO
     * @throws ScrmException 字典不存在
     */
    public ScrmDataDictionaryDto getDictionaryByCode(String code) throws ScrmException {
        return manageService.getDictionaryByCode(code);
    }

    /**
     * 分页查询字典, 支持按类型 / 分类 / 模块 / 启用状态 / 关键词过滤。
     *
     * @param queryDto 查询条件
     * @param pageable 分页参数
     * @return 字典分页结果 (按排序值与创建时间倒序)
     */
    public Page<ScrmDataDictionaryDto> listDictionaries(ScrmDictQueryDto queryDto, Pageable pageable) {
        return manageService.listDictionaries(queryDto, pageable);
    }

    /**
     * 启用字典。
     *
     * @param id 字典 ID
     * @return 更新后的字典
     * @throws ScrmException 字典不存在
     */
    public ScrmDataDictionaryDto enableDictionary(Long id) throws ScrmException {
        return manageService.enableDictionary(id);
    }

    /**
     * 禁用字典。
     *
     * @param id 字典 ID
     * @return 更新后的字典
     * @throws ScrmException 字典不存在 / 系统内置字典不允许禁用
     */
    public ScrmDataDictionaryDto disableDictionary(Long id) throws ScrmException {
        return manageService.disableDictionary(id);
    }

    /**
     * 复制字典 (含字典项), 新字典编码由入参指定。
     *
     * @param id      源字典 ID
     * @param newCode 新字典编码
     * @return 新字典
     * @throws ScrmException 字典不存在 / 新编码已存在
     */
    public ScrmDataDictionaryDto copyDictionary(Long id, String newCode) throws ScrmException {
        return manageService.copyDictionary(id, newCode);
    }

    /**
     * 合并字典: 将源字典的字典项合并到目标字典, 重复项 (按 itemCode 或 itemValue) 跳过。
     *
     * @param sourceId 源字典 ID
     * @param targetId 目标字典 ID
     * @return 目标字典
     * @throws ScrmException 字典不存在 / 源与目标相同
     */
    public ScrmDataDictionaryDto mergeDictionaries(Long sourceId, Long targetId) throws ScrmException {
        return manageService.mergeDictionaries(sourceId, targetId);
    }

    /**
     * 更新字典统计 (重新计算 itemCount 与 usageCount)。
     *
     * @param id 字典 ID
     * @return 更新后的字典
     * @throws ScrmException 字典不存在
     */
    public ScrmDataDictionaryDto updateDictionaryStats(Long id) throws ScrmException {
        return manageService.updateDictionaryStats(id);
    }

    /**
     * 字典树 (分类 → 字典列表)。
     *
     * @return 分类 → 字典列表 映射
     */
    public Map<String, List<ScrmDataDictionaryDto>> getDictionaryTree() {
        return manageService.getDictionaryTree();
    }

    /**
     * 导出字典 (含字典元信息与全部字典项)。
     *
     * @param dictCode 字典编码
     * @return 导出数据 (dictionary + items)
     * @throws ScrmException 字典不存在
     */
    public Map<String, Object> exportDictionary(String dictCode) throws ScrmException {
        return manageService.exportDictionary(dictCode);
    }

    /**
     * 导出全部字典 (含字典项)。
     *
     * @return 全部字典导出数据列表
     */
    public List<Map<String, Object>> exportAllDictionaries() {
        return manageService.exportAllDictionaries();
    }

    /**
     * 导入字典 (字典元信息 + 字典项)。
     *
     * @param importData 导入数据
     * @return 导入结果
     * @throws ScrmException 参数非法
     */
    public Map<String, Object> importDictionary(Map<String, Object> importData) throws ScrmException {
        return manageService.importDictionary(importData);
    }

    /**
     * 获取导入模板 (空字典 + 空字典项示例)。
     *
     * @return 模板数据
     */
    public Map<String, Object> getExportTemplate() {
        return manageService.getExportTemplate();
    }

    // ============================================================
    // 字典项管理
    // ============================================================

    /**
     * 创建字典项。
     *
     * @param dto 字典项参数
     * @return 创建后的字典项
     * @throws ScrmException 参数非法 / 字典不存在
     */
    public ScrmDataDictionaryItemDto createItem(ScrmDataDictionaryItemDto dto) throws ScrmException {
        return itemService.createItem(dto);
    }

    /**
     * 更新字典项 (字段非空才覆盖)。
     *
     * @param id  字典项 ID
     * @param dto 字典项参数
     * @return 更新后的字典项
     * @throws ScrmException 字典项不存在 / 参数非法
     */
    public ScrmDataDictionaryItemDto updateItem(Long id, ScrmDataDictionaryItemDto dto) throws ScrmException {
        return itemService.updateItem(id, dto);
    }

    /**
     * 删除字典项 (级联清理子项与使用记录)。
     *
     * @param id 字典项 ID
     * @throws ScrmException 字典项不存在
     */
    public void deleteItem(Long id) throws ScrmException {
        itemService.deleteItem(id);
    }

    /**
     * 查询字典项详情。
     *
     * @param id 字典项 ID
     * @return 字典项 DTO
     * @throws ScrmException 字典项不存在
     */
    public ScrmDataDictionaryItemDto getItem(Long id) throws ScrmException {
        return itemService.getItem(id);
    }

    /**
     * 按字典编码 + 字典项值查询。
     *
     * @param dictCode  字典编码
     * @param itemValue 字典项值
     * @return 字典项 DTO
     * @throws ScrmException 字典项不存在
     */
    public ScrmDataDictionaryItemDto getItemByValue(String dictCode, String itemValue) throws ScrmException {
        return itemService.getItemByValue(dictCode, itemValue);
    }

    /**
     * 按字典编码 + 字典项编码查询。
     *
     * @param dictCode 字典编码
     * @param itemCode 字典项编码
     * @return 字典项 DTO
     * @throws ScrmException 字典项不存在
     */
    public ScrmDataDictionaryItemDto getItemByCode(String dictCode, String itemCode) throws ScrmException {
        return itemService.getItemByCode(dictCode, itemCode);
    }

    /**
     * 分页查询字典项, 支持按字典 ID / 父项 / 启用状态 / 关键词过滤。
     *
     * @param dictId   字典 ID (可空)
     * @param parentId 父项 ID 过滤 (可空)
     * @param enabled  启用状态过滤 (可空)
     * @param keyword  关键词过滤, 匹配标签 / 值 / 编码 (可空)
     * @param pageable 分页参数
     * @return 字典项分页结果
     */
    public Page<ScrmDataDictionaryItemDto> listItems(Long dictId, Long parentId, Boolean enabled,
                                                      String keyword, Pageable pageable) {
        return itemService.listItems(dictId, parentId, enabled, keyword, pageable);
    }

    /**
     * 按字典编码获取所有字典项 (按排序值升序)。
     *
     * @param dictCode 字典编码
     * @return 字典项列表
     * @throws ScrmException 字典不存在
     */
    public List<ScrmDataDictionaryItemDto> getItemsByDictCode(String dictCode) throws ScrmException {
        return itemService.getItemsByDictCode(dictCode);
    }

    /**
     * 树形字典项 (按 parentId 组装, 根项 parentId 为 null)。
     *
     * @param dictCode 字典编码
     * @return 树形结构 (根项列表, 每项含 children)
     * @throws ScrmException 字典不存在
     */
    public List<Map<String, Object>> getTreeItems(String dictCode) throws ScrmException {
        return itemService.getTreeItems(dictCode);
    }

    /**
     * 启用字典项。
     *
     * @param id 字典项 ID
     * @return 更新后的字典项
     * @throws ScrmException 字典项不存在
     */
    public ScrmDataDictionaryItemDto enableItem(Long id) throws ScrmException {
        return itemService.enableItem(id);
    }

    /**
     * 禁用字典项。
     *
     * @param id 字典项 ID
     * @return 更新后的字典项
     * @throws ScrmException 字典项不存在
     */
    public ScrmDataDictionaryItemDto disableItem(Long id) throws ScrmException {
        return itemService.disableItem(id);
    }

    /**
     * 设为默认 (清除同字典内其他默认项)。
     *
     * @param id 字典项 ID
     * @return 更新后的字典项
     * @throws ScrmException 字典项不存在
     */
    public ScrmDataDictionaryItemDto setDefault(Long id) throws ScrmException {
        return itemService.setDefault(id);
    }

    /**
     * 移动字典项 (变更父项与排序值)。
     *
     * @param id            字典项 ID
     * @param newParentId   新父项 ID (可空)
     * @param newSortOrder  新排序值 (可空)
     * @return 更新后的字典项
     * @throws ScrmException 字典项不存在 / 父项不存在 / 父项为自身或子孙
     */
    public ScrmDataDictionaryItemDto moveItem(
            Long id, Long newParentId, Integer newSortOrder) throws ScrmException {
        return itemService.moveItem(id, newParentId, newSortOrder);
    }

    /**
     * 批量导入字典项。
     *
     * @param batchImportDto 批量导入参数
     * @return 导入结果 (新增数 / 更新数 / 跳过数)
     * @throws ScrmException 字典不存在 / 参数非法
     */
    public Map<String, Object> batchImport(ScrmDictBatchImportDto batchImportDto) throws ScrmException {
        return itemService.batchImport(batchImportDto);
    }

    /**
     * 批量更新字典项 (字段非空才覆盖)。
     *
     * @param updates 字典项更新列表
     * @return 更新数量
     * @throws ScrmException 参数非法
     */
    public Map<String, Object> batchUpdateItems(List<ScrmDataDictionaryItemDto> updates) throws ScrmException {
        return itemService.batchUpdateItems(updates);
    }

    /**
     * 重排序字典项 (按给定 ID 顺序依次设置 sortOrder)。
     *
     * @param dictId    字典 ID
     * @param itemOrders 字典项 ID 顺序列表
     * @return 重排序数量
     * @throws ScrmException 参数非法
     */
    public Map<String, Object> reorderItems(Long dictId, List<Long> itemOrders) throws ScrmException {
        return itemService.reorderItems(dictId, itemOrders);
    }

    /**
     * 按路径查询字典项 (返回路径前缀匹配的全部项, 用于检索子树)。
     *
     * @param dictCode 字典编码
     * @param path     路径前缀 (如 '1/5/')
     * @return 字典项列表
     * @throws ScrmException 字典不存在
     */
    public List<ScrmDataDictionaryItemDto> getItemsByPath(String dictCode, String path) throws ScrmException {
        return itemService.getItemsByPath(dictCode, path);
    }

    // ============================================================
    // 缓存与使用统计
    // ============================================================

    /**
     * 获取缓存的字典项 (若缓存未命中或已过期, 自动加载并缓存)。
     *
     * @param dictCode 字典编码
     * @return 字典项列表 (仅启用且可见项)
     * @throws ScrmException 字典不存在
     */
    public List<ScrmDataDictionaryItemDto> getCachedItems(String dictCode) throws ScrmException {
        return cacheService.getCachedItems(dictCode);
    }

    /**
     * 刷新字典缓存 (强制重新加载)。
     *
     * @param dictCode 字典编码
     * @return 刷新后的字典项列表
     * @throws ScrmException 字典不存在
     */
    public List<ScrmDataDictionaryItemDto> refreshCache(String dictCode) throws ScrmException {
        return cacheService.refreshCache(dictCode);
    }

    /**
     * 清除指定字典缓存。
     *
     * @param dictCode 字典编码
     */
    public void clearCache(String dictCode) {
        cacheService.clearCache(dictCode);
    }

    /**
     * 清除所有字典缓存。
     *
     * @return 清除的缓存条目数
     */
    public Map<String, Object> clearAllCache() {
        return cacheService.clearAllCache();
    }

    /**
     * 缓存统计 (缓存条目数 / 命中的字典编码列表)。
     *
     * @return 统计结果
     */
    public Map<String, Object> getCacheStats() {
        return cacheService.getCacheStats();
    }

    /**
     * 记录字典使用 (幂等累加或覆盖)。
     *
     * @param usageDto 使用记录参数
     * @return 使用记录
     * @throws ScrmException 参数非法 / 字典不存在
     */
    public ScrmDataDictionaryUsageDto recordUsage(ScrmDataDictionaryUsageDto usageDto) throws ScrmException {
        return cacheService.recordUsage(usageDto);
    }

    /**
     * 字典使用统计 (按时间范围过滤)。
     *
     * @param dictId    字典 ID
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果 (总使用次数 / 各字典项使用次数)
     * @throws ScrmException 字典不存在
     */
    public Map<String, Object> getUsageStats(Long dictId, LocalDateTime startTime, LocalDateTime endTime)
            throws ScrmException {
        return cacheService.getUsageStats(dictId, startTime, endTime);
    }

    /**
     * 按模块统计字典使用。
     *
     * @param dictId 字典 ID
     * @return 模块 → 使用次数
     * @throws ScrmException 字典不存在
     */
    public Map<String, Object> getUsageByModule(Long dictId) throws ScrmException {
        return cacheService.getUsageByModule(dictId);
    }

    /**
     * 查询未使用的字典项 (最近 days 天无使用记录)。
     *
     * @param dictId 字典 ID
     * @param days   天数阈值
     * @return 未使用的字典项列表
     * @throws ScrmException 字典不存在
     */
    public List<ScrmDataDictionaryItemDto> getUnusedItems(Long dictId, int days) throws ScrmException {
        return cacheService.getUnusedItems(dictId, days);
    }

    /**
     * 热门字典项 (按 usageCount 倒序取前 limit 条)。
     *
     * @param dictId 字典 ID
     * @param limit  取前 N 条
     * @return 热门字典项列表
     * @throws ScrmException 字典不存在
     */
    public List<Map<String, Object>> getPopularItems(Long dictId, int limit) throws ScrmException {
        return cacheService.getPopularItems(dictId, limit);
    }

    /**
     * 清理过期使用记录 (lastUsedAt 早于 days 天前)。
     *
     * @param days 天数阈值
     * @return 清理的记录数
     */
    public Map<String, Object> cleanupUsage(int days) {
        return cacheService.cleanupUsage(days);
    }

    // ============================================================
    // 统计概览
    // ============================================================

    /**
     * 字典统计 (总数 / 各类型 / 各分类 / 系统数 / 自定义数)。
     *
     * @return 统计结果
     */
    public Map<String, Object> getDictionaryStats() {
        return statsService.getDictionaryStats();
    }

    /**
     * 字典项统计 (总数 / 各字典项数 / 启用项数 / 禁用项数)。
     *
     * @return 统计结果
     */
    public Map<String, Object> getItemStats() {
        return statsService.getItemStats();
    }

    /**
     * 模块统计 (按字典所属 module 分组的字典数与字典项数)。
     *
     * @return 模块 → 统计信息
     */
    public Map<String, Object> getModuleStats() {
        return statsService.getModuleStats();
    }

    /**
     * 字典健康度 (空字典 / 无引用字典 / 禁用项数)。
     *
     * @return 健康度信息
     */
    public Map<String, Object> getDictionaryHealth() {
        return statsService.getDictionaryHealth();
    }
}