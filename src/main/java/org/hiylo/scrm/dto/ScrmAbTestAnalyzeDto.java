/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAbTestAnalyzeDto.java
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

/**
 * SCRM A/B 测试分析入参 DTO。
 * <p>
 * 用于 {@code analyzeTest} 接口, 指定要分析的测试 ID、统计方法与显著性水平。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmAbTestAnalyzeDto {

    /** 测试 ID */
    @NotNull(message = "测试 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long testId;

    /** 统计方法: CHI_SQUARE/T_TEST/Z_TEST/MANN_WHITNEY/BAYESIAN */
    @NotBlank(message = "统计方法不能为空")
    @Pattern(regexp = "CHI_SQUARE|T_TEST|Z_TEST|MANN_WHITNEY|BAYESIAN",
            message = "统计方法仅支持 CHI_SQUARE/T_TEST/Z_TEST/MANN_WHITNEY/BAYESIAN")
    private String method;

    /** 显著性水平 α (默认 0.05) */
    private Double significanceLevel;
}
