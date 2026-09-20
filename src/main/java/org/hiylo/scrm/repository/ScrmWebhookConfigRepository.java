/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWebhookConfigRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmWebhookConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * SCRM Webhook 配置数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmWebhookConfigRepository extends JpaRepository<ScrmWebhookConfigEntity, Long>,
        JpaSpecificationExecutor<ScrmWebhookConfigEntity> {

    /**
     * 按状态查询 Webhook 配置列表。
     *
     * @param status   状态
     * @return Webhook 配置列表
     */
    List<ScrmWebhookConfigEntity> findByStatus(String status);

    /**
     * 统计指定账号下的 Webhook 配置总数。
     *
     * @return Webhook 配置总数
     */
}
