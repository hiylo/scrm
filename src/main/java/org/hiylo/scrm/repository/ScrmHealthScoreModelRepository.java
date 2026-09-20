/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmHealthScoreModelRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmHealthScoreModelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * SCRM 客户健康度评分模型数据访问层。
 * <p>
 * 提供按 + 模型编码定位模型 (供 {@code getModelByCode} 使用), 默认模型查询 (供
 * {@code setDefault} 切换默认时清理旧默认), 以及增量更新应用次数与最近应用时间。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmHealthScoreModelRepository extends JpaRepository<ScrmHealthScoreModelEntity, Long>,
        JpaSpecificationExecutor<ScrmHealthScoreModelEntity> {

    /**
     * 按与模型编码查询模型 (modelCode 唯一)。
     *
     * @param modelCode 模型编码
     * @return 模型 (可能为空)
     */
    Optional<ScrmHealthScoreModelEntity> findByModelCode(String modelCode);

    /**
     * 查询账号下的默认模型。
     *
     * @return 默认模型 (可能为空)
     */
    Optional<ScrmHealthScoreModelEntity> findByIsDefaultTrue();

    /**
     * 增量更新模型应用次数与最近应用时间 (避免乐观锁冲突, 直接 SQL 更新)。
     *
     * @param modelId   模型 ID
     * @param appliedAt 应用时间
     * @return 受影响行数 (1 表示更新成功)
     */
    @Modifying
    @Query(value = "UPDATE ScrmHealthScoreModelEntity m SET m.appliedCount = COALESCE(m.appliedCount, 0) + 1,"
                          + "m.lastAppliedAt = :appliedAt WHERE m.id = :modelId")
    int incrementAppliedCount(@Param("modelId") Long modelId, @Param("appliedAt") LocalDateTime appliedAt);
}
