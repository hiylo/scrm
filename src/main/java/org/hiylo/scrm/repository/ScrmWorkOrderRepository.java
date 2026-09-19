/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWorkOrderRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmWorkOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * SCRM 工单数据访问层。
 * <p>
 * 提供按工单编号查询、按统计当日工单序号 (生成工单编号用)、按客户/类型/状态/处理人/优先级
 * 查询、SLA 违规与超期扫描、统计聚合等能力, 供 {@code ScrmWorkOrderService} 使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmWorkOrderRepository extends JpaRepository<ScrmWorkOrderEntity, Long>,
        JpaSpecificationExecutor<ScrmWorkOrderEntity> {

    /**
     * 按工单编号查询工单。
     *
     * @param orderNo 工单编号
     * @return 工单 (可能为空)
     */
    Optional<ScrmWorkOrderEntity> findByOrderNo(String orderNo);

    /**
     * 按与工单编号前缀统计当日工单数量 (生成工单编号用, 计算当日序号)。
     *
     * @param orderNoPrefix 工单编号前缀
     * @return 当日已生成工单数
     */
    long countByOrderNoStartingWith(String orderNoPrefix);

    /**
     * 按客户查询工单。
     *
     * @param customerId 客户 ID
     * @return 工单列表
     */
    List<ScrmWorkOrderEntity> findByCustomerId(Long customerId);

    /**
     * 按与工单类型查询工单。
     *
     * @param orderType 工单类型
     * @return 工单列表
     */
    List<ScrmWorkOrderEntity> findByOrderType(String orderType);

    /**
     * 按与工单状态查询工单。
     *
     * @param orderStatus 工单状态
     * @return 工单列表
     */
    List<ScrmWorkOrderEntity> findByOrderStatus(String orderStatus);

    /**
     * 按与优先级查询工单。
     *
     * @param priority 优先级
     * @return 工单列表
     */
    List<ScrmWorkOrderEntity> findByPriority(String priority);

    /**
     * 按与处理人 ID 查询工单。
     *
     * @param assignedToId 处理人 ID
     * @return 工单列表
     */
    List<ScrmWorkOrderEntity> findByAssignedToId(Long assignedToId);

    /**
     * 按查询紧急工单。
     *
     * @return 紧急工单列表
     */
    List<ScrmWorkOrderEntity> findByIsUrgentTrue();

    /**
     * 按查询 SLA 违规工单。
     *
     * @return 违规工单列表
     */
    List<ScrmWorkOrderEntity> findBySlaBreachedTrue();

    /**
     * 按与处理人统计各状态工单数 (处理人工作量统计用)。
     *
     * @param assignedToId 处理人 ID
     * @param orderStatuses 状态集合
     * @return 工单数
     */
    long countByAssignedToIdAndOrderStatusIn(Long assignedToId, List<String> orderStatuses);

    /**
     * 按扫描 SLA 解决超期工单 (slaResolutionDue 早于阈值时间且状态属于未关闭集合)。
     *
     * @param threshold     阈值时间
     * @param openStatuses  未关闭状态集合
     * @return 超期工单列表
     */
    List<ScrmWorkOrderEntity> findBySlaResolutionDueBeforeAndOrderStatusIn(LocalDateTime threshold, List<String> openStatuses);

    /**
     * 按统计已解决工单的平均解决时长 (分钟, 时间范围按创建时间过滤)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime    截止时间 (可空)
     * @return 平均解决时长 (无记录返回 null)
     */
    @Query(value = "SELECT AVG(o.resolutionTimeMinutes) FROM ScrmWorkOrderEntity o WHERE o.resolutionTimeMinutes IS "
                          + "NOT NULL AND (:startTime IS NULL OR o.createTime >= :startTime) AND (:endTime IS NULL OR "
                          + "o.createTime <= :endTime)")
    Double avgResolutionMinutes(
                                          @Param("startTime") LocalDateTime startTime,
                                          @Param("endTime") LocalDateTime endTime);

    /**
     * 按统计已响应工单的平均响应时长 (分钟, 时间范围按创建时间过滤)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime    截止时间 (可空)
     * @return 平均响应时长 (无记录返回 null)
     */
    @Query(value = "SELECT AVG(o.responseTimeMinutes) FROM ScrmWorkOrderEntity o WHERE o.responseTimeMinutes IS NOT "
                          + "NULL AND (:startTime IS NULL OR o.createTime >= :startTime) AND (:endTime IS NULL OR "
                          + "o.createTime <= :endTime)")
    Double avgResponseMinutes(
                                        @Param("startTime") LocalDateTime startTime,
                                        @Param("endTime") LocalDateTime endTime);

    /**
     * 按统计已评价工单的平均满意度 (时间范围按创建时间过滤)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime    截止时间 (可空)
     * @return 平均满意度 (无记录返回 null)
     */
    @Query(value = "SELECT AVG(o.satisfactionScore) FROM ScrmWorkOrderEntity o WHERE o.satisfactionScore IS NOT NULL "
                          + "AND (:startTime IS NULL OR o.createTime >= :startTime) AND (:endTime IS NULL OR o.createTime <="
                          + ":endTime)")
    Double avgSatisfaction(
                                     @Param("startTime") LocalDateTime startTime,
                                     @Param("endTime") LocalDateTime endTime);

    /**
     * 按统计 SLA 违规工单数 (时间范围按创建时间过滤)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime    截止时间 (可空)
     * @return 违规工单数
     */
    @Query(value = "SELECT COUNT(o) FROM ScrmWorkOrderEntity o WHERE o.slaBreached = TRUE AND (:startTime IS NULL OR "
                          + "o.createTime >= :startTime) AND (:endTime IS NULL OR o.createTime <= :endTime)")
    long countSlaBreached(
                                    @Param("startTime") LocalDateTime startTime,
                                    @Param("endTime") LocalDateTime endTime);

    /**
     * 按统计工单总数 (时间范围按创建时间过滤)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime    截止时间 (可空)
     * @return 工单总数
     */
    @Query(value = "SELECT COUNT(o) FROM ScrmWorkOrderEntity o WHERE (:startTime IS NULL OR o.createTime >="
                          + ":startTime) AND (:endTime IS NULL OR o.createTime <= :endTime)")
    long countByTimeRange(
                                    @Param("startTime") LocalDateTime startTime,
                                    @Param("endTime") LocalDateTime endTime);
}
