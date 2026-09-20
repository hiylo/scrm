/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAiReplyFeedbackDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * SCRM AI 对话反馈 DTO。
 * <p>
 * 用户对单次 AI 对话回复的反馈: {@link #conversationId} 标识对话记录,
 * {@link #feedback} 仅支持 GOOD (好评) / BAD (差评) / NONE (撤销反馈)。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmAiReplyFeedbackDto {

    /** 对话记录 ID */
    @NotNull(message = "对话 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long conversationId;

    /** 用户反馈: GOOD / BAD / NONE */
    @NotBlank(message = "反馈不能为空")
    @Pattern(regexp = "GOOD|BAD|NONE", message = "反馈仅支持 GOOD/BAD/NONE")
    private String feedback;
}
