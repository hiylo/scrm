/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSpeechCategoryRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmSpeechCategoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * SCRM 话术分类数据访问层。
 * <p>
 * 提供按父分类查询、按加载全量分类等能力, 供 {@code ScrmSpeechLibraryService} 使用。
 * 复杂多条件过滤通过 {@link JpaSpecificationExecutor} 由 Service 层动态构造。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmSpeechCategoryRepository extends JpaRepository<ScrmSpeechCategoryEntity, Long>,
        JpaSpecificationExecutor<ScrmSpeechCategoryEntity> {

    /**
     * 按与父分类 ID 分页查询子分类。
     *
     * @param parentId 父分类 ID
     * @param pageable 分页参数
     * @return 分类分页结果
     */
    Page<ScrmSpeechCategoryEntity> findByParentId(Long parentId, Pageable pageable);

    /**
     * 按加载全量分类 (用于构建分类树)。
     *
     * @return 分类列表
     */

    /**
     * 按与父分类 ID 查询子分类 (用于级联删除前的引用检查)。
     *
     * @param parentId 父分类 ID
     * @return 子分类列表
     */
    List<ScrmSpeechCategoryEntity> findByParentId(Long parentId);
}
