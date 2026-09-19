/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAutoTagRuleLogRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmAutoTagRuleLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 客户自动标签规则执行日志数据访问层。
 * <p>
 * 提供按规则 ID、客户 ID 与执行时间范围查询执行日志的能力, 支撑规则效果追踪、
 * 失败排查与统计聚合。日志写入由 {@code ScrmAutoTagService} 在规则命中并执行动作后调用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmAutoTagRuleLogRepository extends JpaRepository<ScrmAutoTagRuleLogEntity, Long>,
        JpaSpecificationExecutor<ScrmAutoTagRuleLogEntity> {

    /**
     * 按规则 ID 分页查询执行日志, 执行时间倒序返回。
     *
     * @param ruleId   规则 ID
     * @param pageable 分页参数
     * @return 执行日志分页结果
     */
    Page<ScrmAutoTagRuleLogEntity> findByRuleIdOrderByExecutedAtDesc(Long ruleId, Pageable pageable);

    /**
     * 按客户 ID 分页查询执行日志, 执行时间倒序返回。
     *
     * @param customerId 客户 ID
     * @param pageable   分页参数
     * @return 执行日志分页结果
     */
    Page<ScrmAutoTagRuleLogEntity> findByCustomerIdOrderByExecutedAtDesc(Long customerId, Pageable pageable);

    /**
     * 按规则 ID 与客户 ID 分页查询执行日志, 执行时间倒序返回。
     *
     * @param ruleId     规则 ID
     * @param customerId 客户 ID
     * @param pageable   分页参数
     * @return 执行日志分页结果
     */
    Page<ScrmAutoTagRuleLogEntity> findByRuleIdAndCustomerIdOrderByExecutedAtDesc(
            Long ruleId, Long customerId, Pageable pageable);

    /**
     * 按执行时间范围分页查询执行日志。
     *
     * @param start    起始时间 (含)
     * @param end      截止时间 (含)
     * @param pageable 分页参数
     * @return 执行日志分页结果
     */
    Page<ScrmAutoTagRuleLogEntity> findByExecutedAtBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);

    /**
     * 按执行时间范围查询全部执行日志（统计聚合用, 不分页）。
     *
     * @param start    起始时间 (含)
     * @param end      截止时间 (含)
     * @return 执行日志列表
     */
    List<ScrmAutoTagRuleLogEntity> findByExecutedAtBetweenOrderByExecutedAtAsc(LocalDateTime start, LocalDateTime end);

    /**
     * 按规则 ID 与执行时间范围查询全部执行日志（统计聚合用, 不分页）。
     *
     * @param ruleIds 规则 ID 列表
     * @param start   起始时间 (含)
     * @param end     截止时间 (含)
     * @return 执行日志列表
     */
    @Query("SELECT l FROM ScrmAutoTagRuleLogEntity l WHERE l.ruleId IN :ruleIds "
            + "AND l.executedAt BETWEEN :start AND :end ORDER BY l.executedAt ASC")
    List<ScrmAutoTagRuleLogEntity> findByRuleIdInAndExecutedAtBetween(
            @Param("ruleIds") List<Long> ruleIds,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}
