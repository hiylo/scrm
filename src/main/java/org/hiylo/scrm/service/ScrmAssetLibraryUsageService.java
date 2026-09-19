/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAssetLibraryUsageService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.dto.ScrmAssetUsageDto;
import org.hiylo.scrm.entity.ScrmAssetEntity;
import org.hiylo.scrm.entity.ScrmAssetUsageEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmAssetRepository;
import org.hiylo.scrm.repository.ScrmAssetUsageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SCRM 营销素材库 - 素材使用记录子域服务。
 * <p>
 * 承载素材使用记录的登记 / 查询 / 统计 / 趋势 / 热门聚合与过期清理能力。
 * 同时托管素材级统计字段同步工具与合法使用类型常量。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmAssetLibraryUsageService {

    /** 合法使用类型集合 */
    private static final Set<String> VALID_USAGE_TYPES = new HashSet<>(Arrays.asList(
            "VIEW", "DOWNLOAD", "USE", "SHARE", "FAVORITE", "LIKE", "EMBED", "EXPORT"));

    /** 素材使用记录数据访问层 */
    private final ScrmAssetUsageRepository usageRepository;

    /** 素材数据访问层 (热门聚合补全素材信息与统计回写) */
    private final ScrmAssetRepository assetRepository;

    /** 素材子域服务 (素材查询校验) */
    private final ScrmAssetLibraryAssetService assetService;

    /**
     * 记录素材使用。
     * <p>校验素材存在且属于当前账号; 使用类型必须合法; usedAt 缺省填充当前时间;
     * 同时更新素材对应统计字段 (viewCount / downloadCount / useCount 等)。</p>
     *
     * @param usageDto 使用记录参数
     * @return 使用记录
     * @throws ScrmException 参数非法 / 素材不存在
     */
    @Transactional
    public ScrmAssetUsageEntity recordUsage(ScrmAssetUsageDto usageDto) throws ScrmException {
        if (usageDto == null) {
            throw ScrmException.badRequest("使用记录参数不能为空");
        }
        if (usageDto.getAssetId() == null) {
            throw ScrmException.badRequest("素材 ID 不能为空");
        }
        if (usageDto.getUsageType() == null || !VALID_USAGE_TYPES.contains(usageDto.getUsageType())) {
            throw ScrmException.badRequest("使用类型非法: " + usageDto.getUsageType());
        }
        ScrmAssetEntity asset = assetService.findAssetOrThrow(usageDto.getAssetId());
        LocalDateTime now = usageDto.getUsedAt() != null ? usageDto.getUsedAt() : LocalDateTime.now();
        ScrmAssetUsageEntity entity = new ScrmAssetUsageEntity();
        entity.setAssetId(asset.getId());
        entity.setUsageType(usageDto.getUsageType());
        entity.setUsageModule(usageDto.getUsageModule());
        entity.setUsageEntity(usageDto.getUsageEntity());
        entity.setUsageEntityName(usageDto.getUsageEntityName());
        entity.setUsageScenario(usageDto.getUsageScenario());
        entity.setUserId(usageDto.getUserId());
        entity.setUserName(usageDto.getUserName() != null ? usageDto.getUserName() : UserContext.getUsername());
        entity.setUserRole(usageDto.getUserRole());
        entity.setUsageCount(usageDto.getUsageCount() != null && usageDto.getUsageCount() > 0
                ? usageDto.getUsageCount() : 1);
        entity.setMetadata(usageDto.getMetadata());
        entity.setUsedAt(now);
        entity = usageRepository.save(entity);
        // 同步素材级统计
        bumpAssetStat(asset, usageDto.getUsageType(), entity.getUsageCount());
        log.info("记录素材使用: assetId={}, usageType={}, userId={}",
                asset.getId(), usageDto.getUsageType(), usageDto.getUserId());
        return entity;
    }

    /**
     * 查询使用记录详情。
     *
     * @param id 使用记录 ID
     * @return 使用记录
     * @throws ScrmException 使用记录不存在
     */
    @Transactional(readOnly = true)
    public ScrmAssetUsageEntity getUsage(Long id) throws ScrmException {
        ScrmAssetUsageEntity entity = usageRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "素材使用记录不存在: id=" + id));

        return entity;
    }

    /**
     * 分页查询使用记录, 支持按素材 / 使用类型 / 使用模块 / 时间范围过滤。
     *
     * @param assetId     素材 ID (可空)
     * @param usageType   使用类型 (可空)
     * @param usageModule 使用模块 (可空)
     * @param startTime   起始时间 (可空)
     * @param endTime     截止时间 (可空)
     * @param pageable    分页参数
     * @return 使用记录分页结果 (按 usedAt 倒序)
     */
    @Transactional(readOnly = true)
    public Page<ScrmAssetUsageEntity> listUsage(Long assetId, String usageType, String usageModule,
                                                 LocalDateTime startTime, LocalDateTime endTime,
                                                 Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "usedAt"));
        Specification<ScrmAssetUsageEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (assetId != null) {
                predicates.add(cb.equal(root.get("assetId"), assetId));
            }
            if (usageType != null && !usageType.isBlank()) {
                predicates.add(cb.equal(root.get("usageType"), usageType));
            }
            if (usageModule != null && !usageModule.isBlank()) {
                predicates.add(cb.equal(root.get("usageModule"), usageModule));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("usedAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("usedAt"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return usageRepository.findAll(spec, sorted);
    }

    /**
     * 素材使用统计 (按时间范围过滤)。
     *
     * @param assetId   素材 ID
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果 (总使用次数 / 各使用类型次数 / 各模块次数)
     * @throws ScrmException 素材不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getUsageStats(Long assetId, LocalDateTime startTime, LocalDateTime endTime)
            throws ScrmException {
        assetService.findAssetOrThrow(assetId);
        List<ScrmAssetUsageEntity> usages = usageRepository.findByAssetId(assetId);
        long total = 0;
        Map<String, Integer> byType = new LinkedHashMap<>();
        Map<String, Integer> byModule = new LinkedHashMap<>();
        for (ScrmAssetUsageEntity u : usages) {
            if (u.getUsedAt() != null) {
                if (startTime != null && u.getUsedAt().isBefore(startTime)) continue;
                if (endTime != null && u.getUsedAt().isAfter(endTime)) continue;
            }
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
        return stats;
    }

    /**
     * 按模块统计素材使用。
     *
     * @param assetId 素材 ID
     * @return 模块 → 使用次数
     * @throws ScrmException 素材不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getUsageByModule(Long assetId) throws ScrmException {
        return getUsageStats(assetId, null, null);
    }

    /**
     * 素材使用趋势 (按天聚合最近 days 天的使用次数)。
     *
     * @param assetId 素材 ID
     * @param days    天数
     * @return 日期 → 使用次数
     * @throws ScrmException 素材不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getUsageTrend(Long assetId, int days) throws ScrmException {
        assetService.findAssetOrThrow(assetId);
        int d = Math.max(1, days);
        LocalDateTime start = LocalDateTime.now().minusDays(d);
        List<ScrmAssetUsageEntity> usages = usageRepository.findByAssetId(assetId);
        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        Map<String, Long> trend = new LinkedHashMap<>();
        for (int i = d; i >= 0; i--) {
            trend.put(LocalDate.now().minusDays(i).format(dayFmt), 0L);
        }
        for (ScrmAssetUsageEntity u : usages) {
            if (u.getUsedAt() == null || u.getUsedAt().isBefore(start)) continue;
            String day = u.getUsedAt().toLocalDate().format(dayFmt);
            int count = u.getUsageCount() != null ? u.getUsageCount() : 0;
            trend.merge(day, (long) count, Long::sum);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("trend", trend);
        result.put("days", d);
        return result;
    }

    /**
     * 热门素材 (按使用次数聚合, 取前 limit 条)。
     *
     * @param limit 取前 N 条
     * @return 热门素材列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getPopularAssetsByUsage(int limit) {
        int n = limit > 0 ? limit : 10;
        List<Object[]> rows = usageRepository.findPopularAssetIdsByUsage(n);
        List<Map<String, Object>> popular = new ArrayList<>();
        for (Object[] row : rows) {
            Long assetId = (Long) row[0];
            Long totalUsage = row[1] instanceof Number ? ((Number) row[1]).longValue() : 0L;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("assetId", assetId);
            entry.put("usageCount", totalUsage);
            assetRepository.findById(assetId).ifPresent(a -> {
                entry.put("assetName", a.getAssetName());
                entry.put("assetCode", a.getAssetCode());
                entry.put("assetType", a.getAssetType());
                entry.put("thumbnailUrl", a.getThumbnailUrl());
            });
            popular.add(entry);
        }
        return popular;
    }

    /**
     * 清理过期使用记录 (usedAt 早于 days 天前)。
     *
     * @param days 天数阈值
     * @return 清理的记录数
     */
    @Transactional
    public Map<String, Object> cleanupUsage(int days) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(Math.max(0, days));
        int deleted = usageRepository.deleteExpired(threshold);
        log.info("清理过期素材使用记录: days={}, deleted={}", days, deleted);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deleted", deleted);
        result.put("threshold", threshold);
        return result;
    }

    /**
     * 根据使用类型同步素材级统计字段。
     */
    private void bumpAssetStat(ScrmAssetEntity asset, String usageType, int delta) {
        switch (usageType) {
            case "VIEW":
                asset.setViewCount((asset.getViewCount() != null ? asset.getViewCount() : 0) + delta);
                break;
            case "DOWNLOAD":
                asset.setDownloadCount((asset.getDownloadCount() != null ? asset.getDownloadCount() : 0) + delta);
                asset.setLastUsedAt(LocalDateTime.now());
                break;
            case "USE":
                asset.setUseCount((asset.getUseCount() != null ? asset.getUseCount() : 0) + delta);
                asset.setLastUsedAt(LocalDateTime.now());
                break;
            case "LIKE":
                asset.setLikeCount((asset.getLikeCount() != null ? asset.getLikeCount() : 0) + delta);
                break;
            case "SHARE":
                asset.setShareCount((asset.getShareCount() != null ? asset.getShareCount() : 0) + delta);
                break;
            case "FAVORITE":
                asset.setFavoriteCount((asset.getFavoriteCount() != null ? asset.getFavoriteCount() : 0) + delta);
                break;
            default:
                return;
        }
        assetRepository.save(asset);
    }
}