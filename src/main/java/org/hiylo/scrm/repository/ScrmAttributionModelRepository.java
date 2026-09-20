/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAttributionModelRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmAttributionModelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * SCRM 营销效果归因模型数据访问层。
 * <p>
 * 提供按 + 模型编码定位模型 (供 {@code getModelByCode} 使用), 默认模型查询 (供
 * {@code setDefault} 切换默认时清理旧默认), 以及增量更新应用次数与最近应用时间。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmAttributionModelRepository extends JpaRepository<ScrmAttributionModelEntity, Long>,
        JpaSpecificationExecutor<ScrmAttributionModelEntity> {

    /**
     * 按与模型编码查询模型 (modelCode 唯一)。
     *
     * @param modelCode 模型编码
     * @return 模型 (可能为空)
     */
    Optional<ScrmAttributionModelEntity> findByModelCode(String modelCode);

    /**
     * 查询账号下的默认模型。
     *
     * @return 默认模型 (可能为空)
     */
    Optional<ScrmAttributionModelEntity> findByIsDefaultTrue();

    /**
     * 增量更新模型应用次数与最近应用时间 (避免乐观锁冲突, 直接 SQL 更新)。
     *
     * @param modelId   模型 ID
     * @param appliedAt 应用时间
     * @return 受影响行数 (1 表示更新成功)
     */
    @Modifying
    @Query(value = "UPDATE ScrmAttributionModelEntity m SET m.appliedCount = COALESCE(m.appliedCount, 0) + 1,"
                          + "m.lastAppliedAt = :appliedAt WHERE m.id = :modelId")
    int incrementAppliedCount(@Param("modelId") Long modelId, @Param("appliedAt") LocalDateTime appliedAt);

    /**
     * 清理账号下所有默认模型标记 (供切换默认模型时使用)。
     *
     * @return 受影响行数
     */
    @Modifying
    @Query("UPDATE ScrmAttributionModelEntity m SET m.isDefault = false WHERE m.isDefault = true")
    int clearDefaultFlag();
}
