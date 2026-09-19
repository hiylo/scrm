/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : DashboardTrendVo.java
 * Date : 2026/07/27 02:41:22
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
 * 看板趋势数据 VO。
 * <p>
 * 暴露多指标、多天数的按日趋势数据, 供前端趋势图渲染。
 * 支持的指标: customers / campaigns / conversations / messages / risk-signals。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardTrendVo {

    /** 指标名称 (customers / campaigns / conversations / messages / risk-signals) */
    private String metric;

    /** 统计天数 */
    private Integer days;

    /** 起始日期 (yyyy-MM-dd, 含) */
    private String startDate;

    /** 结束日期 (yyyy-MM-dd, 含, 即今天) */
    private String endDate;

    /** 趋势数据总量 */
    private Long totalCount;

    /** 按日趋势 */
    private List<DailyCountVo> trend;

    /** 日均值 (totalCount / days) */
    private Double dailyAverage;

    /** 峰值 (trend 中的最大 count) */
    private Long peakValue;

    /** 峰值日期 (yyyy-MM-dd) */
    private String peakDate;
}
