/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSalesRankingRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmSalesRankingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;

/**
 * SCRM 销售业绩排名数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmSalesRankingRepository extends JpaRepository<ScrmSalesRankingEntity, Long>,
        JpaSpecificationExecutor<ScrmSalesRankingEntity> {

    /**
     * 按 ID、周期、指标类型与目标对象类型查询排名 (按 rank 升序)。
     *
     * @param periodType   周期类型
     * @param periodStart  周期开始
     * @param periodEnd    周期结束
     * @param metricType   指标类型
     * @param targetType   目标对象类型
     * @return 排名列表
     */
    List<ScrmSalesRankingEntity> findByPeriodTypeAndPeriodStartAndPeriodEndAndMetricTypeAndTargetTypeOrderByRankAsc(String periodType, LocalDate periodStart, LocalDate periodEnd,
            String metricType, String targetType);

    /**
     * 按 ID、周期、目标对象类型与目标对象 ID 查询某对象排名列表 (我的排名)。
     *
     * @param periodType   周期类型
     * @param periodStart  周期开始
     * @param periodEnd    周期结束
     * @param targetType   目标对象类型
     * @param targetId     目标对象 ID
     * @return 排名列表
     */
    List<ScrmSalesRankingEntity> findByPeriodTypeAndPeriodStartAndPeriodEndAndTargetTypeAndTargetIdOrderByRankingDateDesc(String periodType, LocalDate periodStart, LocalDate periodEnd,
            String targetType, String targetId);

    /**
     * 按 ID、周期、指标类型与目标对象类型删除排名 (重算前清理)。
     *
     * @param periodType   周期类型
     * @param periodStart  周期开始
     * @param periodEnd    周期结束
     * @param metricType   指标类型
     * @param targetType   目标对象类型
     * @return 删除条数
     */
    long deleteByPeriodTypeAndPeriodStartAndPeriodEndAndMetricTypeAndTargetType(String periodType, LocalDate periodStart, LocalDate periodEnd,
            String metricType, String targetType);
}
