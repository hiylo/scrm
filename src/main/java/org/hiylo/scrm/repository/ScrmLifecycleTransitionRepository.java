/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLifecycleTransitionRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmLifecycleTransitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * SCRM 客户生命周期阶段流转规则数据访问层。
 * <p>
 * 提供按源/目标阶段加载转换规则能力, 供 {@code ScrmLifecycleService}
 * 流转规则管理与事件触发转换使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmLifecycleTransitionRepository extends JpaRepository<ScrmLifecycleTransitionEntity, Long>,
        JpaSpecificationExecutor<ScrmLifecycleTransitionEntity> {

    /**
     * 按源阶段加载启用的转换规则 (按优先级降序)。
     *
     * @param fromStageId 源阶段 ID
     * @param isEnabled   启用状态
     * @return 转换规则列表
     */
    List<ScrmLifecycleTransitionEntity> findByFromStageIdAndIsEnabledOrderByPriorityDesc(Long fromStageId, Boolean isEnabled);

    /**
     * 按目标阶段加载转换规则。
     *
     * @param toStageId 目标阶段 ID
     * @return 转换规则列表
     */
    List<ScrmLifecycleTransitionEntity> findByToStageId(Long toStageId);

    /**
     * 按加载全部启用的转换规则 (按优先级降序)。
     *
     * @param isEnabled 启用状态
     * @return 转换规则列表
     */
    List<ScrmLifecycleTransitionEntity> findByIsEnabledOrderByPriorityDesc(Boolean isEnabled);
}
