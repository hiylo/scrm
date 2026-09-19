/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTicketRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmTicketEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * SCRM 客户工单数据访问层。
 * <p>
 * 提供按工单编号查询、按统计当日工单序号 (生成工单编号用)、按处理人统计工单数 (工作量统计用)
 * 以及超期工单扫描 (SLA 到期早于当前时间且状态未关闭), 供 {@code ScrmTicketService} 使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmTicketRepository extends JpaRepository<ScrmTicketEntity, Long>,
        JpaSpecificationExecutor<ScrmTicketEntity> {

    /**
     * 按工单编号查询工单。
     *
     * @param ticketNo 工单编号
     * @return 工单 (可能为空)
     */
    Optional<ScrmTicketEntity> findByTicketNo(String ticketNo);

    /**
     * 按与工单编号前缀统计当日工单数量 (生成工单编号用, 计算当日序号)。
     * <p>ticketNoPrefix 形如 'TKT20260803', 统计该前缀的工单数后 +1 即为下一个序号。</p>
     *
     * @param ticketNoPrefix 工单编号前缀
     * @return 当日已生成工单数
     */
    long countByTicketNoStartingWith(String ticketNoPrefix);

    /**
     * 按与处理人统计指定状态的工单数 (处理人工作量统计用)。
     *
     * @param assigneeId 处理人 ID
     * @param statuses   状态集合
     * @return 工单数
     */
    long countByAssigneeIdAndStatusIn(String assigneeId, List<String> statuses);

    /**
     * 按扫描 SLA 超期工单 (slaDueAt 早于阈值时间且状态属于未关闭集合)。
     *
     * @param threshold        阈值时间 (slaDueAt 早于此时间)
     * @param openStatuses     未关闭状态集合
     * @return 超期工单列表
     */
    List<ScrmTicketEntity> findBySlaDueAtBeforeAndStatusIn(LocalDateTime threshold, List<String> openStatuses);

    /**
     * 按统计已解决工单的平均解决时长 (分钟, 时间范围按创建时间过滤)。
     * <p>startTime/endTime 为可空, 为空时不限制对应时间边界。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 平均解决时长 (无记录返回 null)
     */
    @Query(value = "SELECT AVG(t.resolutionTimeMinutes) FROM ScrmTicketEntity t WHERE t.resolutionTimeMinutes IS NOT "
                          + "NULL AND (:startTime IS NULL OR t.createTime >= :startTime) AND (:endTime IS NULL OR "
                          + "t.createTime <= :endTime)")
    Double avgResolutionMinutes(
                                         @Param("startTime") LocalDateTime startTime,
                                         @Param("endTime") LocalDateTime endTime);

    /**
     * 按统计已评价工单的平均满意度 (时间范围按创建时间过滤)。
     * <p>startTime/endTime 为可空, 为空时不限制对应时间边界。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 平均满意度 (无记录返回 null)
     */
    @Query(value = "SELECT AVG(t.satisfactionScore) FROM ScrmTicketEntity t WHERE t.satisfactionScore IS NOT NULL "
                          + "AND (:startTime IS NULL OR t.createTime >= :startTime) AND (:endTime IS NULL OR t.createTime <="
                          + ":endTime)")
    Double avgSatisfaction(
                                    @Param("startTime") LocalDateTime startTime,
                                    @Param("endTime") LocalDateTime endTime);
}
