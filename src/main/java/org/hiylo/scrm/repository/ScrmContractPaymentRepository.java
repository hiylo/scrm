/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContractPaymentRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmContractPaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * SCRM 合同付款数据访问层。
 * <p>
 * 提供按付款编号查询、按合同 ID 查询全部付款、按状态查询到期/逾期付款,
 * 以及按状态列表查询 (统计用), 供 {@code ScrmContractPaymentService} 使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmContractPaymentRepository extends JpaRepository<ScrmContractPaymentEntity, Long>,
        JpaSpecificationExecutor<ScrmContractPaymentEntity> {

    /**
     * 按付款编号查询付款。
     *
     * @param paymentNo 付款编号
     * @return 付款 (可能为空)
     */
    Optional<ScrmContractPaymentEntity> findByPaymentNo(String paymentNo);

    /**
     * 按合同 ID 查询全部付款 (按计划日期升序)。
     *
     * @param contractId 合同 ID
     * @return 付款列表
     */
    List<ScrmContractPaymentEntity> findByContractIdOrderByPlannedDateAsc(Long contractId);

    /**
     * 按与付款状态查询 (到期/逾期付款扫描用)。
     *
     * @param paymentStatus 付款状态
     * @return 付款列表
     */
    List<ScrmContractPaymentEntity> findByPaymentStatus(String paymentStatus);

    /**
     * 按与付款状态列表查询 (到期/逾期付款扫描用)。
     *
     * @param paymentStatuses 付款状态集合
     * @return 付款列表
     */
    List<ScrmContractPaymentEntity> findByPaymentStatusIn(List<String> paymentStatuses);

    /**
     * 按、付款状态与计划日期范围查询 (到期付款扫描用)。
     *
     * @param paymentStatus 付款状态
     * @param startDate     计划日期下限
     * @param endDate       计划日期上限
     * @return 付款列表
     */
    List<ScrmContractPaymentEntity> findByPaymentStatusAndPlannedDateBetween(String paymentStatus, LocalDate startDate, LocalDate endDate);
}
