/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmKnowledgeCategoryRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmKnowledgeCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 知识分类数据访问层。
 * <p>
 * 提供按 + 父分类 / 编码查询等能力, 供 {@code ScrmKnowledgeBaseService} 使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmKnowledgeCategoryRepository extends JpaRepository<ScrmKnowledgeCategoryEntity, Long>,
        JpaSpecificationExecutor<ScrmKnowledgeCategoryEntity> {

    /**
     * 按与编码查询分类。
     *
     * @param categoryCode 分类编码
     * @return 分类实体 (可选)
     */
    Optional<ScrmKnowledgeCategoryEntity> findByCategoryCode(String categoryCode);

    /**
     * 按与父分类查询子分类 (按 sortOrder ASC)。
     *
     * @param parentId 父分类 ID
     * @return 子分类列表
     */
    List<ScrmKnowledgeCategoryEntity> findByParentIdOrderBySortOrderAscCategoryLevelAsc(Long parentId);

    /**
     * 按查询全部分类 (按 sortOrder ASC)。
     *
     * @return 分类列表
     */
    List<ScrmKnowledgeCategoryEntity> findAllByOrderBySortOrderAscCategoryLevelAsc();

    /**
     * 按与父分类查询子分类数量。
     *
     * @param parentId 父分类 ID
     * @return 子分类数量
     */
    long countByParentId(Long parentId);

    /**
     * 校验分类编码是否已存在。
     *
     * @param categoryCode 分类编码
     * @return 是否存在
     */
    boolean existsByCategoryCode(String categoryCode);
}
