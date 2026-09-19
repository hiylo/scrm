/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmReferralRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmReferralEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * SCRM 客户推荐关系数据访问层。
 * <p>
 * 提供按推荐码查询推荐、按与活动统计推荐数与成功推荐数 (活动统计刷新用),
 * 供 {@code ScrmReferralService} 使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmReferralRepository extends JpaRepository<ScrmReferralEntity, Long>,
        JpaSpecificationExecutor<ScrmReferralEntity> {

    /**
     * 按推荐码查询推荐。
     *
     * @param referralCode 推荐码
     * @return 推荐 (可能为空)
     */
    Optional<ScrmReferralEntity> findByReferralCode(String referralCode);

    /**
     * 按与活动统计推荐数量 (活动统计刷新用)。
     *
     * @param programId 活动 ID
     * @return 推荐数量
     */
    long countByProgramId(Long programId);

    /**
     * 按与活动统计指定状态集合的推荐数量 (成功推荐数统计用)。
     *
     * @param programId 活动 ID
     * @param statuses  状态集合
     * @return 推荐数量
     */
    long countByProgramIdAndStatusIn(Long programId, java.util.List<String> statuses);

    /**
     * 按统计推荐人已发起的推荐数量 (单人推荐上限校验用)。
     *
     * @param programId         活动 ID
     * @param referrerCustomerId 推荐人客户 ID
     * @return 推荐人已发起的推荐数量
     */
    long countByProgramIdAndReferrerCustomerId(Long programId, Long referrerCustomerId);

    /**
     * 按统计指定状态集合的推荐数量 (总览统计用)。
     *
     * @param statuses 状态集合
     * @return 推荐数量
     */
    long countByStatusIn(java.util.List<String> statuses);

    /**
     * 按统计累计奖励价值 (按活动汇总, 时间范围按创建时间过滤)。
     * <p>startTime/endTime 为可空, 为空时不限制对应时间边界。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 累计奖励价值 (无记录返回 null)
     */
    @Query(value = "SELECT COALESCE(SUM(r.purchaseAmount), 0) FROM ScrmReferralEntity r WHERE (:startTime IS NULL OR "
                          + "r.createTime >= :startTime) AND (:endTime IS NULL OR r.createTime <= :endTime)")
    Double sumPurchaseAmount(
                                     @Param("startTime") java.time.LocalDateTime startTime,
                                     @Param("endTime") java.time.LocalDateTime endTime);
}
