/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWebhookLogRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmWebhookLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM Webhook 推送日志数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmWebhookLogRepository extends JpaRepository<ScrmWebhookLogEntity, Long>,
        JpaSpecificationExecutor<ScrmWebhookLogEntity> {

    /**
     * 按状态分页查询指定账号的日志。
     *
     * @param status   日志状态
     * @param pageable 分页参数
     * @return 日志分页结果
     */
    Page<ScrmWebhookLogEntity> findByStatus(String status, Pageable pageable);

    /**
     * 捞取指定时间之前仍为 RETRY 状态且 nextRetryAt 已到期的日志 (定时重试任务调用)。
     *
     * @param status     日志状态 (通常为 RETRY)
     * @param nextRetryAt 下次重试时间上限
     * @return 待重试日志列表
     */
    List<ScrmWebhookLogEntity> findByStatusAndNextRetryAtLessThanEqual(String status, LocalDateTime nextRetryAt);

    /**
     * 统计指定 Webhook 在时间范围内的推送结果 (用于统计, 避免 N+1)。
     *
     * @param webhookId Webhook 配置 ID
     * @param startTime 起始时间
     * @param endTime   截止时间
     * @return Object[]{status, count}
     */
    @Query("SELECT l.status, COUNT(l.id) FROM ScrmWebhookLogEntity l "
            + "WHERE l.webhookId = :webhookId "
            + "AND l.createTime >= :startTime AND l.createTime <= :endTime "
            + "GROUP BY l.status")
    List<Object[]> countByStatus(@Param("webhookId") Long webhookId,
                                 @Param("startTime") LocalDateTime startTime,
                                 @Param("endTime") LocalDateTime endTime);

    /**
     * 统计指定 Webhook 在时间范围内成功推送的平均耗时 (毫秒)。
     *
     * @param webhookId Webhook 配置 ID
     * @param status    日志状态 (通常为 SUCCESS)
     * @param startTime 起始时间
     * @param endTime   截止时间
     * @return 平均耗时 (毫秒), 无数据返回 null
     */
    @Query("SELECT AVG(l.durationMs) FROM ScrmWebhookLogEntity l "
            + "WHERE l.webhookId = :webhookId AND l.status = :status "
            + "AND l.createTime >= :startTime AND l.createTime <= :endTime")
    Double averageDurationMsByWebhookIdAndStatus(@Param("webhookId") Long webhookId,
                                                  @Param("status") String status,
                                                  @Param("startTime") LocalDateTime startTime,
                                                  @Param("endTime") LocalDateTime endTime);
}
