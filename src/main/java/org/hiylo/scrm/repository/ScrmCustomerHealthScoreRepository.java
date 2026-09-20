/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerHealthScoreRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCustomerHealthScoreEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * SCRM 客户健康度评分数据访问层。
 * <p>
 * 提供按 + 客户 + 模型定位唯一评分记录 (供 {@code getScoreByCustomer} 使用), 风险客户 /
 * 流失风险列表查询, 按模型加载全量评分 (供 {@code calculateAll} 重算), 等级分布 / 分数分布 /
 * 风险分布统计, 以及时间区间内的健康度统计。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCustomerHealthScoreRepository extends JpaRepository<ScrmCustomerHealthScoreEntity, Long>,
        JpaSpecificationExecutor<ScrmCustomerHealthScoreEntity> {

    /**
     * 按 + 客户 + 模型查询评分记录 (客户 + 模型唯一)。
     *
     * @param customerId 客户 ID
     * @param modelId    模型 ID
     * @return 评分记录 (可能为空)
     */
    Optional<ScrmCustomerHealthScoreEntity> findByCustomerIdAndModelId(Long customerId, Long modelId);

    /**
     * 按 + 模型加载全部评分记录 (供 calculateAll 重算)。
     *
     * @param modelId  模型 ID
     * @return 评分记录列表
     */
    List<ScrmCustomerHealthScoreEntity> findByModelId(Long modelId);

    /**
     * 按分页查询风险客户 (按总分升序, 分越低越危险)。
     *
     * @param pageable 分页参数
     * @return 评分分页结果
     */
    Page<ScrmCustomerHealthScoreEntity> findByIsAtRiskTrueOrderByTotalScoreAsc(Pageable pageable);

    /**
     * 按分页查询危急客户 (按健康等级为 CRITICAL 过滤, 按总分升序)。
     *
     * @param healthLevel 健康等级
     * @param pageable   分页参数
     * @return 评分分页结果
     */
    Page<ScrmCustomerHealthScoreEntity> findByHealthLevelOrderByTotalScoreAsc(String healthLevel, Pageable pageable);

    /**
     * 等级分布统计: 按模型聚合各健康等级客户数。
     *
     * @param modelId  模型 ID
     * @return Object[] 列表: [healthLevel, customerCount]
     */
    @Query(value = "SELECT s.healthLevel, COUNT(s) FROM ScrmCustomerHealthScoreEntity s WHERE s.modelId = :modelId "
                          + "AND s.healthLevel IS NOT NULL GROUP BY s.healthLevel")
    List<Object[]> countByHealthLevel(@Param("modelId") Long modelId);

    /**
     * 分数区间分布统计: 按分数区间 [0,20), [20,40), [40,60), [60,80), [80,100] 聚合客户数。
     *
     * @param modelId  模型 ID
     * @return Object[] 列表: [bucket, customerCount]
     */
    @Query(value = "SELECT CASE WHEN s.totalScore < 20 THEN '0-20' WHEN s.totalScore < 40 THEN '20-40' WHEN "
                          + "s.totalScore < 60 THEN '40-60' WHEN s.totalScore < 80 THEN '60-80' ELSE '80-100' END, COUNT(s) "
                          + "FROM ScrmCustomerHealthScoreEntity s WHERE s.modelId = :modelId GROUP BY CASE WHEN s.totalScore"
                          + "< 20 THEN '0-20' WHEN s.totalScore < 40 THEN '20-40' WHEN s.totalScore < 60 THEN '40-60' WHEN "
                          + "s.totalScore < 80 THEN '60-80' ELSE '80-100' END")
    List<Object[]> countByScoreBucket(@Param("modelId") Long modelId);

    /**
     * 风险等级分布统计: 按模型聚合各风险等级客户数。
     *
     * @param modelId  模型 ID
     * @return Object[] 列表: [riskLevel, customerCount]
     */
    @Query(value = "SELECT s.riskLevel, COUNT(s) FROM ScrmCustomerHealthScoreEntity s WHERE s.modelId = :modelId AND "
                          + "s.riskLevel IS NOT NULL GROUP BY s.riskLevel")
    List<Object[]> countByRiskLevel(@Param("modelId") Long modelId);

    /**
     * 时间区间内的健康度统计: 总数 / 风险客户数 / 平均分。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return Object[]: [total, atRiskCount, avgScore]
     */
    @Query(value = "SELECT COUNT(s), SUM(CASE WHEN s.isAtRisk = TRUE THEN 1 ELSE 0 END), COALESCE(AVG(s.totalScore),"
                          + "0) FROM ScrmCustomerHealthScoreEntity s WHERE (:startTime IS NULL OR s.calculatedAt >= :startTime) "
                          + "AND (:endTime IS NULL OR s.calculatedAt <= :endTime)")
    Object[] getHealthStats(
                            @Param("startTime") LocalDateTime startTime,
                            @Param("endTime") LocalDateTime endTime);
}
