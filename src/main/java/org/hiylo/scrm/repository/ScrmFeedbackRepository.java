/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFeedbackRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmFeedbackEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * SCRM 客户反馈数据访问层。
 * <p>
 * 提供按反馈编号查询、按统计当日反馈序号 (生成反馈编号用)、按处理人统计反馈数 (工作量统计用)
 * 以及平均解决时长/平均满意度统计, 供 {@code ScrmFeedbackService} 使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmFeedbackRepository extends JpaRepository<ScrmFeedbackEntity, Long>,
        JpaSpecificationExecutor<ScrmFeedbackEntity> {

    /**
     * 按反馈编号查询反馈。
     *
     * @param feedbackNo 反馈编号
     * @return 反馈 (可能为空)
     */
    Optional<ScrmFeedbackEntity> findByFeedbackNo(String feedbackNo);

    /**
     * 按与反馈编号前缀统计当日反馈数量 (生成反馈编号用, 计算当日序号)。
     * <p>feedbackNoPrefix 形如 'FB20260804', 统计该前缀的反馈数后 +1 即为下一个序号。</p>
     *
     * @param feedbackNoPrefix 反馈编号前缀
     * @return 当日已生成反馈数
     */
    long countByFeedbackNoStartingWith(String feedbackNoPrefix);

    /**
     * 按与处理人统计指定状态的反馈数 (处理人工作量统计用)。
     *
     * @param assigneeId 处理人 ID
     * @param statuses   状态集合
     * @return 反馈数
     */
    long countByAssigneeIdAndStatusIn(String assigneeId, List<String> statuses);

    /**
     * 按统计已解决反馈的平均解决时长 (小时, 时间范围按创建时间过滤)。
     * <p>startTime/endTime 为可空, 为空时不限制对应时间边界。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 平均解决时长 (无记录返回 null)
     */
    @Query(value = "SELECT AVG(f.resolutionTimeHours) FROM ScrmFeedbackEntity f WHERE f.resolutionTimeHours IS NOT "
                          + "NULL AND (:startTime IS NULL OR f.createTime >= :startTime) AND (:endTime IS NULL OR "
                          + "f.createTime <= :endTime)")
    Double avgResolutionHours(
                                       @Param("startTime") LocalDateTime startTime,
                                       @Param("endTime") LocalDateTime endTime);

    /**
     * 按统计已响应反馈的平均响应时长 (小时, 时间范围按创建时间过滤)。
     * <p>startTime/endTime 为可空, 为空时不限制对应时间边界。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 平均响应时长 (无记录返回 null)
     */
    @Query(value = "SELECT AVG(f.responseTimeHours) FROM ScrmFeedbackEntity f WHERE f.responseTimeHours IS NOT NULL "
                          + "AND (:startTime IS NULL OR f.createTime >= :startTime) AND (:endTime IS NULL OR f.createTime <="
                          + ":endTime)")
    Double avgResponseHours(
                                     @Param("startTime") LocalDateTime startTime,
                                     @Param("endTime") LocalDateTime endTime);

    /**
     * 按统计已评价反馈的平均处理满意度 (时间范围按创建时间过滤)。
     * <p>startTime/endTime 为可空, 为空时不限制对应时间边界。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 平均满意度 (无记录返回 null)
     */
    @Query(value = "SELECT AVG(f.satisfactionScore) FROM ScrmFeedbackEntity f WHERE f.satisfactionScore IS NOT NULL "
                          + "AND (:startTime IS NULL OR f.createTime >= :startTime) AND (:endTime IS NULL OR f.createTime <="
                          + ":endTime)")
    Double avgSatisfaction(
                                    @Param("startTime") LocalDateTime startTime,
                                    @Param("endTime") LocalDateTime endTime);
}
