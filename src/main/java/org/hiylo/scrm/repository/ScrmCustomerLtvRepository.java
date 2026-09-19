/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerLtvRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCustomerLtvEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * SCRM 客户 LTV 计算结果数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCustomerLtvRepository extends JpaRepository<ScrmCustomerLtvEntity, Long>,
        JpaSpecificationExecutor<ScrmCustomerLtvEntity> {

    /**
     * 按客户 ID 查询最新 LTV 结果 (取该客户最新一条)。
     *
     * @param customerId 客户 ID
     * @return LTV 结果 (可能为空)
     */
    Optional<ScrmCustomerLtvEntity> findFirstByCustomerIdOrderByCalculatedAtDesc(Long customerId);

    /**
     * 按客户 ID 与模型 ID 查询最新 LTV 结果。
     *
     * @param customerId 客户 ID
     * @param modelId    模型 ID
     * @return LTV 结果 (可能为空)
     */
    Optional<ScrmCustomerLtvEntity> findFirstByCustomerIdAndModelIdOrderByCalculatedAtDesc(Long customerId, Long modelId);

    /**
     * 按模型 ID 查询 LTV 结果列表。
     *
     * @param modelId  模型 ID
     * @return LTV 结果列表
     */
    List<ScrmCustomerLtvEntity> findByModelId(Long modelId);

    /**
     * 按客户 ID 删除 LTV 结果 (重新计算前清理)。
     *
     * @param customerId 客户 ID
     * @return 删除条数
     */
    long deleteByCustomerId(Long customerId);

    /**
     * 按客户 ID 与模型 ID 删除 LTV 结果 (重新计算前清理)。
     *
     * @param customerId 客户 ID
     * @param modelId    模型 ID
     * @return 删除条数
     */
    long deleteByCustomerIdAndModelId(Long customerId, Long modelId);

    /**
     * 按模型 ID 删除 LTV 结果 (重新计算全部前清理)。
     *
     * @param modelId  模型 ID
     * @return 删除条数
     */
    long deleteByModelId(Long modelId);

    /**
     * 按 ID 查询高价值客户 (按预测 LTV 降序, 取 Top N)。
     *
     * @param pageable 分页参数 (用于限制条数)
     * @return LTV 结果列表
     */
    List<ScrmCustomerLtvEntity> findAllByOrderByPredictedLtvDesc(Pageable pageable);

    /**
     * 按 ID 查询高流失风险客户 (按流失概率降序, 取 Top N)。
     *
     * @param pageable 分页参数 (用于限制条数)
     * @return LTV 结果列表
     */
    List<ScrmCustomerLtvEntity> findAllByOrderByChurnProbabilityDesc(Pageable pageable);


    /**
     * 按价值层级聚合: 客户数、平均预测 LTV、平均历史 LTV (层级分布统计用)。
     *
     * @return Object[]{valueTier, count, avgPredictedLtv, avgHistoricalLtv}
     */
    @Query(value = "SELECT value_tier, COUNT(*), COALESCE(AVG(predicted_ltv), 0), COALESCE(AVG(historical_ltv), 0) "
                          + "FROM scrm.scrm_customer_ltv WHERE value_tier IS NOT NULL GROUP BY value_tier",
            nativeQuery = true)
    List<Object[]> tierDistribution();

    /**
     * 按计算时间范围统计: 客户数、平均预测 LTV、中位 LTV、平均历史 LTV (LTV 概览统计用)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return Object[]{count, avgPredictedLtv, medianLtv, avgHistoricalLtv}
     */
    @Query(value = "SELECT COUNT(*), COALESCE(AVG(predicted_ltv), 0), COALESCE(PERCENTILE_CONT(0.5) WITHIN GROUP "
                          + "(ORDER BY predicted_ltv), 0), COALESCE(AVG(historical_ltv), 0) FROM scrm.scrm_customer_ltv WHERE "
                          + "(:startTime IS NULL OR calculated_at >= :startTime) AND (:endTime IS NULL OR calculated_at <="
                          + ":endTime)",
            nativeQuery = true)
    Object[] ltvStats(
                      @Param("startTime") LocalDateTime startTime,
                      @Param("endTime") LocalDateTime endTime);

    /**
     * 按模型 ID 统计: 客户数、平均预测 LTV、平均历史 LTV、平均置信度 (模型效果用)。
     *
     * @param modelId  模型 ID
     * @return Object[]{count, avgPredictedLtv, avgHistoricalLtv, avgConfidence}
     */
    @Query(value = "SELECT COUNT(*), COALESCE(AVG(predicted_ltv), 0), COALESCE(AVG(historical_ltv), 0),"
                          + "COALESCE(AVG(confidence_score), 0) FROM scrm.scrm_customer_ltv WHERE model_id = :modelId",
            nativeQuery = true)
    Object[] modelPerformance(@Param("modelId") Long modelId);

    /**
     * 按 ID 按日聚合平均预测 LTV (LTV 趋势用, native query 借助 PostgreSQL to_char)。
     *
     * @param from     起始时间 (含)
     * @return Object[]{date(yyyy-MM-dd), avgLtv}
     */
    @Query(value = "SELECT to_char(calculated_at, 'YYYY-MM-DD') AS d, COALESCE(AVG(predicted_ltv), 0) AS avg_ltv "
                          + "FROM scrm.scrm_customer_ltv WHERE calculated_at >= :from GROUP BY d ORDER BY d",
            nativeQuery = true)
    List<Object[]> dailyAvgLtv(@Param("from") LocalDateTime from);

    /**
     * 按计算时间范围统计: 平均 ROI、平均客户盈利性、总获客成本、总预测 LTV (ROI 统计用)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return Object[]{avgRoi, avgProfitability, totalAcquisitionCost, totalPredictedLtv}
     */
    @Query(value = "SELECT COALESCE(AVG(roi), 0), COALESCE(AVG(customer_profitability), 0),"
                          + "COALESCE(SUM(acquisition_cost), 0), COALESCE(SUM(predicted_ltv), 0) FROM scrm.scrm_customer_ltv "
                          + "WHERE (:startTime IS NULL OR calculated_at >= :startTime) AND (:endTime IS NULL OR calculated_at"
                          + "<= :endTime)",
            nativeQuery = true)
    Object[] roiStats(
                      @Param("startTime") LocalDateTime startTime,
                      @Param("endTime") LocalDateTime endTime);
}
