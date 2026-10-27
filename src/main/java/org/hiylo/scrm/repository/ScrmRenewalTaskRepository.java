/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmRenewalTaskRepository.java
 * Date : 2026/10/23 12:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmRenewalTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 服务续费任务数据访问层（续费流失域）。
 * <p>
 * 提供按合同查询未完成任务（幂等去重）、按状态加载任务（提醒节奏扫描 / 到期转 OVERDUE）、
 * 按合同 + 状态查询等能力。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
public interface ScrmRenewalTaskRepository
        extends JpaRepository<ScrmRenewalTaskEntity, Long>,
        JpaSpecificationExecutor<ScrmRenewalTaskEntity> {

    /**
     * 按合同 ID 查询第一条未完成任务（幂等键: 合同 + 状态∈待办集）。
     *
     * @param contractId 合同 ID
     * @return 待办续费任务（可能为空）
     */
    @Query("SELECT t FROM ScrmRenewalTaskEntity t WHERE t.contractId = :contractId "
            + "AND t.status IN ('PENDING','APPROVAL_PENDING','APPROVED','IN_PROGRESS') ORDER BY t.id ASC")
    Optional<ScrmRenewalTaskEntity> findFirstPendingByContractId(@Param("contractId") Long contractId);

    /**
     * 按合同 ID 查询全部续费任务（历史/统计）。
     *
     * @param contractId 合同 ID
     * @return 续费任务列表
     */
    List<ScrmRenewalTaskEntity> findByContractId(Long contractId);

    /**
     * 按状态加载待办任务（提醒节奏扫描用）。
     *
     * @param status 状态
     * @return 任务列表
     */
    List<ScrmRenewalTaskEntity> findByStatus(String status);

    /**
     * 统计该主体关联的业务数据数量（公司 360 聚合用）。
     *
     * @param companyId 公司主体 ID
     * @return 数量
     */
    long countByCompanyId(Long companyId);
}
