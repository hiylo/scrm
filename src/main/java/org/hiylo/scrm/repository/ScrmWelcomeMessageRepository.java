/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWelcomeMessageRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmWelcomeMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * SCRM 企微欢迎语配置数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmWelcomeMessageRepository extends JpaRepository<ScrmWelcomeMessageEntity, Long>,
        JpaSpecificationExecutor<ScrmWelcomeMessageEntity> {

    /**
     * 按、平台类型与状态查询欢迎语规则 (规则匹配用)。
     *
     * @param platformType 平台类型
     * @param status       状态
     * @return 规则列表
     */
    List<ScrmWelcomeMessageEntity> findByPlatformTypeAndStatus(String platformType, String status);

    /**
     * 按渠道活码 ID 查询欢迎语规则 (渠道活码删除时联动校验用)。
     *
     * @param channelCodeId 渠道活码 ID
     * @return 规则列表
     */
    List<ScrmWelcomeMessageEntity> findByChannelCodeId(Long channelCodeId);

    /**
     * 按绑定账号 ID 查询欢迎语规则 (账号删除时联动校验用)。
     *
     * @param accountId 账号 ID
     * @return 规则列表
     */
    List<ScrmWelcomeMessageEntity> findByAccountId(Long accountId);
}
