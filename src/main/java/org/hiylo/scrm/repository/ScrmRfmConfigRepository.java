/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmRfmConfigRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmRfmConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * SCRM RFM 配置数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmRfmConfigRepository extends JpaRepository<ScrmRfmConfigEntity, Long>,
        JpaSpecificationExecutor<ScrmRfmConfigEntity> {

    /**
     * 按 ID 查询配置列表。
     *
     * @return 配置列表
     */

    /**
     * 按启用状态查询配置列表。
     *
     * @param enabled  启用状态
     * @return 配置列表
     */
    List<ScrmRfmConfigEntity> findByEnabled(Boolean enabled);

    /**
     * 查询指定账号下的默认配置。
     *
     * @return 默认配置 (可能为空)
     */
    Optional<ScrmRfmConfigEntity> findByIsDefaultTrue();

    /**
     * 按 ID 统计默认配置数量 (用于设置默认前的唯一性校验)。
     *
     * @return 默认配置数量
     */
    long countByIsDefaultTrue();
}
