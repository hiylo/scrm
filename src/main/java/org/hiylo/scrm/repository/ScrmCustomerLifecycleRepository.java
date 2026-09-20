/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerLifecycleRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCustomerLifecycleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * SCRM 客户当前生命周期阶段数据访问层。
 * <p>
 * 提供按客户加载生命周期、按阶段统计客户数能力, 供
 * {@code ScrmLifecycleService} 客户阶段管理与统计使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCustomerLifecycleRepository extends JpaRepository<ScrmCustomerLifecycleEntity, Long>,
        JpaSpecificationExecutor<ScrmCustomerLifecycleEntity> {

    /**
     * 按客户 ID 加载客户生命周期 (同客户唯一)。
     *
     * @param customerId 客户 ID
     * @return 客户生命周期实体 (可能不存在)
     */
    Optional<ScrmCustomerLifecycleEntity> findByCustomerId(Long customerId);

    /**
     * 按与当前阶段统计客户数 (统计用)。
     *
     * @param currentStageId 当前阶段 ID
     * @return 客户数
     */
    long countByCurrentStageId(Long currentStageId);

    /**
     * 按与当前阶段统计超期客户数。
     *
     * @param currentStageId 当前阶段 ID
     * @return 超期客户数
     */
    long countByCurrentStageIdAndIsOverdueTrue(Long currentStageId);

    /**
     * 按统计全部客户数。
     *
     * @return 客户数
     */
}
