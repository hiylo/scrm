/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmJourneyProgressLogRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmJourneyProgressLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * SCRM 旅程进度日志数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmJourneyProgressLogRepository extends JpaRepository<ScrmJourneyProgressLogEntity, Long>,
        JpaSpecificationExecutor<ScrmJourneyProgressLogEntity> {

    /**
     * 按入营记录 ID 查询进度日志, 按执行时间升序 (时间线展示)。
     *
     * @param enrollmentId 入营记录 ID
     * @return 进度日志列表
     */
    List<ScrmJourneyProgressLogEntity> findByEnrollmentIdOrderByExecutedAtAsc(Long enrollmentId);


    /**
     * 删除指定入营记录的全部进度日志。
     *
     * @param enrollmentId 入营记录 ID
     * @return 删除条数
     */
    long deleteByEnrollmentId(Long enrollmentId);

    /**
     * 删除指定旅程下的全部进度日志 (旅程删除时级联清理)。
     *
     * @param journeyId 旅程 ID
     * @return 删除条数
     */
    long deleteByJourneyId(Long journeyId);

    /**
     * 按与旅程聚合各步骤的执行结果计数 (步骤统计用)。
     *
     * @param journeyId 旅程 ID
     * @return Object[]{stepId, actionResult, count}
     */
    @Query(value = "SELECT e.stepId, e.actionResult, COUNT(e.id) FROM ScrmJourneyProgressLogEntity e WHERE "
                          + "e.journeyId = :journeyId GROUP BY e.stepId, e.actionResult")
    List<Object[]> aggregateByStepAndResult(
                                             @Param("journeyId") Long journeyId);
}
