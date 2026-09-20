/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmUserDeviceRepository.java
 * Date : 2026/07/29 21:19:51
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmUserDeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 用户设备数据访问层。
 * <p>
 * 提供按用户 / 平台 / client_id 查询设备推送 token 的能力, 供设备注册与推送服务使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmUserDeviceRepository extends JpaRepository<ScrmUserDeviceEntity, Long> {

    /**
     * 根据用户 ID 查询其所有注册设备。
     *
     * @param userId 用户 ID
     * @return 设备列表
     */
    List<ScrmUserDeviceEntity> findByUserId(String userId);

    /**
     * 根据用户 ID 与平台查询设备列表。
     *
     * @param userId   用户 ID
     * @param platform 平台 (android / ios)
     * @return 设备列表
     */
    List<ScrmUserDeviceEntity> findByUserIdAndPlatform(String userId, String platform);

    /**
     * 根据个推 client_id 查询设备。
     *
     * @param clientId 个推 client_id
     * @return 设备（可能为空）
     */
    Optional<ScrmUserDeviceEntity> findByClientId(String clientId);

    /**
     * 根据用户 ID 与 client_id 查询设备（upsert 依据）。
     *
     * @param userId   用户 ID
     * @param clientId 个推 client_id
     * @return 设备（可能为空）
     */
    Optional<ScrmUserDeviceEntity> findByUserIdAndClientId(String userId, String clientId);

    /**
     * 根据用户 ID 与 client_id 删除设备记录（注销推送）。
     *
     * @param userId   用户 ID
     * @param clientId 个推 client_id
     */
    @Transactional
    void deleteByUserIdAndClientId(String userId, String clientId);
}
