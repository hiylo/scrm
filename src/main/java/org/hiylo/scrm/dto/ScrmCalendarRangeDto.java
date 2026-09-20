/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCalendarRangeDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * SCRM 营销日历日期范围查询 DTO。
 * <p>
 * 用于按日期范围查询日历视图数据, 可选传入事件类型与渠道过滤条件。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmCalendarRangeDto {

    /** 开始日期 */
    @NotNull(message = "开始日期不能为空")
    private LocalDate startDate;

    /** 结束日期 */
    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;

    /** 事件类型过滤 (可空, 为空表示不限) */
    private List<String> eventTypes;

    /** 渠道过滤 (可空, 为空表示不限) */
    private List<String> channels;
}
