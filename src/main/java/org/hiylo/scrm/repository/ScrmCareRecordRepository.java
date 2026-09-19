/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCareRecordRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCareRecordEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 客户关怀记录数据访问层。
 * <p>
 * 提供按客户 / 关怀类型 / 关怀结果查询记录, 以及关怀效果统计等能力, 供
 * {@code ScrmCustomerCareService.getCareStats} / {@code getCareEffectiveness}
 * 关怀效果分析使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCareRecordRepository extends JpaRepository<ScrmCareRecordEntity, Long>,
        JpaSpecificationExecutor<ScrmCareRecordEntity> {

    /**
     * 按客户查询全部关怀记录 (按执行时间倒序, 客户关怀历史用)。
     *
     * @param customerId 客户 ID
     * @return 关怀记录列表
     */
    List<ScrmCareRecordEntity> findByCustomerIdOrderByExecutedAtDesc(Long customerId);

    /**
     * 按客户分页查询关怀记录 (按执行时间倒序)。
     *
     * @param customerId 客户 ID
     * @param pageable   分页参数
     * @return 关怀记录分页结果
     */
    Page<ScrmCareRecordEntity> findByCustomerIdOrderByExecutedAtDesc(Long customerId, Pageable pageable);

    /**
     * 按关怀类型聚合记录数 (统计用)。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return Object[]{careType, count}
     */
    @Query(value = "SELECT r.careType, COUNT(r.id) FROM ScrmCareRecordEntity r WHERE (:startTime IS NULL OR "
                          + "r.executedAt >= :startTime) AND (:endTime IS NULL OR r.executedAt <= :endTime) GROUP BY "
                          + "r.careType")
    List<Object[]> countByCareType(
                                    @Param("startTime") LocalDateTime startTime,
                                    @Param("endTime") LocalDateTime endTime);

    /**
     * 按关怀结果聚合记录数 (统计用)。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return Object[]{careResult, count}
     */
    @Query(value = "SELECT r.careResult, COUNT(r.id) FROM ScrmCareRecordEntity r WHERE (:startTime IS NULL OR "
                          + "r.executedAt >= :startTime) AND (:endTime IS NULL OR r.executedAt <= :endTime) GROUP BY "
                          + "r.careResult")
    List<Object[]> countByCareResult(
                                      @Param("startTime") LocalDateTime startTime,
                                      @Param("endTime") LocalDateTime endTime);
}
