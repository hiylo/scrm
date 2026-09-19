/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCampaignFunnelRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCampaignFunnelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * SCRM 营销活动转化漏斗数据访问层。
 * <p>
 * 提供按 + 分析 ID 查询漏斗阶段 (供 {@code getFunnelByAnalysis} 使用),
 * 按与活动 ID 查询漏斗, 按与漏斗类型查询。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCampaignFunnelRepository extends JpaRepository<ScrmCampaignFunnelEntity, Long>,
        JpaSpecificationExecutor<ScrmCampaignFunnelEntity> {

    /**
     * 按与分析 ID 查询漏斗阶段列表。
     *
     * @param analysisId 分析 ID
     * @return 漏斗阶段列表
     */
    List<ScrmCampaignFunnelEntity> findByAnalysisId(Long analysisId);

    /**
     * 按与分析 ID 查询并按阶段顺序排序。
     *
     * @param analysisId 分析 ID
     * @return 漏斗阶段列表 (按 stageOrder 升序)
     */
    List<ScrmCampaignFunnelEntity> findByAnalysisIdOrderByStageOrderAsc(Long analysisId);

    /**
     * 按与活动 ID 查询漏斗阶段列表。
     *
     * @param campaignId 活动 ID
     * @return 漏斗阶段列表
     */
    List<ScrmCampaignFunnelEntity> findByCampaignId(Long campaignId);

    /**
     * 按与漏斗类型查询漏斗阶段列表。
     *
     * @param funnelType 漏斗类型
     * @return 漏斗阶段列表
     */
    List<ScrmCampaignFunnelEntity> findByFunnelType(String funnelType);
}
