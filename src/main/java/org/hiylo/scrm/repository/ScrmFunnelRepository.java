/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFunnelRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmFunnelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 销售漏斗数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmFunnelRepository extends JpaRepository<ScrmFunnelEntity, Long>,
        JpaSpecificationExecutor<ScrmFunnelEntity> {

    /**
     * 按 ID 查询漏斗列表。
     *
     * @return 漏斗列表
     */

    /**
     * 按状态查询漏斗列表。
     *
     * @param status   状态: ACTIVE / INACTIVE
     * @return 漏斗列表
     */
    List<ScrmFunnelEntity> findByStatus(String status);

    /**
     * 查询指定账号下的默认漏斗。
     *
     * @return 默认漏斗 (可能为空)
     */
    Optional<ScrmFunnelEntity> findByIsDefaultTrue();

    /**
     * 按 ID 统计默认漏斗数量 (用于设置默认漏斗前的唯一性校验)。
     *
     * @return 默认漏斗数量
     */
    long countByIsDefaultTrue();
}
