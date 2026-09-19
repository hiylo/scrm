/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAccountHealthRepository.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmAccountHealthEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * SCRM 账号健康度检测记录数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmAccountHealthRepository extends JpaRepository<ScrmAccountHealthEntity, Long> {

    /**
     * 查询指定账号最新一条健康检测记录。
     *
     * @param accountId 账号 ID
     * @return 最新健康记录（无记录返回 empty）
     */
    Optional<ScrmAccountHealthEntity> findTopByAccountIdOrderByCheckedAtDesc(Long accountId);

    /**
     * 按账号 ID 分页查询健康检测历史, 检测时间倒序。
     *
     * @param accountId 账号 ID
     * @param pageable  分页参数
     * @return 健康记录分页结果
     */
    Page<ScrmAccountHealthEntity> findByAccountIdOrderByCheckedAtDesc(Long accountId, Pageable pageable);

    /**
     * 按检测结果分页查询（如查询所有 OFFLINE 记录）。
     *
     * @param checkResult 检测结果: HEALTHY / OFFLINE / FROZEN / UNKNOWN / ERROR
     * @param pageable    分页参数
     * @return 健康记录分页结果
     */
    Page<ScrmAccountHealthEntity> findByCheckResult(String checkResult, Pageable pageable);

    /**
     * 按检测时间区间分页查询健康记录。
     *
     * @param start    起始时间（含）
     * @param end      结束时间（含）
     * @param pageable 分页参数
     * @return 健康记录分页结果
     */
    Page<ScrmAccountHealthEntity> findByCheckedAtBetween(
                                                                     LocalDateTime start,
                                                                     LocalDateTime end,
                                                                     Pageable pageable);

    /**
     * 统计指定检测结果的健康记录数。
     *
     * @param checkResult 检测结果
     * @return 记录数
     */
    long countByCheckResult(String checkResult);

    /**
     * 统计指定账号下的健康检测记录总数。
     *
     * @return 记录数
     */
}
