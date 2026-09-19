/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : AiGenerateRequestDto.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 回复生成回调请求 DTO，Lua 行为流脚本通过 {@code POST /scrm/callback/ai-generate}
 * 调用 scrm-server 生成符合人设风格的回复内容时携带。
 *
 * @author Hsi Chu
 */
@Data
public class AiGenerateRequestDto {

    /** 用户入站消息（必填，行为流待回复的最后一条消息） */
    @NotBlank(message = "入站消息不能为空")
    @Size(max = 4000, message = "入站消息长度不能超过 4000")
    private String incomingMessage;

    /** 人设 ID（可空，空则使用默认人设） */
    @Size(max = 100, message = "人设 ID 长度不能超过 100")
    private String personaId;

    /** 回复规则（可空，由行为流 state.replyRules 透传） */
    @Size(max = 4000, message = "回复规则长度不能超过 4000")
    private String replyRules;

    /** 会话 ID（可选，用于链路追踪与日志关联） */
    @Size(max = 100, message = "会话 ID 长度不能超过 100")
    private String sessionId;
}
