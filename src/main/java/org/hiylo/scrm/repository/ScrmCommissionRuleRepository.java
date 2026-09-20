/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCommissionRuleRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCommissionRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * SCRM 销售佣金规则数据访问层。
 * <p>
 * 提供按与方案 ID 查询规则列表, 用于方案下规则的批量加载与匹配。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCommissionRuleRepository extends JpaRepository<ScrmCommissionRuleEntity, Long>,
        JpaSpecificationExecutor<ScrmCommissionRuleEntity> {

    /**
     * 按与方案 ID 查询规则列表。
     *
     * @param planId   方案 ID
     * @return 规则列表
     */
    List<ScrmCommissionRuleEntity> findByPlanId(Long planId);
}
