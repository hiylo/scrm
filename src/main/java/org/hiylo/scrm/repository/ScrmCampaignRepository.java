/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCampaignRepository.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCampaignEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 营销任务数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCampaignRepository extends JpaRepository<ScrmCampaignEntity, Long>,
        JpaSpecificationExecutor<ScrmCampaignEntity> {

    /**
     * 根据任务状态查询营销任务列表。
     *
     * @param status 任务状态：DRAFT / RUNNING / PAUSED / COMPLETED / FAILED
     * @return 任务列表
     */
    List<ScrmCampaignEntity> findByStatus(String status);

    /**
     * 根据平台类型查询营销任务列表。
     *
     * @param platformType 平台类型
     * @return 任务列表
     */
    List<ScrmCampaignEntity> findByPlatformType(String platformType);

    /**
     * 根据账号 ID 查询营销任务列表。
     *
     * @return 任务列表
     */

    /**
     * 统计指定账号下的任务总数。
     *
     * @return 任务总数
     */

    /**
     * 按状态聚合任务数（看板用, 避免 N+1）。
     *
     * @return Object[]{status, count}
     */
    @Query("SELECT e.status, COUNT(e.id) FROM ScrmCampaignEntity e GROUP BY e.status")
    List<Object[]> countByStatus();

    /**
     * 按日聚合任务创建数（看板近 7 天趋势用, native query 借助 PostgreSQL to_char）。
     *
     * @param from     起始时间（含）
     * @return Object[]{date(yyyy-MM-dd), count}
     */
    default List<Object[]> dailyCountByCreateTime(LocalDateTime from) {
        return DailyStatsRepository.toRows(DailyStatsRepository.getInstance().countByDay(
                "scrm.scrm_campaign", "create_time", "",
                Map.of(), from, null));
    }

    /**
     * 根据行为流 ID 查询营销任务（回调更新状态时使用）。
     *
     * @param behaviorFlowId 行为流 ID
     * @return 任务列表（同一行为流可能对应多个任务）
     */
    List<ScrmCampaignEntity> findByBehaviorFlowId(Long behaviorFlowId);
}
