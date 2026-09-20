/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmVocTopicRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmVocTopicEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * SCRM VoC 主题数据访问层。
 * <p>
 * 提供按主题编码查询, 供 {@code ScrmVocService} 使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmVocTopicRepository extends JpaRepository<ScrmVocTopicEntity, Long>,
        JpaSpecificationExecutor<ScrmVocTopicEntity> {

    /**
     * 按主题编码查询主题。
     *
     * @param topicCode 主题编码
     * @return 主题 (可能为空)
     */
    Optional<ScrmVocTopicEntity> findByTopicCode(String topicCode);
}
