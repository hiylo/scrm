/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMonitorMetricRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmMonitorMetricEntity;
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
 * SCRM 监控指标数据访问层。
 * <p>
 * 提供按与编码加载指标, 以及按分组 / 告警态 / 时间区间等条件查询能力,
 * 供 {@code ScrmSystemMonitorService} 的指标管理与统计使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmMonitorMetricRepository extends JpaRepository<ScrmMonitorMetricEntity, Long>,
        JpaSpecificationExecutor<ScrmMonitorMetricEntity> {

    /**
     * 按与指标编码加载指标。
     *
     * @param metricCode 指标编码
     * @return 指标实体 (可能不存在)
     */
    Optional<ScrmMonitorMetricEntity> findByMetricCode(String metricCode);

    /**
     * 按与指标分组分页查询。
     *
     * @param metricGroup 指标分组
     * @param pageable    分页参数
     * @return 指标分页结果
     */
    Page<ScrmMonitorMetricEntity> findByMetricGroup(String metricGroup, Pageable pageable);

    /**
     * 按查询告警激活的指标。
     *
     * @param isAlertActive  是否告警激活
     * @param pageable       分页参数
     * @return 指标分页结果
     */
    Page<ScrmMonitorMetricEntity> findByIsAlertActive(Boolean isAlertActive, Pageable pageable);

    /**
     * 按与启用状态查询指标 (统计用)。
     *
     * @param enabled  启用状态
     * @return 指标列表
     */
    List<ScrmMonitorMetricEntity> findByEnabled(Boolean enabled);

    /**
     * 按与指标分组聚合指标数 (统计用)。
     *
     * @return Object[]{metricGroup, count}
     */
    @Query("SELECT m.metricGroup, COUNT(m.id) FROM ScrmMonitorMetricEntity m GROUP BY m.metricGroup")
    List<Object[]> countByMetricGroup();

    /**
     * 统计指定账号下处于告警激活态的指标数。
     *
     * @param isAlertActive 是否告警激活
     * @return 记录数
     */
    long countByIsAlertActive(Boolean isAlertActive);

    /**
     * 统计指定账号下最近采集时间在区间内的指标数。
     *
     * @param start    起始时间 (含)
     * @param end      结束时间 (含)
     * @return 记录数
     */
    long countByLastCollectedAtBetween(LocalDateTime start, LocalDateTime end);
}
