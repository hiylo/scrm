/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCouponUsageLogRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCouponUsageLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * SCRM 优惠券使用日志数据访问层。
 * <p>
 * 提供按券 ID 分页查询日志, 供 {@code ScrmCouponService} 使用。日志查询的复合过滤
 * (couponId / templateId / actionType / 时间范围) 通过 {@link JpaSpecificationExecutor}
 * 动态构建查询条件实现。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCouponUsageLogRepository extends JpaRepository<ScrmCouponUsageLogEntity, Long>,
        JpaSpecificationExecutor<ScrmCouponUsageLogEntity> {

    /**
     * 按券 ID 分页查询使用日志, 操作时间倒序返回。
     *
     * @param couponId 优惠券 ID
     * @param pageable 分页参数
     * @return 使用日志分页结果
     */
    Page<ScrmCouponUsageLogEntity> findByCouponIdOrderByActionTimeDesc(Long couponId, Pageable pageable);
}
