/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmNotificationPreferenceRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmNotificationPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 通知偏好数据访问层。
 * <p>
 * 提供按 + 用户查询全部偏好, 以及按 + 用户 + 渠道 + 分类精确查询偏好
 * (供 {@code ScrmNotificationCenterService.checkPreference} 偏好检查使用)。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmNotificationPreferenceRepository extends JpaRepository<ScrmNotificationPreferenceEntity, Long>,
        JpaSpecificationExecutor<ScrmNotificationPreferenceEntity> {

    /**
     * 查询账号下指定用户的所有偏好。
     *
     * @param userId   用户 ID
     * @return 偏好列表
     */
    List<ScrmNotificationPreferenceEntity> findByUserId(String userId);

    /**
     * 按 + 用户 + 渠道 + 分类精确查询偏好。
     *
     * @param userId   用户 ID
     * @param channel  通知渠道
     * @param category 分类
     * @return 偏好实体 (可能为空)
     */
    Optional<ScrmNotificationPreferenceEntity> findByUserIdAndChannelAndCategory(String userId, String channel, String category);
}
