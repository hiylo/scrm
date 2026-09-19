/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMarketingTriggerRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmMarketingTriggerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * SCRM 触发式营销规则数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmMarketingTriggerRepository extends JpaRepository<ScrmMarketingTriggerEntity, Long>,
        JpaSpecificationExecutor<ScrmMarketingTriggerEntity> {

    /**
     * 根据事件类型查询启用中的触发器列表。
     *
     * @param eventType 事件类型
     * @param enabled   是否启用
     * @return 触发器列表
     */
    List<ScrmMarketingTriggerEntity> findByEventTypeAndEnabled(String eventType, Boolean enabled);

    /**
     * 根据动作类型查询触发器列表。
     *
     * @param actionType 动作类型
     * @return 触发器列表
     */
    List<ScrmMarketingTriggerEntity> findByActionType(String actionType);

    /**
     * 统计指定账号下的触发器总数。
     *
     * @return 触发器总数
     */
}
