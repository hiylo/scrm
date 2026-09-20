/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmChurnRecoveryRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmChurnRecoveryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 客户流失挽留记录数据访问层。
 * <p>
 * 提供按预警 / 客户查询挽留记录, 以及激活率统计等能力, 供
 * {@code ScrmChurnWarningService.getRecoveryRate} 挽留效果分析使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmChurnRecoveryRepository extends JpaRepository<ScrmChurnRecoveryEntity, Long>,
        JpaSpecificationExecutor<ScrmChurnRecoveryEntity> {

    /**
     * 按与预警 ID 分页查询挽留记录 (按执行时间倒序)。
     *
     * @param warningId 预警 ID
     * @param pageable  分页参数
     * @return 挽留记录分页结果
     */
    Page<ScrmChurnRecoveryEntity> findByWarningIdOrderByActionExecutedAtDesc(Long warningId, Pageable pageable);

    /**
     * 按客户 ID 分页查询挽留记录 (按执行时间倒序)。
     *
     * @param customerId 客户 ID
     * @param pageable   分页参数
     * @return 挽留记录分页结果
     */
    Page<ScrmChurnRecoveryEntity> findByCustomerIdOrderByActionExecutedAtDesc(Long customerId, Pageable pageable);

    /**
     * 按与预警 ID 查询全部挽留记录 (按执行时间倒序)。
     *
     * @param warningId 预警 ID
     * @return 挽留记录列表
     */
    List<ScrmChurnRecoveryEntity> findByWarningIdOrderByActionExecutedAtDesc(Long warningId);

    /**
     * 按客户 ID 查询全部挽留记录 (按执行时间倒序)。
     *
     * @param customerId 客户 ID
     * @return 挽留记录列表
     */
    List<ScrmChurnRecoveryEntity> findByCustomerIdOrderByActionExecutedAtDesc(Long customerId);

    /**
     * 统计时间区间内的挽留动作总数与激活数 (挽留成功率用)。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return Object[]{totalCount, reactivatedCount}
     */
    @Query(value = "SELECT COUNT(r.id), SUM(CASE WHEN r.reactivated = TRUE THEN 1 ELSE 0 END) FROM "
                          + "ScrmChurnRecoveryEntity r WHERE (:startTime IS NULL OR r.actionExecutedAt >= :startTime) AND "
                          + "(:endTime IS NULL OR r.actionExecutedAt <= :endTime)")
    Object[] countRecoveryStats(
                                 @Param("startTime") LocalDateTime startTime,
                                 @Param("endTime") LocalDateTime endTime);
}
