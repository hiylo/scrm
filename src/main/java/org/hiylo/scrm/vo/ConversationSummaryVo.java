/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ConversationSummaryVo.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话 AI 总结 VO, 描述一次会话总结的生成结果。
 * <p>
 * 由 {@link org.hiylo.scrm.service.ScrmConversationService#summarizeConversation(Long)}
 * 调用 ai-server 生成,包含总结文本、参与总结的消息数、所用模型与耗时等元信息。
 *
 * @author Hsi Chu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSummaryVo {

    /** 会话 ID */
    private Long conversationId;

    /** AI 生成的总结 */
    private String summary;

    /** 参与总结的消息数 */
    private Integer messageCount;

    /** 使用的 AI 模型 */
    private String model;

    /** 生成耗时（毫秒） */
    private Long latencyMs;

    /** 总结生成时间 */
    private LocalDateTime summarizedAt;
}
