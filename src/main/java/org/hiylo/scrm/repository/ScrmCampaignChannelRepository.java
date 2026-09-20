/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCampaignChannelRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCampaignChannelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * SCRM 营销活动渠道效果数据访问层。
 * <p>
 * 提供按 + 分析 ID 查询渠道 (供 {@code getChannelsByAnalysis} 使用),
 * 按与活动 ID 查询渠道, 按与渠道类型查询。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCampaignChannelRepository extends JpaRepository<ScrmCampaignChannelEntity, Long>,
        JpaSpecificationExecutor<ScrmCampaignChannelEntity> {

    /**
     * 按与分析 ID 查询渠道列表。
     *
     * @param analysisId 分析 ID
     * @return 渠道列表
     */
    List<ScrmCampaignChannelEntity> findByAnalysisId(Long analysisId);

    /**
     * 按与活动 ID 查询渠道列表。
     *
     * @param campaignId 活动 ID
     * @return 渠道列表
     */
    List<ScrmCampaignChannelEntity> findByCampaignId(Long campaignId);

    /**
     * 按与渠道类型查询渠道列表。
     *
     * @param channelType 渠道类型
     * @return 渠道列表
     */
    List<ScrmCampaignChannelEntity> findByChannelType(String channelType);
}
