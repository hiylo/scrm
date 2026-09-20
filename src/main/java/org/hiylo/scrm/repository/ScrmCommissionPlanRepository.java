/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCommissionPlanRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCommissionPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * SCRM 销售佣金方案数据访问层。
 * <p>
 * 提供按 + 方案编码定位方案 (供 {@code getPlanByCode} 使用), 默认方案查询,
 * 以及切换默认方案时清理旧默认标记。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCommissionPlanRepository extends JpaRepository<ScrmCommissionPlanEntity, Long>,
        JpaSpecificationExecutor<ScrmCommissionPlanEntity> {

    /**
     * 按与方案编码查询方案 (planCode 唯一)。
     *
     * @param planCode 方案编码
     * @return 方案 (可能为空)
     */
    Optional<ScrmCommissionPlanEntity> findByPlanCode(String planCode);

    /**
     * 查询账号下的默认方案。
     *
     * @return 默认方案 (可能为空)
     */
    Optional<ScrmCommissionPlanEntity> findByIsDefaultTrue();

    /**
     * 清理账号下所有默认方案标记 (供切换默认方案时使用)。
     *
     * @return 受影响行数
     */
    @Modifying
    @Query("UPDATE ScrmCommissionPlanEntity p SET p.isDefault = false WHERE p.isDefault = true")
    int clearDefaultFlag();
}
