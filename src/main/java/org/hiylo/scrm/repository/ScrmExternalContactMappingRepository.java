/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmExternalContactMappingRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmExternalContactMappingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 外部联系人映射数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmExternalContactMappingRepository
        extends JpaRepository<ScrmExternalContactMappingEntity, Long>,
        JpaSpecificationExecutor<ScrmExternalContactMappingEntity> {

    /**
     * 按、平台与外部联系人 ID 查询映射 (唯一约束)。
     *
     * @param platform          平台
     * @param externalContactId 平台外部联系人 ID
     * @return 映射记录 (可能不存在)
     */
    Optional<ScrmExternalContactMappingEntity> findByPlatformAndExternalContactId(String platform, String externalContactId);

    /**
     * 按客户 ID 查询映射列表 (一个客户可能映射到多个平台联系人)。
     *
     * @param customerId SCRM 客户 ID
     * @return 映射列表
     */
    List<ScrmExternalContactMappingEntity> findByCustomerId(Long customerId);

    /**
     * 按与同步状态查询映射列表。
     *
     * @param syncStatus 同步状态
     * @return 映射列表
     */
    List<ScrmExternalContactMappingEntity> findBySyncStatus(String syncStatus);
}
