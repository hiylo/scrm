/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSalesForecastDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * SCRM 销售业绩预测请求 DTO。
 *
 * @author Hsi Chu
 */
@Data
public class ScrmSalesForecastDto {

    /** 销售目标 ID */
    @NotNull(message = "目标 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long targetId;

    /** 预测天数 (1-90) */
    @NotNull(message = "预测天数不能为空")
    @Min(value = 1, message = "预测天数不能小于 1")
    private Integer forecastDays;
}
