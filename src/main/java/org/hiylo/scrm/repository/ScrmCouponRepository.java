/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCouponRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCouponEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * SCRM 优惠券实例数据访问层。
 * <p>
 * 提供按券码查询、按客户与状态查询 (限领校验与客户券列表)、按状态与过期时间扫描 (过期处理)
 * 以及按模板统计各状态数量与抵扣金额汇总, 供 {@code ScrmCouponService} 使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCouponRepository extends JpaRepository<ScrmCouponEntity, Long>,
        JpaSpecificationExecutor<ScrmCouponEntity> {

    /**
     * 按券码查询优惠券。
     *
     * @param couponCode 优惠券码
     * @return 优惠券 (可能为空)
     */
    Optional<ScrmCouponEntity> findByCouponCode(String couponCode);

    /**
     * 按、客户与状态列表查询优惠券 (用于每人限领校验, 统计客户已持有该模板的可用券数)。
     *
     * @param templateId 模板 ID
     * @param customerId 客户 ID
     * @param statuses   状态集合
     * @return 优惠券列表
     */
    List<ScrmCouponEntity> findByTemplateIdAndCustomerIdAndStatusIn(Long templateId, Long customerId, Collection<String> statuses);

    /**
     * 按、状态与过期时间查询需过期的优惠券 (过期处理定时任务扫描)。
     *
     * @param status    当前状态 (UNUSED)
     * @param expiresAt 过期时间上限 (expiresAt 早于此时间)
     * @return 需过期的优惠券列表
     */
    List<ScrmCouponEntity> findByStatusAndExpiresAtBefore(String status, LocalDateTime expiresAt);

    /**
     * 按与模板统计指定状态的优惠券数量 (模板统计与优惠券统计用)。
     *
     * @param templateId 模板 ID
     * @param status     优惠券状态
     * @return 数量
     */
    long countByTemplateIdAndStatus(Long templateId, String status);

    /**
     * 按与模板统计已使用优惠券的实际抵扣金额汇总 (优惠券统计用)。
     * <p>startTime/endTime 为可空, 为空时不限制对应时间边界, 按 create_time 过滤。</p>
     *
     * @param templateId 模板 ID
     * @param startTime  起始时间 (可空)
     * @param endTime    截止时间 (可空)
     * @return 抵扣金额汇总 (无记录返回 0)
     */
    @Query(value = "SELECT COALESCE(SUM(c.usedAmount), 0) FROM ScrmCouponEntity c WHERE c.templateId = :templateId "
                          + "AND c.status = 'USED' AND (:startTime IS NULL OR c.createTime >= :startTime) AND (:endTime IS "
                          + "NULL OR c.createTime <= :endTime)")
    Double sumUsedAmountByTemplate(
                                             @Param("templateId") Long templateId,
                                             @Param("startTime") LocalDateTime startTime,
                                             @Param("endTime") LocalDateTime endTime);

    /**
     * 按客户统计指定状态的优惠券数量 (客户优惠券统计用)。
     *
     * @param customerId 客户 ID
     * @param status     优惠券状态
     * @return 数量
     */
    long countByCustomerIdAndStatus(Long customerId, String status);
}
