/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCampaignTemplateRepository.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCampaignTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * SCRM SOP 模板数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCampaignTemplateRepository extends JpaRepository<ScrmCampaignTemplateEntity, Long>,
        JpaSpecificationExecutor<ScrmCampaignTemplateEntity> {

    /**
     * 根据任务类型查询模板列表。
     *
     * @param campaignType 任务类型
     * @return 模板列表
     */
    List<ScrmCampaignTemplateEntity> findByCampaignType(String campaignType);

    /**
     * 根据平台类型查询模板列表。
     *
     * @param platformType 平台类型
     * @return 模板列表
     */
    List<ScrmCampaignTemplateEntity> findByPlatformType(String platformType);
}
