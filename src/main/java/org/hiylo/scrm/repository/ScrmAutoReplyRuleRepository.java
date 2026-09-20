/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAutoReplyRuleRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmAutoReplyRuleEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * SCRM 消息自动回复规则数据访问层。
 * <p>
 * 提供按加载启用规则、按规则类型加载、兜底规则查询与触发次数增量更新等能力,
 * 供 {@code ScrmAutoReplyService} 匹配引擎使用。匹配时通过
 * {@link #findByEnabledTrueOrderByPriorityAsc(Long)} 加载当前账号全部启用规则
 * 并按优先级升序遍历评估。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmAutoReplyRuleRepository extends JpaRepository<ScrmAutoReplyRuleEntity, Long>,
        JpaSpecificationExecutor<ScrmAutoReplyRuleEntity> {

    /**
     * 按加载启用规则并按优先级升序排列（数字越小越优先）。
     * <p>匹配引擎调用此方法获取待评估规则集。</p>
     *
     * @return 启用规则列表（按 priority ASC）
     */
    List<ScrmAutoReplyRuleEntity> findByEnabledTrueOrderByPriorityAsc();

    /**
     * 按与规则类型加载启用规则并按优先级升序排列。
     *
     * @param ruleType 规则类型
     * @return 启用规则列表（按 priority ASC）
     */
    List<ScrmAutoReplyRuleEntity> findByRuleTypeAndEnabledTrueOrderByPriorityAsc(String ruleType);

    /**
     * 按加载兜底规则（仅一条, 启用状态）。
     *
     * @return 兜底规则（不存在时返回空）
     */
    Optional<ScrmAutoReplyRuleEntity> findByFallbackRuleTrueAndEnabledTrue(
            );

    /**
     * 按分页查询规则并按创建时间倒序返回。
     *
     * @param pageable 分页参数
     * @return 规则分页结果
     */

    /**
     * 增量更新规则的触发次数与最近触发时间（避免乐观锁冲突, 直接 SQL 更新）。
     *
     * @param ruleId       规则 ID
     * @param triggeredAt  触发时间
     * @return 受影响行数 (1 表示更新成功)
     */
    @Modifying
    @Query(value = "UPDATE ScrmAutoReplyRuleEntity r SET r.triggerCount = r.triggerCount + 1, r.lastTriggeredAt ="
                          + ":triggeredAt WHERE r.id = :ruleId")
    int incrementTriggerCount(@Param("ruleId") Long ruleId,
                              @Param("triggeredAt") LocalDateTime triggeredAt);

    /**
     * 清除同账号下其他兜底规则标记（设置兜底规则时调用, 保证唯一兜底）。
     *
     * @return 受影响行数
     */
    @Modifying
    @Query("UPDATE ScrmAutoReplyRuleEntity r SET r.fallbackRule = false WHERE r.fallbackRule = true")
    int clearFallbackFlags();
}
