/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAttributionModelService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmAttributionModelDto;
import org.hiylo.scrm.entity.ScrmAttributionModelEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmAttributionModelRepository;
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
 * SCRM 营销归因模型管理服务。
 * <p>
 * 承载归因模型的增删改查、发布/取消发布、设置默认模型与参数校验。
 * 归因模型定义类型 (FIRST_TOUCH / LAST_TOUCH / LINEAR / TIME_DECAY / POSITION_BASED /
 * U_SHAPED / W_SHAPED / CUSTOM)、回溯窗口、位置权重与自定义权重。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026/09/19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmAttributionModelService {

    /** 模型类型: 首次触点归因 */
    static final String MODEL_FIRST_TOUCH = "FIRST_TOUCH";

    /** 合法的模型类型 */
    private static final List<String> VALID_MODEL_TYPES = List.of(
            MODEL_FIRST_TOUCH, "LAST_TOUCH", "LINEAR", "TIME_DECAY",
            "POSITION_BASED", "U_SHAPED", "W_SHAPED", "CUSTOM");

    /** 默认回溯天数 */
    static final int DEFAULT_LOOKBACK_DAYS = 30;
    /** 默认时间衰减半衰期 (天) */
    static final int DEFAULT_TIME_DECAY_HALF_LIFE = 7;
    /** 默认转化窗口 (天) */
    static final int DEFAULT_CONVERSION_WINDOW_DAYS = 7;
    /** 默认应用次数初值 */
    private static final int DEFAULT_APPLIED_COUNT = 0;
    /** 默认是否默认模型 */
    private static final boolean DEFAULT_IS_DEFAULT = false;
    /** 默认是否已发布 */
    private static final boolean DEFAULT_IS_PUBLISHED = false;

    /** 归因模型数据访问层 */
    private final ScrmAttributionModelRepository modelRepository;

    /** JSON 解析器 (解析 positionWeights / customWeights) */
    private final ObjectMapper objectMapper;

    /**
     * 创建归因模型。
     * <p>校验 modelType 合法性与 modelCode 唯一性后写入归属账号 ID 持久化,
     * lookbackDays / timeDecayHalfLife / conversionWindowDays / isDefault / isPublished /
     * appliedCount 缺省时填默认值。若设为默认, 清理旧默认。</p>
     *
     * @param dto 模型参数
     * @return 创建后的模型
     * @throws ScrmException 参数非法 / modelCode 重复
     */
    @Transactional
    public ScrmAttributionModelEntity createModel(ScrmAttributionModelDto dto) throws ScrmException {
        validateModelDto(dto, false);
        if (modelRepository.findByModelCode(dto.getModelCode()).isPresent()) {
            throw ScrmException.conflict("归因模型编码已存在: " + dto.getModelCode());
        }
        ScrmAttributionModelEntity entity = new ScrmAttributionModelEntity();
        entity.setModelName(dto.getModelName());
        entity.setModelCode(dto.getModelCode());
        entity.setDescription(dto.getDescription());
        entity.setModelType(dto.getModelType() != null ? dto.getModelType() : MODEL_FIRST_TOUCH);
        entity.setLookbackDays(dto.getLookbackDays() != null ? dto.getLookbackDays() : DEFAULT_LOOKBACK_DAYS);
        entity.setPositionWeights(dto.getPositionWeights());
        entity.setTimeDecayHalfLife(dto.getTimeDecayHalfLife() != null
                ? dto.getTimeDecayHalfLife() : DEFAULT_TIME_DECAY_HALF_LIFE);
        entity.setCustomWeights(dto.getCustomWeights());
        entity.setConversionWindowDays(dto.getConversionWindowDays() != null
                ? dto.getConversionWindowDays() : DEFAULT_CONVERSION_WINDOW_DAYS);
        entity.setIsDefault(dto.getIsDefault() != null ? dto.getIsDefault() : DEFAULT_IS_DEFAULT);
        entity.setIsPublished(dto.getIsPublished() != null ? dto.getIsPublished() : DEFAULT_IS_PUBLISHED);
        entity.setAppliedCount(DEFAULT_APPLIED_COUNT);
        entity.setCreatedBy(dto.getCreatedBy());
        if (Boolean.TRUE.equals(entity.getIsDefault())) {
            modelRepository.clearDefaultFlag();
        }
        entity = modelRepository.save(entity);
        log.info("创建归因模型: id={}, modelName={}, modelCode={}, modelType={}",
                entity.getId(), entity.getModelName(), entity.getModelCode(), entity.getModelType());
        return entity;
    }

    /**
     * 更新归因模型（字段非空才覆盖）。
     *
     * @param id  模型 ID
     * @param dto 模型参数
     * @return 更新后的模型
     * @throws ScrmException 模型不存在 / 参数非法 / modelCode 重复
     */
    @Transactional
    public ScrmAttributionModelEntity updateModel(Long id, ScrmAttributionModelDto dto) throws ScrmException {
        ScrmAttributionModelEntity entity = findModelOrThrow(id);
        validateModelDto(dto, true);
        if (dto.getModelCode() != null && !dto.getModelCode().equals(entity.getModelCode()) && modelRepository.findByModelCode(dto.getModelCode()).isPresent()) {
            throw ScrmException.conflict("归因模型编码已存在: " + dto.getModelCode());
        }
        if (dto.getModelName() != null) entity.setModelName(dto.getModelName());
        if (dto.getModelCode() != null) entity.setModelCode(dto.getModelCode());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getModelType() != null) entity.setModelType(dto.getModelType());
        if (dto.getLookbackDays() != null) entity.setLookbackDays(dto.getLookbackDays());
        if (dto.getPositionWeights() != null) entity.setPositionWeights(dto.getPositionWeights());
        if (dto.getTimeDecayHalfLife() != null) entity.setTimeDecayHalfLife(dto.getTimeDecayHalfLife());
        if (dto.getCustomWeights() != null) entity.setCustomWeights(dto.getCustomWeights());
        if (dto.getConversionWindowDays() != null) entity.setConversionWindowDays(dto.getConversionWindowDays());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        if (dto.getIsDefault() != null) {
            if (Boolean.TRUE.equals(dto.getIsDefault()) && !Boolean.TRUE.equals(entity.getIsDefault())) {
                modelRepository.clearDefaultFlag();
            }
            entity.setIsDefault(dto.getIsDefault());
        }
        if (dto.getIsPublished() != null) {
            entity.setIsPublished(dto.getIsPublished());
        }
        entity = modelRepository.save(entity);
        log.info("更新归因模型: id={}, modelName={}", entity.getId(), entity.getModelName());
        return entity;
    }

    /**
     * 删除归因模型。
     *
     * @param id 模型 ID
     * @throws ScrmException 模型不存在
     */
    @Transactional
    public void deleteModel(Long id) throws ScrmException {
        ScrmAttributionModelEntity entity = findModelOrThrow(id);
        modelRepository.delete(entity);
        log.info("删除归因模型: id={}, modelName={}", id, entity.getModelName());
    }

    /**
     * 查询模型详情。
     *
     * @param id 模型 ID
     * @return 模型实体
     * @throws ScrmException 模型不存在
     */
    @Transactional(readOnly = true)
    public ScrmAttributionModelEntity getModel(Long id) throws ScrmException {
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
    public ScrmAttributionModelEntity getModelByCode(String code) throws ScrmException {
        if (code == null || code.isBlank()) {
            throw ScrmException.badRequest("模型编码不能为空");
        }
        return modelRepository.findByModelCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "归因模型不存在: code=" + code));
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
    public Page<ScrmAttributionModelEntity> listModels(String modelType, Boolean isPublished,
                                                        String keyword, Pageable pageable) {
        Specification<ScrmAttributionModelEntity> spec = (root, query, cb) -> {
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
    public ScrmAttributionModelEntity publishModel(Long id) throws ScrmException {
        ScrmAttributionModelEntity entity = findModelOrThrow(id);
        entity.setIsPublished(true);
        entity = modelRepository.save(entity);
        log.info("发布归因模型: id={}, modelName={}", id, entity.getModelName());
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
    public ScrmAttributionModelEntity unpublishModel(Long id) throws ScrmException {
        ScrmAttributionModelEntity entity = findModelOrThrow(id);
        entity.setIsPublished(false);
        entity = modelRepository.save(entity);
        log.info("取消发布归因模型: id={}, modelName={}", id, entity.getModelName());
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
    public ScrmAttributionModelEntity setDefault(Long id) throws ScrmException {
        ScrmAttributionModelEntity entity = findModelOrThrow(id);
        modelRepository.clearDefaultFlag();
        entity.setIsDefault(true);
        entity = modelRepository.save(entity);
        log.info("设置默认归因模型: id={}, modelName={}", id, entity.getModelName());
        return entity;
    }

    /**
     * 按主键查询模型, 不存在抛异常, 并校验账号归属。
     * <p>供归因计算/报告兄弟类共用。</p>
     *
     * @param id 模型 ID
     * @return 模型实体
     * @throws ScrmException 模型不存在
     */
    ScrmAttributionModelEntity findModelOrThrow(Long id) throws ScrmException {
        ScrmAttributionModelEntity entity = modelRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "归因模型不存在: id=" + id));
        return entity;
    }

    /**
     * 校验模型参数。
     *
     * @param dto     模型参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateModelDto(ScrmAttributionModelDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("归因模型参数不能为空");
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
        if (dto.getModelType() != null && !dto.getModelType().isBlank() && !VALID_MODEL_TYPES.contains(dto.getModelType())) {
            throw ScrmException.badRequest(
                    "模型类型非法: " + dto.getModelType() + ", 仅支持 " + VALID_MODEL_TYPES);
        }
        if (dto.getPositionWeights() != null && !dto.getPositionWeights().isBlank()) {
            try {
                objectMapper.readTree(dto.getPositionWeights());
            } catch (Exception e) {
                throw ScrmException.badRequest("位置权重 positionWeights JSON 解析失败: " + e.getMessage());
            }
        }
        if (dto.getCustomWeights() != null && !dto.getCustomWeights().isBlank()) {
            try {
                objectMapper.readTree(dto.getCustomWeights());
            } catch (Exception e) {
                throw ScrmException.badRequest("自定义权重 customWeights JSON 解析失败: " + e.getMessage());
            }
        }
    }

    /**
     * 确保分页参数带默认排序 (按指定字段倒序)。
     * <p>供触点/转化兄弟类共用。</p>
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
}
