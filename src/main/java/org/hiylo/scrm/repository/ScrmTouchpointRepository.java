/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTouchpointRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmTouchpointEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * SCRM 触点管理数据访问层。
 * <p>
 * 提供按触点编码查询、按类型 / 启用状态分页查询, 以及触点统计字段的增量更新
 * (累计事件数 / 转化数 / 最近事件时间), 供 {@code ScrmBehaviorTrackService} 在记录行为时维护。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmTouchpointRepository extends JpaRepository<ScrmTouchpointEntity, Long>,
        JpaSpecificationExecutor<ScrmTouchpointEntity> {

    /**
     * 按与触点编码查询 (编码唯一)。
     *
     * @param touchpointCode 触点编码
     * @return 触点实体 (存在时)
     */
    Optional<ScrmTouchpointEntity> findByTouchpointCode(String touchpointCode);

    /**
     * 按与触点类型分页查询 (按创建时间倒序)。
     *
     * @param touchpointType 触点类型
     * @param pageable       分页参数
     * @return 触点分页结果
     */
    Page<ScrmTouchpointEntity> findByTouchpointType(String touchpointType, Pageable pageable);

    /**
     * 按与启用状态分页查询 (按创建时间倒序)。
     *
     * @param isActive 启用状态
     * @param pageable 分页参数
     * @return 触点分页结果
     */
    Page<ScrmTouchpointEntity> findByIsActive(Boolean isActive, Pageable pageable);

    /**
     * 按分页查询 (按创建时间倒序)。
     *
     * @param pageable 分页参数
     * @return 触点分页结果
     */

    /**
     * 校验触点编码在是否已存在 (去重)。
     *
     * @param touchpointCode 触点编码
     * @return 是否存在
     */
    boolean existsByTouchpointCode(String touchpointCode);

    /**
     * 自增触点累计事件数, 并刷新最近事件时间。
     *
     * @param touchpointCode 触点编码
     * @param eventTime      事件时间
     * @return 影响行数
     */
    @Modifying
    @Query(value = "UPDATE ScrmTouchpointEntity tp SET tp.totalEvents = tp.totalEvents + 1, tp.lastEventAt ="
                          + ":eventTime WHERE tp.touchpointCode = :touchpointCode")
    int incrementEvents(
                        @Param("touchpointCode") String touchpointCode,
                        @Param("eventTime") LocalDateTime eventTime);

    /**
     * 自增触点转化数。
     *
     * @param touchpointCode 触点编码
     * @return 影响行数
     */
    @Modifying
    @Query(value = "UPDATE ScrmTouchpointEntity tp SET tp.conversionCount = tp.conversionCount + 1 WHERE "
                          + "tp.touchpointCode = :touchpointCode")
    int incrementConversions(
                             @Param("touchpointCode") String touchpointCode);
}
