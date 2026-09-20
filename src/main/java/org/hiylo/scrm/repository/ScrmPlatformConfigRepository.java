/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPlatformConfigRepository.java
 * Date : 2026/07/29 21:19:51
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmPlatformConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 平台配置数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmPlatformConfigRepository extends JpaRepository<ScrmPlatformConfigEntity, Long> {

    /**
     * 根据账号 ID 和平台类型查询配置（唯一约束）。
     *
     * @param platformType 平台类型
     * @return 平台配置（可能为空）
     */
    Optional<ScrmPlatformConfigEntity> findByPlatformType(String platformType);

    /**
     * 根据账号 ID 查询所有平台配置。
     *
     * @return 平台配置列表
     */

    /**
     * 根据账号 ID 和平台类型删除配置。
     *
     * @param platformType 平台类型
     */
    void deleteByPlatformType(String platformType);
}
