/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFollowUpTaskRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmFollowUpTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * SCRM 跟进任务数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmFollowUpTaskRepository extends JpaRepository<ScrmFollowUpTaskEntity, Long>,
        JpaSpecificationExecutor<ScrmFollowUpTaskEntity> {

    /**
     * 按与负责人查询任务列表。
     *
     * @param assigneeId 负责人 ID
     * @return 任务列表
     */
    List<ScrmFollowUpTaskEntity> findByAssigneeId(String assigneeId);

    /**
     * 按客户查询任务列表。
     *
     * @param customerId 客户 ID
     * @return 任务列表
     */
    List<ScrmFollowUpTaskEntity> findByCustomerId(Long customerId);

    /**
     * 查询所有未提醒且未完成的任务 (待提醒候选集)。
     * <p>
     * 由于 reminderMinutes 因任务而异, 难以直接用 JPQL 表达 plannedAt - reminderMinutes <= now,
     * 与当前时间比较决定是否真正触发提醒。
     * </p>
     *
     * @return 未提醒的任务列表
     */
    @Query(value = "SELECT t FROM ScrmFollowUpTaskEntity t WHERE t.reminded = FALSE AND t.status IN "
                          + "('PENDING', 'IN_PROGRESS') AND t.reminderMinutes IS NOT NULL ORDER BY t.plannedAt ASC")
    List<ScrmFollowUpTaskEntity> findUnremindedTasks();
}
