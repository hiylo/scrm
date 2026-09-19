/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAlertRuleRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmAlertRuleEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 告警规则数据访问层。
 * <p>
 * 提供按与编码加载规则, 以及按指标 / 严重程度 / 启用状态等条件查询能力,
 * 供 {@code ScrmSystemMonitorService} 的规则管理与评估使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmAlertRuleRepository extends JpaRepository<ScrmAlertRuleEntity, Long>,
        JpaSpecificationExecutor<ScrmAlertRuleEntity> {

    /**
     * 按与规则编码加载规则。
     *
     * @param ruleCode 规则编码
     * @return 规则实体 (可能不存在)
     */
    Optional<ScrmAlertRuleEntity> findByRuleCode(String ruleCode);

    /**
     * 按与指标 ID 查询规则。
     *
     * @param metricId 指标 ID
     * @return 规则列表
     */
    List<ScrmAlertRuleEntity> findByMetricId(Long metricId);

    /**
     * 按与严重程度分页查询规则。
     *
     * @param severity 严重程度
     * @param pageable 分页参数
     * @return 规则分页结果
     */
    Page<ScrmAlertRuleEntity> findBySeverity(String severity, Pageable pageable);

    /**
     * 按与启用状态查询规则 (评估所有规则用)。
     *
     * @param enabled  启用状态
     * @return 规则列表
     */
    List<ScrmAlertRuleEntity> findByEnabled(Boolean enabled);
}
