/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmConversationMessageRepository.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmConversationMessageEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SCRM 会话消息数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmConversationMessageRepository extends JpaRepository<ScrmConversationMessageEntity, Long> {

    /**
     * 根据会话 ID 查询消息，按发送时间倒序返回（最新在前）。
     *
     * @param conversationId 会话 ID
     * @return 消息列表
     */
    List<ScrmConversationMessageEntity> findByConversationIdOrderBySentAtDesc(Long conversationId);

    /**
     * 根据会话 ID 分页查询消息，按发送时间倒序返回（最新在前）。
     *
     * @param conversationId 会话 ID
     * @param pageable       分页参数
     * @return 消息分页结果
     */
    Page<ScrmConversationMessageEntity> findByConversationIdOrderBySentAtDesc(Long conversationId, Pageable pageable);

    /**
     * 根据会话 ID 与发送时间范围查询消息（按发送时间倒序）。
     *
     * @param conversationId 会话 ID
     * @param from           起始时间（含）
     * @param to             截止时间（含）
     * @return 消息列表
     */
    List<ScrmConversationMessageEntity> findByConversationIdAndSentAtBetweenOrderBySentAtDesc(
            Long conversationId, LocalDateTime from, LocalDateTime to);

    /**
     * 根据多个会话 ID 与发送时间范围批量查询消息（按发送时间倒序）。
     * <p>用于质检批量加载多个会话的消息，避免逐会话查询产生的 N+1 查询。</p>
     *
     * @param conversationIds 会话 ID 集合
     * @param from            起始时间（含）
     * @param to              截止时间（含）
     * @return 消息列表
     */
    List<ScrmConversationMessageEntity> findByConversationIdInAndSentAtBetweenOrderBySentAtDesc(
            Collection<Long> conversationIds, LocalDateTime from, LocalDateTime to);

    /**
     * 根据多个会话 ID 批量查询全部消息（按发送时间倒序）。
     * <p>用于质检在未指定时间范围时批量加载多个会话的消息，避免 N+1 查询。</p>
     *
     * @param conversationIds 会话 ID 集合
     * @return 消息列表
     */
    List<ScrmConversationMessageEntity> findByConversationIdInOrderBySentAtDesc(
            Collection<Long> conversationIds);

    /**
     * 根据会话 ID 与文本内容关键字 LIKE 搜索消息（按发送时间倒序）。
     *
     * @param conversationId 会话 ID
     * @param keyword         搜索关键字
     * @param pageable        分页参数
     * @return 消息分页结果
     */
    Page<ScrmConversationMessageEntity> findByConversationIdAndContentContainingIgnoreCaseOrderBySentAtDesc(
            Long conversationId, String keyword, Pageable pageable);

    /**
     * 根据消息 ID（业务唯一）查询消息。
     *
     * @param messageId 消息 ID
     * @return 消息（可能为空）
     */
    Optional<ScrmConversationMessageEntity> findByMessageId(String messageId);

    /**
     * 根据平台消息 ID 查询消息（用于回调去重）。
     *
     * @param platformMessageId 平台消息 ID
     * @return 消息（可能为空）
     */
    Optional<ScrmConversationMessageEntity> findByPlatformMessageId(String platformMessageId);

    /**
     * 统计指定会话的消息总数。
     *
     * @param conversationId 会话 ID
     * @return 消息总数
     */
    long countByConversationId(Long conversationId);

    /**
     * 根据账号 ID 与发送时间范围查询消息列表。
     *
     * @param from     起始时间（含）
     * @param to       截止时间（含）
     * @return 消息列表
     */
    List<ScrmConversationMessageEntity> findBySentAtBetween(LocalDateTime from, LocalDateTime to);

    /**
     * 统计指定账号下的消息总数。
     *
     * @return 消息总数
     */

    /**
     * 按日聚合消息量（看板近 7 天消息量用, native query 借助 PostgreSQL to_char）。
     *
     * @param from     起始时间（含）
     * @return Object[]{date(yyyy-MM-dd), count}
     */
    default List<Object[]> dailyCountBySentAt(LocalDateTime from) {
        return DailyStatsRepository.toRows(DailyStatsRepository.getInstance().countByDay(
                "scrm.scrm_conversation_message", "sent_at", "",
                Map.of(), from, null));
    }
}
