/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmApiScopeRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmApiScopeEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 开放API 权限范围数据访问层。
 * <p>
 * 提供按与 scopeName 查询, 默认权限加载, 以及按资源分页查询能力,
 * 支撑 {@code ScrmOpenApiService} 的权限范围管理与校验接口。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmApiScopeRepository extends JpaRepository<ScrmApiScopeEntity, Long>,
        JpaSpecificationExecutor<ScrmApiScopeEntity> {

    /**
     * 按与 scopeName 查询权限范围 (校验用)。
     *
     * @param scopeName 权限范围名
     * @return 权限范围实体
     */
    Optional<ScrmApiScopeEntity> findByScopeName(String scopeName);

    /**
     * 加载账号下的默认权限范围 (isDefault=true 且 enabled=true)。
     *
     * @return 默认权限范围列表
     */
    List<ScrmApiScopeEntity> findByIsDefaultTrueAndEnabledTrue();

    /**
     * 按与启用状态分页查询权限范围, 按创建时间倒序返回。
     *
     * @param enabled  启用状态
     * @param pageable 分页参数
     * @return 权限范围分页结果
     */
    Page<ScrmApiScopeEntity> findByEnabledOrderByCreateTimeDesc(Boolean enabled, Pageable pageable);
}
