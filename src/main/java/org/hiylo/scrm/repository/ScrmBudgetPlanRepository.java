/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBudgetPlanRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmBudgetPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 营销预算方案数据访问层。
 * <p>
 * 提供按 + 方案编码定位方案 (供 {@code getPlanByCode} 使用),
 * 按与财年查询方案 (供 {@code getBudgetStats} 使用),
 * 按状态查询即将到期方案。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmBudgetPlanRepository extends JpaRepository<ScrmBudgetPlanEntity, Long>,
        JpaSpecificationExecutor<ScrmBudgetPlanEntity> {

    /**
     * 按与方案编码查询方案 (planCode 唯一)。
     *
     * @param planCode 方案编码
     * @return 方案 (可能为空)
     */
    Optional<ScrmBudgetPlanEntity> findByPlanCode(String planCode);

    /**
     * 按与财年查询方案列表。
     *
     * @param fiscalYear 财年
     * @return 方案列表
     */
    List<ScrmBudgetPlanEntity> findByFiscalYear(Integer fiscalYear);

    /**
     * 按状态查询方案列表。
     *
     * @param status   状态
     * @return 方案列表
     */
    List<ScrmBudgetPlanEntity> findByStatus(String status);
}
