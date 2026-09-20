/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMessageTemplateGroupRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmMessageTemplateGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 消息模板分组数据访问层。
 * <p>
 * 提供按分组编码、父分组、分组类型查询等能力, 供 {@code ScrmMessageTemplateCenterService}
 * 使用。所有查询均按隔离。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmMessageTemplateGroupRepository
        extends JpaRepository<ScrmMessageTemplateGroupEntity, Long>,
                JpaSpecificationExecutor<ScrmMessageTemplateGroupEntity> {

    /**
     * 按与分组编码查询分组 (数据隔离 + 业务编码唯一)。
     *
     * @param groupCode 分组编码
     * @return 分组实体（可能为空）
     */
    Optional<ScrmMessageTemplateGroupEntity> findByGroupCode(String groupCode);

    /**
     * 按与父分组 ID 查询子分组, 按排序值升序排列。
     *
     * @param parentGroupId 父分组 ID
     * @return 子分组列表（按 sortOrder ASC）
     */
    List<ScrmMessageTemplateGroupEntity> findByParentGroupIdOrderBySortOrderAsc(Long parentGroupId);

    /**
     * 查询账号下所有顶级分组 (parentGroupId 为空), 按排序值升序排列。
     *
     * @return 顶级分组列表（按 sortOrder ASC）
     */
    List<ScrmMessageTemplateGroupEntity> findByParentGroupIdIsNullOrderBySortOrderAsc();

    /**
     * 按与分组类型查询启用的分组, 按排序值升序排列。
     *
     * @param groupType 分组类型
     * @param enabled   启用状态
     * @return 分组列表（按 sortOrder ASC）
     */
    List<ScrmMessageTemplateGroupEntity> findByGroupTypeAndEnabledOrderBySortOrderAsc(String groupType, Boolean enabled);

    /**
     * 按与启用状态查询分组, 按排序值升序排列。
     *
     * @param enabled  启用状态
     * @return 分组列表（按 sortOrder ASC）
     */
    List<ScrmMessageTemplateGroupEntity> findByEnabledOrderBySortOrderAsc(Boolean enabled);

    /**
     * 查询账号下全部分组, 按排序值升序排列 (用于构建分组树)。
     *
     * @return 全部分组列表（按 sortOrder ASC）
     */
    List<ScrmMessageTemplateGroupEntity> findAllByOrderBySortOrderAsc();

    /**
     * 按与分组 ID 查询分组是否存在 (校验归属用)。
     *
     * @param id       分组 ID
     * @return 是否存在
     */
    boolean existsById(Long id);
}
