/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmQuickReplyCategoryRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmQuickReplyCategoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * SCRM 快捷回复分类数据访问层。
 * <p>
 * 提供按与平台过滤查询能力, 复杂多条件过滤通过
 * {@link JpaSpecificationExecutor} 由 Service 层动态构造。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmQuickReplyCategoryRepository extends JpaRepository<ScrmQuickReplyCategoryEntity, Long>,
        JpaSpecificationExecutor<ScrmQuickReplyCategoryEntity> {

    /**
     * 按加载全量分类 (用于排序)。
     *
     * @return 分类列表
     */

    /**
     * 按与平台类型分页查询分类。
     *
     * @param platformType 平台类型
     * @param pageable     分页参数
     * @return 分类分页结果
     */
    Page<ScrmQuickReplyCategoryEntity> findByPlatformType(String platformType,
            Pageable pageable);

    /**
     * 按分页查询分类。
     *
     * @param pageable 分页参数
     * @return 分类分页结果
     */
}
