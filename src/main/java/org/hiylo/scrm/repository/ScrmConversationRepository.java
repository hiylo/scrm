/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmConversationRepository.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmConversationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * SCRM 会话数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmConversationRepository extends JpaRepository<ScrmConversationEntity, Long> {

    /**
     * 根据账号 ID 查询会话列表。
     *
     * @param accountId 账号 ID
     * @return 会话列表
     */
    List<ScrmConversationEntity> findByAccountId(Long accountId);

    /**
     * 根据账号 ID 分页查询会话列表（按最后消息时间倒序）。
     *
     * @param accountId 账号 ID
     * @param pageable  分页参数
     * @return 会话分页结果
     */
    Page<ScrmConversationEntity> findByAccountIdOrderByLastMessageAtDesc(Long accountId, Pageable pageable);

    /**
     * 根据客户 ID 查询会话列表。
     *
     * @param customerId 客户 ID
     * @return 会话列表
     */
    List<ScrmConversationEntity> findByCustomerId(Long customerId);

    /**
     * 根据客户 ID 分页查询会话列表（按最后消息时间倒序）。
     *
     * @param customerId 客户 ID
     * @param pageable   分页参数
     * @return 会话分页结果
     */
    Page<ScrmConversationEntity> findByCustomerIdOrderByLastMessageAtDesc(Long customerId, Pageable pageable);

    /**
     * 根据账号 ID 与最后消息时间范围查询会话列表（按最后消息时间倒序，时间范围含边界）。
     * <p>用于质检会话按时间范围批量加载，替代 Integer.MAX_VALUE 伪分页 + 内存过滤，避免加载全量会话。</p>
     *
     * @param accountId 账号 ID
     * @param from      起始时间（含）
     * @param to        截止时间（含）
     * @return 会话列表
     */
    List<ScrmConversationEntity> findByAccountIdAndLastMessageAtBetweenOrderByLastMessageAtDesc(
            Long accountId, LocalDateTime from, LocalDateTime to);

    /**
     * 根据客户 ID 与最后消息时间范围查询会话列表（按最后消息时间倒序，时间范围含边界）。
     * <p>用于质检会话按客户加载，避免在 Integer.MAX_VALUE 伪分页后于内存过滤。</p>
     *
     * @param customerId 客户 ID
     * @param from       起始时间（含）
     * @param to         截止时间（含）
     * @return 会话列表
     */
    List<ScrmConversationEntity> findByCustomerIdAndLastMessageAtBetweenOrderByLastMessageAtDesc(
            Long customerId, LocalDateTime from, LocalDateTime to);

    /**
     * 根据平台会话 ID 查询会话。
     *
     * @param platformConversationId 平台会话 ID
     * @return 会话（可能为空）
     */
    Optional<ScrmConversationEntity> findByPlatformConversationId(String platformConversationId);

    /**
     * 根据账号 ID 与最后消息时间范围查询会话列表。
     *
     * @param from     起始时间（含）
     * @param to       截止时间（含）
     * @return 会话列表
     */
    List<ScrmConversationEntity> findByLastMessageAtBetween(LocalDateTime from, LocalDateTime to);

    /**
     * 根据账号 ID 与最后消息时间范围分页查询会话列表（按最后消息时间倒序）。
     *
     * @param from     起始时间（含）
     * @param to       截止时间（含）
     * @param pageable 分页参数
     * @return 会话分页结果
     */
    Page<ScrmConversationEntity> findByLastMessageAtBetweenOrderByLastMessageAtDesc(LocalDateTime from, LocalDateTime to, Pageable pageable);

    /**
     * 根据账号 ID 与客户 ID 查询会话（用于回调时定位会话）。
     *
     * @param accountId  账号 ID
     * @param customerId 客户 ID
     * @return 会话（可能为空）
     */
    Optional<ScrmConversationEntity> findByAccountIdAndCustomerId(Long accountId, Long customerId);

    /**
     * 统计指定账号下的会话总数。
     *
     * @return 会话总数
     */

    /**
     * 统计指定账号下、近 N 天有消息的活跃会话数。
     *
     * @param from     起始时间（含）
     * @return 活跃会话数
     */
    long countByLastMessageAtAfter(LocalDateTime from);

    /**
     * 根据账号 ID 与状态分页查询会话列表（按最后消息时间倒序）。
     *
     * @param status   会话状态（ACTIVE / CLOSED / PENDING）
     * @param pageable 分页参数
     * @return 会话分页结果
     */
    Page<ScrmConversationEntity> findByStatusOrderByLastMessageAtDesc(String status, Pageable pageable);

    /**
     * 根据账号 ID 分页查询会话列表（按最后消息时间倒序）。
     *
     * @param pageable 分页参数
     * @return 会话分页结果
     */
    Page<ScrmConversationEntity> findAllByOrderByLastMessageAtDesc(Pageable pageable);

    /**
     * 根据账号 ID 与状态，按最后消息时间范围分页查询会话列表。
     *
     * @param status   会话状态
     * @param from     起始时间（含）
     * @param to       截止时间（含）
     * @param pageable 分页参数
     * @return 会话分页结果
     */
    Page<ScrmConversationEntity> findByStatusAndLastMessageAtBetweenOrderByLastMessageAtDesc(String status, LocalDateTime from, LocalDateTime to, Pageable pageable);

}
