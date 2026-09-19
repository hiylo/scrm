/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmEngagementScoreService.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.dto.ScrmEngagementEventDto;
import org.hiylo.scrm.dto.ScrmEngagementLevelDto;
import org.hiylo.scrm.dto.ScrmEngagementRecordDto;
import org.hiylo.scrm.dto.ScrmEngagementRuleDto;
import org.hiylo.scrm.dto.ScrmEngagementScoreDto;
import org.hiylo.scrm.exception.ScrmException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 客户互动评分引擎服务 (门面)。
 * <p>
 * 承载客户互动行为评分的核心能力: 评分规则 (行为类型/渠道/得分/上限/衰减/权重) 增删改查与启用禁用,
 * 互动事件记录 (匹配规则→计算得分→应用上限→更新评分), 客户评分计算 (汇总事件→应用衰减→更新等级
 * 与趋势), 活跃度等级管理, 评分趋势/分布/排行, 衰减计算, 以及互动统计 (总览/行为/渠道/趋势)。
 * 实际实现按子域拆分至兄弟服务: {@link ScrmEngagementScoreRuleService} (规则管理)、
 * {@link ScrmEngagementScoreEventService} (事件记录与评分)、{@link ScrmEngagementScoreLevelService}
 * (等级管理)、{@link ScrmEngagementScoreStatsService} (统计趋势)。本门面仅做方法委托, 全部
 * public 方法签名保持不变。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
public class ScrmEngagementScoreService {

    /** 规则管理兄弟服务 */
    private final ScrmEngagementScoreRuleService ruleService;

    /** 事件记录与评分兄弟服务 */
    private final ScrmEngagementScoreEventService eventScoreService;

    /** 等级管理兄弟服务 */
    private final ScrmEngagementScoreLevelService levelService;

    /** 统计趋势兄弟服务 */
    private final ScrmEngagementScoreStatsService statsService;

    // ============================================================
    // 规则管理
    // ============================================================

    /**
     * 创建互动评分规则。
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException 参数非法
     */
    public ScrmEngagementRuleDto createRule(ScrmEngagementRuleDto dto) throws ScrmException {
        return ruleService.createRule(dto);
    }

    /**
     * 更新互动评分规则（字段非空才覆盖）。
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 参数非法
     */
    public ScrmEngagementRuleDto updateRule(Long id, ScrmEngagementRuleDto dto) throws ScrmException {
        return ruleService.updateRule(id, dto);
    }

    /**
     * 删除互动评分规则。
     *
     * @param id 规则 ID
     * @throws ScrmException 规则不存在
     */
    public void deleteRule(Long id) throws ScrmException {
        ruleService.deleteRule(id);
    }

    /**
     * 查询规则详情。
     *
     * @param id 规则 ID
     * @return 规则 DTO
     * @throws ScrmException 规则不存在
     */
    public ScrmEngagementRuleDto getRule(Long id) throws ScrmException {
        return ruleService.getRule(id);
    }

    /**
     * 分页查询互动评分规则, 支持按行为类型 / 渠道 / 启用状态 / 关键字过滤。
     *
     * @param behaviorType 行为类型过滤（可空）
     * @param channel      渠道过滤（可空）
     * @param enabled      启用状态过滤（可空）
     * @param keyword      规则名称关键字过滤（可空）
     * @param pageable     分页参数
     * @return 规则分页结果 (按 createTime DESC)
     */
    public Page<ScrmEngagementRuleDto> listRules(String behaviorType, String channel, Boolean enabled,
                                                  String keyword, Pageable pageable) {
        return ruleService.listRules(behaviorType, channel, enabled, keyword, pageable);
    }

    /**
     * 启用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    public ScrmEngagementRuleDto enableRule(Long id) throws ScrmException {
        return ruleService.enableRule(id);
    }

    /**
     * 禁用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    public ScrmEngagementRuleDto disableRule(Long id) throws ScrmException {
        return ruleService.disableRule(id);
    }

    /**
     * 匹配规则: 按行为类型 + 渠道查找适用规则列表。
     *
     * @param behaviorType 行为类型
     * @param channel      渠道（可空）
     * @return 适用规则列表
     */
    public List<ScrmEngagementRuleDto> matchRule(String behaviorType, String channel) {
        return ruleService.matchRule(behaviorType, channel);
    }

