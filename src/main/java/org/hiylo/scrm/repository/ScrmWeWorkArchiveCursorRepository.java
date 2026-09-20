/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWeWorkArchiveCursorRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmWeWorkArchiveCursorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * 企微会话存档游标数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmWeWorkArchiveCursorRepository extends JpaRepository<ScrmWeWorkArchiveCursorEntity, Long>,
        JpaSpecificationExecutor<ScrmWeWorkArchiveCursorEntity> {

    /**
     * 按配置 ID 查询游标
     *
     * @param configId 存档配置 ID
     * @return 游标 (可能不存在)
     */
    Optional<ScrmWeWorkArchiveCursorEntity> findByConfigId(Long configId);
}
