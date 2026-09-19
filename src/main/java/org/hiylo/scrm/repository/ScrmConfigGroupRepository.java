/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmConfigGroupRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmConfigGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 配置分组数据访问层。
 * <p>
 * 提供按与分组编码加载分组, 以及按父分组 / 启用状态等条件查询能力,
 * 供 {@code ScrmSystemConfigService} 的分组管理与统计使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmConfigGroupRepository extends JpaRepository<ScrmConfigGroupEntity, Long>,
        JpaSpecificationExecutor<ScrmConfigGroupEntity> {

    /**
     * 按与分组编码加载分组。
     *
     * @param groupCode 分组编码
     * @return 分组实体 (可能不存在)
     */
    Optional<ScrmConfigGroupEntity> findByGroupCode(String groupCode);

    /**
     * 按与父分组编码查询子分组。
     *
     * @param parentGroupCode 父分组编码
     * @return 子分组列表
     */
    List<ScrmConfigGroupEntity> findByParentGroupCode(String parentGroupCode);

    /**
     * 按查询全部分组 (用于分组树)。
     *
     * @return 分组列表
     */

    /**
     * 按与启用状态查询分组。
     *
     * @param enabled  启用状态
     * @return 分组列表
     */
    List<ScrmConfigGroupEntity> findByEnabled(Boolean enabled);
}
