/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSystemConfigStatsService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmConfigGroupEntity;
import org.hiylo.scrm.entity.ScrmSystemConfigEntity;
import org.hiylo.scrm.repository.ScrmConfigGroupRepository;
import org.hiylo.scrm.repository.ScrmConfigHistoryRepository;
import org.hiylo.scrm.repository.ScrmSystemConfigRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRM 系统配置统计服务。
 * <p>
 * 承载配置统计子域: 配置统计 (总数/类型/分组/环境)、变更统计、分组统计、覆盖率、
 * 热门配置与长期未更新配置、配置概览。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmSystemConfigStatsService {

    /** 系统配置数据访问层 */
    private final ScrmSystemConfigRepository configRepository;
    /** 配置分组数据访问层 */
    private final ScrmConfigGroupRepository groupRepository;
    /** 配置变更历史数据访问层 */
    private final ScrmConfigHistoryRepository historyRepository;
    /** 配置组管理服务 (分组配置数) */
    private final ScrmSystemConfigGroupService groupService;

    // ============================================================
    // Stats 配置与变更统计
    // ============================================================

    /**
     * 配置统计 (总数 / 各类型数 / 各分组数 / 各环境数)。
     *
     * @return 配置统计 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getConfigStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        long total = configRepository.count();
        stats.put("total", total);
        stats.put("enabled", configRepository.countByEnabled(Boolean.TRUE));
        stats.put("disabled", configRepository.countByEnabled(Boolean.FALSE));
        // 按类型聚合
        List<Object[]> byType = configRepository.countByConfigType();
        Map<String, Long> typeCount = new LinkedHashMap<>();
        for (String t : ScrmSystemConfigItemService.VALID_CONFIG_TYPES) {
            typeCount.put(t, 0L);
        }
        for (Object[] row : byType) {
            String type = (String) row[0];
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            typeCount.put(type, count);
        }
        stats.put("typeCount", typeCount);
        // 按分组聚合
        List<Object[]> byGroup = configRepository.countByConfigGroup();
        Map<String, Long> groupCount = new LinkedHashMap<>();
        for (Object[] row : byGroup) {
            String group = (String) row[0];
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            groupCount.put(group, count);
        }
        stats.put("groupCount", groupCount);
        // 按环境聚合
        List<Object[]> byEnv = configRepository.countByEnvironment();
        Map<String, Long> envCount = new LinkedHashMap<>();
        for (String e : ScrmSystemConfigItemService.VALID_ENVIRONMENTS) {
            envCount.put(e, 0L);
        }
        for (Object[] row : byEnv) {
            String env = (String) row[0];
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            envCount.put(env, count);
        }
        stats.put("environmentCount", envCount);
        stats.put("generatedAt", LocalDateTime.now());
        return stats;
    }

    /**
     * 变更统计 (变更次数 / 各类型数 / 各用户数)。
     *
     * @param startTime 起始时间（可空）
     * @param endTime   结束时间（可空）
     * @return 变更统计 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getChangeStats(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> stats = new LinkedHashMap<>();
        long total = historyRepository.countByTimeRange(startTime, endTime);
        stats.put("total", total);
        // 按类型聚合
        List<Object[]> byType = historyRepository.countByChangeType(startTime, endTime);
        Map<String, Long> typeCount = new LinkedHashMap<>();
        for (String t : ScrmSystemConfigItemService.VALID_CHANGE_TYPES) {
            typeCount.put(t, 0L);
        }
        for (Object[] row : byType) {
            String type = (String) row[0];
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            typeCount.put(type, count);
        }
        stats.put("typeCount", typeCount);
        // 按用户聚合
        List<Object[]> byUser = historyRepository.countByChangedBy(startTime, endTime);
        List<Map<String, Object>> topUsers = new ArrayList<>();
        for (Object[] row : byUser) {
            Map<String, Object> u = new LinkedHashMap<>();
            u.put("changedBy", row[0]);
            u.put("count", row[1] == null ? 0L : ((Number) row[1]).longValue());
            topUsers.add(u);
        }
        stats.put("userCount", topUsers);
        stats.put("startTime", startTime);
        stats.put("endTime", endTime);
        stats.put("generatedAt", LocalDateTime.now());
        return stats;
    }

    /**
     * 分组统计 (各分组的配置数)。
     *
     * @return 分组统计 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getGroupStats() {
        List<ScrmConfigGroupEntity> groups = groupRepository.findAll();
        List<Map<String, Object>> groupStats = new ArrayList<>();
        for (ScrmConfigGroupEntity g : groups) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("groupCode", g.getGroupCode());
            s.put("groupName", g.getGroupName());
            s.put("configCount", groupService.getGroupConfigCount(g.getGroupCode()));
            s.put("enabled", g.getEnabled());
            s.put("isVisible", g.getIsVisible());
            groupStats.add(s);
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalGroups", groups.size());
        stats.put("groups", groupStats);
        stats.put("generatedAt", LocalDateTime.now());
        return stats;
    }

    /**
     * 配置覆盖率 (有值配置数 / 总配置数)。
     *
     * @return 覆盖率 Map {total, hasValue, coverage}
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getConfigCoverage() {
        List<ScrmSystemConfigEntity> configs = configRepository.findByEnabled(Boolean.TRUE);
        long total = configs.size();
        long hasValue = configs.stream()
                .filter(c -> c.getConfigValue() != null && !c.getConfigValue().isEmpty())
                .count();
        double coverage = total > 0 ? hasValue * 100.0 / total : 0.0;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("hasValue", hasValue);
        result.put("emptyValue", total - hasValue);
        result.put("coverage", Math.round(coverage * 100.0) / 100.0);
        result.put("generatedAt", LocalDateTime.now());
        return result;
    }

    /**
     * 热门配置 (按变更次数倒序, Top N)。
     *
     * @param limit 返回数量
     * @return 热门配置列表
     */
    @Transactional(readOnly = true)
    public List<ScrmSystemConfigEntity> getPopularConfigs(int limit) {
        if (limit <= 0) {
            limit = ScrmSystemConfigItemService.DEFAULT_POPULAR_LIMIT;
        }
        return configRepository.findAllByOrderByChangeCountDesc(
                 Pageable.ofSize(limit)).getContent();
    }

    /**
     * 长期未更新配置 (最近变更时间早于 N 天前或从未变更)。
     *
     * @param days 天数
     * @return 过期配置列表
     */
    @Transactional(readOnly = true)
    public List<ScrmSystemConfigEntity> getStaleConfigs(int days) {
        if (days <= 0) {
            days = ScrmSystemConfigItemService.DEFAULT_STALE_DAYS;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        return configRepository.findByLastChangedAtBeforeOrLastChangedAtIsNull(
                 cutoff);
    }

    /**
     * 配置概览 (总数 / 启用 / 敏感 / 系统级 / 分组数 / 变更总数)。
     *
     * @return 概览 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getConfigOverview() {
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("totalConfigs", configRepository.count());
        overview.put("enabledConfigs", configRepository.countByEnabled(Boolean.TRUE));
        overview.put("disabledConfigs", configRepository.countByEnabled(Boolean.FALSE));
        overview.put("totalGroups", groupRepository.findAll().size());
        overview.put("totalChanges", historyRepository.countByTimeRange(null, null));
        overview.put("generatedAt", LocalDateTime.now());
        return overview;
    }
}