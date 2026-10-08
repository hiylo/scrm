/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : RealAiResponseGenerator.java
 * Date : 2026/10/08 12:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.evaluator;

import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.entity.ScrmAiAssistantConfigEntity;
import org.hiylo.scrm.feign.AiChatClient;
import org.hiylo.scrm.feign.dto.AiChatRequest;
import org.hiylo.scrm.feign.dto.AiChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 回复生成器真实实现 (对接大模型)。
 * <p>
 * 通过既有 {@link AiChatClient} 调用 AI 服务的对话补全接口
 * ({@code POST {scrm.ai.base-url}{scrm.ai.chat-path}}, 默认
 * {@code /v1/ai/chat/completions}; 对接 OpenAI 兼容服务时可配置
 * {@code scrm.ai.chat-path=/v1/chat/completions}), 生成真实 AI 回复。
 * 生成真实 AI 回复。标注 {@code @Primary} 后自动替换 {@link DefaultAiResponseGenerator},
 * {@code ScrmAiAssistantConversationService} 无需改动即可获得真实回复能力。
 * </p>
 * <p>
 * 降级策略 (fail-open, 保证对话不中断):
 * <ul>
 *   <li>{@code scrm.ai.enabled=false} 或 AI 服务不可达 → {@link AiChatClient} 返回降级内容 →
 *       回退 {@link DefaultAiResponseGenerator} 的意图/情感拼接回复</li>
 *   <li>调用抛异常或响应为空 → 同样回退占位生成器</li>
 * </ul>
 * 模型/温度/maxTokens 优先取 {@link ScrmAiAssistantConfigEntity} 配置, 缺失时回退全局
 * {@code scrm.ai.*} 配置。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Primary
@Component
public class RealAiResponseGenerator implements AiResponseGenerator {

    /** 系统提示词角色 */
    private static final String ROLE_SYSTEM = "system";

    /** 用户消息角色 */
    private static final String ROLE_USER = "user";

    /** 默认系统提示词 (配置缺失且无意图/情感上下文时使用) */
    private static final String DEFAULT_SYSTEM_PROMPT =
            "你是一名专业、礼貌、高效的客户服务助手。请根据客户消息给出自然、口语化的回复。";

    /** AI 对话客户端 (复用既有通道, 含降级) */
    private final AiChatClient aiChatClient;

    /** 占位生成器 (降级回退) */
    private final DefaultAiResponseGenerator fallbackGenerator;

    /** 默认 AI 模型名称 */
    private final String model;

    /** 默认采样温度 */
    private final double temperature;

    /** 默认生成最大 token 数 */
    private final int maxTokens;

    /**
     * 构造真实 AI 回复生成器。
     *
     * @param aiChatClient      AI 对话客户端
     * @param fallbackGenerator 占位生成器 (降级回退)
     * @param model             默认模型名称
     * @param temperature       默认采样温度
     * @param maxTokens         默认生成最大 token 数
     */
    public RealAiResponseGenerator(AiChatClient aiChatClient,
                                   DefaultAiResponseGenerator fallbackGenerator,
                                   @Value("${scrm.ai.model:gpt-4o-mini}") String model,
                                   @Value("${scrm.ai.temperature:0.7}") double temperature,
                                   @Value("${scrm.ai.max-tokens:500}") int maxTokens) {
        this.aiChatClient = aiChatClient;
        this.fallbackGenerator = fallbackGenerator;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

    /**
     * 生成 AI 回复: 调用大模型, 失败/降级时回退占位生成器。
     *
     * @param message        客户消息
     * @param config         AI 配置 (含 systemPrompt / model / temperature / maxTokens, 可空)
     * @param detectedIntent 识别意图
     * @param sentiment      情感
     * @return AI 回复文本; 大模型不可用时返回占位生成器的拼接回复
     */
    @Override
    public String generate(String message, ScrmAiAssistantConfigEntity config,
                           String detectedIntent, String sentiment) {
        try {
            AiChatRequest request = buildRequest(message, config, detectedIntent, sentiment);
            AiChatResponse response = aiChatClient.chatCompletion(request);
            String content = response == null ? null : response.getFirstContent();
            if (content == null || content.isBlank()
                    || AiChatClient.FALLBACK_CONTENT.equals(content)) {
                log.warn("真实 AI 回复为空或触发降级, 回退占位生成器: model={}", request.getModel());
                return fallbackGenerator.generate(message, config, detectedIntent, sentiment);
            }
            log.info("真实 AI 回复生成成功: model={}, finishReason={}, len={}",
                    response.getModel(), extractFinishReason(response), content.length());
            return content;
        } catch (Exception e) {
            log.warn("调用真实 AI 服务异常, 回退占位生成器: err={}", e.getMessage());
            return fallbackGenerator.generate(message, config, detectedIntent, sentiment);
        }
    }

    /**
     * 构建对话补全请求。
     * <p>消息列表固定为两条: system 提示词 + user 客户消息。模型/温度/maxTokens 优先取
     * 配置实体, 缺失时回退全局默认值。</p>
     *
     * @param message        客户消息
     * @param config         AI 配置 (可空)
     * @param detectedIntent 识别意图 (拼入默认系统提示词)
     * @param sentiment      情感 (拼入默认系统提示词)
     * @return 对话补全请求
     */
    private AiChatRequest buildRequest(String message, ScrmAiAssistantConfigEntity config,
                                       String detectedIntent, String sentiment) {
        String systemPrompt = buildSystemPrompt(config, detectedIntent, sentiment);
        String reqModel = config != null && config.getModel() != null && !config.getModel().isBlank()
                ? config.getModel() : model;
        Double reqTemperature = config != null && config.getTemperature() != null
                ? config.getTemperature() : temperature;
        Integer reqMaxTokens = config != null && config.getMaxTokens() != null
                ? config.getMaxTokens() : maxTokens;
        return AiChatRequest.builder()
                .model(reqModel)
                .temperature(reqTemperature)
                .maxTokens(reqMaxTokens)
                .messages(List.of(
                        new AiChatRequest.AiChatMessage(ROLE_SYSTEM, systemPrompt),
                        new AiChatRequest.AiChatMessage(ROLE_USER, message)
                ))
                .build();
    }

    /**
     * 构建系统提示词: 优先取配置实体的 systemPrompt, 缺失时基于意图/情感拼默认提示词。
     *
     * @param config         AI 配置 (可空)
     * @param detectedIntent 识别意图
     * @param sentiment      情感
     * @return 系统提示词
     */
    private String buildSystemPrompt(ScrmAiAssistantConfigEntity config,
                                     String detectedIntent, String sentiment) {
        if (config != null && config.getSystemPrompt() != null && !config.getSystemPrompt().isBlank()) {
            return config.getSystemPrompt();
        }
        return DEFAULT_SYSTEM_PROMPT + String.format(" (已识别意图: %s, 情感: %s)",
                detectedIntent == null ? "UNKNOWN" : detectedIntent,
                sentiment == null ? "NEUTRAL" : sentiment);
    }

    /**
     * 提取响应首条候选的结束原因 (日志用)。
     *
     * @param response AI 响应
     * @return 结束原因, 无法提取时返回 null
     */
    private String extractFinishReason(AiChatResponse response) {
        if (response.getChoices() == null || response.getChoices().isEmpty()) {
            return null;
        }
        AiChatResponse.AiChatChoice first = response.getChoices().get(0);
        return first == null ? null : first.getFinishReason();
    }
}
