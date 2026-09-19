/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPersonaRepository.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmPersonaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 人设数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmPersonaRepository extends JpaRepository<ScrmPersonaEntity, Long>,
        JpaSpecificationExecutor<ScrmPersonaEntity> {

    /**
     * 根据人设 ID 查询人设。
     *
     * @param personaId 人设 ID
     * @return 人设（可能为空）
     */
    Optional<ScrmPersonaEntity> findByPersonaId(String personaId);

    /**
     * 根据归属账号 ID 查询人设列表。
     *
     * @param accountId 账号 ID
     * @return 人设列表
     */
    List<ScrmPersonaEntity> findByAccountId(Long accountId);

    /**
     * 根据账号 ID 查询人设列表。
     *
     * @return 人设列表
     */
}
