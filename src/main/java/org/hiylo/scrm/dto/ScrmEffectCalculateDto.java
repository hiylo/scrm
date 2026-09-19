/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmEffectCalculateDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;

/**
 * SCRM 活动效果计算入参 DTO。
 * <p>
 * 用于 {@code calculateEffect} 接口, 指定要计算的活动 ID、计算时间范围与归因模型。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmEffectCalculateDto {

    /** 营销活动 ID */
    @NotNull(message = "营销活动 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long campaignId;

    /** 计算开始日期 */
    @NotNull(message = "计算开始日期不能为空")
    private LocalDate startDate;

    /** 计算结束日期 */
    @NotNull(message = "计算结束日期不能为空")
    private LocalDate endDate;

    /** 归因模型 (默认 LAST_TOUCH) */
    @Pattern(regexp = "FIRST_TOUCH|LAST_TOUCH|LINEAR|TIME_DECAY|POSITION_BASED|DATA_DRIVEN",
            message = "归因模型仅支持 FIRST_TOUCH/LAST_TOUCH/LINEAR/TIME_DECAY/POSITION_BASED/DATA_DRIVEN")
    @NotBlank(message = "归因模型不能为空")
    private String attributionModel;
}
