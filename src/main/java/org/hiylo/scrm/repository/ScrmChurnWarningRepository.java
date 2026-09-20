/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmChurnWarningRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmChurnWarningEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * SCRM 客户流失预警数据访问层。
 * <p>
 * 提供按客户 / 规则 / 状态查询预警, 冷却期检查, 高风险客户聚合, 风险等级分布统计等能力,
 * 供 {@code ScrmChurnWarningService} 扫描与统计引擎使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmChurnWarningRepository extends JpaRepository<ScrmChurnWarningEntity, Long>,
        JpaSpecificationExecutor<ScrmChurnWarningEntity> {

    /**
     * 查询客户在指定时间之后是否已有 ACTIVE 状态预警 (冷却期检查用)。
     *
     * @param customerId 客户 ID
     * @param since     起始时间 (含)
     * @return 是否存在 ACTIVE 预警
     */
    Optional<ScrmChurnWarningEntity> findFirstByCustomerIdAndStatusAndDetectedAtAfterOrderByDetectedAtDesc(Long customerId, String status, LocalDateTime since);

    /**
     * 按状态分页查询预警 (按检测时间倒序)。
     *
     * @param status   预警状态 (可空)
     * @param pageable 分页参数
     * @return 预警分页结果
     */
    Page<ScrmChurnWarningEntity> findByStatus(String status, Pageable pageable);

    /**
     * 按与负责人分页查询预警 (按检测时间倒序)。
     *
     * @param assigneeId 负责人 ID
     * @param pageable   分页参数
     * @return 预警分页结果
     */
    Page<ScrmChurnWarningEntity> findByAssigneeId(String assigneeId, Pageable pageable);

    /**
     * 按分页查询预警 (按检测时间倒序)。
     *
     * @param pageable 分页参数
     * @return 预警分页结果
     */

    /**
     * 高风险客户列表: 按客户聚合最大风险分, 取风险分最高的客户 (按风险分降序)。
     *
     * @param pageable 分页参数
     * @return Object[]{customerId, customerName, maxRiskScore, riskLevel, latestDetectedAt}
     */
    @Query("SELECT w.customerId, w.customerName, MAX(w.riskScore), w.riskLevel, MAX(w.detectedAt) "
            + "FROM ScrmChurnWarningEntity w WHERE w.status = 'ACTIVE' "
            + "GROUP BY w.customerId, w.customerName, w.riskLevel "
            + "ORDER BY MAX(w.riskScore) DESC")
    List<Object[]> findAtRiskCustomers(Pageable pageable);

    /**
     * 按风险等级聚合 ACTIVE 预警数 (统计用)。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return Object[]{riskLevel, count}
     */
    @Query(value = "SELECT w.riskLevel, COUNT(w.id) FROM ScrmChurnWarningEntity w WHERE (:startTime IS NULL OR "
                          + "w.detectedAt >= :startTime) AND (:endTime IS NULL OR w.detectedAt <= :endTime) GROUP BY "
                          + "w.riskLevel")
    List<Object[]> countByRiskLevel(
                                     @Param("startTime") LocalDateTime startTime,
                                     @Param("endTime") LocalDateTime endTime);

    /**
     * 按状态聚合预警数 (统计用)。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return Object[]{status, count}
     */
    @Query(value = "SELECT w.status, COUNT(w.id) FROM ScrmChurnWarningEntity w WHERE (:startTime IS NULL OR "
                          + "w.detectedAt >= :startTime) AND (:endTime IS NULL OR w.detectedAt <= :endTime) GROUP BY w.status")
    List<Object[]> countByStatus(
                                  @Param("startTime") LocalDateTime startTime,
                                  @Param("endTime") LocalDateTime endTime);

    /**
     * 计算平均风险分 (统计用)。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return 平均风险分 (无数据返回 null)
     */
    @Query(value = "SELECT AVG(w.riskScore) FROM ScrmChurnWarningEntity w WHERE (:startTime IS NULL OR w.detectedAt"
                          + ">= :startTime) AND (:endTime IS NULL OR w.detectedAt <= :endTime)")
    Double averageRiskScore(
                             @Param("startTime") LocalDateTime startTime,
                             @Param("endTime") LocalDateTime endTime);
}
