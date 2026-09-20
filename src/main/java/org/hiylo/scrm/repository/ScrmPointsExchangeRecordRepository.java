/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPointsExchangeRecordRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmPointsExchangeRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

/**
 * SCRM 积分兑换记录数据访问层。
 * <p>
 * 提供每人限兑校验 (统计客户对某商品的非取消兑换数) 与兑换统计 (按状态分组聚合) 等能力。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmPointsExchangeRecordRepository extends JpaRepository<ScrmPointsExchangeRecordEntity, Long>,
        JpaSpecificationExecutor<ScrmPointsExchangeRecordEntity> {

    /**
     * 统计客户对指定商品的非取消兑换次数 (用于每人限兑校验)。
     *
     * @param exchangeId 兑换商品 ID
     * @param customerId 客户 ID
     * @param status     排除的状态 (通常为 CANCELLED)
     * @return 非取消状态的兑换次数
     */
    long countByExchangeIdAndCustomerIdAndStatusNot(Long exchangeId,
                                                               Long customerId, String status);

    /**
     * 兑换统计: 总记录数与消耗积分总额 (按 exchangedAt 区间)。
     *
     * @param start    区间起点 (含, 可空)
     * @param end      区间终点 (不含, 可空)
     * @return Object[]: [recordCount, totalPointsCost]
     */
    @Query(value = "SELECT COUNT(r), COALESCE(SUM(r.pointsCost), 0) FROM ScrmPointsExchangeRecordEntity r WHERE "
                          + "(:start IS NULL OR r.exchangedAt >= :start) AND (:end IS NULL OR r.exchangedAt < :end)")
    Object[] getExchangeStats(
                              @Param("start") LocalDateTime start,
                              @Param("end") LocalDateTime end);
}
