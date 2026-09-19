/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmDataDictionaryItemService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmDataDictionaryItemDto;
import org.hiylo.scrm.dto.ScrmDictBatchImportDto;
import org.hiylo.scrm.entity.ScrmDataDictionaryEntity;
import org.hiylo.scrm.entity.ScrmDataDictionaryItemEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmDataDictionaryItemRepository;
import org.hiylo.scrm.repository.ScrmDataDictionaryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SCRM 数据字典项管理服务。
 * <p>
 * 承载字典项子域: 字典项增删改查、按值 / 编码查询、树形 / 路径查询、启停与默认项、
 * 移动重排、批量导入 / 批量更新 / 重排序。同时托管字典项相关共享常量与转换工具
 * (路径分隔符、默认层级、项实体转 DTO 等), 供字典管理 / 缓存与使用统计兄弟类以
 * package 级访问复用。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmDataDictionaryItemService {

    // ==================== 常量约定 (共享) ====================

    /** 默认字典项层级 (根项) */
    static final int DEFAULT_ITEM_LEVEL = 1;
    /** 路径分隔符 */
    static final String PATH_SEPARATOR = "/";

    // ==================== 常量约定 (私有) ====================

    /** 合法字典项样式集合 */
    private static final Set<String> VALID_ITEM_STYLES = new HashSet<>(Arrays.asList(
            "DEFAULT", "PRIMARY", "SUCCESS", "WARNING", "DANGER", "INFO"));

    // ==================== 依赖注入 ====================

    /** 字典项数据访问层 */
    private final ScrmDataDictionaryItemRepository itemRepository;
    /** 字典数据访问层 (归属校验) */
    private final ScrmDataDictionaryRepository dictionaryRepository;
    /** 缓存与使用统计子域服务 (缓存失效) */
    private final ScrmDataDictionaryCacheService cacheService;

    /**
     * 创建字典项。
     * <p>校验字典存在且启用; 若指定父项, 自动维护 itemLevel 与 itemPath;
     * 同字典内默认项唯一 (设置 isDefault=true 时先清除其他默认)。</p>
     *
     * @param dto 字典项参数
     * @return 创建后的字典项
     * @throws ScrmException 参数非法 / 字典不存在
     */
    @Transactional
    public ScrmDataDictionaryItemDto createItem(ScrmDataDictionaryItemDto dto) throws ScrmException {
        validateItemDto(dto, false);
        ScrmDataDictionaryEntity dict = findDictionaryByCodeOrThrow(dto.getDictCode());
        if (!dict.getId().equals(dto.getDictId())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "字典 ID 与字典编码不匹配: dictId=" + dto.getDictId()
                            + ", dictCode=" + dto.getDictCode());
        }
        ScrmDataDictionaryItemEntity entity = new ScrmDataDictionaryItemEntity();
        entity.setDictId(dict.getId());
        entity.setDictCode(dict.getDictCode());
        applyItemFields(entity, dto);
        // 维护层级与路径
        if (dto.getParentId() != null) {
            ScrmDataDictionaryItemEntity parent = findItemOrThrow(dto.getParentId());
            if (!parent.getDictId().equals(dict.getId())) {
                throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                        "父项不属于同一字典: parentDictId=" + parent.getDictId()
                                + ", dictId=" + dict.getId());
            }
            entity.setParentId(parent.getId());
            entity.setItemLevel((parent.getItemLevel() != null ? parent.getItemLevel() : DEFAULT_ITEM_LEVEL) + 1);
            entity.setItemPath(buildItemPath(parent.getItemPath(), null));
        } else {
            entity.setItemLevel(DEFAULT_ITEM_LEVEL);
            entity.setItemPath(null);
        }
        entity.setUsageCount(0);
        if (entity.getEnabled() == null) entity.setEnabled(Boolean.TRUE);
        if (entity.getIsDefault() == null) entity.setIsDefault(Boolean.FALSE);
        if (entity.getIsDisabled() == null) entity.setIsDisabled(Boolean.FALSE);
        if (entity.getIsVisible() == null) entity.setIsVisible(Boolean.TRUE);
        entity = itemRepository.save(entity);
        // 落地后用自身 ID 完善路径 (根项路径: id/)
        if (entity.getParentId() == null) {
            entity.setItemPath(entity.getId() + PATH_SEPARATOR);
            entity = itemRepository.save(entity);
        } else {
            entity.setItemPath(buildItemPath(
                    entity.getParentId() != null
                            ? findItemOrThrow(entity.getParentId()).getItemPath()
                            : null,
                    entity.getId()));
            entity = itemRepository.save(entity);
        }
        // 默认项唯一性
        if (Boolean.TRUE.equals(entity.getIsDefault())) {
            itemRepository.clearDefaultByDict(dict.getId());
            entity.setIsDefault(Boolean.TRUE);
            entity = itemRepository.save(entity);
        }
        refreshItemCount(dict.getId());
        cacheService.evictCache(dict.getDictCode());
        log.info("创建字典项: id={}, dictCode={}, itemValue={}",
                entity.getId(), dict.getDictCode(), entity.getItemValue());
        return toItemDto(entity);
    }

    /**
     * 更新字典项 (字段非空才覆盖)。
     * <p>字典编码与字典 ID 不允许变更; parentId 变更会重算 level 与 path。</p>
     *
     * @param id  字典项 ID
     * @param dto 字典项参数
     * @return 更新后的字典项
     * @throws ScrmException 字典项不存在 / 参数非法
     */
    @Transactional
    public ScrmDataDictionaryItemDto updateItem(Long id, ScrmDataDictionaryItemDto dto) throws ScrmException {
        ScrmDataDictionaryItemEntity entity = findItemOrThrow(id);
        validateItemDto(dto, true);
        boolean parentChanged = dto.getParentId() != null && !dto.getParentId().equals(entity.getParentId());
        applyItemFields(entity, dto);
        if (parentChanged) {
            if (dto.getParentId().equals(id)) {
                throw ScrmException.badRequest("字典项的父项不能为自身");
            }
            ScrmDataDictionaryItemEntity parent = findItemOrThrow(dto.getParentId());
            if (!parent.getDictId().equals(entity.getDictId())) {
                throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                        "父项不属于同一字典");
            }
            entity.setParentId(parent.getId());
            entity.setItemLevel((parent.getItemLevel() != null ? parent.getItemLevel() : DEFAULT_ITEM_LEVEL) + 1);
            entity.setItemPath(buildItemPath(parent.getItemPath(), entity.getId()));
        }
        if (Boolean.TRUE.equals(entity.getIsDefault())) {
            itemRepository.clearDefaultByDict(entity.getDictId());
            entity.setIsDefault(Boolean.TRUE);
        }
        entity = itemRepository.save(entity);
        cacheService.evictCache(entity.getDictCode());
        log.info("更新字典项: id={}", id);
        return toItemDto(entity);
    }

    /**
     * 删除字典项 (级联清理子项与使用记录)。
     *
     * @param id 字典项 ID
     * @throws ScrmException 字典项不存在
     */
    @Transactional
    public void deleteItem(Long id) throws ScrmException {
        ScrmDataDictionaryItemEntity entity = findItemOrThrow(id);
        // 递归删除子项 (按 parentId 链)
        deleteItemRecursive(id);
        refreshItemCount(entity.getDictId());
        cacheService.evictCache(entity.getDictCode());
        log.info("删除字典项: id={}, dictCode={}", id, entity.getDictCode());
    }

    /**
     * 查询字典项详情。
     *
     * @param id 字典项 ID
     * @return 字典项 DTO
     * @throws ScrmException 字典项不存在
     */
    @Transactional(readOnly = true)
    public ScrmDataDictionaryItemDto getItem(Long id) throws ScrmException {
        return toItemDto(findItemOrThrow(id));
    }

    /**
     * 按字典编码 + 字典项值查询。
     *
     * @param dictCode  字典编码
     * @param itemValue 字典项值
     * @return 字典项 DTO
     * @throws ScrmException 字典项不存在
     */
    @Transactional(readOnly = true)
    public ScrmDataDictionaryItemDto getItemByValue(String dictCode, String itemValue) throws ScrmException {
        if (dictCode == null || dictCode.isBlank() || itemValue == null || itemValue.isBlank()) {
            throw ScrmException.badRequest("字典编码与字典项值不能为空");
        }
        ScrmDataDictionaryItemEntity entity = itemRepository
                .findByDictCodeAndItemValue(dictCode, itemValue)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "字典项不存在: dictCode=" + dictCode + ", itemValue=" + itemValue));
        return toItemDto(entity);
    }

    /**
     * 按字典编码 + 字典项编码查询。
     *
     * @param dictCode 字典编码
     * @param itemCode 字典项编码
     * @return 字典项 DTO
     * @throws ScrmException 字典项不存在
     */
    @Transactional(readOnly = true)
    public ScrmDataDictionaryItemDto getItemByCode(String dictCode, String itemCode) throws ScrmException {
        if (dictCode == null || dictCode.isBlank() || itemCode == null || itemCode.isBlank()) {
            throw ScrmException.badRequest("字典编码与字典项编码不能为空");
        }
        ScrmDataDictionaryItemEntity entity = itemRepository
                .findByDictCodeAndItemCode(dictCode, itemCode)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "字典项不存在: dictCode=" + dictCode + ", itemCode=" + itemCode));
        return toItemDto(entity);
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
    @Transactional(readOnly = true)
    public Page<ScrmDataDictionaryItemDto> listItems(Long dictId, Long parentId, Boolean enabled,
                                                      String keyword, Pageable pageable) {
        Specification<ScrmDataDictionaryItemEntity> spec = buildItemSpec(dictId, parentId, enabled, keyword);
        return itemRepository.findAll(spec, pageable).map(ScrmDataDictionaryItemService::toItemDto);
    }

    /**
     * 按字典编码获取所有字典项 (按排序值升序)。
     *
     * @param dictCode 字典编码
     * @return 字典项列表
     * @throws ScrmException 字典不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmDataDictionaryItemDto> getItemsByDictCode(String dictCode) throws ScrmException {
        if (dictCode == null || dictCode.isBlank()) {
            throw ScrmException.badRequest("字典编码不能为空");
        }
        // 校验字典存在且属于当前账号
        findDictionaryByCodeOrThrow(dictCode);
        return itemRepository
                .findByDictCodeOrderBySortOrderAsc(dictCode)
                .stream().map(ScrmDataDictionaryItemService::toItemDto).collect(Collectors.toList());
    }

    /**
     * 树形字典项 (按 parentId 组装, 根项 parentId 为 null)。
     *
     * @param dictCode 字典编码
     * @return 树形结构 (根项列表, 每项含 children)
     * @throws ScrmException 字典不存在
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTreeItems(String dictCode) throws ScrmException {
        if (dictCode == null || dictCode.isBlank()) {
            throw ScrmException.badRequest("字典编码不能为空");
        }
        findDictionaryByCodeOrThrow(dictCode);
        List<ScrmDataDictionaryItemEntity> all = itemRepository
                .findByDictCodeOrderBySortOrderAsc(dictCode);
        Map<Long, List<ScrmDataDictionaryItemEntity>> byParent = all.stream()
                .collect(Collectors.groupingBy(i -> i.getParentId() != null ? i.getParentId() : 0L));
        return buildItemTree(0L, byParent);
    }

    /**
     * 启用字典项。
     *
     * @param id 字典项 ID
     * @return 更新后的字典项
     * @throws ScrmException 字典项不存在
     */
    @Transactional
    public ScrmDataDictionaryItemDto enableItem(Long id) throws ScrmException {
        ScrmDataDictionaryItemEntity entity = findItemOrThrow(id);
        entity.setEnabled(Boolean.TRUE);
        entity = itemRepository.save(entity);
        cacheService.evictCache(entity.getDictCode());
        log.info("启用字典项: id={}", id);
        return toItemDto(entity);
    }

    /**
     * 禁用字典项。
     *
     * @param id 字典项 ID
     * @return 更新后的字典项
     * @throws ScrmException 字典项不存在
     */
    @Transactional
    public ScrmDataDictionaryItemDto disableItem(Long id) throws ScrmException {
        ScrmDataDictionaryItemEntity entity = findItemOrThrow(id);
        entity.setEnabled(Boolean.FALSE);
        entity = itemRepository.save(entity);
        cacheService.evictCache(entity.getDictCode());
        log.info("禁用字典项: id={}", id);
        return toItemDto(entity);
    }

    /**
     * 设为默认 (清除同字典内其他默认项)。
     *
     * @param id 字典项 ID
     * @return 更新后的字典项
     * @throws ScrmException 字典项不存在
     */
    @Transactional
    public ScrmDataDictionaryItemDto setDefault(Long id) throws ScrmException {
        ScrmDataDictionaryItemEntity entity = findItemOrThrow(id);
        itemRepository.clearDefaultByDict(entity.getDictId());
        entity.setIsDefault(Boolean.TRUE);
        entity = itemRepository.save(entity);
        cacheService.evictCache(entity.getDictCode());
        log.info("设为默认字典项: id={}, dictCode={}", id, entity.getDictCode());
        return toItemDto(entity);
    }

    /**
     * 移动字典项 (变更父项与排序值)。
     * <p>parentId 传 null 表示移到根级; 移动后重算 level 与 path。</p>
     *
     * @param id            字典项 ID
     * @param newParentId   新父项 ID (可空)
     * @param newSortOrder  新排序值 (可空)
     * @return 更新后的字典项
     * @throws ScrmException 字典项不存在 / 父项不存在 / 父项为自身或子孙
     */
    @Transactional
    public ScrmDataDictionaryItemDto moveItem(
            Long id, Long newParentId, Integer newSortOrder) throws ScrmException {
        ScrmDataDictionaryItemEntity entity = findItemOrThrow(id);
        if (newParentId != null) {
            if (newParentId.equals(id)) {
                throw ScrmException.badRequest("字典项的父项不能为自身");
            }
            ScrmDataDictionaryItemEntity parent = findItemOrThrow(newParentId);
            if (!parent.getDictId().equals(entity.getDictId())) {
                throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                        "父项不属于同一字典");
            }
            // 防止将项移动到自己的子孙下 (形成环)
            if (isAncestor(id, newParentId)) {
                throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                        "不允许将字典项移动到其子孙项下");
            }
            entity.setParentId(parent.getId());
            entity.setItemLevel((parent.getItemLevel() != null ? parent.getItemLevel() : DEFAULT_ITEM_LEVEL) + 1);
            entity.setItemPath(buildItemPath(parent.getItemPath(), entity.getId()));
        } else {
            entity.setParentId(null);
            entity.setItemLevel(DEFAULT_ITEM_LEVEL);
            entity.setItemPath(entity.getId() + PATH_SEPARATOR);
        }
        if (newSortOrder != null) {
            entity.setSortOrder(newSortOrder);
        }
        entity = itemRepository.save(entity);
        cacheService.evictCache(entity.getDictCode());
        log.info("移动字典项: id={}, newParentId={}, newSortOrder={}", id, newParentId, newSortOrder);
        return toItemDto(entity);
    }

    /**
     * 批量导入字典项。
     * <p>按 dictCode 定位字典; 按 itemCode / itemValue 去重; overwrite=true 时更新已存在项。</p>
     *
     * @param batchImportDto 批量导入参数
     * @return 导入结果 (新增数 / 更新数 / 跳过数)
     * @throws ScrmException 字典不存在 / 参数非法
     */
    @Transactional
    public Map<String, Object> batchImport(ScrmDictBatchImportDto batchImportDto) throws ScrmException {
        if (batchImportDto == null || batchImportDto.getItems() == null || batchImportDto.getItems().isEmpty()) {
            throw ScrmException.badRequest("批量导入参数与字典项列表不能为空");
        }
        ScrmDataDictionaryEntity dict = findDictionaryByCodeOrThrow(batchImportDto.getDictCode());
        boolean overwrite = Boolean.TRUE.equals(batchImportDto.getOverwrite());
        List<ScrmDataDictionaryItemEntity> existing = itemRepository
                .findByDictCodeOrderBySortOrderAsc(dict.getDictCode());
        Map<String, ScrmDataDictionaryItemEntity> byCode = new HashMap<>();
        Map<String, ScrmDataDictionaryItemEntity> byValue = new HashMap<>();
        for (ScrmDataDictionaryItemEntity e : existing) {
            if (e.getItemCode() != null && !e.getItemCode().isBlank()) {
                byCode.put(e.getItemCode(), e);
            }
            byValue.put(e.getItemValue(), e);
        }
        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        for (ScrmDataDictionaryItemDto dto : batchImportDto.getItems()) {
            dto.setDictId(dict.getId());
            dto.setDictCode(dict.getDictCode());
            validateItemDto(dto, false);
            ScrmDataDictionaryItemEntity exist = null;
            if (dto.getItemCode() != null && !dto.getItemCode().isBlank()) {
                exist = byCode.get(dto.getItemCode());
            }
            if (exist == null) {
                exist = byValue.get(dto.getItemValue());
            }
            if (exist != null) {
                if (overwrite) {
                    applyItemFields(exist, dto);
                    if (dto.getParentId() != null) {
                        ScrmDataDictionaryItemEntity parent = findItemOrThrow(dto.getParentId());
                        exist.setParentId(parent.getId());
                        exist.setItemLevel((parent.getItemLevel() != null
                                ? parent.getItemLevel() : DEFAULT_ITEM_LEVEL) + 1);
                        exist.setItemPath(buildItemPath(parent.getItemPath(), exist.getId()));
                    }
                    itemRepository.save(exist);
                    updated++;
                } else {
                    skipped++;
                }
            } else {
                ScrmDataDictionaryItemEntity entity = new ScrmDataDictionaryItemEntity();
                entity.setDictId(dict.getId());
                entity.setDictCode(dict.getDictCode());
                applyItemFields(entity, dto);
                if (dto.getParentId() != null) {
                    ScrmDataDictionaryItemEntity parent = findItemOrThrow(dto.getParentId());
                    entity.setParentId(parent.getId());
                    entity.setItemLevel((parent.getItemLevel() != null
                            ? parent.getItemLevel() : DEFAULT_ITEM_LEVEL) + 1);
                    entity.setItemPath(buildItemPath(parent.getItemPath(), null));
                } else {
                    entity.setItemLevel(DEFAULT_ITEM_LEVEL);
                }
                entity.setUsageCount(0);
                if (entity.getEnabled() == null) entity.setEnabled(Boolean.TRUE);
                if (entity.getIsDefault() == null) entity.setIsDefault(Boolean.FALSE);
                if (entity.getIsDisabled() == null) entity.setIsDisabled(Boolean.FALSE);
                if (entity.getIsVisible() == null) entity.setIsVisible(Boolean.TRUE);
                entity = itemRepository.save(entity);
                if (entity.getParentId() == null) {
                    entity.setItemPath(entity.getId() + PATH_SEPARATOR);
                } else {
                    ScrmDataDictionaryItemEntity parent = findItemOrThrow(entity.getParentId());
                    entity.setItemPath(buildItemPath(parent.getItemPath(), entity.getId()));
                }
                itemRepository.save(entity);
                if (entity.getItemCode() != null && !entity.getItemCode().isBlank()) {
                    byCode.put(entity.getItemCode(), entity);
                }
                byValue.put(entity.getItemValue(), entity);
                inserted++;
            }
        }
        refreshItemCount(dict.getId());
        cacheService.evictCache(dict.getDictCode());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("inserted", inserted);
        result.put("updated", updated);
        result.put("skipped", skipped);
        result.put("total", inserted + updated + skipped);
        log.info("批量导入字典项: dictCode={}, inserted={}, updated={}, skipped={}",
                dict.getDictCode(), inserted, updated, skipped);
        return result;
    }

    /**
     * 批量更新字典项 (字段非空才覆盖)。
     *
     * @param updates 字典项更新列表
     * @return 更新数量
     * @throws ScrmException 参数非法
     */
    @Transactional
    public Map<String, Object> batchUpdateItems(List<ScrmDataDictionaryItemDto> updates) throws ScrmException {
        if (updates == null || updates.isEmpty()) {
            throw ScrmException.badRequest("批量更新列表不能为空");
        }
        int updated = 0;
        Set<String> touchedDictCodes = new HashSet<>();
        for (ScrmDataDictionaryItemDto dto : updates) {
            if (dto.getId() == null) {
                throw ScrmException.badRequest("批量更新字典项 ID 不能为空");
            }
            ScrmDataDictionaryItemEntity entity = findItemOrThrow(dto.getId());
            validateItemDto(dto, true);
            applyItemFields(entity, dto);
            if (dto.getParentId() != null && !dto.getParentId().equals(entity.getParentId())) {
                if (dto.getParentId().equals(entity.getId())) {
                    throw ScrmException.badRequest("字典项的父项不能为自身");
                }
                ScrmDataDictionaryItemEntity parent = findItemOrThrow(dto.getParentId());
                if (!parent.getDictId().equals(entity.getDictId())) {
                    throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                            "父项不属于同一字典");
                }
                entity.setParentId(parent.getId());
                entity.setItemLevel((parent.getItemLevel() != null
                        ? parent.getItemLevel() : DEFAULT_ITEM_LEVEL) + 1);
                entity.setItemPath(buildItemPath(parent.getItemPath(), entity.getId()));
            }
            itemRepository.save(entity);
            touchedDictCodes.add(entity.getDictCode());
            updated++;
        }
        for (String code : touchedDictCodes) {
            cacheService.evictCache(code);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updated", updated);
        log.info("批量更新字典项: count={}", updated);
        return result;
    }

    /**
     * 重排序字典项 (按给定 ID 顺序依次设置 sortOrder)。
     *
     * @param dictId    字典 ID
     * @param itemOrders 字典项 ID 顺序列表
     * @return 重排序数量
     * @throws ScrmException 参数非法
     */
    @Transactional
    public Map<String, Object> reorderItems(Long dictId, List<Long> itemOrders) throws ScrmException {
        if (dictId == null) {
            throw ScrmException.badRequest("字典 ID 不能为空");
        }
        if (itemOrders == null || itemOrders.isEmpty()) {
            throw ScrmException.badRequest("字典项顺序列表不能为空");
        }
        int reordered = 0;
        for (int i = 0; i < itemOrders.size(); i++) {
            Long itemId = itemOrders.get(i);
            ScrmDataDictionaryItemEntity entity = itemRepository.findById(itemId)
                    .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                            "字典项不存在: id=" + itemId));

            entity.setSortOrder(i);
            itemRepository.save(entity);
            reordered++;
        }
        ScrmDataDictionaryEntity dict = findDictionaryOrThrow(dictId);
        cacheService.evictCache(dict.getDictCode());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reordered", reordered);
        log.info("重排序字典项: dictId={}, count={}", dictId, reordered);
        return result;
    }

    /**
     * 按路径查询字典项 (返回路径前缀匹配的全部项, 用于检索子树)。
     *
     * @param dictCode 字典编码
     * @param path     路径前缀 (如 '1/5/')
     * @return 字典项列表
     * @throws ScrmException 字典不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmDataDictionaryItemDto> getItemsByPath(String dictCode, String path) throws ScrmException {
        if (dictCode == null || dictCode.isBlank() || path == null || path.isBlank()) {
            throw ScrmException.badRequest("字典编码与路径不能为空");
        }
        findDictionaryByCodeOrThrow(dictCode);
        return itemRepository
                .findByDictCodeAndItemPathStartingWithOrderBySortOrderAsc(
                         dictCode, path)
                .stream().map(ScrmDataDictionaryItemService::toItemDto).collect(Collectors.toList());
    }

    // ============================================================
    // 公共辅助方法 (供兄弟类复用)
    // ============================================================

    /**
     * 字典项实体转 DTO (纯转换, 供兄弟类静态复用避免循环依赖)。
     *
     * @param entity 字典项实体
     * @return 字典项 DTO
     */
    static ScrmDataDictionaryItemDto toItemDto(ScrmDataDictionaryItemEntity entity) {
        ScrmDataDictionaryItemDto dto = new ScrmDataDictionaryItemDto();
        dto.setId(entity.getId());
        dto.setDictId(entity.getDictId());
        dto.setDictCode(entity.getDictCode());
        dto.setItemLabel(entity.getItemLabel());
        dto.setItemValue(entity.getItemValue());
        dto.setItemCode(entity.getItemCode());
        dto.setParentId(entity.getParentId());
        dto.setItemLevel(entity.getItemLevel());
        dto.setItemPath(entity.getItemPath());
        dto.setSortOrder(entity.getSortOrder());
        dto.setItemStyle(entity.getItemStyle());
        dto.setColor(entity.getColor());
        dto.setIcon(entity.getIcon());
        dto.setDescription(entity.getDescription());
        dto.setExtraData(entity.getExtraData());
        dto.setTags(entity.getTags());
        dto.setIsDefault(entity.getIsDefault());
        dto.setIsDisabled(entity.getIsDisabled());
        dto.setIsVisible(entity.getIsVisible());
        dto.setUsageCount(entity.getUsageCount());
        dto.setEnabled(entity.getEnabled());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 将 DTO 字段 (非空) 应用到实体 (公共字段除外)。
     *
     * @param entity 实体
     * @param dto    DTO
     */
    void applyItemFields(ScrmDataDictionaryItemEntity entity, ScrmDataDictionaryItemDto dto) {
        if (dto.getItemLabel() != null) entity.setItemLabel(dto.getItemLabel());
        if (dto.getItemValue() != null) entity.setItemValue(dto.getItemValue());
        if (dto.getItemCode() != null) entity.setItemCode(dto.getItemCode());
        if (dto.getSortOrder() != null) entity.setSortOrder(dto.getSortOrder());
        if (dto.getItemStyle() != null) entity.setItemStyle(dto.getItemStyle());
        if (dto.getColor() != null) entity.setColor(dto.getColor());
        if (dto.getIcon() != null) entity.setIcon(dto.getIcon());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getExtraData() != null) entity.setExtraData(dto.getExtraData());
        if (dto.getTags() != null) entity.setTags(dto.getTags());
        if (dto.getIsDefault() != null) entity.setIsDefault(dto.getIsDefault());
        if (dto.getIsDisabled() != null) entity.setIsDisabled(dto.getIsDisabled());
        if (dto.getIsVisible() != null) entity.setIsVisible(dto.getIsVisible());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
    }

    /**
     * 重新计算并持久化字典的 itemCount。
     *
     * @param dictId 字典 ID
     */
    void refreshItemCount(Long dictId) {
        long count = itemRepository.countByDictId(dictId);
        dictionaryRepository.findById(dictId).ifPresent(d -> {
            d.setItemCount((int) count);
            dictionaryRepository.save(d);
        });
    }

    // ============================================================
    // 私有辅助方法
    // ============================================================

    /**
     * 校验字典项参数。
     *
     * @param dto     字典项参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateItemDto(ScrmDataDictionaryItemDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("字典项参数不能为空");
        }
        if (!partial) {
            if (dto.getDictId() == null) {
                throw ScrmException.badRequest("字典 ID 不能为空");
            }
            if (dto.getDictCode() == null || dto.getDictCode().isBlank()) {
                throw ScrmException.badRequest("字典编码不能为空");
            }
            if (dto.getItemLabel() == null || dto.getItemLabel().isBlank()) {
                throw ScrmException.badRequest("字典项标签不能为空");
            }
            if (dto.getItemValue() == null || dto.getItemValue().isBlank()) {
                throw ScrmException.badRequest("字典项值不能为空");
            }
        }
        if (dto.getItemStyle() != null && !dto.getItemStyle().isBlank()
                && !VALID_ITEM_STYLES.contains(dto.getItemStyle())) {
            throw ScrmException.badRequest("字典项样式非法: " + dto.getItemStyle()
                    + ", 合法值: DEFAULT / PRIMARY / SUCCESS / WARNING / DANGER / INFO");
        }
    }

    /**
     * 构建字典项路径: parentPath + selfId + "/" (parentPath 为空时为 selfId + "/")
     *
     * @param parentPath 父项路径 (可空)
     * @param selfId     当前项 ID (可空, 为空时仅返回父路径)
     * @return 完整路径
     */
    private String buildItemPath(String parentPath, Long selfId) {
        StringBuilder sb = new StringBuilder();
        if (parentPath != null && !parentPath.isBlank()) {
            sb.append(parentPath);
        }
        if (selfId != null) {
            sb.append(selfId).append(PATH_SEPARATOR);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /**
     * 判断 candidateId 是否为 itemId 的祖先 (用于防止移动到子孙下)。
     *
     * @param itemId       待移动项 ID
     * @param candidateId  候选父项 ID
     * @return true 表示 candidateId 是 itemId 的子孙 (即 itemId 是 candidateId 的祖先)
     */
    private boolean isAncestor(Long itemId, Long candidateId) {
        Long cursor = candidateId;
        Set<Long> visited = new HashSet<>();
        while (cursor != null && visited.add(cursor)) {
            ScrmDataDictionaryItemEntity current = itemRepository.findById(cursor).orElse(null);

            if (itemId.equals(current.getParentId())) {
                return true;
            }
            cursor = current.getParentId();
        }
        return false;
    }

    /**
     * 递归删除字典项及其子孙项。
     *
     * @param itemId 字典项 ID
     */
    private void deleteItemRecursive(Long itemId) {
        List<ScrmDataDictionaryItemEntity> children = itemRepository
                .findByParentIdOrderBySortOrderAsc(itemId);
        for (ScrmDataDictionaryItemEntity child : children) {
            deleteItemRecursive(child.getId());
        }
        itemRepository.findById(itemId).ifPresent(itemRepository::delete);
    }

    /**
     * 构建字典项查询条件 Specification。
     *
     * @param dictId   字典 ID (可空)
     * @param parentId 父项 ID (可空)
     * @param enabled  启用状态 (可空)
     * @param keyword  关键词 (可空)
     * @return 查询条件
     */
    private Specification<ScrmDataDictionaryItemEntity> buildItemSpec(Long dictId, Long parentId,
                                                                      Boolean enabled, String keyword) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (dictId != null) {
                predicates.add(cb.equal(root.get("dictId"), dictId));
            }
            if (parentId != null) {
                predicates.add(cb.equal(root.get("parentId"), parentId));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("itemLabel")), like),
                        cb.like(cb.lower(root.get("itemValue")), like),
                        cb.like(cb.lower(root.get("itemCode")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 递归构建字典项树 (供 getTreeItems 使用)。
     *
     * @param parentId 父项 ID (根项传 0L)
     * @param byParent 按父项 ID 分组的字典项映射
     * @return 树形节点列表
     */
    private List<Map<String, Object>> buildItemTree(Long parentId,
                                                     Map<Long, List<ScrmDataDictionaryItemEntity>> byParent) {
        List<ScrmDataDictionaryItemEntity> children = byParent.get(parentId);
        if (children == null || children.isEmpty()) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> tree = new ArrayList<>();
        for (ScrmDataDictionaryItemEntity item : children) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", item.getId());
            node.put("dictId", item.getDictId());
            node.put("dictCode", item.getDictCode());
            node.put("itemLabel", item.getItemLabel());
            node.put("itemValue", item.getItemValue());
            node.put("itemCode", item.getItemCode());
            node.put("parentId", item.getParentId());
            node.put("itemLevel", item.getItemLevel());
            node.put("itemPath", item.getItemPath());
            node.put("sortOrder", item.getSortOrder());
            node.put("itemStyle", item.getItemStyle());
            node.put("color", item.getColor());
            node.put("icon", item.getIcon());
            node.put("description", item.getDescription());
            node.put("extraData", item.getExtraData());
            node.put("tags", item.getTags());
            node.put("isDefault", item.getIsDefault());
            node.put("isDisabled", item.getIsDisabled());
            node.put("isVisible", item.getIsVisible());
            node.put("usageCount", item.getUsageCount());
            node.put("enabled", item.getEnabled());
            node.put("children", buildItemTree(item.getId(), byParent));
            tree.add(node);
        }
        return tree;
    }

    /**
     * 按主键查询字典, 不存在抛异常。
     *
     * @param id 字典 ID
     * @return 字典实体
     * @throws ScrmException 字典不存在
     */
    private ScrmDataDictionaryEntity findDictionaryOrThrow(Long id) throws ScrmException {
        ScrmDataDictionaryEntity entity = dictionaryRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "字典不存在: id=" + id));

        return entity;
    }

    /**
     * 按字典编码查询字典, 不存在抛异常。
     *
     * @param dictCode 字典编码
     * @return 字典实体
     * @throws ScrmException 字典不存在
     */
    private ScrmDataDictionaryEntity findDictionaryByCodeOrThrow(String dictCode) throws ScrmException {
        ScrmDataDictionaryEntity entity = dictionaryRepository
                .findByDictCode(dictCode)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "字典不存在: dictCode=" + dictCode));
        return entity;
    }

    /**
     * 按主键查询字典项, 不存在抛异常。
     *
     * @param id 字典项 ID
     * @return 字典项实体
     * @throws ScrmException 字典项不存在
     */
    private ScrmDataDictionaryItemEntity findItemOrThrow(Long id) throws ScrmException {
        ScrmDataDictionaryItemEntity entity = itemRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "字典项不存在: id=" + id));

        return entity;
    }
}