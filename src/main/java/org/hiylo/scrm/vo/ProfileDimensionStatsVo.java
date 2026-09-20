/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ProfileDimensionStatsVo.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SCRM 画像维度统计 VO。
 * <p>
 * 描述单个维度的画像分布情况, 用于维度洞察。基于
 * {@link org.hiylo.scrm.entity.ScrmCustomerProfileEntity}
 * 各维度 JSON 字段聚合生成。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileDimensionStatsVo {

    /** 维度名称: DEMOGRAPHIC / BEHAVIORAL / PSYCHOGRAPHIC / PURCHASE / COMMUNICATION / SOCIAL / RISK / VALUE */
    private String dimension;

    /** 该维度已填充画像数 */
    private Long filledCount;

    /** 该维度未填充画像数 */
    private Long emptyCount;

    /** 该维度覆盖率 (filledCount / total * 100, 百分比) */
    private Double coverageRate;

    /** 该维度平均置信度 (0-1) */
    private Double avgConfidence;
}
