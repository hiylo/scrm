/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : AiChatRequest.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.feign.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI 对话补全请求 DTO，scrm-server 调用 ai-server 的 {@code POST /v1/ai/chat/completions} 时携带。
 * <p>字段对齐 OpenAI Chat Completions 协议子集，由 {@code ScrmAiReplyService} 构建后通过
 * {@link org.hiylo.scrm.feign.AiChatClient#chatCompletion(AiChatRequest)} 发送。
 *
 * @author Hsi Chu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatRequest {

    /** 模型名称，默认 gpt-4o-mini */
    @Builder.Default
    private String model = "gpt-4o-mini";

    /** 对话消息列表（按时间顺序，首条通常为 system） */
    private List<AiChatMessage> messages;

    /** 采样温度，越高越随机，默认 0.7 */
    @Builder.Default
    private Double temperature = 0.7;

    /** 生成最大 token 数，默认 500 */
    @Builder.Default
    private Integer maxTokens = 500;

    /**
     * AI 对话消息（OpenAI Chat Message 子集）。
     * <ul>
     *   <li>{@code system} - 系统提示词，设定人设与回复规则</li>
     *   <li>{@code user} - 用户输入消息</li>
     *   <li>{@code assistant} - 模型历史回复</li>
     * </ul>
     * @author Hsi Chu
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiChatMessage {

        /** 角色：user / system / assistant */
        private String role;

        /** 消息内容 */
        private String content;
    }
}
