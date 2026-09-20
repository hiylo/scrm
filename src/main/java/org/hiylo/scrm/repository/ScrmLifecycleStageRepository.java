/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLifecycleStageRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmLifecycleStageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 客户生命周期阶段数据访问层。
 * <p>
 * 提供按与编码加载阶段、按顺序列出启用的阶段能力, 供
 * {@code ScrmLifecycleService} 阶段管理与统计使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmLifecycleStageRepository extends JpaRepository<ScrmLifecycleStageEntity, Long>,
        JpaSpecificationExecutor<ScrmLifecycleStageEntity> {

    /**
     * 按与阶段编码加载阶段。
     *
     * @param stageCode 阶段编码
     * @return 阶段实体 (可能不存在)
     */
    Optional<ScrmLifecycleStageEntity> findByStageCode(String stageCode);

    /**
     * 按加载全部启用的阶段 (按 stageOrder 升序)。
     *
     * @param enabled  启用状态
     * @return 阶段列表
     */
    List<ScrmLifecycleStageEntity> findByEnabledOrderByStageOrderAsc(Boolean enabled);

    /**
     * 按加载全部阶段 (按 stageOrder 升序)。
     *
     * @return 阶段列表
     */
    List<ScrmLifecycleStageEntity> findAllByOrderByStageOrderAsc();
}
