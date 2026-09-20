/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLtvModelRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmLtvModelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * SCRM LTV 模型配置数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmLtvModelRepository extends JpaRepository<ScrmLtvModelEntity, Long>,
        JpaSpecificationExecutor<ScrmLtvModelEntity> {

    /**
     * 按 ID 查询模型列表。
     *
     * @return 模型列表
     */

    /**
     * 按模型编码查询模型 (唯一性校验用)。
     *
     * @param modelCode 模型编码
     * @return 模型 (可能为空)
     */
    Optional<ScrmLtvModelEntity> findByModelCode(String modelCode);

    /**
     * 查询指定账号下的默认模型。
     *
     * @return 默认模型 (可能为空)
     */
    Optional<ScrmLtvModelEntity> findByIsDefaultTrue();

    /**
     * 按 ID 统计默认模型数量 (用于设置默认前的唯一性校验)。
     *
     * @return 默认模型数量
     */
    long countByIsDefaultTrue();

    /**
     * 按 ID 统计指定模型编码数量 (用于编码唯一性校验)。
     *
     * @param modelCode 模型编码
     * @return 数量
     */
    long countByModelCode(String modelCode);
}
