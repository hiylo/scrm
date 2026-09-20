/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmVocInsightRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmVocInsightEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * SCRM VoC 洞察数据访问层。
 * <p>
 * 供 {@code ScrmVocService} 使用, 通过 Specification 实现多条件过滤查询。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmVocInsightRepository extends JpaRepository<ScrmVocInsightEntity, Long>,
        JpaSpecificationExecutor<ScrmVocInsightEntity> {
}
