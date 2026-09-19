/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPriceMonitorDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 竞品价格监测 DTO。
 * <p>
 * 用于价格更新接口入参: 指定产品 ID、最新价格与来源。由 {@code updatePrice} 追加价格历史、
 * 计算变化幅度、刷新价格统计 (最低 / 最高 / 平均价 / 变化次数) 并自动生成 PRICE_CHANGE 动态。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmPriceMonitorDto {

    /** 竞品产品 ID */
    @NotNull(message = "产品 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long productId;

    /** 最新价格 */
    @NotNull(message = "新价格不能为空")
    private Double newPrice;

    /** 价格来源 (可空) */
    @Size(max = 200, message = "来源长度不能超过 200")
    private String source;
}
