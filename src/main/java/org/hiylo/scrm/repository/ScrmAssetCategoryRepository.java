/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAssetCategoryRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmAssetCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 营销素材分类数据访问层。
 * <p>
 * 提供按父分类查询、按加载全量分类、按编码查询等能力, 供
 * {@code ScrmAssetLibraryService} 使用。复杂多条件过滤通过
 * {@link JpaSpecificationExecutor} 由 Service 层动态构造。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmAssetCategoryRepository extends JpaRepository<ScrmAssetCategoryEntity, Long>,
        JpaSpecificationExecutor<ScrmAssetCategoryEntity> {

    /**
     * 按与父分类 ID 查询子分类 (用于级联删除前的引用检查)。
     *
     * @param parentId 父分类 ID
     * @return 子分类列表
     */
    List<ScrmAssetCategoryEntity> findByParentId(Long parentId);

    /**
     * 按加载全量分类 (用于构建分类树)。
     *
     * @return 分类列表
     */

    /**
     * 按与分类编码查询分类。
     *
     * @param categoryCode 分类编码
     * @return 分类 (可能为空)
     */
    Optional<ScrmAssetCategoryEntity> findByCategoryCode(String categoryCode);

    /**
     * 判断分类编码是否已存在。
     *
     * @param categoryCode 分类编码
     * @return true 表示已存在
     */
    boolean existsByCategoryCode(String categoryCode);
}
