/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMessageTrackingRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmMessageTrackingEntity;
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
 * SCRM 消息跟踪数据访问层。
 * <p>
 * 提供按消息 ID 查询、按批次 / 发送者 / 渠道 / 状态分页查询, 以及跟踪统计
 * (发送数 / 送达率 / 已读率 / 撤回率 / 转发率 / 回复率 / 渠道对比 / 已读趋势 /
 * 最佳发送时间) 所需的聚合查询, 供 {@code ScrmMessageTrackingService} 使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmMessageTrackingRepository extends JpaRepository<ScrmMessageTrackingEntity, Long>,
        JpaSpecificationExecutor<ScrmMessageTrackingEntity> {

    /**
     * 按消息 ID 查询跟踪记录。
     *
     * @param messageId 消息 ID
     * @return 跟踪记录 (可能为空)
     */
    Optional<ScrmMessageTrackingEntity> findByMessageId(String messageId);


    /**
     * 按批次 ID 分页查询未读消息 (按创建时间倒序)。
     *
     * @param batchId  批次 ID
     * @param pageable 分页参数
     * @return 未读跟踪记录分页结果
     */
    Page<ScrmMessageTrackingEntity> findByBatchIdAndIsReadFalse(Long batchId, Pageable pageable);

    /**
     * 统计区间内发送消息总数 (按 sentAt 过滤)。
     *
     * @param start    区间起点 (含, 可空)
     * @param end      区间终点 (含, 可空)
     * @return 发送消息数
     */
    @Query(value = "SELECT COUNT(t) FROM ScrmMessageTrackingEntity t WHERE (:start IS NULL OR t.sentAt >= :start) "
                          + "AND (:end IS NULL OR t.sentAt <= :end)")
    long countSentInRange(
                          @Param("start") LocalDateTime start,
                          @Param("end") LocalDateTime end);

    /**
     * 统计区间内已送达消息数。
     *
     * @param start    区间起点 (含, 可空)
     * @param end      区间终点 (含, 可空)
     * @return 已送达消息数
     */
    @Query(value = "SELECT COUNT(t) FROM ScrmMessageTrackingEntity t WHERE t.sendStatus = 'DELIVERED' AND (:start IS "
                          + "NULL OR t.sentAt >= :start) AND (:end IS NULL OR t.sentAt <= :end)")
    long countDeliveredInRange(
                               @Param("start") LocalDateTime start,
                               @Param("end") LocalDateTime end);

    /**
     * 统计区间内已读消息数。
     *
     * @param start    区间起点 (含, 可空)
     * @param end      区间终点 (含, 可空)
     * @return 已读消息数
     */
    @Query(value = "SELECT COUNT(t) FROM ScrmMessageTrackingEntity t WHERE t.isRead = true AND (:start IS NULL OR "
                          + "t.sentAt >= :start) AND (:end IS NULL OR t.sentAt <= :end)")
    long countReadInRange(
                          @Param("start") LocalDateTime start,
                          @Param("end") LocalDateTime end);

    /**
     * 统计区间内已撤回消息数。
     *
     * @param start    区间起点 (含, 可空)
     * @param end      区间终点 (含, 可空)
     * @return 已撤回消息数
     */
    @Query(value = "SELECT COUNT(t) FROM ScrmMessageTrackingEntity t WHERE t.isRecalled = true AND (:start IS NULL "
                          + "OR t.sentAt >= :start) AND (:end IS NULL OR t.sentAt <= :end)")
    long countRecalledInRange(
                              @Param("start") LocalDateTime start,
                              @Param("end") LocalDateTime end);

    /**
     * 统计区间内已转发消息数。
     *
     * @param start    区间起点 (含, 可空)
     * @param end      区间终点 (含, 可空)
     * @return 已转发消息数
     */
    @Query(value = "SELECT COUNT(t) FROM ScrmMessageTrackingEntity t WHERE t.isForwarded = true AND (:start IS NULL "
                          + "OR t.sentAt >= :start) AND (:end IS NULL OR t.sentAt <= :end)")
    long countForwardedInRange(
                               @Param("start") LocalDateTime start,
                               @Param("end") LocalDateTime end);

    /**
     * 统计区间内已回复消息数。
     *
     * @param start    区间起点 (含, 可空)
     * @param end      区间终点 (含, 可空)
     * @return 已回复消息数
     */
    @Query(value = "SELECT COUNT(t) FROM ScrmMessageTrackingEntity t WHERE t.isReplied = true AND (:start IS NULL OR "
                          + "t.sentAt >= :start) AND (:end IS NULL OR t.sentAt <= :end)")
    long countRepliedInRange(
                             @Param("start") LocalDateTime start,
                             @Param("end") LocalDateTime end);

    /**
     * 统计区间内平均互动评分。
     *
     * @param start    区间起点 (含, 可空)
     * @param end      区间终点 (含, 可空)
     * @return 平均互动评分 (无记录返回 null)
     */
    @Query(value = "SELECT AVG(t.engagementScore) FROM ScrmMessageTrackingEntity t WHERE (:start IS NULL OR t.sentAt"
                          + ">= :start) AND (:end IS NULL OR t.sentAt <= :end)")
    Double avgEngagementInRange(
                                @Param("start") LocalDateTime start,
                                @Param("end") LocalDateTime end);

    /**
     * 按渠道统计发送数 / 已读数 (渠道对比与已读率统计)。
     *
     * @param start    区间起点 (含, 可空)
     * @param end      区间终点 (含, 可空)
     * @return Object[] 列表: [channel, totalSent, readCount]
     */
    @Query(value = "SELECT t.channel, COUNT(t), SUM(CASE WHEN t.isRead = true THEN 1 ELSE 0 END) FROM "
                          + "ScrmMessageTrackingEntity t WHERE (:start IS NULL OR t.sentAt >= :start) AND (:end IS NULL OR "
                          + "t.sentAt <= :end) GROUP BY t.channel ORDER BY COUNT(t) DESC")
    List<Object[]> aggregateByChannel(
                                      @Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end);

    /**
     * 按渠道统计发送数 / 已读数 (指定渠道已读率统计)。
     *
     * @param channel  渠道
     * @param start    区间起点 (含, 可空)
     * @param end      区间终点 (含, 可空)
     * @return Object[]: [totalSent, readCount]
     */
    @Query(value = "SELECT COUNT(t), SUM(CASE WHEN t.isRead = true THEN 1 ELSE 0 END) FROM ScrmMessageTrackingEntity t"
                          + " WHERE t.channel = :channel AND (:start IS NULL OR t.sentAt >= :start) AND (:end IS NULL OR "
                          + "t.sentAt <= :end)")
    Object[] aggregateByChannelReadRate(
                                        @Param("channel") String channel,
                                        @Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end);

    /**
     * 按日期统计发送数与已读数 (已读趋势)。
     *
     * @param start    区间起点 (含)
     * @return Object[] 列表: [date(yyyy-MM-dd), sentCount, readCount]
     */
    @Query(value = "SELECT to_char(t.sent_at, 'YYYY-MM-DD') AS d, COUNT(*) AS c, SUM(CASE WHEN t.is_read THEN 1 ELSE "
                          + "0 END) AS r FROM scrm.scrm_message_tracking t WHERE t.sent_at >= :start GROUP BY d ORDER BY d "
                          + "ASC",
            nativeQuery = true)
    List<Object[]> countByDay(
                              @Param("start") LocalDateTime start);

    /**
     * 按发送小时统计发送数与已读数 (最佳发送时间分析)。
     *
     * @param channel  渠道 (可空, 为空时忽略)
     * @param start    区间起点 (含, 可空)
     * @param end      区间终点 (含, 可空)
     * @return Object[] 列表: [hour(0-23), sentCount, readCount]
     */
    @Query(value = "SELECT EXTRACT(HOUR FROM t.sent_at) AS h, COUNT(*) AS c, SUM(CASE WHEN t.is_read THEN 1 ELSE 0 "
                          + "END) AS r FROM scrm.scrm_message_tracking t WHERE (:channel IS NULL OR t.channel = :channel) AND "
                          + "(:start IS NULL OR t.sent_at >= :start) AND (:end IS NULL OR t.sent_at <= :end) AND t.sent_at IS "
                          + "NOT NULL GROUP BY h ORDER BY h ASC",
            nativeQuery = true)
    List<Object[]> aggregateByHour(
                                   @Param("channel") String channel,
                                   @Param("start") LocalDateTime start,
                                   @Param("end") LocalDateTime end);
}
