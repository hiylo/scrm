/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWeWorkArchiveConfigRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmWeWorkArchiveConfigEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * 企微会话存档配置数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmWeWorkArchiveConfigRepository extends JpaRepository<ScrmWeWorkArchiveConfigEntity, Long>,
        JpaSpecificationExecutor<ScrmWeWorkArchiveConfigEntity> {

    /**
     * 按状态分页查询配置
     *
     * @param status   状态 (可空)
     * @param pageable 分页参数
     * @return 配置分页
     */
    Page<ScrmWeWorkArchiveConfigEntity> findByStatus(String status, Pageable pageable);

    /**
     * 按分页查询配置
     *
     * @param pageable 分页参数
     * @return 配置分页
     */

    /**
     * 按与配置名称查询 (唯一性校验)
     *
     * @param configName 配置名称
     * @return 配置 (可能不存在)
     */
    Optional<ScrmWeWorkArchiveConfigEntity> findByConfigName(String configName);

    /**
     * 查询账号下指定状态的所有配置 (批量拉取用)
     *
     * @param status   状态
     * @return 配置列表
     */
    List<ScrmWeWorkArchiveConfigEntity> findByStatus(String status);
}
