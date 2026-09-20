/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLtvCohortRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmLtvCohortEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * SCRM LTV 分组分析数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmLtvCohortRepository extends JpaRepository<ScrmLtvCohortEntity, Long>,
        JpaSpecificationExecutor<ScrmLtvCohortEntity> {

    /**
     * 按分组类型查询分组列表。
     *
     * @param cohortType 分组类型
     * @return 分组列表
     */
    List<ScrmLtvCohortEntity> findByCohortType(String cohortType);

    /**
     * 按分组类型与分组键值查询分组。
     *
     * @param cohortType 分组类型
     * @param cohortKey  分组键值
     * @return 分组列表
     */
    List<ScrmLtvCohortEntity> findByCohortTypeAndCohortKey(String cohortType, String cohortKey);

    /**
     * 按分组类型按平均 LTV 降序查询分组趋势 (分组对比用)。
     *
     * @param cohortType 分组类型
     * @return 分组列表
     */
    List<ScrmLtvCohortEntity> findByCohortTypeOrderByAvgLtvDesc(String cohortType);

    /**
     * 按分组类型聚合: 分组数、总客户数、平均 LTV、平均留存率 (分组对比用)。
     *
     * @param cohortType 分组类型
     * @return Object[]{cohortCount, totalCustomers, avgLtv, avgRetention}
     */
    @Query(value = "SELECT COUNT(*), COALESCE(SUM(cohort_size), 0), COALESCE(AVG(avg_ltv), 0),"
                          + "COALESCE(AVG(retention_rate), 0) FROM scrm.scrm_ltv_cohort WHERE cohort_type = :cohortType",
            nativeQuery = true)
    Object[] cohortTypeAggregation(@Param("cohortType") String cohortType);
}
