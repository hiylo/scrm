/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFeedbackCommentRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmFeedbackCommentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * SCRM 反馈评论数据访问层。
 * <p>
 * 提供按与反馈 ID 查询评论列表 (按评论时间升序), 供 {@code ScrmFeedbackService} 使用。
 * 复杂多条件过滤通过 {@link JpaSpecificationExecutor} 由 Service 层动态构造。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmFeedbackCommentRepository extends JpaRepository<ScrmFeedbackCommentEntity, Long>,
        JpaSpecificationExecutor<ScrmFeedbackCommentEntity> {

    /**
     * 按与反馈 ID 查询评论列表 (按评论发生时间升序)。
     *
     * @param feedbackId 反馈 ID
     * @return 评论列表
     */
    List<ScrmFeedbackCommentEntity> findByFeedbackIdOrderByCreatedAtAsc(Long feedbackId);

    /**
     * 按与反馈 ID 统计评论数 (用于刷新反馈的评论计数)。
     *
     * @param feedbackId 反馈 ID
     * @return 评论数
     */
    long countByFeedbackId(Long feedbackId);
}
