/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCampaignEffectRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCampaignEffectEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 营销活动效果分析数据访问层。
 * <p>
 * 提供按与营销活动 ID 查询效果记录、按活动类型/目标/状态查询效果列表。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCampaignEffectRepository extends JpaRepository<ScrmCampaignEffectEntity, Long>,
        JpaSpecificationExecutor<ScrmCampaignEffectEntity> {

    /**
     * 按与营销活动 ID 查询效果记录 (一个活动一条效果记录)。
     *
     * @param campaignId 营销活动 ID
     * @return 效果记录 (可能为空)
     */
    Optional<ScrmCampaignEffectEntity> findByCampaignId(Long campaignId);

    /**
     * 按与活动类型查询效果列表。
     *
     * @param campaignType 活动类型
     * @return 效果列表
     */
    List<ScrmCampaignEffectEntity> findByCampaignType(String campaignType);

    /**
     * 按与活动目标查询效果列表。
     *
     * @param objective 活动目标
     * @return 效果列表
     */
    List<ScrmCampaignEffectEntity> findByObjective(String objective);

    /**
     * 按状态查询效果列表。
     *
     * @param status   状态
     * @return 效果列表
     */
    List<ScrmCampaignEffectEntity> findByStatus(String status);
}
