/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContractRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmContractEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * SCRM 合同实例数据访问层。
 * <p>
 * 提供按合同编号查询、按与编号前缀计数 (生成合同编号用)、按状态与结束日期扫描
 * (即将到期/已到期合同扫描) 以及按续约来源合同查询, 供 {@code ScrmContractService} 使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmContractRepository extends JpaRepository<ScrmContractEntity, Long>,
        JpaSpecificationExecutor<ScrmContractEntity> {

    /**
     * 按合同编号查询合同。
     *
     * @param contractNo 合同编号
     * @return 合同 (可能为空)
     */
    Optional<ScrmContractEntity> findByContractNo(String contractNo);

    /**
     * 按与合同编号前缀计数 (生成合同编号序号用)。
     *
     * @param prefix 合同编号前缀 (HT + 年月日)
     * @return 数量
     */
    long countByContractNoStartingWith(String prefix);

    /**
     * 按状态列表查询在指定结束日期之前的合同 (即将到期/已到期扫描用)。
     *
     * @param statuses 状态集合 (ACTIVE / SIGNED 等)
     * @param date     结束日期上限 (endDate 早于或等于此日期)
     * @return 合同列表
     */
    List<ScrmContractEntity> findByStatusInAndEndDateLessThanEqual(List<String> statuses, LocalDate date);

    /**
     * 按续约来源合同 ID 查询 (统计续约次数用)。
     *
     * @param renewalOfId 续约来源合同 ID
     * @return 合同列表
     */
    List<ScrmContractEntity> findByRenewalOfId(Long renewalOfId);
}
