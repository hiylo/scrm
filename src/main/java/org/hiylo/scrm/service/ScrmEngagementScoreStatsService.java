/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmEngagementScoreStatsService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmEngagementScoreDto;
import org.hiylo.scrm.entity.ScrmEngagementEventEntity;
import org.hiylo.scrm.entity.ScrmEngagementRuleEntity;
import org.hiylo.scrm.entity.ScrmEngagementScoreEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmEngagementEventRepository;
import org.hiylo.scrm.repository.ScrmEngagementRuleRepository;
import org.hiylo.scrm.repository.ScrmEngagementScoreRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SCRM 客户互动评分统计与趋势服务 (统计趋势子域)。
 * <p>
 * 承载评分趋势 / 分布 / 排行, 以及互动统计 (总览 / 行为 / 渠道 / 趋势)。衰减计算复用
 * 事件评分享兄弟类 {@link ScrmEngagementScoreEventService#applyDecay}, 共享常量与
 * 通用统计工具取自 {@link ScrmEngagementScoreRuleService}。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmEngagementScoreStatsService {

    /** 互动事件数据访问层 */
    private final ScrmEngagementEventRepository eventRepository;

    /** 互动评分数据访问层 */
    private final ScrmEngagementScoreRepository scoreRepository;

    /** 互动规则数据访问层 (趋势衰减参数用) */
    private final ScrmEngagementRuleRepository ruleRepository;

    /** 事件记录与评分兄弟服务 (衰减计算用) */
    private final ScrmEngagementScoreEventService eventScoreService;

    /**
     * 评分趋势: 返回最近 days 天每日的事件数 / 原始得分 / 衰减后得分。
     *
     * @param customerId 客户 ID
     * @param days       天数
     * @return 趋势列表 (按日期升序)
     * @throws ScrmException 参数非法
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getScoreTrend(Long customerId, int days) throws ScrmException {
        if (customerId == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        if (days <= 0) {
            days = ScrmEngagementScoreRuleService.PERIOD_WEEK_DAYS;
        }
        List<ScrmEngagementEventEntity> events = eventRepository
                .findByCustomerIdAndProcessedTrueOrderByEventTimeAsc(customerId);
        // 加载规则用于衰减参数
        Map<Long, ScrmEngagementRuleEntity> ruleMap = new HashMap<>();
        for (ScrmEngagementEventEntity e : events) {
            if (e.getRuleId() != null && !ruleMap.containsKey(e.getRuleId())) {
                ruleRepository.findById(e.getRuleId()).ifPresent(r -> {
                    ruleMap.put(r.getId(), r);
                });
            }
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.toLocalDate().minusDays(days - 1L).atStartOfDay();
        // 按日期聚合
        Map<LocalDate, double[]> daily = new LinkedHashMap<>();
        for (ScrmEngagementEventEntity e : events) {
            if (e.getEventTime().isBefore(start)) {
                continue;
            }
            LocalDate day = e.getEventTime().toLocalDate();
            int decayDays = ScrmEngagementScoreRuleService.DEFAULT_DECAY_DAYS;
            String decayType = ScrmEngagementScoreRuleService.DEFAULT_DECAY_TYPE;
            if (e.getRuleId() != null && ruleMap.containsKey(e.getRuleId())) {
                ScrmEngagementRuleEntity rule = ruleMap.get(e.getRuleId());
                decayDays = rule.getDecayDays() != null ? rule.getDecayDays()
                        : ScrmEngagementScoreRuleService.DEFAULT_DECAY_DAYS;
                decayType = rule.getDecayType() != null ? rule.getDecayType()
                        : ScrmEngagementScoreRuleService.DEFAULT_DECAY_TYPE;
            }
            int pts = 0;
            if (e.getPoints() != null) {
                pts = e.getPoints();
            }
            double decayed = eventScoreService.applyDecay(pts, e.getEventTime(), decayDays, decayType);
            double[] bucket = daily.computeIfAbsent(day, k -> new double[3]);
            bucket[0] += 1;                              // eventCount
            if (e.getPoints() != null) {
                bucket[1] += e.getPoints();
            }  // rawPoints
            bucket[2] += decayed;                        // decayedPoints
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate day = now.toLocalDate().minusDays(i);
            double[] bucket = daily.getOrDefault(day, new double[3]);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", day.toString());
            row.put("eventCount", (int) bucket[0]);
            row.put("rawPoints", ScrmEngagementScoreRuleService.round2(bucket[1]));
            row.put("decayedPoints", ScrmEngagementScoreRuleService.round2(bucket[2]));
            result.add(row);
        }
        return result;
    }

    /**
     * 评分分布: 按活跃等级分组统计客户数。
     *
     * @return 等级分布 (含全部 5 个等级, 无客户的等级为 0)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getScoreDistribution() {
        List<Object[]> rows = scoreRepository.countByLevel();
        Map<String, Long> raw = new HashMap<>();
        for (Object[] row : rows) {
            String level = row[0] != null ? row[0].toString() : ScrmEngagementScoreRuleService.LEVEL_INACTIVE;
            long count = ScrmEngagementScoreRuleService.toLong(row, 1);
            raw.put(level, count);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(ScrmEngagementScoreRuleService.LEVEL_INACTIVE,
                raw.getOrDefault(ScrmEngagementScoreRuleService.LEVEL_INACTIVE, 0L));
        result.put(ScrmEngagementScoreRuleService.LEVEL_LOW,
                raw.getOrDefault(ScrmEngagementScoreRuleService.LEVEL_LOW, 0L));
        result.put(ScrmEngagementScoreRuleService.LEVEL_MEDIUM,
                raw.getOrDefault(ScrmEngagementScoreRuleService.LEVEL_MEDIUM, 0L));
        result.put(ScrmEngagementScoreRuleService.LEVEL_HIGH,
                raw.getOrDefault(ScrmEngagementScoreRuleService.LEVEL_HIGH, 0L));
        result.put(ScrmEngagementScoreRuleService.LEVEL_VERY_HIGH,
                raw.getOrDefault(ScrmEngagementScoreRuleService.LEVEL_VERY_HIGH, 0L));
        result.put("total", raw.values().stream().mapToLong(Long::longValue).sum());
        return result;
    }

    /**
     * 高分客户排行 (按当前分倒序)。
     *
     * @param limit 返回数量
     * @return 评分列表
     */
    @Transactional(readOnly = true)
    public List<ScrmEngagementScoreDto> getTopCustomers(int limit) {
        if (limit <= 0) {
            limit = 10;
        }
        return scoreRepository
                .findAllByOrderByCurrentScoreDesc(PageRequest.of(0, limit))
                .getContent().stream()
                .map(this::toScoreDto)
                .collect(Collectors.toList());
    }

    /**
     * 互动统计: 总事件 / 总客户 / 平均分 / 各等级分布。
     *
     * @param startTime 起始时间（可空）
     * @param endTime   截止时间（可空）
     * @return 互动统计
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getEngagementStats(LocalDateTime startTime, LocalDateTime endTime) {
        long totalEvents = eventRepository.countInRange(startTime, endTime);
        Object[] overview = scoreRepository.getOverviewStats();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalEvents", totalEvents);
        result.put("totalCustomers", ScrmEngagementScoreRuleService.toLong(overview, 0));
        result.put("avgCurrentScore", ScrmEngagementScoreRuleService.round2(
                ScrmEngagementScoreRuleService.toDouble(overview, 1)));
        result.put("totalAccumulatedEvents", ScrmEngagementScoreRuleService.toLong(overview, 2));
        result.put("levelDistribution", getScoreDistribution());
        result.put("startTime", startTime);
        result.put("endTime", endTime);
        return result;
    }

    /**
     * 行为类型统计: 按行为类型分组统计事件数。
     *
     * @param startTime 起始时间（可空）
     * @param endTime   截止时间（可空）
     * @return 行为统计列表 [{behaviorType, eventCount}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getBehaviorStats(LocalDateTime startTime, LocalDateTime endTime) {
        List<Object[]> rows = eventRepository.countByBehaviorType(startTime, endTime);
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("behaviorType", row[0]);
            item.put("eventCount", ScrmEngagementScoreRuleService.toLong(row, 1));
            result.add(item);
        }
        return result;
    }

    /**
     * 渠道统计: 按渠道分组统计事件数。
     *
     * @param startTime 起始时间（可空）
     * @param endTime   截止时间（可空）
     * @return 渠道统计列表 [{channel, eventCount}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getChannelStats(LocalDateTime startTime, LocalDateTime endTime) {
        List<Object[]> rows = eventRepository.countByChannel(startTime, endTime);
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("channel", row[0] != null ? row[0] : "UNKNOWN");
            item.put("eventCount", ScrmEngagementScoreRuleService.toLong(row, 1));
            result.add(item);
        }
        return result;
    }

    /**
     * 互动趋势: 最近 days 天每日事件数。
     *
     * @param days 天数
     * @return 趋势列表 [{date, eventCount}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTrend(int days) {
        if (days <= 0) {
            days = ScrmEngagementScoreRuleService.PERIOD_WEEK_DAYS;
        }
        LocalDateTime start = LocalDateTime.now().toLocalDate().minusDays(days - 1L).atStartOfDay();
        List<Object[]> rows = eventRepository.countByDay(start);
        Map<String, Long> daily = new HashMap<>();
        for (Object[] row : rows) {
            String date = row[0] != null ? row[0].toString() : "";
            daily.put(date, ScrmEngagementScoreRuleService.toLong(row, 1));
        }
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", day.toString());
            row.put("eventCount", daily.getOrDefault(day.toString(), 0L));
            result.add(row);
        }
        return result;
    }

    /**
     * 评分实体转 DTO。
     */
    private ScrmEngagementScoreDto toScoreDto(ScrmEngagementScoreEntity entity) {
        ScrmEngagementScoreDto dto = new ScrmEngagementScoreDto();
        dto.setId(entity.getId());
        dto.setCustomerId(entity.getCustomerId());
        dto.setCustomerName(entity.getCustomerName());
        dto.setTotalScore(entity.getTotalScore());
        dto.setCurrentScore(entity.getCurrentScore());
        dto.setEngagementLevel(entity.getEngagementLevel());
        dto.setScoreTrend(entity.getScoreTrend());
        dto.setTrendChangePercent(entity.getTrendChangePercent());
        dto.setLastEventAt(entity.getLastEventAt());
        dto.setLastCalculatedAt(entity.getLastCalculatedAt());
        dto.setStreakDays(entity.getStreakDays());
        dto.setTotalEvents(entity.getTotalEvents());
        dto.setWeeklyScore(entity.getWeeklyScore());
        dto.setMonthlyScore(entity.getMonthlyScore());
        dto.setQuarterlyScore(entity.getQuarterlyScore());
        dto.setYearlyScore(entity.getYearlyScore());
        dto.setLevelUpdatedAt(entity.getLevelUpdatedAt());
        dto.setMetadata(entity.getMetadata());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}