/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCommunitySopRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCommunitySopEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * SCRM 社群 SOP 数据访问层。
 * <p>
 * 提供按触发类型加载启用的定时 SOP (供 {@code ScrmCommunityService.processTimeBasedSops} 调度),
 * 以及增量更新执行次数与最后执行时间 (避免乐观锁冲突)。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCommunitySopRepository extends JpaRepository<ScrmCommunitySopEntity, Long>,
        JpaSpecificationExecutor<ScrmCommunitySopEntity> {

    /**
     * 按、触发类型加载启用的 SOP 列表。
     *
     * @param triggerType 触发类型
     * @param enabled     是否启用
     * @return SOP 列表
     */
    List<ScrmCommunitySopEntity> findByTriggerTypeAndEnabled(String triggerType, Boolean enabled);

    /**
     * 增量更新 SOP 的执行次数与最后执行时间 (避免乐观锁冲突, 直接 SQL 更新)。
     *
     * @param sopId         SOP ID
     * @param executedAt    执行时间
     * @return 受影响行数 (1 表示更新成功)
     */
    @Modifying
    @Query(value = "UPDATE ScrmCommunitySopEntity s SET s.executionCount = s.executionCount + 1, s.lastExecutedAt ="
                          + ":executedAt WHERE s.id = :sopId")
    int incrementExecutionCount(@Param("sopId") Long sopId,
                                @Param("executedAt") java.time.LocalDateTime executedAt);
}
