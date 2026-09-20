/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTagRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmTagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 客户标签定义数据访问层。
 * <p>
 * 提供按与标签编码查询标签 (唯一性校验)、按分组查询标签列表 (刷新分组标签数) 能力,
 * 供 {@code ScrmTagSystemService} 使用。标签列表通过 Specification 动态过滤, 支持按
 * 分组、类型、启用状态与关键字查询。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmTagRepository extends JpaRepository<ScrmTagEntity, Long>,
        JpaSpecificationExecutor<ScrmTagEntity> {

    /**
     * 按与标签编码查询标签 (用于唯一性校验与按编码查询)。
     *
     * @param tagCode  标签编码
     * @return 标签 (可能为空)
     */
    Optional<ScrmTagEntity> findByTagCode(String tagCode);

    /**
     * 按与分组 ID 查询标签列表 (用于刷新分组的标签数与级联检查)。
     *
     * @param groupId  分组 ID
     * @return 标签列表
     */
    List<ScrmTagEntity> findByGroupId(Long groupId);

    /**
     * 按与分组 ID 查询标签数量 (用于分组标签数统计)。
     *
     * @param groupId  分组 ID
     * @return 标签数量
     */
    long countByGroupId(Long groupId);

    /**
     * 按与分组 ID 列表批量查询标签 (用于统计分组覆盖率)。
     *
     * @param groupIds 分组 ID 列表
     * @return 标签列表
     */
    List<ScrmTagEntity> findByGroupIdIn(Iterable<Long> groupIds);
}
