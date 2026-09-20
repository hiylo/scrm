/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCalendarMoveDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * SCRM 营销日历事件移动 DTO。
 * <p>
 * 用于将事件从原日期移动到新日期 (拖拽日历场景), 保留事件其余属性不变。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmCalendarMoveDto {

    /** 事件 ID */
    @NotNull(message = "事件 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long eventId;

    /** 新的开始日期 */
    @NotNull(message = "新的开始日期不能为空")
    private LocalDate newStartDate;

    /** 新的结束日期 */
    @NotNull(message = "新的结束日期不能为空")
    private LocalDate newEndDate;
}
