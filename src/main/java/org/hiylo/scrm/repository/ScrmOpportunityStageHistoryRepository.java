/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmOpportunityStageHistoryRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmOpportunityStageHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * SCRM 商机阶段变更历史数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmOpportunityStageHistoryRepository
        extends JpaRepository<ScrmOpportunityStageHistoryEntity, Long> {

    /**
     * 按商机 ID 查询变更历史, 按变更时间升序排列 (时间线展示)。
     *
     * @param opportunityId 商机 ID
     * @return 变更历史列表
     */
    List<ScrmOpportunityStageHistoryEntity> findByOpportunityIdOrderByChangedAtAsc(Long opportunityId);

    /**
     * 查询商机最近一次阶段变更记录 (用于计算在当前阶段的停留天数)。
     *
     * @param opportunityId 商机 ID
     * @return 最近一次变更记录 (按 id 倒序取首条)
     */
    List<ScrmOpportunityStageHistoryEntity> findByOpportunityIdOrderByIdDesc(Long opportunityId);

    /**
     * 按目标阶段聚合平均停留天数 (漏斗分析用)。
     *
     * @return Object[]{toStageId, avgDurationDays}
     */
    @org.springframework.data.jpa.repository.Query(value = "SELECT to_stage_id, "
            + "COALESCE(AVG(duration_days), 0)"
            + "FROM scrm.scrm_opportunity_stage_history"
            + "WHERE duration_days IS NOT NULL"
            + "GROUP BY to_stage_id", nativeQuery = true)
    List<Object[]> avgDurationByStage();
}
