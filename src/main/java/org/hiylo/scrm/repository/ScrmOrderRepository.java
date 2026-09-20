/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmOrderRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * SCRM 订单数据访问层。
 * <p>
 * 提供按订单编号查询、按与时间范围查询订单 (统计用), 以及按统计订单数、
 * 各状态订单数、金额汇总、按渠道聚合收入等能力, 供 {@code ScrmProductOrderService} 使用。
 * 订单的复合过滤 (客户 / 状态 / 支付状态 / 类型 / 时间 / 关键词) 通过
 * {@link JpaSpecificationExecutor} 动态构建查询条件实现。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmOrderRepository extends JpaRepository<ScrmOrderEntity, Long>,
        JpaSpecificationExecutor<ScrmOrderEntity> {

    /**
     * 按订单编号查询订单 (全局唯一)。
     *
     * @param orderNo 订单编号
     * @return 订单 (可能为空)
     */
    Optional<ScrmOrderEntity> findByOrderNo(String orderNo);


    /**
     * 按订单编号前缀统计订单数 (订单号生成序号用)。
     *
     * @param prefix   订单编号前缀
     * @return 匹配的订单数
     */
    long countByOrderNoStartingWith(String prefix);

    /**
     * 按与时间范围查询订单 (统计用, 按创建时间倒序)。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return 订单列表
     */
    @Query(value = "SELECT o FROM ScrmOrderEntity o WHERE (:startTime IS NULL OR o.createTime >= :startTime) AND "
                          + "(:endTime IS NULL OR o.createTime <= :endTime) ORDER BY o.createTime DESC")
    List<ScrmOrderEntity> findByTimeRange(
                                                       @Param("startTime") LocalDateTime startTime,
                                                       @Param("endTime") LocalDateTime endTime);

    /**
     * 按与时间范围统计已完成订单的销售额汇总 (订单统计用)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 销售额汇总 (无记录返回 0)
     */
    @Query(value = "SELECT COALESCE(SUM(o.totalAmount), 0) FROM ScrmOrderEntity o WHERE o.orderStatus = 'COMPLETED' "
                          + "AND (:startTime IS NULL OR o.createTime >= :startTime) AND (:endTime IS NULL OR o.createTime <="
                          + ":endTime)")
    Double sumCompletedAmount(
                                        @Param("startTime") LocalDateTime startTime,
                                        @Param("endTime") LocalDateTime endTime);

    /**
     * 按与时间范围统计已支付订单的实际收入汇总 (按已付金额)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 收入汇总 (无记录返回 0)
     */
    @Query(value = "SELECT COALESCE(SUM(o.paidAmount), 0) FROM ScrmOrderEntity o WHERE o.paymentStatus = 'PAID' AND "
                          + "(:startTime IS NULL OR o.createTime >= :startTime) AND (:endTime IS NULL OR o.createTime <="
                          + ":endTime)")
    Double sumPaidAmount(
                                   @Param("startTime") LocalDateTime startTime,
                                   @Param("endTime") LocalDateTime endTime);

    /**
     * 按与时间范围按订单状态聚合订单数 (订单状态分布统计用)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return Object[]{orderStatus, count}
     */
    @Query(value = "SELECT o.orderStatus, COUNT(o.id) FROM ScrmOrderEntity o WHERE (:startTime IS NULL OR "
                          + "o.createTime >= :startTime) AND (:endTime IS NULL OR o.createTime <= :endTime) GROUP BY "
                          + "o.orderStatus")
    List<Object[]> countByOrderStatus(
                                        @Param("startTime") LocalDateTime startTime,
                                        @Param("endTime") LocalDateTime endTime);

    /**
     * 按与时间范围按渠道聚合收入 (渠道收入统计用, channel 为空归入 UNKNOWN)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return Object[]{channel, count, totalAmount}
     */
    @Query(value = "SELECT COALESCE(o.channel, 'UNKNOWN'), COUNT(o.id), COALESCE(SUM(o.totalAmount), 0) FROM "
                          + "ScrmOrderEntity o WHERE o.orderStatus <> 'CANCELLED' AND (:startTime IS NULL OR o.createTime >="
                          + ":startTime) AND (:endTime IS NULL OR o.createTime <= :endTime) GROUP BY COALESCE(o.channel,"
                          + "'UNKNOWN')")
    List<Object[]> revenueByChannel(
                                     @Param("startTime") LocalDateTime startTime,
                                     @Param("endTime") LocalDateTime endTime);

    /**
     * 按客户查询订单 (客户购买历史用, 按创建时间倒序)。
     *
     * @param customerId 客户 ID
     * @return 订单列表
     */
    List<ScrmOrderEntity> findByCustomerIdOrderByCreateTimeDesc(Long customerId);
}
