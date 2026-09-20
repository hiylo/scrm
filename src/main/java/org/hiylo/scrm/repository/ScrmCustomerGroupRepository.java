/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerGroupRepository.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCustomerGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 客户分组数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCustomerGroupRepository extends JpaRepository<ScrmCustomerGroupEntity, Long> {

    /**
     * 根据归属账号 ID 查询分组列表。
     *
     * @param ownerAccountId 归属账号 ID
     * @return 分组列表
     */
    List<ScrmCustomerGroupEntity> findByOwnerAccountId(Long ownerAccountId);

    /**
     * 根据账号 ID 查询分组列表。
     *
     * @return 分组列表
     */

    /**
     * 根据账号 ID 和平台群组唯一标识查询分组（upsert 用）。
     *
     * @param platformGroupUid 平台群组唯一标识（企微 chatId）
     * @return 分组实体（可能为空）
     */
    Optional<ScrmCustomerGroupEntity> findByPlatformGroupUid(String platformGroupUid);
}
