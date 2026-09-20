/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTagGroupRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmTagGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * SCRM 客户标签分组数据访问层。
 * <p>
 * 提供按与分组编码查询分组 (唯一性校验) 能力, 供 {@code ScrmTagSystemService} 使用。
 * 分组列表通过 Specification 动态过滤, 支持按启用状态与关键字查询。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmTagGroupRepository extends JpaRepository<ScrmTagGroupEntity, Long>,
        JpaSpecificationExecutor<ScrmTagGroupEntity> {

    /**
     * 按与分组编码查询分组 (用于唯一性校验)。
     *
     * @param groupCode 分组编码
     * @return 分组 (可能为空)
     */
    Optional<ScrmTagGroupEntity> findByGroupCode(String groupCode);
}
