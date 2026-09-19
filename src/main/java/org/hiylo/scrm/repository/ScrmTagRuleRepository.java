/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTagRuleRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmTagRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 客户标签自动规则数据访问层。
 * <p>
 * 提供按状态加载活跃规则 (批量执行用)、按标签 ID 查询规则 (标签删除前级联检查)、
 * 增量更新执行统计等能力, 供 {@code ScrmTagSystemService} 使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmTagRuleRepository extends JpaRepository<ScrmTagRuleEntity, Long>,
        JpaSpecificationExecutor<ScrmTagRuleEntity> {

    /**
     * 按状态加载规则 (批量执行时加载全部 ACTIVE 规则)。
     *
     * @param status   状态
     * @return 规则列表
     */
    List<ScrmTagRuleEntity> findByStatus(String status);

    /**
     * 按与标签 ID 查询规则列表 (标签删除前级联检查)。
     *
     * @param tagId    标签 ID
     * @return 规则列表
     */
    List<ScrmTagRuleEntity> findByTagId(Long tagId);

    /**
     * 增量更新规则的执行时间与匹配客户数 (避免乐观锁冲突, 直接 SQL 更新)。
     *
     * @param ruleId        规则 ID
     * @param executedAt    执行时间
     * @param matchedCount  本次匹配客户数
     * @return 受影响行数 (1 表示更新成功)
     */
    @Modifying
    @Query(value = "UPDATE ScrmTagRuleEntity r SET r.lastExecutedAt = :executedAt, r.matchedCount = :matchedCount "
                          + "WHERE r.id = :ruleId")
    int updateExecutionStats(@Param("ruleId") Long ruleId,
                             @Param("executedAt") LocalDateTime executedAt,
                             @Param("matchedCount") int matchedCount);
}
