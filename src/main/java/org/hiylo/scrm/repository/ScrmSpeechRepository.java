/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSpeechRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmSpeechEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * SCRM 话术条目数据访问层。
 * <p>
 * 提供话术条目的基础 CRUD 与多条件过滤能力, 复杂多条件过滤通过
 * {@link JpaSpecificationExecutor} 由 Service 层动态构造。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmSpeechRepository extends JpaRepository<ScrmSpeechEntity, Long>,
        JpaSpecificationExecutor<ScrmSpeechEntity> {
}
