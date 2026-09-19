/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAbTestRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmAbTestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * SCRM A/B 测试数据访问层。
 * <p>
 * 提供按与测试编码定位测试 (供 {@code getAbTestByCode} 使用),
 * 按与营销活动 ID / 测试类型 / 状态查询测试列表。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmAbTestRepository extends JpaRepository<ScrmAbTestEntity, Long>,
        JpaSpecificationExecutor<ScrmAbTestEntity> {

    /**
     * 按与测试编码查询测试 (testCode 唯一)。
     *
     * @param testCode 测试编码
     * @return 测试 (可能为空)
     */
    Optional<ScrmAbTestEntity> findByTestCode(String testCode);

    /**
     * 按与营销活动 ID 查询测试列表。
     *
     * @param campaignId 营销活动 ID
     * @return 测试列表
     */
    List<ScrmAbTestEntity> findByCampaignId(Long campaignId);

    /**
     * 按与测试类型查询测试列表。
     *
     * @param testType 测试类型
     * @return 测试列表
     */
    List<ScrmAbTestEntity> findByTestType(String testType);

    /**
     * 按状态查询测试列表。
     *
     * @param status   状态
     * @return 测试列表
     */
    List<ScrmAbTestEntity> findByStatus(String status);
}
