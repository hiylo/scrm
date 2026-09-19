/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSpeechRecommendationRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmSpeechRecommendationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * SCRM 销售话术推荐记录数据访问层。
 * <p>
 * 提供按客户加载推荐历史、按客户与时间范围查询推荐记录等能力,
 * 供 {@code ScrmSpeechRecommendService} 推荐记录管理与统计使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmSpeechRecommendationRepository extends JpaRepository<ScrmSpeechRecommendationEntity, Long>,
        JpaSpecificationExecutor<ScrmSpeechRecommendationEntity> {

    /**
     * 按客户分页加载推荐历史 (按 recommendedAt 降序)。
     * <p>
     * 需要 Top N 列表时, 调用方传入 {@code PageRequest.of(0, limit)} 并使用 {@code Page#getContent()} 获取列表。
     * </p>
     *
     * @param customerId 客户 ID
     * @param pageable   分页参数
     * @return 推荐记录分页结果
     */
    Page<ScrmSpeechRecommendationEntity> findByCustomerIdOrderByRecommendedAtDesc(Long customerId, Pageable pageable);
}
