/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ProfileCoverageStatsVo.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SCRM 画像覆盖率统计 VO。
 * <p>
 * 描述画像在客户群体中的覆盖率, 用于评估画像建设进度。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileCoverageStatsVo {

    /** 客户总数 */
    private Long totalCustomers;

    /** 已有画像的客户数 */
    private Long customersWithProfile;

    /** 画像覆盖率 (customersWithProfile / totalCustomers * 100, 百分比) */
    private Double coverageRate;

    /** 高置信度画像数 (confidenceScore >= 0.8) */
    private Long highConfidenceCount;

    /** 中置信度画像数 (0.5 <= confidenceScore < 0.8) */
    private Long mediumConfidenceCount;

    /** 低置信度画像数 (confidenceScore < 0.5) */
    private Long lowConfidenceCount;
}
