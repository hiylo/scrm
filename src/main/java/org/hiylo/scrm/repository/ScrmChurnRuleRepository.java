/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmChurnRuleRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmChurnRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 客户流失预警规则数据访问层。
 * <p>
 * 提供按风险等级 / 启用状态加载规则, 以及增量更新匹配统计等能力, 供
 * {@code ScrmChurnWarningService.scanCustomer} 扫描引擎使用。扫描时通过
 * {@link #findByEnabledTrueOrderByPriorityAsc} 加载启用规则并按优先级升序遍历。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmChurnRuleRepository extends JpaRepository<ScrmChurnRuleEntity, Long>,
        JpaSpecificationExecutor<ScrmChurnRuleEntity> {

    /**
     * 按加载全部启用规则并按优先级升序排列 (数字越小越优先)。
     *
     * @return 启用规则列表 (按 priority ASC)
     */
    List<ScrmChurnRuleEntity> findByEnabledTrueOrderByPriorityAsc();

    /**
     * 按与风险等级加载启用规则并按优先级升序排列。
     *
     * @param riskLevel 风险等级: HIGH / MEDIUM / LOW
     * @return 启用规则列表 (按 priority ASC)
     */
    List<ScrmChurnRuleEntity> findByRiskLevelAndEnabledTrueOrderByPriorityAsc(String riskLevel);

    /**
     * 增量更新规则的匹配次数与最近匹配时间 (避免乐观锁冲突, 直接 SQL 更新)。
     *
     * @param ruleId  规则 ID
     * @param matchAt 匹配时间
     * @return 受影响行数 (1 表示更新成功)
     */
    @Modifying
    @Query(value = "UPDATE ScrmChurnRuleEntity r SET r.matchCount = r.matchCount + 1, r.lastMatchAt = :matchAt WHERE "
                          + "r.id = :ruleId")
    int incrementMatchCount(@Param("ruleId") Long ruleId, @Param("matchAt") LocalDateTime matchAt);
}
