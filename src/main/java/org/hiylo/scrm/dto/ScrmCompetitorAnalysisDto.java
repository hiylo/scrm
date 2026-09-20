/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCompetitorAnalysisDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 竞品竞争分析请求 DTO。
 * <p>
 * 指定分析对象竞品 ID、分析维度与时间范围, 由 {@code analyzeCompetitor} 生成完整竞争分析报告。
 * analysisType 控制分析维度: PRICING (价格策略) / PRODUCT (产品矩阵) / POSITIONING (市场定位) /
 * ACTIVITY (动态频次) / THREAT (威胁评估) / FULL (完整报告, 默认)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmCompetitorAnalysisDto {

    /** 竞品 ID */
    @NotNull(message = "竞品 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long competitorId;

    /** 分析类型: PRICING / PRODUCT / POSITIONING / ACTIVITY / THREAT / FULL */
    @NotBlank(message = "分析类型不能为空")
    @Pattern(regexp = "PRICING|PRODUCT|POSITIONING|ACTIVITY|THREAT|FULL",
            message = "分析类型仅支持 PRICING/PRODUCT/POSITIONING/ACTIVITY/THREAT/FULL")
    private String analysisType;

    /** 分析起始时间 (含, 可空, 缺省取最近 90 天) */
    private LocalDateTime startTime;

    /** 分析截止时间 (含, 可空, 缺省取当前时间) */
    private LocalDateTime endTime;
}
