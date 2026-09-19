/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmForecastRunDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * SCRM 销售预测场景运行入参 DTO。
 * <p>
 * 用于 {@code /scenarios/run} 接口, 指定要运行的场景 ID 以及运行时的覆盖参数与调整因子。
 * parameters 为 JSON 文本覆盖模型的默认参数; adjustments 为 JSON 调整因子覆盖场景配置。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmForecastRunDto {

    /** 场景 ID */
    @NotNull(message = "场景 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long scenarioId;

    /** 运行时覆盖的模型参数 (JSON 文本, 如 {alpha:0.3, windowSize:4}, 可空) */
    private String parameters;

    /** 运行时应用的调整因子列表 (JSON 数组, 每项 {factor, value}) */
    @NotEmpty(message = "调整因子列表不能为空")
    private List<AdjustmentFactor> adjustments;

    /** 预测周期数覆盖 (可空, 缺省使用模型配置) */
    @Min(value = 1, message = "预测周期数不能小于 1")
    private Integer forecastPeriods;

    /**
     * 调整因子: factor 名称与 value 数值 (如 {factor:"growthRate", value:0.15})。
     * @author Hsi Chu
     */
    @Data
    public static class AdjustmentFactor {
        /** 因子名称 (如 growthRate / seasonalityFactor / marketCondition) */
        private String factor;
        /** 因子数值 */
        private Double value;
    }
}
