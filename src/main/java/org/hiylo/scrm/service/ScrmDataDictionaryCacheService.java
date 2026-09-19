/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmDataDictionaryCacheService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmDataDictionaryItemDto;
import org.hiylo.scrm.dto.ScrmDataDictionaryUsageDto;
import org.hiylo.scrm.entity.ScrmDataDictionaryEntity;
import org.hiylo.scrm.entity.ScrmDataDictionaryItemEntity;
import org.hiylo.scrm.entity.ScrmDataDictionaryUsageEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmDataDictionaryItemRepository;
import org.hiylo.scrm.repository.ScrmDataDictionaryRepository;
import org.hiylo.scrm.repository.ScrmDataDictionaryUsageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * SCRM 数据字典缓存与使用统计服务。
 * <p>
 * 承载缓存与使用统计子域: 字典项内存缓存 (Map 模拟, 可替换为 Redis) 的读取 / 刷新 /
 * 清除、缓存统计、字典使用记录的写入与多维统计、未使用项查询 / 热门项 / 过期记录
 * 清理。缓存存储与失效方法 ({@link #evictCache(String)}) 供字典管理与字典项管理
 * 兄弟类调用。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmDataDictionaryCacheService {

    // ==================== 常量约定 (共享) ====================

    /** 默认缓存 TTL 秒数 */
    static final int DEFAULT_CACHE_TTL_SECONDS = 3600;

    // ==================== 缓存存储 ====================

    /**
     * 字典缓存 (内存 Map 模拟, 可替换为 Redis)。
     * <p>key = 字典编码, value = 缓存条目 (含字典项列表与过期时间)。</p>
     */
    private final Map<String, CacheEntry> dictCache = new ConcurrentHashMap<>();

    // ==================== 依赖注入 ====================

    /** 字典数据访问层 */
    private final ScrmDataDictionaryRepository dictionaryRepository;
    /** 字典项数据访问层 */
    private final ScrmDataDictionaryItemRepository itemRepository;
    /** 字典使用记录数据访问层 */
    private final ScrmDataDictionaryUsageRepository usageRepository;

    /**
     * 获取缓存的字典项 (若缓存未命中或已过期, 自动加载并缓存)。
     * <p>仅可缓存字典 (isCacheable=true) 才会缓存; 否则每次直接查库。</p>
     *
     * @param dictCode 字典编码
     * @return 字典项列表 (仅启用且可见项)
     * @throws ScrmException 字典不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmDataDictionaryItemDto> getCachedItems(String dictCode) throws ScrmException {
        if (dictCode == null || dictCode.isBlank()) {
            throw ScrmException.badRequest("字典编码不能为空");
        }
        ScrmDataDictionaryEntity dict = findDictionaryByCodeOrThrow(dictCode);
        if (!Boolean.TRUE.equals(dict.getIsCacheable())) {
            return loadVisibleItems(dictCode);
        }
        String cacheKey = buildCacheKey(dictCode);
        CacheEntry entry = dictCache.get(cacheKey);
        int ttl = dict.getCacheTtlSeconds() != null ? dict.getCacheTtlSeconds() : DEFAULT_CACHE_TTL_SECONDS;
        if (entry != null && !entry.isExpired()) {
            return entry.items;
        }
        List<ScrmDataDictionaryItemDto> items = loadVisibleItems(dictCode);
        dictCache.put(cacheKey, new CacheEntry(items, System.currentTimeMillis() + ttl * 1000L));
        return items;
    }

    /**
     * 刷新字典缓存 (强制重新加载)。
     *
     * @param dictCode 字典编码
     * @return 刷新后的字典项列表
     * @throws ScrmException 字典不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmDataDictionaryItemDto> refreshCache(String dictCode) throws ScrmException {
        if (dictCode == null || dictCode.isBlank()) {
            throw ScrmException.badRequest("字典编码不能为空");
        }
        ScrmDataDictionaryEntity dict = findDictionaryByCodeOrThrow(dictCode);
        evictCache(dictCode);
        if (!Boolean.TRUE.equals(dict.getIsCacheable())) {
            return loadVisibleItems(dictCode);
        }
        return getCachedItems(dictCode);
    }

    /**
     * 清除指定字典缓存。
     *
     * @param dictCode 字典编码
     */
    public void clearCache(String dictCode) {
        if (dictCode != null && !dictCode.isBlank()) {
            dictCache.remove(buildCacheKey(dictCode));
            log.info("清除字典缓存: dictCode={}", dictCode);
        }
    }

    /**
     * 清除所有字典缓存。
     *
     * @return 清除的缓存条目数
     */
    public Map<String, Object> clearAllCache() {
        int size = dictCache.size();
        dictCache.clear();
        log.info("清除全部字典缓存: count={}", size);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cleared", size);
        return result;
    }

    /**
     * 缓存统计 (缓存条目数 / 命中的字典编码列表)。
     *
     * @return 统计结果
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("size", dictCache.size());
        List<String> keys = new ArrayList<>();
        long now = System.currentTimeMillis();
        int expired = 0;
        for (Map.Entry<String, CacheEntry> e : dictCache.entrySet()) {
            keys.add(e.getKey());
            if (e.getValue().isExpired(now)) {
                expired++;
            }
        }
        stats.put("keys", keys);
        stats.put("expired", expired);
        return stats;
    }

    /**
     * 记录字典使用 (幂等累加或覆盖)。
     * <p>按 账号 + 字典 + 字典项 + 使用模块 定位使用记录: 已存在则按 increment 模式
     * 累加或覆盖, 不存在则新建。同时更新字典与字典项的 usageCount 与 lastUsedAt。</p>
     *
     * @param usageDto 使用记录参数
     * @return 使用记录
     * @throws ScrmException 参数非法 / 字典不存在
     */
    @Transactional
    public ScrmDataDictionaryUsageDto recordUsage(ScrmDataDictionaryUsageDto usageDto) throws ScrmException {
        if (usageDto == null) {
            throw ScrmException.badRequest("使用记录参数不能为空");
        }
        if (usageDto.getDictId() == null || usageDto.getDictCode() == null || usageDto.getUsageModule() == null) {
            throw ScrmException.badRequest("字典 ID / 字典编码 / 使用模块不能为空");
        }
        ScrmDataDictionaryEntity dict = findDictionaryByCodeOrThrow(usageDto.getDictCode());
        if (!dict.getId().equals(usageDto.getDictId())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "字典 ID 与字典编码不匹配");
        }
        boolean increment = usageDto.getIncrement() == null || usageDto.getIncrement();
        int delta = usageDto.getUsageCount() != null && usageDto.getUsageCount() > 0
                ? usageDto.getUsageCount() : 1;
        LocalDateTime now = LocalDateTime.now();
        ScrmDataDictionaryUsageEntity entity = usageRepository
                .findByDictIdAndItemIdAndUsageModule(
                         dict.getId(), usageDto.getItemId(), usageDto.getUsageModule())
                .orElse(null);
        if (entity == null) {
            entity = new ScrmDataDictionaryUsageEntity();
            entity.setDictId(dict.getId());
            entity.setDictCode(dict.getDictCode());
            entity.setItemId(usageDto.getItemId());
            entity.setItemValue(usageDto.getItemValue());
            entity.setUsageModule(usageDto.getUsageModule());
            entity.setUsageEntity(usageDto.getUsageEntity());
            entity.setUsageField(usageDto.getUsageField());
            entity.setUsageScenario(usageDto.getUsageScenario());
            if (increment) {
                entity.setUsageCount(delta);
            } else {
                entity.setUsageCount(usageDto.getUsageCount());
            }
            entity.setFirstUsedAt(now);
            entity.setLastUsedAt(now);
            entity.setNotes(usageDto.getNotes());
        } else {
            if (increment) {
                entity.setUsageCount((entity.getUsageCount() != null ? entity.getUsageCount() : 0) + delta);
            } else {
                entity.setUsageCount(usageDto.getUsageCount());
            }
            entity.setLastUsedAt(now);
            if (usageDto.getNotes() != null) entity.setNotes(usageDto.getNotes());
            if (usageDto.getItemValue() != null) entity.setItemValue(usageDto.getItemValue());
            if (usageDto.getUsageEntity() != null) entity.setUsageEntity(usageDto.getUsageEntity());
            if (usageDto.getUsageField() != null) entity.setUsageField(usageDto.getUsageField());
            if (usageDto.getUsageScenario() != null) entity.setUsageScenario(usageDto.getUsageScenario());
        }
        entity = usageRepository.save(entity);
        // 更新字典与字典项的冗余统计
        dict.setUsageCount((dict.getUsageCount() != null ? dict.getUsageCount() : 0) + delta);
        dict.setLastUsedAt(now);
        dictionaryRepository.save(dict);
        if (usageDto.getItemId() != null) {
            itemRepository.findById(usageDto.getItemId()).ifPresent(item -> {
                item.setUsageCount((item.getUsageCount() != null ? item.getUsageCount() : 0) + delta);
                itemRepository.save(item);
            });
        }
        evictCache(dict.getDictCode());
        return toUsageDto(entity);
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
    @Transactional(readOnly = true)
    public Map<String, Object> getUsageStats(Long dictId, LocalDateTime startTime, LocalDateTime endTime)
            throws ScrmException {
        findDictionaryOrThrow(dictId);
        List<ScrmDataDictionaryUsageEntity> usages = usageRepository.findByDictId(dictId);
        long total = 0;
        Map<String, Integer> byItem = new LinkedHashMap<>();
        Map<String, Integer> byModule = new LinkedHashMap<>();
        for (ScrmDataDictionaryUsageEntity u : usages) {
            if (u.getLastUsedAt() != null) {
                if (startTime != null && u.getLastUsedAt().isBefore(startTime)) continue;
                if (endTime != null && u.getLastUsedAt().isAfter(endTime)) continue;
            }
            int count = u.getUsageCount() != null ? u.getUsageCount() : 0;
            total += count;
            String itemKey = u.getItemId() != null ? String.valueOf(u.getItemId()) : "DICT_LEVEL";
            byItem.merge(itemKey, count, Integer::sum);
            byModule.merge(u.getUsageModule(), count, Integer::sum);
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("byItem", byItem);
        stats.put("byModule", byModule);
        return stats;
    }

    /**
     * 按模块统计字典使用。
     *
     * @param dictId 字典 ID
     * @return 模块 → 使用次数
     * @throws ScrmException 字典不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getUsageByModule(Long dictId) throws ScrmException {
        findDictionaryOrThrow(dictId);
        List<ScrmDataDictionaryUsageEntity> usages = usageRepository.findByDictId(dictId);
        Map<String, Integer> byModule = new LinkedHashMap<>();
        long total = 0;
        for (ScrmDataDictionaryUsageEntity u : usages) {
            int count = u.getUsageCount() != null ? u.getUsageCount() : 0;
            byModule.merge(u.getUsageModule(), count, Integer::sum);
            total += count;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("byModule", byModule);
        return result;
    }

    /**
     * 查询未使用的字典项 (最近 days 天无使用记录)。
     *
     * @param dictId 字典 ID
     * @param days   天数阈值
     * @return 未使用的字典项列表
     * @throws ScrmException 字典不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmDataDictionaryItemDto> getUnusedItems(Long dictId, int days) throws ScrmException {
        findDictionaryOrThrow(dictId);
        LocalDateTime threshold = LocalDateTime.now().minusDays(Math.max(0, days));
        List<ScrmDataDictionaryItemEntity> items = itemRepository
                .findByDictIdOrderBySortOrderAsc(dictId);
        List<ScrmDataDictionaryUsageEntity> usages = usageRepository.findByDictId(dictId);
        Map<Long, LocalDateTime> lastUsedByItem = new HashMap<>();
        for (ScrmDataDictionaryUsageEntity u : usages) {
            if (u.getItemId() == null || u.getLastUsedAt() == null) continue;
            lastUsedByItem.merge(u.getItemId(), u.getLastUsedAt(),
                    (a, b) -> a.isAfter(b) ? a : b);
        }
        List<ScrmDataDictionaryItemDto> unused = new ArrayList<>();
        for (ScrmDataDictionaryItemEntity item : items) {
            LocalDateTime last = lastUsedByItem.get(item.getId());
            if (last == null || last.isBefore(threshold)) {
                unused.add(ScrmDataDictionaryItemService.toItemDto(item));
            }
        }
        return unused;
    }

    /**
     * 热门字典项 (按 usageCount 倒序取前 limit 条)。
     *
     * @param dictId 字典 ID
     * @param limit  取前 N 条
     * @return 热门字典项列表
     * @throws ScrmException 字典不存在
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getPopularItems(Long dictId, int limit) throws ScrmException {
        findDictionaryOrThrow(dictId);
        List<ScrmDataDictionaryUsageEntity> usages = usageRepository.findPopularByDict(dictId);
        int n = limit > 0 ? limit : 10;
        List<Map<String, Object>> popular = new ArrayList<>();
        for (int i = 0; i < Math.min(n, usages.size()); i++) {
            ScrmDataDictionaryUsageEntity u = usages.get(i);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("itemId", u.getItemId());
            entry.put("itemValue", u.getItemValue());
            entry.put("usageModule", u.getUsageModule());
            entry.put("usageCount", u.getUsageCount());
            entry.put("lastUsedAt", u.getLastUsedAt());
            popular.add(entry);
        }
        return popular;
    }

    /**
     * 清理过期使用记录 (lastUsedAt 早于 days 天前)。
     *
     * @param days 天数阈值
     * @return 清理的记录数
     */
    @Transactional
    public Map<String, Object> cleanupUsage(int days) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(Math.max(0, days));
        int deleted = usageRepository.deleteExpired(threshold);
        log.info("清理过期使用记录: days={}, deleted={}", days, deleted);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deleted", deleted);
        result.put("threshold", threshold);
        return result;
    }

    // ============================================================
    // 公共辅助方法 (供兄弟类复用)
    // ============================================================

    /**
     * 清除指定字典缓存 (内部便捷方法, 供兄弟类在写操作后调用)。
     *
     * @param dictCode 字典编码
     */
    void evictCache(String dictCode) {
        if (dictCode != null && !dictCode.isBlank()) {
            dictCache.remove(buildCacheKey(dictCode));
        }
    }

    // ============================================================
    // 私有辅助方法
    // ============================================================

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
     * 加载字典下可见 (isVisible=true) 且启用 (enabled=true) 的字典项。
     *
     * @param dictCode 字典编码
     * @return 字典项列表
     */
    private List<ScrmDataDictionaryItemDto> loadVisibleItems(String dictCode) {
        return itemRepository
                .findByDictCodeAndEnabledTrueOrderBySortOrderAsc(
                         dictCode)
                .stream()
                .filter(i -> Boolean.TRUE.equals(i.getIsVisible()))
                .map(ScrmDataDictionaryItemService::toItemDto)
                .collect(Collectors.toList());
    }

    /**
     * 构建缓存 key (字典编码)。
     *
     * @param dictCode 字典编码
     * @return 缓存 key
     */
    private String buildCacheKey(String dictCode) {
        return dictCode;
    }

    /**
     * 使用记录实体转 DTO。
     *
     * @param entity 使用记录实体
     * @return 使用记录 DTO
     */
    private ScrmDataDictionaryUsageDto toUsageDto(ScrmDataDictionaryUsageEntity entity) {
        ScrmDataDictionaryUsageDto dto = new ScrmDataDictionaryUsageDto();
        dto.setId(entity.getId());
        dto.setDictId(entity.getDictId());
        dto.setDictCode(entity.getDictCode());
        dto.setItemId(entity.getItemId());
        dto.setItemValue(entity.getItemValue());
        dto.setUsageModule(entity.getUsageModule());
        dto.setUsageEntity(entity.getUsageEntity());
        dto.setUsageField(entity.getUsageField());
        dto.setUsageScenario(entity.getUsageScenario());
        dto.setUsageCount(entity.getUsageCount());
        dto.setLastUsedAt(entity.getLastUsedAt());
        dto.setFirstUsedAt(entity.getFirstUsedAt());
        dto.setNotes(entity.getNotes());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        return dto;
    }

    /**
     * 缓存条目 (含过期时间, 内存 Map 模拟, 可替换为 Redis)。
     *
     * @since V1.0
     * @author Hsi Chu
     */
    private static class CacheEntry {
        /** 缓存的字典项列表 */
        final List<ScrmDataDictionaryItemDto> items;
        /** 过期时间戳 (毫秒) */
        final long expireAt;

        CacheEntry(List<ScrmDataDictionaryItemDto> items, long expireAt) {
            this.items = items;
            this.expireAt = expireAt;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }

        boolean isExpired(long now) {
            return now > expireAt;
        }
    }
}