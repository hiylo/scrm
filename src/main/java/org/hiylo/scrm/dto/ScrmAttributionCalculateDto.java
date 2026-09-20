/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAttributionCalculateDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 营销效果归因计算 DTO。
 * <p>
 * 归因计算接口入参: 指定归因模型与待归因转化列表 (可选), 配合时间范围筛选。
 * conversionIds 为空时, 按时间范围批量归因 startDate 至 endDate 内的全部转化。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmAttributionCalculateDto {

    /** 归因模型 ID */
    @NotNull(message = "归因模型 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long modelId;

    /** 待归因转化 ID 列表 (可空, 为空时按时间范围批量归因) */
    private List<Long> conversionIds;

    /** 起始时间 (可空, conversionIds 为空时用于筛选转化时间范围) */
    private LocalDateTime startDate;

    /** 结束时间 (可空, conversionIds 为空时用于筛选转化时间范围) */
    private LocalDateTime endDate;
}
