/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSurveyResponseRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmSurveyResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 调查回复数据访问层。
 * <p>
 * 提供按调查问卷 / 客户 / NPS 分数 / 情感查询回复, 以及按 NPS 区间聚合统计等能力, 供
 * {@code ScrmNpsSurveyService.generateBenchmark} / {@code getSurveyStats} / {@code getSentimentDistribution}
 * 等 NPS / CSAT 分析使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmSurveyResponseRepository extends JpaRepository<ScrmSurveyResponseEntity, Long>,
        JpaSpecificationExecutor<ScrmSurveyResponseEntity> {

    /**
     * 按与调查问卷查询全部回复 (按提交时间倒序, 统计 / Benchmark 生成用)。
     *
     * @param surveyId 调查问卷 ID
     * @return 回复列表
     */
    List<ScrmSurveyResponseEntity> findBySurveyIdOrderBySubmittedAtDesc(Long surveyId);

    /**
     * 按客户分页查询回复 (按提交时间倒序, 客户回复历史用)。
     *
     * @param customerId 客户 ID
     * @param pageable   分页参数
     * @return 回复分页结果
     */
    Page<ScrmSurveyResponseEntity> findByCustomerIdOrderBySubmittedAtDesc(Long customerId, Pageable pageable);

    /**
     * 按与时间区间查询全部回复 (总体统计 / NPS 趋势用)。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return 回复列表
     */
    @Query(value = "SELECT r FROM ScrmSurveyResponseEntity r WHERE (:startTime IS NULL OR r.submittedAt >="
                          + ":startTime) AND (:endTime IS NULL OR r.submittedAt <= :endTime) ORDER BY r.submittedAt DESC")
    List<ScrmSurveyResponseEntity> findByTimeRange(
                                                                @Param("startTime") LocalDateTime startTime,
                                                                @Param("endTime") LocalDateTime endTime);

    /**
     * 按与调查问卷统计回复数 (问卷统计用)。
     *
     * @param surveyId 调查问卷 ID
     * @return 回复数
     */
    long countBySurveyId(Long surveyId);

    /**
     * 按情感聚合回复数 (情感分布统计用)。
     *
     * @param surveyId  调查问卷 ID (可空, 为空统计全部)
     * @return Object[]{sentiment, count}
     */
    @Query(value = "SELECT r.sentiment, COUNT(r.id) FROM ScrmSurveyResponseEntity r WHERE (:surveyId IS NULL OR "
                          + "r.surveyId = :surveyId) GROUP BY r.sentiment")
    List<Object[]> countBySentiment(
                                     @Param("surveyId") Long surveyId);
}
