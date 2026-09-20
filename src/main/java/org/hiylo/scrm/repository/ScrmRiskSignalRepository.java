/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmRiskSignalRepository.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmRiskSignalEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 风控信号数据访问层。
 * <p>
 * 提供按账号 / 人设 / 风险等级 / 时间窗口的查询与计数能力，以及看板按日聚合趋势。
 * 继承 {@link JpaSpecificationExecutor} 支持多条件动态组合查询。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmRiskSignalRepository extends JpaRepository<ScrmRiskSignalEntity, Long>,
        JpaSpecificationExecutor<ScrmRiskSignalEntity> {

    /**
     * 根据账号 ID 查询风控信号列表。
     *
     * @param accountId 账号 ID
     * @return 风控信号列表
     */
    List<ScrmRiskSignalEntity> findByAccountId(Long accountId);

    /**
     * 根据规则 ID 查询风控信号列表 (用于删除规则前检查是否有信号引用)。
     *
     * @param ruleId 规则 ID (字符串形式)
     * @return 风控信号列表
     */
    List<ScrmRiskSignalEntity> findByRuleId(String ruleId);

    /**
     * 统计指定规则 ID 的风控信号数 (用于删除规则前检查是否有信号引用)。
     *
     * @param ruleId 规则 ID (字符串形式)
     * @return 信号数
     */
    long countByRuleId(String ruleId);

    /**
     * 根据人设 ID 查询风控信号列表。
     *
     * @param personaId 人设 ID
     * @return 风控信号列表
     */
    List<ScrmRiskSignalEntity> findByPersonaId(String personaId);

    /**
     * 根据风险等级查询风控信号列表。
     *
     * @param riskLevel 风险等级：LOW / MEDIUM / HIGH / CRITICAL
     * @return 风控信号列表
     */
    List<ScrmRiskSignalEntity> findByRiskLevel(String riskLevel);

    /**
     * 根据风险等级分页查询风控信号。
     *
     * @param riskLevel 风险等级
     * @param pageable  分页参数
     * @return 风控信号分页
     */
    Page<ScrmRiskSignalEntity> findByRiskLevel(String riskLevel, Pageable pageable);

    /**
     * 根据信号类型分页查询风控信号。
     *
     * @param signalType 信号类型
     * @param pageable   分页参数
     * @return 风控信号分页
     */
    Page<ScrmRiskSignalEntity> findBySignalType(String signalType, Pageable pageable);

    /**
     * 按触发时间区间查询风控信号（看板时间窗口聚合用）。
     *
     * @param start    起始时间（含）
     * @param end      结束时间（含）
     * @return 风控信号列表
     */
    List<ScrmRiskSignalEntity> findByTriggeredAtBetween(
                                                                    LocalDateTime start,
                                                                    LocalDateTime end);

    /**
     * 统计指定账号下的风控信号总数。
     *
     * @return 风控信号总数
     */

    /**
     * 统计指定风险等级的风控信号数。
     *
     * @param riskLevel 风险等级
     * @return 信号数
     */
    long countByRiskLevel(String riskLevel);

    /**
     * 统计指定信号类型的风控信号数。
     *
     * @param signalType 信号类型
     * @return 信号数
     */
    long countBySignalType(String signalType);

    /**
     * 按风险等级聚合指定账号的风控信号数（看板分布用, 避免 N+1）。
     *
     * @return Object[]{riskLevel, count}
     */
    @Query("SELECT e.riskLevel, COUNT(e.id) FROM ScrmRiskSignalEntity e GROUP BY e.riskLevel")
    List<Object[]> countGroupByRiskLevel();

    /**
     * 按信号类型聚合指定账号的风控信号数（看板分布用, 避免 N+1）。
     *
     * @return Object[]{signalType, count}
     */
    @Query("SELECT e.signalType, COUNT(e.id) FROM ScrmRiskSignalEntity e GROUP BY e.signalType")
    List<Object[]> countGroupBySignalType();

    /**
     * 按日聚合风控信号触发数（看板近 7 天趋势用, native query 借助 PostgreSQL to_char）。
     *
     * @param from     起始时间（含）
     * @return Object[]{date(yyyy-MM-dd), count}
     */
    default List<Object[]> dailyCountByTriggeredAt(LocalDateTime from) {
        return DailyStatsRepository.toRows(DailyStatsRepository.getInstance().countByDay(
                "scrm.scrm_risk_signal", "triggered_at", "",
                Map.of(), from, null));
    }
}
