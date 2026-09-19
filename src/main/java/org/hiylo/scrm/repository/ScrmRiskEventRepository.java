/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmRiskEventRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmRiskEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * SCRM 风险事件数据访问层。
 * <p>
 * 提供按事件编号查询、按规则 / 客户分页查询、待处理 / 严重事件查询等能力,
 * 供 {@code ScrmBlacklistService} 事件管理与统计使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmRiskEventRepository extends JpaRepository<ScrmRiskEventEntity, Long>,
        JpaSpecificationExecutor<ScrmRiskEventEntity> {

    /**
     * 按与事件编号查询事件 (编号业务唯一)。
     *
     * @param eventNo  事件编号
     * @return 事件实体 (可能不存在)
     */
    Optional<ScrmRiskEventEntity> findByEventNo(String eventNo);

    /**
     * 按与规则 ID 分页查询事件。
     *
     * @param ruleId   规则 ID
     * @param pageable 分页参数
     * @return 事件分页结果
     */
    Page<ScrmRiskEventEntity> findByRuleId(Long ruleId, Pageable pageable);

    /**
     * 按客户 ID 分页查询事件。
     *
     * @param customerId 客户 ID
     * @param pageable  分页参数
     * @return 事件分页结果
     */
    Page<ScrmRiskEventEntity> findByCustomerId(Long customerId, Pageable pageable);

    /**
     * 按状态分页查询事件。
     *
     * @param status   状态
     * @param pageable 分页参数
     * @return 事件分页结果
     */
    Page<ScrmRiskEventEntity> findByStatus(String status, Pageable pageable);

    /**
     * 按与风险等级分页查询事件。
     *
     * @param riskLevel 风险等级
     * @param pageable  分页参数
     * @return 事件分页结果
     */
    Page<ScrmRiskEventEntity> findByRiskLevel(String riskLevel, Pageable pageable);

    /**
     * 统计指定状态的事件数。
     *
     * @param status   状态
     * @return 事件数
     */
    long countByStatus(String status);

    /**
     * 统计指定风险等级的事件数。
     *
     * @param riskLevel 风险等级
     * @return 事件数
     */
    long countByRiskLevel(String riskLevel);

    /**
     * 统计指定客户的事件数。
     *
     * @param customerId 客户 ID
     * @return 事件数
     */
    long countByCustomerId(Long customerId);

    /**
     *
     * @param targetType  目标类型
     * @param targetValue 目标值
     * @return 事件列表
     */
    java.util.List<ScrmRiskEventEntity> findByTargetTypeAndTargetValue(String targetType, String targetValue);

    /**
     * 统计事件编号以指定前缀开头的事件数 (用于生成事件编号序号)。
     *
     * @param prefix   事件编号前缀
     * @return 事件数
     */
    long countByEventNoStartingWith(String prefix);
}
