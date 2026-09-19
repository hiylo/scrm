/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmHealthScoreModelService.java
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

import org.hiylo.scrm.entity.ScrmHealthScoreModelEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmHealthScoreModelRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRM 客户健康度模型管理服务。
 * <p>
 * 承载健康度模型管理子域: 模型 CRUD / 发布与取消发布 / 设置默认 / 复制 / 验证模型配置。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmHealthScoreModelService {

    // ==================== 评分类型常量 ====================

    /** 评分类型: 简单求和 */
    private static final String SCORING_SIMPLE = "SIMPLE";
    /** 评分类型: 加权 */
    private static final String SCORING_WEIGHTED = "WEIGHTED";
    /** 评分类型: 动态 */
    private static final String SCORING_DYNAMIC = "DYNAMIC";

    // ==================== 健康等级常量 ====================

    /** 健康等级: 危急 */
    private static final String LEVEL_CRITICAL = "CRITICAL";
    /** 健康等级: 风险 */
    private static final String LEVEL_AT_RISK = "AT_RISK";
    /** 健康等级: 中性 */
    private static final String LEVEL_NEUTRAL = "NEUTRAL";
    /** 健康等级: 健康 */
    private static final String LEVEL_HEALTHY = "HEALTHY";
    /** 健康等级: 优秀 */
    private static final String LEVEL_EXCELLENT = "EXCELLENT";

    // ==================== 更新频率常量 ====================

    /** 更新频率: 实时 */
    private static final String FREQ_REALTIME = "REALTIME";
    /** 更新频率: 每日 */
    private static final String FREQ_DAILY = "DAILY";
    /** 更新频率: 每周 */
    private static final String FREQ_WEEKLY = "WEEKLY";
    /** 更新频率: 每月 */
    private static final String FREQ_MONTHLY = "MONTHLY";

    // ==================== 默认值常量 ====================

    /** 默认评分类型 */
    private static final String DEFAULT_SCORING_TYPE = SCORING_WEIGHTED;
    /** 默认总分上限 */
    private static final int DEFAULT_TOTAL_MAX_SCORE = 100;
    /** 默认模型版本号 */
    private static final int DEFAULT_VERSION_NO = 1;
    /** 默认应用次数初值 */
    private static final int DEFAULT_APPLIED_COUNT = 0;
    /** 默认是否默认模型 */
    private static final boolean DEFAULT_IS_DEFAULT = false;
    /** 默认是否已发布 */
    private static final boolean DEFAULT_IS_PUBLISHED = false;
    /** 默认更新频率 */
    private static final String DEFAULT_UPDATE_FREQUENCY = FREQ_DAILY;

    /** 合法的评分类型 */
    private static final List<String> VALID_SCORING_TYPES = List.of(
            SCORING_SIMPLE, SCORING_WEIGHTED, SCORING_DYNAMIC);

    /** 合法的更新频率 */
    private static final List<String> VALID_UPDATE_FREQUENCIES = List.of(
            FREQ_REALTIME, FREQ_DAILY, FREQ_WEEKLY, FREQ_MONTHLY);

    /** 合法的健康等级 */
    private static final List<String> VALID_HEALTH_LEVELS = List.of(
            LEVEL_CRITICAL, LEVEL_AT_RISK, LEVEL_NEUTRAL, LEVEL_HEALTHY, LEVEL_EXCELLENT);

    /** 健康度模型数据访问层 */
    private final ScrmHealthScoreModelRepository modelRepository;

    /** JSON 解析器 (解析 metrics / healthThresholds) */
    private final ObjectMapper objectMapper;

    /**
     * 创建健康度模型。
     * <p>校验 scoringType / metrics 合法性与 modelCode 唯一性后写入账号 ID 持久化,
     * scoringType / totalMaxScore / versionNo / isDefault / isPublished / appliedCount /
     * updateFrequency 缺省时填默认值。</p>
     *
     * @param dto 模型参数
     * @return 创建后的模型
     * @throws ScrmException 参数非法 / modelCode 重复
     */
    @Transactional
    public ScrmHealthScoreModelEntity createModel(org.hiylo.scrm.dto.ScrmHealthScoreModelDto dto)
            throws ScrmException {
        validateModelDto(dto, false);
        if (modelRepository.findByModelCode(dto.getModelCode()).isPresent()) {
            throw ScrmException.conflict("模型编码已存在: " + dto.getModelCode());
        }
        ScrmHealthScoreModelEntity entity = new ScrmHealthScoreModelEntity();
        entity.setModelName(dto.getModelName());
        entity.setModelCode(dto.getModelCode());
        entity.setDescription(dto.getDescription());
        entity.setApplicableSegment(dto.getApplicableSegment());
        entity.setMetrics(dto.getMetrics());
        entity.setScoringType(dto.getScoringType() != null ? dto.getScoringType() : DEFAULT_SCORING_TYPE);
        entity.setTotalMaxScore(dto.getTotalMaxScore() != null ? dto.getTotalMaxScore() : DEFAULT_TOTAL_MAX_SCORE);
        entity.setHealthThresholds(dto.getHealthThresholds());
        entity.setIsDefault(dto.getIsDefault() != null ? dto.getIsDefault() : DEFAULT_IS_DEFAULT);
        entity.setIsPublished(dto.getIsPublished() != null ? dto.getIsPublished() : DEFAULT_IS_PUBLISHED);
        entity.setVersionNo(dto.getVersionNo() != null ? dto.getVersionNo() : DEFAULT_VERSION_NO);
        entity.setAppliedCount(DEFAULT_APPLIED_COUNT);
        entity.setUpdateFrequency(
                dto.getUpdateFrequency() != null ? dto.getUpdateFrequency() : DEFAULT_UPDATE_FREQUENCY);
        entity.setCreatedBy(dto.getCreatedBy());
        // 若设为默认, 清理旧默认
        if (Boolean.TRUE.equals(entity.getIsDefault())) {
            clearExistingDefault();
        }
        entity = modelRepository.save(entity);
        log.info("创建健康度模型: id={}, modelName={}, modelCode={}, scoringType={}",
                entity.getId(), entity.getModelName(), entity.getModelCode(), entity.getScoringType());
        return entity;
    }

    /**
     * 更新健康度模型（字段非空才覆盖）。
     *
     * @param id  模型 ID
     * @param dto 模型参数
     * @return 更新后的模型
     * @throws ScrmException 模型不存在 / 参数非法 / modelCode 重复
     */
    @Transactional
    public ScrmHealthScoreModelEntity updateModel(Long id,
                                                  org.hiylo.scrm.dto.ScrmHealthScoreModelDto dto)
            throws ScrmException {
        ScrmHealthScoreModelEntity entity = findModelOrThrow(id);
        validateModelDto(dto, true);
        if (dto.getModelCode() != null && !dto.getModelCode().equals(entity.getModelCode()) && modelRepository.findByModelCode(dto.getModelCode()).isPresent()) {
            throw ScrmException.conflict("模型编码已存在: " + dto.getModelCode());
        }
        if (dto.getModelName() != null) entity.setModelName(dto.getModelName());
        if (dto.getModelCode() != null) entity.setModelCode(dto.getModelCode());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getApplicableSegment() != null) entity.setApplicableSegment(dto.getApplicableSegment());
        if (dto.getMetrics() != null) entity.setMetrics(dto.getMetrics());
        if (dto.getScoringType() != null) entity.setScoringType(dto.getScoringType());
        if (dto.getTotalMaxScore() != null) entity.setTotalMaxScore(dto.getTotalMaxScore());
        if (dto.getHealthThresholds() != null) entity.setHealthThresholds(dto.getHealthThresholds());
        if (dto.getVersionNo() != null) entity.setVersionNo(dto.getVersionNo());
        if (dto.getUpdateFrequency() != null) entity.setUpdateFrequency(dto.getUpdateFrequency());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        if (dto.getIsDefault() != null) {
            if (Boolean.TRUE.equals(dto.getIsDefault()) && !Boolean.TRUE.equals(entity.getIsDefault())) {
                clearExistingDefault();
            }
            entity.setIsDefault(dto.getIsDefault());
        }
        if (dto.getIsPublished() != null) {
            entity.setIsPublished(dto.getIsPublished());
        }
        entity = modelRepository.save(entity);
        log.info("更新健康度模型: id={}, modelName={}", entity.getId(), entity.getModelName());
        return entity;
    }

    /**
     * 删除健康度模型。
     *
     * @param id 模型 ID
     * @throws ScrmException 模型不存在
     */
    @Transactional
    public void deleteModel(Long id) throws ScrmException {
        ScrmHealthScoreModelEntity entity = findModelOrThrow(id);
        modelRepository.delete(entity);
        log.info("删除健康度模型: id={}, modelName={}", id, entity.getModelName());
    }

    /**
     * 查询模型详情。
     *
     * @param id 模型 ID
     * @return 模型实体
     * @throws ScrmException 模型不存在
     */
    @Transactional(readOnly = true)
    public ScrmHealthScoreModelEntity getModel(Long id) throws ScrmException {
        return findModelOrThrow(id);
    }

    /**
     * 按模型编码查询模型。
     *
     * @param code 模型编码
     * @return 模型实体
     * @throws ScrmException 模型不存在
     */
    @Transactional(readOnly = true)
    public ScrmHealthScoreModelEntity getModelByCode(String code) throws ScrmException {
        if (code == null || code.isBlank()) {
            throw ScrmException.badRequest("模型编码不能为空");
        }
        return modelRepository.findByModelCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "健康度模型不存在: code=" + code));
    }

    /**
     * 分页查询模型, 支持按发布状态 / 关键字过滤。
     *
     * @param isPublished 发布状态过滤（可空）
     * @param keyword     模型名称关键字模糊匹配（可空）
     * @param pageable    分页参数
     * @return 模型分页结果 (按 createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmHealthScoreModelEntity> listModels(Boolean isPublished, String keyword, Pageable pageable) {
        Specification<ScrmHealthScoreModelEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (isPublished != null) {
                predicates.add(cb.equal(root.get("isPublished"), isPublished));
            }
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(root.get("modelName"), "%" + keyword + "%"));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return modelRepository.findAll(spec, ensureSort(pageable, "createTime"));
    }

    /**
     * 发布模型。
     *
     * @param id 模型 ID
     * @return 更新后的模型
     * @throws ScrmException 模型不存在
     */
    @Transactional
    public ScrmHealthScoreModelEntity publishModel(Long id) throws ScrmException {
        ScrmHealthScoreModelEntity entity = findModelOrThrow(id);
        entity.setIsPublished(true);
        entity = modelRepository.save(entity);
        log.info("发布健康度模型: id={}, modelName={}", id, entity.getModelName());
        return entity;
    }

    /**
     * 取消发布模型。
     *
     * @param id 模型 ID
     * @return 更新后的模型
     * @throws ScrmException 模型不存在
     */
    @Transactional
    public ScrmHealthScoreModelEntity unpublishModel(Long id) throws ScrmException {
        ScrmHealthScoreModelEntity entity = findModelOrThrow(id);
        entity.setIsPublished(false);
        entity = modelRepository.save(entity);
        log.info("取消发布健康度模型: id={}, modelName={}", id, entity.getModelName());
        return entity;
    }

    /**
     * 设置为默认模型 (清理旧默认)。
     *
     * @param id 模型 ID
     * @return 更新后的模型
     * @throws ScrmException 模型不存在
     */
    @Transactional
    public ScrmHealthScoreModelEntity setDefault(Long id) throws ScrmException {
        ScrmHealthScoreModelEntity entity = findModelOrThrow(id);
        clearExistingDefault();
        entity.setIsDefault(true);
        entity = modelRepository.save(entity);
        log.info("设置默认健康度模型: id={}, modelName={}", id, entity.getModelName());
        return entity;
    }

    /**
     * 复制模型 (深拷贝模型配置, 新模型默认未发布且非默认, modelCode 加 _copy 后缀)。
     *
     * @param id 源模型 ID
     * @return 复制后的新模型
     * @throws ScrmException 源模型不存在 / modelCode 冲突
     */
    @Transactional
    public ScrmHealthScoreModelEntity copyModel(Long id) throws ScrmException {
        ScrmHealthScoreModelEntity source = findModelOrThrow(id);
        String newCode = source.getModelCode() + "_copy";
        if (modelRepository.findByModelCode(newCode).isPresent()) {
            throw ScrmException.conflict("模型编码已存在: " + newCode);
        }
        ScrmHealthScoreModelEntity copy = new ScrmHealthScoreModelEntity();
        copy.setModelName(source.getModelName() + " (副本)");
        copy.setModelCode(newCode);
        copy.setDescription(source.getDescription());
        copy.setApplicableSegment(source.getApplicableSegment());
        copy.setMetrics(source.getMetrics());
        copy.setScoringType(source.getScoringType());
        copy.setTotalMaxScore(source.getTotalMaxScore());
        copy.setHealthThresholds(source.getHealthThresholds());
        copy.setIsDefault(DEFAULT_IS_DEFAULT);
        copy.setIsPublished(DEFAULT_IS_PUBLISHED);
        copy.setVersionNo(DEFAULT_VERSION_NO);
        copy.setAppliedCount(DEFAULT_APPLIED_COUNT);
        copy.setUpdateFrequency(source.getUpdateFrequency());
        copy.setCreatedBy(source.getCreatedBy());
        copy = modelRepository.save(copy);
        log.info("复制健康度模型: sourceId={}, newId={}, newCode={}", id, copy.getId(), newCode);
        return copy;
    }

    /**
     * 验证模型配置 (校验 metrics JSON 合法性 / 指标权重 / maxScore 总和等)。
     *
     * @param id 模型 ID
     * @return 验证结果 Map: {valid, issues, metricCount, totalWeight, totalMaxScore}
     * @throws ScrmException 模型不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> validateModel(Long id) throws ScrmException {
        ScrmHealthScoreModelEntity model = findModelOrThrow(id);
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> issues = new ArrayList<>();
        List<Map<String, Object>> metrics = parseJsonArray(model.getMetrics());
        double totalWeight = 0;
        double totalMaxScore = 0;
        for (int i = 0; i < metrics.size(); i++) {
            Map<String, Object> m = metrics.get(i);
            String metricCode = (String) m.get("metricCode");
            if (metricCode == null || metricCode.isBlank()) {
                issues.add("指标[" + i + "] 缺少 metricCode");
            }
            double weight = toDouble(m.get("weight"));
            if (weight < 0) {
                issues.add("指标[" + metricCode + "] 权重不能为负: " + weight);
            }
            totalWeight += weight;
            double maxScore = toDouble(m.get("maxScore"));
            if (maxScore <= 0) {
                issues.add("指标[" + metricCode + "] maxScore 必须为正: " + maxScore);
            }
            totalMaxScore += maxScore;
        }
        if (metrics.isEmpty()) {
            issues.add("模型未配置任何指标");
        }
        if (!VALID_SCORING_TYPES.contains(model.getScoringType())) {
            issues.add("评分类型非法: " + model.getScoringType());
        }
        if (model.getTotalMaxScore() == null || model.getTotalMaxScore() <= 0) {
            issues.add("总分上限必须为正");
        }
        if (model.getHealthThresholds() != null && !model.getHealthThresholds().isBlank()) {
            try {
                List<Map<String, Object>> thresholds = parseJsonArray(model.getHealthThresholds());
                for (Map<String, Object> t : thresholds) {
                    String level = (String) t.get("level");
                    if (level != null && !VALID_HEALTH_LEVELS.contains(level)) {
                        issues.add("健康等级非法: " + level);
                    }
                }
            } catch (Exception e) {
                issues.add("健康阈值 JSON 解析失败: " + e.getMessage());
            }
        }
        result.put("valid", issues.isEmpty());
        result.put("issues", issues);
        result.put("metricCount", metrics.size());
        result.put("totalWeight", round2(totalWeight));
        result.put("totalMaxScore", round2(totalMaxScore));
        return result;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 校验健康度模型参数。
     *
     * @param dto     模型参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateModelDto(org.hiylo.scrm.dto.ScrmHealthScoreModelDto dto, boolean partial)
            throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("模型参数不能为空");
        }
        if (dto.getModelName() != null) {
            if (dto.getModelName().isBlank()) {
                throw ScrmException.badRequest("模型名称不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("模型名称不能为空");
        }
        if (dto.getModelCode() != null) {
            if (dto.getModelCode().isBlank()) {
                throw ScrmException.badRequest("模型编码不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("模型编码不能为空");
        }
        if (dto.getScoringType() != null && !VALID_SCORING_TYPES.contains(dto.getScoringType())) {
            throw ScrmException.badRequest(
                    "评分类型非法: " + dto.getScoringType() + ", 仅支持 " + VALID_SCORING_TYPES);
        }
        if (dto.getUpdateFrequency() != null && !VALID_UPDATE_FREQUENCIES.contains(dto.getUpdateFrequency())) {
            throw ScrmException.badRequest(
                    "更新频率非法: " + dto.getUpdateFrequency() + ", 仅支持 " + VALID_UPDATE_FREQUENCIES);
        }
        if (dto.getMetrics() != null) {
            if (dto.getMetrics().isBlank()) {
                throw ScrmException.badRequest("指标配置不能为空");
            }
            try {
                objectMapper.readTree(dto.getMetrics());
            } catch (Exception e) {
                throw ScrmException.badRequest("指标配置 JSON 解析失败: " + e.getMessage());
            }
        } else if (!partial) {
            throw ScrmException.badRequest("指标配置不能为空");
        }
        if (dto.getHealthThresholds() != null && !dto.getHealthThresholds().isBlank()) {
            try {
                objectMapper.readTree(dto.getHealthThresholds());
            } catch (Exception e) {
                throw ScrmException.badRequest("健康阈值 JSON 解析失败: " + e.getMessage());
            }
        }
    }

    /**
     * 清理账号下已有默认模型 (设为非默认)。
     *
     */
    private void clearExistingDefault() {
        modelRepository.findByIsDefaultTrue().ifPresent(existing -> {
            existing.setIsDefault(false);
            modelRepository.save(existing);
        });
    }

    /**
     * 解析 JSON 数组为 List。
     *
     * @param json JSON 字符串
     * @return List, 解析失败返回空列表
     */
    private List<Map<String, Object>> parseJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("JSON 数组解析失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 将对象转换为 double 数值。
     *
     * @param obj 对象
     * @return double 值, 不可转换时返回 0
     */
    private double toDouble(Object obj) {
        if (obj == null) {
            return 0;
        }
        if (obj instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(obj.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 保留两位小数。
     *
     * @param value 原始值
     * @return 保留两位小数后的值
     */
    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * 确保分页参数带默认排序 (按指定字段倒序)。
     *
     * @param pageable 分页参数
     * @param field    默认排序字段
     * @return 处理后的分页参数
     */
    private Pageable ensureSort(Pageable pageable, String field) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, field));
    }

    /**
     * 按主键查询模型, 不存在抛异常, 并校验账号归属。
     *
     * @param id 模型 ID
     * @return 模型实体
     * @throws ScrmException 模型不存在
     */
    private ScrmHealthScoreModelEntity findModelOrThrow(Long id) throws ScrmException {
        ScrmHealthScoreModelEntity entity = modelRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "健康度模型不存在: id=" + id));
        return entity;
    }
}