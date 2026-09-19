/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAssetLibraryCategoryService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmAssetCategoryDto;
import org.hiylo.scrm.entity.ScrmAssetCategoryEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmAssetCategoryRepository;
import org.hiylo.scrm.repository.ScrmAssetRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRM 营销素材库 - 素材分类管理子域服务。
 * <p>
 * 承载素材分类的增删改查、层级路径计算、启停、移动与统计更新能力。
 * 同时托管素材分类查询/路径构建工具, 供素材兄弟类以 package 级访问复用。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmAssetLibraryCategoryService {

    /** 默认排序值 */
    private static final int DEFAULT_SORT_ORDER = 0;

    /** 默认分类层级 (顶级) */
    private static final int DEFAULT_CATEGORY_LEVEL = 1;

    /** 路径分隔符 */
    private static final String PATH_SEPARATOR = "/";

    /** 素材分类数据访问层 */
    private final ScrmAssetCategoryRepository categoryRepository;

    /** 素材数据访问层 (分类统计重算用) */
    private final ScrmAssetRepository assetRepository;

    /**
     * 创建素材分类。
     * <p>parentId 引用当前账号的已有分类 (为空表示顶级分类); categoryCode 唯一;
     * 层级 / 排序 / 启用状态缺省时填默认值。</p>
     *
     * @param dto 分类参数
     * @return 创建后的分类
     * @throws ScrmException 参数非法 / 分类编码重复 / 父分类不存在
     */
    @Transactional
    public ScrmAssetCategoryEntity createCategory(ScrmAssetCategoryDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("分类参数不能为空");
        }
        if (categoryRepository.existsByCategoryCode(dto.getCategoryCode())) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    "分类编码已存在: categoryCode=" + dto.getCategoryCode());
        }
        int level = DEFAULT_CATEGORY_LEVEL;
        String path = null;
        if (dto.getParentId() != null) {
            ScrmAssetCategoryEntity parent = findCategoryOrThrow(dto.getParentId());
            level = (parent.getCategoryLevel() != null ? parent.getCategoryLevel() : DEFAULT_CATEGORY_LEVEL) + 1;
            path = buildCategoryPath(parent.getCategoryPath(), null);
        }
        ScrmAssetCategoryEntity entity = new ScrmAssetCategoryEntity();
        entity.setCategoryName(dto.getCategoryName());
        entity.setCategoryCode(dto.getCategoryCode());
        entity.setDescription(dto.getDescription());
        entity.setParentId(dto.getParentId());
        entity.setCategoryLevel(level);
        entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : DEFAULT_SORT_ORDER);
        entity.setIcon(dto.getIcon());
        entity.setColor(dto.getColor());
        entity.setAssetCount(0);
        entity.setTotalSizeBytes(0L);
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : Boolean.TRUE);
        entity.setVisibleToRoles(dto.getVisibleToRoles());
        entity.setCreatedBy(dto.getCreatedBy());
        entity = categoryRepository.save(entity);
        // 落地后用自身 ID 完善路径
        entity.setCategoryPath(buildCategoryPath(path, entity.getId()));
        entity = categoryRepository.save(entity);
        log.info("创建素材分类: id={}, categoryCode={}", entity.getId(), entity.getCategoryCode());
        return entity;
    }

    /**
     * 更新素材分类 (字段非空才覆盖)。
     * <p>分类编码不允许变更; parentId 变更会重算层级与路径。</p>
     *
     * @param id  分类 ID
     * @param dto 分类参数
     * @return 更新后的分类
     * @throws ScrmException 分类不存在 / 父分类不存在 / 自引用
     */
    @Transactional
    public ScrmAssetCategoryEntity updateCategory(Long id, ScrmAssetCategoryDto dto) throws ScrmException {
        ScrmAssetCategoryEntity entity = findCategoryOrThrow(id);
        if (dto == null) {
            throw ScrmException.badRequest("分类参数不能为空");
        }
        if (dto.getParentId() != null) {
            if (dto.getParentId().equals(id)) {
                throw ScrmException.badRequest("父分类 ID 不能等于自身 ID, 禁止自引用");
            }
            ScrmAssetCategoryEntity parent = findCategoryOrThrow(dto.getParentId());
            entity.setParentId(parent.getId());
            entity.setCategoryLevel((parent.getCategoryLevel() != null
                    ? parent.getCategoryLevel() : DEFAULT_CATEGORY_LEVEL) + 1);
            entity.setCategoryPath(buildCategoryPath(parent.getCategoryPath(), entity.getId()));
        }
        if (dto.getCategoryName() != null) entity.setCategoryName(dto.getCategoryName());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getSortOrder() != null) entity.setSortOrder(dto.getSortOrder());
        if (dto.getIcon() != null) entity.setIcon(dto.getIcon());
        if (dto.getColor() != null) entity.setColor(dto.getColor());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getVisibleToRoles() != null) entity.setVisibleToRoles(dto.getVisibleToRoles());
        entity = categoryRepository.save(entity);
        log.info("更新素材分类: id={}, categoryCode={}", entity.getId(), entity.getCategoryCode());
        return entity;
    }

    /**
     * 删除素材分类。
     * <p>删除前检查是否有子分类, 若有则阻止删除。</p>
     *
     * @param id 分类 ID
     * @throws ScrmException 分类不存在 / 仍有子分类
     */
    @Transactional
    public void deleteCategory(Long id) throws ScrmException {
        ScrmAssetCategoryEntity entity = findCategoryOrThrow(id);
        List<ScrmAssetCategoryEntity> children = categoryRepository
                .findByParentId(id);
        if (!children.isEmpty()) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    String.format("无法删除分类: 仍有 %d 个子分类, 请先删除子分类", children.size()));
        }
        categoryRepository.delete(entity);
        log.info("删除素材分类: id={}, categoryCode={}", id, entity.getCategoryCode());
    }

    /**
     * 查询分类详情。
     *
     * @param id 分类 ID
     * @return 分类实体
     * @throws ScrmException 分类不存在
     */
    @Transactional(readOnly = true)
    public ScrmAssetCategoryEntity getCategory(Long id) throws ScrmException {
        return findCategoryOrThrow(id);
    }

    /**
     * 按分类编码查询分类。
     *
     * @param code 分类编码
     * @return 分类实体
     * @throws ScrmException 分类不存在
     */
    @Transactional(readOnly = true)
    public ScrmAssetCategoryEntity getCategoryByCode(String code) throws ScrmException {
        if (code == null || code.isBlank()) {
            throw ScrmException.badRequest("分类编码不能为空");
        }
        return categoryRepository.findByCategoryCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "素材分类不存在: categoryCode=" + code));
    }

    /**
     * 分页查询分类, 支持按父分类 / 启用状态 / 关键词过滤。
     *
     * @param parentId 父分类过滤 (可空)
     * @param enabled  启用状态过滤 (可空)
     * @param keyword  关键词过滤, 匹配分类名称 / 编码 / 描述 (可空)
     * @param pageable 分页参数
     * @return 分类分页结果 (按 sortOrder ASC + createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmAssetCategoryEntity> listCategories(Long parentId, Boolean enabled,
                                                         String keyword, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.ASC, "sortOrder")
                        .and(Sort.by(Sort.Direction.DESC, "createTime")));
        Specification<ScrmAssetCategoryEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (parentId != null) {
                predicates.add(cb.equal(root.get("parentId"), parentId));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (keyword != null && !keyword.isBlank()) {
                String kw = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("categoryName")), kw),
                        cb.like(cb.lower(root.get("categoryCode")), kw),
                        cb.like(cb.lower(root.get("description")), kw)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return categoryRepository.findAll(spec, sorted);
    }

    /**
     * 查询分类树。
     * <p>加载当前账号全量分类, 在内存中按 parentId 构建多级树结构 (按 sortOrder 升序)。</p>
     *
     * @return 顶级分类列表
     */
    @Transactional(readOnly = true)
    public List<ScrmAssetCategoryEntity> getCategoryTree() {
        List<ScrmAssetCategoryEntity> all = categoryRepository.findAll();
        all.sort(Comparator.comparingInt((ScrmAssetCategoryEntity c) ->
                c.getSortOrder() != null ? c.getSortOrder() : DEFAULT_SORT_ORDER));
        Map<Long, List<ScrmAssetCategoryEntity>> byParent = new LinkedHashMap<>();
        for (ScrmAssetCategoryEntity c : all) {
            byParent.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c);
        }
        return byParent.getOrDefault(null, new ArrayList<>());
    }

    /**
     * 启用分类。
     *
     * @param id 分类 ID
     * @return 更新后的分类
     * @throws ScrmException 分类不存在
     */
    @Transactional
    public ScrmAssetCategoryEntity enableCategory(Long id) throws ScrmException {
        ScrmAssetCategoryEntity entity = findCategoryOrThrow(id);
        entity.setEnabled(Boolean.TRUE);
        entity = categoryRepository.save(entity);
        log.info("启用素材分类: id={}", id);
        return entity;
    }

    /**
     * 禁用分类。
     *
     * @param id 分类 ID
     * @return 更新后的分类
     * @throws ScrmException 分类不存在
     */
    @Transactional
    public ScrmAssetCategoryEntity disableCategory(Long id) throws ScrmException {
        ScrmAssetCategoryEntity entity = findCategoryOrThrow(id);
        entity.setEnabled(Boolean.FALSE);
        entity = categoryRepository.save(entity);
        log.info("禁用素材分类: id={}", id);
        return entity;
    }

    /**
     * 移动分类 (变更父分类与排序值)。
     * <p>parentId 传 null 表示移到根级; 移动后重算层级与路径。</p>
     *
     * @param id           分类 ID
     * @param newParentId  新父分类 ID (可空)
     * @param newSortOrder 新排序值 (可空)
     * @return 更新后的分类
     * @throws ScrmException 分类不存在 / 父分类不存在 / 父分类为自身
     */
    @Transactional
    public ScrmAssetCategoryEntity moveCategory(Long id, Long newParentId, Integer newSortOrder)
            throws ScrmException {
        ScrmAssetCategoryEntity entity = findCategoryOrThrow(id);
        if (newParentId != null) {
            if (newParentId.equals(id)) {
                throw ScrmException.badRequest("父分类 ID 不能等于自身 ID, 禁止自引用");
            }
            ScrmAssetCategoryEntity parent = findCategoryOrThrow(newParentId);
            entity.setParentId(parent.getId());
            entity.setCategoryLevel((parent.getCategoryLevel() != null
                    ? parent.getCategoryLevel() : DEFAULT_CATEGORY_LEVEL) + 1);
            entity.setCategoryPath(buildCategoryPath(parent.getCategoryPath(), entity.getId()));
        } else {
            entity.setParentId(null);
            entity.setCategoryLevel(DEFAULT_CATEGORY_LEVEL);
            entity.setCategoryPath(entity.getId() + PATH_SEPARATOR);
        }
        if (newSortOrder != null) {
            entity.setSortOrder(newSortOrder);
        }
        entity = categoryRepository.save(entity);
        log.info("移动素材分类: id={}, newParentId={}, newSortOrder={}", id, newParentId, newSortOrder);
        return entity;
    }

    /**
     * 更新分类统计 (重新计算 assetCount 与 totalSizeBytes)。
     *
     * @param id 分类 ID
     * @return 更新后的分类
     * @throws ScrmException 分类不存在
     */
    @Transactional
    public ScrmAssetCategoryEntity updateCategoryStats(Long id) throws ScrmException {
        ScrmAssetCategoryEntity entity = findCategoryOrThrow(id);
        long count = assetRepository.countByCategoryId(id);
        Long totalSize = assetRepository.sumFileSizeBytesByCategoryId(id);
        entity.setAssetCount((int) count);
        entity.setTotalSizeBytes(totalSize != null ? totalSize : 0L);
        entity = categoryRepository.save(entity);
        log.info("更新素材分类统计: id={}, assetCount={}, totalSizeBytes={}",
                id, entity.getAssetCount(), entity.getTotalSizeBytes());
        return entity;
    }

    /**
     * 按主键查询分类, 不存在或越权抛异常。
     */
    ScrmAssetCategoryEntity findCategoryOrThrow(Long id) throws ScrmException {
        ScrmAssetCategoryEntity entity = categoryRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "素材分类不存在: id=" + id));

        return entity;
    }

    /**
     * 构建分类路径: parentPath + selfId + "/" (parentPath 为空时为 selfId + "/")。
     */
    private String buildCategoryPath(String parentPath, Long selfId) {
        StringBuilder sb = new StringBuilder();
        if (parentPath != null && !parentPath.isBlank()) {
            sb.append(parentPath);
        }
        if (selfId != null) {
            sb.append(selfId).append(PATH_SEPARATOR);
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}
