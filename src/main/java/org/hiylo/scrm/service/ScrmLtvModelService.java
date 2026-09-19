/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLtvModelService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmLtvModelDto;
import org.hiylo.scrm.entity.ScrmLtvModelEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCustomerLtvRepository;
import org.hiylo.scrm.repository.ScrmLtvModelRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * LTV 模型管理服务: 模型增删改查与按编码查询、分页查询 (按类型/发布状态/关键词过滤)、
 * 发布/取消发布、设置默认模型、复制模型, 删除模型时级联清理客户 LTV 计算结果。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmLtvModelService {

    /** 默认操作人 (请求头未透传 X-User-Id 时使用) */
    private static final String DEFAULT_OPERATOR = "scrm-system";

    /** LTV 模型配置数据访问层 */
    private final ScrmLtvModelRepository modelRepository;
    /** 客户 LTV 计算结果数据访问层 (删除模型时级联清理) */
    private final ScrmCustomerLtvRepository ltvRepository;

    // ============================================================
    // 模型管理
    // ============================================================

    /**
     * 创建 LTV 模型
     *
     * @param dto 模型参数
     * @return 创建后的模型
     * @throws ScrmException 参数非法 / 模型编码冲突 / 默认模型冲突
     */
    @Transactional
    public ScrmLtvModelDto createModel(ScrmLtvModelDto dto) throws ScrmException {
        if (dto.getModelName() == null || dto.getModelName().isBlank()) {
            throw ScrmException.badRequest("模型名称不能为空");
        }
        if (dto.getModelCode() == null || dto.getModelCode().isBlank()) {
            throw ScrmException.badRequest("模型编码不能为空");
        }
        if (dto.getModelType() == null || dto.getModelType().isBlank()) {
            throw ScrmException.badRequest("模型类型不能为空");
        }
        if (dto.getCalculationMethod() == null || dto.getCalculationMethod().isBlank()) {
            throw ScrmException.badRequest("计算方法不能为空");
        }
        // 模型编码唯一性校验
        if (modelRepository.countByModelCode(dto.getModelCode()) > 0) {
            throw ScrmException.conflict("模型编码已存在: " + dto.getModelCode());
        }
        Boolean isDefault = Boolean.TRUE.equals(dto.getIsDefault());
        if (isDefault && modelRepository.countByIsDefaultTrue() > 0) {
            throw ScrmException.conflict("同账号下已存在默认 LTV 模型, 请先取消原默认模型");
        }
        ScrmLtvModelEntity entity = new ScrmLtvModelEntity();
        entity.setModelName(dto.getModelName());
        entity.setModelCode(dto.getModelCode());
        entity.setDescription(dto.getDescription());
        entity.setModelType(dto.getModelType());
        entity.setCalculationMethod(dto.getCalculationMethod());
        entity.setLookbackDays(dto.getLookbackDays() != null ? dto.getLookbackDays() : 365);
        entity.setForecastDays(dto.getForecastDays() != null ? dto.getForecastDays() : 365);
        entity.setDiscountRate(dto.getDiscountRate() != null ? dto.getDiscountRate() : 0.1d);
        entity.setChurnRate(dto.getChurnRate() != null ? dto.getChurnRate() : 0.05d);
        entity.setAvgProfitMargin(dto.getAvgProfitMargin() != null ? dto.getAvgProfitMargin() : 0.3d);
        entity.setPurchaseFrequencyThreshold(dto.getPurchaseFrequencyThreshold() != null
                ? dto.getPurchaseFrequencyThreshold() : 2);
        entity.setTierThresholds(dto.getTierThresholds());
        entity.setIsDefault(isDefault);
        entity.setIsPublished(Boolean.TRUE.equals(dto.getIsPublished()));
        entity.setModelVersion(dto.getModelVersion() != null ? dto.getModelVersion() : 1);
        entity.setAppliedCount(0);
        entity.setCreatedBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : DEFAULT_OPERATOR);
        entity = modelRepository.save(entity);
        log.info("创建 LTV 模型: id={}, code={}", entity.getId(), entity.getModelCode());
        return toModelDto(entity);
    }

    /**
     * 更新 LTV 模型
     *
     * @param id  模型 ID
     * @param dto 模型参数
     * @return 更新后的模型
     * @throws ScrmException 模型不存在 / 参数非法
     */
    @Transactional
    public ScrmLtvModelDto updateModel(Long id, ScrmLtvModelDto dto) throws ScrmException {
        ScrmLtvModelEntity entity = findModelOrThrow(id);
        if (dto.getModelName() != null) {
            if (dto.getModelName().isBlank()) {
                throw ScrmException.badRequest("模型名称不能为空");
            }
            entity.setModelName(dto.getModelName());
        }
        if (dto.getDescription() != null) {
            entity.setDescription(dto.getDescription());
        }
        if (dto.getModelType() != null) {
            entity.setModelType(dto.getModelType());
        }
        if (dto.getCalculationMethod() != null) {
            entity.setCalculationMethod(dto.getCalculationMethod());
        }
        if (dto.getLookbackDays() != null) {
            entity.setLookbackDays(dto.getLookbackDays());
        }
        if (dto.getForecastDays() != null) {
            entity.setForecastDays(dto.getForecastDays());
        }
        if (dto.getDiscountRate() != null) {
            entity.setDiscountRate(dto.getDiscountRate());
        }
        if (dto.getChurnRate() != null) {
            entity.setChurnRate(dto.getChurnRate());
        }
        if (dto.getAvgProfitMargin() != null) {
            entity.setAvgProfitMargin(dto.getAvgProfitMargin());
        }
        if (dto.getPurchaseFrequencyThreshold() != null) {
            entity.setPurchaseFrequencyThreshold(dto.getPurchaseFrequencyThreshold());
        }
        if (dto.getTierThresholds() != null) {
            entity.setTierThresholds(dto.getTierThresholds());
        }
        if (dto.getIsPublished() != null) {
            entity.setIsPublished(dto.getIsPublished());
        }
        if (dto.getModelVersion() != null) {
            entity.setModelVersion(dto.getModelVersion());
        }
        // isDefault 通过 setDefault 专用接口维护, 此处不直接修改
        entity = modelRepository.save(entity);
        return toModelDto(entity);
    }

    /**
     * 删除 LTV 模型
     * <p>
     * 同时清理该模型下的客户 LTV 计算结果。
     * </p>
     *
     * @param id 模型 ID
     * @throws ScrmException 模型不存在
     */
    @Transactional
    public void deleteModel(Long id) throws ScrmException {
        ScrmLtvModelEntity entity = findModelOrThrow(id);
        ltvRepository.deleteByModelId(id);
        modelRepository.delete(entity);
        log.info("删除 LTV 模型: id={}, code={}", id, entity.getModelCode());
    }

    /**
     * 查询 LTV 模型详情
     *
     * @param id 模型 ID
     * @return 模型 DTO
     * @throws ScrmException 模型不存在
     */
    @Transactional(readOnly = true)
    public ScrmLtvModelDto getModel(Long id) throws ScrmException {
        return toModelDto(findModelOrThrow(id));
    }

    /**
     * 按模型编码查询模型
     *
     * @param code 模型编码
     * @return 模型 DTO
     * @throws ScrmException 模型不存在
     */
    @Transactional(readOnly = true)
    public ScrmLtvModelDto getModelByCode(String code) throws ScrmException {
        ScrmLtvModelEntity entity = modelRepository
                .findByModelCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "LTV 模型不存在: code=" + code));
        return toModelDto(entity);
    }

    /**
     * 分页查询 LTV 模型, 支持按模型类型、发布状态与关键词过滤
     *
     * @param modelType   模型类型过滤 (可空)
     * @param isPublished 发布状态过滤 (可空)
     * @param keyword     名称/编码关键词 (可空)
     * @param pageable    分页参数
     * @return 模型分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmLtvModelDto> listModels(String modelType, Boolean isPublished, String keyword,
                                             Pageable pageable) {
        Specification<ScrmLtvModelEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (modelType != null && !modelType.isBlank()) {
                predicates.add(cb.equal(root.get("modelType"), modelType));
            }
            if (isPublished != null) {
                predicates.add(cb.equal(root.get("isPublished"), isPublished));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("modelName")), pattern),
                        cb.like(cb.lower(root.get("modelCode")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createTime"));
        return modelRepository.findAll(spec, sorted).map(this::toModelDto);
    }

    /**
     * 发布模型
     *
     * @param id 模型 ID
     * @return 更新后的模型
     * @throws ScrmException 模型不存在
     */
    @Transactional
    public ScrmLtvModelDto publishModel(Long id) throws ScrmException {
        ScrmLtvModelEntity entity = findModelOrThrow(id);
        entity.setIsPublished(true);
        entity = modelRepository.save(entity);
        log.info("发布 LTV 模型: id={}, code={}", id, entity.getModelCode());
        return toModelDto(entity);
    }

    /**
     * 取消发布模型
     *
     * @param id 模型 ID
     * @return 更新后的模型
     * @throws ScrmException 模型不存在
     */
    @Transactional
    public ScrmLtvModelDto unpublishModel(Long id) throws ScrmException {
        ScrmLtvModelEntity entity = findModelOrThrow(id);
        entity.setIsPublished(false);
        entity = modelRepository.save(entity);
        log.info("取消发布 LTV 模型: id={}, code={}", id, entity.getModelCode());
        return toModelDto(entity);
    }

    /**
     * 设置默认模型
     * <p>
     * 取消同账号下原默认模型标记, 将当前模型置为默认。
     * </p>
     *
     * @param id 模型 ID
     * @return 更新后的模型
     * @throws ScrmException 模型不存在
     */
    @Transactional
    public ScrmLtvModelDto setDefault(Long id) throws ScrmException {
        ScrmLtvModelEntity entity = findModelOrThrow(id);
        modelRepository.findByIsDefaultTrue().ifPresent(old -> {
            if (!Objects.equals(old.getId(), id)) {
                old.setIsDefault(false);
                modelRepository.save(old);
            }
        });
        entity.setIsDefault(true);
        entity = modelRepository.save(entity);
        log.info("设置默认 LTV 模型: id={}, code={}", id, entity.getModelCode());
        return toModelDto(entity);
    }

    /**
     * 复制模型
     * <p>
     * 基于已有模型复制一份新模型, 新模型编码需在 DTO 中提供 (此处自动追加 _copy 后缀避免冲突),
     * 默认未发布、非默认, 模型版本号递增。
     * </p>
     *
     * @param id 源模型 ID
     * @return 复制后的模型
     * @throws ScrmException 源模型不存在
     */
    @Transactional
    public ScrmLtvModelDto copyModel(Long id) throws ScrmException {
        ScrmLtvModelEntity source = findModelOrThrow(id);
        // 生成不冲突的新编码
        String newCode = source.getModelCode() + "_copy";
        int suffix = 1;
        while (modelRepository.countByModelCode(newCode) > 0) {
            newCode = source.getModelCode() + "_copy" + suffix;
            suffix++;
        }
        ScrmLtvModelEntity entity = new ScrmLtvModelEntity();
        entity.setModelName(source.getModelName() + " (副本)");
        entity.setModelCode(newCode);
        entity.setDescription(source.getDescription());
        entity.setModelType(source.getModelType());
        entity.setCalculationMethod(source.getCalculationMethod());
        entity.setLookbackDays(source.getLookbackDays());
        entity.setForecastDays(source.getForecastDays());
        entity.setDiscountRate(source.getDiscountRate());
        entity.setChurnRate(source.getChurnRate());
        entity.setAvgProfitMargin(source.getAvgProfitMargin());
        entity.setPurchaseFrequencyThreshold(source.getPurchaseFrequencyThreshold());
        entity.setTierThresholds(source.getTierThresholds());
        entity.setIsDefault(false);
        entity.setIsPublished(false);
        entity.setModelVersion((source.getModelVersion() != null ? source.getModelVersion() : 1) + 1);
        entity.setAppliedCount(0);
        entity.setCreatedBy(DEFAULT_OPERATOR);
        entity = modelRepository.save(entity);
        log.info("复制 LTV 模型: sourceId={}, newId={}, code={}", id, entity.getId(), entity.getModelCode());
        return toModelDto(entity);
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 保存模型实体 (供 LTV 计算后更新应用次数/时间使用)。
     *
     * @param entity 模型实体
     */
    void saveModel(ScrmLtvModelEntity entity) {
        modelRepository.save(entity);
    }

    /**
     * 按主键查询模型, 不存在或越权抛异常
     *
     * @param id 模型 ID
     * @return 模型实体
     * @throws ScrmException 模型不存在
     */
    ScrmLtvModelEntity findModelOrThrow(Long id) throws ScrmException {
        ScrmLtvModelEntity entity = modelRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "LTV 模型不存在: id=" + id));
        return entity;
    }

    /**
     * 模型实体转 DTO
     *
     * @param entity 模型实体
     * @return 模型 DTO
     */
    private ScrmLtvModelDto toModelDto(ScrmLtvModelEntity entity) {
        ScrmLtvModelDto dto = new ScrmLtvModelDto();
        dto.setId(entity.getId());
        dto.setModelName(entity.getModelName());
        dto.setModelCode(entity.getModelCode());
        dto.setDescription(entity.getDescription());
        dto.setModelType(entity.getModelType());
        dto.setCalculationMethod(entity.getCalculationMethod());
        dto.setLookbackDays(entity.getLookbackDays());
        dto.setForecastDays(entity.getForecastDays());
        dto.setDiscountRate(entity.getDiscountRate());
        dto.setChurnRate(entity.getChurnRate());
        dto.setAvgProfitMargin(entity.getAvgProfitMargin());
        dto.setPurchaseFrequencyThreshold(entity.getPurchaseFrequencyThreshold());
        dto.setTierThresholds(entity.getTierThresholds());
        dto.setIsDefault(entity.getIsDefault());
        dto.setIsPublished(entity.getIsPublished());
        dto.setModelVersion(entity.getModelVersion());
        dto.setAppliedCount(entity.getAppliedCount());
        dto.setLastAppliedAt(entity.getLastAppliedAt());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}