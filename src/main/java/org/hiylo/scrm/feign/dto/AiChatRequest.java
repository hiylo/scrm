/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : AiChatRequest.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
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

    /**
     * 模型名称。
     * <p>
     * 默认 null 表示「未指定」，由 {@link org.hiylo.scrm.feign.AiChatClient} 注入
     * {@code scrm.ai.model} 配置的模型。不在这里硬编码模型名，否则配置项形同虚设
     * （历史问题：默认 gpt-4o-mini 被原样发给网关，网关不认识该模型 → 404 → 被降级吞掉）。
     * </p>
     */
    private String model;

    /** 对话消息列表（按时间顺序，首条通常为 system） */
    private List<AiChatMessage> messages;

    /** 采样温度，越高越随机，默认 0.7 */
    @Builder.Default
    private Double temperature = 0.7;

    /** 生成最大 token 数, 默认 500 (OpenAI 协议字段名为 max_tokens) */
    @Builder.Default
    @com.fasterxml.jackson.annotation.JsonProperty("max_tokens")
    private Integer maxTokens = 500;

    /**
     * 是否启用模型思考 (reasoning) 模式, 为 null 时不发送该字段。
     * <p>部分模型 (如 sensenova-6.8-flash-lite) 默认开启思考, 会把 max_tokens 全部消耗在
     * reasoning 上导致正文为空/超时; 客服接待与批量文案场景需显式关闭。
     * 由 {@code scrm.ai.enable-thinking} 配置统一注入, 网关侧字段名为 {@code enable_thinking}。</p>
     */
    @com.fasterxml.jackson.annotation.JsonProperty("enable_thinking")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private Boolean enableThinking;

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
