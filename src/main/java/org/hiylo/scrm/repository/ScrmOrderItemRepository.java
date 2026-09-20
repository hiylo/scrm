/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmOrderItemRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmOrderItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * SCRM 订单项数据访问层。
 * <p>
 * 提供按订单 ID 查询订单项列表 (按 ID 升序), 以及按商品 ID 聚合销量 (热销商品统计用),
 * 供 {@code ScrmProductOrderService} 使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmOrderItemRepository extends JpaRepository<ScrmOrderItemEntity, Long>,
        JpaSpecificationExecutor<ScrmOrderItemEntity> {

    /**
     * 按与订单 ID 查询订单项 (按 ID 升序)。
     *
     * @param orderId  订单 ID
     * @return 订单项列表
     */
    List<ScrmOrderItemEntity> findByOrderIdOrderByIdAsc(Long orderId);

    /**
     * 按订单 ID 删除全部订单项 (重建订单项时使用)。
     *
     * @param orderId  订单 ID
     * @return 删除的订单项数量
     */
    long deleteByOrderId(Long orderId);

    /**
     * 按商品 ID 聚合销量 (热销商品统计用, 关联有效订单)。
     * <p>startTime/endTime 为可空, 按 create_time 过滤订单项。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return Object[]{productId, productName, sumQuantity, sumSubtotal}
     */
    @Query(value = "SELECT i.productId, MAX(i.productName), COALESCE(SUM(i.quantity), 0), COALESCE(SUM(i.subtotal),"
                          + "0) FROM ScrmOrderItemEntity i WHERE (:startTime IS NULL OR i.createTime >= :startTime) AND "
                          + "(:endTime IS NULL OR i.createTime <= :endTime) AND i.productId IS NOT NULL GROUP BY i.productId "
                          + "ORDER BY COALESCE(SUM(i.quantity), 0) DESC")
    List<Object[]> topProductsBySales(
                                        @Param("startTime") java.time.LocalDateTime startTime,
                                        @Param("endTime") java.time.LocalDateTime endTime);
}
