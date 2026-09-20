/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ProfileStatsVo.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * SCRM 客户画像统计 VO。
 * <p>
 * 描述画像整体生成情况: 已生成数 / 待验证数 / 过期数 / 归档数,
 * 以及按置信度区间的画像分布。基于 {@link org.hiylo.scrm.entity.ScrmCustomerProfileEntity}
 * 聚合生成。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileStatsVo {

    /** 已生成画像总数 (含 GENERATED / VERIFIED / OUTDATED) */
    private Long totalCount;

    /** 草稿画像数 */
    private Long draftCount;

    /** 已生成画像数 (GENERATED) */
    private Long generatedCount;

    /** 已验证画像数 (VERIFIED) */
    private Long verifiedCount;

    /** 过期画像数 (OUTDATED) */
    private Long outdatedCount;

    /** 归档画像数 (ARCHIVED) */
    private Long archivedCount;

    /** 平均置信度 (0-1) */
    private Double avgConfidence;

    /** 各置信度区间画像分布: {HIGH(>=0.8), MEDIUM(0.5-0.8), LOW(<0.5)} */
    private Map<String, Long> confidenceDistribution;
}
