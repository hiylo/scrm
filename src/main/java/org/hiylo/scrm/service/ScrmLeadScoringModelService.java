/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLeadScoringModelService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmLeadScoringModelDto;
import org.hiylo.scrm.entity.ScrmLeadScoringModelEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmLeadScoringModelRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * SCRM 销售线索评分模型管理服务。
 * <p>
 * 承载评分模型管理子域: 模型增删改查与按编码查询、分页列表、发布 / 取消发布、设默认与复制。
 * 同时托管模型共享常量与辅助方法 (模型类型与默认值、按主键查找、参数校验、清默认、分页排序
 * 兜底), 供维度 / 评分计算 / 分配统计兄弟类以 package 级访问复用。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmLeadScoringModelService {

    // ==================== 模型类型常量 ====================

    /** 模型类型: 基于规则 */
    private static final String MODEL_RULE_BASED = "RULE_BASED";
    /** 模型类型: 基于机器学习 */
    private static final String MODEL_ML_BASED = "ML_BASED";
    /** 模型类型: 混合 */
    private static final String MODEL_HYBRID = "HYBRID";

    // ==================== 默认值常量 ====================

    /** 默认模型类型 */
    private static final String DEFAULT_MODEL_TYPE = MODEL_RULE_BASED;
    /** 默认总分上限 */
    static final int DEFAULT_TOTAL_MAX_SCORE = 100;
    /** 默认模型版本号 */
    private static final int DEFAULT_VERSION_NO = 1;
    /** 默认应用次数初值 */
    private static final int DEFAULT_APPLIED_COUNT = 0;
    /** 默认是否默认模型 */
    private static final boolean DEFAULT_IS_DEFAULT = false;
    /** 默认是否已发布 */
    private static final boolean DEFAULT_IS_PUBLISHED = false;

    /** 合法的模型类型 */
    static final List<String> VALID_MODEL_TYPES = List.of(
            MODEL_RULE_BASED, MODEL_ML_BASED, MODEL_HYBRID);

    /** 评分模型数据访问层 */
    private final ScrmLeadScoringModelRepository modelRepository;

    /** JSON 映射器 (维度与阈值 JSON 校验) */
    private final ObjectMapper objectMapper;

    // ============================================================
    // 模型管理
    // ============================================================

    /**
     * 创建评分模型。
     * <p>校验 modelType / dimensions 合法性与 modelCode 唯一性后写入账号 ID 持久化,
     * modelType / totalMaxScore / versionNo / isDefault / isPublished / appliedCount 缺省时填默认值。</p>
     *
     * @param dto 模型参数
     * @return 创建后的模型
     * @throws ScrmException 参数非法 / modelCode 重复
     */
    @Transactional
    public ScrmLeadScoringModelEntity createModel(ScrmLeadScoringModelDto dto) throws ScrmException {
        validateModelDto(dto, false);
        if (modelRepository.findByModelCode(dto.getModelCode()).isPresent()) {
            throw ScrmException.conflict("模型编码已存在: " + dto.getModelCode());
        }
        ScrmLeadScoringModelEntity entity = new ScrmLeadScoringModelEntity();
        entity.setModelName(dto.getModelName());
        entity.setModelCode(dto.getModelCode());
        entity.setDescription(dto.getDescription());
        entity.setModelType(dto.getModelType() != null ? dto.getModelType() : DEFAULT_MODEL_TYPE);
        entity.setDimensions(dto.getDimensions());
        entity.setTotalMaxScore(dto.getTotalMaxScore() != null ? dto.getTotalMaxScore() : DEFAULT_TOTAL_MAX_SCORE);
        entity.setGradeThresholds(dto.getGradeThresholds());
        entity.setIsDefault(dto.getIsDefault() != null ? dto.getIsDefault() : DEFAULT_IS_DEFAULT);
        entity.setIsPublished(dto.getIsPublished() != null ? dto.getIsPublished() : DEFAULT_IS_PUBLISHED);
        entity.setVersionNo(dto.getVersionNo() != null ? dto.getVersionNo() : DEFAULT_VERSION_NO);
        entity.setAppliedCount(DEFAULT_APPLIED_COUNT);
        entity.setCreatedBy(dto.getCreatedBy());
        // 若设为默认, 清理旧默认
        if (Boolean.TRUE.equals(entity.getIsDefault())) {
            clearExistingDefault();
        }
        entity = modelRepository.save(entity);
        log.info("创建评分模型: id={}, modelName={}, modelCode={}, modelType={}",
                entity.getId(), entity.getModelName(), entity.getModelCode(), entity.getModelType());
        return entity;
    }

    /**
     * 更新评分模型（字段非空才覆盖）。
     *
     * @param id  模型 ID
     * @param dto 模型参数
     * @return 更新后的模型
     * @throws ScrmException 模型不存在 / 参数非法 / modelCode 重复
     */
    @Transactional
    public ScrmLeadScoringModelEntity updateModel(Long id, ScrmLeadScoringModelDto dto) throws ScrmException {
        ScrmLeadScoringModelEntity entity = findModelOrThrow(id);
        validateModelDto(dto, true);
        if (dto.getModelCode() != null && !dto.getModelCode().equals(entity.getModelCode()) && modelRepository.findByModelCode(dto.getModelCode()).isPresent()) {
            throw ScrmException.conflict("模型编码已存在: " + dto.getModelCode());
        }
        if (dto.getModelName() != null) entity.setModelName(dto.getModelName());
        if (dto.getModelCode() != null) entity.setModelCode(dto.getModelCode());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getModelType() != null) entity.setModelType(dto.getModelType());
        if (dto.getDimensions() != null) entity.setDimensions(dto.getDimensions());
        if (dto.getTotalMaxScore() != null) entity.setTotalMaxScore(dto.getTotalMaxScore());
        if (dto.getGradeThresholds() != null) entity.setGradeThresholds(dto.getGradeThresholds());
        if (dto.getVersionNo() != null) entity.setVersionNo(dto.getVersionNo());
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
        log.info("更新评分模型: id={}, modelName={}", entity.getId(), entity.getModelName());
        return entity;
    }

    /**
     * 删除评分模型。
     *
     * @param id 模型 ID
     * @throws ScrmException 模型不存在
     */
    @Transactional
    public void deleteModel(Long id) throws ScrmException {
        ScrmLeadScoringModelEntity entity = findModelOrThrow(id);
        modelRepository.delete(entity);
        log.info("删除评分模型: id={}, modelName={}", id, entity.getModelName());
    }

    /**
     * 查询模型详情。
     *
     * @param id 模型 ID
     * @return 模型实体
     * @throws ScrmException 模型不存在
     */
    @Transactional(readOnly = true)
    public ScrmLeadScoringModelEntity getModel(Long id) throws ScrmException {
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
    public ScrmLeadScoringModelEntity getModelByCode(String code) throws ScrmException {
        if (code == null || code.isBlank()) {
            throw ScrmException.badRequest("模型编码不能为空");
        }
        return modelRepository.findByModelCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "评分模型不存在: code=" + code));
    }

    /**
     * 分页查询模型, 支持按模型类型 / 发布状态 / 关键字过滤。
     *
     * @param modelType   模型类型过滤（可空）
     * @param isPublished 发布状态过滤（可空）
     * @param keyword     模型名称关键字模糊匹配（可空）
     * @param pageable    分页参数
     * @return 模型分页结果 (按 createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmLeadScoringModelEntity> listModels(String modelType, Boolean isPublished,
                                                        String keyword, Pageable pageable) {
        Specification<ScrmLeadScoringModelEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (modelType != null && !modelType.isBlank()) {
                predicates.add(cb.equal(root.get("modelType"), modelType));
            }
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
    public ScrmLeadScoringModelEntity publishModel(Long id) throws ScrmException {
        ScrmLeadScoringModelEntity entity = findModelOrThrow(id);
        entity.setIsPublished(true);
        entity = modelRepository.save(entity);
        log.info("发布评分模型: id={}, modelName={}", id, entity.getModelName());
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
    public ScrmLeadScoringModelEntity unpublishModel(Long id) throws ScrmException {
        ScrmLeadScoringModelEntity entity = findModelOrThrow(id);
        entity.setIsPublished(false);
        entity = modelRepository.save(entity);
        log.info("取消发布评分模型: id={}, modelName={}", id, entity.getModelName());
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
    public ScrmLeadScoringModelEntity setDefaultModel(Long id) throws ScrmException {
        ScrmLeadScoringModelEntity entity = findModelOrThrow(id);
        clearExistingDefault();
        entity.setIsDefault(true);
        entity = modelRepository.save(entity);
        log.info("设置默认评分模型: id={}, modelName={}", id, entity.getModelName());
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
    public ScrmLeadScoringModelEntity copyModel(Long id) throws ScrmException {
        ScrmLeadScoringModelEntity source = findModelOrThrow(id);
        String newCode = source.getModelCode() + "_copy";
        if (modelRepository.findByModelCode(newCode).isPresent()) {
            throw ScrmException.conflict("模型编码已存在: " + newCode);
        }
        ScrmLeadScoringModelEntity copy = new ScrmLeadScoringModelEntity();
        copy.setModelName(source.getModelName() + " (副本)");
        copy.setModelCode(newCode);
        copy.setDescription(source.getDescription());
        copy.setModelType(source.getModelType());
        copy.setDimensions(source.getDimensions());
        copy.setTotalMaxScore(source.getTotalMaxScore());
        copy.setGradeThresholds(source.getGradeThresholds());
        copy.setIsDefault(DEFAULT_IS_DEFAULT);
        copy.setIsPublished(DEFAULT_IS_PUBLISHED);
        copy.setVersionNo(DEFAULT_VERSION_NO);
        copy.setAppliedCount(DEFAULT_APPLIED_COUNT);
        copy.setCreatedBy(source.getCreatedBy());
        copy = modelRepository.save(copy);
        log.info("复制评分模型: sourceId={}, newId={}, newCode={}", id, copy.getId(), newCode);
        return copy;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 校验评分模型参数。
     *
     * @param dto     模型参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateModelDto(ScrmLeadScoringModelDto dto, boolean partial) throws ScrmException {
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
        if (dto.getModelType() != null && !VALID_MODEL_TYPES.contains(dto.getModelType())) {
            throw ScrmException.badRequest(
                    "模型类型非法: " + dto.getModelType() + ", 仅支持 " + VALID_MODEL_TYPES);
        }
        if (dto.getDimensions() != null) {
            if (dto.getDimensions().isBlank()) {
                throw ScrmException.badRequest("评分维度配置不能为空");
            }
            try {
                objectMapper.readTree(dto.getDimensions());
            } catch (Exception e) {
                throw ScrmException.badRequest("评分维度配置 JSON 解析失败: " + e.getMessage());
            }
        } else if (!partial) {
            throw ScrmException.badRequest("评分维度配置不能为空");
        }
        if (dto.getGradeThresholds() != null && !dto.getGradeThresholds().isBlank()) {
            try {
                objectMapper.readTree(dto.getGradeThresholds());
            } catch (Exception e) {
                throw ScrmException.badRequest("等级阈值 JSON 解析失败: " + e.getMessage());
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
     * 确保分页参数带默认排序 (按指定字段倒序)。
     *
     * @param pageable 分页参数
     * @param field    默认排序字段
     * @return 处理后的分页参数
     */
    Pageable ensureSort(Pageable pageable, String field) {
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
    ScrmLeadScoringModelEntity findModelOrThrow(Long id) throws ScrmException {
        ScrmLeadScoringModelEntity entity = modelRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "评分模型不存在: id=" + id));
        return entity;
    }
}