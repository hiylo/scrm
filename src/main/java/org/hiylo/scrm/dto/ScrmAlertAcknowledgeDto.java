/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAlertAcknowledgeDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 告警确认 DTO。
 * <p>
 * 用于 {@code acknowledgeEvent} / {@code batchAcknowledge} 接口, 对告警事件
 * 执行确认 (ACKNOWLEDGE) / 恢复 (RESOLVE) / 抑制 (SUPPRESS) 操作。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmAlertAcknowledgeDto {

    /** 告警事件 ID */
    @NotNull(message = "告警事件 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long eventId;

    /** 操作类型: ACKNOWLEDGE/RESOLVE/SUPPRESS */
    @NotBlank(message = "操作类型不能为空")
    @Pattern(regexp = "ACKNOWLEDGE|RESOLVE|SUPPRESS",
            message = "操作类型仅支持 ACKNOWLEDGE/RESOLVE/SUPPRESS")
    private String action;

    /** 操作备注 (可空) */
    @Size(max = 500, message = "操作备注长度不能超过 500")
    private String note;

    /** 操作人 (RESOLVE 时为恢复人, 可空) */
    @Size(max = 100, message = "操作人长度不能超过 100")
    private String resolvedBy;

    /** 抑制时长分钟 (SUPPRESS 时生效, 可空) */
    private Integer suppressDurationMinutes;
}
