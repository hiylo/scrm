/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmKnowledgeCategoryService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.dto.ScrmKnowledgeCategoryDto;
import org.hiylo.scrm.entity.ScrmKnowledgeArticleEntity;
import org.hiylo.scrm.entity.ScrmKnowledgeCategoryEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmKnowledgeArticleRepository;
import org.hiylo.scrm.repository.ScrmKnowledgeCategoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * SCRM 知识分类管理服务。
 * <p>
 * 承载知识库分类子域: 分类 CRUD / 树形查询 / 启停 / 移动 / 统计刷新 / 分类下文章查询。
 * 同时托管知识库共享基础能力 (安全取值 safeInt / toLong、当前操作人 currentOperator、
 * 已发布状态 STATUS_PUBLISHED) 与按主键查找分类, 供文章 / 版本 / 搜索反馈 / 审核 / 统计
 * 兄弟类以 package 级访问复用。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmKnowledgeCategoryService {

    // ==================== 共享基础能力 ====================

    /** 默认操作人 (请求头未透传时使用) */
    private static final String DEFAULT_OPERATOR = "scrm-system";

    /** 文章状态: 已发布 (共享, 分类聚合与搜索过滤均以此判定发布态文章) */
    static final String STATUS_PUBLISHED = "PUBLISHED";

    /** 知识分类数据访问层 */
    private final ScrmKnowledgeCategoryRepository categoryRepository;

    /** 知识文章数据访问层 (分类统计 / 删除校验 / 分类下文章查询) */
    private final ScrmKnowledgeArticleRepository articleRepository;

    // ============================================================
    // 分类管理
    // ============================================================

    /**
     * 创建知识分类 (自动计算层级与路径, 校验编码唯一)。
     *
     * @param dto 分类参数
     * @return 创建后的分类
     * @throws ScrmException 参数非法 / 编码重复
     */
    @Transactional
    public ScrmKnowledgeCategoryEntity createCategory(ScrmKnowledgeCategoryDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("分类参数不能为空");
        }
        if (categoryRepository.existsByCategoryCode(dto.getCategoryCode())) {
            throw ScrmException.conflict("分类编码已存在: " + dto.getCategoryCode());
        }
        ScrmKnowledgeCategoryEntity entity = new ScrmKnowledgeCategoryEntity();
        entity.setCategoryName(dto.getCategoryName());
        entity.setCategoryCode(dto.getCategoryCode());
        entity.setDescription(dto.getDescription());
        entity.setParentId(dto.getParentId());
        entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        entity.setIcon(dto.getIcon());
        entity.setColor(dto.getColor());
        entity.setEnabled(Boolean.TRUE);
        entity.setArticleCount(0);
        entity.setTotalViews(0);
        entity.setTotalLikes(0);
        entity.setVisibleToRoles(dto.getVisibleToRoles());
        entity.setCreatedBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : currentOperator());
        // 先保存获取 ID, 再回填层级与路径
        entity = categoryRepository.save(entity);
        populateCategoryHierarchy(entity, dto.getParentId());
        if (dto.getParentId() != null) {
            entity = categoryRepository.save(entity);
        }
        log.info("创建知识分类: id={}, code={}, name={}", entity.getId(), entity.getCategoryCode(), entity.getCategoryName());
        return entity;
    }

    /**
     * 更新知识分类 (字段非空才覆盖, 不允许修改编码)。
     *
     * @param id  分类 ID
     * @param dto 分类参数
     * @return 更新后的分类
     * @throws ScrmException 分类不存在 / 参数非法
     */
    @Transactional
    public ScrmKnowledgeCategoryEntity updateCategory(Long id, ScrmKnowledgeCategoryDto dto) throws ScrmException {
        ScrmKnowledgeCategoryEntity entity = findCategoryOrThrow(id);
        if (dto == null) {
            throw ScrmException.badRequest("分类参数不能为空");
        }
        if (dto.getCategoryName() != null) entity.setCategoryName(dto.getCategoryName());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getParentId() != null) {
            if (Objects.equals(dto.getParentId(), id)) {
                throw ScrmException.badRequest("父分类不能为自身");
            }
            entity.setParentId(dto.getParentId());
            populateCategoryHierarchy(entity, dto.getParentId());
        }
        if (dto.getSortOrder() != null) entity.setSortOrder(dto.getSortOrder());
        if (dto.getIcon() != null) entity.setIcon(dto.getIcon());
        if (dto.getColor() != null) entity.setColor(dto.getColor());
        if (dto.getVisibleToRoles() != null) entity.setVisibleToRoles(dto.getVisibleToRoles());
        entity = categoryRepository.save(entity);
        log.info("更新知识分类: id={}, name={}", entity.getId(), entity.getCategoryName());
        return entity;
    }

    /**
     * 删除知识分类 (存在子分类或关联文章时拒绝)。
     *
     * @param id 分类 ID
     * @throws ScrmException 分类不存在 / 存在子分类 / 存在关联文章
     */
    @Transactional
    public void deleteCategory(Long id) throws ScrmException {
        ScrmKnowledgeCategoryEntity entity = findCategoryOrThrow(id);
        long childCount = categoryRepository.countByParentId(id);
        if (childCount > 0) {
            throw ScrmException.conflict("存在子分类, 无法删除: id=" + id + ", childCount=" + childCount);
        }
        long articleCount = articleRepository.findByCategoryIdAndStatus(
                 id, STATUS_PUBLISHED, PageRequest.of(0, 1)).getTotalElements();
        if (articleCount > 0) {
            throw ScrmException.conflict("分类下存在文章, 无法删除: id=" + id + ", articleCount=" + articleCount);
        }
        categoryRepository.delete(entity);
        log.info("删除知识分类: id={}, code={}", id, entity.getCategoryCode());
    }

    /**
     * 查询知识分类详情。
     *
     * @param id 分类 ID
     * @return 分类实体
     * @throws ScrmException 分类不存在
     */
    @Transactional(readOnly = true)
    public ScrmKnowledgeCategoryEntity getCategory(Long id) throws ScrmException {
        return findCategoryOrThrow(id);
    }

    /**
     * 按编码查询知识分类。
     *
     * @param code 分类编码
     * @return 分类实体
     * @throws ScrmException 分类不存在
     */
    @Transactional(readOnly = true)
    public ScrmKnowledgeCategoryEntity getCategoryByCode(String code) throws ScrmException {
        ScrmKnowledgeCategoryEntity entity = categoryRepository.findByCategoryCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "知识分类不存在: code=" + code));
        return entity;
    }

    /**
     * 分页查询知识分类, 支持按父分类 / 启用状态 / 关键字过滤。
     *
     * @param parentId 父分类 ID 过滤（可空）
     * @param enabled  启用状态过滤（可空）
     * @param keyword  分类名称关键字模糊匹配（可空）
     * @param pageable 分页参数
     * @return 分类分页结果 (按 sortOrder ASC, categoryLevel ASC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmKnowledgeCategoryEntity> listCategories(Long parentId, Boolean enabled,
                                                             String keyword, Pageable pageable) {
        Specification<ScrmKnowledgeCategoryEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (parentId != null) {
                predicates.add(cb.equal(root.get("parentId"), parentId));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(root.get("categoryName"), "%" + keyword + "%"));
            }
            query.orderBy(cb.asc(root.get("sortOrder")), cb.asc(root.get("categoryLevel")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return categoryRepository.findAll(spec, pageable);
    }

    /**
     * 获取分类树 (递归构建, 顶层 parentId 为 null)。
     *
     * @return 分类树节点列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCategoryTree() {
        List<ScrmKnowledgeCategoryEntity> all = categoryRepository.findAllByOrderBySortOrderAscCategoryLevelAsc();
        Map<Long, List<ScrmKnowledgeCategoryEntity>> byParent = all.stream()
                .collect(Collectors.groupingBy(c -> c.getParentId() != null ? c.getParentId() : 0L));
        return buildCategoryTree(byParent, 0L);
    }

    /**
     * 启用知识分类。
     *
     * @param id 分类 ID
     * @return 更新后的分类
     * @throws ScrmException 分类不存在
     */
    @Transactional
    public ScrmKnowledgeCategoryEntity enableCategory(Long id) throws ScrmException {
        ScrmKnowledgeCategoryEntity entity = findCategoryOrThrow(id);
        entity.setEnabled(Boolean.TRUE);
        return categoryRepository.save(entity);
    }

    /**
     * 禁用知识分类。
     *
     * @param id 分类 ID
     * @return 更新后的分类
     * @throws ScrmException 分类不存在
     */
    @Transactional
    public ScrmKnowledgeCategoryEntity disableCategory(Long id) throws ScrmException {
        ScrmKnowledgeCategoryEntity entity = findCategoryOrThrow(id);
        entity.setEnabled(Boolean.FALSE);
        return categoryRepository.save(entity);
    }

    /**
     * 移动分类到新的父分类下并设置排序序号。
     *
     * @param id            分类 ID
     * @param newParentId   新父分类 ID (可空, null 表示移到顶层)
     * @param newSortOrder  新排序序号 (可空, 默认 0)
     * @return 更新后的分类
     * @throws ScrmException 分类不存在 / 父分类不能为自身
     */
    @Transactional
    public ScrmKnowledgeCategoryEntity moveCategory(Long id, Long newParentId, Integer newSortOrder)
            throws ScrmException {
        ScrmKnowledgeCategoryEntity entity = findCategoryOrThrow(id);
        if (Objects.equals(newParentId, id)) {
            throw ScrmException.badRequest("父分类不能为自身");
        }
        entity.setParentId(newParentId);
        if (newSortOrder != null) {
            entity.setSortOrder(newSortOrder);
        }
        populateCategoryHierarchy(entity, newParentId);
        return categoryRepository.save(entity);
    }

    /**
     * 更新分类统计 (文章数 / 总浏览量 / 总点赞数)。
     *
     * @param id 分类 ID
     * @return 更新后的分类
     * @throws ScrmException 分类不存在
     */
    @Transactional
    public ScrmKnowledgeCategoryEntity updateCategoryStats(Long id) throws ScrmException {
        ScrmKnowledgeCategoryEntity entity = findCategoryOrThrow(id);
        Specification<ScrmKnowledgeArticleEntity> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("categoryId"), id),
                cb.equal(root.get("status"), STATUS_PUBLISHED));
        List<ScrmKnowledgeArticleEntity> articles = articleRepository.findAll(spec);
        int totalViews = 0;
        int totalLikes = 0;
        for (ScrmKnowledgeArticleEntity a : articles) {
            totalViews += safeInt(a.getViewCount());
            totalLikes += safeInt(a.getLikeCount());
        }
        entity.setArticleCount(articles.size());
        entity.setTotalViews(totalViews);
        entity.setTotalLikes(totalLikes);
        return categoryRepository.save(entity);
    }

    /**
     * 查询分类下的已发布文章 (分页)。
     *
     * @param categoryId 分类 ID
     * @param pageable   分页参数
     * @return 文章分页结果
     * @throws ScrmException 分类不存在
     */
    @Transactional(readOnly = true)
    public Page<ScrmKnowledgeArticleEntity> getArticlesByCategory(Long categoryId, Pageable pageable)
            throws ScrmException {
        findCategoryOrThrow(categoryId);
        return articleRepository.findByCategoryIdAndStatus(
                 categoryId, STATUS_PUBLISHED, pageable);
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 填充分类层级与路径。
     *
     * @param entity   分类实体
     * @param parentId 父分类 ID
     * @throws ScrmException 父分类不存在
     */
    private void populateCategoryHierarchy(
            ScrmKnowledgeCategoryEntity entity, Long parentId) throws ScrmException {
        if (parentId == null) {
            entity.setCategoryLevel(1);
            entity.setCategoryPath(entity.getId() + "/");
        } else {
            ScrmKnowledgeCategoryEntity parent = findCategoryOrThrow(parentId);
            entity.setCategoryLevel(safeInt(parent.getCategoryLevel()) + 1);
            String parentPath = parent.getCategoryPath() != null ? parent.getCategoryPath() : "";
            entity.setCategoryPath(parentPath + entity.getId() + "/");
        }
    }

    /**
     * 递归构建分类树。
     *
     * @param byParent 按父 ID 分组的分类 Map
     * @param parentId 当前父 ID
     * @return 树节点列表
     */
    private List<Map<String, Object>> buildCategoryTree(Map<Long, List<ScrmKnowledgeCategoryEntity>> byParent,
                                                         Long parentId) {
        List<ScrmKnowledgeCategoryEntity> children = byParent.get(parentId);
        if (children == null || children.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (ScrmKnowledgeCategoryEntity c : children) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", c.getId());
            node.put("categoryName", c.getCategoryName());
            node.put("categoryCode", c.getCategoryCode());
            node.put("categoryLevel", c.getCategoryLevel());
            node.put("categoryPath", c.getCategoryPath());
            node.put("sortOrder", c.getSortOrder());
            node.put("icon", c.getIcon());
            node.put("color", c.getColor());
            node.put("articleCount", safeInt(c.getArticleCount()));
            node.put("totalViews", safeInt(c.getTotalViews()));
            node.put("totalLikes", safeInt(c.getTotalLikes()));
            node.put("enabled", c.getEnabled());
            node.put("children", buildCategoryTree(byParent, c.getId()));
            nodes.add(node);
        }
        return nodes;
    }

    /**
     * 按主键查询分类, 不存在则抛异常。
     *
     * @param id 分类 ID
     * @return 分类实体
     * @throws ScrmException 分类不存在
     */
    ScrmKnowledgeCategoryEntity findCategoryOrThrow(Long id) throws ScrmException {
        ScrmKnowledgeCategoryEntity entity = categoryRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "知识分类不存在: id=" + id));
        return entity;
    }

    /**
     * 安全获取 Integer 值 (null 视为 0)。
     *
     * @param value 整数值
     * @return 非空整数
     */
    static int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    /**
     * 将对象转换为 long 数值。
     *
     * @param obj 对象
     * @return long 值, 不可转换时返回 0
     */
    static long toLong(Object obj) {
        if (obj == null) {
            return 0;
        }
        if (obj instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(obj.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 获取当前操作人 (优先从 UserContext 获取)。
     *
     * @return 操作人用户名
     */
    static String currentOperator() {
        String username = UserContext.getUsername();
        return username != null ? username : DEFAULT_OPERATOR;
    }
}