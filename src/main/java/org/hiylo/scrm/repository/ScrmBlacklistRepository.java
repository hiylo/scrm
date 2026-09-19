/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBlacklistRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmBlacklistEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;

/**
 * SCRM 黑名单数据访问层。
 * <p>
 * 提供按目标查询、按客户分页、按风险等级分页、过期 / 即将到期扫描等能力,
 * 供 {@code ScrmBlacklistService} 黑名单管理与风险评估使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmBlacklistRepository extends JpaRepository<ScrmBlacklistEntity, Long>,
        JpaSpecificationExecutor<ScrmBlacklistEntity> {

    /**
     *
     * @param targetType  目标类型
     * @param targetValue 目标值
     * @return 名单条目列表
     */
    List<ScrmBlacklistEntity> findByTargetTypeAndTargetValue(String targetType, String targetValue);

    /**
     *
     * @param targetType  目标类型
     * @param targetValue 目标值
     * @param status      状态
     * @return 名单条目列表
     */
    List<ScrmBlacklistEntity> findByTargetTypeAndTargetValueAndStatus(String targetType,
                                                                                  String targetValue, String status);

    /**
     * 按客户 ID 分页查询名单。
     *
     * @param customerId 客户 ID
     * @param pageable  分页参数
     * @return 名单分页结果
     */
    Page<ScrmBlacklistEntity> findByCustomerId(Long customerId, Pageable pageable);

    /**
     * 按与风险等级分页查询名单。
     *
     * @param riskLevel 风险等级
     * @param pageable  分页参数
     * @return 名单分页结果
     */
    Page<ScrmBlacklistEntity> findByRiskLevel(String riskLevel, Pageable pageable);

    /**
     * 按状态查询已过期名单 (expiryDate 早于指定日期)。
     *
     * @param status   状态
     * @param date     截止日期
     * @return 名单条目列表
     */
    List<ScrmBlacklistEntity> findByStatusAndExpiryDateBefore(String status, LocalDate date);

    /**
     * 按状态查询即将到期名单 (expiryDate 在指定区间内)。
     *
     * @param status    状态
     * @param startDate 起始日期
     * @param endDate   截止日期
     * @return 名单条目列表
     */
    List<ScrmBlacklistEntity> findByStatusAndExpiryDateBetween(String status,
                                                                           LocalDate startDate, LocalDate endDate);

    /**
     * 按与名单类型查询名单 (用于导出)。
     *
     * @param listType 名单类型
     * @return 名单条目列表
     */
    List<ScrmBlacklistEntity> findByListType(String listType);

    /**
     * 按状态统计条目数。
     *
     * @param status   状态
     * @return 条目数
     */
    long countByStatus(String status);

    /**
     *
     * @param listType 名单类型
     * @param status   状态
     * @return 条目数
     */
    long countByListTypeAndStatus(String listType, String status);
}
