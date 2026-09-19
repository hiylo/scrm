/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmExternalContactSyncTaskRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmExternalContactSyncTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 外部联系人同步任务数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmExternalContactSyncTaskRepository
        extends JpaRepository<ScrmExternalContactSyncTaskEntity, Long>,
        JpaSpecificationExecutor<ScrmExternalContactSyncTaskEntity> {

    /**
     * 按同步配置 ID 查询任务列表。
     *
     * @param configId 同步配置 ID
     * @return 任务列表
     */
    List<ScrmExternalContactSyncTaskEntity> findByConfigId(Long configId);

    /**
     * 按状态聚合任务数 (统计用)。
     *
     * @return Object[]{status, count}
     */
    @Query("SELECT t.status, COUNT(t.id) FROM ScrmExternalContactSyncTaskEntity t GROUP BY t.status")
    List<Object[]> countByStatus();

    /**
     * 按时间区间聚合任务数与成功任务数 (同步统计用)。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return Object[]{totalTasks, successTasks, totalNew, totalUpdate, totalFailed}
     */
    @Query(value = "SELECT COUNT(t.id), SUM(CASE WHEN t.status = 'SUCCESS' THEN 1 ELSE 0 END),"
                          + "COALESCE(SUM(t.newCount), 0), COALESCE(SUM(t.updateCount), 0), COALESCE(SUM(t.failedCount), 0) "
                          + "FROM ScrmExternalContactSyncTaskEntity t WHERE (:startTime IS NULL OR t.startTime >= :startTime) "
                          + "AND (:endTime IS NULL OR t.startTime <= :endTime)")
    Object[] aggregateStats(
                             @Param("startTime") LocalDateTime startTime,
                             @Param("endTime") LocalDateTime endTime);

    /**
     * 按配置 ID 聚合任务数与成功任务数 (配置统计用)。
     *
     * @param configId 同步配置 ID
     * @return Object[]{totalTasks, successTasks, totalNew, totalUpdate, totalFailed}
     */
    @Query(value = "SELECT COUNT(t.id), SUM(CASE WHEN t.status = 'SUCCESS' THEN 1 ELSE 0 END),"
                          + "COALESCE(SUM(t.newCount), 0), COALESCE(SUM(t.updateCount), 0), COALESCE(SUM(t.failedCount), 0) "
                          + "FROM ScrmExternalContactSyncTaskEntity t WHERE t.configId = :configId")
    Object[] aggregateStatsByConfig(
                                     @Param("configId") Long configId);

    /**
     * 按日聚合任务创建数 (同步趋势用, native query 借助 PostgreSQL to_char)。
     *
     * @param from     起始时间 (含)
     * @return Object[]{date(yyyy-MM-dd), count}
     */
    default List<Object[]> dailyCountByCreateTime(LocalDateTime from) {
        return DailyStatsRepository.toRows(DailyStatsRepository.getInstance().countByDay(
                "scrm.scrm_external_contact_sync_task", "create_time", "",
                Map.of(), from, null));
    }
}
