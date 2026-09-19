/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFeedbackCategoryService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmFeedbackCategoryDto;
import org.hiylo.scrm.entity.ScrmFeedbackCategoryEntity;
import org.hiylo.scrm.entity.ScrmFeedbackEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmFeedbackCategoryRepository;
import org.hiylo.scrm.repository.ScrmFeedbackRepository;
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
 * 客户反馈分类服务: 分类增删改查与按编码查询、分页查询 (按适用类型/启用状态过滤)、
 * 启用/禁用、反馈计数统计刷新。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmFeedbackCategoryService {

    /** 反馈类型: 建议 (分类默认优先级缺省值使用) */
    private static final String PRIORITY_MEDIUM = "MEDIUM";
    /** 默认 SLA 时长 (小时) */
    private static final int DEFAULT_SLA_HOURS = 48;

    /** 反馈分类数据访问层 */
    private final ScrmFeedbackCategoryRepository categoryRepository;
    /** 反馈数据访问层 (分类计数统计) */
    private final ScrmFeedbackRepository feedbackRepository;
    /** 反馈管理服务 (优先级合法性校验) */
    private final ScrmFeedbackManagementService managementService;

    // ============================================================
    // 分类 Category
    // ============================================================

    /**
     * 创建反馈分类。
     * <p>校验分类编码唯一, defaultPriority 缺省 MEDIUM, slaHours 缺省 48, sortOrder 缺省 0,
     * enabled 缺省 TRUE, feedbackCount 缺省 0。</p>
     *
     * @param dto 分类参数
     * @return 创建后的分类
     * @throws ScrmException 参数非法 / 分类编码重复
     */
    @Transactional
    public ScrmFeedbackCategoryDto createCategory(ScrmFeedbackCategoryDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("分类参数不能为空");
        }
        if (dto.getCategoryName() == null || dto.getCategoryName().isBlank()) {
            throw ScrmException.badRequest("分类名称不能为空");
        }
        if (dto.getCategoryCode() == null || dto.getCategoryCode().isBlank()) {
            throw ScrmException.badRequest("分类编码不能为空");
        }
        if (categoryRepository.findByCategoryCode(dto.getCategoryCode()).isPresent()) {
            throw ScrmException.conflict("分类编码已存在: " + dto.getCategoryCode());
        }
        if (dto.getDefaultPriority() != null) {
            managementService.validatePriority(dto.getDefaultPriority());
        }
        ScrmFeedbackCategoryEntity entity = new ScrmFeedbackCategoryEntity();
        entity.setCategoryName(dto.getCategoryName());
        entity.setCategoryCode(dto.getCategoryCode());
        entity.setDescription(dto.getDescription());
        entity.setApplicableTypes(dto.getApplicableTypes());
        entity.setDefaultPriority(dto.getDefaultPriority() != null ? dto.getDefaultPriority() : PRIORITY_MEDIUM);
        entity.setDefaultAssigneeId(dto.getDefaultAssigneeId());
        entity.setDefaultTeamId(dto.getDefaultTeamId());
        entity.setSlaHours(dto.getSlaHours() != null ? dto.getSlaHours() : DEFAULT_SLA_HOURS);
        entity.setAutoTag(dto.getAutoTag());
        entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        entity.setFeedbackCount(0);
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : Boolean.TRUE);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = categoryRepository.save(entity);
        log.info("创建反馈分类: id={}, code={}", entity.getId(), entity.getCategoryCode());
        return toCategoryDto(entity);
    }

    /**
     * 更新反馈分类（字段非空才覆盖）。
     *
     * @param id  分类 ID
     * @param dto 分类参数
     * @return 更新后的分类
     * @throws ScrmException 分类不存在 / 参数非法 / 分类编码重复
     */
    @Transactional
    public ScrmFeedbackCategoryDto updateCategory(Long id, ScrmFeedbackCategoryDto dto) throws ScrmException {
        ScrmFeedbackCategoryEntity entity = findCategoryOrThrow(id);
        if (dto == null) {
            throw ScrmException.badRequest("分类参数不能为空");
        }
        if (dto.getCategoryCode() != null && !dto.getCategoryCode().equals(entity.getCategoryCode())) {
            categoryRepository.findByCategoryCode(dto.getCategoryCode()).ifPresent(c -> {
                throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                        "分类编码已存在: " + dto.getCategoryCode());
            });
            entity.setCategoryCode(dto.getCategoryCode());
        }
        if (dto.getDefaultPriority() != null) {
            managementService.validatePriority(dto.getDefaultPriority());
        }
        if (dto.getCategoryName() != null) entity.setCategoryName(dto.getCategoryName());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getApplicableTypes() != null) entity.setApplicableTypes(dto.getApplicableTypes());
        if (dto.getDefaultPriority() != null) entity.setDefaultPriority(dto.getDefaultPriority());
        if (dto.getDefaultAssigneeId() != null) entity.setDefaultAssigneeId(dto.getDefaultAssigneeId());
        if (dto.getDefaultTeamId() != null) entity.setDefaultTeamId(dto.getDefaultTeamId());
        if (dto.getSlaHours() != null) entity.setSlaHours(dto.getSlaHours());
        if (dto.getAutoTag() != null) entity.setAutoTag(dto.getAutoTag());
        if (dto.getSortOrder() != null) entity.setSortOrder(dto.getSortOrder());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        entity = categoryRepository.save(entity);
        log.info("更新反馈分类: id={}", id);
        return toCategoryDto(entity);
    }

    /**
     * 删除反馈分类。
     *
     * @param id 分类 ID
     * @throws ScrmException 分类不存在
     */
    @Transactional
    public void deleteCategory(Long id) throws ScrmException {
        ScrmFeedbackCategoryEntity entity = findCategoryOrThrow(id);
        categoryRepository.delete(entity);
        log.info("删除反馈分类: id={}", id);
    }

    /**
     * 查询反馈分类详情。
     *
     * @param id 分类 ID
     * @return 分类 DTO
     * @throws ScrmException 分类不存在
     */
    @Transactional(readOnly = true)
    public ScrmFeedbackCategoryDto getCategory(Long id) throws ScrmException {
        return toCategoryDto(findCategoryOrThrow(id));
    }

    /**
     * 按分类编码查询反馈分类。
     *
     * @param code 分类编码
     * @return 分类 DTO
     * @throws ScrmException 分类不存在
     */
    @Transactional(readOnly = true)
    public ScrmFeedbackCategoryDto getCategoryByCode(String code) throws ScrmException {
        if (code == null || code.isBlank()) {
            throw ScrmException.badRequest("分类编码不能为空");
        }
        ScrmFeedbackCategoryEntity entity = categoryRepository.findByCategoryCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "反馈分类不存在: code=" + code));

        return toCategoryDto(entity);
    }

    /**
     * 分页查询反馈分类, 支持按适用反馈类型与启用状态过滤。
     * <p>applicableType 非空时, 仅返回 applicableTypes 包含该类型的分类; enabled 为空时返回全部。
     * 结果按 sortOrder 升序。</p>
     *
     * @param applicableType 适用反馈类型过滤 (可空)
     * @param enabled        启用状态过滤 (可空)
     * @param pageable       分页参数
     * @return 分类分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmFeedbackCategoryDto> listCategories(String applicableType, Boolean enabled, Pageable pageable) {
        Specification<ScrmFeedbackCategoryEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (applicableType != null && !applicableType.isBlank()) {
                predicates.add(cb.like(root.get("applicableTypes"), "%" + applicableType + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.ASC, "sortOrder"));
        return categoryRepository.findAll(spec, sorted).map(this::toCategoryDto);
    }

    /**
     * 启用反馈分类。
     *
     * @param id 分类 ID
     * @return 更新后的分类
     * @throws ScrmException 分类不存在
     */
    @Transactional
    public ScrmFeedbackCategoryDto enableCategory(Long id) throws ScrmException {
        ScrmFeedbackCategoryEntity entity = findCategoryOrThrow(id);
        entity.setEnabled(Boolean.TRUE);
        entity = categoryRepository.save(entity);
        log.info("启用反馈分类: id={}", id);
        return toCategoryDto(entity);
    }

    /**
     * 禁用反馈分类。
     *
     * @param id 分类 ID
     * @return 更新后的分类
     * @throws ScrmException 分类不存在
     */
    @Transactional
    public ScrmFeedbackCategoryDto disableCategory(Long id) throws ScrmException {
        ScrmFeedbackCategoryEntity entity = findCategoryOrThrow(id);
        entity.setEnabled(Boolean.FALSE);
        entity = categoryRepository.save(entity);
        log.info("禁用反馈分类: id={}", id);
        return toCategoryDto(entity);
    }

    /**
     * 更新分类的反馈计数。
     * <p>统计当前账号下 category 字段等于该分类编码的反馈数量, 写入 feedbackCount。</p>
     *
     * @param id 分类 ID
     * @return 更新后的分类
     * @throws ScrmException 分类不存在
     */
    @Transactional
    public ScrmFeedbackCategoryDto updateCategoryStats(Long id) throws ScrmException {
        ScrmFeedbackCategoryEntity entity = findCategoryOrThrow(id);
        // 提取为 final 变量, 避免 lambda 引用被重新赋值的 entity
        final String categoryCode = entity.getCategoryCode();
        Specification<ScrmFeedbackEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("category"), categoryCode));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        long count = feedbackRepository.count(spec);
        entity.setFeedbackCount((int) count);
        entity = categoryRepository.save(entity);
        log.info("更新反馈分类计数: id={}, count={}", id, count);
        return toCategoryDto(entity);
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 按主键查询反馈分类, 不存在或越权抛异常
     *
     * @param id 分类 ID
     * @return 分类实体
     * @throws ScrmException 分类不存在
     */
    private ScrmFeedbackCategoryEntity findCategoryOrThrow(Long id) throws ScrmException {
        ScrmFeedbackCategoryEntity entity = categoryRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "反馈分类不存在: id=" + id));

        return entity;
    }

    /**
     * 分类实体转 DTO
     *
     * @param entity 分类实体
     * @return 分类 DTO
     */
    private ScrmFeedbackCategoryDto toCategoryDto(ScrmFeedbackCategoryEntity entity) {
        ScrmFeedbackCategoryDto dto = new ScrmFeedbackCategoryDto();
        dto.setId(entity.getId());
        dto.setCategoryName(entity.getCategoryName());
        dto.setCategoryCode(entity.getCategoryCode());
        dto.setDescription(entity.getDescription());
        dto.setApplicableTypes(entity.getApplicableTypes());
        dto.setDefaultPriority(entity.getDefaultPriority());
        dto.setDefaultAssigneeId(entity.getDefaultAssigneeId());
        dto.setDefaultTeamId(entity.getDefaultTeamId());
        dto.setSlaHours(entity.getSlaHours());
        dto.setAutoTag(entity.getAutoTag());
        dto.setSortOrder(entity.getSortOrder());
        dto.setFeedbackCount(entity.getFeedbackCount());
        dto.setEnabled(entity.getEnabled());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}