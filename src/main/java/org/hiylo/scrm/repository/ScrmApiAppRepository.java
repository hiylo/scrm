/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmApiAppRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmApiAppEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * SCRM 开放API 应用数据访问层。
 * <p>
 * 提供按与 clientId / appCode 查询应用, 以及活跃应用数统计能力,
 * 支撑 {@code ScrmOpenApiService} 的应用管理与统计接口。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmApiAppRepository extends JpaRepository<ScrmApiAppEntity, Long>,
        JpaSpecificationExecutor<ScrmApiAppEntity> {

    /**
     * 按与 clientId 查询应用 (登录态校验用)。
     *
     * @param clientId 客户端 ID
     * @return 应用实体
     */
    Optional<ScrmApiAppEntity> findByClientId(String clientId);

    /**
     * 按与 appCode 查询应用 (唯一性校验用)。
     *
     * @param appCode  应用编码
     * @return 应用实体
     */
    Optional<ScrmApiAppEntity> findByAppCode(String appCode);

    /**
     * 统计账号下指定状态的应用数。
     *
     * @param status   应用状态
     * @return 应用数
     */
    long countByStatus(String status);
}
