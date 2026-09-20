/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmVocVoiceRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmVocVoiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * SCRM VoC 声音数据访问层。
 * <p>
 * 提供按声音编号查询、按统计当日声音序号 (生成声音编号用)、按统计已解决声音的平均解决时长
 * 与平均解决满意度, 供 {@code ScrmVocService} 使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmVocVoiceRepository extends JpaRepository<ScrmVocVoiceEntity, Long>,
        JpaSpecificationExecutor<ScrmVocVoiceEntity> {

    /**
     * 按声音编号查询声音。
     *
     * @param voiceNo 声音编号
     * @return 声音 (可能为空)
     */
    Optional<ScrmVocVoiceEntity> findByVoiceNo(String voiceNo);

    /**
     * 按与声音编号前缀统计当日声音数量 (生成声音编号用, 计算当日序号)。
     *
     * @param voiceNoPrefix 声音编号前缀
     * @return 当日已生成声音数
     */
    long countByVoiceNoStartingWith(String voiceNoPrefix);

    /**
     * 按统计已解决声音的平均解决时长 (小时, 时间范围按收集时间过滤)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 平均解决时长 (无记录返回 null)
     */
    @Query(value = "SELECT AVG(v.resolutionTimeHours) FROM ScrmVocVoiceEntity v WHERE v.resolutionTimeHours IS NOT "
                          + "NULL AND v.resolutionTimeHours > 0 AND (:startTime IS NULL OR v.collectedAt >= :startTime) AND "
                          + "(:endTime IS NULL OR v.collectedAt <= :endTime)")
    Double avgResolutionHours(
                                      @Param("startTime") LocalDateTime startTime,
                                      @Param("endTime") LocalDateTime endTime);

    /**
     * 按统计已评价声音的平均解决满意度 (时间范围按收集时间过滤)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 平均满意度 (无记录返回 null)
     */
    @Query(value = "SELECT AVG(v.customerSatisfaction) FROM ScrmVocVoiceEntity v WHERE v.customerSatisfaction IS NOT "
                          + "NULL AND (:startTime IS NULL OR v.collectedAt >= :startTime) AND (:endTime IS NULL OR "
                          + "v.collectedAt <= :endTime)")
    Double avgSatisfaction(
                                    @Param("startTime") LocalDateTime startTime,
                                    @Param("endTime") LocalDateTime endTime);
}
