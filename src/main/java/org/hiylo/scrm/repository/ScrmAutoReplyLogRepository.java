/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAutoReplyLogRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmAutoReplyLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 消息自动回复日志数据访问层。
 * <p>
 * 提供按规则 ID、客户 ID、发送时间范围与状态查询回复日志的能力, 支撑回复效果追踪、
 * 失败排查与统计聚合。日志写入由 {@code ScrmAutoReplyService.matchReply} 在规则命中
 * 并模拟发送后调用 recordLog 写入。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmAutoReplyLogRepository extends JpaRepository<ScrmAutoReplyLogEntity, Long>,
        JpaSpecificationExecutor<ScrmAutoReplyLogEntity> {

    /**
     * 按客户 ID 分页查询回复日志, 发送时间倒序返回。
     *
     * @param customerId 客户 ID
     * @param pageable   分页参数
     * @return 回复日志分页结果
     */
    Page<ScrmAutoReplyLogEntity> findByCustomerIdOrderBySentAtDesc(Long customerId, Pageable pageable);

    /**
     * 按查询最近 N 条回复日志, 发送时间倒序返回。
     *
     * @param pageable 分页参数 (取第 0 页, size 为返回条数上限)
     * @return 回复日志列表
     */
    List<ScrmAutoReplyLogEntity> findAllByOrderBySentAtDesc(Pageable pageable);

    /**
     * 按与发送时间范围查询全部回复日志（统计聚合用, 不分页）。
     *
     * @param start    起始时间 (含)
     * @param end      截止时间 (含)
     * @return 回复日志列表
     */
    List<ScrmAutoReplyLogEntity> findBySentAtBetweenOrderBySentAtAsc(LocalDateTime start, LocalDateTime end);

    /**
     * 按规则 ID 列表与发送时间范围查询全部回复日志（统计聚合用, 不分页）。
     *
     * @param ruleIds 规则 ID 列表
     * @param start   起始时间 (含)
     * @param end     截止时间 (含)
     * @return 回复日志列表
     */
    @Query("SELECT l FROM ScrmAutoReplyLogEntity l WHERE l.ruleId IN :ruleIds "
            + "AND l.sentAt BETWEEN :start AND :end ORDER BY l.sentAt ASC")
    List<ScrmAutoReplyLogEntity> findByRuleIdInAndSentAtBetween(
            @Param("ruleIds") List<Long> ruleIds,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}
