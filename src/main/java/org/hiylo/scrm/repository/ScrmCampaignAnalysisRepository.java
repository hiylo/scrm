/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCampaignAnalysisRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCampaignAnalysisEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 营销活动效果分析数据访问层。
 * <p>
 * 提供按 + 活动定位分析 (供 {@code getAnalysisByCampaign} 使用),
 * 按状态查询分析 (供 running/completed 列表使用),
 * 按与审批状态查询。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCampaignAnalysisRepository extends JpaRepository<ScrmCampaignAnalysisEntity, Long>,
        JpaSpecificationExecutor<ScrmCampaignAnalysisEntity> {

    /**
     * 按与活动 ID 查询分析。
     *
     * @param campaignId 活动 ID
     * @return 分析 (可能为空)
     */
    Optional<ScrmCampaignAnalysisEntity> findByCampaignId(Long campaignId);

    /**
     * 按状态查询分析列表。
     *
     * @param status   状态
     * @return 分析列表
     */
    List<ScrmCampaignAnalysisEntity> findByStatus(String status);

    /**
     * 按与审批状态查询分析列表。
     *
     * @param isApproved 是否已审批
     * @return 分析列表
     */
    List<ScrmCampaignAnalysisEntity> findByIsApproved(Boolean isApproved);
}
