/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLeadDimensionService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmLeadDimensionDto;
import org.hiylo.scrm.entity.ScrmLeadDimensionEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmLeadDimensionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SCRM 销售线索评分维度管理服务。
 * <p>
 * 承载评分维度管理子域: 维度增删改查与按编码查询、分页列表、启用 / 禁用。
 * 同时托管维度共享常量与辅助方法 (维度类别 / 合法操作符 / 默认值、按主键查找、参数校验),
 * 供评分计算兄弟类以 package 级访问复用。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmLeadDimensionService {

    // ==================== 维度类别常量 ====================

    /** 维度类别: 人口统计 */
    private static final String CATEGORY_DEMOGRAPHIC = "DEMOGRAPHIC";
    /** 维度类别: 行为 */
    private static final String CATEGORY_BEHAVIORAL = "BEHAVIORAL";
    /** 维度类别: 互动 */
    private static final String CATEGORY_ENGAGEMENT = "ENGAGEMENT";
    /** 维度类别: 企业属性 */
    private static final String CATEGORY_FIRMOGRAPHIC = "FIRMOGRAPHIC";
    /** 维度类别: 技术 */
    private static final String CATEGORY_TECHNOGRAPHIC = "TECHNOGRAPHIC";
    /** 维度类别: 需求 */
    private static final String CATEGORY_NEED_BASED = "NEED_BASED";
    /** 维度类别: 时机 */
    private static final String CATEGORY_TIMING = "TIMING";

    // ==================== 默认值常量 ====================

    /** 默认维度权重 */
    static final double DEFAULT_WEIGHT = 1.0;
    /** 默认维度最高分 */
    static final int DEFAULT_MAX_SCORE = 20;
    /** 默认启用状态 */
    private static final boolean DEFAULT_ENABLED = true;
    /** 默认使用次数初值 */
    private static final int DEFAULT_USAGE_COUNT = 0;

    /** 合法的维度类别 */
    private static final List<String> VALID_DIMENSION_CATEGORIES = List.of(
            CATEGORY_DEMOGRAPHIC, CATEGORY_BEHAVIORAL, CATEGORY_ENGAGEMENT,
            CATEGORY_FIRMOGRAPHIC, CATEGORY_TECHNOGRAPHIC, CATEGORY_NEED_BASED, CATEGORY_TIMING);

    /** 合法的操作符 */
    private static final List<String> VALID_OPERATORS = List.of(
            "eq", "ne", "gt", "lt", "ge", "le", "between", "in", "contains");

    /** 评分维度数据访问层 */
    private final ScrmLeadDimensionRepository dimensionRepository;

    /** JSON 映射器 (评分规则 JSON 校验) */
    private final ObjectMapper objectMapper;

    /** 评分模型管理子域服务 (共享分页排序兜底) */
    private final ScrmLeadScoringModelService modelService;

    // ============================================================
    // 维度管理
    // ============================================================

    /**
     * 创建评分维度。
     * <p>校验 dimensionCategory / scoringRules 合法性与 dimensionCode 唯一性后写入账号 ID 持久化,
     * defaultWeight / defaultMaxScore / enabled / usageCount 缺省时填默认值。</p>
     *
     * @param dto 维度参数
     * @return 创建后的维度
     * @throws ScrmException 参数非法 / dimensionCode 重复
     */
    @Transactional
    public ScrmLeadDimensionEntity createDimension(ScrmLeadDimensionDto dto) throws ScrmException {
        validateDimensionDto(dto, false);
        if (dimensionRepository.findByDimensionCode(dto.getDimensionCode()).isPresent()) {
            throw ScrmException.conflict("维度编码已存在: " + dto.getDimensionCode());
        }
        ScrmLeadDimensionEntity entity = new ScrmLeadDimensionEntity();
        entity.setDimensionName(dto.getDimensionName());
        entity.setDimensionCode(dto.getDimensionCode());
        entity.setDescription(dto.getDescription());
        entity.setDimensionCategory(dto.getDimensionCategory());
        entity.setDefaultWeight(dto.getDefaultWeight() != null ? dto.getDefaultWeight() : DEFAULT_WEIGHT);
        entity.setDefaultMaxScore(dto.getDefaultMaxScore() != null ? dto.getDefaultMaxScore() : DEFAULT_MAX_SCORE);
        entity.setScoringRules(dto.getScoringRules());
        entity.setApplicableFields(dto.getApplicableFields());
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : DEFAULT_ENABLED);
        entity.setUsageCount(DEFAULT_USAGE_COUNT);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = dimensionRepository.save(entity);
        log.info("创建评分维度: id={}, dimensionName={}, dimensionCode={}, category={}",
                entity.getId(), entity.getDimensionName(), entity.getDimensionCode(), entity.getDimensionCategory());
        return entity;
    }

    /**
     * 更新评分维度（字段非空才覆盖）。
     *
     * @param id  维度 ID
     * @param dto 维度参数
     * @return 更新后的维度
     * @throws ScrmException 维度不存在 / 参数非法 / dimensionCode 重复
     */
    @Transactional
    public ScrmLeadDimensionEntity updateDimension(Long id, ScrmLeadDimensionDto dto) throws ScrmException {
        ScrmLeadDimensionEntity entity = findDimensionOrThrow(id);
        validateDimensionDto(dto, true);
        if (dto.getDimensionCode() != null && !dto.getDimensionCode().equals(entity.getDimensionCode()) && dimensionRepository.findByDimensionCode(dto.getDimensionCode()).isPresent()) {
            throw ScrmException.conflict("维度编码已存在: " + dto.getDimensionCode());
        }
        if (dto.getDimensionName() != null) entity.setDimensionName(dto.getDimensionName());
        if (dto.getDimensionCode() != null) entity.setDimensionCode(dto.getDimensionCode());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getDimensionCategory() != null) entity.setDimensionCategory(dto.getDimensionCategory());
        if (dto.getDefaultWeight() != null) entity.setDefaultWeight(dto.getDefaultWeight());
        if (dto.getDefaultMaxScore() != null) entity.setDefaultMaxScore(dto.getDefaultMaxScore());
        if (dto.getScoringRules() != null) entity.setScoringRules(dto.getScoringRules());
        if (dto.getApplicableFields() != null) entity.setApplicableFields(dto.getApplicableFields());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = dimensionRepository.save(entity);
        log.info("更新评分维度: id={}, dimensionName={}", entity.getId(), entity.getDimensionName());
        return entity;
    }

    /**
     * 删除评分维度。
     *
     * @param id 维度 ID
     * @throws ScrmException 维度不存在
     */
    @Transactional
    public void deleteDimension(Long id) throws ScrmException {
        ScrmLeadDimensionEntity entity = findDimensionOrThrow(id);
        dimensionRepository.delete(entity);
        log.info("删除评分维度: id={}, dimensionName={}", id, entity.getDimensionName());
    }

    /**
     * 查询维度详情。
     *
     * @param id 维度 ID
     * @return 维度实体
     * @throws ScrmException 维度不存在
     */
    @Transactional(readOnly = true)
    public ScrmLeadDimensionEntity getDimension(Long id) throws ScrmException {
        return findDimensionOrThrow(id);
    }

    /**
     * 按维度编码查询维度。
     *
     * @param code 维度编码
     * @return 维度实体
     * @throws ScrmException 维度不存在
     */
    @Transactional(readOnly = true)
    public ScrmLeadDimensionEntity getDimensionByCode(String code) throws ScrmException {
        if (code == null || code.isBlank()) {
            throw ScrmException.badRequest("维度编码不能为空");
        }
        return dimensionRepository.findByDimensionCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "评分维度不存在: code=" + code));
    }

    /**
     * 分页查询维度, 支持按维度类别 / 启用状态 / 关键字过滤。
     *
     * @param dimensionCategory 维度类别过滤（可空）
     * @param enabled           启用状态过滤（可空）
     * @param keyword           维度名称关键字模糊匹配（可空）
     * @param pageable          分页参数
     * @return 维度分页结果 (按 createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmLeadDimensionEntity> listDimensions(String dimensionCategory, Boolean enabled,
                                                         String keyword, Pageable pageable) {
        Specification<ScrmLeadDimensionEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (dimensionCategory != null && !dimensionCategory.isBlank()) {
                predicates.add(cb.equal(root.get("dimensionCategory"), dimensionCategory));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(root.get("dimensionName"), "%" + keyword + "%"));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return dimensionRepository.findAll(spec, modelService.ensureSort(pageable, "createTime"));
    }

    /**
     * 启用维度。
     *
     * @param id 维度 ID
     * @return 更新后的维度
     * @throws ScrmException 维度不存在
     */
    @Transactional
    public ScrmLeadDimensionEntity enableDimension(Long id) throws ScrmException {
        ScrmLeadDimensionEntity entity = findDimensionOrThrow(id);
        entity.setEnabled(true);
        entity = dimensionRepository.save(entity);
        log.info("启用评分维度: id={}, dimensionName={}", id, entity.getDimensionName());
        return entity;
    }

    /**
     * 禁用维度。
     *
     * @param id 维度 ID
     * @return 更新后的维度
     * @throws ScrmException 维度不存在
     */
    @Transactional
    public ScrmLeadDimensionEntity disableDimension(Long id) throws ScrmException {
        ScrmLeadDimensionEntity entity = findDimensionOrThrow(id);
        entity.setEnabled(false);
        entity = dimensionRepository.save(entity);
        log.info("禁用评分维度: id={}, dimensionName={}", id, entity.getDimensionName());
        return entity;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 校验评分维度参数。
     *
     * @param dto     维度参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateDimensionDto(ScrmLeadDimensionDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("维度参数不能为空");
        }
        if (dto.getDimensionName() != null) {
            if (dto.getDimensionName().isBlank()) {
                throw ScrmException.badRequest("维度名称不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("维度名称不能为空");
        }
        if (dto.getDimensionCode() != null) {
            if (dto.getDimensionCode().isBlank()) {
                throw ScrmException.badRequest("维度编码不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("维度编码不能为空");
        }
        if (dto.getDimensionCategory() != null && !VALID_DIMENSION_CATEGORIES.contains(dto.getDimensionCategory())) {
            throw ScrmException.badRequest(
                    "维度类别非法: " + dto.getDimensionCategory() + ", 仅支持 " + VALID_DIMENSION_CATEGORIES);
        }
        if (dto.getScoringRules() != null) {
            if (dto.getScoringRules().isBlank()) {
                throw ScrmException.badRequest("评分规则不能为空");
            }
            try {
                List<Map<String, Object>> parsed = objectMapper.readValue(
                        dto.getScoringRules(), new TypeReference<List<Map<String, Object>>>() {});
                for (Map<String, Object> rule : parsed) {
                    String operator = (String) rule.get("operator");
                    if (operator != null && !VALID_OPERATORS.contains(operator)) {
                        throw ScrmException.badRequest(
                                "操作符非法: " + operator + ", 仅支持 " + VALID_OPERATORS);
                    }
                }
            } catch (ScrmException e) {
                throw e;
            } catch (Exception e) {
                throw ScrmException.badRequest("评分规则 JSON 解析失败: " + e.getMessage());
            }
        } else if (!partial) {
            throw ScrmException.badRequest("评分规则不能为空");
        }
    }

    /**
     * 按主键查询维度, 不存在抛异常, 并校验账号归属。
     *
     * @param id 维度 ID
     * @return 维度实体
     * @throws ScrmException 维度不存在
     */
    private ScrmLeadDimensionEntity findDimensionOrThrow(Long id) throws ScrmException {
        ScrmLeadDimensionEntity entity = dimensionRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "评分维度不存在: id=" + id));
        return entity;
    }
}