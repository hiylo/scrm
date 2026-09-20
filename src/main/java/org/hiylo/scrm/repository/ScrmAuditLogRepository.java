/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAuditLogRepository.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmAuditLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 操作审计日志数据访问层。
 * <p>
 * 提供按用户 / 资源 / 结果 / 时间窗口的查询与统计能力, 支撑审计日志检索与看板聚合。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmAuditLogRepository extends JpaRepository<ScrmAuditLogEntity, Long> {

    /**
     * 按操作人用户 ID 查询审计日志, 按操作时间倒序返回。
     *
     * @param userId   操作人用户 ID
     * @param pageable 分页参数
     * @return 审计日志分页结果
     */
    Page<ScrmAuditLogEntity> findByUserIdOrderByOperatedAtDesc(String userId, Pageable pageable);

    /**
     * 按操作资源查询审计日志。
     *
     * @param resource 资源标识
     * @param pageable 分页参数
     * @return 审计日志分页结果
     */
    Page<ScrmAuditLogEntity> findByResource(String resource, Pageable pageable);

    /**
     * 按操作结果查询审计日志 (SUCCESS / FAILED)。
     *
     * @param result   操作结果
     * @param pageable 分页参数
     * @return 审计日志分页结果
     */
    Page<ScrmAuditLogEntity> findByResult(String result, Pageable pageable);

    /**
     * 按操作时间区间查询审计日志。
     *
     * @param start    起始时间 (含)
     * @param end      结束时间 (含)
     * @param pageable 分页参数
     * @return 审计日志分页结果
     */
    Page<ScrmAuditLogEntity> findByOperatedAtBetween(
                                                                 LocalDateTime start,
                                                                 LocalDateTime end,
                                                                 Pageable pageable);

    /**
     * 按 ID 查询审计日志, 按操作时间倒序返回。
     *
     * @param pageable 分页参数
     * @return 审计日志分页结果
     */
    Page<ScrmAuditLogEntity> findAllByOrderByOperatedAtDesc(Pageable pageable);

    /**
     * 统计指定结果类型的审计日志数。
     *
     * @param result 操作结果 (SUCCESS / FAILED)
     * @return 审计日志数
     */
    long countByResult(String result);


    /**
     * 统计指定资源的审计日志数。
     *
     * @param resource 资源标识
     * @return 审计日志数
     */
    long countByResource(String resource);

    /**
     * 按日聚合审计日志数 (native query 借助 PostgreSQL to_char)。
     *
     * @param start    起始时间 (含)
     * @param end      结束时间 (含)
     * @return Object[]{date(yyyy-MM-dd), count}
     */
    default List<Object[]> dailyCountByOperatedAt(LocalDateTime start, LocalDateTime end) {
        return DailyStatsRepository.toRows(DailyStatsRepository.getInstance().countByDay(
                "scrm.scrm_audit_log", "operated_at", "",
                Map.of(), start, end));
    }

    /**
     * 按资源聚合审计日志数 (看板资源分布用, 避免 N+1)。
     *
     * @return Object[]{resource, count}
     */
    @Query("SELECT e.resource, COUNT(e.id) FROM ScrmAuditLogEntity e GROUP BY e.resource")
    List<Object[]> countGroupByResource();


    /**
     * 按与操作结果查询审计日志 (数据隔离用)。
     *
     * @param result   操作结果 (SUCCESS / FAILED)
     * @param pageable 分页参数
     * @return 审计日志分页结果
     */
    Page<ScrmAuditLogEntity> findByResultOrderByOperatedAtDesc(String result, Pageable pageable);
}
