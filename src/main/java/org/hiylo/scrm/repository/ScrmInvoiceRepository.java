/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmInvoiceRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmInvoiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 发票实例数据访问层。
 * <p>
 * 提供按发票编号查询、按与发票编号前缀计数 (生成发票编号用)、按订单 ID 查询、
 * 按原发票 ID 查询红冲关联, 供 {@code ScrmInvoiceService} 使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmInvoiceRepository extends JpaRepository<ScrmInvoiceEntity, Long>,
        JpaSpecificationExecutor<ScrmInvoiceEntity> {

    /**
     * 按发票编号查询发票。
     *
     * @param invoiceNo 发票编号
     * @return 发票 (可能为空)
     */
    Optional<ScrmInvoiceEntity> findByInvoiceNo(String invoiceNo);

    /**
     * 按与发票编号前缀计数 (生成发票编号序号用)。
     *
     * @param prefix  发票编号前缀 (FP + 年月日)
     * @return 数量
     */
    long countByInvoiceNoStartingWith(String prefix);

    /**
     * 按与申请编号前缀计数 (生成申请编号序号用)。
     *
     * @param prefix   申请编号前缀 (AP + 年月日)
     * @return 数量
     */
    long countByApplicationNoStartingWith(String prefix);

    /**
     * 按订单 ID 查询关联发票列表。
     *
     * @param orderId 订单 ID
     * @return 发票列表
     */
    List<ScrmInvoiceEntity> findByOrderId(String orderId);

    /**
     * 按原发票 ID 查询红冲发票 (红冲关联用)。
     *
     * @param originalInvoiceId 原发票 ID
     * @return 红冲发票列表
     */
    List<ScrmInvoiceEntity> findByOriginalInvoiceId(Long originalInvoiceId);
}