    // ============================================================
    // 事件管理
    // ============================================================

    /**
     * 记录行为事件 (匹配规则→计算得分→应用上限→更新评分)。
     *
     * @param recordDto 事件记录参数
     * @return 创建后的事件
     * @throws ScrmException 参数非法
     */
    public ScrmEngagementEventDto recordEvent(ScrmEngagementRecordDto recordDto) throws ScrmException {
        return eventScoreService.recordEvent(recordDto);
    }

    /**
     * 批量记录行为事件。
     *
     * @param records 事件记录列表
     * @return 创建后的事件列表
     * @throws ScrmException 参数非法
     */
    public List<ScrmEngagementEventDto> batchRecordEvents(List<ScrmEngagementRecordDto> records)
            throws ScrmException {
        return eventScoreService.batchRecordEvents(records);
    }

    /**
     * 查询事件详情。
     *
     * @param id 事件 ID
     * @return 事件 DTO
     * @throws ScrmException 事件不存在
     */
    public ScrmEngagementEventDto getEvent(Long id) throws ScrmException {
        return eventScoreService.getEvent(id);
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
    public Page<ScrmEngagementEventDto> listEvents(Long customerId, String behaviorType, String channel,
                                                    LocalDateTime startTime, LocalDateTime endTime,
                                                    Pageable pageable) {
        return eventScoreService.listEvents(customerId, behaviorType, channel, startTime, endTime, pageable);
    }

    /**
     * 按客户分页查询事件 (按 eventTime DESC)。
     *
     * @param customerId 客户 ID
     * @param pageable   分页参数
     * @return 事件分页结果
     */
    public Page<ScrmEngagementEventDto> getEventsByCustomer(Long customerId, Pageable pageable) {
        return eventScoreService.getEventsByCustomer(customerId, pageable);
    }

    // ============================================================
    // 评分计算
    // ============================================================

    /**
     * 计算客户评分 (汇总事件→应用衰减→更新等级→更新趋势)。
     *
     * @param customerId 客户 ID
     * @return 更新后的评分
     * @throws ScrmException 参数非法
     */
    public ScrmEngagementScoreDto calculateScore(Long customerId) throws ScrmException {
        return eventScoreService.calculateScore(customerId);
    }

    /**
     * 重算所有客户评分。
     *
     * @return 处理结果 (total 客户数 / processed 实际处理数)
     */
    public Map<String, Object> recalculateAllScores() {
        return eventScoreService.recalculateAllScores();
    }

    /**
     * 获取客户评分 (不存在则初始化为 0 分)。
     *
     * @param customerId 客户 ID
     * @return 评分 DTO
     * @throws ScrmException 参数非法
     */
    public ScrmEngagementScoreDto getScore(Long customerId) throws ScrmException {
        return eventScoreService.getScore(customerId);
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
    public Page<ScrmEngagementScoreDto> listScores(String engagementLevel, Double minScore, Double maxScore,
                                                    String sortBy, Pageable pageable) {
        return eventScoreService.listScores(engagementLevel, minScore, maxScore, sortBy, pageable);
    }

    /**
     * 评分趋势: 返回最近 days 天每日的事件数 / 原始得分 / 衰减后得分。
     *
     * @param customerId 客户 ID
     * @param days       天数
     * @return 趋势列表 (按日期升序)
     * @throws ScrmException 参数非法
     */
    public List<Map<String, Object>> getScoreTrend(Long customerId, int days) throws ScrmException {
        return statsService.getScoreTrend(customerId, days);
    }

    /**
     * 评分分布: 按活跃等级分组统计客户数。
     *
     * @return 等级分布 (含全部 5 个等级, 无客户的等级为 0)
     */
    public Map<String, Object> getScoreDistribution() {
        return statsService.getScoreDistribution();
    }

    /**
     * 高分客户排行 (按当前分倒序)。
     *
     * @param limit 返回数量
     * @return 评分列表
     */
    public List<ScrmEngagementScoreDto> getTopCustomers(int limit) {
        return statsService.getTopCustomers(limit);
    }

    // ============================================================
    // 等级管理
    // ============================================================

    /**
     * 创建互动活跃度等级。
     *
     * @param dto 等级参数
     * @return 创建后的等级
     * @throws ScrmException 参数非法
     */
    public ScrmEngagementLevelDto createLevel(ScrmEngagementLevelDto dto) throws ScrmException {
        return levelService.createLevel(dto);
    }

    /**
     * 更新互动等级（字段非空才覆盖）。
     *
     * @param id  等级 ID
     * @param dto 等级参数
     * @return 更新后的等级
     * @throws ScrmException 等级不存在 / 参数非法
     */
    public ScrmEngagementLevelDto updateLevel(Long id, ScrmEngagementLevelDto dto) throws ScrmException {
        return levelService.updateLevel(id, dto);
    }

    /**
     * 删除互动等级。
     *
     * @param id 等级 ID
     * @throws ScrmException 等级不存在
     */
    public void deleteLevel(Long id) throws ScrmException {
        levelService.deleteLevel(id);
    }

    /**
     * 查询等级详情。
     *
     * @param id 等级 ID
     * @return 等级 DTO
     * @throws ScrmException 等级不存在
     */
    public ScrmEngagementLevelDto getLevel(Long id) throws ScrmException {
        return levelService.getLevel(id);
    }

    /**
     * 分页查询互动等级, 支持按启用状态过滤。
     *
     * @param enabled  启用状态过滤（可空）
     * @param pageable 分页参数
     * @return 等级分页结果 (按 priority DESC, createTime DESC)
     */
    public Page<ScrmEngagementLevelDto> listLevels(Boolean enabled, Pageable pageable) {
        return levelService.listLevels(enabled, pageable);
    }

    /**
     * 启用等级。
     *
     * @param id 等级 ID
     * @return 更新后的等级
     * @throws ScrmException 等级不存在
     */
    public ScrmEngagementLevelDto enableLevel(Long id) throws ScrmException {
        return levelService.enableLevel(id);
    }

    /**
     * 禁用等级。
     *
     * @param id 等级 ID
     * @return 更新后的等级
     * @throws ScrmException 等级不存在
     */
    public ScrmEngagementLevelDto disableLevel(Long id) throws ScrmException {
        return levelService.disableLevel(id);
    }

    /**
     * 根据分数确定活跃等级。
     *
     * @param score 当前分
     * @return 等级编码
     */
    public String determineLevel(double score) {
        return levelService.determineLevel(score);
    }

    // ============================================================
    // 衰减计算
    // ============================================================

    /**
     * 应用衰减计算。
     *
     * @param score     原始得分
     * @param eventTime 事件发生时间
     * @param decayDays 衰减天数
     * @param decayType 衰减类型
     * @return 衰减后得分 (>=0)
     */
    public double applyDecay(double score, LocalDateTime eventTime, int decayDays, String decayType) {
        return eventScoreService.applyDecay(score, eventTime, decayDays, decayType);
    }

    /**
     * 获取客户衰减后分数 (触发重算并返回 currentScore)。
     *
     * @param customerId 客户 ID
     * @return 衰减后分数
     * @throws ScrmException 参数非法
     */
    public double getDecayedScore(Long customerId) throws ScrmException {
        return eventScoreService.getDecayedScore(customerId);
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 互动统计: 总事件 / 总客户 / 平均分 / 各等级分布。
     *
     * @param startTime 起始时间（可空）
     * @param endTime   截止时间（可空）
     * @return 互动统计
     */
    public Map<String, Object> getEngagementStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getEngagementStats(startTime, endTime);
    }

    /**
     * 行为类型统计: 按行为类型分组统计事件数。
     *
     * @param startTime 起始时间（可空）
     * @param endTime   截止时间（可空）
     * @return 行为统计列表 [{behaviorType, eventCount}]
     */
    public List<Map<String, Object>> getBehaviorStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getBehaviorStats(startTime, endTime);
    }

    /**
     * 渠道统计: 按渠道分组统计事件数。
     *
     * @param startTime 起始时间（可空）
     * @param endTime   截止时间（可空）
     * @return 渠道统计列表 [{channel, eventCount}]
     */
    public List<Map<String, Object>> getChannelStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getChannelStats(startTime, endTime);
    }

    /**
     * 互动趋势: 最近 days 天每日事件数。
     *
     * @param days 天数
     * @return 趋势列表 [{date, eventCount}]
     */
    public List<Map<String, Object>> getTrend(int days) {
        return statsService.getTrend(days);
    }
}