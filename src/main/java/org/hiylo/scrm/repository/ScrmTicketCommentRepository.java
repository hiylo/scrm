/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTicketCommentRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmTicketCommentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * SCRM 工单评论数据访问层。
 * <p>
 * 提供按工单 ID 查询评论列表, 供 {@code ScrmTicketService} 评论管理与时间线组装使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmTicketCommentRepository extends JpaRepository<ScrmTicketCommentEntity, Long>,
        JpaSpecificationExecutor<ScrmTicketCommentEntity> {

    /**
     * 按与工单 ID 查询评论列表 (按评论发生时间升序, 时间线组装用)。
     *
     * @param ticketId 工单 ID
     * @return 评论列表
     */
    List<ScrmTicketCommentEntity> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
}
