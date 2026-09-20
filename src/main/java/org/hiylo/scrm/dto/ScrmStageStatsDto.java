/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmStageStatsDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * SCRM 生命周期阶段统计 DTO。
 * <p>
 * 承载单个生命周期阶段的统计指标 (客户数 / 累计进入数 / 平均停留 / 转化率 / 流失率 /
 * 留存率 / 进入率 / 退出率), 由 {@code ScrmCustomerLifecycleService} 阶段统计与
 * 模式分析接口返回, 供前端阶段漏斗与瓶颈分析展示。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmStageStatsDto {

    /** 阶段 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long stageId;

    /** 阶段名称 */
    private String stageName;

    /** 阶段编码 */
    private String stageCode;

    /** 阶段类别 */
    private String stageCategory;

    /** 阶段顺序 */
    private Integer stageOrder;

    /** 当前客户数 */
    private Integer customerCount;

    /** 累计进入数 */
    private Integer totalEnteredCount;

    /** 平均停留天数 */
    private Double avgDurationDays;

    /** 目标停留天数 */
    private Integer targetDurationDays;

    /** 转化率 (0-1) */
    private Double conversionRate;

    /** 流失率 (0-1) */
    private Double churnRate;

    /** 留存率 (0-1) */
    private Double retentionRate;

    /** 进入率 (0-1) */
    private Double entryRate;

    /** 退出率 (0-1) */
    private Double exitRate;

    /** 是否流失阶段 */
    private Boolean isChurnStage;

    /** 是否起始阶段 */
    private Boolean isStartStage;

    /** 是否终止阶段 */
    private Boolean isEndStage;

    /** 是否启用 */
    private Boolean enabled;
}
