/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmReferralProgramRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmReferralProgramEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * SCRM 客户推荐活动数据访问层。
 * <p>
 * 提供按活动编码查询活动 (唯一性校验与编码查询用), 供 {@code ScrmReferralService} 使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmReferralProgramRepository extends JpaRepository<ScrmReferralProgramEntity, Long>,
        JpaSpecificationExecutor<ScrmReferralProgramEntity> {

    /**
     * 按活动编码查询推荐活动。
     *
     * @param programCode 活动编码
     * @return 推荐活动 (可能为空)
     */
    Optional<ScrmReferralProgramEntity> findByProgramCode(String programCode);
}
