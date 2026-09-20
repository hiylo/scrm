/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPublicSeaCustomerRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmPublicSeaCustomerEntity;
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
}
