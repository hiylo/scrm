/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : JourneyStepStatsVo.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SCRM 旅程步骤统计 VO, 描述单个步骤的详细执行统计。
 * <p>
 * 基于 {@link org.hiylo.scrm.entity.ScrmJourneyProgressLogEntity} 聚合生成,
 * 供旅程步骤统计接口返回前端。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JourneyStepStatsVo {

    /** 步骤 ID */
    private Long stepId;

    /** 步骤名称 */
    private String stepName;

    /** 步骤类型 */
    private String stepType;

    /** 步骤顺序 */
    private Integer stepOrder;

    /** 进入此步骤的客户数 (该步骤日志总条数) */
    private Integer enteredCount;

    /** 执行成功 (SUCCESS) 次数 */
    private Integer passedCount;

    /** 执行失败 (FAILED) 次数 */
    private Integer failedCount;

    /** 跳过 (SKIPPED) 次数 */
    private Integer skippedCount;

    /** 等待中 (WAITING) 次数 */
    private Integer waitingCount;

    /** 通过率 (passedCount / enteredCount * 100, 百分比) */
    private Double passRate;
}
