/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmInteractionRescheduleDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 客户互动计划改期 DTO。
 * <p>
 * 用于互动计划改期接口入参, 携带计划 ID、新的开始/结束时间与改期原因。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmInteractionRescheduleDto {

    /** 计划 ID */
    @NotNull(message = "计划 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long planId;

    /** 新计划开始时间 */
    @NotNull(message = "新计划开始时间不能为空")
    private LocalDateTime newStart;

    /** 新计划结束时间 (可空) */
    private LocalDateTime newEnd;

    /** 改期原因 */
    @NotBlank(message = "改期原因不能为空")
    @Size(max = 500, message = "改期原因长度不能超过 500")
    private String reason;
}
