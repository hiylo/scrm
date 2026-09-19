/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : AiChatResponse.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.feign.dto;

import lombok.Data;

import java.util.List;

/**
 * AI 对话补全响应 DTO，对应 ai-server {@code POST /v1/ai/chat/completions} 的返回体。
 * <p>仅保留 scrm-server 回复场景所需字段，{@link #getFirstContent()} 提供空安全的便捷取值。
 *
 * @author Hsi Chu
 */
@Data
public class AiChatResponse {

    /** 响应 ID（ai-server 生成） */
    private String id;

    /** 实际生成使用的模型名称 */
    private String model;

    /** 候选回复列表，通常仅 1 条 */
    private List<AiChatChoice> choices;

    /**
     * 获取第一个候选回复的文本内容，空安全。
     * <p>用于 SCRM 自动回复场景直接取用首条回复，无需关心 choices 数组结构。
     *
     * @return 首条回复内容；choices 为空或 message 缺失时返回 null
     */
    public String getFirstContent() {
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        AiChatChoice first = choices.get(0);
        if (first == null || first.getMessage() == null) {
            return null;
        }
        return first.getMessage().getContent();
    }

    /**
     * AI 对话候选回复。
     * @author Hsi Chu
     */
    @Data
    public static class AiChatChoice {

        /** 回复消息（含 role 与 content） */
        private AiChatRequest.AiChatMessage message;

        /** 结束原因：stop / length / content_filter 等 */
        private String finishReason;
    }
}
