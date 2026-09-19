/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSystemConfigRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmSystemConfigEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * SCRM 系统配置数据访问层。
 * <p>
 * 提供按与配置键加载配置, 以及按分组 / 类型 / 环境 / 启用状态 / 敏感 / 可覆盖等
 * 条件查询能力, 供 {@code ScrmSystemConfigService} 的配置管理与统计使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmSystemConfigRepository extends JpaRepository<ScrmSystemConfigEntity, Long>,
        JpaSpecificationExecutor<ScrmSystemConfigEntity> {

    /**
     * 按与配置键加载配置。
     *
     * @param configKey 配置键
     * @return 配置实体 (可能不存在)
     */
    Optional<ScrmSystemConfigEntity> findByConfigKey(String configKey);

    /**
     * 按与配置分组分页查询。
     *
     * @param configGroup 配置分组
     * @param pageable    分页参数
     * @return 配置分页结果
     */
    Page<ScrmSystemConfigEntity> findByConfigGroup(String configGroup, Pageable pageable);

    /**
     * 按与配置类型分页查询。
     *
     * @param configType 配置类型
     * @param pageable   分页参数
     * @return 配置分页结果
     */
    Page<ScrmSystemConfigEntity> findByConfigType(String configType, Pageable pageable);

    /**
     * 按与环境限定分页查询。
     *
     * @param environment 环境限定
     * @param pageable    分页参数
     * @return 配置分页结果
     */
    Page<ScrmSystemConfigEntity> findByEnvironment(String environment, Pageable pageable);

    /**
     * 按与启用状态分页查询。
     *
     * @param enabled  启用状态
     * @param pageable 分页参数
     * @return 配置分页结果
     */
    Page<ScrmSystemConfigEntity> findByEnabled(Boolean enabled, Pageable pageable);

    /**
     * 按与敏感标志分页查询。
     *
     * @param isSensitive 是否敏感
     * @param pageable    分页参数
     * @return 配置分页结果
     */
    Page<ScrmSystemConfigEntity> findByIsSensitive(Boolean isSensitive, Pageable pageable);

    /**
     * 按与可覆盖标志分页查询。
     *
     * @param isOverridable 可覆盖
     * @param pageable      分页参数
     * @return 配置分页结果
     */
    Page<ScrmSystemConfigEntity> findByIsOverridable(Boolean isOverridable, Pageable pageable);

    /**
     * 按与配置分组查询全部配置 (用于树/导出)。
     *
     * @param configGroup 配置分组 (可空, 空则查全部)
     * @return 配置列表
     */
    List<ScrmSystemConfigEntity> findByConfigGroup(String configGroup);

    /**
     * 按与环境限定查询全部配置 (用于导出)。
     *
     * @param environment 环境限定
     * @return 配置列表
     */
    List<ScrmSystemConfigEntity> findByEnvironment(String environment);

    /**
     * 按查询全部启用配置 (用于缓存预热)。
     *
     * @param enabled  启用状态
     * @return 配置列表
     */
    List<ScrmSystemConfigEntity> findByEnabled(Boolean enabled);

    /**
     * 按与依赖配置键查询依赖配置 (用于依赖分析)。
     *
     * @param dependsOn 依赖配置键
     * @return 配置列表
     */
    List<ScrmSystemConfigEntity> findByDependsOn(String dependsOn);

    /**
     * 按与配置分组聚合配置数 (统计用)。
     *
     * @return Object[]{configGroup, count}
     */
    @Query("SELECT c.configGroup, COUNT(c.id) FROM ScrmSystemConfigEntity c GROUP BY c.configGroup")
    List<Object[]> countByConfigGroup();

    /**
     * 按与配置类型聚合配置数 (统计用)。
     *
     * @return Object[]{configType, count}
     */
    @Query("SELECT c.configType, COUNT(c.id) FROM ScrmSystemConfigEntity c GROUP BY c.configType")
    List<Object[]> countByConfigType();

    /**
     * 按与环境限定聚合配置数 (统计用)。
     *
     * @return Object[]{environment, count}
     */
    @Query("SELECT c.environment, COUNT(c.id) FROM ScrmSystemConfigEntity c GROUP BY c.environment")
    List<Object[]> countByEnvironment();

    /**
     * 统计指定账号下的配置总数。
     *
     * @return 记录数
     */

    /**
     * 统计指定账号下启用配置数。
     *
     * @param enabled  启用状态
     * @return 记录数
     */
    long countByEnabled(Boolean enabled);

    /**
     * 查询指定账号下最近变更时间早于 cutoff 的配置 (用于过期配置统计)。
     *
     * @param cutoff   时间阈值
     * @return 配置列表
     */
    List<ScrmSystemConfigEntity> findByLastChangedAtBeforeOrLastChangedAtIsNull(LocalDateTime cutoff);

    /**
     * 按与变更次数倒序查询热门配置 (Top N)。
     *
     * @param pageable 分页参数 (限制数量)
     * @return 配置分页结果
     */
    Page<ScrmSystemConfigEntity> findAllByOrderByChangeCountDesc(Pageable pageable);
}
