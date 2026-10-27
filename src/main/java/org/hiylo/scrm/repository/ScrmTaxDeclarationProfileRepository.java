/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmTaxDeclarationProfileRepository.java
 * Date : 2026/10/23 12:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmTaxDeclarationProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * 税种申报档案数据访问层（报税日历域）。
 * <p>
 * 提供按客户查询档案、按档案唯一键（客户×税种×纳税人类型×开始日期）查询
 * 与去重校验、按启用状态加载档案（调度生成申报记录用）。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
public interface ScrmTaxDeclarationProfileRepository
        extends JpaRepository<ScrmTaxDeclarationProfileEntity, Long>,
        JpaSpecificationExecutor<ScrmTaxDeclarationProfileEntity> {

    /**
     * 按客户 ID 查询申报档案（客户时间线/钻取用）。
     *
     * @param customerId 客户 ID
     * @return 档案列表
     */
    List<ScrmTaxDeclarationProfileEntity> findByCustomerId(Long customerId);

    /**
     * 按档案唯一键查询（创建去重校验）。
     *
     * @param customerId    客户 ID
     * @param taxType       税种
     * @param taxpayerType  纳税人类型
     * @param startDate     有效开始日期
     * @return 档案（可能为空）
     */
    Optional<ScrmTaxDeclarationProfileEntity> findByCustomerIdAndTaxTypeAndTaxpayerTypeAndStartDate(
            Long customerId, String taxType, String taxpayerType, java.time.LocalDate startDate);

    /**
     * 加载启用档案（ACTIVE 且生效窗口覆盖当日, 调度生成申报记录用）。
     *
     * @param status 状态（ACTIVE）
     * @return 档案列表
     */
    List<ScrmTaxDeclarationProfileEntity> findByStatus(String status);

    /**
     * 按公司主体查询申报档案（代账场景主查询维度）。
     * <p>
     * 财税业务的天然主体是公司，联系人只是经办人。按 {@code customerId} 查档案在更换对接人后
     * 会丢失历史，因此公司维度的查询是主路径。存量未关联公司的档案 {@code company_id} 为空，
     * 不在此结果内，需先做联系人与主体的归并。
     * </p>
     *
     * @param companyId 公司主体 ID
     * @return 档案列表（按创建时间倒序）
     */
    List<ScrmTaxDeclarationProfileEntity> findByCompanyIdOrderByCreateTimeDesc(Long companyId);

    /**
     * 统计该主体关联的业务数据数量（公司 360 聚合用）。
     *
     * @param companyId 公司主体 ID
     * @return 数量
     */
    long countByCompanyId(Long companyId);

    /**
     * 批量查询多个主体下的申报档案（会计数据隔离用）。
     * <p>
     * 会计只能看自己负责的主体，记录表只存 {@code profile_id}，
     * 需先按可见主体集合取档案 ID 再过滤记录。
     * </p>
     *
     * @param companyIds 公司主体 ID 集合（不可为空集合，调用方需先判空）
     * @return 档案列表
     */
    List<ScrmTaxDeclarationProfileEntity> findByCompanyIdIn(java.util.Collection<Long> companyIds);
}
