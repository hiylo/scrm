/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmDataDictionaryManageService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmDataDictionaryDto;
import org.hiylo.scrm.dto.ScrmDataDictionaryItemDto;
import org.hiylo.scrm.dto.ScrmDictQueryDto;
import org.hiylo.scrm.entity.ScrmDataDictionaryEntity;
import org.hiylo.scrm.entity.ScrmDataDictionaryItemEntity;
import org.hiylo.scrm.entity.ScrmDataDictionaryUsageEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmDataDictionaryItemRepository;
import org.hiylo.scrm.repository.ScrmDataDictionaryRepository;
import org.hiylo.scrm.repository.ScrmDataDictionaryUsageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
 * SCRM 数据字典管理服务。
 * <p>
 * 承载字典管理子域: 字典增删改查、启停、复制 / 合并 / 统计刷新、字典树构建、字典
 * 导入导出与模板。写操作通过 {@link ScrmDataDictionaryCacheService} 失效缓存, 字典项
 * 相关落库操作委托 {@link ScrmDataDictionaryItemService} 完成。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmDataDictionaryManageService {

    // ==================== 字典类型 (共享) ====================

    /** 字典类型: 列表 */
    static final String DICT_TYPE_LIST = "LIST";
    /** 字典类型: 树 */
    static final String DICT_TYPE_TREE = "TREE";
    /** 字典类型: 级联 */
    static final String DICT_TYPE_CASCADE = "CASCADE";
    /** 字典类型: 多级 */
    static final String DICT_TYPE_MULTI_LEVEL = "MULTI_LEVEL";

    // ==================== 常量约定 (私有) ====================

    /** 合法字典类型集合 */
    private static final Set<String> VALID_DICT_TYPES = new HashSet<>(Arrays.asList(
            DICT_TYPE_LIST, DICT_TYPE_TREE, DICT_TYPE_CASCADE, DICT_TYPE_MULTI_LEVEL));

    /** 合法字典分类集合 */
    private static final Set<String> VALID_CATEGORIES = new HashSet<>(Arrays.asList(
            "SYSTEM", "BUSINESS", "CUSTOM", "INDUSTRY", "REGION"));

    /** 默认排序值 */
    private static final int DEFAULT_SORT_ORDER = 0;

    // ==================== 依赖注入 ====================

    /** 字典数据访问层 */
    private final ScrmDataDictionaryRepository dictionaryRepository;
    /** 字典项数据访问层 */
    private final ScrmDataDictionaryItemRepository itemRepository;
    /** 字典使用记录数据访问层 */
    private final ScrmDataDictionaryUsageRepository usageRepository;
    /** 字典项子域服务 (字段应用 / 项计数刷新) */
    private final ScrmDataDictionaryItemService itemService;
    /** 缓存与使用统计子域服务 (缓存失效) */
    private final ScrmDataDictionaryCacheService cacheService;

    /**
     * 创建字典。
     * <p>校验字典编码唯一, 字典类型缺省 LIST, 布尔字段缺省补全, 写入账号 ID 持久化。
     * 系统内置字典 (isSystem=true) 禁止通过此接口创建。</p>
     *
     * @param dto 字典参数
     * @return 创建后的字典
     * @throws ScrmException 参数非法 / 字典编码重复
     */
    @Transactional
    public ScrmDataDictionaryDto createDictionary(ScrmDataDictionaryDto dto) throws ScrmException {
        validateDictionaryDto(dto, false);
        if (dictionaryRepository.existsByDictCode(dto.getDictCode())) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    "字典编码已存在: dictCode=" + dto.getDictCode());
        }
        if (dto.getParentId() != null) {
            findDictionaryOrThrow(dto.getParentId());
        }
        ScrmDataDictionaryEntity entity = new ScrmDataDictionaryEntity();
        entity.setDictName(dto.getDictName());
        entity.setDictCode(dto.getDictCode());
        entity.setDescription(dto.getDescription());
        entity.setDictType(dto.getDictType() != null ? dto.getDictType() : DICT_TYPE_LIST);
        entity.setCategory(dto.getCategory());
        entity.setParentId(dto.getParentId());
        entity.setModule(dto.getModule());
        entity.setApplicableScenarios(dto.getApplicableScenarios());
        entity.setItemCount(0);
        entity.setIsSystem(Boolean.TRUE.equals(dto.getIsSystem()));
        entity.setIsCacheable(dto.getIsCacheable() != null ? dto.getIsCacheable() : Boolean.TRUE);
        entity.setCacheTtlSeconds(dto.getCacheTtlSeconds() != null ? dto.getCacheTtlSeconds()
                : ScrmDataDictionaryCacheService.DEFAULT_CACHE_TTL_SECONDS);
        entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : DEFAULT_SORT_ORDER);
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : Boolean.TRUE);
        entity.setUsageCount(0);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = dictionaryRepository.save(entity);
        log.info("创建字典: id={}, dictCode={}", entity.getId(), entity.getDictCode());
        return toDictionaryDto(entity);
    }

    /**
     * 更新字典 (字段非空才覆盖)。
     * <p>字典编码不允许变更; 系统内置字典仅允许修改描述 / 排序 / 缓存配置等非核心
     * 字段。</p>
     *
     * @param id  字典 ID
     * @param dto 字典参数
     * @return 更新后的字典
     * @throws ScrmException 字典不存在 / 参数非法
     */
    @Transactional
    public ScrmDataDictionaryDto updateDictionary(Long id, ScrmDataDictionaryDto dto) throws ScrmException {
        ScrmDataDictionaryEntity entity = findDictionaryOrThrow(id);
        validateDictionaryDto(dto, true);
        if (dto.getDictName() != null) entity.setDictName(dto.getDictName());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getDictType() != null) {
            validateDictType(dto.getDictType());
            entity.setDictType(dto.getDictType());
        }
        if (dto.getCategory() != null) {
            validateCategory(dto.getCategory());
            entity.setCategory(dto.getCategory());
        }
        if (dto.getParentId() != null) {
            if (!dto.getParentId().equals(id)) {
                findDictionaryOrThrow(dto.getParentId());
                entity.setParentId(dto.getParentId());
            }
        }
        if (dto.getModule() != null) entity.setModule(dto.getModule());
        if (dto.getApplicableScenarios() != null) entity.setApplicableScenarios(dto.getApplicableScenarios());
        if (dto.getIsCacheable() != null) entity.setIsCacheable(dto.getIsCacheable());
        if (dto.getCacheTtlSeconds() != null) entity.setCacheTtlSeconds(dto.getCacheTtlSeconds());
        if (dto.getSortOrder() != null) entity.setSortOrder(dto.getSortOrder());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        entity = dictionaryRepository.save(entity);
        cacheService.evictCache(entity.getDictCode());
        log.info("更新字典: id={}, dictCode={}", id, entity.getDictCode());
        return toDictionaryDto(entity);
    }

    /**
     * 删除字典 (级联清理字典项与使用记录)。
     * <p>系统内置字典禁止删除。</p>
     *
     * @param id 字典 ID
     * @throws ScrmException 字典不存在 / 系统内置字典不允许删除
     */
    @Transactional
    public void deleteDictionary(Long id) throws ScrmException {
        ScrmDataDictionaryEntity entity = findDictionaryOrThrow(id);
        if (Boolean.TRUE.equals(entity.getIsSystem())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "系统内置字典不允许删除: dictCode=" + entity.getDictCode());
        }
        itemRepository.deleteByDictId(id);
        usageRepository.deleteByDictId(id);
        dictionaryRepository.delete(entity);
        cacheService.evictCache(entity.getDictCode());
        log.info("删除字典: id={}, dictCode={}", id, entity.getDictCode());
    }

    /**
     * 查询字典详情。
     *
     * @param id 字典 ID
     * @return 字典 DTO
     * @throws ScrmException 字典不存在
     */
    @Transactional(readOnly = true)
    public ScrmDataDictionaryDto getDictionary(Long id) throws ScrmException {
        return toDictionaryDto(findDictionaryOrThrow(id));
    }

    /**
     * 按字典编码查询字典。
     *
     * @param code 字典编码
     * @return 字典 DTO
     * @throws ScrmException 字典不存在
     */
    @Transactional(readOnly = true)
    public ScrmDataDictionaryDto getDictionaryByCode(String code) throws ScrmException {
        if (code == null || code.isBlank()) {
            throw ScrmException.badRequest("字典编码不能为空");
        }
        return toDictionaryDto(findDictionaryByCodeOrThrow(code));
    }

    /**
     * 分页查询字典, 支持按类型 / 分类 / 模块 / 启用状态 / 关键词过滤。
     *
     * @param queryDto 查询条件
     * @param pageable 分页参数
     * @return 字典分页结果 (按排序值与创建时间倒序)
     */
    @Transactional(readOnly = true)
    public Page<ScrmDataDictionaryDto> listDictionaries(ScrmDictQueryDto queryDto, Pageable pageable) {
        Specification<ScrmDataDictionaryEntity> spec = buildDictionarySpec(queryDto);
        return dictionaryRepository.findAll(spec, pageable).map(this::toDictionaryDto);
    }

    /**
     * 启用字典。
     *
     * @param id 字典 ID
     * @return 更新后的字典
     * @throws ScrmException 字典不存在
     */
    @Transactional
    public ScrmDataDictionaryDto enableDictionary(Long id) throws ScrmException {
        ScrmDataDictionaryEntity entity = findDictionaryOrThrow(id);
        entity.setEnabled(Boolean.TRUE);
        entity = dictionaryRepository.save(entity);
        cacheService.evictCache(entity.getDictCode());
        log.info("启用字典: id={}, dictCode={}", id, entity.getDictCode());
        return toDictionaryDto(entity);
    }

    /**
     * 禁用字典。
     *
     * @param id 字典 ID
     * @return 更新后的字典
     * @throws ScrmException 字典不存在 / 系统内置字典不允许禁用
     */
    @Transactional
    public ScrmDataDictionaryDto disableDictionary(Long id) throws ScrmException {
        ScrmDataDictionaryEntity entity = findDictionaryOrThrow(id);
        if (Boolean.TRUE.equals(entity.getIsSystem())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "系统内置字典不允许禁用: dictCode=" + entity.getDictCode());
        }
        entity.setEnabled(Boolean.FALSE);
        entity = dictionaryRepository.save(entity);
        cacheService.evictCache(entity.getDictCode());
        log.info("禁用字典: id={}, dictCode={}", id, entity.getDictCode());
        return toDictionaryDto(entity);
    }

    /**
     * 复制字典 (含字典项), 新字典编码由入参指定。
     * <p>新字典的 dictName 缺省为 "原字典名_副本", isSystem 强制为 false。</p>
     *
     * @param id      源字典 ID
     * @param newCode 新字典编码
     * @return 新字典
     * @throws ScrmException 字典不存在 / 新编码已存在
     */
    @Transactional
    public ScrmDataDictionaryDto copyDictionary(Long id, String newCode) throws ScrmException {
        if (newCode == null || newCode.isBlank()) {
            throw ScrmException.badRequest("新字典编码不能为空");
        }
        ScrmDataDictionaryEntity source = findDictionaryOrThrow(id);
        if (dictionaryRepository.existsByDictCode(newCode)) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    "字典编码已存在: dictCode=" + newCode);
        }
        ScrmDataDictionaryEntity target = new ScrmDataDictionaryEntity();
        target.setDictName(source.getDictName() + "_副本");
        target.setDictCode(newCode);
        target.setDescription(source.getDescription());
        target.setDictType(source.getDictType());
        target.setCategory(source.getCategory());
        target.setModule(source.getModule());
        target.setApplicableScenarios(source.getApplicableScenarios());
        target.setItemCount(0);
        target.setIsSystem(Boolean.FALSE);
        target.setIsCacheable(source.getIsCacheable());
        target.setCacheTtlSeconds(source.getCacheTtlSeconds());
        target.setSortOrder(source.getSortOrder());
        target.setEnabled(Boolean.TRUE);
        target.setUsageCount(0);
        target.setCreatedBy(source.getCreatedBy());
        target = dictionaryRepository.save(target);
        // 复制字典项 (保持树形结构需通过原 ID → 新 ID 映射重写 parentId / itemPath)
        List<ScrmDataDictionaryItemEntity> sourceItems = itemRepository
                .findByDictIdOrderBySortOrderAsc(id);
        Map<Long, Long> idMapping = new HashMap<>();
        for (ScrmDataDictionaryItemEntity src : sourceItems) {
            ScrmDataDictionaryItemEntity dst = new ScrmDataDictionaryItemEntity();
            dst.setDictId(target.getId());
            dst.setDictCode(target.getDictCode());
            dst.setItemLabel(src.getItemLabel());
            dst.setItemValue(src.getItemValue());
            dst.setItemCode(src.getItemCode());
            dst.setParentId(src.getParentId() != null ? idMapping.get(src.getParentId()) : null);
            dst.setItemLevel(src.getItemLevel());
            dst.setSortOrder(src.getSortOrder());
            dst.setItemStyle(src.getItemStyle());
            dst.setColor(src.getColor());
            dst.setIcon(src.getIcon());
            dst.setDescription(src.getDescription());
            dst.setExtraData(src.getExtraData());
            dst.setTags(src.getTags());
            dst.setIsDefault(src.getIsDefault());
            dst.setIsDisabled(src.getIsDisabled());
            dst.setIsVisible(src.getIsVisible());
            dst.setUsageCount(0);
            dst.setEnabled(src.getEnabled());
            dst.setCreatedBy(src.getCreatedBy());
            dst = itemRepository.save(dst);
            idMapping.put(src.getId(), dst.getId());
            // 重建 itemPath: 用新 ID 替换路径中的旧 ID
            String newPath = rebuildPath(src.getItemPath(), idMapping);
            dst.setItemPath(newPath);
            itemRepository.save(dst);
        }
        target.setItemCount(idMapping.size());
        dictionaryRepository.save(target);
        log.info("复制字典: sourceId={}, targetId={}, newCode={}", id, target.getId(), newCode);
        return toDictionaryDto(target);
    }

    /**
     * 合并字典: 将源字典的字典项合并到目标字典, 重复项 (按 itemCode 或 itemValue) 跳过。
     * <p>合并完成后源字典保留 (不删除), 仅迁移字典项。</p>
     *
     * @param sourceId 源字典 ID
     * @param targetId 目标字典 ID
     * @return 目标字典
     * @throws ScrmException 字典不存在 / 源与目标相同
     */
    @Transactional
    public ScrmDataDictionaryDto mergeDictionaries(Long sourceId, Long targetId) throws ScrmException {
        if (sourceId == null || targetId == null) {
            throw ScrmException.badRequest("源字典 ID 与目标字典 ID 不能为空");
        }
        if (sourceId.equals(targetId)) {
            throw ScrmException.badRequest("源字典与目标字典不能相同");
        }
        findDictionaryOrThrow(sourceId);
        ScrmDataDictionaryEntity target = findDictionaryOrThrow(targetId);
        List<ScrmDataDictionaryItemEntity> sourceItems = itemRepository
                .findByDictIdOrderBySortOrderAsc(sourceId);
        List<ScrmDataDictionaryItemEntity> targetItems = itemRepository
                .findByDictIdOrderBySortOrderAsc(targetId);
        Set<String> targetCodes = targetItems.stream()
                .map(ScrmDataDictionaryItemEntity::getItemCode)
                .filter(c -> c != null && !c.isBlank())
                .collect(Collectors.toSet());
        Set<String> targetValues = targetItems.stream()
                .map(ScrmDataDictionaryItemEntity::getItemValue)
                .collect(Collectors.toSet());
        int merged = 0;
        Map<Long, Long> idMapping = new HashMap<>();
        for (ScrmDataDictionaryItemEntity src : sourceItems) {
            boolean dupCode = src.getItemCode() != null && !src.getItemCode().isBlank()
                    && targetCodes.contains(src.getItemCode());
            boolean dupValue = targetValues.contains(src.getItemValue());
            if (dupCode || dupValue) {
                continue;
            }
            ScrmDataDictionaryItemEntity dst = new ScrmDataDictionaryItemEntity();
            dst.setDictId(target.getId());
            dst.setDictCode(target.getDictCode());
            dst.setItemLabel(src.getItemLabel());
            dst.setItemValue(src.getItemValue());
            dst.setItemCode(src.getItemCode());
            dst.setParentId(src.getParentId() != null ? idMapping.get(src.getParentId()) : null);
            dst.setItemLevel(src.getItemLevel());
            dst.setSortOrder(src.getSortOrder());
            dst.setItemStyle(src.getItemStyle());
            dst.setColor(src.getColor());
            dst.setIcon(src.getIcon());
            dst.setDescription(src.getDescription());
            dst.setExtraData(src.getExtraData());
            dst.setTags(src.getTags());
            dst.setIsDefault(src.getIsDefault());
            dst.setIsDisabled(src.getIsDisabled());
            dst.setIsVisible(src.getIsVisible());
            dst.setUsageCount(0);
            dst.setEnabled(src.getEnabled());
            dst.setCreatedBy(src.getCreatedBy());
            dst = itemRepository.save(dst);
            idMapping.put(src.getId(), dst.getId());
            dst.setItemPath(rebuildPath(src.getItemPath(), idMapping));
            itemRepository.save(dst);
            if (src.getItemCode() != null && !src.getItemCode().isBlank()) {
                targetCodes.add(src.getItemCode());
            }
            targetValues.add(src.getItemValue());
            merged++;
        }
        target.setItemCount(targetItems.size() + merged);
        dictionaryRepository.save(target);
        cacheService.evictCache(target.getDictCode());
        log.info("合并字典: sourceId={}, targetId={}, merged={}", sourceId, targetId, merged);
        return toDictionaryDto(target);
    }

    /**
     * 更新字典统计 (重新计算 itemCount 与 usageCount)。
     *
     * @param id 字典 ID
     * @return 更新后的字典
     * @throws ScrmException 字典不存在
     */
    @Transactional
    public ScrmDataDictionaryDto updateDictionaryStats(Long id) throws ScrmException {
        ScrmDataDictionaryEntity entity = findDictionaryOrThrow(id);
        long itemCount = itemRepository.countByDictId(id);
        List<ScrmDataDictionaryUsageEntity> usages = usageRepository.findByDictId(id);
        long usageCount = usages.stream()
                .mapToLong(u -> u.getUsageCount() != null ? u.getUsageCount() : 0)
                .sum();
        entity.setItemCount((int) itemCount);
        entity.setUsageCount((int) usageCount);
        if (!usages.isEmpty()) {
            entity.setLastUsedAt(usages.stream()
                    .map(ScrmDataDictionaryUsageEntity::getLastUsedAt)
                    .filter(t -> t != null)
                    .max(LocalDateTime::compareTo)
                    .orElse(null));
        }
        entity = dictionaryRepository.save(entity);
        cacheService.evictCache(entity.getDictCode());
        return toDictionaryDto(entity);
    }

    /**
     * 字典树 (分类 → 字典列表)。
     * <p>仅返回启用字典, 按分类分组, 未分类的归入 "UNGROUPED"。</p>
     *
     * @return 分类 → 字典列表 映射
     */
    @Transactional(readOnly = true)
    public Map<String, List<ScrmDataDictionaryDto>> getDictionaryTree() {
        List<ScrmDataDictionaryEntity> all = dictionaryRepository
                .findByEnabledTrue();
        Map<String, List<ScrmDataDictionaryDto>> tree = new LinkedHashMap<>();
        for (ScrmDataDictionaryEntity entity : all) {
            String key = entity.getCategory() != null && !entity.getCategory().isBlank()
                    ? entity.getCategory() : "UNGROUPED";
            tree.computeIfAbsent(key, k -> new ArrayList<>()).add(toDictionaryDto(entity));
        }
        return tree;
    }

    /**
     * 导出字典 (含字典元信息与全部字典项)。
     *
     * @param dictCode 字典编码
     * @return 导出数据 (dictionary + items)
     * @throws ScrmException 字典不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> exportDictionary(String dictCode) throws ScrmException {
        if (dictCode == null || dictCode.isBlank()) {
            throw ScrmException.badRequest("字典编码不能为空");
        }
        ScrmDataDictionaryEntity dict = findDictionaryByCodeOrThrow(dictCode);
        List<ScrmDataDictionaryItemDto> items = itemRepository
                .findByDictCodeOrderBySortOrderAsc(dictCode)
                .stream().map(ScrmDataDictionaryItemService::toItemDto).collect(Collectors.toList());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dictionary", toDictionaryDto(dict));
        result.put("items", items);
        result.put("exportedAt", LocalDateTime.now());
        return result;
    }

    /**
     * 导出全部字典 (含字典项)。
     *
     * @return 全部字典导出数据列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> exportAllDictionaries() {
        List<ScrmDataDictionaryEntity> dicts = dictionaryRepository.findAll(
                (root, query, cb) -> cb.and(),
                Sort.by(Sort.Direction.ASC, "sortOrder"));
        List<Map<String, Object>> result = new ArrayList<>();
        for (ScrmDataDictionaryEntity dict : dicts) {
            List<ScrmDataDictionaryItemDto> items = itemRepository
                    .findByDictIdOrderBySortOrderAsc(dict.getId())
                    .stream().map(ScrmDataDictionaryItemService::toItemDto).collect(Collectors.toList());
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("dictionary", toDictionaryDto(dict));
            entry.put("items", items);
            result.add(entry);
        }
        return result;
    }

    /**
     * 导入字典 (字典元信息 + 字典项)。
     * <p>若字典编码已存在则跳过或更新 (overwrite=true 时); 字典项按 itemCode / itemValue 去重。
     * </p>
     *
     * @param importData 导入数据
     * @return 导入结果
     * @throws ScrmException 参数非法
     */
    @Transactional
    public Map<String, Object> importDictionary(Map<String, Object> importData) throws ScrmException {
        if (importData == null || importData.get("dictionary") == null) {
            throw ScrmException.badRequest("导入数据不能为空且必须包含 dictionary 字段");
        }
        Object dictObj = importData.get("dictionary");
        if (!(dictObj instanceof Map)) {
            throw ScrmException.badRequest("dictionary 字段需为对象");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> dictMap = (Map<String, Object>) dictObj;
        ScrmDataDictionaryDto dictDto = mapToDictionaryDto(dictMap);
        boolean overwrite = Boolean.TRUE.equals(importData.get("overwrite"));
        ScrmDataDictionaryEntity dict;
        boolean dictCreated;
        if (dictionaryRepository.existsByDictCode(dictDto.getDictCode())) {
            if (overwrite) {
                dict = findDictionaryByCodeOrThrow(dictDto.getDictCode());
                if (dictDto.getDictName() != null) dict.setDictName(dictDto.getDictName());
                if (dictDto.getDescription() != null) dict.setDescription(dictDto.getDescription());
                if (dictDto.getDictType() != null) dict.setDictType(dictDto.getDictType());
                if (dictDto.getCategory() != null) dict.setCategory(dictDto.getCategory());
                if (dictDto.getModule() != null) dict.setModule(dictDto.getModule());
                if (dictDto.getApplicableScenarios() != null) dict.setApplicableScenarios(
                        dictDto.getApplicableScenarios());
                if (dictDto.getSortOrder() != null) dict.setSortOrder(dictDto.getSortOrder());
                dict = dictionaryRepository.save(dict);
                dictCreated = false;
            } else {
                Map<String, Object> skip = new LinkedHashMap<>();
                skip.put("dictCode", dictDto.getDictCode());
                skip.put("action", "skipped");
                skip.put("reason", "字典编码已存在");
                return skip;
            }
        } else {
            dictDto.setId(null);
            createDictionary(dictDto);
            dict = findDictionaryByCodeOrThrow(dictDto.getDictCode());
            dictCreated = true;
        }
        int itemInserted = 0;
        int itemSkipped = 0;
        Object itemsObj = importData.get("items");
        if (itemsObj instanceof List) {
            List<ScrmDataDictionaryItemEntity> existing = itemRepository
                    .findByDictIdOrderBySortOrderAsc(dict.getId());
            Set<String> existCodes = existing.stream()
                    .map(ScrmDataDictionaryItemEntity::getItemCode)
                    .filter(c -> c != null && !c.isBlank())
                    .collect(Collectors.toSet());
            Set<String> existValues = existing.stream()
                    .map(ScrmDataDictionaryItemEntity::getItemValue)
                    .collect(Collectors.toSet());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> itemsList = (List<Map<String, Object>>) itemsObj;
            for (Map<String, Object> itemMap : itemsList) {
                ScrmDataDictionaryItemDto itemDto = mapToItemDto(itemMap, dict.getId(), dict.getDictCode());
                boolean dupCode = itemDto.getItemCode() != null && !itemDto.getItemCode().isBlank()
                    && existCodes.contains(itemDto.getItemCode());
                boolean dupValue = existValues.contains(itemDto.getItemValue());
                if (dupCode || dupValue) {
                    itemSkipped++;
                    continue;
                }
                if (itemDto.getItemCode() != null) existCodes.add(itemDto.getItemCode());
                existValues.add(itemDto.getItemValue());
                ScrmDataDictionaryItemEntity entity = new ScrmDataDictionaryItemEntity();
                entity.setDictId(dict.getId());
                entity.setDictCode(dict.getDictCode());
                itemService.applyItemFields(entity, itemDto);
                entity.setItemLevel(itemDto.getItemLevel() != null ? itemDto.getItemLevel()
                    : ScrmDataDictionaryItemService.DEFAULT_ITEM_LEVEL);
                entity.setUsageCount(0);
                if (entity.getEnabled() == null) entity.setEnabled(Boolean.TRUE);
                if (entity.getIsDefault() == null) entity.setIsDefault(Boolean.FALSE);
                if (entity.getIsDisabled() == null) entity.setIsDisabled(Boolean.FALSE);
                if (entity.getIsVisible() == null) entity.setIsVisible(Boolean.TRUE);
                entity = itemRepository.save(entity);
                if (entity.getParentId() == null) {
                    entity.setItemPath(entity.getId() + ScrmDataDictionaryItemService.PATH_SEPARATOR);
                    itemRepository.save(entity);
                }
                itemInserted++;
            }
            itemService.refreshItemCount(dict.getId());
        }
        cacheService.evictCache(dict.getDictCode());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dictCode", dict.getDictCode());
        result.put("dictCreated", dictCreated);
        result.put("itemsInserted", itemInserted);
        result.put("itemsSkipped", itemSkipped);
        log.info("导入字典: dictCode={}, created={}, inserted={}, skipped={}",
                dict.getDictCode(), dictCreated, itemInserted, itemSkipped);
        return result;
    }

    /**
     * 获取导入模板 (空字典 + 空字典项示例)。
     *
     * @return 模板数据
     */
    public Map<String, Object> getExportTemplate() {
        Map<String, Object> dictionary = new LinkedHashMap<>();
        dictionary.put("dictName", "示例字典");
        dictionary.put("dictCode", "EXAMPLE_DICT");
        dictionary.put("description", "字典描述");
        dictionary.put("dictType", "LIST");
        dictionary.put("category", "BUSINESS");
        dictionary.put("module", "customer");
        dictionary.put("applicableScenarios", "表单下拉 / 报表过滤");
        dictionary.put("sortOrder", 0);
        dictionary.put("isCacheable", true);
        dictionary.put("cacheTtlSeconds", 3600);
        dictionary.put("createdBy", "importer");

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("itemLabel", "示例项");
        item.put("itemValue", "EXAMPLE");
        item.put("itemCode", "EXAMPLE");
        item.put("sortOrder", 0);
        item.put("itemStyle", "DEFAULT");
        item.put("isDefault", true);

        Map<String, Object> template = new LinkedHashMap<>();
        template.put("dictionary", dictionary);
        template.put("items", List.of(item));
        template.put("overwrite", false);
        return template;
    }

    // ============================================================
    // 私有辅助方法
    // ============================================================

    /**
     * 校验字典参数。
     *
     * @param dto     字典参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateDictionaryDto(ScrmDataDictionaryDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("字典参数不能为空");
        }
        if (!partial) {
            if (dto.getDictName() == null || dto.getDictName().isBlank()) {
                throw ScrmException.badRequest("字典名称不能为空");
            }
            if (dto.getDictCode() == null || dto.getDictCode().isBlank()) {
                throw ScrmException.badRequest("字典编码不能为空");
            }
        }
        if (dto.getDictType() != null && !dto.getDictType().isBlank()) {
            validateDictType(dto.getDictType());
        }
        if (dto.getCategory() != null && !dto.getCategory().isBlank()) {
            validateCategory(dto.getCategory());
        }
    }

    /**
     * 校验字典类型合法性。
     *
     * @param dictType 字典类型
     * @throws ScrmException 参数非法
     */
    private void validateDictType(String dictType) throws ScrmException {
        if (!VALID_DICT_TYPES.contains(dictType)) {
            throw ScrmException.badRequest("字典类型非法: " + dictType
                    + ", 合法值: LIST / TREE / CASCADE / MULTI_LEVEL");
        }
    }

    /**
     * 校验字典分类合法性。
     *
     * @param category 字典分类
     * @throws ScrmException 参数非法
     */
    private void validateCategory(String category) throws ScrmException {
        if (!VALID_CATEGORIES.contains(category)) {
            throw ScrmException.badRequest("字典分类非法: " + category
                    + ", 合法值: SYSTEM / BUSINESS / CUSTOM / INDUSTRY / REGION");
        }
    }

    /**
     * 构建字典查询条件 Specification。
     *
     * @param queryDto 查询条件
     * @return 查询条件
     */
    private Specification<ScrmDataDictionaryEntity> buildDictionarySpec(ScrmDictQueryDto queryDto) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (queryDto != null) {
                if (queryDto.getDictType() != null && !queryDto.getDictType().isBlank()) {
                    predicates.add(cb.equal(root.get("dictType"), queryDto.getDictType()));
                }
                if (queryDto.getCategory() != null && !queryDto.getCategory().isBlank()) {
                    predicates.add(cb.equal(root.get("category"), queryDto.getCategory()));
                }
                if (queryDto.getModule() != null && !queryDto.getModule().isBlank()) {
                    predicates.add(cb.equal(root.get("module"), queryDto.getModule()));
                }
                if (queryDto.getEnabled() != null) {
                    predicates.add(cb.equal(root.get("enabled"), queryDto.getEnabled()));
                }
                if (queryDto.getIsSystem() != null) {
                    predicates.add(cb.equal(root.get("isSystem"), queryDto.getIsSystem()));
                }
                if (queryDto.getKeyword() != null && !queryDto.getKeyword().isBlank()) {
                    String like = "%" + queryDto.getKeyword().toLowerCase() + "%";
                    predicates.add(cb.or(
                            cb.like(cb.lower(root.get("dictName")), like),
                            cb.like(cb.lower(root.get("dictCode")), like),
                            cb.like(cb.lower(root.get("description")), like)));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
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
     * 字典实体转 DTO。
     *
     * @param entity 字典实体
     * @return 字典 DTO
     */
    private ScrmDataDictionaryDto toDictionaryDto(ScrmDataDictionaryEntity entity) {
        ScrmDataDictionaryDto dto = new ScrmDataDictionaryDto();
        dto.setId(entity.getId());
        dto.setDictName(entity.getDictName());
        dto.setDictCode(entity.getDictCode());
        dto.setDescription(entity.getDescription());
        dto.setDictType(entity.getDictType());
        dto.setCategory(entity.getCategory());
        dto.setParentId(entity.getParentId());
        dto.setModule(entity.getModule());
        dto.setApplicableScenarios(entity.getApplicableScenarios());
        dto.setItemCount(entity.getItemCount());
        dto.setIsSystem(entity.getIsSystem());
        dto.setIsCacheable(entity.getIsCacheable());
        dto.setCacheTtlSeconds(entity.getCacheTtlSeconds());
        dto.setSortOrder(entity.getSortOrder());
        dto.setEnabled(entity.getEnabled());
        dto.setUsageCount(entity.getUsageCount());
        dto.setLastUsedAt(entity.getLastUsedAt());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 用新 ID 映射重建路径 (复制/合并字典项时使用)。
     *
     * @param sourcePath 源路径 (形如 '1/5/12/')
     * @param idMapping  旧 ID → 新 ID 映射
     * @return 重建后的路径
     */
    private String rebuildPath(String sourcePath, Map<Long, Long> idMapping) {
        if (sourcePath == null || sourcePath.isBlank()) {
            return null;
        }
        String[] parts = sourcePath.split(ScrmDataDictionaryItemService.PATH_SEPARATOR);
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            try {
                Long oldId = Long.valueOf(p);
                Long newId = idMapping.get(oldId);
                if (newId != null) {
                    sb.append(newId).append(ScrmDataDictionaryItemService.PATH_SEPARATOR);
                }
            } catch (NumberFormatException ignored) {
                // 跳过非数字片段
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /**
     * 将 Map 转为字典 DTO (导入用, 容错转换)。
     *
     * @param map 导入数据 Map
     * @return 字典 DTO
     */
    private ScrmDataDictionaryDto mapToDictionaryDto(Map<String, Object> map) {
        ScrmDataDictionaryDto dto = new ScrmDataDictionaryDto();
        dto.setDictName(asString(map.get("dictName")));
        dto.setDictCode(asString(map.get("dictCode")));
        dto.setDescription(asString(map.get("description")));
        dto.setDictType(asString(map.get("dictType")));
        dto.setCategory(asString(map.get("category")));
        dto.setModule(asString(map.get("module")));
        dto.setApplicableScenarios(asString(map.get("applicableScenarios")));
        dto.setSortOrder(asInt(map.get("sortOrder")));
        dto.setIsCacheable(asBool(map.get("isCacheable")));
        dto.setCacheTtlSeconds(asInt(map.get("cacheTtlSeconds")));
        dto.setEnabled(asBool(map.get("enabled")));
        dto.setCreatedBy(asString(map.get("createdBy")));
        return dto;
    }

    /**
     * 将 Map 转为字典项 DTO (导入用, 容错转换)。
     *
     * @param map     导入数据 Map
     * @param dictId  字典 ID
     * @param dictCode 字典编码
     * @return 字典项 DTO
     */
    private ScrmDataDictionaryItemDto mapToItemDto(Map<String, Object> map, Long dictId, String dictCode) {
        ScrmDataDictionaryItemDto dto = new ScrmDataDictionaryItemDto();
        dto.setDictId(dictId);
        dto.setDictCode(dictCode);
        dto.setItemLabel(asString(map.get("itemLabel")));
        dto.setItemValue(asString(map.get("itemValue")));
        dto.setItemCode(asString(map.get("itemCode")));
        dto.setSortOrder(asInt(map.get("sortOrder")));
        dto.setItemStyle(asString(map.get("itemStyle")));
        dto.setColor(asString(map.get("color")));
        dto.setIcon(asString(map.get("icon")));
        dto.setDescription(asString(map.get("description")));
        Object extra = map.get("extraData");
        if (extra != null) {
            dto.setExtraData(extra instanceof String ? (String) extra : extra.toString());
        }
        dto.setTags(asString(map.get("tags")));
        dto.setIsDefault(asBool(map.get("isDefault")));
        dto.setIsDisabled(asBool(map.get("isDisabled")));
        dto.setIsVisible(asBool(map.get("isVisible")));
        dto.setEnabled(asBool(map.get("enabled")));
        dto.setItemLevel(asInt(map.get("itemLevel")));
        dto.setCreatedBy(asString(map.get("createdBy")));
        return dto;
    }

    /**
     * 容错转字符串。
     *
     * @param obj 原始值
     * @return 字符串
     */
    private String asString(Object obj) {
        return obj == null ? null : String.valueOf(obj);
    }

    /**
     * 容错转 Integer。
     *
     * @param obj 原始值
     * @return Integer 值
     */
    private Integer asInt(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            return Integer.valueOf(String.valueOf(obj));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 容错转 Boolean。
     *
     * @param obj 原始值
     * @return Boolean 值
     */
    private Boolean asBool(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Boolean) return (Boolean) obj;
        return Boolean.valueOf(String.valueOf(obj));
    }
}