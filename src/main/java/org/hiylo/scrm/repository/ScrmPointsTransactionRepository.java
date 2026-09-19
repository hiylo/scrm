/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPointsTransactionRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmPointsTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 积分流水数据访问层。
 * <p>
 * 提供按规则 + 客户统计区间内获取积分 (供每日/每月上限校验), 以及加载已过期/即将过期流水
 * (供 {@code ScrmPointsService.processExpiredPoints} 与 {@code getExpiringPoints} 使用)。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmPointsTransactionRepository extends JpaRepository<ScrmPointsTransactionEntity, Long>,
        JpaSpecificationExecutor<ScrmPointsTransactionEntity> {

    /**
     * 统计客户通过指定规则在 [start, end) 区间内产生的某类流水积分总和。
     * <p>用于每日/每月获取上限校验。</p>
     *
     * @param customerId 客户 ID
     * @param ruleId     规则 ID
     * @param type       交易类型
     * @param start      区间起点 (含)
     * @param end        区间终点 (不含)
     * @return 积分总和 (无记录返回 0)
     */
    @Query(value = "SELECT COALESCE(SUM(t.points), 0) FROM ScrmPointsTransactionEntity t WHERE t.customerId ="
                          + ":customerId AND t.ruleId = :ruleId AND t.transactionType = :type AND t.createdAt >= :start AND "
                          + "t.createdAt < :end")
    long sumPointsByRuleAndCustomer(
                                   @Param("customerId") Long customerId,
                                   @Param("ruleId") Long ruleId,
                                   @Param("type") String type,
                                   @Param("start") LocalDateTime start,
                                   @Param("end") LocalDateTime end);

    /**
     * 加载账号下所有已过期但未标记的获取类流水 (expiresAt 早于指定时间)。
     *
     * @param threshold 过期阈值时间 (通常为当前时间)
     * @return 待过期处理的流水列表
     */
    List<ScrmPointsTransactionEntity> findByExpiredFalseAndExpiresAtBefore(LocalDateTime threshold);

    /**
     * 加载指定账户下未过期且将在阈值前过期的获取类流水。
     *
     * @param accountId 账户 ID
     * @param threshold 过期阈值时间
     * @return 即将过期的流水列表
     */
    List<ScrmPointsTransactionEntity> findByAccountIdAndExpiredFalseAndExpiresAtBefore(Long accountId, LocalDateTime threshold);
}
