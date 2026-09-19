/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLeadDimensionRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmLeadDimensionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 销售线索评分维度数据访问层。
 * <p>
 * 提供按 + 维度编码定位维度 (供 {@code getDimensionByCode} 使用), 按加载启用维度
 * (供评分计算时遍历), 以及增量更新使用次数。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmLeadDimensionRepository extends JpaRepository<ScrmLeadDimensionEntity, Long>,
        JpaSpecificationExecutor<ScrmLeadDimensionEntity> {

    /**
     * 按与维度编码查询维度 (dimensionCode 唯一)。
     *
     * @param dimensionCode 维度编码
     * @return 维度 (可能为空)
     */
    Optional<ScrmLeadDimensionEntity> findByDimensionCode(String dimensionCode);

    /**
     * 按加载全部启用维度 (供评分计算时遍历)。
     *
     * @return 启用维度列表
     */
    List<ScrmLeadDimensionEntity> findByEnabledTrue();

    /**
     * 增量更新维度使用次数 (避免乐观锁冲突, 直接 SQL 更新)。
     *
     * @param dimensionId 维度 ID
     * @return 受影响行数 (1 表示更新成功)
     */
    @Modifying
    @Query("UPDATE ScrmLeadDimensionEntity d SET d.usageCount = COALESCE(d.usageCount, 0) + 1 "
            + "WHERE d.id = :dimensionId")
    int incrementUsageCount(@Param("dimensionId") Long dimensionId);
}
