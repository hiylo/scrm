/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmDataDictionaryStatsService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmDataDictionaryEntity;
import org.hiylo.scrm.entity.ScrmDataDictionaryItemEntity;
import org.hiylo.scrm.repository.ScrmDataDictionaryItemRepository;
import org.hiylo.scrm.repository.ScrmDataDictionaryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRM 数据字典统计概览服务。
 * <p>
 * 承载统计概览子域: 字典统计、字典项统计、模块统计与字典健康度。数字均取自数据
 * 访问层的真实数据。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmDataDictionaryStatsService {

    // ==================== 依赖注入 ====================

    /** 字典数据访问层 */
    private final ScrmDataDictionaryRepository dictionaryRepository;
    /** 字典项数据访问层 */
    private final ScrmDataDictionaryItemRepository itemRepository;

    /**
     * 字典统计 (总数 / 各类型 / 各分类 / 系统数 / 自定义数)。
     *
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getDictionaryStats() {
        List<ScrmDataDictionaryEntity> dicts = dictionaryRepository.findAll(
                (root, query, cb) -> cb.and());
        Map<String, Long> byType = new LinkedHashMap<>();
        for (String t : Arrays.asList(ScrmDataDictionaryManageService.DICT_TYPE_LIST,
                ScrmDataDictionaryManageService.DICT_TYPE_TREE,
                ScrmDataDictionaryManageService.DICT_TYPE_CASCADE,
                ScrmDataDictionaryManageService.DICT_TYPE_MULTI_LEVEL)) {
            byType.put(t, 0L);
        }
        Map<String, Long> byCategory = new LinkedHashMap<>();
        for (String c : Arrays.asList("SYSTEM", "BUSINESS", "CUSTOM", "INDUSTRY", "REGION")) {
            byCategory.put(c, 0L);
        }
        long systemCount = 0;
        long customCount = 0;
        long enabledCount = 0;
        for (ScrmDataDictionaryEntity d : dicts) {
            if (d.getDictType() != null) byType.merge(d.getDictType(), 1L, Long::sum);
            if (d.getCategory() != null) byCategory.merge(d.getCategory(), 1L, Long::sum);
            if (Boolean.TRUE.equals(d.getIsSystem())) systemCount++;
            else customCount++;
            if (Boolean.TRUE.equals(d.getEnabled())) enabledCount++;
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", (long) dicts.size());
        stats.put("byType", byType);
        stats.put("byCategory", byCategory);
        stats.put("systemCount", systemCount);
        stats.put("customCount", customCount);
        stats.put("enabledCount", enabledCount);
        return stats;
    }

    /**
     * 字典项统计 (总数 / 各字典项数 / 启用项数 / 禁用项数)。
     *
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getItemStats() {
        List<ScrmDataDictionaryItemEntity> items = itemRepository.findAll(
                (root, query, cb) -> cb.and());
        long total = items.size();
        long enabled = items.stream().filter(i -> Boolean.TRUE.equals(i.getEnabled())).count();
        long disabled = items.stream().filter(i -> Boolean.FALSE.equals(i.getEnabled())).count();
        long defaultCount = items.stream().filter(i -> Boolean.TRUE.equals(i.getIsDefault())).count();
        long totalUsage = items.stream()
                .mapToLong(i -> i.getUsageCount() != null ? i.getUsageCount() : 0).sum();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("enabled", enabled);
        stats.put("disabled", disabled);
        stats.put("defaultCount", defaultCount);
        stats.put("totalUsageCount", totalUsage);
        return stats;
    }

    /**
     * 模块统计 (按字典所属 module 分组的字典数与字典项数)。
     *
     * @return 模块 → 统计信息
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getModuleStats() {
        List<ScrmDataDictionaryEntity> dicts = dictionaryRepository.findAll(
                (root, query, cb) -> cb.and());
        Map<String, Map<String, Long>> byModule = new LinkedHashMap<>();
        for (ScrmDataDictionaryEntity d : dicts) {
            String key = d.getModule() != null && !d.getModule().isBlank() ? d.getModule() : "UNGROUPED";
            Map<String, Long> entry = byModule.computeIfAbsent(key, k -> {
                Map<String, Long> m = new LinkedHashMap<>();
                m.put("dictCount", 0L);
                m.put("itemCount", 0L);
                return m;
            });
            entry.merge("dictCount", 1L, Long::sum);
            entry.merge("itemCount", (long) (d.getItemCount() != null ? d.getItemCount() : 0), Long::sum);
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("byModule", byModule);
        return stats;
    }

    /**
     * 字典健康度 (空字典 / 无引用字典 / 禁用项数)。
     *
     * @return 健康度信息
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getDictionaryHealth() {
        List<ScrmDataDictionaryEntity> dicts = dictionaryRepository.findAll(
                (root, query, cb) -> cb.and());
        List<Map<String, Object>> emptyDicts = new ArrayList<>();
        List<Map<String, Object>> unusedDicts = new ArrayList<>();
        long totalDisabledItems = 0;
        long totalEnabledDicts = 0;
        for (ScrmDataDictionaryEntity d : dicts) {
            int itemCount = d.getItemCount() != null ? d.getItemCount() : 0;
            if (itemCount == 0) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("dictId", d.getId());
                entry.put("dictCode", d.getDictCode());
                entry.put("dictName", d.getDictName());
                emptyDicts.add(entry);
            }
            int usage = d.getUsageCount() != null ? d.getUsageCount() : 0;
            if (usage == 0) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("dictId", d.getId());
                entry.put("dictCode", d.getDictCode());
                entry.put("dictName", d.getDictName());
                entry.put("lastUsedAt", d.getLastUsedAt());
                unusedDicts.add(entry);
            }
            if (Boolean.TRUE.equals(d.getEnabled())) totalEnabledDicts++;
            List<ScrmDataDictionaryItemEntity> items = itemRepository
                    .findByDictIdOrderBySortOrderAsc(d.getId());
            totalDisabledItems += items.stream().filter(i -> Boolean.FALSE.equals(i.getEnabled())).count();
        }
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("totalDicts", (long) dicts.size());
        health.put("enabledDicts", totalEnabledDicts);
        health.put("emptyDictCount", (long) emptyDicts.size());
        health.put("unusedDictCount", (long) unusedDicts.size());
        health.put("totalDisabledItems", totalDisabledItems);
        health.put("emptyDicts", emptyDicts);
        health.put("unusedDicts", unusedDicts);
        return health;
    }
}