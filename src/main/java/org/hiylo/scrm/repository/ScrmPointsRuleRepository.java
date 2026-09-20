/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPointsRuleRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmPointsRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * SCRM 积分规则数据访问层。
 * <p>
 * 提供按触发事件与规则类型加载启用规则 (供 {@code ScrmPointsService.earnPoints} 匹配), 以及
 * 增量更新触发次数等能力。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmPointsRuleRepository extends JpaRepository<ScrmPointsRuleEntity, Long>,
        JpaSpecificationExecutor<ScrmPointsRuleEntity> {

    /**
     * 按、触发事件与规则类型加载启用规则。
     *
     * @param triggerEvent 触发事件
     * @param ruleType     规则类型: EARN / REDEEM
     * @return 启用规则列表
     */
    List<ScrmPointsRuleEntity> findByTriggerEventAndRuleTypeAndEnabledTrue(String triggerEvent, String ruleType);

    /**
     * 增量更新规则的触发次数 (避免乐观锁冲突, 直接 SQL 更新)。
     *
     * @param ruleId 规则 ID
     * @return 受影响行数 (1 表示更新成功)
     */
    @Modifying
    @Query("UPDATE ScrmPointsRuleEntity r SET r.triggerCount = r.triggerCount + 1 WHERE r.id = :ruleId")
    int incrementTriggerCount(@Param("ruleId") Long ruleId);
}
