/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMarketingTriggerEventRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmMarketingTriggerEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * SCRM 触发式营销事件记录数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmMarketingTriggerEventRepository
        extends JpaRepository<ScrmMarketingTriggerEventEntity, Long>,
        JpaSpecificationExecutor<ScrmMarketingTriggerEventEntity> {

    /**
     * 根据触发器 ID 分页查询事件记录。
     *
     * @param triggerId 触发器 ID
     * @param pageable  分页参数
     * @return 事件记录分页结果
     */
    Page<ScrmMarketingTriggerEventEntity> findByTriggerId(Long triggerId, Pageable pageable);

    /**
     * 根据触发器 ID 与状态分页查询事件记录。
     *
     * @param triggerId 触发器 ID
     * @param status    事件状态
     * @param pageable  分页参数
     * @return 事件记录分页结果
     */
    Page<ScrmMarketingTriggerEventEntity> findByTriggerIdAndStatus(Long triggerId, String status, Pageable pageable);

    /**
     * 查询指定触发器+客户下, 已成功执行且最近一次的事件 (用于冷却期判断)。
     *
     * @param triggerId  触发器 ID
     * @param customerId 客户 ID
     * @param status     事件状态 (通常为 SUCCESS)
     * @return 最近一次成功事件 (按执行时间倒序)
     */
    Optional<ScrmMarketingTriggerEventEntity> findFirstByTriggerIdAndCustomerIdAndStatusOrderByExecutedAtDesc(
            Long triggerId, Long customerId, String status);

    /**
     * 统计指定触发器+客户下, 已成功执行的事件次数 (用于每客户最大触发次数判断)。
     *
     * @param triggerId  触发器 ID
     * @param customerId 客户 ID
     * @param status     事件状态 (通常为 SUCCESS)
     * @return 成功事件次数
     */
    long countByTriggerIdAndCustomerIdAndStatus(Long triggerId, Long customerId, String status);

    /**
     * 捞取指定时间之前仍为 PENDING 状态的事件 (定时处理任务调用)。
     *
     * @param status      事件状态
     * @param scheduledAt 计划执行时间上限
     * @return 待处理事件列表
     */
    List<ScrmMarketingTriggerEventEntity> findByStatusAndScheduledAtLessThanEqual(String status, LocalDateTime scheduledAt);

    /**
     * 按状态聚合指定账号与时间范围内的事件数 (统计用, 避免 N+1)。
     *
     * @param startTime 起始时间
     * @param endTime   截止时间
     * @return Object[]{status, count}
     */
    @Query(value = "SELECT e.status, COUNT(e.id) FROM ScrmMarketingTriggerEventEntity e WHERE e.createTime >="
                          + ":startTime AND e.createTime <= :endTime GROUP BY e.status")
    List<Object[]> countByStatus(
                                 @Param("startTime") LocalDateTime startTime,
                                 @Param("endTime") LocalDateTime endTime);

    /**
     * 按动作类型聚合指定账号与时间范围内的事件数 (统计用)。
     *
     * @param startTime 起始时间
     * @param endTime   截止时间
     * @return Object[]{actionType, count}
     */
    @Query(value = "SELECT e.actionType, COUNT(e.id) FROM ScrmMarketingTriggerEventEntity e WHERE e.createTime >="
                          + ":startTime AND e.createTime <= :endTime GROUP BY e.actionType")
    List<Object[]> countByActionType(
                                     @Param("startTime") LocalDateTime startTime,
                                     @Param("endTime") LocalDateTime endTime);
}
