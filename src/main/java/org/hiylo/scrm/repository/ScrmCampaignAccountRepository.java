/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCampaignAccountRepository.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCampaignAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * SCRM 营销任务-账号关联数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCampaignAccountRepository extends JpaRepository<ScrmCampaignAccountEntity, Long> {

    /**
     * 根据营销任务 ID 查询关联记录。
     *
     * @param campaignId 营销任务 ID
     * @return 关联记录列表
     */
    List<ScrmCampaignAccountEntity> findByCampaignId(Long campaignId);

    /**
     * 根据账号 ID 查询其参与的营销任务关联记录。
     *
     * @param accountId 账号 ID
     * @return 关联记录列表
     */
    List<ScrmCampaignAccountEntity> findByAccountId(Long accountId);

    /**
     * 删除指定营销任务的所有账号关联记录 (级联清理用)。
     *
     * @param campaignId 营销任务 ID
     */
    void deleteByCampaignId(Long campaignId);

    /**
     * 删除指定营销任务与指定账号的关联记录 (移除单个账号分配)。
     *
     * @param campaignId 营销任务 ID
     * @param accountId  账号 ID
     */
    void deleteByCampaignIdAndAccountId(Long campaignId, Long accountId);

    /**
     * 批量删除指定营销任务与多个账号的关联记录 (移除多个账号分配)。
     *
     * @param campaignId 营销任务 ID
     * @param accountIds 账号 ID 列表
     */
    void deleteByCampaignIdAndAccountIdIn(Long campaignId, List<Long> accountIds);

    /**
     * 校验指定账号是否已分配到指定营销任务。
     *
     * @param campaignId 营销任务 ID
     * @param accountId  账号 ID
     * @return 已存在返回 true, 否则 false
     */
    boolean existsByCampaignIdAndAccountId(Long campaignId, Long accountId);
}
