/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAssetLibraryStatsService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.entity.ScrmAssetCategoryEntity;
import org.hiylo.scrm.entity.ScrmAssetEntity;
import org.hiylo.scrm.entity.ScrmAssetUsageEntity;
import org.hiylo.scrm.repository.ScrmAssetCategoryRepository;
import org.hiylo.scrm.repository.ScrmAssetRepository;
import org.hiylo.scrm.repository.ScrmAssetUsageRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRM 营销素材库 - 统计概览子域服务。
 * <p>
 * 承载素材总体 / 分类 / 存储 / 审核 / 使用概览与上传趋势等只读聚合统计能力。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
public class ScrmAssetLibraryStatsService {

    /** 素材数据访问层 */
    private final ScrmAssetRepository assetRepository;

    /** 素材分类数据访问层 */
    private final ScrmAssetCategoryRepository categoryRepository;

    /** 素材使用记录数据访问层 */
    private final ScrmAssetUsageRepository usageRepository;

    /**
     * 素材统计 (总数 / 各类型数 / 各分类数 / 总大小)。
     *
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getAssetStats() {
        List<ScrmAssetEntity> assets = assetRepository.findAll(
                (root, query, cb) -> cb.and());
        Map<String, Long> byType = new LinkedHashMap<>();
        for (String t : ScrmAssetLibraryAssetService.VALID_ASSET_TYPES) {
            byType.put(t, 0L);
        }
        Map<String, Long> byStatus = new LinkedHashMap<>();
        long totalSize = 0;
        for (ScrmAssetEntity a : assets) {
            if (a.getAssetType() != null) byType.merge(a.getAssetType(), 1L, Long::sum);
            if (a.getStatus() != null) byStatus.merge(a.getStatus(), 1L, Long::sum);
            totalSize += a.getFileSizeBytes() != null ? a.getFileSizeBytes() : 0;
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", (long) assets.size());
        stats.put("byType", byType);
        stats.put("byStatus", byStatus);
        stats.put("totalSizeBytes", totalSize);
        return stats;
    }

    /**
     * 分类统计 (各分类素材数与总大小)。
     *
     * @return 分类 → 统计信息
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCategoryStats() {
        List<ScrmAssetCategoryEntity> categories = categoryRepository.findAll();
        Map<String, Object> byCategory = new LinkedHashMap<>();
        for (ScrmAssetCategoryEntity c : categories) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("categoryId", c.getId());
            entry.put("categoryCode", c.getCategoryCode());
            entry.put("categoryName", c.getCategoryName());
            entry.put("assetCount", c.getAssetCount() != null ? c.getAssetCount() : 0);
            entry.put("totalSizeBytes", c.getTotalSizeBytes() != null ? c.getTotalSizeBytes() : 0L);
            byCategory.put(c.getCategoryCode(), entry);
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalCategories", (long) categories.size());
        stats.put("byCategory", byCategory);
        return stats;
    }

    /**
     * 存储统计 (各存储类型素材数与大小分布)。
     *
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getStorageStats() {
        List<ScrmAssetEntity> assets = assetRepository.findAll(
                (root, query, cb) -> cb.and());
        Map<String, Long> countByStorage = new LinkedHashMap<>();
        Map<String, Long> sizeByStorage = new LinkedHashMap<>();
        for (String s : ScrmAssetLibraryAssetService.VALID_STORAGE_TYPES) {
            countByStorage.put(s, 0L);
            sizeByStorage.put(s, 0L);
        }
        for (ScrmAssetEntity a : assets) {
            String storage = a.getStorageType() != null ? a.getStorageType() : "UNKNOWN";
            countByStorage.merge(storage, 1L, Long::sum);
            sizeByStorage.merge(storage, a.getFileSizeBytes() != null ? a.getFileSizeBytes() : 0L, Long::sum);
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("countByStorageType", countByStorage);
        stats.put("sizeByStorageType", sizeByStorage);
        stats.put("totalSizeBytes", assetRepository.sumFileSizeBytes());
        return stats;
    }

    /**
     * 使用统计概览 (按时间范围过滤)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果 (总使用次数 / 各类型次数 / 各模块次数)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getUsageStatsOverview(LocalDateTime startTime, LocalDateTime endTime) {
        Specification<ScrmAssetUsageEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("usedAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("usedAt"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmAssetUsageEntity> usages = usageRepository.findAll(spec);
        long total = 0;
        Map<String, Integer> byType = new LinkedHashMap<>();
        Map<String, Integer> byModule = new LinkedHashMap<>();
        for (ScrmAssetUsageEntity u : usages) {
            int count = u.getUsageCount() != null ? u.getUsageCount() : 0;
            total += count;
            byType.merge(u.getUsageType(), count, Integer::sum);
            String module = u.getUsageModule() != null ? u.getUsageModule() : "UNKNOWN";
            byModule.merge(module, count, Integer::sum);
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("byType", byType);
        stats.put("byModule", byModule);
        stats.put("recordCount", (long) usages.size());
        return stats;
    }

    /**
     * 审核统计 (各审核状态素材数)。
     *
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getReviewStats() {
        List<ScrmAssetEntity> assets = assetRepository.findAll(
                (root, query, cb) -> cb.and());
        Map<String, Long> byReviewStatus = new LinkedHashMap<>();
        for (ScrmAssetEntity a : assets) {
            if (a.getReviewStatus() != null) {
                byReviewStatus.merge(a.getReviewStatus(), 1L, Long::sum);
            }
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", (long) assets.size());
        stats.put("byReviewStatus", byReviewStatus);
        return stats;
    }

    /**
     * 趋势统计 (按天聚合最近 days 天的上传量)。
     *
     * @param days 天数
     * @return 日期 → 上传量
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getTrend(int days) {
        int d = Math.max(1, days);
        LocalDateTime start = LocalDateTime.now().minusDays(d);
        List<ScrmAssetEntity> assets = assetRepository
                .findByUploadedAtBetween(start, LocalDateTime.now());
        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        Map<String, Long> trend = new LinkedHashMap<>();
        for (int i = d; i >= 0; i--) {
            trend.put(LocalDate.now().minusDays(i).format(dayFmt), 0L);
        }
        for (ScrmAssetEntity a : assets) {
            if (a.getUploadedAt() == null) continue;
            String day = a.getUploadedAt().toLocalDate().format(dayFmt);
            trend.merge(day, 1L, Long::sum);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("trend", trend);
        result.put("days", d);
        return result;
    }
}