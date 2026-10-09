/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmOutboundMessageRepository.java
 * Date : 2026/10/07 18:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmOutboundMessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * SCRM 出站消息队列数据访问层。
 * <p>
 * PENDING 取走（拉一批置 IN_PROGRESS）与 ack（成功/失败回写）是并发的关键路径，
 * 使用状态条件查询 + 乐观锁 version 保证同一消息只被一个执行侧处理。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmOutboundMessageRepository extends JpaRepository<ScrmOutboundMessageEntity, Long> {

    /**
     * 按平台拉取待发送消息（PENDING，早创建优先，且已到退避重试时间）。
     * <p>
     * ack FAILED 退回 PENDING 时会设 {@code next_retry_at}（指数退避），
     * 该时间未到前不得再次被取走，否则退避完全失效、失败消息会瞬间耗尽重试。
     * </p>
     *
     * @param platformType 平台类型
     * @param pageable     分页限制条数
     * @return 待发送消息列表
     */
    List<ScrmOutboundMessageEntity> findByPlatformTypeAndStatusAndNextRetryAtIsNullOrNextRetryAtBeforeOrderByCreateTimeAsc(
            String platformType, String status, LocalDateTime now, Pageable pageable);

    /**
     * 拉取超时未回执的发送中消息（IN_PROGRESS 超过阈值退回 PENDING 重试）。
     *
     * @param platformType 平台类型
     * @param status       当前状态 IN_PROGRESS
     * @param timeoutBefore in_progress_at 早于该时间视为超时
     * @param pageable     分页限制条数
     * @return 超时消息列表
     */
    List<ScrmOutboundMessageEntity> findByPlatformTypeAndStatusAndInProgressAtBefore(
            String platformType, String status, LocalDateTime timeoutBefore, Pageable pageable);

    /**
     * 按业务消息 ID 查待发送记录（回调 OUT 消息落库时防重复入队）。
     *
     * @param businessMessageId 业务消息 ID
     * @return 出站队列记录（可空）
     */
    Optional<ScrmOutboundMessageEntity> findFirstByBusinessMessageIdAndPlatformType(
            String businessMessageId, String platformType);

    /**
     * 查询某会话下终态之外的出站记录（防止重复入队同一条消息）。
     *
     * @param conversationId 会话 ID
     * @param businessMessageId 业务消息 ID
     * @return 出站队列记录（可空）
     */
    Optional<ScrmOutboundMessageEntity> findFirstByConversationIdAndBusinessMessageId(
            Long conversationId, String businessMessageId);

    /**
     * 统计指定账号在指定时间之后、状态为给定集合内的出站消息数 (频率守卫用)。
     *
     * @param accountId  发送账号 ID
     * @param statuses   参与统计的状态集合 (PENDING / IN_PROGRESS / SENT)
     * @param after      时间下界
     * @return 出站消息数
     */
    long countByAccountIdAndStatusInAndCreateTimeAfter(
            Long accountId, java.util.Collection<String> statuses, LocalDateTime after);
}