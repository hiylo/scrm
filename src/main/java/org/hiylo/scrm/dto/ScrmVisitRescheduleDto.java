/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmVisitRescheduleDto.java
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

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * SCRM 客户回访任务改期 DTO。
 * <p>
 * 用于回访任务改期接口入参, 携带任务 ID、新日期/时间与改期原因。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmVisitRescheduleDto {

    /** 任务 ID */
    @NotNull(message = "任务 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    /** 新计划日期 */
    @NotNull(message = "新计划日期不能为空")
    private LocalDate newDate;

    /** 新计划时间 (可空) */
    private LocalTime newTime;

    /** 改期原因 */
    @NotBlank(message = "改期原因不能为空")
    @Size(max = 500, message = "改期原因长度不能超过 500")
    private String reason;
}
