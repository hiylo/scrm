/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContractChangeRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmContractChangeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 合同变更数据访问层。
 * <p>
 * 提供按变更编号查询、按合同 ID 查询全部变更、按与变更类型查询,
 * 以及按合同 ID 查询变更历史 (按创建时间升序), 供 {@code ScrmContractChangeService} 使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmContractChangeRepository extends JpaRepository<ScrmContractChangeEntity, Long>,
        JpaSpecificationExecutor<ScrmContractChangeEntity> {

    /**
     * 按变更编号查询变更。
     *
     * @param changeNo 变更编号
     * @return 变更 (可能为空)
     */
    Optional<ScrmContractChangeEntity> findByChangeNo(String changeNo);

    /**
     * 按合同 ID 查询全部变更 (按创建时间升序, 变更历史用)。
     *
     * @param contractId 合同 ID
     * @return 变更列表
     */
    List<ScrmContractChangeEntity> findByContractIdOrderByCreateTimeAsc(Long contractId);

    /**
     * 按与变更类型查询。
     *
     * @param changeType 变更类型
     * @return 变更列表
     */
    List<ScrmContractChangeEntity> findByChangeType(String changeType);

    /**
     * 按与变更状态查询。
     *
     * @param changeStatus 变更状态
     * @return 变更列表
     */
    List<ScrmContractChangeEntity> findByChangeStatus(String changeStatus);
}
