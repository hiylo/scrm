/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMessageRecallRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmMessageRecallEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * SCRM 消息撤回数据访问层。
 * <p>
 * 提供按消息 ID 查询撤回记录、按撤回状态 / 发送者 / 时间范围分页查询,
 * 供 {@code ScrmMessageTrackingService} 撤回管理使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmMessageRecallRepository extends JpaRepository<ScrmMessageRecallEntity, Long>,
        JpaSpecificationExecutor<ScrmMessageRecallEntity> {

    /**
     * 按消息 ID 查询撤回记录 (取最近一条, 一条消息仅允许一次撤回)。
     *
     * @param messageId 消息 ID
     * @return 撤回记录 (可能为空)
     */
    Optional<ScrmMessageRecallEntity> findFirstByMessageIdOrderByRecalledAtDesc(String messageId);

    /**
     * 按与发送者分页查询撤回记录 (按撤回时间倒序)。
     *
     * @param senderId 发送者 ID
     * @param pageable 分页参数
     * @return 撤回记录分页结果
     */
    Page<ScrmMessageRecallEntity> findBySenderIdOrderByRecalledAtDesc(String senderId, Pageable pageable);
}
