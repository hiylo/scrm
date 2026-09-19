/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMessageTemplateRepository.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmMessageTemplateEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 消息模板数据访问层。
 * <p>
 * 提供按模板名称查询、按分类加载启用模板、分页过滤与适用模板查询等能力, 供
 * {@code ScrmMessageTemplateService} 使用。发送消息选用模板时通过
 * {@link #findApplicableTemplates} 按与平台类型加载启用模板 (含通用模板)。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmMessageTemplateRepository extends JpaRepository<ScrmMessageTemplateEntity, Long>,
        JpaSpecificationExecutor<ScrmMessageTemplateEntity> {

    /**
     * 根据模板名称查询模板。
     *
     * @param templateName 模板名称
     * @return 模板实体（可能为空）
     */
    Optional<ScrmMessageTemplateEntity> findByTemplateName(String templateName);

    /**
     * 按分类加载启用模板并按排序值升序排列（数字越小越靠前）。
     *
     * @param category 分类
     * @return 启用模板列表（按 sortOrder ASC）
     */
    List<ScrmMessageTemplateEntity> findByCategoryAndEnabledTrueOrderBySortOrderAsc(String category);

    /**
     * 加载全部启用模板并按排序值升序排列。
     *
     * @return 启用模板列表（按 sortOrder ASC）
     */
    List<ScrmMessageTemplateEntity> findByEnabledTrueOrderBySortOrderAsc();

    /**
     * 按分类分页查询模板。
     *
     * @param category 分类
     * @param pageable 分页参数
     * @return 模板分页结果
     */
    Page<ScrmMessageTemplateEntity> findByCategory(String category, Pageable pageable);

    /**
     * 按启用状态分页查询模板。
     *
     * @param enabled  启用状态
     * @param pageable 分页参数
     * @return 模板分页结果
     */
    Page<ScrmMessageTemplateEntity> findByEnabled(Boolean enabled, Pageable pageable);

    /**
     * 按模板名称或分类模糊匹配分页查询。
     *
     * @param name     模板名称关键字
     * @param category 分类关键字
     * @param pageable 分页参数
     * @return 模板分页结果
     */
    Page<ScrmMessageTemplateEntity> findByTemplateNameContainingOrCategoryContaining(
            String name, String category, Pageable pageable);

    /**
     * 查询适用模板: 按隔离, 平台类型匹配 (含通用模板 platformType 为空), 仅返回启用模板,
     * 按排序值升序排列。
     *
     * @param platformType 平台类型（可空, 空则不按平台过滤）
     * @return 适用模板列表（按 sortOrder ASC）
     */
    @Query(value = "SELECT t FROM ScrmMessageTemplateEntity t WHERE (:platformType IS NULL OR t.platformType ="
                          + ":platformType OR t.platformType IS NULL ) AND t.enabled = true ORDER BY t.sortOrder ASC")
    List<ScrmMessageTemplateEntity> findApplicableTemplates(
                                                            @Param("platformType") String platformType);

}
