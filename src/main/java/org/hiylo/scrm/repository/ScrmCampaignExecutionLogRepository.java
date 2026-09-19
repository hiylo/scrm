/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCampaignExecutionLogRepository.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCampaignExecutionLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 营销任务执行日志数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCampaignExecutionLogRepository extends JpaRepository<ScrmCampaignExecutionLogEntity, Long> {

    /**
     * 按营销任务 ID 分页查询执行日志, 操作时间倒序返回。
     *
     * @param campaignId 营销任务 ID
     * @param pageable   分页参数
     * @return 执行日志分页结果
     */
    Page<ScrmCampaignExecutionLogEntity> findByCampaignIdOrderByOperatedAtDesc(Long campaignId, Pageable pageable);

    /**
     * 按营销任务 ID 查询全部执行日志, 操作时间升序返回。
     * <p>
     * 供效果分析报告聚合使用, 避免分页带来多次查询。
     * </p>
     *
     * @param campaignId 营销任务 ID
     * @return 执行日志列表（按 operatedAt 升序）
     */
    List<ScrmCampaignExecutionLogEntity> findByCampaignIdOrderByOperatedAtAsc(Long campaignId);

    /**
     * 按营销任务 ID 与动作类型查询执行日志。
     *
     * @param campaignId 营销任务 ID
     * @param action     动作类型: START / PAUSE / RESUME / STOP / CALLBACK
     * @return 执行日志列表
     */
    List<ScrmCampaignExecutionLogEntity> findByCampaignIdAndAction(Long campaignId, String action);

    /**
     * 按执行状态查询日志 (如查询所有 FAILED 日志用于失败排查)。
     *
     * @param status 执行状态: SUCCESS / FAILED / RUNNING
     * @return 执行日志列表
     */
    List<ScrmCampaignExecutionLogEntity> findByStatus(String status);

    /**
     * 按操作时间范围分页查询执行日志。
     *
     * @param start    起始时间 (含)
     * @param end      截止时间 (含)
     * @param pageable 分页参数
     * @return 执行日志分页结果
     */
    Page<ScrmCampaignExecutionLogEntity> findByOperatedAtBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);

    /**
     * 删除操作时间早于指定时间的执行日志 (定时清理用)。
     *
     * @param cutoff 截止时间, 早于此时间的日志将被删除
     * @return 删除记录数
     */
    @Modifying
    @Query("DELETE FROM ScrmCampaignExecutionLogEntity e WHERE e.operatedAt < :cutoff")
    int deleteByOperatedAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
