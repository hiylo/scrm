/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCommissionRecordRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCommissionRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 销售佣金记录数据访问层。
 * <p>
 * 提供按 + 佣金编号定位记录 (供 {@code getRecordByNo} 使用), 按订单查询记录,
 * 按周期与销售人员查询记录 (供发放使用)。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCommissionRecordRepository extends JpaRepository<ScrmCommissionRecordEntity, Long>,
        JpaSpecificationExecutor<ScrmCommissionRecordEntity> {

    /**
     * 按与佣金编号查询记录 (recordNo 唯一)。
     *
     * @param recordNo 佣金编号
     * @return 记录 (可能为空)
     */
    Optional<ScrmCommissionRecordEntity> findByRecordNo(String recordNo);

    /**
     * 按与订单 ID 查询记录列表。
     *
     * @param orderId  订单 ID
     * @return 记录列表
     */
    List<ScrmCommissionRecordEntity> findByOrderId(String orderId);

    /**
     * 按、周期与销售人员 ID 查询记录列表。
     *
     * @param period        所属周期
     * @param salesPersonId 销售人员 ID
     * @return 记录列表
     */
    List<ScrmCommissionRecordEntity> findByPeriodAndSalesPersonId(String period, String salesPersonId);
}
