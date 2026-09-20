/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBehaviorPathRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmBehaviorPathEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 客户行为路径数据访问层。
 * <p>
 * 提供按客户 + 会话查询路径 (构建时去重 / 更新), 转化路径查询, 常见路径聚合
 * (按触点序列分组统计), 流失点分析 (按出口触点统计) 等能力,
 * 供 {@code ScrmBehaviorTrackService} 路径分析引擎使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmBehaviorPathRepository extends JpaRepository<ScrmBehaviorPathEntity, Long>,
        JpaSpecificationExecutor<ScrmBehaviorPathEntity> {

    /**
     * 按客户与会话查询路径 (构建时判断是否已存在)。
     *
     * @param customerId 客户 ID
     * @param sessionId  会话 ID
     * @return 路径实体 (存在时)
     */
    Optional<ScrmBehaviorPathEntity> findByCustomerIdAndSessionId(Long customerId, String sessionId);

    /**
     * 转化路径: 按查询含转化的路径 (按会话开始时间倒序)。
     *
     * @param pageable 分页参数
     * @return 路径分页结果
     */
    Page<ScrmBehaviorPathEntity> findByHasConversionTrueOrderBySessionStartTimeDesc(Pageable pageable);

    /**
     * 按查询全部路径 (按会话开始时间倒序)。
     *
     * @param pageable 分页参数
     * @return 路径分页结果
     */
    Page<ScrmBehaviorPathEntity> findAllByOrderBySessionStartTimeDesc(Pageable pageable);

    /**
     * 常见路径: 按触点序列 (touchpoints) 分组统计, 取出现次数最多的路径。
     *
     * @param pageable 分页参数 (限制返回条数)
     * @return Object[] 列表: [touchpoints, count]
     */
    @Query(value = "SELECT p.touchpoints, COUNT(p.id) FROM ScrmBehaviorPathEntity p WHERE p.touchpoints IS NOT NULL "
                          + "GROUP BY p.touchpoints ORDER BY COUNT(p.id) DESC")
    List<Object[]> findCommonPaths(Pageable pageable);

    /**
     * 流失点分析: 按出口触点统计路径数 (会话在该触点结束即视为流失)。
     *
     * @return Object[] 列表: [exitTouchpoint, exitCount]
     */
    @Query(value = "SELECT p.exitTouchpoint, COUNT(p.id) FROM ScrmBehaviorPathEntity p WHERE p.exitTouchpoint IS NOT "
                          + "NULL GROUP BY p.exitTouchpoint ORDER BY COUNT(p.id) DESC")
    List<Object[]> countByExitTouchpoint();
}
