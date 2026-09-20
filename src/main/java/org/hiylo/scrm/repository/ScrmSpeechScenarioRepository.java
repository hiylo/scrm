/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSpeechScenarioRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmSpeechScenarioEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * SCRM 销售话术场景数据访问层。
 * <p>
 * 提供按与场景编码加载场景能力, 供 {@code ScrmSpeechRecommendService}
 * 场景管理与匹配使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmSpeechScenarioRepository extends JpaRepository<ScrmSpeechScenarioEntity, Long>,
        JpaSpecificationExecutor<ScrmSpeechScenarioEntity> {

    /**
     * 按与场景编码加载场景 (同账号唯一)。
     *
     * @param scenarioCode 场景编码
     * @return 场景实体 (可能不存在)
     */
    Optional<ScrmSpeechScenarioEntity> findByScenarioCode(String scenarioCode);
}
