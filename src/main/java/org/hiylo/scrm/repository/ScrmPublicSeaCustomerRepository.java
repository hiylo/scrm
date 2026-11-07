/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmPublicSeaCustomerRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmPublicSeaCustomerEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * 客户公海池数据访问层。
 * <p>
 * 提供公海客户的按平台类型与平台客户 UID 查询能力,
 * 复杂分页与过滤通过 {@link JpaSpecificationExecutor} 在 Service 层构建。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmPublicSeaCustomerRepository
        extends JpaRepository<ScrmPublicSeaCustomerEntity, Long>,
        JpaSpecificationExecutor<ScrmPublicSeaCustomerEntity> {

    /**
     * 根据平台类型与平台客户 UID 查询公海客户 (唯一性校验用)。
     *
     * @param platformType        平台类型
     * @param platformCustomerUid 平台客户 UID
     * @return 公海客户 (可能为空)
     */
    Optional<ScrmPublicSeaCustomerEntity> findByPlatformTypeAndPlatformCustomerUid(
            String platformType, String platformCustomerUid);

    /**
     * 查询分配给指定销售的线索（销售 App「我的客户」主数据源）。
     *
     * @param assignedTo 归属销售 userId
     * @param pageable   分页参数
     * @return 线索分页
     */
    Page<ScrmPublicSeaCustomerEntity> findByAssignedTo(String assignedTo, Pageable pageable);

    /**
     * 查询分配给指定销售且处于指定状态的线索。
     *
     * @param assignedTo 归属销售 userId
     * @param status     公海状态（ASSIGNED / LOCKED 等）
     * @param pageable   分页参数
     * @return 线索分页
     */
    Page<ScrmPublicSeaCustomerEntity> findByAssignedToAndStatus(String assignedTo, String status,
                                                                Pageable pageable);

    /**
     * 统计分配给指定销售的线索数量。
     *
     * @param assignedTo 归属销售 userId
     * @return 数量
     */
    long countByAssignedTo(String assignedTo);
}
