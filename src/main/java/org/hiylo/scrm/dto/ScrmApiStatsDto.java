/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmApiStatsDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import lombok.Data;

/**
 * SCRM 开放API 统计 DTO。
 * <p>
 * 描述 API 调用的总体统计指标: 总请求数 / 错误数 / 错误率 / 平均响应时间 / 活跃应用数,
 * 用于开放API 概览看板。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmApiStatsDto {

    /** 总请求数 */
    private Long totalRequests;

    /** 错误请求数 (HTTP 状态码 >= 400) */
    private Long errorCount;

    /** 错误率 (0~1) */
    private Double errorRate;

    /** 平均响应时间 (毫秒) */
    private Double avgResponseTimeMs;

    /** 活跃应用数 (status=ACTIVE) */
    private Long activeAppCount;
}
