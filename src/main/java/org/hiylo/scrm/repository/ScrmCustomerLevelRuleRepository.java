/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerLevelRuleRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCustomerLevelRuleEntity;
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
 * SCRM 客户等级升降级规则数据访问层。
 * <p>
 * 提供按目标等级 / 规则类型 / 启用状态加载规则, 以及增量更新匹配统计等能力, 供
 * {@code ScrmCustomerLevelService.evaluateRules} 评估引擎使用。评估时通过
 * {@link #findByRuleTypeAndEnabledTrueOrderByPriorityAsc(Long, String)}
 * 按规则类型加载启用规则并按优先级升序遍历。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCustomerLevelRuleRepository extends JpaRepository<ScrmCustomerLevelRuleEntity, Long>,
        JpaSpecificationExecutor<ScrmCustomerLevelRuleEntity> {

    /**
     * 按与规则类型加载启用规则并按优先级升序排列 (数字越小越优先)。
     *
     * @param ruleType 规则类型: UPGRADE / DOWNGRADE
     * @return 启用规则列表 (按 priority ASC)
     */
    List<ScrmCustomerLevelRuleEntity> findByRuleTypeAndEnabledTrueOrderByPriorityAsc(String ruleType);

    /**
     * 按分页查询规则。
     *
     * @param pageable  分页参数
     * @return 规则分页结果
     */

    /**
     * 按目标等级 ID 查询规则 (等级删除前引用校验用)。
     *
     * @param targetLevelId 目标等级 ID
     * @return 规则列表
     */
    List<ScrmCustomerLevelRuleEntity> findByTargetLevelId(Long targetLevelId);

    /**
     * 增量更新规则的匹配次数与最近匹配时间 (避免乐观锁冲突, 直接 SQL 更新)。
     *
     * @param ruleId  规则 ID
     * @param matchAt 匹配时间
     * @return 受影响行数 (1 表示更新成功)
     */
    @Modifying
    @Query(value = "UPDATE ScrmCustomerLevelRuleEntity r SET r.matchCount = r.matchCount + 1, r.lastMatchAt ="
                          + ":matchAt WHERE r.id = :ruleId")
    int incrementMatchCount(@Param("ruleId") Long ruleId, @Param("matchAt") LocalDateTime matchAt);
}
