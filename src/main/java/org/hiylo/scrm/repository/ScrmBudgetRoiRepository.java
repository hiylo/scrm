/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBudgetRoiRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmBudgetRoiEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 营销预算 ROI 数据访问层。
 * <p>
 * 提供按 + 方案 ID + 周期定位 ROI (供 {@code calculateRoi} 复用已有记录使用),
 * 按与营销活动 ID 查询 ROI 列表。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmBudgetRoiRepository extends JpaRepository<ScrmBudgetRoiEntity, Long>,
        JpaSpecificationExecutor<ScrmBudgetRoiEntity> {

    /**
     * 按、方案 ID 与周期查询 ROI (方案 + 周期唯一)。
     *
     * @param planId   方案 ID
     * @param period   周期
     * @return ROI (可能为空)
     */
    Optional<ScrmBudgetRoiEntity> findByPlanIdAndPeriod(Long planId, String period);

    /**
     * 按与营销活动 ID 查询 ROI 列表。
     *
     * @param campaignId 营销活动 ID
     * @return ROI 列表
     */
    List<ScrmBudgetRoiEntity> findByCampaignId(Long campaignId);

    /**
     * 按与方案 ID 查询 ROI 列表。
     *
     * @param planId   方案 ID
     * @return ROI 列表
     */
    List<ScrmBudgetRoiEntity> findByPlanId(Long planId);
}
