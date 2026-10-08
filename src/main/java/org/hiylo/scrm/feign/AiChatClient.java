/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : AiChatClient.java
 * Date : 2026/09/17 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.feign;

import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.feign.dto.AiChatRequest;
import org.hiylo.scrm.feign.dto.AiChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collections;

/**
 * AI 对话服务客户端
 * <p>
 * 以 {@link RestClient} 直连 AI 服务, 提供对话补全能力。AI 服务为可选组件,
 * 地址由 {@code scrm.ai.base-url} 配置, 关闭 ({@code scrm.ai.enabled=false})
 * 或服务不可达时不抛异常, 而是返回带降级提示的响应, 由上层替换为客户友好文案。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Component
public class AiChatClient {

    /** 降级提示回复内容, 上层服务据此识别降级场景并替换为客户友好文案 */
    public static final String FALLBACK_CONTENT = "（AI 服务暂不可用，请稍后重试）";

    /** 降级时填充的模型标识 */
    private static final String FALLBACK_MODEL = "fallback";

    /** 降级结束原因 */
    private static final String FALLBACK_FINISH_REASON = "fallback";

    /** AI 对话补全接口路径 (可配置, 默认 ai-server 专有路径; 对接 OpenAI 兼容服务时覆盖为 /v1/chat/completions) */
    private static final String DEFAULT_CHAT_COMPLETIONS_PATH = "/v1/ai/chat/completions";

    /** RestClient 实例, base-url 由配置注入 */
    private final RestClient restClient;

    /** AI 能力总开关, false 时直接返回降级响应 */
    private final boolean enabled;

    /** AI 对话补全接口路径 (由 scrm.ai.chat-path 配置, 默认 {@value #DEFAULT_CHAT_COMPLETIONS_PATH}) */
    private final String chatCompletionsPath;

    /**
     * 构造 AI 对话客户端
     *
     * @param baseUrl    AI 服务基础地址
     * @param enabled    AI 能力总开关
     * @param chatPath   AI 对话补全接口路径 (OpenAI 兼容服务填 /v1/chat/completions)
     * @param apiKey     API 密钥, 非空时以 {@code Authorization: Bearer} 头注入 (默认空, 不鉴权)
     */
    public AiChatClient(@Value("${scrm.ai.base-url:}") String baseUrl,
                        @Value("${scrm.ai.enabled:false}") boolean enabled,
                        @Value("${scrm.ai.chat-path:" + DEFAULT_CHAT_COMPLETIONS_PATH + "}") String chatPath,
                        @Value("${scrm.ai.api-key:}") String apiKey) {
        this.enabled = enabled;
        this.chatCompletionsPath = (chatPath == null || chatPath.isBlank())
                ? DEFAULT_CHAT_COMPLETIONS_PATH : chatPath;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Authorization",
                        "Bearer " + (apiKey == null ? "" : apiKey))
                .build();
    }

    /**
     * 调用 AI 对话补全接口
     * <p>AI 未启用或服务异常时返回降级响应, 保证调用方无需处理空指针或异常。</p>
     *
     * @param request 对话请求
     * @return 对话响应, 降级时 {@code choices[0].message.content} 为 {@link #FALLBACK_CONTENT}
     */
    public AiChatResponse chatCompletion(AiChatRequest request) {
        if (!enabled || request == null) {
            log.warn("AI 能力未启用, 返回降级响应, model={}",
                    request == null ? null : request.getModel());
            return buildFallbackResponse();
        }
        try {
            return restClient.post()
                    .uri(chatCompletionsPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiChatResponse.class);
        } catch (Exception e) {
            log.warn("AI 服务调用失败, 触发降级, model={}, err={}",
                    request.getModel(), e.getMessage());
            return buildFallbackResponse();
        }
    }

    /**
     * 构建降级响应
     *
     * @return 含降级提示内容的响应对象
     */
    private AiChatResponse buildFallbackResponse() {
        AiChatResponse response = new AiChatResponse();
        response.setId("fallback");
        response.setModel(FALLBACK_MODEL);
        AiChatResponse.AiChatChoice choice = new AiChatResponse.AiChatChoice();
        choice.setMessage(new AiChatRequest.AiChatMessage("assistant", FALLBACK_CONTENT));
        choice.setFinishReason(FALLBACK_FINISH_REASON);
        response.setChoices(Collections.singletonList(choice));
        return response;
    }
}
