/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLifecycleHistoryRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmLifecycleHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 客户生命周期阶段转换历史数据访问层。
 * <p>
 * 提供按客户加载历史、按阶段聚合转换统计能力, 供
 * {@code ScrmLifecycleService} 历史查询与趋势分析使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmLifecycleHistoryRepository extends JpaRepository<ScrmLifecycleHistoryEntity, Long>,
        JpaSpecificationExecutor<ScrmLifecycleHistoryEntity> {

    /**
     * 按客户加载转换历史 (按 transitionTime 降序)。
     *
     * @param customerId 客户 ID
     * @return 历史列表
     */
    List<ScrmLifecycleHistoryEntity> findByCustomerIdOrderByTransitionTimeDesc(Long customerId);

    /**
     * 按转换类型聚合触发次数与目标阶段 (统计用)。
     *
     * @param startTime 转换时间起始 (含, 可空)
     * @param endTime   转换时间截止 (含, 可空)
     * @return Object[]{transitionType, toStageId, count}
     */
    @Query(value = "SELECT h.transitionType, h.toStageId, COUNT(h.id) FROM ScrmLifecycleHistoryEntity h WHERE "
                          + "(:startTime IS NULL OR h.transitionTime >= :startTime) AND (:endTime IS NULL OR h.transitionTime"
                          + "<= :endTime) GROUP BY h.transitionType, h.toStageId")
    List<Object[]> countByTransitionTypeAndToStage(
                                                   @Param("startTime") LocalDateTime startTime,
                                                   @Param("endTime") LocalDateTime endTime);

    /**
     * 按目标阶段聚合每日转换数 (趋势分析用)。
     *
     * @param stageId  目标阶段 ID
     * @param startTime 转换时间起始 (含)
     * @return Object[]{date(yyyy-MM-dd), count}
     */
    @Query(value = "SELECT FUNCTION('DATE', h.transitionTime), COUNT(h.id) FROM ScrmLifecycleHistoryEntity h WHERE "
                          + "h.toStageId = :stageId AND h.transitionTime >= :startTime GROUP BY FUNCTION('DATE',"
                          + "h.transitionTime) ORDER BY FUNCTION('DATE', h.transitionTime) ASC")
    List<Object[]> dailyCountByToStage(
                                       @Param("stageId") Long stageId,
                                       @Param("startTime") LocalDateTime startTime);

    /**
     * 按源阶段聚合转换数 (转化率计算用)。
     *
     * @param fromStageId  源阶段 ID
     * @param startTime    转换时间起始 (含, 可空)
     * @param endTime      转换时间截止 (含, 可空)
     * @return 转换数
     */
    @Query(value = "SELECT COUNT(h.id) FROM ScrmLifecycleHistoryEntity h WHERE h.fromStageId = :fromStageId AND "
                          + "(:startTime IS NULL OR h.transitionTime >= :startTime) AND (:endTime IS NULL OR h.transitionTime"
                          + "<= :endTime)")
    long countByFromStage(
                          @Param("fromStageId") Long fromStageId,
                          @Param("startTime") LocalDateTime startTime,
                          @Param("endTime") LocalDateTime endTime);

    /**
     * 按源阶段聚合平均上一阶段停留天数 (停留分析用)。
     *
     * @param fromStageId 源阶段 ID
     * @return 平均停留天数 (无数据时返回 null)
     */
    @Query(value = "SELECT AVG(h.durationInPreviousStage) FROM ScrmLifecycleHistoryEntity h WHERE h.fromStageId ="
                          + ":fromStageId AND h.durationInPreviousStage IS NOT NULL")
    Double avgDurationInPreviousStage(
                                      @Param("fromStageId") Long fromStageId);
}
