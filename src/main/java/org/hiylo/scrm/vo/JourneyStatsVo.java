/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : JourneyStatsVo.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * SCRM 客户旅程统计 VO。
 * <p>
 * 描述旅程整体执行情况: 入旅程数 / 完成数 / 退出数 / 转化率 / 当前活跃数,
 * 以及各步骤的通过率概要。基于 {@link org.hiylo.scrm.entity.ScrmJourneyEnrollmentEntity}
 * 与 {@link org.hiylo.scrm.entity.ScrmJourneyProgressLogEntity} 聚合生成。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JourneyStatsVo {

    /** 旅程 ID */
    private Long journeyId;

    /** 旅程名称 */
    private String journeyName;

    /** 旅程状态 */
    private String status;

    /** 入旅程客户数 */
    private Integer enrolledCount;

    /** 完成旅程客户数 */
    private Integer completedCount;

    /** 退出旅程客户数 */
    private Integer exitedCount;

    /** 当前活跃 (入营状态为 ACTIVE) 客户数 */
    private Integer activeCount;

    /** 转化率 (完成数 / 入旅程数 * 100, 百分比) */
    private Double conversionRate;

    /** 各步骤通过率概要 (按 stepOrder 升序) */
    private List<StepPassRateVo> steps;

    /**
     * 单个步骤通过率概要 VO。
     *
     * @author Hsi Chu
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StepPassRateVo {

        /** 步骤 ID */
        private Long stepId;

        /** 步骤名称 */
        private String stepName;

        /** 步骤类型 */
        private String stepType;

        /** 步骤顺序 */
        private Integer stepOrder;

        /** 进入此步骤的客户数 */
        private Integer enteredCount;

        /** 通过此步骤 (SUCCESS) 的客户数 */
        private Integer passedCount;

        /** 通过率 (passedCount / enteredCount * 100, 百分比) */
        private Double passRate;
    }
}
