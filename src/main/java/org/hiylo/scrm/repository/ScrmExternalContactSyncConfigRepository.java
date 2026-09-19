/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmExternalContactSyncConfigRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmExternalContactSyncConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * SCRM 外部联系人同步配置数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmExternalContactSyncConfigRepository
        extends JpaRepository<ScrmExternalContactSyncConfigEntity, Long>,
        JpaSpecificationExecutor<ScrmExternalContactSyncConfigEntity> {

    /**
     * 按查询全部同步配置。
     *
     * @return 配置列表
     */

    /**
     * 按与启用状态查询同步配置 (调度器扫描可用配置用)。
     *
     * @param enabled  启用状态
     * @return 配置列表
     */
    List<ScrmExternalContactSyncConfigEntity> findByEnabled(Boolean enabled);

    /**
     * 按与平台查询同步配置。
     *
     * @param platform 平台
     * @return 配置列表
     */
    List<ScrmExternalContactSyncConfigEntity> findByPlatform(String platform);
}
