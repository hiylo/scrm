/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMetricRecordDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 指标记录值 DTO。
 * <p>
 * 用于 {@code recordMetric} / {@code batchRecordMetrics} 接口, 上报指定指标编码
 * 的采集值与时间戳, 由服务端更新当前值、历史数据并触发阈值校验。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmMetricRecordDto {

    /** 指标编码 */
    @NotBlank(message = "指标编码不能为空")
    @Size(max = 50, message = "指标编码长度不能超过 50")
    private String metricCode;

    /** 采集值 */
    @NotNull(message = "采集值不能为空")
    private Double value;

    /** 采集时间 (可空, 缺省为当前时间) */
    private LocalDateTime timestamp;
}
