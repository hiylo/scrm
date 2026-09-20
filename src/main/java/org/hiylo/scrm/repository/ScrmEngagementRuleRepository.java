/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmEngagementRuleRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmEngagementRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * SCRM 互动评分规则数据访问层。
 * <p>
 * 提供按行为类型 + 渠道匹配启用规则 (供 {@code ScrmEngagementScoreService.matchRule} 使用),
 * 以及增量更新规则匹配次数等能力。渠道匹配优先精确等于, 其次为 channel 为空 (表示任意渠道)。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmEngagementRuleRepository extends JpaRepository<ScrmEngagementRuleEntity, Long>,
        JpaSpecificationExecutor<ScrmEngagementRuleEntity> {

    /**
     * 加载账号下指定行为类型且启用的规则 (含 channel 为空与指定 channel 的规则, 由 Service 过滤)。
     *
     * @param behaviorType 行为类型
     * @return 启用规则列表 (channel 为空或等于入参 channel)
     */
    List<ScrmEngagementRuleEntity> findByBehaviorTypeAndEnabledTrue(String behaviorType);

    /**
     * 增量更新规则的匹配次数 (避免乐观锁冲突, 直接 SQL 更新)。
     *
     * @param ruleId 规则 ID
     * @return 受影响行数 (1 表示更新成功)
     */
    @Modifying
    @Query("UPDATE ScrmEngagementRuleEntity r SET r.matchCount = r.matchCount + 1 WHERE r.id = :ruleId")
    int incrementMatchCount(@Param("ruleId") Long ruleId);
}
