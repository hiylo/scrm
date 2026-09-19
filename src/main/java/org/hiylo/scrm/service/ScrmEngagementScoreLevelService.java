/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmEngagementScoreLevelService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmEngagementLevelDto;
import org.hiylo.scrm.entity.ScrmEngagementLevelEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmEngagementLevelRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * SCRM 客户互动活跃等级管理服务 (等级管理子域)。
 * <p>
 * 承载互动活跃度等级增删改查 / 启用禁用, 以及按分数确定活跃等级 (determineLevel,
 * 含无自定义等级时的默认阈值映射)。共享常量与通用统计工具取自
 * {@link ScrmEngagementScoreRuleService}。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmEngagementScoreLevelService {

    /** 互动等级数据访问层 */
    private final ScrmEngagementLevelRepository levelRepository;

    /**
     * 创建互动活跃度等级。
     *
     * @param dto 等级参数
     * @return 创建后的等级
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmEngagementLevelDto createLevel(ScrmEngagementLevelDto dto) throws ScrmException {
        validateLevelDto(dto, false);
        ScrmEngagementLevelEntity entity = new ScrmEngagementLevelEntity();
        entity.setLevelName(dto.getLevelName());
        entity.setLevelCode(dto.getLevelCode());
        entity.setMinScore(dto.getMinScore());
        entity.setMaxScore(dto.getMaxScore());
        entity.setColor(dto.getColor());
        entity.setDescription(dto.getDescription());
        entity.setRecommendedAction(dto.getRecommendedAction());
        entity.setPriority(dto.getPriority() != null ? dto.getPriority()
                : ScrmEngagementScoreRuleService.DEFAULT_PRIORITY);
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled()
                : ScrmEngagementScoreRuleService.DEFAULT_ENABLED);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = levelRepository.save(entity);
        log.info("创建互动等级: id={}, levelName={}, levelCode={}",
                entity.getId(), entity.getLevelName(), entity.getLevelCode());
        return toLevelDto(entity);
    }

    /**
     * 更新互动等级（字段非空才覆盖）。
     *
     * @param id  等级 ID
     * @param dto 等级参数
     * @return 更新后的等级
     * @throws ScrmException 等级不存在 / 参数非法
     */
    @Transactional
    public ScrmEngagementLevelDto updateLevel(Long id, ScrmEngagementLevelDto dto) throws ScrmException {
        ScrmEngagementLevelEntity entity = findLevelOrThrow(id);
        validateLevelDto(dto, true);
        if (dto.getLevelName() != null) entity.setLevelName(dto.getLevelName());
        if (dto.getLevelCode() != null) entity.setLevelCode(dto.getLevelCode());
        if (dto.getMinScore() != null) entity.setMinScore(dto.getMinScore());
        if (dto.getMaxScore() != null) entity.setMaxScore(dto.getMaxScore());
        if (dto.getColor() != null) entity.setColor(dto.getColor());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getRecommendedAction() != null) entity.setRecommendedAction(dto.getRecommendedAction());
        if (dto.getPriority() != null) entity.setPriority(dto.getPriority());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = levelRepository.save(entity);
        log.info("更新互动等级: id={}, levelName={}", entity.getId(), entity.getLevelName());
        return toLevelDto(entity);
    }

    /**
     * 删除互动等级。
     *
     * @param id 等级 ID
     * @throws ScrmException 等级不存在
     */
    @Transactional
    public void deleteLevel(Long id) throws ScrmException {
        ScrmEngagementLevelEntity entity = findLevelOrThrow(id);
        levelRepository.delete(entity);
        log.info("删除互动等级: id={}, levelName={}", id, entity.getLevelName());
    }

    /**
     * 查询等级详情。
     *
     * @param id 等级 ID
     * @return 等级 DTO
     * @throws ScrmException 等级不存在
     */
    @Transactional(readOnly = true)
    public ScrmEngagementLevelDto getLevel(Long id) throws ScrmException {
        return toLevelDto(findLevelOrThrow(id));
    }

    /**
     * 分页查询互动等级, 支持按启用状态过滤。
     *
     * @param enabled  启用状态过滤（可空）
     * @param pageable 分页参数
     * @return 等级分页结果 (按 priority DESC, createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmEngagementLevelDto> listLevels(Boolean enabled, Pageable pageable) {
        Specification<ScrmEngagementLevelEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            query.orderBy(cb.desc(root.get("priority")), cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable sorted = pageable.getSort().isSorted() ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "priority"));
        return levelRepository.findAll(spec, sorted).map(this::toLevelDto);
    }

    /**
     * 启用等级。
     *
     * @param id 等级 ID
     * @return 更新后的等级
     * @throws ScrmException 等级不存在
     */
    @Transactional
    public ScrmEngagementLevelDto enableLevel(Long id) throws ScrmException {
        ScrmEngagementLevelEntity entity = findLevelOrThrow(id);
        entity.setEnabled(true);
        entity = levelRepository.save(entity);
        log.info("启用互动等级: id={}, levelName={}", id, entity.getLevelName());
        return toLevelDto(entity);
    }

    /**
     * 禁用等级。
     *
     * @param id 等级 ID
     * @return 更新后的等级
     * @throws ScrmException 等级不存在
     */
    @Transactional
    public ScrmEngagementLevelDto disableLevel(Long id) throws ScrmException {
        ScrmEngagementLevelEntity entity = findLevelOrThrow(id);
        entity.setEnabled(false);
        entity = levelRepository.save(entity);
        log.info("禁用互动等级: id={}, levelName={}", id, entity.getLevelName());
        return toLevelDto(entity);
    }

    /**
     * 根据分数确定活跃等级。
     * <p>加载账号下启用等级并按 priority 倒序, 返回首个 minScore <= score 且 (maxScore 为空或
     * score < maxScore) 的等级。无匹配等级时返回 INACTIVE。</p>
     *
     * @param score 当前分
     * @return 等级编码
     */
    @Transactional(readOnly = true)
    public String determineLevel(double score) {
        List<ScrmEngagementLevelEntity> levels = levelRepository.findByEnabledTrue();
        if (levels.isEmpty()) {
            // 无自定义等级时使用默认阈值
            return determineDefaultLevel(score);
        }
        return levels.stream()
                .sorted(Comparator.comparingInt(
                        (ScrmEngagementLevelEntity l) -> l.getPriority() != null ? l.getPriority() : 0).reversed())
                .filter(l -> score >= l.getMinScore() && (l.getMaxScore() == null || score < l.getMaxScore()))
                .map(ScrmEngagementLevelEntity::getLevelCode)
                .findFirst()
                .orElse(ScrmEngagementScoreRuleService.LEVEL_INACTIVE);
    }

    /**
     * 校验等级参数。
     *
     * @param dto     等级参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateLevelDto(ScrmEngagementLevelDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("等级参数不能为空");
        }
        if (dto.getLevelName() == null || dto.getLevelName().isBlank()) {
            if (!partial) {
                throw ScrmException.badRequest("等级名称不能为空");
            }
        }
        if (dto.getLevelCode() == null || dto.getLevelCode().isBlank()) {
            if (!partial) {
                throw ScrmException.badRequest("等级编码不能为空");
            }
        }
        if (dto.getMinScore() == null && !partial) {
            throw ScrmException.badRequest("最低分不能为空");
        }
    }

    /**
     * 无自定义等级时的默认等级判定。
     *
     * @param score 当前分
     * @return 默认等级编码
     */
    private String determineDefaultLevel(double score) {
        if (score >= 100) {
            return ScrmEngagementScoreRuleService.LEVEL_VERY_HIGH;
        } else if (score >= 50) {
            return ScrmEngagementScoreRuleService.LEVEL_HIGH;
        } else if (score >= 20) {
            return ScrmEngagementScoreRuleService.LEVEL_MEDIUM;
        } else if (score > 0) {
            return ScrmEngagementScoreRuleService.LEVEL_LOW;
        }
        return ScrmEngagementScoreRuleService.LEVEL_INACTIVE;
    }

    /**
     * 按主键查询等级并校验账号归属, 不存在或越权抛异常。
     *
     * @param id 等级 ID
     * @return 等级实体
     * @throws ScrmException 等级不存在
     */
    private ScrmEngagementLevelEntity findLevelOrThrow(Long id) throws ScrmException {
        ScrmEngagementLevelEntity entity = levelRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "互动等级不存在: id=" + id));
        return entity;
    }

    /**
     * 等级实体转 DTO。
     */
    private ScrmEngagementLevelDto toLevelDto(ScrmEngagementLevelEntity entity) {
        ScrmEngagementLevelDto dto = new ScrmEngagementLevelDto();
        dto.setId(entity.getId());
        dto.setLevelName(entity.getLevelName());
        dto.setLevelCode(entity.getLevelCode());
        dto.setMinScore(entity.getMinScore());
        dto.setMaxScore(entity.getMaxScore());
        dto.setColor(entity.getColor());
        dto.setDescription(entity.getDescription());
        dto.setRecommendedAction(entity.getRecommendedAction());
        dto.setPriority(entity.getPriority());
        dto.setEnabled(entity.getEnabled());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}