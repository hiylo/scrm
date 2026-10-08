/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : RealAiResponseGeneratorTest.java
 * Date : 2026/10/08 12:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.evaluator;

import org.hiylo.scrm.entity.ScrmAiAssistantConfigEntity;
import org.hiylo.scrm.feign.AiChatClient;
import org.hiylo.scrm.feign.dto.AiChatRequest;
import org.hiylo.scrm.feign.dto.AiChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RealAiResponseGenerator} 单元测试。
 * <p>
 * 覆盖真实 AI 回复生成的主路径与三条降级路径:
 * <ul>
 *   <li>主路径: 调用 {@link AiChatClient#chatCompletion} 成功, 返回真实回复内容</li>
 *   <li>降级 1: AI 未启用 / 服务不可达 → {@link AiChatClient} 返回降级内容 → 回退占位生成器</li>
 *   <li>降级 2: 调用抛异常 → 回退占位生成器</li>
 *   <li>降级 3: 响应为空 → 回退占位生成器</li>
 * </ul>
 * 同时校验请求构建: 系统提示词优先取配置实体, 模型/温度/maxTokens 配置优先、全局兜底。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("RealAiResponseGenerator 单元测试")
@ExtendWith(MockitoExtension.class)
class RealAiResponseGeneratorTest {

    /** AI 对话客户端 Mock */
    @Mock
    private AiChatClient aiChatClient;

    /** 占位生成器 Mock */
    @Mock
    private DefaultAiResponseGenerator fallbackGenerator;

    /** 被测对象 (默认全局配置) */
    private RealAiResponseGenerator generator;

    /**
     * 测试前装配被测对象, 使用默认全局配置。
     */
    @BeforeEach
    void setUp() {
        generator = new RealAiResponseGenerator(aiChatClient, fallbackGenerator,
                "gpt-4o-mini", 0.7, 500);
    }

    // ============================================================
    // 主路径
    // ============================================================

    @Test
    @DisplayName("generate: 大模型返回正常内容时直接返回, 不调用占位生成器")
    void generate_success() {
        when(aiChatClient.chatCompletion(any(AiChatRequest.class)))
                .thenReturn(buildAiResponse("您好, 请问有什么可以帮您?", "gpt-4o-mini"));

        String result = generator.generate("你好", null, "GREETING", "NEUTRAL");

        assertThat(result).isEqualTo("您好, 请问有什么可以帮您?");
        verify(fallbackGenerator, never()).generate(any(), any(), any(), any());
    }

    @Test
    @DisplayName("generate: 请求构建优先取配置实体 (systemPrompt/model/temperature/maxTokens)")
    void generate_configEntityWins() {
        ScrmAiAssistantConfigEntity config = new ScrmAiAssistantConfigEntity();
        config.setSystemPrompt("你是客服小张, 请用亲切口吻回复。");
        config.setModel("qwen-max");
        config.setTemperature(0.9);
        config.setMaxTokens(800);
        when(aiChatClient.chatCompletion(any(AiChatRequest.class)))
                .thenReturn(buildAiResponse("好的", "qwen-max"));

        generator.generate("在吗", config, "GREETING", "NEUTRAL");

        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiChatClient, times(1)).chatCompletion(captor.capture());
        AiChatRequest request = captor.getValue();
        assertThat(request.getModel()).isEqualTo("qwen-max");
        assertThat(request.getTemperature()).isEqualTo(0.9);
        assertThat(request.getMaxTokens()).isEqualTo(800);
        assertThat(request.getMessages()).hasSize(2);
        assertThat(request.getMessages().get(0).getRole()).isEqualTo("system");
        assertThat(request.getMessages().get(0).getContent()).isEqualTo("你是客服小张, 请用亲切口吻回复。");
        assertThat(request.getMessages().get(1).getRole()).isEqualTo("user");
        assertThat(request.getMessages().get(1).getContent()).isEqualTo("在吗");
    }

    @Test
    @DisplayName("generate: 配置实体缺省时回退全局配置并拼接意图/情感到默认系统提示词")
    void generate_globalDefaultsAndIntentPrompt() {
        when(aiChatClient.chatCompletion(any(AiChatRequest.class)))
                .thenReturn(buildAiResponse("好的", "gpt-4o-mini"));

        generator.generate("在吗", null, "GREETING", "POSITIVE");

        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiChatClient, times(1)).chatCompletion(captor.capture());
        AiChatRequest request = captor.getValue();
        assertThat(request.getModel()).isEqualTo("gpt-4o-mini");
        assertThat(request.getTemperature()).isEqualTo(0.7);
        assertThat(request.getMaxTokens()).isEqualTo(500);
        String systemPrompt = request.getMessages().get(0).getContent();
        assertThat(systemPrompt).contains("GREETING");
        assertThat(systemPrompt).contains("POSITIVE");
    }

    // ============================================================
    // 降级路径
    // ============================================================

    @Test
    @DisplayName("generate: AiChatClient 返回降级内容时回退占位生成器")
    void generate_fallbackContent() {
        when(aiChatClient.chatCompletion(any(AiChatRequest.class)))
                .thenReturn(buildAiResponse(AiChatClient.FALLBACK_CONTENT, "fallback"));
        when(fallbackGenerator.generate(any(), any(), any(), any())).thenReturn("[AI 模拟回复]已识别您的意图...");

        String result = generator.generate("你好", null, "GREETING", "NEUTRAL");

        assertThat(result).isEqualTo("[AI 模拟回复]已识别您的意图...");
        verify(fallbackGenerator, times(1)).generate(eq("你好"), isNull(), eq("GREETING"), eq("NEUTRAL"));
    }

    @Test
    @DisplayName("generate: AiChatClient 调用抛异常时回退占位生成器")
    void generate_exception() {
        when(aiChatClient.chatCompletion(any(AiChatRequest.class)))
                .thenThrow(new RuntimeException("ai-server 不可达"));
        when(fallbackGenerator.generate(any(), any(), any(), any())).thenReturn("[AI 模拟回复]已识别您的意图...");

        String result = generator.generate("你好", null, "GREETING", "NEUTRAL");

        assertThat(result).isEqualTo("[AI 模拟回复]已识别您的意图...");
        verify(fallbackGenerator, times(1)).generate(eq("你好"), isNull(), eq("GREETING"), eq("NEUTRAL"));
    }

    @Test
    @DisplayName("generate: 响应为空或内容为空时回退占位生成器")
    void generate_emptyResponse() {
        when(aiChatClient.chatCompletion(any(AiChatRequest.class))).thenReturn(null);
        when(fallbackGenerator.generate(any(), any(), any(), any())).thenReturn("[AI 模拟回复]已识别您的意图...");

        String result = generator.generate("你好", null, "GREETING", "NEUTRAL");

        assertThat(result).isEqualTo("[AI 模拟回复]已识别您的意图...");
        verify(fallbackGenerator, times(1)).generate(eq("你好"), isNull(), eq("GREETING"), eq("NEUTRAL"));
    }

    // ============================================================
    // 辅助
    // ============================================================

    /**
     * 构造 AI 响应
     */
    private AiChatResponse buildAiResponse(String content, String model) {
        AiChatResponse response = new AiChatResponse();
        response.setId("resp_1");
        response.setModel(model);
        AiChatResponse.AiChatChoice choice = new AiChatResponse.AiChatChoice();
        choice.setMessage(new AiChatRequest.AiChatMessage("assistant", content));
        choice.setFinishReason("stop");
        response.setChoices(Collections.singletonList(choice));
        return response;
    }
}
