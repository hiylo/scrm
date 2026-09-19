/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBudgetAllocationRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmBudgetAllocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * SCRM 营销预算分配数据访问层。
 * <p>
 * 提供按 + 方案 ID 查询分配列表, 按状态查询已耗尽分配。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmBudgetAllocationRepository extends JpaRepository<ScrmBudgetAllocationEntity, Long>,
        JpaSpecificationExecutor<ScrmBudgetAllocationEntity> {

    /**
     * 按与方案 ID 查询分配列表。
     *
     * @param planId   方案 ID
     * @return 分配列表
     */
    List<ScrmBudgetAllocationEntity> findByPlanId(Long planId);

    /**
     * 按状态查询分配列表。
     *
     * @param status   状态
     * @return 分配列表
     */
    List<ScrmBudgetAllocationEntity> findByStatus(String status);
}
