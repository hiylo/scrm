/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCommunityMessageRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCommunityMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 社群消息数据访问层。
 * <p>
 * 提供按社群 + 时间区间统计消息数 (用于活跃度计算与消息趋势), 以及按发送日期分桶统计
 * (供 {@code ScrmCommunityService.getActivityTrend} 活跃度趋势展示)。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCommunityMessageRepository extends JpaRepository<ScrmCommunityMessageEntity, Long>,
        JpaSpecificationExecutor<ScrmCommunityMessageEntity> {

    /**
     * 统计社群在指定时间区间内的消息数。
     *
     * @param communityId 社群 ID
     * @param start       区间起点 (含)
     * @param end         区间终点 (不含)
     * @return 消息数
     */
    @Query(value = "SELECT COUNT(m) FROM ScrmCommunityMessageEntity m WHERE m.communityId = :communityId AND "
                          + "m.sentAt >= :start AND m.sentAt < :end")
    long countByCommunityAndTimeRange(
                                      @Param("communityId") Long communityId,
                                      @Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end);

    /**
     * 统计账号下所有社群在指定时间区间内的消息总数。
     *
     * @param start    区间起点 (含)
     * @param end      区间终点 (不含)
     * @return 消息总数
     */
    @Query("SELECT COUNT(m) FROM ScrmCommunityMessageEntity m WHERE m.sentAt >= :start AND m.sentAt < :end")
    long countByTimeRange(
                                   @Param("start") LocalDateTime start,
                                   @Param("end") LocalDateTime end);

    /**
     * 按发送日期分桶统计社群消息数 (活跃度趋势)。
     * <p>返回每行 [日期字符串 (yyyy-MM-dd), 消息数]。</p>
     *
     * @param communityId 社群 ID
     * @param start       区间起点 (含)
     * @param end         区间终点 (不含)
     * @return 每日消息统计 [date, count]
     */
    @Query(value = "SELECT FUNCTION('DATE', m.sentAt), COUNT(m) FROM ScrmCommunityMessageEntity m WHERE "
                          + "m.communityId = :communityId AND m.sentAt >= :start AND m.sentAt < :end GROUP BY "
                          + "FUNCTION('DATE', m.sentAt) ORDER BY FUNCTION('DATE', m.sentAt)")
    List<Object[]> dailyMessageStats(
                                     @Param("communityId") Long communityId,
                                     @Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end);

    /**
     * 删除指定社群的所有消息记录 (社群删除时级联清理)。
     *
     * @param communityId 社群 ID
     * @return 删除行数
     */
    long deleteByCommunityId(Long communityId);
}
