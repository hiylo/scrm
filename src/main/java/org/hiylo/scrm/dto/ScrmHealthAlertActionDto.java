/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmHealthAlertActionDto.java
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
 * SCRM 客户健康度告警处理动作 DTO。
 * <p>
 * 用于将告警从 ACTIVE 推进到下一状态: ACKNOWLEDGE (确认) / RESOLVE (解决) / DISMISS (忽略)。
 * note 为处理备注, assigneeId 为分配的负责人 (可空)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmHealthAlertActionDto {

    /** 告警 ID */
    @NotNull(message = "告警 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long alertId;

    /** 动作: ACKNOWLEDGE / RESOLVE / DISMISS */
    @NotBlank(message = "动作不能为空")
    @Pattern(regexp = "ACKNOWLEDGE|RESOLVE|DISMISS",
            message = "动作仅支持 ACKNOWLEDGE/RESOLVE/DISMISS")
    private String action;

    /** 处理备注 (可空) */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String note;

    /** 分配负责人 (可空) */
    @Size(max = 100, message = "负责人长度不能超过 100")
    private String assigneeId;
}
