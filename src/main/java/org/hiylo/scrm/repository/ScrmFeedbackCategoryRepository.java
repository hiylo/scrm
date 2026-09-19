/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFeedbackCategoryRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmFeedbackCategoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 反馈分类数据访问层。
 * <p>
 * 提供按分类编码查询、按加载全量分类 (用于排序) 以及按启用状态分页查询,
 * 供 {@code ScrmFeedbackService} 使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmFeedbackCategoryRepository extends JpaRepository<ScrmFeedbackCategoryEntity, Long>,
        JpaSpecificationExecutor<ScrmFeedbackCategoryEntity> {

    /**
     * 按分类编码查询分类。
     *
     * @param categoryCode 分类编码
     * @return 分类 (可能为空)
     */
    Optional<ScrmFeedbackCategoryEntity> findByCategoryCode(String categoryCode);

    /**
     * 按加载全量分类 (用于排序)。
     *
     * @return 分类列表
     */

    /**
     * 按与启用状态分页查询分类。
     *
     * @param enabled  启用状态
     * @param pageable 分页参数
     * @return 分类分页结果
     */
    Page<ScrmFeedbackCategoryEntity> findByEnabled(Boolean enabled, Pageable pageable);

    /**
     * 按分页查询分类。
     *
     * @param pageable 分页参数
     * @return 分类分页结果
     */
}
