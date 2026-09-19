/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmInteractionPlanCompleteDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 客户互动计划完成 DTO。
 * <p>
 * 用于互动计划完成接口入参, 携带实际开始/结束时间、完成备注、互动结果与后续行动。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmInteractionPlanCompleteDto {

    /** 计划 ID */
    @NotNull(message = "计划 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long planId;

    /** 实际开始时间 (可空, 为空则使用当前时间) */
    private LocalDateTime actualStart;

    /** 实际结束时间 (可空, 为空则使用当前时间) */
    private LocalDateTime actualEnd;

    /** 完成备注 (可空) */
    @Size(max = 2000, message = "完成备注长度不能超过 2000")
    private String completionNotes;

    /** 互动结果: POSITIVE/NEUTRAL/NEGATIVE/FOLLOW_UP_NEEDED (可空) */
    @Pattern(regexp = "POSITIVE|NEUTRAL|NEGATIVE|FOLLOW_UP_NEEDED|",
            message = "互动结果仅支持 POSITIVE/NEUTRAL/NEGATIVE/FOLLOW_UP_NEEDED")
    private String outcome;

    /** 后续行动 (可空) */
    @Size(max = 500, message = "后续行动长度不能超过 500")
    private String followUpAction;

    /** 后续日期 (可空) */
    private LocalDate followUpDate;
}
