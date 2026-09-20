/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmJourneyEnrollmentRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmJourneyEnrollmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 旅程入营记录数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmJourneyEnrollmentRepository extends JpaRepository<ScrmJourneyEnrollmentEntity, Long>,
        JpaSpecificationExecutor<ScrmJourneyEnrollmentEntity> {

    /**
     * 按旅程 ID 查询入营记录。
     *
     * @param journeyId 旅程 ID
     * @return 入营记录列表
     */
    List<ScrmJourneyEnrollmentEntity> findByJourneyId(Long journeyId);

    /**
     * 按客户 ID 查询其入营记录。
     *
     * @param customerId 客户 ID
     * @return 入营记录列表
     */
    List<ScrmJourneyEnrollmentEntity> findByCustomerId(Long customerId);

    /**
     * 校验客户是否已入指定旅程 (避免重复入营)。
     *
     * @param journeyId  旅程 ID
     * @param customerId 客户 ID
     * @return 已存在活跃入营返回 true
     */
    boolean existsByJourneyIdAndCustomerIdAndStatus(Long journeyId, Long customerId, String status);

    /**
     * 删除指定旅程下的全部入营记录 (旅程删除时级联清理)。
     *
     * @param journeyId 旅程 ID
     * @return 删除条数
     */
    long deleteByJourneyId(Long journeyId);

    /**
     * 查询所有待执行的 WAIT 到期入营记录 (定时任务用)。
     * <p>
     * 筛选条件: 状态为 ACTIVE 且 nextStepAt 非空且早于等于阈值时间。
     * </p>
     *
     * @param threshold 阈值时间 (通常为当前时间)
     * @return 到期待执行的入营记录列表
     */
    @Query(value = "SELECT e FROM ScrmJourneyEnrollmentEntity e WHERE e.status = 'ACTIVE' AND e.nextStepAt IS NOT NULL "
                          + "AND e.nextStepAt <= :threshold ORDER BY e.nextStepAt ASC")
    List<ScrmJourneyEnrollmentEntity> findPendingWaitEnrollments(@Param("threshold") LocalDateTime threshold);

}
