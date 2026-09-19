/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCalendarViewDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 日历视图查询 DTO。
 * <p>
 * 用于日历视图聚合查询接口入参, 携带日期范围、负责人、日历类型与客户过滤条件。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmCalendarViewDto {

    /** 视图起始时间 (含) */
    @NotNull(message = "起始时间不能为空")
    private LocalDateTime startDate;

    /** 视图结束时间 (含) */
    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endDate;

    /** 负责人 ID 过滤 (可空, 为空则查询全部负责人) */
    private String ownerId;

    /** 日历类型过滤 (可空) */
    private String calendarType;

    /** 客户 ID 过滤 (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;
}
