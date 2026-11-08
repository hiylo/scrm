/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : AiChatClient.java
 * Date : 2026/09/17 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.feign;

import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.entity.ScrmAiProviderEntity;
import org.hiylo.scrm.feign.dto.AiChatRequest;
import org.hiylo.scrm.feign.dto.AiChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
 * <p>
 * <b>Provider 路由</b>：见 {@link #fromProvider(ScrmAiProviderEntity)}——
 * 系统管理内配置的 AI Provider 可直接构建客户端，文本任务按能力路由到
 * 默认文本 provider，实现模型接入点的动态管理（无需重启）。
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

    /** 默认模型名 (由 scrm.ai.model 配置覆盖); 与 AiChatRequest 的默认值保持一致, 避免网关不认识空模型名 */
    static final String DEFAULT_MODEL = "gpt-4o-mini";

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

    /** 是否启用模型思考模式 (由 scrm.ai.enable-thinking 配置, 默认 false; 客服/文案场景需关闭以免正文被 reasoning 吃光) */
    private final boolean enableThinking;

    /** 默认模型名 (由 scrm.ai.model 配置); 调用方未显式指定时注入到请求体 */
    private final String model;

    /** Provider 路由感知的温度（来自 {@link #fromProvider} 构建的 provider 配置） */
    private double providerTemperature = 0.7;

    /** Provider 路由感知的最大 token 数（来自 {@link #fromProvider} 构建的 provider 配置） */
    private int providerMaxTokens = 500;

    /** 建立连接超时 (毫秒), 由 scrm.ai.connect-timeout-ms 配置 */
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;

    /** 读取响应超时 (毫秒), 由 scrm.ai.read-timeout-ms 配置; 批量文案生成可能耗时数十秒, 默认给足 180 秒 */
    private static final int DEFAULT_READ_TIMEOUT_MS = 180_000;

    /**
     * 构造 AI 对话客户端
     *
     * @param baseUrl            AI 服务基础地址
     * @param enabled            AI 能力总开关
     * @param chatPath           AI 对话补全接口路径 (OpenAI 兼容服务填 /v1/chat/completions)
     * @param apiKey             API 密钥, 非空时以 {@code Authorization: Bearer} 头注入 (默认空, 不鉴权)
     * @param model              默认模型名 (如 Magpie 的 {@code group/sensenova-pool}); 调用方未指定时使用
     * @param enableThinking     是否启用模型思考模式, 默认 false
     * @param connectTimeoutMs   建立连接超时 (毫秒)
     * @param readTimeoutMs      读取响应超时 (毫秒)
     */
    public AiChatClient(@Value("${scrm.ai.base-url:}") String baseUrl,
                        @Value("${scrm.ai.enabled:false}") boolean enabled,
                        @Value("${scrm.ai.chat-path:" + DEFAULT_CHAT_COMPLETIONS_PATH + "}") String chatPath,
                        @Value("${scrm.ai.api-key:}") String apiKey,
                        @Value("${scrm.ai.model:" + DEFAULT_MODEL + "}") String model,
                        @Value("${scrm.ai.enable-thinking:false}") boolean enableThinking,
                        @Value("${scrm.ai.connect-timeout-ms:" + DEFAULT_CONNECT_TIMEOUT_MS + "}") int connectTimeoutMs,
                        @Value("${scrm.ai.read-timeout-ms:" + DEFAULT_READ_TIMEOUT_MS + "}") int readTimeoutMs) {
        this.enabled = enabled;
        this.enableThinking = enableThinking;
        this.model = (model == null || model.isBlank()) ? DEFAULT_MODEL : model;
        this.chatCompletionsPath = (chatPath == null || chatPath.isBlank())
                ? DEFAULT_CHAT_COMPLETIONS_PATH : chatPath;
        // 显式配置超时: RestClient 未配置时读超时可能过短, 批量文案生成 (数十秒) 会被提前断开
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Authorization",
                        "Bearer " + (apiKey == null ? "" : apiKey))
                .build();
    }

    /**
     * 当前客户端使用的默认模型名。
     *
     * @return 生效的模型名
     */
    public String getDefaultModel() {
        return model;
    }

    /**
     * 当前客户端使用 Provider 配置的温度（来自 {@link #fromProvider}；老配置路径返回默认 0.7）。
     *
     * @return 生效的采样温度
     */
    public double getTemperature() {
        return providerTemperature;
    }

    /**
     * 当前客户端使用 Provider 配置的最大 token 数（来自 {@link #fromProvider}；老配置路径返回默认 500）。
     *
     * @return 生效的最大 token 数
     */
    public int getMaxTokens() {
        return providerMaxTokens;
    }

    /**
     * 按 AI Provider 注册表条目构建对话客户端。
     * <p>
     * Provider 体系下，模型接入点在系统管理内动态配置（base-url / api-key /
     * chat-path / model / temperature / maxTokens 全部取自 Provider），文本任务
     * 路由到默认文本 provider，证照 OCR 路由到默认多模态 provider。该静态工厂
     * 让调用方免于感知 RestClient 细节，直接拿 Provider 实体构建可用的客户端。
     * 原 {@link #AiChatClient(String, boolean, String, String, String, boolean, int, int)}
     * 构造器保留不动（老配置路径与测试仍在使用）。</p>
     *
     * @param provider Provider 实体
     * @return 以 Provider 配置构建的 AI 对话客户端
     */
    public static AiChatClient fromProvider(ScrmAiProviderEntity provider) {
        String baseUrl = provider.getApiBaseUrl() == null ? "" : provider.getApiBaseUrl().trim();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String chatPath = provider.getChatPath() == null || provider.getChatPath().isBlank()
                ? DEFAULT_CHAT_COMPLETIONS_PATH : provider.getChatPath().trim();
        String apiKey = provider.getApiKey() == null ? "" : provider.getApiKey();
        String model = (provider.getDefaultModel() == null || provider.getDefaultModel().isBlank())
                ? DEFAULT_MODEL : provider.getDefaultModel().trim();
        // 走 Provider 时按 provider 配置注入能力状态：
        //   基础地址有效 + 具备 text 能力 → 视为已启用（无鉴权 provider 如本地
        //   llama-server 允许 api-key 为空）；模型名空 → 回退默认模型名
        boolean effectiveEnabled = !baseUrl.isEmpty()
                && provider.getCapability() != null
                && provider.getCapability().toLowerCase().contains("text");
        double temp = provider.getTemperature() == null ? 0.7 : provider.getTemperature();
        int maxTokens = provider.getMaxTokens() == null ? 500 : provider.getMaxTokens();
        AiChatClient client = new AiChatClient(baseUrl, effectiveEnabled, chatPath, apiKey,
                model, false, DEFAULT_CONNECT_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS);
        client.providerTemperature = temp;
        client.providerMaxTokens = maxTokens;
        return client;
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
            // 调用方未显式指定思考模式时, 按全局配置注入 (避免敏感模型的 reasoning 吃光 max_tokens)
            if (request.getEnableThinking() == null) {
                request.setEnableThinking(enableThinking);
            }
            // 调用方未指定模型时注入配置的默认模型;
            // 不注入会落到 DTO 里的 gpt-4o-mini, 网关（如 Magpie）不认识该名字 → 404 → 被降级吞掉
            if (request.getModel() == null || request.getModel().isBlank()) {
                request.setModel(model);
            }
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
