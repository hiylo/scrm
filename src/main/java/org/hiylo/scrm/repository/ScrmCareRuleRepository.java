/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCareRuleRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCareRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 客户关怀规则数据访问层。
 * <p>
 * 提供按关怀类型 / 启用状态加载规则, 以及增量更新执行统计等能力, 供
 * {@code ScrmCustomerCareService.generateDailyTasks} 任务生成引擎使用。
 * 任务生成时通过 {@link #findByEnabledTrueOrderByPriorityAsc} 加载启用规则并按优先级升序遍历。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCareRuleRepository extends JpaRepository<ScrmCareRuleEntity, Long>,
        JpaSpecificationExecutor<ScrmCareRuleEntity> {

    /**
     * 按加载全部启用规则并按优先级升序排列 (数字越小越优先)。
     *
     * @return 启用规则列表 (按 priority ASC)
     */
    List<ScrmCareRuleEntity> findByEnabledTrueOrderByPriorityAsc();

    /**
     * 按与关怀类型加载启用规则并按优先级升序排列。
     *
     * @param careType 关怀类型: BIRTHDAY / FESTIVAL / ANNIVERSARY / MEMBERSHIP_EXPIRY / INACTIVITY_REMINDER / CUSTOM
     * @return 启用规则列表 (按 priority ASC)
     */
    List<ScrmCareRuleEntity> findByCareTypeAndEnabledTrueOrderByPriorityAsc(String careType);

    /**
     * 增量更新规则的执行次数与最近执行时间 (避免乐观锁冲突, 直接 SQL 更新)。
     *
     * @param ruleId    规则 ID
     * @param executedAt 执行时间
     * @return 受影响行数 (1 表示更新成功)
     */
    @Modifying
    @Query(value = "UPDATE ScrmCareRuleEntity r SET r.executionCount = r.executionCount + 1, r.lastExecutedAt ="
                          + ":executedAt WHERE r.id = :ruleId")
    int incrementExecutionCount(@Param("ruleId") Long ruleId, @Param("executedAt") LocalDateTime executedAt);
}
