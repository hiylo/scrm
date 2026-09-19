/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmEngagementScoreEventService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmEngagementEventDto;
import org.hiylo.scrm.dto.ScrmEngagementRecordDto;
import org.hiylo.scrm.dto.ScrmEngagementScoreDto;
import org.hiylo.scrm.entity.ScrmEngagementEventEntity;
import org.hiylo.scrm.entity.ScrmEngagementRuleEntity;
import org.hiylo.scrm.entity.ScrmEngagementScoreEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmEngagementEventRepository;
import org.hiylo.scrm.repository.ScrmEngagementRuleRepository;
import org.hiylo.scrm.repository.ScrmEngagementScoreRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SCRM 客户互动事件记录与评分计算服务 (事件记录与评分子域)。
 * <p>
 * 承载互动事件记录 (匹配规则→计算得分→应用上限→更新评分)、事件查询, 以及客户评分计算
 * (汇总事件→应用衰减→更新等级与趋势)、评分查询。衰减计算 (applyDecay) 与等级判定分别
 * 复用规则兄弟类常量与等级兄弟类 {@link ScrmEngagementScoreLevelService#determineLevel}。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmEngagementScoreEventService {

    /** 互动事件数据访问层 */
    private final ScrmEngagementEventRepository eventRepository;

    /** 互动评分数据访问层 */
    private final ScrmEngagementScoreRepository scoreRepository;

    /** 互动规则数据访问层 */
    private final ScrmEngagementRuleRepository ruleRepository;

    /** 规则管理兄弟服务 (匹配规则实体用) */
    private final ScrmEngagementScoreRuleService ruleService;

    /** 等级管理兄弟服务 (等级判定用) */
    private final ScrmEngagementScoreLevelService levelService;

    /**
     * 记录行为事件 (匹配规则→计算得分→应用上限→更新评分)。
     * <p>按 behaviorType + channel 匹配启用规则, 逐条计算得分 (points × weight), 应用每日/每周/
     * 每月上限后写入事件并同步更新评分 (totalScore/currentScore/totalEvents/lastEventAt/
     * streakDays/engagementLevel/scoreTrend)。eventTime 缺省为当前时间。</p>
     *
     * @param recordDto 事件记录参数
     * @return 创建后的事件
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmEngagementEventDto recordEvent(ScrmEngagementRecordDto recordDto) throws ScrmException {
        if (recordDto == null) {
            throw ScrmException.badRequest("事件记录参数不能为空");
        }
        if (recordDto.getCustomerId() == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        if (recordDto.getBehaviorType() == null || recordDto.getBehaviorType().isBlank()) {
            throw ScrmException.badRequest("行为类型不能为空");
        }
        LocalDateTime eventTime = recordDto.getEventTime() != null ? recordDto.getEventTime() : LocalDateTime.now();
        // 匹配规则
        List<ScrmEngagementRuleEntity> matchedRules = ruleService.matchRuleEntities(
                recordDto.getBehaviorType(), recordDto.getChannel());
        // 计算得分: 命中规则则累加 (points × weight), 应用周期上限; 未命中规则得分为 0
        int totalPoints = 0;
        Long matchedRuleId = null;
        ScrmEngagementRuleEntity appliedRule = null;
        for (ScrmEngagementRuleEntity rule : matchedRules) {
            int rawPoints = (int) Math.round(
                    rule.getPoints() * (rule.getWeight() != null ? rule.getWeight()
                            : ScrmEngagementScoreRuleService.DEFAULT_WEIGHT));
            if (rawPoints <= 0) {
                continue;
            }
            // 每日上限
            int adjusted = applyPeriodLimit(recordDto.getCustomerId(), rule, rawPoints, eventTime);
            if (adjusted <= 0) {
                continue;
            }
            totalPoints += adjusted;
            matchedRuleId = rule.getId();
            appliedRule = rule;
            ruleRepository.incrementMatchCount(rule.getId());
        }
        // 写入事件
        ScrmEngagementEventEntity event = new ScrmEngagementEventEntity();
        event.setCustomerId(recordDto.getCustomerId());
        event.setCustomerName(recordDto.getCustomerName());
        event.setBehaviorType(recordDto.getBehaviorType());
        event.setChannel(recordDto.getChannel());
        event.setEventTime(eventTime);
        event.setPoints(totalPoints);
        event.setRuleId(matchedRuleId);
        event.setSessionId(recordDto.getSessionId());
        event.setPageUrl(recordDto.getPageUrl());
        event.setReferrer(recordDto.getReferrer());
        event.setUserAgent(recordDto.getUserAgent());
        event.setDeviceType(recordDto.getDeviceType());
        event.setLocation(recordDto.getLocation());
        event.setMetadata(recordDto.getMetadata());
        event.setIp(recordDto.getIp());
        event.setProcessed(true);
        event = eventRepository.save(event);
        // 同步更新评分 (事件刚发生, 衰减因子≈1, 直接累加)
        if (totalPoints > 0) {
            updateScoreOnEvent(recordDto.getCustomerId(), recordDto.getCustomerName(),
                    totalPoints, eventTime, appliedRule);
        }
        log.info("记录互动事件: id={}, customerId={}, behaviorType={}, points={}, ruleId={}",
                event.getId(), event.getCustomerId(), event.getBehaviorType(), totalPoints, matchedRuleId);
        return toEventDto(event);
    }

    /**
     * 批量记录行为事件。
     *
     * @param records 事件记录列表
     * @return 创建后的事件列表
     * @throws ScrmException 参数非法
     */
    @Transactional
    public List<ScrmEngagementEventDto> batchRecordEvents(List<ScrmEngagementRecordDto> records)
            throws ScrmException {
        if (records == null || records.isEmpty()) {
            throw ScrmException.badRequest("事件记录列表不能为空");
        }
        List<ScrmEngagementEventDto> result = new ArrayList<>(records.size());
        for (ScrmEngagementRecordDto record : records) {
            result.add(recordEvent(record));
        }
        log.info("批量记录互动事件: count={}", records.size());
        return result;
    }

    /**
     * 查询事件详情。
     *
     * @param id 事件 ID
     * @return 事件 DTO
     * @throws ScrmException 事件不存在
     */
    @Transactional(readOnly = true)
    public ScrmEngagementEventDto getEvent(Long id) throws ScrmException {
        return toEventDto(findEventOrThrow(id));
    }

    /**
     * 分页查询事件, 支持按客户 / 行为类型 / 渠道 / 时间区间过滤。
     *
     * @param customerId   客户 ID 过滤（可空）
     * @param behaviorType 行为类型过滤（可空）
     * @param channel      渠道过滤（可空）
     * @param startTime    起始时间过滤（可空）
     * @param endTime      截止时间过滤（可空）
     * @param pageable     分页参数
     * @return 事件分页结果 (按 eventTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmEngagementEventDto> listEvents(Long customerId, String behaviorType, String channel,
                                                    LocalDateTime startTime, LocalDateTime endTime,
                                                    Pageable pageable) {
        Specification<ScrmEngagementEventEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (behaviorType != null && !behaviorType.isBlank()) {
                predicates.add(cb.equal(root.get("behaviorType"), behaviorType));
            }
            if (channel != null && !channel.isBlank()) {
                predicates.add(cb.equal(root.get("channel"), channel));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("eventTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("eventTime"), endTime));
            }
            query.orderBy(cb.desc(root.get("eventTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return eventRepository.findAll(spec, ScrmEngagementScoreRuleService.ensureSort(pageable, "eventTime"))
                .map(this::toEventDto);
    }

    /**
     * 按客户分页查询事件 (按 eventTime DESC)。
     *
     * @param customerId 客户 ID
     * @param pageable   分页参数
     * @return 事件分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmEngagementEventDto> getEventsByCustomer(Long customerId, Pageable pageable) {
        return eventRepository
                .findByCustomerIdOrderByEventTimeDesc(customerId, pageable)
                .map(this::toEventDto);
    }

    /**
     * 计算客户评分 (汇总事件→应用衰减→更新等级→更新趋势)。
     * <p>从客户全部已计分事件重算: totalScore 为原始得分之和, currentScore 为各事件应用关联规则
     * 衰减后的得分之和, weeklyScore/monthlyScore/quarterlyScore/yearlyScore 为对应时间窗口内
     * 衰减后得分合计。streakDays 从最近事件日向前计数连续互动天数。engagementLevel 由
     * {@link ScrmEngagementScoreLevelService#determineLevel} 映射, scoreTrend 与上次
     * currentScore 对比得出。</p>
     *
     * @param customerId 客户 ID
     * @return 更新后的评分
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmEngagementScoreDto calculateScore(Long customerId) throws ScrmException {
        if (customerId == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        List<ScrmEngagementEventEntity> events = eventRepository
                .findByCustomerIdAndProcessedTrueOrderByEventTimeAsc(customerId);
        // 加载账号全部规则构建 ruleId → rule 映射 (用于衰减参数)
        Map<Long, ScrmEngagementRuleEntity> ruleMap = ruleRepository.findByBehaviorTypeAndEnabledTrue(
                        events.isEmpty() ? "__none__" : events.get(0).getBehaviorType()).stream()
                .collect(Collectors.toMap(ScrmEngagementRuleEntity::getId, r -> r, (a, b) -> a));
        // 补全其他行为类型的规则 (上面只加载了第一个事件的行为类型, 这里补全全部)
        for (ScrmEngagementEventEntity e : events) {
            if (e.getRuleId() != null && !ruleMap.containsKey(e.getRuleId())) {
                ruleRepository.findById(e.getRuleId()).ifPresent(r -> {
                    ruleMap.put(r.getId(), r);
                });
            }
        }
        LocalDateTime now = LocalDateTime.now();
        double totalScore = 0;
        double currentScore = 0;
        double weeklyScore = 0;
        double monthlyScore = 0;
        double quarterlyScore = 0;
        double yearlyScore = 0;
        Map<String, Double> behaviorBreakdown = new HashMap<>();
        LocalDateTime lastEventAt = null;
        for (ScrmEngagementEventEntity event : events) {
            int rawPoints = event.getPoints() != null ? event.getPoints() : 0;
            totalScore += rawPoints;
            // 衰减后得分 (按关联规则的 decayDays/decayType, 无关联规则用默认 30 天 LINEAR)
            int decayDays = ScrmEngagementScoreRuleService.DEFAULT_DECAY_DAYS;
            String decayType = ScrmEngagementScoreRuleService.DEFAULT_DECAY_TYPE;
            if (event.getRuleId() != null && ruleMap.containsKey(event.getRuleId())) {
                ScrmEngagementRuleEntity rule = ruleMap.get(event.getRuleId());
                decayDays = rule.getDecayDays() != null ? rule.getDecayDays()
                        : ScrmEngagementScoreRuleService.DEFAULT_DECAY_DAYS;
                decayType = rule.getDecayType() != null ? rule.getDecayType()
                        : ScrmEngagementScoreRuleService.DEFAULT_DECAY_TYPE;
            }
            double decayed = applyDecay(rawPoints, event.getEventTime(), decayDays, decayType);
            currentScore += decayed;
            // 周期得分 (按 eventTime 是否在窗口内 + 衰减)
            long daysSince = java.time.Duration.between(event.getEventTime(), now).toDays();
            if (daysSince <= ScrmEngagementScoreRuleService.PERIOD_WEEK_DAYS) {
                weeklyScore += decayed;
            }
            if (daysSince <= ScrmEngagementScoreRuleService.PERIOD_MONTH_DAYS) {
                monthlyScore += decayed;
            }
            if (daysSince <= ScrmEngagementScoreRuleService.PERIOD_QUARTER_DAYS) {
                quarterlyScore += decayed;
            }
            if (daysSince <= ScrmEngagementScoreRuleService.PERIOD_YEAR_DAYS) {
                yearlyScore += decayed;
            }
            // 行为得分明细
            behaviorBreakdown.merge(event.getBehaviorType(), decayed, Double::sum);
            if (lastEventAt == null || event.getEventTime().isAfter(lastEventAt)) {
                lastEventAt = event.getEventTime();
            }
        }
        // 获取或创建评分记录
        ScrmEngagementScoreEntity score = getOrCreateScoreEntity(customerId);
        double oldCurrentScore = score.getCurrentScore() != null ? score.getCurrentScore() : 0;
        String oldLevel = score.getEngagementLevel();
        // 更新评分字段
        score.setTotalScore(totalScore);
        score.setCurrentScore(currentScore);
        score.setWeeklyScore(weeklyScore);
        score.setMonthlyScore(monthlyScore);
        score.setQuarterlyScore(quarterlyScore);
        score.setYearlyScore(yearlyScore);
        score.setTotalEvents(events.size());
        score.setLastEventAt(lastEventAt);
        score.setLastCalculatedAt(now);
        score.setMetadata(toBehaviorJson(behaviorBreakdown));
        // 连续互动天数
        score.setStreakDays(calculateStreakDays(events));
        // 等级
        String newLevel = levelService.determineLevel(currentScore);
        score.setEngagementLevel(newLevel);
        if (!newLevel.equals(oldLevel)) {
            score.setLevelUpdatedAt(now);
        }
        // 趋势
        applyTrend(score, oldCurrentScore, currentScore);
        score = scoreRepository.save(score);
        log.info("计算客户评分: customerId={}, totalScore={}, currentScore={}, level={}, trend={}",
                customerId, totalScore, currentScore, newLevel, score.getScoreTrend());
        return toScoreDto(score);
    }

    /**
     * 重算所有客户评分。
     *
     * @return 处理结果 (total 客户数 / processed 实际处理数)
     */
    @Transactional
    public Map<String, Object> recalculateAllScores() {
        List<ScrmEngagementScoreEntity> scores = scoreRepository.findAll();
        int processed = 0;
        for (ScrmEngagementScoreEntity score : scores) {
            try {
                calculateScore(score.getCustomerId());
                processed++;
            } catch (Exception e) {
                log.warn("重算客户评分失败: customerId={}, error={}", score.getCustomerId(), e.getMessage());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", scores.size());
        result.put("processed", processed);
        log.info("重算所有客户评分:, total={}, processed={}", scores.size(), processed);
        return result;
    }

    /**
     * 获取客户评分 (不存在则初始化为 0 分)。
     *
     * @param customerId 客户 ID
     * @return 评分 DTO
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmEngagementScoreDto getScore(Long customerId) throws ScrmException {
        if (customerId == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        return toScoreDto(getOrCreateScoreEntity(customerId));
    }

    /**
     * 分页查询评分, 支持按等级 / 分数区间过滤与排序。
     *
     * @param engagementLevel 等级过滤（可空）
     * @param minScore        最低当前分过滤（可空）
     * @param maxScore        最高当前分过滤（可空）
     * @param sortBy          排序字段: currentScore / totalScore / totalEvents / streakDays（可空, 默认 currentScore）
     * @param pageable        分页参数
     * @return 评分分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmEngagementScoreDto> listScores(String engagementLevel, Double minScore, Double maxScore,
                                                    String sortBy, Pageable pageable) {
        Specification<ScrmEngagementScoreEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (engagementLevel != null && !engagementLevel.isBlank()) {
                predicates.add(cb.equal(root.get("engagementLevel"), engagementLevel));
            }
            if (minScore != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("currentScore"), minScore));
            }
            if (maxScore != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("currentScore"), maxScore));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        String field = sanitizeSortField(sortBy);
        return scoreRepository.findAll(spec, ScrmEngagementScoreRuleService.ensureSort(pageable, field))
                .map(this::toScoreDto);
    }

    /**
     * 获取客户衰减后分数 (触发重算并返回 currentScore)。
     *
     * @param customerId 客户 ID
     * @return 衰减后分数
     * @throws ScrmException 参数非法
     */
    @Transactional
    public double getDecayedScore(Long customerId) throws ScrmException {
        return calculateScore(customerId).getCurrentScore();
    }

    /**
     * 应用衰减计算。
     * <p>按 decayType 计算 score 在 eventTime 之后 decayDays 天的衰减值:
     * <ul>
     *   <li>LINEAR: score × max(0, 1 - daysSince / decayDays)</li>
     *   <li>EXPONENTIAL: score × e^(-daysSince / decayDays)</li>
     *   <li>STEP: 前 1/3 全分, 中 1/3 半分, 后 1/3 四分之一, 超期 0</li>
     *   <li>NONE: 原值</li>
     * </ul>
     * decayDays <= 0 时按 NONE 处理。</p>
     *
     * @param score     原始得分
     * @param eventTime 事件发生时间
     * @param decayDays 衰减天数
     * @param decayType 衰减类型
     * @return 衰减后得分 (>=0)
     */
    public double applyDecay(double score, LocalDateTime eventTime, int decayDays, String decayType) {
        if (score <= 0 || eventTime == null) {
            return 0;
        }
        if (decayDays <= 0 || ScrmEngagementScoreRuleService.DECAY_NONE.equals(decayType)) {
            return score;
        }
        long daysSince = java.time.Duration.between(eventTime, LocalDateTime.now()).toDays();
        if (daysSince <= 0) {
            return score;
        }
        double factor;
        switch (decayType == null ? ScrmEngagementScoreRuleService.DEFAULT_DECAY_TYPE : decayType) {
            case ScrmEngagementScoreRuleService.DECAY_EXPONENTIAL:
                factor = Math.exp(-(double) daysSince / decayDays);
                break;
            case ScrmEngagementScoreRuleService.DECAY_STEP:
                double third = decayDays / 3.0;
                if (daysSince < third) {
                    factor = 1.0;
                } else if (daysSince < third * 2) {
                    factor = 0.5;
                } else if (daysSince < decayDays) {
                    factor = 0.25;
                } else {
                    factor = 0;
                }
                break;
            case ScrmEngagementScoreRuleService.DECAY_LINEAR:
            default:
                factor = Math.max(0, 1 - (double) daysSince / decayDays);
                break;
        }
        return score * factor;
    }

    /**
     * 应用周期上限: 校验每日/每周/每月上限, 超限则截断或跳过。
     *
     * @param customerId 客户 ID
     * @param rule       规则
     * @param rawPoints  本次原始得分
     * @param eventTime  事件时间
     * @return 调整后得分 (0 表示已达上限跳过)
     */
    private int applyPeriodLimit(Long customerId, ScrmEngagementRuleEntity rule,
                                  int rawPoints, LocalDateTime eventTime) {
        int points = rawPoints;
        // 每日上限
        if (rule.getDailyLimit() != null && rule.getDailyLimit() > 0) {
            LocalDateTime dayStart = eventTime.toLocalDate().atStartOfDay();
            long todayCount = eventRepository.countByRuleAndCustomerInRange(
                     customerId, rule.getId(), dayStart, dayStart.plusDays(1));
            if (todayCount >= rule.getDailyLimit()) {
                return 0;
            }
        }
        // 每周上限
        if (rule.getWeeklyLimit() != null && rule.getWeeklyLimit() > 0) {
            LocalDateTime weekStart = eventTime.toLocalDate().minusDays(6).atStartOfDay();
            long weekCount = eventRepository.countByRuleAndCustomerInRange(
                     customerId, rule.getId(), weekStart, eventTime.plusNanos(1));
            if (weekCount >= rule.getWeeklyLimit()) {
                return 0;
            }
        }
        // 每月上限
        if (rule.getMonthlyLimit() != null && rule.getMonthlyLimit() > 0) {
            LocalDateTime monthStart = eventTime.toLocalDate().minusDays(29).atStartOfDay();
            long monthCount = eventRepository.countByRuleAndCustomerInRange(
                     customerId, rule.getId(), monthStart, eventTime.plusNanos(1));
            if (monthCount >= rule.getMonthlyLimit()) {
                return 0;
            }
        }
        return points;
    }

    /**
     * 事件落库后同步更新评分 (增量累加, 事件刚发生时衰减因子≈1)。
     *
     * @param customerId   客户 ID
     * @param customerName 客户名称
     * @param points       本次得分
     * @param eventTime    事件时间
     * @param rule         命中规则 (可空)
     */
    private void updateScoreOnEvent(Long customerId, String customerName,
                                     int points, LocalDateTime eventTime, ScrmEngagementRuleEntity rule) {
        ScrmEngagementScoreEntity score = getOrCreateScoreEntity(customerId);
        if (customerName != null && !customerName.isBlank()) {
            score.setCustomerName(customerName);
        }
        double oldCurrent = score.getCurrentScore() != null ? score.getCurrentScore() : 0;
        score.setTotalScore((score.getTotalScore() != null ? score.getTotalScore() : 0) + points);
        // 事件刚发生, 衰减因子≈1, 直接累加 (calculateScore 会重算精确值)
        double newCurrent = oldCurrent + points;
        score.setCurrentScore(newCurrent);
        score.setTotalEvents((score.getTotalEvents() != null ? score.getTotalEvents() : 0) + 1);
        score.setLastEventAt(eventTime);
        score.setLastCalculatedAt(LocalDateTime.now());
        // 等级
        String oldLevel = score.getEngagementLevel();
        String newLevel = levelService.determineLevel(newCurrent);
        score.setEngagementLevel(newLevel);
        if (!newLevel.equals(oldLevel)) {
            score.setLevelUpdatedAt(LocalDateTime.now());
        }
        // 趋势
        applyTrend(score, oldCurrent, newCurrent);
        scoreRepository.save(score);
    }

    /**
     * 计算并设置评分趋势。
     *
     * @param score       评分实体
     * @param oldCurrent  旧当前分
     * @param newCurrent  新当前分
     */
    private void applyTrend(ScrmEngagementScoreEntity score, double oldCurrent, double newCurrent) {
        String trend;
        double changePercent;
        if (oldCurrent == 0 && newCurrent == 0) {
            trend = ScrmEngagementScoreRuleService.TREND_STABLE;
            changePercent = 0;
        } else if (oldCurrent == 0) {
            trend = ScrmEngagementScoreRuleService.TREND_UP;
            changePercent = 100;
        } else {
            double ratio = (newCurrent - oldCurrent) / oldCurrent;
            if (ratio > ScrmEngagementScoreRuleService.TREND_THRESHOLD) {
                trend = ScrmEngagementScoreRuleService.TREND_UP;
            } else if (ratio < -ScrmEngagementScoreRuleService.TREND_THRESHOLD) {
                trend = ScrmEngagementScoreRuleService.TREND_DOWN;
            } else {
                trend = ScrmEngagementScoreRuleService.TREND_STABLE;
            }
            changePercent = ScrmEngagementScoreRuleService.round2(ratio * 100);
        }
        score.setScoreTrend(trend);
        score.setTrendChangePercent(changePercent);
    }

    /**
     * 计算连续互动天数 (从最近事件日向前计数, 出现间隔则中断)。
     *
     * @param events 事件列表
     * @return 连续互动天数
     */
    private int calculateStreakDays(List<ScrmEngagementEventEntity> events) {
        if (events == null || events.isEmpty()) {
            return 0;
        }
        List<LocalDate> dates = events.stream()
                .map(e -> e.getEventTime().toLocalDate())
                .distinct()
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
        int streak = 0;
        LocalDate expected = dates.get(0);
        for (LocalDate d : dates) {
            if (d.equals(expected)) {
                streak++;
                expected = expected.minusDays(1);
            } else if (d.isBefore(expected)) {
                break;
            }
        }
        return streak;
    }

    /**
     * 获取或创建评分实体 (不存在则初始化为 0 分)。
     *
     * @param customerId 客户 ID
     * @return 评分实体
     */
    private ScrmEngagementScoreEntity getOrCreateScoreEntity(Long customerId) {
        return scoreRepository.findByCustomerId(customerId).orElseGet(() -> {
            ScrmEngagementScoreEntity score = new ScrmEngagementScoreEntity();
            score.setCustomerId(customerId);
            score.setTotalScore(0d);
            score.setCurrentScore(0d);
            score.setEngagementLevel(ScrmEngagementScoreRuleService.LEVEL_INACTIVE);
            score.setStreakDays(0);
            score.setTotalEvents(0);
            score.setWeeklyScore(0d);
            score.setMonthlyScore(0d);
            score.setQuarterlyScore(0d);
            score.setYearlyScore(0d);
            score.setTrendChangePercent(0d);
            return scoreRepository.save(score);
        });
    }

    /**
     * 白名单校验排序字段, 防止注入非法字段名。
     *
     * @param sortBy 排序字段
     * @return 合法排序字段
     */
    private String sanitizeSortField(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return "currentScore";
        }
        switch (sortBy.trim()) {
            case "currentScore":
            case "totalScore":
            case "totalEvents":
            case "streakDays":
            case "weeklyScore":
            case "monthlyScore":
            case "lastEventAt":
                return sortBy.trim();
            default:
                return "currentScore";
        }
    }

    /**
     * 将行为得分明细 Map 序列化为 JSON 字符串。
     *
     * @param map 行为得分明细
     * @return JSON 字符串
     */
    private String toBehaviorJson(Map<String, Double> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Double> e : map.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            sb.append("\"").append(e.getKey()).append("\":")
                    .append(ScrmEngagementScoreRuleService.round2(e.getValue()));
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * 按主键查询事件并校验账号归属, 不存在或越权抛异常。
     *
     * @param id 事件 ID
     * @return 事件实体
     * @throws ScrmException 事件不存在
     */
    private ScrmEngagementEventEntity findEventOrThrow(Long id) throws ScrmException {
        ScrmEngagementEventEntity entity = eventRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "互动事件不存在: id=" + id));
        return entity;
    }

    /**
     * 事件实体转 DTO。
     */
    private ScrmEngagementEventDto toEventDto(ScrmEngagementEventEntity entity) {
        ScrmEngagementEventDto dto = new ScrmEngagementEventDto();
        dto.setId(entity.getId());
        dto.setCustomerId(entity.getCustomerId());
        dto.setCustomerName(entity.getCustomerName());
        dto.setBehaviorType(entity.getBehaviorType());
        dto.setChannel(entity.getChannel());
        dto.setEventTime(entity.getEventTime());
        dto.setPoints(entity.getPoints());
        dto.setRuleId(entity.getRuleId());
        dto.setSessionId(entity.getSessionId());
        dto.setPageUrl(entity.getPageUrl());
        dto.setReferrer(entity.getReferrer());
        dto.setUserAgent(entity.getUserAgent());
        dto.setDeviceType(entity.getDeviceType());
        dto.setLocation(entity.getLocation());
        dto.setMetadata(entity.getMetadata());
        dto.setIp(entity.getIp());
        dto.setProcessed(entity.getProcessed());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
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