/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBudgetExpenseRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmBudgetExpenseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 营销预算支出数据访问层。
 * <p>
 * 提供按 + 支出编号定位支出 (供 {@code getExpenseByNo} 使用),
 * 按与方案 ID / 营销活动 ID 查询支出列表。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmBudgetExpenseRepository extends JpaRepository<ScrmBudgetExpenseEntity, Long>,
        JpaSpecificationExecutor<ScrmBudgetExpenseEntity> {

    /**
     * 按与支出编号查询支出 (expenseNo 唯一)。
     *
     * @param expenseNo 支出编号
     * @return 支出 (可能为空)
     */
    Optional<ScrmBudgetExpenseEntity> findByExpenseNo(String expenseNo);

    /**
     * 按与方案 ID 查询支出列表。
     *
     * @param planId   方案 ID
     * @return 支出列表
     */
    List<ScrmBudgetExpenseEntity> findByPlanId(Long planId);

    /**
     * 按与营销活动 ID 查询支出列表。
     *
     * @param campaignId 营销活动 ID
     * @return 支出列表
     */
    List<ScrmBudgetExpenseEntity> findByCampaignId(Long campaignId);
}
