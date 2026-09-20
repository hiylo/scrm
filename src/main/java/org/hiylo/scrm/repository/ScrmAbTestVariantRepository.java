/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAbTestVariantRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmAbTestVariantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * SCRM A/B 测试变体数据访问层。
 * <p>
 * 提供按测试加载变体、查询对照组等能力, 供 {@code ScrmAbTestService} 变体管理与
 * 流量分配、结果统计使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmAbTestVariantRepository extends JpaRepository<ScrmAbTestVariantEntity, Long>,
        JpaSpecificationExecutor<ScrmAbTestVariantEntity> {

    /**
     * 按测试加载全部变体 (按 sortOrder 升序)。
     *
     * @param testId   测试 ID
     * @return 变体列表
     */
    List<ScrmAbTestVariantEntity> findByTestIdOrderBySortOrderAsc(Long testId);

    /**
     * 按测试加载对照组。
     *
     * @param testId   测试 ID
     * @return 对照组变体 (可能不存在)
     */
    Optional<ScrmAbTestVariantEntity> findByTestIdAndIsControlTrue(Long testId);
}
