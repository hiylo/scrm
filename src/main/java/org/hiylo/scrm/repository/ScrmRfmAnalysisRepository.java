/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmRfmAnalysisRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmRfmAnalysisEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * SCRM RFM 分析结果数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmRfmAnalysisRepository extends JpaRepository<ScrmRfmAnalysisEntity, Long>,
        JpaSpecificationExecutor<ScrmRfmAnalysisEntity> {

    /**
     * 按客户 ID 查询分析结果 (取该客户最新一条)。
     *
     * @param customerId 客户 ID
     * @return 分析结果 (可能为空)
     */
    Optional<ScrmRfmAnalysisEntity> findFirstByCustomerIdOrderByCalculatedAtDesc(Long customerId);

    /**
     * 按配置 ID 查询分析结果列表。
     *
     * @param configId 配置 ID
     * @return 分析结果列表
     */
    List<ScrmRfmAnalysisEntity> findByConfigId(Long configId);

    /**
     * 按客户 ID 删除分析结果 (重新计算前清理)。
     *
     * @param customerId 客户 ID
     * @return 删除条数
     */
    long deleteByCustomerId(Long customerId);

    /**
     * 按配置 ID 删除分析结果 (重新计算全部前清理)。
     *
     * @param configId 配置 ID
     * @return 删除条数
     */
    long deleteByConfigId(Long configId);

    /**
     * 按分群大类聚合: 客户数、平均价值分 (分群分布统计用)。
     *
     * @return Object[]{segmentCategory, count, avgValueScore}
     */
    @Query(value = "SELECT segment_category, COUNT(*), COALESCE(AVG(value_score), 0) FROM scrm.scrm_rfm_analysis "
                          + "GROUP BY segment_category",
            nativeQuery = true)
    List<Object[]> segmentDistribution();

}
