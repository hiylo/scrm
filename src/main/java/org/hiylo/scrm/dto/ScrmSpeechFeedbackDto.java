/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSpeechFeedbackDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
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
 * SCRM 销售话术反馈 DTO。
 * <p>
 * 反馈接口入参: {@link #recommendationId} 标识推荐记录, {@link #selectedSpeechId}
 * 为用户选择的话术, {@link #feedback} / {@link #outcome} / {@link #comment}
 * 为反馈内容, 服务端据此更新推荐记录与话术统计。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmSpeechFeedbackDto {

    /** 推荐 ID */
    @NotNull(message = "推荐 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long recommendationId;

    /** 用户选择的话术 ID */
    @NotNull(message = "选择的话术 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long selectedSpeechId;

    /** 反馈: POSITIVE / NEGATIVE / NEUTRAL */
    @NotBlank(message = "反馈不能为空")
    @Pattern(regexp = "POSITIVE|NEGATIVE|NEUTRAL",
            message = "反馈仅支持 POSITIVE/NEGATIVE/NEUTRAL")
    private String feedback;

    /** 使用结果: SUCCESS / PARTIAL / FAILURE / NOT_USED */
    @NotBlank(message = "使用结果不能为空")
    @Pattern(regexp = "SUCCESS|PARTIAL|FAILURE|NOT_USED",
            message = "使用结果仅支持 SUCCESS/PARTIAL/FAILURE/NOT_USED")
    private String outcome;

    /** 反馈注释 (可空) */
    @Size(max = 500, message = "反馈注释长度不能超过 500")
    private String comment;
}
