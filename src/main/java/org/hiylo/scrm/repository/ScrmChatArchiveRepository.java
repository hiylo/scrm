/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmChatArchiveRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmChatArchiveEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 会话存档数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmChatArchiveRepository extends JpaRepository<ScrmChatArchiveEntity, Long>,
        JpaSpecificationExecutor<ScrmChatArchiveEntity> {

    /**
     * 按账号 ID 分页查询归档消息
     *
     * @param accountId 账号 ID
     * @param pageable  分页参数
     * @return 归档消息分页
     */
    Page<ScrmChatArchiveEntity> findByAccountIdOrderBySentAtDesc(Long accountId, Pageable pageable);

    /**
     * 按客户 ID 分页查询归档消息
     *
     * @param customerId 客户 ID
     * @param pageable  分页参数
     * @return 归档消息分页
     */
    Page<ScrmChatArchiveEntity> findByCustomerIdOrderBySentAtDesc(Long customerId, Pageable pageable);

    /**
     * 按会话 ID 分页查询归档消息
     *
     * @param conversationId 会话 ID
     * @param pageable       分页参数
     * @return 归档消息分页
     */
    Page<ScrmChatArchiveEntity> findByConversationIdOrderBySentAtDesc(Long conversationId, Pageable pageable);

    /**
     * 按时间范围查询归档消息 (用于定时归档与统计)
     *
     * @param from     起始时间 (含)
     * @param to       结束时间 (含)
     * @return 归档消息列表
     */
    @Query(value = "SELECT a FROM ScrmChatArchiveEntity a WHERE a.sentAt >= :from AND a.sentAt <= :to ORDER BY "
                          + "a.sentAt DESC")
    List<ScrmChatArchiveEntity> findBySentAtRange(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * 按质量标记统计归档消息数 (看板用)
     *
     * @param qualityFlag 质量标记
     * @return 消息数
     */
    long countByQualityFlag(String qualityFlag);

    /**
     * 统计指定账号的归档消息总数
     *
     * @return 消息总数
     */
}
