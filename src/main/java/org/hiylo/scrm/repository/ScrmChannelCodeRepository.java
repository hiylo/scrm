/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmChannelCodeRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmChannelCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 渠道活码数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmChannelCodeRepository extends JpaRepository<ScrmChannelCodeEntity, Long>,
        JpaSpecificationExecutor<ScrmChannelCodeEntity> {

    /**
     * 根据状态查询渠道活码列表。
     *
     * @param status 状态: ACTIVE / INACTIVE
     * @return 活码列表
     */
    List<ScrmChannelCodeEntity> findByStatus(String status);

    /**
     * 根据平台类型查询渠道活码列表。
     *
     * @param platformType 平台类型
     * @return 活码列表
     */
    List<ScrmChannelCodeEntity> findByPlatformType(String platformType);

    /**
     * 根据二维码 URL 查询渠道活码 (扫码入口按 qrKey 反查活码)。
     *
     * @param qrCodeUrl 二维码 URL
     * @return 活码 (可能为空)
     */
    Optional<ScrmChannelCodeEntity> findByQrCodeUrl(String qrCodeUrl);

    /**
     * 统计指定账号下的渠道活码总数。
     *
     * @return 活码总数
     */
}
