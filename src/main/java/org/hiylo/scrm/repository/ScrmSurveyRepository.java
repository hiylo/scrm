/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSurveyRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmSurveyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

/**
 * SCRM 满意度调查问卷数据访问层。
 * <p>
 * 提供按调查类型 / 状态查询问卷, 以及增量更新回复计数与完成率等能力, 供
 * {@code ScrmNpsSurveyService.submitResponse} 回复提交后增量刷新问卷统计使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmSurveyRepository extends JpaRepository<ScrmSurveyEntity, Long>,
        JpaSpecificationExecutor<ScrmSurveyEntity> {

    /**
     * 增量更新问卷回复计数 (避免乐观锁冲突, 直接 SQL 更新)。
     *
     * @param surveyId       调查问卷 ID
     * @param increment      增量数 (通常为 1)
     * @param completionRate 新的完成率
     * @param updateTime     更新时间
     * @return 受影响行数 (1 表示更新成功)
     */
    @Modifying
    @Query(value = "UPDATE ScrmSurveyEntity s SET s.responseCount = s.responseCount + :increment, s.completionRate ="
                          + ":completionRate, s.updateTime = :updateTime WHERE s.id = :surveyId")
    int incrementResponseCount(@Param("surveyId") Long surveyId,
                                @Param("increment") int increment,
                                @Param("completionRate") double completionRate,
                                @Param("updateTime") LocalDateTime updateTime);
}
