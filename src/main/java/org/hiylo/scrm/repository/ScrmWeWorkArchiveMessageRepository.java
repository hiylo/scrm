/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWeWorkArchiveMessageRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmWeWorkArchiveMessageEntity;
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
 * 企微会话存档消息数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmWeWorkArchiveMessageRepository extends JpaRepository<ScrmWeWorkArchiveMessageEntity, Long>,
        JpaSpecificationExecutor<ScrmWeWorkArchiveMessageEntity> {

    /**
     * 按配置 ID 与 seq 查询消息 (去重校验)
     *
     * @param configId 存档配置 ID
     * @param seq      企微消息 seq
     * @return 消息 (可能不存在)
     */
    Optional<ScrmWeWorkArchiveMessageEntity> findByConfigIdAndSeq(Long configId, Long seq);

    /**
     * 按配置 ID 分页查询消息 (按发送时间倒序)
     *
     * @param configId 存档配置 ID
     * @param pageable 分页参数
     * @return 消息分页
     */
    Page<ScrmWeWorkArchiveMessageEntity> findByConfigIdOrderBySentAtDesc(Long configId, Pageable pageable);

    /**
     * 按时间范围与配置 ID 查询消息 (统计用)
     *
     * @param configId 存档配置 ID (可空, null 表示全部配置)
     * @param from     起始时间 (含)
     * @param to       结束时间 (含)
     * @return 消息列表
     */
    @Query(value = "SELECT m FROM ScrmWeWorkArchiveMessageEntity m WHERE (:configId IS NULL OR m.configId = :configId) "
                          + "AND m.sentAt >= :from AND m.sentAt <= :to ORDER BY m.sentAt DESC")
    List<ScrmWeWorkArchiveMessageEntity> findByConfigIdAndSentAtRange(
            @Param("configId") Long configId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * 统计账号下指定配置的消息数
     *
     * @param configId 存档配置 ID
     * @return 消息数
     */
    long countByConfigId(Long configId);

    /**
     * 统计账号下的消息总数
     *
     * @return 消息数
     */
}
