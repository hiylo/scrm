/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLeadScoreRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmLeadScoreEntity;
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
 * SCRM 销售线索评分数据访问层。
 * <p>
 * 提供按 + 客户 + 模型定位唯一评分记录 (供 {@code getScoreByCustomer} 使用), 热线索 /
 * 合格线索列表查询, 按模型加载全量评分 (供 {@code calculateAllScores} 重算), 等级分布与
 * 分数分布统计, 以及转化统计与转化漏斗。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmLeadScoreRepository extends JpaRepository<ScrmLeadScoreEntity, Long>,
        JpaSpecificationExecutor<ScrmLeadScoreEntity> {

    /**
     * 按 + 客户 + 模型查询评分记录 (客户 + 模型唯一)。
     *
     * @param customerId 客户 ID
     * @param modelId    模型 ID
     * @return 评分记录 (可能为空)
     */
    Optional<ScrmLeadScoreEntity> findByCustomerIdAndModelId(Long customerId, Long modelId);

    /**
     * 按 + 模型加载全部评分记录 (供 calculateAllScores 重算)。
     *
     * @param modelId  模型 ID
     * @return 评分记录列表
     */
    List<ScrmLeadScoreEntity> findByModelId(Long modelId);

    /**
     * 按与模型分页查询热线索 (按总分倒序)。
     *
     * @param modelId  模型 ID
     * @param pageable 分页参数
     * @return 评分分页结果
     */
    Page<ScrmLeadScoreEntity> findByModelIdAndIsHotLeadTrueOrderByTotalScoreDesc(Long modelId, Pageable pageable);

    /**
     * 按与模型分页查询合格线索 (按总分倒序)。
     *
     * @param modelId  模型 ID
     * @param pageable 分页参数
     * @return 评分分页结果
     */
    Page<ScrmLeadScoreEntity> findByModelIdAndIsQualifiedTrueOrderByTotalScoreDesc(Long modelId, Pageable pageable);

    /**
     * 按与负责人分页查询分配的线索 (按分配时间倒序)。
     *
     * @param assignedTo 负责人用户标识
     * @param pageable   分页参数
     * @return 评分分页结果
     */
    Page<ScrmLeadScoreEntity> findByAssignedToOrderByAssignedAtDesc(String assignedTo, Pageable pageable);

    /**
     * 等级分布统计: 按模型聚合各等级客户数。
     *
     * @param modelId  模型 ID
     * @return Object[] 列表: [grade, customerCount]
     */
    @Query(value = "SELECT s.grade, COUNT(s) FROM ScrmLeadScoreEntity s WHERE s.modelId = :modelId AND s.grade IS "
                          + "NOT NULL GROUP BY s.grade")
    List<Object[]> countByGrade(@Param("modelId") Long modelId);

    /**
     * 分数区间分布统计: 按分数区间 [0,20), [20,40), [40,60), [60,80), [80,100] 聚合客户数。
     *
     * @param modelId  模型 ID
     * @return Object[] 列表: [bucket, customerCount]
     */
    @Query(value = "SELECT CASE WHEN s.totalScore < 20 THEN '0-20' WHEN s.totalScore < 40 THEN '20-40' WHEN "
                          + "s.totalScore < 60 THEN '40-60' WHEN s.totalScore < 80 THEN '60-80' ELSE '80-100' END, COUNT(s) "
                          + "FROM ScrmLeadScoreEntity s WHERE s.modelId = :modelId GROUP BY CASE WHEN s.totalScore < 20 THEN"
                          + "'0-20' WHEN s.totalScore < 40 THEN '20-40' WHEN s.totalScore < 60 THEN '40-60' WHEN s.totalScore"
                          + "< 80 THEN '60-80' ELSE '80-100' END")
    List<Object[]> countByScoreBucket(@Param("modelId") Long modelId);

    /**
     * 转化统计: 总线索数 / 已转化数 / 平均转化概率 / 平均转化价值。
     *
     * @param modelId  模型 ID
     * @return Object[]: [totalLeads, convertedLeads, avgConversionProbability, totalConversionValue]
     */
    @Query(value = "SELECT COUNT(s), SUM(CASE WHEN s.isConverted = TRUE THEN 1 ELSE 0 END),"
                          + "COALESCE(AVG(s.conversionProbability), 0), COALESCE(SUM(s.conversionValue), 0) FROM "
                          + "ScrmLeadScoreEntity s WHERE s.modelId = :modelId")
    Object[] getConversionStats(@Param("modelId") Long modelId);

    /**
     * 转化漏斗: 按等级聚合线索数与转化数。
     *
     * @param modelId  模型 ID
     * @return Object[] 列表: [grade, totalLeads, convertedLeads]
     */
    @Query(value = "SELECT s.grade, COUNT(s), SUM(CASE WHEN s.isConverted = TRUE THEN 1 ELSE 0 END) FROM "
                          + "ScrmLeadScoreEntity s WHERE s.modelId = :modelId AND s.grade IS NOT NULL GROUP BY s.grade")
    List<Object[]> getConversionFunnel(@Param("modelId") Long modelId);

    /**
     * 时间区间内的线索统计: 总数 / 已转化数 / 平均分。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return Object[]: [totalLeads, convertedLeads, avgScore]
     */
    @Query(value = "SELECT COUNT(s), SUM(CASE WHEN s.isConverted = TRUE THEN 1 ELSE 0 END),"
                          + "COALESCE(AVG(s.totalScore), 0) FROM ScrmLeadScoreEntity s WHERE (:startTime IS NULL OR "
                          + "s.lastCalculatedAt >= :startTime) AND (:endTime IS NULL OR s.lastCalculatedAt <= :endTime)")
    Object[] getLeadStats(
                          @Param("startTime") LocalDateTime startTime,
                          @Param("endTime") LocalDateTime endTime);

    /**
     * 高分线索排行: 按总分倒序分页。
     *
     * @param pageable 分页参数
     * @return 评分分页结果 (按 totalScore DESC)
     */
    Page<ScrmLeadScoreEntity> findAllByOrderByTotalScoreDesc(Pageable pageable);
}
