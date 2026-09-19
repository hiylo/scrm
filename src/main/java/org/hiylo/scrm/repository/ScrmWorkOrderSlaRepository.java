/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWorkOrderSlaRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmWorkOrderSlaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 工单 SLA 策略数据访问层。
 * <p>
 * 提供按策略编码、默认策略查询等能力, 供 {@code ScrmWorkOrderService} 使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmWorkOrderSlaRepository extends JpaRepository<ScrmWorkOrderSlaEntity, Long>,
        JpaSpecificationExecutor<ScrmWorkOrderSlaEntity> {

    /**
     * 按策略编码查询 SLA 策略。
     *
     * @param policyCode 策略编码
     * @return SLA 策略 (可能为空)
     */
    Optional<ScrmWorkOrderSlaEntity> findByPolicyCode(String policyCode);

    /**
     * 按策略名称查询 SLA 策略。
     *
     * @param policyName 策略名称
     * @return SLA 策略 (可能为空)
     */
    Optional<ScrmWorkOrderSlaEntity> findByPolicyName(String policyName);

    /**
     * 按查询启用的 SLA 策略列表。
     *
     * @return SLA 策略列表
     */
    List<ScrmWorkOrderSlaEntity> findByEnabledTrue();

    /**
     * 按查询默认 SLA 策略。
     *
     * @return 默认 SLA 策略 (可能为空)
     */
    Optional<ScrmWorkOrderSlaEntity> findByIsDefaultTrue();

    /**
     * 按与工单类型查询 SLA 策略。
     *
     * @param orderType 工单类型
     * @return SLA 策略列表
     */
    List<ScrmWorkOrderSlaEntity> findByOrderType(String orderType);
}
