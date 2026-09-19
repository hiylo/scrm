/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAutoTagRuleRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmAutoTagRuleEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 客户自动标签规则数据访问层。
 * <p>
 * 提供按触发事件加载启用规则、按规则 ID 增量更新匹配统计等能力, 供
 * {@code ScrmAutoTagService} 与规则评估引擎使用。评估时通过
 * {@link #findByTriggerEventAndEnabledTrueOrderByPriorityAsc(Long, String)}
 * 加载指定触发事件的全部启用规则并按优先级升序遍历。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmAutoTagRuleRepository extends JpaRepository<ScrmAutoTagRuleEntity, Long>,
        JpaSpecificationExecutor<ScrmAutoTagRuleEntity> {

    /**
     * 按与触发事件加载启用规则并按优先级升序排列（数字越小越优先）。
     * <p>规则评估引擎调用此方法获取待评估规则集。</p>
     *
     * @param triggerEvent 触发事件
     * @return 启用规则列表（按 priority ASC）
     */
    List<ScrmAutoTagRuleEntity> findByTriggerEventAndEnabledTrueOrderByPriorityAsc(String triggerEvent);

    /**
     * 按分页查询规则并按创建时间倒序返回。
     *
     * @param pageable 分页参数
     * @return 规则分页结果
     */

    /**
     * 增量更新规则的匹配次数与最近匹配时间（避免乐观锁冲突, 直接 SQL 更新）。
     *
     * @param ruleId      规则 ID
     * @param matchAt     匹配时间
     * @return 受影响行数 (1 表示更新成功)
     */
    @Modifying
    @Query(value = "UPDATE ScrmAutoTagRuleEntity r SET r.matchCount = r.matchCount + 1, r.lastMatchAt = :matchAt "
                          + "WHERE r.id = :ruleId")
    int incrementMatchCount(@Param("ruleId") Long ruleId, @Param("matchAt") LocalDateTime matchAt);
}
