/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerTimelineRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCustomerTimelineEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * SCRM 客户时间线数据访问层。
 * <p>
 * 支撑客户 360° 视图的时间线展示与互动统计。复杂过滤 (事件类型 + 时间范围)
 * 通过 {@link JpaSpecificationExecutor} 在 Service 层组装 Specification 实现,
 * 避免在此处枚举所有查询组合。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCustomerTimelineRepository
        extends JpaRepository<ScrmCustomerTimelineEntity, Long>,
        JpaSpecificationExecutor<ScrmCustomerTimelineEntity> {

    /**
     * 按客户 ID 分页查询时间线 (按事件时间倒序, 最新在前)。
     *
     * @param customerId 客户 ID
     * @param pageable   分页参数
     * @return 时间线分页结果
     */
    Page<ScrmCustomerTimelineEntity> findByCustomerIdOrderByEventTimeDesc(Long customerId, Pageable pageable);

    /**
     * 按、客户 ID 与事件类型分页查询时间线 (按事件时间倒序)。
     *
     * @param customerId 客户 ID
     * @param eventType  事件类型
     * @param pageable   分页参数
     * @return 时间线分页结果
     */
    Page<ScrmCustomerTimelineEntity> findByCustomerIdAndEventTypeOrderByEventTimeDesc(Long customerId, String eventType, Pageable pageable);

    /**
     * 统计指定客户的时间线事件总数。
     *
     * @param customerId 客户 ID
     * @return 事件总数
     */
    long countByCustomerId(Long customerId);

    /**
     * 统计指定客户某事件类型的事件数。
     *
     * @param customerId 客户 ID
     * @param eventType  事件类型
     * @return 事件数
     */
    long countByCustomerIdAndEventType(Long customerId, String eventType);
}
