/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAiConversationRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmAiConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM AI 对话记录数据访问层。
 * <p>
 * 反馈率等统计能力, 供 {@code ScrmAiAssistantService} 对话查询与统计使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmAiConversationRepository extends JpaRepository<ScrmAiConversationEntity, Long>,
        JpaSpecificationExecutor<ScrmAiConversationEntity> {

    /**
     * 按识别意图聚合对话数 (统计用)。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return Object[]{detectedIntent, count}
     */
    @Query(value = "SELECT c.detectedIntent, COUNT(c.id) FROM ScrmAiConversationEntity c WHERE (:startTime IS NULL "
                          + "OR c.createdAt >= :startTime) AND (:endTime IS NULL OR c.createdAt <= :endTime) GROUP BY "
                          + "c.detectedIntent")
    List<Object[]> countByDetectedIntent(
                                          @Param("startTime") LocalDateTime startTime,
                                          @Param("endTime") LocalDateTime endTime);

    /**
     * 按情感聚合对话数 (统计用)。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return Object[]{sentiment, count}
     */
    @Query(value = "SELECT c.sentiment, COUNT(c.id) FROM ScrmAiConversationEntity c WHERE (:startTime IS NULL OR "
                          + "c.createdAt >= :startTime) AND (:endTime IS NULL OR c.createdAt <= :endTime) GROUP BY "
                          + "c.sentiment")
    List<Object[]> countBySentiment(
                                     @Param("startTime") LocalDateTime startTime,
                                     @Param("endTime") LocalDateTime endTime);

    /**
     * 计算平均意图置信度 (统计用)。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return 平均置信度 (无数据返回 null)
     */
    @Query(value = "SELECT AVG(c.intentConfidence) FROM ScrmAiConversationEntity c WHERE (:startTime IS NULL OR "
                          + "c.createdAt >= :startTime) AND (:endTime IS NULL OR c.createdAt <= :endTime)")
    Double averageIntentConfidence(
                                    @Param("startTime") LocalDateTime startTime,
                                    @Param("endTime") LocalDateTime endTime);

    /**
     * 按反馈聚合对话数 (统计用)。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return Object[]{feedback, count}
     */
    @Query(value = "SELECT c.feedback, COUNT(c.id) FROM ScrmAiConversationEntity c WHERE (:startTime IS NULL OR "
                          + "c.createdAt >= :startTime) AND (:endTime IS NULL OR c.createdAt <= :endTime) GROUP BY c.feedback")
    List<Object[]> countByFeedback(
                                    @Param("startTime") LocalDateTime startTime,
                                    @Param("endTime") LocalDateTime endTime);
}
