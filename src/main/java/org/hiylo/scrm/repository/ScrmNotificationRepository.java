/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmNotificationRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmNotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 通知记录数据访问层。
 * <p>
 * 提供未读数统计, 全部已读批量更新, 以及按渠道 / 分类 / 状态分桶统计 (供
 * {@code ScrmNotificationCenterService} 的统计接口使用)。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmNotificationRepository extends JpaRepository<ScrmNotificationEntity, Long>,
        JpaSpecificationExecutor<ScrmNotificationEntity> {

    /**
     * 统计用户未读站内信数 (IN_APP 渠道且状态非 READ)。
     *
     * @param userId   用户 ID (接收者 ID)
     * @return 未读数
     */
    @Query(value = "SELECT COUNT(n) FROM ScrmNotificationEntity n WHERE n.recipientId = :userId AND n.channel ="
                          + "'IN_APP' AND n.status <> 'READ'")
    long countUnread(@Param("userId") String userId);

    /**
     * 批量将用户的未读站内信标记为已读。
     *
     * @param userId   用户 ID (接收者 ID)
     * @param readAt   已读时间
     * @return 影响行数
     */
    @Modifying
    @Query(value = "UPDATE ScrmNotificationEntity n SET n.status = 'READ', n.readAt = :readAt WHERE n.recipientId ="
                          + ":userId AND n.channel = 'IN_APP' AND n.status <> 'READ'")
    int markAllAsRead(@Param("userId") String userId,
                      @Param("readAt") LocalDateTime readAt);

    /**
     * 按渠道分桶统计指定时间区间内的通知数。
     *
     * @param start    区间起点 (含)
     * @param end      区间终点 (不含)
     * @return 每渠道统计 [channel, count]
     */
    @Query(value = "SELECT n.channel, COUNT(n) FROM ScrmNotificationEntity n WHERE n.sentAt >= :start AND n.sentAt <"
                          + ":end GROUP BY n.channel")
    List<Object[]> countByChannel(
                                  @Param("start") LocalDateTime start,
                                  @Param("end") LocalDateTime end);

    /**
     * 按分类分桶统计指定时间区间内的通知数。
     *
     * @param start    区间起点 (含)
     * @param end      区间终点 (不含)
     * @return 每分类统计 [category, count]
     */
    @Query(value = "SELECT n.category, COUNT(n) FROM ScrmNotificationEntity n WHERE n.sentAt >= :start AND n.sentAt"
                          + "< :end GROUP BY n.category")
    List<Object[]> countByCategory(
                                   @Param("start") LocalDateTime start,
                                   @Param("end") LocalDateTime end);

    /**
     * 统计指定时间区间内某状态的通知数。
     *
     * @param status   状态
     * @param start    区间起点 (含)
     * @param end      区间终点 (不含)
     * @return 数量
     */
    @Query(value = "SELECT COUNT(n) FROM ScrmNotificationEntity n WHERE n.status = :status AND n.sentAt >= :start "
                          + "AND n.sentAt < :end")
    long countByStatusAndTimeRange(
                                   @Param("status") String status,
                                   @Param("start") LocalDateTime start,
                                   @Param("end") LocalDateTime end);

    /**
     * 统计指定时间区间内某渠道的通知总数。
     *
     * @param channel  渠道
     * @param start    区间起点 (含)
     * @param end      区间终点 (不含)
     * @return 数量
     */
    @Query(value = "SELECT COUNT(n) FROM ScrmNotificationEntity n WHERE n.channel = :channel AND n.sentAt >= :start "
                          + "AND n.sentAt < :end")
    long countByChannelAndTimeRange(
                                    @Param("channel") String channel,
                                    @Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end);
}
