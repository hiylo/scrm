/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmEngagementScoreRuleService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmEngagementRuleDto;
import org.hiylo.scrm.entity.ScrmEngagementRuleEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmEngagementRuleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SCRM 客户互动评分规则管理服务 (规则管理子域)。
 * <p>
 * 承载互动评分规则增删改查 / 启用禁用 / 规则匹配, 并托管互动评分模块共享常量
 * (衰减类型 / 活跃等级 / 趋势 / 默认值与周期天数) 与通用统计工具 (round2 / toLong /
 * toDouble / ensureSort), 供事件评分 / 等级 / 统计兄弟类以 package 级访问复用。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmEngagementScoreRuleService {

    // ==================== 衰减类型常量 (共享) ====================

    /** 衰减类型: 线性 */
    static final String DECAY_LINEAR = "LINEAR";
    /** 衰减类型: 指数 */
    static final String DECAY_EXPONENTIAL = "EXPONENTIAL";
    /** 衰减类型: 阶梯 */
    static final String DECAY_STEP = "STEP";
    /** 衰减类型: 不衰减 */
    static final String DECAY_NONE = "NONE";

    // ==================== 活跃等级常量 (共享) ====================

    /** 活跃等级: 不活跃 */
    static final String LEVEL_INACTIVE = "INACTIVE";
    /** 活跃等级: 低 */
    static final String LEVEL_LOW = "LOW";
    /** 活跃等级: 中 */
    static final String LEVEL_MEDIUM = "MEDIUM";
    /** 活跃等级: 高 */
    static final String LEVEL_HIGH = "HIGH";
    /** 活跃等级: 非常高 */
    static final String LEVEL_VERY_HIGH = "VERY_HIGH";

    // ==================== 趋势常量 (共享) ====================

    /** 趋势: 上升 */
    static final String TREND_UP = "UP";
    /** 趋势: 持平 */
    static final String TREND_STABLE = "STABLE";
    /** 趋势: 下降 */
    static final String TREND_DOWN = "DOWN";

    // ==================== 默认值常量 (共享) ====================

    /** 默认启用状态 */
    static final boolean DEFAULT_ENABLED = true;
    /** 默认单次得分 */
    static final int DEFAULT_POINTS = 1;
    /** 默认每日上限 (0=不限) */
    static final int DEFAULT_DAILY_LIMIT = 0;
    /** 默认每周上限 (0=不限) */
    static final int DEFAULT_WEEKLY_LIMIT = 0;
    /** 默认每月上限 (0=不限) */
    static final int DEFAULT_MONTHLY_LIMIT = 0;
    /** 默认衰减天数 */
    static final int DEFAULT_DECAY_DAYS = 30;
    /** 默认衰减类型 */
    static final String DEFAULT_DECAY_TYPE = DECAY_LINEAR;
    /** 默认权重 */
    static final double DEFAULT_WEIGHT = 1.0;
    /** 默认匹配次数初值 */
    static final int DEFAULT_MATCH_COUNT = 0;
    /** 默认优先级 */
    static final int DEFAULT_PRIORITY = 0;
    /** 默认操作人 */
    static final String DEFAULT_OPERATOR = "scrm-system";
    /** 趋势变化阈值 (5%) */
    static final double TREND_THRESHOLD = 0.05;
    /** 周期: 周 (7 天) */
    static final int PERIOD_WEEK_DAYS = 7;
    /** 周期: 月 (30 天) */
    static final int PERIOD_MONTH_DAYS = 30;
    /** 周期: 季度 (90 天) */
    static final int PERIOD_QUARTER_DAYS = 90;
    /** 周期: 年 (365 天) */
    static final int PERIOD_YEAR_DAYS = 365;

    /** 互动规则数据访问层 */
    private final ScrmEngagementRuleRepository ruleRepository;

    /**
     * 创建互动评分规则。
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmEngagementRuleDto createRule(ScrmEngagementRuleDto dto) throws ScrmException {
        validateRuleDto(dto, false);
        ScrmEngagementRuleEntity entity = new ScrmEngagementRuleEntity();
        entity.setRuleName(dto.getRuleName());
        entity.setBehaviorType(dto.getBehaviorType());
        entity.setChannel(dto.getChannel());
        entity.setPoints(dto.getPoints());
        entity.setDailyLimit(dto.getDailyLimit() != null ? dto.getDailyLimit() : DEFAULT_DAILY_LIMIT);
        entity.setWeeklyLimit(dto.getWeeklyLimit() != null ? dto.getWeeklyLimit() : DEFAULT_WEEKLY_LIMIT);
        entity.setMonthlyLimit(dto.getMonthlyLimit() != null ? dto.getMonthlyLimit() : DEFAULT_MONTHLY_LIMIT);
        entity.setDecayDays(dto.getDecayDays() != null ? dto.getDecayDays() : DEFAULT_DECAY_DAYS);
        entity.setDecayType(dto.getDecayType() != null ? dto.getDecayType() : DEFAULT_DECAY_TYPE);
        entity.setWeight(dto.getWeight() != null ? dto.getWeight() : DEFAULT_WEIGHT);
        entity.setDescription(dto.getDescription());
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : DEFAULT_ENABLED);
        entity.setMatchCount(DEFAULT_MATCH_COUNT);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = ruleRepository.save(entity);
        log.info("创建互动评分规则: id={}, ruleName={}, behaviorType={}",
                entity.getId(), entity.getRuleName(), entity.getBehaviorType());
        return toRuleDto(entity);
    }

    /**
     * 更新互动评分规则（字段非空才覆盖）。
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 参数非法
     */
    @Transactional
    public ScrmEngagementRuleDto updateRule(Long id, ScrmEngagementRuleDto dto) throws ScrmException {
        ScrmEngagementRuleEntity entity = findRuleOrThrow(id);
        validateRuleDto(dto, true);
        if (dto.getRuleName() != null) entity.setRuleName(dto.getRuleName());
        if (dto.getBehaviorType() != null) entity.setBehaviorType(dto.getBehaviorType());
        if (dto.getChannel() != null) entity.setChannel(dto.getChannel());
        if (dto.getPoints() != null) entity.setPoints(dto.getPoints());
        if (dto.getDailyLimit() != null) entity.setDailyLimit(dto.getDailyLimit());
        if (dto.getWeeklyLimit() != null) entity.setWeeklyLimit(dto.getWeeklyLimit());
        if (dto.getMonthlyLimit() != null) entity.setMonthlyLimit(dto.getMonthlyLimit());
        if (dto.getDecayDays() != null) entity.setDecayDays(dto.getDecayDays());
        if (dto.getDecayType() != null) entity.setDecayType(dto.getDecayType());
        if (dto.getWeight() != null) entity.setWeight(dto.getWeight());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = ruleRepository.save(entity);
        log.info("更新互动评分规则: id={}, ruleName={}", entity.getId(), entity.getRuleName());
        return toRuleDto(entity);
    }

    /**
     * 删除互动评分规则。
     *
     * @param id 规则 ID
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public void deleteRule(Long id) throws ScrmException {
        ScrmEngagementRuleEntity entity = findRuleOrThrow(id);
        ruleRepository.delete(entity);
        log.info("删除互动评分规则: id={}, ruleName={}", id, entity.getRuleName());
    }

    /**
     * 查询规则详情。
     *
     * @param id 规则 ID
     * @return 规则 DTO
     * @throws ScrmException 规则不存在
     */
    @Transactional(readOnly = true)
    public ScrmEngagementRuleDto getRule(Long id) throws ScrmException {
        return toRuleDto(findRuleOrThrow(id));
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
    @Transactional(readOnly = true)
    public Page<ScrmEngagementRuleDto> listRules(String behaviorType, String channel, Boolean enabled,
                                                  String keyword, Pageable pageable) {
        Specification<ScrmEngagementRuleEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (behaviorType != null && !behaviorType.isBlank()) {
                predicates.add(cb.equal(root.get("behaviorType"), behaviorType));
            }
            if (channel != null && !channel.isBlank()) {
                predicates.add(cb.equal(root.get("channel"), channel));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(root.get("ruleName"), "%" + keyword + "%"));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return ruleRepository.findAll(spec, ensureSort(pageable, "createTime")).map(this::toRuleDto);
    }

    /**
     * 启用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public ScrmEngagementRuleDto enableRule(Long id) throws ScrmException {
        ScrmEngagementRuleEntity entity = findRuleOrThrow(id);
        entity.setEnabled(true);
        entity = ruleRepository.save(entity);
        log.info("启用互动评分规则: id={}, ruleName={}", id, entity.getRuleName());
        return toRuleDto(entity);
    }

    /**
     * 禁用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public ScrmEngagementRuleDto disableRule(Long id) throws ScrmException {
        ScrmEngagementRuleEntity entity = findRuleOrThrow(id);
        entity.setEnabled(false);
        entity = ruleRepository.save(entity);
        log.info("禁用互动评分规则: id={}, ruleName={}", id, entity.getRuleName());
        return toRuleDto(entity);
    }

    /**
     * 匹配规则: 按行为类型 + 渠道查找适用规则列表。
     * <p>channel 为空时仅匹配 channel 为空 (任意渠道) 的规则; channel 非空时匹配 channel 为空
     * 或精确等于该渠道的规则。</p>
     *
     * @param behaviorType 行为类型
     * @param channel      渠道（可空）
     * @return 适用规则列表
     */
    @Transactional(readOnly = true)
    public List<ScrmEngagementRuleDto> matchRule(String behaviorType, String channel) {
        List<ScrmEngagementRuleEntity> rules = ruleRepository
                .findByBehaviorTypeAndEnabledTrue(behaviorType);
        return rules.stream()
                .filter(r -> r.getChannel() == null || r.getChannel().equals(channel))
                .map(this::toRuleDto)
                .collect(Collectors.toList());
    }

    /**
     * 校验规则参数。
     *
     * @param dto     规则参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateRuleDto(ScrmEngagementRuleDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("规则参数不能为空");
        }
        if (dto.getRuleName() == null || dto.getRuleName().isBlank()) {
            if (!partial) {
                throw ScrmException.badRequest("规则名称不能为空");
            }
        }
        if (dto.getBehaviorType() == null || dto.getBehaviorType().isBlank()) {
            if (!partial) {
                throw ScrmException.badRequest("行为类型不能为空");
            }
        }
        if (dto.getPoints() == null && !partial) {
            throw ScrmException.badRequest("得分不能为空");
        }
        if (dto.getPoints() != null && dto.getPoints() <= 0) {
            throw ScrmException.badRequest("得分必须为正数");
        }
        if (dto.getDecayType() != null && !DECAY_LINEAR.equals(dto.getDecayType())
                && !DECAY_EXPONENTIAL.equals(dto.getDecayType())
                && !DECAY_STEP.equals(dto.getDecayType()) && !DECAY_NONE.equals(dto.getDecayType())) {
            throw ScrmException.badRequest("衰减类型非法: " + dto.getDecayType()
                    + ", 仅支持 LINEAR/EXPONENTIAL/STEP/NONE");
        }
    }

    /**
     * 匹配规则实体 (供事件兄弟类调用, 返回实体列表)。
     *
     * @param behaviorType 行为类型
     * @param channel      渠道（可空）
     * @return 适用规则实体列表
     */
    List<ScrmEngagementRuleEntity> matchRuleEntities(String behaviorType, String channel) {
        List<ScrmEngagementRuleEntity> rules = ruleRepository
                .findByBehaviorTypeAndEnabledTrue(behaviorType);
        return rules.stream()
                .filter(r -> r.getChannel() == null || r.getChannel().equals(channel))
                .collect(Collectors.toList());
    }

    /**
     * 确保分页参数带默认排序 (按指定字段倒序)。
     *
     * @param pageable 分页参数
     * @param field    默认排序字段
     * @return 处理后的分页参数
     */
    static Pageable ensureSort(Pageable pageable, String field) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, field));
    }

    /**
     * 保留两位小数。
     *
     * @param value 原始值
     * @return 保留两位小数后的值
     */
    static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * 将统计结果数组的指定位置转为 long。
     *
     * @param stats 统计结果数组
     * @param index 索引
     * @return long 值
     */
    static long toLong(Object[] stats, int index) {
        if (stats == null || index >= stats.length || stats[index] == null) {
            return 0L;
        }
        if (stats[index] instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(stats[index].toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * 将统计结果数组的指定位置转为 double。
     *
     * @param stats 统计结果数组
     * @param index 索引
     * @return double 值
     */
    static double toDouble(Object[] stats, int index) {
        if (stats == null || index >= stats.length || stats[index] == null) {
            return 0d;
        }
        if (stats[index] instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(stats[index].toString());
        } catch (NumberFormatException e) {
            return 0d;
        }
    }

    /**
     * 按主键查询规则并校验账号归属, 不存在或越权抛异常。
     *
     * @param id 规则 ID
     * @return 规则实体
     * @throws ScrmException 规则不存在
     */
    private ScrmEngagementRuleEntity findRuleOrThrow(Long id) throws ScrmException {
        ScrmEngagementRuleEntity entity = ruleRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "互动评分规则不存在: id=" + id));
        return entity;
    }

    /**
     * 规则实体转 DTO。
     */
    private ScrmEngagementRuleDto toRuleDto(ScrmEngagementRuleEntity entity) {
        ScrmEngagementRuleDto dto = new ScrmEngagementRuleDto();
        dto.setId(entity.getId());
        dto.setRuleName(entity.getRuleName());
        dto.setBehaviorType(entity.getBehaviorType());
        dto.setChannel(entity.getChannel());
        dto.setPoints(entity.getPoints());
        dto.setDailyLimit(entity.getDailyLimit());
        dto.setWeeklyLimit(entity.getWeeklyLimit());
        dto.setMonthlyLimit(entity.getMonthlyLimit());
        dto.setDecayDays(entity.getDecayDays());
        dto.setDecayType(entity.getDecayType());
        dto.setWeight(entity.getWeight());
        dto.setDescription(entity.getDescription());
        dto.setEnabled(entity.getEnabled());
        dto.setMatchCount(entity.getMatchCount());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}