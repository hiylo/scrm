/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmReferralRewardRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmReferralRewardEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 客户推荐奖励数据访问层。
 * <p>
 * 提供按推荐记录查询奖励列表、按客户统计奖励 (客户奖励列表与统计用)、
 * 按统计累计奖励价值 (总览统计用), 供 {@code ScrmReferralService} 使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmReferralRewardRepository extends JpaRepository<ScrmReferralRewardEntity, Long>,
        JpaSpecificationExecutor<ScrmReferralRewardEntity> {

    /**
     * 按推荐记录查询奖励列表 (按创建时间升序)。
     *
     * @param referralId 推荐记录 ID
     * @return 奖励列表
     */
    List<ScrmReferralRewardEntity> findByReferralIdOrderByCreateTimeAsc(Long referralId);

    /**
     * 按与接收客户统计指定状态的奖励数量 (奖励统计用)。
     *
     * @param recipientCustomerId 接收者客户 ID
     * @param statuses           状态集合
     * @return 奖励数量
     */
    long countByRecipientCustomerIdAndStatusIn(Long recipientCustomerId,
                                                           List<String> statuses);

    /**
     * 按统计指定状态的奖励数量 (奖励统计用)。
     *
     * @param statuses 状态集合
     * @return 奖励数量
     */
    long countByStatusIn(List<String> statuses);

    /**
     * 按统计累计奖励价值 (按奖励值汇总, 时间范围按创建时间过滤)。
     * <p>startTime/endTime 为可空, 为空时不限制对应时间边界。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 累计奖励价值 (无记录返回 null)
     */
    @Query(value = "SELECT COALESCE(SUM(r.rewardValue), 0) FROM ScrmReferralRewardEntity r WHERE (:startTime IS NULL "
                          + "OR r.createTime >= :startTime) AND (:endTime IS NULL OR r.createTime <= :endTime)")
    Double sumRewardValue(
                                  @Param("startTime") LocalDateTime startTime,
                                  @Param("endTime") LocalDateTime endTime);
}
