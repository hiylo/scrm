/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAttributionReportDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 营销效果归因报告 DTO。
 * <p>
 * 归因报告生成接口入参: 指定归因模型与时间范围, 按 groupBy 维度汇总渠道 / 触点类型 / 活动。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmAttributionReportDto {

    /** 归因模型 ID */
    @NotNull(message = "归因模型 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long modelId;

    /** 起始时间 (可空, 缺省取 30 天前) */
    private LocalDateTime startDate;

    /** 结束时间 (可空, 缺省取当前时间) */
    private LocalDateTime endDate;

    /** 汇总维度: CHANNEL/TOUCHPOINT_TYPE/CAMPAIGN (默认 CHANNEL) */
    @Pattern(regexp = "CHANNEL|TOUCHPOINT_TYPE|CAMPAIGN",
            message = "汇总维度仅支持 CHANNEL/TOUCHPOINT_TYPE/CAMPAIGN")
    private String groupBy;
}
