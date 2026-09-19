/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSalesSpeechRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmSalesSpeechEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * SCRM 销售话术库数据访问层。
 * <p>
 * 提供按场景加载话术、按场景统计话术数、使用次数与成功次数增量更新等能力,
 * 供 {@code ScrmSpeechRecommendService} 话术管理、推荐匹配与统计使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmSalesSpeechRepository extends JpaRepository<ScrmSalesSpeechEntity, Long>,
        JpaSpecificationExecutor<ScrmSalesSpeechEntity> {

    /**
     * 按与场景加载全部启用话术 (按 rating 降序, usageCount 降序)。
     *
     * @param scenarioId 场景 ID
     * @param enabled    启用状态
     * @param pageable   分页参数
     * @return 话术分页结果
     */
    Page<ScrmSalesSpeechEntity> findByScenarioIdAndEnabled(Long scenarioId, Boolean enabled, Pageable pageable);

    /**
     * 按与场景加载启用话术 (用于推荐匹配, 按 rating 降序)。
     *
     * @param scenarioId 场景 ID
     * @param enabled    启用状态
     * @return 话术列表
     */
    List<ScrmSalesSpeechEntity> findByScenarioIdAndEnabledOrderByRatingDesc(Long scenarioId, Boolean enabled);

    /**
     * 统计场景下启用话术数。
     *
     * @param scenarioId 场景 ID
     * @param enabled    启用状态
     * @return 话术数
     */
    long countByScenarioIdAndEnabled(Long scenarioId, Boolean enabled);

    /**
     * 增量更新话术使用次数与成功次数 (避免乐观锁冲突, 直接 SQL 更新)。
     *
     * @param speechId 话术 ID
     * @param success  是否成功 (1 表示成功, 0 表示未成功)
     * @return 受影响行数 (1 表示更新成功)
     */
    @Modifying
    @Query(value = "UPDATE ScrmSalesSpeechEntity s SET s.usageCount = s.usageCount + 1, s.successCount ="
                          + "s.successCount + :success WHERE s.id = :speechId")
    int incrementUsage(@Param("speechId") Long speechId, @Param("success") int success);

    /**
     * 增量更新话术反馈统计 (避免乐观锁冲突, 直接 SQL 更新)。
     *
     * @param speechId         话术 ID
     * @param feedbackDelta    反馈数增量 (通常为 1)
     * @param positiveDelta    正面反馈增量 (POSITIVE=1, 否则 0)
     * @param negativeDelta    负面反馈增量 (NEGATIVE=1, 否则 0)
     * @return 受影响行数 (1 表示更新成功)
     */
    @Modifying
    @Query(value = "UPDATE ScrmSalesSpeechEntity s SET s.feedbackCount = s.feedbackCount + :feedbackDelta,"
                          + "s.positiveFeedback = s.positiveFeedback + :positiveDelta, s.negativeFeedback ="
                          + "s.negativeFeedback + :negativeDelta WHERE s.id = :speechId")
    int incrementFeedback(@Param("speechId") Long speechId,
                          @Param("feedbackDelta") int feedbackDelta,
                          @Param("positiveDelta") int positiveDelta,
                          @Param("negativeDelta") int negativeDelta);
}
